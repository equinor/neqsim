package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.database.NeqSimDataBase;

/** Internal consistency regressions for issue #3709; these are not experimental wax benchmarks. */
class WATConsistencyTest extends neqsim.NeqSimTest {
  private static final Logger logger = LogManager.getLogger(WATConsistencyTest.class);

  static SystemInterface syntheticOil(boolean water) {
    boolean temporaryTables = NeqSimDataBase.createTemporaryTables();
    NeqSimDataBase.setCreateTemporaryTables(true);
    try {
      SystemInterface fluid = new SystemSrkEos(298.0, 10.0);
      fluid.addComponent("methane", 6.78);
      if (water) {
        fluid.addComponent("water", 2.0);
      }
      fluid.addTBPfraction("C19", 10.13, 0.170, 0.7814);
      fluid.addPlusFraction("C20", 10.62, 0.381, 0.850871882888);
      fluid.setMixingRule("classic");
      fluid.getCharacterization().characterisePlusFraction();
      fluid.getWaxModel().addTBPWax();
      fluid.createDatabase(true);
      fluid.setMixingRule("classic");
      fluid.addSolidComplexPhase("wax");
      fluid.setMultiphaseWaxCheck(true);
      fluid.setMultiPhaseCheck(true);
      fluid.init(0);
      fluid.init(1);
      return fluid;
    } finally {
      NeqSimDataBase.setCreateTemporaryTables(temporaryTables);
    }
  }

  static SystemInterface flash(SystemInterface fluid, double temperatureC) {
    SystemInterface trial = fluid.clone();
    trial.setTemperature(temperatureC, "C");
    new ThermodynamicOperations(trial).TPflash();
    return trial;
  }

  @Test
  void reportedOilMustAgreeWithFreshFlashAppearance() throws Exception {
    SystemInterface fluid = syntheticOil(false);
    fluid.setPressure(60.0);
    assertFalse(flash(fluid, 66.0).hasPhaseType("wax"));
    assertTrue(flash(fluid, 64.0).getPhaseFraction("wax", "mass") > 0.004);
    SystemInterface direct = fluid.clone();
    direct.setTemperature(323.15);
    new ThermodynamicOperations(direct).calcWAT();
    double watC = direct.getTemperature("C");
    assertTrue(watC > 64.0 && watC < 66.0, "WAT must lie within the TP appearance bracket: " + watC);
    logger.info("Issue #3709 corrected WAT at 60 bara: {} C", watC);
    assertConserved(fluid, direct);
  }

  @ParameterizedTest
  @CsvSource({ "40,false", "60,false", "80,false", "40,true", "60,true", "80,true" })
  void pressureWaterAndStartingGuessesAgreeWithIndependentBracket(double pressure, boolean water) throws Exception {
    SystemInterface fluid = syntheticOil(water);
    fluid.setPressure(pressure);
    double cold = 50.0;
    double warm = 80.0;
    assertTrue(waxFraction(flash(fluid, cold)) > 1e-8);
    assertTrue(waxFraction(flash(fluid, warm)) <= 1e-8);
    // Independent fresh-clone TP scan: different initial interval and no WAT implementation calls.
    while (warm - cold > 0.001) {
      double midpoint = (warm + cold) / 2.0;
      if (waxFraction(flash(fluid, midpoint)) > 1e-8) {
        cold = midpoint;
      } else {
        warm = midpoint;
      }
    }
    for (double guess : new double[] { 35.0, 50.0, 90.0 }) {
      SystemInterface direct = fluid.clone();
      direct.setTemperature(guess, "C");
      new ThermodynamicOperations(direct).calcWAT();
      double wat = direct.getTemperature("C");
      assertTrue(wat >= cold - 0.001 && wat <= warm + 0.001,
          "WAT=" + wat + " C, bracket=[" + cold + ", " + warm + "] C, P=" + pressure + ", water=" + water);
      assertConserved(fluid, direct);
      if (water) {
        assertTrue(direct.hasPhaseType("aqueous"));
      }
      assertTrue(waxFraction(flash(fluid, wat + 0.01)) <= 1e-8);
      assertTrue(waxFraction(flash(fluid, wat - 0.01)) > 1e-8);
      logger.info("WAT: P={} bara, water={}, initial={} C, result={} C, TP bracket=[{}, {}] C", pressure, water, guess,
          wat, cold, warm);
    }
  }

  @Test
  void reorderedAndPreviouslyWaxBearingStatesRemainReusable() throws Exception {
    SystemInterface fluid = syntheticOil(true);
    fluid.setPressure(60.0);
    SystemInterface direct = flash(fluid, 50.0);
    assertTrue(direct.hasPhaseType("wax"));
    int first = direct.getPhaseIndex(0);
    direct.setPhaseIndex(0, direct.getPhaseIndex(1));
    direct.setPhaseIndex(1, first);
    ThermodynamicOperations ops = new ThermodynamicOperations(direct);
    ops.calcWAT();
    double wat = direct.getTemperature();
    assertConserved(fluid, direct);
    ops.calcWAT();
    assertEquals(wat, direct.getTemperature(), 0.001);
    assertConserved(fluid, direct);
    direct.setTemperature(50.0, "C");
    ops.TPflash();
    assertTrue(direct.hasPhaseType("wax"));
    assertConserved(fluid, direct);
  }

