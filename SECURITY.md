# Security policy

## Supported versions

Only the latest release receives security fixes.

| Version | Supported |
|---|---|
| latest 0.x | ✅ |
| older | ❌ |

## Reporting a vulnerability

**Please do not open a public issue.** Report it privately through
[GitHub private vulnerability reporting](https://github.com/mikysal78/openwisp-android/security/advisories/new)
(Security → Report a vulnerability).

Include, if possible: affected version, Android version, a description of the
issue and its impact, steps to reproduce or a proof of concept, and a
suggested fix.

What to expect:

- acknowledgement within **7 days**;
- an assessment and, if confirmed, a fix and a new release as soon as
  possible (target: 30 days for high/critical issues);
- a [GitHub Security Advisory](https://github.com/mikysal78/openwisp-android/security/advisories)
  published with the release; a **CVE** is requested through GitHub when the
  issue warrants one;
- credit in the advisory, unless you prefer to stay anonymous.

## Scope

In scope: this app (storage of the token, network requests, handling of
intents and notifications, the confirmation of actions).

Out of scope: OpenWISP and its API, openwisp-luci and openwisp-netconsole
(report those to their projects), Android itself, and the documented known
limits (see the README, "Security"): the OpenWISP token is one per user and
stays valid on the server after signing out.
