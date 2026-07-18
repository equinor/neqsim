package neqsim.process.engineering.safety;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.SafetyFunctionDesign;
import neqsim.process.engineering.safety.SafetyFunctionOperatingMode.ChannelState;
import neqsim.process.engineering.safety.SafetyFunctionOperatingMode.ModeType;

/** Evaluates effective voting and governance gaps for a declared SIF operating mode. */
public final class SafetyFunctionDegradedModeAssessment {
  private SafetyFunctionDegradedModeAssessment() {
  }

  /** Effective voting result for one SIF subsystem. */
  public static final class SubsystemResult implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String subsystemName;
    private final String designArchitecture;
    private final String effectiveArchitecture;
    private final int availableChannels;
    private final int unavailableChannels;
    private final int forcedTripChannels;
    private final boolean demandCapable;
    private final boolean tripAlreadyDemanded;
    private final boolean proofTestAgeRecorded;
    private final boolean proofTestOverdue;

    private SubsystemResult(SafetyFunctionDesign.Subsystem subsystem, SafetyFunctionOperatingMode mode) {
      subsystemName = subsystem.getName();
      designArchitecture = subsystem.getVotingArchitecture();
      Map<Integer, ChannelState> states = mode.getChannelStates(subsystemName);
      int available = 0;
      int forced = 0;
      for (int channel = 1; channel <= subsystem.getChannelCount(); channel++) {
        ChannelState state = states.containsKey(Integer.valueOf(channel))
            ? states.get(Integer.valueOf(channel)) : ChannelState.AVAILABLE;
        if (state == ChannelState.AVAILABLE) {
          available++;
        } else if (state == ChannelState.FORCED_TRIP) {
          forced++;
        }
      }
      availableChannels = available;
      forcedTripChannels = forced;
      unavailableChannels = subsystem.getChannelCount() - available - forced;
      int remainingVotes = Math.max(0, subsystem.getVotesRequired() - forced);
      demandCapable = available >= remainingVotes;
      tripAlreadyDemanded = forced >= subsystem.getVotesRequired();
      effectiveArchitecture = remainingVotes + "oo" + available;
      Double proofAge = mode.getHoursSinceProofTest(subsystemName);
      proofTestAgeRecorded = proofAge != null;
      proofTestOverdue = proofAge != null
          && proofAge.doubleValue() > subsystem.getProofTestIntervalHours() + 1.0e-9;
    }

    public String getEffectiveArchitecture() {
      return effectiveArchitecture;
    }

    public boolean isDemandCapable() {
      return demandCapable;
    }

