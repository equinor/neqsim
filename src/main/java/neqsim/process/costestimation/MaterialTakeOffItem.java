package neqsim.process.costestimation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A material take-off line item used in detailed NeqSim cost estimates.
 *
 * <p>
 * Each item records the quantity basis, material, weight, cost, and source used for a cost estimate. It is
 * intentionally generic so vessel shells, piping, internals, structures, wells, and package equipment can share the
 * same report contract.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class MaterialTakeOffItem implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private final String item;
  private final String category;
  private final String material;
  private final double quantity;
  private final String unit;
  private final double weightKg;
  private final double costUSD;
  private final String source;

  /**
   * Creates a material take-off item.
   *
   * @param item item label
   * @param category category label such as vessel, piping, electrical, structural, or package
   * @param material material label
   * @param quantity quantity value
   * @param unit quantity unit
   * @param weightKg item weight in kg, or {@link Double#NaN} when not weight-based
   * @param costUSD estimated cost in USD
   * @param source source identifier for the quantity or cost
   */
  public MaterialTakeOffItem(String item, String category, String material, double quantity, String unit,
      double weightKg, double costUSD, String source) {
    this.item = item == null ? "" : item;
    this.category = category == null ? "" : category;
    this.material = material == null ? "" : material;
    this.quantity = quantity;
    this.unit = unit == null ? "" : unit;
    this.weightKg = weightKg;
    this.costUSD = costUSD;
    this.source = source == null ? "" : source;
  }

  /**
   * Gets the item label.
   *
   * @return item label
   */
  public String getItem() {
    return item;
  }

  /**
   * Gets the category label.
   *
   * @return category label
   */
  public String getCategory() {
    return category;
  }

  /**
   * Gets the material label.
   *
   * @return material label
   */
  public String getMaterial() {
    return material;
  }

  /**
   * Gets the quantity value.
   *
   * @return quantity value
   */
  public double getQuantity() {
    return quantity;
  }

  /**
   * Gets the quantity unit.
   *
   * @return quantity unit
   */
  public String getUnit() {
    return unit;
  }

  /**
   * Gets the item weight.
   *
   * @return item weight in kg, or {@link Double#NaN} when unavailable
   */
  public double getWeightKg() {
    return weightKg;
  }

  /**
   * Gets the estimated item cost.
   *
   * @return estimated item cost in USD
   */
  public double getCostUSD() {
    return costUSD;
  }

  /**
   * Gets the source identifier.
   *
   * @return source identifier
   */
  public String getSource() {
    return source;
  }

  /**
   * Converts the item to a JSON-friendly map.
   *
   * @return map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("item", item);
    result.put("category", category);
    result.put("material", material);
    result.put("quantity", quantity);
    result.put("unit", unit);
    result.put("weight_kg", weightKg);
    result.put("cost_USD", costUSD);
    result.put("source", source);
    return result;
  }
}