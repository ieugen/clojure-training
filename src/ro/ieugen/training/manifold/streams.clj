(ns ro.ieugen.training.manifold.streams
  (:require [manifold.stream :as s]))

(comment

  ;; a simple stream
  (let [s (s/stream)]
    (println s)
    (s/put! s 1)
    (s/take! s)
    (println "closing")
    (s/close! s)
    ;; closed streams return deferred false on put!
    @(s/put! s 3))

  ;; a drained stream
  (let [s (s/stream)]
    (println s)
    (s/put! s 1)
    (s/take! s)
    (s/put! s 2)
    ;; (s/put! s 3)
    (println @(s/take! s ::drained))
    (s/close! s)
    (println @(s/take! s ::drained)))

  ;; timeouts with try-put
  (let [s (s/stream)]
    (println s)
    (println @(s/try-put! s :foo 1000 ::timeout))
    (println @(s/try-take! s ::drained 1000 ::timeout))
    ;; after close the stream is drained
    (s/close! s)
    (println @(s/try-take! s ::drained 1000 ::timeout)))
  
  
  ;; stream operators
  (let [s (s/stream)
        c (s/consume #(prn 'message! %) s)]
    (println s)
    (println c)
    (println @(s/put! s 1))
    
    (println (->> [1 2 3]
                  s/->source
                  (s/map inc)
                  s/stream->seq))
    (println "again")
    (println (->> [1 2 3]
                  (s/map inc)
                  s/stream->seq)))
  
  (let [s (s/stream)
        a (s/map inc s)
        b (s/map dec s)]
    (println "s" @(s/put! s 0))
    ;; (println "s" @(s/take! s))
    (println "a" @(s/take! a))
    (println "b" @(s/take! b)))
  
  ;; transducers
  (->> [1 2 3]
       (s/transform (map inc))
       s/stream->seq)
  ;; periodically ?!
  
  ;; connecting streams
  (let [a (s/stream)
        b (s/stream)]
    (s/connect a b)
    (println "a" @(s/put! a 1))
    (println "b" @(s/take! b)))
  
  ;; description
  
  (let [a (s/stream)
        b (s/stream)]
    (s/connect a b {:description "a connection"})
    (println "a" (s/description a))
    (println "b" (s/description b))
    (println "down a" (s/downstream a))
    (println "down b" (s/downstream b)))
  
  ;; connect-via = transform message 
  (let [a (s/stream)
        b (s/stream)
        connected (s/connect-via a #(s/put! b (inc %)) b)]
    (s/map inc a)
    (s/downstream a))
  
  ;; buffers and backpressure
  ;; event busses and publish/subscribe models
  

  0
  )