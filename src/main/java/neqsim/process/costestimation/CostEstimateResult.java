package neqsim.process.costestimation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Detailed, report-ready cost estimate result for one unit or a whole process.
 *
 * <p>
 * The result carries a common capital-cost stack, estimate basis, material take-off quantities, and quality flags. It
 * is intentionally lightweight so existing equipment-specific estimators can populate it without changing their sizing
 * correlations.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class CostEstimateResult implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private String estimateId = "";
  private String equipmentName = "";
  private String equipmentType = "";
  private CostEstimateBasis basis = new CostEstimateBasis();
  private Map<String, Double> capitalCosts = new LinkedHashMap<String, Double>();
  private Map<String, Double> capitalCostBreakdown = new LinkedHashMap<String, Double>();
  private Map<String, Double> capitalCostSummary = new LinkedHashMap<String, Double>();
  private Map<String, Double> projectCosts = new LinkedHashMap<String, Double>();
  private Map<String, Double> projectCostSummary = new LinkedHashMap<String, Double>();
  private Map<String, Double> quantityBasis = new LinkedHashMap<String, Double>();
  private Map<String, String> quantityUnits = new LinkedHashMap<String, String>();
  private Map<String, Double> weightBasis = new LinkedHashMap<String, Double>();
  private List<MaterialTakeOffItem> materialTakeOff = new ArrayList<MaterialTakeOffItem>();
  private List<String> qualityFlags = new ArrayList<String>();

  /**
   * Sets identification fields for the result.
   *
   * @param estimateId estimate identifier
   * @param equipmentName equipment or process name
   * @param equipmentType equipment or process type
   * @return this result for chaining
   */
  public CostEstimateResult setIdentification(String estimateId, String equipmentName, String equipmentType) {
    this.estimateId = estimateId == null ? "" : estimateId;
    this.equipmentName = equipmentName == null ? "" : equipmentName;
    this.equipmentType = equipmentType == null ? "" : equipmentType;
    return this;
  }

  /**
   * Gets the estimate basis.
   *
   * @return estimate basis
   */
  public CostEstimateBasis getBasis() {
    return basis;
  }

  /**
   * Sets the estimate basis.
   *
   * @param basis estimate basis; {@code null} resets to the default Class 4 basis
   * @return this result for chaining
   */
  public CostEstimateResult setBasis(CostEstimateBasis basis) {
    this.basis = basis == null ? new CostEstimateBasis() : basis;
    return this;
  }

  /**
   * Adds or replaces a capital-cost line.
   *
   * @param name cost line name
   * @param valueUSD cost value in USD
   * @return this result for chaining
   */
  public CostEstimateResult addCapitalCost(String name, double valueUSD) {
    if (name != null && !name.trim().isEmpty()) {
      capitalCosts.put(name, valueUSD);
    }
    return this;
  }

  /**
   * Adds or replaces a supplementary capital-cost breakdown line.
   *
   * @param name breakdown line name
   * @param valueUSD cost value in USD
   * @return this result for chaining
   */
  public CostEstimateResult addCapitalCostBreakdown(String name, double valueUSD) {
    if (name != null && !name.trim().isEmpty()) {
      capitalCostBreakdown.put(name, valueUSD);
    }
    return this;
  }

  /**
   * Adds or replaces a capital-cost subtotal or total line.
   *
   * @param name summary line name
   * @param valueUSD cost value in USD
   * @return this result for chaining
   */
  public CostEstimateResult addCapitalCostSummary(String name, double valueUSD) {
    if (name != null && !name.trim().isEmpty()) {
      capitalCostSummary.put(name, valueUSD);
    }
    return this;
  }

  /**
   * Adds or replaces a project-cost line.
   *
   * @param name cost line name
   * @param valueUSD cost value in USD
   * @return this result for chaining
   */
  public CostEstimateResult addProjectCost(String name, double valueUSD) {
    if (name != null && !name.trim().isEmpty()) {
      projectCosts.put(name, valueUSD);
    }
    return this;
  }

  /**
   * Adds or replaces a project-cost subtotal or total line.
   *
   * @param name summary line name
   * @param valueUSD cost value in USD
   * @return this result for chaining
   */
  public CostEstimateResult addProjectCostSummary(String name, double valueUSD) {
    if (name != null && !name.trim().isEmpty()) {
      projectCostSummary.put(name, valueUSD);
    }
    return this;
  }

  /**
   * Adds or replaces a non-cost quantity basis line.
   *
   * @param name quantity line name
   * @param value quantity value
   * @param unit quantity unit
   * @return this result for chaining
   */
  public CostEstimateResult addQuantityBasis(String name, double value, String unit) {
    if (name != null && !name.trim().isEmpty()) {
      quantityBasis.put(name, value);
      quantityUnits.put(name, unit == null ? "" : unit);
    }
    return this;
  }

  /**
   * Adds or replaces a weight-basis line.
   *
   * @param name weight line name
   * @param weightKg weight in kg
   * @return this result for chaining
   */
  public CostEstimateResult addWeightBasis(String name, double weightKg) {
    if (name != null && !name.trim().isEmpty()) {
      weightBasis.put(name, weightKg);
    }
    return this;
  }

  /**
   * Adds a material take-off quantity.
   *
   * @param item item label
   * @param material material label
   * @param quantity quantity value
   * @param unit quantity unit
   * @param costUSD estimated cost in USD
   * @return this result for chaining
   */
  public CostEstimateResult addMaterialQuantity(String item, String material, double quantity, String unit,
      double costUSD) {
    double weightKg = "kg".equalsIgnoreCase(unit) ? quantity : Double.NaN;
    materialTakeOff
        .add(new MaterialTakeOffItem(item, "bulk", material, quantity, unit, weightKg, costUSD, "mechanical-design"));
    return this;
  }

  /**
   * Adds a material take-off item.
   *
   * @param item material take-off item; {@code null} items are ignored
   * @return this result for chaining
   */
  public CostEstimateResult addMaterialTakeOff(MaterialTakeOffItem item) {
    if (item != null) {
      materialTakeOff.add(item);
    }
    return this;
  }

  /**
   * Adds a quality or scope flag.
   *
   * @param flag quality flag text
   * @return this result for chaining
   */
  public CostEstimateResult addQualityFlag(String flag) {
    if (flag != null && !flag.trim().isEmpty()) {
      qualityFlags.add(flag);
    }
    return this;
  }

  /**
   * Gets the capital-cost map.
   *
   * @return copy of capital costs
   */
  public Map<String, Double> getCapitalCosts() {
    return new LinkedHashMap<String, Double>(capitalCosts);
  }

  /**
   * Gets the supplementary capital-cost breakdown map.
   *
   * @return copy of capital-cost breakdown lines
   */
  public Map<String, Double> getCapitalCostBreakdown() {
    return new LinkedHashMap<String, Double>(capitalCostBreakdown);
  }

  /**
   * Gets the capital-cost summary map.
   *
   * @return copy of capital-cost subtotal and total lines
   */
  public Map<String, Double> getCapitalCostSummary() {
    return new LinkedHashMap<String, Double>(capitalCostSummary);
  }

  /**
   * Gets the project-cost map.
   *
   * @return copy of project costs
   */
  public Map<String, Double> getProjectCosts() {
    return new LinkedHashMap<String, Double>(projectCosts);
  }

  /**
   * Gets the project-cost summary map.
   *
   * @return copy of project-cost subtotal and total lines
   */
  public Map<String, Double> getProjectCostSummary() {
    return new LinkedHashMap<String, Double>(projectCostSummary);
  }

  /**
   * Gets the non-cost quantity basis map.
   *
   * @return copy of quantity values
   */
  public Map<String, Double> getQuantityBasis() {
    return new LinkedHashMap<String, Double>(quantityBasis);
  }

  /**
   * Gets the non-cost quantity unit map.
   *
   * @return copy of quantity units keyed by quantity name
   */
  public Map<String, String> getQuantityUnits() {
    return new LinkedHashMap<String, String>(quantityUnits);
  }

  /**
   * Gets the weight-basis map.
   *
   * @return copy of weight-basis values in kg
   */
  public Map<String, Double> getWeightBasis() {
    return new LinkedHashMap<String, Double>(weightBasis);
  }

  /**
   * Gets the material take-off list.
   *
   * @return copy of material take-off lines
   */
  public List<MaterialTakeOffItem> getMaterialTakeOff() {
    return new ArrayList<MaterialTakeOffItem>(materialTakeOff);
  }

  /**
   * Converts the result to a JSON-friendly map.
   *
   * @return map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("estimateId", estimateId);
    result.put("equipmentName", equipmentName);
    result.put("equipmentType", equipmentType);
    result.put("estimateBasis", basis.toMap());
    result.put("capitalCosts_USD", new LinkedHashMap<String, Double>(capitalCosts));
    result.put("capitalCostBreakdown_USD", new LinkedHashMap<String, Double>(capitalCostBreakdown));
    result.put("capitalCostSummary_USD", new LinkedHashMap<String, Double>(capitalCostSummary));
    result.put("projectCosts_USD", new LinkedHashMap<String, Double>(projectCosts));
    result.put("projectCostSummary_USD", new LinkedHashMap<String, Double>(projectCostSummary));
    result.put("quantityBasis", buildQuantityBasisMap());
    result.put("weightBasis_kg", new LinkedHashMap<String, Double>(weightBasis));

    List<Map<String, Object>> materialMaps = new ArrayList<Map<String, Object>>();
    for (MaterialTakeOffItem quantity : materialTakeOff) {
      materialMaps.add(quantity.toMap());
    }
    result.put("materialTakeOff", materialMaps);
    result.put("qualityFlags", new ArrayList<String>(qualityFlags));
    return result;
  }

  /**
   * Builds the JSON-friendly quantity basis map with values and units.
   *
   * @return quantity basis map keyed by quantity name
   */
  private Map<String, Map<String, Object>> buildQuantityBasisMap() {
    Map<String, Map<String, Object>> result = new LinkedHashMap<String, Map<String, Object>>();
    for (Map.Entry<String, Double> entry : quantityBasis.entrySet()) {
      Map<String, Object> quantity = new LinkedHashMap<String, Object>();
      quantity.put("value", entry.getValue());
      quantity.put("unit", quantityUnits.get(entry.getKey()));
      result.put(entry.getKey(), quantity);
    }
    return result;
  }

  /**
   * Converts the result to JSON.
   *
   * @return pretty-printed JSON string
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(toMap());
  }
}
