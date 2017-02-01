(defproject clj-opengrok "0.13.0"
  :description "command line interface for OpenGrok"
  :url "https://github.com/youngker/clj-opengrok"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[com.googlecode.json-simple/json-simple "1.1.1"]
                 [org.apache.ant/ant "1.9.6"]
                 [org.apache.bcel/bcel "6.0"]
                 [org.apache.lucene/lucene-analyzers-common "6.4.0"]
                 [org.apache.lucene/lucene-core "6.4.0"]
                 [org.apache.lucene/lucene-queryparser "6.4.0"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.opensolaris/opengrok "0.13-rc8"]
                 [org.opensolaris/opengrok.jrcs "0.13.0"]]
  :plugins [[lein-localrepo "0.5.3"]
            [elastic/lein-bin "0.3.6"]]
  :bin {:name "clj-opengrok"}
  :global-vars {*warn-on-reflection* true}
  :jvm-opts ["-Xmx2g"]
  :aot :all
  :main clj-opengrok.core)
