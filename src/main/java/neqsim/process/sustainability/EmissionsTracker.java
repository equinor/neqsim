package neqsim.process.sustainability;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.flare.Flare;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.reactor.FurnaceBurner;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Tracks CO2 equivalent emissions and energy consumption for a ProcessSystem.
 *
 * <p>
 * This class provides comprehensive sustainability metrics for process systems:
 * <ul>
 * <li><b>CO2e Emissions:</b> Per equipment and system-wide accounting</li>
 * <li><b>Energy Consumption:</b> Power breakdown by equipment type</li>
 * <li><b>Flaring Emissions:</b> Direct and cumulative CO2 from flaring</li>
 * <li><b>Regulatory Reporting:</b> Export formats for EU ETS, EPA, etc.</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * 
 * <pre>
 * ProcessSystem process = new ProcessSystem();
 * // ... configure process ...
 * process.run();
 *
 * EmissionsTracker tracker = new EmissionsTracker(process);
 * EmissionsReport report = tracker.calculateEmissions();
 *
 * System.out.println("Total CO2e: " + report.getTotalCO2e("kg/hr") + " kg/hr");
 * System.out.println("Flaring: " + report.getFlaringCO2e("kg/hr") + " kg/hr");
 * System.out.println("Power consumption: " + report.getTotalPower("MW") + " MW");
 *
 * // Export for regulatory compliance
 * report.exportToCSV("emissions_report.csv");
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class EmissionsTracker implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final ProcessSystem processSystem;
  private double gridEmissionFactor = 0.4; // kg CO2/kWh default (varies by region)
  private double naturalGasEmissionFactor = 2.75; // kg CO2/Sm3 natural gas combustion
  private boolean includeIndirectEmissions = true;
  private Instant trackingStartTime;
  private List<EmissionsSnapshot> history = new ArrayList<>();

  /**
   * Creates an emissions tracker for a process system.
   *
   * @param processSystem the process system to track
   */
  public EmissionsTracker(ProcessSystem processSystem) {
    this.processSystem = processSystem;
    this.trackingStartTime = Instant.now();
  }

  /**
   * Calculates current emissions from the process system.
   *
   * @return an EmissionsReport with detailed breakdown
   */
  public EmissionsReport calculateEmissions() {
    EmissionsReport report = new EmissionsReport();
    report.timestamp = Instant.now();
    report.processName = processSystem.getName();

    for (ProcessEquipmentInterface equipment : processSystem.getUnitOperations()) {
      EquipmentEmissions eqEmissions = calculateEquipmentEmissions(equipment);
      report.equipmentEmissions.put(equipment.getName(), eqEmissions);
      report.totalCO2eKgPerHr += eqEmissions.directCO2eKgPerHr + eqEmissions.indirectCO2eKgPerHr;
      report.totalPowerKW += eqEmissions.powerConsumptionKW;
      report.totalHeatDutyKW += eqEmissions.heatDutyKW;
    }

    return report;
  }

  /**
   * Records a snapshot of emissions for time-series tracking.
   */
  public void recordSnapshot() {
    EmissionsReport report = calculateEmissions();
    EmissionsSnapshot snapshot = new EmissionsSnapshot();
    snapshot.timestamp = Instant.now();
    snapshot.totalCO2eKgPerHr = report.totalCO2eKgPerHr;
    snapshot.totalPowerKW = report.totalPowerKW;
    history.add(snapshot);
  }

  /**
   * Gets cumulative emissions over the tracking period.
   *
   * @return cumulative CO2e in kg
   */
  public double getCumulativeCO2e() {
    if (history.size() < 2) {
      return 0.0;
    }

    double cumulative = 0.0;
    for (int i = 1; i < history.size(); i++) {
      EmissionsSnapshot prev = history.get(i - 1);
      EmissionsSnapshot curr = history.get(i);
      Duration dt = Duration.between(prev.timestamp, curr.timestamp);
      double hours = dt.toMillis() / 3600000.0;
      double avgRate = (prev.totalCO2eKgPerHr + curr.totalCO2eKgPerHr) / 2.0;
      cumulative += avgRate * hours;
    }

    return cumulative;
  }

  private EquipmentEmissions calculateEquipmentEmissions(ProcessEquipmentInterface equipment) {
    EquipmentEmissions emissions = new EquipmentEmissions();
    emissions.equipmentName = equipment.getName();
    emissions.equipmentType = equipment.getClass().getSimpleName();

    // Flares - direct CO2 emissions
    if (equipment instanceof Flare) {
      Flare flare = (Flare) equipment;
      emissions.directCO2eKgPerHr = flare.getCO2Emission() * 3600; // kg/s to kg/hr
      emissions.category = EmissionCategory.FLARING;
    }

    // Furnace/Burners - direct CO2 emissions from emission rates
    if (equipment instanceof FurnaceBurner) {
      FurnaceBurner furnace = (FurnaceBurner) equipment;
      // Use the emission rates if available
      java.util.Map<String, Double> emissionRates = furnace.getEmissionRatesKgPerHr();
      if (emissionRates != null && emissionRates.containsKey("CO2")) {
        emissions.directCO2eKgPerHr = emissionRates.get("CO2");
      }
      emissions.category = EmissionCategory.COMBUSTION;
    }

    // Compressors - power consumption (indirect emissions)
    // Note: Check Expander first since it extends Compressor but generates power
    if (equipment instanceof Expander
        && !(equipment instanceof Compressor && !(equipment instanceof Expander))) {
      Expander expander = (Expander) equipment;
      // Expanders generate power (negative consumption)
      emissions.powerConsumptionKW = -Math.abs(expander.getPower("kW"));
      emissions.category = EmissionCategory.EXPANSION;
      // Note: Power generation offsets emissions, but we track as negative for accounting
    } else if (equipment instanceof Compressor) {
      Compressor compressor = (Compressor) equipment;
      emissions.powerConsumptionKW = compressor.getPower("kW");
      if (includeIndirectEmissions) {
        emissions.indirectCO2eKgPerHr = emissions.powerConsumptionKW * gridEmissionFactor;
      }
      emissions.category = EmissionCategory.COMPRESSION;
    }

    // Pumps - power consumption
    if (equipment instanceof Pump) {
      Pump pump = (Pump) equipment;
      emissions.powerConsumptionKW = pump.getPower("kW");
      if (includeIndirectEmissions) {
        emissions.indirectCO2eKgPerHr = emissions.powerConsumptionKW * gridEmissionFactor;
      }
      emissions.category = EmissionCategory.PUMPING;
    }

    // Heaters - heat duty (may be electric or fired)
    if (equipment instanceof Heater) {
      Heater heater = (Heater) equipment;
      emissions.heatDutyKW = Math.abs(heater.getDuty() / 1000.0); // W to kW
      // Assume electric heater for indirect emissions
      if (includeIndirectEmissions) {
        emissions.indirectCO2eKgPerHr = emissions.heatDutyKW * gridEmissionFactor;
      }
      emissions.category = EmissionCategory.HEATING;
    }

    // Coolers - power for cooling (air coolers, etc.)
    if (equipment instanceof Cooler) {
      Cooler cooler = (Cooler) equipment;
      // Estimate fan power for air coolers (rough approximation)
      emissions.heatDutyKW = Math.abs(cooler.getDuty() / 1000.0);
      emissions.powerConsumptionKW = emissions.heatDutyKW * 0.02; // ~2% of duty for fans
      if (includeIndirectEmissions) {
        emissions.indirectCO2eKgPerHr = emissions.powerConsumptionKW * gridEmissionFactor;
      }
      emissions.category = EmissionCategory.COOLING;
    }

    return emissions;
  }

  // Getters and setters

  public double getGridEmissionFactor() {
    return gridEmissionFactor;
  }

  /**
   * Sets the grid emission factor for indirect emissions calculation.
   *
   * @param factor kg CO2 per kWh of electricity
   */
  public void setGridEmissionFactor(double factor) {
    this.gridEmissionFactor = factor;
  }

  public double getNaturalGasEmissionFactor() {
    return naturalGasEmissionFactor;
  }

  /**
   * Sets the natural gas combustion emission factor.
   *
   * @param factor kg CO2 per Sm3 natural gas
   */
  public void setNaturalGasEmissionFactor(double factor) {
    this.naturalGasEmissionFactor = factor;
  }

  public boolean isIncludeIndirectEmissions() {
    return includeIndirectEmissions;
  }

  public void setIncludeIndirectEmissions(boolean include) {
    this.includeIndirectEmissions = include;
  }

  public List<EmissionsSnapshot> getHistory() {
    return history;
  }

  /**
   * Categories of emissions sources.
   */
  public enum EmissionCategory {
    FLARING, COMBUSTION, COMPRESSION, EXPANSION, PUMPING, HEATING, COOLING, VENTING, OTHER
  }

  /**
   * Emissions from a single piece of equipment.
   */
  public static class EquipmentEmissions implements Serializable {
    private static final long serialVersionUID = 1000L;

    public String equipmentName;
    public String equipmentType;
    public EmissionCategory category = EmissionCategory.OTHER;
    public double directCO2eKgPerHr = 0.0;
    public double indirectCO2eKgPerHr = 0.0;
    public double powerConsumptionKW = 0.0;
    public double heatDutyKW = 0.0;

    public double getTotalCO2e() {
      return directCO2eKgPerHr + indirectCO2eKgPerHr;
    }
  }

  /**
   * Complete emissions report for a process system.
   */
  public static class EmissionsReport implements Serializable {
    private static final long serialVersionUID = 1000L;

    public Instant timestamp;
    public String processName;
    public double totalCO2eKgPerHr = 0.0;
    public double totalPowerKW = 0.0;
    public double totalHeatDutyKW = 0.0;
    public Map<String, EquipmentEmissions> equipmentEmissions = new HashMap<>();

    /**
     * Gets total CO2e in specified units.
     *
     * @param unit "kg/hr", "ton/hr", "ton/day", "ton/yr"
     * @return emissions in specified unit
     */
    public double getTotalCO2e(String unit) {
      switch (unit.toLowerCase()) {
        case "kg/hr":
          return totalCO2eKgPerHr;
        case "ton/hr":
        case "tonne/hr":
          return totalCO2eKgPerHr / 1000.0;
        case "ton/day":
        case "tonne/day":
          return totalCO2eKgPerHr * 24.0 / 1000.0;
        case "ton/yr":
        case "tonne/yr":
          return totalCO2eKgPerHr * 24.0 * 365.0 / 1000.0;
        default:
          return totalCO2eKgPerHr;
      }
    }

    /**
     * Gets total power consumption in specified units.
     *
     * @param unit "kW", "MW", "hp"
     * @return power in specified unit
     */
    public double getTotalPower(String unit) {
      switch (unit.toLowerCase()) {
        case "kw":
          return totalPowerKW;
        case "mw":
          return totalPowerKW / 1000.0;
        case "hp":
          return totalPowerKW * 1.341;
        default:
          return totalPowerKW;
      }
    }

    /**
     * Gets CO2e from flaring only.
     *
     * @param unit output unit
     * @return flaring emissions
     */
    public double getFlaringCO2e(String unit) {
      double flaringKgPerHr =
          equipmentEmissions.values().stream().filter(e -> e.category == EmissionCategory.FLARING)
              .mapToDouble(e -> e.directCO2eKgPerHr).sum();

      if (unit.toLowerCase().contains("ton")) {
        return flaringKgPerHr / 1000.0;
      }
      return flaringKgPerHr;
    }

    /**
     * Gets emissions breakdown by category.
     *
     * @return map of category to total emissions (kg/hr)
     */
    public Map<EmissionCategory, Double> getEmissionsByCategory() {
      Map<EmissionCategory, Double> byCategory = new HashMap<>();
      for (EquipmentEmissions eq : equipmentEmissions.values()) {
        byCategory.merge(eq.category, eq.getTotalCO2e(), Double::sum);
      }
      return byCategory;
    }

    /**
     * Exports the report to CSV format.
     *
     * @param filePath output file path
     * @throws java.io.IOException if file cannot be written
     */
    public void exportToCSV(String filePath) throws java.io.IOException {
      StringBuilder sb = new StringBuilder();
      sb.append(
          "Equipment,Type,Category,DirectCO2e_kg_hr,IndirectCO2e_kg_hr,TotalCO2e_kg_hr,Power_kW,HeatDuty_kW\n");

      for (EquipmentEmissions eq : equipmentEmissions.values()) {
        sb.append(String.format("%s,%s,%s,%.4f,%.4f,%.4f,%.4f,%.4f\n", eq.equipmentName,
            eq.equipmentType, eq.category, eq.directCO2eKgPerHr, eq.indirectCO2eKgPerHr,
            eq.getTotalCO2e(), eq.powerConsumptionKW, eq.heatDutyKW));
      }

      sb.append(String.format("\nTOTAL,,,%s,%s,%.4f,%.4f,%.4f\n", "", "", totalCO2eKgPerHr,
          totalPowerKW, totalHeatDutyKW));

      try (java.io.FileWriter writer = new java.io.FileWriter(filePath)) {
        writer.write(sb.toString());
      }
    }

    /**
     * Exports the report to JSON format.
     *
     * @param filePath output file path
     * @throws java.io.IOException if file cannot be written
     */
    public void exportToJSON(String filePath) throws java.io.IOException {
      com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting()
          .serializeSpecialFloatingPointValues().create();
      try (java.io.FileWriter writer = new java.io.FileWriter(filePath)) {
        gson.toJson(this, writer);
      }
    }

    /**
     * Converts the report to a JSON string.
     *
     * @return JSON representation
     */
    public String toJson() {
      com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting()
          .serializeSpecialFloatingPointValues().create();
      return gson.toJson(this);
    }

    /**
     * Returns a summary string of the emissions report.
     *
     * @return formatted summary
     */
    public String getSummary() {
      StringBuilder sb = new StringBuilder();
      sb.append("=== Emissions Report: ").append(processName).append(" ===\n");
      sb.append("Timestamp: ").append(timestamp).append("\n\n");
      sb.append(String.format("Total CO2e: %.2f kg/hr (%.2f tonne/yr)\n", totalCO2eKgPerHr,
          getTotalCO2e("ton/yr")));
      sb.append(
          String.format("Total Power: %.2f kW (%.2f MW)\n", totalPowerKW, getTotalPower("MW")));
      sb.append(String.format("Total Heat Duty: %.2f kW\n", totalHeatDutyKW));
      sb.append("\nBreakdown by Category:\n");
      for (Map.Entry<EmissionCategory, Double> entry : getEmissionsByCategory().entrySet()) {
        sb.append(String.format("  %s: %.2f kg/hr\n", entry.getKey(), entry.getValue()));
      }
      return sb.toString();
    }
  }

  /**
   * Time-series snapshot of emissions.
   */
  public static class EmissionsSnapshot implements Serializable {
    private static final long serialVersionUID = 1000L;

    public Instant timestamp;
    public double totalCO2eKgPerHr;
    public double totalPowerKW;
  }
}
