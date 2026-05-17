# NeqSim Improvement Proposals (NIPs)

This file documents gaps found in NeqSim during the task and proposes concrete
implementations. Each NIP is a development ticket that can be picked up.

**If no gaps were found, write:** "No NeqSim gaps identified for this task."

---

<!--
For each gap found in the capability assessment (analysis.md Section 3),
copy the template below and fill it in:

### NIP-01: [Short Title]

**Gap:** [What NeqSim cannot do today]
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
