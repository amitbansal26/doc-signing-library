#!/bin/sh
set -e

VAULT_ADDR="${VAULT_ADDR:-http://openbao:8200}"
VAULT_TOKEN="${VAULT_TOKEN:-root}"

echo "Waiting for OpenBao to be ready at ${VAULT_ADDR}..."
until curl -sf "${VAULT_ADDR}/v1/sys/health" > /dev/null 2>&1; do
  echo "OpenBao not ready yet, retrying in 2s..."
  sleep 2
done
echo "OpenBao is ready."

echo "Enabling Transit secrets engine..."
curl -sf \
  --request POST \
  --header "X-Vault-Token: ${VAULT_TOKEN}" \
  --header "Content-Type: application/json" \
  --data '{"type":"transit"}' \
  "${VAULT_ADDR}/v1/sys/mounts/transit" || echo "Transit already enabled"

echo "Enabling PKI secrets engine..."
curl -sf \
  --request POST \
  --header "X-Vault-Token: ${VAULT_TOKEN}" \
  --header "Content-Type: application/json" \
  --data '{"type":"pki","config":{"max_lease_ttl":"87600h"}}' \
  "${VAULT_ADDR}/v1/sys/mounts/pki" || echo "PKI already enabled"

echo "Generating root CA..."
curl -sf \
  --request POST \
  --header "X-Vault-Token: ${VAULT_TOKEN}" \
  --header "Content-Type: application/json" \
  --data '{
    "common_name": "Doc Signing Root CA",
    "ttl": "87600h",
    "key_type": "rsa",
    "key_bits": 4096
  }' \
  "${VAULT_ADDR}/v1/pki/root/generate/internal" > /dev/null

echo "Setting PKI URLs..."
curl -sf \
  --request POST \
  --header "X-Vault-Token: ${VAULT_TOKEN}" \
  --header "Content-Type: application/json" \
  --data "{
    \"issuing_certificates\": \"${VAULT_ADDR}/v1/pki/ca\",
    \"crl_distribution_points\": \"${VAULT_ADDR}/v1/pki/crl\"
  }" \
  "${VAULT_ADDR}/v1/pki/config/urls" > /dev/null

echo "Creating PKI role 'doc-signer'..."
curl -sf \
  --request POST \
  --header "X-Vault-Token: ${VAULT_TOKEN}" \
  --header "Content-Type: application/json" \
  --data '{
    "key_type": "any",
    "allow_any_name": true,
    "max_ttl": "24h",
    "key_usage": ["DigitalSignature"],
    "no_store": false,
    "require_cn": false,
    "use_csr_common_name": true,
    "use_csr_sans": true
  }' \
  "${VAULT_ADDR}/v1/pki/roles/doc-signer" > /dev/null

echo "OpenBao initialization complete."
