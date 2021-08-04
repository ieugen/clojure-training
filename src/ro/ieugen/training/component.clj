(ns ro.ieugen.training.component
  (:require [com.stuartsierra.component :as component]))



(defrecord Database [host port connection]
  component/Lifecycle

  (start [component]
    (println ";; Starting database")
    (let [conn (str host ":" port)]
      (assoc component :connection conn)))
  (stop [component]
    (println ";; Stopping database")
    (println "Close connection")
    (assoc component :connection nil)))

(defn new-database [host port]
  (map->Database {:host host :port port}))

(defn get-user [database username]
  (println (:connection database) " -> " username))

(defn add-user [database username favorite-color]
  (println (:connection database) " -> " username " " favorite-color))

(defrecord Scheduler []
  component/Lifecycle

  (start [this]
    (println ";; Start scheduler")
    (assoc this :scheduler {:cron "0 * * * * *"}))

(stop [this]
      (println ";; Stop scheduler")
      (assoc this :scheduler nil))  )

(defn new-scheduler []
  (map->Scheduler {}))

(defrecord ExampleComponent [options cache database scheduler]
  component/Lifecycle

  (start [this]
    (println ";; Start ExampleComponent")
    (assoc this :admin (str database "admin")))

  (stop [this]
        (println ";; Stopping ExampleComponent")
        this))

(defn new-example-component [config-options]
  (map->ExampleComponent {:options config-options
                          :cache (atom {})}))

(defn example-system [config-options]
  (let [{:keys [host port]} config-options]
    (component/system-map
     :db (new-database host port)
     :scheduler (new-scheduler)
     :app (component/using
           (new-example-component config-options)
           {:database :db
            :scheduler :scheduler}))))

(def system (example-system {:host "netdava.com" :port 80}))


(comment

  (alter-var-root #'system component/start)
  
  (alter-var-root #'system component/stop)
  
  (let [s (example-system {:host "netdava.com" :port 80})]
    (component/start s))

  0)