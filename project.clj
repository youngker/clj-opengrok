(defproject clj-opengrok "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [lein-localrepo "0.5.3"]
                 [cli4clj "1.0.0"]
                 [org.clojure/tools.cli "0.3.3"]
                 [org.opensolaris/opengrok "0.12.1.5"]
                 [org.apache.commons/jrcs "0.12.1.5"]
                 [org.apache.lucene/lucene-core "4.7.0"]
                 [org.apache.lucene/lucene-queryparser "4.7.0"]
                 [org.apache.lucene/lucene-analyzers-common "4.7.0"]
                 [org.apache.lucene/lucene-highlighter "4.7.0"]]
  :aot [clj-opengrok.core]
  :main clj-opengrok.core)
