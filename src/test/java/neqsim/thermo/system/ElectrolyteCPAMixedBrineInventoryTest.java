package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Method;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermodynamicoperations.flashops.TPflash;
import neqsim.thermodynamicoperations.flashops.saturationops.HydrateFormationTemperatureFlash;

/** Component conservation regressions for non-reactive mixed brines (issue #3585). */
class ElectrolyteCPAMixedBrineInventoryTest extends neqsim.NeqSimTest {
  private static final Logger logger = LogManager.getLogger(ElectrolyteCPAMixedBrineInventoryTest.class);

  @Test
  void calciumBrineHydratePreservesInventory() throws Exception {
    SystemInterface fluid = createBrine(50.0, 3.0, 0.6, "Ca++", false);
    new ThermodynamicOperations(fluid).hydrateFormationTemperature();
    logger.info("Mixed brine hydrate temperature: {} C", fluid.getTemperature("C"));
    assertInventory(fluid);
    assertHydrateResidual(fluid);
    assertEquals(8.16097, fluid.getTemperature("C"), 1.0e-3,
        "Numerical regression only, not an experimental temperature benchmark");
  }

  @ParameterizedTest
  @CsvSource({ "40, 3, 0.6, Ca++", "60, 3, 0.6, Ca++", "50, 1.5, 0.3, Ca++", "50, 4, 1.2, Ca++", "40, 3, 2, K+",
      "50, 3, 2, K+", "60, 3, 2, K+" })
  void nearbyAndMonovalentBrinesConserveInventory(double pressure, double nacl, double secondSalt, String cation)
      throws Exception {
    SystemInterface fluid = createBrine(pressure, nacl, secondSalt, cation, false);
    new ThermodynamicOperations(fluid).hydrateFormationTemperature();
    assertInventory(fluid);
    assertHydrateResidual(fluid);
    logger.info("{} + NaCl at {} bara: {} C", cation, pressure, fluid.getTemperature("C"));
  }

  @ParameterizedTest
  @ValueSource(strings = { "Ca++", "K+" })
  void additionOrderAndRepeatedRunsMatchFreshFluid(String cation) throws Exception {
    double secondSalt = "Ca++".equals(cation) ? 0.6 : 2.0;
    SystemInterface reference = createBrine(50.0, 3.0, secondSalt, cation, false);
    SystemInterface reordered = createBrine(50.0, 3.0, secondSalt, cation, true);
    new ThermodynamicOperations(reference).hydrateFormationTemperature();
    new ThermodynamicOperations(reordered).hydrateFormationTemperature();
    assertEquivalent(reference, reordered);
    new ThermodynamicOperations(reordered).hydrateFormationTemperature();
    assertEquivalent(reference, reordered);
    reordered.setPressure(40.0);
    new ThermodynamicOperations(reordered).hydrateFormationTemperature();
    SystemInterface fresh = createBrine(40.0, 3.0, secondSalt, cation, false);
    new ThermodynamicOperations(fresh).hydrateFormationTemperature();
    assertEquivalent(fresh, reordered);
  }

  @Test
  void intermediateTpFlashesConserveBothBrines() {
    for (String cation : new String[] { "Ca++", "K+" }) {
      SystemInterface fluid = createBrine(50.0, 3.0, "Ca++".equals(cation) ? 0.6 : 2.0, cation, false);
      ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
      for (double temperature : new double[] { 263.0, 271.0, 281.3, 290.0, 281.3 }) {
        fluid.setTemperature(temperature);
        ops.TPflash();
        assertInventory(fluid);
      }
    }
  }

  @Test
  void lowerGibbsOfUnbalancedInventoryCannotVetoIonicRefinement() throws Exception {
    SystemInterface fluid = createBrine(50.0, 3.0, 0.6, "Ca++", false);
    new ThermodynamicOperations(fluid).hydrateFormationTemperature();
    SystemInterface reference = fluid.clone();
    double referenceGibbs = fluid.getGibbsEnergy();
    double co2 = fluid.getPhase("aqueous").getComponent("CO2").getx();
    double water = fluid.getPhase("aqueous").getComponent("water").getx();
    fluid.getPhase("aqueous").getComponent("CO2").setx(co2 - 0.001);
    fluid.getPhase("aqueous").getComponent("water").setx(water + 0.001);
    fluid.init(1);
    assertTrue(fluid.getGibbsEnergy() < referenceGibbs,
        "The infeasible state reproduces the former lower-Gibbs rejection condition");
    Method refinement = TPflash.class.getDeclaredMethod("refineIonicGasAqueousEndpoint");
    refinement.setAccessible(true);
    refinement.invoke(new TPflash(fluid));
    assertEquivalent(reference, fluid);
  }

  @ParameterizedTest
  @ValueSource(strings = { "component", "phase", "normalization", "ion" })
  void invalidFluidStateThrowsAndRestoresCallerSetting(final String fault) {
    final SystemInterface fluid = createBrine(50.0, 3.0, 0.6, "Ca++", false);
    fluid.setMultiPhaseCheck(false);
    HydrateFormationTemperatureFlash flash = new HydrateFormationTemperatureFlash(fluid) {
      private static final long serialVersionUID = 1L;

      @Override
      public void setFug() {
        super.setFug();
        int aqueous = fluid.getPhaseNumberOfPhase("aqueous");
        if ("phase".equals(fault)) {
          fluid.setBeta(aqueous, Double.NaN);
        } else if ("normalization".equals(fault)) {
          fluid.getPhase(aqueous).getComponent("water").setx(0.5);
        } else if ("ion".equals(fault)) {
          int other = aqueous == 0 ? 1 : 0;
          fluid.getPhase(other).getComponent("Na+").setx(0.001);
        } else {
          double co2 = fluid.getPhase(aqueous).getComponent("CO2").getx();
          double water = fluid.getPhase(aqueous).getComponent("water").getx();
          fluid.getPhase(aqueous).getComponent("CO2").setx(co2 + 0.001);
          fluid.getPhase(aqueous).getComponent("water").setx(water - 0.001);
        }
      }
    };
    IllegalStateException failure = assertThrows(IllegalStateException.class, flash::run);
    assertTrue(failure.getMessage().startsWith("Hydrate fluid inventory"), failure.getMessage());
    if ("component".equals(fault)) {
      assertTrue(failure.getMessage().contains("CO2"), failure.getMessage());
    }
    assertFalse(fluid.doMultiPhaseCheck(), "Caller setting must also survive a failed evaluation");
  }

