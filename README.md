# Phoenixfire

Fault-tolerant test orchestration for Maven. Phoenixfire is a resilient, supervisory replacement for
Surefire/Failsafe that assumes forked JVMs are unreliable and can die in non-recoverable ways. It
guarantees that **every discovered test reaches a terminal state** and that CI always receives a
**complete, coherent report**, even after catastrophic fork failures.

## Why

Surefire/Failsafe are fragile when a forked JVM crashes, hangs, gets OOM-killed, or becomes
corrupted: execution can terminate prematurely, retries may run in a poisoned JVM, and the final
report can be incomplete. Phoenixfire detects these failures, escalates isolation, and accounts for
every test.

The fork is assumed to be an unreliable worker process whose state must be monitored, accounted for, 
and recovered from. Result visiblity as an actional fact-table is a first class consideration, and so 
is restarting terminated forks with configurable retry logic and more. 

## How it works

- **Controller owns the truth.** While a Maven goal is running, all orchestration state lives in the
  controller (in-memory execution journal). Forks are disposable workers. Optional `journal.ndjson`
  is an append-only audit log for that run, not a checkpoint to resume a failed build.
- **Dedicated IPC channel.** Each fork connects back over a loopback socket and streams per-test
  lifecycle events plus periodic heartbeats (NDJSON). Control signals are therefore immune to test
  code writing to `System.out`/`System.err`.
- **Failure detection.** The supervisor classifies fork death by exit code, missing heartbeat (hang),
  failed handshake (classloading/fork instability), and OOM markers.
- **Adaptive escalation.** A crashed/hung test is never retried in the JVM that poisoned it. It is
  retried along an escalating isolation ladder:

  `SHARED_FORK_POOL -> FRESH_FORK -> ONE_FORK_PER_CLASS`

- **Complete reporting.** Surefire-compatible `TEST-*.xml` plus a native `phoenixfire-report.json`
  audit trail (every attempt, failure mode, escalation path). Tests that can never complete are
  forced to a terminal `CRASHED` state so nothing is silently lost.

## Modules

- `phoenixfire-api` - stable model + extension SPIs + IPC contract (zero external dependencies).
- `phoenixfire-fork-runner` - lightweight in-fork worker (JUnit Platform Launcher + IPC client).
- `phoenixfire-core` - controller engine (discovery, journal, scheduler, supervisor, retry, reports).
- `phoenixfire-maven-plugin` - the Maven plugin binding goals to lifecycle phases.

## Requirements

- Java 17+
- Maven 3.6.3+
- JUnit 5 (JUnit Platform). JUnit 4 tests run transparently via the JUnit Vintage engine if present.

### JDK 17 vs 21 in CI

CI builds and tests on both JDK 17 and 21; **publish** builds once on JDK 17. There is a single set
of published artifacts (`io.github.benmanifold:*`), compiled with `maven.compiler.release` **17** (Java 17
bytecode). You do **not** publish separate coordinates per JDK.

- **Consumer on JDK 17** — supported (baseline).
- **Consumer on JDK 21** — supported; the JVM runs Java 17 bytecode without a separate plugin build.

Test **forks** use the JDK configured for the project under test (Maven `java` / toolchain), not the
JDK used to compile Phoenixfire. The matrix only proves the plugin runs correctly when Maven itself
is on 17 or 21.

## Usage: opt-in full replacement of Surefire/Failsafe

Disable the built-in Surefire/Failsafe executions and bind Phoenixfire to `test`,
`integration-test`, and `verify`:

```xml
<build>
  <plugins>
    <!-- Turn off the default Surefire/Failsafe executions. -->
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-surefire-plugin</artifactId>
      <configuration>
        <skip>true</skip>
      </configuration>
    </plugin>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-failsafe-plugin</artifactId>
      <configuration>
        <skip>true</skip>
      </configuration>
    </plugin>

    <!-- Run tests with Phoenixfire instead. -->
    <plugin>
      <groupId>io.github.benmanifold</groupId>
      <artifactId>phoenixfire-maven-plugin</artifactId>
      <version>0.1.0</version>
      <executions>
        <execution>
          <id>phoenixfire-test</id>
          <goals>
            <goal>test</goal>
          </goals>
        </execution>
        <execution>
          <id>phoenixfire-integration-test</id>
          <goals>
            <goal>integration-test</goal>
            <goal>verify</goal>
          </goals>
        </execution>
      </executions>
      <configuration>
        <forkCount>2</forkCount>
        <maxAttempts>3</maxAttempts>
        <heartbeatTimeout>30000</heartbeatTimeout>
        <rerunFailingTestsCount>0</rerunFailingTestsCount>
      </configuration>
    </plugin>
  </plugins>
</build>
```

