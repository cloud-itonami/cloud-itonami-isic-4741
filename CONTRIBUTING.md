# Contributing

`cloud-itonami-isic-4741` accepts contributions to the OSS blueprint,
capability bindings, policy tests, documentation and operator model.

## Development
The capability layer lives in `kotoba-lang/*` libraries
(`langgraph`, `robotics`). This repo holds the business blueprint and
operator contracts.

```bash
clojure -M:dev:test
clojure -M:lint
clojure -M:dev:run
```

## Rules
- Do not commit real customer, trade-in-device, personal or credential
  data.
- Keep order fulfillment, sanitization-certificate issuance and
  disclosures behind the Retail Governor.
- Treat workflows as high-risk: add tests for robot-safety gating
  (data-wipe mission), evidence/record integrity, disclosure and audit
  logging.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests
PRs should describe: what behavior changed, which policy invariant is
affected, how it was tested, whether operator or certification docs
need updates.
