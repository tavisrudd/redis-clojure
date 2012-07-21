# redis-clojure

redis-clojure is the oldest of several Clojure client libraries for
[Redis](http://redis.io). If you are currently trying to choose which
of these client libraries to use, I (tavisrudd) recommend using
[Carmine](https://github.com/ptaoussanis/carmine) instead. It has
better documentation, better connection pooling, support for newer
features of Redis 2.0+, Leiningen 2.0 support, more flexible
serialization, and is faster.

If you are currently using redis-clojure and would like to add support
for new Redis commands, patches are welcome.

## Building 

This version of redis-clojure uses
[Leiningen](http://github.com/technomancy/leiningen) as build tool.

## Running tests

To run tests:

    lein test

*Note* you need to have `redis-server` running on `localhost` at port `6379`.


## Adding it as a dependency for your own project

Simply add it to your project.clj :dependencies list:

    :dependencies [[org.clojars.tavisrudd/redis-clojure "1.3.1"] ...]

then run lein deps.
