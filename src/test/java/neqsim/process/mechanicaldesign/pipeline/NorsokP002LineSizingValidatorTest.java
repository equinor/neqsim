package neqsim.process.mechanicaldesign.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for NORSOK P-002 line-sizing validator.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class NorsokP002LineSizingValidatorTest {

  /**
   * Verifies gas line velocity and pressure-gradient screening.
   */
  @Test
  void validatesGasPipelineFromPipeInterface() {
    Stream feed = new Stream("feed", gasFluid());
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("export pipe", feed);
    pipe.setLength(1000.0);
    pipe.setDiameter(0.25);

    NorsokP002LineSizingValidator.LineSizingResult result = new NorsokP002LineSizingValidator().validate(pipe);

    assertEquals(NorsokP002LineSizingValidator.ServiceType.GAS, result.getServiceType());
    assertTrue(result.getVelocityMPerS() > 0.0);
    assertTrue(result.isAcceptable());
    assertTrue(result.toJson().contains("NORSOK P-002"));
  }

  /** Checks actual bore selection, the maximum velocity and invalid inputs independently of the P&amp;ID writer. */
  @Test
  void roundsUpUsingTheScheduledBore() {
    Stream feed = new Stream("feed", gasFluid());
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();
    double flow = feed.getFlowRate("m3/sec");
    NorsokP002LineSizingValidator rule = new NorsokP002LineSizingValidator()
        .setMaximumGasVelocityMPerS(4 * flow / (Math.PI * 0.05 * 0.05));
    assertEquals(2.0, rule.recommendNominalPipeSize(feed.getFluid(), "40").getAsDouble(), 0);
    assertEquals(2.5, rule.recommendNominalPipeSize(feed.getFluid(), "80").getAsDouble(), 0);
    assertTrue(!rule.recommendNominalPipeSize(null, "40").isPresent());
    rule.setMaximumGasVelocityMPerS(1e-15);
    assertTrue(!rule.recommendNominalPipeSize(feed.getFluid(), "40").isPresent());
  }

  /**
   * Creates a simple gas fluid for line-sizing tests.
   *
   * @return methane-rich SRK fluid
   */
  private SystemInterface gasFluid() {
    SystemInterface fluid = new SystemSrkEos(293.15, 30.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");
    return fluid;
  }
}