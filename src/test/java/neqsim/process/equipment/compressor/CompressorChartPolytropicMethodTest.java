package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.NeqSimTest;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Checks that chart and temperature specifications use the selected polytropic basis (issue #4718).
 *
 * @author Even Solbraa
 * @version 1.0
 */
class CompressorChartPolytropicMethodTest extends NeqSimTest {
  /**
   * Creates the public synthetic inlet from issue #4718.
   *
   * @return initialized natural-gas stream at 40 bara and 40 degrees Celsius
   */
  private Stream inlet() {
    SystemSrkEos fluid = new SystemSrkEos(313.15, 40.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("nitrogen", 0.01);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(2.0e6, "Sm3/day");
    feed.run();
    return feed;
  }

  /**
   * Creates the synthetic chart from the issue in either supported head unit.
   *
   * @param method selected polytropic method
   * @param unit chart head unit
   * @return fixed-speed chart compressor
   */
  private Compressor chart(String method, String unit) {
    Compressor compressor = new Compressor("chart", inlet());
    compressor.setUsePolytropicCalc(true);
    compressor.setPolytropicMethod(method);
    compressor.setCompressorChartType("interpolate and extrapolate");
    double[][] heads = {{110.0, 100.0, 85.0}, {171.9, 156.3, 132.8}};
    if ("meter".equals(unit)) {
      for (double[] curve : heads) {
        for (int i = 0; i < curve.length; i++) {
          curve[i] *= 1000.0 / 9.81;
        }
      }
    }
    compressor.getCompressorChart().setCurves(new double[0], new double[] {8000.0, 10000.0},
        new double[][] {{1500.0, 2000.0, 2500.0}, {1875.0, 2500.0, 3125.0}}, heads,
        new double[][] {{78.0, 80.0, 77.0}, {78.0, 80.0, 77.0}});
    compressor.getCompressorChart().setHeadUnit(unit);
    compressor.setSpeed(9000.0);
    return compressor;
  }

  /**
   * Creates the corresponding compressor without a chart.
   *
   * @param source chart compressor with the desired method and efficiency
   * @return pressure-specified compressor on the same basis
   */
  private Compressor reference(Compressor source) {
    Compressor compressor = new Compressor("reference", source.getInletStream());
    compressor.setUsePolytropicCalc(true);
    compressor.setPolytropicMethod(source.getPolytropicMethod());
    compressor.setPolytropicEfficiency(source.getPolytropicEfficiency());
    compressor.setUseGERG2008(source.isUseGERG2008());
    compressor.setUseLeachman(source.isUseLeachman());
    compressor.setUseVega(source.isUseVega());
    compressor.setOutletPressure(source.getOutletStream().getPressure("bara"), "bara");
    compressor.run();
    return compressor;
  }

  /**
   * Fixed-speed map head must match the selected pressure-specified calculation.
   *
   * @param method polytropic method
   * @param unit chart head unit
   */
  @ParameterizedTest
  @CsvSource({"schultz,kJ/kg", "schultz,meter", "detailed,kJ/kg", "ideal_inlet_properties,kJ/kg"})
  void fixedSpeedMatchesSelectedMethod(String method, String unit) {
    Compressor compressor = chart(method, unit);
    compressor.run();
    assertSameBasis(compressor);
    double pressure = compressor.getOutletStream().getPressure("bara");
    compressor.run();
    assertEquals(pressure, compressor.getOutletStream().getPressure("bara"), 1.0e-8);
    assertEquals(2.0e6, compressor.getOutletStream().getFlowRate("Sm3/day"), 1.0e-5);
  }

  /**
   * Pressure-target speed solving must use the same real-gas relation as forward evaluation.
   *
   * @param unit chart head unit
   */
  @ParameterizedTest
  @CsvSource({"kJ/kg", "meter"})
  void solvedSpeedMatchesSelectedMethod(String unit) {
    Compressor compressor = chart("schultz", unit);
    compressor.setOutletPressure(100.0, "bara");
    compressor.setSolveSpeed(true);
    compressor.run();
    assertTrue(compressor.isSpeedSolveConverged());
    assertEquals(100.0, compressor.getOutletStream().getPressure("bara"), 1.0e-3);
    assertSameBasis(compressor);
    compressor.run();
    assertTrue(compressor.isSpeedSolveConverged());
    assertSameBasis(compressor);
  }

  /**
   * Checks head, power, temperature and map-head closure independently of the speed solver.
   *
   * @param compressor evaluated chart compressor
   */
  private void assertSameBasis(Compressor compressor) {
    Compressor plain = reference(compressor);
    assertEquals(plain.getPolytropicHead("kJ/kg"), compressor.getPolytropicHead("kJ/kg"), 1.0e-5);
    assertEquals(plain.getPower(), compressor.getPower(), 0.1);
    assertEquals(plain.getOutletStream().getTemperature(), compressor.getOutletStream().getTemperature(), 1.0e-4);
    double mapHead = compressor.getCompressorChart().getPolytropicHead(compressor.getInletStream().getFlowRate("m3/hr"),
        compressor.getSpeed());
    if ("meter".equals(compressor.getCompressorChart().getHeadUnit())) {
      mapHead *= 9.81 / 1000.0;
    }
    assertEquals(mapHead, compressor.getPolytropicHead("kJ/kg"), 1.0e-5);
    assertEquals(
        compressor.getOutletStream().getFluid().getEnthalpy() - compressor.getInletStream().getFluid().getEnthalpy(),
        compressor.getPower(), 0.1);
  }

  /** Specifying a measured temperature must invert the selected forward method. */
  @Test
  void specifiedTemperatureRecoversSchultzEfficiency() {
    Compressor forward = new Compressor("forward", inlet());
    forward.setUsePolytropicCalc(true);
    forward.setPolytropicEfficiency(0.812);
    forward.setOutletPressure(100.0, "bara");
    forward.run();
    Compressor measured = new Compressor("measured", inlet());
    measured.setUsePolytropicCalc(true);
    measured.setOutletPressure(100.0, "bara");
    measured.setOutTemperature(forward.getOutletStream().getTemperature());
    UUID id = UUID.randomUUID();
    measured.run(id);
    assertEquals(0.812, measured.getPolytropicEfficiency(), 1.0e-5);
    assertEquals(forward.getPower(), measured.getPower(), 10.0);
    assertEquals(id, measured.getCalculationIdentifier());
    assertEquals(id, measured.getOutletStream().getCalculationIdentifier());
    assertFalse(measured.needRecalculation());
  }

  /** The compatibility method must retain the historical synthetic-chart result. */
  @Test
  void legacyMethodIsExplicitAndMethodChangesInvalidateCache() {
    Compressor compressor = chart("ideal_inlet_properties", "kJ/kg");
    compressor.run();
    double legacyPressure = compressor.getOutletStream().getPressure("bara");
    assertEquals(131.2065283067428, compressor.getPolytropicHead("kJ/kg"), 1.0e-8);
    assertFalse(compressor.needRecalculation());
    compressor.setPolytropicMethod("schultz");
    assertTrue(compressor.needRecalculation());
    compressor.run();
    assertTrue(compressor.getOutletStream().getPressure("bara") < legacyPressure);
    assertSameBasis(compressor);
  }

  /** GERG-based chart pressure and caloric state must agree with the chart-free model. */
  @Test
  void gergChartMatchesSelectedModel() {
    Compressor compressor = chart("schultz", "kJ/kg");
    compressor.setUseGERG2008(true);
    compressor.run();
    Compressor plain = reference(compressor);
    assertEquals(plain.getPolytropicHead("kJ/kg"), compressor.getPolytropicHead("kJ/kg"), 1.0e-5);
    assertEquals(plain.getOutletStream().getTemperature(), compressor.getOutletStream().getTemperature(), 1.0e-4);
    assertEquals(plain.getPower(), compressor.getPower(), 0.1);
  }

  /** A fixed-speed chart calculation must retain absolute units after a non-bara setpoint. */
  @Test
  void fixedSpeedIgnoresSetpointUnit() {
    Compressor compressor = chart("schultz", "kJ/kg");
    compressor.run();
    double pressure = compressor.getOutletStream().getPressure("bara");
    compressor.setOutletPressure(1450.377, "psia");
    compressor.run();
    assertEquals(pressure, compressor.getOutletStream().getPressure("bara"), 1.0e-8);
    assertSameBasis(compressor);
  }

  /**
   * Power-specified pressure must invert the same forward polytropic relation.
   *
   * @param method selected polytropic method
   */
  @ParameterizedTest
  @ValueSource(strings = {"schultz", "detailed", "ideal_inlet_properties"})
  void specifiedPowerRecoversForwardPressure(String method) {
    Compressor forward = new Compressor("forward", inlet());
    forward.setUsePolytropicCalc(true);
    forward.setPolytropicMethod(method);
    forward.setPolytropicEfficiency(0.8);
    forward.setOutletPressure(100.0, "bara");
    forward.run();
    Compressor inverse = new Compressor("inverse", forward.getInletStream());
    inverse.setUsePolytropicCalc(true);
    inverse.setPolytropicMethod(method);
    inverse.setPolytropicEfficiency(0.8);
    inverse.setPower(forward.getPower());
    inverse.setCalcPressureOut(true);
    inverse.run();
    assertEquals(100.0, inverse.getOutletStream().getPressure("bara"), 1.0e-5);
    assertEquals(forward.getOutletStream().getTemperature(), inverse.getOutletStream().getTemperature(), 1.0e-4);
  }

  /**
   * Alternative pure-fluid property models must be retained in the head inversion.
   *
   * @param component pure component selecting the property model
   */
  @ParameterizedTest
  @ValueSource(strings = {"hydrogen", "helium"})
  void pureFluidChartMatchesSelectedModel(String component) {
    SystemSrkEos fluid = new SystemSrkEos(313.15, 40.0);
    fluid.addComponent(component, 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("pure feed", fluid);
    feed.setFlowRate(1.0, "kg/sec");
    feed.run();
    Compressor compressor = chart("schultz", "kJ/kg");
    compressor.setInletStream(feed);
    double flow = feed.getFlowRate("m3/hr");
    compressor.getCompressorChart().setCurves(new double[0], new double[] {8000.0, 10000.0},
        new double[][] {{0.5 * flow, flow, 1.5 * flow}, {0.5 * flow, flow, 1.5 * flow}},
        new double[][] {{60.0, 50.0, 40.0}, {90.0, 80.0, 70.0}},
        new double[][] {{80.0, 80.0, 80.0}, {80.0, 80.0, 80.0}});
    compressor.setUseLeachman("hydrogen".equals(component));
    compressor.setUseVega("helium".equals(component));
    compressor.run();
    Compressor plain = reference(compressor);
    assertEquals(plain.getPolytropicHead("kJ/kg"), compressor.getPolytropicHead("kJ/kg"), 1.0e-5);
    assertEquals(plain.getOutletStream().getTemperature(), compressor.getOutletStream().getTemperature(), 1.0e-4);
    assertEquals(plain.getPower(), compressor.getPower(), 0.1);
  }
}
