(ns redis.vars
  (:use [redis.connection-pool :only (make-connection-pool)]))
;;;; Vars

(def ^:dynamic
  #^{:doc "Bound to an implementation of RedisConnectionPool"}
  *pool*
  (make-connection-pool :lifo false
                        :test-on-borrow true))

(def ^:dynamic #^{:doc "Bound to an implementation of RedisChannel"
                 }
  *channel* nil)

