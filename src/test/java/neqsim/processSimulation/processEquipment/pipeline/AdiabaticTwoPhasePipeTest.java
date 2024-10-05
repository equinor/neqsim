package neqsim.processSimulation.processEquipment.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.processsimulation.processequipment.pipeline.AdiabaticTwoPhasePipe;
import neqsim.processsimulation.processequipment.stream.Stream;

public class AdiabaticTwoPhasePipeTest {
  @Test
  public void testMain() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 5.0), 200.00);
    testSystem.addComponent("methane", 75, "MSm^3/day");
    testSystem.addComponent("n-heptane", 0.0000001, "MSm^3/day");
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.init(0);

    Stream stream_1 = new Stream("Stream1", testSystem);

    AdiabaticTwoPhasePipe pipe = new AdiabaticTwoPhasePipe("pipe1", stream_1);
    pipe.setLength(400.0 * 1e3);
    pipe.setDiameter(1.017112);
    pipe.setPipeWallRoughness(5e-6);

    AdiabaticTwoPhasePipe pipe2 = new AdiabaticTwoPhasePipe("pipe2", pipe.getOutletStream());
    pipe2.setLength(100.0);
    pipe2.setDiameter(0.3017112);
    pipe2.setPipeWallRoughness(5e-6);
    // pipe.setOutPressure(112.0);

    neqsim.processsimulation.processsystem.ProcessSystem operations =
        new neqsim.processsimulation.processsystem.ProcessSystem();
    operations.add(stream_1);
    operations.add(pipe);
    operations.add(pipe2);
    operations.run();
    // pipe.displayResult();
    // System.out.println("flow " + pipe2.getOutletStream().getFluid().getFlowRate("MSm3/day"));
    // System.out.println("out pressure " + pipe.getOutletStream().getPressure("bara"));
    // System.out.println("velocity " + pipe.getSuperficialVelocity());
    // System.out.println("out pressure " + pipe2.getOutletStream().getPressure("bara"));
    // System.out.println("velocity " + pipe2.getSuperficialVelocity());

    Assertions.assertEquals(75.0000001, pipe2.getOutletStream().getFluid().getFlowRate("MSm3/day"));
    Assertions.assertEquals(153.58741116226855, pipe.getOutletStream().getPressure("bara"));
    Assertions.assertEquals(4.207400548548574, pipe.getSuperficialVelocity());
    Assertions.assertEquals(146.28492500260614, pipe2.getOutletStream().getPressure("bara"));
    Assertions.assertEquals(60.751298047046646, pipe2.getSuperficialVelocity());
  }
}
