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
# PR #316 review (Copilot): Helm, the Job and the rollouts share one
# 50-min deadline. Later rounds (Claude reviewer, Sinnez1): a failed
# db-init Job stops the deploy at once instead of waiting out the
# deadline, and the deadline check exits the script, not a subshell.
# 2026-11-10, Claude Code (Opus 5.5), issue #48 (team decision DP22, Leong
# Wei Zhi, options Q&A): removes the unsuffixed Deployments the split
# into <svc>-spot and <svc>-stable replaced. Author review: Leong Wei
# Zhi, pending pull request review.
# PR #329 review (Claude reviewer, Copilot), team decision (Leong Wei
# Zhi, options Q&A): the old Deployments are scaled to 1 right after the
# apply and each is deleted once its service's new halves are Ready,
# instead of all after every rollout. Author review: Leong Wei Zhi,
# pending pull request review.
# PR #329 review round 2 (Sinnez1), team decision (Leong Wei Zhi,
# options Q&A): the -spot halves roll first, then the -stable ones, then
# Traefik's chart; the pre-split Deployments go by label once -spot is
# Ready. Author review: Leong Wei Zhi, pending pull request review.
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

# one deadline for everything that waits (Helm, the db-init Job, every
# rollout): 50 min, inside the workflow job's 60 (PR #316 review). A
# single slow phase then fails here, with its name, instead of the job
# being killed from outside
# `left` is set by a plain call, never read through $(...): an exit inside
# a command substitution would end only the subshell
deadline=$((SECONDS + 3000))
left=0
remaining() {
    left=$((deadline - SECONDS))
    [ "$left" -gt 0 ] || { echo "deploy exceeded 50 min at: $1" >&2; exit 1; }
}

export KUBECONFIG="${KUBECONFIG:-$(mktemp)}"
aws eks update-kubeconfig --name "$cluster_name" >/dev/null

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
# polled for either outcome: `kubectl wait --for=condition=complete` would
# sit out the whole deadline on a Job that has already failed (a wrong
# master password, RDS unreachable), since Complete then never arrives
while :; do
    remaining db-init
    status="$(kubectl -n foc get job db-init -o \
        jsonpath='{range .status.conditions[?(@.status=="True")]}{.type}{" "}{end}')"
    case " $status " in
        *" Complete "*) break ;;
        *" Failed "*)
            kubectl -n foc logs job/db-init --all-containers --tail=50 >&2 || true
            echo "db-init failed" >&2
            exit 1
            ;;
    esac
    sleep 5
done

# the two halves of every service roll one after the other, never
# together (DP22, PR #329 review): each half has one replica at rest and
# rolls without surge, so rolling both at once would leave a service with
# no Ready pod. Everything but the -stable Deployments first (the
# ConfigMap, Services, HPAs, data stores and the -spot halves; `!=`
# also matches objects without the label), while -stable serves
kubectl apply -k "$work" -l 'foc.dev/pool!=stable'
# on the first deploy of the split, the Deployments from before it (no
# pool label) lost their HPAs to -spot in that apply: one replica each
# keeps every service answering and frees room on the Spot nodes for
# the -spot halves. Finds nothing on every later deploy (`scale` with
# nothing to scale is an error, hence the lookup)
pre_split="$(kubectl -n foc get deployment -l '!foc.dev/pool' -o name)"
if [ -n "$pre_split" ]; then
    # shellcheck disable=SC2086 # one name per word
    kubectl -n foc scale $pre_split --replicas=1
fi
wait_for() {
    local kind="$1"
    shift
    for name in $(kubectl -n foc get "$kind" -o name "$@"); do
        remaining "$name"
        kubectl -n foc rollout status "$name" --timeout="${left}s"
    done
}
wait_for statefulset
wait_for deployment -l foc.dev/pool=spot

# once, on the first deploy of the split: the Deployments from before it
# carry no pool label. Every service now has a Ready -spot replica, so
# deleting them leaves none without a replica, and frees the on-demand
# node for the -stable halves. Finds nothing on every later deploy
kubectl -n foc delete deployment -l '!foc.dev/pool'

# then the -stable halves, while -spot serves
kubectl apply -k "$work"
wait_for deployment -l foc.dev/pool=stable

# the ingress controller behind the load balancer's NodePort target group,
# last: one replica must land on the on-demand node (DP22), which has room
# only once the Deployments from before the split are gone. The running
# one keeps routing until then
remaining helm
helm upgrade --install traefik traefik \
    --repo https://traefik.github.io/charts --version "$traefik_chart_version" \
    --namespace traefik --create-namespace \
    -f "$repo/infra/helm/traefik-values.yaml" --wait --timeout "${left}s"

kubectl -n foc get pods -o wide
