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

- **The main session orchestrates; it does not write code itself** (owner's standing
  request, 2026-07). Delegate hands-on implementation — source/test edits in `core/`
  and `plugin/`, config files — to subagents on a cheaper model (Agent tool with
  `model: "sonnet"`). The orchestrator keeps for itself: planning, delegation prompts,
  diff review, running tests/CI, git, PRs, and `docs/`/`CLAUDE.md` upkeep. Trivial
  one-line fixes still go through a subagent unless the owner says otherwise.
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

## Placement-fidelity playbook

Lessons from the outpost false-positive fix (0.3.0); follow them for future
marker-vs-reality reports:

- **Desk-verify before implementing**: reproduce the suspected placement math in a
  scratchpad script and test it against the reporter's world seed + coordinates first.
  The outpost rarity gate was confirmed 4/4 (three phantoms fail, the real one passes)
  — and competing formula variants were *refuted* by the same data — before any core
  code changed.
- **cubiomes cross-checks must cover `isViableStructurePos`, not just
  `getStructureConfig`**: spacing/separation/salt live in the config, but rarity gates,
  exclusion zones, and other extra conditions live in the viability check. Checking
  only the config is how the outpost gate was originally missed.
- Remaining known gaps are tracked in DESIGN §2.2 / REQUIREMENTS §6 (end-city distance
  floor, fortress biome-gating question, stronghold biome nudge). Validate against a
  real server before modeling them.
