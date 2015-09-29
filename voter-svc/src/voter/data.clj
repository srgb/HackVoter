(ns voter.data
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.string :refer [blank?]]
            [environ.core :refer [env]]
            [taoensso.faraday :as far]
            [clj-time.core :as time]
 				  	[clojure.tools.logging :refer [warn error]]))

(defn- remove-empty-entries
  [map-entries]
  (into {} (remove #(empty? (str (second %))) map-entries)))

(def client-opts
  (remove-empty-entries
    {:access-key (env :aws-access-key)
     :secret-key (env :aws-secret-key)
     :endpoint (env :dynamo-endpoint)}))

(def hack-table (env :hacks-table))
(def votes-table (env :hack-votes-table))

(defn- table-exists? [name]
  (try
    (let [table (far/describe-table client-opts name)
    			exists (zero? (compare :active (:status table)))]
    	exists)
    (catch Exception _ false)))

(defn- query-table [table query]
  (try
    (far/query client-opts table query)
    (catch Exception _ nil)))

(defn- ensure-hacks-table []
	(far/ensure-table client-opts hack-table [:publicid :s]
  	 {:range-keydef [:editorid :s]
  	 	:throughput {:read (env :readalloc-hack) :write (env :writealloc-hack)} :block? true }))

(defn- ensure-votes-table []
	(far/ensure-table client-opts votes-table [:publicid :s]
  	 {:range-keydef [:userid :s]
  	 	:throughput {:read (env :readalloc-vote) :write (env :writealloc-vote)} :block? true }))

(defn store-hack [editorid publicid title description creator imgurl]
	(ensure-hacks-table)
	(prn (str "store-hack editorid=" editorid " publicid=" publicid " title=" title " description=" description " creator=" creator " imgurl=" imgurl))
	(far/put-item client-opts hack-table {:publicid publicid
																 				:editorid editorid
																 				:title title
																 				:description description
																 				:creator creator
																 				:imgurl imgurl
																 				:lastupdate (str (time/now))}))

(defn store-vote [userid publicid votes]
	(ensure-votes-table)
	(prn (str "store-vote userid=" userid " publicid=" publicid))
	(far/put-item client-opts votes-table {:userid userid
																				 :publicid publicid
																				 :votes votes
																 				 :lastupdate (str (time/now))}))

; primary access by publicid
(defn get-hack-by-publicid [publicid]
	(query-table hack-table {:publicid [:eq publicid]}))

(defn get-hack-by-editorid [editorid]
	(first (filter (fn[x] (== 0 (compare editorid (:editorid x)))) (far/scan client-opts hack-table))))

; primary access by publicid
(defn get-votes-by-publicid [publicid]
	(query-table votes-table {:publicid [:eq publicid]}))

(defn get-votes-by-userid [userid]
	(filter (fn[x] (== 0 (compare userid (:userid x)))) (far/scan client-opts votes-table)))

(defn get-config-items []
	{	:currency (env :currency)
		:allocation (env :allocation)
		:maxspend (env :max-spend) })

(defn list-hacks []
	(if (table-exists? hack-table)
		(far/scan client-opts hack-table)
	[]))
