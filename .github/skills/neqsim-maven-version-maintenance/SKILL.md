---
name: neqsim-maven-version-maintenance
description: "USE WHEN: updating or reviewing dependency or Maven plugin versions in NeqSim. Keeps pom.xml and pomJava8.xml coordinated, preserves Java 8-compatible pins, and verifies both Maven configurations."
---

# NeqSim Maven Version Maintenance

Use this workflow when changing a library dependency or Maven plugin version in
either project POM. Treat `pom.xml` and `pomJava8.xml` as a coordinated pair;
they build the same project for different runtime baselines and can require
different versions.

## Workflow

1. Inspect the current worktree and both POMs before editing. Preserve unrelated
   user changes. Identify the dependency or plugin coordinates, scope, and
   current version in each file. Do not assume the two files currently match.
2. Establish the intended version from the request or a trustworthy release
   source. For an update scan, query both POMs independently:

   ```bash
   ./mvnw -f pom.xml versions:display-dependency-updates
   ./mvnw -f pom.xml versions:display-plugin-updates
   ./mvnw -f pomJava8.xml versions:display-dependency-updates
   ./mvnw -f pomJava8.xml versions:display-plugin-updates
   ```

   Treat reported versions as candidates, not automatic recommendations. Check
   the target artifact's runtime requirements and release notes, especially
   before changing Java 8 pins. Do not claim Java 8 support from the compiler's
   `source`/`target` settings alone.
3. Make the requested changes in both POMs in one edit. Keep versions equal
   when both runtime baselines support the same release; otherwise use the
   newest verified Java 8-compatible version in `pomJava8.xml` and document the
   reason in a concise XML comment only when that exception is not already
   explained nearby. Keep dependency scopes and plugin configuration intentional.
4. Compare the resulting declarations by Maven coordinates. Check that an add,
   removal, or scope change is reflected in both POMs unless the difference is
   deliberate and explained. Do not modify unrelated plugin, dependency, or
   project versions as part of a narrow update.
5. Resolve and validate each configuration separately:

   ```bash
   ./mvnw -f pom.xml validate
   ./mvnw -f pomJava8.xml validate
   ```

   Then run `test-compile` or the narrowest relevant test under each POM. For
   Java 8 runtime claims, run the Java 8 POM with an actual Java 8 JDK; compiling
   with a newer JDK and `source`/`target` 1.8 does not prove dependency runtime
   compatibility. If the required JDK or external artifact is unavailable,
   report that as an unverified gate instead of implying success.
6. Summarize the coordinates changed, the version chosen in each POM, any
   deliberate divergence, and the exact validation commands and outcomes.

## Scope

- Dependency version reports cover project dependencies; plugin version reports
  cover Maven build plugins. Review each category separately.
- Do not use `versions:use-latest-*` or bulk rewrite goals without reviewing
  every proposed change for Java 8 support and project compatibility.
- Do not update `pomJava8.xml` by copying the modern POM wholesale. Preserve
  its Java 8 dependency pins and Java 8-specific build configuration.