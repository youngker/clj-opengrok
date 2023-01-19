(ns clj-opengrok.index
  (:require
   [clojure.java.shell :as shell]
   [clojure.string :refer [split trim-newline]])
  (:import
   (java.io File)
   (org.opengrok.indexer.configuration Configuration RuntimeEnvironment)
   (org.opengrok.indexer.index Indexer)))

(def ^{:private true} into-vector (comp vec flatten vector))
(def ^{:private true} ^RuntimeEnvironment env (RuntimeEnvironment/getInstance))

(defn- ctags [cmd]
  (trim-newline (:out (shell/sh cmd "ctags"))))

(defn- get-ctag-path-in-system []
  (let [^String os (get (System/getProperties) "os.name")]
    (if (.contains os "Windows") (ctags "where") (ctags "which"))))

(defn- conf ^String [dir]
  (str dir "/.opengrok/configuration.xml"))

(defn- get-args [opts]
  (let [ctags (get-ctag-path-in-system)
        src-root (:src-root opts)
        data-root (str src-root "/.opengrok")
        conf (conf src-root)]
    (into-vector
     "-W" conf
     "-c" ctags
     "-d" data-root
     "-s" src-root
     "-G" "-H" "-S" "-e" "-q"
     (and (:project opts) "-P")
     (and (:ignore opts)
          (interleave (repeat "-i") (split (:ignore opts) #":"))))))

(defn index [opts]
  (let [configuration (Configuration.)
        file (File. (conf (:src-root opts)))]
    (.setHistoryCache configuration true)
    (.setConfiguration env configuration)
    (.mkdirs (.getParentFile file))
    (.createNewFile file)
    (.writeConfiguration env file)
    (Indexer/main (into-array (filter identity (get-args opts))))
    (println "\n Indexing complete.")))
