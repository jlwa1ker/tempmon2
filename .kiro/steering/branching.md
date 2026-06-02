# Branching Strategy

We use a simplified GitFlow model with an integration branch for release candidates and short-lived feature/fix branches.

## Long-lived Branches

- **`main`** — Production-ready code. Only receives merges from `develop` (releases) or `fix/` branches (hotfix patches).
- **`develop`** — Integration branch for release candidates. All feature and fix branches merge here via pull request.

## Short-lived Branches

- **`feature/`** — New functionality. Branch off `develop`, merge back to `develop` via PR.
- **`fix/`** — Bug fixes. Branch off `develop`, merge back to `develop` via PR.
- **`fix/` (hotfix)** — Urgent patches that bypass integration. Branch off `main`, merge to `main` via PR, then merge down to `develop` before or at the next release.

## Release Process

1. When `develop` is ready for release, merge `develop` into `main` via pull request
2. Tag the merge commit on `main` with the release number (e.g., `1.2.3`)
3. Deploy from the tagged commit

## Branch Protection

The `main` branch is protected with the following rules:

- **Pull request required** — No direct pushes to `main`; all changes must go through a PR
- **Approval required** — At least 1 approving review before merge
- **Stale reviews dismissed** — Pushing new commits invalidates previous approvals
- **Status checks required** — CI jobs must pass before merge (project-specific check names)
- **Admin bypass** — The repository owner can override status check requirements when necessary

## Release Numbering

Releases use a dotted triple: `major.minor.build`

- **Major** — Breaking changes or significant milestones
- **Minor** — New features, backward-compatible
- **Build** — Incremental, corresponds to the GitHub Actions `run_number` from the Build & Test workflow

## Merge Direction Summary

```
feature/xyz ──► develop ──► main (release)
fix/abc     ──► develop ──► main (release)
fix/hotfix  ──► main (patch) ──► develop (backport)
```
