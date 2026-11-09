#!/usr/bin/env bash
# AI-assisted (CS3219 AI Usage Policy disclosure):
# Tool: Claude Code (Opus 5.5), 2026-11-09, issue #48 (N5.3.3, continuous
# deployment).
# Scope: the Deploy workflow's plan step: which images to build and which
# to retag (team decisions DP20, Leong Wei Zhi, 2026-11-09, options Q&A:
# rebuild changed images only, the live tag read from the cluster, skip
# docs-only changes).
# Author review: Leong Wei Zhi, pending pull request review.
# PR #317 review (Claude reviewer, Copilot): an automatic run whose commit
# is older than the live one deploys nothing (CI runs on main can finish
# out of order); the root .dockerignore rebuilds the root-context images.
#
# Usage: changed-images.sh <target-commit> <rebuild-all: true|false> <newer-only: true|false>
# Needs a kubeconfig whose current context is the cluster and a checkout
# with the full history (fetch-depth 0). Prints, in $GITHUB_OUTPUT form:
#   build=<JSON array>     images to build at <target-commit>
#   retag=<JSON array>     {image, from}: unchanged images, retagged from
#                          the tag running now
#   relevant=true|false    whether anything outside the docs changed since
#                          the commits running now
# The live tag is <commit>-<run id> (deploy.yml), so its commit is where
# the diff starts. An image with no Deployment yet, or whose commit is not
# in the history (the k3s host's), is built. The diff is symmetric, so a
# re-run of an older Deploy run (rollback) rebuilds what differs too.
# With <newer-only> true (the first attempt of an automatic run), a target
# that is an ancestor of a live commit deploys nothing: a later merge's
# CI finished first and is already live.
set -euo pipefail

target="${1:?target commit}"
rebuild_all="${2:?rebuild all (true|false)}"
newer_only="${3:?newer only (true|false)}"

images=(user-service supplier-service order-service credit-service
    notification-service rating-service chat-service gateway web)

# what each image is built from (the Dockerfiles' COPY lines), one
# pathspec per line; the workflow itself rebuilds everything, as in ci.yml
sources() {
    echo ":(glob)$1/**"
    # web and gateway build from their own folder; the rest from the repo
    # root, filtered by its .dockerignore
    case "$1" in
        gateway | web) ;;
        *) echo ".dockerignore" ;;
    esac
    case "$1" in
        user-service | order-service | credit-service | notification-service | rating-service)
            echo ":(glob)foc-contracts/**" ;;
    esac
    echo ".github/workflows/deploy.yml"
}
# docs-only changes neither build nor deploy
not_docs=(":(exclude,glob)**/*.md" ":(exclude,glob)**/docs/**" ":(exclude,glob)ai/**")

# fails loudly when the cluster is unreachable; an empty namespace (the
# first deploy) lists nothing
live="$(kubectl -n foc get deployment -o \
    jsonpath='{range .items[*]}{.metadata.name}{" "}{.spec.template.spec.containers[0].image}{"\n"}{end}')"

if [ "$newer_only" = true ]; then
    target_full="$(git rev-parse "$target^{commit}")"
    while read -r ref; do
        tag="${ref##*:}"
        commit="$(git rev-parse --quiet --verify "${tag%%-*}^{commit}")" || continue
        if [ "$commit" != "$target_full" ] &&
            git merge-base --is-ancestor "$target_full" "$commit"; then
            echo "$target is older than the live $commit: nothing to deploy" >&2
            printf '%s\n' "build=[]" "retag=[]" "relevant=false"
            exit 0
        fi
    done < <(awk '$2 ~ /:/ { print $2 }' <<<"$live" | sort -u)
fi

build=()
retag="[]"
relevant=false
for image in "${images[@]}"; do
    ref="$(awk -v n="$image" '$1 == n { print $2 }' <<<"$live")"
    tag=""
    [[ "$ref" == *:* ]] && tag="${ref##*:}"
    commit="${tag%%-*}"
    if [ -z "$tag" ] || ! git cat-file -e "$commit^{commit}" 2>/dev/null; then
        echo "$image: no live commit in the history (${tag:-none}), build" >&2
        build+=("$image")
        relevant=true
        continue
    fi
    if ! git diff --quiet "$commit" "$target" -- . "${not_docs[@]}"; then
        relevant=true
    fi
    src=()
    while read -r p; do src+=("$p"); done < <(sources "$image")
    if [ "$rebuild_all" = true ] ||
        ! git diff --quiet "$commit" "$target" -- "${src[@]}" "${not_docs[@]}"; then
        echo "$image: changed since $commit, build" >&2
        build+=("$image")
    else
        echo "$image: unchanged since $commit, retag $tag" >&2
        retag="$(jq -c --arg i "$image" --arg t "$tag" '. + [{image: $i, from: $t}]' <<<"$retag")"
    fi
done

echo "build=$(printf '%s\n' ${build[@]+"${build[@]}"} | jq -Rnc '[inputs | select(length > 0)]')"
echo "retag=$retag"
echo "relevant=$relevant"
