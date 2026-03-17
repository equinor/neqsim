---
title: "Copilot Automation: AI-Assisted Issue Solving"
description: "Guide to using GitHub Copilot coding agent for automated issue solving, code changes, and engineering simulations via GitHub Actions workflows."
---

# Copilot Automation: AI-Assisted Issue Solving

NeqSim uses two GitHub Actions workflows to automatically assign the
**GitHub Copilot coding agent** to issues. Copilot reads the issue, implements
a solution, opens a PR, and iterates on review feedback — all without manual
intervention.

## Prerequisites

Before using these workflows:

1. **GitHub Copilot Business or Enterprise** plan must be active on the repository
2. **Copilot coding agent** must be enabled: Settings > Copilot > Coding agent > Enable
3. The `copilot-solve` and `copilot-engineering` **labels** must exist in the repository

## Two Workflows, Two Use Cases

| Workflow | Label | Use Case | What Copilot Does |
|----------|-------|----------|-------------------|
| `copilot-solve.yml` | `copilot-solve` | Code changes (bugs, features, tests, refactoring, docs) | Reads issue, edits Java/test/doc files, opens PR |
| `copilot-engineering.yml` | `copilot-engineering` | Engineering tasks (simulations, PVT, process design) | Builds NeqSim, runs Python/Jupyter simulations, creates notebooks, opens PR |

### When to use which

**Use `copilot-solve` when:**
- Fixing a bug in existing Java code
- Adding a new Java class, method, or feature
- Writing or updating JUnit 5 tests
- Refactoring or cleaning up code
- Updating documentation or JavaDoc

**Use `copilot-engineering` when:**
- Running NeqSim process simulations
- Calculating thermodynamic properties
- Sizing equipment (separators, compressors, heat exchangers)
- Creating Jupyter notebook examples
- Any task that requires executing NeqSim

## Quick Start

### Option A: Use an issue template

1. Go to **Issues > New Issue**
2. Choose either:
   - **Copilot task (AI-assisted)** — for code changes
   - **Engineering task (AI-assisted simulation)** — for simulations
3. Fill in the template and submit
4. The label is automatically applied, and Copilot starts working

### Option B: Label an existing issue

1. Open any existing issue
2. Add the label `copilot-solve` or `copilot-engineering`
3. Copilot is assigned automatically and begins working

### Option C: Assign Copilot directly

1. Open any issue
2. Add `copilot` as an assignee
3. Copilot starts working (no label needed, but no status comment is posted)

## The Code Task Workflow (`copilot-solve`)

When an issue is labeled `copilot-solve`, Copilot:

1. **Reads the issue** — understands the task description and acceptance criteria
2. **Reads repo instructions** — follows `.github/copilot-instructions.md` for
   Java 8 constraints, code patterns, and conventions
3. **Searches for context** — finds related classes, existing patterns, and tests
4. **Implements the solution** — creates or modifies Java files, tests, docs
5. **Opens a PR** — with a clear description of what was changed and why

### What the code agent delivers

- Java source changes in `src/main/java/neqsim/`
- JUnit 5 tests in `src/test/java/neqsim/`
- CSV data files in `src/main/resources/`
- Documentation updates (markdown, JavaDoc)
- Build configuration changes

### Constraints enforced

- **Java 8 only** — no `var`, `List.of()`, `String.repeat()`, text blocks, records
- **Complete JavaDoc** — all public/protected/private methods documented
- **Test coverage** — every code change includes tests
- **API verification** — Copilot reads actual source files before using any API

## The Engineering Task Workflow (`copilot-engineering`)

When an issue is labeled `copilot-engineering`, Copilot has terminal access and
follows an extended workflow:

1. **Builds NeqSim from source**:
   ```bash
   ./mvnw package -DskipTests -q
   ```

2. **Installs the devtools Python environment**:
   ```bash
   pip install -e devtools/ matplotlib jupyter nbconvert python-docx
   ```
   This installs `neqsim_dev_setup` — the local JVM bootstrap that loads
   NeqSim directly from the freshly-built JAR in `target/`.

3. **Creates a task folder** using the task-solving script:
   ```bash
   python devtools/new_task.py "TASK TITLE" --type B --author "Copilot"
   ```

4. **Writes scope notes** in `step1_scope_and_research/task_spec.md`

5. **Creates a Jupyter notebook** in `step2_analysis/` with NeqSim simulations
   using the dual-boot setup cell:
   ```python
   try:
       from neqsim_dev_setup import neqsim_init, neqsim_classes
       ns = neqsim_init(recompile=False)
       ns = neqsim_classes(ns)
       NEQSIM_MODE = "devtools"
   except ImportError:
       from neqsim import jneqsim
       NEQSIM_MODE = "pip"
   ```

6. **Runs the notebook** and generates results

7. **Writes Java tests** if new NeqSim classes were added

