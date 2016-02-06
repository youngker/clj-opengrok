(ns clj-opengrok.core
  (:require [clj-opengrok.search :refer :all]
            [clojure.string :as string]
            [cli4clj.cli :refer [start-cli]]
            [clojure.tools.cli :refer [parse-opts]])
  (:gen-class))

(def cli-options
  [["-R" "--conf CONF" "<configuration.xml> Read configuration from the specified file"]
   ["-d" "--def DEF" "Symbol Definitions"]
   ["-r" "--ref REF" "Symbol References"]
   ["-p" "--path PATH" "Path"]
   ["-h" "--hist HIST" "History"]
   ["-f" "--text TEXT" "Full Text"]
   ["-t" "--type TYPE" "Type"]
   ["-help" "--help"]])

(defn usage [options-summary]
  (->> ["clj-opengrok -R <configuration.xml> [-d | -r | -p | -h | -f | -t] 'query string' .."
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

(def page (atom 1))
(def option (atom {}))

(defn set-page [p]
  (reset! page p))

(defn set-option [o]
  (reset! option o))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) (exit 0 (usage summary))
      (< (count options) 2) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors)))
    (set-option options)
    (search @page @option)
    (start-cli {:cmds {:search {:fn #(do (set-page %1)
                                         (set-option %2)
                                         (search %1 %2))}
                       :s :search
                       :next {:fn #(do (set-page (inc @page))
                                       (search @page @option))}
                       :n :next
                       :prev {:fn #(do (set-page (dec @page))
                                       (search @page @option))}
                       :p :prev
                       :go-page {:fn #(do (set-page %)
                                          (search % @option))}
                       :g :go-page}
                :prompt-string "opengrok> "})
    (exit 0 "done")))
