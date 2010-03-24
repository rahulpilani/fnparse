(ns edu.arizona.fnparse.hound.test
  (:require [edu.arizona.fnparse :as c]
            [edu.arizona.fnparse.hound :as p]
            [clojure.test :as test]))

(defmethod test/assert-expr 'match?
  [msg [_ rule input & opts]]
  (c/match-assert-expr p/match msg rule input opts))

(defmethod test/assert-expr 'non-match?
  [msg [_ rule input & opts]]
  (c/non-match-assert-expr p/match msg rule input opts))
