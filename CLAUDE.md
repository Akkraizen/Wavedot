# CLAUDE.md

@AGENTS.md

## Notes for Claude

- Follow AGENTS.md first. Of its rules, these are the easiest to break: no comments in code or configuration, documentation in English, no AI attribution in commits or pull requests.
- Do not run builds or tests on the owner's machine. Build and test in an isolated environment, then apply the changes to the working tree directly.
- Before editing a module, read its `build.gradle.kts` and the matching section of `docs/ARCHITECTURE.md` to confirm which layer it belongs to.
- When a task touches the client–server contract, update `shared/protocol-*`, both sides, and `docs/PROTOCOL.md` together.
- Finish each change by running the narrowest Gradle test tasks that cover it, for example `./gradlew :client:core:domain:jvmTest` or `./gradlew :server:hub:api:test`.
