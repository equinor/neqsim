# neqsim.mcp.runners — Stateless Calculation Engines

Framework-agnostic runner classes that perform NeqSim calculations from JSON input.
Every class is stateless with only static methods — no instantiation needed.

## Classes

### FlashRunner

Thermodynamic flash (phase equilibrium) calculations.

```java
// String-based API
String result = FlashRunner.run(jsonInput);

// Typed API
ApiEnvelope<FlashResult> result = FlashRunner.runTyped(flashRequest);
```

**9 flash types:** TP, PH, PS, TV, dewPointT, dewPointP, bubblePointT, bubblePointP, hydrateTP
**6 EOS models:** SRK, PR, CPA, GERG2008, PCSAFT, UMRPRU
**Pressure units:** bara, barg, Pa, kPa, MPa, psi, atm
**Temperature units:** C, K, F

Output includes per-phase: density, viscosity, thermal conductivity, Cp, Cv,
Z-factor, molar mass, speed of sound, JT coefficient, and component mole fractions.

### ProcessRunner

Process flowsheet simulation.

```java
// String-based API
String result = ProcessRunner.run(processJson);

// With pre-validation
String result = ProcessRunner.validateAndRun(processJson);

// Typed API
ApiEnvelope<ProcessResult> result = ProcessRunner.runTyped(processJson);
```

**11 equipment types:** Stream, Separator, Compressor, Cooler, Heater, Valve,
Mixer, Splitter, HeatExchanger, DistillationColumn, Pipe

Delegates to `ProcessSystem.fromJsonAndRun()` which uses `JsonProcessBuilder`
for two-pass construction: create all units, then wire inlet/outlet connections.

### Validator

Pre-flight input validation. Auto-detects flash vs. process JSON.

```java
String result = Validator.validate(jsonInput);
```

Returns `{"valid": true/false, "issues": [...]}` with error codes and fix suggestions.

### ComponentQuery

Component database search and fuzzy matching.

```java
// Search (partial match)
String json = ComponentQuery.search("meth");  // → methane, methanol, ...

// Exact match
boolean exists = ComponentQuery.exists("methane");  // → true

// Fuzzy suggestions for typos
List<String> suggestions = ComponentQuery.suggest("metane");  // → ["methane"]

// All component names
List<String> all = ComponentQuery.getAllNames();  // → 100+ components
```

## Test Coverage

6 test classes in `src/test/java/neqsim/mcp/runners/`:

| Test | Checks |
|---|---|
| `FlashRunnerTest` | All flash types, all EOS, error cases, unit parsing |
| `FlashRunnerTypedTest` | Typed API, FlashRequest builder, FlashResult accessors |
| `ProcessRunnerTest` | Equipment types, multi-unit trains, error handling |
| `ProcessRunnerTypedTest` | Typed API, ProcessResult, report parsing |
| `ValidatorTest` | All 12+ validation codes, edge cases, multi-error |
| `ComponentQueryTest` | Search, exists, suggest, empty/null input |
