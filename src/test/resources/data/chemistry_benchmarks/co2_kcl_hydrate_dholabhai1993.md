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
