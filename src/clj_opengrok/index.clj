(ns clj-opengrok.index
  (:require
   [clojure.java.shell :as shell]
   [clojure.string :refer [trim-newline]])
  (:import
   (java.io File)
   (org.opensolaris.opengrok.configuration Configuration RuntimeEnvironment)
   (org.opensolaris.opengrok.index Indexer)))

(def ^{:private true} into-vector (comp vec flatten vector))

(def ^{:private true} ignore-list
  (list ".opengrok" "out" "*.so" "*.a" "*.o" "*.gz" "*.bz2" "*.jar" "*.zip"
        "*.class" "*.elc"))

(defn- cmd [w]
  (trim-newline (:out (shell/sh w "ctags"))))

(defn- ctags-path []
  (let [^String os (get (System/getProperties) "os.name")]
    (if (.contains os "Windows") (cmd "where") (cmd "which"))))

(defn- conf ^String [dir]
  (str dir "/.opengrok/configuration.xml"))

(defn- get-args [opts]
  (let [ctags (ctags-path)
        src-root (:src-root opts)
        conf (conf src-root)
        data-root (str src-root "/.opengrok")]
    (shutdown-agents)
    (into-vector "-Xmx2048m"
                 "-r" "on"
                 "-c" ctags
                 "-a" "on"
                 "-W" conf
                 "-R" conf
                 "-S"
                 "-s" src-root
                 "-d" data-root
                 "-H" "-q"
                 "-e"
                 (if (:project opts) "-P" [])
                 (interleave (repeat "-i") ignore-list))))

(defn index [opts]
  (let [configuration (new Configuration)
        file (File. (conf (:src-root opts)))]
    (.setHistoryCache configuration true)
    (.setConfiguration (RuntimeEnvironment/getInstance) configuration)
    (.mkdirs (.getParentFile file))
    (.createNewFile file)
    (.writeConfiguration (RuntimeEnvironment/getInstance) file)
    (Indexer/main (into-array (get-args opts)))))
