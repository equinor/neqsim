# Modified-NRTL selector and pure-request compatibility

`baseline-probe.csv` records the unmodified production evaluator on master
`f1982968aac7dd2bf02340fe13a97635462e624f`, using OpenJDK17.0.20+8 on Linux.
It is a numerical regression fingerprint, not literature data, fitted parameters
or empirical accuracy evidence. `HuronVidalRowPureRequestTest` reconstructs the
complete fixture and asserts the pure-function request budget independently of
elapsed time. The production evaluator and regression preserve every baseline request count at
both initialization levels; it does not implement the held row-cache proposal.

The fixture covers SRK/PR, five/fifteen components, initialization1/3 and all
CLASSICAL, explicitly selected CO2/water HV, and all-HV cell selection. The
selected HV entries have zero synthetic coefficients; they do not represent a
calibrated light-gas/polar source profile. Every component is evaluated, yielding
240 records and3960 gamma, log-gamma, temperature-derivative and composition-
derivative values. The dynamic proxies delegate to actual EOS components and
count `aT`, `diffaT` and `diffdiffaT` calls. They do not supply alternate energies.
The test source declares the full component amounts, temperature, pressure,
phase construction, matrices, ordering and requested initialization levels.

All pure-energy requests, derivatives and reduction ordering are unchanged.
No new workspace or retained cache is introduced. All-HV evaluation must make
no pure-energy requests. Row-cache prototypes reduced requests but were held
for measured timing regressions; their separate source/raw receipts accompany
the performance protocol. Existing fixed-temperature
alpha mutation, serialization, clone/concurrency, changed-state and native
thermodynamic consistency tests remain separate acceptance gates.

Stored floating-point values are compared at a fixed absolute/relative scale of
`1e-12 * max(1, abs(expected))` to permit Java8/platform math variation; nonfinite
values are rejected. Request counts, metadata, roster and ordering are exact.
The local baseline/candidate probe additionally requires identical hexadecimal
floating-point representations across the entire fingerprint. This portability
band does not replace the existing independent finite-difference or native
ThermodynamicModelTest tolerances.

Bootstrap Maven with the work-with-neqsim helper before running:

```bash
./mvnw -q -Dtest=HuronVidalRowPureRequestTest test
./mvnw -q '-Dtest=HuronVidal*Test,ComponentGENRTLmodifiedHV*Test' test
```

These synthetic numerical fixtures do not qualify a parameter/alpha model,
inhibitor partition, ice, brine, hydrates or a continuous operating range.

## Raw-selector compatibility

`baseline-selector-probe.csv` retains40 additional records/240 numeric values
from the same unmodified production baseline. It uses a synthetic equal-mole
water/methane forced-phase fixture at350K/10bara, SRK/PR, init1/3, two evaluated
components and five selector forms: canonical HV, padded HV, canonical Classic,
padded Classic and distinct String objects containing HV. All coefficients are
zero numerical fixtures, not calibrated interaction parameters.

The historical primary reduction trims selector strings; the nested reduction
uses an exact comparison. The records preserve those historical values and do
not assert that a padded matrix is physically equivalent to its normalized
form. A workspace eligibility scan must follow the nested reduction's exact
decision. A rejected allocation-placement prototype incorrectly trimmed in
that scan and threw NullPointerException on padded HV at init3; unmodified
master returns all40 finite records. The regression checks their complete
gamma/log-gamma/T/T2/composition values with the same fixed1e-12 portability
band. The separate local probe also requires hex-bit equality.

Both historical selector comparisons remain unchanged in production. The
content-comparison shortcut was also held after its separate timing pilot.
Directed coefficients and physical activation are unchanged.