  private static void assertEquivalent(SystemInterface expected, SystemInterface actual) {
    assertInventory(expected);
    assertInventory(actual);
    assertHydrateResidual(expected);
    assertHydrateResidual(actual);
    assertEquals(expected.getTemperature(), actual.getTemperature(), 1.0e-4);
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases());
    for (int phase = 0; phase < expected.getNumberOfPhases(); phase++) {
      PhaseType type = expected.getPhase(phase).getType();
      assertTrue(actual.hasPhaseType(type));
      assertEquals(expected.getPhase(phase).getBeta(), actual.getPhase(type).getBeta(), 1.0e-7);
      for (int component = 0; component < expected.getPhase(phase).getNumberOfComponents(); component++) {
        String name = expected.getPhase(phase).getComponent(component).getComponentName();
        assertEquals(expected.getPhase(phase).getComponent(component).getx(),
            actual.getPhase(type).getComponent(name).getx(), 1.0e-7, name);
      }
    }
  }

  private static void assertHydrateResidual(SystemInterface fluid) {
    assertTrue(Double.isFinite(fluid.getTemperature()));
    double residual = 1.0 - fluid.getPhase(4).getFugacity("water") / fluid.getPhase("aqueous").getFugacity("water");
    assertEquals(0.0, residual, 1.0e-6, "Hydrate-water fugacity residual");
    assertTrue(fluid.getNumberOfPhases() > 1, "The regression cases must retain a CO2-rich material phase");
  }

  private static SystemInterface createBrine(double pressure, double naclWt, double secondSaltWt, String cation,
      boolean reverseOrder) {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(283.15, pressure);
    fluid.addComponent("CO2", 10.0);
    fluid.addComponent("water", 1.0 / 0.01801528);
    double waterWt = 100.0 - naclWt - secondSaltWt;
    double nacl = (naclWt / waterWt) / 0.05844277;
    double secondSalt = (secondSaltWt / waterWt) / ("Ca++".equals(cation) ? 0.11098 : 0.0745513);
    double chloride = secondSalt * ("Ca++".equals(cation) ? 2.0 : 1.0);
    if (reverseOrder) {
      fluid.addComponent("Cl-", chloride);
      fluid.addComponent(cation, secondSalt);
      fluid.addComponent("Cl-", nacl);
      fluid.addComponent("Na+", nacl);
    } else {
      fluid.addComponent("Na+", nacl);
      fluid.addComponent("Cl-", nacl);
      fluid.addComponent(cation, secondSalt);
      fluid.addComponent("Cl-", chloride);
    }
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    fluid.setHydrateCheck(true);
    return fluid;
  }

  private static void assertInventory(SystemInterface fluid) {
    assertTrue(fluid.hasPhaseType(PhaseType.AQUEOUS));
    assertEquals(10.0, fluid.getPhase(0).getComponent("CO2").getNumberOfmoles(), 1.0e-12);
    assertEquals(1.0 / 0.01801528, fluid.getPhase(0).getComponent("water").getNumberOfmoles(), 1.0e-12);
    double betaSum = 0.0;
    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      double beta = fluid.getBeta(p);
      assertTrue(Double.isFinite(beta) && beta >= 0.0 && beta <= 1.0);
      betaSum += beta;
      double xSum = 0.0;
      double charge = 0.0;
      for (int i = 0; i < fluid.getPhase(p).getNumberOfComponents(); i++) {
        double x = fluid.getPhase(p).getComponent(i).getx();
        assertTrue(Double.isFinite(x) && x >= 0.0 && x <= 1.0);
        xSum += x;
        double ionCharge = fluid.getPhase(p).getComponent(i).getIonicCharge();
        charge += x * ionCharge;
        if (ionCharge != 0 && fluid.getPhase(p).getType() != PhaseType.AQUEOUS) {
          assertTrue(x < 1.0e-40, "Ions must remain in the aqueous phase");
        }
      }
      assertEquals(1.0, xSum, 1.0e-9);
      assertEquals(0.0, charge, 1.0e-10);
    }
    assertEquals(1.0, betaSum, 1.0e-12);
    for (int i = 0; i < fluid.getPhase(0).getNumberOfComponents(); i++) {
      double recovered = 0.0;
      for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
        recovered += fluid.getBeta(p) * fluid.getPhase(p).getComponent(i).getx();
      }
      assertEquals(fluid.getPhase(0).getComponent(i).getz(), recovered, 1.0e-9,
          fluid.getPhase(0).getComponent(i).getComponentName());
      assertEquals(fluid.getPhase(0).getComponent(i).getNumberOfmoles() / fluid.getTotalNumberOfMoles(), recovered,
          1.0e-9, "The molecular-basis transformation must preserve the full feed");
    }
  }
}
