#!/usr/bin/env bash

set -euo pipefail

readonly pattern='^(feat|fix|test|refactor|docs|chore|build|ci|perf|revert)(\([a-z0-9][a-z0-9._/-]*\))?!?: [a-z0-9]'

check_subject() {
    local subject="$1"
    local summary

    if [[ ! "$subject" =~ $pattern ]]; then
        return 1
    fi
    if (( ${#subject} > 72 )); then
        return 1
    fi

    summary="${subject#*: }"
    if [[ "$summary" =~ ^(.+)\ \(#[0-9]+\)$ ]]; then
        summary="${BASH_REMATCH[1]}"
    fi
    [[ "$summary" != *. ]]
}

# The subject a squash merge writes is the pull request title with ` (#123)`
# appended by GitHub, so a title that fits inside the limit lands over it. This
# is the budget a title actually has: the limit, less the longest reference the
# repository is going to grow into.
readonly reference_room=8

check_pull_request_title() {
    local title="$1"
    if (( ${#title} + reference_room > 72 )); then
        return 1
    fi
    check_subject "$title"
}

if [[ "${1:-}" == "--pr-title" ]]; then
    if (( $# != 2 )); then
        echo "usage: $0 --pr-title <title>" >&2
        exit 2
    fi
    if ! check_pull_request_title "$2"; then
        echo "invalid pull request title: $2" >&2
        echo "a squash merge appends the number, so a title has $((72 - reference_room))" \
            "characters" >&2
        exit 1
    fi
    exit 0
fi

if [[ "${1:-}" == "--message" ]]; then
    if (( $# != 2 )); then
        echo "usage: $0 --message <subject>" >&2
        exit 2
    fi
    if ! check_subject "$2"; then
        echo "invalid commit subject: $2" >&2
        exit 1
    fi
    exit 0
fi

# Commits up to 2732a75 predate the convention.
readonly baseline="${1:-2732a75}"
readonly head="${2:-HEAD}"

if [[ -n "$baseline" ]] && ! git merge-base --is-ancestor "$baseline" "$head"; then
    echo "commit-message baseline $baseline is not an ancestor of $head" >&2
    exit 2
fi

status=0
while IFS=$'\t' read -r commit subject; do
    if ! check_subject "$subject"; then
        echo "invalid commit subject in ${commit:0:12}: $subject" >&2
        status=1
    fi
# Merge commits carry GitHub's generated subject ("Merge pull request #N
# from ..."), which no Conventional Commits pattern accepts. The convention
# governs the commits an author writes, so merges are out of scope rather
# than exempt one hash at a time.
done < <(git log --no-merges --format='%H%x09%s' "${baseline:+$baseline..}$head")

if (( status != 0 )); then
    echo "expected: <type>[optional scope][!]: <lowercase imperative summary>" >&2
fi
exit "$status"
