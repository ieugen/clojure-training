(ns ro.ieugen.training.transducers.riptutorial
  "Exercises from https://riptutorial.com/clojure/example/32428/applying-transducers"
  (:require [clojure.tools.trace :as ctt]
            [clojure.core.async :refer [chan >!! <!! poll!]]))

(ctt/trace-ns ro.ieugen.training.transducers.riptutorial)

(def xf (filter keyword?))

(sequence xf [:a 1 2 :b :c])

(transduce xf str [:a 1 2 :b :c])

(into [] xf [:a 1 2 :b :c])

(def ch (chan 10 xf))

(doseq [e [:a 1 2 :b :c]]
  (>!! ch e))

(comment

  (<!! ch)
  (<!! ch)
  (<!! ch)
  (poll! ch)
  )