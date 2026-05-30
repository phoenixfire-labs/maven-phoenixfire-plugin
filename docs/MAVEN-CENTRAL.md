# Publishing to Maven Central

Artifacts use **`io.github.benmanifold`** so Sonatype can verify ownership via your GitHub account
(no custom domain required).

## One-time setup

### 1. Central Portal account and namespace

1. Sign in at [central.sonatype.com](https://central.sonatype.com) with the **same GitHub user**
   that owns `BenManifold/maven-phoenixfire-plugin`.
2. Open **Namespaces**. You should see **`io.github.benmanifold`** auto-provisioned after GitHub login.
3. If not: **Add namespace** → `io.github.benmanifold` → verify with the temporary public repo
   Sonatype requests (name = verification key), then delete that repo.

### 2. User token (CI credentials)

1. Central Portal → **Account** → **Generate User Token**.
2. Add repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|--------|--------|
| `CENTRAL_USERNAME` | Token username from the portal |
| `CENTRAL_PASSWORD` | Token password from the portal |

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
| `GPG_PASSPHRASE` | Passphrase for that key |

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
  <groupId>io.github.benmanifold</groupId>
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

Maven **`groupId`** is `io.github.benmanifold`. Java packages remain `io.phoenixfire.*` (internal API);
consumers only need the plugin `groupId` / `artifactId` above.
