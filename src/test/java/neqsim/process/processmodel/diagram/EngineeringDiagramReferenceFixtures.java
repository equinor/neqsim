package neqsim.process.processmodel.diagram;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/** Synthetic, public regression fixtures for the coordinated PFD and DEXPI/P&amp;ID campaigns. */
public final class EngineeringDiagramReferenceFixtures {
  private EngineeringDiagramReferenceFixtures() {
  }

  /** One executable ProcessSystem reference case and its material-topology contract. */
  public static final class SystemCase {
    private final String caseId;
    private final ProcessSystem processSystem;
    private final StreamInterface feed;
    private final List<StreamInterface> products;
    private final List<String> materialConnections;

    private SystemCase(String caseId, ProcessSystem processSystem, StreamInterface feed, List<StreamInterface> products,
        String... materialConnections) {
      this.caseId = caseId;
      this.processSystem = processSystem;
      this.feed = feed;
      this.products = Collections.unmodifiableList(new ArrayList<StreamInterface>(products));
      this.materialConnections = sorted(materialConnections);
    }

    public String getCaseId() {
      return caseId;
    }

    public ProcessSystem getProcessSystem() {
      return processSystem;
    }

    public StreamInterface getFeed() {
      return feed;
    }

    public List<StreamInterface> getProducts() {
      return products;
    }

    public List<String> getMaterialConnections() {
      return materialConnections;
    }
  }

  /** One executable multi-area ProcessModel reference case and its material-topology contract. */
  public static final class ModelCase {
    private final String caseId;
    private final ProcessModel processModel;
    private final StreamInterface feed;
    private final List<StreamInterface> products;
    private final List<String> areaNames;
    private final List<String> materialConnections;

    private ModelCase(String caseId, ProcessModel processModel, StreamInterface feed, List<StreamInterface> products,
        List<String> areaNames, String... materialConnections) {
      this.caseId = caseId;
      this.processModel = processModel;
      this.feed = feed;
      this.products = Collections.unmodifiableList(new ArrayList<StreamInterface>(products));
      this.areaNames = Collections.unmodifiableList(new ArrayList<String>(areaNames));
      this.materialConnections = sorted(materialConnections);
    }

    public String getCaseId() {
      return caseId;
    }

    public ProcessModel getProcessModel() {
      return processModel;
    }

    public StreamInterface getFeed() {
      return feed;
    }

    public List<StreamInterface> getProducts() {
      return products;
    }

    public List<String> getAreaNames() {
      return areaNames;
    }

    public List<String> getMaterialConnections() {
      return materialConnections;
    }
  }

  /** Feed, isolation/control valve, separator, compressor, cooler, and product boundaries. */
  public static SystemCase simpleTrain() {
    Stream feed = createFeed("10-FEED-001", 12000.0);
    ThrottlingValve inletValve = new ThrottlingValve("10-XV-001", feed);
    inletValve.setOutletPressure(50.0, "bara");
    Separator separator = new Separator("10-VA-001", inletValve.getOutletStream());
    Compressor compressor = new Compressor("10-KA-001", separator.getGasOutStream());
    compressor.setOutletPressure(75.0, "bara");
    Cooler gasCooler = new Cooler("10-HA-001", compressor.getOutletStream());
    gasCooler.setOutTemperature(30.0, "C");

    ProcessSystem process = new ProcessSystem("DEXPI simple reference train");
    process.add(feed);
    process.add(inletValve);
    process.add(separator);
    process.add(compressor);
    process.add(gasCooler);

    return new SystemCase("DEXPI-REF-SIMPLE", process, feed,
        Arrays.<StreamInterface>asList(gasCooler.getOutletStream(), separator.getLiquidOutStream()),
        "10-FEED-001->10-XV-001", "10-XV-001->10-VA-001", "10-VA-001->10-KA-001", "10-KA-001->10-HA-001");
  }

