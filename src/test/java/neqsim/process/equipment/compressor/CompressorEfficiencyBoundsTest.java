package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests clamped efficiency bounds on compressor configuration.
 */
class CompressorEfficiencyBoundsTest {
  /**
   * Ensure efficiencies above 1 are clamped to 1 and non-positive values are clamped to a small positive floor.
   */
  @Test
  void testEfficiencyClamping() {
    Compressor compressor = new Compressor("efficiency clamp test");

    compressor.setIsentropicEfficiency(2.5);
    assertEquals(1.0, compressor.getIsentropicEfficiency(), 1e-12);

    compressor.setPolytropicEfficiency(1.8);
    assertEquals(1.0, compressor.getPolytropicEfficiency(), 1e-12);

    compressor.setIsentropicEfficiency(0.0);
    assertTrue(compressor.getIsentropicEfficiency() > 0.0 && compressor.getIsentropicEfficiency() <= 1.0);

    compressor.setPolytropicEfficiency(-0.2);
    assertTrue(compressor.getPolytropicEfficiency() > 0.0 && compressor.getPolytropicEfficiency() <= 1.0);
  }

  /**
   * Ensure deserialize-like direct field assignment is sanitized at run start.
   */
  @Test
  void testRunSanitizesDirectlyAssignedEfficiencyFields() {
    SystemInterface fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("methane", 1.0);
    fluid.createDatabase(true);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    Compressor compressor = new Compressor("deserialize clamp test", feed);
    compressor.setOutletPressure(40.0);

    // Simulate XStream-style direct field restoration that bypasses setters.
    compressor.isentropicEfficiency = 2.4462556625464034;
    compressor.polytropicEfficiency = 2.219867501109995;

    compressor.run();

    assertTrue(compressor.getIsentropicEfficiency() > 0.0 && compressor.getIsentropicEfficiency() <= 1.0);
    assertTrue(compressor.getPolytropicEfficiency() > 0.0 && compressor.getPolytropicEfficiency() <= 1.0);
  }
}
