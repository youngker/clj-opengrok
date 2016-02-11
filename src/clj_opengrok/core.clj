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
  (string/join
   \newline
   ["clj-opengrok -R <configuration.xml> [-d | -r | -p | -h | -f | -t] 'query string' .."
    ""
    "Options:"
    options-summary
    ""
    "Please refer to the manual page for more information."]))

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

(defn clj-opengrok-search [p]
  (if (pos? p)
    (if (pos? (search p @option))
      (do (set-page p) nil)
      (do (search (set-page (dec p)) @option) nil))
    (do (search 1 @option) nil)))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) (exit 0 (usage summary))
      (< (count options) 2) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors)))
    (set-option options)
    (time (search @page @option))
    (start-cli {:cmds {:search {:fn (fn [& args]
                                      (let [{:keys [options]} (parse-opts
                                                               args
                                                               cli-options)]
                                        (set-page 1)
                                        (set-option options)
                                        (do (time (search @page @option)) nil)))
                                :short-info "search"
                                :long-info "E.g.: s \"-f\" \\\"clojure opengrok\\\""}
                       :s :search
                       :next {:fn #(time (clj-opengrok-search (inc @page)))
                              :short-info "next page"
                              :long-info "E.g.: next or n"}
                       :n :next
                       :prev {:fn #(time (clj-opengrok-search (dec @page)))
                              :short-info "previous page"
                              :long-info "E.g.: prev or p"}
                       :p :prev
                       :go-page {:fn #(clj-opengrok-search %)
                                 :short-info "go to the page"
                                 :long-info "E.g.: g numberofpage"}
                       :g :go-page}
                :prompt-string ""})
    (exit 0 "done")))
