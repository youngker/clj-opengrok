(ns clj-opengrok.core
  (:require
   [clj-opengrok.index :as index]
   [clj-opengrok.search :as search]
   [clojure.string :as string]
   [clojure.tools.cli :refer [parse-opts]])
  (:gen-class))

(def cli-options
  [["-R" "--conf file" "search, /path/to/configuration.xml"]
   ["-d" "--def definition" "search, definition"]
   ["-r" "--ref reference" "search, reference"]
   ["-p" "--path path" "search, file"]
   ["-h" "--hist history" "search, history"]
   ["-f" "--text text" "search, text"]
   ["-t" "--type type" "search, type (c/h/java...)"]
   ["-o" "--sort order" "search, date or path :default relevance"]
   ["-q" "--quiet" "search, does not show page."]
   ["-i" "--ignore file-ext" "index, *.o:*.class:*.zip:.svn:target"]
   ["-s" "--src-root path" "index, source root path"]
   ["-e" "--project" "index, enable projects"]
   ["-v" "--version"]])

(defn usage [options-summary]
  (string/join
   \newline
   [""
    "Usage: clj-opengrok action options"
    "  clj-opengrok index -s /path/to/projects -e"
    "  clj-opengrok search -R configuration.xml -d|r|p|h|f|t text"
    ""
    "Options:"
    options-summary
    ""
    "Actions:"
    "  index      Creating the index"
    "  search     Searching the text"
    ""
    "See documentation on https://github.com/youngker/clj-opengrok"]))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn version []
  (str "clj-opengrok v" (System/getProperty "clj-opengrok.version")))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]}
        (parse-opts args cli-options)]
    (cond
      (:version options) (exit 0 (version))
      (not= (count arguments) 1) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors)))
    (case (first arguments)
      "search" (time (search/search options))
      "index" (time (index/index options))
      (exit 1 (usage summary)))
    (System/exit 0)))
