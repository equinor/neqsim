package neqsim.process.safety.risk.bowtie;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.safety.risk.sis.SafetyInstrumentedFunction;

/**
 * Bow-Tie Model representing hazard, threats, consequences, and barriers.
 *
 * <p>
 * A bow-tie model visualizes the relationship between causes (threats), a central hazardous event
 * (top event), and potential outcomes (consequences), along with the preventive and mitigating
 * barriers in place.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class BowTieModel implements Serializable {

  private static final long serialVersionUID = 1000L;

  /** Hazard identifier. */
  private String hazardId;

  /** Hazard description (top event). */
  private String hazardDescription;

  /** Hazard type. */
  private String hazardType;

  /** Threats (causes). */
  private List<Threat> threats;

  /** Consequences. */
  private List<Consequence> consequences;

  /** Barriers. */
  private List<Barrier> barriers;

  /** Calculated unmitigated frequency. */
  private double unmitigatedFrequency;

  /** Calculated mitigated frequency. */
  private double mitigatedFrequency;

  /** Maximum consequence severity. */
  private int maxSeverity;

  /**
   * Barrier type enumeration.
   */
  public enum BarrierType {
    PREVENTION, MITIGATION, BOTH
  }

  /**
   * Threat (cause) leading to hazard.
   */
  public static class Threat implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String description;
    private double frequency;
    private List<String> linkedBarrierIds;
    private boolean active = true;

    public Threat(String id, String description, double frequency) {
      this.id = id;
      this.description = description;
      this.frequency = frequency;
      this.linkedBarrierIds = new ArrayList<>();
    }

    public String getId() {
      return id;
    }

    public String getDescription() {
      return description;
    }

    public double getFrequency() {
      return frequency;
    }

    public void setFrequency(double frequency) {
      this.frequency = frequency;
    }

    public List<String> getLinkedBarrierIds() {
      return linkedBarrierIds;
    }

    public void linkBarrier(String barrierId) {
      linkedBarrierIds.add(barrierId);
    }

    public boolean isActive() {
      return active;
    }

    public void setActive(boolean active) {
      this.active = active;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<>();
      map.put("id", id);
      map.put("description", description);
      map.put("frequency", frequency);
      map.put("linkedBarriers", linkedBarrierIds);
      map.put("active", active);
      return map;
    }
  }

  /**
   * Consequence of hazard realization.
   */
  public static class Consequence implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String description;
    private int severity;
    private String category;
    private double probability;
    private List<String> linkedBarrierIds;

    public Consequence(String id, String description, int severity) {
      this.id = id;
      this.description = description;
      this.severity = severity;
      this.probability = 1.0;
      this.linkedBarrierIds = new ArrayList<>();
    }

    public String getId() {
      return id;
    }

    public String getDescription() {
      return description;
    }

    public int getSeverity() {
      return severity;
    }

    public void setSeverity(int severity) {
      this.severity = severity;
    }

    public String getCategory() {
      return category;
    }

    public void setCategory(String category) {
      this.category = category;
    }

    public double getProbability() {
      return probability;
    }

    public void setProbability(double probability) {
      this.probability = probability;
    }

    public List<String> getLinkedBarrierIds() {
      return linkedBarrierIds;
    }

    public void linkBarrier(String barrierId) {
      linkedBarrierIds.add(barrierId);
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<>();
      map.put("id", id);
      map.put("description", description);
      map.put("severity", severity);
      map.put("category", category);
      map.put("probability", probability);
      map.put("linkedBarriers", linkedBarrierIds);
      return map;
    }
  }

  /**
   * Barrier (prevention or mitigation).
   */
  public static class Barrier implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String description;
    private BarrierType barrierType;
    private double pfd;
    private double effectiveness;
    private boolean functional = true;
    private SafetyInstrumentedFunction sif;
    private String owner;
    private String verificationStatus;

    public Barrier(String id, String description, double pfd) {
      this.id = id;
      this.description = description;
      this.pfd = pfd;
      this.effectiveness = 1.0 - pfd;
      this.barrierType = BarrierType.PREVENTION;
    }

    public String getId() {
      return id;
    }

    public String getDescription() {
      return description;
    }

    public BarrierType getBarrierType() {
      return barrierType;
    }

    public void setBarrierType(BarrierType type) {
      this.barrierType = type;
    }

    public double getPfd() {
      return pfd;
    }

    public void setPfd(double pfd) {
      this.pfd = pfd;
      this.effectiveness = 1.0 - pfd;
    }

    public double getEffectiveness() {
      return effectiveness;
    }

    public boolean isFunctional() {
      return functional;
    }

    public void setFunctional(boolean functional) {
      this.functional = functional;
    }

    public SafetyInstrumentedFunction getSif() {
      return sif;
    }

    public void setSif(SafetyInstrumentedFunction sif) {
      this.sif = sif;
      if (sif != null) {
        this.pfd = sif.getPfdAvg();
        this.effectiveness = 1.0 - pfd;
      }
    }

    public String getOwner() {
      return owner;
    }

    public void setOwner(String owner) {
      this.owner = owner;
    }

    public String getVerificationStatus() {
      return verificationStatus;
    }

    public void setVerificationStatus(String status) {
      this.verificationStatus = status;
    }

    public double getRRF() {
      return pfd > 0 ? 1.0 / pfd : Double.POSITIVE_INFINITY;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<>();
      map.put("id", id);
      map.put("description", description);
      map.put("barrierType", barrierType.name());
      map.put("pfd", pfd);
      map.put("effectiveness", effectiveness);
      map.put("rrf", getRRF());
      map.put("functional", functional);
      if (sif != null) {
        map.put("sifName", sif.getName());
        map.put("sil", sif.getSil());
      }
      map.put("owner", owner);
      map.put("verificationStatus", verificationStatus);
      return map;
    }
  }

  /**
   * Creates a bow-tie model.
   *
   * @param hazardId hazard identifier
   * @param hazardDescription hazard description
   */
  public BowTieModel(String hazardId, String hazardDescription) {
    this.hazardId = hazardId;
    this.hazardDescription = hazardDescription;
    this.threats = new ArrayList<>();
    this.consequences = new ArrayList<>();
    this.barriers = new ArrayList<>();
  }

  /**
   * Adds a threat.
   *
   * @param threat threat to add
   */
  public void addThreat(Threat threat) {
    threats.add(threat);
  }

  /**
   * Adds a consequence.
   *
   * @param consequence consequence to add
   */
  public void addConsequence(Consequence consequence) {
    consequences.add(consequence);
  }

  /**
   * Adds a barrier.
   *
   * @param barrier barrier to add
   */
  public void addBarrier(Barrier barrier) {
    barriers.add(barrier);
  }

  /**
   * Links a barrier to a threat.
   *
   * @param threatId threat ID
   * @param barrierId barrier ID
   */
  public void linkBarrierToThreat(String threatId, String barrierId) {
    for (Threat threat : threats) {
      if (threat.getId().equals(threatId)) {
        threat.linkBarrier(barrierId);
        break;
      }
    }
  }

  /**
   * Links a barrier to a consequence.
   *
   * @param consequenceId consequence ID
   * @param barrierId barrier ID
   */
  public void linkBarrierToConsequence(String consequenceId, String barrierId) {
    for (Consequence consequence : consequences) {
      if (consequence.getId().equals(consequenceId)) {
        consequence.linkBarrier(barrierId);
        break;
      }
    }
  }

  /**
   * Calculates risk for this bow-tie.
   */
  public void calculateRisk() {
    // Calculate unmitigated frequency (sum of active threat frequencies)
    unmitigatedFrequency = 0;
    for (Threat threat : threats) {
      if (threat.isActive()) {
        unmitigatedFrequency += threat.getFrequency();
      }
    }

    // Calculate mitigated frequency through prevention barriers
    mitigatedFrequency = unmitigatedFrequency;
    for (Barrier barrier : barriers) {
      if (barrier.getBarrierType() == BarrierType.PREVENTION
          || barrier.getBarrierType() == BarrierType.BOTH) {
        if (barrier.isFunctional()) {
          mitigatedFrequency *= barrier.getPfd();
        }
      }
    }

    // Find maximum severity
    maxSeverity = 0;
    for (Consequence consequence : consequences) {
      if (consequence.getSeverity() > maxSeverity) {
        maxSeverity = consequence.getSeverity();
      }
    }
  }

  /**
   * Gets prevention barriers.
   *
   * @return list of prevention barriers
   */
  public List<Barrier> getPreventionBarriers() {
    List<Barrier> prevention = new ArrayList<>();
    for (Barrier barrier : barriers) {
      if (barrier.getBarrierType() == BarrierType.PREVENTION
          || barrier.getBarrierType() == BarrierType.BOTH) {
        prevention.add(barrier);
      }
    }
    return prevention;
  }

  /**
   * Gets mitigation barriers.
   *
   * @return list of mitigation barriers
   */
  public List<Barrier> getMitigationBarriers() {
    List<Barrier> mitigation = new ArrayList<>();
    for (Barrier barrier : barriers) {
      if (barrier.getBarrierType() == BarrierType.MITIGATION
          || barrier.getBarrierType() == BarrierType.BOTH) {
        mitigation.add(barrier);
      }
    }
    return mitigation;
  }

  /**
   * Gets total risk reduction factor.
   *
   * @return total RRF
   */
  public double getTotalRRF() {
    return mitigatedFrequency > 0 ? unmitigatedFrequency / mitigatedFrequency : 0;
  }

  // Getters

  public String getHazardId() {
    return hazardId;
  }

  public String getHazardDescription() {
    return hazardDescription;
  }

  public String getHazardType() {
    return hazardType;
  }

  public void setHazardType(String type) {
    this.hazardType = type;
  }

  public List<Threat> getThreats() {
    return new ArrayList<>(threats);
  }

  public List<Consequence> getConsequences() {
    return new ArrayList<>(consequences);
  }

  public List<Barrier> getBarriers() {
    return new ArrayList<>(barriers);
  }

  public double getUnmitigatedFrequency() {
    return unmitigatedFrequency;
  }

  public double getMitigatedFrequency() {
    return mitigatedFrequency;
  }

  public int getMaxSeverity() {
    return maxSeverity;
  }

  /**
   * Generates ASCII visualization of bow-tie.
   *
   * @return ASCII diagram
   */
  public String toVisualization() {
    StringBuilder sb = new StringBuilder();

    sb.append("┌─────────────────────────────────────────────────────────────────────┐\n");
    sb.append("│ BOW-TIE: ").append(hazardDescription);
    int padding = 60 - hazardDescription.length();
    for (int i = 0; i < padding; i++) {
      sb.append(" ");
    }
    sb.append("│\n");
    sb.append("├─────────────────────────────────────────────────────────────────────┤\n");

    // Threats section
    sb.append("│ THREATS (Causes)          │    TOP EVENT    │  CONSEQUENCES         │\n");
    sb.append("│                           │                 │                       │\n");

    int maxRows = Math.max(Math.max(threats.size(), consequences.size()), 3);
    List<Barrier> prevention = getPreventionBarriers();
    List<Barrier> mitigation = getMitigationBarriers();

    for (int i = 0; i < maxRows; i++) {
      sb.append("│ ");

      // Threat
      if (i < threats.size()) {
        String threatText = threats.get(i).getDescription();
        if (threatText.length() > 22) {
          threatText = threatText.substring(0, 19) + "...";
        }
        sb.append(String.format("%-22s", threatText));
      } else {
        sb.append(String.format("%-22s", ""));
      }

      // Arrow and barriers
      if (i == 0) {
        sb.append(" ──▶");
        if (!prevention.isEmpty()) {
          sb.append(" [B] ");
        } else {
          sb.append("     ");
        }
        sb.append("──▶ ");
      } else if (i == 1) {
        sb.append("              ◆       ");
      } else if (i == 2) {
        sb.append("          (HAZARD)    ");
      } else {
        sb.append("                      ");
      }

      // Arrow to consequences
      if (i == 0) {
        sb.append("──▶");
        if (!mitigation.isEmpty()) {
          sb.append(" [B] ");
        } else {
          sb.append("     ");
        }
        sb.append("──▶ ");
      } else {
        sb.append("          ");
      }

      // Consequence
      if (i < consequences.size()) {
        String consText = consequences.get(i).getDescription();
        if (consText.length() > 18) {
          consText = consText.substring(0, 15) + "...";
        }
        sb.append(String.format("%-18s", consText));
      } else {
        sb.append(String.format("%-18s", ""));
      }

      sb.append("│\n");
    }

    sb.append("├─────────────────────────────────────────────────────────────────────┤\n");

    // Barriers section
    sb.append("│ PREVENTION BARRIERS:                                                │\n");
    for (Barrier b : prevention) {
      String status = b.isFunctional() ? "✓" : "✗";
      sb.append(String.format("│   %s %-40s PFD=%.2e  RRF=%.0f │%n", status, b.getDescription(),
          b.getPfd(), b.getRRF()));
    }
    if (prevention.isEmpty()) {
      sb.append("│   (none)                                                            │\n");
    }

    sb.append("│                                                                     │\n");
    sb.append("│ MITIGATION BARRIERS:                                                │\n");
    for (Barrier b : mitigation) {
      String status = b.isFunctional() ? "✓" : "✗";
      sb.append(String.format("│   %s %-40s PFD=%.2e  RRF=%.0f │%n", status, b.getDescription(),
          b.getPfd(), b.getRRF()));
    }
    if (mitigation.isEmpty()) {
      sb.append("│   (none)                                                            │\n");
    }

    sb.append("├─────────────────────────────────────────────────────────────────────┤\n");
    sb.append(
        String.format("│ Unmitigated Freq: %.2e /yr    Mitigated Freq: %.2e /yr   RRF: %.0f │%n",
            unmitigatedFrequency, mitigatedFrequency, getTotalRRF()));
    sb.append("└─────────────────────────────────────────────────────────────────────┘\n");

    return sb.toString();
  }

  /**
   * Converts to map for JSON serialization.
   *
   * @return map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("hazardId", hazardId);
    map.put("hazardDescription", hazardDescription);
    map.put("hazardType", hazardType);

    // Threats
    List<Map<String, Object>> threatList = new ArrayList<>();
    for (Threat threat : threats) {
      threatList.add(threat.toMap());
    }
    map.put("threats", threatList);

    // Consequences
    List<Map<String, Object>> consList = new ArrayList<>();
    for (Consequence cons : consequences) {
      consList.add(cons.toMap());
    }
    map.put("consequences", consList);

    // Barriers
    List<Map<String, Object>> barrierList = new ArrayList<>();
    for (Barrier barrier : barriers) {
      barrierList.add(barrier.toMap());
    }
    map.put("barriers", barrierList);

    // Risk metrics
    Map<String, Object> risk = new HashMap<>();
    risk.put("unmitigatedFrequency", unmitigatedFrequency);
    risk.put("mitigatedFrequency", mitigatedFrequency);
    risk.put("totalRRF", getTotalRRF());
    risk.put("maxSeverity", maxSeverity);
    map.put("riskMetrics", risk);

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

  @Override
  public String toString() {
    return String.format("BowTieModel[%s: threats=%d, consequences=%d, barriers=%d]", hazardId,
        threats.size(), consequences.size(), barriers.size());
  }
}
