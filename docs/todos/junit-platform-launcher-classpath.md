---
title: JUnit Platform launcher classpath (Surefire-style UX)
status: draft
---

# JUnit Platform launcher classpath (Surefire-style UX)

## Problem

Many enterprise projects depend only on `junit-jupiter` (or a BOM such as Spring Boot) and do **not** declare `junit-platform-launcher`. Surefire historically avoided breakage by supplying a launcher on the provider side. Phoenixfire already bundles `junit-platform-launcher` (plugin dependency, appended in `AbstractPhoenixfireMojo.buildForkClasspath()`), but:

- The **test classpath is built first**; plugin artifacts (including the bundled launcher) are **appended**.
- If engines are JUnit 5.10 (or similar) and no project launcher is present, the plugin’s bundled launcher (currently **6.0.3**) can load against mismatched engines → discovery fails with `NoSuchMethodError` / `AbstractMethodError` on `org.junit.platform.*`.

`LauncherCompatibilityDiagnostics` improves the error message; it does not fix the default classpath policy.

## Architectural constraint

Surefire runs providers on a **controlled/provider classpath** and discovers engines from the test side. Phoenixfire runs `ForkRunnerMain` on the **merged test + plugin classpath** (`ForkLauncher` `@argfile`). On that model, launcher and engines must be **binary-compatible on one classpath** — we cannot assume a single globally shipped launcher version works for every consumer.

## Options

| Option | Summary | Pros | Cons |
|--------|---------|------|------|
| **A. Status quo + docs** | Keep appending plugin launcher; document explicit `junit-platform-launcher` test dep | Simple; predictable when project declares launcher | Poor migration from Surefire; easy misconfiguration |
| **B. Auto-resolve matching launcher** (recommended) | Detect engine version(s) from resolved test deps / classpath; if `junit-platform-launcher` is absent, resolve and add a launcher at the **same version line** via Maven resolver; if project already has a launcher, leave it | Surefire-like “just works” for `junit-jupiter`-only projects; respects BOMs that pin both | Must handle JUnit 5 vs 6 version schemes, multiple engine versions, resolver failures |
| **C. Prepend plugin launcher** | Put plugin’s launcher **first** on fork classpath | Always uses a known launcher | Forces plugin version over project; **worse** for JUnit 5 consumers |
| **D. Isolated provider classloader** | Match Surefire: launcher/provider in isolated loader; engines from test classpath | Closest to Surefire semantics; less version coupling on one flat CP | Larger architectural change; more complexity in fork IPC / class loading |
| **E. Require explicit launcher only** | Never append plugin launcher; fail fast with diagnostics if missing | No version skew from plugin jar | Same extra POM step users want to avoid |

## Recommended direction

**Option B** as the default policy:

1. Scan dependency tree / test classpath for `junit-platform-engine`, `junit-jupiter-engine`, etc.
2. If **`junit-platform-launcher` is not** on the test classpath, resolve and add launcher at matching version.
3. If the project **already** declares launcher, do not override.
4. If detection or resolution fails, fail with existing `LauncherCompatibilityDiagnostics` guidance.
5. Only use the plugin-bundled launcher as last resort when it matches detected engines (or no engines found).

## Likely implementation touchpoints

- `phoenixfire-maven-plugin`: `AbstractPhoenixfireMojo.buildForkClasspath()`
- New helper, e.g. `LauncherClasspathResolver` (Maven APIs / `RepositorySystem` + project dependencies)
- Tests: invoker IT with JUnit 5.10 consumer (no launcher dep), JUnit 6 BOM, explicit launcher override
- Docs: update `COMPATIBILITY.md` once B ships (reduce “add launcher yourself” to fallback)

## Classpath policy (when implementing B)

- **Done (partial):** if the test classpath already contains `junit-platform-launcher-*.jar`, do not append the plugin’s launcher (`LauncherClasspathPolicy`).
- **Remaining:** if no launcher on the test classpath, resolve and add one from the project’s dependency graph (engine/BOM version) instead of blindly appending the plugin’s JUnit 6 jar.
- Fork classpath plugin artifacts: `io.github.phoenixfire-labs` modules only, plus `org.junit.platform` launcher only as fallback — **no** private publish namespaces on the fork classpath.

## Related

- `io.phoenixfire.api.junit.LauncherCompatibilityDiagnostics` — fork + controller messaging (done)
- `phoenixfire-it` `all-passing` — JUnit 5.10.2 compatibility fixture

## Implementation checklist

- [ ] `LauncherClasspathResolver` (engine version detection + Maven resolve)
- [ ] Integrate into `AbstractPhoenixfireMojo.buildForkClasspath()`
- [ ] Invoker IT: JUnit 5.10 without launcher dep; JUnit 6 BOM; explicit launcher override
- [ ] Update `COMPATIBILITY.md`
