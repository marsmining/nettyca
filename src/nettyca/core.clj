(ns nettyca.core
  "Echo examples and start/stop plus main fns"
  (:require [clojure.tools.logging :as log]
            [nettyca.netty :as netty]
            [clojure.core.async :refer [chan timeout go go-loop alts!
                                        <! >! close!] :as async]))

;; three echo implementations
;;

(defn echo-impl-simple [r w]
  (async/pipe r w))

(defn echo-impl-newline [r w]
  (let [map-ch (chan 1 (map #(str % "\r\n"))
                     #(log/error % "transducer err!"))]
    (async/pipe r map-ch)
    (async/pipe map-ch w)))

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
