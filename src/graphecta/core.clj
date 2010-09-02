(ns graphecta.core
  (:require [clojure.contrib.string :as string]
            [clj-http.client :as client]
            (org.danlarkin [json :as json])))

(def *SOCIALGRAPH-BASE-URL* "http://socialgraph.apis.google.com/")
(def *SOCIALGRAPH-LOOKUP-URL* (str *SOCIALGRAPH-BASE-URL* "lookup"))
(def *SOCIALGRAPH-OPTIONS* "edo=true&edi=true&fme=true")
(def *TWITTER-BASE-URL* "http://twitter.com/")

(defn- parse-raw-response
  [raw-response]
  (json/decode-from-str (:body raw-response)))

(defn- extract-twitter-graph
  [twitter-profile-url social-graph]
  ((keyword twitter-profile-url) (:nodes social-graph)))

(defn graph-from-twitter
  [twitter-username]
  (let [twitter-profile-url (str *TWITTER-BASE-URL* twitter-username)
        raw-response (client/get (str *SOCIALGRAPH-LOOKUP-URL* "?" *SOCIALGRAPH-OPTIONS* "&q=" twitter-profile-url))
        social-graph (parse-raw-response raw-response)]
    (extract-twitter-graph twitter-profile-url social-graph)))

(defn- element-extractor
  [twitter-graph key]
  (map string/as-str (keys (key twitter-graph))))

(defn nodes-referenced-by
  [twitter-graph]
  (element-extractor twitter-graph :nodes_referenced_by))

(defn nodes-referenced
  [twitter-graph]
  (element-extractor twitter-graph :nodes_referenced))