  /** Separator gas compression and liquid pumping branches with explicit product boundaries. */
  public static SystemCase branchedSeparatorCompressionTrain() {
    Stream feed = createFeed("20-FEED-001", 20000.0);
    Separator separator = new Separator("20-VA-001", feed);
    Compressor compressor = new Compressor("20-KA-001", separator.getGasOutStream());
    compressor.setOutletPressure(80.0, "bara");
    Cooler gasCooler = new Cooler("20-HA-001", compressor.getOutletStream());
    gasCooler.setOutTemperature(32.0, "C");
    Pump liquidPump = new Pump("20-PA-001", separator.getLiquidOutStream());
    liquidPump.setOutletPressure(80.0, "bara");
    Cooler liquidCooler = new Cooler("20-HB-001", liquidPump.getOutletStream());
    liquidCooler.setOutTemperature(35.0, "C");

    ProcessSystem process = new ProcessSystem("DEXPI branched separator compression reference");
    process.add(feed);
    process.add(separator);
    process.add(compressor);
    process.add(gasCooler);
    process.add(liquidPump);
    process.add(liquidCooler);

    return new SystemCase("DEXPI-REF-BRANCHED", process, feed,
        Arrays.<StreamInterface>asList(gasCooler.getOutletStream(), liquidCooler.getOutletStream()),
        "20-FEED-001->20-VA-001", "20-VA-001->20-KA-001", "20-KA-001->20-HA-001", "20-VA-001->20-PA-001",
        "20-PA-001->20-HB-001");
  }

  /** Inlet, compression, export, and flare-area topology sharing one semantic ProcessModel. */
  public static ModelCase multiAreaFacility() {
    Stream feed = createFeed("30-FEED-001", 30000.0);
    ThrottlingValve inletValve = new ThrottlingValve("30-XV-001", feed);
    inletValve.setOutletPressure(52.0, "bara");
    Separator separator = new Separator("30-VA-001", inletValve.getOutletStream());
    Splitter gasAllocation = new Splitter("30-SP-001", separator.getGasOutStream());
    gasAllocation.setSplitFactors(new double[] { 0.98, 0.02 });
    ProcessSystem inlet = new ProcessSystem("Inlet process area");
    inlet.add(feed);
    inlet.add(inletValve);
    inlet.add(separator);
    inlet.add(gasAllocation);

    Compressor compressor = new Compressor("31-KA-001", gasAllocation.getSplitStream(0));
    compressor.setOutletPressure(85.0, "bara");
    Cooler afterCooler = new Cooler("31-HA-001", compressor.getOutletStream());
    afterCooler.setOutTemperature(35.0, "C");
    ProcessSystem compression = new ProcessSystem("Compression process area");
    compression.add(compressor);
    compression.add(afterCooler);

    AdiabaticPipe exportPipeline = new AdiabaticPipe("32-PL-001", afterCooler.getOutletStream());
    exportPipeline.setLength(500.0);
    exportPipeline.setDiameter(0.40);
    ProcessSystem export = new ProcessSystem("Export process area");
    export.add(exportPipeline);

    AdiabaticPipe flareBoundary = new AdiabaticPipe("40-PL-001", gasAllocation.getSplitStream(1));
    flareBoundary.setLength(100.0);
    flareBoundary.setDiameter(0.20);
    ProcessSystem flare = new ProcessSystem("Flare boundary area");
    flare.add(flareBoundary);

    ProcessModel model = new ProcessModel();
    model.add("Inlet", inlet);
    model.add("Compression", compression);
    model.add("Export", export);
    model.add("Flare", flare);

    return new ModelCase("DEXPI-REF-MULTI-AREA", model, feed,
        Arrays.<StreamInterface>asList(exportPipeline.getOutletStream(), flareBoundary.getOutletStream(),
            separator.getLiquidOutStream()),
        Arrays.asList("Inlet", "Compression", "Export", "Flare"), "30-FEED-001->30-XV-001", "30-XV-001->30-VA-001",
        "30-VA-001->30-SP-001", "30-SP-001->31-KA-001", "31-KA-001->31-HA-001", "31-HA-001->32-PL-001",
        "30-SP-001->40-PL-001");
  }

  private static Stream createFeed(String name, double flowRateKgPerHour) {
    SystemSrkEos fluid = new SystemSrkEos(303.15, 60.0);
    fluid.addComponent("methane", 0.78);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("n-heptane", 0.14);
    fluid.setMixingRule("classic");
    Stream feed = new Stream(name, fluid);
    feed.setFlowRate(flowRateKgPerHour, "kg/hr");
    return feed;
  }

  private static List<String> sorted(String... values) {
    List<String> result = new ArrayList<String>(Arrays.asList(values));
    Collections.sort(result);
    return Collections.unmodifiableList(result);
  }
}
