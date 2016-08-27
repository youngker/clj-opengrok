(ns clj-opengrok.index
  (:import
   [java.io File]
   [org.opensolaris.opengrok.index Indexer]
   [org.opensolaris.opengrok.configuration RuntimeEnvironment Configuration]))

(defn- get-configuration [opts]
  (str (:src-root opts) "/.opengrok/configuration.xml"))

(defn- ignore-files []
  (mapcat #(list "-i" %) '(".opengrok" "out" "*.so" "*.a" "*.o" "*.gz" "*.bz2"
                           "*.jar" "*.zip" "*.class")))

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
   "-P" "-e"
   (ignore-files)))

(defn index [opts]
  (let [config (new Configuration)
        file (File. ^String (get-configuration opts))]
    (.setHistoryCache config true)
    (.setConfiguration (RuntimeEnvironment/getInstance) config)
    (.mkdirs (.getParentFile file))
    (.createNewFile file)
    (.writeConfiguration (RuntimeEnvironment/getInstance) file)
    (Indexer/main (into-array (get-args opts)))))
