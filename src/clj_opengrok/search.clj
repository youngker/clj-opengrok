(ns clj-opengrok.search
  (:import
   [java.io File InputStreamReader FileInputStream]
   [java.util List ArrayList]
   [java.util.concurrent Executors ExecutorService]
   org.apache.lucene.index.IndexableField
   org.apache.lucene.document.Document
   org.apache.lucene.store.FSDirectory
   org.opensolaris.opengrok.util.IOUtils
   [org.opensolaris.opengrok.search.context Context HistoryContext]
   [org.opensolaris.opengrok.configuration RuntimeEnvironment Project]
   [org.opensolaris.opengrok.search Hit QueryBuilder]
   [org.opensolaris.opengrok.analysis Definitions Definitions$Tag]
   [org.apache.lucene.index IndexReader MultiReader DirectoryReader]
   [org.apache.lucene.search TopScoreDocCollector IndexSearcher Sort SortField
    SortField$Type ScoreDoc TopFieldDocs Query]))

(def current-configuration (atom nil))
(def executor (atom nil))

(def env (RuntimeEnvironment/getInstance))

(defn get-query-builder []
  (QueryBuilder.))

(defn get-query [^QueryBuilder query-builder options]
  (.build
   (do (.setDefs query-builder (:def options))
       (.setRefs query-builder (:ref options))
       (.setPath query-builder (:path options))
       (.setHist query-builder (:hist options))
       (.setFreetext query-builder (:text options))
       (.setType query-builder (:type options)))))

(defn get-source-context [query ^QueryBuilder query-builder]
  (let [source-context (Context. query (.getQueries query-builder))]
    (when-not (.isEmpty source-context)
      source-context)))

(defn get-history-context [query]
  (let [history-context (HistoryContext. query)]
    (when-not (.isEmpty history-context)
      history-context)))

(defn get-root-path ^String []
  (.getSourceRootPath ^RuntimeEnvironment env))

(defn get-data-root-path []
  (.getDataRootFile ^RuntimeEnvironment env))

(defn get-index-reader [projects]
  (map #(DirectoryReader/open
         (FSDirectory/open
          (File. (str (get-data-root-path)
                      "/index" (.getPath ^Project %))))) projects))

(defn number-processor []
  (.availableProcessors (Runtime/getRuntime)))

(defn get-searcher [projects]
  (let [np (number-processor)
        nthread (+ 2 (* 2 np))
        searchable (into-array (get-index-reader projects))]
    (if (> np 1)
      (do
        (reset! executor (Executors/newFixedThreadPool nthread))
        (IndexSearcher.
         (MultiReader. ^MultiReader searchable true)
         ^ExecutorService @executor))
      (IndexSearcher. ^MultiReader searchable))))

(defn get-sort []
  (Sort. (SortField. "date" SortField$Type/STRING true)))

(defn get-hits-per-page []
  (.getHitsPerPage ^RuntimeEnvironment env))

(defn get-fdocs [page ^IndexSearcher searcher ^Query query]
  (.search searcher query (int (* page (get-hits-per-page))) ^Sort (get-sort)))

(defn get-total-hits [^TopFieldDocs fdocs]
  (.totalHits fdocs))

(defn get-hits [^TopFieldDocs fdocs]
  (.scoreDocs fdocs))

(defn get-page-hits [page hits]
  (let [hpp (get-hits-per-page)
        n (* (dec page) hpp)]
    (->> hits
         (drop n)
         (take hpp))))

(defn get-docs [^IndexSearcher searcher hits]
  (map #(.doc searcher (.doc ^ScoreDoc %)) hits))

(defn get-tags-field [^Document doc]
  (.getField doc "tags"))

(defn print-hit [^Hit hit]
  (let [root (get-root-path)
        file (File. root (.getPath hit))
        line (.getLine hit)]
    (str (.getAbsolutePath file) ":" (.getLineno hit) ": "
         (if (> (count line) 200)
           (subs line 0 200)
           line))))

(defn get-tag [^Context source-context ^HistoryContext history-context
               ^Document doc]
  (let [filename (.get doc "path")
        tag-field (get-tags-field doc)
        hit (ArrayList.)
        tags (when tag-field
               (Definitions/deserialize
                 (.bytes (.binaryValue ^IndexableField tag-field))))]

    (when (and source-context (= "p" (.get doc "t")))
      (.getContext source-context
                   (InputStreamReader.
                    (FileInputStream. (str
                                       (get-root-path)
                                       filename))) nil nil nil
                   filename tags false false hit))

    (when history-context
      (.getContext history-context
                   (str (get-root-path) filename) filename hit))

    (when-not (and source-context history-context)
      (.add hit (Hit. filename "..." "1" false true)))

    (map #(print-hit %) hit)))

(defn get-tags [source-context history-context docs]
  (remove nil? (flatten
                (map #(get-tag source-context history-context %) docs))))

(defn set-configuration [^String conf]
  (.readConfiguration ^RuntimeEnvironment env (File. conf)))

(defn print-tags [tags]
  (doseq [result tags]
    (println result)))

(defn print-page [page totalhits]
  (let [len (take 10 (drop (* 10 (int (/ (dec page) 10)))
                           (range 1 (inc (/ totalhits (get-hits-per-page))))))]
    (doseq [num len]
      (if (= num page)
        (print (str " [" num "]"))
        (print "" num))
      (when (not= num (last len))
        (print " |"))))
  (println ""))

(defn projects? []
  (.hasProjects ^RuntimeEnvironment env))

(defn get-projects []
  (.getProjects ^RuntimeEnvironment env))

(defn get-page [page]
  (str (inc (* (dec page) (get-hits-per-page))) " - "
       (* page (get-hits-per-page))))

(defn destroy-resource [^IndexSearcher searcher]
  (IOUtils/close ^IndexReader (.getIndexReader searcher))
  (.shutdown ^ExecutorService @executor))

(defn search [page options]
  (when-not (= @current-configuration (:conf options))
    (reset! current-configuration (:conf options))
    (set-configuration (:conf options)))

  (let [root (get-root-path)
        query-builder (get-query-builder)
        query (get-query query-builder options)
        source-context (get-source-context query query-builder)
        history-context (get-history-context query)
        searcher (get-searcher
                  (if (projects?)
                    (get-projects)
                    (str (get-data-root-path) "/index")))
        fdocs (get-fdocs page searcher query)
        totalhits (get-total-hits fdocs)
        hits (get-hits fdocs)
        hits (get-page-hits page hits)
        docs (get-docs searcher hits)
        tags (get-tags source-context history-context docs)]
    (println "Results::" (get-page page) " of " totalhits)
    (print-page page totalhits)
    (print-tags tags)
    (print-page page totalhits)
    (destroy-resource searcher)))