## Configuration (selected)

Surefire-parity: `includes`, `excludes`, `includesFile`, `excludesFile`, `forkCount`, `argLine`,
`systemPropertyVariables`, `environmentVariables`, `skipTests`/`maven.test.skip`, `testFailureIgnore`,
`rerunFailingTestsCount`. Test selection uses Surefire-style path globs (e.g. `**/*Test.java`);
`includesFile`/`excludesFile` read one pattern per line (`#` comments allowed), keeping large
selections out of the POM.

### Selecting tests from the command line (`-Dtest` / `-Dit.test`)

Surefire/Failsafe parity for ad-hoc selection. The unit goal reads `-Dtest`, the integration-test goal
reads `-Dit.test`, so the two phases stay independently selectable:

```bash
mvn test -Dtest=FooTest                 # one class (simple or fully-qualified name)
mvn test -Dtest='FooTest#bar+baz'       # specific methods
mvn test -Dtest='*ServiceTest,!SlowTest' # wildcards and negation (!)
mvn verify -Dit.test='CheckoutIT#happy*' # integration tests, by method pattern
```

Like Surefire, a `-Dtest` value **overrides the includes**, so a class outside the default
`*Test`/`*IT` globs is still found. Selection is applied as a precise post-discovery filter, so
method-level (`#method`) selection works regardless of fork/isolation strategy.

### Sharding (split a suite across CI nodes)

Indexable, Jest-style sharding splits the suite into `count` shards; each node runs one:

```bash
mvn test -Dphoenixfire.shard.index=1 -Dphoenixfire.shard.count=4   # node 1 of 4
```

Sharding is **by class** (all methods of a class stay together, preserving class fixtures and the
fork-reuse model) and **deterministic**: classes are sorted by name and assigned round-robin, so every
node computes the same partition with no coordination and balanced class counts. The shard identity is
recorded in the run envelope and on every `phoenixfire-facts.jsonl` line, so per-node results merge
cleanly downstream. `count <= 1` disables sharding. Combine with `-Dtest` to shard a pre-filtered set.

### JVM arguments (`argLine`)

`argLine` is a drop-in for Surefire/Failsafe and is applied to every fork. Because the parameter is
bound to the `argLine` *property* (`@Parameter(property = "argLine")`), agents that publish that
property work unchanged - notably `jacoco:prepare-agent`, whose coverage agent is picked up with no
config change. The value is **quote-aware** (`-Dmsg="a b"` stays one argument) and supports Surefire's
`@{property}` **late expansion**, resolved just before the fork launches against build-time project
properties (then JVM system properties) - e.g. `<argLine>@{argLine} -Xmx1g</argLine>`.

### Command-line length safety

Phoenixfire never places the set of tests to run on a fork's command line - the test list is sent to
each fork over the IPC socket, so a project with thousands of test classes is fine. The classpath
(the only realistically unbounded argument) is written to a JVM `@argfile` rather than the command
line, so launches stay under the operating system's command-line length limit on every platform.

Phoenixfire-specific: `maxAttempts`, `heartbeatInterval`, `heartbeatTimeout`, `backoff`,
`escalationLadder` (list of `IsolationLevel` names), `journalEnabled`, `phoenixfire.reportsDirectory`,
`failOnFlakyTests`.

### Shared-pool retry before isolating (`sharedForkPoolMaxPasses`)

Within a **single** `mvn test` / `mvn verify` invocation (not a failed CI job replay), Phoenixfire can
retry tests after a shared-pool fork dies. By default a shared-pool crash escalates straight to an
isolated fork. Set `sharedForkPoolMaxPasses` (default `1`) higher to treat an early crash as possibly
transient and **retry the affected tests in a fresh shared-pool fork** that many times before paying
for isolation:

```bash
mvn test -Dphoenixfire.sharedForkPoolMaxPasses=2
```

This is the "run in a shared JVM; if a fork dies, restart where it left off; then, only if it keeps
dying, fall back to isolated JVMs (`reuseForks=false`)" workflow. Each retry uses a brand-new JVM (the
poisoned one is dead), and the whole sequence stays bounded by `maxAttempts`. With the default `1`,
behaviour is unchanged: escalate on the first shared-pool crash.

When a fork **crashes or exits abnormally**, every non-passing test in that fork (including assertion
failures that ran before the crash) is retried on the same path: another shared pass if
`sharedForkPoolMaxPasses` allows, then isolated forks. A failure in a **clean** fork is only re-run at
the same level (`rerunFailingTestsCount`, Surefire parity) and is not escalated.

