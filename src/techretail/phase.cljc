(ns techretail.phase
  "Phase 0->3 staged rollout -- the computer-retailer analog of
  `cloud-itonami-isic-6512`'s `casualty.phase`.

    Phase 0  read-only        -- no writes, still governor-gated.
    Phase 1  assisted-intake  -- order intake allowed, every write
                                 needs human approval.
    Phase 2  assisted-verify  -- adds consumer-protection requirements
                                 verification + trade-in-condition
                                 screening + robot certified-data-wipe
                                 mission writes, still approval.
    Phase 3  supervised auto  -- governor-clean, high-confidence
                                 `:order/intake` (no capital risk yet)
                                 may auto-commit. `:actuation/fulfill-
                                 order`/`:actuation/issue-sanitization-
                                 certificate` NEVER auto-commit, at any
                                 phase.

  `:actuation/fulfill-order`/`:actuation/issue-sanitization-
  certificate` are deliberately ABSENT from every phase's `:auto` set,
  including phase 3 -- a permanent structural fact, not a rollout
  milestone still to come. Shipping a real purchased device to a
  customer and issuing a real Certificate of Data Destruction are the
  two real-world acts this actor performs; both are always a human
  retail-operations approver's call. `techretail.governor`'s
  `:actuation/fulfill-order`/`:actuation/issue-sanitization-
  certificate` high-stakes gate enforces the same invariant
  independently -- two layers, not one, agree on this.
  `:trade-in-condition/screen`/`:robotics/simulate-data-wipe`/
  `:robotics/simulate-drop-test` (ADR-2607152000, the real `physics-2d`-
  backed functional drop/shock-test mission) are likewise never
  auto-eligible, at any phase -- the same posture every sibling's
  screening/verification op has.
  Phase 3's `:auto` set here has only ONE member (`:order/intake`) --
  this domain has no separate no-capital-risk 'file' lifecycle distinct
  from the order record itself."
  )

(def read-ops  #{})
(def write-ops #{:order/intake :consumer-protection-rules/verify :trade-in-condition/screen
                 :robotics/simulate-data-wipe :robotics/simulate-drop-test
                 :actuation/fulfill-order :actuation/issue-sanitization-certificate})

;; NOTE the invariant: `:actuation/fulfill-order`/`:actuation/issue-
;; sanitization-certificate` are members of `write-ops` (governor-
;; gated like any write) but are NEVER members of any phase's `:auto`
;; set below. Do not add them there.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed to
  auto-commit when governor-clean>}."
  {0 {:label "read-only"        :writes #{}                                                          :auto #{}}
   1 {:label "assisted-intake"  :writes #{:order/intake}                                             :auto #{}}
   2 {:label "assisted-verify"  :writes #{:order/intake :consumer-protection-rules/verify :trade-in-condition/screen
                                          :robotics/simulate-data-wipe :robotics/simulate-drop-test}   :auto #{}}
   3 {:label "supervised-auto"  :writes write-ops
      :auto #{:order/intake}}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE (:phase-approval),
    even if the governor was clean.
  - `:actuation/fulfill-order`/`:actuation/issue-sanitization-
    certificate` are never auto-eligible at any phase, so they always
    escalate once the governor clears them (or hold if the governor
    doesn't)."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)       {:disposition :hold :reason nil}
      (contains? read-ops op)              {:disposition governor-disposition :reason nil}
      (not (contains? writes op))          {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))      {:disposition :escalate :reason :phase-approval}
      :else                                {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Map a Retail Governor verdict to a base disposition before the phase
  gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
