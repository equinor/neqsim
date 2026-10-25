# CO2 hydrate equilibrium in KCl brines: diagnostic assessment

`co2_kcl_hydrate_dholabhai1993.csv` contains the four K10 and four K15
three-phase equilibrium points from Dholabhai, Kalogerakis and Bishnoi (1993),
*J. Chem. Eng. Data* **38**, 650–654,
[doi:10.1021/je00012a045](https://doi.org/10.1021/je00012a045), Tables I and III.
The recipe values are 10.02 and 14.97 mass % KCl on the reported wet
water-plus-salt basis. Pressure is retained in MPa and is absolute: the paper
corrected the gauge reading by atmospheric pressure. Temperature is retained
in kelvin. The paper reports an RTD accuracy of +/-0.06 K and gauge accuracy of
+/-15 kPa; these instrument accuracies are not treated as a combined endpoint
uncertainty.

The numerical values were manually transcribed and visually checked against a
user-supplied ACS article on 2026-10-06. That article is copyrighted. This
repository contains only the eight factual values required to reproduce the
identified model discrepancy; no PDF page, figure, or substantial text is
redistributed.

## Assessment and diagnostic contract

`CO2KClHydrateAccuracyAssessmentTest` evaluates the eight points with
`SystemElectrolyteCPAstatoil`, mixing rule 10, one kilogram initial water and
10 mol excess CO2. It does not fit a parameter or assert that the experimental
temperatures meet a tolerance. The generated
`target/co2-kcl-hydrate-dholabhai1993-assessment.csv` keeps numerical and
experimental acceptance separate and reports:

- calculated temperature and signed error (`calculated - measured`);
- phase topology, saturated-CO2 classification, hydrate residual, fluid
  fugacity residual, component balance and charge residual;
- the aqueous water-activity proxy `x_water * gamma_water`;
- dissolved aqueous CO2 mole fraction and CO2-rich-phase guest fugacity; and
- aqueous- and hydrate-water fugacities at the calculated boundary.

For the lowest and highest pressure of each recipe, the same calculation is
also run for pure water and synthetic equal-total-salt controls: NaCl, CaCl2,
equal-mass NaCl-KCl and equal-mass NaCl-CaCl2. These are contribution screens,
not additional measurements. They prevent the KCl discrepancy from being
interpreted without the water-activity, dissolved-CO2/guest-fugacity and
hydrate-reference terms, and retain the single- and mixed-salt comparisons.
The independent Burgass et al. (2023) NaCl assessment remains in
`CO2BrineHydrateReferenceAssessmentTest` and is not weakened by this test.

## Independent KCl-water reference and controlled sequence

`KClWaterActivityReferenceAssessmentTest` separates the salt-only water
activity from pressure and CO2 loading. The Dholabhai K10 and K15 recipes are
1.49371 and 2.36154 mol KCl/kg water. A no-fit calculation from Archer's
273.15 K KCl-H2O Pitzer parameters gives water activities 0.95369 and 0.92701
([doi:10.1063/1.556034](https://doi.org/10.1063/1.556034)). These values are
salt-only, near-atmospheric reference checkpoints, not hydrate acceptance
limits.

At 273.15 K and 1.01325 bara, `SystemElectrolyteCPAstatoil` gives 0.95154 and
0.92234, residuals of -0.00215 and -0.00467. Raising pressure alone to 14.15
or 35.75 bara changes either result by less than 0.00013. Adding 10 mol CO2 at
the same fixed temperature raises the K15 proxy to 0.97147 at 14.15 bara and
0.94559 at 35.75 bara, while dissolved aqueous CO2 mole fractions become
0.07179 and 0.02793. The generated
`target/kcl-water-activity-archer-assessment.csv` retains every stage and phase
topology. The calculation explicitly selects the ion-rich aqueous phase.

The close salt-only agreement and negligible pressure-only response make the
CO2-loaded aqueous treatment the leading diagnostic location for the KCl
hydrate discrepancy. This sequence does not uniquely identify an interaction
parameter, qualify KCl hydrate prediction, or justify an empirical offset.
Independent CO2-in-KCl solubility data remain the next evidence boundary.

## Independent CO2-in-KCl solubility boundary

`KClCO2SolubilityReferenceAssessmentTest` executes the complete 26-point,
five-temperature series compiled by
[NIST SRD 106, system 62_229](https://srdata.nist.gov/solubility/sol_detail.aspx?sysID=62_229)
and the evaluated IUPAC-NIST Solubility Data Series volume 62 table from He and
Morse (1993), *Geochimica et Cosmochimica Acta* **57**, 3533-3554. The series
covers 273.15-363.15 K, 0.301-0.966 atm reported CO2 partial pressure and
0.100-5.000 mol KCl/kg water. NIST reports relative uncertainties of 0.012 for
CO2 solubility and 0.04 for its activity coefficient, plus 0.1 K temperature
uncertainty. Source solvent density and CO2 activity coefficient are retained
as evidence columns; they are not used as fitted inputs.

The fixture corrects one earlier transcription: at 273.15 K and 2.015 molal
KCl, the evaluated table reports 0.0572 mol CO2/kg water, not 0.0575. The
IUPAC-NIST PDF table also resolves the compact HTML rendering of the 348.15 K,
1.0 molal solvent density as 1.0179 g/cm3.

The no-fit `SystemElectrolyteCPAstatoil` calculation matches both the reported
CO2 partial pressure (`y_CO2 * P`) and final aqueous KCl molality. Each trial
starts from a fresh system with a 1 mol CO2 reservoir, the source salt inventory
and enough feed water to leave 1 kg **in the aqueous phase** after evaporation.
A nested water-inventory iteration checks that final mass at 1e-10 kg; the
calculated KCl molality must match the source within 1e-9 mol/kg water. A separate
363.15 K, 3 molal regression halves the gas reservoir and requires unchanged
solubility and pressure. This is a boundary-condition correction, not parameter
fitting. A fixed 1 kg feed with 10 mol CO2 can lose substantial water to the gas,
so its final liquid no longer represents the specified source molality.

The safeguarded bracketed secant pressure driver retains its 1e-10 bar stopping
criterion and permits up to 80 iterations. It brackets downward from a validated
liquid-bearing state, rather than starting with total pressure equal to CO2
partial pressure. At high temperature, that earlier lower trial can evaporate
the liquid and erase its salt inventory. Every accepted trial now requires
GAS+AQUEOUS topology, component-inventory residual below 1e-10 mol, 1e-12 mol
individual aqueous-ion closure, ion exclusion from gas, and molecular log
fugacity-ratio residual below 1e-7. A pressure root that exhausts its iterations
fails explicitly. Only fixed-T/P fugacities are consumed, so `init(1)` suffices;
no transport or derivative initialization is required.

The water-basis calculation explicitly divides aqueous CO2 moles by aqueous
water moles times water molar mass; it does not use generic `getMolality`, whose
denominator is total phase mass. Source salting-out order is checked separately
within each temperature block. The calculated change and its `salting_out`,
`salting_in` or `flat` classification remain evidence rather than an
experimental-accuracy pass condition. The generated
`target/kcl-co2-solubility-he-morse1993-assessment.csv` additionally retains actual
KCl molality, CO2 feed inventory, component balance and molecular fugacity
residuals. Rows are saved as they pass, so a later numerical failure preserves
earlier evidence while the collector still marks an incomplete producer as
failed. Experimental uncertainties and numerical tolerances are not relaxed.

The complete hosted exact-head artifact contains all 26 source states. Maximum
numerical residuals are 9.91e-11 bar in CO2 partial pressure, 4.70e-10 mol/kg
water in final aqueous KCl molality, 7.11e-15 mol in component inventory and
3.51e-12 in molecular log fugacity ratio. Every calculated CO2 solubility is
below experiment: relative residuals span -31.46% to -11.40%, with 19.95% mean
absolute relative residual and no point within the source's 1.2% relative
solubility uncertainty.

Within the five constant-partial-pressure temperature blocks, least-squares
salting coefficients `-d(log10(m_CO2))/d(m_KCl)` are 0.04662, 0.03702,
0.02587, 0.02027 and 0.01732 for the model versus 0.04032, 0.03750, 0.02480,
0.01739 and 0.01699 for experiment from 273.15 to 363.15 K. The model therefore
broadly retains the source salting slope, especially at 298.15 K, while showing
local salting-in at the 4.998/5.000 molal endpoints at 323.15, 348.15 and
363.15 K. This separates a systematic absolute-solubility deficit from the
high-molality shape defect; it does not uniquely identify a K+-specific
interaction value or justify an empirical temperature correction.

## Independent Ostwald-coefficient holdout

`KClCO2OstwaldReferenceAssessmentTest` evaluates the unambiguous 298.15 K
block compiled by
[NIST SRD 106, system 62_227](https://srdata.nist.gov/solubility/sol_detail.aspx?sysID=62_227)
from Yasunishi and Yoshida (1979), *Journal of Chemical and Engineering Data*
**24**, 11-14. The seven source points hold total pressure at 101.325 kPa while
KCl concentration rises from 0 to 4.131 mol/L of equilibrium solution. The
reported Ostwald coefficient falls from 0.8264 to 0.4703; NIST reports relative
solubility uncertainty of 0.01 and temperature uncertainty of 0.05 K.

The no-fit calculation retains the source observable rather than converting it
to molality. It computes the model Ostwald coefficient as the equilibrium
aqueous molecular-CO2 concentration divided by the gas-phase molecular-CO2
concentration. KCl inventory is iterated until the model aqueous phase matches
the source mol/L solution coordinate, avoiding an assumed experimental density.
All seven states, including 4.131 mol/L, match the source concentration. The
generated
`target/kcl-co2-ostwald-yasunishi-yoshida1979-assessment.csv` records target and
actual KCl concentration, experimental and calculated coefficients, residuals,
source uncertainty, both CO2 concentrations, aqueous water mass and phase
topology, and the state-matching status.

Experiment decreases from 0.8264 to 0.4703 and the model from 0.68156 to
0.40120 as KCl rises from 0 to 4.131 mol/L. All seven model values are low,
with relative residuals from -19.08% to -14.69% and 17.69% mean absolute
relative residual. Fitted coefficients
`-d(log10(L_CO2))/d(c_KCl)` are 0.05877 L/mol for experiment and
0.05608 L/mol for the model; endpoint reductions are 43.09% and 41.13%,
respectively. The independent observable therefore confirms the salting-out
direction and the systematic absolute-solubility deficit seen in the corrected
He and Morse assessment. It does not uniquely identify an ion-pair parameter or
qualify the hydrate endpoints.

The 308.15 K rows are deliberately excluded because the public compilation's
temperature grouping must be checked against the original article before
transcription. This cross-source diagnostic is a held-out model assessment, not
a calibration set or acceptance tolerance. It cannot justify a K+-specific
interaction value without a separate model-form derivation, calibration source
and independent scientific review.

## Existing evidence and stop boundary

The source-pinned notebook at EvenSol/NeqSim-Colab draft PR 189, head
`61a7ac0f01a5880c97946e15e44fcacbd9d78aa1`, evaluated 110 Dholabhai points
without parameter fitting. Its K10 errors were +1.5835 to +2.4720 K and K15
errors were +4.4775 to +6.7290 K. Family RMSE values were 0.3830 K for pure
water, 0.3249 K for NaCl, 0.7608 K for CaCl2, 0.9998 K for NaCl-KCl,
0.5106 K for NaCl-CaCl2 and 2.6851 K for KCl. Opposing mixture biases are not
pooled into a signed mean.

These results show a concentration-dependent KCl accuracy gap, but do not yet
identify a unique root cause or justify changing an ion interaction, guest
fugacity model, or hydrate parameter. Freezing and salt precipitation are not
modelled; synthetic seawater and commercial drilling-fluid qualification are
outside scope. No empirical temperature offset, paper-specific parameter,
relaxed tolerance, or model qualification is introduced here.
