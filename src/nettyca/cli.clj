(ns nettyca.cli
  "Basic cli support"
  (:require [clojure.tools.logging :as log]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as string]
            [clojure.core.async :as async]
            [nettyca.core :as nc])
  (:gen-class))

;; cli stuff
;;

(def cli-options
  [[nil "--help"]
   ["-s" "--server" "Create server not client"
    :default false]
   ["-n" "--name HOST" "Hostname to bind or connect to"
    :default "127.0.0.1"]
   ["-p" "--port PORT" "Port number"
    :default 9090
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]])

(defn usage [options-summary]
  (->> ["Nettyca examples program."
        ""
        "Usage: lein run [options]"
        ""
        "Options:"
        options-summary
        ""]
       (string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (shutdown-agents)
  (System/exit status))

(defn -main [& args]
  (log/info "starting..")
  (let [{:keys [options arguments errors summary]}
        (parse-opts args cli-options)
        host (:name options)
        port (:port options)
        type (if (:server options) :server :client)]
    (cond
     (:help options) (exit 0 (usage summary))
     (not= (count arguments) 0) (exit 1 (usage summary))
     errors (exit 1 (error-msg errors)))
    (log/info "starting netty" type "on" host "and" port)
    (let [sys (if (= type :server)
                (nc/start host port nc/echo-impl-timeout :server)
                (nc/start host port nc/echo-client-test :client))]
      (async/<!! (:go-chan sys)))
    (exit 0 "done. exiting..")))
