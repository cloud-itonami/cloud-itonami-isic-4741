(ns techretail.export-test
  "Audit-package export contract -- social/regulatory hand-off shape."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [langgraph.graph :as g]
            [techretail.export :as export]
            [techretail.operation :as op]
            [techretail.store :as store]))

(def operator {:actor-id "op-1" :actor-role :retail-operations-approver :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn- seed-with-one-fulfillment-and-one-certificate []
  (let [db (store/seed-db)
        actor (op/build db)]
    (exec! actor "v" {:op :consumer-protection-rules/verify :subject "order-1"})
    (approve! actor "v")
    (exec! actor "f" {:op :actuation/fulfill-order :subject "order-1"})
    (approve! actor "f")
    (exec! actor "s" {:op :trade-in-condition/screen :subject "unit-1"})
    (approve! actor "s")
    (exec! actor "w" {:op :robotics/simulate-data-wipe :subject "unit-1"})
    (approve! actor "w")
    (exec! actor "d" {:op :robotics/simulate-drop-test :subject "unit-1"})
    (approve! actor "d")
    (exec! actor "c" {:op :actuation/issue-sanitization-certificate :subject "unit-1"})
    (approve! actor "c")
    db))

(deftest audit-package-shape
  (let [db (seed-with-one-fulfillment-and-one-certificate)
        pkg (export/audit-package db)]
    (is (= "4741" (:isic pkg)))
    (is (= "cloud-itonami-isic-4741" (:business-id pkg)))
    (is (= :edn-maps (:format pkg)))
    (is (pos? (get-in pkg [:counts :ledger])))
    (is (= 1 (get-in pkg [:counts :fulfillments])))
    (is (= 1 (get-in pkg [:counts :sanitization-certificates])))
    (is (some #(= "order-1" (:id %)) (:orders pkg)))
    (is (true? (:order-fulfilled?
                (first (filter #(= "order-1" (:id %)) (:orders pkg))))))
    (is (true? (:sanitization-certified?
                (first (filter #(= "unit-1" (:id %)) (:trade-in-units pkg))))))))

(deftest csv-bundle-has-headers-and-rows
  (let [db (seed-with-one-fulfillment-and-one-certificate)
        bundle (export/package->csv-bundle db)]
    (is (every? bundle ["orders.csv" "trade-in-units.csv" "ledger.csv" "fulfillments.csv" "sanitization-certificates.csv"]))
    (is (str/starts-with? (get bundle "orders.csv") "id,customer-name,"))
    (is (re-find #"order-1" (get bundle "orders.csv")))
    (is (re-find #"JPN-FUL-000000" (get bundle "fulfillments.csv")))
    (is (re-find #"JPN-COD-000000" (get bundle "sanitization-certificates.csv")))
    (is (re-find #":actuation/fulfill-order" (get bundle "ledger.csv")))))

(deftest empty-store-export-is-usable
  (let [db (store/seed-db)
        pkg (export/audit-package db)
        bundle (export/package->csv-bundle db)]
    (is (= 0 (get-in pkg [:counts :fulfillments])))
    (is (= 4 (get-in pkg [:counts :orders])))
    (is (= 5 (get-in pkg [:counts :trade-in-units])))
    (is (str/includes? (get bundle "ledger.csv") "seq,t,op"))))
