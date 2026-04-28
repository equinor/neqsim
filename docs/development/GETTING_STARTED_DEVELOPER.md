---
title: Getting Started as a NeqSim Developer
description: From git clone to first contribution in 30 minutes - environment setup, project structure, common workflows, and contribution paths
---

# Getting Started as a NeqSim Developer

**Goal**: From `git clone` to first contribution in 30 minutes.

---

## Step 1: Environment Setup (5 min)

### Prerequisites

**Required:**
- ☑️ Java 8+ (JDK, not just JRE) - [Download OpenJDK](https://adoptium.net/)
- ☑️ Git - [Download](https://git-scm.com/downloads)

**Recommended:**
- VS Code with [Java Extension Pack](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack)
- Python 3.8+ (for Jupyter notebook development)

**Verify Installation:**
```powershell
java -version    # Should show 1.8.0 or higher
git --version    # Should show 2.x or higher
```

### Clone and Build

```bash
# Clone repository
git clone https://github.com/equinor/neqsim.git
cd neqsim

# First build (downloads dependencies, ~2 min)
./mvnw clean install

# Verify tests pass
./mvnw test
```

**Windows**: Use `.\mvnw.cmd` instead of `./mvnw`

**Troubleshooting**: See [BUILD.md](BUILD.md) for common issues.

---

## Step 2: Understand Project Structure (10 min)

### Read These First (in order)

1. **[CONTEXT.md](../../CONTEXT.md)** - 60-second repo overview (READ THIS FIRST)
2. **[modules.md](../modules.md)** - Package architecture
3. This file - Developer-specific workflows

### Key Directories

```
neqsim/
├── src/main/java/neqsim/       # Production code (your changes go here)
│   ├── thermo/                 # Thermodynamics & EOS
│   ├── process/                # Process equipment & flowsheets
│   ├── pvtsimulation/          # PVT lab tests
│   ├── standards/              # Industry standards (ISO, API)
│   └── util/                   # Utilities
│
├── src/test/java/neqsim/       # Unit tests (mirrors main/)
│   ├── thermo/
│   ├── process/
│   └── ...
│
├── src/main/resources/         # Data files, component databases
├── docs/                       # Documentation (markdown)
├── examples/                   # Jupyter notebooks, Java examples
├── .github/agents/             # Copilot Chat agents
├── devtools/                   # Development utilities
└── task_solve/                 # Your local work area (gitignored)
```

### Package Organization

Production code follows domain-driven design:

| Package | Purpose | Key Classes |
|---------|---------|-------------|
| `neqsim.thermo` | Thermodynamics | SystemSrkEos, SystemPrEos, ComponentInterface |
| `neqsim.thermodynamicoperations` | Flash calculations | ThermodynamicOperations, TPflash, PHflash |
| `neqsim.physicalproperties` | Transport properties | Density, viscosity, thermal conductivity |
| `neqsim.process` | Process equipment | Separator, Compressor, HeatExchanger, Pipeline |
| `neqsim.process.processmodel` | Flowsheets | ProcessSystem (the flowsheet orchestrator) |
| `neqsim.pvtsimulation` | PVT lab tests | CME, CVD, DifferentialLiberation |
| `neqsim.standards` | Industry standards | ISO6976, SalesContract, GasQuality |

**Rule**: Tests mirror production structure. If you edit `neqsim.process.equipment.separator.Separator`, add tests to `neqsim.process.equipment.separator.SeparatorTest`.

---

## Step 3: Pick Your Path

Choose the contribution type that fits your goal:

### A. Fix a Bug or Add a Feature

**Best for**: Specific issues you've encountered or features you need.

1. **Find or create an issue**:
   - Browse [GitHub Issues](https://github.com/equinor/neqsim/issues)
   - Comment "I'll take this" to claim an issue
   - Or create a new issue describing the bug/feature

2. **Create a branch**:
   ```bash
   git checkout -b fix/issue-123-separator-bug
   # or
   git checkout -b feature/add-co2-compressor
   ```

3. **Make changes**:
   - Edit code in `src/main/java/neqsim/`
   - Follow [CODE_PATTERNS.md](CODE_PATTERNS.md) for examples
   - Add JavaDoc (required for all public methods)

4. **Add tests**:
   - Create/update test in `src/test/java/neqsim/` (mirror structure)
   - Extend `NeqSimTest` base class
   - Use descriptive test method names

5. **Verify locally**:
   ```bash
   ./mvnw test -Dtest=YourTest
   ./mvnw checkstyle:check spotbugs:check pmd:check
   ```

6. **Submit PR**:
   - Use [pull request template](../../.github/pull_request_template.md)
   - Link to issue: "Fixes #123"
   - Describe what changed and why

**Example PR checklist**:
- [ ] Code compiles with Java 8
- [ ] Tests added and passing
- [ ] Static analysis passes (checkstyle, spotbugs, pmd)
- [ ] JavaDoc added for public methods
- [ ] TASK_LOG.md updated (if applicable)

---

### B. Solve an Engineering Task

**Best for**: Answering engineering questions or developing new capabilities.

NeqSim has an **AI-assisted workflow** for solving engineering tasks:

#### Fast Path: Use AI Agent
```
# In VS Code Copilot Chat:
@solve.task JT cooling for rich gas at 100 bara
```

The agent creates a task folder, researches the topic, builds a simulation, validates results, and generates a Word report.

#### Manual Alternative
```bash
neqsim new-task "Your task description" --type B --intake-pause always
```

The intake pause creates the folder first, then lets you edit
`study_config.yaml`, add details to `user_input.md`, or place document inputs
such as PDFs, Excel stream tables, P&IDs, and data sheets in
`step1_scope_and_research/references/` before notebooks are created.

Then follow the workflow in `task_solve/<your_task>/README.md`:
- **Step 1**: Scope & Research (standards, methods, deliverables)
- **Step 2**: Analysis & Evaluation (build simulation, validate)
- **Step 3**: Report (generate Word + HTML deliverables)

**After completing**:
- Copy reusable outputs to main repo:
  - Notebooks → `examples/notebooks/`
  - Tests → `src/test/java/neqsim/`
  - Docs → `docs/`
- Add entry to `docs/development/TASK_LOG.md`
- Create PR with promoted files

**See**: [TASK_SOLVING_GUIDE.md](TASK_SOLVING_GUIDE.md) for complete workflow.

---

### C. Write Documentation

**Best for**: Filling gaps, fixing errors, or improving clarity.

1. **Find documentation to improve**:
   - Browse [docs/](../)
   - Look for "TODO", missing sections, or outdated content
   - Check [BROKEN_API_AUDIT_REPORT.md](../BROKEN_API_AUDIT_REPORT.md) for known issues

2. **Use AI assistance** (optional):
   ```
   @documentation Create a guide for TEG dehydration calculations
   ```

3. **Follow conventions**:
   - Add Jekyll YAML front matter to all markdown files:
     ```yaml
     ---
     title: Your Document Title
     description: Brief description with searchable keywords
     ---
     ```
   - Use proper markdown formatting (see [documentation agent](../../.github/agents/documentation.agent.md))
   - Add KaTeX for equations: `$...$` for inline, `$$...$$` for display
   - Create links to other docs: `[text](relative/path.md)`

4. **Update indexes**:
   - Add entry to [REFERENCE_MANUAL_INDEX.md](../REFERENCE_MANUAL_INDEX.md)
   - Update relevant section's `index.md`

5. **Verify links**:
   ```bash
   # Check for broken links
   python docs/check_links.py
   ```

**Example documentation PR**:
- Fixes wrong API signatures in existing docs
- Adds missing guide for new feature
- Updates index files

---

### D. Add Examples

**Best for**: Demonstrating capabilities to help others learn.

#### Jupyter Notebooks

1. **Create notebook**:
   - Use AI agent: `@notebook.example 3-stage compression with intercooling`
   - Or manually create in `examples/notebooks/`

2. **Follow structure**:
   - Introduction cell (what it demonstrates)
   - Setup cell (imports using `jneqsim` gateway)
   - Fluid creation
   - Process building
   - Run simulation
   - Results (formatted table/plots)
   - Discussion/conclusions

3. **Import pattern** (CRITICAL):
   ```python
   # Use this pattern for all notebooks:
   from neqsim import jneqsim

   SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
   ProcessSystem = jneqsim.process.processmodel.ProcessSystem
   Stream = jneqsim.process.equipment.stream.Stream
   ```

4. **Test notebook**:
   - Restart kernel & run all cells
   - Verify runtime < 5 minutes (or mark as "long-running")

5. **Add to index**:
   - Update `examples/README.md`
   - Add brief description

#### Java Examples

1. **Create standalone example** in `examples/neqsim/`
2. **Make it self-contained**: Include all required code
3. **Add comments**: Explain each step
4. **Test compilation**: `javac YourExample.java`

**See**: [notebook.example.agent.md](../../.github/agents/notebook.example.agent.md) for guidelines.

### E. Contribute a Skill

Skills are the **easiest** way to contribute — no Java required. You write a markdown
file with domain knowledge, code patterns, and common mistakes that AI agents use to
solve engineering tasks.

**Core skill** (shipped with NeqSim):
```bash
neqsim new-skill "my-topic"      # scaffold SKILL.md
# Edit .github/skills/neqsim-my-topic/SKILL.md
# Register in README + copilot-instructions.md
# Submit PR
```

**Community skill** (hosted in your own repo):
```bash
# 1. Create SKILL.md in your GitHub repo
# 2. Publish to the catalog:
neqsim skill publish your-username/your-repo
```

**See**: [Skills Guide](../integration/skills_guide.md) for the full walkthrough
including SKILL.md format, requirements checklist, and private/team skills.

---

## Step 4: Code Style and Validation

### Automatic Formatting (VS Code)

If you followed the setup in [CONTRIBUTING.md](../../CONTRIBUTING.md), code formats automatically on save.

**Manual format**:
- Right-click in editor → **Format Document**
- Or: `Shift+Alt+F` (Windows), `Shift+Option+F` (Mac)

### Before Committing

Run these checks locally:

```bash
# 1. Compile
./mvnw compile

# 2. Run tests
./mvnw test

# 3. Static analysis
./mvnw checkstyle:check spotbugs:check pmd:check

# 4. If you modified docs, regenerate manual
cd docs
python generate_manual.py
```

**Fix any issues** before pushing. CI runs the same checks.

### Java 8 Compatibility (CRITICAL)

**All code MUST compile with Java 8.** Never use:
- `var` → Use explicit types
- `List.of()` → Use `Arrays.asList()`
- `String.repeat()` → Use `StringUtils.repeat()`
- Text blocks `"""..."""` → Regular strings with `\n`

See [copilot-instructions.md](../../.github/copilot-instructions.md#java-8-compatibility) for complete list.

---

## Step 5: Submit Your Contribution

### Create Pull Request

1. **Push your branch**:
   ```bash
   git push -u origin your-branch-name
   ```

2. **Create PR** (choose one):
   - GitHub UI: Go to repo → **Pull Requests** → **New pull request**
   - GitHub CLI: `gh pr create`

3. **Fill the template**:
   - What changed (concise summary)
   - Why (link to issue if applicable)
   - Testing performed
   - Docs updated (checkbox)
   - Breaking changes (checkbox)

4. **Wait for CI checks**:
   - Build must pass
   - All tests must pass (100%)
   - Static analysis must pass

5. **Respond to reviews**:
   - Address comments
   - Push new commits (CI re-runs automatically)

6. **Merge**:
   - After approval, maintainer merges (usually squash & merge)

### PR Best Practices

✅ **DO**:
- Write descriptive commit messages
- Keep PRs focused (one feature/fix per PR)
- Add tests for new functionality
- Update documentation
- Respond to feedback promptly

❌ **DON'T**:
- Mix unrelated changes
- Submit without testing locally
- Ignore CI failures
- Leave merge conflicts unresolved

---

## Common Workflows

### Update Your Branch with Latest Master

```bash
# Fetch latest changes
git fetch origin

# Rebase on master (recommended)
git rebase origin/master

# Or merge (alternative)
git merge origin/master
```

**If conflicts**: Resolve manually, then:
```bash
git rebase --continue    # If rebasing
# or
git commit               # If merging
```

### Run Specific Test

```bash
# Single test class
./mvnw test -Dtest=SeparatorTest

# Single test method
./mvnw test -Dtest=SeparatorTest#testTwoPhase

# All tests in a package
./mvnw test -Dtest=neqsim.thermo.**
```

### Debug in IDE

#### VS Code
- Set breakpoints in code
- Press **F5** (or **Debug** → **Start Debugging**)
- Configuration in `.vscode/launch.json`

#### IntelliJ IDEA
- Right-click test method → **Debug 'testMethod()'**

#### Eclipse
- Right-click test class → **Debug As** → **JUnit Test**

### Build and Update Python Package

After Java changes, rebuild and copy to Python:

```powershell
# Windows
.\mvnw.cmd package -DskipTests
Copy-Item target\neqsim-3.5.0.jar $env:APPDATA\Python\Python312\site-packages\neqsim\lib\java11\ -Force

# Restart Jupyter kernel to reload
```

### Clean Workspace

```bash
# Remove all generated files
./mvnw clean

# Remove IDE metadata (if needed)
rm -rf .vscode .idea .project .classpath

# Regenerate IDE files
./mvnw eclipse:eclipse    # Eclipse
# For VS Code/IntelliJ, just re-import as Maven project
```

---

## Getting Help

### Documentation
- **User docs**: [https://equinor.github.io/neqsim/](https://equinor.github.io/neqsim/)
- **JavaDoc**: [API Reference](https://htmlpreview.github.io/?https://github.com/equinor/neqsimhome/blob/master/javadoc/site/apidocs/index.html)
- **Reference manual**: [REFERENCE_MANUAL_INDEX.md](../REFERENCE_MANUAL_INDEX.md)

### Community
- **Questions**: [GitHub Discussions](https://github.com/equinor/neqsim/discussions)
- **Bug reports**: [GitHub Issues](https://github.com/equinor/neqsim/issues)
- **Code review**: Tag `@equinor/neqsim-maintainers` in PR

### Internal Resources (Equinor)
- **Academic support**: [NTNU contact](https://www.ntnu.edu/employees/even.solbraa)
- **Slack**: #neqsim channel (if you have access)

---

## Next Steps

Once you're comfortable with the basics:

### Deepen Understanding
- **[Code Patterns](CODE_PATTERNS.md)** - Copy-paste starters for common tasks
- **[Task Log](TASK_LOG.md)** - See what problems have been solved before
- **[AI Agents](../../.github/agents/)** - Automate repetitive tasks

### Advanced Topics
- **Custom EOS**: Add new equation of state models
- **New unit operations**: Extend process equipment
- **Performance optimization**: Profile and optimize solvers
- **Integration**: Connect NeqSim to other tools (MATLAB, Excel, etc.)

### Contribute to the Project
- **Triage issues**: Help label and prioritize issues
- **Review PRs**: Provide feedback on other contributions
- **Documentation**: Write tutorials and guides
- **Outreach**: Present NeqSim at conferences or write blog posts

---

## Quick Reference Card

### Essential Commands
```bash
# Build
./mvnw clean install

# Test
./mvnw test -Dtest=ClassName#methodName

# Format check
./mvnw checkstyle:check

# Create task
neqsim new-task "Task description" --intake-pause always

# Update branch
git fetch origin && git rebase origin/master
```

### File Locations
- Production code: `src/main/java/neqsim/`
- Tests: `src/test/java/neqsim/`
- Documentation: `docs/`
- Examples: `examples/`
- Your work: `task_solve/`

### Key Files
- `CONTEXT.md` - Quick orientation
- `CONTRIBUTING.md` - Contribution guidelines
- `pom.xml` - Build configuration
- `.github/pull_request_template.md` - PR template

---

**Ready to contribute?** Pick a path above and dive in! 🚀
