(ns ro.ieugen.training.manifold.deferreds
  (:require [manifold.deferred :as d]))


(comment

  ;; Accessing unrealized defferred will block your repl
  (let [d (d/deferred)]
    @d)

  ;; This will not block your repl
  (let [d (d/deferred)]
    (d/success! d :foo)
    @d)

  ;; This will not block your repl
  (let [d (d/deferred)]
    (d/error! d (Exception. "boom"))
    @d)

  ;; Use a callback
  (let [d (d/deferred)]
    (d/on-realized d
                   (fn success [x] (println "success!" x))
                   (fn error [x] (println "error!" x)))
    (d/success! d :foo)
    ;; (d/error! d (Exception. "boom"))
    )

  ;; composing with deffereds
  (let [d (d/deferred)
        ;; chain returns a deffered with the value of right most callback
        dd (d/chain d inc inc inc (fn printt [x]
                                    (println "x+3" x)
                                    x))]
    (d/success! d 0)
    (println "values are" @d "and" @dd))


  ;; Coercing values to a deferred
  (let [d (d/deferred)
        dd (d/chain d
                    (fn [x] (future (inc x)))
                    (fn [x] (println "the future returned" x) x))]
    (d/success! d 0)
    (println "Values are" @d @dd))

  ;; Error handling
  (let [d (d/deferred)
        dd (-> (d/chain dec
                        #(/ 1 %)
                        (fn [x] (println "We are here" x) x))
               (d/catch Exception (fn [x] (println "whoops, that didn't work:" x) x)))]
    (d/success! d 1)
    (println "Values are" @d "and" @dd))

  ;; Multiple deferrable values
  (let [z (d/zip (future 1) (future 2) (future 3))]
    @z)

  ;; Timeout
  (let [d (d/future (Thread/sleep 1000) :foo)
        t (d/timeout! d 100 :bar)]
    (println "Values are" @t "and" @d))

  ;; future vs manifold.deferred/future
  ;; let-flow
  ;; manifold.deferred/loop

  0
  )