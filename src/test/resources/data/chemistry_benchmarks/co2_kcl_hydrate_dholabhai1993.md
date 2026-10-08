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

`KClCO2SolubilityReferenceAssessmentTest` executes the complete 273.15 K
series compiled by
[NIST SRD 106, system 62_229](https://srdata.nist.gov/solubility/sol_detail.aspx?sysID=62_229)
from He and Morse (1993), *Geochimica et Cosmochimica Acta* **57**,
3533-3554. The six source points hold reported CO2 partial pressure at
0.966 atm while KCl molality rises from 0.100 to 3.508 mol/kg water. Measured
CO2 molality falls monotonically from 0.0713 to 0.0525 mol/kg water. NIST
reports relative solubility uncertainty of 0.012 and temperature uncertainty
of 0.1 K.

The no-fit `SystemElectrolyteCPAstatoil` calculation iterates total pressure so
that gas-phase `y_CO2 * P` matches the reported partial pressure, then compares
aqueous CO2 molality on the same kilogram-water basis. The water-basis
calculation explicitly divides aqueous CO2 moles by aqueous water moles times
the water molar mass; it does not use the generic component `getMolality`
method, whose denominator is total phase mass. The selected aqueous phase
retains 0.99884-0.99903 kg water across the series. The model instead rises
monotonically from 0.05757 to 0.89365 mol/kg water. Its signed relative residual
moves from -19.3% at 0.100 molal KCl to +1602.2% at 3.508 molal KCl. All six
states retain GAS+AQUEOUS topology, the target partial pressure and equal
aqueous K+/Cl- inventories. The generated
`target/kcl-co2-solubility-he-morse1993-assessment.csv` retains every source
value, uncertainty, aqueous water mass, calculated value, residual and phase
diagnostic.

This independent salt-out series changes the leading diagnostic from a broad
CO2-loaded-aqueous suspicion to a demonstrated concentration-dependent CO2/KCl
salting-direction error at low pressure. It does not identify a unique
interaction parameter, directly qualify the 14.15-35.75 bara hydrate states,
or justify fitting before an independent domain review of the electrolyte-CPA
CO2/KCl interaction treatment.

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
Six points through 3.505 mol/L are constructed at the source coordinate. The
4.131 mol/L point is retained as an explicit unmatched state: the present
calculation reaches 3.8167 mol/L before ion restoration collapses, so its
residual is withheld rather than evaluated at a different concentration. The
generated
`target/kcl-co2-ostwald-yasunishi-yoshida1979-assessment.csv` records target and
actual KCl concentration, experimental and calculated coefficients, residuals,
source uncertainty, both CO2 concentrations, aqueous water mass and phase
topology, and the state-matching status.

Across the six exactly constructed states, the model relative residual changes
from -17.5% in salt-free water to +34.96%, +117.6%, +447.4%, +1165.6% and
+2790.9% as KCl rises to 3.505 mol/L. The independent observable therefore
reproduces the wrong KCl salting direction seen in the He and Morse molality
series; it does not convert the unmatched 4.131 mol/L state into evidence.

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
