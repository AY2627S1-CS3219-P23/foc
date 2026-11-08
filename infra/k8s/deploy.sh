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
# review. Second round (Sinnez1): the caller checks out the commit, the
# image tag is an argument, the ConfigMap hash rolls the pods too, and
# every rollout shares one deadline.
# 2026-11-08, Claude Code (Fable 5.1), issue #48 (HTTPS): the public host
# is the site's hostname behind the load balancer, no longer the Elastic
# IP (DP18, DP20); no logic change. Author review: Leong Wei Zhi, pending
# pull request review.
#
# Usage (as root on the host): deploy.sh <image-tag> <public-host>
# <public-host> is the hostname the load balancer serves over https; the
# manifests prefix it with https:// themselves.
# The caller (the Deploy workflow's SSM command) has already checked out
# the commit in /opt/foc. Refreshes the Secrets from Secrets Manager
# (secrets.sh), fills the image tag, public host, Secret version and
# ConfigMap hash into a copy of infra/k8s, applies it, and waits for
# every rollout under one deadline.
set -euo pipefail

image_tag="${1:?image tag}"
public_host="${2:?public host (hostname)}"
repo=/opt/foc
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml

bash "$repo/infra/k8s/secrets.sh"
# stamped into the pod templates (foc/secrets-version annotation): changed
# values roll the pods in the same rollout as a new image tag
secrets_version="$(kubectl -n foc get secret foc-secrets \
    -o jsonpath='{.metadata.resourceVersion}')"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cp -R "$repo/infra/k8s/." "$work/"
fill() {
    grep -rl -e "$1" "$work" | xargs -r sed -i -e "s|$1|$2|g"
}
fill __IMAGE_TAG__ "$image_tag"
fill __PUBLIC_HOST__ "$public_host"
fill __SECRETS_VERSION__ "$secrets_version"
# the ConfigMap, hashed after the public host is in it (foc/config-hash
# annotation): a config change rolls the pods too
fill __CONFIG_HASH__ "$(sha256sum "$work/config.yaml" | cut -c1-16)"

kubectl apply -k "$work"

# one deadline for every rollout together: the SSM command dies at 60 min,
# and a timeout there would hide which rollout was the slow one
deadline=$((SECONDS + 2700))
for kind in statefulset deployment; do
    for name in $(kubectl -n foc get "$kind" -o name); do
        remaining=$((deadline - SECONDS))
        [ "$remaining" -gt 0 ] || { echo "rollouts exceeded 45 min" >&2; exit 1; }
        kubectl -n foc rollout status "$name" --timeout="${remaining}s"
    done
done
kubectl -n foc get pods -o wide
