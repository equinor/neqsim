---
name: neqsim-code-hygiene
version: "1.0.0"
description: "Fix formatting, Checkstyle, Spotless, and JavaDoc build failures in NeqSim Java code. USE WHEN: the build fails on spotless:check, checkstyle:check, or javadoc:javadoc; a PR shows formatting/import-order/JavaDoc violations; or you edited any .java file and need to make it CI-clean. Covers the exact recurring errors (JavadocParagraph <p>, orphan </p> after lists, single-line @param/@return, import ordering, HTML5 tables, unused variables) and the fix-and-verify loop."
last_verified: "2026-09-18"
---

# NeqSim Code Hygiene: Formatting, Checkstyle, Spotless & JavaDoc Fixes

This skill is the fix-it playbook for the code-quality gates that fail NeqSim CI:
`spotless:check` (formatting), `checkstyle:check` (style, imports, JavaDoc structure),
and `javadoc:javadoc` (HTML5-valid JavaDoc). It maps each recurring error message to
its exact cause and fix, and gives the mandatory verify loop. For the separate rule
set on which Java **language features** are allowed, load `neqsim-java8-rules`.

## When to Use This Skill

- The build fails on `spotless:check`, `checkstyle:check`, or `javadoc:javadoc`.
- A PR or the Problems panel shows formatting, import-order, or JavaDoc violations.
- You created or edited ANY `.java` file (main, test, or examples) and must make it CI-clean.
- You pasted a JavaDoc/error block and asked "fix errors like this".

## The Fix-and-Verify Loop (do this every time)

1. **Format first** — Spotless fixes whitespace/indentation but NOT import order or JavaDoc content:
   ```bash
   ./mvnw spotless:apply
   ```
2. **Fix Checkstyle/JavaDoc by hand** — imports, `<p>` tags, single-line JavaDoc, tables (below).
3. **Verify each gate** on the touched files, then the whole set:
   ```bash
   ./mvnw spotless:check
   ./mvnw checkstyle:check
   ./mvnw javadoc:javadoc
   ```
4. **Re-run `spotless:apply`** after hand edits (JavaDoc edits can change wrapping), then `git add`.
5. NEVER bypass with `git commit --no-verify`.

> Order matters: hand-fix JavaDoc/imports, then `spotless:apply` last so the committed
> file is both style-correct and formatter-clean.

## Spotless

- AI-generated Java is **not** auto-formatted; a single unformatted file fails the whole CI build.
- Profile: Eclipse `.config/neqsim_formatter.xml` (pom.xml), applied to `src/main/java` and `src/test/java`.
- `spotless:apply` fixes: indentation (2 spaces), trailing whitespace, blank lines,
  brace placement, line wrapping of long method chains and string concatenations.
- `spotless:apply` does NOT fix: import ordering, JavaDoc content/structure, unused variables.
- Convenience wrapper also available: `python devtools/run_spotless.py` / `devtools/run_spotless.sh`.

## Checkstyle: Import Ordering

Config: `.config/checkstyle_neqsim.xml` (Google style + project overrides). Imports must be
**alphabetical by full path**, grouped in this order with the groups appearing top-to-bottom:

```java
import com.google.gson.JsonObject;      // com.*
import java.util.HashMap;               // java.*  (HashMap before HashSet before List...)
import java.util.HashSet;
import java.util.List;
import neqsim.thermo.system.SystemInterface;  // neqsim.*
import org.apache.logging.log4j.LogManager;   // org.*  (goes last)
import org.apache.logging.log4j.Logger;
```

Rule: sort strictly by the full import string. `java.util.HashMap` sorts before
`java.util.HashSet`; `org.*` comes after `neqsim.*`. No blank lines inside a group
unless the project's config requires group separation — match the surrounding file.

## Checkstyle: JavadocParagraph (`<p>` errors)

The `JavadocParagraph` rule requires that **every `<p>` tag is preceded by a blank JavaDoc line**
(a lone ` *`). This is the most common failure.

```java
// WRONG — <p> immediately after a heading/text line:
/**
 * <h2>Hardy Cross Method</h2>
 * <p>Balances loop flows iteratively...</p>
 */

// CORRECT — blank ` *` line before <p>:
/**
 * <h2>Hardy Cross Method</h2>
 *
 * <p>Balances loop flows iteratively...
 */
```

Notes:
- Do NOT self-close or add a closing `</p>` — JavaDoc treats `<p>` as an opening separator.
- The blank line rule applies before **every** `<p>`, including ones after `<h2>`, `<pre>`, `</ul>`.

## JavaDoc: Orphan `</p>` After a List (`javadoc:javadoc` HTML error)

Starting a `<ul>` or `<ol>` implicitly closes the current paragraph. A `</p>` after the
list has no matching `<p>` and breaks HTML5 JavaDoc ("unexpected end tag: </p>").

```java
// WRONG — orphan </p> after the list closes:
/**
 * <p>Supported modes:
 * <ul>
 *   <li>Sequential</li>
 *   <li>Newton-Raphson</li>
 * </ul>
 * </p>
 */

// CORRECT — drop the trailing </p>:
/**
 * <p>Supported modes:
 *
 * <ul>
 *   <li>Sequential</li>
 *   <li>Newton-Raphson</li>
 * </ul>
 */
```

Find them fast across a file/tree:
```bash
grep -rnE '^\s*\*\s*</p>' src/main/java src/test/java
```

## Checkstyle: Single-Line `@param` / `@return` JavaDoc

A single-line JavaDoc like `/** @return the pump speed */` triggers THREE violations:
missing summary sentence, not-multiline, and tag-not-preceded-by-blank-line. Expand it:

