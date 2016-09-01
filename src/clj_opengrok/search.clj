(ns clj-opengrok.search
  (:import
   (java.io File FileInputStream InputStreamReader)
   (java.util ArrayList List)
   (java.util.concurrent Executors ExecutorService)
   (org.apache.lucene.document Document)
   (org.apache.lucene.index DirectoryReader IndexReader MultiReader)
   (org.apache.lucene.search IndexSearcher Query ScoreDoc Sort SortField
                             SortField$Type TopFieldDocs)
   (org.apache.lucene.store FSDirectory)
   (org.opensolaris.opengrok.configuration Project RuntimeEnvironment)
   (org.opensolaris.opengrok.search Hit QueryBuilder)
   (org.opensolaris.opengrok.search.context Context HistoryContext)
   (org.opensolaris.opengrok.util IOUtils)))

(def ^{:private true} ^RuntimeEnvironment env (RuntimeEnvironment/getInstance))

(def ^{:private true} executor (atom nil))

(defn- read-configuration [^String conf]
  (.readConfiguration env (File. conf)))

(defn- has-projects? []
  (.hasProjects env))

(defn- get-projects []
  (.getProjects env))

(defn- number-processor []
  (.availableProcessors (Runtime/getRuntime)))

(defn- root-path ^String []
  (.getSourceRootPath env))

(defn- data-root-path ^String []
  (.getDataRootFile env))

(defn- hits-per-page []
  (.getHitsPerPage env))

(defn- get-sort [opts]
  (let [sort (:sort opts)]
    (cond
      (= sort "date") (Sort. (SortField. "date" SortField$Type/STRING true))
      (= sort "path") (Sort. (SortField. "fullpath" SortField$Type/STRING))
      :else (Sort/RELEVANCE))))

(defn- query-builder [opts]
  (let [qb (QueryBuilder.)]
    (doto qb
      (.setDefs (:def opts))
      (.setRefs (:ref opts))
      (.setPath (:path opts))
      (.setHist (:hist opts))
      (.setFreetext (:text opts))
      (.setType (:type opts)))
    qb))

(defn- subreaders [projects]
  (map #(DirectoryReader/open
         (FSDirectory/open
          (File. (str (data-root-path)
                      "/index" (.getPath ^Project %))))) projects))

(defn- search-multi-database [projects]
  (let [np (number-processor)
        nthread (+ 2 (* 2 np))
        subreaders (into-array (subreaders projects))]
    (if (> np 1)
      (do
        (reset! executor (Executors/newFixedThreadPool nthread))
        (IndexSearcher. (MultiReader. ^MultiReader subreaders true)
                        ^ExecutorService @executor))
      (IndexSearcher. ^MultiReader subreaders))))

(defn- search-single-database []
  (IndexSearcher. (DirectoryReader/open
                   (FSDirectory/open
                    (File. (str (data-root-path) "/index"))))))

(defn- searcher []
  (if (has-projects?)
    (search-multi-database (get-projects))
    (search-single-database)))

(defn- fdocs [^IndexSearcher searcher ^Query query opts]
  (.search searcher query nil
           (* (:page opts) (hits-per-page)) (get-sort opts)))

(defn- total-page [^TopFieldDocs fdocs]
  (let [total-hits (.totalHits fdocs)]
    (inc (int (/ total-hits (hits-per-page))))))

(defn- page-info [opts]
  (when-not (:quiet opts)
    (println (format "clj-opengrok> %s/%s" (:page opts) (:total-page opts)))))

(defn- print-hit [^Hit hit]
  (let [file (.getAbsolutePath (File. (root-path) (.getPath hit)))
        line-num (.getLineno hit)
        line (.getLine hit)]
    (println (str file ":" line-num ": "
                  (if (> (count line) 200) (subs line 0 200) line)))))

(defn- hit [^Document doc ^Query query ^QueryBuilder qb]
  (let [scontext (Context. query (.getQueries qb))
        hcontext (HistoryContext. query)
        file (.get doc "path")
        hit (ArrayList.)]
    (cond
      (not (.isEmpty scontext))
      (when (= "p" (.get doc "t"))
        (.getContext scontext
                     (InputStreamReader.
                      (FileInputStream. (str (root-path) file)))
                     nil nil nil file nil false false hit))

      (not (.isEmpty hcontext))
      (.getContext hcontext
                   (str (root-path) file) file hit)

      :else
      (.add hit (Hit. file "..." "1" false true)))

    (doseq [h hit]
      (print-hit h))))

(defn- close [^IndexSearcher searcher]
  (IOUtils/close ^IndexReader (.getIndexReader searcher))
  (when @executor
    (.shutdown ^ExecutorService @executor)))

(defn- document [^IndexSearcher searcher ^TopFieldDocs fdocs
                 ^Query query ^QueryBuilder qb opts]
  (let [hpp (hits-per-page)
        n (* (dec (:page opts)) hpp)
        scoredocs (take hpp (drop n (.scoreDocs fdocs)))]
    (doseq [scoredoc scoredocs]
      (hit (.doc searcher (.doc ^ScoreDoc scoredoc)) query qb))
    (close searcher)))

(defn- search-page [opts]
  (let [qb ^QueryBuilder (query-builder opts)
        query (.build qb)
        searcher (searcher)
        fdocs (fdocs searcher query opts)
        total-page (total-page fdocs)]
    (page-info (assoc opts :total-page total-page))
    (document searcher fdocs query qb opts)
    {:total-page total-page}))

(defn search [opts]
  (read-configuration (:conf opts))
  (loop [page 1]
    (when (< page (:total-page (search-page (assoc opts :page page))))
      (recur (inc page)))))
