#!/usr/bin/env bash
# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Fable 5.1), 2026-11-08, issue #48 (N5 cloud deployment).
# Scope: copies the two Secrets Manager secrets into the k3s Secrets the
# manifests reference, from the host (team decision DP7, Leong Wei Zhi,
# 2026-11-08, options Q&A: IMDS hop limit 1 and no External Secrets
# Operator, so only host processes can use the instance role).
# Author review: Leong Wei Zhi, pending pull request review.
# PR #302 review: foc-secrets is built as a Secret document, so a value
# may hold newlines; a non-string value stops the script. Second round
# (Sinnez1): reads retried for a minute, foc/ghcr validated too.
#
# Usage (as root on the host): secrets.sh
# Reads foc/app and foc/ghcr with the instance role and writes them as the
# Secrets foc-secrets and ghcr-pull in namespace foc. Idempotent: unchanged
# values leave the Secrets untouched. Run by cloud-init once and by
# deploy.sh on every deploy.
set -euo pipefail
umask 077

export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
here="$(cd "$(dirname "$0")" && pwd)"

# the AWS CLI needs a region; the host knows its own
if [ -z "${AWS_DEFAULT_REGION:-}" ]; then
    token="$(curl -sf -X PUT -H 'X-aws-ec2-metadata-token-ttl-seconds: 60' \
        http://169.254.169.254/latest/api/token)"
    AWS_DEFAULT_REGION="$(curl -sf -H "X-aws-ec2-metadata-token: $token" \
        http://169.254.169.254/latest/meta-data/placement/region)"
    export AWS_DEFAULT_REGION
fi

# IAM is eventually consistent and cloud-init runs this right after the
# first apply, so a failed read is retried for about a minute
secret() {
    local out
    for _ in $(seq 1 10); do
        if out="$(aws secretsmanager get-secret-value --secret-id "$1" \
                --query SecretString --output text 2>&1)"; then
            printf '%s\n' "$out"
            return 0
        fi
        sleep 6
    done
    printf '%s\n' "$out" >&2
    return 1
}

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

kubectl apply -f "$here/namespace.yaml"

# foc/app: flat JSON of KEY: value, written as a Secret document. A value
# may hold newlines or '='; a non-string value is a mistake and stops here
secret foc/app | jq '
    if type != "object" then error("foc/app is not a JSON object") else . end
    | to_entries
    | map(if (.value | type) != "string"
          then error("foc/app." + .key + " is not a string")
          else {key, value: (.value | @base64)} end)
    | {apiVersion: "v1", kind: "Secret",
       metadata: {name: "foc-secrets", namespace: "foc"},
       type: "Opaque", data: from_entries}' \
    | kubectl apply -f -

# foc/ghcr: {username, token} -> dockerconfigjson (the token never hits
# argv); a missing or non-string field stops here instead of surfacing
# later as ImagePullBackOff
secret foc/ghcr | jq '
    if (.username | type) != "string" or (.token | type) != "string"
    then error("foc/ghcr needs string username and token")
    else {auths: {"ghcr.io": {auth: ("\(.username):\(.token)" | @base64)}}} end' \
    > "$work/config.json"
kubectl -n foc create secret generic ghcr-pull \
    --type=kubernetes.io/dockerconfigjson \
    --from-file=.dockerconfigjson="$work/config.json" --dry-run=client -o yaml \
    | kubectl apply -f -
