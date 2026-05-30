package neqsim.process.measurementdevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the new dynamic / safety measurement devices added in v1.1.
 */
class NewMeasurementDevicesTest {

  private Stream makeStream(String name, double pBara, double tC, String[] comps, double[] zMole) {
    SystemInterface fluid = new SystemSrkEos(273.15 + tC, pBara);
    for (int i = 0; i < comps.length; i++) {
      fluid.addComponent(comps[i], zMole[i]);
    }
    fluid.setMixingRule("classic");
    Stream s = new Stream(name, fluid);
    s.setFlowRate(1000.0, "kg/hr");
    s.setTemperature(tC, "C");
    s.setPressure(pBara, "bara");
    s.run();
    return s;
  }

  @Test
  void differentialPressureTransmitterReportsDelta() {
    Stream upstream = makeStream("up", 70.0, 25.0, new String[] {"methane"}, new double[] {1.0});
    Stream downstream = makeStream("dn", 30.0, 25.0, new String[] {"methane"}, new double[] {1.0});
    DifferentialPressureTransmitter dp =
        new DifferentialPressureTransmitter("PDT-001", upstream, downstream);
    assertEquals(40.0, dp.getMeasuredValue("bar"), 1.0e-6);
    assertNotNull(dp.getHighPressureStream());
    assertNotNull(dp.getLowPressureStream());
    assertEquals("PDT-001", dp.getName());
  }

  @Test
  void differentialPressureCanBeNegativeForReversedFlow() {
    Stream upstream = makeStream("up", 20.0, 25.0, new String[] {"methane"}, new double[] {1.0});
    Stream downstream = makeStream("dn", 50.0, 25.0, new String[] {"methane"}, new double[] {1.0});
    DifferentialPressureTransmitter dp = new DifferentialPressureTransmitter(upstream, downstream);
    assertTrue(dp.getMeasuredValue("bar") < 0.0, "ΔP must be negative when low > high");
  }

  @Test
  void differentialPressureRejectsNullStreams() {
    Stream s = makeStream("up", 70.0, 25.0, new String[] {"methane"}, new double[] {1.0});
    assertThrows(IllegalArgumentException.class,
        () -> new DifferentialPressureTransmitter(null, s));
    assertThrows(IllegalArgumentException.class,
        () -> new DifferentialPressureTransmitter(s, null));
  }

  @Test
  void compositionAnalyzerReadsGasMoleFraction() {
    // Methane-rich two-phase mixture at 50 bara, 25C: methane mostly in gas.
    Stream s =
        makeStream("two", 50.0, 25.0, new String[] {"methane", "nC10"}, new double[] {0.6, 0.4});
    CompositionAnalyzer aiGas =
        new CompositionAnalyzer("AI-001", s, "methane", CompositionAnalyzer.AnalyzerPhase.GAS);
    double x = aiGas.getMeasuredValue("");
    assertTrue(x > 0.9, "methane mole fraction in gas phase should be > 0.9, got " + x);
    assertEquals("methane", aiGas.getComponentName());
    assertEquals(CompositionAnalyzer.AnalyzerPhase.GAS, aiGas.getAnalyzerPhase());

    CompositionAnalyzer aiOverall =
        new CompositionAnalyzer("AI-002", s, "methane", CompositionAnalyzer.AnalyzerPhase.OVERALL);
    assertEquals(0.6, aiOverall.getMeasuredValue(""), 1.0e-6);
  }

  @Test
  void compositionAnalyzerReturnsNaNForUnknownComponent() {
    Stream s = makeStream("two", 50.0, 25.0, new String[] {"methane"}, new double[] {1.0});
    CompositionAnalyzer ai =
        new CompositionAnalyzer(s, "ethane", CompositionAnalyzer.AnalyzerPhase.OVERALL);
    assertTrue(Double.isNaN(ai.getMeasuredValue("")));
  }

  @Test
  void compositionAnalyzerRejectsBadInputs() {
    Stream s = makeStream("s", 30.0, 25.0, new String[] {"methane"}, new double[] {1.0});
    assertThrows(IllegalArgumentException.class,
        () -> new CompositionAnalyzer(s, " ", CompositionAnalyzer.AnalyzerPhase.OVERALL));
    assertThrows(IllegalArgumentException.class, () -> new CompositionAnalyzer(s, "methane", null));
  }

  @Test
  void flowRatioMeterReportsRatioOnMassBasis() {
    Stream num = makeStream("num", 30.0, 25.0, new String[] {"methane"}, new double[] {1.0});
    Stream den = makeStream("den", 30.0, 25.0, new String[] {"methane"}, new double[] {1.0});
    num.setFlowRate(800.0, "kg/hr");
    num.run();
    den.setFlowRate(200.0, "kg/hr");
    den.run();
    FlowRatioMeter meter = new FlowRatioMeter("FY-001", num, den, FlowRatioMeter.FlowBasis.MASS);
    assertEquals(4.0, meter.getMeasuredValue(""), 1.0e-6);
    assertEquals(FlowRatioMeter.FlowBasis.MASS, meter.getFlowBasis());
  }

  @Test
  void flowRatioMeterReturnsNaNForZeroDenominator() {
    Stream num = makeStream("num", 30.0, 25.0, new String[] {"methane"}, new double[] {1.0});
    Stream den = makeStream("den", 30.0, 25.0, new String[] {"methane"}, new double[] {1.0});
    den.setFlowRate(0.0, "kg/hr");
    den.run();
    FlowRatioMeter meter = new FlowRatioMeter(num, den, FlowRatioMeter.FlowBasis.MASS);
    assertTrue(Double.isNaN(meter.getMeasuredValue("")));
  }

  @Test
  void flowRatioMeterRejectsBadInputs() {
    Stream num = makeStream("n", 30.0, 25.0, new String[] {"methane"}, new double[] {1.0});
    assertThrows(IllegalArgumentException.class,
        () -> new FlowRatioMeter(null, num, FlowRatioMeter.FlowBasis.MASS));
    assertThrows(IllegalArgumentException.class,
        () -> new FlowRatioMeter(num, null, FlowRatioMeter.FlowBasis.MASS));
    assertThrows(IllegalArgumentException.class, () -> new FlowRatioMeter(num, num, null));
  }
}
