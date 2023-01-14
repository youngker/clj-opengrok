(defproject clj-opengrok "1.7.42"
  :description "command line interface for OpenGrok"
  :url "https://github.com/youngker/clj-opengrok"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[com.cronutils/cron-utils "9.1.6"]
                 [com.fasterxml.jackson.dataformat/jackson-dataformat-smile "2.14.1"]
                 [io.micrometer/micrometer-registry-prometheus "1.8.2"]
                 [io.micrometer/micrometer-registry-statsd "1.8.2"]
                 [org.apache.ant/ant "1.10.11"]
                 [org.apache.bcel/bcel "6.5.0"]
                 [org.apache.commons/commons-lang3 "3.12.0"]
                 [org.apache.lucene/lucene-analyzers-common "8.11.0"]
                 [org.apache.lucene/lucene-core "8.11.0"]
                 [org.apache.lucene/lucene-highlighter "8.11.0"]
                 [org.apache.lucene/lucene-queryparser "8.11.0"]
                 [org.clojure/clojure "1.11.1"]
                 [org.clojure/tools.cli "1.0.214"]
                 [org.clojure/tools.logging "1.2.4"]
                 [org.eclipse.jgit/org.eclipse.jgit "5.12.0.202106070339-r"]
                 [org.jvnet.hudson/org.suigeneris.jrcs.diff "0.4.2"]
                 [org.jvnet.hudson/org.suigeneris.jrcs.rcs "0.4.2"]
                 [org.opengrok/opengrok "1.7.42"]
                 [org.slf4j/slf4j-nop "1.7.30"]]
  :plugins [[lein-localrepo "0.5.3"]
            [elastic/lein-bin "0.3.6"]]
  :bin {:name "clj-opengrok"}
  :global-vars {*warn-on-reflection* true}
  :jvm-opts ["-Xmx2g" "-Djava.util.logging.config.file=logging.properties"]
  :aot :all
  :main clj-opengrok.core)
