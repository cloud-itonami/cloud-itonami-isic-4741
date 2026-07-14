# Security Policy

This project handles computer-retail order and trade-in-device
workflows, including a robot-executed data-sanitization mission.
Treat vulnerabilities as potentially high impact even when the demo
data is synthetic -- a Retail Governor bypass could mean a Certificate
of Data Destruction issued for a device that was never actually wiped.

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential exposure
- real customer or trade-in-device data exposure
- authorization bypass
- Retail Governor bypass (especially the data-wipe-mission-missing /
  sanitization-incomplete checks)
- audit-ledger tampering
- over-disclosure in reports or exports
- tenant isolation failures

## Reporting

Use GitHub private vulnerability reporting when available for the
repository. If that is unavailable, contact the repository maintainers
through the cloud-itonami organization before publishing details.

Include:

- affected commit or version
- reproduction steps
- expected and actual behavior
- impact on customer data, trade-in device data, policy enforcement or
  audit logging
- suggested fix, if known

## Production Guidance

- Store secrets outside Git.
- Keep real customer and trade-in-device data outside this repository.
- Run governor/policy tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for operators and service accounts.
- Never issue a Certificate of Data Destruction for a device whose
  data-wipe mission has not run and independently verified clean.
