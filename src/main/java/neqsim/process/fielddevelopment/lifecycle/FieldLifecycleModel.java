package neqsim.process.fielddevelopment.lifecycle;

import java.io.Serializable;
import neqsim.process.equipment.reservoir.SimpleReservoir;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Assembled NeqSim model used by the field-lifecycle simulator.
 *
 * <p>
 * The model provides explicit connection points between subsurface material balance and the surface process. The
 * process model itself remains a normal {@link ProcessSystem}; detailed user-built flowsheets can therefore replace the
 * reference model without changing the lifecycle simulator.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class FieldLifecycleModel implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String name;
  private final SimpleReservoir reservoir;
  private final ProcessSystem processSystem;
  private final StreamInterface reservoirOilProducer;
  private final StreamInterface reservoirWaterProducer;
  private final StreamInterface reservoirGasInjector;
  private final StreamInterface recoveredGas;
  private final Splitter gasAllocationSplitter;
  private final StreamInterface stabilizedOilExport;
  private final StreamInterface gasExport;
  private final StreamInterface compressedInjectionGas;
  private final StreamInterface hostOilFeed;
  private final StreamInterface hostGasFeed;
  private final StreamInterface hostWaterFeed;
  private FieldProductionPotentialProvider productionPotentialProvider;

  /**
   * Creates an assembled lifecycle model.
   *
   * @param name model/concept name
   * @param reservoir reservoir material-balance model
   * @param processSystem wells, SURF and facility process model
   * @param reservoirOilProducer oil producer stream removed from the reservoir
   * @param reservoirWaterProducer water producer stream removed from the reservoir
   * @param reservoirGasInjector gas stream added to the reservoir
   * @param recoveredGas combined produced gas upstream of export/injection allocation
   * @param gasAllocationSplitter splitter allocating gas between export and injection
   * @param stabilizedOilExport stabilized oil product stream
   * @param gasExport sales/export gas stream
   * @param compressedInjectionGas gas stream downstream of injection compression
   */
  public FieldLifecycleModel(String name, SimpleReservoir reservoir, ProcessSystem processSystem,
      StreamInterface reservoirOilProducer, StreamInterface reservoirWaterProducer,
      StreamInterface reservoirGasInjector, StreamInterface recoveredGas, Splitter gasAllocationSplitter,
      StreamInterface stabilizedOilExport, StreamInterface gasExport, StreamInterface compressedInjectionGas) {
    this(name, reservoir, processSystem, reservoirOilProducer, reservoirWaterProducer, reservoirGasInjector,
        recoveredGas, gasAllocationSplitter, stabilizedOilExport, gasExport, compressedInjectionGas, null, null, null);
  }

  /**
   * Creates an assembled lifecycle model with explicit existing-host feed streams for tieback studies.
   *
   * <p>
   * The detailed {@code ProcessSystem} should mix these streams with the new-field SURF arrival stream upstream of
   * shared processing equipment. The lifecycle simulator updates all three host rates every year before solving the
   * combined process.
   * </p>
   *
   * @param name model/concept name
   * @param reservoir reservoir material-balance model
   * @param processSystem wells, SURF and shared-facility process model
   * @param reservoirOilProducer new-field oil producer stream
   * @param reservoirWaterProducer new-field water producer stream
   * @param reservoirGasInjector new-field gas injector stream
   * @param recoveredGas combined recovered gas upstream of allocation
   * @param gasAllocationSplitter gas export/injection splitter
   * @param stabilizedOilExport combined stabilized-oil product stream
   * @param gasExport combined sales-gas stream
   * @param compressedInjectionGas compressed injection-gas stream
   * @param hostOilFeed existing-host oil feed, or null for greenfield
   * @param hostGasFeed existing-host free-gas feed, or null for greenfield
   * @param hostWaterFeed existing-host water feed, or null for greenfield
   */
  public FieldLifecycleModel(String name, SimpleReservoir reservoir, ProcessSystem processSystem,
      StreamInterface reservoirOilProducer, StreamInterface reservoirWaterProducer,
      StreamInterface reservoirGasInjector, StreamInterface recoveredGas, Splitter gasAllocationSplitter,
      StreamInterface stabilizedOilExport, StreamInterface gasExport, StreamInterface compressedInjectionGas,
      StreamInterface hostOilFeed, StreamInterface hostGasFeed, StreamInterface hostWaterFeed) {
    this.name = require(name, "name");
    this.reservoir = require(reservoir, "reservoir");
    this.processSystem = require(processSystem, "processSystem");
    this.reservoirOilProducer = require(reservoirOilProducer, "reservoirOilProducer");
    this.reservoirWaterProducer = require(reservoirWaterProducer, "reservoirWaterProducer");
    this.reservoirGasInjector = require(reservoirGasInjector, "reservoirGasInjector");
    this.recoveredGas = require(recoveredGas, "recoveredGas");
    this.gasAllocationSplitter = require(gasAllocationSplitter, "gasAllocationSplitter");
    this.stabilizedOilExport = require(stabilizedOilExport, "stabilizedOilExport");
    this.gasExport = require(gasExport, "gasExport");
    this.compressedInjectionGas = require(compressedInjectionGas, "compressedInjectionGas");
    this.hostOilFeed = hostOilFeed;
    this.hostGasFeed = hostGasFeed;
    this.hostWaterFeed = hostWaterFeed;
  }

  private static <T> T require(T value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value;
  }

  /** Returns the model or concept name. */
  public String getName() {
    return name;
  }

  /** Returns the mutable reservoir material-balance model. */
  public SimpleReservoir getReservoir() {
    return reservoir;
  }

  /** Returns the assembled wells, SURF and process-plant flowsheet. */
  public ProcessSystem getProcessSystem() {
    return processSystem;
  }

  /** Returns the oil-producing stream removed from the reservoir. */
  public StreamInterface getReservoirOilProducer() {
    return reservoirOilProducer;
  }

  /** Returns the water-producing stream removed from the reservoir. */
  public StreamInterface getReservoirWaterProducer() {
    return reservoirWaterProducer;
  }

  /** Returns the compressed-gas stream added to the reservoir. */
  public StreamInterface getReservoirGasInjector() {
    return reservoirGasInjector;
  }

  /** Returns recovered gas upstream of sales and injection allocation. */
  public StreamInterface getRecoveredGas() {
    return recoveredGas;
  }

  /** Returns the splitter that allocates recovered gas. */
  public Splitter getGasAllocationSplitter() {
    return gasAllocationSplitter;
  }

  /** Returns the stabilized oil export stream. */
  public StreamInterface getStabilizedOilExport() {
    return stabilizedOilExport;
  }

  /** Returns the compressed sales-gas export stream. */
  public StreamInterface getGasExport() {
    return gasExport;
  }

  /** Returns the gas stream downstream of injection compression. */
  public StreamInterface getCompressedInjectionGas() {
    return compressedInjectionGas;
  }

  /** Returns true when the detailed process exposes all existing-host feed streams. */
  public boolean hasHostProductionFeeds() {
    return hostOilFeed != null && hostGasFeed != null && hostWaterFeed != null;
  }

  /** Returns the existing-host oil feed, or null for a greenfield model. */
  public StreamInterface getHostOilFeed() {
    return hostOilFeed;
  }

  /** Returns the existing-host free-gas feed, or null for a greenfield model. */
  public StreamInterface getHostGasFeed() {
    return hostGasFeed;
  }

  /** Returns the existing-host water feed, or null for a greenfield model. */
  public StreamInterface getHostWaterFeed() {
    return hostWaterFeed;
  }

  /**
   * Sets an optional detailed-well, network, reservoir-schedule or surrogate production-potential provider.
   *
   * @param provider provider to use instead of the default linear PI/water-cut calculation
   * @return this model
   */
  public FieldLifecycleModel setProductionPotentialProvider(FieldProductionPotentialProvider provider) {
    productionPotentialProvider = provider;
    return this;
  }

  /** Returns the optional custom production-potential provider. */
  public FieldProductionPotentialProvider getProductionPotentialProvider() {
    return productionPotentialProvider;
  }
}
