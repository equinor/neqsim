# User Input Log

This file captures the **verbatim** input the user provided to the LLM/agent
for this task. Its purpose is **reproducibility** — anyone (or any agent)
should be able to read this file and re-run the task with the same starting
information.

> **Rule:** Do not rewrite, summarise, or "clean up" the user's wording.
> Paste it as-is. Add new entries chronologically; never delete previous ones.

---

## 1. Original Request

**Date/Time:** YYYY-MM-DD HH:MM
**Channel:** [VS Code Copilot Chat / CLI / API / other]
**Agent invoked:** [@solve.task / @field.development / ...]

```text
<!-- ORIGINAL_USER_PROMPT -->
[Paste the user's original prompt here, verbatim. Do not paraphrase, summarise,
or "clean up" wording. Include code blocks, file paths, numbers, units, and
typos as given.]
```

### Attachments / referenced files

- [List any files, images, PDFs, links, or pasted tables the user provided]
- [e.g., `references/vendor_datasheet.pdf` — vendor datasheet pasted by user]

---

## 2. Clarifying Questions & Answers

When the agent asks scoping questions (fluid composition, design pressure,
standards, economics, deliverable format, etc.), record both the question
and the user's answer here. One block per question.

### Q1: [question text]

**Asked:** YYYY-MM-DD HH:MM

```text
[verbatim question]
```

**Answer:**

```text
[verbatim user response]
```

### Q2: [question text]

**Asked:** YYYY-MM-DD HH:MM

```text
[verbatim question]
```

**Answer:**

```text
[verbatim user response]
```

---

## 3. Follow-up Instructions

Any additional instructions the user gives during the task (scope changes,
new requirements, corrections, "use 50 bar instead of 60", etc.).

### YYYY-MM-DD HH:MM — [short label]

```text
[verbatim user message]
```

---

## 4. Inferred Assumptions

Where the user did **not** specify a value and the agent had to assume one,
record the assumption and its source. This separates "what the user said"
from "what the agent decided".

| Parameter | Assumed value | Justification / source |
|-----------|---------------|------------------------|
|           |               |                        |

---

## 5. Reproducibility Recipe

To recreate this task from scratch:

```bash
neqsim new-task "[exact title used]" --type [X] --author "[name]" \
    --prompt "[paste section 1 prompt here, or path to a .txt file]"
```

Then feed the questions in section 2 to the agent in the same order with
the recorded answers. Apply the assumptions in section 4 if the user does
not override them.
