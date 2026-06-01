# Publishing to Maven Central

Artifacts use **`io.github.phoenixfire-labs`** so Sonatype can verify ownership via the
[phoenixfire-labs](https://github.com/phoenixfire-labs) GitHub organization (no custom domain required).

## One-time setup

### 1. Central Portal account and namespace

1. Sign in at [central.sonatype.com](https://central.sonatype.com) with a GitHub user that is an
   **owner** of the `phoenixfire-labs` organization.
2. Open **Namespaces**. Add or confirm **`io.github.phoenixfire-labs`** (Sonatype maps the org login
   `phoenixfire-labs` to this `groupId`).
3. Complete verification when prompted (often a temporary public repo under the org whose name matches
   the verification key); delete that repo after approval.
4. Ensure the canonical plugin repository is **`phoenixfire-labs/maven-phoenixfire-plugin`** (public).

### 2. User token (CI credentials)

1. Central Portal → **Account** → **Generate User Token**.
2. Add repository (or organization) secrets on **`phoenixfire-labs/maven-phoenixfire-plugin`**
   (Settings → Secrets and variables → Actions):

| Secret | Value |
|--------|--------|
| `CENTRAL_USERNAME` | **Username** from Central Portal → Account → Generate User Token (not your GitHub login) |
| `CENTRAL_PASSWORD` | **Password** from that same token pair (not your GitHub password) |

Secrets must live on the repo that runs the workflow (`phoenixfire-labs/maven-phoenixfire-plugin`).
CI copies them into env vars `CENTRAL_USERNAME` / `CENTRAL_PASSWORD` for Maven; `setup-java` must receive those **names**, not `${{ secrets.* }}` as the `server-username` / `server-password` inputs.

### 3. GPG signing key

Maven Central requires signed artifacts.

```bash
gpg --full-generate-key
# RSA, 4096 bits, name/email you are comfortable publishing
gpg --list-secret-keys --keyid-format LONG
# export armored private key for GitHub secret GPG_PRIVATE_KEY:
gpg --armor --export-secret-keys YOUR_KEY_ID
```

Add secrets:

| Secret | Value |
|--------|--------|
| `GPG_PRIVATE_KEY` | Full armored private key block |
| `GPG_PASSPHRASE` | Passphrase for that key (no trailing newline) |

CI maps `GPG_PASSPHRASE` → environment variable `MAVEN_GPG_PASSPHRASE` for `setup-java` and
`maven-gpg-plugin`. The workflow input `gpg-passphrase` must be that variable **name**, not the secret value.

Upload the **public** key to a keyserver (Central docs recommend keys.openpgp.org):

```bash
gpg --armor --export YOUR_KEY_ID | curl -fsSL --upload-file - https://keys.openpgp.org/vks/YOUR_KEY_ID
```

### 4. First release

1. Ensure `main` has the version you want to ship (or let CI set it from the tag).
2. Create GitHub release **`v0.1.0`** (or push tag `v0.1.0`).
3. Workflow **Publish to Maven Central** runs `mvn deploy -Pcentral -Prun-its` and waits until
   artifacts are **published** on Central.
4. Consumers add only the plugin coordinate (no extra repository):

```xml
<plugin>
  <groupId>io.github.phoenixfire-labs</groupId>
  <artifactId>phoenixfire-maven-plugin</artifactId>
  <version>0.1.0</version>
</plugin>
```

## Local dry run (optional)

`~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>TOKEN_USERNAME</username>
      <password>TOKEN_PASSWORD</password>
    </server>
  </servers>
</settings>
```

Release version (no `-SNAPSHOT`):

```bash
mvn -B -ntp clean deploy -Pcentral -Prun-its
```

## Modules published

| Artifact | Purpose |
|----------|---------|
| `phoenixfire-api` | SPI / model |
| `phoenixfire-fork-runner` | Fork worker |
| `phoenixfire-core` | Engine |
| `phoenixfire-maven-plugin` | **Consumer-facing** Maven plugin |

`phoenixfire-it` is not deployed (`maven.deploy.skip`).

## Java packages vs Maven coordinates

Maven **`groupId`** is `io.github.phoenixfire-labs`. Java packages remain `io.phoenixfire.*` (internal API);
consumers only need the plugin `groupId` / `artifactId` above.
