(ns nettyca.core
  "Echo examples and start/stop plus main fns"
  (:require [clojure.tools.logging :as log]
            [nettyca.netty :as netty]
            [clojure.core.async :refer [chan timeout go go-loop alts!
                                        <! >! close!] :as async]))

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

(defn echo-client-test [r w]
  (go (let [[v p] (alts! [[w "42\r\n"] (timeout 500)])
            _ (log/info "### wrote:" v p)
            [v p] (alts! [r (timeout 5000)])
            _ (log/info "### read:" v p)]
        (log/info "### test result:" (= v "42"))
        (close! r) (close! w))))

;; start/stop and a main
;;

(defn start [host port handler type]
  "Start a socket client or server on port"
  (let [ch (async/chan)]
    {:chan ch
     :future (netty/start-netty-core-async
              ch host port handler type)}))

(defn stop [sys]
  "Stop the system and clean-up"
  (async/close! (sys :chan)))

(defn -main []
  (log/info "starting..")
  (start "127.0.0.1" 9090 echo-impl-timeout :server))

(comment

  ;; call from repl examples, server
  (def ss (start "127.0.0.1" 9090 echo-impl-timeout :server))
  (stop ss)

  ;; client connection
  (def sc (start "127.0.0.1" 9090 echo-client-test :client))
  (stop sc)
  )
