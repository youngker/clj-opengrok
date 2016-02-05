(ns clj-opengrok.core
  (:require [clj-opengrok.search :refer :all]
            [clojure.string :as string]
            [cli4clj.cli :refer [start-cli]]
            [clojure.tools.cli :refer [parse-opts]])
  (:gen-class))

(def cli-options
  [["-R" nil "<configuration.xml> Read configuration from the specified file"
    :id :conf]
   ["-d" nil "Symbol Definitions"
    :id :def]
   ;; ["-r" nil "Symbol References"
   ;;  :id :ref]
   ;; ["-p" nil "Path"
   ;;  :id :path]
   ;; ["-h" nil "History"
   ;;  :id :hist]
   ["-f" nil "Full Text"
    :id :text ]
   ;; ["-t" nil "Type"
   ;;  :id :type]
   ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["Search -R <configuration.xml> [-d | -r | -p | -h | -f | -t] 'query string' .."
        ""
        "Usage: program-name [options] action"
        ""
        "Options:"
        options-summary
        ""
        "Please refer to the manual page for more information."]
       (string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    ;; Handle help and error conditions
    (println options "///" arguments "///" errors "///\n")
    (cond
      (:help options) (exit 0 (usage summary))
      (not= (count arguments) 2) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors)))
    (start-cli {:cmds {:search {:fn #(search options arguments)
                                }
                       :s :search
                       }})))
