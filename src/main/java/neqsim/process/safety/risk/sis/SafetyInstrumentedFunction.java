package neqsim.process.safety.risk.sis;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Represents a Safety Instrumented Function (SIF) for risk reduction calculation.
 *
 * <p>
 * A SIF is a safety function implemented by a Safety Instrumented System (SIS) that brings the
 * process to a safe state when a dangerous condition is detected. Key metrics include:
 * </p>
 * <ul>
 * <li>Safety Integrity Level (SIL 1-4)</li>
 * <li>Probability of Failure on Demand (PFD)</li>
 * <li>Risk Reduction Factor (RRF = 1/PFD)</li>
 * </ul>
 *
 * <h2>SIL Levels and PFD Ranges</h2>
 * <table>
 * <caption>Safety Integrity Level Requirements</caption>
 * <tr>
 * <th>SIL</th>
 * <th>PFD Range</th>
 * <th>RRF Range</th>
 * </tr>
 * <tr>
 * <td>1</td>
 * <td>0.01 - 0.1</td>
 * <td>10 - 100</td>
 * </tr>
 * <tr>
 * <td>2</td>
 * <td>0.001 - 0.01</td>
 * <td>100 - 1,000</td>
 * </tr>
 * <tr>
 * <td>3</td>
 * <td>0.0001 - 0.001</td>
 * <td>1,000 - 10,000</td>
 * </tr>
 * <tr>
 * <td>4</td>
 * <td>0.00001 - 0.0001</td>
 * <td>10,000 - 100,000</td>
 * </tr>
 * </table>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>
 * {@code
 * SafetyInstrumentedFunction hipps = SafetyInstrumentedFunction.builder().name("HIPPS-001")
 *     .description("High Integrity Pipeline Protection System").sil(3).pfd(0.001)
 *     .protectedEquipment(Arrays.asList("Export Pipeline", "Riser"))
 *     .initiatingEvent("Overpressure").build();
 *
 * double unmitigatedFrequency = 0.1; // per year
 * double mitigatedFrequency = hipps.getMitigatedFrequency(unmitigatedFrequency);
 * // mitigatedFrequency = 0.0001 per year
 * }
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class SafetyInstrumentedFunction implements Serializable {

  private static final long serialVersionUID = 1000L;

  /** SIF identifier. */
  private String id;

  /** SIF name. */
  private String name;

  /** SIF description. */
  private String description;

  /** Safety Integrity Level (1-4). */
  private int sil;

  /** Probability of Failure on Demand (average). */
  private double pfdAvg;

  /** Test interval in hours. */
  private double testIntervalHours;

  /** Mean Time To Repair in hours. */
  private double mttr;

  /** Equipment protected by this SIF. */
  private List<String> protectedEquipment;

  /** Initiating event that triggers this SIF. */
  private String initiatingEvent;

  /** Safe state description. */
  private String safeState;

  /** SIF category (ESD, HIPPS, Fire and Gas, etc.). */
  private SIFCategory category;

  /** Architecture (1oo1, 1oo2, 2oo3, etc.). */
  private String architecture;

  /** Spurious trip rate per year. */
  private double spuriousTripRate;

  /** Last proof test date. */
  private java.util.Date lastProofTest;

  /** Notes and comments. */
  private String notes;

  /**
   * SIF categories.
   */
  public enum SIFCategory {
    /** Emergency Shutdown. */
    ESD("Emergency Shutdown"),
    /** High Integrity Pressure Protection. */
    HIPPS("High Integrity Pressure Protection"),
    /** Fire and Gas detection. */
    FIRE_GAS("Fire & Gas"),
    /** Blowdown system. */
    BLOWDOWN("Blowdown"),
    /** Process shutdown. */
    PSD("Process Shutdown"),
    /** Other safety function. */
    OTHER("Other");

    private final String description;

    SIFCategory(String description) {
      this.description = description;
    }

    public String getDescription() {
      return description;
    }
  }

  /**
   * Creates an empty SIF.
   */
  public SafetyInstrumentedFunction() {
    this.protectedEquipment = new ArrayList<>();
    this.category = SIFCategory.OTHER;
    this.architecture = "1oo1";
  }

  /**
   * Creates a SIF with basic parameters.
   *
   * @param name SIF name
   * @param sil Safety Integrity Level (1-4)
   * @param pfd Probability of Failure on Demand
   */
  public SafetyInstrumentedFunction(String name, int sil, double pfd) {
    this();
    this.name = name;
    this.sil = validateSil(sil);
    this.pfdAvg = validatePfd(pfd, sil);
  }

  /**
   * Creates a builder for SafetyInstrumentedFunction.
   *
   * @return new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder for SafetyInstrumentedFunction.
   */
  public static class Builder {
    private SafetyInstrumentedFunction sif = new SafetyInstrumentedFunction();

    public Builder id(String id) {
      sif.id = id;
      return this;
    }

    public Builder name(String name) {
      sif.name = name;
      return this;
    }

    public Builder description(String description) {
      sif.description = description;
      return this;
    }

    public Builder sil(int sil) {
      sif.sil = sif.validateSil(sil);
      return this;
    }

    public Builder pfd(double pfd) {
      sif.pfdAvg = pfd;
      return this;
    }

    public Builder testIntervalHours(double hours) {
      sif.testIntervalHours = hours;
      return this;
    }

    public Builder mttr(double hours) {
      sif.mttr = hours;
      return this;
    }

    public Builder protectedEquipment(List<String> equipment) {
      sif.protectedEquipment = new ArrayList<>(equipment);
      return this;
    }

    public Builder addProtectedEquipment(String equipment) {
      sif.protectedEquipment.add(equipment);
      return this;
    }

    public Builder initiatingEvent(String event) {
      sif.initiatingEvent = event;
      return this;
    }

    public Builder safeState(String state) {
      sif.safeState = state;
      return this;
    }

    public Builder category(SIFCategory category) {
      sif.category = category;
      return this;
    }

    public Builder architecture(String arch) {
      sif.architecture = arch;
      return this;
    }

    public Builder spuriousTripRate(double rate) {
      sif.spuriousTripRate = rate;
      return this;
    }

    public Builder notes(String notes) {
      sif.notes = notes;
      return this;
    }

    public SafetyInstrumentedFunction build() {
      if (sif.pfdAvg > 0 && sif.sil > 0) {
        sif.pfdAvg = sif.validatePfd(sif.pfdAvg, sif.sil);
      }
      return sif;
    }
  }

  // Validation methods

  private int validateSil(int sil) {
    if (sil < 1 || sil > 4) {
      throw new IllegalArgumentException("SIL must be between 1 and 4, got: " + sil);
    }
    return sil;
  }

  private double validatePfd(double pfd, int sil) {
    double[] minPfd = {0, 0.01, 0.001, 0.0001, 0.00001};
    double[] maxPfd = {1, 0.1, 0.01, 0.001, 0.0001};

    if (pfd < minPfd[sil] || pfd > maxPfd[sil]) {
      // Warning but don't fail
      System.err.printf("Warning: PFD %.2e outside SIL %d range [%.2e, %.2e]%n", pfd, sil,
          minPfd[sil], maxPfd[sil]);
    }
    return pfd;
  }

  // Risk calculation methods

  /**
   * Calculates the Risk Reduction Factor.
   *
   * @return RRF = 1/PFD
   */
  public double getRiskReductionFactor() {
    if (pfdAvg <= 0) {
      return Double.POSITIVE_INFINITY;
    }
    return 1.0 / pfdAvg;
  }

  /**
   * Calculates the mitigated frequency after SIF credit.
   *
   * @param unmitigatedFrequency base event frequency (per year)
   * @return mitigated frequency (per year)
   */
  public double getMitigatedFrequency(double unmitigatedFrequency) {
    return unmitigatedFrequency * pfdAvg;
  }

  /**
   * Calculates required SIF capability for target risk reduction.
   *
   * @param unmitigatedFrequency current frequency
   * @param targetFrequency target frequency
   * @return required PFD
   */
  public static double calculateRequiredPfd(double unmitigatedFrequency, double targetFrequency) {
    if (unmitigatedFrequency <= 0) {
      return 1.0;
    }
    return targetFrequency / unmitigatedFrequency;
  }

  /**
   * Determines required SIL for a target PFD.
   *
   * @param targetPfd target PFD
   * @return minimum SIL required
   */
  public static int getRequiredSil(double targetPfd) {
    if (targetPfd >= 0.01) {
      return 1;
    } else if (targetPfd >= 0.001) {
      return 2;
    } else if (targetPfd >= 0.0001) {
      return 3;
    } else {
      return 4;
    }
  }

  /**
   * Gets the maximum PFD allowed for a given SIL level.
   *
   * @param sil Safety Integrity Level (1-4)
   * @return maximum PFD for that SIL
   */
  public static double getMaxPfdForSil(int sil) {
    switch (sil) {
      case 1:
        return 0.1;
      case 2:
        return 0.01;
      case 3:
        return 0.001;
      case 4:
        return 0.0001;
      default:
        return 1.0;
    }
  }

  /**
   * Gets proof test interval in years.
   *
   * @return test interval in years
   */
  public double getProofTestIntervalYears() {
    return testIntervalHours / 8760.0;
  }

  /**
   * Calculates PFD from component failure rates (simplified 1oo1 model).
   *
   * @param lambdaDU dangerous undetected failure rate (per hour)
   * @param testInterval proof test interval (hours)
   * @return average PFD
   */
  public static double calculatePfd1oo1(double lambdaDU, double testInterval) {
    // PFDavg = λDU × TI / 2 (simplified formula for low failure rates)
    return lambdaDU * testInterval / 2.0;
  }

  /**
   * Calculates PFD for 1oo2 architecture.
   *
   * @param lambdaDU dangerous undetected failure rate per channel (per hour)
   * @param testInterval proof test interval (hours)
   * @return average PFD
   */
  public static double calculatePfd1oo2(double lambdaDU, double testInterval) {
    // PFDavg = (λDU × TI)² / 3 for 1oo2
    double lambdaTI = lambdaDU * testInterval;
    return (lambdaTI * lambdaTI) / 3.0;
  }

  /**
   * Calculates PFD for 2oo3 architecture.
   *
   * @param lambdaDU dangerous undetected failure rate per channel (per hour)
   * @param testInterval proof test interval (hours)
   * @return average PFD
   */
  public static double calculatePfd2oo3(double lambdaDU, double testInterval) {
    // PFDavg = (λDU × TI)² for 2oo3
    double lambdaTI = lambdaDU * testInterval;
    return lambdaTI * lambdaTI;
  }

  /**
   * Calculates availability including SIF spurious trips.
   *
   * @param baseAvailability baseline availability without spurious trips
   * @param mttrHours mean time to restore after spurious trip
   * @return availability accounting for spurious trips
   */
  public double getAvailabilityWithSpuriousTrips(double baseAvailability, double mttrHours) {
    if (spuriousTripRate <= 0) {
      return baseAvailability;
    }
    // Downtime per year from spurious trips
    double spuriousDowntime = spuriousTripRate * mttrHours;
    double totalHoursPerYear = 8760.0;
    double spuriousUnavailability = spuriousDowntime / totalHoursPerYear;
    return baseAvailability * (1.0 - spuriousUnavailability);
  }

  // Getters

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public int getSil() {
    return sil;
  }

  public double getPfdAvg() {
    return pfdAvg;
  }

  public double getTestIntervalHours() {
    return testIntervalHours;
  }

  public double getMttr() {
    return mttr;
  }

  public List<String> getProtectedEquipment() {
    return new ArrayList<>(protectedEquipment);
  }

  public String getInitiatingEvent() {
    return initiatingEvent;
  }

  public String getSafeState() {
    return safeState;
  }

  public SIFCategory getCategory() {
    return category;
  }

  public String getArchitecture() {
    return architecture;
  }

  public double getSpuriousTripRate() {
    return spuriousTripRate;
  }

  public String getNotes() {
    return notes;
  }

  // Setters

  public void setId(String id) {
    this.id = id;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setSil(int sil) {
    this.sil = validateSil(sil);
  }

  public void setPfdAvg(double pfd) {
    this.pfdAvg = pfd;
  }

  public void setTestIntervalHours(double hours) {
    this.testIntervalHours = hours;
  }

  public void setMttr(double hours) {
    this.mttr = hours;
  }

  public void setProtectedEquipment(List<String> equipment) {
    this.protectedEquipment = new ArrayList<>(equipment);
  }

  public void addProtectedEquipment(String equipment) {
    this.protectedEquipment.add(equipment);
  }

  public void setInitiatingEvent(String event) {
    this.initiatingEvent = event;
  }

  public void setSafeState(String state) {
    this.safeState = state;
  }

  public void setCategory(SIFCategory category) {
    this.category = category;
  }

  public void setArchitecture(String arch) {
    this.architecture = arch;
  }

  public void setSpuriousTripRate(double rate) {
    this.spuriousTripRate = rate;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  /**
   * Converts to map for JSON serialization.
   *
   * @return map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", id);
    map.put("name", name);
    map.put("description", description);
    map.put("category", category != null ? category.name() : null);

    // SIL/PFD data
    Map<String, Object> silData = new HashMap<>();
    silData.put("level", sil);
    silData.put("pfdAvg", pfdAvg);
    silData.put("rrf", getRiskReductionFactor());
    silData.put("architecture", architecture);
    map.put("silData", silData);

    // Testing
    Map<String, Object> testing = new HashMap<>();
    testing.put("testIntervalHours", testIntervalHours);
    testing.put("mttr", mttr);
    testing.put("spuriousTripRate", spuriousTripRate);
    map.put("testing", testing);

    // Protection scope
    Map<String, Object> protection = new HashMap<>();
    protection.put("equipment", protectedEquipment);
    protection.put("initiatingEvent", initiatingEvent);
    protection.put("safeState", safeState);
    map.put("protection", protection);

    map.put("notes", notes);

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
    return String.format("SIF[%s: SIL%d, PFD=%.2e, RRF=%.0f, protects=%d equipment]", name, sil,
        pfdAvg, getRiskReductionFactor(), protectedEquipment.size());
  }
}
