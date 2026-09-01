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

  /** IEC 61511 demand-mode classification used by the screening report. */
  public enum DemandMode {
    LOW_DEMAND, HIGH_OR_CONTINUOUS_DEMAND
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
    private double proofTestCoverage = 1.0;
    private double partialStrokeCoverage = 0.0;
    private double partialStrokeTestIntervalHours = Double.NaN;
    private double missionTimeHours = Double.NaN;
    private double bypassProbability = 0.0;
    private int systematicCapability = 0;
    private int hardwareFaultTolerance = -1;
    private String commonCauseGroup = "";
    private String certifiedDataReference = "";

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
      double mission = Double.isFinite(missionTimeHours) ? missionTimeHours : proofTestIntervalHours;
      double effectiveUndetectedInterval = proofTestCoverage * proofTestIntervalHours
          + (1.0 - proofTestCoverage) * mission;
      if (partialStrokeCoverage > 0.0 && Double.isFinite(partialStrokeTestIntervalHours)) {
        effectiveUndetectedInterval = partialStrokeCoverage * partialStrokeTestIntervalHours
            + (1.0 - partialStrokeCoverage) * effectiveUndetectedInterval;
      }
      double channelPfd = Math.min(1.0, lambdaDu * effectiveUndetectedInterval / 2.0 + lambdaDd * meanRepairTimeHours);
      double independent = 0.0;
      for (int functioning = 0; functioning < votesRequired; functioning++) {
        independent += combination(channelCount, functioning) * Math.pow(1.0 - channelPfd, functioning)
            * Math.pow(channelPfd, channelCount - functioning);
      }
      double votingPfd = betaFactor * channelPfd + (1.0 - betaFactor) * independent;
      return Math.min(1.0, votingPfd + (1.0 - votingPfd) * bypassProbability);
    }

    /** @return conservative dangerous-failure frequency screening value in 1/h */
    public double calculatePfh() {
      double lambdaDu = dangerousFailureRatePerHour * (1.0 - diagnosticCoverage);
      return Math.min(1.0, betaFactor * lambdaDu + (1.0 - betaFactor) * lambdaDu * channelCount);
    }

    public Subsystem setProofTestCoverage(double value) {
      requireFraction(value, "proofTestCoverage");
      proofTestCoverage = value;
      return this;
    }

    public Subsystem setPartialStrokeTesting(double intervalHours, double coverage) {
      requirePositive(intervalHours, "partialStrokeTestIntervalHours");
      requireFraction(coverage, "partialStrokeCoverage");
      partialStrokeTestIntervalHours = intervalHours;
      partialStrokeCoverage = coverage;
      return this;
    }

    public Subsystem setMissionTimeHours(double value) {
      requirePositive(value, "missionTimeHours");
      missionTimeHours = value;
      return this;
    }

    public Subsystem setBypassProbability(double value) {
      requireFraction(value, "bypassProbability");
      bypassProbability = value;
      return this;
    }

    public Subsystem setArchitecturalConstraints(int capability, int faultTolerance) {
      if (capability < 1 || capability > 4 || faultTolerance < 0) {
        throw new IllegalArgumentException("systematic capability must be 1 to 4 and fault tolerance non-negative");
      }
      systematicCapability = capability;
      hardwareFaultTolerance = faultTolerance;
      return this;
    }

    public Subsystem setCommonCauseGroup(String value) {
      commonCauseGroup = requireText(value, "commonCauseGroup");
      return this;
    }

    public Subsystem setCertifiedDataReference(String value) {
      certifiedDataReference = requireText(value, "certifiedDataReference");
      return this;
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
      map.put("pfhPerHour", calculatePfh());
      map.put("proofTestCoverage", proofTestCoverage);
      map.put("partialStrokeCoverage", partialStrokeCoverage);
      map.put("partialStrokeTestIntervalHours", partialStrokeTestIntervalHours);
      map.put("missionTimeHours", Double.isFinite(missionTimeHours) ? missionTimeHours : proofTestIntervalHours);
      map.put("bypassProbability", bypassProbability);
      map.put("systematicCapability", systematicCapability);
      map.put("hardwareFaultTolerance", hardwareFaultTolerance);
      map.put("commonCauseGroup", commonCauseGroup);
      map.put("certifiedDataReference", certifiedDataReference);
      return map;
    }

    public List<String> getMissingFields() {
      List<String> missing = new ArrayList<String>();
      if (systematicCapability == 0) {
        missing.add("systematicCapability");
      }
      if (hardwareFaultTolerance < 0) {
        missing.add("hardwareFaultTolerance");
      }
      if (channelCount > 1 && commonCauseGroup.isEmpty()) {
        missing.add("commonCauseGroup");
      }
      if (certifiedDataReference.isEmpty()) {
        missing.add("certifiedDataReference");
      }
      return missing;
    }

    public SubsystemType getType() {
      return type;
    }

    public String getName() {
      return name;
    }

    public int getVotesRequired() {
      return votesRequired;
    }

    public int getChannelCount() {
      return channelCount;
    }

    public double getDangerousFailureRatePerHour() {
      return dangerousFailureRatePerHour;
    }

    public double getDiagnosticCoverage() {
      return diagnosticCoverage;
    }

    public double getProofTestIntervalHours() {
      return proofTestIntervalHours;
    }

    public double getMeanRepairTimeHours() {
      return meanRepairTimeHours;
    }

    public double getBetaFactor() {
      return betaFactor;
    }

    public double getBypassProbability() {
      return bypassProbability;
    }

    /**
     * Copies the subsystem while replacing uncertain reliability inputs.
     *
     * @param dangerousFailureRate sampled dangerous failure rate in 1/h
     * @param coverage sampled diagnostic coverage
     * @param proofInterval sampled proof-test interval in hours
     * @param repairTime sampled mean repair time in hours
     * @param beta sampled common-cause beta factor
     * @param bypass sampled probability of bypass on demand
     * @return subsystem copy retaining proof-test, PST, mission, architecture, and evidence settings
     */
    public Subsystem copyWithReliabilityInputs(double dangerousFailureRate, double coverage, double proofInterval,
        double repairTime, double beta, double bypass) {
      Subsystem copy = new Subsystem(name, type, votesRequired, channelCount, dangerousFailureRate, coverage,
          proofInterval, repairTime, beta);
      copy.proofTestCoverage = proofTestCoverage;
      copy.partialStrokeCoverage = partialStrokeCoverage;
      copy.partialStrokeTestIntervalHours = partialStrokeTestIntervalHours;
      copy.missionTimeHours = missionTimeHours;
      copy.bypassProbability = bypass;
      copy.systematicCapability = systematicCapability;
      copy.hardwareFaultTolerance = hardwareFaultTolerance;
      copy.commonCauseGroup = commonCauseGroup;
      copy.certifiedDataReference = certifiedDataReference;
      return copy;
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
  private DemandMode demandMode = DemandMode.LOW_DEMAND;
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

  public SafetyFunctionDesign setDemandMode(DemandMode value) {
    if (value == null) {
      throw new IllegalArgumentException("demandMode must not be null");
    }
    demandMode = value;
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

  /** @return conservative total dangerous-failure frequency screening value in 1/h */
  public double calculatePfh() {
    double total = 0.0;
    for (Subsystem subsystem : subsystems) {
      total += subsystem.calculatePfh();
    }
    return total;
  }

  /** @return achieved SIL band from PFDavg or PFH according to the declared demand mode, or zero */
  public int getAchievedSil() {
    if (demandMode == DemandMode.HIGH_OR_CONTINUOUS_DEMAND) {
      double pfh = calculatePfh();
      if (pfh >= 1.0e-5 || pfh <= 0.0) {
        return 0;
      }
      if (pfh >= 1.0e-6) {
        return 1;
      }
      if (pfh >= 1.0e-7) {
        return 2;
      }
      if (pfh >= 1.0e-8) {
        return 3;
      }
      return pfh >= 1.0e-9 ? 4 : 0;
    }
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
    for (Subsystem subsystem : subsystems) {
      for (String field : subsystem.getMissingFields()) {
        missing.add(subsystem.name + ":" + field);
      }
    }
    return missing;
  }

  /** @return true when supplied systematic capability is at least the target SIL for every subsystem */
  public boolean areArchitecturalConstraintsMet() {
    if (subsystems.isEmpty()) {
      return false;
    }
    for (Subsystem subsystem : subsystems) {
      if (subsystem.systematicCapability < targetSil || subsystem.hardwareFaultTolerance < 0) {
        return false;
      }
    }
    return true;
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
    map.put("pfhPerHour", calculatePfh());
    map.put("demandMode", demandMode.name());
    map.put("verificationMeasure", demandMode == DemandMode.LOW_DEMAND ? "PFD_AVERAGE" : "PFH_PER_HOUR_SCREENING");
    map.put("riskReductionFactor", demandMode == DemandMode.LOW_DEMAND && pfd > 0.0 ? 1.0 / pfd : Double.NaN);
    map.put("targetMet", getAchievedSil() >= targetSil);
    map.put("architecturalConstraintsMet", areArchitecturalConstraintsMet());
    map.put("architecturalAssessmentNote", "The screen confirms declared systematic capability and that HFT was "
        + "recorded. Complete IEC 61511 route/type/SFF and independence assessment remains required.");
    map.put("lopaReference", lopaReference);
    map.put("srsReference", srsReference);
    map.put("safeState", safeState);
    map.put("approvalStatus", approvalStatus.name());
    map.put("subsystems", subsystemMaps);
    map.put("missingFields", getMissingFields());
    map.put("method", "IEC_61511_SCREENING_MOON_BETA_PROOF_TEST_PST_BYPASS");
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

  public DemandMode getDemandMode() {
    return demandMode;
  }
}
