package neqsim.process.allocation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ejml.simple.SimpleMatrix;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Closed-form first-order uncertainty propagation for the linear recovery-factor allocation network.
 *
 * <p>
 * Because the per-source allocation {@code v_k = (I - A_k)^-1 b_k} is linear in the metered source injections,
 * metering-noise covariance propagates analytically:
 * </p>
 *
 * <p>
 * {@code Sigma_v_k = J_k * Sigma_b_k * J_k^T} with {@code J_k = (I - A_k)^-1}.
 * </p>
 *
 * <p>
 * This class implements the common case of <b>independent per-source metering</b>, i.e. a diagonal input covariance
 * {@code Sigma_b_k}. For each component {@code k}, source {@code s} entering at node {@code e_s} with
 * measured-injection standard deviation {@code sigma_{s,k}}, and custody outlet {@code j} produced by node {@code w}
 * with frozen split factor {@code f_w(j,k)}, the variance of the allocated component flow is
 * </p>
 *
 * <p>
 * {@code Var(alloc(s, j, k)) = f_w(j, k)^2 * (J_k[w, e_s])^2 * sigma_{s,k}^2}.
 * </p>
 *
 * <p>
 * Per-source per-outlet variances on the totalised molar and mass flows follow by summing independent component
 * contributions. Standard deviations and one-sigma molar/mass intervals are exposed by {@link UncertaintyResult}.
 * </p>
 *
 * <p>
 * Algorithmic cost is the same {@code O(K N^3) + O(K N^2 S)} as the deterministic solve in
 * {@link LinearAllocationSolver}: one LU per component, one back-substitution per source. No flash or equation-of-state
 * evaluation is performed.
 * </p>
 *
 * <p>
 * Correlated metering, frozen-split-factor uncertainty, and sensitivity-corrected drift terms are left for future work;
 * see the accompanying paper for the derivation.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class AllocationUncertaintyEstimator implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger instance. */
  private static final Logger logger = LogManager.getLogger(AllocationUncertaintyEstimator.class);

  /** Magnitude below which a tiny negative variance from round-off is clipped to zero. */
  private double negativeClipTolerance = 1.0e-12;

  /**
   * Result of an uncertainty-propagation run: per-source, per-custody, per-component variances of the allocated
   * component flow plus aggregated totals.
   */
  public static class UncertaintyResult implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    /** Schema version identifier for {@link #toJson()}. */
    public static final String SCHEMA_VERSION = "1.0";

    /** Source names, length {@code S}. */
    private final String[] sourceNames;

    /** Custody outlet names, length {@code C}. */
    private final String[] custodyNames;

    /** Custody product types, length {@code C}. */
    private final ProductType[] custodyTypes;

    /** Component names, length {@code K}. */
    private final String[] componentNames;

    /** Component molar masses (kg/kmol), length {@code K}. */
    private final double[] molarMass;

    /** Per-source per-custody per-component allocated-flow variance (mole/sec)^2. */
    private final double[][][] allocMoleVariance;

    /** Source name to index lookup. */
    private final Map<String, Integer> sourceIndex;

    /** Custody name to index lookup. */
    private final Map<String, Integer> custodyIndex;

    /**
     * Creates an uncertainty result.
     *
     * @param sourceNames source names; must be non-null and length {@code S}
     * @param custodyNames custody outlet names; must be non-null and length {@code C}
     * @param custodyTypes product types of each custody outlet; must be non-null and length {@code C}
     * @param componentNames component names; must be non-null and length {@code K}
     * @param molarMass component molar masses in kg/kmol; must be non-null and length {@code K}
     * @param allocMoleVariance per-source per-custody per-component allocated-flow variance in (mole/sec)^2; must be
     * non-null and shape {@code [S][C][K]}
     */
    public UncertaintyResult(String[] sourceNames, String[] custodyNames, ProductType[] custodyTypes,
	String[] componentNames, double[] molarMass, double[][][] allocMoleVariance) {
      this.sourceNames = sourceNames.clone();
      this.custodyNames = custodyNames.clone();
      this.custodyTypes = custodyTypes.clone();
      this.componentNames = componentNames.clone();
      this.molarMass = molarMass.clone();
      this.allocMoleVariance = allocMoleVariance;
      this.sourceIndex = new LinkedHashMap<>();
      for (int i = 0; i < sourceNames.length; i++) {
	sourceIndex.put(sourceNames[i], i);
      }
      this.custodyIndex = new LinkedHashMap<>();
      for (int i = 0; i < custodyNames.length; i++) {
	custodyIndex.put(custodyNames[i], i);
      }
    }

    /**
     * Gets the variance of the allocated total molar flow from a source to a custody outlet, summed over independent
     * component contributions.
     *
     * @param source the source name; must be a registered source
     * @param custody the custody outlet name; must be a registered outlet
     * @return the variance in (mole/sec)^2
     */
    public double getAllocatedFlowVariance(String source, String custody) {
      int s = lookup(sourceIndex, source, "source");
      int c = lookup(custodyIndex, custody, "custody");
      double sum = 0.0;
      for (int k = 0; k < componentNames.length; k++) {
	sum += allocMoleVariance[s][c][k];
      }
      return sum;
    }

    /**
     * Gets the one-sigma standard deviation of the allocated total molar flow from a source to a custody outlet.
     *
     * @param source the source name; must be a registered source
     * @param custody the custody outlet name; must be a registered outlet
     * @return the standard deviation in mole/sec
     */
    public double getAllocatedFlowStdDevMoles(String source, String custody) {
      return Math.sqrt(getAllocatedFlowVariance(source, custody));
    }

    /**
     * Gets the one-sigma standard deviation of the allocated total mass flow from a source to a custody outlet.
     *
     * @param source the source name; must be a registered source
     * @param custody the custody outlet name; must be a registered outlet
     * @return the standard deviation in kg/hr
     */
    public double getAllocatedFlowStdDevKgPerHr(String source, String custody) {
      int s = lookup(sourceIndex, source, "source");
      int c = lookup(custodyIndex, custody, "custody");
      double sum = 0.0;
      for (int k = 0; k < componentNames.length; k++) {
	double mass = molarMass[k] * 3.6;
	sum += allocMoleVariance[s][c][k] * mass * mass;
      }
      return Math.sqrt(sum);
    }

    /**
     * Gets the one-sigma standard deviation of the allocated component flow from a source to a custody outlet.
     *
     * @param source the source name; must be a registered source
     * @param custody the custody outlet name; must be a registered outlet
     * @param component the component name; must be on the master slate
     * @return the standard deviation in mole/sec
     */
    public double getAllocatedComponentFlowStdDevMoles(String source, String custody, String component) {
      int s = lookup(sourceIndex, source, "source");
      int c = lookup(custodyIndex, custody, "custody");
      int k = componentIndexOf(component);
      return Math.sqrt(allocMoleVariance[s][c][k]);
    }

    /**
     * Gets the one-sigma standard deviation of the total product allocation of a given type for a source, summed over
     * all custody outlets of that product type.
     *
     * @param source the source name; must be a registered source
     * @param productType the product type (e.g. GAS, OIL, WATER)
     * @param unit either {@code "mole/sec"} or {@code "kg/hr"}
     * @return the standard deviation in the requested unit
     */
    public double getProductAllocationStdDev(String source, ProductType productType, String unit) {
      int s = lookup(sourceIndex, source, "source");
      double sum = 0.0;
      for (int c = 0; c < custodyNames.length; c++) {
	if (custodyTypes[c] != productType) {
	  continue;
	}
	for (int k = 0; k < componentNames.length; k++) {
	  double weight = unitWeight(k, unit);
	  sum += allocMoleVariance[s][c][k] * weight * weight;
	}
      }
      return Math.sqrt(sum);
    }

    /**
     * Gets the source names.
     *
     * @return a copy of the source name array
     */
    public String[] getSourceNames() {
      return sourceNames.clone();
    }

    /**
     * Gets the custody outlet names.
     *
     * @return a copy of the custody name array
     */
    public String[] getCustodyNames() {
      return custodyNames.clone();
    }

    /**
     * Gets the master component slate.
     *
     * @return a copy of the component name array
     */
    public String[] getComponentNames() {
      return componentNames.clone();
    }

    /**
     * Returns a schema-versioned JSON serialisation of the standard deviations.
     *
     * <p>
     * The JSON contains source, custody, and component lists plus a flat per-source per-custody matrix of standard
     * deviations in mole/sec and kg/hr, and per-product totals.
     * </p>
     *
     * @return a pretty-printed JSON string
     */
    public String toJson() {
      Map<String, Object> root = new LinkedHashMap<>();
      root.put("schemaVersion", SCHEMA_VERSION);
      root.put("sources", sourceNames);
      root.put("custodyOutlets", custodyNames);
      root.put("components", componentNames);

      List<Map<String, Object>> allocStdDev = new ArrayList<>();
      for (int s = 0; s < sourceNames.length; s++) {
	for (int c = 0; c < custodyNames.length; c++) {
	  Map<String, Object> entry = new LinkedHashMap<>();
	  entry.put("source", sourceNames[s]);
	  entry.put("custody", custodyNames[c]);
	  entry.put("productType", custodyTypes[c].name());
	  entry.put("stdDev_moleps", getAllocatedFlowStdDevMoles(sourceNames[s], custodyNames[c]));
	  entry.put("stdDev_kgph", getAllocatedFlowStdDevKgPerHr(sourceNames[s], custodyNames[c]));
	  allocStdDev.add(entry);
	}
      }
      root.put("allocationStdDev", allocStdDev);
      return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    /**
     * Looks up the index of a name in a map and throws if missing.
     *
     * @param map the index map; must be non-null
     * @param name the name to find; must be non-null
     * @param label a label for the error message; must be non-null
     * @return the zero-based index
     */
    private static int lookup(Map<String, Integer> map, String name, String label) {
      Integer i = map.get(name);
      if (i == null) {
	throw new IllegalArgumentException("Unknown " + label + ": " + name);
      }
      return i;
    }

    /**
     * Finds the master-slate index of a component.
     *
     * @param component the component name; must be non-null
     * @return the zero-based component index
     */
    private int componentIndexOf(String component) {
      for (int k = 0; k < componentNames.length; k++) {
	if (componentNames[k].equals(component)) {
	  return k;
	}
      }
      throw new IllegalArgumentException("Unknown component: " + component);
    }

    /**
     * Returns the per-component weight to convert mole/sec into the requested unit.
     *
     * @param componentIndex zero-based component index
     * @param unit either {@code "mole/sec"} or {@code "kg/hr"}
     * @return the multiplicative weight
     */
    private double unitWeight(int componentIndex, String unit) {
      if ("mole/sec".equals(unit)) {
	return 1.0;
      }
      if ("kg/hr".equals(unit)) {
	return molarMass[componentIndex] * 3.6;
      }
      throw new IllegalArgumentException("Unsupported unit: " + unit + " (use mole/sec or kg/hr)");
    }
  }

  /**
   * Propagates independent metering noise on each source's per-component injection through the proxy network.
   * Convenience overload that pulls source list, custody outlets, and entry-node indices from the allocator.
   *
   * @param allocator the configured allocator; must have a base case set and at least one source
   * @param injectionVariance per-source per-component variance of the injection in (mole/sec)^2; must be non-null and
   * shape {@code [S][K]}
   * @return an {@link UncertaintyResult} with per-source per-custody per-component variances
   */
  public UncertaintyResult propagate(SourceAllocator allocator, double[][] injectionVariance) {
    if (allocator == null) {
      throw new IllegalArgumentException("allocator must be non-null");
    }
    if (allocator.getNetwork() == null) {
      throw new IllegalStateException("setBaseCase(process) must be called on the allocator");
    }
    List<AllocationSource> sources = collectSources(allocator);
    List<CustodyOutlet> custodyOutlets = collectCustody(allocator);
    AllocationNetwork network = allocator.getNetwork();
    int numSources = sources.size();
    int[] entryUnits = new int[numSources];
    String[] sourceNames = new String[numSources];
    for (int j = 0; j < numSources; j++) {
      entryUnits[j] = network.findEntryUnit(sources.get(j).getFeedStream());
      sourceNames[j] = sources.get(j).getName();
    }
    int numCustody = custodyOutlets.size();
    String[] custodyNames = new String[numCustody];
    ProductType[] custodyTypes = new ProductType[numCustody];
    for (int c = 0; c < numCustody; c++) {
      custodyNames[c] = custodyOutlets.get(c).getName();
      custodyTypes[c] = custodyOutlets.get(c).getProductType();
    }
    List<String> componentNames = allocator.getExtractor().getComponentNames();
    int numComp = componentNames.size();
    double[] molarMass = new double[numComp];
    for (int k = 0; k < numComp; k++) {
      molarMass[k] = allocator.getExtractor().getMolarMass(componentNames.get(k));
    }
    return propagate(network, entryUnits, custodyOutlets, sourceNames, custodyNames, custodyTypes,
	componentNames.toArray(new String[0]), molarMass, injectionVariance);
  }

  /**
   * Propagates independent metering noise through the proxy network with explicit topology and label arrays.
   * Lower-level entry point used by {@link #propagate(SourceAllocator, double[][])}.
   *
   * @param network the assembled proxy network; must be non-null
   * @param sourceEntryUnits zero-based node index where each source enters the network, length {@code S}; must be
   * non-null
   * @param custodyOutlets custody outlets in result order; must be non-null and length {@code C}
   * @param sourceNames source labels in result order; must be non-null and length {@code S}
   * @param custodyNames custody outlet labels in result order; must be non-null and length {@code C}
   * @param custodyTypes custody product types in result order; must be non-null and length {@code C}
   * @param componentNames component names on the master slate; must be non-null and length {@code K}
   * @param molarMass component molar masses in kg/kmol; must be non-null and length {@code K}
   * @param injectionVariance per-source per-component variance in (mole/sec)^2; must be non-null and shape
   * {@code [S][K]}
   * @return an {@link UncertaintyResult} with per-source per-custody per-component variances
   */
  public UncertaintyResult propagate(AllocationNetwork network, int[] sourceEntryUnits,
      List<CustodyOutlet> custodyOutlets, String[] sourceNames, String[] custodyNames, ProductType[] custodyTypes,
      String[] componentNames, double[] molarMass, double[][] injectionVariance) {
    int n = network.getNodeCount();
    int numComp = network.getComponentCount();
    int numSources = sourceEntryUnits.length;
    int numCustody = custodyOutlets.size();

    if (componentNames.length != numComp) {
      throw new IllegalArgumentException(
	  "componentNames length (" + componentNames.length + ") must equal network component count (" + numComp + ")");
    }
    if (injectionVariance.length != numSources) {
      throw new IllegalArgumentException("injectionVariance must have one row per source (S=" + numSources + ")");
    }
    for (int j = 0; j < numSources; j++) {
      if (injectionVariance[j].length != numComp) {
	throw new IllegalArgumentException(
	    "injectionVariance[" + j + "] must have one entry per component (K=" + numComp + ")");
      }
    }

    int[][] producers = new int[numCustody][];
    for (int c = 0; c < numCustody; c++) {
      StreamInterface stream = custodyOutlets.get(c).getStream();
      producers[c] = network.findProducer(stream);
      if (producers[c] == null) {
	logger.warn("Custody outlet '{}' is not produced by any node; its variance will be zero.",
	    custodyOutlets.get(c).getName());
      }
    }

    double[][][] allocMoleVariance = new double[numSources][numCustody][numComp];

    for (int k = 0; k < numComp; k++) {
      // Skip components with no input variance from any source.
      boolean anyVariance = false;
      for (int j = 0; j < numSources; j++) {
	if (injectionVariance[j][k] > 0.0) {
	  anyVariance = true;
	  break;
	}
      }
      if (!anyVariance) {
	continue;
      }

      double[][] a = network.buildRoutingMatrix(k);
      SimpleMatrix mMatrix = SimpleMatrix.identity(n).minus(toSimple(a));

      // Build a unit-injection RHS: column j has 1 at the entry node of source j.
      SimpleMatrix e = new SimpleMatrix(n, numSources);
      boolean anyEntry = false;
      for (int j = 0; j < numSources; j++) {
	int eu = sourceEntryUnits[j];
	if (eu >= 0 && injectionVariance[j][k] > 0.0) {
	  e.set(eu, j, 1.0);
	  anyEntry = true;
	}
      }
      if (!anyEntry) {
	continue;
      }

      SimpleMatrix sens;
      try {
	sens = mMatrix.solve(e);
      } catch (RuntimeException ex) {
	logger.warn("Sensitivity solve failed for component {} ({}); component variances set to zero.", k,
	    ex.getMessage());
	continue;
      }
      if (!isFinite(sens)) {
	logger.warn("Sensitivity solve returned non-finite entries for component {}; variances set to zero.", k);
	continue;
      }

      for (int c = 0; c < numCustody; c++) {
	if (producers[c] == null) {
	  continue;
	}
	int w = producers[c][0];
	double f = network.getCustodyFactor(producers[c], k);
	if (f == 0.0) {
	  continue;
	}
	double f2 = f * f;
	for (int j = 0; j < numSources; j++) {
	  double sigma2 = injectionVariance[j][k];
	  if (sigma2 <= 0.0) {
	    continue;
	  }
	  double jVal = sens.get(w, j);
	  double var = f2 * jVal * jVal * sigma2;
	  if (var < 0.0 && var > -negativeClipTolerance) {
	    var = 0.0;
	  }
	  allocMoleVariance[j][c][k] = var;
	}
      }
    }

    return new UncertaintyResult(sourceNames, custodyNames, custodyTypes, componentNames, molarMass, allocMoleVariance);
  }

  /**
   * Sets the magnitude below which a tiny negative variance from round-off is clipped to zero.
   *
   * @param negativeClipTolerance the clip tolerance; must be non-negative
   */
  public void setNegativeClipTolerance(double negativeClipTolerance) {
    this.negativeClipTolerance = negativeClipTolerance;
  }

  /**
   * Collects the configured (or auto-detected) sources from an allocator. Mirrors the same fallback rule used by
   * {@link SourceAllocator#allocate()}: if no sources are registered, auto-detect them from external feed streams.
   *
   * @param allocator the allocator; must be non-null with a base case set
   * @return the source list in result order
   */
  private static List<AllocationSource> collectSources(SourceAllocator allocator) {
    if (!allocator.getSources().isEmpty()) {
      return new ArrayList<>(allocator.getSources());
    }
    List<AllocationSource> list = new ArrayList<>();
    List<StreamInterface> external = allocator.getNetwork().findSourceStreams();
    int i = 1;
    for (StreamInterface feed : external) {
      String name = feed.getName();
      if (name == null || name.trim().isEmpty()) {
	name = "Source-" + i;
      }
      list.add(new AllocationSource(name, feed));
      i++;
    }
    return list;
  }

  /**
   * Collects the configured (or auto-detected) custody outlets from an allocator. Mirrors the same fallback rule used
   * by {@link SourceAllocator#allocate()}: if no outlets are registered, auto-detect them from terminal product
   * streams.
   *
   * @param allocator the allocator; must be non-null with a base case set
   * @return the custody outlet list in result order
   */
  private static List<CustodyOutlet> collectCustody(SourceAllocator allocator) {
    if (!allocator.getCustodyOutlets().isEmpty()) {
      return new ArrayList<>(allocator.getCustodyOutlets());
    }
    List<CustodyOutlet> list = new ArrayList<>();
    List<StreamInterface> terminal = allocator.getNetwork().findCustodyStreams();
    int i = 1;
    for (StreamInterface product : terminal) {
      String name = product.getName();
      if (name == null || name.trim().isEmpty()) {
	name = "Custody-" + i;
      }
      list.add(new CustodyOutlet(name, product, SourceAllocator.inferProductType(product)));
      i++;
    }
    return list;
  }

  /**
   * Converts a primitive matrix to an EJML {@link SimpleMatrix}.
   *
   * @param a the matrix; must be non-null and rectangular
   * @return the equivalent {@code SimpleMatrix}
   */
  private static SimpleMatrix toSimple(double[][] a) {
    int rows = a.length;
    int cols = rows == 0 ? 0 : a[0].length;
    SimpleMatrix m = new SimpleMatrix(rows, cols);
    for (int i = 0; i < rows; i++) {
      for (int j = 0; j < cols; j++) {
	m.set(i, j, a[i][j]);
      }
    }
    return m;
  }

  /**
   * Checks whether all entries of a matrix are finite.
   *
   * @param m the matrix; must be non-null
   * @return {@code true} if every entry is finite
   */
  private static boolean isFinite(SimpleMatrix m) {
    for (int i = 0; i < m.numRows(); i++) {
      for (int j = 0; j < m.numCols(); j++) {
	if (!Double.isFinite(m.get(i, j))) {
	  return false;
	}
      }
    }
    return true;
  }
}
