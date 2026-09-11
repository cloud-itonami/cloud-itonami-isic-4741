(ns techretail.governor-contract-test
  "The governor contract as executable tests -- the computer-retailer
  analog of `cloud-itonami-isic-6512`'s `casualty.governor-contract-
  test` and, for the robotics HARD check specifically, the direct
  sibling of `automotive.governor-contract-test`
  (`cloud-itonami-isic-2910`). The single invariant under test:

    Retail Advisor never fulfills an order or issues a Certificate of
    Data Destruction the Retail Governor would reject,
    `:actuation/fulfill-order`/`:actuation/issue-sanitization-
    certificate` NEVER auto-commit at any phase, `:order/intake` (no
    direct capital risk) MAY auto-commit when clean, and every
    decision (commit OR hold) leaves exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [techretail.store :as store]
            [techretail.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :retail-operations-approver :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify!
  "Walks `subject` (an order-id) through consumer-protection-rules
  verify -> approve, leaving a verification on file. Uses distinct
  thread-ids per call site by suffixing `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :consumer-protection-rules/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(defn- screen!
  "Walks `subject` (a trade-in-unit-id) through trade-in-condition
  screening -> approve, leaving a screening on file. Only safe to call
  for a device whose grading-defect status has already resolved -- an
  unresolved defect HARD-holds the screen itself (see
  `trade-in-defect-is-held-and-unoverridable`)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-screen") {:op :trade-in-condition/screen :subject subject} operator)
  (approve! actor (str tid-prefix "-screen")))

(defn- simulate-data-wipe!
  "Walks `subject` (a trade-in-unit-id) through the robot certified
  data-wipe mission -> approve, leaving `:sanitization-sim-verified?`
  on file. Only meaningful to call for a device whose post-wipe
  verification-read is actually clean -- a device whose ground-truth
  field shows a recoverable sector still gets `:sanitization-sim-
  verified?` recorded (per whatever the mission itself found), but
  `techretail.governor`'s independent recheck HARD-holds regardless
  (see `sanitization-incomplete-is-held`)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-wipe") {:op :robotics/simulate-data-wipe :subject subject} operator)
  (approve! actor (str tid-prefix "-wipe")))

(defn- simulate-drop-test!
  "Walks `subject` (a trade-in-unit-id) through the robot functional
  drop/shock-test mission (ADR-2607152000, a REAL `physics-2d`-
  simulated free-fall/impact check) -> approve, leaving `:drop-test-
  sim-verified?` on file. Only meaningful to call for a device whose
  own `:device-class`/`:device-mass-kg` genuinely clears the real
  simulated tolerance -- a device whose real simulated impact-
  deceleration is out of tolerance still gets `:drop-test-sim-
  verified?` recorded (per whatever the mission itself found), but
  `techretail.governor`'s independent recheck HARD-holds regardless
  (see `drop-test-out-of-tolerance-is-held`)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-drop") {:op :robotics/simulate-drop-test :subject subject} operator)
  (approve! actor (str tid-prefix "-drop")))

(deftest clean-order-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :order/intake :subject "order-1"
                   :patch {:id "order-1" :customer-name "Kenji Sato"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Kenji Sato" (:customer-name (store/order db "order-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest consumer-protection-verify-always-needs-approval
  (testing "verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :consumer-protection-rules/verify :subject "order-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/consumer-protection-verification-of db "order-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a consumer-protection-rules/verify proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :consumer-protection-rules/verify :subject "order-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/consumer-protection-verification-of db "order-1")) "no verification written"))))

(deftest order-2-real-atl-jurisdiction-is-also-held
  (testing "order-2's own recorded jurisdiction (ATL) genuinely has no spec-basis -- no :no-spec? flag needed"
    (let [[db actor] (fresh)
          res (exec-op actor "t3b" {:op :consumer-protection-rules/verify :subject "order-2"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis))))))

