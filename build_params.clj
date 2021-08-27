(ns build-params
  (:require [clojure.tools.build.api :as b]))

;; clj -T:build-params jar :env :dev

(def lib 'my/lib)
(def version (format "1.2.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))
(def copy-srcs ["src" "resources"])

;; Parameterized builds

(defn clean [params]
  (b/delete {:path "target"})
  params)

(defn jar [{:keys [env] :as params}]
  (let [srcs (if (= env :dev)
               (cons "dev-resources" copy-srcs)
               copy-srcs)]    
    (b/write-pom {:class-dir class-dir
                  :lib lib
                  :version version
                  :basis basis
                  :src-dirs ["src"]})
    (b/copy-dir {:src-dirs srcs
                 :target-dir class-dir})
    (b/jar {:class-dir class-dir
            :jar-file jar-file})))

(comment

  (jar nil)


  
  0)