  @Test
  void configuredWaxPhaseWorksWithWaxCheckDisabledAndPreservesOptions() throws Exception {
    SystemInterface direct = syntheticOil(false);
    direct.setPressure(60.0);
    direct.setMultiphaseWaxCheck(false);
    direct.setMultiPhaseCheck(false);
    new ThermodynamicOperations(direct).calcWAT();
    assertFalse(direct.isMultiphaseWaxCheck());
    assertFalse(direct.doMultiPhaseCheck());
    assertTrue(direct.getTemperature("C") > 64.0 && direct.getTemperature("C") < 66.0);
  }

  @Test
  void missingWaxConfigurationAndInvalidTemperatureFailWithoutChangingState() {
    SystemInterface fluid = new SystemSrkEos(300.0, 60.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    assertThrows(IllegalArgumentException.class, () -> new ThermodynamicOperations(fluid).calcWAT());
    assertEquals(300.0, fluid.getTemperature());
    fluid.addSolidComplexPhase("wax");
    assertThrows(IllegalArgumentException.class, () -> new ThermodynamicOperations(fluid).calcWAT());
    SystemInterface waxFluid = syntheticOil(false);
    waxFluid.setTemperature(50.0);
    assertThrows(IllegalArgumentException.class, () -> new ThermodynamicOperations(waxFluid).calcWAT());
    assertEquals(50.0, waxFluid.getTemperature());
  }

  @Test
  void absenceOfNumericallyDetectableWaxFailsWithEvidenceAndLeavesFeedIntact() {
    SystemInterface fluid = syntheticOil(false);
    double[] composition = fluid.getzvector();
    java.util.Arrays.fill(composition, 1e-30);
    composition[0] = 1.0;
    fluid.setMolarComposition(composition);
    fluid.setTemperature(100.0);
    fluid.setPressure(60.0);
    SystemInterface before = fluid.clone();
    IllegalStateException error = assertThrows(IllegalStateException.class,
        () -> new ThermodynamicOperations(fluid).calcWAT());
    assertTrue(error.getMessage().contains("bracket="));
    assertTrue(error.getMessage().contains("TP evaluations="));
    assertTrue(error.getMessage().contains("pressure=60.0 bara"));
    assertEquals(before.getTemperature(), fluid.getTemperature());
    assertEquals(before.getNumberOfPhases(), fluid.getNumberOfPhases());
    assertArrayEquals(before.getzvector(), fluid.getzvector(), 0.0);
    for (int phase = 0; phase < before.getNumberOfPhases(); phase++) {
      assertEquals(before.getBeta(phase), fluid.getBeta(phase));
      assertArrayEquals(before.getPhase(phase).getMolarComposition(), fluid.getPhase(phase).getMolarComposition(), 0.0);
    }
  }

  private static double waxFraction(SystemInterface fluid) {
    return fluid.hasPhaseType("wax") ? fluid.getPhaseFraction("wax", "mass") : 0.0;
  }

  private static void assertConserved(SystemInterface feed, SystemInterface result) {
    assertEquals(feed.getPressure(), result.getPressure(), 0.0);
    assertEquals(feed.getTotalNumberOfMoles(), result.getTotalNumberOfMoles(), 1e-10);
    assertArrayEquals(feed.getzvector(), result.getzvector(), 1e-12);
    boolean[] usedSlots = new boolean[result.getMaxNumberOfPhases()];
    double totalMass = 0.0;
    double betaSum = 0.0;
    double[] componentMoles = new double[feed.getNumberOfComponents()];
    for (int phase = 0; phase < result.getNumberOfPhases(); phase++) {
      int slot = result.getPhaseIndex(phase);
      assertFalse(usedSlots[slot], "Active phases must have distinct storage slots");
      usedSlots[slot] = true;
      assertTrue(result.getBeta(phase) > 0.0 && result.getBeta(phase) <= 1.0);
      betaSum += result.getBeta(phase);
      totalMass += result.getPhase(phase).getNumberOfMolesInPhase() * result.getPhase(phase).getMolarMass();
      for (int component = 0; component < componentMoles.length; component++) {
        componentMoles[component] += result.getPhase(phase).getComponent(component).getNumberOfMolesInPhase();
      }
    }
    assertEquals(1.0, betaSum, 1e-8);
    assertEquals(feed.getTotalNumberOfMoles() * feed.getMolarMass(), totalMass, 1e-6);
    for (int component = 0; component < componentMoles.length; component++) {
      assertEquals(feed.getPhase(0).getComponent(component).getNumberOfmoles(), componentMoles[component], 1e-6);
    }
  }
}
