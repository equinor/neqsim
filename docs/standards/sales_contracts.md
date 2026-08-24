---
title: "Gas Sales Contract Checks"
description: "Run NeqSim's database-backed gas-quality contract checks, interpret the 12-column result table, and apply explicit project limits safely."
---

The `neqsim.standards.salescontract` package runs gas-quality calculations selected
from NeqSim's bundled `GASCONTRACTSPECIFICATIONS.csv` data. It is a legacy,
database-backed screening facility. It is not a general contract authoring API,
does not establish that a product complies with a current commercial agreement,
and does not replace representative sampling, validated composition analysis, or
accountable contract review.

## Current workflow and boundary

`BaseContract(SystemInterface, terminal, country)` selects rows whose `TERMINAL`
and `COUNTRY` exactly match the supplied strings. Each row names a calculation
method, parameter key, limits, unit, and reference conditions. `runCheck()` calls
the selected standards and fills a 12-column string table.

Treat terminal and country as trusted lookup keys for the bundled data, not as
free-form user input. The current source builds its database query by string
concatenation. Also verify the selected rows in the repository before use: the
file is example data shipped with NeqSim, not a maintained register of current
pipeline, terminal, national, or contractual limits.

The two empty-construction routes are not complete custom-contract workflows:

- `new BaseContract()` contains zero specifications.
- `new BaseContract(system)` adds an internal water-dew-point object but does not
  update the specification count used by `runCheck()`.
- `BaseContract` and `ContractInterface` expose no `addSpecification` or removal
  method. Constructing a `ContractSpecification` does not attach it to a contract.

Use the database-backed constructor for the current public workflow. Implementing
a supported programmatic contract builder requires a separate production-API
change with validation and compatibility review.

## Complete Java example

This fixture uses the exact case-sensitive `central` / `Brazil` keys present in
the bundled data. It keeps calculated values and limits separate so the caller,
rather than `isOnSpec()`, owns the explicit row-by-row decision.

```java
import neqsim.standards.salescontract.BaseContract;
import neqsim.thermo.system.SystemGERGwaterEos;
import neqsim.thermo.system.SystemInterface;

SystemInterface gas = new SystemGERGwaterEos(268.15, 20.0);
gas.addComponent("methane", 0.90);
gas.addComponent("ethane", 0.04);
gas.addComponent("propane", 0.02);
gas.addComponent("n-heptane", 0.00012);
gas.addComponent("H2S", 0.000012);
gas.addComponent("water", 0.000071);
gas.addComponent("oxygen", 0.0012);
gas.addComponent("CO2", 0.022);
gas.addComponent("nitrogen", 0.022);
gas.setMixingRule(8);
gas.init(0);

BaseContract contract = new BaseContract(gas, "central", "Brazil");
contract.runCheck();

String[][] rows = contract.getResultTable();
int specificationCount = contract.getSpecificationsNumber();
int rowsWithinLimits = 0;
for (int rowIndex = 0; rowIndex < specificationCount; rowIndex++) {
  double value = Double.parseDouble(rows[rowIndex][1]);
  double minimum = Double.parseDouble(rows[rowIndex][4]);
  double maximum = Double.parseDouble(rows[rowIndex][5]);
  if (Double.isFinite(value) && value >= minimum && value <= maximum) {
    rowsWithinLimits++;
  }
}
```

For this repository fixture, `specificationCount` is 8 and the second row is
`CO2`, approximately 2.188 mol%, with stored limits of 0 to 3 mol%. Those values
are regression evidence for the bundled example, not a recommended sales-gas
specification.

## Result-table contract

After `runCheck()`, each row has exactly these columns:

| Index | Meaning |
| ---: | --- |
| 0 | Parameter key passed to the standard's `getValue(...)` |
| 1 | Calculated value, serialized as a string |
| 2 | Country lookup value |
| 3 | Terminal lookup value |
| 4 | Stored minimum |
| 5 | Stored maximum |
| 6 | Unit passed to `getValue(...)` |
| 7 | Standard name |
| 8 | Measurement reference temperature |
| 9 | Combustion reference temperature |
| 10 | Reference pressure in bara |
| 11 | Comments |

There is no pass/fail column. Compare the parsed value with columns 4 and 5
explicitly, then apply measurement uncertainty, rounding rules, exception policy,
and any contractual hierarchy outside this class. Do not infer compliance from a
row's presence.

`runCheck()` invokes each standard's `isOnSpec()` only for a diagnostic console
line; it does not store that boolean in the result table. The standards do not
share one generic min/max implementation, and some calculation-only standards
return `true` unconditionally. Therefore `isOnSpec()` is not a substitute for the
explicit comparison above.

## Bundled method keys

The current `BaseContract.getMethod(...)` recognizes these exact keys:

| Data key | Calculation class |
| --- | --- |
| `ISO18453` | `Draft_ISO18453` |
| `ISO6974` or `oxygen` | `Standard_ISO6974` |
| `Total sulphur` | `GasChromotograpyhBase` |
| `ISO6976` | `Standard_ISO6976` |
| `SulfurSpecificationMethod` | `SulfurSpecificationMethod` |
| `BestPracticeHydrocarbonDewPoint` | `BestPracticeHydrocarbonDewPoint` |
| `UKspecifications` | `UKspecifications_ICF_SI` |

Unknown keys return `null`. One bundled Norway row currently contains
`StatoilBestPracticeHydrocarbonDewPoint`, which is not one of the recognized
keys. Do not treat that data set as a validated complete contract until its
method mapping is repaired and requalified.

## Database-loading limitations

- The CSV has one `ReferenceTdegC` field. The loader copies it into both the
  measurement and combustion reference-temperature columns.
- Although the CSV has a `Comments` field, the current loader supplies an empty
  string to `ContractSpecification`; column 11 is therefore empty for loaded
  rows.
- The constructor does not assign terminal or country to `contractName`.
- Loading stops at the first exception, logs a generic error, and retains only
  rows added before that exception.
- `display()` opens Swing and is unsuitable for headless services.
  `prettyPrint()` and the current `runCheck()` path write to standard output;
  consume `getResultTable()` for structured integration and account for the
  remaining console side effect.
- The contract and its standards are mutable. Do not share one instance across
  concurrent calculations.

## Safe reporting checklist

1. Freeze the governing contract revision and its authoritative source outside
   NeqSim.
2. Verify the exact terminal/country rows and method keys before calculation.
3. Record composition basis, NeqSim version, class, reference temperatures,
   reference pressure, unit, and standard edition for every result.
4. Parse and validate all numeric cells; fail closed on missing, non-finite, or
   unsupported values.
5. Apply min/max limits, uncertainty, rounding, and waiver rules in a
   version-controlled project layer.
6. Compare important results with certified measurements or another qualified
   method before fiscal or contractual use.

## Related documentation

- [Standards package overview](README.md)
- [ISO 6976 calorific values and Wobbe index](iso6976_calorific_values.md)
- [Water and hydrocarbon dew-point methods](dew_point_standards.md)
- [ISO 15403 CNG quality](iso15403_cng_quality.md)

