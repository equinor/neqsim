package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentHydrate;
import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Regression coverage for the phase inventories reported in issue #3871. */
class TPHydrateFlashBalanceTest {
  @Test
  void reportedMixtureConservesEveryComponent() {
    SystemInterface fluid = mixture(283.15);
    TPHydrateFlash flash = new TPHydrateFlash(fluid);
    flash.run();
    assertTrue(flash.isHydrateFormed());
    assertBalanced(fluid);
  }

  private static final Logger logger = LogManager.getLogger(TPHydrateFlashBalanceTest.class);

  @Test
  void temperatureSweepSolvesFugacityAndBothCavityBalances() throws java.io.IOException {
    java.util.List<String> evidence = new java.util.ArrayList<String>();
    evidence.add("temperature_K,hydrate_fraction,structure,log_fugacity_residual,max_balance_residual,fluid_flashes");
    double previous = Double.NaN;
    for (double temperature : new double[] {288.15, 283.15, 278.15}) {
      SystemInterface fluid = mixture(temperature);
      TPHydrateFlash flash = new TPHydrateFlash(fluid);
      flash.run();
      assertEquilibrium(fluid, flash);
      double fraction = flash.getHydrateFraction();
      assertTrue(Double.isNaN(previous) || Math.abs(fraction - previous) > 1e-7,
          "Hydrate fraction must respond to temperature");
      previous = fraction;
      evidence.add(temperature + "," + fraction + "," + flash.getStableHydrateStructure() + ","
          + flash.getLastResidual() + "," + flash.getMaximumBalanceResidual() + "," + flash.getFluidFlashCount());
      logger.info("Issue 3871: T={} K, hydrate fraction={}, structure={}, residual={}", temperature, fraction,
          flash.getStableHydrateStructure(), flash.getLastResidual());
    }
    java.nio.file.Files.write(java.nio.file.Paths.get("target/hydrate-3871-validation.csv"), evidence,
        java.nio.charset.StandardCharsets.UTF_8);
  }

  @Test
  void repeatedRunAndHeatingRestoreTheSameFeed() {
    SystemInterface fluid = mixture(283.15);
    TPHydrateFlash flash = new TPHydrateFlash(fluid);
    flash.run();
    double beta = flash.getHydrateFraction();
    flash.run();
    assertEquals(beta, flash.getHydrateFraction(), 1e-9);
    assertEquilibrium(fluid, flash);
    fluid.init(1);
    assertBalanced(fluid);
    fluid.setTemperature(330.0);
    flash.run();
    assertTrue(flash.isConverged());
    assertFalse(flash.isHydrateFormed());
    assertFalse(fluid.hasHydratePhase());
    assertEquals(101.15, fluid.getTotalNumberOfMoles(), 1e-10);
    assertBalanced(fluid);
    fluid.setTemperature(283.15);
    flash.run();
    assertEquals(beta, flash.getHydrateFraction(), 1e-9);
  }

  @Test
  void feedScaleAndInsertionOrderDoNotChangeEquilibrium() {
    double reference = Double.NaN;
    for (double scale : new double[] {1e-3, 1.0, 1e3}) {
      SystemInterface fluid = new SystemSrkEos(283.15, 100.0);
      fluid.addComponent("water", scale * 10.0);
      fluid.addComponent("propane", scale * 2.050);
      fluid.addComponent("ethane", scale * 10.10);
      fluid.addComponent("methane", scale * 79.0);
      fluid.setMixingRule(2);
      TPHydrateFlash flash = new TPHydrateFlash(fluid);
      flash.run();
      assertEquilibrium(fluid, flash);
      if (Double.isNaN(reference)) {
        reference = flash.getHydrateFraction();
      }
      assertEquals(reference, flash.getHydrateFraction(), 1e-8);
      assertEquals(scale * 101.15, fluid.getTotalNumberOfMoles(), scale * 1e-10);
    }
    SystemInterface fluid = mixture(283.15);
    TPHydrateFlash flash = new TPHydrateFlash(fluid);
    flash.run();
    assertEquals(reference, flash.getHydrateFraction(), 1e-8);
  }

  @Test
  void traceWaterRetainsEquilibriumVapourWater() {
    SystemInterface fluid = new SystemSrkCPAstatoil(258.15, 250.0);
    fluid.addComponent("methane", 0.9998);
    fluid.addComponent("water", 0.0002);
    fluid.setMixingRule(10);
    TPHydrateFlash flash = new TPHydrateFlash(fluid);
    flash.setGasHydrateOnlyMode(true);
    flash.run();
    assertEquilibrium(fluid, flash);
    assertFalse(fluid.hasPhaseType(PhaseType.AQUEOUS));
    double vapourWater = fluid.getPhase(PhaseType.GAS).getComponent("water").getNumberOfMolesInPhase();
    assertTrue(vapourWater > 0.0 && vapourWater < 0.0002);
  }

  @Test
  void guestLimitedWaterRichFeedHasAnInteriorSolution() {
    SystemInterface fluid = new SystemSrkEos(278.15, 100.0);
    fluid.addComponent("methane", 0.01);
    fluid.addComponent("water", 0.99);
    fluid.setMixingRule(2);
    TPHydrateFlash flash = new TPHydrateFlash(fluid);
    flash.run();
    assertEquilibrium(fluid, flash);
    assertTrue(fluid.hasPhaseType(PhaseType.AQUEOUS));
    assertTrue(flash.getHydrateFraction() < 0.10, "Methane supply limits hydrate formation");
  }

