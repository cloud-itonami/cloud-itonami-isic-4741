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

  ADR-2607152000 (extending ADR-2607151600's automotive pilot to this
  vertical) ADDS a SECOND, genuinely time-stepped, real-physics-backed
  robot mission alongside the data-wipe mission above:
  `simulate-drop-test` -- a functional drop/shock test, a real,
  standard ITAD/electronics-refurbishment QA procedure (not invented
  for this ADR) verifying a traded-in device survives ordinary
  handling/shipping. This is built DIRECTLY on `kotoba-lang/physics-2d`'s
  real, tested `world-step` impulse/gravity solver (see deps.edn) --
  the SAME real dependency `automotive.robotics`
  (`cloud-itonami-isic-2910`) took for its crash-dispatch simulation,
  used here directly (no design-library sibling repo, unlike
  automotive's `kami-engine-vehicle-designer` pairing -- this vertical
  has no equivalent pre-existing design-repo relationship, so the
  physics module lives in THIS namespace, per ADR-2607152000's own
  'key simplification' decision). A device rigid body free-falls
  (real gravity integration, `world-new [0.0 -9.81]`) from a standard
  test height and impacts a static (mass 0) test-surface rigid body;
  `drop-test-impact-out-of-tolerance?` independently re-derives the
  pass/fail verdict from the device's OWN recorded, REALLY simulated
  `:sim-impact-decel-g` field against a real, honestly-sourced
  tolerance ceiling -- never from the mission's self-reported result --
  the SAME 'ground truth, not self-report' discipline this ns's own
  `sanitization-incomplete?` and `automotive.robotics/simulation-out-
  of-tolerance?` established. `techretail.governor`'s NEW
  `drop-test-violations` check calls this ns's independent recheck
  before any `:actuation/issue-sanitization-certificate` proposal may
  commit -- see that ns's docstring for why this is a SEPARATE check
  (and a SEPARATE robot mission/op) rather than folded into
  `:trade-in-condition/screen`.

  Data sanitization (above) genuinely is NOT a physics event -- it
  stays symbolic, unchanged. The drop/shock test genuinely IS a
  physics event (a real ITAD functional-drop QA procedure), so it
  alone gets the real `physics-2d` treatment; this ns does not force-
  fit physics onto the data-wipe mission.

  Pure data + pure functions -- no real robot I/O, no network.
  `kotoba.robotics` is itself \"policy, not control\"; `physics-2d`'s
  own `world-step` is a pure fixed-timestep integrator (no wall-clock/
  IO) -- this namespace simulates what a real sanitization rig AND a
  real drop/shock-test rig would report, deterministically, from the
  device's own recorded fields, so tests and the demo run offline
  exactly like every other sibling namespace in this fleet."
  (:require [kotoba.robotics :as robotics]
            [physics-2d :as p2d]))

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

;; ───────────────────── real drop/shock-test physics (ADR-2607152000) ─────────────────────
;;
;; Everything below this point genuinely runs `kotoba-lang/physics-2d`'s
;; real `world-step` gravity/impulse solver -- no invented physics engine,
;; no fabricated numbers. See `physics-2d/src/physics_2d.cljc` for the
;; solver itself (restored from the original Rust `kami-physics-2d` crate).

