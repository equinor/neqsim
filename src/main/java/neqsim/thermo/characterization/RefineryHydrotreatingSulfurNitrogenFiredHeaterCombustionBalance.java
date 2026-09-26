package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable complete-combustion receipt downstream of a qualified fired-heater utility balance.
 *
 * <p>
 * The caller supplies a dry, ash-free CHSON elemental fuel basis, dry-air oxygen mole fraction, and excess-oxygen
 * fraction. The receipt applies element conservation only; it does not predict incomplete combustion, dissociation,
 * NOx, CO, unburned hydrocarbons, heat transfer, or stack temperature.
 *
 * @author esolbr1
 * @version 1.0
 */
public final class RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double KILOGRAMS_PER_TONNE = 1000.0;
  private static final double CARBON_KG_PER_KMOL = 12.011;
  private static final double HYDROGEN_KG_PER_KMOL_ATOMS = 1.008;
  private static final double SULFUR_KG_PER_KMOL = 32.065;
  private static final double OXYGEN_KG_PER_KMOL_ATOMS = 15.999;
  private static final double NITROGEN_KG_PER_KMOL_ATOMS = 14.007;
  private static final double CARBON_DIOXIDE_KG_PER_KMOL = 44.009;
  private static final double WATER_KG_PER_KMOL = 18.015;
  private static final double SULFUR_DIOXIDE_KG_PER_KMOL = 64.063;
  private static final double OXYGEN_KG_PER_KMOL = 31.998;
  private static final double NITROGEN_KG_PER_KMOL = 28.014;

  private final RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance utilityBalance;
  private final double carbonMassFraction;
  private final double hydrogenMassFraction;
  private final double sulfurMassFraction;
  private final double oxygenMassFraction;
  private final double nitrogenMassFraction;
  private final double dryAirOxygenMoleFraction;
  private final double excessOxygenFraction;
  private final double stoichiometricOxygenKmolPerHour;
  private final double suppliedOxygenKmolPerHour;
  private final double dryAirKmolPerHour;
  private final double dryAirMassFlowKgPerHour;
  private final double carbonDioxideKmolPerHour;
  private final double waterKmolPerHour;
  private final double sulfurDioxideKmolPerHour;
  private final double excessOxygenKmolPerHour;
  private final double nitrogenKmolPerHour;
  private final double wetFlueGasKmolPerHour;
  private final double dryFlueGasKmolPerHour;
  private final double dryCarbonDioxideMoleFraction;
  private final double dryOxygenMoleFraction;
  private final double directCarbonDioxideKgPerHour;
  private final double directCarbonDioxideKgPerTonneFeed;
  private final double flueGasMassFlowKgPerHour;
  private final double massClosureResidualKgPerHour;

  private RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance(
      RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance utilityBalance, double carbonMassFraction,
      double hydrogenMassFraction, double sulfurMassFraction, double oxygenMassFraction, double nitrogenMassFraction,
      double dryAirOxygenMoleFraction, double excessOxygenFraction, double stoichiometricOxygenKmolPerHour,
      double suppliedOxygenKmolPerHour, double dryAirKmolPerHour, double dryAirMassFlowKgPerHour,
      double carbonDioxideKmolPerHour, double waterKmolPerHour, double sulfurDioxideKmolPerHour,
      double excessOxygenKmolPerHour, double nitrogenKmolPerHour, double wetFlueGasKmolPerHour,
      double dryFlueGasKmolPerHour, double dryCarbonDioxideMoleFraction, double dryOxygenMoleFraction,
      double directCarbonDioxideKgPerHour, double directCarbonDioxideKgPerTonneFeed, double flueGasMassFlowKgPerHour,
      double massClosureResidualKgPerHour) {
    this.utilityBalance = utilityBalance;
    this.carbonMassFraction = carbonMassFraction;
    this.hydrogenMassFraction = hydrogenMassFraction;
    this.sulfurMassFraction = sulfurMassFraction;
    this.oxygenMassFraction = oxygenMassFraction;
    this.nitrogenMassFraction = nitrogenMassFraction;
    this.dryAirOxygenMoleFraction = dryAirOxygenMoleFraction;
    this.excessOxygenFraction = excessOxygenFraction;
    this.stoichiometricOxygenKmolPerHour = stoichiometricOxygenKmolPerHour;
    this.suppliedOxygenKmolPerHour = suppliedOxygenKmolPerHour;
    this.dryAirKmolPerHour = dryAirKmolPerHour;
    this.dryAirMassFlowKgPerHour = dryAirMassFlowKgPerHour;
    this.carbonDioxideKmolPerHour = carbonDioxideKmolPerHour;
    this.waterKmolPerHour = waterKmolPerHour;
    this.sulfurDioxideKmolPerHour = sulfurDioxideKmolPerHour;
    this.excessOxygenKmolPerHour = excessOxygenKmolPerHour;
    this.nitrogenKmolPerHour = nitrogenKmolPerHour;
    this.wetFlueGasKmolPerHour = wetFlueGasKmolPerHour;
    this.dryFlueGasKmolPerHour = dryFlueGasKmolPerHour;
    this.dryCarbonDioxideMoleFraction = dryCarbonDioxideMoleFraction;
    this.dryOxygenMoleFraction = dryOxygenMoleFraction;
    this.directCarbonDioxideKgPerHour = directCarbonDioxideKgPerHour;
    this.directCarbonDioxideKgPerTonneFeed = directCarbonDioxideKgPerTonneFeed;
    this.flueGasMassFlowKgPerHour = flueGasMassFlowKgPerHour;
    this.massClosureResidualKgPerHour = massClosureResidualKgPerHour;
  }

  /**
   * Calculate a complete-combustion and flue-gas element balance.
   *
   * @param utilityBalance qualified fired-heater utility receipt
   * @param carbonMassFraction dry, ash-free fuel carbon mass fraction
   * @param hydrogenMassFraction dry, ash-free fuel hydrogen mass fraction
   * @param sulfurMassFraction dry, ash-free fuel sulfur mass fraction
   * @param oxygenMassFraction dry, ash-free fuel oxygen mass fraction
   * @param nitrogenMassFraction dry, ash-free fuel nitrogen mass fraction
   * @param dryAirOxygenMoleFraction caller-supplied dry-air oxygen mole fraction in (0, 1)
   * @param excessOxygenFraction oxygen supplied above stoichiometric demand divided by demand
   * @return immutable combustion receipt
   */
  public static RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance calculate(
      RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance utilityBalance, double carbonMassFraction,
      double hydrogenMassFraction, double sulfurMassFraction, double oxygenMassFraction, double nitrogenMassFraction,
      double dryAirOxygenMoleFraction, double excessOxygenFraction) {
    Objects.requireNonNull(utilityBalance, "utilityBalance");
    requireFraction("carbonMassFraction", carbonMassFraction);
    requireFraction("hydrogenMassFraction", hydrogenMassFraction);
    requireFraction("sulfurMassFraction", sulfurMassFraction);
    requireFraction("oxygenMassFraction", oxygenMassFraction);
    requireFraction("nitrogenMassFraction", nitrogenMassFraction);
    double fractionSum = carbonMassFraction + hydrogenMassFraction + sulfurMassFraction + oxygenMassFraction
        + nitrogenMassFraction;
    if (Math.abs(fractionSum - 1.0) > 1.0e-9) {
      throw new IllegalArgumentException("dry ash-free CHSON fuel mass fractions must sum to 1.0");
    }
    if (!Double.isFinite(dryAirOxygenMoleFraction) || dryAirOxygenMoleFraction <= 0.0
        || dryAirOxygenMoleFraction >= 1.0) {
      throw new IllegalArgumentException("dryAirOxygenMoleFraction must be finite and between 0 and 1");
    }
    requireFiniteNonNegative("excessOxygenFraction", excessOxygenFraction);

    double fuelMassFlow = utilityBalance.getFuelMassFlowKgPerHour();
    double carbonKmolPerHour = fuelMassFlow * carbonMassFraction / CARBON_KG_PER_KMOL;
    double hydrogenAtomKmolPerHour = fuelMassFlow * hydrogenMassFraction / HYDROGEN_KG_PER_KMOL_ATOMS;
    double sulfurKmolPerHour = fuelMassFlow * sulfurMassFraction / SULFUR_KG_PER_KMOL;
    double oxygenAtomKmolPerHour = fuelMassFlow * oxygenMassFraction / OXYGEN_KG_PER_KMOL_ATOMS;
    double nitrogenAtomKmolPerHour = fuelMassFlow * nitrogenMassFraction / NITROGEN_KG_PER_KMOL_ATOMS;

    double stoichiometricOxygen = carbonKmolPerHour + hydrogenAtomKmolPerHour / 4.0 + sulfurKmolPerHour
        - oxygenAtomKmolPerHour / 2.0;
    if (fuelMassFlow > 0.0 && (!Double.isFinite(stoichiometricOxygen) || stoichiometricOxygen <= 0.0)) {
      throw new IllegalArgumentException("fuel composition must require positive stoichiometric oxygen");
    }
    double suppliedOxygen = stoichiometricOxygen * (1.0 + excessOxygenFraction);
    double airNitrogen = suppliedOxygen * (1.0 - dryAirOxygenMoleFraction) / dryAirOxygenMoleFraction;
    double dryAir = suppliedOxygen + airNitrogen;
    double dryAirMass = suppliedOxygen * OXYGEN_KG_PER_KMOL + airNitrogen * NITROGEN_KG_PER_KMOL;

    double carbonDioxide = carbonKmolPerHour;
    double water = hydrogenAtomKmolPerHour / 2.0;
    double sulfurDioxide = sulfurKmolPerHour;
    double excessOxygen = suppliedOxygen - stoichiometricOxygen;
    double nitrogen = airNitrogen + nitrogenAtomKmolPerHour / 2.0;
    double dryFlueGas = carbonDioxide + sulfurDioxide + excessOxygen + nitrogen;
    double wetFlueGas = dryFlueGas + water;
    double dryCarbonDioxideFraction = dryFlueGas == 0.0 ? 0.0 : carbonDioxide / dryFlueGas;
    double dryOxygenFraction = dryFlueGas == 0.0 ? 0.0 : excessOxygen / dryFlueGas;

    double directCarbonDioxide = carbonDioxide * CARBON_DIOXIDE_KG_PER_KMOL;
    double flueGasMass = directCarbonDioxide + water * WATER_KG_PER_KMOL + sulfurDioxide * SULFUR_DIOXIDE_KG_PER_KMOL
        + excessOxygen * OXYGEN_KG_PER_KMOL + nitrogen * NITROGEN_KG_PER_KMOL;
    double feedMassFlow = utilityBalance.getThermalDutyBalance().getProductDistributionReceipt().getThroughputBalance()
        .getFeedMassFlowKgPerHour();
    double directCarbonDioxidePerTonneFeed = directCarbonDioxide * KILOGRAMS_PER_TONNE / feedMassFlow;
    double massResidual = fuelMassFlow + dryAirMass - flueGasMass;

    double tolerance = 1.0e-9 * Math.max(1.0, fuelMassFlow + dryAirMass);
    if (!allFiniteNonNegative(stoichiometricOxygen, suppliedOxygen, dryAir, dryAirMass, carbonDioxide, water,
        sulfurDioxide, excessOxygen, nitrogen, wetFlueGas, dryFlueGas, dryCarbonDioxideFraction, dryOxygenFraction,
        directCarbonDioxide, directCarbonDioxidePerTonneFeed, flueGasMass) || !Double.isFinite(massResidual)
        || Math.abs(massResidual) > tolerance) {
      throw new IllegalArgumentException("inputs do not define a closed combustion receipt");
    }

    return new RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance(utilityBalance, carbonMassFraction,
        hydrogenMassFraction, sulfurMassFraction, oxygenMassFraction, nitrogenMassFraction, dryAirOxygenMoleFraction,
        excessOxygenFraction, stoichiometricOxygen, suppliedOxygen, dryAir, dryAirMass, carbonDioxide, water,
        sulfurDioxide, excessOxygen, nitrogen, wetFlueGas, dryFlueGas, dryCarbonDioxideFraction, dryOxygenFraction,
        directCarbonDioxide, directCarbonDioxidePerTonneFeed, flueGasMass, massResidual);
  }

  private static void requireFraction(String name, double value) {
    if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
      throw new IllegalArgumentException(name + " must be finite and between 0 and 1");
    }
  }

  private static void requireFiniteNonNegative(String name, double value) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and non-negative");
    }
  }

  private static boolean allFiniteNonNegative(double... values) {
    for (double value : values) {
      if (!Double.isFinite(value) || value < 0.0) {
        return false;
      }
    }
    return true;
  }

  /** @return upstream qualified fired-heater utility receipt */
  public RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance getUtilityBalance() {
    return utilityBalance;
  }

  /** @return caller-supplied dry-fuel carbon mass fraction */
  public double getCarbonMassFraction() {
    return carbonMassFraction;
  }

  /** @return caller-supplied dry-fuel hydrogen mass fraction */
  public double getHydrogenMassFraction() {
    return hydrogenMassFraction;
  }

  /** @return caller-supplied dry-fuel sulfur mass fraction */
  public double getSulfurMassFraction() {
    return sulfurMassFraction;
  }

  /** @return caller-supplied dry-fuel oxygen mass fraction */
  public double getOxygenMassFraction() {
    return oxygenMassFraction;
  }

  /** @return caller-supplied dry-fuel nitrogen mass fraction */
  public double getNitrogenMassFraction() {
    return nitrogenMassFraction;
  }

  /** @return caller-supplied dry-air oxygen mole fraction */
  public double getDryAirOxygenMoleFraction() {
    return dryAirOxygenMoleFraction;
  }

  /** @return caller-supplied excess oxygen fraction */
  public double getExcessOxygenFraction() {
    return excessOxygenFraction;
  }

  /** @return stoichiometric oxygen demand in kmol/h */
  public double getStoichiometricOxygenKmolPerHour() {
    return stoichiometricOxygenKmolPerHour;
  }

  /** @return supplied oxygen in kmol/h */
  public double getSuppliedOxygenKmolPerHour() {
    return suppliedOxygenKmolPerHour;
  }

  /** @return dry combustion air in kmol/h */
  public double getDryAirKmolPerHour() {
    return dryAirKmolPerHour;
  }

  /** @return dry combustion air mass flow in kg/h */
  public double getDryAirMassFlowKgPerHour() {
    return dryAirMassFlowKgPerHour;
  }

  /** @return carbon dioxide in kmol/h */
  public double getCarbonDioxideKmolPerHour() {
    return carbonDioxideKmolPerHour;
  }

  /** @return water vapour in kmol/h */
  public double getWaterKmolPerHour() {
    return waterKmolPerHour;
  }

  /** @return sulfur dioxide in kmol/h */
  public double getSulfurDioxideKmolPerHour() {
    return sulfurDioxideKmolPerHour;
  }

  /** @return excess oxygen in kmol/h */
  public double getExcessOxygenKmolPerHour() {
    return excessOxygenKmolPerHour;
  }

  /** @return nitrogen in kmol/h, including fuel-bound nitrogen converted to N2 */
  public double getNitrogenKmolPerHour() {
    return nitrogenKmolPerHour;
  }

  /** @return wet flue-gas flow in kmol/h */
  public double getWetFlueGasKmolPerHour() {
    return wetFlueGasKmolPerHour;
  }

  /** @return dry flue-gas flow in kmol/h */
  public double getDryFlueGasKmolPerHour() {
    return dryFlueGasKmolPerHour;
  }

  /** @return dry flue-gas carbon-dioxide mole fraction */
  public double getDryCarbonDioxideMoleFraction() {
    return dryCarbonDioxideMoleFraction;
  }

  /** @return dry flue-gas oxygen mole fraction */
  public double getDryOxygenMoleFraction() {
    return dryOxygenMoleFraction;
  }

  /** @return direct stack carbon dioxide in kg/h */
  public double getDirectCarbonDioxideKgPerHour() {
    return directCarbonDioxideKgPerHour;
  }

  /** @return direct stack carbon dioxide in kg per tonne liquid feed */
  public double getDirectCarbonDioxideKgPerTonneFeed() {
    return directCarbonDioxideKgPerTonneFeed;
  }

  /** @return total wet flue-gas mass flow in kg/h */
  public double getFlueGasMassFlowKgPerHour() {
    return flueGasMassFlowKgPerHour;
  }

  /** @return fuel-plus-air minus wet-flue-gas mass closure residual in kg/h */
  public double getMassClosureResidualKgPerHour() {
    return massClosureResidualKgPerHour;
  }
}
