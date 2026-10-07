# Human tasks (owner only)

Claude Code never does these. Everything else is automated.

## Before or during the first phase (≈ 15 minutes)

None of these block development: Claude Code keeps working while they are pending.

1. **Environment for Claude Code**: a Linux machine with ~25 GB free disk, JDK 21, Android SDK command-line tools, NDK and CMake (Claude Code may install missing ones in user space).
2. **GitHub CLI authenticated** in that environment: `gh auth login` with scopes `repo` and `workflow`.
3. **Repository settings** for `qtekfun/UltimateKeys` (Claude Code creates the repo if it does not exist):
   - Settings → General → Pull Requests: allow squash merge only; "Automatically delete head branches".
   - Branch protection for `master` is added by Claude Code in Phase 0; if its token lacks admin rights, add it yourself: required check `CI / check`, linear history.
4. **Release signing key** — on your own machine, never on the machine Claude Code uses if you can avoid it. Keep the file and passwords backed up: if the key is lost, users have to uninstall to update.
   ```sh
   keytool -genkeypair -v -keystore ultimatekeys-release.jks -alias ultimatekeys \
     -keyalg RSA -keysize 4096 -validity 10000
   base64 -w0 ultimatekeys-release.jks | gh secret set UK_KEYSTORE_BASE64 -R qtekfun/UltimateKeys
   gh secret set UK_KEYSTORE_PASSWORD -R qtekfun/UltimateKeys
   gh secret set UK_KEY_ALIAS -R qtekfun/UltimateKeys --body ultimatekeys
   gh secret set UK_KEY_PASSWORD -R qtekfun/UltimateKeys
   ```
   For F-Droid later, the certificate fingerprint:
   ```sh
   keytool -list -v -keystore ultimatekeys-release.jks -alias ultimatekeys | grep SHA256
   ```

No personal access token is needed: the release workflow uses the default `GITHUB_TOKEN`.

## During the run

- Nothing required. Follow `PROGRESS.md` or the PR list if you want.
- Resolve any issue labeled `blocked`.

## After the run

- Go through `docs/HUMAN_VERIFICATION.md` (typing feel, autocorrect, dictation quality, gesture typing, benchmarks, battery, stability on the Find X8 Pro and a Pixel).
- Submit to F-Droid: open the merge request in `fdroiddata` with `fdroid/com.qtekfun.ultimatekeys.yml` and the certificate fingerprint.
