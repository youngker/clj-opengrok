(defproject clj-opengrok "0.4.0"
  :description "command line interface for OpenGrok"
  :url "https://github.com/youngker/clj-opengrok"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.apache.ant/ant "1.8.4"]
                 [org.apache.bcel/bcel "5.2"]
                 [org.apache.commons/jrcs "0.12.1.5"]
                 [org.apache.lucene/lucene-analyzers-common "4.7.0"]
                 [org.apache.lucene/lucene-core "4.7.0"]
                 [org.apache.lucene/lucene-highlighter "4.7.0"]
                 [org.apache.lucene/lucene-queryparser "4.7.0"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.opensolaris/opengrok "0.12.1.5"]]
  :plugins [[elastic/lein-bin "0.3.6"]]
  :bin {:name "clj-opengrok"}
  :global-vars {*warn-on-reflection* true}
  :jvm-opts ["-Xmx2g"]
  :aot :all
  :main clj-opengrok.core
  )
