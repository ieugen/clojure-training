(ns ro.ieugen.training.async.core
  (:require [clojure.core.async :as a
             :refer [>! <! >!! <!! go chan buffer close! thread alts! alts!! take! put! timeout]])
  (:gen-class))

;; https://github.com/halgari/clojure-conj-2013-core.async-examples


;; https://medium.com/swlh/asynchronous-clojure-a59fa0f9bda0


(comment

  ;; unbuffered channel
  (def simplechan (chan))

  (put! simplechan "1")
  (put! simplechan "2")
  (put! simplechan "3")

  (dotimes [i 1024]
    (put! simplechan i))

  (take! simplechan println)

  ;; buffered channel
  (def bufferedchan (chan (a/buffer 2000)))
  (dotimes [i 2000] (put! bufferedchan i))

  (dotimes [i 2001]
    (take! bufferedchan println))

  ;; dropping channel
  (def droppingchan (chan (a/dropping-buffer 2000)))
  (dotimes [i 20000] (put! droppingchan i))

  (dotimes [i 20000] (take! droppingchan println))

  ;; sliding buffer channels
  (def slidingchan (chan (a/sliding-buffer 2000)))

  (dotimes [i 5000000] (put! slidingchan i))

  (take! slidingchan println)



  0)