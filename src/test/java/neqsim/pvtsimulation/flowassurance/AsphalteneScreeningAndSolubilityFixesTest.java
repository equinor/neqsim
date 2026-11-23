package neqsim.pvtsimulation.flowassurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.PedersenAsphalteneCharacterization;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.flashops.saturationops.AsphalteneOnsetPressureFlash;

/**
 * Regression tests for the De Boer, stability-analyzer, Flory-Huggins, onset-flash and solid-model fixes.
 */
class AsphalteneScreeningAndSolubilityFixesTest {

  /** Live oil with a Pedersen asphaltene that has an upper and a lower onset at 100 C. */
  private static SystemInterface pedersenOil() {
    SystemInterface oil = new SystemSrkEos(373.15, 500.0);
    oil.getCharacterization().setTBPModel("PedersenSRK");
    oil.addComponent("methane", 0.35);
    oil.addComponent("ethane", 0.08);
    oil.addComponent("propane", 0.05);
    oil.addComponent("n-butane", 0.03);
    oil.addComponent("n-hexane", 0.03);
    oil.addTBPfraction("C7", 0.12, 0.096, 0.738);
    oil.addTBPfraction("C10", 0.10, 0.14, 0.79);
    oil.addTBPfraction("C20", 0.12, 0.28, 0.86);
    oil.addTBPfraction("C40", 0.10, 0.56, 0.93);
    PedersenAsphalteneCharacterization pedersen = new PedersenAsphalteneCharacterization();
    pedersen.setAsphalteneMW(1200.0);
    pedersen.setAsphalteneDensity(1.15);
    pedersen.addAsphalteneToSystem(oil, 0.01);
    oil.setMixingRule("classic");
    oil.init(0);
    pedersen.applyAsphalteneKij(oil, 0.08);
    oil.setMultiPhaseCheck(true);
    return oil;
  }

  /** Simple live oil without asphaltene for the solubility-parameter model. */
  private static SystemInterface liveOil() {
    SystemInterface oil = new SystemSrkEos(373.15, 350.0);
    oil.addComponent("methane", 0.40);
    oil.addComponent("ethane", 0.05);
    oil.addComponent("propane", 0.03);
    oil.addComponent("n-heptane", 0.45);
    oil.addComponent("nC10", 0.07);
    oil.setMixingRule("classic");
    return oil;
  }

  @Test
  void deBoerBoundariesAreOrdered() {
    for (double rho = 550.0; rho <= 1000.0; rho += 10.0) {
      double[] b = DeBoerAsphalteneScreening.boundaries(rho);
      assertTrue(b[0] <= b[1] && b[1] <= b[2], "Unordered boundaries at " + rho + " kg/m3");
    }
    // At 700 kg/m3 the fitted no-problem curve (98 bar) lies above the slight curve (90 bar): now capped.
    double[] b700 = DeBoerAsphalteneScreening.boundaries(700.0);
    assertEquals(90.0, b700[1], 1e-9);
    assertEquals(90.0, b700[0], 1e-9);
  }

  @Test
  void deBoerAsphalteneMultiplierDoublesAtFivePercent() {
    DeBoerAsphalteneScreening screening = new DeBoerAsphalteneScreening(350.0, 150.0, 750.0);
    double base = screening.calculateRiskIndex();
    screening.setAsphalteneContent(0.05);
    assertEquals(2.0 * base, screening.calculateRiskIndex(), 1e-9);
  }

  @Test
  void analyzerDeBoerDelegatesToScreeningClass() {
    AsphalteneStabilityAnalyzer analyzer = new AsphalteneStabilityAnalyzer();
    for (double dp : new double[] {20.0, 95.0, 150.0, 400.0}) {
      DeBoerAsphalteneScreening.DeBoerRisk reference = new DeBoerAsphalteneScreening(100.0 + dp, 100.0, 700.0)
          .evaluateRisk();
      AsphalteneStabilityAnalyzer.AsphalteneRisk mapped = analyzer.deBoerScreening(100.0 + dp, 100.0, 700.0);
      AsphalteneStabilityAnalyzer.AsphalteneRisk[] expected = {AsphalteneStabilityAnalyzer.AsphalteneRisk.STABLE,
          AsphalteneStabilityAnalyzer.AsphalteneRisk.LOW_RISK, AsphalteneStabilityAnalyzer.AsphalteneRisk.MODERATE_RISK,
          AsphalteneStabilityAnalyzer.AsphalteneRisk.HIGH_RISK};
      assertEquals(expected[reference.ordinal()], mapped);
    }
  }

  @Test
  void riskCombinationTakesTheHigherLevel() {
    assertEquals(AsphalteneStabilityAnalyzer.AsphalteneRisk.HIGH_RISK, AsphalteneStabilityAnalyzer.combineRisks(
        AsphalteneStabilityAnalyzer.AsphalteneRisk.STABLE, AsphalteneStabilityAnalyzer.AsphalteneRisk.HIGH_RISK));
    assertEquals(AsphalteneStabilityAnalyzer.AsphalteneRisk.MODERATE_RISK,
        AsphalteneStabilityAnalyzer.combineRisks(null, null));
    assertEquals(AsphalteneStabilityAnalyzer.AsphalteneRisk.HIGH_RISK,
        AsphalteneStabilityAnalyzer.thermodynamicRisk(300.0, 400.0, 50.0));
    assertEquals(AsphalteneStabilityAnalyzer.AsphalteneRisk.STABLE,
        AsphalteneStabilityAnalyzer.thermodynamicRisk(Double.NaN, 400.0, 50.0));
  }

