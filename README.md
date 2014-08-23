# nettyca

A bit of code for starting up a [Netty](http://netty.io) server which
delegates control for each incoming connection to a protocol handler
function you define, passing it a pair of
[core.async](https://github.com/clojure/core.async) channels, which
you use in your handler for reading and writing to a client.

## Usage

See the core namespace for examples of a simple echo protocol.

## License

Copyright © 2014 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
