(ns hfds-clj.core-test
  (:require
   [clojure.test :refer [deftest is]]
   [hfds-clj.core :refer [read-ds]]))

(deftest read-ds-test
  (is (= 6 (count (read-ds {:dataset "hfds/small-test-ds"}
                           {:hfds/cache-dir "test/data"})))))
