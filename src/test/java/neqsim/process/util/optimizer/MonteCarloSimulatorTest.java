package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for MonteCarloSimulator uncertainty analysis.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class MonteCarloSimulatorTest extends neqsim.NeqSimTest {

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

    process = new ProcessSystem();
    process.add(feed);
    process.add(comp);
    process.run();
  }

  @Test
  public void testBasicMonteCarlo() {
    MonteCarloSimulator mc = new MonteCarloSimulator(process, 20);
    mc.setSeed(42);

    mc.addTriangularParameter("Outlet Pressure", 80.0, 100.0, 130.0, (proc, val) -> {
      Compressor c = (Compressor) proc.getUnit("Comp");
      c.setOutletPressure(val);
    });

    mc.setOutputExtractor("Compressor Power (kW)", proc -> {
      Compressor c = (Compressor) proc.getUnit("Comp");
      return c.getPower("kW");
    });

    MonteCarloSimulator.MonteCarloResult result = mc.run();

    assertNotNull(result);
    assertTrue(result.getP50() > 0, "P50 power should be positive");
    assertTrue(result.getP10() <= result.getP50(), "P10 <= P50");
    assertTrue(result.getP50() <= result.getP90(), "P50 <= P90");
    assertTrue(result.getMean() > 0, "Mean should be positive");
    assertTrue(result.getStdDev() >= 0, "StdDev should be non-negative");
  }

  @Test
  public void testMultipleParameters() {
    MonteCarloSimulator mc = new MonteCarloSimulator(process, 15);
    mc.setSeed(123);

    mc.addTriangularParameter("Outlet Pressure", 80.0, 100.0, 120.0, (proc, val) -> {
      Compressor c = (Compressor) proc.getUnit("Comp");
      c.setOutletPressure(val);
    });

    mc.addUniformParameter("Feed Flow", 8000.0, 12000.0, (proc, val) -> {
      Stream s = (Stream) proc.getUnit("Feed");
      s.setFlowRate(val, "kg/hr");
    });

    mc.setOutputExtractor("Power", proc -> {
      Compressor c = (Compressor) proc.getUnit("Comp");
      return c.getPower("kW");
    });

    MonteCarloSimulator.MonteCarloResult result = mc.run();

    assertNotNull(result);
    assertTrue(result.getP10() <= result.getP90());
  }

  @Test
  public void testProbabilityBelow() {
    MonteCarloSimulator mc = new MonteCarloSimulator(process, 20);
    mc.setSeed(42);

    mc.addTriangularParameter("Pressure", 80.0, 100.0, 120.0, (proc, val) -> {
      Compressor c = (Compressor) proc.getUnit("Comp");
      c.setOutletPressure(val);
    });

    mc.setOutputExtractor("Power", proc -> {
      Compressor c = (Compressor) proc.getUnit("Comp");
      return c.getPower("kW");
    });

    MonteCarloSimulator.MonteCarloResult result = mc.run();

    double probBelow = result.getProbabilityBelow(result.getP90() + 1e6);
    assertTrue(probBelow >= 0.0 && probBelow <= 1.0, "Probability should be 0-1");
  }

  @Test
  public void testToJson() {
    MonteCarloSimulator mc = new MonteCarloSimulator(process, 10);
    mc.setSeed(99);

    mc.addTriangularParameter("Pressure", 80.0, 100.0, 120.0, (proc, val) -> {
      Compressor c = (Compressor) proc.getUnit("Comp");
      c.setOutletPressure(val);
    });

    mc.setOutputExtractor("Power", proc -> {
      Compressor c = (Compressor) proc.getUnit("Comp");
      return c.getPower("kW");
    });

    MonteCarloSimulator.MonteCarloResult result = mc.run();
    String json = result.toJson();

    assertNotNull(json);
    assertFalse(json.isEmpty());
    assertTrue(json.contains("P10"), "JSON should contain P10");
    assertTrue(json.contains("P50"), "JSON should contain P50");
    assertTrue(json.contains("P90"), "JSON should contain P90");
    assertTrue(json.contains("tornado"), "JSON should contain tornado");
    assertTrue(json.contains("inputParameters"), "JSON should contain input params");
  }

  @Test
  public void testTornadoData() {
    MonteCarloSimulator mc = new MonteCarloSimulator(process, 10);
    mc.setSeed(42);

    mc.addTriangularParameter("Pressure", 80.0, 100.0, 120.0, (proc, val) -> {
      Compressor c = (Compressor) proc.getUnit("Comp");
      c.setOutletPressure(val);
    });

    mc.addUniformParameter("Flow", 8000.0, 12000.0, (proc, val) -> {
      Stream s = (Stream) proc.getUnit("Feed");
      s.setFlowRate(val, "kg/hr");
    });

    mc.setOutputExtractor("Power", proc -> {
      Compressor c = (Compressor) proc.getUnit("Comp");
      return c.getPower("kW");
    });

    MonteCarloSimulator.MonteCarloResult result = mc.run();

    assertNotNull(result.getTornado());
    assertEquals(2, result.getTornado().size(), "Should have tornado entries for 2 parameters");

    // Tornado should be sorted by swing descending
    if (result.getTornado().size() >= 2) {
      assertTrue(result.getTornado().get(0).swing >= result.getTornado().get(1).swing,
          "Tornado should be sorted by swing descending");
    }
  }
}
