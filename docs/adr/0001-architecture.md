# ADR-0001: Retail Advisor ⊣ Retail Governor architecture

- Status: Accepted (2026-07-14)
- Repository: `cloud-itonami-isic-4741` (ISIC Rev.5 `4741`)

## Context

Computer/peripheral retail with a trade-in program (order fulfillment,
consumer-protection/distance-selling compliance, trade-in-device
condition grading, certified data sanitization, Certificate of Data
Destruction issuance) needs the same governed-actor pattern as the rest
of the cloud-itonami fleet: an untrusted advisor proposes; an
independent governor may HOLD; high-stakes actuation never
auto-commits.

A 2026-07-14 value-chain survey of the computer/electronics industry
found manufacturing stages covered (e.g. `cloud-itonami-isic-2620`
computer/peripheral manufacture) but the downstream RETAIL tier (ISIC
4741) had no actor at all -- this repository closes that gap, and is
the first cloud-itonami vertical to make the robotics premise
(ADR-2607011000) concrete for a RETAIL business rather than a factory:
a computer retailer that takes trade-ins needs a robot-executed,
NIST-SP-800-88-Rev.2-compliant data-sanitization cell before a
trade-in device can be resold, following the robotics-process-
simulation pattern `automotive.robotics` established
(`cloud-itonami-isic-2910`, ADR-2607142800).

## Decision

1. Namespaces live under `techretail.*` with the standard
   facts / registry / store / governor / phase / advisor / operation /
   sim / export / robotics shape.
2. UNLIKE most single-entity siblings, this domain has TWO entity
   types: an **order** (a customer purchase, possibly bundling a
   trade-in) and a **trade-in-unit** (the traded-in device). The two
   entities are only descriptively linked (`:trade-in-unit-id` on the
   order) -- no governor check traverses the link; each entity's
   lifecycle is independently governed.
3. Dual actuation, one per entity:
   - `:actuation/fulfill-order` (order-fulfillment draft, on the order)
   - `:actuation/issue-sanitization-certificate` (Certificate-of-Data-
     Destruction draft, on the trade-in-unit)
4. Double-actuation guards use dedicated booleans
   (`:order-fulfilled?`, `:sanitization-certified?`), never a status
   lifecycle (ADR-2607071320 / `cloud-itonami-isic-6492` lesson).
5. `order-total-mismatch?` (an order's own recorded total vs. its own
   recorded line-items) and `sanitization-incomplete?` (a trade-in
   device's own post-wipe verification-read field) continue the fleet
   two-sided range-check family (after testlab / conservation / water /
   aerospace / steelworks / turbine / automotive), applied here to
   commerce arithmetic and physical data-sanitization ground truth
   respectively.
6. Trade-in grading/defect-unresolved is evaluated unconditionally so
   `:trade-in-condition/screen` itself can HARD-hold (parksafety
   ADR-2607071922 Decision 5 discipline, continued by
   `automotive.governor`'s `end-of-line-defect-unresolved-violations`).
7. Consumer-protection spec-basis catalog seeds JPN (消費者庁 /
   特定商取引法) / USA (FTC 16 CFR Part 435) / EUR (Directive
   2011/83/EU) only; missing jurisdictions (incl. GBR) are uncovered,
   never fabricated -- following `automotive.facts`'s honesty
   discipline. Japan's entry is deliberately narrow: 通信販売 does NOT
   carry a statutory cooling-off right under 特定商取引法 (that is a
   door-to-door/telemarketing concept); the catalog cites the seller-
   identity/price/return-policy disclosure duties the law actually
   imposes on mail-order, not an invented cooling-off right.
8. The media-sanitization evidence basis cites **NIST SP 800-88 Rev. 2**
   (2025-09), NOT the originally-suggested Rev. 1 -- Rev. 1
   (2014-12-17) was withdrawn 2025-09-26 and superseded by Rev. 2. This
   repository cites the current, in-force standard, the same honesty
   discipline applied to jurisdiction coverage above.

## Consequences

(+) Computer retailers with a trade-in program gain a forkable OSS
operating stack with auditable governor holds.
(+) Reuses langgraph + store dual-backend parity without new physics.
(+) First cloud-itonami vertical to demonstrate the robotics premise
concretely in a RETAIL (not manufacturing) context.
(−) No physical store/warehouse digital-twin tick in this repo
(export + operator console samples only).
(−) Consumer-protection-authority coverage is a starting catalog (JPN/
USA/EUR), not exhaustive -- GBR and others are explicitly uncovered.

## Related

- Superproject fleet ADR for this promotion (ADR-2607150400)
- ADR-2607011000 (robotics premise, fleet-wide)
- ADR-2607142800 (robotics-process-simulation pattern, reference impl
  `cloud-itonami-isic-2910`)
- Sibling architecture: `cloud-itonami-isic-2910` docs/adr/0001,
  `cloud-itonami-isic-4711` (community retail, non-trade-in sibling)
