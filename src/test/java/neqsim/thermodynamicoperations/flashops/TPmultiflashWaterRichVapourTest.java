package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression tests for the vapour phase of a water-rich hydrocarbon feed.
 *
 * <p>
 * Diluting a two-phase hydrocarbon mixture with water cannot remove its vapour phase: water is almost insoluble in the
 * hydrocarbon phases, so it forms its own aqueous phase and leaves the hydrocarbon vapour-liquid split essentially
 * untouched. The multiphase stability analysis nevertheless builds its trial compositions from the overall feed, so
 * once the water cut is high enough the hydrocarbons become a small minority of that feed and the vapour stationary
 * point is missed. The flash then returned OIL + AQUEOUS with a higher extensive Gibbs energy than the correct GAS +
 * OIL + AQUEOUS split.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
class TPmultiflashWaterRichVapourTest {
  /** Hydrocarbon components of the synthetic wellstream. */
  private static final String[] HYDROCARBON_NAMES = { "methane", "ethane", "propane", "n-butane", "n-pentane",
      "n-hexane", "n-heptane", "n-octane" };
  /** Water-free mole fractions matching {@link #HYDROCARBON_NAMES}. */
  private static final double[] HYDROCARBON_FRACTIONS = { 0.55, 0.08, 0.05, 0.03, 0.02, 0.02, 0.10, 0.15 };
  /** Flash pressure in bara, representative of a wellhead. */
  private static final double PRESSURE = 45.62;
  /** Flash temperature in K, representative of a wellhead. */
  private static final double TEMPERATURE = 273.15 + 30.8;

  /**
   * Builds the synthetic wellstream at a given water mole fraction.
   *
   * @param waterFraction overall water mole fraction, between 0.0 and 1.0
   * @return fluid ready for a multiphase TP flash
   */
  private SystemInterface buildFluid(double waterFraction) {
    SystemInterface fluid = new SystemSrkEos(TEMPERATURE, PRESSURE);
    for (int i = 0; i < HYDROCARBON_NAMES.length; i++) {
      fluid.addComponent(HYDROCARBON_NAMES[i], HYDROCARBON_FRACTIONS[i] * (1.0 - waterFraction));
    }
    if (waterFraction > 0.0) {
      fluid.addComponent("water", waterFraction);
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * The water-free feed must be two-phase, otherwise the dilution test below has no vapour to keep.
   */
  @Test
  void waterFreeFeedIsTwoPhase() {
    SystemInterface fluid = buildFluid(0.0);
    new ThermodynamicOperations(fluid).TPflash();
    assertTrue(fluid.hasPhaseType(PhaseType.GAS), "water-free feed must contain a gas phase");
    assertTrue(fluid.hasPhaseType(PhaseType.OIL), "water-free feed must contain an oil phase");
  }

  /**
   * Adding water must not remove the hydrocarbon vapour phase, at any water cut.
   */
  @Test
  void waterDilutionKeepsTheHydrocarbonVapourPhase() {
    double[] waterFractions = { 0.50, 0.70, 0.76, 0.78, 0.80, 0.83, 0.90, 0.95 };
    for (int i = 0; i < waterFractions.length; i++) {
      double waterFraction = waterFractions[i];
      SystemInterface fluid = buildFluid(waterFraction);
      new ThermodynamicOperations(fluid).TPflash();

      assertTrue(fluid.hasPhaseType(PhaseType.GAS), "gas phase lost at water mole fraction " + waterFraction);
      assertTrue(fluid.hasPhaseType(PhaseType.AQUEOUS),
          "aqueous phase missing at water mole fraction " + waterFraction);

      double phaseFractionSum = 0.0;
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        phaseFractionSum += fluid.getBeta(phase);
      }
      assertEquals(1.0, phaseFractionSum, 1.0e-6,
          "phase fractions must sum to one at water mole fraction " + waterFraction);
    }
  }

  /**
   * The hydrocarbon phase fractions must track the hydrocarbon inventory rather than collapse.
   */
  @Test
  void hydrocarbonPhaseFractionsFollowTheHydrocarbonInventory() {
    double waterFraction = 0.83;
    SystemInterface fluid = buildFluid(waterFraction);
    new ThermodynamicOperations(fluid).TPflash();

    double hydrocarbonBeta = 0.0;
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      if (fluid.getPhase(phase).getType() != PhaseType.AQUEOUS) {
        hydrocarbonBeta += fluid.getBeta(phase);
      }
    }
    assertEquals(1.0 - waterFraction, hydrocarbonBeta, 0.02, "hydrocarbon phases must carry the hydrocarbon inventory");
  }
}
