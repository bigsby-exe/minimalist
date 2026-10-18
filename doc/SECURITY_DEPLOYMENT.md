# Security hardening deployment

## Before deploying

- Use Ruby 3.4.9, matching `.ruby-version` and the existing lockfile.
- For this new deployment, initialize the database with `bin/rails db:prepare`.
  The schema includes the unique stack-token index. The migration also supports
  an already initialized database.
- Configure HTTPS at the ingress. Rails now enforces HTTPS, HSTS, and secure
  cookies in production. A TLS-terminating proxy must correctly set
  `X-Forwarded-Proto: https` and overwrite untrusted forwarded headers.
  Check health checks and redirect behaviour before rollout.
- Recompile assets to serve the updated vendored jQuery UI 1.14.2.

## Anonymous access links

This is a new deployment with no existing users or access links. Every new
stack receives a token generated from 32 cryptographically random bytes.
No legacy-token migration or rotation is required.

A stack URL remains a bearer credential granting read/write access. The new
`Referrer-Policy: no-referrer` header prevents browsers from forwarding it as a
referrer. Configure the reverse proxy, monitoring, and access logs to redact
`/s/:token` paths; Rails parameter filtering does not redact raw URL paths in
all logging systems.

## Authentication and contributor privacy

Google sign-in uses POST and OmniAuth's built-in authenticity verifier with the
Rails CSRF session key. The server-rendered buttons submit a global Rails token;
the sidebar uses the existing Rails UJS integration and CSRF meta tags.
Sign-out now requires DELETE through that same UJS integration. Browser actions
reject invalid CSRF tokens; token-authenticated API controllers remain separate.

Contributor email is explicitly conditional on list ownership using ActiveModel
Serializers 0.10's API. Serializer context uses `instance_options`.

## Vendored dependency provenance

`app/assets/javascripts/lib/jquery-ui.js` is the unmodified upstream full build:
https://code.jquery.com/ui/1.14.2/jquery-ui.js

It replaces 1.12.1, which predates the fixes for CVE-2021-41182,
CVE-2021-41183, CVE-2021-41184, and CVE-2022-31160.

References:
- https://jqueryui.com/upgrade-guide/1.13/
- https://blog.jqueryui.com/2022/07/jquery-ui-1-13-2-released/
- https://github.com/cookpad/omniauth-rails_csrf_protection#readme

## Validation limits

No tests were added or run, as requested. Ruby, Rails boot, database migrations,
and asset compilation were unavailable in the authoring environment. Review
login/logout, contributor permissions, and mouse/touch list sorting in staging
before deployment. This change is not a complete dependency vulnerability audit.
