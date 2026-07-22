package neqsim.process.equipment.expander;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.expander.TurboExpanderSealGasEnvelope;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the state-of-the-art turbo-expander enhancements: P1 (ExpanderChartKhader), P2 (IGV controllable degree of
 * freedom), P4 (OEM map ingestion), P5 (mechanical/seal-gas envelope) and P6 (operating-envelope sweep).
 */
public class TurboExpanderEnhancementsTest {

  /**
   * Build a representative dry feed gas.
   *
   * @return a fresh SystemSrkEos feed gas
   */
  private SystemInterface feedGas() {
    SystemInterface gas = new SystemSrkEos(273.15 + 42.0, 10.0);
    gas.addComponent("nitrogen", 0.006);
    gas.addComponent("CO2", 0.014);
    gas.addComponent("methane", 0.862);
    gas.addComponent("ethane", 0.08);
    gas.addComponent("propane", 0.03);
    gas.addComponent("i-butane", 0.0024);
    gas.addComponent("n-butane", 0.004);
    gas.addComponent("n-hexane", 0.0015);
    gas.setMixingRule(2);
    gas.init(0);
    return gas;
  }

  /**
   * Build a configured turbo-expander-compressor matching the validated reference case.
   *
   * @return a runnable TurboExpanderCompressor
   */
  private TurboExpanderCompressor buildMachine() {
    SystemInterface gas = feedGas();
    Stream feedStream = new Stream("dry feed gas", gas);
    feedStream.setFlowRate(456000.0, "kg/hr");
    feedStream.setTemperature(-23.0, "C");
    feedStream.setPressure(60.95, "bara");
    feedStream.run();

    Stream feedStream2 = new Stream("dry feed gas 2", gas.clone());
    feedStream2.setFlowRate(423448.0, "kg/hr");
    feedStream2.setTemperature(17.0, "C");
    feedStream2.setPressure(42.0, "bara");
    feedStream2.run();

    TurboExpanderCompressor m = new TurboExpanderCompressor("TurboExpander", feedStream);
    m.setCompressorFeedStream(feedStream2);
    m.setUCcurve(
        new double[] { 0.9964751359624449, 0.7590835113213541, 0.984295619176559, 0.8827799803397821,
            0.9552460269880922, 1.0 },
        new double[] { 0.984090909090909, 0.796590909090909, 0.9931818181818183, 0.9363636363636364, 0.9943181818181818,
            1.0 });
    m.setQNEfficiencycurve(new double[] { 0.5, 0.7, 0.85, 1.0, 1.2, 1.4, 1.6 },
        new double[] { 0.88, 0.91, 0.95, 1.0, 0.97, 0.85, 0.6 });
    m.setQNHeadcurve(new double[] { 0.5, 0.8, 1.0, 1.2, 1.4, 1.6 }, new double[] { 1.1, 1.05, 1.0, 0.9, 0.7, 0.4 });
    m.setImpellerDiameter(0.424);
    m.setDesignSpeed(6850.0);
    m.setExpanderDesignIsentropicEfficiency(0.88);
    m.setDesignUC(0.7);
    m.setDesignQn(0.03328);
    m.setExpanderOutPressure(42.0);
    m.setCompressorDesignPolytropicEfficiency(0.81);
    m.setCompressorDesignPolytropicHead(20.47);
    m.setMaximumIGVArea(1.637e4);
    return m;
  }

  /**
   * Build a small but realistic 2-D expander performance map (P1/P4).
   *
   * @param referenceFluid the reference fluid for composition correction
   * @return an ExpanderChartKhader with two IGV curves
   */
  private ExpanderChartKhader buildChart(SystemInterface referenceFluid) {
    ExpanderChartKhader chart = new ExpanderChartKhader(referenceFluid, 0.424);
    double[] igv = new double[] { 0.6, 1.0 };
    double[][] uc = new double[][] { { 0.5, 0.6, 0.7, 0.8, 0.9 }, { 0.5, 0.6, 0.7, 0.8, 0.9 } };
    double[][] eta = new double[][] { { 0.72, 0.80, 0.84, 0.82, 0.76 }, { 0.78, 0.85, 0.88, 0.86, 0.80 } };
    double[][] head = new double[][] { { 30.0, 32.0, 33.0, 32.5, 31.0 }, { 34.0, 36.0, 37.0, 36.5, 35.0 } };
    chart.setCurves(igv, uc, eta, head);
    return chart;
  }

  @Test
  void testExpanderChartInterpolation() {
    ExpanderChartKhader chart = buildChart(null);
    Assertions.assertTrue(chart.isMapDefined());
    // peak efficiency on the 1.0 IGV curve is at uc = 0.7
    Assertions.assertEquals(0.7, chart.getOptimumVelocityRatio(1.0), 1e-9);
    // efficiency at a stored node reproduces the input
    Assertions.assertEquals(0.88, chart.getEfficiency(0.7, 1.0), 1e-9);
    // blends between the two IGV curves
    double mid = chart.getEfficiency(0.7, 0.8);
    Assertions.assertTrue(mid > 0.84 && mid < 0.88);
    // with no reference fluid the head round-trips back to kJ/kg
    Assertions.assertEquals(37.0, chart.getStageHeadDrop(0.7, 1.0, null), 1e-6);
  }

