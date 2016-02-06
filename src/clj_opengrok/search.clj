(ns clj-opengrok.search
  (:import
   [java.io File InputStreamReader FileInputStream]
   java.util.ArrayList
   java.util.concurrent.Executors
   org.apache.lucene.store.FSDirectory
   org.opensolaris.opengrok.util.IOUtils
   org.opensolaris.opengrok.search.context.Context
   org.opensolaris.opengrok.configuration.RuntimeEnvironment
   [org.opensolaris.opengrok.search Hit QueryBuilder]
   [org.opensolaris.opengrok.analysis Definitions Definitions$Tag]
   [org.apache.lucene.index IndexReader MultiReader DirectoryReader]
   [org.apache.lucene.search TopScoreDocCollector IndexSearcher Sort SortField
    SortField$Type]))

(def current-configuration (atom nil))

(def env (RuntimeEnvironment/getInstance))

(defn get-query-builder []
  (QueryBuilder.))

(defn get-query [query-builder options]
  (.build
   (do (.setDefs query-builder (:def options))
       (.setRefs query-builder (:ref options))
       (.setPath query-builder (:path options))
       (.setHist query-builder (:hist options))
       (.setFreetext query-builder (:text options))
       (.setType query-builder (:type options)))))

(defn get-context [query query-builder]
  (Context. query (.getQueries query-builder)))

(defn get-root-path []
  (.getSourceRootPath env))

(defn get-data-root-path []
  (.getDataRootFile env))

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

(defn get-sort []
  (Sort. (SortField. "date" SortField$Type/STRING true)))

(defn get-hits-per-page []
  (.getHitsPerPage env))

(defn get-fdocs [page searcher query]
  (.search searcher query (* page (get-hits-per-page)) (get-sort)))

(defn get-total-hits [fdocs]
  (.totalHits fdocs))

(defn get-hits [fdocs]
  (.scoreDocs fdocs))

(defn get-page-hits [page hits]
  (let [hpp (get-hits-per-page)
        n (* (dec page) hpp)]
    (->> hits
         (drop n)
         (take hpp))))

(defn get-docs [searcher hits]
  (map #(.doc searcher (.doc %)) hits))

(defn get-tags-field [doc]
  (.getField doc "tags"))

(defn print-hit [hit]
  (let [root (get-root-path)
        file (File. root (.getPath hit))]
    (str (.getAbsolutePath file) ":" (.getLineno hit) ": " (.getLine hit))))

(defn get-tag [context doc]
  (when (= "p" (.get doc "t"))
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
      (map #(print-hit %) hit))))

(defn get-tags [context docs]
  (flatten (map #(get-tag context %) docs)))

(defn set-configuration [conf]
  (.readConfiguration env (File. conf)))

(defn print-tags [tags]
  (doseq [result tags]
    (println result)))

(defn print-page [page totalhits]
  (doseq [num (take 10 (drop (* 10 (int (/ (dec page) 10)))
                             (range 1 (inc (/ totalhits 25)))))]
    (if (= num page)
      (print (str "[" num "]") "| ")
      (print num "| ")))
  (println ""))

(defn projects? []
  (.hasProjects env))

(defn get-projects []
  (.getProjects env))

(defn get-page [page]
  (str (inc (* (dec page) (get-hits-per-page))) " - "
       (* page (get-hits-per-page))))

(defn destroy-resource [searcher]
  (IOUtils/close (.getIndexReader searcher)))

(defn search [page options]
  (when-not (= @current-configuration (:conf options))
    (reset! current-configuration (:conf options))
    (set-configuration (:conf options)))

  (let [root (get-root-path)
        query-builder (get-query-builder)
        query (get-query query-builder options)
        context (get-context query query-builder)
        searcher (get-searcher
                  (if (projects?)
                    (get-projects)
                    (str (get-data-root-path) "/index")))
        fdocs (get-fdocs page searcher query)
        totalhits (get-total-hits fdocs)
        hits (get-hits fdocs)
        hits (get-page-hits page hits)
        docs (get-docs searcher hits)
        tags (get-tags context docs)]
    (print-page page totalhits)
    (print-tags tags)
    (print-page page totalhits)
    (destroy-resource searcher)
    (println "Results " (get-page page) " of " totalhits)))
