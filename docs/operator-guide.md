# Operator Guide

## First Deployment
1. Register retail-operations approvers, stores, orders, trade-in
   devices, personnel and sanitization-rig robots.
2. Import historical order / trade-in-device / consumer-protection
   verification records.
3. Run read-only validation and robot data-wipe mission dry-runs.
4. Configure consumer-protection evidence checklists and human
   sign-off paths.
5. Publish a dry-run audit export.

## Minimum Production Controls
- governor gate on every robot action before a Certificate of Data
  Destruction may be proposed
- human sign-off for `:actuation/fulfill-order`/`:actuation/issue-
  sanitization-certificate` (order fulfillment, Certificate-of-Data-
  Destruction issuance)
- audit export for every fulfillment, sign-off and disclosure
- backup manual process

## Certification
Certified operators must prove robot-safety integrity, evidence-backed
records and human review for safety-affecting actions.

## Operating states
intake : consumer-protection-rules-verify : trade-in-condition-screen : robotics-simulate-data-wipe : approve : fulfill-order : issue-sanitization-certificate : audit

## Audit export (social operation)

After a trading session, export the append-only package for
consumer-protection inspectors, ITAD partners or internal compliance:

```clojure
(require '[techretail.store :as store]
         '[techretail.export :as export])
(export/audit-package store)        ; EDN maps
(export/package->csv-bundle store)  ; CSV files as string map
```

Drafts remain **unsigned** — signing and physical shipment/sanitization-
rig dispatch are the computer retailer's own acts (see README
`Actuation honesty`).

Static UI sample: `docs/samples/operator-console.html`.
