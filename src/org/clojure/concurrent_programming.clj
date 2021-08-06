(ns org.clojure.concurrent-programming
  (:import [java.util.concurrent Executors]))

;; https://clojure.org/about/concurrent_programming

(defn test-stm [nitems nthreads niters]
  (let [refs (map ref (repeat nitems 0))
        pool (Executors/newFixedThreadPool nthreads)
        tasks (map (fn [t]
                     (fn []
                       (dotimes [n niters]
                         (dosync
                          (doseq [r refs]
                            (alter r + 1 t))))))
                   (range nthreads))]
    (doseq [future (.invokeAll pool tasks)]
      (.get future))
    (.shutdown pool)
    (map deref refs)))

;; Dynamic vars

(def ^:dynamic *v*)

(defn incv [n] (set! *v* (+ *v* n)))

(defn test-vars [nthreds niters]
  (let [pool (Executors/newFixedThreadPool nthreds)
        tasks (map (fn [t]
                     #(binding [*v* 0]
                        (dotimes [n niters]
                          (incv t))
                        *v*))
                   (range nthreds))]
    (let [ret (.invokeAll pool tasks)]
      (.shutdown pool)
      (map #(.get %) ret))))


;; Context oriented programming with var rebinding

(defn ^:dynamic say [& args]
  (apply str args))

(defn loves [x y]
  (say x " loves " y))

(defn test-rebind []
  (println (loves "ricky" "lucy"))
  (let [say-orig say]
    (binding [say (fn [& args]
                    (println "Logging say")
                    (apply say-orig args))]
      (println (loves "fred" "ethel")))))


(comment

  (test-stm 10 10 10000)
  
  (test-vars 10 1000000)

  (test-rebind)
  
  0
  )