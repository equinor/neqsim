---
title: "Task Log"
description: "Chronological record of engineering tasks solved in the NeqSim repo. Searchable by keywords, task type, and equipment. Provides memory across sessions."
---

# Task Log

> **Purpose:** Persistent memory across sessions. Every solved task gets an entry here
> so future sessions can find prior solutions instead of starting from scratch.
>
> **How to use:** Search this file for keywords before starting a new task.
> If a similar task was solved before, start from that solution.

## Entry Format

```
### YYYY-MM-DD — Short task title
**Type:** A (Property) | B (Process) | C (PVT) | D (Standards) | E (Feature) | F (Design)
**Keywords:** comma, separated, search, terms
**Solution:** Where the code lives (test file, notebook, source file)
**Notes:** Key decisions, gotchas, or results worth remembering
```

---

## Log

<!-- Add new entries at the top. Most recent first. -->

### 2026-03-01 — Task log and context system created
**Type:** E (Feature)
**Keywords:** context, documentation, workflow, onboarding, task-solving
**Solution:** `CONTEXT.md`, `docs/development/TASK_SOLVING_GUIDE.md`, `docs/development/TASK_LOG.md`
**Notes:** Created a 3-file context system to make repo-based task solving faster:
- `CONTEXT.md` — 60-second orientation (repo map, patterns, constraints)
- `TASK_SOLVING_GUIDE.md` — workflow for classifying and solving tasks
- `TASK_LOG.md` — this file, persistent memory across sessions

---

<!-- 
TEMPLATE — copy this block for each new entry:

### YYYY-MM-DD — Title
**Type:** 
**Keywords:** 
**Solution:** 
**Notes:** 

-->
