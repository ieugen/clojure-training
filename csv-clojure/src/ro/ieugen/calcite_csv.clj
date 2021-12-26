(ns ro.ieugen.calcite-csv
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [next.jdbc :as jdbc])
  (:import (java.io File)
           (java.util ArrayList
                      HashSet
                      HashMap
                      List
                      Map
                      Objects)
           (org.apache.calcite DataContext$Variable)
           (org.apache.calcite.adapter.file CsvEnumerator
                                            JsonScannableTable)
           (org.apache.calcite.linq4j AbstractEnumerable)
           (org.apache.calcite.model ModelHandler$ExtraOperand)
           (org.apache.calcite.rel.type RelDataTypeFactory
                                        RelProtoDataType)
           (org.apache.calcite.schema ScannableTable
                                      Schema
                                      Schema$TableType
                                      SchemaPlus
                                      Schemas
                                      Statistics
                                      Table
                                      Wrapper)
           (org.apache.calcite.sql SqlNode)
           (org.apache.calcite.sql.parser SqlParser)
           (org.apache.calcite.util Source
                                    Sources
                                    ImmutableIntList)))

(set! *warn-on-reflection* true)

(def csv-table-flavors [:scannable :filterable :translatable])

(defn get-field-types
  ^List [^RelDataTypeFactory type-factory ^Source source ^List field-types ^Boolean is-stream]
  ;; TODO: This will mutate field-types !!
  (when (.isEmpty field-types)
    (CsvEnumerator/deduceRowType type-factory source field-types is-stream))
  field-types)

(defn scannable-table
  ^Table [^Source source ^RelProtoDataType proto-row-type]
  (let [row-type (atom nil)
        is-tream false
        field-types-x (ArrayList.)]
    (reify
      ScannableTable Wrapper
      (getStatistic [this] Statistics/UNKNOWN)
      (getJdbcTableType [this] Schema$TableType/TABLE)
      (unwrap [this aClass]
              (if (.isInstance aClass this)
                (.cast aClass this)
                nil))
      (isRolledUp [this column] false)
      (rolledUpColumnValidInsideAgg [this column call parent config] true)
      (getRowType [this type-factory]
        (cond
          (some? proto-row-type) (do
                                  ;;  (println "Oh oh" proto-row-type "aa" type-factory)
                                   (.apply proto-row-type type-factory))
          (nil? @row-type) (do
                            ;;  (println "reset")
                             (reset! row-type (CsvEnumerator/deduceRowType type-factory source nil is-tream))))
        @row-type)
      (toString [this] "ro.ieugen.calcite-csv/scannable-table")
      (scan [this root]
        ;; (println "scan " source "root" root)
        (let [type-factory (.getTypeFactory root)
              field-types (get-field-types type-factory source field-types-x is-tream)
              fields (ImmutableIntList/identity (.size field-types))
              cancel-flag (.get DataContext$Variable/CANCEL_FLAG root)]
          (proxy
           [AbstractEnumerable] []
            (enumerator []
              ;; (println "Inside enumerator" field-types "fields" fields "cancel" cancel-flag)
              (let [converter (CsvEnumerator/arrayConverter field-types fields false)]
                (CsvEnumerator. source cancel-flag false nil converter)))))))))

(defn translatable-table
  ^Table [^Source source proto-row-type]
  (throw (UnsupportedOperationException. "Translatable not implemented.")))

(defn filterable-table
  ^Table [^Source source proto-row-type]
  (throw (UnsupportedOperationException. "Filterable not implemented.")))

(defn create-table
  "Creates different sub-type of table based on the `flavor`"
  ^Table [^Source source flavor]
  ;; (println "Create table" source "flavor:" flavor)
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
    ;; (println "File" fname)
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
  ^Map [^File dir flavor]
  (let [base-source (Sources/of dir)]
    (if-let [files (filter csv-json-file-filter (file-seq dir))]
      (into {} (map (partial file->Table base-source flavor) files))
      (do
        (println "directory" dir "not found")
        ;; empty map - no files / tables found
        {}))))

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

(defn csv-schema
  "Create a Calcite Schema and return it for use."
  ^Schema [^SchemaPlus parent-schema ^String name ^Map operand]
  (println "csv-schema" parent-schema "name:" name "operand:" operand)
  (let [directory (.get operand "directory")
        baseDirName (.camelName ModelHandler$ExtraOperand/BASE_DIRECTORY)
        base (io/file (.get operand baseDirName))
        directory-file (schema-directory-file base directory)
        flavor-name (or (.get operand "flavor") "scannable")
        flavor (keyword (str/lower-case flavor-name))
        tables (create-table-map directory-file flavor)
        sub-schemas (atom {})]
    ;; (println tables)
    (reify
      Schema
      (isMutable [this] true)
      (snapshot [this version] this)
      (getExpression [this parent-schema name]
        (Objects/requireNonNull parent-schema "parent-schema")
        (Schemas/subSchemaExpression parent-schema name (.getClass this)))
      (getTableNames [this] (.keySet tables))
      (getTable [this name] (.get tables name))
      (getType [this name] (.get (HashMap.) name))
      (getTypeNames [this] (HashSet.))
      (getFunctions [this name] (HashSet.))
      (getFunctionNames [this] (HashSet.))
      (getSubSchemaNames [this] (.keySet (HashMap.)))
      (getSubSchema [this name] (get @sub-schemas name)))))

(comment

  (trim "hello.csv.gz" ".gz")

  (let [tables (create-table-map (io/file "resources/sales") :scannable)]
    (.keySet tables))

  (require '[clojure.tools.trace :as trace])
  (trace/trace-ns 'ro.ieugen.calcite-csv)

  (let [db {:jdbcUrl "jdbc:calcite:model=resources/model.json"
            :user "admin"
            :password "admin"}
        ds (jdbc/get-datasource db)]
    (jdbc/execute! ds ["select * from emps where age is null or age >= 40"]))
  )
