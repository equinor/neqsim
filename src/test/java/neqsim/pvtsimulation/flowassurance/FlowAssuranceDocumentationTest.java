package neqsim.pvtsimulation.flowassurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.pvtsimulation.flowassurance.DeBoerAsphalteneScreening.DeBoerRisk;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Verifies executable examples in the flow-assurance landing page and overview. */
class FlowAssuranceDocumentationTest {

  @Test
  void testDeBoerQuickStart() {
    DeBoerAsphalteneScreening screening = new DeBoerAsphalteneScreening(350.0, 150.0, 750.0);

    DeBoerRisk risk = screening.evaluateRisk();
    double riskIndex = screening.calculateRiskIndex();

    assertEquals(DeBoerRisk.MODERATE_PROBLEM, risk);
    assertEquals(1.6, riskIndex, 1.0e-12);
    assertFalse(risk.getDescription().trim().isEmpty());
  }

  /**
   * Verifies the hydrate and scale API calls in the integrated flow-assurance overview.
   *
   * @throws Exception if the hydrate equilibrium calculation fails
   */
  @Test
  void testIntegratedOverviewHydrateAndScaleScreen() throws Exception {
    SystemInterface hydrateFluid = new SystemElectrolyteCPAstatoil(273.15 + 10.0, 50.0);
    hydrateFluid.addComponent("water", 0.494505);
    hydrateFluid.addComponent("MEG", 0.164835);
    hydrateFluid.addComponent("methane", 0.247253);
    hydrateFluid.addComponent("ethane", 0.0164835);
    hydrateFluid.addComponent("propane", 0.010989);
    hydrateFluid.addComponent("i-butane", 0.00549451);
    hydrateFluid.addComponent("n-butane", 0.00549451);
    hydrateFluid.addComponent("Na+", 0.0274725);
    hydrateFluid.addComponent("Cl-", 0.0274725);
    hydrateFluid.setMixingRule(10);
    hydrateFluid.setMultiPhaseCheck(true);
    hydrateFluid.setHydrateCheck(true);

    ThermodynamicOperations hydrateOps = new ThermodynamicOperations(hydrateFluid);
    hydrateOps.hydrateFormationTemperature();
    double hydrateTemperatureC = hydrateFluid.getTemperature("C");

    assertTrue(Double.isFinite(hydrateTemperatureC));
    assertTrue(hydrateTemperatureC > -30.0 && hydrateTemperatureC < 15.0);

    ScalePredictionCalculator scaleScreen = new ScalePredictionCalculator();
    scaleScreen.setTemperatureCelsius(80.0);
    scaleScreen.setPressureBara(100.0);
    scaleScreen.setCalciumConcentration(400.0);
    scaleScreen.setBariumConcentration(10.0);
    scaleScreen.setStrontiumConcentration(5.0);
    scaleScreen.setIronConcentration(2.0);
    scaleScreen.setMagnesiumConcentration(1300.0);
    scaleScreen.setSodiumConcentration(11000.0);
    scaleScreen.setBicarbonateConcentration(150.0);
    scaleScreen.setSulphateConcentration(10.0);
    scaleScreen.setTotalDissolvedSolids(35000.0);
    scaleScreen.setCO2PartialPressure(2.0);
    scaleScreen.enableAutoPH();
    scaleScreen.calculate();

    assertTrue(Double.isFinite(scaleScreen.getCaCO3SaturationIndex()));
    assertTrue(Double.isFinite(scaleScreen.getBaSO4SaturationIndex()));
    assertEquals(!scaleScreen.getScaleRisks().isEmpty(), scaleScreen.hasScalingRisk());
    assertNotNull(scaleScreen.getScaleRisks());
    assertNotNull(scaleScreen.toJson());
  }
}
