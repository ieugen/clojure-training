(ns ro.ieugen.training.design-patterns.dummy)

(defn login [& args]
  (println "Login" args))

(defn logout [& args]
  (println "Logout" args))


(defn news-feed [& args]
  (println "news-feed " args))