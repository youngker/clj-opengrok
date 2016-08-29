(ns clj-opengrok.search
  (:import
   (java.io File FileInputStream InputStreamReader)
   (java.util ArrayList List)
   (java.util.concurrent Executors ExecutorService)
   (org.apache.lucene.document Document)
   (org.apache.lucene.index DirectoryReader IndexReader MultiReader)
   (org.apache.lucene.index IndexableField)
   (org.apache.lucene.search IndexSearcher Query ScoreDoc Sort SortField
                             SortField$Type TopFieldDocs)
   (org.apache.lucene.store FSDirectory)
   (org.opensolaris.opengrok.analysis Definitions)
   (org.opensolaris.opengrok.configuration Project RuntimeEnvironment)
   (org.opensolaris.opengrok.search Hit QueryBuilder)
   (org.opensolaris.opengrok.search.context Context HistoryContext)
   (org.opensolaris.opengrok.util IOUtils)))

(def ^{:private true} ^RuntimeEnvironment env (RuntimeEnvironment/getInstance))

(def ^{:private true} executor (atom nil))

(defn- read-configuration [^String conf]
  (.readConfiguration env (File. conf)))

(defn- make-query-builder [opts]
  (assoc opts :query-builder (QueryBuilder.)))

(defn- make-query [opts]
  (let [query-builder ^QueryBuilder (:query-builder opts)]
    (assoc opts :query (.build
                        (doto query-builder
                          (.setDefs (:def opts))
                          (.setRefs (:ref opts))
                          (.setPath (:path opts))
                          (.setHist (:hist opts))
                          (.setFreetext (:text opts))
                          (.setType (:type opts)))))))

(defn- make-source-context [opts]
  (assoc opts :source-context
         (Context. (:query opts)
                   (.getQueries ^QueryBuilder (:query-builder opts)))))

(defn- make-history-context [opts]
  (assoc opts :history-context (HistoryContext. (:query opts))))

(defn- has-projects? []
  (.hasProjects env))

(defn- get-projects []
  (.getProjects env))

(defn- number-processor []
  (.availableProcessors (Runtime/getRuntime)))

(defn- get-root-path ^String []
  (.getSourceRootPath env))

(defn- get-data-root-path ^String []
  (.getDataRootFile env))

(defn- get-index-reader [projects]
  (map #(DirectoryReader/open
         (FSDirectory/open
          (File. (str (get-data-root-path)
                      "/index" (.getPath ^Project %))))) projects))

(defn- get-searcher-in-projects [projects opts]
  (let [np (number-processor)
        nthread (+ 2 (* 2 np))
        searchable (into-array (get-index-reader projects))]
    (if (> np 1)
      (do
        (reset! executor (Executors/newFixedThreadPool nthread))
        (assoc opts :searcher (IndexSearcher.
                               (MultiReader. ^MultiReader searchable true)
                               ^ExecutorService @executor)))
      (assoc opts :searcher (IndexSearcher. ^MultiReader searchable)))))

(defn- get-searcher-no-projects [opts]
  (assoc opts
         :searcher (IndexSearcher. (DirectoryReader/open
                                    (FSDirectory/open
                                     (File.
                                      (str (get-data-root-path) "/index")))))))

(defn- get-searcher [opts]
  (if (has-projects?)
    (get-searcher-in-projects (get-projects) opts)
    (get-searcher-no-projects opts)))

(defn- get-sort [opts]
  (let [sort (:sort opts)]
    (cond
      (= sort "date") (Sort. (SortField. "date" SortField$Type/STRING true))
      (= sort "path") (Sort. (SortField. "fullpath" SortField$Type/STRING))
      :else (Sort/RELEVANCE))))

(defn- get-hits-per-page []
  (.getHitsPerPage env))

(defn- get-fdocs [opts]
  (assoc opts :fdocs
         (.search ^IndexSearcher (:searcher opts) ^Query (:query opts)
                  (int (* (:page opts) (get-hits-per-page))) ^Sort (get-sort opts))))

(defn- get-total-hits [opts]
  (let [total-hits (.totalHits ^TopFieldDocs (:fdocs opts))
        total-page (inc (int (/ total-hits (get-hits-per-page))))]
    (assoc opts :total-hits total-hits :total-page total-page)))

(defn- get-hits [opts]
  (assoc opts :hits (.scoreDocs ^TopFieldDocs (:fdocs opts))))

(defn- get-page-hits [opts]
  (let [hpp (get-hits-per-page)
        n (* (dec (:page opts)) hpp)]
    (assoc opts :page-hits (->> (:hits opts)
                                (drop n)
                                (take hpp)))))

(defn- get-docs [opts]
  (assoc opts :docs
         (map #(.doc ^IndexSearcher (:searcher opts) (.doc ^ScoreDoc %))
              (:page-hits opts))))

(defn- get-tags-field [^Document doc]
  (.getField doc "tags"))

(defn- print-hit [^Hit hit]
  (let [root (get-root-path)
        file (File. root (.getPath hit))
        line (.getLine hit)]
    (str (.getAbsolutePath file) ":" (.getLineno hit) ": "
         (if (> (count line) 200)
           (subs line 0 200)
           line))))

(defn- get-tag [^Context source-context
                ^HistoryContext history-context
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

    (when (and (.isEmpty source-context) (.isEmpty history-context))
      (.add hit (Hit. filename "..." "1" false true)))

    (map #(print-hit %) hit)))

(defn- get-tags [opts]
  (assoc opts :tags (remove nil? (flatten
                                  (map #(get-tag (:source-context opts)
                                                 (:history-context opts) %)
                                       (:docs opts))))))

(defn- close [opts]
  (IOUtils/close ^IndexReader (.getIndexReader ^IndexSearcher (:searcher opts)))
  (when @executor
    (.shutdown ^ExecutorService @executor)))

(defn- get-tags-list [opts]
  (->> opts
       make-query-builder
       make-query
       make-source-context
       make-history-context
       get-searcher
       get-fdocs
       get-total-hits
       get-hits
       get-page-hits
       get-docs
       get-tags))

(defn- print-page-info [opts]
  (when-not (:quiet opts)
    (println (format "clj-opengrok> %s/%s" (:page opts) (:total-page opts)))))

(defn- search-page [opts]
  (let [opts (get-tags-list opts)]
    (print-page-info opts)
    (doseq [result (:tags opts)]
      (println result))
    (close opts)))

(defn search [opts]
  (read-configuration (:conf opts))
  (let [opts (get-tags-list (assoc opts :page 1))
        total-page (:total-page opts)]
    (print-page-info opts)
    (doseq [result (:tags opts)]
      (println result))
    (close opts)
    (doseq [page (range 2 (inc total-page))]
      (search-page (assoc opts :page page)))))
