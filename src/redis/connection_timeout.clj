(ns redis.connection-timeout
  (:require redis.vars))

(defn get-socket
  "Returns the socket for the current server connection"
  []
  (-> redis.vars/*channel* :connection :socket))

(defn set-timeout [timeout]
  "Sets the socket timeout on the current server connection"
  (-> (get-socket) (.setSoTimeout timeout)))

(defn get-timeout []
  (-> (get-socket) (.getSoTimeout)))

(defmacro with-timeout
  [timeout & body]
  `(let [old-timeout# (get-timeout)]
     (try
       (set-timeout ~timeout)
       ~@body
       (finally
        (set-timeout old-timeout#)))))