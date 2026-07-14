(ns techretail.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean order + bundled
  trade-in device through intake -> consumer-protection requirements
  verification -> trade-in-condition screening -> robot certified data-
  wipe mission (always escalates) -> human approval -> commit (order-
  fulfillment), then through Certificate-of-Data-Destruction proposal
  (always escalates) -> human approval -> commit, then shows six HARD
  holds (a jurisdiction with no spec-basis, an order whose recorded
  total does not reconcile with its own line-items, a trade-in device
  screened for an unresolved grading defect DIRECTLY via `:trade-in-
  condition/screen` [never via an actuation op against an unscreened
  device -- see this actor's own governor ns docstring / the lesson
  `automotive`'s ADR-2607142800 / `parksafety`'s ADR-2607071922
  Decision 5 and its many other siblings already recorded], an order
  fulfillment proposed before its consumer-protection evidence was ever
  verified, a Certificate-of-Data-Destruction proposed before the data-
  wipe mission ever ran, and one whose mission ran and reported
  :passed? but whose OWN post-wipe verification read still shows a
  recoverable sector on independent recheck) that never reach a human
  at all, plus a double fulfillment/certificate-issuance of an already-
  processed order/device -- and prints the audit ledger + the draft
  order-fulfillment and Certificate-of-Data-Destruction records."
  (:require [langgraph.graph :as g]
            [techretail.export :as export]
            [techretail.store :as store]
            [techretail.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :retail-operations-approver :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== order/intake order-1 (JPN, clean; total reconciles, trade-in unit-1 bundled) ==")
    (println (exec! actor "t1" {:op :order/intake :subject "order-1"
                                :patch {:id "order-1" :customer-name "Kenji Sato"}} operator))

    (println "== consumer-protection-rules/verify order-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :consumer-protection-rules/verify :subject "order-1"} operator))
    (println (approve! actor "t2"))

    (println "== trade-in-condition/screen unit-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :trade-in-condition/screen :subject "unit-1"} operator))
    (println (approve! actor "t3"))

    (println "== robotics/simulate-data-wipe unit-1 (robot certified data-wipe mission; escalates -- human approves) ==")
    (println (exec! actor "t3b" {:op :robotics/simulate-data-wipe :subject "unit-1"} operator))
    (println (approve! actor "t3b"))

    (println "== actuation/fulfill-order order-1 (always escalates -- actuation/fulfill-order) ==")
    (let [r (exec! actor "t4" {:op :actuation/fulfill-order :subject "order-1"} operator)]
      (println r)
      (println "-- human retail-operations approver approves --")
      (println (approve! actor "t4")))

    (println "== actuation/issue-sanitization-certificate unit-1 (always escalates -- actuation/issue-sanitization-certificate) ==")
    (let [r (exec! actor "t5" {:op :actuation/issue-sanitization-certificate :subject "unit-1"} operator)]
      (println r)
      (println "-- human retail-operations approver approves --")
      (println (approve! actor "t5")))

    (println "== consumer-protection-rules/verify order-2 (jurisdiction ATL, no spec-basis -> HARD hold) ==")
    (println (exec! actor "t6" {:op :consumer-protection-rules/verify :subject "order-2"} operator))

    (println "== consumer-protection-rules/verify order-3 (escalates -- human approves; sets up the order-total-mismatch test) ==")
    (println (exec! actor "t7" {:op :consumer-protection-rules/verify :subject "order-3"} operator))
    (println (approve! actor "t7"))

    (println "== actuation/fulfill-order order-3 (recorded total 158000 vs line-item sum 128000 -> HARD hold) ==")
    (println (exec! actor "t8" {:op :actuation/fulfill-order :subject "order-3"} operator))

    (println "== actuation/fulfill-order order-4 before any consumer-protection verification -> HARD hold (evidence-incomplete) ==")
    (println (exec! actor "t9" {:op :actuation/fulfill-order :subject "order-4"} operator))

    (println "== trade-in-condition/screen unit-2 (unresolved -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t10" {:op :trade-in-condition/screen :subject "unit-2"} operator))

    (println "== actuation/issue-sanitization-certificate unit-4 before the data-wipe mission ever ran -> HARD hold (data-wipe-mission-missing) ==")
    (println (exec! actor "t11" {:op :actuation/issue-sanitization-certificate :subject "unit-4"} operator))

    (println "== actuation/issue-sanitization-certificate unit-3 (sanitization-sim-verified? true on file, but post-wipe-recoverable-sectors-found=4 on independent recheck -> HARD hold) ==")
    (println (exec! actor "t12" {:op :actuation/issue-sanitization-certificate :subject "unit-3"} operator))

    (println "== actuation/fulfill-order order-1 AGAIN (double-fulfillment -> HARD hold) ==")
    (println (exec! actor "t13" {:op :actuation/fulfill-order :subject "order-1"} operator))

    (println "== actuation/issue-sanitization-certificate unit-1 AGAIN (double-issuance -> HARD hold) ==")
    (println (exec! actor "t14" {:op :actuation/issue-sanitization-certificate :subject "unit-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft order-fulfillment records ==")
    (doseq [r (store/fulfillment-history db)] (println r))

    (println "== draft Certificate-of-Data-Destruction records ==")
    (doseq [r (store/sanitization-certificate-history db)] (println r))

    (println "== social hand-off: audit package counts ==")
    (println (:counts (export/audit-package db)))
    (println "== social hand-off: CSV bundle keys ==")
    (println (keys (export/package->csv-bundle db)))))
