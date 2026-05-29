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

## How it works

- **Controller owns the truth.** All durable state lives in the controller (the Maven JVM) in an
  execution journal. Forks are disposable workers.
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
      <groupId>io.phoenixfire</groupId>
      <artifactId>phoenixfire-maven-plugin</artifactId>
      <version>0.1.0-SNAPSHOT</version>
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

Surefire-parity: `includes`, `excludes`, `forkCount`, `argLine`, `systemPropertyVariables`,
`environmentVariables`, `skipTests`/`maven.test.skip`, `testFailureIgnore`, `rerunFailingTestsCount`.

Phoenixfire-specific: `maxAttempts`, `heartbeatInterval`, `heartbeatTimeout`, `backoff`,
`escalationLadder` (list of `IsolationLevel` names), `journalEnabled`, `phoenixfire.reportsDirectory`.

## Extensibility (SPI, via `java.util.ServiceLoader`)

Implement and register any of these (classes discovered on the project test classpath):

- `io.phoenixfire.api.spi.IsolationStrategy` - custom work-unit planning per isolation level.
- `io.phoenixfire.api.spi.RetryPolicy` - custom retry/escalation decisions.
- `io.phoenixfire.api.spi.FailureClassifier` - custom mapping of exit codes/signals to failure modes.
- `io.phoenixfire.api.report.ReportWriter` - custom report formats (defaults: JUnit XML + JSON).

## Reports

Written to `target/phoenixfire-reports` (unit) / `target/phoenixfire-it-reports` (integration):

- `TEST-<ClassName>.xml` - Surefire-compatible JUnit XML.
- `phoenixfire-report.json` - native audit report (per-test attempts and escalation path).
- `journal.ndjson` - crash-safe append-only event log (within-run resume).
- `forks/<forkId>.log` - merged stdout/stderr per fork (diagnostic tails on failure).

## Status

MVP. JUnit 5 Platform only; within-run resume only (no cross-run resume yet).
