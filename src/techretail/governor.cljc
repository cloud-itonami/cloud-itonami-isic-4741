(ns techretail.governor
  "Retail Governor -- the independent compliance layer that earns the
  Retail Advisor the right to commit. The LLM has no notion of
  consumer-protection/distance-selling law, whether an order's own
  recorded total actually reconciles with its own recorded line-items,
  whether a trade-in device's own post-wipe verification read has
  actually confirmed zero recoverable sectors, whether an unresolved
  grading/defect finding against a trade-in device has actually stayed
  unresolved, or when an act stops being a draft and becomes a real-
  world shipment or Certificate-of-Data-Destruction issuance, so this
  MUST be a separate system able to *reject* a proposal and fall back
  to HOLD -- the computer-retailer analog of `cloud-itonami-isic-6512`'s
  CasualtyGovernor and, for the robotics HARD check specifically, the
  direct sibling of `automotive.governor` (`cloud-itonami-isic-2910`).

  Seven checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them (you don't get to approve your way past a
  fabricated consumer-protection spec-basis, incomplete evidence, a
  robot data-wipe mission that never ran or that independently re-
  checks incomplete, a mismatched order total, an unresolved trade-in
  grading defect, or a double fulfillment/certificate-issuance). The
  confidence/actuation gate is SOFT: it asks a human to look (low
  confidence / actuation), and the human may approve -- but see
  `techretail.phase`: for `:stake :actuation/fulfill-order`/
  `:actuation/issue-sanitization-certificate` (a real act) NO phase
  ever allows auto-commit either. Two independent layers agree that
  actuation is always a human call.

    1. Spec-basis                  -- did the consumer-protection
                                       requirements proposal cite an
                                       OFFICIAL source (`techretail.
                                       facts`), or invent one?
    2. Evidence incomplete         -- for `:actuation/fulfill-order`,
                                       has the order actually been
                                       verified with a full consumer-
                                       protection evidence checklist on
                                       file?
    3. Data-wipe mission missing or
       independently incomplete     -- for `:actuation/issue-
                                       sanitization-certificate`, has
                                       the robot certified data-wipe
                                       mission (`techretail.robotics`)
                                       actually run and been recorded
                                       on the trade-in-unit
                                       (`:sanitization-sim-verified?`)?
                                       AND INDEPENDENTLY recompute
                                       whether the device's own
                                       recorded post-wipe verification-
                                       read still shows a recoverable
                                       sector
                                       (`techretail.robotics/
                                       sanitization-incomplete?`),
                                       ignoring whatever :passed?
                                       verdict the mission run itself
                                       stored -- the same 'ground
                                       truth, not self-report'
                                       discipline check 4 below uses
                                       for order totals, and
                                       `automotive.governor`'s
                                       `robotics-simulation-violations`
                                       established fleet-wide
                                       (ADR-2607142800).
    4. Order total mismatch         -- for `:actuation/fulfill-order`,
                                       INDEPENDENTLY recompute whether
                                       the order's own recorded total
                                       reconciles with its own recorded
                                       line-items
                                       (`techretail.registry/order-
                                       total-mismatch?`) -- needs no
                                       proposal inspection or stored-
                                       verdict lookup at all. This
                                       fleet's two-sided range-check
                                       family (`automotive.registry/
                                       vehicle-emissions-out-of-range?`
                                       and its siblings established the
                                       priors; `techretail.robotics/
                                       sanitization-incomplete?` above
                                       is a further instance).
    5. Trade-in grading defect
       unresolved                   -- reported by THIS proposal
                                       itself (a `:trade-in-condition/
                                       screen` that just found an
                                       unresolved defect), or already
                                       on file for the trade-in-unit
                                       (`:trade-in-condition/screen`/
                                       `:actuation/issue-sanitization-
                                       certificate`). Evaluated
                                       UNCONDITIONALLY (not scoped to a
                                       specific op), the SAME
                                       discipline `automotive.governor/
                                       end-of-line-defect-unresolved-
                                       violations` (and its own priors)
                                       established -- exercised in
                                       tests/demo via `:trade-in-
                                       condition/screen` DIRECTLY, not
                                       via an actuation op against an
                                       unscreened device -- see this
                                       ns's own test suite.
    6. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:actuation/
                                       fulfill-order`/`:actuation/
                                       issue-sanitization-certificate`
                                       (REAL acts) -> escalate.

  Two more guards, double-fulfillment/double-certificate-issuance
  prevention, are enforced but NOT listed as numbered HARD checks
  above because they need no upstream comparison at all --
  `already-fulfilled-violations`/`already-sanitization-certified-
  violations` refuse to fulfill an order/issue a Certificate of Data
  Destruction for the SAME order/trade-in-unit twice, off dedicated
  `:order-fulfilled?`/`:sanitization-certified?` facts (never a
  `:status` value) -- the SAME 'check a dedicated boolean, not status'
  discipline every prior sibling governor's guards establish, informed
  by `cloud-itonami-isic-6492`'s status-lifecycle bug
  (ADR-2607071320).

  UNLIKE `automotive.governor` (one entity, two actuations on it), this
  domain's two actuations act on TWO DIFFERENT entities (an order /
  a trade-in-unit) that are only descriptively linked. Every check
  below is therefore scoped to exactly ONE of the two entity types --
  no check traverses `:trade-in-unit-id` to reach across entities."
  (:require [techretail.facts :as facts]
            [techretail.registry :as registry]
            [techretail.robotics :as robotics]
            [techretail.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Shipping a real purchased device to a customer and issuing a real
  Certificate of Data Destruction are the two real-world actuation
  events this actor performs -- a two-member set, matching every prior
  dual-actuation sibling's shape."
  #{:actuation/fulfill-order :actuation/issue-sanitization-certificate})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:consumer-protection-rules/verify` (or actuation) proposal with
  no spec-basis citation is a HARD violation -- never invent a
  jurisdiction's consumer-protection requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:consumer-protection-rules/verify :actuation/fulfill-order} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は消費者保護要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:actuation/fulfill-order`, the jurisdiction's required
  consumer-protection evidence must actually be satisfied -- do not
  trust the advisor's self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (= op :actuation/fulfill-order)
    (let [o (store/order st subject)
          verification (store/consumer-protection-verification-of st subject)]
      (when-not (and verification
                     (facts/required-evidence-satisfied?
                      (:jurisdiction o) (:checklist verification)))
        [{:rule :evidence-incomplete
          :detail "法域の必要開示事項(事業者情報表示/価格支払時期表示/返品特約表示等)が充足していない状態での発送提案"}]))))

