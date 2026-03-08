package neqsim.process.electricaldesign.loadanalysis;

import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Electrical load list aggregation for a process system.
 *
 * <p>
 * Collects all electrical loads from equipment in a process system and provides summary
 * calculations: total connected load, maximum demand, power generation sizing, and transformer
 * sizing. Follows typical electrical load list format per IEC 61936.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ElectricalLoadList implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private String projectName = "";
  private List<LoadItem> loadItems = new ArrayList<LoadItem>();

  // === Summary values ===
  private double totalConnectedLoadKW;
  private double totalConnectedLoadKVA;
  private double maximumDemandKW;
  private double maximumDemandKVA;
  private double overallPowerFactor;
  private double totalReactivePowerKVAR;

  // === Transformer sizing ===
  private double requiredTransformerKVA;
  private double requiredGeneratorKVA;
  private double designMargin = 1.15;

  /**
   * Default constructor.
   */
  public ElectricalLoadList() {}

  /**
   * Constructor with project name.
   *
   * @param projectName the project name
   */
  public ElectricalLoadList(String projectName) {
    this.projectName = projectName;
  }

  /**
   * Add a load item to the list.
   *
   * @param item the load item
   */
  public void addLoadItem(LoadItem item) {
    loadItems.add(item);
  }

  /**
   * Clear the load list.
   */
  public void clear() {
    loadItems.clear();
    totalConnectedLoadKW = 0;
    totalConnectedLoadKVA = 0;
    maximumDemandKW = 0;
    maximumDemandKVA = 0;
  }

  /**
   * Calculate summary values from all load items.
   */
  public void calculateSummary() {
    totalConnectedLoadKW = 0;
    totalConnectedLoadKVA = 0;
    maximumDemandKW = 0;
    maximumDemandKVA = 0;
    totalReactivePowerKVAR = 0;

    for (LoadItem item : loadItems) {
      totalConnectedLoadKW += item.getRatedPowerKW();
      totalConnectedLoadKVA += item.getApparentPowerKVA();
      maximumDemandKW += item.getMaxDemandKW();
      maximumDemandKVA += item.getMaxDemandKVA();

      // Reactive power
      if (item.getPowerFactor() > 0 && item.getPowerFactor() < 1.0) {
        double phi = Math.acos(item.getPowerFactor());
        totalReactivePowerKVAR += item.getMaxDemandKW() * Math.tan(phi);
      }
    }

    // Overall power factor
    if (maximumDemandKVA > 0) {
      overallPowerFactor = maximumDemandKW / maximumDemandKVA;
    }

    // Transformer and generator sizing
    requiredTransformerKVA = maximumDemandKVA * designMargin;
    requiredGeneratorKVA = maximumDemandKVA * designMargin * 1.1; // Extra margin for generator
  }

  /**
   * Get the number of load items.
   *
   * @return number of items
   */
  public int getLoadCount() {
    return loadItems.size();
  }

  /**
   * Serialize to JSON.
   *
   * @return JSON string
   */
  public String toJson() {
    Map<String, Object> map = toMap();
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(map);
  }

  /**
   * Convert to a map.
   *
   * @return map of load list data
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("projectName", projectName);
    map.put("loadCount", loadItems.size());

    // Summary
    Map<String, Object> summary = new LinkedHashMap<String, Object>();
    summary.put("totalConnectedLoadKW", totalConnectedLoadKW);
    summary.put("totalConnectedLoadKVA", totalConnectedLoadKVA);
    summary.put("maximumDemandKW", maximumDemandKW);
    summary.put("maximumDemandKVA", maximumDemandKVA);
    summary.put("overallPowerFactor", overallPowerFactor);
    summary.put("totalReactivePowerKVAR", totalReactivePowerKVAR);
    summary.put("requiredTransformerKVA", requiredTransformerKVA);
    summary.put("requiredGeneratorKVA", requiredGeneratorKVA);
    map.put("summary", summary);

    // Load items
    List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
    for (LoadItem item : loadItems) {
      items.add(item.toMap());
    }
    map.put("loadItems", items);

    return map;
  }

  // === Getters and Setters ===

  /**
   * Get project name.
   *
   * @return project name
   */
  public String getProjectName() {
    return projectName;
  }

  /**
   * Set project name.
   *
   * @param projectName project name
   */
  public void setProjectName(String projectName) {
    this.projectName = projectName;
  }

  /**
   * Get total connected load in kW.
   *
   * @return total connected load in kW
   */
  public double getTotalConnectedLoadKW() {
    return totalConnectedLoadKW;
  }

  /**
   * Get total connected load in kVA.
   *
   * @return total connected load in kVA
   */
  public double getTotalConnectedLoadKVA() {
    return totalConnectedLoadKVA;
  }

  /**
   * Get maximum demand in kW.
   *
   * @return maximum demand in kW
   */
  public double getMaximumDemandKW() {
    return maximumDemandKW;
  }

  /**
   * Get maximum demand in kVA.
   *
   * @return maximum demand in kVA
   */
  public double getMaximumDemandKVA() {
    return maximumDemandKVA;
  }

  /**
   * Get overall power factor.
   *
   * @return overall power factor
   */
  public double getOverallPowerFactor() {
    return overallPowerFactor;
  }

  /**
   * Get total reactive power in kVAR.
   *
   * @return total reactive power in kVAR
   */
  public double getTotalReactivePowerKVAR() {
    return totalReactivePowerKVAR;
  }

  /**
   * Get required transformer sizing in kVA.
   *
   * @return required transformer in kVA
   */
  public double getRequiredTransformerKVA() {
    return requiredTransformerKVA;
  }

  /**
   * Get the load items list.
   *
   * @return list of load items
   */
  public List<LoadItem> getLoadItems() {
    return loadItems;
  }

  /**
   * Get design margin.
   *
   * @return design margin
   */
  public double getDesignMargin() {
    return designMargin;
  }

  /**
   * Set design margin.
   *
   * @param designMargin design margin (e.g. 1.15 for 15%)
   */
  public void setDesignMargin(double designMargin) {
    this.designMargin = designMargin;
  }
}