```java
// WRONG:
/** @return the pump speed in rpm */
public double getPumpSpeed() { ... }

// CORRECT — summary sentence, blank line, then the tag:
/**
 * Returns the pump speed.
 *
 * @return the pump speed in rpm
 */
public double getPumpSpeed() { ... }
```

Same shape for setters/params:
```java
/**
 * Sets the pump speed.
 *
 * @param speed the pump speed in rpm
 */
public void setPumpSpeed(double speed) { ... }
```

## JavaDoc: HTML5 Tables

```java
// WRONG — deprecated summary attribute, no caption:
/**
 * <table summary="modes">
 * <tr><th>Mode</th></tr>
 * </table>
 */

// CORRECT — <caption> element, no summary attribute:
/**
 * <table>
 * <caption>Solver modes</caption>
 * <tr><th>Mode</th></tr>
 * </table>
 */
```

## JavaDoc: Other Recurring Errors

| Error | Cause | Fix |
|-------|-------|-----|
| `reference not found` | `@see IEC 61508` (plain text) | Move the standard into the description text; `@see` only takes `ClassName`/`#method` |
| `bad use of '>'` | Lambda `->` or `>` in a JavaDoc code example | Use `&gt;`, or rewrite the lambda as an anonymous class |
| `no @param for X` / `no @return` / `no @throws for X` | Missing tag (applies to private methods too) | Add the tag; every `throws` clause needs a matching `@throws` |
| `semicolon missing` / `malformed HTML` | Unbalanced tag in JavaDoc | Check tag nesting; lists close the paragraph implicitly |

## Unused Variables / Fields (compiler warnings, not Checkstyle failures)

`get_errors` may report unused locals/fields after cleanup. They do NOT fail the CI gates
but are worth clearing:
- Unused **local** with a side-effect-free initializer (e.g. `double viscosity = ...;`) → delete it.
- Unused **field** that is part of a data model (has no getter/setter yet) → leave it unless
  the owner confirms; deleting model state can break serialization or future use.

## Deprecated Method / Constructor Calls (`[deprecation]` warnings)

Calling an API marked `@Deprecated` produces a `[deprecation]` compiler warning
(`uses or overrides a deprecated API`). NeqSim treats these as debt to pay down: replace
the call with the successor named in the member's `@deprecated` JavaDoc.

**Find the successor — never guess.** Read the deprecated member's JavaDoc; the
`@deprecated` tag states the replacement:

```java
/**
 * @deprecated use {@link #getFluid()} instead
 */
@Deprecated
public SystemInterface getThermoSystem() { ... }
```

**Migrate the call site** (do NOT change the deprecated method's body):

```java
// WRONG — deprecated call:
SystemInterface sys = stream.getThermoSystem();

// CORRECT — successor from the @deprecated note:
SystemInterface sys = stream.getFluid();
```

Find deprecated usages across the tree:
```bash
# every call site the compiler warned about
./mvnw -q compile 2>&1 | grep -i deprecat
# or search for a specific retired method by name
grep -rn "getThermoSystem(" src/main/java src/test/java
```

Rules and cautions:
- Prefer `vscode_listCodeUsages` / the rename provider to migrate every call site of a
  retired member consistently, rather than editing one file at a time.
- Verify the successor's **signature and semantics** (units, return type, side effects)
  match — a deprecation replacement is not always a drop-in; check the JavaDoc.
- If the `@deprecated` tag gives no replacement, or the successor changes behaviour, do
  NOT blind-swap it — leave it and flag it for the owner.
- Do NOT delete the deprecated member itself or strip its `@Deprecated` annotation; other
  callers (and downstream users) may still depend on it. This skill fixes *call sites*.
- Overriding a deprecated method also warns — annotate the override `@Deprecated` too, or
  re-target it to the successor.
- After migrating, re-run `./mvnw -q compile` and confirm the `[deprecation]` warning for
  that member is gone, then run the full verify loop.

## Common Mistakes

| Mistake | Fix |
|---------|-----|
| Running only `spotless:check` and expecting it to fix files | Run `spotless:apply` to actually reformat |
| Expecting Spotless to sort imports or fix JavaDoc | Do those by hand; Spotless only touches formatting |
| Adding `</p>` to "close" a `<p>` | JavaDoc `<p>` is a separator — never add `</p>` |
| Leaving `</p>` after `</ul>`/`</ol>` | Remove it — the list already closed the paragraph |
| Committing with `--no-verify` to skip the gate | Never bypass; fix the violation |
| Hand-indenting long method chains | Let `spotless:apply` wrap them |
| Guessing a deprecated method's replacement | Read the `@deprecated` JavaDoc tag for the named successor |
| Deleting a deprecated member to clear a warning | Fix the *call site*; other callers may still need the member |

## Validation Checklist

- [ ] `./mvnw spotless:apply` run, files re-`git add`ed
- [ ] `./mvnw spotless:check` passes
- [ ] `./mvnw checkstyle:check` passes (imports sorted, `<p>` preceded by blank line, no single-line JavaDoc)
- [ ] `./mvnw javadoc:javadoc` passes (no orphan `</p>`, tables have `<caption>`, no plain-text `@see`)
- [ ] Every method with a `throws` clause has a matching `@throws`
- [ ] No `[deprecation]` warnings — call sites migrated to the successor named in the `@deprecated` tag
- [ ] Code still compiles with Java 8 (see `neqsim-java8-rules`)

## References

- `.config/checkstyle_neqsim.xml` — Checkstyle rules (Google style + overrides)
- `.config/neqsim_formatter.xml` — Eclipse formatter profile used by Spotless
- `neqsim-java8-rules` skill — forbidden Java 9+ features and JavaDoc requirements
- `AGENTS.md` / `.github/copilot-instructions.md` — Spotless and JavaDoc mandates
