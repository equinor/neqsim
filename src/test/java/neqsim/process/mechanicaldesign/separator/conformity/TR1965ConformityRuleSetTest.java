package neqsim.process.mechanicaldesign.separator.conformity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.GasScrubber;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.separator.GasScrubberMechanicalDesign;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for TR1965 scrubber conformity checks.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class TR1965ConformityRuleSetTest {

  /**
   * Verifies factory support and a conforming mesh scrubber case.
   */
  @Test
  void evaluatesConfiguredGasScrubberAgainstTR1965() {
    Stream feed = new Stream("gas feed", gasFluid());
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    GasScrubber scrubber = new GasScrubber("TR1965 scrubber", feed);
    scrubber.run();
    scrubber.initMechanicalDesign();
    GasScrubberMechanicalDesign design = scrubber.getMechanicalDesign();
    design.setInnerDiameter(2.0);
    design.setMeshPad(3.0, 100.0);
    design.setLaHHElevationM(0.5);
    design.setInletDeviceElevationM(1.2);
    design.setMeshPadElevationM(2.3);
    design.setLiquidEntrainmentLitresPerMSm3(5.0);
    design.setLiquidDesignMarginFraction(0.25);
    design.setConformityRules("TR1965");

    ConformityReport report = design.checkConformity();

    assertEquals("TR1965", report.getStandard());
    assertTrue(report.isConforming());
    assertNotNull(ConformityRuleSet.create("Equinor TR1965"));
    assertTrue(report.toTextReport().contains("tr1965-k-factor"));
  }

  /**
   * Creates a simple gas fluid for scrubber conformity tests.
   *
   * @return methane SRK fluid
   */
  private SystemInterface gasFluid() {
    SystemInterface fluid = new SystemSrkEos(293.15, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    return fluid;
  }
}