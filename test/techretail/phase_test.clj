(ns techretail.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:actuation/fulfill-order`/`:actuation/issue-
  sanitization-certificate` must NEVER be a member of any phase's
  `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [techretail.phase :as phase]))

(deftest fulfill-order-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real order fulfillment"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/fulfill-order))
          (str "phase " n " must not auto-commit :actuation/fulfill-order")))))

(deftest issue-sanitization-certificate-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-commits a real Certificate of Data Destruction"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/issue-sanitization-certificate))
          (str "phase " n " must not auto-commit :actuation/issue-sanitization-certificate")))))

(deftest trade-in-condition-screen-never-auto-at-any-phase
  (testing "screening carries no direct capital risk, but is still never auto-eligible, matching every sibling screening op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :trade-in-condition/screen))
          (str "phase " n " must not auto-commit :trade-in-condition/screen")))))

(deftest robotics-simulate-data-wipe-never-auto-at-any-phase
  (testing "the robot certified data-wipe mission carries no direct capital risk, but is still never auto-eligible, matching every sibling verification op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :robotics/simulate-data-wipe))
          (str "phase " n " must not auto-commit :robotics/simulate-data-wipe")))))

(deftest robotics-simulate-data-wipe-enabled-from-phase-2
  (is (contains? (:writes (get phase/phases 2)) :robotics/simulate-data-wipe))
  (is (contains? (:writes (get phase/phases 3)) :robotics/simulate-data-wipe))
  (is (not (contains? (:writes (get phase/phases 1)) :robotics/simulate-data-wipe))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":order/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:order/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :order/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/fulfill-order} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/issue-sanitization-certificate} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :order/intake} :commit)))))
