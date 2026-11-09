#!/usr/bin/env bash
# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Fable 5.1), 2026-11-08, issue #48 (N5 cloud deployment).
# Scope: copies the two Secrets Manager secrets into the Kubernetes Secrets
# the manifests reference (team decision DP7, Leong Wei Zhi, options Q&A).
# Author review: Leong Wei Zhi, pending pull request review.
# PR #302 review: foc-secrets is built as a Secret document, so a value
# may hold newlines; a non-string value stops the script. Second round
# (Sinnez1): reads retried for a minute, foc/ghcr validated too.
# 2026-11-09, Claude Code (Fable 5.1), issue #48 (EKS re-platform, DP7
# re-decided): the script runs in the Deploy job with the GitHub deploy
# role, against the kubeconfig deploy.sh wrote; the k3s kubeconfig and the
# IMDS region lookup are gone. Author review: Leong Wei Zhi, pending pull
# request review.
# PR #316 review (Copilot): the RDS master credentials are written to
# their own Secret, foc-db-admin, so a read of foc-secrets is not
# database-admin access.
#
# Usage: secrets.sh
# Needs AWS credentials that may read foc/* (the deploy role) and a
# kubeconfig whose current context is the cluster (deploy.sh runs
# `aws eks update-kubeconfig` first). Reads foc/app and foc/ghcr and writes
# them as the Secrets foc-secrets, foc-db-admin and ghcr-pull in namespace
# foc.
# Idempotent: unchanged values leave the Secrets untouched. Run by
# deploy.sh on every deploy.
set -euo pipefail
umask 077

here="$(cd "$(dirname "$0")" && pwd)"

: "${AWS_REGION:=${AWS_DEFAULT_REGION:-}}"
[ -n "$AWS_REGION" ] || { echo "AWS_REGION is not set" >&2; exit 1; }
export AWS_REGION
kubectl config current-context >/dev/null

# a fresh role session can be refused for a few seconds (IAM is eventually
# consistent), so a failed read is retried for about a minute
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

# foc/app: flat JSON of KEY: value, written as two Secret documents. The
# RDS_MASTER_* keys go to foc-db-admin, which only the db-init Job mounts
# (DP16); everything else to foc-secrets, which the services read key by
# key. A value may hold newlines or '='; a non-string value is a mistake
# and stops here
secret foc/app | jq '
    if type != "object" then error("foc/app is not a JSON object") else . end
    | to_entries
    | map(if (.value | type) != "string"
          then error("foc/app." + .key + " is not a string")
          else {key, value: (.value | @base64)} end)
    | (map(select(.key | startswith("RDS_MASTER_")))) as $admin
    | (map(select(.key | startswith("RDS_MASTER_") | not))) as $app
    | if ($admin | length) == 0 then error("foc/app has no RDS_MASTER_* keys") else . end
    | {apiVersion: "v1", kind: "List", items:
        [{name: "foc-secrets", entries: $app}, {name: "foc-db-admin", entries: $admin}]
        | map({apiVersion: "v1", kind: "Secret",
               metadata: {name: .name, namespace: "foc"},
               type: "Opaque", data: (.entries | from_entries)})}' \
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
