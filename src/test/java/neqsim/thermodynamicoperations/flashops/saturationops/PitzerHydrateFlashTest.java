package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhasePitzer;
import neqsim.thermo.phase.PitzerParameterDatasets;
import neqsim.thermo.system.SystemPitzer;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Integration tests of Pitzer water activity with hydrate equilibrium and the public operations API. */
public class PitzerHydrateFlashTest extends neqsim.NeqSimTest {
  private static final Logger logger = LogManager.getLogger(PitzerHydrateFlashTest.class);

  /** Creates a charge-balanced brine on a one-kilogram water basis. */
  private SystemPitzer brine(String guest, double sodium, double potassium, double calcium, double pressure) {
    SystemPitzer fluid = new SystemPitzer(280.0, pressure);
    fluid.addComponent(guest, 10.0);
    fluid.addComponent("water", 1.0 / 0.01801528);
    if (sodium > 0.0) {
      fluid.addComponent("Na+", sodium);
    }
    if (potassium > 0.0) {
      fluid.addComponent("K+", potassium);
    }
    if (calcium > 0.0) {
      fluid.addComponent("Ca++", calcium);
    }
    if (sodium + potassium + calcium > 0.0) {
      fluid.addComponent("Cl-", sodium + potassium + 2.0 * calcium);
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    if ("CO2".equals(guest) && sodium + potassium + calcium > 0.0) {
      // Explicit screening assumption for missing neutral ternary terms, never a silent catalog default.
      Map<String, Double> zeta = new LinkedHashMap<String, Double>();
      zeta.put("Na+", 0.0);
      zeta.put("K+", 0.0);
      zeta.put("Ca++", 0.0);
      fluid.applyPhreeqcCo2ChlorideParameters(zeta);
    }
    return fluid;
  }

  @Test
  void genericTemperatureFlashReportsPitzerConvergenceAndResidual() {
    SystemPitzer fluid = brine("CO2", 1.0, 0.0, 0.0, 30.0);
    HydrateFormationTemperatureFlash flash = new HydrateFormationTemperatureFlash(fluid);
    flash.run();
    assertTrue(flash.isConverged());
    double fugacityResidual = 1.0
        - fluid.getPhases()[4].getFugacity("water") / fluid.getPhase("aqueous").getFugacity("water");
    assertEquals(fugacityResidual, flash.getLastResidual(), 1.0e-14);
    assertEquals(0.0, flash.getLastResidual(), 1.0e-8);
  }

  @Test
  void co2TemperaturePressureRoundTripPreservesFluidRoles() throws Exception {
    SystemPitzer fluid = brine("CO2", 1.0, 0.0, 0.0, 30.0);
    PhaseInterface aqueousRole = fluid.getPhases()[1];
    PhaseInterface oilRole = fluid.getPhases()[2];
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.hydrateFormationTemperature();
    PitzerHydrateFlash temperatureFlash = (PitzerHydrateFlash) ops.getOperation();
    assertTrue(temperatureFlash.isConverged());
    assertEquals(0.0, temperatureFlash.getResidual(), 1.0e-8);
    assertTrue(temperatureFlash.getWaterActivity() < 0.98);
    double temperature = fluid.getTemperature();
    logger.info("CO2 + 1 m NaCl at 30 bara: hydrate T={} K, aw={}", temperature, temperatureFlash.getWaterActivity());
    assertTrue(temperature > 273.15 && temperature < 283.15);
    assertSame(aqueousRole, fluid.getPhases()[1]);
    assertSame(oilRole, fluid.getPhases()[2]);
    assertEquals(1, ((PhaseHydrate) fluid.getPhases()[4]).getStableHydrateStructure());
    fluid.setPressure(20.0);
    ops.hydrateFormationPressure();
    assertEquals(30.0, fluid.getPressure(), 0.002);
    assertEquals(temperature, fluid.getTemperature(), 1.0e-10);
  }

  @Test
  void saltsSuppressCo2HydratesAndMixedBrineRetainsIons() throws Exception {
    double[][] salts = { { 0, 0, 0 }, { 1, 0, 0 }, { 0, 1, 0 }, { 0, 0, 0.5 }, { 0.5, 0.3, 0.2 } };
    double pureTemperature = 0.0;
    for (int i = 0; i < salts.length; i++) {
      SystemPitzer fluid = brine("CO2", salts[i][0], salts[i][1], salts[i][2], 40.0);
      new ThermodynamicOperations(fluid).hydrateFormationTemperature();
      logger.info("Salt case {}: hydrate T={} K", i, fluid.getTemperature());
      if (i == 0) {
        pureTemperature = fluid.getTemperature();
      } else {
        assertTrue(fluid.getTemperature() < pureTemperature - 0.5);
        PhaseInterface aqueous = fluid.getPhase("aqueous");
        assertEquals(salts[i][0] + salts[i][1] + 2.0 * salts[i][2],
            aqueous.getComponent("Cl-").getNumberOfMolesInPhase(), 1.0e-6);
      }
    }
  }

  @Test
  void methaneAlsoUsesPitzerWaterActivity() throws Exception {
    SystemPitzer fresh = brine("methane", 0, 0, 0, 100.0);
    SystemPitzer saline = brine("methane", 1, 0, 0, 100.0);
    new ThermodynamicOperations(fresh).hydrateFormationTemperature();
    new ThermodynamicOperations(saline).hydrateFormationTemperature();
    assertTrue(fresh.getTemperature() > 280.0 && fresh.getTemperature() < 290.0);
    assertTrue(saline.getTemperature() < fresh.getTemperature() - 1.0);
  }

  /**
   * Independent experimental benchmark: Burgass et al. (2023), Table 4, doi:10.2516/stet/2023005, CC BY 4.0. Five mass
   * percent means salt/(salt+water), not salt/water. The 1 K engineering tolerance is larger than the reported 0.4 K
   * expanded measurement uncertainty and does not claim agreement within that uncertainty.
   */
  @Test
  void co2FiveWeightPercentNaClExperimentalBenchmark() throws Exception {
    double[][] points = { { 21.80, 275.9 }, { 25.99, 277.3 }, { 32.43, 278.9 }, { 42.26, 280.6 } };
    for (double[] point : points) {
      SystemPitzer fluid = brine("CO2", 0.05 / (0.95 * 0.05844), 0, 0, point[0]);
      new ThermodynamicOperations(fluid).hydrateFormationTemperature();
      logger.info("Burgass 2023: P={} bara, measured T={} K, calculated T={} K", point[0], point[1],
          fluid.getTemperature());
      assertEquals(point[1], fluid.getTemperature(), 1.0, "CO2 + 5 wt% NaCl at " + point[0] + " bara");
    }
  }

  @Test
  void liquidCo2AndCloneDoNotChangeOriginalHydrateOccupancy() throws Exception {
    SystemPitzer fluid = brine("CO2", 1, 0, 0, 100.0);
    new ThermodynamicOperations(fluid).hydrateFormationTemperature();
    PhaseHydrate hydrate = (PhaseHydrate) fluid.getPhases()[4];
    double occupancy = hydrate.getCavityOccupancy("CO2", 1, 1);
    double temperature = fluid.getTemperature();
    assertTrue(occupancy > 0.5 && occupancy < 1.0);
    assertTrue(fluid.hasPhaseType("oil"), "Must exercise the liquid CO2 branch");
    assertEquals(1.0, fluid.getPhase("oil").getFugacity("CO2") / fluid.getPhase("aqueous").getFugacity("CO2"), 1.0e-6);
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      double moles = 0.0;
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        moles += fluid.getPhase(phase).getComponent(component).getNumberOfMolesInPhase();
      }
      assertEquals(fluid.getPhase(0).getComponent(component).getNumberOfmoles(), moles, 1.0e-6);
    }
    SystemPitzer copy = fluid.clone();
    copy.setPressure(30.0);
    new ThermodynamicOperations(copy).hydrateFormationTemperature();
    assertEquals(temperature, fluid.getTemperature(), 0.0);
    assertEquals(occupancy, hydrate.getCavityOccupancy("CO2", 1, 1), 0.0);
  }

  @Test
  void curvePropagatesFailureAndPreservesOriginal() {
    SystemPitzer fluid = brine("CO2", 1, 0, 0, 30.0);
    HydrateEquilibriumLine curve = new HydrateEquilibriumLine(fluid, 1.0, 3.0);
    assertThrows(IllegalStateException.class, curve::run);
    assertEquals(null, curve.getPoints(0));
    assertEquals(280.0, fluid.getTemperature(), 0.0);
    assertEquals(30.0, fluid.getPressure(), 0.0);
  }

  @Test
  void curveCrossesGasAndLiquidCo2RegionsWithoutChangingFeed() {
    SystemPitzer fluid = brine("CO2", 0.5, 0.3, 0.2, 40.0);
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.hydrateEquilibriumLine(30.0, 100.0);
    double[][] points = ops.getOperation().getPoints(0);
    assertEquals(10, points[0].length);
    assertEquals(30.0, points[1][0], 0.0);
    assertEquals(100.0, points[1][9], 1.0e-10);
    for (int i = 0; i < points[0].length; i++) {
      assertTrue(Double.isFinite(points[0][i]));
      assertTrue(points[0][i] > 274.19 && points[0][i] < 290.0);
      if (i > 0) {
        assertTrue(points[0][i] > points[0][i - 1]);
      }
    }
    assertEquals(280.0, fluid.getTemperature(), 0.0);
    assertEquals(40.0, fluid.getPressure(), 0.0);
  }

  @Test
  void fixedStructureAndAutomaticSelectionCanBeReused() throws Exception {
    SystemPitzer fluid = brine("methane", 1, 0, 0, 100.0);
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.hydrateFormationTemperature(2);
    double structureTwoTemperature = fluid.getTemperature();
    assertEquals(2, ((PhaseHydrate) fluid.getPhases()[4]).getStableHydrateStructure());
    ops.hydrateFormationTemperature();
    assertEquals(1, ((PhaseHydrate) fluid.getPhases()[4]).getStableHydrateStructure());
    assertTrue(fluid.getTemperature() > structureTwoTemperature);
    assertThrows(IllegalArgumentException.class, () -> ops.hydrateFormationTemperature(0));
  }

  @Test
  void co2ChlorideSetupRequiresEveryExplicitZetaBeforeChangingParameters() {
    SystemPitzer fluid = new SystemPitzer(280.0, 40.0);
    fluid.addComponent("water", 55.508);
    fluid.addComponent("CO2", 10.0);
    fluid.addComponent("Na+", 0.5);
    fluid.addComponent("K+", 0.5);
    fluid.addComponent("Cl-", 1.0);
    fluid.setMixingRule("classic");
    PhasePitzer aqueous = (PhasePitzer) fluid.getPhases()[1];
    String originalDataset = aqueous.getParameterDatasetId();
    Map<String, Double> zeta = new LinkedHashMap<String, Double>();
    zeta.put("Na+", 0.01);
    assertThrows(IllegalArgumentException.class, () -> fluid.applyPhreeqcCo2ChlorideParameters(zeta));
    assertEquals(originalDataset, aqueous.getParameterDatasetId());
    zeta.put("K+", Double.NaN);
    assertThrows(IllegalArgumentException.class, () -> fluid.applyPhreeqcCo2ChlorideParameters(zeta));
    zeta.put("K+", -0.02);
    fluid.applyPhreeqcCo2ChlorideParameters(zeta);
    assertEquals(PitzerParameterDatasets.PHREEQC_CO2_CHLORIDE_USER_ZETA_ID, aqueous.getParameterDatasetId());
    assertEquals(0.01, aqueous.getZeta(1, 2, 4, 280.0), 0.0);
    assertEquals(-0.02, aqueous.getZeta(1, 3, 4, 280.0), 0.0);
    assertTrue(aqueous.isPhreeqcCommonIonTermsActive());
  }

  @Test
  void rejectsHydrateAmountsAndTemperatureBoundsOutsideHenryRange() {
    SystemPitzer fluid = brine("CO2", 1, 0, 0, 30.0);
    assertThrows(UnsupportedOperationException.class, () -> new ThermodynamicOperations(fluid).hydrateTPflash());
    PitzerHydrateFlash flash = new PitzerHydrateFlash(fluid, false);
    flash.setTemperatureBounds(273.15, 274.0);
    assertThrows(IllegalArgumentException.class, flash::run);
    assertFalse(flash.isConverged());
    assertEquals(280.0, fluid.getTemperature(), 0.0);
    // Table 4's 17.36 bara point lies outside this model's computed Henry-reference envelope.
    fluid.setPressure(17.36);
    assertThrows(IllegalStateException.class, () -> new PitzerHydrateFlash(fluid, false).run());
  }

  @Test
  void noBracketFailsAndRestoresSettings() {
    SystemPitzer fluid = brine("CO2", 1, 0, 0, 30.0);
    fluid.setMultiPhaseCheck(false);
    PitzerHydrateFlash flash = new PitzerHydrateFlash(fluid, false);
    flash.setTemperatureBounds(310.0, 315.0);
    assertThrows(IllegalStateException.class, flash::run);
    assertFalse(flash.isConverged());
    assertEquals(280.0, fluid.getTemperature(), 0.0);
    assertEquals(30.0, fluid.getPressure(), 0.0);
    assertFalse(fluid.doMultiPhaseCheck());
  }

  @Test
  void rejectsMissingWaterGuestAndUnsupportedDomain() {
    SystemPitzer dry = new SystemPitzer(280.0, 30.0);
    dry.addComponent("CO2", 1.0);
    dry.setMixingRule("classic");
    assertThrows(IllegalArgumentException.class, () -> new PitzerHydrateFlash(dry, false).run());
    SystemPitzer water = new SystemPitzer(280.0, 30.0);
    water.addComponent("water", 1.0);
    water.setMixingRule("classic");
    assertThrows(IllegalArgumentException.class, () -> new PitzerHydrateFlash(water, false).run());
    SystemPitzer brine = brine("CO2", 1, 0, 0, 30.0);
    brine.setTemperature(270.0);
    assertThrows(IllegalArgumentException.class, () -> new PitzerHydrateFlash(brine, true).run());
  }
}