8. **Copies reusable outputs** (notebooks, tests, docs) to tracked paths:
   ```bash
   cp step2_analysis/*.ipynb examples/notebooks/
   ```

9. **Opens a PR** with the deliverables

### What the engineering agent delivers

- Jupyter notebooks with working NeqSim simulations
- Matplotlib figures with axis labels and units
- Java tests for any new classes
- Documentation updates
- Updated `TASK_LOG.md` entry

### Important notes

- The `task_solve/` folder is **gitignored** — only reusable deliverables
  (notebooks, tests, docs) are committed
- Engineering results are **AI-assisted preliminary estimates** — they require
  review by a qualified engineer before use in design decisions

## Iterating on Feedback

Both workflows support iterating on Copilot's work. There are three feedback
channels:

### 1. PR review comments (automatic)

Copilot natively picks up PR review comments on PRs it opened. Just leave
code review comments or request changes, and Copilot will push updates to
its branch.

### 2. Issue comments with `@copilot` (workflow-triggered)

Comment on the original issue starting with `@copilot`:

```
@copilot The test should also cover the case where pressure is below 10 bara.
Please add a negative test case.
```

The workflow detects the `@copilot` mention, verifies the issue has the right
label, and re-assigns Copilot to re-read all comments and update its PR.

### 3. Remove and re-add the label

If Copilot needs a fresh start:

1. Remove the `copilot-solve` or `copilot-engineering` label
2. Add it back
3. Copilot is re-assigned and starts fresh

### Feedback loop

```
You: Create issue → Label it
                      ↓
              Copilot: Creates PR
                      ↓
You: Review PR → Leave comments
                      ↓
              Copilot: Pushes fixes
                      ↓
You: Approve & Merge (or request more changes)
```

## Writing Good Issues for Copilot

The quality of Copilot's output depends heavily on the issue description.

### Good issue (specific, testable)

> **Title:** Add methane solubility calculation to WaterPhase
>
> **Description:** Add a `getMethanesolubility()` method to
> `SystemSrkCPAstatoil` that calculates dissolved methane in the aqueous
> phase after a TPflash.
>
> **Acceptance criteria:**
> - Method returns mole fraction of methane in aqueous phase
> - Test validates against published data (Duan & Mao 2006)
> - Result within 5% of reference value at 100 bara, 25°C

### Poor issue (vague, no criteria)

> **Title:** Fix water calculations
>
> **Description:** The water phase results don't look right. Please fix.

### Tips for engineering tasks

- **Include fluid composition** with mole fractions
- **Specify conditions** (temperature, pressure, flow rate) with units
- **Give expected ranges** — "hydrate temperature between 15-25°C"
- **Name applicable standards** — "per DNV-OS-F101" or "ASME VIII Div.1"
- **Link related classes** — "similar to `SeparatorTest.java`"

## CI Integration

Copilot's PRs must pass the same CI checks as any other PR:

- **Build**: `./mvnw install` (Java 8 and 11 compatibility)
- **Tests**: `./mvnw test` (all JUnit 5 tests)
- **JavaDoc**: `./mvnw javadoc:javadoc` (documentation completeness)
- **Static analysis**: checkstyle, spotbugs, pmd

If CI fails, Copilot can be asked to fix the issue via a PR review comment
like:

```
@copilot The build failed because String.repeat() is Java 11+.
Use StringUtils.repeat() from Apache Commons instead.
```

## Security Considerations

- Copilot only has access to the repository contents and GitHub API
- It cannot access external systems, databases, or private networks
- All PRs require human approval before merging
- The workflow filters out bot comments to prevent infinite loops
- Copilot follows the repo's security policies and OWASP guidelines

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Copilot not assigned after labeling | Check that coding agent is enabled in repo settings |
| Copilot produces Java 9+ code | Comment on PR: "Use Java 8 syntax — see .github/copilot-instructions.md" |
| Engineering notebook fails to run | Check that `./mvnw package -DskipTests` succeeds first |
| Copilot ignores review comments | Re-assign `copilot` as assignee on the PR |
| Workflow doesn't trigger | Verify the label name matches exactly: `copilot-solve` or `copilot-engineering` |
| `@copilot` comment ignored | Check the issue has the correct label; bot comments are filtered |

## File Reference

| File | Purpose |
|------|---------|
| `.github/workflows/copilot-solve.yml` | Workflow for code-level tasks |
| `.github/workflows/copilot-engineering.yml` | Workflow for engineering simulations |
| `.github/ISSUE_TEMPLATE/copilot_task.md` | Issue template for code tasks |
| `.github/ISSUE_TEMPLATE/engineering_task.md` | Issue template for engineering tasks |
| `.github/copilot-instructions.md` | Repo-wide instructions Copilot reads |
| `.github/agents/solve.task.agent.md` | VS Code agent for local engineering tasks |
| `devtools/new_task.py` | Task folder creation script |
| `devtools/neqsim_dev_setup.py` | Python JVM bootstrap for local builds |