(deftest fulfill-order-without-verification-is-held
  (testing "actuation/fulfill-order before any consumer-protection verification -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :actuation/fulfill-order :subject "order-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest order-total-mismatch-is-held
  (testing "an order whose own recorded total does not reconcile with its own line-items -> HOLD"
    (let [[db actor] (fresh)
          _ (verify! actor "t5pre" "order-3")
          res (exec-op actor "t5" {:op :actuation/fulfill-order :subject "order-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:order-total-mismatch} (-> (store/ledger db) last :basis)))
      (is (empty? (store/fulfillment-history db))))))

(deftest trade-in-defect-is-held-and-unoverridable
  (testing "an unresolved trade-in grading/defect finding -> HOLD, and never reaches request-approval -- exercised via :trade-in-condition/screen DIRECTLY, not via the actuation op against an unscreened device (see this actor's governor ns docstring / automotive's ADR-2607142800 and parksafety's ADR-2607071922 Decision 5 and its siblings)"
    (let [[db actor] (fresh)
          res (exec-op actor "t6" {:op :trade-in-condition/screen :subject "unit-2"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:trade-in-defect-unresolved} (-> (store/ledger db) first :basis)))
      (is (nil? (store/trade-in-condition-screen-of db "unit-2")) "no clearance written"))))

(deftest fulfill-order-always-escalates-then-human-decides
  (testing "a clean, fully-verified, reconciled order still ALWAYS interrupts for human approval -- actuation/fulfill-order is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t7pre" "order-1")
          r1 (exec-op actor "t7" {:op :actuation/fulfill-order :subject "order-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, fulfillment record drafted"
        (let [r2 (approve! actor "t7")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:order-fulfilled? (store/order db "order-1"))))
          (is (= 1 (count (store/fulfillment-history db))) "one draft fulfillment record"))))))

(deftest issue-sanitization-certificate-always-escalates-then-human-decides
  (testing "a clean, fully-wiped, resolved-defect, drop-test-cleared device still ALWAYS interrupts for human approval -- actuation/issue-sanitization-certificate is never auto"
    (let [[db actor] (fresh)
          _ (screen! actor "t8pre" "unit-1")
          _ (simulate-data-wipe! actor "t8pre2" "unit-1")
          _ (simulate-drop-test! actor "t8pre3" "unit-1")
          r1 (exec-op actor "t8" {:op :actuation/issue-sanitization-certificate :subject "unit-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, certificate record drafted"
        (let [r2 (approve! actor "t8")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:sanitization-certified? (store/trade-in-unit db "unit-1"))))
          (is (= 1 (count (store/sanitization-certificate-history db))) "one draft certificate record"))))))

(deftest fulfill-order-double-fulfillment-is-held
  (testing "fulfilling the same order twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t9pre" "order-1")
          _ (exec-op actor "t9a" {:op :actuation/fulfill-order :subject "order-1"} operator)
          _ (approve! actor "t9a")
          res (exec-op actor "t9" {:op :actuation/fulfill-order :subject "order-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-fulfilled} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/fulfillment-history db))) "still only the one earlier fulfillment"))))

(deftest issue-sanitization-certificate-double-issuance-is-held
  (testing "issuing the same device's Certificate of Data Destruction twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (screen! actor "t10pre" "unit-1")
          _ (simulate-data-wipe! actor "t10pre2" "unit-1")
          _ (simulate-drop-test! actor "t10pre3" "unit-1")
          _ (exec-op actor "t10a" {:op :actuation/issue-sanitization-certificate :subject "unit-1"} operator)
          _ (approve! actor "t10a")
          res (exec-op actor "t10" {:op :actuation/issue-sanitization-certificate :subject "unit-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-sanitization-certified} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/sanitization-certificate-history db))) "still only the one earlier certificate issuance"))))

(deftest data-wipe-simulation-always-needs-approval
  (testing "robotics/simulate-data-wipe is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t11" {:op :robotics/simulate-data-wipe :subject "unit-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t11")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:sanitization-sim-verified? (store/trade-in-unit db "unit-1"))))))))

(deftest issue-sanitization-certificate-without-data-wipe-simulation-is-held
  (testing "actuation/issue-sanitization-certificate before the robot data-wipe mission ever ran -> HOLD (data-wipe-mission-missing)"
    (let [[db actor] (fresh)
          res (exec-op actor "t12" {:op :actuation/issue-sanitization-certificate :subject "unit-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:data-wipe-mission-missing} (-> (store/ledger db) last :basis)))
      (is (empty? (store/sanitization-certificate-history db))))))

(deftest sanitization-incomplete-is-held
  (testing "unit-3 has a data-wipe simulation already on file (:sanitization-sim-verified? true), but its own post-wipe verification read shows a recoverable sector on INDEPENDENT recheck -> HOLD, never trusts the on-file verdict alone"
    (let [[db actor] (fresh)
          res (exec-op actor "t13" {:op :actuation/issue-sanitization-certificate :subject "unit-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:sanitization-incomplete} (-> (store/ledger db) last :basis)))
      (is (empty? (store/sanitization-certificate-history db))))))

(deftest drop-test-simulation-always-needs-approval
  (testing "robotics/simulate-drop-test (ADR-2607152000, a REAL physics-2d free-fall/impact simulation) is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t14" {:op :robotics/simulate-drop-test :subject "unit-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t14")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:drop-test-sim-verified? (store/trade-in-unit db "unit-1"))))
        (is (number? (:sim-impact-decel-g (store/trade-in-unit db "unit-1")))
            "a real physics-2d-simulated impact-deceleration reading was actually persisted")))))

(deftest issue-sanitization-certificate-without-drop-test-simulation-is-held
  (testing "actuation/issue-sanitization-certificate before the robot functional drop/shock-test mission ever ran -> HOLD (drop-test-missing), even though the data-wipe mission already ran"
    (let [[db actor] (fresh)
          _ (screen! actor "t15pre" "unit-1")
          _ (simulate-data-wipe! actor "t15pre2" "unit-1")
          res (exec-op actor "t15" {:op :actuation/issue-sanitization-certificate :subject "unit-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:drop-test-missing} (-> (store/ledger db) last :basis)))
      (is (empty? (store/sanitization-certificate-history db))))))

(deftest drop-test-out-of-tolerance-is-held
  (testing "unit-5 (a small-form-factor mini-PC mistakenly routed through the standard portable drop-test procedure) has a drop-test simulation already on file (:drop-test-sim-verified? true), but its own REAL physics-2d-simulated impact-deceleration telemetry (:sim-impact-decel-g) is out of tolerance on INDEPENDENT recheck -> HOLD, never trusts the on-file verdict alone -- the trade-in-unit analog of automotive's vehicle-5"
    (let [[db actor] (fresh)
          res (exec-op actor "t16" {:op :actuation/issue-sanitization-certificate :subject "unit-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:drop-test-out-of-tolerance} (-> (store/ledger db) last :basis)))
      (is (> (:sim-impact-decel-g (store/trade-in-unit db "unit-5")) 400.0)
          "the real simulated telemetry on file genuinely exceeds decel-ceiling-g")
      (is (empty? (store/sanitization-certificate-history db))))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :order/intake :subject "order-1"
                          :patch {:id "order-1" :customer-name "Kenji Sato"}} operator)
      (exec-op actor "b" {:op :consumer-protection-rules/verify :subject "order-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
