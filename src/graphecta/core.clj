(ns graphecta.core
  (:require [clj-http.client :as client]))

(def *SOCIALGRAPH-BASE-URL* "http://socialgraph.apis.google.com/")
(def *SOCIALGRAPH-LOOKUP-URL* (str *SOCIALGRAPH-BASE-URL* "lookup"))
(def *SOCIALGRAPH-OPTIONS* "edo=true&edi=true&fme=true&pretty=true")
(def *TWITTER-BASE-URL* "http://twitter.com/")

(defn graph-from-twitter
  [twitter-username]
  (client/get (str *SOCIALGRAPH-LOOKUP-URL* "?" *SOCIALGRAPH-OPTIONS* "&q=" *TWITTER-BASE-URL* twitter-username)))