(ns redis.command-tests
  (:refer-clojure :exclude [keys type get set sort])
  (:require [redis.core :as redis])
  (:use [clojure.test]))

(defn server-fixture [f]
  (redis/with-server
   {:host "127.0.0.1"
    :port 6379
    :db 15
    :password (. System getenv "REDIS_TESTPASS")}
   ;; String value
   (redis/set "foo" "bar")
   ;; List with three items
   (redis/rpush "list" "one")
   (redis/rpush "list" "two")
   (redis/rpush "list" "three")
   ;; Set with three members
   (redis/sadd "set" "one")
   (redis/sadd "set" "two")
   (redis/sadd "set" "three")
   ;; Hash with three fields
   (redis/hset "hash" "one" "foo")
   (redis/hset "hash" "two" "bar")
   (redis/hset "hash" "three" "baz")
   (f)
   (redis/flushdb)))

(use-fixtures :each server-fixture)

(deftest ping
  (is (= "PONG" (redis/ping))))

(deftest exists
  (is (= true (redis/exists "foo")))
  (is (= false (redis/exists "nonexistent"))))

(deftest del
  (is (= 0 (redis/del "nonexistent")))
  (is (= 1 (redis/del "foo")))
  (is (= nil  (redis/get "foo")))
  (redis/mset "one" "1" "two" "2" "three" "3")
  (is (= 3 (redis/del "one" "two" "three"))))

(deftest type
  (is (= :none (redis/type "nonexistent")))
  (is (= :string (redis/type "foo")))
  (is (= :list (redis/type "list")))
  (is (= :set (redis/type "set"))))

