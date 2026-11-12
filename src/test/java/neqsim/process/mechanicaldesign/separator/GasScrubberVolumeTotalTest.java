package neqsim.process.mechanicaldesign.separator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.GasScrubber;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/** Regression coverage for issue #4965: scrubber volumeTotal must match the sized scrubber geometry. */
class GasScrubberVolumeTotalTest extends neqsim.NeqSimTest {
  @Test
  void volumeTotalMatchesSizedScrubberGeometry() {
    SystemSrkEos fluid = new SystemSrkEos(273.15 + 25.0, 20.0);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("ethane", 0.1);
    fluid.addComponent("propane", 0.1);
    fluid.addComponent("n-pentane", 0.1);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.run();
    GasScrubber scrubber = new GasScrubber("scrubber", feed);
    scrubber.run();

    SeparatorMechanicalDesign design = scrubber.getMechanicalDesign();
    design.setMaxOperationPressure(20.0);
    design.calcDesign();

    double diameter = design.getInnerDiameter();
    double length = design.getTantanLength();
    assertTrue(diameter > 0.0, "scrubber must be sized");
    assertEquals(5.0 * diameter, length, 1e-12);
    double expectedVolume = Math.PI * Math.pow(diameter / 2.0, 2.0) * length;
    assertEquals(expectedVolume, design.getVolumeTotal(), expectedVolume * 1e-8);
  }
}
