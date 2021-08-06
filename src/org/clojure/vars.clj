(ns org.clojure.vars)

;; https://clojure.org/reference/vars


;; Static vars

;; Unbound var
(def x)
x
;; Bound var
(def x 1)


;; Dynamic vars

(def ^:dynamic x 1)
(def ^:dynamic y 1)

(+ x y)

(binding [x 2 y 3]
  (+ x y))

(+ x y)


;; Binding conveyance

(def ^:dynamic *num* 1)
(binding [*num* 2] 
  (future (println *num*)))


(comment
  
  (var x)
  

  0
  )