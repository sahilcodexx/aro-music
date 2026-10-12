# Contributing to Aro

Thanks for helping improve Aro. Start with the [development guide](DEVELOPMENT.md) and [release validation record](RELEASE_CHECKLIST.md).

## Report a problem

Search [existing issues](https://github.com/sahilcodexx/aro-music/issues), then include the Aro version, Android version, device model, affected music source, steps to reproduce, expected behavior, and actual behavior. Screenshots and short sanitized logs help. Note whether the problem occurs in debug or signed release builds.

Never post session cookies, passwords, API keys, signing files, private playlist details, or unsanitized account logs. Follow [SECURITY.md](SECURITY.md) for vulnerabilities.

## Submit a change

1. Open a focused branch from `main` and describe the problem or feature.
2. Keep the existing Aro visual style unless the change explicitly concerns design.
3. Add meaningful regression coverage for behavior changes. Update affected documentation and the changelog.
4. Run the relevant wrapper tasks. Database changes need migration tests; playback changes need decoding checks; provider changes need mock coverage and live checks when an account is available.
5. Open a pull request using the template. State what passed, what was not tested, and any limitations.

Do not run simultaneous Gradle builds in the same checkout. Do not regenerate the lint baseline to hide new failures. Preserve upstream notices and identify the source and license of any imported code.

## Licensing

The combined app's licensing remains unresolved; see [LICENSE_STATUS.md](LICENSE_STATUS.md). Contributing does not grant permission to remove existing notices, relicense others' work, or publish restricted code. Resolve licensing before distribution.
