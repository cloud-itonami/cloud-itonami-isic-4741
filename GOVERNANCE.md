# Governance

`cloud-itonami-isic-4741` is an OSS open-business blueprint for computer/
peripheral/software retail with a trade-in program, robotics-premised.

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a robot action the governor refuses is never dispatched to hardware.
- the Retail Governor remains independent of the advisor.
- hard policy violations (fabricated consumer-protection spec-basis,
  incomplete evidence, an unverified data wipe, a mismatched order
  total, an unresolved trade-in grading defect, a double fulfillment/
  certificate-issuance) cannot be overridden by human approval.
- every fulfillment, sign-off, sanitization-certificate issuance and
  disclosure path is auditable.
- sensitive customer and trade-in device data stays outside Git.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or
license should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification
is a separate trust mark and should require security, robot-safety,
audit and data-flow review.

Certified operators can lose certification for:
- bypassing the Retail Governor's robot-safety, evidence or
  double-actuation checks
- mishandling customer or trade-in device data
- issuing a Certificate of Data Destruction without a verified wipe
- misrepresenting certification status
- failing to respond to security or safety incidents
