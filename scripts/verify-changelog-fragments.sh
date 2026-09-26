#!/usr/bin/env bash
set -euo pipefail

# Every commit that changes runtime source must add one release fragment.  The
# fragments retain the full engineering history until release preparation,
# where obsolete/reverted entries are removed and the remaining player-facing
# points are curated into HISTORY.md and release-notes/<version>.md.
readonly fragment_pattern='^changelog/unreleased/.+\.md$'
readonly source_pattern='^(src/|pom\.xml$|libs/PluginAPI\.jar$)'
readonly base_ref=${CHANGELOG_BASE_REF:?CHANGELOG_BASE_REF must name the pull-request base commit}

git rev-list --reverse "${base_ref}..HEAD" | while read -r commit; do
    changed_files="$(git diff-tree --no-commit-id --name-only -r "$commit")"
    if ! grep -Eq "$source_pattern" <<<"$changed_files"; then
        continue
    fi
    fragment_count="$(grep -Ec "$fragment_pattern" <<<"$changed_files" || true)"
    if [[ "$fragment_count" -ne 1 ]]; then
        printf 'Commit %s changes runtime source without a changelog fragment under changelog/unreleased/.\n' "$commit" >&2
        exit 1
    fi
done
