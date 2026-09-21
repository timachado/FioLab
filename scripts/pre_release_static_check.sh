#!/usr/bin/env bash
set -euo pipefail

echo "Checking production source for forbidden secrets and cleartext URLs..."

if grep -RInE   --include='*.kt'   --include='*.kts'   '(service[_-]?role|SUPABASE_SERVICE_ROLE|BEGIN (RSA|OPENSSH|EC) PRIVATE KEY)'   app/src/main app/build.gradle.kts; then
  echo "Forbidden production secret marker found."
  exit 1
fi

HTTP_MATCHES="$(
  grep -RInE --include='*.kt' --include='*.kts' '"http://' app/src/main app/build.gradle.kts \
    | grep -vE 'http://apache\.org/xml/|http://xml\.org/sax/' \
    || true
)"

if [ -n "$HTTP_MATCHES" ]; then
  echo "$HTTP_MATCHES"
  echo "Cleartext HTTP endpoint found in production Kotlin/Gradle source."
  exit 1
fi

echo "Static pre-release safety checks passed."
