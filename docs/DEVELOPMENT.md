# Development, CI, and release

How this repository is built, tested, and published. For using Phoenixfire in your own project, see the [README](../README.md).

## Repository layout

| Module | Role |
|--------|------|
| `phoenixfire-api` | Stable model, extension SPIs, IPC contract |
| `phoenixfire-fork-runner` | In-fork JUnit Platform worker |
| `phoenixfire-core` | Controller engine |
| `phoenixfire-maven-plugin` | Maven plugin goals |
| `phoenixfire-coverage` | JaCoCo aggregate report and optional gate |
| `phoenixfire-it` | Invoker integration tests (not published) |

Versioning uses Maven **CI-friendly `${revision}`** ([docs](https://maven.apache.org/maven-ci-friendly.html)). Release versions come from Git tags (`v*`), not manual POM edits at release time.

**JUnit:** the reactor uses the **JUnit 6.0.3** BOM (`${junit.version}`). Invoker ITs exercise **6.0.3** and one legacy **5.10.2** consumer (`all-passing`). User-facing compatibility: [COMPATIBILITY.md](COMPATIBILITY.md).

## JDK policy

- **Published bytecode:** Java **17** (`maven.compiler.release=17`), single coordinate set `io.github.phoenixfire-labs:*`.
- **Consumers:** JDK 17 or 21 (and later) can run the plugin; no per-JDK artifacts.
- **Test forks** in consumer projects use the project’s JDK / toolchain, not the JDK used to compile Phoenixfire.
- **This repo’s CI** runs the full test suite on JDK **17** and **21** to guard Maven-on-17 vs Maven-on-21 compatibility.

## Continuous integration

| Trigger | Workflow | What runs |
|---------|----------|-----------|
| Pull request → `main` | [build.yml](../.github/workflows/build.yml) | `mvn clean verify install -Prun-its -Pcoverage` on JDK 17 and 21 |
| Push to `main` | [publish-snapshot.yml](../.github/workflows/publish-snapshot.yml) | Deploy `-SNAPSHOT` (no test suite; registry TBD for consumers) |
| Release / tag `v*` | [publish-central.yml](../.github/workflows/publish-central.yml), [publish.yml](../.github/workflows/publish.yml) | Deploy release (Central + GitHub Packages per current workflows) |

**Branch protection:** require the PR **Build** check before merge.

Build uploads `phoenixfire-coverage/target/site/jacoco-aggregate/` (JDK 17 leg only) and test report artifacts. See [COVERAGE.md](COVERAGE.md) for gate scope and local commands.

### CI metadata in reports

The Build workflow passes git/CI dimensions into the plugin for integration-test runs, for example:

```bash
-Dphoenixfire.git.sha=...
-Dphoenixfire.git.branch=...
-Dphoenixfire.ci.provider=github
-Dphoenixfire.ci.buildId=...
-Dphoenixfire.ci.buildUrl=...
```

Consumers can set the same properties in any CI; see README **Reports**.

## Local commands (contributors)

```bash
# Default: tests without JaCoCo gate
mvn clean verify
mvn clean verify install -Prun-its

# Fast install (no tests, no JaCoCo)
mvn install -DskipTests

# Optional coverage gate (95% line / 90% branch)
mvn clean verify -Pcoverage
mvn clean verify install -Prun-its -Pcoverage
```

Gap triage after a coverage build: `scripts/jacoco-gaps.py` (reads aggregate CSV).

## Code coverage

JaCoCo is **off by default** (`jacoco.skip=true`). PR CI enables **`-Pcoverage`**. Details: [COVERAGE.md](COVERAGE.md).

## Publishing

### Release (recommended)

1. GitHub **Releases → Create a new release**.
2. Tag **`v0.1.0`** (with `v` prefix) on `main`.
3. Publish the release.

Workflows:

1. **Publish to Maven Central** — `publish-central.yml`; uploads to Sonatype then exits. Check [central.sonatype.com](https://central.sonatype.com) → **Deployments** for live status.
2. **Publish to GitHub Packages** — `publish.yml`; same version.

`publish.yml` then bumps `main` to **`x.y.z+1-SNAPSHOT`** (e.g. after `0.2.0` → `0.2.1-SNAPSHOT`).

Alternatively: `git tag v0.1.0 && git push origin v0.1.0`, or run workflows manually with a version input.

### SNAPSHOTs on `main`

Each push to `main` runs **publish-snapshot.yml**, deploying the current `<revision>` from `pom.xml` (must end with `-SNAPSHOT`). PR tests are not re-run on that path.

**Consumer docs:** SNAPSHOT repository URL and authentication are **not** published in the README until we choose Maven Central snapshots vs GitHub Packages. Maintainer workflows may still deploy in the meantime.

### Secrets

| Secret | Used for |
|--------|----------|
| `CENTRAL_USERNAME` / `CENTRAL_PASSWORD` | Maven Central deploy |
| `GPG_PRIVATE_KEY` / `GPG_PASSPHRASE` | Artifact signing |
| `PACKAGES_PUBLISH_TOKEN` | GitHub Packages deploy (classic PAT: `write:packages`, `read:packages`, and `repo` if private) |

`GITHUB_TOKEN` alone usually returns **401** for Maven deploy to GitHub Packages; publish workflows fail fast if `PACKAGES_PUBLISH_TOKEN` is missing.

CI sets `GITHUB_PACKAGES_TOKEN` from that secret and passes `-Dgithub.repository=${{ github.repository }}` so the deploy URL always matches the repo running the workflow (not only the default in `pom.xml`). Add `PACKAGES_PUBLISH_TOKEN` on **`phoenixfire-labs/maven-phoenixfire-plugin`** after the org transfer; authorize the classic PAT for the org (**SSO**) if required.

**“Could not find artifact … in github (https://maven.pkg.github.com/phoenixfire-labs/…)”** while the workflow runs on **`BenManifold/...`**: registry URL vs checkout repo mismatch (fixed by the `-Dgithub.repository` override) or a PAT that cannot publish to the org package namespace.

One-time Central namespace setup: [MAVEN-CENTRAL.md](MAVEN-CENTRAL.md).

### Local deploy

GitHub Packages (`github` server in `~/.m2/settings.xml`):

```bash
mvn -B -ntp clean deploy -Pgithub-packages -Dmaven.test.skip=true -Djacoco.skip=true
```

Maven Central (GPG + `central` server; release version only):

```bash
mvn -B -ntp clean deploy -Pcentral -Dmaven.test.skip=true -Djacoco.skip=true
```

`phoenixfire-it` is not deployed (`maven.deploy.skip`).

## Consuming SNAPSHOTs (developers)

Released artifacts: Maven Central (`io.github.phoenixfire-labs`). SNAPSHOT install instructions will be added to README **Installation** when the registry is chosen.
