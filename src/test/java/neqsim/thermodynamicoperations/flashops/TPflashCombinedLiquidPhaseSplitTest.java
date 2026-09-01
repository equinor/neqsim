package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos1978;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression guard for the combined oil + aqueous liquid stream a two-phase separator publishes.
 *
 * <p>
 * A three-phase separator flashes its feed to gas, oil and aqueous, and then hands downstream equipment a single liquid
 * stream built from the two liquid phases. Re-flashing that stream at the same temperature and pressure must return the
 * same two liquids: it was in equilibrium with a vapour at exactly this state, so no further vaporization is possible.
 * If the flash instead returns a gas/oil split it has to dissolve the water in the vapour, which puts the water partial
 * pressure far above its saturation pressure and makes every downstream unit see a compressible stream.
 * </p>
 */
class TPflashCombinedLiquidPhaseSplitTest {
  /** Water saturation pressure at 30 C in bara. */
  private static final double WATER_SATURATION_PRESSURE_30C = 0.0425;

  @Test
  void srkCombinedLiquidKeepsBothLiquidPhases() {
    assertCombinedLiquidStaysLiquid(new SystemSrkEos(273.15 + 30.0, 1.62), 1.62);
  }

  @Test
  void pengRobinsonCombinedLiquidKeepsBothLiquidPhases() {
    assertCombinedLiquidStaysLiquid(new SystemPrEos1978(273.15 + 30.0, 1.62), 1.62);
  }

  @Test
  void srkCombinedLiquidKeepsBothLiquidPhasesAcrossSeparatorPressures() {
    for (double pressure : new double[] { 2.10, 1.62, 1.20, 0.74 }) {
      assertCombinedLiquidStaysLiquid(new SystemSrkEos(273.15 + 30.0, pressure), pressure);
    }
  }

  /**
   * Flashes a water-rich separator feed and re-flashes its combined liquid phases.
   *
   * @param system empty fluid to fill with the separator feed
   * @param pressureBara flash pressure in bara
   */
  private void assertCombinedLiquidStaysLiquid(SystemInterface system, double pressureBara) {
    addSeparatorFeed(system);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    new ThermodynamicOperations(system).TPflash();

    assertTrue(system.hasPhaseType(PhaseType.OIL), "feed should form an oil phase at " + pressureBara + " bara");
    assertTrue(system.hasPhaseType(PhaseType.AQUEOUS),
        "feed should form an aqueous phase at " + pressureBara + " bara");

    SystemInterface combinedLiquid = system.phaseToSystem(system.getPhaseNumberOfPhase("oil"),
        system.getPhaseNumberOfPhase("aqueous"));
    combinedLiquid.setMultiPhaseCheck(true);
    combinedLiquid.setTemperature(273.15 + 30.0);
    combinedLiquid.setPressure(pressureBara, "bara");
    new ThermodynamicOperations(combinedLiquid).TPflash();

    assertEquals(2, combinedLiquid.getNumberOfPhases(),
        "combined oil + aqueous liquid should stay two liquid phases at " + pressureBara + " bara");
    assertTrue(combinedLiquid.hasPhaseType(PhaseType.AQUEOUS),
        "combined liquid lost its aqueous phase at " + pressureBara + " bara");

    for (int phaseIndex = 0; phaseIndex < combinedLiquid.getNumberOfPhases(); phaseIndex++) {
      if (combinedLiquid.getPhase(phaseIndex).getType() != PhaseType.GAS) {
        continue;
      }
      double waterPartialPressure = combinedLiquid.getPhase(phaseIndex).getComponent("water").getx() * pressureBara;
      assertTrue(waterPartialPressure <= 2.0 * WATER_SATURATION_PRESSURE_30C,
          "vapour water partial pressure " + waterPartialPressure + " bara exceeds the 30 C saturation pressure "
              + WATER_SATURATION_PRESSURE_30C + " bara at " + pressureBara + " bara");
    }
  }

  /**
   * Adds a water-rich low-pressure separator feed.
   *
   * @param system fluid to fill
   */
  private void addSeparatorFeed(SystemInterface system) {
    system.addComponent("methane", 0.0034);
    system.addComponent("ethane", 0.0038);
    system.addComponent("propane", 0.0406);
    system.addComponent("i-butane", 0.0339);
    system.addComponent("n-butane", 0.1114);
    system.addComponent("i-pentane", 0.0536);
    system.addComponent("n-pentane", 0.0733);
    system.addComponent("n-hexane", 0.0681);
    system.addComponent("n-heptane", 0.0637);
    system.addComponent("n-octane", 0.0365);
    system.addComponent("nC10", 0.0172);
    system.addComponent("water", 0.4945);
  }
}
