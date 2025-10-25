# Security Policy

## Supported Versions

The following table outlines which versions of Railroad currently receive security updates.

| Version | Supported |
|----------|------------|
| Main branch (`main`) | ✅ |
| Latest stable release (`vX.Y.Z`) | ✅ |
| Older releases | ❌ |

We only provide security fixes for the **most recent stable release** and the `main` development branch.  
If you're using an older version, please update to the latest release.

---

## Reporting a Vulnerability

If you discover a vulnerability in Railroad or any related project (such as Switchboard, RailroadLogger, or the Plugin API), **please report it responsibly**.  
Do **not** disclose it publicly until it has been patched.

### 🔒 How to Report
- **Preferred:** [Create a private vulnerability report](https://github.com/Railroad-Team/Railroad/security/advisories/new)
- **Alternative:** Email the maintainers at **security@railroadide.dev**

Please include the following details:
- A clear description of the issue and its potential impact.
- Steps to reproduce (if applicable).
- Any relevant logs, crash reports, or proof of concept.
- A suggested fix or mitigation (optional but appreciated).

You can expect a response **within 48 hours**, and we'll work with you to confirm and fix the issue as quickly as possible.

---

## Disclosure Policy

- Once a fix is ready, we’ll release an updated version of Railroad.
- You’ll be credited for the discovery if you wish.
- We generally aim to disclose details publicly **after the patch release**, unless there’s a reason to delay for ecosystem safety.

---

## Security Best Practices for Plugin Developers

If you're developing plugins for Railroad:
- **Never** execute remote code or download arbitrary files without explicit user consent.
- **Always** verify signatures or hashes for remote content.
- **Avoid** storing credentials in plain text — use Railroad’s secure storage API if available.
- **Do not** request unnecessary permissions.
- **Respect user privacy** — plugins must not track or collect personal data without consent.

Plugins found violating these policies may be removed from the official plugin registry.

---

## Scope

This policy covers:
- Railroad IDE (core application)
- Railroad Plugin API
- Railroad Logger
- Switchboard service
- Official Railroad plugins

If the vulnerability affects a dependency or external service, we’ll coordinate disclosure with the relevant maintainers.

---

*Thank you for helping keep the Railroad ecosystem secure.*
