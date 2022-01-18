(ns ro.ieugen.training.transducers.joaoptrindade
  "https://joaoptrindade.com/clojure-transducer-quick-example")


(range 1 10)

(map inc (range 1 10))

(map inc (filter odd? (range 1 10)))



(def t1 (map inc))

(def t2 (filter odd?))

(def t1+t2 (comp t1 t2))

(def t2+t1 (comp t2 t1))

(transduce (comp t1 t2) conj (range 1 10))

(into [] t1+t2 (range 1 10))

(transduce t2+t1 conj (range 1 10))

(transduce t1+t2 str (range 1 10))