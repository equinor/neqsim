package neqsim.process.engineering;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Review-governed IEC 61511 low-demand SIF architecture and PFD screening model.
 *
 * <p>
 * The SIL target and reliability data are external engineering inputs. NeqSim calculates a transparent screening PFDavg
 * for sensor, logic-solver and final-element subsystems, including MooN voting and a beta-factor common-cause term. The
 * result supports verification planning; it is not a certified SIL verification or an inferred SIL target.
 * </p>
 */
public final class SafetyFunctionDesign implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** SIF subsystem type. */
  public enum SubsystemType {
    SENSOR, LOGIC_SOLVER, FINAL_ELEMENT
  }

  /** Reliability and voting inputs for one homogeneous subsystem. */
  public static final class Subsystem implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final SubsystemType type;
    private final int votesRequired;
    private final int channelCount;
    private final double dangerousFailureRatePerHour;
    private final double diagnosticCoverage;
    private final double proofTestIntervalHours;
    private final double meanRepairTimeHours;
    private final double betaFactor;

    public Subsystem(String name, SubsystemType type, int votesRequired, int channelCount,
        double dangerousFailureRatePerHour, double diagnosticCoverage, double proofTestIntervalHours,
        double meanRepairTimeHours, double betaFactor) {
      if (name == null || name.trim().isEmpty() || type == null) {
        throw new IllegalArgumentException("subsystem name and type are required");
      }
      if (votesRequired < 1 || channelCount < votesRequired) {
        throw new IllegalArgumentException("voting must satisfy 1 <= M <= N");
      }
      requireFraction(diagnosticCoverage, "diagnosticCoverage");
      requireFraction(betaFactor, "betaFactor");
      requirePositive(dangerousFailureRatePerHour, "dangerousFailureRatePerHour");
      requirePositive(proofTestIntervalHours, "proofTestIntervalHours");
      requirePositive(meanRepairTimeHours, "meanRepairTimeHours");
      this.name = name.trim();
      this.type = type;
      this.votesRequired = votesRequired;
      this.channelCount = channelCount;
      this.dangerousFailureRatePerHour = dangerousFailureRatePerHour;
      this.diagnosticCoverage = diagnosticCoverage;
      this.proofTestIntervalHours = proofTestIntervalHours;
      this.meanRepairTimeHours = meanRepairTimeHours;
      this.betaFactor = betaFactor;
    }

    private static void requirePositive(double value, String name) {
      if (!Double.isFinite(value) || value <= 0.0) {
        throw new IllegalArgumentException(name + " must be positive");
      }
    }

    private static void requireFraction(double value, String name) {
      if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
        throw new IllegalArgumentException(name + " must be in [0, 1]");
      }
    }

    /** @return screening PFDavg for the subsystem */
    public double calculatePfdAverage() {
      double lambdaDu = dangerousFailureRatePerHour * (1.0 - diagnosticCoverage);
      double lambdaDd = dangerousFailureRatePerHour * diagnosticCoverage;
      double channelPfd = Math.min(1.0, lambdaDu * proofTestIntervalHours / 2.0 + lambdaDd * meanRepairTimeHours);
      double independent = 0.0;
      for (int functioning = 0; functioning < votesRequired; functioning++) {
        independent += combination(channelCount, functioning) * Math.pow(1.0 - channelPfd, functioning)
            * Math.pow(channelPfd, channelCount - functioning);
      }
      return Math.min(1.0, betaFactor * channelPfd + (1.0 - betaFactor) * independent);
    }

    private static long combination(int n, int k) {
      int effectiveK = Math.min(k, n - k);
      long result = 1L;
      for (int i = 1; i <= effectiveK; i++) {
        result = result * (n - effectiveK + i) / i;
      }
      return result;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("name", name);
      map.put("type", type.name());
      map.put("votingArchitecture", votesRequired + "oo" + channelCount);
      map.put("dangerousFailureRatePerHour", dangerousFailureRatePerHour);
      map.put("diagnosticCoverage", diagnosticCoverage);
      map.put("proofTestIntervalHours", proofTestIntervalHours);
      map.put("meanRepairTimeHours", meanRepairTimeHours);
      map.put("betaFactor", betaFactor);
      map.put("pfdAverage", calculatePfdAverage());
      return map;
    }

    public SubsystemType getType() {
      return type;
    }

    public String getVotingArchitecture() {
      return votesRequired + "oo" + channelCount;
    }
  }

  private final String sifTag;
  private final String requirementId;
  private final int targetSil;
  private final List<Subsystem> subsystems = new ArrayList<Subsystem>();
  private String lopaReference = "";
  private String srsReference = "";
  private String safeState = "";
  private EngineeringApprovalStatus approvalStatus = EngineeringApprovalStatus.REVIEW_REQUIRED;

  public SafetyFunctionDesign(String sifTag, String requirementId, int targetSil) {
    if (sifTag == null || sifTag.trim().isEmpty() || requirementId == null || requirementId.trim().isEmpty()) {
      throw new IllegalArgumentException("sifTag and requirementId are required");
    }
    if (targetSil < 1 || targetSil > 4) {
      throw new IllegalArgumentException("targetSil must be 1 to 4");
    }
    this.sifTag = sifTag.trim();
    this.requirementId = requirementId.trim();
    this.targetSil = targetSil;
  }

  public SafetyFunctionDesign addSubsystem(Subsystem subsystem) {
    if (subsystem == null) {
      throw new IllegalArgumentException("subsystem must not be null");
    }
    subsystems.add(subsystem);
    return this;
  }

  public SafetyFunctionDesign setLopaReference(String value) {
    lopaReference = requireText(value, "lopaReference");
    return this;
  }

  public SafetyFunctionDesign setSrsReference(String value) {
    srsReference = requireText(value, "srsReference");
    return this;
  }

  public SafetyFunctionDesign setSafeState(String value) {
    safeState = requireText(value, "safeState");
    return this;
  }

  public SafetyFunctionDesign approve(String record) {
    setSrsReference(record);
    approvalStatus = EngineeringApprovalStatus.APPROVED;
    return this;
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }

  /** @return total low-demand PFDavg assuming subsystem independence */
  public double calculatePfdAverage() {
    double success = 1.0;
    for (Subsystem subsystem : subsystems) {
      success *= 1.0 - subsystem.calculatePfdAverage();
    }
    return 1.0 - success;
  }

  /** @return achieved SIL band from calculated PFDavg, or zero */
  public int getAchievedSil() {
    double pfd = calculatePfdAverage();
    if (pfd >= 1.0e-1 || pfd <= 0.0) {
      return 0;
    }
    if (pfd >= 1.0e-2) {
      return 1;
    }
    if (pfd >= 1.0e-3) {
      return 2;
    }
    if (pfd >= 1.0e-4) {
      return 3;
    }
    return 4;
  }

  public List<String> getMissingFields() {
    List<String> missing = new ArrayList<String>();
    if (lopaReference.isEmpty()) {
      missing.add("lopaReference");
    }
    if (srsReference.isEmpty()) {
      missing.add("srsReference");
    }
    if (safeState.isEmpty()) {
      missing.add("safeState");
    }
    for (SubsystemType type : SubsystemType.values()) {
      boolean found = false;
      for (Subsystem subsystem : subsystems) {
        found = found || subsystem.type == type;
      }
      if (!found) {
        missing.add(type.name().toLowerCase() + "Subsystem");
      }
    }
    return missing;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    List<Map<String, Object>> subsystemMaps = new ArrayList<Map<String, Object>>();
    for (Subsystem subsystem : subsystems) {
      subsystemMaps.add(subsystem.toMap());
    }
    double pfd = calculatePfdAverage();
    map.put("sifTag", sifTag);
    map.put("requirementId", requirementId);
    map.put("targetSil", targetSil);
    map.put("achievedSil", getAchievedSil());
    map.put("pfdAverage", pfd);
    map.put("riskReductionFactor", pfd > 0.0 ? 1.0 / pfd : Double.POSITIVE_INFINITY);
    map.put("targetMet", getAchievedSil() >= targetSil);
    map.put("lopaReference", lopaReference);
    map.put("srsReference", srsReference);
    map.put("safeState", safeState);
    map.put("approvalStatus", approvalStatus.name());
    map.put("subsystems", subsystemMaps);
    map.put("missingFields", getMissingFields());
    map.put("method", "IEC_61511_LOW_DEMAND_SCREENING_MOON_BETA_FACTOR");
    return map;
  }

  public String getSifTag() {
    return sifTag;
  }

  public String getRequirementId() {
    return requirementId;
  }

  public int getTargetSil() {
    return targetSil;
  }

  public List<Subsystem> getSubsystems() {
    return Collections.unmodifiableList(subsystems);
  }

  public EngineeringApprovalStatus getApprovalStatus() {
    return approvalStatus;
  }
}
