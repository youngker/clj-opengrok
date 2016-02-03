(ns clj-opengrok.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as string])
  (:gen-class)
  (:import (java.io StringReader File ByteArrayInputStream ObjectInputStream
                    InputStreamReader FileInputStream)
           (java.util ArrayList)
           (org.opensolaris.opengrok.history RepositoryFactory)
           (org.opensolaris.opengrok.analysis Definitions Definitions$Tag)
           (org.opensolaris.opengrok.search QueryBuilder Hit)
           (org.opensolaris.opengrok.search.context Context)
           (org.opensolaris.opengrok.configuration RuntimeEnvironment)
           (org.apache.lucene.analysis Analyzer TokenStream)
           (org.apache.lucene.analysis.standard StandardAnalyzer)
           (org.apache.lucene.document Document Field Field$Index Field$Store)
           (org.apache.lucene.index IndexWriter IndexReader Term
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
  [["-R" nil "<configuration.xml> Read configuration from the specified file"]
   ["-d" nil "Symbol Definitions"]
   ["-r" nil "Symbol References"]
   ["-p" nil "Path"]
   ["-h" nil "History"]
   ["-f" nil "Full Text"]
   ["-t" nil "Type"]
   ["-h" "--help" "Help"]])

(defn get-query-builder []
  (QueryBuilder.))

(defn get-query [query-builder query]
  (.build (.setDefs query-builder query)))

(defn get-context [query query-builder]
  (Context. query (.getQueries query-builder)))

(defn get-searcher [dir]
  (IndexSearcher.
   (DirectoryReader/open
    (FSDirectory/open (File. dir)))))

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

(defn get-root-path []
  (.getSourceRootPath (RuntimeEnvironment/getInstance)))

(defn get-data-root-path []
  (.getDataRootFile (RuntimeEnvironment/getInstance)))

(defn print-hit [hit]
  (let [root (get-root-path)
        file (File. root (.getPath hit))]
    (println (str (.getAbsolutePath file) ":" (.getLineno hit) ":") (.getLine hit))))

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
  (map #(get-tag context %) docs))

(defn ^:static set-configuration [conf]
  (try
    (.readConfiguration (RuntimeEnvironment/getInstance) (File. conf))
    (catch Exception e (str "Failed to read config file: " (.getMessage e)))))

;;(search)
(defn search []
  (let [root (get-root-path)
        query-builder (get-query-builder)
        query (get-query query-builder "google")
        context (get-context query query-builder)
        searcher (get-searcher
                  (str (get-data-root-path) "/index"))
        fdocs (get-fdocs searcher query)
        totalhits (get-total-hits fdocs)
        hits (get-hits fdocs)
        docs (get-docs searcher hits)
        tags (get-tags context docs)]
    (println "total hits: " totalhits)
    (println "total hits: " (count hits))
    tags))

(defn usage [options-summary]
  (->> ["Search -R <configuration.xml> [-d | -r | -p | -h | -f | -t] 'query string' .."
        ""
        "Usage: program-name [options] action"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "  start    Start a new server"
        "  stop     Stop an existing server"
        "  status   Print a server's status"
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
  ;; (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
  ;;   (cond
  ;;     (:help options) (exit 0 (usage summary))
  ;;     (not= (count arguments) 1) (exit 1 (usage summary))
  ;;     errors (exit 1 (error-msg errors)))

  ;;   ;; (case (first arguments)
  ;;   ;;   "start" (server/start! options)
  ;;   ;;   "stop" (server/stop! options)
  ;;   ;;   "status" (server/status! options)
  ;;   ;;   (exit 1 (usage summary)))

  (set-configuration
   "/path/to")
  (search)
  (println "done!")
  )
