package neqsim.process.engineering.safety;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Declared normal, degraded, or maintenance state used for SIF demand-capability assessment. */
public final class SafetyFunctionOperatingMode implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Operational classification. */
  public enum ModeType {
    NORMAL, DEGRADED, MAINTENANCE
  }

  /** State of an individual voted channel. */
  public enum ChannelState {
    AVAILABLE, BYPASSED, DANGEROUS_FAILED, FORCED_TRIP, UNDER_REPAIR
  }

  private final String name;
  private final ModeType type;
  private final String authorizationReference;
  private final String compensatingMeasure;
  private final double maximumDurationHours;
  private final double elapsedDurationHours;
  private final Map<String, Map<Integer, ChannelState>> channelStates;
  private final Map<String, Double> hoursSinceProofTest;

  private SafetyFunctionOperatingMode(Builder builder) {
    name = requireText(builder.name, "name");
    type = builder.type;
    authorizationReference = normalize(builder.authorizationReference);
    compensatingMeasure = normalize(builder.compensatingMeasure);
    maximumDurationHours = builder.maximumDurationHours;
    elapsedDurationHours = nonNegative(builder.elapsedDurationHours, "elapsedDurationHours");
    Map<String, Map<Integer, ChannelState>> copiedStates = new LinkedHashMap<String, Map<Integer, ChannelState>>();
    for (Map.Entry<String, Map<Integer, ChannelState>> entry : builder.channelStates.entrySet()) {
      copiedStates.put(entry.getKey(),
          Collections.unmodifiableMap(new LinkedHashMap<Integer, ChannelState>(entry.getValue())));
    }
    channelStates = Collections.unmodifiableMap(copiedStates);
    hoursSinceProofTest = Collections
        .unmodifiableMap(new LinkedHashMap<String, Double>(builder.hoursSinceProofTest));
  }

  public static Builder builder(String name, ModeType type) {
    return new Builder(name, type);
  }

  public String getName() {
    return name;
  }

  public ModeType getType() {
    return type;
  }

  public String getAuthorizationReference() {
    return authorizationReference;
  }

  public String getCompensatingMeasure() {
    return compensatingMeasure;
  }

  public double getMaximumDurationHours() {
    return maximumDurationHours;
  }

  public double getElapsedDurationHours() {
    return elapsedDurationHours;
  }

  Map<Integer, ChannelState> getChannelStates(String subsystemName) {
    Map<Integer, ChannelState> states = channelStates.get(subsystemName);
    return states == null ? Collections.<Integer, ChannelState>emptyMap() : states;
  }

  Double getHoursSinceProofTest(String subsystemName) {
    return hoursSinceProofTest.get(subsystemName);
  }

  Map<String, Map<Integer, ChannelState>> getAllChannelStates() {
    return channelStates;
  }

  Map<String, Double> getAllProofTestAges() {
    return hoursSinceProofTest;
  }

  /** Builder for an operating mode snapshot. */
  public static final class Builder {
    private final String name;
    private final ModeType type;
    private String authorizationReference = "";
    private String compensatingMeasure = "";
    private double maximumDurationHours = Double.NaN;
    private double elapsedDurationHours;
    private final Map<String, Map<Integer, ChannelState>> channelStates = new LinkedHashMap<String, Map<Integer, ChannelState>>();
    private final Map<String, Double> hoursSinceProofTest = new LinkedHashMap<String, Double>();

    private Builder(String name, ModeType type) {
      if (type == null) {
        throw new IllegalArgumentException("type must not be null");
      }
      this.name = name;
      this.type = type;
    }

    public Builder authorizationReference(String value) {
      authorizationReference = requireText(value, "authorizationReference");
      return this;
    }

    public Builder compensatingMeasure(String value) {
      compensatingMeasure = requireText(value, "compensatingMeasure");
      return this;
    }

    public Builder duration(double elapsedHours, double maximumHours) {
      elapsedDurationHours = nonNegative(elapsedHours, "elapsedDurationHours");
      if (!Double.isFinite(maximumHours) || maximumHours <= 0.0) {
        throw new IllegalArgumentException("maximumDurationHours must be finite and positive");
      }
      maximumDurationHours = maximumHours;
      return this;
    }

    /**
     * Sets one channel state using a one-based channel index.
     *
     * @param subsystemName exact subsystem name from the safety design
     * @param channelIndex one-based channel number
     * @param state declared channel state
     * @return this builder
     */
    public Builder channelState(String subsystemName, int channelIndex, ChannelState state) {
      String normalized = requireText(subsystemName, "subsystemName");
      if (channelIndex < 1 || state == null) {
        throw new IllegalArgumentException("channelIndex must be positive and state must not be null");
      }
      Map<Integer, ChannelState> states = channelStates.get(normalized);
      if (states == null) {
        states = new LinkedHashMap<Integer, ChannelState>();
        channelStates.put(normalized, states);
      }
      states.put(Integer.valueOf(channelIndex), state);
      return this;
    }

    public Builder hoursSinceProofTest(String subsystemName, double hours) {
      hoursSinceProofTest.put(requireText(subsystemName, "subsystemName"),
          Double.valueOf(nonNegative(hours, "hoursSinceProofTest")));
      return this;
    }

    public SafetyFunctionOperatingMode build() {
      return new SafetyFunctionOperatingMode(this);
    }
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  private static double nonNegative(double value, String field) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(field + " must be finite and non-negative");
    }
    return value;
  }
}
