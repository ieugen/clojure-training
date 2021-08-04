(ns ro.ieugen.training.design-patterns.core
  (:require [ro.ieugen.training.design-patterns.dummy :as db]))

;
; http://mishadoff.com/blog/clojure-design-patterns/
;


; Intro

;; Episode 1 - command

(defn execute [command]
  (command))

(execute #(db/login "django" "unCh@ined"))
(execute #(db/logout "django"))

(defn execute2 [command & args]
  (apply command args))

(execute2 db/login "django" "unCh@ined")
(execute2 db/logout "django")

;; Episode 2 - Strategy

(def users [{:subscription true
             :name "Alex"}
            {:subscription false
             :name "Bia"}
            {:subscription true
             :name "Nina"}])

(sort (comparator
       (fn [u1 u2]
         (cond
           (= (:subscription u1)
              (:subscription u2)) (neg? (compare (:name u1)
                                                 (:name u2)))
           (:subscription u1) true
           :else false))) users)

;; forward sort
(sort-by (juxt (complement :subscription) :name) users)

;; reverse sort
(sort-by (juxt :subscription :name) #(compare %2 %1) users)

((juxt :subscription :name) (first users))

; Episode 3 - State



(defmulti news-feed :user-state)

(defmethod news-feed :subscription [user]
  (db/news-feed user))

(defmethod news-feed :no-subscription [user]
  (take 10 (db/news-feed user)))

(def user (atom {:name "jackie Brown"
                 :balance 0
                 :user-state :no-subscription}))

(def ^:const SUBSCRIPTION_COST 30)

(defn pay [user amount]
  (swap! user update-in [:balance] + amount)
  (when (and (>= (:balance @user) SUBSCRIPTION_COST)
             (= :no-subscription (:user-state @user)))
    (swap! user assoc :user-state :subscription)
    (swap! user update-in [:balance] - SUBSCRIPTION_COST)))

(news-feed @user)
(pay user 10)
(news-feed @user)
(pay user 25)
(news-feed @user)


; Episode 4 Visitor

(->> "Test" .toUpperCase .toLowerCase)