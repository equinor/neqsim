# Contributing

When contributing to this repository, please first discuss the change you wish to make via issue,
email, or any other method with the owners of this repository before making a change.

Please note we have a code of conduct, please follow it in all your interactions with the project.

## Getting Started

**New contributor?** Set up your environment with the onboarding wizard:

```bash
pip install -e devtools/    # one-time: registers the `neqsim` command
neqsim onboard             # interactive setup — Java, Maven, build, Python, agents
neqsim onboard --check     # non-interactive diagnostic
neqsim doctor              # detailed environment health check
```

See [VISION_AGENTS.md](VISION_AGENTS.md) for what belongs in the core repo vs. community contributions.

## Pull Request Process

1. Ensure any install or build dependencies are removed before the end of the layer when doing a
   build.
2. Update the README.md with details of changes to the interface, this includes new environment
   variables, exposed ports, useful file locations and container parameters.
3. Increase the version numbers in any examples files and the README.md to the new version that this
   Pull Request would represent. The versioning scheme we use is [SemVer](http://semver.org/).
4. You may merge the Pull Request in once you have the sign-off of two other developers, or if you
   do not have permission to do that, you may request the second reviewer to merge it for you.

## AI-Assisted Contributions Welcome

NeqSim embraces AI-assisted development. PRs generated with the help of AI coding agents
(GitHub Copilot, Claude Code, Cursor, Windsurf, Codex, etc.) are first-class contributions.

**When submitting an AI-assisted PR:**

1. **Mark it** — add `[AI-Assisted]` to the PR title or note it in the description.
2. **Note testing level** — state whether tests were run locally (`mvnw test`),
   which specific tests passed, and whether static analysis was checked.
3. **Confirm understanding** — briefly confirm you understand what the code does
   and why the change is appropriate, even if AI generated the initial draft.
4. **Include prompts (optional)** — if you used a specific prompt or agent workflow
   that produced the result, sharing it helps reviewers and future contributors.

**What makes a great AI-assisted PR:**

- A focused change (one issue/topic per PR, same as any contribution)
- Java 8 compatible code (see `AGENTS.md` for forbidden Java 9+ features)
- Passing tests (`mvnw.cmd test` on Windows, `./mvnw test` on Linux/Mac)
- Physical/engineering correctness verified (not just "compiles and runs")

**Skill and agent contributions are especially welcome.** You don't need to write Java
to contribute — domain experts can submit:

- **Skills** (`.github/skills/*/SKILL.md`) — domain workflows, best practices, code patterns
- **Agents** (`.github/agents/*.agent.md`) — specialist agent definitions
- **Notebooks** (`examples/notebooks/`) — validated simulation examples
- **Community skills** — host in your own repo, one command to publish:

```bash
neqsim new-skill "skill-name"                          # scaffold a core skill
neqsim install-skill publish user/neqsim-my-skill      # publish a community skill
```

See [.github/skills/README.md](.github/skills/README.md) for the full skill contribution guide
and [community-skills.yaml](community-skills.yaml) for the community catalog.

## Style guideline

This project uses Google Java style formatting rules.

### Visual Studio Code:

Install extensions [Language Support for Java(TM) by Red Hat](https://marketplace.visualstudio.com/items?itemName=redhat.java) and [Checkstyle for Java
](https://marketplace.visualstudio.com/items?itemName=shengchen.vscode-checkstyle) and add the following to settings.json.
 ```
  "java.configuration.updateBuildConfiguration": "interactive",
  "[java]": {
    "editor.defaultFormatter": "redhat.java",
    "editor.formatOnSave": true,
    "editor.tabSize": 2,
    "editor.insertSpaces": true,
    "editor.detectIndentation": false
  },
  "java.format.settings.url": "https://raw.githubusercontent.com/google/styleguide/gh-pages/eclipse-java-google-style.xml",
  "java.saveActions.organizeImports": true,
  "java.checkstyle.version": "10.18.1",
  "java.checkstyle.configuration": "${workspaceFolder}/.config/checkstyle_neqsim.xml",
  "java.debug.settings.hotCodeReplace": "never",
  "[xml]": {
    "editor.tabSize": 4,
    "editor.insertSpaces": false
  },
  "[json]": {
    "editor.tabSize": 4,
    "editor.insertSpaces": true
  }
```

Note: workspace/project specific settings are located in folder .vscode.

### Eclipse:

Follow the instructions in http://www.practicesofmastery.com/post/eclipse-google-java-style-guide/

## Static analysis

Checkstyle, SpotBugs, and PMD run as part of the Maven build to catch formatting and common coding issues. Before submitting a pull request, verify your changes with:

```bash
./mvnw checkstyle:check spotbugs:check pmd:check
```

These checks are configured not to fail the build by default, but contributions should address any reported problems.

## Project structure and package layout

Most contributions will need to add production code, automated tests, and possibly supporting resources. The high-level layout is:

* **Production code:** place Java sources under `src/main/java` following the existing package hierarchy (for example, `com.equinor.neqsim.thermo` or `com.equinor.neqsim.processsimulation`). Each directory boundary should map cleanly to a cohesive module (e.g., `thermo`, `physicalproperties`, `processsimulation`).
* **Automated tests:** place JUnit tests under `src/test/java`, mirroring the package of the code under test. Keep small test fixtures in `src/test/resources` alongside the relevant package path when possible.
* **Shared resources:** non-test resources that ship with the library belong in `src/main/resources`. Larger sample data or notebooks should go to `data/`, `examples/`, or `notebooks/` depending on audience and usage.

### Package naming and boundaries

* Use the `com.equinor.neqsim` root, followed by the functional area. For example: `com.equinor.neqsim.thermo` for thermodynamics routines, `com.equinor.neqsim.processsimulation` for unit operations and flowsheets, and `com.equinor.neqsim.physicalproperties` for transport properties.
* Avoid creating deep or overlapping packages when an existing boundary fits. Prefer adding to an established module (`thermo`, `physicalproperties`, `processsimulation`, `chemicalreactions`, `parameterfitting`) instead of inventing a parallel hierarchy.
* Keep utilities that are reused across modules in clearly named subpackages such as `com.equinor.neqsim.util.*` so that domain packages remain focused.
* Examples, demos, and notebooks should stay out of the production package tree. Use `examples/` for runnable samples and `notebooks/` for exploratory work.

For a concise overview of where to place new files, see [docs/contributing-structure.md](docs/contributing-structure.md).
