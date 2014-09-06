(ns nettyca.core
  "Echo examples and start/stop plus main fns"
  (:require [clojure.tools.logging :as log]
            [nettyca.netty :as netty]
            [clojure.core.async :refer [chan timeout go go-loop alts!
                                        <! >! close!] :as async]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as string])
  (:gen-class))

;; three echo server impls
;;

(defn echo-impl-simple [r w]
  (async/pipe r w))

(defn echo-impl-newline [r w]
  (async/pipeline 1 w (map #(str % "\r\n")) r))

(defn echo-impl-timeout
  "An echo impl, loop inside go macro, close chan if timeout"
  [r w]
  (go-loop []
    (if-let [msg (first (alts! [r (timeout 5000)]))]
      (do (log/info "echo: got msg:" msg)
          (>! w (str msg "\r\n"))
          (recur))
      (do (log/info "echo: got timeout or closed chan")
          (close! r) (close! w)))))

;; client example, echo test
;; clients receive a 3rd arg, the connection channel

(defn echo-client-test
  "Client test, sends 42 then waits for response"
  [r w c]
  (go (let [[v p] (alts! [[w "42\r\n"] (timeout 500)])
            _ (log/info "### wrote:" v p)
            [v p] (alts! [r (timeout 5000)])
            _ (log/info "### read:" v p)]
        (log/info "### test result:" (= v "42"))
        (close! r) (close! w) (close! c))))

;; start/stop
;;

(defn start [host port handler type]
  "Start a tcp client or server"
  (let [ch (async/chan)]
    {:conn-chan ch
     :go-chan (netty/start-netty-core-async
               ch host port handler type)}))

(defn stop [sys]
  "Stop the system and clean-up"
  (async/close! (sys :conn-chan)))

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
                (start host port echo-impl-timeout :server)
                (start host port echo-client-test :client))]
      (async/<!! (:go-chan sys)))
    (exit 0 "done. exiting..")))

(comment

  ;; call from repl examples, server
  (def ss (start "127.0.0.1" 9090 echo-impl-timeout :server))
  (stop ss)

  ;; client connection
  (start "127.0.0.1" 9090 echo-client-test :client)
  )
