(ns hfds-clj.core
  "Dataset fetching and storing from HuggingFace.

  HF Datasets provide rich functionality
  https://huggingface.co/docs/datasets/index"
  (:require
    [progrock.core :as pr]
    [clojure.java.io :as io]
    [clojure.edn :as edn]
    [hato.client :as hc]
    [jsonista.core :as j]
    [taoensso.timbre :as timbre]
    [net.modulolotus.truegrit :as tg]
    [me.flowthing.pp :as pp]))

(def ^:private datasets-server "https://datasets-server.huggingface.co")

(def ^:private default-cache-dir (str (System/getProperty "user.home") "/.cache/hfds-clj"))

(defn- pp-str
  [x]
  (with-out-str (pp/pprint x)))

(defn- write-ds
  [ds-file ds]
  (io/make-parents ds-file)
  (spit ds-file (pp-str ds)))

(defn- ds-dir-name
  [cache-dir dataset config-name split]
  (str cache-dir "/" dataset "/" config-name "/" split))

(defn- ds-cached?
  [cache-dir dataset config-name split]
  (.exists (io/file (ds-dir-name cache-dir dataset config-name split))))

(defn- ds-file
  ([cache-dir dataset config-name split] (ds-file cache-dir dataset config-name split 1))
  ([cache-dir dataset config-name split part-nr]
   (io/file
    (format "%s/part-%04d.edn"
            (ds-dir-name cache-dir dataset config-name split)
            part-nr))))

(defn- info-file
  [cache-dir dataset config-name]
  (str cache-dir "/" dataset "/" config-name "/info.edn" ))

(defn- fetch-hf
  [hf-url params]
  (-> (hc/get hf-url {:query-params params})
      :body
      (j/read-value j/keyword-keys-object-mapper)))

(defn- fetch-dataset*
  [ds-params]
  (fetch-hf (str datasets-server "/rows") ds-params))

(defn- fetch-dataset
  "Fetch dataset with True Grit backed resilience. It will retry fetching on HF errors.

  Drop all HF metadata like `features`"
  [hf-params]
  (let [fetch (-> (fn [] (mapv :row
                               (:rows (fetch-dataset* hf-params))))
                  (tg/with-time-limiter {:timeout-duration 5000})
                  (tg/with-retry
                    {:name            "hf-retry"
                     :max-attempts    5
                     :wait-duration   1000
                     :retry-on-result nil?}))]
    (fetch)))

(defn- fetch-info
  [params]
  (fetch-hf (str datasets-server "/info") params))

(defn download-ds
  "Download data set from the HuggingFace. See `load-dataset` for params documentation"
  [{:keys [dataset offset length split config]
    :as   params}
   {:hfds/keys [cache-dir limit]
    :or        {cache-dir default-cache-dir}}]
  (timbre/infof "Downloading %s:%s" dataset split)
  (letfn [(log-progress [bar page]
            (pr/print (pr/tick bar (* page length))))]
    (let [{:keys [dataset_info] :as info} (fetch-info params)
          rows-total                      (get-in dataset_info [ :splits (keyword split) :num_examples])
          page                            (fetch-dataset params)
          record-limit                    (or limit rows-total)
          bar                             (pr/progress-bar record-limit)]
      (log-progress bar 1)
      (write-ds (ds-file cache-dir dataset config split 1) page)
      (write-ds (info-file cache-dir dataset config) info)
      (loop [page 1]
        (let [from-offset (+ offset (* page length))]
          (if (and
               (> rows-total from-offset)
               (> record-limit from-offset))
            (do
              (log-progress bar (inc page))
              (write-ds
               (ds-file cache-dir dataset config split (inc page))
               (fetch-dataset (assoc params :offset (+ offset (* page length)))))
              (recur (inc page)))
            (timbre/info "\nDone downloading 🤗")))))))

(defn download-cli
  "A version of `download-ds` to be used for CLI invocation (fixes the params)."
  [{:keys [cache-dir limit offset length] :as params}]
  (download-ds
   (assoc params
          :offset offset
          :length length)
   (merge
    (when limit {:hfds/limit limit})
    (when cache-dir
      {:hfds/cache-dir cache-dir}))))

(defn read-ds
  "Read data set from the cache. It is assumed that it is there.
   See `load-dataset` for params documentation."
  [{:keys [dataset split config]}
   {:hfds/keys [limit cache-dir]
    :or        {cache-dir default-cache-dir}}]
  (timbre/infof "Loading '%s:%s' from cache" dataset split)
  (let [xf (comp
             (filter #(.isFile %))
             (mapcat #(-> % slurp edn/read-string :rows))
             (map :row))
        ds   (sequence xf
               (file-seq (io/file (ds-dir-name cache-dir dataset config split))))]
    (if limit
      (take limit ds)
      ds)))

(defn load-dataset
  "Download a *dataset* from HuggingFace. Dataset name is usually specified
  in HuggingFace dataset webpage. Usually in a form of `org-name/ds-name`

  First argument can be
  1) a map specifying HuggingFace HTTP call parameters and is used as is for HF REST API HTTP calls
  b) a string with dataset name, it will be converted into `{:dataset ds-name}` param map

  Second argument is a map specifying how to read the ds."
  ([ds-params-or-ds-name]
   (load-dataset
     (if (map? ds-params-or-ds-name)
       ds-params-or-ds-name
       {:dataset ds-params-or-ds-name})
     nil))
  ([{:keys [dataset split config offset length]
     :or   {split  "train"
            config "default"
            offset 0
            length 100}}
    {:hfds/keys [cache-dir download-mode]
     :or        {download-mode :reuse-dataset-if-exists
                 cache-dir     default-cache-dir}
     :as        read-params}]
   (let [ds-params {:dataset dataset
                    :split   split
                    :config  config
                    :offset  offset
                    :length  length}]
     (if (and (= :reuse-dataset-if-exists download-mode)
           (ds-cached? cache-dir dataset config split))
       (read-ds ds-params read-params)
       (do
         (download-ds ds-params read-params)
         (read-ds ds-params read-params))))))

(comment
  (def hh-rlhf (load-dataset "Anthropic/hh-rlhf"))
  (def prosoc-ds (load-dataset
                   {:dataset "allenai/prosocial-dialog"
                    :split   "train"
                    :config  "default"
                    :offset  0
                    :length  100} {}))
  (def prosoc-ds (load-dataset
                   {:dataset "stingning/ultrachat"}
                   {:hfds/download-mode :force-download
                    :hfds/limit         1000}))
  #__)
