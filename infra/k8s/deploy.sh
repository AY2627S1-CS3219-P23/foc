#!/usr/bin/env bash
# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Opus 5.5), 2026-11-05, issue #48 (N5 cloud deployment).
# Scope: the rollout step of the Deploy workflow (team decision: GHCR
# images, Leong Wei Zhi, 2026-11-05, options Q&A).
# Author review: Leong Wei Zhi, pending pull request review.
# 2026-11-08, Claude Code (Fable 5.1), issue #48 (DP7, PR #302 review):
# secrets.sh refreshes the Secrets before the apply and their version is
# stamped into the pod templates, so changed values roll out with the apply
# itself; the image tag is an argument, the ConfigMap hash rolls the pods
# too, and every rollout shares one deadline. Author review: Leong Wei Zhi,
# pending pull request review.
# 2026-11-09, Claude Code (Fable 5.1), issue #48 (EKS re-platform, DP8,
# DP15, DP16): runs in the Deploy job against EKS instead of on a host
# over SSM: writes the kubeconfig, installs Traefik's Helm chart, runs the
# db-init Job before the kustomization, and waits as before. Author
# review: Leong Wei Zhi, pending pull request review.
#
# Usage: deploy.sh <image-tag> <public-host> <cluster-name>
# <public-host> is the hostname the load balancer serves over https; the
# manifests prefix it with https:// themselves. Runs from a checkout of the
# target commit (the Deploy job's), with AWS credentials that may describe
# the cluster and read foc/* (the deploy role, which is cluster admin
# through an EKS access entry).
set -euo pipefail

image_tag="${1:?image tag}"
public_host="${2:?public host (hostname)}"
cluster_name="${3:?cluster name}"
repo="$(cd "$(dirname "$0")/../.." && pwd)"

# pinned with infra/helm/traefik-values.yaml; bump both together
traefik_chart_version="42.3.1"

export KUBECONFIG="${KUBECONFIG:-$(mktemp)}"
aws eks update-kubeconfig --name "$cluster_name" >/dev/null

# the ingress controller behind the load balancer's NodePort target group
helm upgrade --install traefik traefik \
    --repo https://traefik.github.io/charts --version "$traefik_chart_version" \
    --namespace traefik --create-namespace \
    -f "$repo/infra/helm/traefik-values.yaml" --wait --timeout 10m

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

# the seven roles and databases first, so the services find them: a Job is
# immutable, so the previous run's object goes before the new one
kubectl -n foc delete job db-init --ignore-not-found --wait
kubectl apply -f "$work/data/db-init.yaml"
if ! kubectl -n foc wait --for=condition=complete --timeout=600s job/db-init; then
    kubectl -n foc logs job/db-init --all-containers --tail=50 >&2 || true
    echo "db-init did not complete" >&2
    exit 1
fi

kubectl apply -k "$work"

# one deadline for every rollout together: the job dies at 60 min, and a
# timeout there would hide which rollout was the slow one
deadline=$((SECONDS + 2700))
for kind in statefulset deployment; do
    for name in $(kubectl -n foc get "$kind" -o name); do
        remaining=$((deadline - SECONDS))
        [ "$remaining" -gt 0 ] || { echo "rollouts exceeded 45 min" >&2; exit 1; }
        kubectl -n foc rollout status "$name" --timeout="${remaining}s"
    done
done
kubectl -n foc get pods -o wide