  @Test
  void noWaterAndNoGuestsRemainHydrateFree() {
    for (String component : new String[] {"methane", "water"}) {
      SystemInterface fluid = new SystemSrkEos(278.15, 100.0);
      fluid.addComponent(component, 1.0);
      fluid.setMixingRule(2);
      TPHydrateFlash flash = new TPHydrateFlash(fluid);
      flash.run();
      assertTrue(flash.isConverged());
      assertFalse(flash.isHydrateFormed());
      assertBalanced(fluid);
    }
  }

  @Test
  void inhibitorRemainsInTheResidualFluid() {
    SystemInterface fluid = new SystemSrkCPAstatoil(278.15, 80.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("water", 0.095);
    fluid.addComponent("MEG", 0.005);
    fluid.setMixingRule(10);
    TPHydrateFlash flash = new TPHydrateFlash(fluid);
    flash.run();
    assertEquilibrium(fluid, flash);
    assertEquals(0.0, fluid.getPhase(PhaseType.HYDRATE).getComponent("MEG").getx(), 0.0);
    assertTrue(fluid.hasPhaseType(PhaseType.AQUEOUS));
  }

  @Test
  void exhaustedBudgetLeavesTheInputUntouched() {
    SystemInterface fluid = mixture(283.15);
    new ThermodynamicOperations(fluid).TPflash();
    SystemInterface before = fluid.clone();
    TPHydrateFlash flash = new TPHydrateFlash(fluid);
    flash.setMaximumIterations(1);
    assertThrows(IllegalStateException.class, flash::run);
    assertFalse(flash.isConverged());
    assertFalse(flash.isHydrateFormed());
    assertTrue(Double.isNaN(flash.getLastResidual()));
    assertEquals(before.getNumberOfPhases(), fluid.getNumberOfPhases());
    assertEquals(before.getTotalNumberOfMoles(), fluid.getTotalNumberOfMoles(), 0.0);
    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      assertEquals(before.getBeta(p), fluid.getBeta(p), 0.0);
      for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
        assertEquals(before.getPhase(p).getComponent(i).getx(), fluid.getPhase(p).getComponent(i).getx(), 0.0);
        assertEquals(before.getPhase(p).getComponent(i).getNumberOfMolesInPhase(),
            fluid.getPhase(p).getComponent(i).getNumberOfMolesInPhase(), 0.0);
      }
    }
    assertThrows(IllegalArgumentException.class, () -> flash.setMaximumIterations(0));
    flash.setMaximumIterations(100);
    flash.run();
    assertEquilibrium(fluid, flash);
  }

  private void assertEquilibrium(SystemInterface fluid, TPHydrateFlash flash) {
    assertTrue(flash.isConverged());
    assertTrue(flash.isHydrateFormed());
    assertEquals(0.0, flash.getLastResidual(), 1e-8);
    assertBalanced(fluid);
    PhaseHydrate hydrate = (PhaseHydrate) fluid.getPhase(PhaseType.HYDRATE);
    assertEquals(flash.getHydrateFraction(), fluid.getHydrateFraction(), 1e-12);
    assertEquals(hydrate.getStableHydrateStructure(), flash.getStableHydrateStructure());
    PhaseInterface waterPhase = fluid.getPhase(0);
    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      PhaseInterface phase = fluid.getPhase(p);
      if (phase.getType() != PhaseType.HYDRATE
          && phase.getComponent("water").getx() > waterPhase.getComponent("water").getx()) {
        waterPhase = phase;
      }
    }
    double hostFugacity = hydrate.getComponent("water").fugcoef(hydrate) * fluid.getPressure();
    assertEquals(0.0, Math.log(hostFugacity / waterPhase.getFugacity("water")), 1e-8);
    int structure = flash.getStableHydrateStructure();
    double waterCount = structure == 1 ? 46.0 : 136.0;
    double smallCount = structure == 1 ? 2.0 : 16.0;
    double largeCount = structure == 1 ? 6.0 : 8.0;
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      if (!hydrate.getComponent(i).isHydrateFormer()) {
        continue;
      }
      String name = hydrate.getComponent(i).getName();
      double guestsPerCell = smallCount * flash.getCavityOccupancy(name, structure, 0)
          + largeCount * flash.getCavityOccupancy(name, structure, 1);
      assertEquals(guestsPerCell / waterCount, hydrate.getComponent(i).getx() / hydrate.getComponent("water").getx(),
          1e-10, name);
    }
  }

  private SystemInterface mixture(double temperature) {
    SystemInterface fluid = new SystemSrkEos(temperature, 100.0);
    fluid.addComponent("methane", 79.0);
    fluid.addComponent("ethane", 10.10);
    fluid.addComponent("propane", 2.050);
    fluid.addComponent("water", 10.0);
    fluid.setMixingRule(2);
    fluid.setMultiPhaseCheck(true);
    fluid.setHydrateCheck(true);
    return fluid;
  }

  private void assertBalanced(SystemInterface fluid) {
    double betaSum = 0.0;
    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      PhaseInterface phase = fluid.getPhase(p);
      betaSum += fluid.getBeta(p);
      double sum = 0.0;
      for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
        sum += phase.getComponent(i).getx();
      }
      assertEquals(1.0, sum, 1e-9, "Phase composition: " + phase.getType());
    }
    assertEquals(1.0, betaSum, 1e-10);
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      double amount = 0.0;
      double moles = 0.0;
      for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
        amount += fluid.getBeta(p) * fluid.getPhase(p).getComponent(i).getx();
        moles += fluid.getPhase(p).getComponent(i).getNumberOfMolesInPhase();
      }
      assertEquals(fluid.getComponent(i).getz(), amount, 1e-9, fluid.getComponent(i).getName());
      assertEquals(fluid.getComponent(i).getNumberOfmoles(), moles, 1e-9 * fluid.getTotalNumberOfMoles(),
          fluid.getComponent(i).getName() + " moles");
    }
  }
}
