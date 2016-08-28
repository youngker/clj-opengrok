(ns clj-opengrok.index
  (:import
   [java.io File]
   [org.opensolaris.opengrok.index Indexer]
   [org.opensolaris.opengrok.configuration RuntimeEnvironment Configuration]))

(defn- get-configuration [opts]
  (str (:src-root opts) "/.opengrok/configuration.xml"))

(def ignore-file-lists
  '(".opengrok" "out" "*.so" "*.a" "*.o" "*.gz" "*.bz2" "*.jar" "*.zip"
    "*.class" "*.elc"))

(defn- get-args [opts]
  ((comp vec flatten vector)
   "-Xmx2048m"
   "-r" "on"
   "-c" (System/getenv "CTAGS_PATH")
   "-a" "on"
   "-W" (get-configuration opts)
   "-R" (get-configuration opts)
   "-S"
   "-s" (:src-root opts)
   "-d" (str (:src-root opts) "/.opengrok")
   "-H" "-q"
   "-e" (when (:project opts) "-P")
   (interleave (repeat "-i") ignore-file-lists)))

(defn index [opts]
  (let [config (new Configuration)
        file (File. ^String (get-configuration opts))]
    (.setHistoryCache config true)
    (.setConfiguration (RuntimeEnvironment/getInstance) config)
    (.mkdirs (.getParentFile file))
    (.createNewFile file)
    (.writeConfiguration (RuntimeEnvironment/getInstance) file)
    (Indexer/main (into-array (remove nil? (get-args opts))))))
