(ns ro.ieugen.training.parallel.claypool
  "https://github.com/TheClimateCorporation/claypoole"
  (:require [com.climate.claypoole :as cp]))

(defn thread-name [] (.getName (Thread/currentThread)))

(defn my-fn 
  [i]
  (println "Wait for" i " " (thread-name))
  (Thread/sleep (+ 2000 (rand-int 500)))
  (println "Done " i " " (thread-name))
  i)

(defn myfn2 [i]
  (let [r (str ">> " i)]
    (println "done" i)
    r))

(def pool (cp/threadpool (cp/ncpus)))

(def fut (cp/future pool (my-fn ["a" "b" "c" :a])))

(def cfut (cp/completable-future pool (my-fn ["b" :a :d])))

(def intermediates (cp/upmap pool my-fn ["a" "b" "c" :a]))
(def output (cp/upmap pool myfn2 intermediates))

(doseq [o output]
  (prn o))

(cp/shutdown pool)
