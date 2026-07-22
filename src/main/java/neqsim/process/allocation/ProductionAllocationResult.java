package neqsim.process.allocation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.allocation.LinearAllocationSolver.ComponentDiagnostics;

/**
 * Result of a linear production-allocation run: how much of each source ends up in each custody outlet, resolved per
 * component and aggregated to product groups and mass/mole totals.
 *
 * <p>
 * Allocations are stored on a molar basis (mole/sec) and converted on demand to mass-based units. The class supports
 * per-stream, per-component and per-product-type queries, optional mass-closure renormalisation to measured (base-case)
 * custody totals, and schema-versioned JSON export.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ProductionAllocationResult implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** JSON schema version of the allocation result. */
  public static final String SCHEMA_VERSION = "1.0";

  /** Source names, length {@code S}. */
  private final String[] sourceNames;

  /** Custody outlet names, length {@code C}. */
  private final String[] custodyNames;

  /** Custody product categories, length {@code C}. */
  private final ProductType[] custodyTypes;

  /** Master component slate, length {@code K}. */
  private final String[] componentNames;

  /** Molar mass per component (kg/mol), length {@code K}. */
  private final double[] molarMass;

  /** Allocated molar flow {@code [source][custody][component]} in mole/sec. */
  private final double[][][] allocMole;

  /** Per-component solver diagnostics. */
  private final List<ComponentDiagnostics> diagnostics;

  /** Index lookup from source name to index. */
  private final Map<String, Integer> sourceIndex;

  /** Index lookup from custody name to index. */
  private final Map<String, Integer> custodyIndex;

  /**
   * Creates an allocation result.
   *
   * @param sourceNames source names; must be non-null
   * @param custodyNames custody outlet names; must be non-null
   * @param custodyTypes custody product categories; must be non-null and same length as {@code custodyNames}
   * @param componentNames master component slate; must be non-null
   * @param molarMass molar mass per component (kg/mol); must be non-null and same length as {@code componentNames}
   * @param allocMole allocated molar flow {@code [source][custody][component]} in mole/sec; must be non-null
   * @param diagnostics per-component solver diagnostics; must be non-null
   */
  public ProductionAllocationResult(String[] sourceNames, String[] custodyNames, ProductType[] custodyTypes,
      String[] componentNames, double[] molarMass, double[][][] allocMole, List<ComponentDiagnostics> diagnostics) {
    this.sourceNames = sourceNames.clone();
    this.custodyNames = custodyNames.clone();
    this.custodyTypes = custodyTypes.clone();
    this.componentNames = componentNames.clone();
    this.molarMass = molarMass.clone();
    this.allocMole = allocMole;
    this.diagnostics = diagnostics;
    this.sourceIndex = new LinkedHashMap<>();
    for (int j = 0; j < sourceNames.length; j++) {
      sourceIndex.put(sourceNames[j], j);
    }
    this.custodyIndex = new LinkedHashMap<>();
    for (int c = 0; c < custodyNames.length; c++) {
      custodyIndex.put(custodyNames[c], c);
    }
  }

  /**
   * Converts a molar flow (mole/sec) of a single component to the requested unit.
   *
   * @param mole molar flow in mole/sec
   * @param componentIndex component index (for molar mass)
   * @param unit target unit (see {@link #getAllocatedFlow(String, String, String)})
   * @return the converted flow
   */
  private double convert(double mole, int componentIndex, String unit) {
    switch (unit) {
    case "mole/sec":
    case "mol/sec":
      return mole;
    case "mole/hr":
    case "mol/hr":
      return mole * 3600.0;
    case "kmole/hr":
    case "kmol/hr":
      return mole * 3.6;
    case "kg/sec":
      return mole * molarMass[componentIndex];
    case "kg/hr":
      return mole * molarMass[componentIndex] * 3600.0;
    case "kg/day":
      return mole * molarMass[componentIndex] * 86400.0;
    case "tonnes/year":
      return mole * molarMass[componentIndex] * 3600.0 * 8760.0 / 1000.0;
    default:
      throw new IllegalArgumentException("Unsupported allocation unit: " + unit);
    }
  }

  /**
   * Gets the allocated flow of a source to a custody outlet, summed over all components.
   *
   * @param source the source name; must exist
   * @param custody the custody outlet name; must exist
   * @param unit the target unit: {@code mole/sec}, {@code mol/sec}, {@code mole/hr}, {@code mol/hr}, {@code kmole/hr},
   * {@code kmol/hr}, {@code kg/sec}, {@code kg/hr}, {@code kg/day} or {@code tonnes/year}
   * @return the allocated flow in the requested unit
   */
  public double getAllocatedFlow(String source, String custody, String unit) {
    int j = requireSource(source);
    int c = requireCustody(custody);
    double total = 0.0;
    for (int k = 0; k < componentNames.length; k++) {
      total += convert(allocMole[j][c][k], k, unit);
    }
    return total;
  }

  /**
   * Gets the allocated flow of a single component from a source to a custody outlet.
   *
   * @param source the source name; must exist
   * @param custody the custody outlet name; must exist
   * @param component the component name; must be on the master slate
   * @param unit the target unit
   * @return the allocated component flow in the requested unit
   */
  public double getAllocatedComponentFlow(String source, String custody, String component, String unit) {
    int j = requireSource(source);
    int c = requireCustody(custody);
    int k = requireComponent(component);
    return convert(allocMole[j][c][k], k, unit);
  }

  /**
   * Gets the allocated flow of a source to all custody outlets of a given product category.
   *
   * @param source the source name; must exist
   * @param productType the product category to sum over; must be non-null
   * @param unit the target unit
   * @return the aggregated product flow in the requested unit
   */
  public double getProductAllocation(String source, ProductType productType, String unit) {
    int j = requireSource(source);
    double total = 0.0;
    for (int c = 0; c < custodyNames.length; c++) {
      if (custodyTypes[c] == productType) {
        for (int k = 0; k < componentNames.length; k++) {
          total += convert(allocMole[j][c][k], k, unit);
        }
      }
    }
    return total;
  }

  /**
   * Gets the total allocated flow of a source across all custody outlets.
   *
   * @param source the source name; must exist
   * @param unit the target unit
   * @return the total allocated flow in the requested unit
   */
  public double getSourceTotal(String source, String unit) {
    int j = requireSource(source);
    double total = 0.0;
    for (int c = 0; c < custodyNames.length; c++) {
      for (int k = 0; k < componentNames.length; k++) {
        total += convert(allocMole[j][c][k], k, unit);
      }
    }
    return total;
  }

  /**
   * Gets the total flow received by a custody outlet from all sources.
   *
   * @param custody the custody outlet name; must exist
   * @param unit the target unit
   * @return the total custody flow in the requested unit
   */
  public double getCustodyTotal(String custody, String unit) {
    int c = requireCustody(custody);
    double total = 0.0;
    for (int j = 0; j < sourceNames.length; j++) {
      for (int k = 0; k < componentNames.length; k++) {
        total += convert(allocMole[j][c][k], k, unit);
      }
    }
    return total;
  }

  /**
   * Gets the field-wide total flow delivered to all custody outlets of a given product category, summed over every
   * source and component.
   *
   * @param productType the product category to sum over; must be non-null
   * @param unit the target unit
   * @return the aggregated field product flow in the requested unit
   */
  public double getProductTotal(ProductType productType, String unit) {
    double total = 0.0;
    for (int c = 0; c < custodyNames.length; c++) {
      if (custodyTypes[c] == productType) {
        for (int j = 0; j < sourceNames.length; j++) {
          for (int k = 0; k < componentNames.length; k++) {
            total += convert(allocMole[j][c][k], k, unit);
          }
        }
      }
    }
    return total;
  }

  /**
   * Gets the allocation factor of a source to a custody outlet: the fraction of that source's total allocated
   * production that is delivered to the outlet. The allocation factors of a source over all custody outlets sum to one.
   *
   * @param source the source name; must exist
   * @param custody the custody outlet name; must exist
   * @return the dimensionless allocation factor in {@code [0, 1]}, or {@code 0.0} if the source allocates nothing
   */
  public double getAllocationFactor(String source, String custody) {
    double sourceTotal = getSourceTotal(source, "mole/sec");
    if (sourceTotal <= 0.0) {
      return 0.0;
    }
    return getAllocatedFlow(source, custody, "mole/sec") / sourceTotal;
  }

  /**
   * Gets the recovery factor of a component at a custody outlet: the fraction of that component's total allocated flow
   * (summed over all sources and all custody outlets) that is delivered to the given outlet.
   *
   * <p>
   * For a gas/oil split this reproduces the classic component oil/gas recovery factor (ORF): the share of the component
   * recovered in a particular product stream. The recovery factors of a component over all custody outlets sum to one.
   * </p>
   *
   * @param custody the custody outlet name; must exist
   * @param component the component name; must be on the master slate
   * @return the dimensionless recovery factor in {@code [0, 1]}, or {@code 0.0} if the component is absent everywhere
   */
  public double getComponentRecoveryFactor(String custody, String component) {
    int c = requireCustody(custody);
    int k = requireComponent(component);
    double here = 0.0;
    double everywhere = 0.0;
    for (int j = 0; j < sourceNames.length; j++) {
      here += allocMole[j][c][k];
      for (int cc = 0; cc < custodyNames.length; cc++) {
        everywhere += allocMole[j][cc][k];
      }
    }
    if (everywhere <= 0.0) {
      return 0.0;
    }
    return here / everywhere;
  }

  /**
   * Renormalises each custody outlet so that the sum over sources of every component exactly matches a measured (or
   * base-case) target, enforcing mass closure. Components whose modelled sum is zero are left untouched.
   *
   * @param custodyComponentTargetMole target molar flow {@code [custody][component]} in mole/sec; must be non-null and
   * sized {@code C x K}
   */
  public void renormalizeMassClosure(double[][] custodyComponentTargetMole) {
    for (int c = 0; c < custodyNames.length; c++) {
      for (int k = 0; k < componentNames.length; k++) {
        double modelled = 0.0;
        for (int j = 0; j < sourceNames.length; j++) {
          modelled += allocMole[j][c][k];
        }
        if (modelled > 0.0) {
          double scale = custodyComponentTargetMole[c][k] / modelled;
          for (int j = 0; j < sourceNames.length; j++) {
            allocMole[j][c][k] *= scale;
          }
        }
      }
    }
  }

  /**
   * Resolves a source name to its index, throwing if unknown.
   *
   * @param source the source name
   * @return the source index
   */
  private int requireSource(String source) {
    Integer j = sourceIndex.get(source);
    if (j == null) {
      throw new IllegalArgumentException("Unknown source: " + source);
    }
    return j;
  }

  /**
   * Resolves a custody name to its index, throwing if unknown.
   *
   * @param custody the custody outlet name
   * @return the custody index
   */
  private int requireCustody(String custody) {
    Integer c = custodyIndex.get(custody);
    if (c == null) {
      throw new IllegalArgumentException("Unknown custody outlet: " + custody);
    }
    return c;
  }

  /**
   * Resolves a component name to its index, throwing if unknown.
   *
   * @param component the component name
   * @return the component index
   */
  private int requireComponent(String component) {
    for (int k = 0; k < componentNames.length; k++) {
      if (componentNames[k].equals(component)) {
        return k;
      }
    }
    throw new IllegalArgumentException("Unknown component: " + component);
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
   * Gets the per-component solver diagnostics.
   *
   * @return the diagnostics list
   */
  public List<ComponentDiagnostics> getDiagnostics() {
    return diagnostics;
  }

  /**
   * Gets the worst (largest) relative residual across all component solves.
   *
   * @return the maximum residual, or {@code 0.0} if there are no diagnostics
   */
  public double getMaxResidual() {
    double max = 0.0;
    for (ComponentDiagnostics d : diagnostics) {
      max = Math.max(max, d.getResidual());
    }
    return max;
  }

  /**
   * Builds a schema-versioned JSON representation of the allocation result.
   *
   * <p>
   * The JSON contains the source totals, custody totals, the full source &#8594; custody allocation matrix (mole/sec
   * and kg/hr), product-group summaries per source, and solver diagnostics.
   * </p>
   *
   * @return a pretty-printed JSON string
   */
  public String toJson() {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("schemaVersion", SCHEMA_VERSION);
    root.put("sources", sourceNames);
    root.put("custodyOutlets", custodyNames);

    List<Map<String, Object>> allocations = new ArrayList<>();
    for (int j = 0; j < sourceNames.length; j++) {
      Map<String, Object> sourceEntry = new LinkedHashMap<>();
      sourceEntry.put("source", sourceNames[j]);
      sourceEntry.put("totalMolePerSec", getSourceTotal(sourceNames[j], "mole/sec"));
      sourceEntry.put("totalKgPerHr", getSourceTotal(sourceNames[j], "kg/hr"));

      Map<String, Object> toCustody = new LinkedHashMap<>();
      for (int c = 0; c < custodyNames.length; c++) {
        Map<String, Object> custodyEntry = new LinkedHashMap<>();
        custodyEntry.put("molePerSec", getAllocatedFlow(sourceNames[j], custodyNames[c], "mole/sec"));
        custodyEntry.put("kgPerHr", getAllocatedFlow(sourceNames[j], custodyNames[c], "kg/hr"));
        custodyEntry.put("allocationFactor", getAllocationFactor(sourceNames[j], custodyNames[c]));
        custodyEntry.put("productType", custodyTypes[c].name());
        toCustody.put(custodyNames[c], custodyEntry);
      }
      sourceEntry.put("custody", toCustody);

      Map<String, Object> products = new LinkedHashMap<>();
      for (ProductType type : ProductType.values()) {
        double kg = getProductAllocation(sourceNames[j], type, "kg/hr");
        if (kg != 0.0) {
          products.put(type.name(), kg);
        }
      }
      sourceEntry.put("productKgPerHr", products);
      allocations.add(sourceEntry);
    }
    root.put("allocations", allocations);

    Map<String, Object> custodyTotals = new LinkedHashMap<>();
    for (int c = 0; c < custodyNames.length; c++) {
      Map<String, Object> totals = new LinkedHashMap<>();
      totals.put("molePerSec", getCustodyTotal(custodyNames[c], "mole/sec"));
      totals.put("kgPerHr", getCustodyTotal(custodyNames[c], "kg/hr"));
      totals.put("productType", custodyTypes[c].name());
      custodyTotals.put(custodyNames[c], totals);
    }
    root.put("custodyTotals", custodyTotals);

    List<Map<String, Object>> diag = new ArrayList<>();
    for (ComponentDiagnostics d : diagnostics) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("component", componentNames[d.getComponentIndex()]);
      entry.put("method", d.getMethod());
      entry.put("residual", d.getResidual());
      entry.put("iterations", d.getIterations());
      diag.add(entry);
    }
    root.put("solverDiagnostics", diag);
    root.put("maxResidual", getMaxResidual());

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(root);
  }
}
