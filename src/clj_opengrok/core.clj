(ns clj-opengrok.core
  (:import (java.io StringReader File ByteArrayInputStream ObjectInputStream
                    InputStreamReader FileInputStream)
           (org.opensolaris.opengrok.analysis Definitions Definitions$Tag)
           (org.opensolaris.opengrok.search QueryBuilder Hit)
           (org.opensolaris.opengrok.search.context Context)
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
  (.search searcher query 5 (get-sort)))

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

(defn get-tag [context doc]
  (println "get-tag")
  (println (.get doc "t"))
  (let [filename (.get doc "path")
        tag-field (get-tags-field doc)
        tag (if tag-field
              (Definitions/deserialize (.bytes (.binaryValue
                                                tag-field)))
              nil)
        hit (list (Hit.))]
    (.getContext context
                 (InputStreamReader. (FileInputStream. (str
                                                        "/path/to"
                                                        filename))) nil nil nil
                 filename tag false false hit)
    (println "line num"(.getLineno (first hit)))
    ))

(defn get-tags [context docs]
  (println "get-tags")
  (map #(get-tag context %) docs))

(defn search []
  (let [query-builder (get-query-builder)
        query (get-query query-builder "test")
        context (get-context query query-builder)
        searcher (get-searcher
                  "/path/to")
        fdocs (get-fdocs searcher query)
        totalhits (get-total-hits fdocs)
        hits (get-hits fdocs)
        docs (get-docs searcher hits)
        tags (get-tags context docs)]
    (println "total hits: " totalhits)
    ;;    (println tags)
    ))
