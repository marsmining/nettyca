# nettyca

A bit of code for starting up a [Netty](http://netty.io) server which
delegates control for each incoming connection to a protocol handler
function you define, passing it a pair of
[core.async](https://github.com/clojure/core.async) channels, which
you use in your handler for reading and writing to a client.

This is not for production! Just a way to play with writing socket
servers in terms of core.async channels. It uses Netty 5 and Clojure
1.7 __alpha__ releases.

## Usage

Add `nettyca` as a dependency in your `project.clj` file:

```clj
(defproject example-project "1.0.0"
  :dependencies [[org.clojure/clojure "1.7.0-alpha1"]
                 [nettyca "0.1.0-SNAPSHOT"]
                 [ch.qos.logback/logback-classic "1.1.2"]])
```

Notice the Clojure version is alpha, this library is using the new
[transducer](http://blog.cognitect.com/blog/2014/8/6/transducers-are-coming)
feature of Clojure. Also, in the example above, I've included a Java
logging implementation. If your project already has one, leave the
[logback](http://logback.qos.ch/) dependency out.

See the core namespace for examples of a simple echo protocol.

To start an echo server in the repl, follow these steps:

    user=> (use 'nettyca.core)
    DEBUG i.n.u.i.l.InternalLoggerFactory Using SLF4J as the default logging framework
    DEBUG i.n.c.MultithreadEventLoopGroup -Dio.netty.eventLoopThreads: 16

Next define an echo impl as a function which accepts two arguments,
the read channel and write channel respectively.

    user=> (defn echo [r w] (clojure.core.async/pipe r w))
    #'user/echo

In this simplest implementation, we just use core.async `pipe`
function to send values from the read channel to the write channel.

Next, start a Netty server listening on a port:

    user=> (def sys (start 9090 echo))
    ... debug messages omitted ...

Now you can telnet to port 9090:

    $ telnet localhost 9090
    Trying 127.0.0.1...
    Connected to localhost.
    Escape character is '^]'.
    56
    56
    Connection closed by foreign host.

Finally, stop the server:

    user=> (stop sys)
    INFO nettyca.netty   snca: recvd nil, conn-chan closed
    INFO nettyca.netty   server: in finally clause..

That's it!

## License

Copyright © 2014 Brandon van Beekum

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
