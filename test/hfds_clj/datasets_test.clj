(ns hfds-clj.datasets-test
  (:require
   [clojure.test :refer [deftest is]]
   [hfds-clj.datasets :refer [read-ds]]))

(deftest read-ds-test
  (is (= 6 (count (read-ds {:dataset "hfds/small-test-ds"}
                           {:hfds/cache-dir "test/data"})))))

