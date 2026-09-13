# Tooling Improvements (NeqSim, agents, skills)

Every task is also a test of the tooling. If solving this task required a
workaround, a rediscovery, or a trial-and-error loop that a NeqSim class, an
agent, or a skill should have handled, that is a defect in the tooling and it is
fixed **as part of this task** - not deferred.

Two sections, both mandatory:

1. **Delivered** - what was actually changed during this task. Copy each row into
   `results.json` under `improvements` so it reaches the report and the gate.
2. **Proposed (NIPs)** - gaps found but not closed here, written as pickup-ready
   tickets.

**If nothing needed changing, write:** "No tooling gaps identified for this task."
An empty file is a gate failure, not a pass.

---

## 1. Delivered in this task

| # | Target | What was wrong | What was changed | Evidence |
|---|--------|----------------|------------------|----------|
| 1 | neqsim / agent / skill | the gap that cost time | the concrete change | test name, commit, or file path |

Targets:

- **neqsim** - Java in `src/main/java/neqsim/`, test in `src/test/java/neqsim/`,
  `mvnw spotless:apply` run, test green. Raise the PR before closing the task.
- **agent** - `.github/agents/*.agent.md`, or the community/enterprise agent repos.
- **skill** - `.github/skills/*/SKILL.md`, or the community/enterprise skill repos.
- **cooperation** - a hand-off, cross-reference, or composition between two of the
  above that was missing or wrong.

Keep enterprise/site-specific detail in the enterprise repos; keep community
content plant-agnostic.

---

## 2. Proposed (not closed here)

<!--
For each gap found in the capability assessment (analysis.md Section 3),
copy the template below and fill it in:

### NIP-01: [Short Title]

**Target:** neqsim / agent / skill
**Gap:** [What the tooling cannot do today]
**Impact on task:** [How this limited the current analysis]
**Priority:** Critical / High / Medium / Low
**Workaround used:** [Python workaround or manual calculation, if any]

#### Proposed Implementation

**Package:** `neqsim.[package]`
**Class name:** `ProposedClassName`
**Extends:** `[BaseClass]`

**Key methods:**

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `calculate()` | — | void | Main calculation |
| `getResult()` | String unit | double | Primary output |

**Governing equations:**

$$ [key equation] $$

**Standards implemented:** [e.g., API 520 Section 4.3]

**Test case sketch:**
```java
@Test
void testBasicCase() {
    // Setup
    // Execute
    // Assert physical result within tolerance
}
```

**Estimated complexity:** Small (1-2 days) / Medium (3-5 days) / Large (1-2 weeks)

#### Why Java Implementation Is Better Than Python Workaround
- [Reason 1]
- [Reason 2]

---
-->
