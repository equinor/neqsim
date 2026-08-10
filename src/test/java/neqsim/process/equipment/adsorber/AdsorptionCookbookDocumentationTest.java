package neqsim.process.equipment.adsorber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.physicalproperties.interfaceproperties.solidadsorption.IsothermType;
import neqsim.physicalproperties.interfaceproperties.solidadsorption.LangmuirAdsorption;
import neqsim.process.equipment.adsorber.AdsorptionCycleController.CyclePhase;
import neqsim.process.equipment.adsorber.AdsorptionCycleController.PhaseStep;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Verifies every public API demonstrated in the adsorption cookbook. */
class AdsorptionCookbookDocumentationTest {

  /**
   * Verifies competitive Langmuir screening for parameterized CO2/methane pairs.
   *
   * @throws Exception if the thermodynamic flash fails
   */
  @Test
  void testCompetitiveEquilibriumScreen() throws Exception {
    SystemSrkEos gas = new SystemSrkEos(298.15, 10.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("CO2", 0.10);
    gas.setMixingRule("classic");
    new ThermodynamicOperations(gas).TPflash();

    String[] materials = new String[] { "AC Calgon F400", "Zeolite 13X", "Zeolite 5A", "Silica Gel", "MOF HKUST-1" };

    for (String material : materials) {
      LangmuirAdsorption model = new LangmuirAdsorption(gas);
      model.setSolidMaterial(material);
      model.calcExtendedLangmuir(0);

      assertTrue(Double.isFinite(model.getSurfaceExcess("CO2")));
      assertTrue(model.getSurfaceExcess("CO2") > 0.0);
      assertTrue(Double.isFinite(model.getSurfaceExcess("methane")));
      assertTrue(model.getSurfaceExcess("methane") > 0.0);
      assertTrue(Double.isFinite(model.getSelectivity(1, 0, 0)));
      assertTrue(model.getSelectivity(1, 0, 0) > 1.0);
    }
  }

  /** Verifies the steady and transient fixed-bed cookbook calls. */
  @Test
  void testSteadyAndTransientBedScreen() {
    AdsorptionBed steadyBed = createBed("steady screen", createFeed());
    steadyBed.run();

    double outletCO2 = steadyBed.getOutletStream().getFluid().getPhase(0).getComponent("CO2").getx();
    assertTrue(steadyBed.getAdsorbentMass() > 0.0);
    assertTrue(Double.isFinite(steadyBed.getPressureDrop()));
    assertTrue(steadyBed.getPressureDrop() >= 0.0);
    assertTrue(Double.isFinite(outletCO2));
    assertTrue(outletCO2 >= 0.0 && outletCO2 <= 1.0);

    AdsorptionBed transientBed = createBed("transient screen", createFeed());
    transientBed.setNumberOfCells(20);
    transientBed.setCalculateSteadyState(false);
    transientBed.setBreakthroughThreshold(0.05);

    double dt = 0.25;
    for (int step = 0; step < 20; step++) {
      transientBed.runTransient(dt, UUID.randomUUID());
    }

    assertEquals(5.0, transientBed.getElapsedTime(), 1.0e-12);
    assertTrue(Double.isFinite(transientBed.getAverageLoading(1)));
    assertTrue(transientBed.getAverageLoading(1) >= 0.0);
    assertFalse(transientBed.getConcentrationProfile(1).length == 0);
    assertFalse(transientBed.isBreakthroughOccurred());
  }

  /** Verifies the PSA and TSA schedule APIs and documented targets. */
  @Test
  void testCycleSchedules() {
    AdsorptionCycleController controller = new AdsorptionCycleController(createBed("cycle bed", createFeed()));

    controller.configurePSA(300.0, 30.0, 60.0, 30.0, 1.0);
    List<PhaseStep> psa = controller.getSchedule();
    assertEquals(4, psa.size());
    assertEquals(CyclePhase.ADSORPTION, psa.get(0).getPhase());
    assertEquals(CyclePhase.BLOWDOWN, psa.get(1).getPhase());
    assertEquals(1.0, psa.get(1).getTargetPressure(), 0.0);
    assertEquals(300.0, psa.get(0).getDuration(), 0.0);

    controller.configureTSA(1800.0, 600.0, 300.0, 523.15);
    List<PhaseStep> tsa = controller.getSchedule();
    assertEquals(3, tsa.size());
    assertEquals(CyclePhase.DESORPTION, tsa.get(1).getPhase());
    assertEquals(523.15, tsa.get(1).getTargetTemperature(), 0.0);
    assertEquals(600.0, tsa.get(1).getDuration(), 0.0);
  }

  private static Stream createFeed() {
    SystemSrkEos gas = new SystemSrkEos(298.15, 10.0);
    gas.addComponent("methane", 0.85);
    gas.addComponent("CO2", 0.10);
    gas.addComponent("nitrogen", 0.05);
    gas.setMixingRule("classic");

    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();
    return feed;
  }

  private static AdsorptionBed createBed(String name, Stream feed) {
    AdsorptionBed bed = new AdsorptionBed(name, feed);
    bed.setBedDiameter(1.0);
    bed.setBedLength(3.0);
    bed.setAdsorbentMaterial("AC Calgon F400");
    bed.setIsothermType(IsothermType.LANGMUIR);
    bed.setKLDF(0.05);
    return bed;
  }
}