(defn- sqrt* [x] #?(:clj (Math/sqrt (double x)) :cljs (js/Math.sqrt x)))
(defn- ceil* [x] #?(:clj (Math/ceil (double x)) :cljs (js/Math.ceil x)))
(defn- abs* [x] (if (neg? x) (- x) x))

(def drop-test-mission-actions
  "The three-step functional drop/shock-test mission a trade-in device
  walks through, ADDED alongside (not replacing) `mission-actions`
  above -- a real, standard ITAD/electronics-refurbishment QA procedure
  verifying a device survives ordinary handling/shipping. All
  :sense/:actuate at :none/:low safety, the same posture the data-wipe
  mission uses: verification/QA on a device already staged in a
  robot-tended test cell, not the customer-facing shipment that is
  `:actuation/fulfill-order` (a different entity, the order -- see
  `techretail.governor`)."
  [{:step :device-placement-on-drop-rig    :kind :sense   :safety :none}
   {:step :drop-release                    :kind :actuate :safety :low}
   {:step :post-drop-functional-recheck    :kind :sense   :safety :none}])

(def ^:const gravity-mps2
  "Real standard gravity (m/s^2) -- the SAME magnitude ADR-2607152000
  mandates for `physics-2d/world-new`'s gravity vector ([0.0 -9.81])."
  9.81)

(def world-gravity
  "The `physics-2d` world's gravity vector -- a genuine downward
  gravity integration (unlike `automotive`/`vdesign.simphysics`'s
  horizontal-crash projection, which sets gravity [0.0 0.0] because
  there is no vertical drop to model there; THIS test IS a vertical
  free fall, so gravity is real and non-zero here)."
  [0.0 (- gravity-mps2)])

(def ^:const drop-height-m
  "Standard functional drop/shock-test height (m) for a handheld/
  laptop-class trade-in device -- 1.0 m, the commonly-used tabletop/
  desk-height accidental-drop reference across consumer-electronics
  and ITAD/refurbishment functional-drop QA practice, and broadly
  consistent with the IEC 60068-2-31 ('Ec: rough handling shocks') /
  IEC 60068-2-32 ('Ed: free fall') family of equipment rough-handling/
  free-fall test standards, whose own per-mass-class table entries
  range roughly 0.25-1.2 m depending on equipment mass category and
  edition. Disclosed honestly: 1.0 m is a REPRESENTATIVE, commonly-used
  reference value for this mass class, not a verbatim single clause
  citation from one specific table row/edition -- the same 'real,
  citable, reasoned reference' discipline `automotive.robotics/decel-
  ceiling-g` anchored on `simverify`'s already-established 20g pulse."
  1.0)

(def device-give-m
  "Disclosed engineering PRIOR (NOT measured) for each device class's
  own effective internal shock-absorbing 'give' (m) -- the assumed
  displacement over which the device's own chassis/internal-mounting
  compliance (rubber feet, chassis flex, internal shock mounts)
  decelerates it to a stop on impact. Plays the SAME role
  `vdesign.simphysics`'s per-class `crush-len-m` plays for automotive's
  crash simulation: a per-class structural/construction design fact,
  never persisted itself, that this ns's own `run-drop-simulation`
  reads to derive a principled `dt` (see that fn's docstring).
  `:desktop-mini` (a small-form-factor desktop's rigid metal chassis,
  minimal internal cushioning) is assumed to have markedly LESS give
  than `:handheld`/`:laptop`-class devices (cases, rubber feet, chassis
  flex) -- a plausible, disclosed prior, not a measured fact, matching
  `vdesign.simphysics`'s own disclosed-prior discipline (its
  `frontal-area-m2`). `:desktop-tower` (a full-size desktop tower) is
  assumed to have somewhat MORE give than `:desktop-mini` (a larger
  chassis has more internal flex/rubber-foot travel) but still less
  than `:laptop` -- REAL PRACTICE, honestly, does not usually
  functional-drop-test a full desktop tower the way it does a laptop
  at all (too heavy/large to realistically free-fall-drop as a
  handling-damage check); this entry exists so this ns can HONESTLY
  represent what happens if a desktop-class unit is nonetheless routed
  through the standard portable drop-test procedure (see
  `techretail.store/demo-data`'s unit-5 fixture)."
  {:handheld 0.015 :laptop 0.010 :monitor 0.007 :desktop-tower 0.006 :desktop-mini 0.003})

(def ^:const decel-ceiling-g
  "Real, non-fabricated ceiling on `:sim-impact-decel-g` (g) -- 400g.
  Anchored on the ORDER OF MAGNITUDE commonly published in laptop-class
  2.5in HDD/SSD 'non-operating shock' datasheet specifications (widely
  seen in roughly the 300-1000G range for short, ~1-2 ms half-sine
  pulses across mainstream vendor spec sheets) -- a device's own
  weakest-commonly-cited internal component durability rating being a
  reasonable real-world 'did the drop survive functionally' gate for
  this QA procedure.

  HONEST CONFIDENCE NOTE (ADR-2607152000 explicitly calls for this):
  unlike automotive's `simverify/a-crash` (a value THIS workspace's own
  pre-existing, already-cited closed-form model established),
  `techretail` has no pre-existing citable device-shock reference of
  its own, and this is not a single, precisely-sourced datasheet number
  quoted verbatim -- 400g is a REASONED ENGINEERING ESTIMATE (a
  moderately conservative pick near the lower end of the commonly-cited
  range above), disclosed as an estimate rather than presented as a
  fake-precise citation."
  400.0)

(def ^:const device-half-w-m
  "Device AABB half-width (m) along the (physically irrelevant, purely
  lateral) x-axis -- large enough that the device's footprint always
  overlaps the test-surface head-on in this 1-D vertical-drop
  projection; no tip-over/edge-first impact is modeled (disclosed
  simplification, matching automotive's own 'no offset/oblique impact'
  disclosure for its horizontal collision)."
  0.15)

(def ^:const device-half-h-m
  "Device AABB half-thickness (m) -- ~2 cm total thickness, plausible
  for a closed laptop-class trade-in device. A disclosed geometric
  simplification (a real device is not a uniform box), the same
  'packaging-envelope box, not a styled body' honesty automotive's
  vehicle AABB already discloses."
  0.01)

(def ^:const floor-half-w-m
  "Test-surface AABB half-width (m) -- wide enough the device's full
  footprint always overlaps it; the immovable drop-test-rig floor/
  platen, not a modeled ground plane."
  5.0)

(def ^:const floor-half-h-m
  "Test-surface AABB half-thickness (m) -- an arbitrary thin static
  slab; only its top face (at `floor-half-h-m`) matters as the real
  contact plane."
  0.05)

(def ^:const settle-ticks
  "Extra ticks appended after the device is expected to reach the
  test-surface, so the trajectory also captures post-impact settling
  -- the SAME technique and rationale (positional correction converges
  geometrically, `0.8` per tick) `vdesign.simphysics/settle-ticks`
  documents; 15 ticks converges residual overlap to ~3e-11."
  15)

(defn- give-for
  [device-class]
  (get device-give-m device-class (:laptop device-give-m)))

(defn closed-form-impact-decel-g
  "The closed-form (constant-deceleration-ramp) counterpart of
  `run-drop-simulation`'s time-stepped `:sim-impact-decel-g`, for
  `device-class` -- derived from real free-fall kinematics alone (no
  physics-2d call): impact speed v0 = sqrt(2 * g * drop-height-m)
  (elementary Galilean free-fall, v^2 = 2*g*h); a body decelerating
  UNIFORMLY from v0 to 0 over `give-for` class's own assumed stopping
  distance d has average deceleration a = v0^2 / (2*d) (from
  v^2 = 2*a*d), i.e. in g units a/g = (2*g*h) / (2*d*g) = h/d. Used only
  by `crosscheck` below as a coarse sanity cross-reference -- see that
  fn's docstring for why the SIMULATED reading is always exactly 2x
  this."
  [device-class]
  (/ drop-height-m (give-for device-class)))

(defn run-drop-simulation
  "Time-steps a REAL `physics-2d` world for `trade-in-unit`'s own
  recorded `:device-class`/`:device-mass-kg` functional drop/shock test
  and returns:

    {:trajectory [{:tick :position :velocity} ...]   ; device body only
     :sim-impact-decel-g n :sim-impact-penetration-m n
     :ticks n :dt n :impact-mps n}

  What is REAL: the device and the test-surface are actual `physics_2d`
  `Body2D`/AABB `Collider2D` entities; `physics_2d/world-step` actually
  integrates gravity/velocity/position and actually runs its
  broadphase/narrowphase collision detection + impulse resolution +
  positional correction over N discrete ticks -- `:trajectory` is the
  ACTUAL per-tick output of that solver, read back tick by tick, not
  synthesized after the fact. The device starts at rest at a height
  such that it free-falls exactly `drop-height-m` (real gravity
  integration) before its AABB first overlaps the static test-surface
  AABB (`physics_2d` treats the test-surface's `mass 0` as infinite
  effective mass -- an immovable rig floor/platen, matching how a real
  drop-test rig's impact surface is rigid/bolted down, exactly the
  role `vdesign.simphysics`'s static barrier AABB plays for
  automotive's crash barrier).

  Deliberate modeling simplifications (disclosed, not hidden -- see
  each constant's own docstring for the specific disclosed prior):
  the device is a single AABB box (no internal-component/PCB
  deformation geometry); the test-surface is a rigid, non-deforming
  slab; `:device-mass-kg` is passed straight through as the `physics_2d`
  body's own mass, but -- exactly like `automotive.robotics`'s crash
  telemetry against an immovable barrier -- `physics_2d`'s impulse
  resolver makes the device's OWN velocity change on impact provably
  INDEPENDENT of its own mass (mass cancels algebraically in
  `resolve-contact` when the other body's inverse mass is exactly
  zero); `physics_2d`'s impulse resolver has no progressive
  crush/cushioning-stiffness model -- whichever tick first detects ANY
  AABB overlap fully zeroes the closing velocity in that ONE tick
  (given `restitution` 0), a discrete, instantaneous 'boxcar' stop, not
  a continuous force ramp. Left at an arbitrary fixed timestep, the
  resulting `:sim-impact-decel-g` would be dominated by whatever `dt`
  happened to be chosen, not a meaningful physical reading -- exactly
  the problem `vdesign.simphysics` documents and solves the same way:
  `dt` here is deliberately derived from THIS device class's own
  assumed internal 'give' / impact speed (`give-for class / v0`, the
  nominal transit time across that assumed stopping distance) -- a
  principled, not arbitrary, choice. By exact kinematic identity (see
  `closed-form-impact-decel-g`'s docstring), a boxcar (instantaneous,
  constant) stop over that transit time is ALWAYS 2x the closed form's
  own averaged/ramp deceleration for the same impact speed/give -- the
  SAME v^2=2ad vs a=v/dt-with-dt=d/v identity `vdesign.simphysics`
  documents, applied here to a vertical free fall instead of a
  horizontal crash."
  [{:keys [device-class device-mass-kg]}]
  (let [give        (give-for device-class)
        mass        (double (or device-mass-kg 1.0))
        v0          (sqrt* (* 2.0 gravity-mps2 drop-height-m))
        dt          (/ give v0)
        floor-top   floor-half-h-m
        device-y0   (+ floor-top drop-height-m device-half-h-m)
        fall-time-s (sqrt* (/ (* 2.0 drop-height-m) gravity-mps2))
        fall-ticks  (long (ceil* (/ fall-time-s dt)))
        ticks       (+ fall-ticks settle-ticks)
        device (p2d/make-body {:position [0.0 device-y0] :velocity [0.0 0.0]
                                :mass mass :restitution 0.0 :friction 0.0
                                :collider (p2d/make-aabb-collider device-half-w-m device-half-h-m)
                                :user-data :device})
        test-surface (p2d/make-body {:position [0.0 0.0] :velocity [0.0 0.0]
                                      :mass 0.0 :restitution 0.0 :friction 0.0
                                      :collider (p2d/make-aabb-collider floor-half-w-m floor-half-h-m)
                                      :user-data :test-surface})
        w0 (p2d/world-new world-gravity)
        [w1 did] (p2d/world-add w0 device)
        [w2 _sid] (p2d/world-add w1 test-surface)
        worlds (reductions (fn [w _] (p2d/world-step w dt)) w2 (range ticks))
        trajectory (mapv (fn [tick world]
                            (let [b (nth (:bodies world) did)]
                              {:tick tick :position (:position b) :velocity (:velocity b)}))
                          (range (count worlds)) worlds)
        vys (mapv (comp second :velocity) trajectory)
        peak-decel-mps2 (->> (map (fn [va vb] (abs* (/ (- vb va) dt))) vys (rest vys))
                              (reduce max 0.0))
        penetrations-m (mapv (fn [{:keys [position]}]
                                (max 0.0 (- floor-top (- (second position) device-half-h-m))))
                              trajectory)]
    {:trajectory trajectory
     :sim-impact-decel-g (/ peak-decel-mps2 gravity-mps2)
     :sim-impact-penetration-m (reduce max 0.0 penetrations-m)
     :ticks (count trajectory)
     :dt dt
     :impact-mps v0}))

(def ^:const crosscheck-ratio-low
  "Lower bound of the sim/closed-form `:sim-impact-decel-g` ratio
  treated as 'within tolerance'. See `run-drop-simulation`'s docstring:
  `physics_2d`'s single-tick full-stop is, by exact kinematic identity,
  ~2x the closed form's averaged/ramp deceleration for the same impact
  speed + give -- this band is centered on that ~2x relationship (with
  slack for discretization/tick-count effects), the SAME
  `vdesign.simphysics/crosscheck-ratio-low` discipline."
  1.3)

(def ^:const crosscheck-ratio-high
  "Upper bound -- see `crosscheck-ratio-low`."
  3.0)

(defn crosscheck
  "Coarse, HONEST cross-check between `run-drop-simulation`'s
  time-stepped `:sim-impact-decel-g` and this SAME trade-in-unit's
  `closed-form-impact-decel-g`. NOT a validation of either model's
  absolute accuracy -- `physics_2d` has no material/cushioning-
  stiffness model at all, and the closed form is itself a reduced-order
  constant-deceleration estimate. `:within-tolerance?` only means
  'these two coarse, related idealizations of the same event are
  within a documented, explainable factor of each other' -- nothing
  stronger, the SAME `vdesign.simphysics/crosscheck` discipline."
  [trade-in-unit]
  (let [sim (run-drop-simulation trade-in-unit)
        closed (closed-form-impact-decel-g (:device-class trade-in-unit))
        ratio (/ (:sim-impact-decel-g sim) closed)]
    {:sim-impact-decel-g (:sim-impact-decel-g sim)
     :closed-form-decel-g closed
     :ratio ratio
     :within-tolerance? (<= crosscheck-ratio-low ratio crosscheck-ratio-high)}))

(defn drop-test-telemetry-for
  "Runs the REAL `run-drop-simulation` for `trade-in-unit`'s own
  recorded `:device-class`/`:device-mass-kg` and returns the actual
  simulated trajectory telemetry: `{:sim-impact-decel-g n
  :sim-impact-penetration-m n :ticks n :dt n :impact-mps n}`.
  `:sim-impact-decel-g`/`:sim-impact-penetration-m` are the SAME fields
  `run-drop-simulation`'s own docstring documents as derived from the
  actual simulated velocity/position trajectory, not invented. Pure,
  deterministic -- no IO; the same `:device-class` always reproduces
  the same telemetry (mass provably does not move it -- see
  `run-drop-simulation`'s docstring)."
  [trade-in-unit]
  (select-keys (run-drop-simulation trade-in-unit)
               [:sim-impact-decel-g :sim-impact-penetration-m :ticks :dt :impact-mps]))

(defn drop-test-impact-out-of-tolerance?
  "Ground-truth check: does `trade-in-unit`'s own recorded
  `:sim-impact-decel-g` (the REAL `physics-2d` free-fall/impact
  trajectory telemetry already on file for this device -- see
  `drop-test-telemetry-for`) exceed `decel-ceiling-g`? Needs no mission
  run -- its input is a permanent field already on the device, the SAME
  shape `sanitization-incomplete?` above and `automotive.robotics/
  crash-simulation-out-of-tolerance?` use."
  [{:keys [sim-impact-decel-g]}]
  (and (number? sim-impact-decel-g) (> sim-impact-decel-g decel-ceiling-g)))

(defn simulate-drop-test
  "Run the robot functional drop/shock-test mission for
  `trade-in-unit-id` (`trade-in-unit` is the full record, incl.
  `:device-class`/`:device-mass-kg`). Actually runs the REAL
  `physics-2d`-stepped free-fall/impact trajectory
  (`drop-test-telemetry-for`) and derives `:passed?`/telemetry from it.
  Returns {:mission .. :actions [{:action .. :proof ..} ..] :passed?
  bool :sim-impact-decel-g n :sim-impact-penetration-m n}.
  Deterministic: :passed? is derived from the device's OWN recorded
  `:device-class`/`:device-mass-kg` via the REAL simulated trajectory
  (`drop-test-impact-out-of-tolerance?`), never invented or randomized
  -- `kotoba.robotics` mandates no network/IO, and a repeatable
  simulation is what makes the governor's independent recheck
  meaningful."
  [trade-in-unit-id trade-in-unit]
  (let [telemetry (drop-test-telemetry-for trade-in-unit)
        out-of-range? (drop-test-impact-out-of-tolerance? telemetry)
        reading (if out-of-range? :impact-out-of-tolerance :nominal)
        mission (robotics/mission (str "mission-" trade-in-unit-id "-drop-test")
                                   :robot/drop-shock-test-cell-1
                                   :trade-in-functional-drop-shock-test
                                   :boundaries {:station "trade-in-intake-drop-test-cell"}
                                   :max-steps (count drop-test-mission-actions))
        actions (mapv (fn [{:keys [step kind safety]}]
                        (let [a (robotics/action (str (:mission/id mission) "-" (name step))
                                                  (:mission/id mission) kind safety
                                                  :params {:step step :trade-in-unit-id trade-in-unit-id})]
                          {:action a
                           :proof (robotics/telemetry-proof (:mission/id mission) step reading
                                                             :provenance :simulated)}))
                      drop-test-mission-actions)]
    {:mission mission
     :actions actions
     :passed? (not out-of-range?)
     :sim-impact-decel-g (:sim-impact-decel-g telemetry)
     :sim-impact-penetration-m (:sim-impact-penetration-m telemetry)}))

(defn drop-test-out-of-tolerance?
  "Independent ground-truth recheck for the governor: does
  `trade-in-unit`'s OWN current, on-file real drop-test telemetry
  (`:sim-impact-decel-g`) fall out of tolerance right now? Ignores
  whatever :passed? verdict a prior mission run stored -- identical in
  spirit to `sanitization-incomplete?` above and `automotive.robotics/
  simulation-out-of-tolerance?`."
  [trade-in-unit]
  (drop-test-impact-out-of-tolerance? trade-in-unit))
