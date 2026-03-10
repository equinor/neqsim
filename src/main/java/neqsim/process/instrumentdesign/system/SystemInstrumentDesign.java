package neqsim.process.instrumentdesign.system;

import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.instrumentdesign.InstrumentDesign;
import neqsim.process.instrumentdesign.InstrumentList;
import neqsim.process.instrumentdesign.InstrumentSpecification;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Plant-wide instrument design summary.
 *
 * <p>
 * Aggregates instrument requirements across all equipment in a {@link ProcessSystem}, calculates
 * total I/O counts, DCS/SIS cabinet requirements, and total instrument CAPEX. This class
 * complements equipment-level instrument designs by providing a system-level view for control
 * system sizing.
 * </p>
 *
 * <p>
 * DCS and SIS sizing follows typical I/O density rules:
 * </p>
 * <ul>
 * <li>DCS: ~64 AI channels per I/O card, ~16 cards per cabinet</li>
 * <li>SIS: ~32 channels per I/O card, ~8 cards per cabinet (redundant)</li>
 * <li>Marshalling: 1 cabinet per ~200 I/O channels</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class SystemInstrumentDesign implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** The process system to analyse. */
  private ProcessSystem processSystem;

  // === Calculated totals ===
  private int totalAI;
  private int totalAO;
  private int totalDI;
  private int totalDO;
  private int totalIO;
  private int safetyAI;
  private int safetyDI;
  private int safetyDO;
  private int totalSafetyIO;
  private int totalInstruments;
  private double totalInstrumentCostUSD;
  private double dcsCostUSD;
  private double sisCostUSD;

  // === DCS/SIS sizing ===
  private int dcsIOCards;
  private int dcsCabinets;
  private int sisIOCards;
  private int sisCabinets;
  private int marshallingCabinets;

  // === Cost assumptions ===
  private double costPerDCSIOChannel = 500.0;
  private double costPerSISIOChannel = 1500.0;
  private double costPerDCSCabinet = 50000.0;
  private double costPerSISCabinet = 80000.0;
  private double costPerMarshallingCabinet = 15000.0;

  /** Per-equipment summaries. */
  private List<Map<String, Object>> equipmentSummaries = new ArrayList<Map<String, Object>>();

  /**
   * Constructor for SystemInstrumentDesign.
   *
   * @param processSystem the process system to analyse
   */
  public SystemInstrumentDesign(ProcessSystem processSystem) {
    this.processSystem = processSystem;
  }

  /**
   * Run the system-level instrument design.
   *
   * <p>
   * Iterates over all equipment, runs their instrument designs, and aggregates the results.
   * </p>
   */
  public void calcDesign() {
    totalAI = 0;
    totalAO = 0;
    totalDI = 0;
    totalDO = 0;
    totalInstruments = 0;
    totalInstrumentCostUSD = 0.0;
    safetyAI = 0;
    safetyDI = 0;
    safetyDO = 0;
    equipmentSummaries.clear();

    List<ProcessEquipmentInterface> equipment = processSystem.getUnitOperations();
    for (ProcessEquipmentInterface equip : equipment) {
      InstrumentDesign design = equip.getInstrumentDesign();
      if (design != null) {
        design.calcDesign();
        InstrumentList list = design.getInstrumentList();

        int eqAI = list.getAnalogInputCount();
        int eqAO = list.getAnalogOutputCount();
        int eqDI = list.getDigitalInputCount();
        int eqDO = list.getDigitalOutputCount();
        int eqSafety = list.getSafetyInstrumentCount();

        totalAI += eqAI;
        totalAO += eqAO;
        totalDI += eqDI;
        totalDO += eqDO;
        totalInstruments += list.size();
        totalInstrumentCostUSD += list.getTotalCostUSD();

        // Count safety I/O by type
        for (InstrumentSpecification spec : list.getAll()) {
          if (spec.isSafetyRelated()) {
            String io = spec.getIoType();
            if ("AI".equals(io)) {
              safetyAI++;
            } else if ("DI".equals(io)) {
              safetyDI++;
            } else if ("DO".equals(io)) {
              safetyDO++;
            }
          }
        }

        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("equipment", equip.getName());
        summary.put("equipmentType", equip.getClass().getSimpleName());
        summary.put("instruments", list.size());
        summary.put("AI", eqAI);
        summary.put("AO", eqAO);
        summary.put("DI", eqDI);
        summary.put("DO", eqDO);
        summary.put("safetyInstruments", eqSafety);
        summary.put("costUSD", list.getTotalCostUSD());
        equipmentSummaries.add(summary);
      }
    }

    totalIO = totalAI + totalAO + totalDI + totalDO;
    totalSafetyIO = safetyAI + safetyDI + safetyDO;

    // Size DCS
    int dcsIO = totalIO - totalSafetyIO;
    dcsIOCards = (int) Math.ceil(dcsIO / 16.0);
    dcsCabinets = Math.max(1, (int) Math.ceil(dcsIOCards / 16.0));

    // Size SIS
    sisIOCards = (int) Math.ceil(totalSafetyIO / 8.0);
    sisCabinets = Math.max(totalSafetyIO > 0 ? 1 : 0, (int) Math.ceil(sisIOCards / 8.0));

    // Marshalling cabinets
    marshallingCabinets = Math.max(1, (int) Math.ceil(totalIO / 200.0));

    // Cost estimation
    dcsCostUSD = dcsIO * costPerDCSIOChannel + dcsCabinets * costPerDCSCabinet;
    sisCostUSD = totalSafetyIO * costPerSISIOChannel + sisCabinets * costPerSISCabinet;
    double marshallingCost = marshallingCabinets * costPerMarshallingCabinet;

    totalInstrumentCostUSD += dcsCostUSD + sisCostUSD + marshallingCost;
  }

  /**
   * Serialize the system instrument design to JSON.
   *
   * @return JSON string with system-level instrument design data
   */
  public String toJson() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("plantName", processSystem.getName());

    Map<String, Object> ioSummary = new LinkedHashMap<String, Object>();
    ioSummary.put("totalAI", totalAI);
    ioSummary.put("totalAO", totalAO);
    ioSummary.put("totalDI", totalDI);
    ioSummary.put("totalDO", totalDO);
    ioSummary.put("totalIO", totalIO);
    ioSummary.put("totalInstruments", totalInstruments);
    result.put("ioSummary", ioSummary);

    Map<String, Object> safety = new LinkedHashMap<String, Object>();
    safety.put("safetyAI", safetyAI);
    safety.put("safetyDI", safetyDI);
    safety.put("safetyDO", safetyDO);
    safety.put("totalSafetyIO", totalSafetyIO);
    result.put("safetyIO", safety);

    Map<String, Object> controlSystem = new LinkedHashMap<String, Object>();
    controlSystem.put("dcsIOCards", dcsIOCards);
    controlSystem.put("dcsCabinets", dcsCabinets);
    controlSystem.put("sisIOCards", sisIOCards);
    controlSystem.put("sisCabinets", sisCabinets);
    controlSystem.put("marshallingCabinets", marshallingCabinets);
    result.put("controlSystemSizing", controlSystem);

    Map<String, Object> cost = new LinkedHashMap<String, Object>();
    cost.put("instrumentCostUSD", totalInstrumentCostUSD - dcsCostUSD - sisCostUSD);
    cost.put("dcsCostUSD", dcsCostUSD);
    cost.put("sisCostUSD", sisCostUSD);
    cost.put("marshallingCostUSD", marshallingCabinets * costPerMarshallingCabinet);
    cost.put("totalCostUSD", totalInstrumentCostUSD);
    result.put("costSummary", cost);

    result.put("equipmentDetails", equipmentSummaries);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(result);
  }

  // === Getters ===

  /**
   * Get total analog input count.
   *
   * @return total AI count
   */
  public int getTotalAI() {
    return totalAI;
  }

  /**
   * Get total analog output count.
   *
   * @return total AO count
   */
  public int getTotalAO() {
    return totalAO;
  }

  /**
   * Get total digital input count.
   *
   * @return total DI count
   */
  public int getTotalDI() {
    return totalDI;
  }

  /**
   * Get total digital output count.
   *
   * @return total DO count
   */
  public int getTotalDO() {
    return totalDO;
  }

  /**
   * Get total I/O count.
   *
   * @return total I/O count
   */
  public int getTotalIO() {
    return totalIO;
  }

  /**
   * Get total safety I/O count.
   *
   * @return total safety I/O count
   */
  public int getTotalSafetyIO() {
    return totalSafetyIO;
  }

  /**
   * Get total number of instruments across all equipment.
   *
   * @return total instrument count
   */
  public int getTotalInstruments() {
    return totalInstruments;
  }

  /**
   * Get total estimated instrument cost in USD.
   *
   * @return total cost in USD
   */
  public double getTotalInstrumentCostUSD() {
    return totalInstrumentCostUSD;
  }

  /**
   * Get DCS cost in USD.
   *
   * @return DCS cost in USD
   */
  public double getDcsCostUSD() {
    return dcsCostUSD;
  }

  /**
   * Get SIS cost in USD.
   *
   * @return SIS cost in USD
   */
  public double getSisCostUSD() {
    return sisCostUSD;
  }

  /**
   * Get DCS cabinet count.
   *
   * @return DCS cabinet count
   */
  public int getDcsCabinets() {
    return dcsCabinets;
  }

  /**
   * Get SIS cabinet count.
   *
   * @return SIS cabinet count
   */
  public int getSisCabinets() {
    return sisCabinets;
  }

  /**
   * Get marshalling cabinet count.
   *
   * @return marshalling cabinet count
   */
  public int getMarshallingCabinets() {
    return marshallingCabinets;
  }

  /**
   * Get per-equipment summaries.
   *
   * @return list of equipment summaries
   */
  public List<Map<String, Object>> getEquipmentSummaries() {
    return equipmentSummaries;
  }
}