### Failure semantics (crash-then-recover)

By default, the build fails **only** when a test never recovers - i.e. it crashes or fails on its
initial attempt **and** on every enabled retry/escalation, ending in a terminal `CRASHED`/`FAILED`
state. A test that crashes initially but is rescued by an escalated retry ends `PASSED`, is reported
as **flaky** (`recovered: true` in the JSON report, `flaky` count in the summary), and does **not**
fail the build.

Set `failOnFlakyTests` (`-Dphoenixfire.failOnFlakyTests=true`) to also fail the build on any test
that needed a retry to pass. Either way, flaky tests are always logged and recorded in the reports.

## Extensibility (SPI, via `java.util.ServiceLoader`)

Implement and register any of these (classes discovered on the project test classpath):

- `io.phoenixfire.api.spi.IsolationStrategy` - custom work-unit planning per isolation level.
- `io.phoenixfire.api.spi.RetryPolicy` - custom retry/escalation decisions.
- `io.phoenixfire.api.spi.FailureClassifier` - custom mapping of exit codes/signals to failure modes.
- `io.phoenixfire.api.report.ReportWriter` - custom report formats (defaults: JUnit XML + JSON).

## Reports

Written to `target/phoenixfire-reports` (unit) / `target/phoenixfire-it-reports` (integration):

- `TEST-<ClassName>.xml` - Surefire-compatible JUnit XML.
- `phoenixfire-report.json` - native audit report (run envelope, per-test attempts and escalation path).
- `phoenixfire-facts.jsonl` - vendor-agnostic JSON Lines "fact table" for downstream analytics (see below).
- `journal.ndjson` - append-only audit timeline for the run (`RUNNING`, outcomes, retries; when
  `journalEnabled` is true). Not replayed on the next Maven invocation.
- `forks/<forkId>.log` - merged stdout/stderr per fork (diagnostic tails on failure).

### Run envelope and the JSON Lines fact table

Every report carries a **run envelope** identifying the run so results can be correlated over time:
a generated `runId`, host/OS/JVM, the resilience config in effect, the Maven coordinates, plus
optional git/CI metadata. The plugin is **CI-vendor-agnostic**: it auto-detects what it can in-process
(host/OS/JVM, project, config), does a best-effort local `git` fallback for commit/branch/dirty, and
otherwise reads neutral overrides that win over everything. Map your CI's variables onto them once:

```bash
mvn verify \
  -Dphoenixfire.git.sha=$GITHUB_SHA \
  -Dphoenixfire.git.branch=$GITHUB_REF_NAME \
  -Dphoenixfire.ci.provider=github \
  -Dphoenixfire.ci.buildId=$GITHUB_RUN_ID \
  -Dphoenixfire.ci.buildUrl=$GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID
```

Arbitrary labels (e.g. `service`, `team`) can be added via the `runLabels` map parameter.

`phoenixfire-facts.jsonl` is one JSON object per line, designed to be shipped verbatim into any
pipeline (Loki via `| json`, Elasticsearch/ECS, Splunk, an OpenTelemetry Collector `filelog`
receiver, or `jq`/DuckDB). Three record types: `run` (full envelope + summary), `test_result` (final
state plus the fork-reuse diagnosis), and `test_attempt` (per attempt). Key low-cardinality
dimensions (`runId`, `gitSha`, `gitBranch`, `os`, `jvm`) are denormalised onto every line so a log
tool can slice without a join. Notable fields:

- `forkReuseSensitive` - test failed/crashed in a reused pooled fork but passed once isolated
  (state-pollution signature: "consistently fails with fork reuse").
- `firstFailLevel` / `recoveryLevel` - where it first broke and which isolation level rescued it.
- `forkReuse` (per attempt) - whether the attempt ran in a reused pooled JVM.
- `exitCode`, `failureMode`, `failureSignature` - crash diagnostics; the signature clusters
  "the same crash" across attempts and runs.

## Using from Maven Central

