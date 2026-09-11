(ns techretail.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a
  configuration change, not a rewrite -- see `testlab.store-contract-
  test` (`cloud-itonami-isic-7120`) for the same pattern on a sibling
  actor, and this actor's own `techretail.store` ns docstring for why
  there are TWO entity directories (order / trade-in-unit) instead of
  one."
  (:require [clojure.test :refer [deftest is testing]]
            [techretail.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Kenji Sato" (:customer-name (store/order s "order-1"))))
      (is (= "JPN" (:jurisdiction (store/order s "order-1"))))
      (is (= 128000 (:order-total-actual (store/order s "order-1"))))
      (is (= "unit-1" (:trade-in-unit-id (store/order s "order-1"))))
      (is (nil? (:trade-in-unit-id (store/order s "order-2"))))
      (is (false? (:order-fulfilled? (store/order s "order-1"))))
      (is (= ["order-1" "order-2" "order-3" "order-4"] (mapv :id (store/all-orders s))))
      (is (= "Sakura NoteBook Pro 14" (:device-model (store/trade-in-unit s "unit-1"))))
      (is (false? (:grading-defect-unresolved? (store/trade-in-unit s "unit-1"))))
      (is (true? (:grading-defect-unresolved? (store/trade-in-unit s "unit-2"))))
      (is (true? (:sanitization-sim-verified? (store/trade-in-unit s "unit-3"))))
      (is (= 4 (:post-wipe-recoverable-sectors-found (store/trade-in-unit s "unit-3"))))
      (is (false? (:sanitization-certified? (store/trade-in-unit s "unit-1"))))
      (is (= ["unit-1" "unit-2" "unit-3" "unit-4" "unit-5"] (mapv :id (store/all-trade-in-units s))))
      (testing "ADR-2607152000: real physics-2d drop/shock-test telemetry, seeded via techretail.robotics/drop-test-telemetry-for"
        (is (= :laptop (:device-class (store/trade-in-unit s "unit-1"))))
        (is (= 1.4 (:device-mass-kg (store/trade-in-unit s "unit-1"))))
        (is (number? (:sim-impact-decel-g (store/trade-in-unit s "unit-1"))))
        (is (< (:sim-impact-decel-g (store/trade-in-unit s "unit-1")) 400.0)
            "unit-1's laptop-class give clears decel-ceiling-g with real margin")
        (is (false? (:drop-test-sim-verified? (store/trade-in-unit s "unit-1"))))
        (is (true? (:drop-test-sim-verified? (store/trade-in-unit s "unit-5"))))
        (is (= :desktop-mini (:device-class (store/trade-in-unit s "unit-5"))))
        (is (> (:sim-impact-decel-g (store/trade-in-unit s "unit-5")) 400.0)
            "unit-5's real re-run simulated impact-decel-g genuinely exceeds decel-ceiling-g"))
      (is (nil? (store/consumer-protection-verification-of s "order-1")))
      (is (nil? (store/trade-in-condition-screen-of s "unit-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/fulfillment-history s)))
      (is (= [] (store/sanitization-certificate-history s)))
      (is (zero? (store/next-fulfillment-sequence s "JPN")))
      (is (zero? (store/next-sanitization-sequence s "JPN")))
      (is (false? (store/order-already-fulfilled? s "order-1")))
      (is (false? (store/trade-in-unit-already-sanitization-certified? s "unit-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :order/upsert
                                 :value {:id "order-1" :customer-name "Kenji Sato (updated)"}})
        (is (= "Kenji Sato (updated)" (:customer-name (store/order s "order-1"))))
        (is (= 128000 (:order-total-actual (store/order s "order-1"))) "order-total-actual preserved")
        (store/commit-record! s {:effect :trade-in-unit/upsert
                                 :value {:id "unit-1" :device-model "Sakura NoteBook Pro 14 (relisted)"}})
        (is (= "Sakura NoteBook Pro 14 (relisted)" (:device-model (store/trade-in-unit s "unit-1"))))
        (is (false? (:grading-defect-unresolved? (store/trade-in-unit s "unit-1"))) "grading-defect-unresolved? preserved"))
      (testing "verification / screen payloads commit and read back"
        (store/commit-record! s {:effect :consumer-protection-verification/set :path ["order-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/consumer-protection-verification-of s "order-1")))
        (store/commit-record! s {:effect :trade-in-condition-screen/set :path ["unit-1"]
                                 :payload {:trade-in-unit-id "unit-1" :verdict :resolved}})
        (is (= {:trade-in-unit-id "unit-1" :verdict :resolved} (store/trade-in-condition-screen-of s "unit-1"))))
      (testing "order fulfillment drafts a fulfillment record and advances the sequence"
        (store/commit-record! s {:effect :order/mark-fulfilled :path ["order-1"]})
        (is (= "JPN-FUL-000000" (get (first (store/fulfillment-history s)) "record_id")))
        (is (= "order-fulfillment-draft" (get (first (store/fulfillment-history s)) "kind")))
        (is (true? (:order-fulfilled? (store/order s "order-1"))))
        (is (= 1 (count (store/fulfillment-history s))))
        (is (= 1 (store/next-fulfillment-sequence s "JPN")))
        (is (true? (store/order-already-fulfilled? s "order-1")))
        (is (false? (store/order-already-fulfilled? s "order-3"))))
      (testing "sanitization-certificate issuance drafts a certificate record and advances the sequence"
        (store/commit-record! s {:effect :trade-in-unit/mark-sanitization-certified :path ["unit-1"]})
        (is (= "JPN-COD-000000" (get (first (store/sanitization-certificate-history s)) "record_id")))
        (is (= "sanitization-certificate-draft" (get (first (store/sanitization-certificate-history s)) "kind")))
        (is (true? (:sanitization-certified? (store/trade-in-unit s "unit-1"))))
        (is (= 1 (count (store/sanitization-certificate-history s))))
        (is (= 1 (store/next-sanitization-sequence s "JPN")))
        (is (true? (store/trade-in-unit-already-sanitization-certified? s "unit-1")))
        (is (false? (store/trade-in-unit-already-sanitization-certified? s "unit-4"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/order s "nope")))
    (is (nil? (store/trade-in-unit s "nope")))
    (is (= [] (store/all-orders s)))
    (is (= [] (store/all-trade-in-units s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/fulfillment-history s)))
    (is (= [] (store/sanitization-certificate-history s)))
    (is (zero? (store/next-fulfillment-sequence s "JPN")))
    (is (zero? (store/next-sanitization-sequence s "JPN")))
    (store/with-orders s {"x" {:id "x" :customer-name "c" :jurisdiction "JPN"
                               :items [{:sku "s" :qty 1 :unit-price 100}]
                               :order-total-actual 100 :order-total-tolerance 0.01
                               :trade-in-unit-id nil :order-fulfilled? false :status :intake}})
    (is (= "c" (:customer-name (store/order s "x"))))
    (store/with-trade-in-units s {"y" {:id "y" :device-model "d" :device-serial "e" :jurisdiction "JPN"
                                       :grading-defect-unresolved? false
                                       :post-wipe-recoverable-sectors-found 0
                                       :sanitization-sim-verified? false :sanitization-sim-record nil
                                       :sanitization-certified? false :status :intake}})
    (is (= "d" (:device-model (store/trade-in-unit s "y"))))))
