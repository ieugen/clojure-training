(ns ro.ieugen.calcite-csv
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [com.rpl.proxy-plus :refer [proxy+]]
            [next.jdbc :as jdbc])
  (:import (java.io File)
           (java.util ArrayList
                      List
                      Map)
           (org.apache.calcite DataContext
                               DataContext$Variable)
           (org.apache.calcite.adapter.file CsvEnumerator
                                            JsonScannableTable)
           (org.apache.calcite.adapter.java JavaTypeFactory)
           (org.apache.calcite.linq4j AbstractEnumerable)
           (org.apache.calcite.model ModelHandler$ExtraOperand)
           (org.apache.calcite.rel.type RelDataTypeFactory
                                        RelProtoDataType)
           (org.apache.calcite.schema Schema
                                      SchemaPlus
                                      ScannableTable
                                      Table)
           (org.apache.calcite.schema.impl AbstractSchema
                                           AbstractTable)
           (org.apache.calcite.util ImmutableIntList
                                    Source
                                    Sources)))

(set! *warn-on-reflection* true)

;; (def csv-table-flavors [:scannable :filterable :translatable])

(defn get-field-types
  ^List [^RelDataTypeFactory type-factory ^Source source ^List field-types is-stream]
  ;; TODO: This will mutate field-types !!
  (when (.isEmpty field-types)
    (CsvEnumerator/deduceRowType type-factory source field-types is-stream))
  field-types)

(defn ^:private get-row-type
  [this ^Source source ^JavaTypeFactory type-factory ^RelProtoDataType proto-row-type row-type is-stream]
  ;; (println "Get row type" source "tf" type-factory "prt" proto-row-type "rt" @row-type "is" is-stream)
  (cond
    (some? proto-row-type) (do
                             (.apply proto-row-type type-factory))
    (nil? @row-type) (do
                       (reset! row-type (CsvEnumerator/deduceRowType type-factory source nil is-stream))))
  @row-type)

(defn scannable-table
  "Table based on a CSV file.
   It implements the {@link ScannableTable} interface, so Calcite gets
   data by calling the {@link #scan(DataContext)} method."
  ^Table [^Source source ^RelProtoDataType proto-row-type]
  (let [row-type (atom nil)
        is-stream false
        field-types-x (ArrayList.)]
    (proxy+
     []
     AbstractTable
     (getRowType
      [this ^RelDataTypeFactory type-factory]
      (get-row-type this source type-factory proto-row-type row-type is-stream))

     ScannableTable
     (scan
      [this ^DataContext root]
      (let [type-factory (.getTypeFactory root)
            field-types (get-field-types type-factory source field-types-x is-stream)
            fields (ImmutableIntList/identity (.size field-types))
            cancel-flag (.get DataContext$Variable/CANCEL_FLAG root)]
        (proxy+
         []
         AbstractEnumerable
         (enumerator
          [this]
          (let [converter (CsvEnumerator/arrayConverter field-types fields false)]
            (CsvEnumerator. source cancel-flag false nil converter)))))))))

(defn create-table
  "Creates different sub-type of table based on the `flavor`"
  ^Table [^Source source flavor]
  ;; (println "Create table" source "flavor:" flavor)
  (case flavor
    :scannable (scannable-table source nil)
    :translatable (do
                    (throw (UnsupportedOperationException. "translatable not implemented"))
                    #_(translatable-table source nil))
    :filterable (do
                  (throw (UnsupportedOperationException. "filterable not implemented"))
                  #_(filterable-table source nil))
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
  "Read a CSV/JSON file as a Table."
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
  ;; (println "csv-schema" parent-schema "name:" name "operand:" operand)
  (let [directory (.get operand "directory")
        baseDirName (.camelName ModelHandler$ExtraOperand/BASE_DIRECTORY)
        base (io/file (.get operand baseDirName))
        directory-file (schema-directory-file base directory)
        flavor-name (or (.get operand "flavor") "scannable")
        flavor (keyword (str/lower-case flavor-name))
        tables (create-table-map directory-file flavor)]
    ;; (println "tables " tables)
    (proxy+
     []
     AbstractSchema
     (getTableMap [this] tables))))

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
    (jdbc/execute! ds ["select * from emps where age is null or age >= 40"])))
