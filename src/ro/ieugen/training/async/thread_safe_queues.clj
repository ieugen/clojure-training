(ns ro.ieugen.training.async.thread-safe-queues
  "https://www.eidel.io/2019/01/22/thread-safe-queues-clojure/"
  (:require [clojure.core.async :as async :refer [go]]))

(comment
  ;; Using workers
  (dotimes [i 10]
    (go
      (println "Processing" i)))


  ;; Keeping track of completed jobs

  (let [completed (atom [])]
    (dotimes [i 10]
      (go
        (swap! completed conj i)))
    (Thread/sleep 100)
    @completed)

  ;; Using atoms to keep track of jobs-to-be-done?

  (let [queue (atom (vec (range 20)))
        completed (atom [])]
    (dotimes [i (count @queue)]
      (go
        (let [item (peek @queue)]
          (Thread/sleep 2)
          (swap! completed conj item)
          (swap! queue pop))))
    (Thread/sleep 100)
    @completed)

  ;; Solve it with refs and STM?
  (let [queue (ref (vec (range 20)))
        completed (atom [])]
    (dotimes [i (count @queue)]
      (go
        (let [item (peek @queue)]
          (dosync
           (Thread/sleep 2)
           (swap! completed conj item)
           (alter queue pop)))))
    (Thread/sleep 100)
    @completed)

  ;; Taking a Step Back
  (let [queue (atom [0 1 2 3])]
    (swap-vals! queue pop))

  (let [queue (atom (vec (range 20)))
        completed (atom [])]
    (dotimes [_ (count @queue)]
      (go
        (let [[old _new] (swap-vals! queue pop)
              item (peek old)]
          (Thread/sleep 2)
          (swap! completed conj item))))
    (Thread/sleep 100)
    @completed)

  ;; Improvement: Use clojure.lang.PersistentQueue





  0)


