---
name: neqsim-version-bump-decision
description: "USE WHEN: deciding whether a NeqSim project version request should be a patch or minor release. Classifies fixes, refactors, and additive features; checks Java serialization compatibility. Major-version decisions remain manual."
last_verified: "2026-10-26"
---

# NeqSim Version Bump Decision

## Scope

Use this skill for the NeqSim project's own release version, not dependency or
Maven plugin versions. For dependency/plugin upgrades, use
[`neqsim-maven-version-maintenance`](../neqsim-maven-version-maintenance/SKILL.md).
This skill recommends patch or minor. It does not decide or apply a major
version, create a GitHub release, or publish artifacts.

## Decision Rules

| Change | Recommendation | Conditions |
|---|---|---|
| Bug fix, refactor, or performance correction | Patch | No public API/capability addition or incompatible API change; serialization gate below passes. |
| New backward-compatible capability or public API | Minor | Existing APIs and serialized state remain compatible. |
| Removed or incompatible public API, incompatible serialized state, or uncertain compatibility | Manual major review | Do not recommend or apply a patch/minor bump as a workaround. A human decides the major version. |

Use SemVer increments from the current released version. For example, from
`3.25.0`, a patch candidate is `3.25.1` and a minor candidate is `3.26.0`.
Do not infer or choose a major version; that decision remains manual. A patch
release does not satisfy the minor-or-major deprecation period in the
[API lifecycle policy](../../../docs/development/api-lifecycle.md).

## Serialization Compatibility Gate

Patch recommendations require evidence that serialized objects from supported
previous releases remain readable and retain their intended state after loading.
Apply this gate to every affected `Serializable` class and its serialized object
graph:

- Preserve an explicit `serialVersionUID`. If the class relies on a computed
  UID, compare it with the previous released version; do not assume a
  source-level refactor leaves it unchanged.
- Check serialized field names, types, and meanings, plus any custom
  `readObject`, `writeObject`, `readResolve`, `writeReplace`, or
  `Externalizable` behavior.
- A new serialized field is acceptable only when old streams load correctly,
  its default state is valid, and compatibility is verified. A renamed or
  retyped field, changed UID, or changed interpretation that prevents correct
  restoration fails the patch gate.
- Prefer a fixture serialized by a supported released version and test loading
  it with the candidate code. A same-version round trip alone does not prove
  cross-version compatibility. Test candidate-to-old-reader compatibility too
  when that is part of the persistence contract.

If there is no suitable prior-release fixture or other defensible compatibility
evidence, report the gate as **unverified** and do not recommend patch. Request
manual review; do not invent fixtures or claim compatibility from a matching
UID alone.

## Procedure

1. Read the requested change and its diff. Identify the current released
   project version and confirm the core revision in `pom.xml`, `pomJava8.xml`,
   and `neqsim-mcp-server/pom.xml`. The MCP server's own artifact version is
   separate; do not change it as part of a NeqSim core version bump.
2. Classify the change by the decision table. A bug fix or refactor that also
   adds a new public capability is minor, not patch.
3. For a patch candidate, verify public API compatibility and complete the
   serialization compatibility gate. For a minor candidate, verify that
   existing APIs and previously serialized state remain usable.
4. If either compatibility check fails, or evidence is insufficient, stop at
   **manual review required**. Do not apply a patch or minor version to avoid
   confronting an incompatible change.
5. Report the recommendation, target version, change classification, API and
   serialization evidence, tests run, and unresolved risks. If the user asks
   to apply the recommended patch/minor version, update the coordinated project
   core revision in all three POMs and validate the core, Java 8, and MCP Maven
   configurations. Do not publish a release.

## Expected Result

Return one of:

- **PATCH** with the target patch version and passing compatibility evidence.
- **MINOR** with the target minor version and a backward-compatible additive
  change summary.
- **MANUAL MAJOR REVIEW** with the incompatible or unverified contract and why
  the skill cannot choose the major version.