    public boolean isProofTestOverdue() {
      return proofTestOverdue;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("subsystemName", subsystemName);
      map.put("designArchitecture", designArchitecture);
      map.put("effectiveArchitecture", effectiveArchitecture);
      map.put("availableChannels", Integer.valueOf(availableChannels));
      map.put("unavailableChannels", Integer.valueOf(unavailableChannels));
      map.put("forcedTripChannels", Integer.valueOf(forcedTripChannels));
      map.put("demandCapable", Boolean.valueOf(demandCapable));
      map.put("tripAlreadyDemanded", Boolean.valueOf(tripAlreadyDemanded));
      map.put("proofTestAgeRecorded", Boolean.valueOf(proofTestAgeRecorded));
      map.put("proofTestOverdue", Boolean.valueOf(proofTestOverdue));
      return map;
    }
  }

  /** Immutable overall mode assessment. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String sifTag;
    private final SafetyFunctionOperatingMode mode;
    private final List<SubsystemResult> subsystemResults;
    private final List<String> findings;
    private final boolean demandCapable;
    private final boolean targetSilClaimPreserved;
    private final String verdict;

    private Result(SafetyFunctionDesign design, SafetyFunctionOperatingMode mode,
        List<SubsystemResult> subsystemResults, List<String> findings) {
      sifTag = design.getSifTag();
      this.mode = mode;
      this.subsystemResults = Collections
          .unmodifiableList(new ArrayList<SubsystemResult>(subsystemResults));
      this.findings = Collections.unmodifiableList(new ArrayList<String>(findings));
      boolean capable = true;
      boolean tripDemanded = false;
      boolean allCurrent = true;
      boolean fullArchitecture = true;
      for (SubsystemResult result : subsystemResults) {
        capable = capable && result.demandCapable;
        tripDemanded = tripDemanded || result.tripAlreadyDemanded;
        allCurrent = allCurrent && result.proofTestAgeRecorded && !result.proofTestOverdue;
        fullArchitecture = fullArchitecture && result.unavailableChannels == 0 && result.forcedTripChannels == 0;
      }
      demandCapable = capable;
      targetSilClaimPreserved = mode.getType() == ModeType.NORMAL && capable && !tripDemanded && allCurrent
          && fullArchitecture && design.getAchievedSil() >= design.getTargetSil();
      if (!capable) {
        verdict = "NOT_DEMAND_CAPABLE";
      } else if (tripDemanded) {
        verdict = "TRIP_ALREADY_DEMANDED";
      } else if (!findings.isEmpty()) {
        verdict = "RESTRICTED_ENGINEERING_REVIEW";
      } else {
        verdict = "REVIEW_REQUIRED";
      }
    }

    public boolean isDemandCapable() {
      return demandCapable;
    }

    public boolean isTargetSilClaimPreserved() {
      return targetSilClaimPreserved;
    }

    public String getVerdict() {
      return verdict;
    }

    public List<SubsystemResult> getSubsystemResults() {
      return subsystemResults;
    }

    public List<String> getFindings() {
      return findings;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("schemaVersion", "sif_degraded_mode_assessment.v1");
      map.put("sifTag", sifTag);
      map.put("modeName", mode.getName());
      map.put("modeType", mode.getType().name());
      map.put("authorizationReference", mode.getAuthorizationReference());
      map.put("compensatingMeasure", mode.getCompensatingMeasure());
      map.put("maximumDurationHours", Double.valueOf(mode.getMaximumDurationHours()));
      map.put("elapsedDurationHours", Double.valueOf(mode.getElapsedDurationHours()));
      List<Map<String, Object>> subsystems = new ArrayList<Map<String, Object>>();
      for (SubsystemResult result : subsystemResults) {
        subsystems.add(result.toMap());
      }
      map.put("subsystems", subsystems);
      map.put("findings", new ArrayList<String>(findings));
      map.put("demandCapable", Boolean.valueOf(demandCapable));
      map.put("targetSilClaimPreserved", Boolean.valueOf(targetSilClaimPreserved));
      map.put("verdict", verdict);
      map.put("silTargetInferred", Boolean.FALSE);
      map.put("engineeringApprovalRequired", Boolean.TRUE);
      return map;
    }
  }

  /**
   * Assesses a declared operating mode against the design voting architectures and proof-test intervals.
   *
   * @param design SIF design basis
   * @param mode current or proposed operating mode
   * @return demand-capability and governance findings
   */
  public static Result assess(SafetyFunctionDesign design, SafetyFunctionOperatingMode mode) {
    if (design == null || mode == null) {
      throw new IllegalArgumentException("design and mode are required");
    }
    validateReferences(design, mode);
    List<SubsystemResult> subsystemResults = new ArrayList<SubsystemResult>();
    List<String> findings = new ArrayList<String>();
    for (SafetyFunctionDesign.Subsystem subsystem : design.getSubsystems()) {
      SubsystemResult result = new SubsystemResult(subsystem, mode);
      subsystemResults.add(result);
      if (!result.demandCapable) {
        findings.add(subsystem.getName() + ": insufficient available channels for the configured vote");
      }
      if (result.unavailableChannels > 0) {
        findings.add(subsystem.getName() + ": design voting is degraded to " + result.effectiveArchitecture);
      }
      if (result.forcedTripChannels > 0) {
        findings.add(subsystem.getName() + ": forced-trip channel reduces spurious-trip tolerance");
      }
      if (!result.proofTestAgeRecorded) {
        findings.add(subsystem.getName() + ": proof-test age is not recorded");
      } else if (result.proofTestOverdue) {
        findings.add(subsystem.getName() + ": proof-test interval is overdue");
      }
    }
    if (mode.getType() != ModeType.NORMAL) {
      if (mode.getAuthorizationReference().isEmpty()) {
        findings.add("non-normal operation requires an authorization reference");
      }
      if (mode.getCompensatingMeasure().isEmpty()) {
        findings.add("non-normal operation requires a compensating measure");
      }
      if (!Double.isFinite(mode.getMaximumDurationHours())) {
        findings.add("non-normal operation requires a maximum duration");
      } else if (mode.getElapsedDurationHours() > mode.getMaximumDurationHours() + 1.0e-9) {
        findings.add("authorized non-normal duration has been exceeded");
      }
    }
    return new Result(design, mode, subsystemResults, findings);
  }

  private static void validateReferences(SafetyFunctionDesign design, SafetyFunctionOperatingMode mode) {
    Map<String, SafetyFunctionDesign.Subsystem> known = new LinkedHashMap<String, SafetyFunctionDesign.Subsystem>();
    for (SafetyFunctionDesign.Subsystem subsystem : design.getSubsystems()) {
      known.put(subsystem.getName(), subsystem);
    }
    for (Map.Entry<String, Map<Integer, ChannelState>> entry : mode.getAllChannelStates().entrySet()) {
      SafetyFunctionDesign.Subsystem subsystem = known.get(entry.getKey());
      if (subsystem == null) {
        throw new IllegalArgumentException("operating mode references unknown subsystem " + entry.getKey());
      }
      for (Integer channel : entry.getValue().keySet()) {
        if (channel.intValue() > subsystem.getChannelCount()) {
          throw new IllegalArgumentException("channel index exceeds subsystem channel count");
        }
      }
    }
    for (String subsystemName : mode.getAllProofTestAges().keySet()) {
      if (!known.containsKey(subsystemName)) {
        throw new IllegalArgumentException("proof-test age references unknown subsystem " + subsystemName);
      }
    }
  }
}
