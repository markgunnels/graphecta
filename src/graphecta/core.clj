(ns graphecta.core
  (:require [clojure.contrib.string :as string]
            [clj-http.client :as client]
            (org.danlarkin [json :as json])
            [clojure.contrib.seq-utils :as seq-utils]
            [clojure.set :as set]
            [work.core :as work]
            [clojure.contrib.duck-streams :as duck-streams]))

(def *SOCIALGRAPH-BASE-URL* "http://socialgraph.apis.google.com/")
(def *SOCIALGRAPH-LOOKUP-URL* (str *SOCIALGRAPH-BASE-URL* "lookup"))
(def *SOCIALGRAPH-OPTIONS* "edo=true&edi=true&fme=true")
(def *TWITTER-BASE-URL* "http://twitter.com/")

(def *social-graph* (ref #{}))
(def *social-profiles* (ref #{}))

(defn add-profile-to-social-profiles
  [profile]
  (dosync
   (alter *social-profiles*  conj profile)))

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

(defn- element-extractor
  [twitter-graph key]
  (map #(string/as-str %) (keys (key twitter-graph))))

(defn nodes-referenced-by
  [twitter-graph]
  (element-extractor twitter-graph :nodes_referenced_by))

(defn nodes-referenced
  [twitter-graph]
  (element-extractor twitter-graph :nodes_referenced))

(defn build-social-profile-from-twitter
  [twitter-user-name]
  (let [graph (graph-from-twitter twitter-user-name)
        followers (nodes-referenced-by graph)
        follows (nodes-referenced graph)]
    {:user-name twitter-user-name
     :followers followers
     :follows follows}))

(defn build-social-graph-from-profile
  [{:keys [follows followers user-name]}]
  (into (build-followers-graph user-name followers)
        (build-follows-graph user-name follows)))

(defn build-social-graph-from-twitter-user-name
  [twitter-user-name]
  (-> twitter-user-name
      (build-social-profile-from-twitter)
      (build-social-graph-from-profile)))

(defn build-and-add-social-graph-from-twitter-user-name
  [twitter-user-name]
  (-> twitter-user-name
      (build-social-graph-from-twitter-user-name)
      (add-nodes-to-social-graph)))

(defn build-and-add-social-profiles-from-twitter-user-name
  [twitter-user-name]
  (-> twitter-user-name
      (build-social-profile-from-twitter)
      (add-profile-to-social-profiles)))

(defn build-candidates-from-profile
  [{:keys [follows followers user-name]}]
  (set/union (set follows)
             (set followers)))
 
(defn populate-social-graph-from-twitter-user-name
  [twitter-user-name]
  (let [patient-zero-profile (build-social-profile-from-twitter twitter-user-name)
        candidates (build-candidates-from-profile patient-zero-profile)]
    (work/map-work build-and-add-social-graph-from-twitter-user-name (conj candidates twitter-user-name) 2)))

(defn populate-social-profiles-from-twitter-user-name
  [twitter-user-name]
  (let [patient-zero-profile (build-social-profile-from-twitter twitter-user-name)
        candidates (build-candidates-from-profile patient-zero-profile)]
    (work/map-work build-and-add-social-profiles-from-twitter-user-name (conj candidates twitter-user-name) 2)))



;;move these to a new namespace
(defn create-count-datapoint
  [social-profile]
  {:user-name (:user-name social-profile)
   :followers-count (count (:followers social-profile))
   :follows-count (count (:follows social-profile))})

(defn create-count-dataset
  [social-profiles]
  (map #(create-count-datapoint %) social-profiles))

(defn serialize
  "Print a data structure to a file so that we may read it in later."
  [data-structure #^String filename]
  (duck-streams/with-out-writer
    (java.io.File. filename)
    (binding [*print-dup* true] (prn data-structure))))

(defn deserialize [filename]
  (with-open [r (java.io.PushbackReader. (java.io.FileReader. filename))]
    (read r)))
