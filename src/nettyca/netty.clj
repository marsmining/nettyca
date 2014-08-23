(ns nettyca.netty
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :refer [chan timeout go go-loop alts!
                                        <! >! close!] :as async])
  (:import (io.netty.bootstrap ServerBootstrap)
           (io.netty.channel ChannelHandler ChannelHandlerAdapter
                             ChannelInitializer ChannelOption
                             ChannelFutureListener)
           (io.netty.channel.nio NioEventLoopGroup)
           (io.netty.channel.socket SocketChannel)
           (io.netty.channel.socket.nio NioServerSocketChannel)
           (io.netty.buffer ByteBuf)
           (io.netty.util ReferenceCountUtil)
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
          (.addLast "frameDecoder" (LineBasedFrameDecoder. (int 80)))
          (.addLast "stringDecoder" (StringDecoder.))
          (.addLast "stringEncoder" (StringEncoder.))
          (.addLast "myHandler" (handler-fn))))))

(defn start-netty
  "Start Netty server, blocking this thread until shutdown"
  [group port handler-fn]
  (try
    (log/info "server: starting netty on port:" port)
    (let [b (doto (ServerBootstrap.)
              (.group group)
              (.channel NioServerSocketChannel)
              (.childHandler (mk-initializer handler-fn))
              (.option ChannelOption/SO_BACKLOG (int 128))
              (.childOption ChannelOption/SO_KEEPALIVE true))
          f (-> b (.bind (int port)) .sync)]
      (-> f .channel .closeFuture .sync))
    (finally
      (log/info "server: in finally clause..")
      (.shutdownGracefully group))))

(defn start-netty-off-thread
  "Start Netty on another thread, return map with handles to shutdown"
  [port handler-fn]
  (let [group (NioEventLoopGroup.)]
    {:group group
     :server (future (start-netty group port handler-fn))
     :shutdown-fn #(.shutdownGracefully group)}))

(defn start-netty-core-async
  "Start Netty server, new connections send r/w channel pair on conn-chan"
  [conn-chan port handler-fn]
  (let [sys (start-netty-off-thread port #(mk-handler-core-async conn-chan))]
    (go-loop [clients []]
      (if-let [rw (<! conn-chan)]
        (do (log/info "snca: got r/w channel pair..")
            (try (handler-fn rw)
                 (catch Throwable t (log/error t "snca: err invoking handler!")))
            (recur (conj clients rw)))
        (do (log/info "snca: recvd nil, conn-chan closed")
            (doseq [lrw clients] (close! (lrw :r)) (close! (lrw :w)))
            (.shutdownGracefully (:group sys)))))))
