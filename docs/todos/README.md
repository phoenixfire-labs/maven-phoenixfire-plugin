# Planned work (design notes)

This folder holds **implementation plans** for Phoenixfire — one markdown file per initiative, written so a future PR (or agent session) can pick them up without re-deriving context.

## Conventions

- **Filename:** kebab-case topic, e.g. `semantic-fork-ids.md`.
- **Status** (optional YAML frontmatter): `draft` | `ready` | `in-progress` | `done`.
- **Scope:** problem, proposed design, touchpoints, tests, and explicit non-goals.
- **Checklist:** use `- [ ]` tasks at the bottom or in frontmatter `todos` when helpful.

## Index

| Plan | Status | Summary |
|------|--------|---------|
| [semantic-fork-ids.md](semantic-fork-ids.md) | draft | Semantic `fork` in reports; per-slot shared logs + aggregated `fork-fresh`/`fork-isolated` logs |
| [junit-platform-launcher-classpath.md](junit-platform-launcher-classpath.md) | draft | Auto-resolve matching `junit-platform-launcher` on fork classpath (Surefire-style UX) |

## Related docs

- [DEVELOPMENT.md](../DEVELOPMENT.md) — build and module layout
- [COMPATIBILITY.md](../COMPATIBILITY.md) — JUnit / launcher compatibility (today)
