# cloud-itonami-isic-4741

Open Business Blueprint for **ISIC Rev.5 4741**: retail sale of
computers, peripheral equipment and software in specialized stores --
order fulfillment, per-jurisdiction consumer-protection/distance-
selling evidence verification, trade-in-device condition screening,
robot-executed certified data-erasure and Certificate-of-Data-
Destruction issuance for a computer retailer that also runs a
buy-back / trade-in-for-credit program.

This repository publishes a computer-retail-plus-trade-in actor --
order intake, per-jurisdiction consumer-protection rules verification,
trade-in-condition defect screening, robot certified data-wipe mission
and Certificate-of-Data-Destruction finalization -- as an OSS business
that any qualified computer retailer can fork, deploy, run, improve
and sell, so a retailer keeps its own order and trade-in-device history
instead of renting a closed POS / ITAD SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet -- here it is **Retail Advisor ⊣
Retail Governor**.

## Scope note: downstream retail, not manufacturing

This repository is scoped to **selling** computers/peripherals at
retail and running the trade-in program around that sale (order
fulfillment, consumer-protection evidence, trade-in-device
sanitization). It is not a manufacturing vertical. Distinct from:

- `cloud-itonami-isic-2610` -- manufacture of electronic components
  and boards **manufacturing** (upstream)
- `cloud-itonami-isic-4711` -- community retail (general merchandise,
  not computer/peripheral-specialized, no trade-in program)
- `cloud-itonami-isic-4730` -- automotive-fuel-retail (forecourt
  operator, unrelated product category)

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here the retailer's trade-in
program needs a robot-executed, NIST-SP-800-88-Rev.2-compliant data-
sanitization/erasure cell before a traded-in device can be resold --
a genuine physical-domain robot task fitting THIS vertical
specifically (not a factory assembly line). Robots (device connect-
and-authenticate, sanitization pass, post-wipe functional test) operate
under an actor that proposes actions and an independent **Retail
Governor** that gates them. The governor never issues a Certificate of
Data Destruction itself; `:actuation/fulfill-order`/`:actuation/issue-
sanitization-certificate` require human sign-off.

**Robot process simulation is concrete, not just a flag**
(ADR-2607142800, extending ADR-2607011000, following the reference
implementation `automotive.robotics` established in
`cloud-itonami-isic-2910`): `techretail.robotics` walks every trade-in
device through a robot-executed certified data-wipe mission
(`kotoba.robotics` mission/action/telemetry-proof contracts) --
device connect-and-authenticate, a cryptographic-erase/multi-pass
sanitization pass, and a post-wipe functional test/verification read --
before `:actuation/issue-sanitization-certificate` is proposable. The
Retail Governor independently re-derives the device's own post-wipe
verification-read completeness from ground-truth fields, never
trusting the mission's self-reported verdict alone.

