# redis-clojure

This is a new version of redis-clojure targeting both Clojure 1.2 and 1.3.

redis-clojure is a Clojure client library for the
[Redis](http://code.google.com/p/redis) key value (and more!) storage
system.

The goal of redis-clojure is to provide a low level interface to all
Redis commands in a Clojure idiomatic way, when possible.

## Building 

This version of redis-clojure uses
[Leiningen](http://github.com/technomancy/leiningen) as build tool.

## Running tests

To run tests:

    lein test

*Note* you need to have `redis-server` running on `localhost` at port `6379`.


## Adding it as a dependency for your own project

Simply add it to your project.clj :dependencies list:

    :dependencies [[org.clojars.tavisrudd/redis-clojure "1.3.0"] ...]

then run lein deps.
