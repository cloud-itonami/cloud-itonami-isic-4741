(ns techretail.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave1 Lane A-no-demo): this repo previously had a hand-typed sample page
  and no generator. This namespace drives the REAL actor stack
  (`techretail.operation` -> `techretail.governor` -> `techretail.store`)
  through a scenario adapted from this repo's own `techretail.sim` demo
  driver (`clojure -M:dev:run`, confirmed BEFORE writing this file to
  produce a sensible ledger against the real seeded order/trade-in ids
  `order-1`..`order-4` / `unit-1`..`unit-5` -- ids match
  `techretail.store/demo-data`, so it was safe to reuse rather than
  author from scratch), trimmed to a representative subset (one full
  order-fulfillment + Certificate-of-Data-Destruction lifecycle, and
  several distinct HARD-hold reasons) and rendered deterministically --
  no invented numbers, no timestamps in the page content, byte-identical
  across reruns against the same seed (verify by diffing two consecutive
  runs).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [techretail.store :as store]
            [techretail.operation :as op]
            [langgraph.graph :as g]))

(def ^:private operator
  {:actor-id "op-1" :actor-role :retail-operations-approver :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach: order-1/unit-1 clears a full lifecycle -- order
  intake (auto-commit clean at phase 3, no capital risk), consumer-
  protection verification (phase-gated -- not yet auto-eligible --
  approved), trade-in-condition screen (approved), robot data-wipe
  mission (approved), robot drop-test mission (approved), order
  fulfillment (ALWAYS escalates -- `:actuation/fulfill-order` is
  permanently high-stakes, never auto at any phase -- approved) and a
  Certificate of Data Destruction (ALWAYS escalates --
  `:actuation/issue-sanitization-certificate`, same posture --
  approved); order-2 HARD-holds consumer-protection verification with
  no official spec-basis for its (deliberately unregistered)
  jurisdiction ATL; order-3 clears verification (approved) but then
  HARD-holds fulfillment whose recorded total (158000) does not match
  the independently recomputed line-item sum (1 x 128000 = 128000);
  order-4 HARD-holds fulfillment before any consumer-protection
  evidence was verified; unit-2 HARD-holds a trade-in-condition screen
  that itself detects an unresolved grading defect; unit-4 HARD-holds
  certificate issuance before the data-wipe mission ever ran; unit-3
  HARD-holds certificate issuance whose OWN post-wipe verification
  read still shows recoverable sectors on independent recheck. Every
  HARD hold never reaches a human. Returns the resulting store -- every
  field read by `render` below is real governor/store output, not a
  hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)]
    (exec! actor "t1-intake" {:op :order/intake :subject "order-1"
                                :patch {:id "order-1" :customer-name "Kenji Sato"}})

    (exec! actor "t1-verify" {:op :consumer-protection-rules/verify :subject "order-1"})
    (approve! actor "t1-verify")

    (exec! actor "t1-screen" {:op :trade-in-condition/screen :subject "unit-1"})
    (approve! actor "t1-screen")

    (exec! actor "t1-wipe" {:op :robotics/simulate-data-wipe :subject "unit-1"})
    (approve! actor "t1-wipe")

    (exec! actor "t1-drop" {:op :robotics/simulate-drop-test :subject "unit-1"})
    (approve! actor "t1-drop")

    (exec! actor "t1-fulfill" {:op :actuation/fulfill-order :subject "order-1"})
    (approve! actor "t1-fulfill")

    (exec! actor "t1-cert" {:op :actuation/issue-sanitization-certificate :subject "unit-1"})
    (approve! actor "t1-cert")

    (exec! actor "t2-verify" {:op :consumer-protection-rules/verify :subject "order-2"})

    (exec! actor "t3-verify" {:op :consumer-protection-rules/verify :subject "order-3"})
    (approve! actor "t3-verify")

    (exec! actor "t3-fulfill" {:op :actuation/fulfill-order :subject "order-3"})

    (exec! actor "t4-fulfill" {:op :actuation/fulfill-order :subject "order-4"})

    (exec! actor "t5-screen" {:op :trade-in-condition/screen :subject "unit-2"})

    (exec! actor "t6-cert" {:op :actuation/issue-sanitization-certificate :subject "unit-4"})

    (exec! actor "t7-cert" {:op :actuation/issue-sanitization-certificate :subject "unit-3"})
    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger subject-id]
  (last (filter #(= (:subject %) subject-id) ledger)))

(defn- status-cell [ledger subject-id]
  (let [f (last-fact-for ledger subject-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (or (-> f :violations first :rule)
                     (first (:basis f)))]
        (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- line-item-sum [items]
  (reduce + 0 (map (fn [{:keys [qty unit-price]}] (* (or qty 0) (or unit-price 0))) items)))

(defn- total-check-cell [{:keys [items order-total-actual]}]
  (let [sum (line-item-sum items)
        actual (or order-total-actual 0)]
    (if (== sum actual)
      (str "<span class=\"ok\">" (esc sum) " = sum(line-items)</span>")
      (str "<span class=\"err\">" (esc actual) " &ne; " (esc sum) "</span>"))))

(defn- trade-in-cell [order]
  (if-let [uid (:trade-in-unit-id order)]
    (esc uid)
    "<span class=\"muted\">&mdash;</span>"))

(defn- order-lifecycle-cell [o]
  (if (:order-fulfilled? o)
    "<span class=\"ok\">fulfilled</span>"
    "<span class=\"muted\">open</span>"))

(defn- order-row [ledger o]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc (:id o))
          (esc (:customer-name o))
          (esc (:jurisdiction o))
          (total-check-cell o)
          (trade-in-cell o)
          (order-lifecycle-cell o)
          (status-cell ledger (:id o))))

(defn- unit-lifecycle-cell [u]
  (cond
    (:sanitization-certified? u) "<span class=\"ok\">certificate issued</span>"
    (:sanitization-sim-verified? u) "<span class=\"warn\">wiped, not certified</span>"
    :else "<span class=\"muted\">intake</span>"))

(defn- grading-cell [u]
  (if (:grading-defect-unresolved? u)
    "<span class=\"err\">unresolved</span>"
    "<span class=\"ok\">resolved</span>"))

(defn- wipe-sectors-cell [u]
  (let [n (or (:post-wipe-recoverable-sectors-found u) 0)]
    (if (zero? n)
      "<span class=\"ok\">0</span>"
      (str "<span class=\"err\">" (esc n) " (independent recheck)</span>"))))

(defn- unit-row [ledger u]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc (:id u))
          (esc (:device-model u))
          (grading-cell u)
          (wipe-sectors-cell u)
          (unit-lifecycle-cell u)
          (status-cell ledger (:id u))))

(defn- ledger-row [{:keys [t op subject disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc subject)
          (esc (or (some->> basis (map name) (str/join ", ")) (some-> disposition name) ""))))

(defn- fulfillment-row [r]
  (format "        <tr><td>%s</td><td>order-fulfillment-draft</td><td>unsigned &middot; retailer ships offline</td></tr>"
          (esc (or (get r "fulfillment_number") (get r :fulfillment_number) r))))

(defn- cert-row [r]
  (format "        <tr><td>%s</td><td>sanitization-certificate-draft</td><td>unsigned &middot; Certificate of Data Destruction, NIST SP 800-88 Rev. 2 basis</td></tr>"
          (esc (or (get r "certificate_number") (get r :certificate_number) r))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own closed op contract
  ;; (README Ops, techretail.governor / techretail.phase) --
  ;; documentation of fixed behavior, not runtime telemetry, so it is
  ;; legitimately hand-described rather than derived from a live run.
  ["        <tr><td><code>:order/intake</code></td><td><span class=\"ok\">phase-3 auto-commit when clean, no capital risk</span></td></tr>"
   "        <tr><td><code>:consumer-protection-rules/verify</code></td><td><span class=\"warn\">phase-3: human approval &middot; no fabricated consumer-protection rules</span></td></tr>"
   "        <tr><td><code>:trade-in-condition/screen</code></td><td><span class=\"critical\">HARD hold if unresolved grading defect &middot; never auto at any phase</span></td></tr>"
   "        <tr><td><code>:robotics/simulate-data-wipe</code></td><td><span class=\"warn\">phase-3: human approval &middot; required before certificate issuance</span></td></tr>"
   "        <tr><td><code>:robotics/simulate-drop-test</code></td><td><span class=\"warn\">phase-3: human approval &middot; real physics-2d free-fall/impact recheck</span></td></tr>"
   "        <tr><td><code>:actuation/fulfill-order</code></td><td><span class=\"warn\">ALWAYS human approval &middot; never auto at any phase &middot; evidence + independent order-total recompute</span></td></tr>"
   "        <tr><td><code>:actuation/issue-sanitization-certificate</code></td><td><span class=\"warn\">ALWAYS human approval &middot; never auto at any phase &middot; data-wipe + drop-test + double-issue guard</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        orders (store/all-orders db)
        units (store/all-trade-in-units db)
        fulfillments (store/fulfillment-history db)
        certs (store/sanitization-certificate-history db)
        order-rows (str/join "\n" (map (partial order-row ledger) orders))
        unit-rows (str/join "\n" (map (partial unit-row ledger) units))
        ledger-rows (str/join "\n" (map ledger-row ledger))
        handoff-rows (str/join "\n"
                               (concat (map fulfillment-row fulfillments)
                                       (map cert-row certs)))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-4741 &middot; computer retail + trade-in</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Computer retail + trade-in (ISIC 4741) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · fulfill-order / sanitization-certificate always human-approved</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Orders</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>techretail.store</code> via <code>techretail.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Order</th><th>Customer</th><th>Jurisdiction</th><th>Total check</th><th>Trade-in</th><th>Fulfillment</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     order-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Trade-in devices</h2>\n"
     "    <table>\n"
     "      <thead><tr><th>Unit</th><th>Device</th><th>Grading</th><th>Post-wipe sectors</th><th>Lifecycle</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     unit-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Retail Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden. Order totals and drop/wipe telemetry are independently recomputed, never trusted from the proposal; an order fulfillment or Certificate of Data Destruction is blocked outright on incomplete evidence, unresolved grading, residual recoverable sectors, out-of-tolerance impact, or double issuance.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Social hand-off</h2>\n"
     "    <p class=\"muted\">Audit package drafts produced this run — <code>techretail.export/audit-package</code> and <code>package-&gt;csv-bundle</code> (orders / trade-in-units / ledger / fulfillments / sanitization-certificates CSV).</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Artifact</th><th>Kind</th><th>Note</th></tr></thead>\n"
     "      <tbody>\n"
     (if (str/blank? handoff-rows)
       "        <tr><td colspan=\"3\" class=\"muted\">none this run</td></tr>"
       handoff-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Subject</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)
        out-file (java.io.File. out)]
    (when-let [parent (.getParentFile out-file)]
      (.mkdirs parent))
    (spit out-file html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/fulfillment-history db)) "fulfillments,"
             (count (store/sanitization-certificate-history db)) "sanitization certificates )")))
