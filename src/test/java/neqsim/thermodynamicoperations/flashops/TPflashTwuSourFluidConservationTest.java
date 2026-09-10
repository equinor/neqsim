package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Regression for the wet PR/Twu sour-fluid mass gain reported in issue #3624. */
class TPflashTwuSourFluidConservationTest {
  /** Build the exact reported composition, including the original TBP cut names and units. */
  private SystemInterface createFluid(double temperature, double pressure) {
    SystemInterface fluid = new SystemPrEos(temperature, pressure);
    String[] names = { "CO2", "methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane", "n-pentane",
        "n-hexane", "H2S", "water" };
    double[] amounts = { 1.587, 52.01, 6.24, 4.23, 0.855, 2.213, 1.124, 1.271, 2.289, 0.5, 5.0 };
    for (int i = 0; i < names.length; i++) {
      fluid.addComponent(names[i], amounts[i]);
    }
    double[][] cuts = { { 0.8501, 108.47, 0.7411 }, { 1.2802, 120.4, 0.755 }, { 1.6603, 133.64, 0.7695 },
        { 6.5311, 164.7, 0.799 }, { 6.3311, 215.94, 0.8387 }, { 4.9618, 273.34, 0.8754 }, { 2.9105, 334.92, 0.90731 },
        { 3.0505, 412.79, 0.94575 } };
    fluid.getCharacterization().setTBPModel("Twu");
    for (int i = 0; i < cuts.length; i++) {
      fluid.addTBPfraction("C7+_cut" + (i + 1), cuts[i][0], cuts[i][1] / 1000.0, cuts[i][2]);
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    fluid.setTotalFlowRate(100000.0, "kg/hr");
    return fluid;
  }

  /** Recreate the pre-fix characterization, independently of the repaired Twu implementation. */
  private SystemInterface createLegacyInvalidFluid() {
    SystemInterface fluid = createFluid(343.15, 33.0);
    double[][] legacyProperties = { { 528.700712930119, 26.751596003314564, 0.6462775091350768 },
        { 548.0577654655797, 25.041662851847313, 0.7088168453770576 },
        { 566.8813124869656, 23.377270637261827, 0.7930589562220649 },
        { 603.4363531055359, 20.19968363526789, 1.0363562268358848 },
        { 648.0280681595877, 16.45956579232401, 1.630368181510991 },
        { 683.4600828341196, 13.620771510979344, 2.8876883670039093 },
        { 712.174783859551, 11.476034995880262, 6.4537267962023845 },
        { 736.9362177688092, 9.637248269519539, -38.44693197351764 } };
    for (PhaseInterface phase : fluid.getPhases()) {
      if (phase == null) {
        continue;
      }
      for (int i = 0; i < legacyProperties.length; i++) {
        ComponentInterface component = phase.getComponent(11 + i);
        component.setTC(legacyProperties[i][0]);
        component.setPC(legacyProperties[i][1]);
        component.setAcentricFactor(legacyProperties[i][2]);
        component.setAttractiveTerm(component.getAttractiveTermNumber());
        component.setRacketZ(0.29056 - 0.08775 * legacyProperties[i][2]);
      }
    }
    return fluid;
  }

  /** Check stored phase inventories, phase fractions, compositions, and independent feed amounts. */
  private void assertConservative(SystemInterface expected, SystemInterface actual) {
    double betaSum = 0.0;
    double massFlow = 0.0;
    for (int p = 0; p < actual.getNumberOfPhases(); p++) {
      PhaseInterface phase = actual.getPhase(p);
      double beta = actual.getBeta(p);
      assertTrue(Double.isFinite(beta) && beta >= 0.0 && beta <= 1.0);
      betaSum += beta;
      massFlow += phase.getFlowRate("kg/hr");
      double compositionSum = 0.0;
      for (int i = 0; i < actual.getNumberOfComponents(); i++) {
        double x = phase.getComponent(i).getx();
        assertTrue(Double.isFinite(x) && x >= 0.0 && x <= 1.0);
        compositionSum += x;
      }
      assertEquals(1.0, compositionSum, 1.0e-10);
    }
    assertEquals(1.0, betaSum, 1.0e-10);
    assertEquals(expected.getFlowRate("kg/hr"), massFlow, 1.0e-5);
    for (int i = 0; i < expected.getNumberOfComponents(); i++) {
      double recovered = 0.0;
      for (int p = 0; p < actual.getNumberOfPhases(); p++) {
        recovered += actual.getPhase(p).getComponent(i).getNumberOfMolesInPhase();
      }
      double feedMoles = expected.getComponent(i).getNumberOfmoles();
      assertEquals(feedMoles, recovered, Math.max(1.0e-10, feedMoles * 1.0e-9),
          expected.getComponent(i).getComponentName());
    }
  }

  @Test
  void unrecoverableLegacyCharacterizationFailsExplicitly() {
    SystemInterface fluid = createLegacyInvalidFluid();
    SystemInterface inventory = fluid.clone();
    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> new ThermodynamicOperations(fluid).TPflash());
    assertTrue(failure.getMessage().contains("TPflash"));
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      assertEquals(inventory.getComponent(i).getNumberOfmoles(), fluid.getComponent(i).getNumberOfmoles(), 1.0e-10);
    }
    Stream feed = new Stream("invalid feed", createLegacyInvalidFluid());
    assertThrows(IllegalStateException.class, () -> feed.run());
    ThreePhaseSeparator separator = new ThreePhaseSeparator("invalid separator",
        new Stream("invalid feed", createLegacyInvalidFluid()));
    assertThrows(IllegalStateException.class, () -> separator.run());
  }

  @Test
  void exactReportedSeparatorConservesEveryComponent() {
    SystemInterface fluid = createFluid(343.15, 33.0);
    SystemInterface inventory = fluid.clone();
    Stream feed = new Stream("sour well feed", fluid);
    feed.run();
    assertConservative(inventory, feed.getFluid());
    ThreePhaseSeparator separator = new ThreePhaseSeparator("separator", feed);
    separator.run();
    StreamInterface[] outlets = { separator.getGasOutStream(), separator.getOilOutStream(),
        separator.getWaterOutStream() };
    double totalFlow = 0.0;
    for (StreamInterface outlet : outlets) {
      assertTrue(outlet.getFlowRate("kg/hr") > 0.0);
      totalFlow += outlet.getFlowRate("kg/hr");
      assertConservative(outlet.getFluid(), outlet.getFluid());
    }
    assertEquals(100000.0, totalFlow, 1.0e-5);
    for (int i = 0; i < inventory.getNumberOfComponents(); i++) {
      double outletMoles = 0.0;
      for (StreamInterface outlet : outlets) {
        outletMoles += outlet.getFluid().getComponent(i).getNumberOfmoles();
      }
      assertEquals(inventory.getComponent(i).getNumberOfmoles(), outletMoles, 1.0e-8,
          inventory.getComponent(i).getComponentName());
    }
  }

  @ParameterizedTest
  @CsvSource({ "338.15, 30.0", "343.15, 33.0", "348.15, 36.0" })
  void repeatedAndClonedFlashesConserveAtNearbyConditions(double temperature, double pressure) {
    SystemInterface fluid = createFluid(temperature, pressure);
    SystemInterface inventory = fluid.clone();
    for (int iteration = 0; iteration < 3; iteration++) {
      new ThermodynamicOperations(fluid).TPflash();
      assertConservative(inventory, fluid);
      assertEquals(3, fluid.getNumberOfPhases());
      assertTrue(fluid.hasPhaseType("gas"));
      assertTrue(fluid.hasPhaseType("oil"));
      assertTrue(fluid.hasPhaseType("aqueous"));
      for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
        double reference = Math.log(fluid.getPhase(0).getComponent(i).getx())
            + fluid.getPhase(0).getComponent(i).getLogFugacityCoefficient();
        for (int p = 1; p < fluid.getNumberOfPhases(); p++) {
          double logFugacity = Math.log(fluid.getPhase(p).getComponent(i).getx())
              + fluid.getPhase(p).getComponent(i).getLogFugacityCoefficient();
          assertEquals(reference, logFugacity, 1.0e-7, fluid.getComponent(i).getComponentName());
        }
      }
      fluid = fluid.clone();
    }
  }
}
