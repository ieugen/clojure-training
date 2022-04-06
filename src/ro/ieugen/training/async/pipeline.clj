(ns ro.ieugen.training.async.pipeline
  "https://gist.github.com/JacobNinja/5c98496a632e1a466cbf"
  (:require [clojure.core.async :as async]
            [clj-http.client :as client]
            [clojure.data.json :as json]
            [taoensso.carmine :as car :refer (wcar)]))



(def concurrency 5)

(def server1-conn {:pool {}
                   :spec {:url "redis://localhost:5672/"}})
(defmacro wcar* [& body] `(car/wcar server1-conn ~@body))


(comment

  (wcar*
   (car/ping)
   (car/set "foo" "bar")
   (car/get "foo")
   (car/lpush "queue" "a" "b" "c" "d"))

  (wcar* (car/lpop "queue"))


  ({"items" [1 2 3]} "items")
  #_(let [b nil] (b "items")) ;; also fails - clj-kondo
  (nil "items") ;; fails !!
  (get nil "items") ;; best - safe

  (let [in (async/chan)
        out (async/chan)
        request-handler (fn [url out*]
                          (async/go
                            (println "Making request:" url)
                            (let [response (client/get url)
                                  body (json/read-str (:body response))]
                              (doseq [repo (get body "items")]
                                (async/>! out (repo "clone_url")))
                              (async/close! out*))
                            (println "Done!" url)))]
    (async/pipeline-async concurrency out request-handler in)
    (async/go
      (doseq [url (for [page (range 10)]
                    (str "https://api.github.com/search/repositories?q=language:clojure&page=" (inc page)))]
        (async/>! in url)))
    (println "Urls sent!")
    (async/go-loop
     []
      (println (async/<! out))
      (recur))
    (println "After go-loop"))

  ; `in` can be backed by a redis queue
  (let [in (async/chan)
        out (async/chan)
        request-handler (fn [url out*]
                          (async/go
                            (println "Making request:" url)
                            (let [response (client/get url)
                                  body (json/read-str (:body response))]
                              (doseq [repo (get body "items")]
                                (async/>! out (repo "clone_url")))
                              (async/close! out*))
                            (println "Done!" url)))]
    (async/pipeline-async concurrency out request-handler in)
    (async/go
      (doseq [url (for [page (range 10)]
                    (str "https://api.github.com/search/repositories?q=language:clojure&page=" (inc page)))]
        (async/>! in url)))
    (println "Urls sent!")
    (async/go-loop
     []
      (println (async/<! out))
      (recur))
    (println "After go-loop")

    ; `in` can be backed by a redis queue
    #_(async/go-loop []
        (if-let [message (pop-redis-queue)]
          (async/>! in message)
      ; Sleep if no messages available
          (async/<! (async/timeout 1000)))
        (recur)))




  0)

(comment)