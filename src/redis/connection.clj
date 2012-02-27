(ns redis.connection
  (:use [redis.protocol :only (write-to-buffer write-to-stream make-inline-command)])
  (:import [java.net Socket]
           [java.io BufferedInputStream ByteArrayOutputStream]))

;;; Protocols

(defprotocol RedisConnectionPool
  (get-connection [pool connection-spec])
  (release-connection [pool connection]
                      [pool connection exception]))

(defprotocol RedisConnection
  (get-server-spec [connection])
  (connected? [connection])
  (close [connection])
  (input-stream [connection])
  (output-stream [connection]))


;;; Macros

(defmacro with-connection [name pool server-spec & body]
  `(let [~name (get-connection ~pool ~server-spec)]
     (try
       (let [result# (do ~@body)]
         (release-connection ~pool ~name)
         result#)
       (catch Exception e#
         (release-connection ~pool ~name e#)
         (throw e#)))))


;;; Implementations
(defn send-command-and-read-reply
  [connection command]
  (let [buf (ByteArrayOutputStream.)
        in (input-stream connection)
        out (output-stream connection)]
    (write-to-buffer command buf)
    (write-to-stream buf out)
    (redis.protocol/read-reply in)))

(defn connection-alive? [connection]
  "Determines whether the connection is still alive"
  (let [ping (make-inline-command "PING")
        resp (send-command-and-read-reply connection ping)]
    (= resp "PONG")))

(defrecord Connection [#^Socket socket server-spec]
  RedisConnection
  (get-server-spec [this] server-spec)
  (connected? [this] (connection-alive? this))
  (close [this] (.close socket))
  (input-stream [this] (BufferedInputStream. (.getInputStream socket)))
  (output-stream [this] (.getOutputStream socket)))

(def default-connection-spec {:host "127.0.0.1"
                              :port 6379
                              :timeout 5000
                              :password nil
                              :db 0})
;TODO: maybe change this on the connection-alive? side instead?
(defn make-connection [server-spec]
  (let [spec (merge default-connection-spec server-spec)
        {:keys [host port timeout password db]} spec
        socket (Socket. #^String host #^Integer port)]
    (doto socket
      (.setTcpNoDelay true)
      (.setSoTimeout timeout))
    (let [connection (Connection. socket server-spec)]
      (if-not (nil? password)
        (send-command-and-read-reply connection (make-inline-command (str "auth " password))))
      (if-not (nil? db)
        (let [resp (send-command-and-read-reply connection
                                           (make-inline-command (str "SELECT " db)))]
          (if-not (= "OK" resp)
            (throw (Exception. (str "Error while selecting db, invalid response: " resp))))))
      connection)))

(defrecord NonPooledConnectionPool []
  RedisConnectionPool
  (get-connection [this connection-spec]
    (make-connection connection-spec))
  (release-connection [this connection]
    (close connection))
  (release-connection [this connection exception]
    (close connection)))

(defn make-non-pooled-connection-pool []
  (NonPooledConnectionPool.))

