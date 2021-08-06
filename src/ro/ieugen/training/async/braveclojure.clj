(ns ro.ieugen.training.async.braveclojure
  (:require [clojure.core.async :as a
             :refer [>! <! >!! <!! go chan buffer close! thread
                     alts! alts!! timeout]]))

;; https://www.braveclojure.com/core-async/


(def echo-chan (chan))

(go (println (<! echo-chan)))

(comment
  (>!! echo-chan "ketchup")
  (>!! echo-chan "mustard")

  (go (>! echo-chan "ketchup")
      (>! echo-chan "mustard"))
  0)


;; Buffering

(def echo-buffer (chan 2))

(comment
  (>!! echo-buffer "ketchup")
  (>!! echo-buffer "ketchup")
  (>!! echo-buffer "ketchup"))

;; Blocking and parking

(def hi-chan (chan))


(comment

  (doseq [n (range 1000)]
    (go (>! hi-chan (str "hi " n))))

  (thread (println (<!! echo-chan)))
  (>!! echo-chan "mustard")


  (let [t (thread "chili")]
    (<!! t))
  0)


;; The hot doc machine

(defn hot-dog-machine
  []
  (let [in (chan)
        out (chan)]
    (go (<! in)
        (>! out "hot dog"))
    [in out]))

(comment

  (let [[in out] (hot-dog-machine)]
    (>!! in "pocket lint")
    (<!! out))
  0)

(defn hot-dog-machine-v2
  [hot-dog-count]
  (let [in (chan)
        out (chan)]
    (go (loop [hc hot-dog-count]
          (if (> hc 0)
            (let [input (<! in)]
              (if (= 3 input)
                (do (>! out "hot dog")
                    (recur (dec hc)))
                (do (>! out "wilted lettuce")
                    (recur hc))))
            (do (close! in)
                (close! out)))))
    [in out]))

(comment

  (let [[in out] (hot-dog-machine-v2 2)]
    (>!! in "pocket lint")
    (println (<!! out))
    (>!! in 3)
    (println (<!! out))
    (>!! in 3)
    (println (<!! out))
    (>!! in 3)
    (<!! out))

  (let [c1 (chan)
        c2 (chan)
        c3 (chan)]
    (go (>! c2 (clojure.string/upper-case (<! c1))))
    (go (>! c3 (clojure.string/reverse (<! c2))))
    (go (println (<! c3)))
    (>!! c1 "redrum"))

  0)


;; alts!!

(defn upload
  [headshot c]
  (go (Thread/sleep (rand 1000))
      (>! c headshot)))

(comment

  (let [c1 (chan)
        c2 (chan)
        c3 (chan)]

    (upload "serious.jpg" c1)
    (upload "fun.jpg" c2)
    (upload "sassy.jpg" c3)
    (let [[headshot channel] (alts!! [c1 c2 c3])]
      (println "Sending headshot notification for " headshot)))


  (let [c1 (chan)]
    (upload "serious.jpg" c1)
    (let [[headshot channel] (alts!! [c1 (timeout 20)])]
      (if headshot
        (println "Sending headshot notification for" headshot)
        (println "Timed out"))))

  (let [c1 (chan)
        c2 (chan)]
    (go (println (<! c2)))
    (let [[value channel] (alts!! [c1 [c2 "put!"]])]
      (println value)
      (= channel c2)))
  0)

;; Queues

(defn append-to-file
  "Write a string to the end of a file"
  [filename s]
  (spit filename s :append true))

(defn format-quote
  "Delineate the beginning and end of a quote because it's convenient"
  [quote]
  (str "=== BEGIN QUOTE ===\n" quote "=== END QUOTE ===\n\n"))

(defn random-quote
  "Retrieve a random quote and format it"
  []
  (format-quote (slurp "http://www.braveclojure.com/random-quote")))

(defn snag-quotes
  [filename num-quotes]
  (let [c (chan)]
    (go (while true (append-to-file filename (<! c))))
    (dotimes [n num-quotes]
      (go (>! c (random-quote))))))


(comment
  (snag-quotes "quotes.txt" 2)
  0)


;; Escape callback hell with process pipelines

(defn upper-caser
  [in]
  (let [out (chan)]
    (go
      (while true (>! out (clojure.string/upper-case (<! in)))))
    out))

(defn reverser
  [in]
  (let [out (chan)]
    (go (while true (>! out (clojure.string/reverse (<! in)))))
    out))

(defn printer
  [in]
  (let [out (chan)]
    (go (while true (println (<! in))))
    out))

(def in-chan (chan))
(def upper-caser-out (upper-caser in-chan))
(def reverser-out (reverser upper-caser-out))
(printer reverser-out)

(comment

  (>!! in-chan "redrum")

  (>!! in-chan "repaid")

  0)