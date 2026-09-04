#!/usr/bin/env bash
# Generate a new NixgramX upload/release keystore. Does NOT print the password
# after writing it to a local untracked credentials file.
set -euo pipefail
root="$(git rev-parse --show-toplevel)"
ks="$root/TMessagesProj/release.keystore"
cred="$root/nixgramx-release-signing.txt"
alias_name="${ALIAS_NAME:-nixgramx}"

if [[ -f "$ks" && "${FORCE:-}" != "1" ]]; then
  echo "refusing to overwrite $ks (set FORCE=1 to rotate)" >&2
  exit 1
fi

pass="$(openssl rand -base64 36 | tr -d '/+=' | head -c 32)"
rm -f "$ks"
keytool -genkeypair \
  -keystore "$ks" \
  -alias "$alias_name" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storepass "$pass" \
  -keypass "$pass" \
  -dname "CN=NixgramX, OU=Anndy999, O=NixgramX, C=US" \
  -deststoretype PKCS12

umask 077
cat > "$cred" <<CREDS
# NixgramX release signing — gitignored. Keep this private.
KEYSTORE_FILE=TMessagesProj/release.keystore
ALIAS_NAME=$alias_name
KEYSTORE_PASS=$pass
ALIAS_PASS=$pass
STORE_TYPE=PKCS12
CREDS

echo "wrote $ks"
echo "credentials: $cred  (gitignored)"
keytool -list -v -keystore "$ks" -alias "$alias_name" -storepass "$pass" | grep -E 'Alias name|SHA1:|SHA256:|Valid from'
echo "Record SHA-256 in docs/IDENTITY.md and keep $cred off git / chat logs."
