package neqsim.process.safety.risk.bowtie;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.risk.sis.SafetyInstrumentedFunction;

/**
 * Bow-Tie Analyzer for process safety risk assessment.
 *
 * <p>
 * Generates bow-tie diagrams linking process hazards with threats (causes) and consequences,
 * including prevention and mitigation barriers. Integrates with the NeqSim process system to
 * automatically identify potential hazards and link to SIS/IPL barriers.
 * </p>
 *
 * <h2>Bow-Tie Structure</h2>
 * 
 * <pre>
 *   THREATS           TOP EVENT           CONSEQUENCES
 *     │                  │                    │
 *     ├──▶ Barrier ──▶   ◆   ──▶ Barrier ──▶ ├──
 *     │                  │                    │
 *     ├──▶ Barrier ──▶   │   ──▶ Barrier ──▶ ├──
 *     │                  │                    │
 * (Prevention)      (Hazard)            (Mitigation)
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class BowTieAnalyzer implements Serializable {

  private static final long serialVersionUID = 1000L;

  /** Analyzer name. */
  private String name;

  /** Process system being analyzed. */
  private transient ProcessSystem processSystem;

  /** Generated bow-tie models. */
  private List<BowTieModel> bowTieModels;

  /** Available SIFs for barriers. */
  private List<SafetyInstrumentedFunction> availableSIFs;

  /** Standard threat library. */
  private Map<String, List<ThreatTemplate>> threatLibrary;

  /** Standard consequence library. */
  private Map<String, List<ConsequenceTemplate>> consequenceLibrary;

  /**
   * Threat template from library.
   */
  public static class ThreatTemplate implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String description;
    private String category;
    private double baseFrequency;
    private List<String> applicableEquipment;
    private List<String> recommendedBarriers;

    public ThreatTemplate(String id, String description, String category, double frequency) {
      this.id = id;
      this.description = description;
      this.category = category;
      this.baseFrequency = frequency;
      this.applicableEquipment = new ArrayList<>();
      this.recommendedBarriers = new ArrayList<>();
    }

    public String getId() {
      return id;
    }

    public String getDescription() {
      return description;
    }

    public String getCategory() {
      return category;
    }

    public double getBaseFrequency() {
      return baseFrequency;
    }

    public List<String> getApplicableEquipment() {
      return applicableEquipment;
    }

    public List<String> getRecommendedBarriers() {
      return recommendedBarriers;
    }

    public void addApplicableEquipment(String equipment) {
      applicableEquipment.add(equipment);
    }

    public void addRecommendedBarrier(String barrier) {
      recommendedBarriers.add(barrier);
    }
  }

  /**
   * Consequence template from library.
   */
  public static class ConsequenceTemplate implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String description;
    private ConsequenceCategory category;
    private int defaultSeverity;
    private List<String> recommendedMitigations;

    public enum ConsequenceCategory {
      SAFETY, ENVIRONMENTAL, ASSET, PRODUCTION, REPUTATION
    }

    public ConsequenceTemplate(String id, String description, ConsequenceCategory category,
        int severity) {
      this.id = id;
      this.description = description;
      this.category = category;
      this.defaultSeverity = severity;
      this.recommendedMitigations = new ArrayList<>();
    }

    public String getId() {
      return id;
    }

    public String getDescription() {
      return description;
    }

    public ConsequenceCategory getCategory() {
      return category;
    }

    public int getDefaultSeverity() {
      return defaultSeverity;
    }

    public List<String> getRecommendedMitigations() {
      return recommendedMitigations;
    }

    public void addRecommendedMitigation(String mitigation) {
      recommendedMitigations.add(mitigation);
    }
  }

  /**
   * Creates a bow-tie analyzer.
   *
   * @param name analyzer name
   */
  public BowTieAnalyzer(String name) {
    this.name = name;
    this.bowTieModels = new ArrayList<>();
    this.availableSIFs = new ArrayList<>();
    initializeLibraries();
  }

  /**
   * Creates a bow-tie analyzer for a process system.
   *
   * @param name analyzer name
   * @param processSystem process system
   */
  public BowTieAnalyzer(String name, ProcessSystem processSystem) {
    this(name);
    this.processSystem = processSystem;
  }

  private void initializeLibraries() {
    threatLibrary = new HashMap<>();
    consequenceLibrary = new HashMap<>();

    // Overpressure threats
    List<ThreatTemplate> overpressureThreats = new ArrayList<>();
    ThreatTemplate t1 =
        new ThreatTemplate("T-OVP-001", "Blocked outlet with continued feed", "Process", 0.1);
    t1.addApplicableEquipment("Separator");
    t1.addApplicableEquipment("Vessel");
    t1.addRecommendedBarrier("HIPPS");
    t1.addRecommendedBarrier("PSV");
    overpressureThreats.add(t1);

    ThreatTemplate t2 =
        new ThreatTemplate("T-OVP-002", "External fire causing overpressure", "External", 0.01);
    t2.addApplicableEquipment("Vessel");
    t2.addApplicableEquipment("Pipeline");
    t2.addRecommendedBarrier("PSV");
    t2.addRecommendedBarrier("Fire protection");
    overpressureThreats.add(t2);

    ThreatTemplate t3 =
        new ThreatTemplate("T-OVP-003", "Control valve fails open", "Equipment", 0.05);
    t3.addApplicableEquipment("Separator");
    t3.addRecommendedBarrier("HIPPS");
    t3.addRecommendedBarrier("High pressure alarm");
    overpressureThreats.add(t3);

    threatLibrary.put("OVERPRESSURE", overpressureThreats);

    // Loss of containment threats
    List<ThreatTemplate> locThreats = new ArrayList<>();
    ThreatTemplate t4 =
        new ThreatTemplate("T-LOC-001", "Corrosion-induced leak", "Equipment", 0.02);
    t4.addApplicableEquipment("Pipeline");
    t4.addApplicableEquipment("Vessel");
    t4.addRecommendedBarrier("Corrosion monitoring");
    t4.addRecommendedBarrier("Inspection program");
    locThreats.add(t4);

    ThreatTemplate t5 =
        new ThreatTemplate("T-LOC-002", "Mechanical seal failure", "Equipment", 0.05);
    t5.addApplicableEquipment("Pump");
    t5.addApplicableEquipment("Compressor");
    t5.addRecommendedBarrier("Dual seals");
    t5.addRecommendedBarrier("Seal monitoring");
    locThreats.add(t5);

    threatLibrary.put("LOSS_OF_CONTAINMENT", locThreats);

    // Overpressure consequences
    List<ConsequenceTemplate> overpressureCons = new ArrayList<>();
    ConsequenceTemplate c1 = new ConsequenceTemplate("C-OVP-001", "Vessel rupture",
        ConsequenceTemplate.ConsequenceCategory.SAFETY, 5);
    c1.addRecommendedMitigation("Emergency shutdown");
    c1.addRecommendedMitigation("Blast walls");
    overpressureCons.add(c1);

    ConsequenceTemplate c2 = new ConsequenceTemplate("C-OVP-002", "Flange leak under pressure",
        ConsequenceTemplate.ConsequenceCategory.SAFETY, 4);
    c2.addRecommendedMitigation("Gas detection");
    c2.addRecommendedMitigation("Emergency isolation");
    overpressureCons.add(c2);

    consequenceLibrary.put("OVERPRESSURE", overpressureCons);

    // Loss of containment consequences
    List<ConsequenceTemplate> locCons = new ArrayList<>();
    ConsequenceTemplate c3 = new ConsequenceTemplate("C-LOC-001", "Hydrocarbon release - fire",
        ConsequenceTemplate.ConsequenceCategory.SAFETY, 5);
    c3.addRecommendedMitigation("Fire and gas detection");
    c3.addRecommendedMitigation("Deluge system");
    c3.addRecommendedMitigation("Emergency response");
    locCons.add(c3);

    ConsequenceTemplate c4 = new ConsequenceTemplate("C-LOC-002", "Environmental contamination",
        ConsequenceTemplate.ConsequenceCategory.ENVIRONMENTAL, 3);
    c4.addRecommendedMitigation("Secondary containment");
    c4.addRecommendedMitigation("Spill response");
    locCons.add(c4);

    consequenceLibrary.put("LOSS_OF_CONTAINMENT", locCons);
  }

  /**
   * Adds available SIF for barrier assignment.
   *
   * @param sif Safety Instrumented Function
   */
  public void addAvailableSIF(SafetyInstrumentedFunction sif) {
    availableSIFs.add(sif);
  }

  /**
   * Creates a bow-tie model for a specific hazard.
   *
   * @param hazardId hazard identifier
   * @param hazardDescription hazard description
   * @param hazardType type of hazard (e.g., OVERPRESSURE, LOSS_OF_CONTAINMENT)
   * @return created bow-tie model
   */
  public BowTieModel createBowTie(String hazardId, String hazardDescription, String hazardType) {
    BowTieModel model = new BowTieModel(hazardId, hazardDescription);
    model.setHazardType(hazardType);

    // Auto-populate from libraries
    if (threatLibrary.containsKey(hazardType)) {
      for (ThreatTemplate template : threatLibrary.get(hazardType)) {
        BowTieModel.Threat threat = new BowTieModel.Threat(template.getId(),
            template.getDescription(), template.getBaseFrequency());
        model.addThreat(threat);
      }
    }

    if (consequenceLibrary.containsKey(hazardType)) {
      for (ConsequenceTemplate template : consequenceLibrary.get(hazardType)) {
        BowTieModel.Consequence consequence = new BowTieModel.Consequence(template.getId(),
            template.getDescription(), template.getDefaultSeverity());
        consequence.setCategory(template.getCategory().name());
        model.addConsequence(consequence);
      }
    }

    bowTieModels.add(model);
    return model;
  }

  /**
   * Auto-generates bow-ties from process system topology.
   *
   * @return list of generated bow-tie models
   */
  public List<BowTieModel> autoGenerateFromProcess() {
    if (processSystem == null) {
      throw new IllegalStateException("No process system configured");
    }

    List<BowTieModel> generated = new ArrayList<>();

    // Analyze each equipment for potential hazards
    List<ProcessEquipmentInterface> units = processSystem.getUnitOperations();
    for (int i = 0; i < units.size(); i++) {
      String equipName = units.get(i).getName();
      String equipType = units.get(i).getClass().getSimpleName();

      // Check for pressure-related hazards
      if (equipType.contains("Separator") || equipType.contains("Vessel")) {
        BowTieModel overpressure =
            createBowTie(equipName + "-OVP", "Overpressure in " + equipName, "OVERPRESSURE");
        generated.add(overpressure);
      }

      // Check for containment hazards
      if (equipType.contains("Pipe") || equipType.contains("Pump")) {
        BowTieModel loc = createBowTie(equipName + "-LOC", "Loss of containment from " + equipName,
            "LOSS_OF_CONTAINMENT");
        generated.add(loc);
      }
    }

    // Auto-assign available SIFs as barriers
    autoAssignSIFBarriers();

    return generated;
  }

  private void autoAssignSIFBarriers() {
    for (BowTieModel model : bowTieModels) {
      for (SafetyInstrumentedFunction sif : availableSIFs) {
        // Match SIF category to hazard type
        boolean matches = false;
        switch (sif.getCategory()) {
          case HIPPS:
            matches = model.getHazardType().equals("OVERPRESSURE");
            break;
          case ESD:
          case PSD:
            matches = true; // ESD/PSD applies to most hazards
            break;
          case FIRE_GAS:
            matches = model.getHazardType().equals("LOSS_OF_CONTAINMENT");
            break;
          default:
            break;
        }

        if (matches) {
          BowTieModel.Barrier barrier =
              new BowTieModel.Barrier(sif.getName(), sif.getName(), sif.getPfdAvg());
          barrier.setBarrierType(BowTieModel.BarrierType.PREVENTION);
          barrier.setSif(sif);
          model.addBarrier(barrier);
        }
      }
    }
  }

  /**
   * Calculates risk for all bow-ties.
   */
  public void calculateRisk() {
    for (BowTieModel model : bowTieModels) {
      model.calculateRisk();
    }
  }

  /**
   * Gets bow-tie model by hazard ID.
   *
   * @param hazardId hazard identifier
   * @return bow-tie model or null
   */
  public BowTieModel getBowTie(String hazardId) {
    for (BowTieModel model : bowTieModels) {
      if (model.getHazardId().equals(hazardId)) {
        return model;
      }
    }
    return null;
  }

  /**
   * Gets all bow-tie models.
   *
   * @return list of bow-tie models
   */
  public List<BowTieModel> getBowTieModels() {
    return new ArrayList<>(bowTieModels);
  }

  /**
   * Generates summary report.
   *
   * @return summary report
   */
  public String generateReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("BOW-TIE ANALYSIS REPORT\n");
    sb.append(StringUtils.repeat("═", 70)).append("\n");
    sb.append("Analysis: ").append(name).append("\n");
    sb.append("Number of Bow-Ties: ").append(bowTieModels.size()).append("\n\n");

    for (BowTieModel model : bowTieModels) {
      sb.append(model.toVisualization()).append("\n");
    }

    return sb.toString();
  }

  /**
   * Converts to map for JSON serialization.
   *
   * @return map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("name", name);
    map.put("bowTieCount", bowTieModels.size());

    List<Map<String, Object>> bowTies = new ArrayList<>();
    for (BowTieModel model : bowTieModels) {
      bowTies.add(model.toMap());
    }
    map.put("bowTies", bowTies);

    return map;
  }

  /**
   * Converts to JSON string.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }

  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return String.format("BowTieAnalyzer[%s, bowTies=%d]", name, bowTieModels.size());
  }
}
