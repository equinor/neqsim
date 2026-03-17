---
name: Copilot task (AI-assisted)
about: Create an issue for Copilot coding agent to solve (code changes, bug fixes, features, tests, docs)
title: ''
labels: copilot-solve
assignees: ''
---

<!--
  WHAT THE CODING AGENT CAN DO:
    ✅ Bug fixes and code changes in Java source files
    ✅ New features (classes, methods, equipment, models)
    ✅ Unit tests (JUnit 5)
    ✅ Refactoring and code cleanup
    ✅ Documentation updates (markdown, JavaDoc)
    ✅ CSV data files, resource files

  WHAT IT CANNOT DO (use VS Code "solve engineering task" agent instead):
    ❌ Run NeqSim simulations or Jupyter notebooks
    ❌ Generate figures or results.json
    ❌ Create task_solve/ folders with full engineering reports
    ❌ Execute Maven builds to verify locally
-->

## Task Description

<!-- Describe what needs to be done. Be specific about the expected behavior. -->



## Task Type

<!-- Check one: -->
- [ ] Bug fix
- [ ] New feature / class
- [ ] Unit tests
- [ ] Refactoring
- [ ] Documentation

## Context

<!-- Which part of the codebase is affected? Link to relevant files or classes. -->

- **Package/area:** <!-- e.g., neqsim.process.equipment.separator, neqsim.thermo.system -->
- **Related files:** <!-- e.g., src/main/java/neqsim/process/equipment/separator/Separator.java -->

## Acceptance Criteria

<!-- List specific requirements. The coding agent will use these to verify its work. -->

- [ ]
- [ ]
- [ ] All code compiles with Java 8 (no var, List.of, String.repeat, etc.)
- [ ] Unit tests pass (`./mvnw test -Dtest=RelevantTest`)
- [ ] JavaDoc is complete for new/modified methods
- [ ] TASK_LOG.md updated if this adds reusable patterns

## Additional Context

<!-- Standards, references, example code, similar existing classes, or links to related issues/PRs. -->


