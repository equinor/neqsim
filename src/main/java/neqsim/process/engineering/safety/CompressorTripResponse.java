package neqsim.process.engineering.safety;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.equipment.compressor.AntiSurge;

/** Snapshot linking anti-surge demand detection to observed compressor-trip evidence. */
public final class CompressorTripResponse implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String compressorTag;
  private final int surgeCycleCount;
  private final int tripCycleThreshold;
  private final double antiSurgeValvePosition;
  private final boolean tripRequired;
  private final boolean tripObserved;
  private final Double tripResponseTimeSeconds;
  private final double allowableResponseTimeSeconds;
  private final String evidenceReference;

  private CompressorTripResponse(String compressorTag, AntiSurge antiSurge, boolean tripObserved,
      Double tripResponseTimeSeconds, double allowableResponseTimeSeconds, String evidenceReference) {
    this.compressorTag = requireText(compressorTag, "compressorTag");
    if (antiSurge == null) {
      throw new IllegalArgumentException("antiSurge is required");
    }
    surgeCycleCount = antiSurge.getSurgeCycleCount();
    tripCycleThreshold = antiSurge.getMaxSurgeCyclesBeforeTrip();
    antiSurgeValvePosition = antiSurge.getValvePosition();
    tripRequired = antiSurge.shouldTrip();
    this.tripObserved = tripObserved;
    if (tripResponseTimeSeconds != null
        && (!Double.isFinite(tripResponseTimeSeconds.doubleValue()) || tripResponseTimeSeconds.doubleValue() < 0.0)) {
      throw new IllegalArgumentException("tripResponseTimeSeconds must be finite and non-negative");
    }
    this.tripResponseTimeSeconds = tripResponseTimeSeconds;
    if (!Double.isFinite(allowableResponseTimeSeconds) || allowableResponseTimeSeconds <= 0.0) {
      throw new IllegalArgumentException("allowableResponseTimeSeconds must be finite and positive");
    }
    this.allowableResponseTimeSeconds = allowableResponseTimeSeconds;
    this.evidenceReference = evidenceReference == null ? "" : evidenceReference.trim();
  }

  /**
   * Captures the anti-surge state and the independently observed trip response.
   *
   * @param compressorTag controlled compressor tag
   * @param antiSurge compressor anti-surge model after scenario execution
   * @param tripObserved whether the compressor trip was observed
   * @param tripResponseTimeSeconds observed time from trip demand to trip, or null when not observed
   * @param allowableResponseTimeSeconds maximum response time from the controlled requirement
   * @param evidenceReference controlled trip-logic or test evidence reference
   * @return immutable response snapshot
   */
  public static CompressorTripResponse capture(String compressorTag, AntiSurge antiSurge, boolean tripObserved,
      Double tripResponseTimeSeconds, double allowableResponseTimeSeconds, String evidenceReference) {
    return new CompressorTripResponse(compressorTag, antiSurge, tripObserved, tripResponseTimeSeconds,
        allowableResponseTimeSeconds, evidenceReference);
  }

  /** @return whether the observed response satisfies the trip demand and deadline */
  public boolean isPassed() {
    if (!tripRequired) {
      return true;
    }
    return tripObserved && tripResponseTimeSeconds != null
        && tripResponseTimeSeconds.doubleValue() <= allowableResponseTimeSeconds;
  }

  /** @return whether controlled evidence is present */
  public boolean isEvidenceComplete() {
    return !evidenceReference.isEmpty();
  }

  /** @return controlled compressor tag */
  public String getCompressorTag() {
    return compressorTag;
  }

  /** @return structured trip-response evidence */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("compressorTag", compressorTag);
    result.put("surgeCycleCount", Integer.valueOf(surgeCycleCount));
    result.put("tripCycleThreshold", Integer.valueOf(tripCycleThreshold));
    result.put("antiSurgeValvePosition", Double.valueOf(antiSurgeValvePosition));
    result.put("tripRequired", Boolean.valueOf(tripRequired));
    result.put("tripObserved", Boolean.valueOf(tripObserved));
    result.put("tripResponseTimeSeconds", tripResponseTimeSeconds);
    result.put("allowableResponseTimeSeconds", Double.valueOf(allowableResponseTimeSeconds));
    result.put("passed", Boolean.valueOf(isPassed()));
    result.put("evidenceReference", evidenceReference);
    return result;
  }

  private static String requireText(String value, String field) {
    String normalized = value == null ? "" : value.trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return normalized;
  }
}
