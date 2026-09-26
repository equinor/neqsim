# NeqSim — Copilot Instructions

All agent rules are in [AGENTS.md](../AGENTS.md) at the repository root. VS Code loads it
automatically; if it is not already in your context, read it once. Detail is in
[docs/development/AGENT_REFERENCE.md](../docs/development/AGENT_REFERENCE.md) (search it; never
read it whole). Repeated below for tools that read only this file (e.g. code review):

- **Java 8 only, tests included:** no `var`, `List.of/Set.of/Map.of`, `String.repeat`
  (use `StringUtils.repeat`), `isBlank/strip/lines`, text blocks, records, pattern-matching
  `instanceof`, `Optional.isEmpty()`.
- **Logging:** Log4j2 logger with parameterized calls; never `System.out/err.println`.
- **Formatting:** run `mvnw spotless:apply` after editing any `.java` file; CI runs `spotless:check`.
- **JavaDoc** on every class and method (private too): `@param`, `@return`, `@throws`;
  HTML5 tables need `<caption>`; `@see` only with Java references; no orphan `</p>` after lists.
- **Serializable** classes: non-serializable fields must be `transient`.
- **Verify** constructor and method signatures before using an API; doc snippets need a passing test.
- After a flash, call `fluid.initProperties()` before reading transport properties; always set a mixing rule.
