package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;

/**
 * Tests boundary checks for PipeBeggsAndBrills profile accessors.
 */
public class PipeBeggsAndBrillsBoundaryTest {

  @Test
  public void testOutOfBoundsProfileIndex() {
    neqsim.thermo.system.SystemInterface system =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 50.0);
    system.addComponent("methane", 1.0);
    system.setMixingRule(2);
    system.init(0);
    system.setTotalFlowRate(1.0, "kg/sec");
    system.initPhysicalProperties();

    Stream stream = new Stream("stream", system);
    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("pipe", stream);
    pipe.setLength(100.0);
    pipe.setDiameter(0.1);
    pipe.setNumberOfIncrements(1);

    neqsim.process.processmodel.ProcessSystem proc = new neqsim.process.processmodel.ProcessSystem();
    proc.add(stream);
    proc.add(pipe);
    proc.run();

    assertThrows(IndexOutOfBoundsException.class,
        () -> pipe.getSegmentLength(pipe.getNumberOfIncrements() + 1));
  }
}