  @Test
  void testExpanderChartCompositionAware() {
    SystemInterface ref = feedGas();
    ExpanderChartKhader chart = buildChart(ref);
    // build chart normalises by reference sound speed
    chart.getStageHeadDrop(0.7, 1.0, null);
    Assertions.assertTrue(chart.getReferenceSoundSpeed() > 0.0);
    // evaluating against a denser fluid (CO2 rich, lower sound speed) changes the head
    SystemInterface co2 = new SystemSrkEos(273.15 + 10.0, 50.0);
    co2.addComponent("CO2", 0.9);
    co2.addComponent("methane", 0.1);
    co2.setMixingRule(2);
    co2.init(0);
    co2.initThermoProperties();
    double headRef = chart.getStageHeadDrop(0.7, 1.0, ref);
    double headCo2 = chart.getStageHeadDrop(0.7, 1.0, co2);
    Assertions.assertTrue(headRef > 0.0);
    Assertions.assertTrue(headCo2 > 0.0);
    Assertions.assertNotEquals(headRef, headCo2, 1e-6);
  }

  @Test
  void testIgvControlModeAndPenalty() {
    // run with chart, IGV fixed, no penalty
    TurboExpanderCompressor m1 = buildMachine();
    m1.setExpanderChart(buildChart(m1.getInletStream().getFluid()));
    m1.setIgvControlMode(true);
    m1.setIGVopening(1.0);
    m1.run();
    double effNoPenalty = m1.getExpanderIsentropicEfficiency();
    Assertions.assertTrue(effNoPenalty > 0.5 && effNoPenalty < 0.95);
    // IGV opening is held at the imposed value
    Assertions.assertEquals(1.0, m1.getIGVopening(), 1e-9);

    // run again with a 10% efficiency penalty at full IGV
    TurboExpanderCompressor m2 = buildMachine();
    m2.setExpanderChart(buildChart(m2.getInletStream().getFluid()));
    m2.setIgvControlMode(true);
    m2.setIGVopening(1.0);
    m2.setIgvEfficiencyPenaltyCurve(new double[] { 0.4, 1.0 }, new double[] { 0.85, 0.90 });
    Assertions.assertEquals(0.90, m2.getIgvEfficiencyPenalty(1.0), 1e-9);
    m2.run();
    double effPenalty = m2.getExpanderIsentropicEfficiency();
    Assertions.assertTrue(effPenalty < effNoPenalty);
  }

  @Test
  void testMapIngestionAndAnchorValidation() {
    SystemInterface ref = feedGas();
    TurboExpanderMapIngestion loader = new TurboExpanderMapIngestion(ref, 0.3, 0.424);
    ExpanderChartKhader chart = loader.buildExpanderChart(new double[] { 0.6, 1.0 },
        new double[][] { { 0.5, 0.6, 0.7, 0.8, 0.9 }, { 0.5, 0.6, 0.7, 0.8, 0.9 } },
        new double[][] { { 0.72, 0.80, 0.84, 0.82, 0.76 }, { 0.78, 0.85, 0.88, 0.86, 0.80 } },
        new double[][] { { 30.0, 32.0, 33.0, 32.5, 31.0 }, { 34.0, 36.0, 37.0, 36.5, 35.0 } });
    Assertions.assertTrue(chart.isMapDefined());
    loader.addAnchorPoint("Design 1998", 0.7, 1.0, 0.88);
    loader.addAnchorPoint("Case B", 0.7, 0.6, 0.84);
    Assertions.assertTrue(loader.validateExpanderChart(chart, 0.02));
    // a clearly wrong anchor fails validation
    loader.addAnchorPoint("Bad", 0.7, 1.0, 0.60);
    Assertions.assertFalse(loader.validateExpanderChart(chart, 0.02));
  }

  @Test
  void testSealGasEnvelope() {
    TurboExpanderCompressor m = buildMachine();
    m.run();
    TurboExpanderSealGasEnvelope env = new TurboExpanderSealGasEnvelope(m);
    env.setFirstCriticalSpeed(4500.0);
    boolean allowable = env.evaluate();
    // heater duty: 2 * 0.01 kg/s * 2200 J/kgK * 25 K = 1100 W < 28 kW
    Assertions.assertEquals(1100.0, env.getRequiredHeaterDuty(), 1.0);
    Assertions.assertTrue(env.isHeaterDutyAcceptable());
    // thrust is finite and bearing utilisation is computed
    Assertions.assertTrue(Double.isFinite(env.getNetAxialThrust()));
    Assertions.assertTrue(env.getThrustUtilisation() >= 0.0);
    // operating speed (~6600 rpm) is well above the 4500 rpm critical speed
    Assertions.assertTrue(env.getCriticalSpeedMargin() > 0.15);
    Assertions.assertTrue(env.isCriticalSpeedMarginAcceptable());
    Assertions.assertTrue(allowable);
    Assertions.assertNotNull(env.toJson());
  }

  @Test
  void testOperatingEnvelopeSweep() {
    TurboExpanderCompressor m = buildMachine();
    TurboExpanderOperatingEnvelope env = new TurboExpanderOperatingEnvelope(m);
    env.setGrid(new double[] { 55.0, 60.95 }, new double[] { 300000.0, 456000.0 });
    env.run();
    Assertions.assertNotNull(env.getFeasibility());
    Assertions.assertNotNull(env.getColdEndTemperature());
    Assertions.assertEquals(2, env.getColdEndTemperature().length);
    Assertions.assertEquals(2, env.getColdEndTemperature()[0].length);
    // at least one grid point should be feasible and produce a finite cold-end temperature
    boolean anyFeasible = false;
    boolean anyFiniteColdEnd = false;
    for (int i = 0; i < 2; i++) {
      for (int j = 0; j < 2; j++) {
        if (env.getFeasibility()[i][j]) {
          anyFeasible = true;
        }
        if (Double.isFinite(env.getColdEndTemperature()[i][j])) {
          anyFiniteColdEnd = true;
        }
      }
    }
    Assertions.assertTrue(anyFeasible);
    Assertions.assertTrue(anyFiniteColdEnd);
    Assertions.assertNotNull(env.toJson());
  }
}
