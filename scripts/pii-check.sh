#!/usr/bin/env bash
# Fails the build if source files contain things that look like real personal data.
# Synthetic data must use @example.com emails and all-zero phone numbers.
# Lines ending with a "pii-allow" comment are skipped (e.g. public test card numbers).
set -euo pipefail

TARGETS="src"
FOUND=0

check() {
  local label="$1" pattern="$2"
  local hits
  hits=$(grep -rnE "$pattern" $TARGETS --include='*.java' --include='*.properties' --include='*.xml' --include='*.json' \
         | grep -v 'pii-allow' || true)
  if [[ -n "$hits" ]]; then
    echo "::error::Possible PII ($label):"
    echo "$hits"
    FOUND=1
  fi
}

# Emails that are not on reserved test domains
EMAILS=$(grep -rnoE '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}' $TARGETS --include='*.java' --include='*.properties' \
         | grep -vE '@example\.(com|org)' | grep -v 'pii-allow' || true)
if [[ -n "$EMAILS" ]]; then
  echo "::error::Possible real email addresses:"; echo "$EMAILS"; FOUND=1
fi

# Indian mobile numbers (10 digits starting 6-9)
check "mobile number" '(^|[^0-9])[6-9][0-9]{9}([^0-9]|$)'

# 13-19 digit card-like numbers
check "card number" '(^|[^0-9])[0-9]{13,19}([^0-9]|$)'

if [[ $FOUND -eq 1 ]]; then
  echo "PII check failed. Use synthetic data or mark a known-safe line with // pii-allow"
  exit 1
fi
echo "PII check passed."
