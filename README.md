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
| `:actuation/fulfill-order` | draft order-fulfillment record (always human; HARD hold if evidence incomplete or order total mismatched) |
| `:actuation/issue-sanitization-certificate` | draft Certificate-of-Data-Destruction record (always human; HARD hold if data-wipe missing or independently incomplete) |

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