(deftest keys
  (is (= [] (redis/keys "a*")))
  (is (= ["foo"] (redis/keys "f*")))
  (is (= ["foo"] (redis/keys "f?o")))
  (redis/set "fuu" "baz")
  (is (= #{"foo" "fuu"} (clojure.core/set (redis/keys "f*")))))

(deftest randomkey
  (redis/flushdb)
  (redis/set "foo" "bar")
  (is (= "foo" (redis/randomkey)))
  (redis/flushdb)
  (is (nil? (redis/randomkey))))

(deftest rename
  (is (thrown? Exception (redis/rename "foo" "foo")))
  (is (thrown? Exception (redis/rename "nonexistent" "foo")))
  (redis/rename "foo" "bar")
  (is (= "bar" (redis/get "bar")))
  (is (= nil (redis/get "foo")))
  (redis/set "foo" "bar")
  (redis/set "bar" "baz")
  (redis/rename "foo" "bar")
  (is (= "bar" (redis/get "bar")))
  (is (= nil (redis/get "foo"))))

(deftest renamenx
  (is (thrown? Exception (redis/renamenx "foo" "foo")))
  (is (thrown? Exception (redis/renamenx "nonexistent" "foo")))
  (is (= true (redis/renamenx "foo" "bar")))
  (is (= "bar" (redis/get "bar")))
  (is (= nil (redis/get "foo")))
  (redis/set "foo" "bar")
  (redis/set "bar" "baz")
  (is (= false (redis/renamenx "foo" "bar"))))

(deftest dbsize
  (let [size-before (redis/dbsize)]
    (redis/set "anewkey" "value")
    (let [size-after (redis/dbsize)]
      (is (= size-after
             (+ 1 size-before))))))

(deftest expire
  (is (= true (redis/expire "foo" 1)))
  (Thread/sleep 2000)
  (is (= false (redis/exists "foo")))
  (redis/set "foo" "bar")
  (is (= true (redis/expire "foo" 20)))
  ;@@TR: this test makes no sense to me!
  ;; (is (= false (redis/expire "foo" 10)))
  (is (= false (redis/expire "nonexistent" 42))))

(deftest ttl
  (is (= -1 (redis/ttl "nonexistent")))
  (is (= -1 (redis/ttl "foo")))
  (redis/expire "foo" 42)
  (is (< 40 (redis/ttl "foo"))))

(deftest select
  (redis/select 0)
  (is (= nil (redis/get "akeythat_probably_doesnotexsistindb0"))))

(deftest flushdb
  (redis/flushdb)
  (is (= 0 (redis/dbsize))))


;;
;; String commands
;; 
(deftest set
  (redis/set "bar" "foo")
  (is (= "foo" (redis/get "bar")))
  (redis/set "foo" "baz")
  (is (= "baz" (redis/get "foo"))))

(deftest get
  (is (= nil (redis/get "bar")))
  (is (= "bar" (redis/get "foo"))))

(deftest getset
  (is (= nil   (redis/getset "bar" "foo")))
  (is (= "foo" (redis/get "bar")))
  (is (= "bar" (redis/getset "foo" "baz")))
  (is (= "baz" (redis/get "foo"))))

(deftest mget
  (is (= [nil] (redis/mget "bar")))
  (redis/set "bar" "baz")
  (redis/set "baz" "buz")
  (is (= ["bar"] (redis/mget "foo")))
  (is (= ["bar" "baz"] (redis/mget "foo" "bar")))
  (is (= ["bar" "baz" "buz"] (redis/mget "foo" "bar" "baz")))
  (is (= ["bar" nil "buz"] (redis/mget "foo" "bra" "baz"))))

(deftest mset
  (is (thrown?  Exception (redis/mset "key1"))) 
  (is (thrown?  Exception (redis/mset "key" "value" "key1")))
  (redis/mset "key1" "value1" "key2" "value2" "key3" "value3")
  (is (= ["value1" "value2" "value3"] (redis/mget "key1" "key2" "key3"))))

(deftest msetnx
  (is (thrown? Exception (redis/msetnx "key1")))
  (is (thrown? Exception (redis/msetnx "key1" "value1" "key2")))
  (is (= true (redis/msetnx "key1" "value1" "key2" "value2" "key3" "value3")))
  (is (= ["value1" "value2" "value3"] (redis/mget "key1" "key2" "key3")))
  (is (= false (redis/msetnx "key4" "value4" "key2" "newvalue" "key5" "value5")))
  (is (= [nil "value2" nil] (redis/mget "key4" "key2" "key5"))))

(deftest setnx
  (is (= true (redis/setnx "bar" "foo")))
  (is (= "foo" (redis/get "bar")))
  (is (= false (redis/setnx "foo" "baz")))
  (is (= "bar" (redis/get "foo"))))

(deftest incr
  (is (= 1 (redis/incr "nonexistent")))
  (is (thrown? Exception (redis/incr "foo")))
  (is (= 1 (redis/incr "counter")))
  (is (= 2 (redis/incr "counter"))))

(deftest incrby
  (is (= 42 (redis/incrby "nonexistent" 42)))
  (is (thrown? Exception (redis/incrby "foo" 42)))
  (is (= 0 (redis/incrby "counter" 0)))
  (is (= 42 (redis/incrby "counter" 42))))

(deftest decr
  (is (= -1 (redis/decr "nonexistent")))
  (is (thrown? Exception (redis/decr "foo")))
  (is (= -1 (redis/decr "counter")))
  (is (= -2 (redis/decr "counter"))))

(deftest decrby
  (is (= -42 (redis/decrby "nonexistent" 42)))
  (is (thrown? Exception (redis/decrby "foo" 0)))
  (is (= 0 (redis/decrby "counter" 0)))
  (is (= -42 (redis/decrby "counter" 42))))

(deftest append
  (is (= 5 (redis/append "string" "Hello")))
  (is (= 11 (redis/append "string" " World")))
  (is (= "Hello World" (redis/get "string"))))

(deftest substr
  (redis/set "s" "This is a string")
  (is (= "This" (redis/substr "s" 0 3)))
  (is (= "ing" (redis/substr "s" -3 -1)))
  (is (= "This is a string" (redis/substr "s" 0 -1)))
  (is (= " string" (redis/substr "s" 9 100000))))

;;
;; List commands
;;
(deftest rpush
  (is (thrown? Exception (redis/rpush "foo")))
  (redis/rpush "newlist" "one")
  (is (= 1 (redis/llen "newlist")))
  (is (= "one" (redis/lindex "newlist" 0)))
  (redis/del "newlist")
  (redis/rpush "list" "item")
  (is (= "item" (redis/rpop "list"))))

(deftest lpush
  (is (thrown? Exception (redis/lpush "foo")))
  (redis/lpush "newlist" "item")
  (is (= 1 (redis/llen "newlist")))
  (is (= "item" (redis/lindex "newlist" 0)))
  (redis/lpush "list" "item")
  (is (= "item" (redis/lpop "list"))))

(deftest llen
  (is (thrown? Exception (redis/llen "foo")))
  (is (= 0 (redis/llen "newlist")))
  (is (= 3 (redis/llen "list"))))

(deftest lrange
  (is (thrown? Exception (redis/lrange "foo" 0 1)))
  (is (= [] (redis/lrange "newlist" 0 42)))
  (is (= ["one"] (redis/lrange "list" 0 0)))
  (is (= ["three"] (redis/lrange "list" -1 -1)))
  (is (= ["one" "two"] (redis/lrange "list" 0 1)))
  (is (= ["one" "two" "three"] (redis/lrange "list" 0 2)))
  (is (= ["one" "two" "three"] (redis/lrange "list" 0 42)))
  (is (= [] (redis/lrange "list" 42 0))))

(deftest ltrim
  (is (thrown? Exception (redis/ltrim "foo" 0 0)))
  (redis/ltrim "list" 0 1)
  (is (= ["one" "two"] (redis/lrange "list" 0  99)))
  (redis/ltrim "list" 1 99)
  (is (= ["two"] (redis/lrange "list" 0  99))))

(deftest lindex
  (is (thrown? Exception (redis/lindex "foo" 0)))
  (is (= nil (redis/lindex "list" 42)))
  (is (= nil (redis/lindex "list" -4)))
  (is (= "one" (redis/lindex "list" 0)))
  (is (= "three" (redis/lindex "list" 2)))
  (is (= "three" (redis/lindex "list" -1))))

(deftest lset
  (is (thrown? Exception (redis/lset "foo" 0 "bar")))
  (is (thrown? Exception (redis/lset "list" 42 "value")))
  (redis/lset "list" 0 "test")
  (is (= "test" (redis/lindex "list" 0)))
  (redis/lset "list" 2 "test2")
  (is (= "test2" (redis/lindex "list" 2)))
  (redis/lset "list" -1 "test3")
  (is (= "test3" (redis/lindex "list" 2))))

(deftest lrem
  (is (thrown? Exception (redis/lrem "foo" 0 "bar")))
  (is (= 0 (redis/lrem "newlist" 0 "")))
  (is (= 1 (redis/lrem "list" 1 "two")))
  (is (= 1 (redis/lrem "list" 42 "three")))
  (is (= 1 (redis/llen "list"))))

(deftest lpop
  (is (thrown? Exception (redis/lpop "foo")))
  (is (= nil (redis/lpop "newlist")))
  (is (= "one" (redis/lpop "list")))
  (is (= 2 (redis/llen "list"))))

(deftest rpop
  (is (thrown? Exception (redis/rpop "foo")))
  (is (= nil (redis/rpop "newlist")))
  (is (= "three" (redis/rpop "list")))
  (is (= 2 (redis/llen "list"))))

; TODO test to see if this waits properly
(deftest blpop
(is (thrown? Exception (redis/blpop "foo" 1)))
(is (= nil (redis/blpop "newlist" 1)))
(is (= ["list" "one"] (redis/blpop "list" 1)))
(is (= 2 (redis/llen "list"))))

(deftest brpop)

(deftest rpoplpush
  (redis/rpush "src" "a")
  (redis/rpush "src" "b")
  (redis/rpush "src" "c")
  (redis/rpush "dest" "foo")
  (redis/rpush "dest" "bar")
  (is (= "c" (redis/rpoplpush "src" "dest")))
  (is (= ["a" "b"] (redis/lrange "src" 0 -1)))
  (is (= ["c" "foo" "bar" (redis/lrange "dest" 0 -1)])))

;;
;; Set commands
;;
(deftest sadd
  (is (thrown? Exception (redis/sadd "foo" "bar")))
  (is (= true (redis/sadd "newset" "member")))
  (is (= true (redis/sismember "newset" "member")))
  (is (= false (redis/sadd "set" "two")))
  (is (= true (redis/sadd "set" "four")))
  (is (= true (redis/sismember "set" "four"))))

(deftest srem
  (is (thrown? Exception (redis/srem "foo" "bar")))
  (is (= false (redis/srem "newset" "member")))
  (is (= true (redis/srem "set" "two")))
  (is (= false (redis/sismember "set" "two")))
  (is (= false (redis/srem "set" "blahonga"))))

(deftest spop
  (is (thrown? Exception (redis/spop "foo" "bar")))
  (is (= nil (redis/spop "newset")))
  (is (contains? #{"one" "two" "three"} (redis/spop "set"))))

(deftest smove
  (is (thrown? Exception (redis/smove "foo" "set" "one")))
  (is (thrown? Exception (redis/smove "set" "foo" "one")))
  (redis/sadd "set1" "two")
  (is (= false (redis/smove "set" "set1" "four")))
  (is (= #{"two"} (redis/smembers "set1")))
  (is (= true (redis/smove "set" "set1" "one")))
  (is (= #{"one" "two"} (redis/smembers "set1"))))

(deftest scard
  (is (thrown? Exception (redis/scard "foo")))
  (is (= 3 (redis/scard "set"))))

(deftest sismember
  (is (thrown? Exception (redis/sismember "foo" "bar")))
  (is (= false (redis/sismember "set" "blahonga")))
  (is (= true (redis/sismember "set" "two"))))

(deftest sinter
  (is (thrown? Exception (redis/sinter "foo" "set")))
  (is (= #{} (redis/sinter "nonexistent")))
  (redis/sadd "set1" "one")
  (redis/sadd "set1" "four")
  (is (= #{"one" "two" "three"} (redis/sinter "set")))
  (is (= #{"one"} (redis/sinter "set" "set1")))
  (is (= #{} (redis/sinter "set" "set1" "nonexistent"))))

(deftest sinterstore
  (redis/sinterstore "foo" "set")
  (is (= #{"one" "two" "three"} (redis/smembers "foo")))
  (redis/sadd "set1" "one")
  (redis/sadd "set1" "four")
  (redis/sinterstore "newset" "set" "set1")
  (is (= #{"one"} (redis/smembers "newset"))))

(deftest sunion
  (is (thrown? Exception (redis/sunion "foo" "set")))
  (is (= #{} (redis/sunion "nonexistent")))
  (redis/sadd "set1" "one")
  (redis/sadd "set1" "four")
  (is (= #{"one" "two" "three"} (redis/sunion "set")))
  (is (= #{"one" "two" "three" "four"} (redis/sunion "set" "set1")))
  (is (= #{"one" "two" "three" "four"} (redis/sunion "set" "set1" "nonexistent"))))

(deftest sunionstore
  (redis/sunionstore "foo" "set")
  (is (= #{"one" "two" "three"} (redis/smembers "foo")))
  (redis/sadd "set1" "one")
  (redis/sadd "set1" "four")
  (redis/sunionstore "newset" "set" "set1")
  (is (= #{"one" "two" "three" "four"} (redis/smembers "newset"))))

(deftest sdiff
  (is (thrown? Exception (redis/sdiff "foo" "set")))
  (is (= #{} (redis/sdiff "nonexistent")))
  (redis/sadd "set1" "one")
  (redis/sadd "set1" "four")
  (is (= #{"one" "two" "three"} (redis/sdiff "set")))
  (is (= #{"two" "three"} (redis/sdiff "set" "set1")))
  (is (= #{"two" "three"} (redis/sdiff "set" "set1" "nonexistent"))))

(deftest sdiffstore
  (redis/sdiffstore "foo" "set")
  (is (= #{"one" "two" "three"} (redis/smembers "foo")))
  (redis/sadd "set1" "one")
  (redis/sadd "set1" "four")
  (redis/sdiffstore "newset" "set" "set1")
  (is (= #{"two" "three"} (redis/smembers "newset"))))

(deftest smembers
  (is (thrown? Exception (redis/smembers "foo")))
  (is (= #{"one" "two" "three"} (redis/smembers "set"))))

(deftest srandmember
  (is (contains? #{"one" "two" "three"} (redis/srandmember "set"))))

;;
;; ZSet commands
;;
(deftest zadd
  (is (thrown? Exception (redis/zadd "foo" 1 "bar")))
  (is (= true (redis/zadd "zset" 3.141592 "foo")))
  (is (= true (redis/zadd "zset" -42 "bar")))
  (is (= true (redis/zadd "zset" 2393845792384752345239485723984534589739284579348.349857983457398457934857 "baz")))
  (is (= ["bar" "foo"] (redis/zrange "zset" 0 1))))

(deftest zrem
  (is (thrown? Exception (redis/zrem "foo" "bar")))
  (is (= false (redis/zrem "zset" "foobar")))
  (redis/zadd "zset" 1.0 "one")
  (redis/zadd "zset" 2.0 "two")
  (redis/zadd "zset" 3.0 "three")
  (is (= true (redis/zrem "zset" "two")))
  (is (= ["one" "three"] (redis/zrange "zset" 0 1))))

(deftest zincrby
  (is (thrown? Exception (redis/zincrby "foo")))
  (is (= 3.141592 (redis/zincrby "zset" 3.141592 "value")))
  (is (= 42.141593) (redis/zincrby "zset" 42.00001 "value"))
  (is (= 3.141592) (redis/zincrby "zset" -42.00001 "value")))

(deftest zrank)

(deftest zrevrank)

(deftest zrange
  (is (thrown? Exception (redis/zrange "foo")))
  (is (= [] (redis/zrange "zset" 0 99)))
  (redis/zadd "zset" 12349809.23873948579348750 "one")
  (redis/zadd "zset" -42 "two")
  (redis/zadd "zset" 3.141592 "three")
  (is (= [] (redis/zrange "zset" -1 -2)))
  (is (= ["two" "three" "one"] (redis/zrange "zset" 0 2)))
  (is (= ["three" "one"] (redis/zrange "zset" 1 2))))

(deftest zrevrange
  (is (thrown? Exception (redis/zrevrange "foo")))
  (is (= [] (redis/zrevrange "zset" 0 99)))
  (redis/zadd "zset" 12349809.23873948579348750 "one")
  (redis/zadd "zset" -42 "two")
  (redis/zadd "zset" 3.141592 "three")
  (is (= [] (redis/zrevrange "zset" -1 -2)))
  (is (= ["one" "three" "two"] (redis/zrevrange "zset" 0 2)))
  (is (= ["three" "two"] (redis/zrevrange "zset" 1 2))))

(deftest zrangebyscore
  (is (thrown? Exception (redis/zrangebyscore "foo")))
  (is (= [] (redis/zrangebyscore "zset" 0 99)))
  (redis/zadd "zset" 1.0 "one")
  (redis/zadd "zset" 2.0 "two")
  (redis/zadd "zset" 3.0 "three")
  (is (= [] (redis/zrangebyscore "zset" -42 0.99)))
  (is (= ["two"] (redis/zrangebyscore "zset" 1.1 2.9)))
  (is (= ["two" "three"] (redis/zrangebyscore "zset" 1.0000001 3.00001))))

(deftest zremrangebyrank)

(deftest zremrangebyscore
  (is (thrown? Exception (redis/zremrangebyscore "foo")))
  (is (= 0 (redis/zremrangebyscore "zset" 0 42.4)))
  (redis/zadd "zset" 1.0 "one")
  (redis/zadd "zset" 2.0 "two")
  (redis/zadd "zset" 3.0 "three")
  (is (= 1 (redis/zremrangebyscore "zset" 2.0 2.999999))))

(deftest zcard
  (is (thrown? Exception (redis/zcard "foo")))
  (is (= 0 (redis/zcard "zset")))
  (redis/zadd "zset" 1.0 "one")
  (is (= 1 (redis/zcard "zset"))))

(deftest zscore
  (is (thrown? Exception (redis/zscore "foo")))
  (redis/zadd "zset" 3.141592 "pi")
  (is (= 3.141592 (redis/zscore "zset" "pi")))
  (redis/zadd "zset" -42 "neg")
  (is (= -42.0 (redis/zscore "zset" "neg"))))


;;
;; Hash commands
;;
(deftest hset
  (is (thrown? Exception (redis/hset "foo" "baz" "poe")))
  (redis/hset "bar" "foo" "hoge")
  (is (= "hoge" (redis/hget "bar" "foo")))

(deftest hget
  (is (= nil (redis/hget "bar" "baz")))
  (is (= "bar" (redis/hget "hash" "two"))))

(deftest hsetnx)

(deftest hmset
  (is (thrown? Exception (redis/hmset "key1" "field1"))) 
  (is (thrown? Exception (redis/hmset "key" "field" "value" "feild1")))
  (redis/hmset "key1" "field1" "value1" "field2" "value2" "field3" "value3")
  (is (= ["value1" "value2" "value3"] (redis/hvals "key1"))))

(deftest hmget
  (is (= ["foo"] (redis/hmget "hash" "one")))
  (is (= ["bar" "baz"] (redis/hmget "hash" "two" "three"))))

(deftest hincrby
  (is (= 42 (redis/hincrby "non-exist-key" "non-exist-field" 42)))
  (is (thrown? Exception (redis/hincrby "foo" "bar" 0)))
  (is (= 0 (redis/hincrby "key1" "field1" 0))))
  (is (= 5 (redis/hincrby "key1" "field1" 5))))

(deftest hexists
  (is (= true (redis/hexists "hash" "one")))
  (is (= false (redis/hexists "non-exist-key" "non-exist-field"))))

(deftest hdel
  (is (= false (redis/hdel "non-exist-key" "non-exist-field")))
  (is (= true (redis/hdel "hash" "three")))
  (is (= nil  (redis/hget "hash" "three"))))

(deftest hlen
  (is (thrown? Exception (redis/hlen "foo")))
  (is (= 0 (redis/hlen "newhash")))
  (is (= 3 (redis/hlen "hash"))))

(deftest hkeys
  (is (= [] (redis/hkeys "noexistent")))
  (is (= ["one" "two" "three"] (redis/hkeys "hash")))
  (redis/hset "hash" "four" "hoge")
  (is (= 4 (count (redis/hkeys "hash")))))

(deftest hvals
  (is (= [] (redis/hvals "noexistent")))
  (is (= ["foo" "bar" "baz"] (redis/hvals "hash")))
  (redis/hdel "hash" "two")
  (is (= ["foo" "baz"] (redis/hvals "hash"))))

(deftest hgetall
  (is (= {} (redis/hgetall "noexistent")))
  (is (= {"one" "foo"
          "two" "bar"
          "three" "baz"}
         (redis/hgetall "hash")))
  (redis/hdel "hash" "one")
  (is (= {"two" "bar"
          "three" "baz"}
         (redis/hgetall "hash"))))

;;
;; Redis Transactions: MULTI/EXEC/DISCARD/WATCH/UNWATCH
;;
(deftest multi-exec
  (redis/set "key" "value")
  (redis/multi)
  (is (= "QUEUED" (redis/set "key" "blahonga")))
  (redis/exec)
  (is (= "blahonga" (redis/get "key"))))

(deftest multi-discard
  (redis/set "key" "value")
  (redis/multi)
  (is (= "QUEUED" (redis/set "key" "blahonga")))
  (redis/discard)
  (is (= "value" (redis/get "key"))))

(deftest atomically
  (redis/set "key" "value")
  (is (= ["OK" "OK" "blahong"]
       (redis/atomically
        (redis/set "key" "blahonga")
        (redis/set "key2" "blahong")
        (redis/get "key2"))))
  (is (= "blahonga" (redis/get "key"))))

(deftest atomically-with-exception
  (redis/set "key" "value")
  (is (thrown? Exception 
               (redis/atomically
                (redis/set "key" "blahonga")
                (throw (Exception. "Fail"))
                (redis/set "key2" "blahong"))))
  (is (= "value" (redis/get "key"))))

;; No tests for WATCH/UNWATCH yet. Waiting for stable Redis 2.1 release.

;;
;; Sorting
;;
(deftest sort
  (redis/lpush "ids" 1)
  (redis/lpush "ids" 4)
  (redis/lpush "ids" 2)
  (redis/lpush "ids" 3)
  (redis/set "object_1" "one")
  (redis/set "object_2" "two")
  (redis/set "object_3" "three")
  (redis/set "object_4" "four")
  (redis/set "name_1" "Derek")
  (redis/set "name_2" "Charlie")
  (redis/set "name_3" "Bob")
  (redis/set "name_4" "Alice")

  (is (= ["one" "two" "three"]
         (redis/sort "list")))
  (is (= ["one" "three" "two"]
         (redis/sort "list" :alpha)))
  (is (= ["1" "2" "3" "4"]
         (redis/sort "ids")))
  (is (= ["1" "2" "3" "4"]
         (redis/sort "ids" :asc)))
  (is (= ["4" "3" "2" "1"]
         (redis/sort "ids" :desc)))
  (is (= ["1" "2"]
         (redis/sort "ids" :asc :limit 0 2)))
  (is (= ["4" "3"]
         (redis/sort "ids" :desc :limit 0 2)))
  (is (= ["4" "3" "2" "1"]
         (redis/sort "ids" :by "name_*" :alpha)))
  (is (= ["one" "two" "three" "four"]
         (redis/sort "ids" :get "object_*")))
  (is (= ["one" "two"]
           (redis/sort "ids"
                       :by "name_*"
                       :alpha
                       :limit 0 2
                       :desc
                       :get "object_*")))
  (redis/sort "ids"
              :by "name_*"
              :alpha
              :limit 0 2
              :desc
              :get "object_*"
              :store "result")
  (is (= ["one" "two"] (redis/lrange "result" 0 -1))))





;; ;;
;; ;; Persistence commands
;; ;;
;; (deftest save
;;   (redis/save))

;; (deftest bgsave
;;   (redis/bgsave))

;; (deftest bgrewriteaof
;;   (redis/bgrewriteaof))

;; (deftest lastsave
;;   (let [ages-ago (new java.util.Date (long 1))]
;;     (is (.before ages-ago (redis/lastsave)))))




