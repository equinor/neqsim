package neqsim.process.processmodel.diagram;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/** Reusable fresh-model fixtures and directed-connection manifests for diagram projections. */
public final class ProcessDiagramGoldenFixtures {
  private ProcessDiagramGoldenFixtures() {
  }

  /** One fresh process fixture with its deterministic material-topology manifest. */
  public static final class Fixture {
    private final ProcessSystem processSystem;
    private final List<String> materialConnections;

    private Fixture(ProcessSystem processSystem, String... materialConnections) {
      this.processSystem = processSystem;
      List<String> sortedConnections = new ArrayList<String>(Arrays.asList(materialConnections));
      Collections.sort(sortedConnections);
      this.materialConnections = Collections.unmodifiableList(sortedConnections);
    }

    public ProcessSystem getProcessSystem() {
      return processSystem;
    }

    public List<String> getMaterialConnections() {
      return materialConnections;
    }
  }

  /** Creates the three-element linear train used by canonical, DOT, and DEXPI tests. */
  public static Fixture simpleTrain() {
    Stream feed = createFeed("feed");
    Heater heater = new Heater("heater", feed);
    Cooler cooler = new Cooler("cooler", heater.getOutletStream());
    ProcessSystem process = new ProcessSystem("simple train");
    process.add(feed);
    process.add(heater);
    process.add(cooler);
    return new Fixture(process, "feed->heater", "heater->cooler");
  }

  /** Creates a splitter/mixer train with two parallel material connections. */
  public static Fixture parallelBranchTrain() {
    Stream feed = createFeed("branch feed");
    feed.run();
    Splitter splitter = new Splitter("branch splitter", feed);
    splitter.setSplitFactors(new double[] { 0.4, 0.6 });
    splitter.run();
    StreamInterface firstBranch = splitter.getSplitStream(0);
    StreamInterface secondBranch = splitter.getSplitStream(1);
    Mixer mixer = new Mixer("branch mixer");
    mixer.addStream(firstBranch);
    mixer.addStream(secondBranch);
    ProcessSystem process = new ProcessSystem("parallel branch train");
    process.add(feed);
    process.add(splitter);
    process.add(mixer);
    return new Fixture(process, "branch feed->branch splitter", "branch splitter->branch mixer",
        "branch splitter->branch mixer");
  }

  private static Stream createFeed(String name) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 40.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("n-heptane", 0.2);
    fluid.setMixingRule("classic");
    Stream feed = new Stream(name, fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    return feed;
  }
}
