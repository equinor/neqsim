package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport.Phase;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.UpstreamCompressibleVolume;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

@Tag("slow")
class TwoFluidPipeNetworkTest {
  @Test
  void serialBranchesAndStorageNodeCloseWholeNetworkMass() {
    TwoFluidPipeNetwork network = new TwoFluidPipeNetwork("serial network");
    network.addFixedPressureNode("source", 60.0e5);
    network.addCompressibleNode("manifold", createVolume(55.0e5, 1000.0));
    network.addFixedPressureNode("sink", 50.0e5);
    network.addPipe("upstream", "source", "manifold", createPipe("upstream"));
    network.addPipe("downstream", "manifold", "sink", createPipe("downstream"));

    network.runTransient(0.01, UUID.randomUUID());

    TwoFluidPipeNetwork.BalanceReport report = network.getLastBalanceReport();
    for (Phase phase : Phase.values()) {
      assertTrue(report.getRelativeResidual(phase) < 1.0e-8,
          phase + " network residual=" + report.getRelativeResidual(phase));
    }
    assertTrue(Double.isFinite(network.getNodePressurePa("manifold")));
    assertEquals(0.01, network.getSimulationTimeSeconds(), 0.0);
  }

  @Test
  void identicalSplitBranchesRemainSymmetric() {
    UpstreamCompressibleVolume splitter = createVolume(60.0e5, 1.0e5);
    TwoFluidPipeNetwork network = new TwoFluidPipeNetwork("symmetric split");
    network.addCompressibleNode("splitter", splitter);
    network.addFixedPressureNode("sink A", 50.0e5);
    network.addFixedPressureNode("sink B", 50.0e5);
    network.addPipe("branch A", "splitter", "sink A", createPipe("branch A"));
    network.addPipe("branch B", "splitter", "sink B", createPipe("branch B"));

    network.runTransient(0.01, UUID.randomUUID());

    TwoFluidMassBalanceReport first = network.getPipe("branch A").getLastMassBalanceReport();
    TwoFluidMassBalanceReport second = network.getPipe("branch B").getLastMassBalanceReport();
    assertEquals(first.getOutletMassKg(Phase.TOTAL), second.getOutletMassKg(Phase.TOTAL), 1.0e-12);
    assertTrue(network.getLastBalanceReport().getRelativeResidual(Phase.TOTAL) < 1.0e-8);
  }

  private static TwoFluidPipe createPipe(String name) {
    SystemInterface fluid = new SystemSrkEos(300.0, 60.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("n-heptane", 0.05);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream feed = new Stream(name + " feed", fluid);
    feed.setFlowRate(2.0, "kg/sec");
    feed.run();

    TwoFluidPipe pipe = new TwoFluidPipe(name, feed);
    pipe.setLength(50.0);
    pipe.setDiameter(0.20);
    pipe.setNumberOfSections(4);
    pipe.setElevationProfile(new double[4]);
    return pipe;
  }

  private static UpstreamCompressibleVolume createVolume(double pressurePa, double volumeM3) {
    double[] density = { 45.0, 700.0, 1000.0 };
    double[] soundSpeed = { 350.0, 1200.0, 1450.0 };
    double[] mass = { 0.90 * volumeM3 * density[0], 0.10 * volumeM3 * density[1], 0.0 };
    return new UpstreamCompressibleVolume(volumeM3, pressurePa, mass, density, soundSpeed);
  }
}
