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
import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.ThermodynamicModelTest;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
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
   * The isentropic reference must retain the selected head basis across efficiency levels.
   *
   * @param efficiency chart efficiency as a fraction
   */
  @ParameterizedTest
  @ValueSource(doubles = {0.65, 0.80, 0.95})
  void solvedSpeedRetainsSchultzBasisAcrossEfficiencies(double efficiency) {
    Compressor compressor = chart("schultz", "kJ/kg");
    compressor.getCompressorChart().setCurves(new double[0], new double[] {8000.0, 10000.0},
        new double[][] {{1500.0, 2000.0, 2500.0}, {1875.0, 2500.0, 3125.0}},
        new double[][] {{110.0, 100.0, 85.0}, {171.9, 156.3, 132.8}},
        new double[][] {{100.0 * efficiency, 100.0 * efficiency, 100.0 * efficiency},
            {100.0 * efficiency, 100.0 * efficiency, 100.0 * efficiency}});
    compressor.run();
    assertSpeedReplay(compressor);
    assertSameBasis(compressor);
  }

  /**
   * Disabling a chart must restore efficiency-based calculation on the same compressor.
   *
   * @param method polytropic head basis
   * @param solveSpeed whether the initial chart run solves speed
   * @param polytropic whether the chart-free run uses polytropic compression
   */
  @ParameterizedTest
  @CsvSource({"schultz,false,true", "schultz,true,true", "detailed,false,true", "detailed,true,true",
      "ideal_inlet_properties,false,true", "ideal_inlet_properties,true,true", "schultz,false,false",
      "schultz,true,false"})
  void chartFreeRunUsesCurrentEfficiency(String method, boolean solveSpeed, boolean polytropic) {
    Compressor compressor = chart(method, "kJ/kg");
    compressor.setUsePolytropicCalc(polytropic);
    compressor.setOutletPressure(100.0, "bara");
    compressor.setSolveSpeed(solveSpeed);
    compressor.run();
    double outletPressure = compressor.getOutletPressure();
    compressor.getCompressorChart().setUseCompressorChart(false);
    compressor.setSolveSpeed(false);

    Compressor plain = new Compressor("plain", compressor.getInletStream());
    plain.setUsePolytropicCalc(polytropic);
    plain.setPolytropicMethod(method);
    plain.setOutletPressure(outletPressure, "bara");
    double previousTemperature = Double.NaN;
    for (double efficiency : new double[] {0.70, 0.90}) {
      compressor.setPolytropicEfficiency(efficiency);
      compressor.setIsentropicEfficiency(efficiency);
      plain.setPolytropicEfficiency(efficiency);
      plain.setIsentropicEfficiency(efficiency);
      compressor.run();
      plain.run();
      double temperature = compressor.getOutletStream().getTemperature();
      assertEquals(plain.getOutletStream().getTemperature(), temperature, 1.0e-6);
      assertEquals(plain.getPower(), compressor.getPower(), 0.1);
      if (Double.isFinite(previousTemperature)) {
        assertTrue(temperature < previousTemperature - 1.0);
      }
      previousTemperature = temperature;
    }
    assertFalse(compressor.powerSet, "A calculated chart duty is not an explicit power specification");
  }

  /**
   * A measured discharge temperature must remain fit-able after leaving chart mode.
   *
   * @param solveSpeed whether the initial chart run solves speed
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void solveEfficiencyAfterChartRun(boolean solveSpeed) {
    Compressor compressor = chart("schultz", "kJ/kg");
    compressor.setOutletPressure(100.0, "bara");
    compressor.setSolveSpeed(solveSpeed);
    compressor.run();
    double chartEfficiency = compressor.getPolytropicEfficiency();
    double target = compressor.getOutletStream().getTemperature() + 3.0;
    compressor.getCompressorChart().setUseCompressorChart(false);
    compressor.setSolveSpeed(false);
    double efficiency = compressor.solveEfficiency(target);
    assertTrue(efficiency > 0.0 && efficiency < chartEfficiency);
    assertEquals(target, compressor.getOutletStream().getTemperature(), 0.01);
    compressor.run();
    assertEquals(target, compressor.getOutletStream().getTemperature(), 0.01);
    assertFalse(compressor.powerSet);
  }

  /**
   * An explicit power specification must still override chart-free efficiency changes.
   *
   * @param polytropic whether to use polytropic compression
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void explicitPowerAfterChartRunRemainsSpecified(boolean polytropic) {
    Compressor compressor = chart("schultz", "kJ/kg");
    compressor.run();
    double power = 1.1 * compressor.getPower();
    compressor.getCompressorChart().setUseCompressorChart(false);
    compressor.setUsePolytropicCalc(polytropic);
    compressor.setPower(power);
    double previousTemperature = Double.NaN;
    for (double efficiency : new double[] {0.70, 0.90}) {
      compressor.setPolytropicEfficiency(efficiency);
      compressor.setIsentropicEfficiency(efficiency);
      compressor.run();
      assertTrue(compressor.powerSet);
      assertEquals(power, compressor.getPower(), 1.0e-6);
      double temperature = compressor.getOutletStream().getTemperature();
      if (Double.isFinite(previousTemperature)) {
        assertEquals(previousTemperature, temperature, 1.0e-8);
      }
      previousTemperature = temperature;
    }
  }

  /**
   * Recover a fixed-speed operating point through an independently initialized speed solve.
   *
   * @param compressor evaluated fixed-speed compressor
   */
  private void assertSpeedReplay(Compressor compressor) {
    double expectedPressure = compressor.getOutletStream().getPressure("bara");
    compressor.setOutletPressure(expectedPressure, "bara");
    compressor.setSpeed(8500.0);
    compressor.setSolveSpeed(true);
    compressor.run();
    assertTrue(compressor.isSpeedSolveConverged());
    assertEquals(expectedPressure, compressor.getOutletStream().getPressure("bara"), 1.0e-3);
    Compressor plain = reference(compressor);
    assertEquals(plain.getPolytropicHead("kJ/kg"), compressor.getPolytropicHead("kJ/kg"), 1.0e-5);
    assertEquals(plain.getOutletStream().getTemperature(), compressor.getOutletStream().getTemperature(), 1.0e-4);
    assertEquals(plain.getPower(), compressor.getPower(), 0.1);
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
    assertSpeedReplay(compressor);
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
   * A small coupled shaft duty must retain its finite pressure rise in a wet-gas inlet.
   *
   * @param method selected polytropic method
   * @param power small specified shaft power in W
   */
  @ParameterizedTest
  @CsvSource({"schultz,0.02", "schultz,0.2", "schultz,0.663991527050103", "detailed,0.02", "detailed,0.2",
      "detailed,0.663991527050103", "ideal_inlet_properties,0.02", "ideal_inlet_properties,0.2",
      "ideal_inlet_properties,0.663991527050103"})
  void smallWetGasPowerRetainsFinitePressureRise(String method, double power) {
    SystemSrkEos fluid = new SystemSrkEos(250.15, 55.95);
    fluid.addComponent("nitrogen", 0.006);
    fluid.addComponent("CO2", 0.014);
    fluid.addComponent("methane", 0.862);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("i-butane", 0.0024);
    fluid.addComponent("n-butane", 0.004);
    fluid.addComponent("n-hexane", 0.0015);
    fluid.setMixingRule(2);
    Stream feed = new Stream("wet feed", fluid);
    feed.setFlowRate(426000.0, "kg/hr");
    feed.run();
    assertEquals(2, feed.getFluid().getNumberOfPhases());
    Compressor compressor = new Compressor("small shaft duty", feed);
    compressor.setUsePolytropicCalc(true);
    compressor.setPolytropicMethod(method);
    compressor.setPolytropicEfficiency(0.2118026423311206);
    compressor.setPower(power);
    compressor.setCalcPressureOut(true);
    boolean warmStartBefore = ThermodynamicModelSettings.isUseWarmStartKValues();
    compressor.run();
    assertEquals(warmStartBefore, ThermodynamicModelSettings.isUseWarmStartKValues());
    double pressureRise = compressor.getOutletStream().getPressure("bara") - feed.getPressure("bara");
    assertTrue(pressureRise > 0.0 && pressureRise < 1.0e-6);
    double targetHead = power * compressor.getPolytropicEfficiency() / feed.getFlowRate("kg/sec") / 1000.0;
    assertEquals(targetHead, reference(compressor).getPolytropicHead("kJ/kg"), 1.0e-8);
    assertEquals(power, compressor.getPower(), 1.0e-5);
    assertEquals(power, compressor.getOutletStream().getFluid().getEnthalpy() - feed.getFluid().getEnthalpy(), 1.0e-5);
    assertEquals(feed.getFlowRate("kg/hr"), compressor.getOutletStream().getFlowRate("kg/hr"), 1.0e-6);
    compressor.run();
    assertEquals(power, compressor.getPower(), 1.0e-5);
    assertEquals(warmStartBefore, ThermodynamicModelSettings.isUseWarmStartKValues());
    SystemInterface outlet = compressor.getOutletStream().getFluid().clone();
    outlet.init(3);
    assertEquals(2, outlet.getNumberOfPhases());
    for (int component = 0; component < outlet.getNumberOfComponents(); component++) {
      double gasFugacity = outlet.getPhase(0).getComponent(component).getx()
          * outlet.getPhase(0).getComponent(component).getFugacityCoefficient();
      double liquidFugacity = outlet.getPhase(1).getComponent(component).getx()
          * outlet.getPhase(1).getComponent(component).getFugacityCoefficient();
      assertEquals(0.0, Math.log(gasFugacity / liquidFugacity), 1.0e-8);
    }
    ThermodynamicModelTest checker = new ThermodynamicModelTest(outlet);
    checker.setMaxError(1.0e-8);
    assertTrue(checker.checkFugacityCoefficients());
    assertTrue(checker.checkFugacityCoefficientsDn());
    assertTrue(checker.checkFugacityCoefficientsDn2());
    assertTrue(checker.checkFugacityCoefficientsDP());
    assertTrue(checker.checkFugacityCoefficientsDT());
    checker.setMaxError(1.0e-5);
    assertTrue(checker.checkNumerically());
  }

  /**
   * Small positive increments retain positive work and the thermodynamic differential limit.
   *
   * @param method selected polytropic method
   * @param pressureRise requested pressure increment in bar
   */
  @ParameterizedTest
  @CsvSource({"schultz,1e-8", "schultz,1e-6", "schultz,3.168e-6", "schultz,3.232e-6", "detailed,1e-8", "detailed,1e-6",
      "detailed,3.168e-6", "detailed,3.232e-6", "ideal_inlet_properties,1e-8", "ideal_inlet_properties,1e-6",
      "ideal_inlet_properties,3.168e-6", "ideal_inlet_properties,3.232e-6"})
  void smallPressureRiseMatchesDifferentialWork(String method, double pressureRise) {
    Stream feed = inlet();
    feed.setFlowRate(10000.0, "kg/hr");
    feed.run();
    Compressor compressor = new Compressor("small pressure increment", feed);
    compressor.setUsePolytropicCalc(true);
    compressor.setPolytropicMethod(method);
    compressor.setPolytropicEfficiency(0.8);
    compressor.setOutletPressure(feed.getPressure("bara") + pressureRise, "bara");
    compressor.run();
    double actualRise = compressor.getOutletStream().getPressure("bara") - feed.getPressure("bara");
    double expectedHead = 1.0e5 * actualRise / feed.getFluid().getDensity() / 1000.0;
    assertTrue(compressor.getPower() > 0.0);
    assertTrue(compressor.getOutletStream().getTemperature() > feed.getTemperature());
    assertEquals(expectedHead, compressor.getPolytropicHead("kJ/kg"), 1.0e-8);
    double expectedPower = feed.getFlowRate("kg/sec") * expectedHead * 1000.0 / 0.8;
    assertEquals(expectedPower, compressor.getPower(), 4.0e-5);
    assertEquals(feed.getPressure("bara") + pressureRise, compressor.getOutletStream().getPressure("bara"), 1.0e-12);
    assertEquals(feed.getFlowRate("kg/hr"), compressor.getOutletStream().getFlowRate("kg/hr"), 1.0e-6);
  }

  /** The existing near-zero isentropic bypass remains unchanged by the polytropic repair. */
  @Test
  void smallIsentropicPressureRisePreservesLegacyBypass() {
    Compressor compressor = new Compressor("isentropic near-zero", inlet());
    compressor.setUsePolytropicCalc(false);
    compressor.setOutletPressure(compressor.getInletStream().getPressure("bara") + 1.0e-8, "bara");
    compressor.run();
    assertEquals(0.0, compressor.getPower());
    assertEquals(compressor.getInletStream().getTemperature(), compressor.getOutletStream().getTemperature());
  }

  /**
   * An unchanged first-run pressure must report zero work on each selected enthalpy basis.
   *
   * @param model selected property model
   */
  @ParameterizedTest
  @ValueSource(strings = {"eos", "gerg", "leachman", "vega"})
  void zeroPressureRiseOnFirstRunUsesSelectedEnthalpyReference(String model) {
    Stream feed;
    if ("leachman".equals(model) || "vega".equals(model)) {
      SystemSrkEos fluid = new SystemSrkEos(313.15, 40.0);
      fluid.addComponent("leachman".equals(model) ? "hydrogen" : "helium", 1.0);
      fluid.setMixingRule("classic");
      feed = new Stream("pure feed", fluid);
      feed.setFlowRate(1.0, "kg/sec");
      feed.run();
    } else {
      feed = inlet();
    }
    Compressor compressor = new Compressor("unchanged pressure", feed);
    compressor.setUsePolytropicCalc(true);
    compressor.setUseGERG2008("gerg".equals(model));
    compressor.setUseLeachman("leachman".equals(model));
    compressor.setUseVega("vega".equals(model));
    compressor.setOutletPressure(feed.getPressure("bara"), "bara");
    compressor.run();
    assertEquals(0.0, compressor.getPower());
    assertEquals(0.0, compressor.getPolytropicHead());
    assertEquals(0.0, compressor.getPolytropicHead("kJ/kg"));
    assertEquals(0.0, compressor.getPolytropicExponent());
    assertEquals(1.0, compressor.getActualCompressionRatio());
    assertEquals(feed.getTemperature(), compressor.getOutletStream().getTemperature());
    assertEquals(feed.getPressure("bara"), compressor.getOutletStream().getPressure("bara"));
  }

  /**
   * Small positive compression steps retain the selected density and enthalpy basis.
   *
   * @param model selected property model
   */
  @ParameterizedTest
  @ValueSource(strings = {"gerg", "leachman", "vega"})
  void smallPressureRiseRetainsSelectedPropertyModel(String model) {
    Stream feed;
    if ("gerg".equals(model)) {
      feed = inlet();
    } else {
      SystemSrkEos fluid = new SystemSrkEos(313.15, 40.0);
      fluid.addComponent("leachman".equals(model) ? "hydrogen" : "helium", 1.0);
      fluid.setMixingRule("classic");
      feed = new Stream("pure feed", fluid);
    }
    feed.setFlowRate(1.0, "kg/sec");
    feed.run();
    PhaseInterface inletPhase = feed.getThermoSystem().getPhase(0);
    double density;
    double[] inletProperties;
    if ("gerg".equals(model)) {
      density = inletPhase.getDensity_GERG2008();
      inletProperties = inletPhase.getProperties_GERG2008();
    } else if ("leachman".equals(model)) {
      density = inletPhase.getDensity_Leachman();
      inletProperties = inletPhase.getProperties_Leachman();
    } else {
      density = inletPhase.getDensity_Vega();
      inletProperties = inletPhase.getProperties_Vega();
    }
    double inletEnthalpy = inletProperties[7] * inletPhase.getNumberOfMolesInPhase();
    Compressor compressor = new Compressor("selected-model differential step", feed);
    compressor.setUsePolytropicCalc(true);
    compressor.setPolytropicEfficiency(0.8);
    compressor.setUseGERG2008("gerg".equals(model));
    compressor.setUseLeachman("leachman".equals(model));
    compressor.setUseVega("vega".equals(model));
    // Remain in the differential branch while resolving the duty above the PH flash's total-enthalpy noise.
    double requestedPressure = feed.getPressure("bara") + 1.0e-6;
    compressor.setOutletPressure(requestedPressure, "bara");
    compressor.run();
    PhaseInterface outletPhase = compressor.getThermoSystem().getPhase(0);
    double[] outletProperties;
    if ("gerg".equals(model)) {
      outletProperties = outletPhase.getProperties_GERG2008();
    } else if ("leachman".equals(model)) {
      outletProperties = outletPhase.getProperties_Leachman();
    } else {
      outletProperties = outletPhase.getProperties_Vega();
    }
    double outletEnthalpy = outletProperties[7] * outletPhase.getNumberOfMolesInPhase();
    double pressureRise = requestedPressure - feed.getPressure("bara");
    double expectedHead = 1.0e5 * pressureRise / density / 1000.0;
    double expectedPower = feed.getFlowRate("kg/sec") * expectedHead * 1000.0 / 0.8;
    double energyTolerance = Math.max(5.0e-8, 1.0e-4 * expectedPower);
    assertTrue(Double.isFinite(compressor.getPower()) && compressor.getPower() > 0.0);
    assertTrue(Double.isFinite(compressor.getPolytropicHead("kJ/kg")));
    assertEquals(expectedHead, compressor.getPolytropicHead("kJ/kg"), energyTolerance * 0.8 / 1000.0);
    assertEquals(expectedPower, compressor.getPower(), energyTolerance);
    assertEquals(expectedPower, outletEnthalpy - inletEnthalpy, energyTolerance,
        "The selected model must close the differential compression energy balance");
    assertEquals(requestedPressure, compressor.getOutletStream().getPressure("bara"), 1.0e-12);
    assertEquals(feed.getFlowRate("kg/sec"), compressor.getOutletStream().getFlowRate("kg/sec"), 1.0e-12);
  }

  /** An exact zero pressure rise clears every previously calculated chart-head representation. */
  @Test
  void zeroPressureRiseClearsPreviousChartHead() {
    Compressor compressor = chart("schultz", "kJ/kg");
    compressor.run();
    assertTrue(compressor.getPolytropicHead() > 0.0);
    compressor.getCompressorChart().setUseCompressorChart(false);
    compressor.setOutletPressure(compressor.getInletStream().getPressure("bara"), "bara");
    compressor.run();
    assertEquals(0.0, compressor.getPower());
    assertEquals(0.0, compressor.getPolytropicHead());
    assertEquals(0.0, compressor.getPolytropicHead("kJ/kg"));
    assertEquals(0.0, compressor.getPolytropicHead("meter"));
    assertEquals(0.0, compressor.getPolytropicExponent());
    assertEquals(1.0, compressor.getActualCompressionRatio());
    assertEquals(compressor.getInletStream().getTemperature(), compressor.getOutletStream().getTemperature());
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
    assertSpeedReplay(compressor);
  }
}
