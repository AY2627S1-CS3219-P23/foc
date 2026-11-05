#!/usr/bin/env bash
# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Opus 5.5), 2026-11-05, issue #48 (N5 cloud deployment).
# Scope: the rollout step the deploy workflow runs on the k3s host through
# SSM Run Command (team decision: GHCR images, deploy via SSM, k3s API
# private — Leong Wei Zhi, 2026-11-05, options Q&A).
# Author review: Leong Wei Zhi, pending pull request review.
#
# Usage (as root on the host): deploy.sh <commit-sha> <public-host>
# Checks out <commit-sha> in /opt/foc, fills the image tag and public host
# into a copy of infra/k8s, applies it, and waits for every rollout.
set -euo pipefail

sha="${1:?commit sha}"
public_host="${2:?public host (Elastic IP)}"
repo=/opt/foc
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml

git -C "$repo" fetch --quiet origin
git -C "$repo" checkout --quiet --detach "$sha"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cp -R "$repo/infra/k8s/." "$work/"
grep -rl -e __IMAGE_TAG__ -e __PUBLIC_HOST__ "$work" \
    | xargs sed -i -e "s|__IMAGE_TAG__|$sha|g" -e "s|__PUBLIC_HOST__|$public_host|g"

# External Secrets Operator is installed by k3s at first boot; its CRDs
# must exist before the ExternalSecrets can be applied
for crd in clustersecretstores.external-secrets.io externalsecrets.external-secrets.io; do
    kubectl wait --for=condition=Established "crd/$crd" --timeout=10m
done
kubectl -n external-secrets rollout status deploy/external-secrets-webhook --timeout=10m

kubectl apply -k "$work"

for kind in statefulset deployment; do
    for name in $(kubectl -n foc get "$kind" -o name); do
        kubectl -n foc rollout status "$name" --timeout=15m
    done
done
kubectl -n foc get pods -o wide
