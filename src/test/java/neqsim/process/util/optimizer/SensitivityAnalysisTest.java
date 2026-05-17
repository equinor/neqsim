package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for SensitivityAnalysis one-at-a-time parameter sweep.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class SensitivityAnalysisTest extends neqsim.NeqSimTest {

  private static ProcessSystem process;

  @BeforeAll
  public static void setUp() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 60.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");

    Compressor comp = new Compressor("Comp", feed);
    comp.setOutletPressure(100.0);

    Cooler cooler = new Cooler("Cooler", comp.getOutletStream());
    cooler.setOutTemperature(273.15 + 30.0);

    process = new ProcessSystem();
    process.add(feed);
    process.add(comp);
    process.add(cooler);
    process.run();
  }

  @Test
  public void testPressureSweep() {
    SensitivityAnalysis sa = new SensitivityAnalysis(process);

    sa.setParameter("Outlet Pressure", 70.0, 150.0, 5, (proc, val) -> {
      Compressor c = (Compressor) proc.getUnit("Comp");
      c.setOutletPressure(val);
    });

    sa.addOutput("Compressor Power (kW)", proc -> {
      Compressor c = (Compressor) proc.getUnit("Comp");
      return c.getPower("kW");
    });

    SensitivityAnalysis.SensitivityResult result = sa.run();

    assertNotNull(result);
    assertEquals(5, result.getSize());
    assertTrue(result.getSuccessCount() >= 3, "Most runs should succeed");
  }

  @Test
  public void testToJson() {
    SensitivityAnalysis sa = new SensitivityAnalysis(process);

    sa.setParameter("Pressure", 60.0, 120.0, 3, (proc, val) -> {
      Compressor c = (Compressor) proc.getUnit("Comp");
      c.setOutletPressure(val);
    });

    sa.addOutput("Power", proc -> {
      Compressor c = (Compressor) proc.getUnit("Comp");
      return c.getPower("kW");
    });

    SensitivityAnalysis.SensitivityResult result = sa.run();
    String json = result.toJson();

    assertNotNull(json);
    assertFalse(json.isEmpty());
    assertTrue(json.contains("Pressure"), "JSON should contain parameter name");
  }
}
