(ns clj-opengrok.index
  (:import
   [java.io File]
   [org.opensolaris.opengrok.index Indexer]
   [org.opensolaris.opengrok.configuration RuntimeEnvironment Configuration])
  (:gen-class))

(defn- get-configuration [^String args]
  (nth args (inc (.indexOf args "-W"))))

(defn- get-args [args file]
  (conj args file "-R" "-e"))

(defn -main [& args]
  (let [config (new Configuration)
        file (File. ^String (get-configuration args))]
    (.setHistoryCache config false)
    (.setConfiguration (RuntimeEnvironment/getInstance) config)
    (.mkdirs (.getParentFile file))
    (.createNewFile file)
    (.writeConfiguration (RuntimeEnvironment/getInstance) file)
    (println (get-configuration args))
    (println (get-args args (get-configuration args)))
    (Indexer/main (into-array (get-args args (get-configuration args))))))
