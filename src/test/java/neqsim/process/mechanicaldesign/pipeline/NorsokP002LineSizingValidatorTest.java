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

    NorsokP002LineSizingValidator.LineSizingResult result =
        new NorsokP002LineSizingValidator().validate(pipe);

    assertEquals(NorsokP002LineSizingValidator.ServiceType.GAS, result.getServiceType());
    assertTrue(result.getVelocityMPerS() > 0.0);
    assertTrue(result.isAcceptable());
    assertTrue(result.toJson().contains("NORSOK P-002"));
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