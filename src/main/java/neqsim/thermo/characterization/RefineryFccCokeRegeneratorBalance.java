package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable screening-level FCC coke-regenerator carbon and dry-flue-gas balance.
 *
 * <p>
 * The calculation applies caller-owned coke carbon, burn, carbon-dioxide selectivity, dry-air oxygen, and excess-oxygen
 * assumptions to a qualified FCC yield receipt. It is a stoichiometric allocation, not a regenerator heat balance,
 * catalyst model, emissions compliance calculation, or flue-gas property model.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryFccCokeRegeneratorBalance implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double CARBON_MOLAR_MASS_KG_PER_KMOL = 12.011;
  private static final double OXYGEN_MOLAR_MASS_KG_PER_KMOL = 31.9988;
  private static final double NITROGEN_MOLAR_MASS_KG_PER_KMOL = 28.0134;
  private static final double CARBON_DIOXIDE_MOLAR_MASS_KG_PER_KMOL = 44.0095;
  private static final double CARBON_MONOXIDE_MOLAR_MASS_KG_PER_KMOL = 28.0101;

  private final RefineryFccYieldBalance fccYieldBalance;
  private final double cokeCarbonMassFraction;
  private final double cokeBurnFraction;
  private final double burnedCarbonToCarbonDioxideFraction;
  private final double dryAirOxygenMoleFraction;
  private final double excessOxygenFraction;
  private final double cokeCarbonMassFlowKgPerHour;
  private final double nonCarbonCokeMassFlowKgPerHour;
  private final double burnedCarbonMassFlowKgPerHour;
  private final double remainingCarbonMassFlowKgPerHour;
  private final double carbonDioxideMolarFlowKmolPerHour;
  private final double carbonMonoxideMolarFlowKmolPerHour;
  private final double stoichiometricOxygenDemandKmolPerHour;
  private final double suppliedOxygenKmolPerHour;
  private final double dryAirMolarFlowKmolPerHour;
  private final double nitrogenMolarFlowKmolPerHour;
  private final double excessOxygenMolarFlowKmolPerHour;
  private final double carbonDioxideMassFlowKgPerHour;
  private final double carbonMonoxideMassFlowKgPerHour;
  private final double dryAirMassFlowKgPerHour;
  private final double dryFlueGasMassFlowKgPerHour;
  private final double carbonBalanceResidualKgPerHour;
  private final double oxygenBalanceResidualKmolPerHour;
  private final double dryGasMolarBalanceResidualKmolPerHour;

  private RefineryFccCokeRegeneratorBalance(RefineryFccYieldBalance fccYieldBalance, double cokeCarbonMassFraction,
      double cokeBurnFraction, double burnedCarbonToCarbonDioxideFraction, double dryAirOxygenMoleFraction,
      double excessOxygenFraction, double cokeCarbonMassFlowKgPerHour, double nonCarbonCokeMassFlowKgPerHour,
      double burnedCarbonMassFlowKgPerHour, double remainingCarbonMassFlowKgPerHour,
      double carbonDioxideMolarFlowKmolPerHour, double carbonMonoxideMolarFlowKmolPerHour,
      double stoichiometricOxygenDemandKmolPerHour, double suppliedOxygenKmolPerHour, double dryAirMolarFlowKmolPerHour,
      double nitrogenMolarFlowKmolPerHour, double excessOxygenMolarFlowKmolPerHour,
      double carbonDioxideMassFlowKgPerHour, double carbonMonoxideMassFlowKgPerHour, double dryAirMassFlowKgPerHour,
      double dryFlueGasMassFlowKgPerHour, double carbonBalanceResidualKgPerHour,
      double oxygenBalanceResidualKmolPerHour, double dryGasMolarBalanceResidualKmolPerHour) {
    this.fccYieldBalance = fccYieldBalance;
    this.cokeCarbonMassFraction = cokeCarbonMassFraction;
    this.cokeBurnFraction = cokeBurnFraction;
    this.burnedCarbonToCarbonDioxideFraction = burnedCarbonToCarbonDioxideFraction;
    this.dryAirOxygenMoleFraction = dryAirOxygenMoleFraction;
    this.excessOxygenFraction = excessOxygenFraction;
    this.cokeCarbonMassFlowKgPerHour = cokeCarbonMassFlowKgPerHour;
    this.nonCarbonCokeMassFlowKgPerHour = nonCarbonCokeMassFlowKgPerHour;
    this.burnedCarbonMassFlowKgPerHour = burnedCarbonMassFlowKgPerHour;
    this.remainingCarbonMassFlowKgPerHour = remainingCarbonMassFlowKgPerHour;
    this.carbonDioxideMolarFlowKmolPerHour = carbonDioxideMolarFlowKmolPerHour;
    this.carbonMonoxideMolarFlowKmolPerHour = carbonMonoxideMolarFlowKmolPerHour;
    this.stoichiometricOxygenDemandKmolPerHour = stoichiometricOxygenDemandKmolPerHour;
    this.suppliedOxygenKmolPerHour = suppliedOxygenKmolPerHour;
    this.dryAirMolarFlowKmolPerHour = dryAirMolarFlowKmolPerHour;
    this.nitrogenMolarFlowKmolPerHour = nitrogenMolarFlowKmolPerHour;
    this.excessOxygenMolarFlowKmolPerHour = excessOxygenMolarFlowKmolPerHour;
    this.carbonDioxideMassFlowKgPerHour = carbonDioxideMassFlowKgPerHour;
    this.carbonMonoxideMassFlowKgPerHour = carbonMonoxideMassFlowKgPerHour;
    this.dryAirMassFlowKgPerHour = dryAirMassFlowKgPerHour;
    this.dryFlueGasMassFlowKgPerHour = dryFlueGasMassFlowKgPerHour;
    this.carbonBalanceResidualKgPerHour = carbonBalanceResidualKgPerHour;
    this.oxygenBalanceResidualKmolPerHour = oxygenBalanceResidualKmolPerHour;
    this.dryGasMolarBalanceResidualKmolPerHour = dryGasMolarBalanceResidualKmolPerHour;
  }

  /**
   * Calculate an FCC coke-regenerator carbon, air, and dry-flue-gas balance.
   *
   * @param fccYieldBalance qualified FCC yield receipt
   * @param cokeCarbonMassFraction carbon mass fraction of the represented coke, in [0, 1]
   * @param cokeBurnFraction fraction of coke carbon burned, in [0, 1]
   * @param burnedCarbonToCarbonDioxideFraction fraction of burned carbon forming CO2, in [0, 1]
   * @param dryAirOxygenMoleFraction oxygen mole fraction in dry air, in (0, 1)
   * @param excessOxygenFraction supplied oxygen above stoichiometric demand as a fraction of demand
   * @return immutable FCC coke-regenerator receipt
   */
  public static RefineryFccCokeRegeneratorBalance calculate(RefineryFccYieldBalance fccYieldBalance,
      double cokeCarbonMassFraction, double cokeBurnFraction, double burnedCarbonToCarbonDioxideFraction,
      double dryAirOxygenMoleFraction, double excessOxygenFraction) {
    Objects.requireNonNull(fccYieldBalance, "fccYieldBalance");
    requireUnitFraction(cokeCarbonMassFraction, "cokeCarbonMassFraction");
    requireUnitFraction(cokeBurnFraction, "cokeBurnFraction");
    requireUnitFraction(burnedCarbonToCarbonDioxideFraction, "burnedCarbonToCarbonDioxideFraction");
    if (!Double.isFinite(dryAirOxygenMoleFraction) || dryAirOxygenMoleFraction <= 0.0
        || dryAirOxygenMoleFraction >= 1.0) {
      throw new IllegalArgumentException("dryAirOxygenMoleFraction must be finite and in (0, 1)");
    }
    if (!Double.isFinite(excessOxygenFraction) || excessOxygenFraction < 0.0) {
      throw new IllegalArgumentException("excessOxygenFraction must be finite and non-negative");
    }

    double cokeMassFlow = fccYieldBalance.getCokeMassFlowKgPerHour();
    double cokeCarbonMassFlow = cokeMassFlow * cokeCarbonMassFraction;
    double nonCarbonCokeMassFlow = cokeMassFlow - cokeCarbonMassFlow;
    double burnedCarbonMassFlow = cokeCarbonMassFlow * cokeBurnFraction;
    double remainingCarbonMassFlow = cokeCarbonMassFlow - burnedCarbonMassFlow;
    double burnedCarbonKmol = burnedCarbonMassFlow / CARBON_MOLAR_MASS_KG_PER_KMOL;
    double carbonDioxideKmol = burnedCarbonKmol * burnedCarbonToCarbonDioxideFraction;
    double carbonMonoxideKmol = burnedCarbonKmol - carbonDioxideKmol;
    double stoichiometricOxygenDemandKmol = carbonDioxideKmol + 0.5 * carbonMonoxideKmol;
    double suppliedOxygenKmol = stoichiometricOxygenDemandKmol * (1.0 + excessOxygenFraction);
    double dryAirKmol = suppliedOxygenKmol / dryAirOxygenMoleFraction;
    double nitrogenKmol = dryAirKmol - suppliedOxygenKmol;
    double excessOxygenKmol = suppliedOxygenKmol - stoichiometricOxygenDemandKmol;
    double carbonDioxideMassFlow = carbonDioxideKmol * CARBON_DIOXIDE_MOLAR_MASS_KG_PER_KMOL;
    double carbonMonoxideMassFlow = carbonMonoxideKmol * CARBON_MONOXIDE_MOLAR_MASS_KG_PER_KMOL;
    double dryAirMassFlow = suppliedOxygenKmol * OXYGEN_MOLAR_MASS_KG_PER_KMOL
        + nitrogenKmol * NITROGEN_MOLAR_MASS_KG_PER_KMOL;
    double dryFlueGasMassFlow = carbonDioxideMassFlow + carbonMonoxideMassFlow
        + excessOxygenKmol * OXYGEN_MOLAR_MASS_KG_PER_KMOL + nitrogenKmol * NITROGEN_MOLAR_MASS_KG_PER_KMOL;
    double carbonBalanceResidual = cokeCarbonMassFlow
        - (carbonDioxideKmol + carbonMonoxideKmol) * CARBON_MOLAR_MASS_KG_PER_KMOL - remainingCarbonMassFlow;
    double oxygenBalanceResidual = suppliedOxygenKmol - carbonDioxideKmol - 0.5 * carbonMonoxideKmol - excessOxygenKmol;
    double dryGasMolarBalanceResidual = dryAirKmol + 0.5 * carbonMonoxideKmol - carbonDioxideKmol
        - carbonMonoxideKmol - excessOxygenKmol - nitrogenKmol;

    if (!allFiniteNonNegative(cokeCarbonMassFlow, nonCarbonCokeMassFlow, burnedCarbonMassFlow, remainingCarbonMassFlow,
        carbonDioxideKmol, carbonMonoxideKmol, stoichiometricOxygenDemandKmol, suppliedOxygenKmol, dryAirKmol,
        nitrogenKmol, excessOxygenKmol, carbonDioxideMassFlow, carbonMonoxideMassFlow, dryAirMassFlow,
        dryFlueGasMassFlow)) {
      throw new IllegalArgumentException("inputs do not define a finite FCC regenerator balance");
    }
    double tolerance = 1.0e-12 * Math.max(1.0, cokeMassFlow);
    if (Math.abs(carbonBalanceResidual) > tolerance || Math.abs(oxygenBalanceResidual) > tolerance
        || Math.abs(dryGasMolarBalanceResidual) > tolerance) {
      throw new IllegalArgumentException("FCC regenerator balance does not close");
    }

    return new RefineryFccCokeRegeneratorBalance(fccYieldBalance, cokeCarbonMassFraction, cokeBurnFraction,
        burnedCarbonToCarbonDioxideFraction, dryAirOxygenMoleFraction, excessOxygenFraction, cokeCarbonMassFlow,
        nonCarbonCokeMassFlow, burnedCarbonMassFlow, remainingCarbonMassFlow, carbonDioxideKmol, carbonMonoxideKmol,
        stoichiometricOxygenDemandKmol, suppliedOxygenKmol, dryAirKmol, nitrogenKmol, excessOxygenKmol,
        carbonDioxideMassFlow, carbonMonoxideMassFlow, dryAirMassFlow, dryFlueGasMassFlow, carbonBalanceResidual,
        oxygenBalanceResidual, dryGasMolarBalanceResidual);
  }

  /**
   * Require a finite fraction in the inclusive unit interval.
   *
   * @param value value to inspect
   * @param name input name used in the exception message
   */
  private static void requireUnitFraction(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
      throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
    }
  }

  /**
   * Test that all supplied values are finite and non-negative.
   *
   * @param values values to inspect
   * @return true when every value is finite and non-negative
   */
  private static boolean allFiniteNonNegative(double... values) {
    for (double value : values) {
      if (!Double.isFinite(value) || value < 0.0) {
        return false;
      }
    }
    return true;
  }

  /** @return qualified FCC yield receipt */
  public RefineryFccYieldBalance getFccYieldBalance() {
    return fccYieldBalance;
  }

  /** @return carbon mass fraction of the represented coke */
  public double getCokeCarbonMassFraction() {
    return cokeCarbonMassFraction;
  }

  /** @return fraction of coke carbon burned */
  public double getCokeBurnFraction() {
    return cokeBurnFraction;
  }

  /** @return fraction of burned carbon forming carbon dioxide */
  public double getBurnedCarbonToCarbonDioxideFraction() {
    return burnedCarbonToCarbonDioxideFraction;
  }

  /** @return oxygen mole fraction in supplied dry air */
  public double getDryAirOxygenMoleFraction() {
    return dryAirOxygenMoleFraction;
  }

  /** @return supplied oxygen above stoichiometric demand as a fraction of demand */
  public double getExcessOxygenFraction() {
    return excessOxygenFraction;
  }

  /** @return carbon in represented coke in kg/h */
  public double getCokeCarbonMassFlowKgPerHour() {
    return cokeCarbonMassFlowKgPerHour;
  }

  /** @return represented non-carbon coke material in kg/h */
  public double getNonCarbonCokeMassFlowKgPerHour() {
    return nonCarbonCokeMassFlowKgPerHour;
  }

  /** @return burned carbon in kg/h */
  public double getBurnedCarbonMassFlowKgPerHour() {
    return burnedCarbonMassFlowKgPerHour;
  }

  /** @return unburned carbon in kg/h */
  public double getRemainingCarbonMassFlowKgPerHour() {
    return remainingCarbonMassFlowKgPerHour;
  }

  /** @return carbon-dioxide molar flow in kmol/h */
  public double getCarbonDioxideMolarFlowKmolPerHour() {
    return carbonDioxideMolarFlowKmolPerHour;
  }

  /** @return carbon-monoxide molar flow in kmol/h */
  public double getCarbonMonoxideMolarFlowKmolPerHour() {
    return carbonMonoxideMolarFlowKmolPerHour;
  }

  /** @return stoichiometric oxygen demand in kmol/h */
  public double getStoichiometricOxygenDemandKmolPerHour() {
    return stoichiometricOxygenDemandKmolPerHour;
  }

  /** @return supplied oxygen in kmol/h */
  public double getSuppliedOxygenKmolPerHour() {
    return suppliedOxygenKmolPerHour;
  }

  /** @return supplied dry-air molar flow in kmol/h */
  public double getDryAirMolarFlowKmolPerHour() {
    return dryAirMolarFlowKmolPerHour;
  }

  /** @return nitrogen molar flow in dry flue gas in kmol/h */
  public double getNitrogenMolarFlowKmolPerHour() {
    return nitrogenMolarFlowKmolPerHour;
  }

  /** @return unconsumed oxygen molar flow in dry flue gas in kmol/h */
  public double getExcessOxygenMolarFlowKmolPerHour() {
    return excessOxygenMolarFlowKmolPerHour;
  }

  /** @return carbon-dioxide mass flow in kg/h */
  public double getCarbonDioxideMassFlowKgPerHour() {
    return carbonDioxideMassFlowKgPerHour;
  }

  /** @return carbon-monoxide mass flow in kg/h */
  public double getCarbonMonoxideMassFlowKgPerHour() {
    return carbonMonoxideMassFlowKgPerHour;
  }

  /** @return supplied dry-air mass flow in kg/h */
  public double getDryAirMassFlowKgPerHour() {
    return dryAirMassFlowKgPerHour;
  }

  /** @return represented dry-flue-gas mass flow in kg/h */
  public double getDryFlueGasMassFlowKgPerHour() {
    return dryFlueGasMassFlowKgPerHour;
  }

  /** @return coke-carbon input minus flue-gas carbon and remaining carbon in kg/h */
  public double getCarbonBalanceResidualKgPerHour() {
    return carbonBalanceResidualKgPerHour;
  }

  /** @return oxygen supplied minus oxygen in CO2, CO, and excess O2 in kmol/h */
  public double getOxygenBalanceResidualKmolPerHour() {
    return oxygenBalanceResidualKmolPerHour;
  }

  /** @return dry air plus CO-formation gas expansion minus represented dry flue gas in kmol/h */
  public double getDryGasMolarBalanceResidualKmolPerHour() {
    return dryGasMolarBalanceResidualKmolPerHour;
  }
}
