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
- **Status checks required** — CI jobs must pass before merge (project-specific check names)
- **Admin bypass** — Reserved for the repository owner only. Kiro must never use `--admin` to bypass branch protection rules or force-merge PRs.

## Merging to Main

When merging `develop` to `main` for a release:

1. Create a PR from `develop` to `main`
2. **Do NOT use admin bypass to merge** — the PR must be reviewed and approved by the repository owner
3. Wait for CI status checks to pass
4. After approval, the owner merges the PR
5. Tag the merge commit on `main` with the release version

## Release Numbering

Releases use a dotted triple: `major.minor.build`

- **Major** — Breaking changes or significant milestones
- **Minor** — New features, backward-compatible. A "point release" bumps this number.
- **Build** — The GitHub Actions `run_number` from the Build & Test CI workflow at the time of release. This is NOT manually assigned — it comes from the CI run that built the release artifact.

Example: if the CI workflow `run_number` is 17 when releasing a new minor version, the tag is `0.2.17`.

## Merge Direction Summary

```
feature/xyz ──► develop ──► main (release)
fix/abc     ──► develop ──► main (release)
fix/hotfix  ──► main (patch) ──► develop (backport)
```
