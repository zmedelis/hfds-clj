(ns hfds-clj.models
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [progress.determinate]
   [progress.indeterminate]
   [hato.client :as hc]
   [hato.middleware :as hm]
   [jsonista.core :as json])
  (:import
   (org.apache.commons.io.input CountingInputStream)))


(def ^:private spinner-style
  ;(:coloured-ascii-boxes progress.determinate/styles)
  (:ascii-basic progress.determinate/styles))



(defn- wrap-downloaded-bytes-counter
  "Middleware that provides an CountingInputStream wrapping the stream output"
  [client]
  (fn [req]
    (let [resp (client req)

          counter (CountingInputStream. (:body resp))]
      (merge resp {:body                     counter
                   :downloaded-bytes-counter counter}))))

(def ^:private my-middleware (concat [(first hm/default-middleware) wrap-downloaded-bytes-counter]
                                     (drop 1 hm/default-middleware)))


(defn- do-download [progress response target]
  (let [buffer-size (* 1024 1024)]
    (with-open [input (:body response)
                output (io/output-stream target)]
      (let [buffer (make-array Byte/TYPE buffer-size)
            counter (:downloaded-bytes-counter response)]
        (loop []
          (let [size (.read input buffer)
                progress-mb (int (/ (.getByteCount counter) 1024 1024))]
            (when (pos? size)
              (.write output buffer 0 size)
              (when (pos? progress-mb)
                (reset! progress progress-mb))
              (recur))))))))


(defn- download-with-progress [url target path authorization-token
                               progress-files
                               total-files]
  (let [response (hc/get url
                         {:as :stream
                          :http-client {:redirect-policy :always}
                          :middleware my-middleware
                          :headers {"Authorization"
                                    (format "Bearer %s" authorization-token)}})

        length (Long. (get-in response [:headers "content-length"] 0))
        total-mb (if (zero? length)
                   Integer/MAX_VALUE
                   (int (/ length 1024 1024)))
        progress (atom 0)]

    (progress.determinate/animate!
     progress
     :opts {:total total-mb
            :redraw-rate 60
            :style spinner-style
            :label (format "%s (%s/%s)"  path progress-files total-files)
            :preserve true
            :units "MB"}
     (do-download progress response target))))



(defn download-model!
  "Download all files from a given 'model' from huggingface to a local dir"

  [models-base-dir model-name revision hf-authorization-token]
  (let [split-by-slash (str/split model-name #"/")
        model-namespace (first split-by-slash)
        model-repo (second split-by-slash)
        model-base-dir (format "%s/%s/%s" models-base-dir model-namespace model-repo)
        
        model-files
        (->
         (hc/get (format "https://huggingface.co/api/models/%s/%s/tree/%s?recursive=true" model-namespace model-repo revision)
                 {:headers {"Authorization" (format "Bearer %s" hf-authorization-token)}})
         :body
         (json/read-value json/keyword-keys-object-mapper))

        file-progress (atom 0)]
    (run!
     (fn [{:keys [type path]}]
       (case type
         "directory" (io/make-parents (format "%s/%s" model-base-dir path))
         "file"
         (do
           (io/make-parents (format "%s/%s" model-base-dir path))
           (download-with-progress (format "https://huggingface.co/%s/%s/resolve/main/%s" model-namespace model-repo path)
                                   (format "%s/%s" model-base-dir path)
                                   path
                                   hf-authorization-token
                                   @file-progress
                                   (count model-files))
           (swap! file-progress inc))))
     model-files)))

(defn download-cli [{:keys [model revision hf-token models-base-dir]
                     :or {revision "main"}
                     }
                    ]
  (assert (some? model) "'model' is missing. Model name must be provided.")
  ;(assert (some? hf-token) "'hf-token' is missing. Huggingface authorization token must be provided.")
  (assert (some? models-base-dir) "'model-base-dir' is missing. Base dir to store models must be provided.")
  (download-model! models-base-dir model revision hf-token))



(comment

  (hfds-clj.models/download-model!  "/tmp/hf-models"
                                    "nvidia/Gemma-2b-it-ONNX-INT4"
                                    "revision"
                                    (slurp "hf_token.txt"))
  )

