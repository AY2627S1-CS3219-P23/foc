#!/usr/bin/env bash
# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Opus 5.5), 2026-11-05, issue #48 (N5 cloud deployment).
# Scope: the rollout step the deploy workflow runs on the k3s host through
# SSM Run Command (team decision: GHCR images, deploy via SSM, k3s API
# private — Leong Wei Zhi, 2026-11-05, options Q&A).
# Author review: Leong Wei Zhi, pending pull request review.
# 2026-11-08, Claude Code (Fable 5.1), issue #48 (DP7): the External
# Secrets Operator waits are gone; secrets.sh refreshes the Secrets before
# the apply, and Deployments restart when the values changed. Author
# review: Leong Wei Zhi, pending pull request review.
#
# Usage (as root on the host): deploy.sh <commit-sha> <public-host>
# Checks out <commit-sha> in /opt/foc, refreshes the Secrets from Secrets
# Manager (secrets.sh), fills the image tag and public host into a copy of
# infra/k8s, applies it, and waits for every rollout. The deploy workflow
# checks out <commit-sha> before calling this script, so the script that
# runs is the one at that commit.
set -euo pipefail

sha="${1:?commit sha}"
public_host="${2:?public host (Elastic IP)}"
repo=/opt/foc
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml

git -C "$repo" fetch --quiet origin
git -C "$repo" checkout --quiet --detach "$sha"

secret_version() {
    kubectl -n foc get secret foc-secrets \
        -o jsonpath='{.metadata.resourceVersion}' 2>/dev/null || true
}
before="$(secret_version)"
bash "$repo/infra/k8s/secrets.sh"
after="$(secret_version)"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cp -R "$repo/infra/k8s/." "$work/"
grep -rl -e __IMAGE_TAG__ -e __PUBLIC_HOST__ "$work" \
    | xargs sed -i -e "s|__IMAGE_TAG__|$sha|g" -e "s|__PUBLIC_HOST__|$public_host|g"

kubectl apply -k "$work"

# env from a Secret is read at pod start, so changed values need new pods
# (the StatefulSets keep running: DB and broker passwords only apply to an
# empty volume anyway, see infra/README.md)
if [ -n "$before" ] && [ "$before" != "$after" ]; then
    kubectl -n foc rollout restart deployment
fi

for kind in statefulset deployment; do
    for name in $(kubectl -n foc get "$kind" -o name); do
        kubectl -n foc rollout status "$name" --timeout=15m
    done
done
kubectl -n foc get pods -o wide
