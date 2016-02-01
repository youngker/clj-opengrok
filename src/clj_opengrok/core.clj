(ns clj-opengrok.core
  (:import (java.io StringReader File ByteArrayInputStream ObjectInputStream)
           (org.opensolaris.opengrok.analysis Definitions Definitions$Tag)
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

(def ^{:dynamic true} *version* Version/LUCENE_CURRENT)
(def ^{:dynamic true} *analyzer* (StandardAnalyzer. *version*))

(defn get-searcher [dir]
  (IndexSearcher. (DirectoryReader/open (FSDirectory/open (File. dir)))))

(defn get-sort []
  (Sort. (SortField. "date" SortField$Type/STRING true)))

(defn get-docs []
  (let [parser (QueryParser. *version* "full" *analyzer*)
        query (.parse parser "google")
        searcher (get-searcher
                  "/Users/youngker/Projects/lp/.opengrok/index/frameworks")
        fdocs (.search searcher query 25 (get-sort))
        totalhits (.totalHits fdocs)
        hits (.scoreDocs fdocs)]
    (map #(.doc searcher (.doc %)) hits)))

(defn get-data [^Definitions definition]
  (let [^Definitions$Tag tag (.getTags definition)]
    (.type (first tag))))

(defn get-tags [doc]
  (when-let [tags-field (.getField doc "tags")]
    (get-data (Definitions/deserialize (.bytes (.binaryValue tags-field))))))

(defn get-xxx []
  (map #(get-tags %) (get-docs)))
