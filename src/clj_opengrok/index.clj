(ns clj-opengrok.index
  (:require
   [clojure.java.shell :as shell]
   [clojure.string :refer [split trim-newline]])
  (:import
   (java.io File)
   (org.opensolaris.opengrok.configuration Configuration RuntimeEnvironment)
   (org.opensolaris.opengrok.index Indexer)))

(def ^{:private true} into-vector (comp vec flatten vector))

(defn- ctags [cmd]
  (trim-newline (:out (shell/sh cmd "ctags"))))

(defn- os []
  (let [^String os (get (System/getProperties) "os.name")]
    (if (.contains os "Windows") (ctags "where") (ctags "which"))))

(defn- conf ^String [dir]
  (str dir "/.opengrok/configuration.xml"))

(defn- get-args [opts]
  (let [ctags (os)
        src-root (:src-root opts)
        conf (conf src-root)
        data-root (str src-root "/.opengrok")]
    (when ctags
      (shutdown-agents))
    (into-vector "-r" "on"
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
                 (interleave (repeat "-i")
                             (split (:ignore opts) #":")))))

(defn index [opts]
  (let [configuration (new Configuration)
        file (File. (conf (:src-root opts)))]
    (.setHistoryCache configuration true)
    (.setConfiguration (RuntimeEnvironment/getInstance) configuration)
    (.mkdirs (.getParentFile file))
    (.createNewFile file)
    (.writeConfiguration (RuntimeEnvironment/getInstance) file)
    (Indexer/main (into-array (get-args opts)))
    (println "\n Indexing complete.")))
