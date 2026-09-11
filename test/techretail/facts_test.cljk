(ns techretail.facts-test
  (:require [clojure.test :refer [deftest is]]
            [techretail.facts :as facts]))

(deftest jpn-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN")))
  (is (string? (:provenance (facts/spec-basis "JPN")))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["JPN" "ATL" "EUR"])]
    (is (= 2 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["EUR" "JPN"] (:covered-jurisdictions report)))))

(deftest gbr-is-honestly-uncovered
  (is (nil? (facts/spec-basis "GBR")) "GBR is deliberately not seeded -- never fabricate coverage"))

(deftest required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "JPN")]
    (is (facts/required-evidence-satisfied? "JPN" all))
    (is (not (facts/required-evidence-satisfied? "JPN" (rest all))))
    (is (not (facts/required-evidence-satisfied? "ATL" all)) "no spec-basis -> never satisfied")))

;; ----------------------------- sanitization-standard -----------------------------

(deftest sanitization-standard-cites-the-current-not-withdrawn-standard
  (let [sb (facts/sanitization-basis)]
    (is (= "NIST (National Institute of Standards and Technology)" (:owner-authority sb)))
    (is (re-find #"Rev\. 2" (:legal-basis sb)) "must cite the CURRENT standard")
    (is (re-find #"supersedes" (:legal-basis sb)) "must acknowledge Rev. 1 was withdrawn, not silently drop it")
    (is (string? (:provenance sb)))
    (is (= 3 (count (:required-evidence sb))))))
