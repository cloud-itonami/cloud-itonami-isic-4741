(ns techretail.cad
  "CAD bridge -- turns a trade-in device's own recorded packaging-
  envelope dimensions (when on file, e.g. from an intake grading/
  photography measurement step) into a coarse BREP envelope via
  `kotoba-lang/org-iso-10303`'s `brep.feature` parametric feature tree,
  then tessellates it (`brep.tessellate`) for `techretail.robotics`'s
  `:device` AABB placement (the functional drop/shock-test rigid body,
  ADR-2607152000) and `techretail.scene`'s render bridge (ADR-2607998500,
  closing out the 6-vertical digital-twin wave ADR-2607160000 started --
  a direct port of `autoparts.cad`'s/`fab.cad`'s/`quarryops.cad`'s
  reference pattern to this actor's own no-sibling-design-library case:
  this ns lives directly in `techretail.*`, same reasoning ADR-2607152000
  already used for putting the physics module directly in `techretail.
  robotics`).

  Honest scope: this is a PACKAGING ENVELOPE -- a bounding-box
  approximation of a laptop/desktop/phone/tablet-class trade-in device's
  volume (length x width x height) -- not a modeled hinge/port/chassis-
  seam surface, and not the device's real internal component geometry.
  `brep.feature/evaluate` currently only realizes an `:extrude`
  `:operation :new` as a fixed +/-0.5-unit-square cross-section extruded
  along the given direction/distance (sketch entities are not yet
  consumed by `evaluate`; revolve/fillet/chamfer/boolean are documented
  not-yet-implemented in `org-iso-10303`), so the cross-section here is
  realized at unit scale, then the resulting vertices are scaled non-
  uniformly to the target dimensions -- the SAME documented work-around
  `vdesign.cad`/`autoparts.cad`/`fab.cad`/`quarryops.cad` use for the
  kernel's current maturity, not a new one invented for this ns.

  HONEST DESIGN CHOICE, GENUINELY DIFFERENT FROM `quarryops.cad`'S OWN
  DEFAULT DESIGN (verified against THIS vertical's own actual pre-
  existing code, not assumed to carry over unchanged -- ADR-2607998500
  explicitly calls for checking, not copying): `quarryops.robotics`'s
  `:fragment` AABB was ALREADY a per-extraction FORMULA (cube edge
  back-derived from `:fragment-mass-kg` via a disclosed rock-density
  assumption) before its own digital-twin ADR, so `quarryops.cad`'s
  default is that SAME formula, redefined. `techretail.robotics`'s
  `:device` AABB has NEVER been mass-derived, though -- `device-half-
  w-m`/`device-half-h-m` have been bare FIXED numeric constants (0.15 m
  / 0.01 m) since ADR-2607152000 introduced the drop-test simulation,
  identical for every device regardless of `:device-class`/`:device-
  mass-kg`. This is the SAME situation `autoparts.cad`'s/`fab.cad`'s own
  docstrings disclose for their pre-ADR `jaw-half-w-m`/`anchor-half-w-m`
  constants, not `quarryops.cad`'s mass-formula situation -- so this ns
  follows `autoparts.cad`'s/`fab.cad`'s DESIGN (disclosed FIXED default
  literals chosen to exactly reproduce the pre-existing constants), not
  `quarryops.cad`'s (a formula redefinition). Two designs were
  considered for bridging the 'only `:device-class`/`:device-mass-kg`
  exist today, no linear-dimension field' gap, mirroring `autoparts.
  cad`'s own considered-and-rejected alternative:

  (a) Back-derive length/width/height from `:device-class` alone via a
      lookup table of 'typical' per-class dimensions (e.g. a typical
      laptop ~320x220x18mm, a typical desktop-tower ~180x400x450mm).
      Rejected: a class-keyed table is still not a real per-device
      measurement -- it would silently change the simulated AABB (and
      therefore `:sim-impact-decel-g`/`:sim-impact-penetration-m`) for
      every device-class OTHER than whichever class the table happened
      to match the pre-existing flat 0.15m/0.01m constants -- a
      behavior change dressed up as 'more data', for devices that
      genuinely have nothing new on file. This would specifically flip
      `techretail.store/demo-data`'s existing fixture behavior
      (unit-1..5, none of which carry any new geometry field) out from
      under this ADR's own 'zero behavior change when nothing on file'
      requirement, including unit-5's documented HOLD finding.
  (b) A new, EXPLICITLY OPTIONAL `:device-length-mm`/`:device-width-mm`/
      `:device-height-mm` triple a trade-in-unit MAY carry once a real
      intake measurement is on file, falling back to a disclosed FIXED
      default (chosen to exactly reproduce the SAME `device-half-w-m`/
      `device-half-h-m` figures `techretail.robotics` used as bare AABB
      constants before this ADR) when absent.

  (b) is the more honest choice and is what this ns implements -- see
  `envelope-dims-mm`.

  A SECOND, real, vertical-specific property worth disclosing on its
  own (see `techretail.robotics/device-half-extents-m` for the full
  derivation this drives, CHECKED against this vertical's own actual
  physics, not assumed to carry over from `quarryops.cad`'s own axis
  mapping): like `quarryops.robotics`'s vertical free-fall (and UNLIKE
  `autoparts.robotics`'s/`fab.simphysics`'s horizontal pull-test), this
  vertical's physics is a VERTICAL free-fall under gravity, so it is
  HEIGHT (not length/width) that feeds the fall-axis AABB half-extent
  `techretail.robotics` actually integrates against; LENGTH here feeds
  the lateral (x-axis) half-extent, which `techretail.robotics`'s own
  docstring already discloses as 'physically irrelevant, purely
  lateral' (the wide, fixed `floor-half-w-m` test-surface never
  meaningfully constrains it); WIDTH is NOT consumed by the 2D drop-
  test physics at all, kept only so the tessellated BREP envelope is a
  genuine 3D box and so `techretail.motionplan`'s working-height
  derivation has a real height figure to read -- the SAME 'not consumed
  by the 2D physics, kept only for BREP 3D-ness' role WIDTH/HEIGHT play
  in `quarryops.cad`'s/`autoparts.cad`'s own docstrings, just on this
  vertical's own specific axis pair (checked against `techretail.
  robotics`'s own placement algebra, not assumed).

  A THIRD, genuinely orthogonal property, disclosed because it is easy
  to conflate with the above: `techretail.robotics/device-give-m` (this
  vertical's per-`:device-class` assumed internal shock-absorbing
  'give', which derives the simulation's principled `dt`) is COMPLETELY
  UNRELATED to this ns's envelope geometry -- `dt` is a function of
  `:device-class` alone (via `give-for`) and `drop-height-m`/
  `gravity-mps2` (both fixed constants), never of `:device-length-mm`/
  `:device-width-mm`/`:device-height-mm`. This orthogonality (checked
  directly against `run-drop-simulation`'s source, not assumed) is WHY
  `techretail.robotics_test.clj`'s empirical sweep (ADR-2607998500)
  finds even STRONGER geometry-invariance for `:sim-impact-decel-g`/
  `:ticks`/`:dt` than `quarryops.robotics_test.clj` found for ITS own
  summary fields -- see that test file and `techretail.robotics`'s own
  updated docstring for the actual swept findings, not assumed from
  either prior vertical's result.

  Disclosed persistence gap (mirrors `autoparts.cad`'s/`fab.cad`'s/
  `quarryops.cad`'s own disclosed gap): `techretail.store/MemStore`'s
  `:trade-in-unit/upsert` merges arbitrary keys, so `:device-length-mm`/
  `:device-width-mm`/`:device-height-mm` round-trip fine through
  MemStore. `techretail.store/DatomicStore`'s schema/`trade-in-
  unit->tx`/`trade-in-unit-pull`/`pull->trade-in-unit` do not yet
  declare these attributes, so they are NOT persisted through a
  DatomicStore round-trip today -- a real, disclosed limitation, not
  silently papered over. `envelope-dims-mm`'s fallback defaults keep
  every downstream consumer (`techretail.robotics`, `techretail.scene`,
  `techretail.motionplan`) fully functional either way; extending the
  Datomic schema to persist real intake measurements is straightforward
  follow-up work, not done here."
  (:require [brep.feature :as feat]
            [brep.tessellate :as tess]))

(def ^:const default-device-length-mm
  "Fallback device-envelope length (mm, lateral/x-axis) when a trade-in-
  unit carries no real `:device-length-mm` -- DELIBERATELY chosen to
  exactly reproduce `techretail.robotics`'s prior `device-half-w-m`
  figure (0.15 m half-width = 0.30 m = 300 mm full length), so a trade-
  in-unit with no intake measurement on file gets the SAME AABB size
  this actor already used before ADR-2607998500 -- a plausible closed-
  laptop-class footprint length, not a measured value."
  300.0)

(def ^:const default-device-width-mm
  "Fallback device-envelope width (mm) -- NOT consumed by `techretail.
  robotics`'s 2D drop-test physics (only length/height feed the
  lateral/fall-axis AABB half-extents -- see ns docstring); kept only
  so the tessellated BREP envelope is a genuine 3D box rather than a
  degenerate flat sheet. A plausible closed-laptop-class footprint
  depth, not a measured value."
  220.0)

(def ^:const default-device-height-mm
  "Fallback device-envelope height (mm, VERTICAL/fall-axis) when a
  trade-in-unit carries no real `:device-height-mm` -- DELIBERATELY
  chosen to exactly reproduce `techretail.robotics`'s prior `device-
  half-h-m` figure (0.01 m half-thickness = 0.02 m = 20 mm full
  thickness), so a trade-in-unit with no intake measurement on file
  gets the SAME AABB size (and therefore the SAME simulated `:sim-
  impact-decel-g`/`:sim-impact-penetration-m`) this actor already used
  before ADR-2607998500 -- a plausible closed-laptop-class thickness,
  not a measured value."
  20.0)

(defn envelope-dims-mm
  "{:length-mm :width-mm :height-mm} for `trade-in-unit`: its OWN
  recorded `:device-length-mm`/`:device-width-mm`/`:device-height-mm`
  when present (a genuine, per-device intake measurement), or this ns's
  disclosed fixed defaults when absent -- see ns docstring for why
  these are FIXED literals (mirroring `autoparts.cad`'s/`fab.cad`'s own
  design), not a mass/class-derived formula (`quarryops.cad`'s own,
  genuinely different, design -- that vertical's pre-existing AABB was
  already mass-derived; this one's was not). `trade-in-unit` may be
  `nil`/`{}` (every field then falls back to its default)."
  [trade-in-unit]
  (let [{:keys [device-length-mm device-width-mm device-height-mm]} trade-in-unit]
    {:length-mm (double (or device-length-mm default-device-length-mm))
     :width-mm  (double (or device-width-mm default-device-width-mm))
     :height-mm (double (or device-height-mm default-device-height-mm))}))

(defn- scale-point [[x y z] sx sy sz]
  [(* x sx) (* y sy) (* z sz)])

(defn envelope-solid
  "Build+evaluate a single-sketch/extrude BREP feature tree sized to
  `trade-in-unit`'s envelope dims (`envelope-dims-mm`). Returns {:solid
  :edges :vertices :dims}. Direct port of `autoparts.cad/envelope-
  solid`/`fab.cad/envelope-solid`/`quarryops.cad/envelope-solid` -- see
  those ns's docstrings (and `vdesign.cad`'s, deeper still) for exactly
  why the cross-section is realized at unit scale then non-uniformly
  scaled. Throws ex-info only if evaluation fails, which it does not
  for this single-extrude case (per `brep.feature/evaluate`'s
  documented base-feature support)."
  [trade-in-unit]
  (let [{:keys [length-mm width-mm height-mm] :as dims} (envelope-dims-mm trade-in-unit)
        ;; sketch on XY (the footprint plane); extrude along Z by
        ;; height-mm -- matches autoparts.cad/fab.cad/quarryops.cad's
        ;; convention. NOTE (same disclosed simplification `quarryops.
        ;; scene`'s docstring documents for its own vertical): this
        ;; Z-up CAD-local convention is NOT the same axis as this
        ;; vertical's Y-up physics fall axis -- see `techretail.scene`'s
        ;; docstring for how the two are (and are not) reconciled.
        sketch  (feat/sketch-feature 1 (feat/sketch-plane-xy) [])
        extrude (feat/extrude-feature 2 1 [0.0 0.0 1.0] height-mm :new)
        tree    (-> (feat/feature-tree)
                    (feat/add-feature sketch)
                    (feat/add-feature extrude))
        [status result] (feat/evaluate tree)]
    (when (not= status :ok)
      (throw (ex-info "brep envelope evaluation failed" {:result result :trade-in-unit trade-in-unit})))
    (let [[solid edges vertices] result
          scaled (mapv #(update % :point scale-point length-mm width-mm 1.0) vertices)]
      {:solid solid :edges edges :vertices scaled :dims dims})))

(defn envelope-mesh
  "Tessellate an `envelope-solid` result into {:positions [[x y z] ...]
  :indices [i0 i1 i2 ...]} -- the shape `techretail.scene/scene-for`
  consumes. Direct port of `autoparts.cad/envelope-mesh`/`fab.cad/
  envelope-mesh`/`quarryops.cad/envelope-mesh`."
  [{:keys [solid edges vertices]}]
  (let [[positions indices] (tess/tessellate-solid solid edges vertices)]
    {:positions positions :indices indices}))
