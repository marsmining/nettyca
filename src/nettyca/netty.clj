(ns nettyca.netty
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :refer [chan timeout go go-loop alts!
                                        <! >! close!] :as async])
  (:import (io.netty.bootstrap Bootstrap ServerBootstrap)
           (io.netty.channel ChannelHandlerAdapter
                             ChannelInitializer ChannelOption)
           (io.netty.channel.nio NioEventLoopGroup)
           (io.netty.channel.socket SocketChannel)
           (io.netty.channel.socket.nio NioSocketChannel NioServerSocketChannel)
           (io.netty.handler.codec.string StringEncoder StringDecoder)
           (io.netty.handler.codec LineBasedFrameDecoder)))

(defn mk-handler-core-async
  "Netty `ChannelHandler` which plugs into core async"
  [conn-chan]
  (log/info "handler: mk-handler-core-async")
  (let [rw {:r (chan) :w (chan)}]
    (proxy [ChannelHandlerAdapter] []
      (channelActive [ctx]
        (log/info "handler: channelActive")
        (go
          (>! conn-chan rw)
          (loop []
            (if-let [msg (<! (rw :w))]
              (do (log/trace "handler: write on netty channel:" msg)
                  (.writeAndFlush ctx msg)
                  (recur))
              (do (log/info "handler: chan closed!")
                  (.close ctx))))))
      (channelRead [ctx msg]
        (log/trace "handler: channelRead")
        (go (>! (rw :r) msg)))
      (exceptionCaught [ctx cause]
        (log/error cause "handler: error")
        (.close ctx)))))

(defn mk-initializer
  "Line based pipeline"
  [handler-fn]
  (proxy [ChannelInitializer] []
    (initChannel [^SocketChannel ch]
      (-> (.pipeline ch)
          (.addLast "frameDecoder" (LineBasedFrameDecoder. (int 1024)))
          (.addLast "stringDecoder" (StringDecoder.))
          (.addLast "stringEncoder" (StringEncoder.))
          (.addLast "myHandler" (handler-fn))))))

(defn start-netty-client
  "Start Netty client"
  [group host port pipeline]
  (try
    (log/info "client: starting netty client on port:" port)
    (let [b (doto (Bootstrap.)
              (.group group)
              (.channel NioSocketChannel)
              (.option ChannelOption/SO_KEEPALIVE true)
              (.handler pipeline))
          f (-> b (.connect host (int port)) .sync)]
      (-> f .channel .closeFuture .sync))
    (finally
      (log/info "client: in finally clause..")
      (.shutdownGracefully group))))

(defn start-netty-server
  "Start Netty server, blocking this thread until shutdown"
  [group host port pipeline]
  (try
    (log/info "server: starting netty on port:" port)
    (let [b (doto (ServerBootstrap.)
              (.group group)
              (.channel NioServerSocketChannel)
              (.childHandler pipeline)
              (.option ChannelOption/SO_BACKLOG (int 128))
              (.childOption ChannelOption/SO_KEEPALIVE true))
          f (-> b (.bind host (int port)) .sync)]
      (-> f .channel .closeFuture .sync))
    (finally
      (log/info "server: in finally clause..")
      (.shutdownGracefully group))))

(defn start-netty-off-thread
  "Start Netty on another thread, return map with handles to shutdown"
  [host port pipeline type]
  (let [group (NioEventLoopGroup.)]
    {:group group
     :server (future (if (= type :server)
                       (start-netty-server group host port pipeline)
                       (start-netty-client group host port pipeline)))
     :shutdown-fn #(.shutdownGracefully group)}))

(defn start-netty-core-async
  "Start Netty server, new connections send r/w channel pair on conn-chan"
  [conn-chan host port handler type]
  (let [pre (str (name type) ":")
        pipeline (mk-initializer #(mk-handler-core-async conn-chan))
        sys (start-netty-off-thread host port pipeline type)]
    (go-loop [clients []]
      (if-let [rw (<! conn-chan)]
        (do (log/info pre "snca: got r/w channel pair..")
            (try (if (= type :server)
                   (handler (rw :r) (rw :w))
                   (handler (rw :r) (rw :w) conn-chan))
                 (catch Throwable t
                   (log/error t pre "snca: err calling handler!")))
            (recur (conj clients rw)))
        (do (log/info pre "snca: recvd nil, conn-chan closed")
            (doseq [lrw clients] (close! (lrw :r)) (close! (lrw :w)))
            (.shutdownGracefully (:group sys)))))))
