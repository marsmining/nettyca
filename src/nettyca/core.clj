(ns nettyca.core
  "Functions to start and stop a socket server"
  (:require [clojure.tools.logging :as log]
            [nettyca.netty :as netty]
            [clojure.core.async :refer [chan timeout go go-loop alts!
                                        <! >! close!] :as async]))

(defn echo-impl
  "An echo impl, loop inside go macro, close chan if timeout"
  [rw]
  (go-loop []
    (if-let [msg (first (alts! [(rw :r) (timeout 5000)]))]
      (do (log/info "echo: got msg:" msg)
          (>! (rw :w) (str msg "\r\n"))
          (recur))
      (do (log/info "echo: got timeout or closed chan")
          (close! (rw :r)) (close! (rw :w))))))

(defn start [port protocol-fn]
  "Start a socket server on port"
  (let [ch (async/chan)]
    {:chan ch
     :future (netty/start-netty-core-async
              ch port protocol-fn)}))

(defn stop [sys]
  "Stop the system and clean-up"
  (async/close! (sys :chan)))

(defn -main []
  (log/info "starting..")
  (start 9090 echo-impl))

(comment

  (def sys (start 9090 echo-impl))
  (stop sys)

  )
