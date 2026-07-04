# CLAUDE.md

Paper plugin that renders seed-derived structure markers on BlueMap.
Read `docs/REQUIREMENTS.md` and `docs/DESIGN.md` before changing behavior.

## Module layout

- `core/` — pure Java (`--release 21` so sandbox JDK 21 can run its tests), **no
  Bukkit/Paper/BlueMap imports allowed**. All logic that can be a pure function lives
  here, test-first (JUnit 5).
- `plugin/` — thin Paper adapter, `--release 25` (Minecraft 26.x runs Java 25).
  `compileOnly` paper-api + bluemap-api. Compiles only where JDK 25 + Paper repos are
  available (CI).

## Build commands

```sh
./gradlew :core:test    # fast local red/green loop — works in sandboxed envs
./gradlew build         # full build incl. :plugin — needs repo.papermc.io access
```

**Sandbox caveat:** Claude Code remote environments can reach Maven Central and
services.gradle.org but NOT `repo.papermc.io` / `repo.bluecolored.de`. So `:plugin`
cannot compile locally there — GitHub Actions (`.github/workflows/ci.yml`) is the
verification loop for `:plugin`. Always run `:core:test` locally before pushing, then
watch CI for the plugin module.

## Working with subagents

Rules that made delegated implementation work well here; keep following them:

- **Subagents never run git.** They report; the orchestrator reviews the diff and
  commits. Uncommitted changes during a delegation are in-flight work, not something
  to commit on a hook's prompting.
- **Partition parallel work by file ownership** (e.g. agent owns `plugin/`,
  orchestrator owns `docs/`) so reviews stay clean and edits never collide.
- **Pre-verify Paper/BlueMap API signatures before writing `plugin/` code** — the
  module doesn't compile locally, so a guessed signature costs a full CI round trip.
  Check jd.papermc.io (via search; direct fetch may be blocked) and put exact
  imports/signatures into the delegation prompt.
- **Give agents an executable oracle when porting algorithms**: golden-vector
  generators (e.g. trimmed reference implementations) belong in the session
  scratchpad, never in the repo; only the generated vectors are committed, inside
  tests.
- **Ask for a structured report back**: files touched, exact external API calls used,
  deviations from instructions, remaining risks. It's the review checklist.

## Conventions

- 2-space indent (see `.editorconfig`), UTF-8, LF.
- Commit messages: `type: <gitmoji> summary` (e.g. `feat: ✨ add ring planner`),
  matching existing history.
- Target Minecraft version is pinned ONLY in `gradle/libs.versions.toml`
  (`paper-api`). Do not hardcode it elsewhere.
- New structure layers go in `StructureCatalog` (core) + an icon in
  `plugin/src/main/resources/icons/` + a row in the DESIGN.md table.
