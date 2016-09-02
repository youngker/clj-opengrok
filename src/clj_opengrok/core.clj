(ns clj-opengrok.core
  (:require
   [clj-opengrok.index :as index]
   [clj-opengrok.search :as search]
   [clojure.string :as string]
   [clojure.tools.cli :refer [parse-opts]])
  (:gen-class))

(def cli-options
  [["-R" "--conf CONF" "configuration.xml"]
   ["-d" "--def DEF" "Definitions"]
   ["-r" "--ref REF" "References"]
   ["-p" "--path PATH" "Path"]
   ["-h" "--hist HIST" "History"]
   ["-f" "--text TEXT" "Full Text"]
   ["-t" "--type TYPE" "Type"]
   ["-o" "--sort SORT" "date or path :default relevance"]
   ["-q" "--quiet" "Does not show page."]
   ["-s" "--src-root SRC_ROOT" "Source Root"]
   ["-e" "--project" "Enable Project"]])

(defn usage [options-summary]
  (string/join
   \newline
   ["Index=>"
    "clj-opengrok index -s src-root-directory"
    ""
    "Search=>"
    "clj-opengrok search -R configuration.xml -[d|r|p|h|f|t] 'query string'"
    ""
    "Options:"
    options-summary
    ""
    "See documentation on https://github.com/youngker/clj-opengrok"]))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]}
        (parse-opts args cli-options)]
    (cond
      (:help options) (exit 0 (usage summary))
      (not= (count arguments) 1) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors)))
    (case (first arguments)
      "search" (time (search/search options))
      "index" (time (index/index options))
      (exit 1 (usage summary)))))
