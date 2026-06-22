package neqsim.process.allocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;

/**
 * Runs and compares the three production-allocation facility-split methods of {@link AllocationMethod} (component
 * ratio, all-in linear proxy, and isolated stand-alone re-simulation) on a single rigorous base case.
 *
 * <p>
 * The class can run one method at a time (so an analyst can inspect each in isolation) or run all three and produce an
 * {@link AllocationComparison} that auto-evaluates the results and recommends a method. All methods share one component
 * slate and, when {@link #setEnforceMassClosure(boolean) mass closure} is enabled (the default), each method partitions
 * exactly the same measured (commingled) custody totals using pro-rata component scaling, so the methods are compared
 * on a fair basis.
 * </p>
 *
 * <p>
 * Method mapping to the allocation-principles matrix:
 * </p>
 *
 * <table>
 * <caption>Facility-split methods and their closure</caption>
 * <tr>
 * <th>Method</th>
 * <th>Facility split (ORF)</th>
 * <th>Closure</th>
 * </tr>
 * <tr>
 * <td>{@link AllocationMethod#COMPONENT_RATIO}</td>
 * <td>One common per-component recovery factor for every source</td>
 * <td>Exact by construction</td>
 * </tr>
 * <tr>
 * <td>{@link AllocationMethod#ALL_IN}</td>
 * <td>Frozen per-equipment factors via superposition</td>
 * <td>Mass-closed by {@link SourceAllocator}</td>
 * </tr>
 * <tr>
 * <td>{@link AllocationMethod#STAND_ALONE}</td>
 * <td>Per-source re-simulation with the other feeds suppressed</td>
 * <td>Pro-rata component scaling to the measured totals</td>
 * </tr>
 * </table>
 *
 * <p>
 * Usage:
 * </p>
 *
 * <pre>
 * MultiMethodAllocator allocator = new MultiMethodAllocator();
 * allocator.setBaseCase(process); // already run once
 * allocator.addSource("Well-A", wellAFeed);
 * allocator.addSource("Well-B", wellBFeed);
 * allocator.addCustodyOutlet("ExportGas", exportGas, ProductType.GAS);
 * allocator.addCustodyOutlet("ExportOil", exportOil, ProductType.OIL);
 *
 * // Run one method:
 * ProductionAllocationResult standalone = allocator.allocate(AllocationMethod.STAND_ALONE);
 *
 * // Or run all three and compare:
 * AllocationComparison comparison = allocator.allocateAll();
 * AllocationMethod recommended = comparison.getRecommendedMethod();
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class MultiMethodAllocator {

  /** Class logger. */
  private static final Logger logger = LogManager.getLogger(MultiMethodAllocator.class);

  /** Molar flow unit used internally for reading stream component flows. */
  private static final String MOLAR_FLOW_UNIT = "mole/sec";

  /** Mass flow unit used when suppressing and restoring source feed rates for stand-alone runs. */
  private static final String MASS_FLOW_UNIT = "kg/hr";

  /** The rigorous base-case process, already run in its commingled state. */
  private ProcessSystem baseCase;

  /** Tagged production sources, in declared order. */
  private final List<AllocationSource> sources = new ArrayList<>();

  /** Tagged custody outlets, in declared order. */
  private final List<CustodyOutlet> custodyOutlets = new ArrayList<>();

  /**
   * Whether each method is renormalised (pro-rata component scaling) to the measured custody totals.
   */
  private boolean enforceMassClosure = true;

  /** Relative multiplier applied to suppressed source feeds during stand-alone re-simulation. */
  private double standaloneZeroFactor = 1.0e-10;

  /** Cached master component slate, built lazily at the first allocation. */
  private String[] componentNames;

  /** Cached molar mass per component (kg/mol), aligned with {@link #componentNames}. */
  private double[] molarMass;

  /**
   * Creates an empty multi-method allocator. Configure it with the builder-style setters before allocating.
   */
  public MultiMethodAllocator() {
  }

  /**
   * Sets the rigorous base-case process. The process must already have been run once in its commingled state.
   *
   * @param process the base-case process; must be non-null and already run
   * @return this allocator for chaining
   */
  public MultiMethodAllocator setBaseCase(ProcessSystem process) {
    this.baseCase = process;
    return this;
  }

  /**
   * Adds a production source by name and feed stream.
   *
   * @param name the unique source name; must be non-null
   * @param feedStream the feed stream entering the process for this source; must be non-null
   * @return this allocator for chaining
   */
  public MultiMethodAllocator addSource(String name, StreamInterface feedStream) {
    sources.add(new AllocationSource(name, feedStream));
    return this;
  }

  /**
   * Adds a production source.
   *
   * @param source the source to add; must be non-null
   * @return this allocator for chaining
   */
  public MultiMethodAllocator addSource(AllocationSource source) {
    sources.add(source);
    return this;
  }

  /**
   * Adds a custody outlet by name, stream and product type.
   *
   * @param name the unique custody outlet name; must be non-null
   * @param stream the terminal product stream; must be non-null
   * @param productType the product category; must be non-null
   * @return this allocator for chaining
   */
  public MultiMethodAllocator addCustodyOutlet(String name, StreamInterface stream, ProductType productType) {
    custodyOutlets.add(new CustodyOutlet(name, stream, productType));
    return this;
  }

  /**
   * Adds a custody outlet.
   *
   * @param outlet the custody outlet to add; must be non-null
   * @return this allocator for chaining
   */
  public MultiMethodAllocator addCustodyOutlet(CustodyOutlet outlet) {
    custodyOutlets.add(outlet);
    return this;
  }

  /**
   * Sets whether each method is renormalised (pro-rata component scaling) to the measured commingled custody totals so
   * that every method partitions exactly the same metered totals. Default {@code true}.
   *
   * @param enforceMassClosure {@code true} to enforce closure (recommended for fair comparison)
   * @return this allocator for chaining
   */
  public MultiMethodAllocator setEnforceMassClosure(boolean enforceMassClosure) {
    this.enforceMassClosure = enforceMassClosure;
    return this;
  }

  /**
   * Sets the relative multiplier applied to suppressed source feeds during stand-alone re-simulation (each other
   * source's mass rate is multiplied by this factor). Default {@code 1.0e-10}.
   *
   * @param standaloneZeroFactor the relative suppression multiplier; must be small and positive
   * @return this allocator for chaining
   */
  public MultiMethodAllocator setStandaloneZeroFactor(double standaloneZeroFactor) {
    this.standaloneZeroFactor = standaloneZeroFactor;
    return this;
  }

  /**
   * Runs a single allocation method.
   *
   * @param method the method to run; must be non-null
   * @return the allocation result
   * @throws IllegalStateException if the base case, sources or custody outlets have not been configured
   */
  public ProductionAllocationResult allocate(AllocationMethod method) {
    requireConfigured();
    ensureSlate();
    switch (method) {
    case COMPONENT_RATIO:
      return allocateComponentRatio();
    case ALL_IN:
      return allocateAllIn();
    case STAND_ALONE:
      return allocateStandAlone();
    default:
      throw new IllegalArgumentException("Unsupported allocation method: " + method);
    }
  }

  /**
   * Runs all three methods and builds a comparison that auto-evaluates the results and recommends a method. The
   * non-mutating methods (component ratio and all-in) are run first; stand-alone re-simulation runs last and restores
   * the base case afterwards.
   *
   * @return the comparison of all three methods
   * @throws IllegalStateException if the base case, sources or custody outlets have not been configured
   */
  public AllocationComparison allocateAll() {
    requireConfigured();
    ensureSlate();

    Map<AllocationMethod, ProductionAllocationResult> results = new LinkedHashMap<>();
    Map<AllocationMethod, Long> runtimeMillis = new LinkedHashMap<>();

    AllocationMethod[] order = new AllocationMethod[] { AllocationMethod.COMPONENT_RATIO, AllocationMethod.ALL_IN,
	AllocationMethod.STAND_ALONE };
    for (AllocationMethod method : order) {
      long t0 = System.currentTimeMillis();
      ProductionAllocationResult result = allocate(method);
      runtimeMillis.put(method, System.currentTimeMillis() - t0);
      results.put(method, result);
      logger.info("Allocation method {} completed in {} ms", method.getDisplayName(), runtimeMillis.get(method));
    }

    return new AllocationComparison(results, runtimeMillis);
  }

  /**
   * Builds the allocation result for the {@link AllocationMethod#COMPONENT_RATIO common overall recovery factor}
   * method. A single per-component recovery factor {@code RF[c][k] = metered[c][k] / fieldIn[k]} is extracted from the
   * commingled base case and applied to every source's own injection vector. This closes to the measured totals exactly
   * by construction.
   *
   * @return the component-ratio allocation result
   */
  private ProductionAllocationResult allocateComponentRatio() {
    double[][] metered = meteredCustodyMole();
    double[][] injection = sourceInjectionMole();

    int numSources = sources.size();
    int numCustody = custodyOutlets.size();
    int numComp = componentNames.length;

    double[] fieldIn = new double[numComp];
    for (int s = 0; s < numSources; s++) {
      for (int k = 0; k < numComp; k++) {
	fieldIn[k] += injection[s][k];
      }
    }

    double[][][] alloc = new double[numSources][numCustody][numComp];
    for (int c = 0; c < numCustody; c++) {
      for (int k = 0; k < numComp; k++) {
	if (fieldIn[k] > 0.0) {
	  double rf = metered[c][k] / fieldIn[k];
	  for (int s = 0; s < numSources; s++) {
	    alloc[s][c][k] = rf * injection[s][k];
	  }
	}
      }
    }

    return buildResult(alloc, "component-ratio");
  }

  /**
   * Builds the allocation result for the {@link AllocationMethod#ALL_IN all-in linear proxy} method by delegating to
   * {@link SourceAllocator}, which freezes per-equipment split factors and propagates each source through the network
   * by superposition.
   *
   * @return the all-in allocation result
   */
  private ProductionAllocationResult allocateAllIn() {
    SourceAllocator allocator = new SourceAllocator();
    allocator.setBaseCase(baseCase);
    for (AllocationSource source : sources) {
      allocator.addSource(source);
    }
    for (CustodyOutlet outlet : custodyOutlets) {
      allocator.addCustodyOutlet(outlet);
    }
    allocator.setEnforceMassClosure(enforceMassClosure);
    return allocator.allocate();
  }

  /**
   * Builds the allocation result for the {@link AllocationMethod#STAND_ALONE isolated stand-alone} method. The process
   * is run once per source with the other feeds suppressed to {@link #standaloneZeroFactor} of their base rate; the
   * resulting standalone custody component flows are recorded and, when mass closure is enforced, renormalised to the
   * measured commingled totals using pro-rata component scaling. The base-case feed rates are always restored.
   *
   * @return the stand-alone allocation result
   */
  private ProductionAllocationResult allocateStandAlone() {
    double[][] metered = meteredCustodyMole();

    int numSources = sources.size();
    int numCustody = custodyOutlets.size();
    int numComp = componentNames.length;

    double[] originalRate = new double[numSources];
    for (int s = 0; s < numSources; s++) {
      originalRate[s] = sources.get(s).getFeedStream().getFlowRate(MASS_FLOW_UNIT);
    }

    double[][][] alloc = new double[numSources][numCustody][numComp];
    try {
      for (int active = 0; active < numSources; active++) {
	for (int s = 0; s < numSources; s++) {
	  double rate = (s == active) ? originalRate[s] : originalRate[s] * standaloneZeroFactor;
	  sources.get(s).getFeedStream().setFlowRate(rate, MASS_FLOW_UNIT);
	}
	baseCase.run();
	for (int c = 0; c < numCustody; c++) {
	  StreamInterface custodyStream = custodyOutlets.get(c).getStream();
	  for (int k = 0; k < numComp; k++) {
	    alloc[active][c][k] = RecoveryFactorExtractor.componentFlow(custodyStream, componentNames[k]);
	  }
	}
	logger.info("Stand-alone re-simulation for source {} completed", sources.get(active).getName());
      }
    } finally {
      for (int s = 0; s < numSources; s++) {
	sources.get(s).getFeedStream().setFlowRate(originalRate[s], MASS_FLOW_UNIT);
      }
      baseCase.run();
    }

    ProductionAllocationResult result = buildResult(alloc, "stand-alone");
    if (enforceMassClosure) {
      result.renormalizeMassClosure(metered);
    }
    return result;
  }

  /**
   * Reads the measured (commingled base-case) custody component molar flows.
   *
   * @return the measured custody flows {@code [custody][component]} in mole/sec
   */
  private double[][] meteredCustodyMole() {
    double[][] metered = new double[custodyOutlets.size()][componentNames.length];
    for (int c = 0; c < custodyOutlets.size(); c++) {
      StreamInterface stream = custodyOutlets.get(c).getStream();
      for (int k = 0; k < componentNames.length; k++) {
	metered[c][k] = RecoveryFactorExtractor.componentFlow(stream, componentNames[k]);
      }
    }
    return metered;
  }

  /**
   * Reads the per-source base-case injection component molar flows.
   *
   * @return the source injections {@code [source][component]} in mole/sec
   */
  private double[][] sourceInjectionMole() {
    double[][] injection = new double[sources.size()][componentNames.length];
    for (int s = 0; s < sources.size(); s++) {
      StreamInterface stream = sources.get(s).getFeedStream();
      for (int k = 0; k < componentNames.length; k++) {
	injection[s][k] = RecoveryFactorExtractor.componentFlow(stream, componentNames[k]);
      }
    }
    return injection;
  }

  /**
   * Wraps an allocation array in a {@link ProductionAllocationResult} using the shared component slate and a uniform
   * per-component diagnostic tag.
   *
   * @param alloc the allocated molar flow {@code [source][custody][component]} in mole/sec; must be non-null
   * @param method the diagnostic method tag; must be non-null
   * @return the allocation result
   */
  private ProductionAllocationResult buildResult(double[][][] alloc, String method) {
    String[] sourceNames = new String[sources.size()];
    for (int s = 0; s < sources.size(); s++) {
      sourceNames[s] = sources.get(s).getName();
    }
    String[] custodyNames = new String[custodyOutlets.size()];
    ProductType[] custodyTypes = new ProductType[custodyOutlets.size()];
    for (int c = 0; c < custodyOutlets.size(); c++) {
      custodyNames[c] = custodyOutlets.get(c).getName();
      custodyTypes[c] = custodyOutlets.get(c).getProductType();
    }

    List<LinearAllocationSolver.ComponentDiagnostics> diagnostics = new ArrayList<>();
    for (int k = 0; k < componentNames.length; k++) {
      diagnostics.add(new LinearAllocationSolver.ComponentDiagnostics(k, method, 0.0, 0));
    }

    return new ProductionAllocationResult(sourceNames, custodyNames, custodyTypes, componentNames.clone(),
	molarMass.clone(), alloc, diagnostics);
  }

  /**
   * Builds the master component slate (union of components across all source and custody streams) and the aligned molar
   * masses, once per allocator run.
   */
  private void ensureSlate() {
    if (componentNames != null) {
      return;
    }
    Map<String, Double> mm = new LinkedHashMap<>();
    List<StreamInterface> all = new ArrayList<>();
    for (AllocationSource source : sources) {
      all.add(source.getFeedStream());
    }
    for (CustodyOutlet outlet : custodyOutlets) {
      all.add(outlet.getStream());
    }
    for (StreamInterface stream : all) {
      SystemInterface fluid = stream.getFluid();
      if (fluid == null) {
	continue;
      }
      for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
	String name = fluid.getComponent(i).getComponentName();
	if (!mm.containsKey(name)) {
	  mm.put(name, fluid.getComponent(i).getMolarMass());
	}
      }
    }
    componentNames = mm.keySet().toArray(new String[0]);
    molarMass = new double[componentNames.length];
    for (int k = 0; k < componentNames.length; k++) {
      molarMass[k] = mm.get(componentNames[k]);
    }
  }

  /**
   * Validates that the allocator has been fully configured.
   *
   * @throws IllegalStateException if the base case, sources or custody outlets are missing
   */
  private void requireConfigured() {
    if (baseCase == null) {
      throw new IllegalStateException("setBaseCase(process) must be called before allocating");
    }
    if (sources.isEmpty()) {
      throw new IllegalStateException("at least one source must be added before allocating");
    }
    if (custodyOutlets.isEmpty()) {
      throw new IllegalStateException("at least one custody outlet must be added before allocating");
    }
  }

  /**
   * Gets the configured sources.
   *
   * @return the sources in declared order
   */
  public List<AllocationSource> getSources() {
    return sources;
  }

  /**
   * Gets the configured custody outlets.
   *
   * @return the custody outlets in declared order
   */
  public List<CustodyOutlet> getCustodyOutlets() {
    return custodyOutlets;
  }

  /**
   * Gets the base-case process.
   *
   * @return the base case, or {@code null} if not set
   */
  public ProcessSystem getBaseCase() {
    return baseCase;
  }
}
