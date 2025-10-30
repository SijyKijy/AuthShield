#!/usr/bin/env bash
set -euo pipefail

release_type="${1:-patch}"
prop_file="gradle.properties"

if [[ ! -f "$prop_file" ]]; then
  echo "gradle.properties not found" >&2
  exit 1
fi

current_version=$(grep '^mod_version=' "$prop_file" | cut -d'=' -f2-)
if [[ -z "$current_version" ]]; then
  echo "mod_version not found in $prop_file" >&2
  exit 1
fi

IFS='.' read -r major minor patch <<<"$current_version"
major=${major:-0}
minor=${minor:-0}
patch=${patch:-0}

case "$release_type" in
  major)
    ((major++))
    minor=0
    patch=0
    ;;
  minor)
    ((minor++))
    patch=0
    ;;
  patch)
    ((patch++))
    ;;
  *)
    echo "Unknown release type: $release_type" >&2
    exit 1
    ;;
 esac

new_version="${major}.${minor}.${patch}"

# Update gradle.properties
awk -v new_version="$new_version" -F '=' 'BEGIN { OFS = "=" } \
  /^mod_version=/ { print $1, new_version; next } \
  { print }' "$prop_file" > "$prop_file.tmp"

mv "$prop_file.tmp" "$prop_file"

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  echo "version=$new_version" >> "$GITHUB_OUTPUT"
fi

echo "$new_version"
