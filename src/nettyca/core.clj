(ns nettyca.core
  "Echo examples and start/stop plus main fns"
  (:require [clojure.tools.logging :as log]
            [nettyca.netty :as netty]
            [clojure.core.async :refer [chan timeout go go-loop alts!
                                        <! >! close!] :as async]))

;; three echo implementations
;;

(defn echo-impl-simple [rw]
  (async/pipe (rw :r) (rw :w)))

(defn echo-impl-newline [rw]
  (let [map-ch (chan 1 (map #(str % "\r\n"))
                     #(log/error % "transducer err!"))]
    (async/pipe (rw :r) map-ch)
    (async/pipe map-ch (rw :w))))

(defn echo-impl-timeout
  "An echo impl, loop inside go macro, close chan if timeout"
  [rw]
  (go-loop []
    (if-let [msg (first (alts! [(rw :r) (timeout 5000)]))]
      (do (log/info "echo: got msg:" msg)
          (>! (rw :w) (str msg "\r\n"))
          (recur))
      (do (log/info "echo: got timeout or closed chan")
          (close! (rw :r)) (close! (rw :w))))))

;; start/stop and a main
;;

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
  (start 9090 echo-impl-timeout))

(comment

  ;; call from repl examples
  (def sys (start 9090 echo-impl-timeout))
  (stop sys)

  )
