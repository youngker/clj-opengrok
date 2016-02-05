(ns clj-opengrok.search
  (:import
   [java.io File InputStreamReader FileInputStream]
   java.util.ArrayList
   java.util.concurrent.Executors
   org.apache.lucene.store.FSDirectory
   org.opensolaris.opengrok.search.context.Context
   org.opensolaris.opengrok.configuration.RuntimeEnvironment
   [org.opensolaris.opengrok.search Hit QueryBuilder]
   [org.opensolaris.opengrok.analysis Definitions Definitions$Tag]
   [org.apache.lucene.index IndexReader MultiReader DirectoryReader]
   [org.apache.lucene.search IndexSearcher Sort SortField SortField$Type]))

(def env (RuntimeEnvironment/getInstance))

(defn get-query-builder []
  (QueryBuilder.))

(defn get-query [query-builder options arguments]
  (.build (cond
            (:def options) (.setDefs query-builder (second arguments))
            (:ref options) (.setRefs query-builder (second arguments))
            (:path options) (.setPath query-builder (second arguments))
            (:hist options) (.setHist query-builder (second arguments))
            (:text options) (.setFreetext query-builder (second arguments))
            (:type options) (.setType query-builder (second arguments)))))

;; (defn get-query [query-builder options arguments]
;;   (.build (.setDefs query-builder "WindowManagerService")))

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
    (.readConfiguration env (File. conf))
    (catch Exception e (str "Failed to read config file: " (.getMessage e)))))

(defn print-tags [tags]
  (doseq [result tags]
    (println result)))

(defn projects? []
  (.hasProjects env))

(defn get-projects []
  (.getProjects env))

(defn search [options arguments]
  (set-configuration (first arguments))
  (let [root (get-root-path)
        query-builder (get-query-builder)
        query (get-query query-builder options arguments)
        context (get-context query query-builder)
        searcher (get-searcher
                  (if (projects?)
                    (get-projects)
                    (str (get-data-root-path) "/index")))
        fdocs (get-fdocs searcher query)
        totalhits (get-total-hits fdocs)
        hits (get-hits fdocs)
        docs (get-docs searcher hits)
        tags (get-tags context docs)]
    (println "total hits: " totalhits)
    (println "total hits: " (count hits))
    (print-tags tags)))
