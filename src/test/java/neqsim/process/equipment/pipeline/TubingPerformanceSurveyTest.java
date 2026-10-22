package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests of the deviated survey, the inlet-pressure solver and the VLP curve of {@link TubingPerformance}.
 *
 * @author ESOL
 * @version 1.0
 */
class TubingPerformanceSurveyTest {
  private SystemInterface gasFluid;
  private Stream gasStream;

  @BeforeEach
  void setUp() {
    gasFluid = new SystemSrkEos(358.15, 200.0);
    gasFluid.addComponent("methane", 0.85);
    gasFluid.addComponent("ethane", 0.08);
    gasFluid.addComponent("propane", 0.04);
    gasFluid.addComponent("n-butane", 0.02);
    gasFluid.addComponent("CO2", 0.01);
    gasFluid.setMixingRule("classic");
    gasFluid.init(0);
    gasStream = new Stream("feed", gasFluid);
    gasStream.setFlowRate(0.5, "MSm3/day");
    gasStream.setTemperature(85.0, "C");
    gasStream.setPressure(200.0, "bara");
    gasStream.run();
  }

  @Test
  void surveyWithOneVerticalIntervalEqualsPlainTubing() {
    TubingPerformance plain = new TubingPerformance("plain", gasStream);
    plain.setDiameter(0.1);
    plain.setLength(3000.0);
    plain.setInclination(90.0);
    plain.setCorrelationType(TubingPerformance.CorrelationType.GRAY);
    plain.setNumberOfSegments(20);
    plain.run();

    TubingPerformance survey = new TubingPerformance("survey", gasStream);
    survey.setSurvey(new double[] {0.0, 3000.0}, new double[] {0.0, 3000.0}, 0.1);
    survey.setCorrelationType(TubingPerformance.CorrelationType.GRAY);
    survey.setNumberOfSegments(20);
    survey.run();

    assertTrue(survey.hasSurvey());
    assertEquals(plain.getOutletStream().getPressure("bara"), survey.getOutletStream().getPressure("bara"), 0.01);
    assertEquals(plain.getPressureDrop(), survey.getPressureDrop(), 0.01);
  }

  @Test
  void deviatedSurveyMatchesChainOfTubingInstances() {
    double[] md = {0.0, 1000.0, 3000.0};
    double[] tvd = {0.0, 900.0, 2900.0};
    double[] id = {0.100, 0.120};

    TubingPerformance survey = new TubingPerformance("survey", gasStream);
    survey.setSurvey(md, tvd, id);
    survey.setCorrelationType(TubingPerformance.CorrelationType.GRAY);
    survey.setNumberOfSegments(50);
    survey.run();

    TubingPerformance bottom = new TubingPerformance("bottom", gasStream);
    bottom.setLength(2000.0);
    bottom.setDiameter(0.120);
    bottom.setInclination(90.0);
    bottom.setCorrelationType(TubingPerformance.CorrelationType.GRAY);
    bottom.setNumberOfSegments(33);
    bottom.run();
    TubingPerformance top = new TubingPerformance("top", bottom.getOutletStream());
    top.setLength(1000.0);
    top.setDiameter(0.100);
    top.setInclination(Math.toDegrees(Math.asin(900.0 / 1000.0)));
    top.setCorrelationType(TubingPerformance.CorrelationType.GRAY);
    top.setNumberOfSegments(17);
    top.run();

    assertEquals(top.getOutletStream().getPressure("bara"), survey.getOutletStream().getPressure("bara"), 0.05);
    assertEquals(200.0 - survey.getOutletStream().getPressure("bara"), survey.getPressureDrop(), 0.01);
  }

  @Test
  void surveyRestoresSingleSectionSettingsAndCanBeCleared() {
    TubingPerformance tubing = new TubingPerformance("t", gasStream);
    tubing.setDiameter(0.09);
    tubing.setLength(1234.0);
    tubing.setInclination(80.0);
    tubing.setSurvey(new double[] {0.0, 500.0, 1500.0}, new double[] {0.0, 400.0, 1400.0}, 0.1);
    tubing.run();
    tubing.clearSurvey();
    assertFalse(tubing.hasSurvey());
    assertEquals(0.09, tubing.getDiameter(), 1e-12);
    assertEquals(80.0, tubing.getInclination(), 1e-12);
  }

  @Test
  void invalidSurveyIsRejected() {
    TubingPerformance tubing = new TubingPerformance("t", gasStream);
    assertThrows(IllegalArgumentException.class,
        () -> tubing.setSurvey(new double[] {0.0, 100.0}, new double[] {0.0}, 0.1));
    assertThrows(IllegalArgumentException.class,
        () -> tubing.setSurvey(new double[] {0.0, 0.0}, new double[] {0.0, 1.0}, 0.1));
    assertThrows(IllegalArgumentException.class,
        () -> tubing.setSurvey(new double[] {0.0, 100.0}, new double[] {0.0, 100.0}, new double[] {-0.1}));
  }

  @Test
  void inletPressureSolveReproducesTargetOutletPressure() {
    TubingPerformance tubing = new TubingPerformance("t");
    tubing.setSurvey(new double[] {0.0, 1000.0, 3000.0}, new double[] {0.0, 900.0, 2900.0}, 0.1);
    tubing.setCorrelationType(TubingPerformance.CorrelationType.GRAY);
    tubing.setNumberOfSegments(20);
    SystemInterface fluid = gasStream.getThermoSystem().clone();
    double bhp = tubing.calculateInletPressureForOutlet(fluid, 60.0, 0.01);
    assertFalse(Double.isNaN(bhp));
    fluid.setPressure(bhp, "bara");
    assertEquals(60.0, tubing.calculateOutletPressure(fluid), 0.05);
    assertTrue(bhp > 60.0);
  }

  @Test
  void vlpCurveIncreasesWithRateAndExceedsWellheadPressure() {
    TubingPerformance tubing = new TubingPerformance("t", gasStream);
    tubing.setDiameter(0.1);
    tubing.setLength(3000.0);
    tubing.setCorrelationType(TubingPerformance.CorrelationType.GRAY);
    tubing.setNumberOfSegments(10);
    tubing.setWellheadPressure(60.0);
    double[][] curve = tubing.generateVLPCurve(new double[] {0.2, 0.5, 1.0});
    for (int i = 0; i < 3; i++) {
      assertTrue(curve[1][i] > 60.0, "BHP must exceed the wellhead pressure");
    }
    assertTrue(curve[1][2] > curve[1][0], "Friction raises the required BHP with rate");
  }

  @Test
  void chokedRateGivesNaNInsteadOfAGarbageBhp() {
    TubingPerformance tubing = new TubingPerformance("t", gasStream);
    tubing.setDiameter(0.05);
    tubing.setLength(3000.0);
    tubing.setCorrelationType(TubingPerformance.CorrelationType.GRAY);
    tubing.setNumberOfSegments(10);
    tubing.setWellheadPressure(60.0);
    double[][] curve = tubing.generateVLPCurve(new double[] {8.0});
    assertTrue(Double.isNaN(curve[1][0]));
  }
}
