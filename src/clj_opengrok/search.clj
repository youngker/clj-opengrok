(ns clj-opengrok.search
  (:require
   [clojure.tools.logging :as log])
  (:import
   (java.io File FileInputStream InputStreamReader IOException)
   (java.util ArrayList TreeSet List)
   (org.apache.lucene.document Document)
   (org.apache.lucene.search IndexSearcher Query ScoreDoc Sort SortField
                             SortField$Type TopFieldDocs)
   (org.opengrok.indexer.configuration Project RuntimeEnvironment SuperIndexSearcher)
   (org.opengrok.indexer.search Hit QueryBuilder)
   (org.opengrok.indexer.search.context Context HistoryContext)))

(def ^{:private true} ^RuntimeEnvironment env (RuntimeEnvironment/getInstance))

(defn- read-configuration [^String conf]
  (.readConfiguration env (File. conf)))

(defn- has-projects? []
  (.hasProjects env))

(defn- get-projects []
  (.getProjectList env))

(defn- root-path ^String []
  (.getSourceRootPath env))

(defn- hits-per-page []
  (.getHitsPerPage env))

(defn- get-single-index-searcher [searcher-list]
  (let [searcher (.getSuperIndexSearcher env "")]
    (.add ^ArrayList searcher-list searcher)
    searcher))

(defn- get-multiple-index-searcher [project-names searcher-list]
  (.newSearcher (.getIndexSearcherFactory env)
                (.getMultiReader env project-names searcher-list)))

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

(defn- search-multi-database [projects searcher-list]
  (let [project-names (TreeSet.)]
    (doseq [^Project project ^List projects]
      (.add project-names (.getName ^Project project)));
    (get-multiple-index-searcher project-names searcher-list)))

(defn- searcher [searcher-list]
  (if (has-projects?)
    (search-multi-database (get-projects) searcher-list)
    (get-single-index-searcher searcher-list)))

(defn- fdocs [^IndexSearcher searcher ^Query query opts]
  (.search searcher query
           (* (:page opts) (hits-per-page)) (get-sort opts)))

(defn- total-page [^TopFieldDocs fdocs]
  (let [total-hits (.value (.totalHits fdocs))]
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
  (let [scontext (Context. query qb)
        hcontext (HistoryContext. query)
        file (.get doc "path")
        hit (ArrayList.)]
    (cond
      (not (.isEmpty scontext))
      (try
        (when (= "p" (.get doc "t"))
          (.getContext scontext
                       (InputStreamReader.
                        (FileInputStream. (str (root-path) file)))
                       nil nil nil file nil false false hit))
        (catch java.io.FileNotFoundException e
          (log/warn (format "%s is not found." (str (root-path) file)))))

      (not (.isEmpty hcontext))
      (.getContext hcontext
                   (str (root-path) file) file hit)

      :else
      (.add hit (Hit. file "..." "1" false true)))

    (doseq [h hit]
      (print-hit h))))

(defn- destory [searcher-list]
  (doseq [^SuperIndexSearcher searcher searcher-list]
    (try
      (.release searcher)
      (catch IOException e
        (log/warn (format "cannot release indexSearcher : %s" e))))))

(defn- document [^IndexSearcher searcher ^TopFieldDocs fdocs
                 ^Query query ^QueryBuilder qb opts]
  (let [hpp (hits-per-page)
        n (* (dec (:page opts)) hpp)
        scoredocs (take hpp (drop n (.scoreDocs fdocs)))]
    (doseq [scoredoc scoredocs]
      (hit (.doc searcher (.doc ^ScoreDoc scoredoc)) query qb))))

(defn- search-page [opts]
  (let [qb ^QueryBuilder (query-builder opts)
        query (.build qb)
        searcher-list (ArrayList.)
        searcher (searcher searcher-list)
        fdocs (fdocs searcher query opts)
        total-page (total-page fdocs)]
    (page-info (assoc opts :total-page total-page))
    (document searcher fdocs query qb opts)
    (destory searcher-list)
    {:tp total-page :th (.value (.totalHits ^TopFieldDocs fdocs))}))

(defn search [opts]
  (read-configuration (:conf opts))
  (loop [p 1]
    (when-let [{:keys [tp th]} (search-page (assoc opts :page p))]
      (if (>= p tp)
        (if (zero? th)
          (println "\n No match found.")
          (println "\n Searching complete."))
        (recur (inc p))))))
