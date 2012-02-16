(ns redis.core
  (:refer-clojure :exclude [keys type get set sort])

  ;; (:require [clojure.contrib.ns-utils :only (immigrate) :as contrib])
  (:use [redis.connection :only (with-connection make-non-pooled-connection-pool)]
        [redis.connection-pool :only (make-connection-pool)]
        [redis.channel :only (make-direct-channel)]
        [redis.vars :only (*pool* *channel*)]
        [redis.protocol :only (*return-byte-arrays?* make-multi-bulk-command)]
        [redis.defcommand :only (defcommand defcommands)]

                                        ;        [redis.commands :only (quit)]
        ))


(defmacro with-server
  "Evaluates body in the context of a connection to Redis server
  specified by server-spec.

  server-spec is a map with any of the following keys:
    :host     (\"127.0.0.1\")
    :port     (6379)
    :db       (0)
    :timeout  (5000)
    :password (nil)"
  ([server-spec & body]
     `(with-connection connection# *pool* ~server-spec
        (binding [redis.vars/*channel* (make-direct-channel connection#)]
          ~@body))))

(defmacro as-bytes
  "Wrap a Redis command in this macro to make it return
  byte array(s) instead of string(s)."
  [& body]
  `(binding [*return-byte-arrays?* true]
     ~@body))


;;; Command definitions

;; Utility conversion functions
(defn- int-to-bool [n] (if (integer? n) (< 0 n) n))
(defn- string-to-keyword [s] (keyword s))
(defn- string-to-double [s] (when s (Double/parseDouble s)))
(defn- seq-to-set [seq] (when seq (clojure.core/set seq)))
(defn- seq-to-map [seq] (when seq (apply hash-map seq)))
(defn- crlf-to-hash [s] (apply hash-map (clojure.string/split s (re-pattern ":|\\r\\n"))))

(defcommands
  ;Server commands
  (config      [operation param & value])
  (info        [] :inline crlf-to-hash)
  ; Connection commands
  (quit        [] :inline )
  (auth        [password])
  (ping        [] :inline)
  ; Commands operating on all types
  (exists      [key] int-to-bool)
  (del         [key & keys])
  (type        [key] string-to-keyword)
  (keys        [pattern])
  (randomkey   [] :inline)
  (rename      [oldkey newkey])
  (renamenx    [oldkey newkey] int-to-bool)
  (dbsize      [] :inline)
  (expire      [key seconds] int-to-bool)
  (ttl         [key])
  (select      [db])
  (move        [key dbindex])
  (flushdb     [] :inline)
  (flushall    [] :inline)
  ; String commands
  (get         [key])
  (set         [key val])
  (getset      [key val])
  (mget        [key & keys])
  (setnx       [key val] int-to-bool)
  (setex       [key ttl val])
  (mset        [key val & keyvals])
  (msetnx      [key val & keyvals] int-to-bool)
  (incr        [key])
  (incrby      [key int])
  (decr        [key])
  (decrby      [key int])
  (append      [key val])
  (substr      [key start end])
  ; List commands
  (rpush       [key value])
  (lpush       [key value])
  (llen        [key])
  (lrange      [key start end])
  (ltrim       [key start end])
  (lindex      [key index])
  (lset        [key index value])
  (lrem        [key count value])
  (lpop        [key])
  (rpop        [key])
  (blpop       [key timeout])
  (brpop       [key timeout])
  (rpoplpush   [srckey destkey])
  (brpoplpush   [srckey destkey timeout])
  ; Set commands
  (sadd        [key member] int-to-bool)
  (srem        [key member] int-to-bool)
  (spop        [key])
  (smove       [srckey destkey member] int-to-bool)
  (scard       [key])
  (sismember   [key member] int-to-bool)
  (sinter      [key & keys] seq-to-set)
  (sinterstore [destkey key & keys])
  (sunion      [key & keys] seq-to-set)
  (sunionstore [destkey key & keys])
  (sdiff       [key & keys] seq-to-set)
  (sdiffstore  [destkey key & keys])
  (smembers    [key] seq-to-set)
  (srandmember [key])
  ; Sorted Set commands
  (zadd        [key score member] int-to-bool)
  (zrem        [key member] int-to-bool)
  (zincrby     [key increment member] string-to-double)
  (zrank       [key member])
  (zrevrank    [key member])
  (zrange      [key start end & options])
  (zrevrange   [key start end & options])
  (zrangebyscore [key start end & options])
  (zrevrangebyscore [key start end & options])
  (zremrangebyrank [key start end])
  (zremrangebyscore [key start end])
  (zcard       [key])
  (zcount      [key min max])
  (zscore      [key member] string-to-double)
  (zunionstore [destkey numkeys key & other-keys-and-options])
  (zinterstore [destkey numkeys key & other-keys-and-options])
  ; Hash commands
  (hset        [key field val])
  (hget        [key field])
  (hsetnx      [key field val] int-to-bool)
  (hmset       [key field val & fieldvals])
  (hmget       [key field & fields])
  (hincrby     [key field val])
  (hexists     [key field] int-to-bool)
  (hdel        [key field] int-to-bool)
  (hlen        [key])
  (hkeys       [key])
  (hvals       [key])
  (hgetall     [key] seq-to-map)
  ; multi/exec/discard/watch/unwatch
  (multi       [] :inline)
  (exec        [] :inline)
  (discard     [] :inline)
  (watch       [key & keys])
  (unwatch     [] :inline))

;; atomically macro
(defmacro atomically
  "Execute all redis commands in body atomically, ie. sandwiched in a
  MULTI/EXEC statement. If an exception is thrown the EXEC command
  will be terminated by a DISCARD, no operations will be performed and
  the exception will be rethrown."
  [& body]
  `(do
     (multi)
     (try
      (do
        ~@body
        (exec))
      (catch Exception e#
        (discard)
        (throw e#)))))

;; Sort command

(defn- parse-sort-args [args]
  (loop [bulks []
         args args]
    (if (empty? args)
      bulks
      (let [[type & args] args]
        ;; TODO: Check for nil!
        (condp = type
            :by    (let [[pattern & rest] args]
                     (recur (conj bulks "BY" pattern) rest))
            :limit (let [[start end & rest] args]
                     (recur (conj bulks "LIMIT" start end) rest))
            :get   (let [[pattern & rest] args]
                     (recur (conj bulks "GET" pattern) rest))
            :store (let [[key & rest] args]
                     (recur (conj bulks "STORE" key) rest))
            :alpha (recur (conj bulks "ALPHA") args)
            :asc   (recur (conj bulks "ASC") args)
            :desc  (recur (conj bulks "DESC") args)
            (throw (Exception. (str "Error parsing arguments to SORT command: Unknown argument: " type))))))))

(defcommand sort [key & args]
  (with-meta
    (apply make-multi-bulk-command "SORT" key (parse-sort-args args))
    {:redis-keys [key]}))
