---
name: neqsim-java8-rules
description: "Java 8 compatibility rules for NeqSim. USE WHEN: writing or reviewing any Java code for NeqSim, including tests. Covers forbidden Java 9+ features, replacement patterns, API verification, and JavaDoc requirements. All NeqSim Java code MUST compile with Java 8."
---

# Java 8 Compatibility Rules for NeqSim

All NeqSim Java code — including test classes in `src/test/java/` — **MUST** compile with Java 8. The CI build will FAIL if you use Java 9+ features.

## Forbidden Java 9+ Features

| Forbidden | Java 8 Alternative |
|-----------|-------------------|
| `var x = ...` | Explicit type: `String x = ...`, `Map<String, Object> map = ...` |
| `List.of(a, b)` | `Arrays.asList(a, b)` or `Collections.singletonList(a)` |
| `Set.of(a, b)` | `new HashSet<>(Arrays.asList(a, b))` |
| `Map.of(k, v)` | `Collections.singletonMap(k, v)` or `new HashMap<>()` |
| `"str".repeat(n)` | `StringUtils.repeat("str", n)` (Apache Commons) |
| `str.isBlank()` | `str.trim().isEmpty()` |
| `str.strip()` | `str.trim()` |
| `str.lines()` | `str.split("\\R")` or BufferedReader |
| `Optional.isEmpty()` | `!optional.isPresent()` |
| Text blocks `"""..."""` | Regular strings with `\n` |
| Records | Regular class with fields, constructor, getters |
| Pattern matching `instanceof` | Traditional `instanceof` + cast |
| `Stream.toList()` | `.collect(Collectors.toList())` |

## Common `var` Replacements

```java
// WRONG (Java 10+):
var map = someMethod.toMap();
var list = getItems();
var result = calculate();

// CORRECT (Java 8):
Map<String, Object> map = someMethod.toMap();
List<String> list = getItems();
CalculationResult result = calculate();
```

## Required Import for String Repeat

```java
import org.apache.commons.lang3.StringUtils;
// Usage: StringUtils.repeat("=", 70)
```

## API Verification (MANDATORY)

Before using any NeqSim class in code or examples:

1. **Search** for the class: `file_search("**/ClassName.java")`
2. **Read** constructor and method signatures from the actual source
3. **Use only methods that actually exist** with correct parameter types
4. Do NOT assume convenience overloads — check first

Common API mistakes:
- Assuming `getXxx95()` when actual is `getXxx(int percentile)`
- Assuming 1-arg constructors when 2+ args are required
- Calling methods on wrong class
- Assuming `calculate()` when actual is `calculateRisk()` or `run()`

## JavaDoc Requirements

All classes and methods (public, protected, AND private) require complete JavaDoc:
- Class-level: description, `@author`, `@version`
- Method-level: description, `@param` for every parameter, `@return` for non-void, `@throws` for each exception
- HTML5 compatible: use `<caption>` in tables (no `summary` attribute)
- No `@see` with plain text — only valid Java references
- No lambda arrows (`->`) in JavaDoc code examples

## Build Commands

```bash
./mvnw install                            # full build
./mvnw test -Dtest=ClassName              # single test class
./mvnw test -Dtest=ClassName#methodName   # single method
./mvnw checkstyle:check spotbugs:check pmd:check  # static analysis
./mvnw javadoc:javadoc                    # verify JavaDoc
```
