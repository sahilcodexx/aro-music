# Security

## Reporting a vulnerability

Do not include passwords, account cookies, signing files, private playlist data, or exploit details in a public issue. Use GitHub private vulnerability reporting if it is available for this repository. Otherwise, contact the maintainer through a private channel already established with the project; no separate security mailbox is currently configured.

Include affected version, platform, a minimal reproduction, impact, and sanitized evidence. There is no guaranteed response deadline or formal support policy for this development candidate.

## Credential handling

Connect accounts through their app flows. Never commit API keys, login credentials, session cookies, `keystore.properties`, or signing material. Keep the release signing key backed up securely and use the GitHub `production` environment secrets for CI. If a credential is exposed, revoke or rotate it at the provider and remove it from affected logs and history.

External music services and self-hosted servers have separate security and privacy policies. Only connect servers you trust. An account-independent CI pass does not validate a provider's authentication or private data handling.
