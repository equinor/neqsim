---
name: paperlab_neqsim_api_claim_verification
description: |
  Verify NeqSim API references in PaperLab books against Java source, tests,
  notebooks, and documented setup patterns. Use for code snippets, method
  names, constructor signatures, units, and API claims in book chapters.
---

# PaperLab NeqSim API Claim Verification

## When to Use

USE WHEN: a chapter mentions NeqSim classes, methods, constructors, units,
notebook examples, or API workflows.

Pair with:

- `neqsim_in_writing` for claim markers,
- `neqsim-api-patterns` for current modeling patterns,
- `neqsim-notebook-patterns` for devtools setup,
- `neqsim-java8-rules` for Java snippet compatibility.

## Verification Categories

| Category | Meaning |
|----------|---------|
| valid | source confirms class/method/signature |
| deprecated | API exists but should be avoided |
| wrong-signature | class/method exists but arguments are wrong |
| missing-class | referenced class does not exist |
| missing-method | class exists but method does not |
| unit-risk | method exists but unit convention is unclear or wrong |
| needs-test | documentation example lacks runnable verification |

## Workflow

1. Extract Java and Python code blocks, inline API names, notebook imports, and
   class references from chapter text.
2. Search `src/main/java/neqsim/` for each class and method.
3. Read constructors and overloads before judging snippet correctness.
4. Check for required NeqSim setup patterns: mixing rule, `createDatabase(true)`
   when needed, flash calculation, and `initProperties()` before transport
   property reads.
5. For documentation examples, identify or propose a JUnit or notebook test.
6. Emit minimal fixes with source-backed confidence.

## Output Schema

```json
{
  "chapter": "ch24_computational_tools_neqsim",
  "references": [
    {
      "symbol": "Cooler",
      "kind": "class",
      "status": "valid",
      "source": "src/main/java/neqsim/process/equipment/heatexchanger/Cooler.java",
      "evidence": "constructor Cooler(String, StreamInterface)",
      "recommendation": "none"
    }
  ]
}
```

## Safety Rules

- Do not assume API patterns; verify actual source.
- All Java examples must remain Java 8 compatible.
- Do not rewrite notebook setup to import installed stale NeqSim when devtools
  setup is required.