  @Test
  void pressureFlashFindsUpperAndLowerOnset() {
    SystemInterface oil = pedersenOil();
    AsphalteneOnsetPressureFlash flash = new AsphalteneOnsetPressureFlash(oil, 500.0, 20.0);
    flash.setPressureStep(10.0);
    flash.run();
    assertTrue(flash.isOnsetFound());
    double upper = flash.getOnsetPressure();
    double lower = flash.getLowerOnsetPressure();
    assertTrue(upper > 400.0 && upper < 500.0, "upper " + upper);
    assertTrue(lower > 100.0 && lower < 200.0, "lower " + lower);
    assertTrue(flash.getPrecipitatedFraction() > 0.0, "Some asphaltene precipitates just below the onset");
    assertEquals(upper, oil.getPressure(), 0.0, "The system is left at the upper onset");
  }

  @Test
  void analyzerEnvelopeHasBubblePointPerTemperature() {
    AsphalteneStabilityAnalyzer analyzer = new AsphalteneStabilityAnalyzer(pedersenOil());
    double[][] envelope = analyzer.generatePrecipitationEnvelope(373.15, 378.15, 2);
    assertEquals(4, envelope.length);
    assertTrue(Double.isFinite(envelope[2][0]) && envelope[2][0] < envelope[1][0], "lower below upper onset");
    assertTrue(Math.abs(envelope[3][0] - envelope[3][1]) > 0.1, "bubble point must follow temperature");
  }

  @Test
  void floryHugginsSolverRejectsFalseRoots() {
    // chi = 7.2, r = 6.8: g(u) < 0 for every u < 0, so the asphaltene is fully miscible (the old Newton
    // iteration stopped at phi ~ 1e-19 with residual -45 and reported almost total precipitation).
    assertEquals(1.0, FloryHugginsAsphalteneModel.solubilityLimit(7.2, 6.8), 0.0);
    double phi = FloryHugginsAsphalteneModel.solubilityLimit(12.0, 5.0);
    assertTrue(phi > 0.0 && phi < 1.0);
    double residual = Math.log(phi) + (1.0 - 5.0) * (1.0 - phi) + 12.0 * (1.0 - phi) * (1.0 - phi);
    assertEquals(0.0, residual, 1e-9);
  }

  @Test
  void floryHugginsCalibrationReproducesMeasuredOnset() {
    FloryHugginsAsphalteneModel model = new FloryHugginsAsphalteneModel(liveOil(), 373.15);
    model.setAsphalteneWeightFraction(0.03);
    model.setPressureSearchStep(5.0);
    model.calibrateToOnsetPressure(373.15, 250.0);
    assertEquals(250.0, model.calculateOnsetPressure(373.15), 0.5);
  }

  @Test
  void floryHugginsSaraConfigurationIsIdempotent() {
    FloryHugginsAsphalteneModel model = new FloryHugginsAsphalteneModel();
    model.setAsphalteneSolubilityParameter(21.0);
    model.configureFromSARA(0.8, 0.1, 0.03, 0.07);
    double once = model.getAsphalteneSolubilityParameter();
    model.configureFromSARA(0.8, 0.1, 0.03, 0.07);
    model.configureFromSARA(0.8, 0.1, 0.03, 0.07);
    assertEquals(once, model.getAsphalteneSolubilityParameter(), 0.0);
    assertEquals(21.0 * 1.02 * 1.01, once, 1e-12);
  }

  @Test
  void eosSolubilityParameterIsPhysical() {
    SystemInterface heptane = new SystemSrkEos(298.15, 1.0);
    heptane.addComponent("n-heptane", 1.0);
    heptane.setMixingRule("classic");
    FloryHugginsAsphalteneModel model = new FloryHugginsAsphalteneModel(heptane, 298.15);
    model.setSolubilityParameterMethod(FloryHugginsAsphalteneModel.SolubilityParameterMethod.EOS);
    double[][] profile = model.generateSolubilityParameterProfile(298.15, 1.0, 1.0 - 1e-6, 2);
    // Literature value for n-heptane at 25 C: 15.3 MPa^0.5.
    assertEquals(15.3, profile[1][0], 1.5);
  }

  @Test
  void liveOilBasisAndPolydispersityAreApplied() {
    FloryHugginsAsphalteneModel model = new FloryHugginsAsphalteneModel(liveOil(), 373.15);
    model.setAsphalteneWeightFraction(0.03);
    model.calibrateToOnsetPressure(373.15, 250.0);
    double below = 60.0;
    double live = model.calculatePrecipitatedFraction(below, 373.15);
    model.setLiveOilBasis(false);
    double dead = model.calculatePrecipitatedFraction(below, 373.15);
    assertTrue(live >= dead, "Gas leaving the oil concentrates the asphaltene");
    model.setLiveOilBasis(true);
    model.setNumberOfAsphalteneFractions(5);
    double poly = model.calculatePrecipitatedFraction(200.0, 373.15);
    assertTrue(Double.isFinite(poly) && poly >= 0.0);
    assertThrows(IllegalArgumentException.class, () -> model.setNumberOfAsphalteneFractions(0));
  }
}
