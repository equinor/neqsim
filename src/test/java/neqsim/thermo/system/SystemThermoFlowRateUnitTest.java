package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Regression tests for the Am3/hr (actual volume flow) getFlowRate/setFlowRate round trip.
 */
public class SystemThermoFlowRateUnitTest extends neqsim.NeqSimTest {
  SystemInterface fluid;

  @BeforeEach
  void setup() {
    fluid = new SystemSrkEos(298.15, 1.01325);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(100.0, "Am3/hr");
  }

  @Test
  void testAm3PerHrRoundTrip() {
    assertEquals(100.0, fluid.getFlowRate("Am3/hr"), 1e-6);
  }

  @Test
  void testAm3AliasesMatchM3Aliases() {
    assertEquals(fluid.getFlowRate("m3/sec"), fluid.getFlowRate("Am3/sec"), 1e-9);
    assertEquals(fluid.getFlowRate("m3/min"), fluid.getFlowRate("Am3/min"), 1e-9);
    assertEquals(fluid.getFlowRate("m3/hr"), fluid.getFlowRate("Am3/hr"), 1e-9);
    assertEquals(fluid.getFlowRate("m3/day"), fluid.getFlowRate("Am3/day"), 1e-9);
  }

  @Test
  void testM3PerDayConsistentWithM3PerHr() {
    assertEquals(fluid.getFlowRate("m3/hr") * 24.0, fluid.getFlowRate("m3/day"), 1e-6);
  }

  @Test
  void testUnsupportedUnitStillThrows() {
    assertThrows(RuntimeException.class, () -> fluid.getFlowRate("not/a/unit"));
  }

  @Test
  void testStreamAm3PerHrRoundTrip() {
    SystemInterface streamFluid = new SystemSrkEos(298.15, 1.01325);
    streamFluid.addComponent("methane", 1.0);
    streamFluid.setMixingRule("classic");

    Stream stream = new Stream("test stream", streamFluid);
    stream.setFlowRate(100.0, "Am3/hr");

    ProcessSystem process = new ProcessSystem();
    process.add(stream);
    process.run();

    assertEquals(100.0, stream.getFlowRate("Am3/hr"), 1e-3);
  }
}
