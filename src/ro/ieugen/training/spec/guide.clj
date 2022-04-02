(ns ro.ieugen.training.spec.guide
  "https://clojure.org/guides/spec"
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]))


;; https://clojure.org/guides/spec#_predicates
(comment

  (s/conform even? 1000)
  (s/conform even? 1001)

  (s/valid? even? 10)
  (s/valid? even? 11)

  (s/valid? nil? nil)
  (s/valid? string? "abc")

  (s/valid? #{42} 42)

  0)

(s/def :order/date inst?)

(s/def :deck/suit #{:club :diamond :heart :spade})

;; https://clojure.org/guides/spec#_registry
(comment


  (s/valid? :order/date (java.util.Date.))
  (s/conform :deck/suit :club)

  (clojure.repl/doc :order/date)
  (clojure.repl/doc :deck/suit)

  0)


;; https://clojure.org/guides/spec#_composing_predicates

(s/def :num/big-even (s/and int? even? #(> % 1000)))

(comment

  (s/valid? :num/big-even :foo)

  (s/valid? :num/big-even 10)
  (s/valid? :num/big-even 10000)

  0)

(s/def :domain/name-or-id (s/or :name string?
                                :id int?))

(comment

  (s/valid? :domain/name-or-id "abc")
  (s/valid? :domain/name-or-id 100)
  (s/valid? :domain/name-or-id :foo)

  (s/conform :domain/name-or-id "abc")

  (s/conform :domain/name-or-id 100)

  (s/conform :domain/name-or-id :foo)

  0)

;; https://clojure.org/guides/spec#_explain

(comment

  (s/explain :deck/suit 42)

  (s/explain :num/big-even 5)

  (s/explain :domain/name-or-id :foo)

  (s/explain-data :domain/name-or-id :foo)

  0)

;; https://clojure.org/guides/spec#_entity_maps

(def email-regex #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$")

(s/def :acct/email-type (s/and string? #(re-matches email-regex %)))

(s/def :acct/acctid int?)
(s/def :acct/first-name string?)
(s/def :acct/last-name string?)
(s/def :acct/email :acct/email-type)

(s/def :acct/person (s/keys :req [:acct/first-name :acct/last-name :acct/email]
                            :opt [:acct/phone]))

(comment
  (s/valid? :acct/person
            {:acct/first-name "Bugs"
             :acct/last-name "Bunny"
             :acct/email "bugs@example.com"})

  (s/explain :acct/person
             {:acct/first-name "Bugs"})

  (s/explain :acct/person
             {:acct/first-name "Bugs"
              :acct/last-name "Bunny"
              :acct/email "n/a"})
  0)

(s/def :unq/person
  (s/keys :req-un [:acct/first-name :acct/last-name :acct/email]
          :opt-un [:acct/phone]))

(defrecord Person [first-name last-name email phone])


(comment

  (s/conform :unq/person
             {:first-name "Bugs"
              :last-name "Bunny"
              :email "bugs@example.com"})

  (s/explain :unq/person
             {:first-name "Bugs"
              :last-name "Bunny"
              :email "n/a"})

  (s/explain :unq/person
             {:first-name "Bugs"})


  (s/conform :unq/person (->Person "Bugs" "Bunny" "bugs@example.com" nil))

  (s/explain :unq/person (->Person "Bugs" nil nil nil))

  0)

(s/def :my.config/port number?)
(s/def :my.config/host string?)
(s/def :my.config/id keyword?)
(s/def :my.config/server (s/keys* :req [:my.config/id :my.config/host]
                                  :opt [:my.config/port]))

(comment
  (s/conform :my.config/server
             [:my.config/id :s1
              :my.config/host "example.com"
              :my.config/port 555])
  0)

(s/def :animal/kind string?)
(s/def :animal/says string?)
(s/def :animal/common (s/keys :req [:animal/kind :animal/says]))
(s/def :dog/tail? boolean?)
(s/def :dog/breed string?)
(s/def :animal/dog (s/merge :animal/common
                            (s/keys :req [:dog/tail? :dog/breed])))

(comment

  (s/valid? :animal/dog
            {:animal/kind "dog"
             :animal/says "woof"
             :dog/tail? true
             :dog/breed "retriever"})

  0)

;; https://clojure.org/guides/spec#_multi_spec
;; TODO





;; https://clojure.org/guides/spec#_sequences


(s/def :ex/config (s/*
                   (s/cat :prop string?
                          :val  (s/alt :s string? :b boolean?))))
(comment
  (s/conform :ex/config ["-server" "foo" "-verbose" true "-user" "joe"])


  0)



;; https://clojure.org/guides/spec#_using_spec_for_validation

(defn person-name
  [person]
  {:pre [(s/valid? :acct/person person)]
   :post [(s/valid? string? %)]}
  (str (:acct/first-name person) " " (:acct/last-name person)))

(comment

  (person-name 42)
  (person-name {:acct/first-name "Bugs"
                :acct/last-name "Bunny"
                :acct/email "bugs@example.com"})

  0)

(defn person-name2
  [person]
  (let [p (s/assert :acct/person person)]
    (str (:acct/first-name p) " " (:acct/last-name p))))

(s/check-asserts true)

(comment

  (person-name2 100)

  0)

(defn- set-config [prop val]
  ;; dummy fn
  (println "set" prop val))

(defn configure
  [input]
  (let [parsed (s/conform :ex/config input)]
    (if (s/invalid? parsed)
      (throw (ex-info "Invalid input" (s/explain-data :ex/config input)))
      (for [{prop :prop [_ val] :val} parsed]
        (set-config (subs prop 1) val)))))

(comment

  (configure ["-server" "foo" "-verbose" true "-user" "joe"])

  0)










;; https://clojure.org/guides/spec#_generators 

(comment

  (gen/generate (s/gen int?))

  (gen/generate (s/gen :animal/dog))

  (gen/generate (s/gen :my.config/server))


  0)