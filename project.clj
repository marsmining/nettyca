(defproject nettyca "0.1.0-SNAPSHOT"
  :description "Create simple socket servers using Netty and core.async"
  :url "http://github.com/marsmining/nettyca"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0-alpha2"]
                 [org.clojure/tools.logging "0.3.0"]
                 [io.netty/netty-handler "5.0.0.Alpha1"]
                 [org.clojure/core.async "0.1.338.0-5c5012-alpha"]
                 [org.clojure/tools.cli "0.3.1"]]
  :profiles {:dev {:dependencies [[org.clojure/test.check "0.5.8"]
                                  [ch.qos.logback/logback-classic "1.1.2"]]}}
  :main nettyca.cli
  :aot [nettyca.cli]
  :jar-exclusions [#"logback.xml"])