(defn- data-wipe-mission-violations
  "For `:actuation/issue-sanitization-certificate`: HARD hold if the
  robot certified data-wipe mission (`techretail.robotics`) never ran
  and was recorded on the trade-in-unit (`:sanitization-sim-verified?`),
  OR if it did but an INDEPENDENT recompute of the device's own post-
  wipe verification-read field (`techretail.robotics/sanitization-
  incomplete?`) says a recoverable sector remains right now -- never
  trusts the mission's own stored :passed? verdict alone, the same
  discipline `order-total-mismatch-violations` below uses for order
  totals."
  [{:keys [op subject]} st]
  (when (= op :actuation/issue-sanitization-certificate)
    (let [u (store/trade-in-unit st subject)]
      (cond
        (not (:sanitization-sim-verified? u))
        [{:rule :data-wipe-mission-missing
          :detail (str subject " の認定データ消去ミッションが未実行・未合格")}]

        (robotics/sanitization-incomplete? u)
        [{:rule :sanitization-incomplete
          :detail (str subject " の消去後検証読取(post-wipe-recoverable-sectors-found="
                       (:post-wipe-recoverable-sectors-found u) ")が独立再検証で回復可能セクタ有りを検出")}]))))

(defn- order-total-mismatch-violations
  "For `:actuation/fulfill-order`, INDEPENDENTLY recompute whether the
  order's own recorded total reconciles with its own recorded line-
  items via `techretail.registry/order-total-mismatch?` -- needs no
  proposal inspection or stored-verdict lookup at all, since its
  inputs are permanent ground-truth fields already on the order."
  [{:keys [op subject]} st]
  (when (= op :actuation/fulfill-order)
    (let [o (store/order st subject)]
      (when (registry/order-total-mismatch? o)
        [{:rule :order-total-mismatch
          :detail (str subject " の記録上の合計(" (:order-total-actual o)
                      ")が明細合計と許容誤差(" (:order-total-tolerance o 0.01) ")を超えて乖離")}]))))

(defn- trade-in-defect-unresolved-violations
  "An unresolved trade-in grading/defect finding -- reported by THIS
  proposal (e.g. a `:trade-in-condition/screen` that itself just found
  one), or already on file in the store for the trade-in-unit
  (`:trade-in-condition/screen`/`:actuation/issue-sanitization-
  certificate`) -- is a HARD, un-overridable hold. Evaluated
  UNCONDITIONALLY (not scoped to a specific op) so the screening op
  itself can HARD-hold on its own finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :unresolved (get-in proposal [:value :verdict]))
        trade-in-unit-id (when (contains? #{:trade-in-condition/screen :actuation/issue-sanitization-certificate} op) subject)
        hit-on-file? (and trade-in-unit-id (= :unresolved (:verdict (store/trade-in-condition-screen-of st trade-in-unit-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :trade-in-defect-unresolved
        :detail "未解決のグレーディング欠陥がある状態でのデータ消去証明書発行提案は進められない"}])))

(defn- already-fulfilled-violations
  "For `:actuation/fulfill-order`, refuses to fulfill the SAME order
  twice, off a dedicated `:order-fulfilled?` fact (never a `:status`
  value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/fulfill-order)
    (when (store/order-already-fulfilled? st subject)
      [{:rule :already-fulfilled
        :detail (str subject " は既に発送済み")}])))

(defn- already-sanitization-certified-violations
  "For `:actuation/issue-sanitization-certificate`, refuses to issue a
  Certificate of Data Destruction for the SAME trade-in-unit twice, off
  a dedicated `:sanitization-certified?` fact (never a `:status`
  value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/issue-sanitization-certificate)
    (when (store/trade-in-unit-already-sanitization-certified? st subject)
      [{:rule :already-sanitization-certified
        :detail (str subject " は既にデータ消去証明書発行済み")}])))

(defn check
  "Censors a Retail Advisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (data-wipe-mission-violations request st)
                           (order-total-mismatch-violations request st)
                           (trade-in-defect-unresolved-violations request proposal st)
                           (already-fulfilled-violations request st)
                           (already-sanitization-certified-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
