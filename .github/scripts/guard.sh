#!/usr/bin/env bash
# Guard checks. Any match fails the build. See SECURITY.md.
set -uo pipefail
fail=0

echo "1) Banned SDKs in Gradle/TOML files"
if git ls-files '*.gradle*' '*.toml' | xargs -r grep -niE 'firebase|crashlytics|sentry|bugsnag|appcenter|amplitude|mixpanel|segment|admob|appsflyer|onesignal'; then
  echo "::error::Banned analytics/crash/ads SDK found"; fail=1
fi

echo "2) Destructive migration"
if grep -rn 'fallbackToDestructiveMigration' app/src; then
  echo "::error::fallbackToDestructiveMigration is forbidden"; fail=1
fi

echo "3) Credentials embedded in URLs (outside tests, except *.example.test hosts)"
hits=$(git ls-files | grep -vE '(^|/)src/test/|^\.github/scripts/guard\.sh$' | grep -vE '\.(keystore|jar|png|webp)$' \
  | xargs -r grep -noE 'https?://[^/[:space:]:@]+:[^/[:space:]@]+@[A-Za-z0-9.-]+' \
  | grep -vE '@([A-Za-z0-9-]+\.)*example\.test$' || true)
if [ -n "$hits" ]; then
  echo "$hits"; echo "::error::Credential-bearing URL found"; fail=1
fi

echo "4) Secret/local files tracked"
if git ls-files | grep -E '(^|/)(local\.properties|google-services\.json)$|\.env$'; then
  echo "::error::local.properties, google-services.json or *.env must not be committed"; fail=1
fi

if [ "$fail" -ne 0 ]; then exit 1; fi
echo "All guard checks passed."
