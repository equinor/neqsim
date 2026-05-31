package neqsim.process.equipment.splitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link ComponentCaptureUnit} selective separation behavior.
 */
class ComponentCaptureUnitTest extends neqsim.NeqSimTest {

  @Test
  void testCapturesSelectedComponentAndPreservesMassBalance() {
    Stream feed = createFeed();
    ComponentCaptureUnit capture = new ComponentCaptureUnit("CO2 capture", feed);
    capture.setComponentName("CO2");
    capture.setCaptureFraction(0.90);
    capture.run();

    assertNotNull(capture.getCapturedStream());
    assertNotNull(capture.getTreatedStream());
    assertEquals(0.90, capture.getActualCaptureFraction(), 1.0e-6);
    assertTrue(Math.abs(capture.getMassBalance("kg/hr")) < 1.0e-6);
    assertTrue(capture.getCapturedComponentMoleFlow() > capture.getTreatedComponentMoleFlow());
    assertTrue(capture.toJson().contains("actualCaptureFraction"));
  }

  /**
   * Creates a simple H2/CO2 feed stream.
   *
   * @return configured feed stream
   */
  private Stream createFeed() {
    SystemInterface fluid = new SystemSrkEos(313.15, 25.0);
    fluid.addComponent("hydrogen", 8.0, "mole/sec");
    fluid.addComponent("CO2", 2.0, "mole/sec");
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.run();
    return feed;
  }
}
