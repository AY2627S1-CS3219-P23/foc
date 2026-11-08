#!/usr/bin/env bash
# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Opus 5.5), 2026-11-05, issue #48 (N5 cloud deployment).
# Scope: the rollout step the deploy workflow runs on the k3s host through
# SSM Run Command (team decision: GHCR images, deploy via SSM, k3s API
# private — Leong Wei Zhi, 2026-11-05, options Q&A).
# Author review: Leong Wei Zhi, pending pull request review.
# 2026-11-08, Claude Code (Fable 5.1), issue #48 (DP7): the External
# Secrets Operator waits are gone; secrets.sh refreshes the Secrets before
# the apply, and their version is stamped into the pod templates so
# changed values roll out with the apply itself (PR #302 review: one
# rollout, not two). Author review: Leong Wei Zhi, pending pull request
# review.
#
# Usage (as root on the host): deploy.sh <commit-sha> <public-host>
# Checks out <commit-sha> in /opt/foc, refreshes the Secrets from Secrets
# Manager (secrets.sh), fills the image tag, public host and Secret
# version into a copy of infra/k8s, applies it, and waits for every
# rollout. The deploy workflow
# checks out <commit-sha> before calling this script, so the script that
# runs is the one at that commit.
set -euo pipefail

sha="${1:?commit sha}"
public_host="${2:?public host (Elastic IP)}"
repo=/opt/foc
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml

git -C "$repo" fetch --quiet origin
git -C "$repo" checkout --quiet --detach "$sha"

bash "$repo/infra/k8s/secrets.sh"
# stamped into the pod templates (foc/secrets-version annotation): changed
# values roll the pods in the same rollout as a new image tag, and a
# redeploy of the same sha after a secret change still rolls them
secrets_version="$(kubectl -n foc get secret foc-secrets \
    -o jsonpath='{.metadata.resourceVersion}')"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cp -R "$repo/infra/k8s/." "$work/"
grep -rl -e __IMAGE_TAG__ -e __PUBLIC_HOST__ -e __SECRETS_VERSION__ "$work" \
    | xargs sed -i -e "s|__IMAGE_TAG__|$sha|g" -e "s|__PUBLIC_HOST__|$public_host|g" \
        -e "s|__SECRETS_VERSION__|$secrets_version|g"

kubectl apply -k "$work"

for kind in statefulset deployment; do
    for name in $(kubectl -n foc get "$kind" -o name); do
        kubectl -n foc rollout status "$name" --timeout=15m
    done
done
kubectl -n foc get pods -o wide