**The trade-in-unit's functional drop/shock test is now a REAL,
time-stepped physics simulation, not a symbolic field comparison**
(ADR-2607152000, extending ADR-2607151600's automotive pilot to this
vertical; the data-wipe mission above stays symbolic, unchanged --
data sanitization genuinely is not a physics event). This repository
takes a REAL git-coordinate dependency on
[`kotoba-lang/physics-2d`](https://github.com/kotoba-lang/physics-2d)
(a real, tested 2D rigid-body impulse/gravity solver, pinned by SHA in
`deps.edn`), and `techretail.robotics/simulate-drop-test` actually
calls it: a device rigid body (the trade-in-unit's own recorded
`:device-class`/`:device-mass-kg`) free-falls under REAL gravity
integration (`physics-2d/world-new [0.0 -9.81]`) from a standard
~1.0 m functional drop-test height (a common consumer-electronics/
ITAD tabletop-height reference, broadly consistent with the
IEC 60068-2-31/-32 rough-handling/free-fall test family) and impacts a
static test-surface rigid body over real simulated ticks -- a genuine
ITAD/electronics-refurbishment QA procedure, not invented for this
ADR. `:sim-impact-decel-g`, the real peak impact deceleration, is read
directly off the simulated velocity trajectory (never invented) and
checked against `decel-ceiling-g` (400g) -- a REASONED ENGINEERING
ESTIMATE anchored on the order of magnitude commonly published in
laptop-class HDD/SSD non-operating-shock datasheet specs (roughly
300-1000G for short pulses), honestly disclosed as an estimate, not a
verbatim single citation (see `techretail.robotics/decel-ceiling-g`'s
own docstring for the full honesty disclosure). The Retail Governor
independently re-derives this pass/fail verdict from the device's own
recorded REAL simulated telemetry, never trusting the mission's
self-reported verdict alone -- the same discipline
`automotive.governor`'s `robotics-simulation-violations`
(ADR-2607151600) established for a real physics-2d-backed telemetry
field specifically. Honest scope: this is a 2D projection (physics-2d
has no 3D solver), the device is a single AABB box (no internal-
component deformation geometry), and unlike automotive's pilot there
is no rendered WebGPU scene bridge or CAD/CAM geometry pipeline here --
the honest scope for this vertical is the physics timestep and its
governor-checked reading only (ADR-2607152000).

## Core contract

```text
order intake + consumer-protection rules verify + trade-in-condition screen
  -> Retail Advisor proposal
  -> Retail Governor (HARD holds un-overridable)
  -> phase gate (actuation always escalates)
  -> human approval for high stakes
  -> append-only ledger + draft records
```

## Actuation honesty

Fulfilling an order and issuing a Certificate of Data Destruction
produce **unsigned draft records and ledger facts only**. This actor
does not talk to real POS/shipping/ITAD control systems. Signature and
physical shipment/sanitization-rig dispatch are the computer
retailer's own acts.

## Ops

| Op | Effect |
|---|---|
| `:order/intake` | normalize order directory patch (phase 3 may auto-commit when clean) |
| `:consumer-protection-rules/verify` | per-jurisdiction consumer-protection/distance-selling evidence checklist (always human) |
| `:trade-in-condition/screen` | trade-in device grading/defect screen (HARD hold if unresolved) |
| `:robotics/simulate-data-wipe` | robot certified data-wipe mission (always human; required on file before certificate issuance) |
| `:robotics/simulate-drop-test` | robot functional drop/shock-test mission -- REAL `physics-2d` free-fall/impact simulation (ADR-2607152000; always human; required on file before certificate issuance) |
| `:actuation/fulfill-order` | draft order-fulfillment record (always human; HARD hold if evidence incomplete or order total mismatched) |
| `:actuation/issue-sanitization-certificate` | draft Certificate-of-Data-Destruction record (always human; HARD hold if data-wipe missing/incomplete or drop-test missing/independently out of tolerance) |

## Social / regulatory hand-off

```clojure
(require '[techretail.store :as store]
         '[techretail.export :as export])

(def db (store/seed-db))
(export/audit-package db)           ;; EDN maps for consumer-protection/ITAD hand-off
(export/package->csv-bundle db)     ;; CSV bundle (orders/trade-in-units/ledger/fulfillments/sanitization-certificates)
```

Operator console (static sample): `docs/samples/operator-console.html`.

## Develop

```bash
clojure -M:dev:test
clojure -M:lint
clojure -M:dev:run
```

## License

AGPL-3.0-or-later — see `LICENSE`.

## Operator console (Pages)

After enabling GitHub Pages (Settings → Pages → GitHub Actions), the
static console is at:

https://cloud-itonami.github.io/cloud-itonami-isic-4741/

Local: open `docs/index.html` or `docs/samples/operator-console.html`.

## Export audit package (CLI)

```bash
clojure -M:dev:export
# or: clojure -M:dev:export /tmp/audit-4741
```

Writes CSV files under `out/audit-package/` (or the given directory).
