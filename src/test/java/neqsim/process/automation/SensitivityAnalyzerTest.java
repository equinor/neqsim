package neqsim.process.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link SensitivityAnalyzer} — finite-difference gradients over
 * {@link ProcessAutomation}.
 */
class SensitivityAnalyzerTest {

  private ProcessSystem buildCompressorProcess() {
    SystemInterface fluid = new SystemSrkEos(293.15, 30.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.setTemperature(20.0, "C");
    feed.setPressure(30.0, "bara");
    Compressor k = new Compressor("Compressor", feed);
    k.setPolytropicEfficiency(0.78);
    k.setOutletPressure(80.0, "bara");
    ProcessSystem ps = new ProcessSystem();
    ps.add(feed);
    ps.add(k);
    ps.run();
    return ps;
  }

  @Test
  void partialOfCompressorPowerWrtOutletPressureIsPositive() {
    ProcessSystem ps = buildCompressorProcess();
    SensitivityAnalyzer sens = new SensitivityAnalyzer(ps.getAutomation());
    double dPdPout = sens.partial("Compressor.outletPressure", "bara", "Compressor.power", "kW");
    // Raising discharge pressure must require more shaft power.
    assertTrue(dPdPout > 0.0, "∂(power)/∂(outletPressure) must be > 0, got " + dPdPout);
    assertTrue(Math.abs(dPdPout) < 1.0e5,
        "magnitude should be plausible (kW per bara), got " + dPdPout);
  }

  @Test
  void gradientReturnsOneEntryPerInput() {
    ProcessSystem ps = buildCompressorProcess();
    SensitivityAnalyzer sens = new SensitivityAnalyzer(ps.getAutomation());
    List<String> inputs =
        Arrays.asList("Compressor.outletPressure", "Compressor.polytropicEfficiency");
    Map<String, Double> g = sens.gradient("Compressor.power", "kW", inputs, null);
    assertEquals(2, g.size());
    assertTrue(g.containsKey("Compressor.outletPressure"));
    assertTrue(g.containsKey("Compressor.polytropicEfficiency"));
    // Higher efficiency → less power required.
    assertTrue(g.get("Compressor.polytropicEfficiency") < 0.0,
        "∂(power)/∂(eta) should be < 0, got " + g.get("Compressor.polytropicEfficiency"));
  }

  @Test
  void jacobianHasCorrectShape() {
    ProcessSystem ps = buildCompressorProcess();
    SensitivityAnalyzer sens = new SensitivityAnalyzer(ps.getAutomation());
    List<String> outputs = Arrays.asList("Compressor.power", "Compressor.outletStream.temperature");
    List<String> inputs =
        Arrays.asList("Compressor.outletPressure", "Compressor.polytropicEfficiency");
    double[][] j = sens.jacobian(outputs, null, inputs, null);
    assertEquals(2, j.length);
    assertEquals(2, j[0].length);
    // Outlet T rises with discharge pressure.
    assertTrue(j[1][0] > 0.0, "∂(Tout)/∂(Pout) should be > 0, got " + j[1][0]);
  }

  @Test
  void jacobianJsonIncludesSchemaVersionAndShape() {
    ProcessSystem ps = buildCompressorProcess();
    SensitivityAnalyzer sens = new SensitivityAnalyzer(ps.getAutomation());
    List<String> outputs = Arrays.asList("Compressor.power");
    List<String> inputs = Arrays.asList("Compressor.outletPressure");
    String json = sens.jacobianAsJson(outputs, "kW", inputs, "bara");
    assertNotNull(json);
    assertTrue(json.contains("\"schemaVersion\":\"" + SensitivityAnalyzer.SCHEMA_VERSION + "\""));
    assertTrue(json.contains("\"jacobian\""));
    assertTrue(json.contains("Compressor.outletPressure"));
  }

  @Test
  void forwardAndCentralAgreeOnLinearishResponse() {
    ProcessSystem ps = buildCompressorProcess();
    SensitivityAnalyzer central = new SensitivityAnalyzer(ps.getAutomation());
    SensitivityAnalyzer forward =
        new SensitivityAnalyzer(ps.getAutomation()).setMode(SensitivityAnalyzer.Mode.FORWARD);
    double dCentral =
        central.partial("Compressor.outletPressure", "bara", "Compressor.power", "kW");
    double dForward =
        forward.partial("Compressor.outletPressure", "bara", "Compressor.power", "kW");
    double diff = Math.abs(dCentral - dForward);
    double rel = diff / Math.max(1.0e-9, Math.abs(dCentral));
    assertTrue(rel < 0.05, "central vs forward should agree to ~5%, got rel=" + rel);
  }

  @Test
  void constructorRejectsNullAutomation() {
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        new SensitivityAnalyzer(null);
      }
    });
  }

  @Test
  void setRelativeStepRejectsNonPositive() {
    ProcessSystem ps = buildCompressorProcess();
    final SensitivityAnalyzer sens = new SensitivityAnalyzer(ps.getAutomation());
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        sens.setRelativeStep(0.0);
      }
    });
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        sens.setAbsoluteStep(-1.0);
      }
    });
  }
}
