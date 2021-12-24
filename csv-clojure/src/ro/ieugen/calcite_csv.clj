(ns ro.ieugen.calcite-csv
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [next.jdbc :as jdbc])
  (:import (java.io File)
           (java.util HashSet
                      HashMap
                      Objects)
           (org.apache.calcite.adapter.file CsvEnumerator
                                            JsonScannableTable)
           (org.apache.calcite.model ModelHandler
                                     ModelHandler$ExtraOperand)
           (org.apache.calcite.rel.type RelProtoDataType)
           (org.apache.calcite.schema ScannableTable
                                      Schema
                                      Schema$TableType
                                      SchemaPlus
                                      Schemas
                                      SchemaFactory
                                      Statistics
                                      Table
                                      Wrapper)
           (org.apache.calcite.util Source
                                    Sources)))

(def csv-table-flavors [:scannable :filterable :translatable])

(defn scannable-table
  ^Table [^Source source ^RelProtoDataType proto-row-type]
  (let [row-type (atom nil)]
    (reify
      ScannableTable Wrapper
      (getStatistic [this] Statistics/UNKNOWN)
      (getJdbcTableType [this] Schema$TableType/TABLE)
      (unwrap [this class] (when (.isInstance class this) (.cast class this)))
      (isRolledUp [this column] false)
      (rolledUpColumnValidInsideAgg [this column call parent config] true)
      (getRowType [this type-factory]
        (cond
          (nil? proto-row-type) (.apply proto-row-type type-factory)
          (nil? @row-type) (do
                             (reset! row-type (CsvEnumerator/deduceRowType type-factory source nil (-> this (.isStream))))
                             @row-type))))))

(defn translatable-table
  ^Table [^Source source proto-row-type]
  (throw (UnsupportedOperationException. "Translatable not implemented.")))

(defn filterable-table
  ^Table [^Source source proto-row-type]
  (throw (UnsupportedOperationException. "Filterable not implemented.")))

(defn create-table
  "Creates different sub-type of table based on the `flavor`"
  ^Table [^Source source flavor]
  (case flavor
    :scannable (scannable-table source nil)
    :translatable (translatable-table source nil)
    :filterable (filterable-table source nil)
    (throw (AssertionError. (str "Unknown flavor" flavor)))))

(defn- trim
  "Looks for a suffix on a string and returns
   either the string with the suffix removed
   or the original string."
  ^String [^String s ^String suffix]
  (if (str/ends-with? s suffix)
    (.substring s 0 (- (.length s) (.length suffix)))
    s))

(defn csv-json-file-filter
  "Returns true if f is a csv or a json file (with optional .gz extension)."
  [^File f]
  (let [fname (trim (.getName f) ".gz")]
    (println "File" fname)
    (and (.isFile f)
         (or (str/ends-with? fname ".csv")
             (str/ends-with? fname ".json")))))

(defn file->Table
  "Read a file as a Table"
  [^Source base-source flavor ^File f]
  (let [^Source source (Sources/of f)
        source-sans-gz (.trim source ".gz")]
    ;; adapted from Java code - there can be only json or csv.
    (or
     (when-let [source-sans-json (.trimOrNull source-sans-gz ".json")]
       [(-> source-sans-json
            (.relative base-source)
            (.path))
        (JsonScannableTable. source)])
     (when-let [source-sans-csv (.trimOrNull source-sans-gz ".csv")]
       [(-> source-sans-csv
            (.relative base-source)
            (.path))
        (create-table source flavor)]))))

(defn create-table-map
  "Scan a directory `dir` to register csv files as tables of `flavor` type."
  [^File dir flavor]
  (let [base-source (Sources/of dir)]
    (if-let [files (filter csv-json-file-filter (file-seq dir))]
      (into {} (map (partial file->Table base-source flavor) files))
      (do
        (println "directory" dir "not found")
        ;; empty map - no files / tables found
        {}))))


(defn csv-schema [dir flavor]
  (let [dir dir
        flavor flavor
        tables (create-table-map dir flavor)
        types (atom {})
        sub-schemas (atom {})]
    (reify
      Schema
      (isMutable [this] true)
      (snapshot [this version] this)
      (getExpression [this parent-schema name]
        (Objects/requireNonNull parent-schema "parent-schema")
        (Schemas/subSchemaExpression parent-schema name (.getClass this)))
      (getTableNames [this] (.keySet tables))
      (getTable [this name] (.get tables name))
      (getType [this name] (.get tables name))
      (getFunctions [this name] (throw (UnsupportedOperationException. "Not implemented")))
      (getFunctionNames [this] (throw (UnsupportedOperationException. "Not implemented")))
      (getSubSchemaNames [this] (HashSet. (keys @sub-schemas)))
      (getSubSchema [this name] (get @sub-schemas name)))))

(defn schema-directory-file
  "Return path for schema directory.
   If path is not absolute, prepend base."
  ^File [^File base ^String directory]
  (let [directory-file (io/file directory)
        nil-base? (nil? base)
        not-absolute-directory? (not (.isAbsolute directory-file))]
    (if (and nil-base? not-absolute-directory?)
      (io/file base directory)
      directory-file)))

(defn csv-schema-factory
  ^SchemaFactory []
  (reify SchemaFactory
    (create [this parent-schema name operand]
      (let [directory (.get operand "directory")
            baseDirName (.camelName ModelHandler$ExtraOperand/BASE_DIRECTORY)
            base (io/file (.get operand baseDirName))
            directory-file (schema-directory-file base directory)
            flavor-name (or (.get operand "flavor") "scannable")
            flavor (keyword (str/lower-case flavor-name))]
        (csv-schema directory-file flavor)))))


(defn- main [& args]
  (println "Hello"))


(comment

  (str/lower-case nil)

  (println "Hello")
  (println (.camelName ModelHandler$ExtraOperand/BASE_DIRECTORY))

  (trim "hello.csv.gz" ".gz")

  (let [x ["a" "b" "c"]]
    (into {} (map (fn [f] [f (str "val-" f)]) x)))

  (create-table-map (io/file "../csv/src/test/resources/sales") :scannable)

  (let [schema-factory (csv-schema-factory)
        operand (doto (HashMap.)
                  (.put "directory" "../csv/src/test/resources/sales")
                  (.put "flavor" "scannable"))]
    (.create schema-factory nil "SALES" operand))


  (let [db {:jdbcUrl "jdbc:calcite:model=resources/model.json"
            :user "admin"
            :password "admin"}
        ds (jdbc/get-datasource db)]
    (jdbc/execute! ds ["select * from sales"]))

  )
