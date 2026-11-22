# NeqSim — repository map

Use [AGENTS.md](AGENTS.md) for contributor constraints. This page is a short map;
API details, examples and rules live in their owning guides. Verify signatures in
current source before changing code or assembling a model.

## Choose an entry point

| Task | Entry point |
|---|---|
| Discover existing functionality | `@capability-scout`; [discovery guide](docs/development/AGENT_CAPABILITY_DISCOVERY.md) |
| Solve an engineering study and prepare a report | `@solve-task`; [task workflow](docs/development/TASK_SOLVING_GUIDE.md) |
| Build a reusable process model | `@solve-process` or `@process-model` |
| Model reservoir-to-products production/injection systems | `@network-energy-systems`; `neqsim-production-system-modeling` |
| Assess maintainability and plan a bounded cleanup | `@code-health`; [maintenance guide](docs/development/CODE_HEALTH.md) |
| Refactor behind stable APIs | `@refactor`; `neqsim-safe-refactoring` |
| Independently review source code | `@code-review`; `neqsim-code-review` |
| Review an engineering task deliverable | `@review` |
| Find all current agents and skills | [agent catalog](.github/agents/README.md), [skills](.github/skills/README.md), [generated map](docs/development/AGENT_SKILL_MAP.md) |

For task output locations, use `neqsim --show-task-root` and the task workflow.
Core, community, enterprise and local-private capabilities remain distinct; see
[the integration guide](docs/integration/skills_guide.md). Load only relevant skills.

## Find the implementation

| Capability | Source location |
|---|---|
| EOS systems, phases, components and mixing rules | `src/main/java/neqsim/thermo/` |
| Flash, stability and phase-envelope operations | `src/main/java/neqsim/thermodynamicoperations/` |
| Density, viscosity, conductivity and interfacial tension | `src/main/java/neqsim/physicalproperties/` |
| Equipment and process execution | `src/main/java/neqsim/process/equipment/`, `process/processmodel/` |
| String-addressed process automation | `src/main/java/neqsim/process/automation/` |
| Persisted model state and version comparisons | `src/main/java/neqsim/process/processmodel/lifecycle/` |
| Equipment/utility/SURF design and cost | `src/main/java/neqsim/process/mechanicaldesign/` |
| Energy networks and power generation | `src/main/java/neqsim/process/equipment/energy/`, `equipment/powergeneration/` |
| Wells, subsea and pipeline networks | `src/main/java/neqsim/process/equipment/well/`, `equipment/subsea/`, `equipment/pipeline/` |
| DEXPI import/export and engineering diagrams | `src/main/java/neqsim/process/processmodel/dexpi/` |
| PVT experiments and fitting | `src/main/java/neqsim/pvtsimulation/`, `statistics/` |
| Reactions and gas-quality standards | `src/main/java/neqsim/chemicalreactions/`, `standards/` |
| Hydraulic solvers | `src/main/java/neqsim/fluidmechanics/` |
| Component and design data | `src/main/resources/data/`, `src/main/resources/designdata/` |
| Tests | `src/test/java/neqsim/` mirrors production packages |
| CLI, discovery, validators and reports | `devtools/` |
| User documentation and executable examples | `docs/REFERENCE_MANUAL_INDEX.md`, `examples/notebooks/` |

## Build and validation

[BUILD.md](docs/development/BUILD.md) owns build commands and Java-profile details.
The project version is in [pom.xml](pom.xml); Java 8 source compatibility remains
required by [AGENTS.md](AGENTS.md), with the separate `pomJava8.xml` CI profile.
Formatting configuration is in `.config/`, and the current PR checks are listed in
[the workflow catalog](.github/WORKFLOWS.md).

Use [CODE_PATTERNS.md](docs/development/CODE_PATTERNS.md) for implementation starters,
[the API lifecycle policy](docs/development/api-lifecycle.md) for compatibility,
[model specifications](docs/development/model_spec_harness.md) for numerical evidence,
and [paired process benchmarks](docs/development/steady_state_performance_benchmark.md)
for hot-path changes. Discovery and test references are not engineering qualification.

For Python source-workspace execution use `devtools/neqsim_dev_setup.py` and the
selected interpreter, as specified in AGENTS.md. Instructions for a clean notebook
runtime live in [the notebook workflow](docs/development/jupyter_development_workflow.md).

## Deeper context

- [Contributing](CONTRIBUTING.md) and [governance](GOVERNANCE.md)
- [Developer setup](docs/development/DEVELOPER_SETUP.md) and [module map](docs/modules.md)
- [Full agent reference](docs/development/AGENT_REFERENCE.md): search the needed section
- [Engineering capability coverage](docs/development/ENGINEERING_CAPABILITY_COVERAGE.md)
- [Community and enterprise integration](docs/integration/enterprise_agent_skill_repos.md)
- [API change history](CHANGELOG_AGENT_NOTES.md): search rather than loading the whole file
