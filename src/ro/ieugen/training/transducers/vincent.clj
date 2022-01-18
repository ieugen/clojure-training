(ns ro.ieugen.training.transducers.vincent
  "https://vincent.404.taipei/clojure/build-your-own-transducer-part1/
   and
   https://github.com/green-coder/transducer-exercises")

(map identity)

(into ["b" "a"] (map identity) (list "n" "a" "n" "a"))

(into ["b" "a"]
      (map #(str (char (dec (Character/codePointAt % 0)))))
      (list "u" "n" "b" "o"))

(into []
      (filter #(<= (Character/codePointAt "a" 0) (Character/codePointAt % 0) (Character/codePointAt "f" 0)))
      (list "c" "r" "a" "f" "e" "b" "h" "a" "b" "l" "e"))

(into []
      (mapcat #(if (<= 0 % 9)
                 (list % %)
                 (list %)))
      (list 10 5 16 7 13))

(into []
      (comp
       (map inc)
       (filter odd?)
       (mapcat #(if (<= 0 % 9)
                  (list % %)
                  (list %))))
      (list 8 9 10 11 12))


;;
;; https://vincent.404.taipei/clojure/build-your-own-transducer-part2/
;;


;; one-one mapping - inc

(def inc-transducer
  (fn [rf]
    (fn
      ([] (rf))
      ([result] (rf result))
      ([result input] (rf result (inc input))))))

(into [] inc-transducer (list 4 5 6))

; idiomatic way:
; (into [] (map inc) (list 4 5 6))

;; one-one with parameters

(defn add-transducer
  [n]
  (fn [rf]
    (fn
      ([] (rf))
      ([result] (rf result))
      ([result input] (rf result (+ input n))))))

(into [] (add-transducer 3) (list 4 5 6))

; idiomatic ways:
; (into [] (remove #(= :rabbit %)) (list :dog :rabbit :lynel))
; (into [] (filter #(not= :rabbit %)) (list :dog :rabbit :lynel))


;; one-some - some elements are filtered

(defn magician-transducer
  [animal]
  (fn [rf]
    (fn
      ([] (rf))
      ([result] (rf result))
      ([result input]
       (if (= animal input)
         result
         (rf result input))))))

(into [] (magician-transducer :rabbit) (list :dog :rabbit :lynel))

; idiomatic ways:
; (into [] (remove #(= :rabbit %)) (list :dog :rabbit :lynel))
; (into [] (filter #(not= :rabbit %)) (list :dog :rabbit :lynel))


;; one-two transducer

(defn glitch-transducer
  [animal]
  (fn [rf]
    (fn
      ([] (rf))
      ([result] (rf result))
      ([result input]
       (if (= animal input)
         (-> result
             (rf input)
             (rf input))
         (rf result input))))))


(into [] (glitch-transducer :cat) (list :dog :cat :lynel))

; (into []
;       (mapcat #(if (= :cat %) (list % %) (list %)))
;    :lynel))


;; one-many

(def rle-decoder-transducer
  (fn [rf]
    (fn
      ([] (rf))
      ([result] (rf result))
      ([result [count data]]
       (reduce rf result (repeat count data))))))

(into []
      rle-decoder-transducer
      (list [0 :a] [1 :b] [2 :c] [3 :d]))

; idiomatic way:
; (into []
;       (mapcat (fn [[count data]] (repeat count data)))
;       (list [0 :a] [1 :b] [2 :c] [3 :d]))



;; Stateful transducers


(defn string-builder-transducer
  [separator]
  (fn [rf]
    (let [state (volatile! [])]
      (fn
        ([] (rf))
        ([result] (-> result
                      (rf (apply str @state))
                      (rf)))
        ([result input]
         (let [chars @state]
           (if (= separator input)
             (do (vreset! state [])
                 (rf result (apply str chars)))
             (do (vreset! state (conj chars input))
                 result))))))))

(into []
      (string-builder-transducer 0)
      (list \H \e \l \l \o 0 \C \l \o \j \u \r \e 0 \w \o \r \l \d \!))

;; chunk sum transcoder

(defn chunk-sum-transcoder
  [separator]
  (fn [rf]
    (let [state (volatile! 0)]
      (fn
        ([] (rf))
        ([result] (-> result
                      (rf @state)
                      (rf)))
        ([result input]
         (let [acc @state]
           (if (= separator input)
             (do (vreset! state 0)
                 (rf result acc))
             (do (vreset! state (+ acc input))
                 result))))))))

(into []
      (chunk-sum-transcoder :|)
      (list 1 2 3 4 :| 42 :| :| 3 5))


;; packet transducer

(defn packet-transducer
  [max-size]
  (fn [rf]
    (let [state (volatile! {:packet []
                            :size 0})]
      (fn
        ([] (rf))
        ([result]
         (let [{:keys [packet size]} @state]
           (cond-> result
             (pos? size) (rf packet)
             :always (rf))))
        ([result input]
         (let [{:keys [packet size]} @state
               input-size (count input)
               new-size (+ size input-size)]
           (if (<= new-size max-size)
             (do (vreset! state {:packet (conj packet input)
                                 :size new-size})
                 result)
             (do (vreset! state {:packet [input]
                                 :size input-size})
                 (cond-> result
                   (pos? size) (rf packet))))))))))


(into []
      (packet-transducer 5)
      (list [1 1] [2 2] [3 3 3] [4 4] [5] [6 6 6 6 6]))


;; Early termination
;; https://vincent.404.taipei/clojure/build-your-own-transducer-part4/
;;

(defn tired-map-transducer
  [func energy tired-answer]
  (fn [rf]
    (let [state (volatile! energy)]
      (fn
        ([] (rf))
        ([result] (rf result))
        ([result input]
         (rf result
             (let [energy @state]
               (if (pos? energy)
                 (do
                   (vreset! state (dec energy))
                   (func input))
                 tired-answer))))))))

(into []
      (tired-map-transducer inc 5 :whatever)
      (list 1 2 3 4 5 6 7 8 9 10))

(defn responsible-map-transducer
  [func energy]
  (fn [rf]
    (let [state (volatile! energy)]
      (fn
        ([] (rf))
        ([result] (rf result))
        ([result input]
         (let [energy @state]
           (vreset! state (dec energy))
           (cond-> (rf result (func input))
             (<= energy 1) reduced)))))))

(into []
      (responsible-map-transducer inc 5)
      (list 1 2 3 4 5 6 7 8 9 10))

(into []
      (comp
       (responsible-map-transducer inc 5)
       (responsible-map-transducer inc 3))
      (list 1 2 3 4 5 6 7 8 9 10))

(into []
      (comp
       (responsible-map-transducer inc 3)
       (responsible-map-transducer inc 5))
      (list 1 2 3 4 5 6 7 8 9 10))

#_(into []
      (comp
       (responsible-map-transducer inc 3)
       (responsible-map-transducer inc 3))
      (list 1 2 3 4 5 6 7 8 9 10))

(defn correct-map-transducer
  [func energy]
  (fn [rf]
    (let [state (volatile! energy)]
      (fn
        ([] (rf))
        ([result] (rf result))
        ([result input]
         (let [energy @state]
           (vreset! state (dec energy))
           (cond-> (rf result (func input))
             (<= energy 1) ensure-reduced)))))))

(into []
      (comp
       (correct-map-transducer inc 3)
       (correct-map-transducer inc 3))
      (list 1 2 3 4 5 6 7 8 9 10))

; Idiomatic way:
; (into []
;       (comp
;         (take 3)   ; Step 1
;         (map inc)  ; Step 2
;         (map inc)) ; Step 3
;       (range 1 11))





;; Exercises
;; https://github.com/green-coder/transducer-exercises
;;

;; No Operation transducer
;; Implement a transducer that does nothing but passing the data from his input to his output. Functionally speaking, it is an identity transducer.

(into [] identity (range 3))

(def identity-transducer
  (fn [rf]
    (fn
      ([] (rf))
      ([result] (rf result))
      ([result input] (rf result input)))))

(into [] identity-transducer (range 4))

;; https://github.com/green-coder/transducer-exercises#prepare-for-battle


(defn debug
  ([]
   (debug 0))
  ([indent]
   (debug indent ">" "<"))
  ([indent in out]
   (let [spaces (apply str (repeat indent \space))]
     (debug (str spaces in)
            (str spaces out))))
  ;; transducer follows
  ([in out]
   (fn [rf]
     (fn
       ([] (rf))
       ([result] (rf result))
       ([result input]
        (println in input)
        (let [res (rf result input)]
          (println out res)
          res))))))


; We use this function instead of `into` for debugging.
; The reason is that this avoids using transient
; structures which do not `print` nicely.
(defn slow-into [to xf from]
  (transduce xf conj to from))

#_(slow-into [] (debug "in" "out") (range 3))


#_(slow-into []
           (comp (debug)
                 (debug 2)
                 (debug 4 ">" "<")
                 (debug "      >" "      <"))
           (range 3))


;; https://github.com/green-coder/transducer-exercises#may-i-beg-your-pardon


(def beg-data (list :may :i :beg :your :pardon :?))

;; complete and correct solution is https://github.com/green-coder/transducer-exercises/blob/master/solution/beg-step2.clj 
;; needs another function - rrf
(defn beg
  [n]
  (fn [rf]
    (fn
      ([] (rf))
      ([result] (rf result))
      ([result input]
       (reduce rf result (repeat n input))))))

(into [] (beg 2) beg-data)

(into []
      (comp (take 3)
            (beg 2))
      beg-data)

(into []
      (comp (beg 2)
            (take 3))
      beg-data)

(defn preserving-reduced [rf]
  (fn [a b]
    (let [ret (rf a b)]
      (if (reduced? ret)
        (reduced ret)
        ret))))

; Step 2
(defn beg-correct [n]
  (fn [rf]
    (let [rrf (preserving-reduced rf)]
      (fn ([] (rf))
        ([result] (rf result))
        ([result input]
         (reduce rrf result (repeat n input)))))))


(slow-into []
           (comp (debug 0)
                 (beg-correct 2)
                 (debug 2)
                 (take 3)
                 (debug 4))
           beg-data)


;; https://github.com/green-coder/transducer-exercises#all-your-data-are-belong-to-me



(comment

  (conj ["1" "2"])

  (repeat 3 :b) ; => (:b :b :b)
  (reduce conj ["a" "x"] (repeat 3 :b))

  (reduce conj ["a" "x"] (list :b :b :b))
  
  )

