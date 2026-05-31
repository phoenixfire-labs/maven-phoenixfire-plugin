# Trying Phoenixfire on an existing Maven project

Use this when pointing Phoenixfire at a large or messy corporate test suite.

## 1. Add the plugin (Maven Central)

Keep your existing **JUnit Jupiter** (or Spring Boot test) dependencies — Phoenixfire only replaces the
Maven test **runner**. See [COMPATIBILITY.md](COMPATIBILITY.md).

No extra repository when using a **released** version from Central:

```xml
<plugin>
  <groupId>io.github.phoenixfire-labs</groupId>
  <artifactId>phoenixfire-maven-plugin</artifactId>
  <version>0.1.0</version>
</plugin>
```

## 2. Opt in on one module first

In the module you want to trial (often the heaviest test module), disable Surefire/Failsafe and
bind Phoenixfire:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-surefire-plugin</artifactId>
      <configuration><skip>true</skip></configuration>
    </plugin>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-failsafe-plugin</artifactId>
      <configuration><skip>true</skip></configuration>
    </plugin>
    <plugin>
      <groupId>io.github.phoenixfire-labs</groupId>
      <artifactId>phoenixfire-maven-plugin</artifactId>
      <version>0.1.0</version>
      <executions>
        <execution>
          <id>phoenixfire-test</id>
          <goals><goal>test</goal></goals>
        </execution>
        <execution>
          <id>phoenixfire-it</id>
          <goals>
            <goal>integration-test</goal>
            <goal>verify</goal>
          </goals>
        </execution>
      </executions>
      <configuration>
        <!-- Start close to your Surefire settings, then tune. -->
        <forkCount>1</forkCount>
        <rerunFailingTestsCount>0</rerunFailingTestsCount>
      </configuration>
    </plugin>
  </plugins>
</build>
```

Run:

```bash
mvn -pl YOUR_MODULE test
mvn -pl YOUR_MODULE verify
```

## 3. What to compare

| Output | Location |
|--------|----------|
| Surefire-style XML | `target/surefire-reports/` (compatibility layout) |
| Native audit trail | `target/phoenixfire-reports/phoenixfire-report.json` |
| Per-attempt facts | `target/phoenixfire-reports/phoenixfire-facts.jsonl` |

Look for tests that **failed in a shared fork** then **passed in isolation** (`forkReuseSensitive`
in the JSON report) — that pattern often matches enterprise state-pollution bugs.

## 4. Narrowing a huge suite

Use the same selectors as Surefire while trialing:

```bash
mvn -pl YOUR_MODULE test -Dtest=com.acme.**.*Fail*
mvn -pl YOUR_MODULE test -Dphoenixfire.shardIndex=0 -Dphoenixfire.shardCount=4
```

## 5. Rollback

Remove or comment the Phoenixfire executions and re-enable Surefire/Failsafe (`skip>false`).
No code changes in tests are required for rollback.
