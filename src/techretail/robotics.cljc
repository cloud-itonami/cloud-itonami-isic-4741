(ns techretail.robotics
  "Robot-executed certified data-erasure mission -- the concrete,
  actor-level realization of ADR-2607011000's robotics premise (every
  cloud-itonami vertical is designed on the premise that a robot
  performs the physical-domain work; an independent governor gates any
  action before it ever reaches hardware) applied to THIS actor's
  trade-in program: a computer retailer accepting a customer's device
  for trade-in credit needs a robot-executed, NIST-SP-800-88-Rev.2-
  compliant data-sanitization/erasure cell before that device can be
  resold or its Certificate of Data Destruction issued -- not merely a
  self-reported 'wiped' checkbox.

  Follows `automotive.robotics` (`cloud-itonami-isic-2910`)'s
  established shape (ADR-2607142800, robotics-process-simulation fleet
  pattern): a robot mission (`kotoba.robotics/mission`) walks the
  trade-in device through three :sense/:actuate steps -- device
  connect-and-authenticate, a sanitization pass (cryptographic-erase or
  multi-pass overwrite per NIST SP 800-88 Rev. 2's Clear/Purge
  categories), and a post-wipe functional test / verification read --
  built with `kotoba.robotics/action` + `kotoba.robotics/telemetry-
  proof`, and reports an overall :passed? verdict.
  `sanitization-incomplete?` independently re-derives that verdict from
  the device's OWN recorded post-wipe verification-read field
  (`:post-wipe-recoverable-sectors-found`), never from the mission's
  self-reported result -- the SAME 'ground truth, not self-report'
  discipline `automotive.robotics/simulation-out-of-tolerance?`
  established (the sixth instance of this fleet's two-sided range-
  check family after `techretail.registry/order-total-mismatch?`, the
  fifth; see that ns's docstring for the earlier four).
  `techretail.governor`'s `data-wipe-mission-violations` calls this
  ns's independent recheck, never the stored :passed? value, before any
  `:actuation/issue-sanitization-certificate` proposal may commit.

  Pure data + pure functions -- no real robot I/O, no network.
  `kotoba.robotics` is itself \"policy, not control\"; this namespace
  simulates what a real sanitization-rig robot cell would report,
  deterministically, from the device's own recorded fields, so tests
  and the demo run offline exactly like every other sibling namespace
  in this fleet."
  (:require [kotoba.robotics :as robotics]))

(def mission-actions
  "The three-step certified data-wipe mission every trade-in device
  walks through before `:actuation/issue-sanitization-certificate` is
  proposable. All :sense/:actuate at :none/:low safety -- a stationary
  device on a sanitization rig, not the customer-facing shipment that
  is `:actuation/fulfill-order` (that op is on a DIFFERENT entity, the
  order, and is always human-gated regardless -- see
  `techretail.governor`)."
  [{:step :device-connect-and-authenticate :kind :sense   :safety :none}
   {:step :sanitization-pass              :kind :actuate :safety :low}
   {:step :post-wipe-functional-test      :kind :sense   :safety :none}])

(defn sanitization-incomplete?
  "Ground-truth check: does `trade-in-unit`'s own recorded
  :post-wipe-recoverable-sectors-found exceed zero? Needs no mission
  run or proposal inspection -- its input is a permanent field already
  on the device once a wipe has actually run, the same shape
  `automotive.robotics/structural-tolerance-out-of-range?` uses for a
  vehicle's structural deviation."
  [{:keys [post-wipe-recoverable-sectors-found]}]
  (and (number? post-wipe-recoverable-sectors-found)
       (pos? post-wipe-recoverable-sectors-found)))

(defn simulate-data-wipe
  "Run the robot certified data-wipe mission for `trade-in-unit-id`
  (`trade-in-unit` is the full record, incl.
  :post-wipe-recoverable-sectors-found). Returns {:mission ..
  :actions [{:action .. :proof ..} ..] :passed? bool}. Deterministic:
  :passed? is derived from the device's OWN recorded post-wipe
  verification-read field via `sanitization-incomplete?`, never
  invented or randomized -- `kotoba.robotics` mandates no network/IO,
  and a repeatable simulation is what makes the governor's independent
  recheck meaningful."
  [trade-in-unit-id trade-in-unit]
  (let [incomplete? (sanitization-incomplete? trade-in-unit)
        reading (if incomplete? :recoverable-sectors-found :clear)
        mission (robotics/mission (str "mission-" trade-in-unit-id "-data-wipe")
                                   :robot/device-sanitization-cell-1
                                   :nist-800-88-media-sanitization
                                   :boundaries {:station "trade-in-intake-sanitization-cell"}
                                   :max-steps (count mission-actions))
        actions (mapv (fn [{:keys [step kind safety]}]
                        (let [a (robotics/action (str (:mission/id mission) "-" (name step))
                                                  (:mission/id mission) kind safety
                                                  :params {:step step :trade-in-unit-id trade-in-unit-id})]
                          {:action a
                           :proof (robotics/telemetry-proof (:mission/id mission) step reading
                                                             :provenance :simulated)}))
                      mission-actions)]
    {:mission mission
     :actions actions
     :passed? (not incomplete?)}))
