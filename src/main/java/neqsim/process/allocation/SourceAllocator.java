package neqsim.process.allocation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * High-level facade for linear recovery-factor production allocation.
 *
 * <p>
 * Allocates the production of a process flowsheet back to its individual sources (wells, templates, commingled feeds)
 * using frozen per-unit per-component split factors and a linear proxy network. It is a fast, scalable alternative to
 * per-source component tagging: one rigorous base-case run with a single common component slate is enough, after which
 * any number of sources can be allocated by superposition at a cost that scales with components &#215; units, not with
 * the number of sources.
 * </p>
 *
 * <p>
 * The method works for any conservative separation / scrubber / column / valve / cooler / mixer / splitter network,
 * including recycle and reflux loops, and therefore applies to all oil and gas field process configurations. Reactive
 * or contacting units (amine, glycol, MEG, scavengers) are out of scope for strict mass conservation but are handled as
 * black boxes using whatever redistribution the base case produced; for hydrocarbon allocation, MEG/water handling does
 * not affect the result.
 * </p>
 *
 * <p>
 * <b>Typical usage:</b>
 * </p>
 *
 * <pre>
 * SourceAllocator allocator = new SourceAllocator();
 * allocator.setBaseCase(process); // process already run once
 * allocator.addSource("Well-A", wellAFeed);
 * allocator.addSource("Well-B", wellBFeed);
 * allocator.addCustodyOutlet("ExportGas", exportGas, ProductType.GAS);
 * allocator.addCustodyOutlet("ExportOil", exportOil, ProductType.OIL);
 * allocator.addCustodyOutlet("ProducedWater", water, ProductType.WATER);
 *
 * ProductionAllocationResult result = allocator.allocate();
 * double gasFromA = result.getProductAllocation("Well-A", ProductType.GAS, "kg/hr");
 * String json = result.toJson();
 * </pre>
 *
 * <p>
 * If no sources or custody outlets are tagged explicitly, they are auto-detected from the flowsheet topology (external
 * feeds become sources, terminal product streams become custody outlets with an inferred product type).
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class SourceAllocator implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger instance. */
  private static final Logger logger = LogManager.getLogger(SourceAllocator.class);

  /** Base-case process system. */
  private ProcessSystem process;

  /** Extracted frozen split factors. */
  private RecoveryFactorExtractor extractor;

  /** Assembled proxy network. */
  private AllocationNetwork network;

  /** Tagged production sources. */
  private final List<AllocationSource> sources = new ArrayList<>();

  /** Tagged custody outlets. */
  private final List<CustodyOutlet> custodyOutlets = new ArrayList<>();

  /** Whether to renormalise the result to base-case custody totals (mass closure). */
  private boolean enforceMassClosure = true;

  /** The linear solver (configurable). */
  private final LinearAllocationSolver solver = new LinearAllocationSolver();

  /**
   * Sets the base-case process and extracts the frozen split factors. The process must already have been run so that
   * all stream compositions and flows are populated.
   *
   * @param process the base-case process system; must be non-null and already solved
   * @return this allocator (fluent)
   */
  public SourceAllocator setBaseCase(ProcessSystem process) {
    this.process = process;
    this.extractor = new RecoveryFactorExtractor(process).extract();
    this.network = new AllocationNetwork(extractor);
    return this;
  }

  /**
   * Tags a production source by name and feed stream.
   *
   * @param name the source name; must be non-null and unique
   * @param feedStream the feed stream entering the process; must be non-null
   * @return this allocator (fluent)
   */
  public SourceAllocator addSource(String name, StreamInterface feedStream) {
    sources.add(new AllocationSource(name, feedStream));
    return this;
  }

  /**
   * Tags a production source.
   *
   * @param source the source to add; must be non-null
   * @return this allocator (fluent)
   */
  public SourceAllocator addSource(AllocationSource source) {
    sources.add(source);
    return this;
  }

  /**
   * Tags a custody outlet by name, stream and product category.
   *
   * @param name the outlet name; must be non-null and unique
   * @param stream the terminal product stream; must be non-null
   * @param productType the product category; must be non-null
   * @return this allocator (fluent)
   */
  public SourceAllocator addCustodyOutlet(String name, StreamInterface stream, ProductType productType) {
    custodyOutlets.add(new CustodyOutlet(name, stream, productType));
    return this;
  }

  /**
   * Tags a custody outlet.
   *
   * @param outlet the custody outlet to add; must be non-null
   * @return this allocator (fluent)
   */
  public SourceAllocator addCustodyOutlet(CustodyOutlet outlet) {
    custodyOutlets.add(outlet);
    return this;
  }

  /**
   * Sets whether the result is renormalised so each custody outlet's per-component totals exactly match the base-case
   * (measured) values. Enabled by default.
   *
   * @param enforceMassClosure {@code true} to enforce mass closure
   * @return this allocator (fluent)
   */
  public SourceAllocator setEnforceMassClosure(boolean enforceMassClosure) {
    this.enforceMassClosure = enforceMassClosure;
    return this;
  }

  /**
   * Runs the allocation: builds source injections, solves the linear network per component, maps node flows onto
   * custody outlets, and optionally renormalises to base-case totals.
   *
   * @return the allocation result
   * @throws IllegalStateException if {@link #setBaseCase(ProcessSystem)} has not been called
   */
  public ProductionAllocationResult allocate() {
    if (network == null) {
      throw new IllegalStateException("setBaseCase(process) must be called before allocate()");
    }
    if (sources.isEmpty()) {
      autoDetectSources();
    }
    if (custodyOutlets.isEmpty()) {
      autoDetectCustodyOutlets();
    }

    List<String> componentNames = extractor.getComponentNames();
    int numComp = componentNames.size();
    int numSources = sources.size();
    int numCustody = custodyOutlets.size();

    // Source injections and entry units.
    int[] entryUnits = new int[numSources];
    double[][] injections = new double[numSources][numComp];
    for (int j = 0; j < numSources; j++) {
      AllocationSource src = sources.get(j);
      entryUnits[j] = network.findEntryUnit(src.getFeedStream());
      if (entryUnits[j] < 0) {
	logger.warn("Source '{}' feed stream is not connected to any node; it will allocate zero.", src.getName());
      } else if (network.findProducer(src.getFeedStream()) != null) {
	logger.warn(
	    "Source '{}' feed stream is produced by a node inside the network (it is an internal stream, not an"
		+ " external feed). Its component flow is injected as a source AND routed by the network, which"
		+ " double-counts that contribution. Register an external (terminal) feed stream instead.",
	    src.getName());
      }
      for (int k = 0; k < numComp; k++) {
	injections[j][k] = RecoveryFactorExtractor.componentFlow(src.getFeedStream(), componentNames.get(k));
      }
    }

    LinearAllocationSolver.SolverResult solved = solver.solve(network, entryUnits, injections);

    // Map node flows onto custody outlets: alloc[j][c][k] = f_w(s,k) * v[k][w][j].
    double[][][] allocMole = new double[numSources][numCustody][numComp];
    int[][] producers = new int[numCustody][];
    for (int c = 0; c < numCustody; c++) {
      producers[c] = network.findProducer(custodyOutlets.get(c).getStream());
      if (producers[c] == null) {
	logger.warn("Custody outlet '{}' is not produced by any node; it will receive zero.",
	    custodyOutlets.get(c).getName());
      }
    }
    for (int c = 0; c < numCustody; c++) {
      if (producers[c] == null) {
	continue;
      }
      int w = producers[c][0];
      for (int k = 0; k < numComp; k++) {
	double f = network.getCustodyFactor(producers[c], k);
	if (f == 0.0) {
	  continue;
	}
	for (int j = 0; j < numSources; j++) {
	  allocMole[j][c][k] = f * solved.getNodeFlow(k, w, j);
	}
      }
    }

    String[] sourceNames = new String[numSources];
    for (int j = 0; j < numSources; j++) {
      sourceNames[j] = sources.get(j).getName();
    }
    String[] custodyNames = new String[numCustody];
    ProductType[] custodyTypes = new ProductType[numCustody];
    for (int c = 0; c < numCustody; c++) {
      custodyNames[c] = custodyOutlets.get(c).getName();
      custodyTypes[c] = custodyOutlets.get(c).getProductType();
    }
    double[] molarMass = new double[numComp];
    for (int k = 0; k < numComp; k++) {
      molarMass[k] = extractor.getMolarMass(componentNames.get(k));
    }

    ProductionAllocationResult result = new ProductionAllocationResult(sourceNames, custodyNames, custodyTypes,
	componentNames.toArray(new String[0]), molarMass, allocMole, solved.getDiagnostics());

    if (enforceMassClosure) {
      double[][] targets = new double[numCustody][numComp];
      for (int c = 0; c < numCustody; c++) {
	StreamInterface custodyStream = custodyOutlets.get(c).getStream();
	for (int k = 0; k < numComp; k++) {
	  targets[c][k] = RecoveryFactorExtractor.componentFlow(custodyStream, componentNames.get(k));
	}
      }
      result.renormalizeMassClosure(targets);
    }

    logger.info("Allocated {} sources to {} custody outlets ({} components); max residual {}", numSources, numCustody,
	numComp, result.getMaxResidual());
    return result;
  }

  /**
   * Auto-detects sources from external feed streams (inlets not produced by any node).
   */
  private void autoDetectSources() {
    int i = 1;
    for (StreamInterface feed : network.findSourceStreams()) {
      String name = feed.getName();
      if (name == null || name.trim().isEmpty()) {
	name = "Source-" + i;
      }
      sources.add(new AllocationSource(name, feed));
      i++;
    }
    logger.info("Auto-detected {} source streams.", sources.size());
  }

  /**
   * Auto-detects custody outlets from terminal product streams, inferring each product type from the stream's dominant
   * phase.
   */
  private void autoDetectCustodyOutlets() {
    int i = 1;
    for (StreamInterface product : network.findCustodyStreams()) {
      String name = product.getName();
      if (name == null || name.trim().isEmpty()) {
	name = "Custody-" + i;
      }
      custodyOutlets.add(new CustodyOutlet(name, product, inferProductType(product)));
      i++;
    }
    logger.info("Auto-detected {} custody outlet streams.", custodyOutlets.size());
  }

  /**
   * Infers a product category for a stream from the dominant phase (by mole fraction) of its fluid.
   *
   * @param stream the stream to classify; must be non-null
   * @return the inferred product type, or {@link ProductType#UNKNOWN} if classification fails
   */
  static ProductType inferProductType(StreamInterface stream) {
    try {
      SystemInterface fluid = stream.getFluid();
      double gas = 0.0;
      double oil = 0.0;
      double water = 0.0;
      for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
	PhaseInterface phase = fluid.getPhase(p);
	String type = phase.getPhaseTypeName();
	double beta = phase.getBeta();
	if (type == null) {
	  continue;
	}
	if (type.contains("gas")) {
	  gas += beta;
	} else if (type.contains("aqueous")) {
	  water += beta;
	} else {
	  oil += beta;
	}
      }
      if (gas >= oil && gas >= water) {
	return ProductType.GAS;
      }
      if (water >= oil) {
	return ProductType.WATER;
      }
      return ProductType.OIL;
    } catch (Exception e) {
      return ProductType.UNKNOWN;
    }
  }

  /**
   * Gets the recovery-factor extractor (available after {@link #setBaseCase(ProcessSystem)}).
   *
   * @return the extractor, or {@code null} if no base case is set
   */
  public RecoveryFactorExtractor getExtractor() {
    return extractor;
  }

  /**
   * Gets the assembled proxy network (available after {@link #setBaseCase(ProcessSystem)}).
   *
   * @return the network, or {@code null} if no base case is set
   */
  public AllocationNetwork getNetwork() {
    return network;
  }

  /**
   * Gets the configurable linear solver.
   *
   * @return the solver
   */
  public LinearAllocationSolver getSolver() {
    return solver;
  }
}
