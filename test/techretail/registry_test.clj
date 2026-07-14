(ns techretail.registry-test
  (:require [clojure.test :refer [deftest is]]
            [techretail.registry :as r]))

;; ----------------------------- order-total-mismatch? -----------------------------

(deftest not-mismatched-when-total-reconciles
  (is (not (r/order-total-mismatch? {:order-total-actual 128000
                                     :items [{:qty 1 :unit-price 128000}]})))
  (is (not (r/order-total-mismatch? {:order-total-actual 24000
                                     :items [{:qty 3 :unit-price 8000}]}))))

(deftest mismatched-when-total-and-line-items-disagree
  (is (r/order-total-mismatch? {:order-total-actual 158000
                                :items [{:qty 1 :unit-price 128000}]}))
  (is (r/order-total-mismatch? {:order-total-actual 90000
                                :items [{:qty 2 :unit-price 40000}]})))

(deftest mismatch-respects-its-own-recorded-tolerance
  (is (not (r/order-total-mismatch? {:order-total-actual 128000.005
                                     :items [{:qty 1 :unit-price 128000}]
                                     :order-total-tolerance 0.01})))
  (is (r/order-total-mismatch? {:order-total-actual 128000.02
                                :items [{:qty 1 :unit-price 128000}]
                                :order-total-tolerance 0.01})))

(deftest mismatch-is-false-on-missing-or-malformed-fields
  (is (not (r/order-total-mismatch? {})))
  (is (not (r/order-total-mismatch? {:order-total-actual 100})))
  (is (not (r/order-total-mismatch? {:order-total-actual 100 :items []})))
  (is (not (r/order-total-mismatch? {:order-total-actual 100 :items [{:qty 1}]}))
      "an item missing :unit-price never fabricates a computed total"))

;; ----------------------------- register-order-fulfillment -----------------------------

(deftest fulfillment-is-a-draft-not-a-real-shipment
  (let [result (r/register-order-fulfillment "order-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest fulfillment-assigns-fulfillment-number
  (let [result (r/register-order-fulfillment "order-1" "JPN" 7)]
    (is (= (get result "fulfillment_number") "JPN-FUL-000007"))
    (is (= (get-in result ["record" "order_id"]) "order-1"))
    (is (= (get-in result ["record" "kind"]) "order-fulfillment-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest fulfillment-validation-rules
  (is (thrown? Exception (r/register-order-fulfillment "" "JPN" 0)))
  (is (thrown? Exception (r/register-order-fulfillment "order-1" "" 0)))
  (is (thrown? Exception (r/register-order-fulfillment "order-1" "JPN" -1))))

;; ----------------------------- register-sanitization-certificate -----------------------------

(deftest certificate-is-a-draft-not-real-certification
  (let [result (r/register-sanitization-certificate "unit-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest certificate-assigns-certificate-number
  (let [result (r/register-sanitization-certificate "unit-1" "JPN" 3)]
    (is (= (get result "certificate_number") "JPN-COD-000003"))
    (is (= (get-in result ["record" "trade_in_unit_id"]) "unit-1"))
    (is (= (get-in result ["record" "kind"]) "sanitization-certificate-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest certificate-validation-rules
  (is (thrown? Exception (r/register-sanitization-certificate "" "JPN" 0)))
  (is (thrown? Exception (r/register-sanitization-certificate "unit-1" "" 0)))
  (is (thrown? Exception (r/register-sanitization-certificate "unit-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-order-fulfillment "order-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-order-fulfillment "order-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-FUL-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-FUL-000001" (get-in hist2 [1 "record_id"])))))
