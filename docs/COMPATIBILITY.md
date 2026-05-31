# Test framework compatibility

Phoenixfire is a **JUnit Platform launcher**. It runs tests the same way Surefire/Failsafe do when
those plugins delegate to Platform engines on the project test classpath. Phoenixfire is **not** a
separate test API and does **not** replace your test dependencies.

## Supported

| Category | Examples | Notes |
|----------|----------|--------|
| **JUnit Jupiter** | `@Test`, `@ParameterizedTest`, extensions, nested tests | Primary path. **JUnit 5.10+** and **JUnit 6.x** on the test classpath (typical: `junit-jupiter` from Maven BOM). |
| **JUnit Vintage** | JUnit 4 `@Test` classes | Requires `junit-vintage-engine` on the test classpath (same as Surefire + Vintage). |
| **Build stacks** | Spring Boot `spring-boot-starter-test`, plain Maven `junit-jupiter`, corporate parent BOMs | Use whatever JUnit versions the BOM pins; Phoenixfire does not force the plugin’s JUnit patch level. |

**Classpath rule:** the fork uses **`project.getTestClasspathElements()` first**, then adds Phoenixfire
and a Platform **launcher** jar if that path is not already present. Your project’s Jupiter/engine jars
should come from the test classpath; the **launcher** should match your Platform engine version.

**JUnit 5 projects:** a minimal `junit-jupiter` dependency alone may not put `junit-platform-launcher`
on the test classpath (Surefire used to supply its own). Add an explicit launcher at the same line as
your Jupiter version, for example:

```xml
<dependency>
  <groupId>org.junit.platform</groupId>
  <artifactId>junit-platform-launcher</artifactId>
  <version>1.10.2</version>
  <scope>test</scope>
</dependency>
```

(or use a BOM / `spring-boot-starter-test` that already manages both). **JUnit 6** BOMs typically
align launcher and engines on one version (e.g. `6.0.3`). If no launcher is on the classpath, Phoenixfire
adds the launcher version bundled with the plugin (**6.0.3**), which will not run against JUnit 5.10
engines.

## Not supported

| Category | Why |
|----------|-----|
| **TestNG** | Different runner; not a JUnit Platform engine. |
| **JUnit 4 without Vintage** | No Platform engine for raw JUnit 4. |
| **Main-only / manual runners** | Nothing for Platform to discover. |

## What you configure

- **Maven:** opt in to Phoenixfire goals instead of Surefire/Failsafe (see [README](../README.md)).
- **Tests:** keep existing Jupiter (and Vintage if needed) dependencies — no Phoenixfire-specific test library.

## What Phoenixfire is built and tested with

This repository compiles and unit-tests against **JUnit 6.0.3** (Platform launcher on the plugin
classpath). Invoker ITs mostly use **6.0.3**; **`all-passing`** uses **5.10.2** to guard compatibility
with older JUnit 5 consumer BOMs.

If something fails only on JUnit 5.10 vs 6.0, open an issue with the consumer `junit-jupiter` version
and a minimal repro.
