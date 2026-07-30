#!/usr/bin/env bash
# One-time GitHub repo setup for service-api-marketplace.
# Safe to re-run — all operations are idempotent.
set -euo pipefail

REPO="hmcts/service-api-marketplace"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "=== GitHub setup: $REPO ==="
echo ""

# ── Prerequisites ────────────────────────────────────────────────────────────
if ! command -v gh &>/dev/null; then
  echo "ERROR: gh CLI not found. Install with: brew install gh"
  exit 1
fi
if ! gh auth status &>/dev/null 2>&1; then
  echo "ERROR: Not authenticated to GitHub. Run: gh auth login"
  exit 1
fi

# ── 1.1 Visibility ───────────────────────────────────────────────────────────
CURRENT_VISIBILITY=$(gh repo view "$REPO" --json visibility -q .visibility)
if [ "$CURRENT_VISIBILITY" = "PUBLIC" ]; then
  echo "1.1 Visibility ✓  (already PUBLIC)"
else
  echo "1.1 Visibility    currently: $CURRENT_VISIBILITY"
  read -rp "    Make repo PUBLIC? This cannot be undone without GitHub admin approval. [y/N] " confirm
  if [[ "${confirm:-}" =~ ^[Yy]$ ]]; then
    gh repo edit "$REPO" --visibility public --accept-visibility-change-consequences
    echo "    → Set to PUBLIC ✓"
  else
    echo "    → Skipped (set manually in GitHub Settings → General when ready)"
  fi
fi

echo ""

# ── 1.2 Topic ────────────────────────────────────────────────────────────────
echo "1.2 Topic"
CURRENT_TOPICS=$(gh api "repos/$REPO/topics" --jq '.names | join(",")' 2>/dev/null || echo "")
if echo "$CURRENT_TOPICS" | grep -q "jenkins-cft-j-z"; then
  echo "    jenkins-cft-j-z ✓  (already set)"
else
  # PUT replaces all topics — preserve any existing ones
  EXISTING=$(gh api "repos/$REPO/topics" --jq '.names[]' 2>/dev/null | tr '\n' ',' | sed 's/,$//' || echo "")
  TOPIC_ARGS="-f names[]=jenkins-cft-j-z"
  if [ -n "$EXISTING" ]; then
    while IFS=',' read -ra TOPICS; do
      for t in "${TOPICS[@]}"; do
        TOPIC_ARGS="$TOPIC_ARGS -f names[]=$t"
      done
    done <<< "$EXISTING"
  fi
  # shellcheck disable=SC2086
  gh api "repos/$REPO/topics" -X PUT -H "Accept: application/vnd.github+json" $TOPIC_ARGS --silent
  echo "    → jenkins-cft-j-z added ✓"
fi

echo ""

# ── 1.3 Branch protection ────────────────────────────────────────────────────
echo "1.3 Branch protection on master"
gh api "repos/$REPO/branches/master/protection" \
  -X PUT \
  -H "Accept: application/vnd.github+json" \
  --silent \
  --input - <<'JSON'
{
  "required_status_checks": null,
  "enforce_admins": false,
  "required_pull_request_reviews": {
    "required_approving_review_count": 1,
    "dismiss_stale_reviews": false,
    "require_code_owner_reviews": false
  },
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false
}
JSON
echo "    → Require PR + 1 approval, no force push ✓"
echo "    NOTE: After first Jenkins run, add 'continuous-integration/jenkins/pr-merge'"
echo "          as a required status check in GitHub Settings → Branches → master"

echo ""

# ── 1.4 catalog-info.yaml ────────────────────────────────────────────────────
echo "1.4 catalog-info.yaml"
cat > "$REPO_ROOT/catalog-info.yaml" <<'YAML'
apiVersion: backstage.io/v1alpha1
kind: Component
metadata:
  name: apim-marketplace
  description: "API Marketplace onboarding service — manages external developer onboarding to HMCTS APIs"
  annotations:
    jenkins.io/job-full-name: "cft:HMCTS_j_to_z/service-api-marketplace"
    github.com/project-slug: 'hmcts/service-api-marketplace'
  tags:
    - java
  links:
    - url: https://hmcts-reform.slack.com/app_redirect?channel=api-marketplace-tech
      title: "#api-marketplace-tech on Slack"
      icon: chat
spec:
  type: service
  system: apim
  lifecycle: experimental
  owner: group:dts-api-marketplace
YAML
echo "    → Written ✓"

echo ""
echo "=== Done ==="
echo ""
echo "Next steps:"
echo "  git add catalog-info.yaml && git commit (include with Phase 2 commit)"
echo "  After first Jenkins run: add CI check to branch protection (step 1.3)"
