# Agent instructions (maven-phoenixfire-plugin)

Follow the Phoenixfire workspace guidance in the parent repo:

- [../AGENTS.md](../AGENTS.md)
- [../.cursor/rules/avoid-static-utilities.mdc](../.cursor/rules/avoid-static-utilities.mdc)

**Summary:** Prefer injectable instance collaborators over static utility/helper classes; use `DEFAULT` singletons only as production defaults, not as the only API.
