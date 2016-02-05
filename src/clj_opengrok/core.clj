(ns clj-opengrok.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as string]
            [cli4clj.cli :refer :all])
  (:gen-class)
  (:import (java.io StringReader File ByteArrayInputStream ObjectInputStream
                    InputStreamReader FileInputStream)
           (java.util ArrayList)
           (java.util.concurrent Executors)
           (org.opensolaris.opengrok.history RepositoryFactory)
           (org.opensolaris.opengrok.analysis Definitions Definitions$Tag)
           (org.opensolaris.opengrok.search QueryBuilder Hit)
           (org.opensolaris.opengrok.search.context Context)
           (org.opensolaris.opengrok.configuration RuntimeEnvironment)
           (org.apache.lucene.analysis Analyzer TokenStream)
           (org.apache.lucene.analysis.standard StandardAnalyzer)
           (org.apache.lucene.document Document Field Field$Index Field$Store)
           (org.apache.lucene.index IndexWriter IndexReader Term MultiReader
                                    IndexWriterConfig DirectoryReader FieldInfo)
           (org.apache.lucene.queryparser.classic QueryParser)
           (org.apache.lucene.search TopScoreDocCollector
                                     BooleanClause BooleanClause$Occur
                                     BooleanQuery IndexSearcher Query ScoreDoc
                                     Scorer TermQuery Sort SortField SortField$Type)
           (org.apache.lucene.search.highlight Highlighter QueryScorer
                                               SimpleHTMLFormatter)
           (org.apache.lucene.util Version AttributeSource BytesRef)
           (org.apache.lucene.store FSDirectory NIOFSDirectory RAMDirectory Directory)))

(def cli-options
  [["-R" "--R" "<configuration.xml> Read configuration from the specified file"]
   ["-d" nil "Symbol Definitions"
    :id :definition]
   ["-r" "--r" "Symbol References"]
   ;; ["-p" "--p" "Path"]
   ;; ["-h" "--h" "History"]
   ;; ["-f" "--f" "Full Text"]
   ;; ["-t" "--t" "Type"]
   ["-h" "--help"]])

;; (def cli-options
;;   [;; First three strings describe a short-option, long-option with optional
;;    ;; example argument description, and a description. All three are optional
;;    ;; and positional.
;;    ["-p" "--port PORT" "Port number"
;;     :default 80
;;     :parse-fn #(Integer/parseInt %)
;;     :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
;;    ["-H" "--hostname HOST" "Remote host"
;;     ;; Specify a string to output in the default column in the options summary
;;     ;; if the default value's string representation is very ugly
;;     :default-desc "localhost"
;;     ]
;;    ;; If no required argument description is given, the option is assumed to
;;    ;; be a boolean option defaulting to nil
;;    [nil "--detach" "Detach from controlling process"]
;;    ["-v" nil "Verbosity level; may be specified multiple times to increase value"
;;     ;; If no long-option is specified, an option :id must be given
;;     :id :verbosity
;;     :default 0
;;     ;; Use assoc-fn to create non-idempotent options
;;     :assoc-fn (fn [m k _] (update-in m [k] inc))]
;;    ["-h" "--help"]])

(def env (RuntimeEnvironment/getInstance))

(defn get-query-builder []
  (QueryBuilder.))

(defn get-query [query-builder query]
  (.build (.setDefs query-builder query)))

(defn get-context [query query-builder]
  (Context. query (.getQueries query-builder)))

(defn get-root-path []
  (.getSourceRootPath (RuntimeEnvironment/getInstance)))

(defn get-data-root-path []
  (.getDataRootFile (RuntimeEnvironment/getInstance)))

(defn get-index-reader [projects]
  (map #(DirectoryReader/open
         (FSDirectory/open
          (File. (str (get-data-root-path)
                      "/index" (.getPath %))))) projects))

(defn number-processor []
  (.availableProcessors (Runtime/getRuntime)))

(defn get-searcher [projects]
  (let [np (number-processor)
        nthread (+ 2 (* 2 np))
        searchable (into-array (get-index-reader projects))]
    (if (> np 1)
      (IndexSearcher.
       (MultiReader. searchable true)
       (Executors/newFixedThreadPool nthread))
      (IndexSearcher. searchable))))

;; (defn get-searcher [dir]
;;   (IndexSearcher.
;;    (DirectoryReader/open
;;     (FSDirectory/open (File. dir)))))

(defn get-sort []
  (Sort. (SortField. "date" SortField$Type/STRING true)))

(defn get-fdocs [searcher query]
  (.search searcher query 25 (get-sort)))

(defn get-total-hits [fdocs]
  (.totalHits fdocs))

(defn get-hits [fdocs]
  (.scoreDocs fdocs))

(defn get-docs [searcher hits]
  (map #(.doc searcher (.doc %)) hits))

(defn get-tags-field [doc]
  (.getField doc "tags"))

(defn get-taglist [tag]
  (map #(list (.line %) "///" (.text %) "///" (.symbol %) "///" (.type %)) tag))

(defn get-data [^Definitions definition]
  (let [^Definitions$Tag tag (.getTags definition)]
    (get-taglist tag)))

(defn print-hit [hit]
  (let [root (get-root-path)
        file (File. root (.getPath hit))]
    (str (.getAbsolutePath file) ":" (.getLineno hit) ": " (.getLine hit))))

(defn get-tag [context doc]
  (let [filename (.get doc "path")
        tag-field (get-tags-field doc)
        hit (ArrayList.)
        tags (if tag-field
               (Definitions/deserialize (.bytes (.binaryValue
                                                 tag-field)))
               nil)]
    (.getContext context
                 (InputStreamReader. (FileInputStream. (str
                                                        (get-root-path)
                                                        filename))) nil nil nil
                 filename tags false false hit)
    (map #(print-hit %) hit)))

(defn get-tags [context docs]
  (flatten (map #(get-tag context %) docs)))

(defn ^:static set-configuration [conf]
  (try
    (.readConfiguration (RuntimeEnvironment/getInstance) (File. conf))
    (catch Exception e (str "Failed to read config file: " (.getMessage e)))))

(defn print-tags [tags]
  (doseq [result tags]
    (println result)))

(defn projects? []
  (.hasProjects (RuntimeEnvironment/getInstance)))

(defn get-projects []
  (.getProjects (RuntimeEnvironment/getInstance)))

;;(search)
(defn search []
  (set-configuration
   "/Users/youngker/Projects/lp/.opengrok/configuration.xml")
  (let [root (get-root-path)
        query-builder (get-query-builder)
        query (get-query query-builder "test")
        context (get-context query query-builder)
        searcher (get-searcher
                  (if (projects?)
                    (get-projects)
                    (str (get-data-root-path) "/index/frameworks")))
        fdocs (get-fdocs searcher query)
        totalhits (get-total-hits fdocs)
        hits (get-hits fdocs)
        docs (get-docs searcher hits)
        tags (get-tags context docs)]
    (println "total hits: " totalhits)
    (println "total hits: " (count hits))
    (print-tags tags)))

(defn usage [options-summary]
  (->> ["Search -R <configuration.xml> [-d | -r | -p | -h | -f | -t] 'query string' .."
        ""
        "Usage: program-name [options] action"
        ""
        "Options:"
        options-summary
        ""
        "Please refer to the manual page for more information."]
       (string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    ;; Handle help and error conditions
    (println options "///" arguments "///" errors "///\n")
    (cond
      (:help options) (exit 0 (usage summary))
      (not= (count arguments) 1) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors))))

  (start-cli {:cmds {:search {:fn #(search args)
                              }
                     :s :search
                     }
              }))
