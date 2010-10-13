(ns graphecta.core
  (:require [clojure.contrib.string :as string]
            [clj-http.client :as client]
            (org.danlarkin [json :as json])
            [clojure.contrib.seq-utils :as seq-utils]))

(def *SOCIALGRAPH-BASE-URL* "http://socialgraph.apis.google.com/")
(def *SOCIALGRAPH-LOOKUP-URL* (str *SOCIALGRAPH-BASE-URL* "lookup"))
(def *SOCIALGRAPH-OPTIONS* "edo=true&edi=true&fme=true")
(def *TWITTER-BASE-URL* "http://twitter.com/")

(def *social-graph* (ref #{}))

(defn add-edge-to-social-graph
  [edge]
  (dosync
   (alter *social-graph*  conj edge)))

(defn add-nodes-to-social-graph
  [nodes]
  (dosync
   (alter *social-graph* into nodes)))

(defn- parse-raw-response
  [raw-response]
  (json/decode-from-str (:body raw-response)))

(defn- extract-twitter-graph
  [twitter-profile-url social-graph]
  ((keyword twitter-profile-url) (:nodes social-graph)))

(defn graph-from-twitter
  [twitter-username]
  (let [twitter-profile-url (str *TWITTER-BASE-URL* twitter-username)
        raw-response (client/get (str *SOCIALGRAPH-LOOKUP-URL*
                                      "?"
                                      *SOCIALGRAPH-OPTIONS*
                                      "&q=" twitter-profile-url))
        social-graph (parse-raw-response raw-response)]
    (extract-twitter-graph twitter-profile-url social-graph)))

(defn build-follows-graph
  [twitter-user-name follows]
  (pmap #(vector twitter-user-name %) follows))

(defn build-followers-graph
  [twitter-user-name followers]
  (pmap #(vector % twitter-user-name) followers))

(defn on-node-update
  [key reference old-state new-state]
  (pmap #(add-edge-to-social-graph %) new-state))

(defn on-node-error
  [a error]
  (println (str a " - " error)))

(defn build-agent
  [twitter-user-name]
  (let [a (agent (string/as-str twitter-user-name))]
    (set-error-handler! a on-node-error)
    (set-error-mode! a :continue)
    (add-watch a "graph" on-node-update)))

(defn- element-extractor
  [twitter-graph key]
  (map #(build-agent %) (keys (key twitter-graph))))

(defn nodes-referenced-by
  [twitter-graph]
  (element-extractor twitter-graph :nodes_referenced_by))

(defn nodes-referenced
  [twitter-graph]
  (element-extractor twitter-graph :nodes_referenced))

(defn build-social-graph-from-twitter
  [twitter-user-name]
  (let [graph (graph-from-twitter twitter-user-name)
        followers (nodes-referenced-by graph)
        follows (nodes-referenced graph)
        twitter-user (agent twitter-user-name)]
    (into (build-followers-graph twitter-user followers)
          (build-follows-graph twitter-user follows))))

(defn build-social-graph
  [nodes]
  (doseq [partitioned-nodes (seq-utils/partition-all 5 nodes)]
    (let [agents (map #(send-off % build-social-graph-from-twitter) partitioned-nodes)]
      (apply await agents))))

(defn graph-to-search-candidates
  [edges]
  (let [candidates (ref #{})]
    (doseq [edge edges]
      (dosync
       (alter candidates into edge)))
    candidates))