Released versions are published under **`io.github.benmanifold`** (no extra repository or PAT).
Use a release version from [Maven Central](https://central.sonatype.com/search?q=io.github.benmanifold+phoenixfire):

```xml
<plugin>
  <groupId>io.github.benmanifold</groupId>
  <artifactId>phoenixfire-maven-plugin</artifactId>
  <version>0.1.0</version>
</plugin>
```

SNAPSHOTs are not on Central; use GitHub Packages below while iterating on `main`.

One-time publisher setup (namespace, GPG, Sonatype token): see [docs/MAVEN-CENTRAL.md](docs/MAVEN-CENTRAL.md).

To trial on an existing corporate multi-module project: [docs/TRY-IN-ENTERPRISE.md](docs/TRY-IN-ENTERPRISE.md).

## Using from GitHub Packages

Artifacts are also published to [GitHub Packages](https://github.com/BenManifold/maven-phoenixfire-plugin/packages)
for this repository (`io.github.benmanifold:*`). Useful for **SNAPSHOT** builds before a Central release.

Add the repository and authenticate (a [classic PAT](https://github.com/settings/tokens) or fine-grained
token with `read:packages`, or `GITHUB_TOKEN` in CI) with server id `github`:

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/BenManifold/maven-phoenixfire-plugin</url>
    <snapshots><enabled>true</enabled></snapshots>
  </repository>
</repositories>
<pluginRepositories>
  <pluginRepository>
    <id>github</id>
    <url>https://maven.pkg.github.com/BenManifold/maven-phoenixfire-plugin</url>
    <snapshots><enabled>true</enabled></snapshots>
  </pluginRepository>
</pluginRepositories>
```

In `~/.m2/settings.xml` (or CI secrets):

```xml
<servers>
  <server>
    <id>github</id>
    <username>YOUR_GITHUB_USERNAME</username>
    <password>YOUR_TOKEN</password>
  </server>
</servers>
```

Then depend on a released version, for example:

```xml
<plugin>
  <groupId>io.github.benmanifold</groupId>
  <artifactId>phoenixfire-maven-plugin</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</plugin>
```

## Publishing

Versioning uses the usual Maven **CI-friendly `${revision}` property** (see [Maven CI Friendly
Versions](https://maven.apache.org/maven-ci-friendly.html)). Release versions come from the Git tag,
not a manual edit at release time.

### Create a release (recommended)

1. In GitHub: **Releases → Create a new release**.
2. Choose tag **`v0.1.0`** (with `v` prefix) targeting `main`.
3. Publish the release.

On release, two workflows run:

1. **Publish to Maven Central** (`.github/workflows/publish-central.yml`) — primary for enterprise
   consumers; coordinate `io.github.benmanifold:phoenixfire-maven-plugin:x.y.z`.
2. **Publish to GitHub Packages** (`.github/workflows/publish.yml`) — same version to GitHub Packages.

Both then bump **`main`** to **`x.y.z+1-SNAPSHOT`** (via `publish.yml`). Example: after `0.2.0` → `0.2.1-SNAPSHOT`.

You can also push a tag alone (`git tag v0.1.0 && git push origin v0.1.0`) or run the workflows
manually with a version input.

### SNAPSHOT builds for testing

Every push to **`main`** runs **Publish SNAPSHOT to GitHub Packages** (`.github/workflows/publish-snapshot.yml`).
It deploys whatever `<revision>` is in `pom.xml` (must end with `-SNAPSHOT`, e.g. `0.1.1-SNAPSHOT`).
Use that coordinate in a consumer project while iterating.

### One-time: `PACKAGES_PUBLISH_TOKEN` (fixes 401)

Maven deploy to GitHub Packages usually **cannot** use `GITHUB_TOKEN` alone (401 Unauthorized). Add a
repository secret once:

1. GitHub → **Settings → Developer settings → Personal access tokens → Tokens (classic)** → generate.
2. Scopes: **`write:packages`**, **`read:packages`**, and **`repo`** if this repository is private.
3. Repo → **Settings → Secrets and variables → Actions** → **New repository secret** → name
   `PACKAGES_PUBLISH_TOKEN`, paste the token.

Both publish workflows fail fast with instructions if the secret is missing.

`phoenixfire-it` is not published (`maven.deploy.skip`).

Local deploy to GitHub Packages: `mvn -B -ntp clean deploy -Pgithub-packages -Prun-its` with the
`github` server in `~/.m2/settings.xml`.

Local deploy to Maven Central: GPG + `central` server in `~/.m2/settings.xml`, then
`mvn -B -ntp clean deploy -Pcentral -Prun-its` (release version only, not `-SNAPSHOT`).

## Status

MVP. JUnit 5 Platform only.

## Trademarks

Phoenixfire is an independent project and is not affiliated with, sponsored by, or endorsed by the
Apache Software Foundation. Apache, Maven, Apache Maven, Surefire, and Failsafe are trademarks of the
Apache Software Foundation. These names are used here only to describe interoperability and
compatibility.
