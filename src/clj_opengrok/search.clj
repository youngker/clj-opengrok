(ns clj-opengrok.search
  (:require [clj-opengrok.core :refer :all]
            [clojure.string :as string]
            [cli4clj.cli :refer [start-cli]]
            [clojure.tools.cli :refer [parse-opts]])
  (:gen-class))

(def cli-options
  [["-R" "--conf CONF" "<configuration.xml> Read configuration
 from the specified file"]
   ["-d" "--def DEF" "Symbol Definitions"]
   ["-r" "--ref REF" "Symbol References"]
   ["-p" "--path PATH" "Path"]
   ["-h" "--hist HIST" "History"]
   ["-f" "--text TEXT" "Full Text"]
   ["-t" "--type TYPE" "Type"]
   ["-a" "--all"]
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

(defn- set-search-options [opts args & [page]]
  (reset! opts (:options (assoc-in (parse-opts args cli-options)
                                   [:options :page] (or page 1)))))

(defn- update-search-options [opts args & [page]]
  (reset! opts (merge (select-keys @opts [:conf])
                      (:options (parse-opts args cli-options))
                      {:page (or page 1)})))

(defn- loop-prompt [args]
  (let [opts (atom {})]
    (search (set-search-options opts args))
    (start-cli
     {:cmds
      {:search
       {:fn (fn [& args]
              (search (update-search-options opts args)))
        :short-info "search"
        :long-info "E.g.: s \"-f\" \\\"clojure opengrok\\\""}
       :s :search
       :next {:fn (fn []
                    (when-not (contains? @opts :all)
                      (search (swap! opts update-in [:page] inc))))
              :short-info "next page"
              :long-info "E.g.: next or n"}
       :n :next}
      :prompt-string "clj-opengrok > "}))
  (exit 0 "done"))

(defn -main [& args]
  (loop-prompt args))
