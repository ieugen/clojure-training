(ns ro.ieugen.training.async.concurrency-blocking
  "https://eli.thegreenplace.net/2017/clojure-concurrency-and-blocking-with-coreasync/"
  (:require [clojure.core.async :as async]
            [clj-http.client]))

(defn receive-n
  [c n]
  (loop [i 0
         res []]
    (if (= i n)
      res
      (recur (inc i) (conj res (async/<!! c))))))


(defn launch-n-go-blocks
  [n]
  (let [c (async/chan)]
    (dotimes [i n]
      (async/go
        (Thread/sleep 10)
        (async/>! c i)))
    (receive-n c n)))

(defn launch-n-threads
  [n]
  (let [c (async/chan)]
    (dotimes [i n]
      (async/thread
        (Thread/sleep 10)
        (async/>!! c i)))
    (receive-n c n)))

(comment

  ;; The go-block thread pool

  (time (launch-n-go-blocks 1))
  (time (launch-n-go-blocks 2))
  (time (launch-n-go-blocks 5))
  (time (launch-n-go-blocks 8))
  (time (launch-n-go-blocks 10))
  (time (launch-n-go-blocks 12))
  (time (launch-n-go-blocks 60))


  (time (launch-n-threads 1))
  (time (launch-n-threads 2))
  (time (launch-n-threads 5))
  (time (launch-n-threads 8))
  (time (launch-n-threads 10))
  (time (launch-n-threads 12))
  (time (launch-n-threads 60))



  0)

;; Blocking I/O

(defn get-multiple
  [generator-fn start n]
  (let [c (async/chan)]
    (generator-fn c start n)
    (loop [i 0
           res []]
      (if (= i n)
        res
        (recur (inc i) (conj res (async/<!! c)))))))

(def url-template "https://github.com/eliben/pycparser/pull/%d")

(defn blocking-get-page
  [i]
  (clj-http.client/get (format url-template i)))

(defn go-blocking-generator
  [c start n]
  (doseq [i (range start (+ start n))]
    (async/go (async/>! c (blocking-get-page i)))))

(defn thread-blocking-generator
  [c start n]
  (doseq [i (range start (+ start n))]
    (async/thread (async/>!! c (blocking-get-page i)))))

(comment

  (let [start 10
        num-results 20]
    #_(time (count (get-multiple go-blocking-generator start num-results)))
    (time (count (get-multiple thread-blocking-generator start num-results))))

  0)

;; Parallelizing CPU-bound tasks

(defn factorize
  "Naive factorization function; takes an integer n and returns a vector of
  factors."
  [n]
  (if (< n 2)
    []
    (loop [factors []
           n n
           p 2]
      (cond (= n 1) factors
            (= 0 (mod n p)) (recur (conj factors p) (quot n p) p)
            (>= (* p p) n) (conj factors n)
            (> p 2) (recur factors n (+ p 2))
            :else (recur factors n (+ p 1))))))


(defn serial-factorizer
  "Simple serial factorizer."
  [nums]
  (zipmap nums (map factorize nums)))

(defn receive-n-maps
  "Receive n items from the given channel and merge them all into one map."
  [c n]
  (loop [i 0
         res {}]
    (if (= i n)
      res
      (recur (inc i) (conj res (async/<!! c))))))

(defn async-go-factorizer
  "Parallel factorizer for nums, launching n go blocks."
  [nums n]
  ;;; Push nums into an input channel; spin up n go-blocks to read from this
  ;;; channel and add numbers to an output channel.
  (let [in-c (async/chan)
        out-c (async/chan)]
    (async/onto-chan in-c nums)
    (dotimes [i n]
      (async/go-loop []
        (when-let [nextnum (async/<! in-c)]
          (async/>! out-c {nextnum (factorize nextnum)})
          (recur))))
    (receive-n-maps out-c (count nums))))


(defn async-with-pipeline
  "Parallel factorizer using async/pipeline."
  [nums n]
  (let [in-c (async/chan)
        out-c (async/chan)]
    (async/onto-chan in-c nums)
    (async/pipeline n out-c (map #(hash-map % (factorize %))) in-c)
    (receive-n-maps out-c (count nums))))

(comment

  (time (serial-factorizer  (take 1000 (repeat (* 29  982451653)))))

  (time (async-go-factorizer (take 1000 (repeat (* 29  982451653))) 8))

  (time (async-with-pipeline (take 1000 (repeat (* 29  982451653))) 8))

  0)


;; Combining blocking and CPU-bound tasks

(def mynum (* 29  982451653))

(defn launch-go-blocking-and-compute
  [nblock ncompute]
  (let [c (async/chan)]
    (dotimes [i nblock]
      (async/go
        (Thread/sleep 250)
        (async/>! c i)))
    (dotimes [i ncompute]
      (async/go
        (async/>! c (factorize mynum))))
    (receive-n c (+ nblock ncompute))))


(comment

  (time (launch-go-blocking-and-compute 32 16))

  (time (launch-go-blocking-and-compute 64 16))

  0)





