package neqsim.process.safety.release;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Immutable calculation result. Numerical validity never implies engineering qualification. */
public final class ReleaseFlowResult implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Calculation outcome, separate from benchmark or engineering evidence. */
  public enum Status {
    /** Required numerical checks passed. */
    VALID,
    /** Calculation succeeded with an optional diagnostic unavailable. */
    VALID_WITH_WARNINGS,
    /** Required input, property, or closure check failed. */
    INVALID,
    /** Requested physics is not represented. */
    UNSUPPORTED
  }

  /** Thermodynamic location; the short-opening model explicitly aliases throat and exit. */
  public enum Station {
    /** Upstream stagnation state. */
    UPSTREAM_STAGNATION,
    /** Critical state or back-pressure boundary for unchoked flow. */
    THROAT_CRITICAL,
    /** Physical opening, aliased to throat for a zero-length orifice. */
    ORIFICE_EXIT,
    /** Ideal isentropic receiving-pressure state, before mixing or shocks. */
    AMBIENT_EXPANDED
  }

  /** Stable machine code and explanatory detail. */
  public static final class Diagnostic implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String code;
    private final String message;

    /**
     * Creates a diagnostic.
     * 
     * @param code stable machine-readable code
     * @param message engineering detail
     */
    public Diagnostic(String code, String message) {
      if (code == null || code.trim().isEmpty() || message == null) {
        throw new IllegalArgumentException("Diagnostic code and message required");
      }
      this.code = code;
      this.message = message;
    }

    /** @return stable code */
    public String getCode() {
      return code;
    }

    /** @return explanatory detail */
    public String getMessage() {
      return message;
    }
  }

  private final String modelId;
  private final String modelVersion;
  private final Status status;
  private final double massFlowRateKgS;
  private final boolean choked;
  private final Map<Station, ReleaseState> stations;
  private final List<Diagnostic> diagnostics;
  private final Double throatSoundSpeedMs;

  private ReleaseFlowResult(String modelId, String version, Status status, double rate, boolean choked,
      Map<Station, ReleaseState> stations, List<Diagnostic> diagnostics, Double soundSpeed) {
    if (modelId == null || modelId.trim().isEmpty() || version == null || version.trim().isEmpty() || status == null
        || stations == null || diagnostics == null || diagnostics.contains(null)) {
      throw new IllegalArgumentException("Model identity, status, stations and diagnostics required");
    }
    boolean usable = status == Status.VALID || status == Status.VALID_WITH_WARNINGS;
    if (usable && (!Double.isFinite(rate) || rate < 0.0 || !stations.containsKey(Station.UPSTREAM_STAGNATION)
        || !stations.containsKey(Station.THROAT_CRITICAL) || !stations.containsKey(Station.ORIFICE_EXIT))) {
      throw new IllegalArgumentException("Successful results require finite rate and resolved opening states");
    }
    if (stations.containsValue(null) || (soundSpeed != null && (!Double.isFinite(soundSpeed) || soundSpeed <= 0.0))) {
      throw new IllegalArgumentException("Null stations or invalid acoustic speed");
    }
    this.modelId = modelId;
    modelVersion = version;
    this.status = status;
    massFlowRateKgS = rate;
    this.choked = choked;
    EnumMap<Station, ReleaseState> copy = new EnumMap<Station, ReleaseState>(Station.class);
    copy.putAll(stations);
    this.stations = Collections.unmodifiableMap(copy);
    this.diagnostics = Collections.unmodifiableList(new ArrayList<Diagnostic>(diagnostics));
    throatSoundSpeedMs = soundSpeed;
  }

  /**
   * Creates a checked successful result for a model implementation.
   * 
   * @param model model identity
   * @param rate release rate in kg/s
   * @param choked true for an interior critical maximum
   * @param stations resolved station snapshots
   * @param diagnostics assumption and warning records
   * @param soundSpeed optional throat equilibrium sound speed in m/s
   * @param warning whether optional diagnostics failed
   * @return immutable successful result
   */
  public static ReleaseFlowResult success(ReleaseFlowModel model, double rate, boolean choked,
      Map<Station, ReleaseState> stations, List<Diagnostic> diagnostics, Double soundSpeed, boolean warning) {
    return new ReleaseFlowResult(model.getModelId(), model.getModelVersion(),
        warning ? Status.VALID_WITH_WARNINGS : Status.VALID, rate, choked, stations, diagnostics, soundSpeed);
  }

  /**
   * Creates an unusable result with no numeric release payload.
   * 
   * @param model model identity
   * @param unsupported true when the regime is outside model scope
   * @param code diagnostic code
   * @param message diagnostic detail
   * @return immutable failure result
   */
  public static ReleaseFlowResult failure(ReleaseFlowModel model, boolean unsupported, String code, String message) {
    return new ReleaseFlowResult(model.getModelId(), model.getModelVersion(),
        unsupported ? Status.UNSUPPORTED : Status.INVALID, Double.NaN, false,
        new EnumMap<Station, ReleaseState>(Station.class),
        Collections.singletonList(new Diagnostic(code, message == null ? code : message)), null);
  }

  /** @return whether physical quantities are usable within the documented model assumptions */
  public boolean isUsable() {
    return status == Status.VALID || status == Status.VALID_WITH_WARNINGS;
  }

  /** @return model identifier */
  public String getModelId() {
    return modelId;
  }

  /** @return semantic model version */
  public String getModelVersion() {
    return modelVersion;
  }

  /** @return calculation status */
  public Status getStatus() {
    return status;
  }

  /**
   * Returns the release rate; failed calculations cannot masquerade as zero flow.
   * 
   * @return mass flow in kg/s
   * @throws IllegalStateException if the result is unusable
   */
  public double getMassFlowRateKgS() {
    if (!isUsable()) {
      throw new IllegalStateException("Unusable release result: " + status);
    }
    return massFlowRateKgS;
  }

  /** @return whether the accepted flow is choked */
  public boolean isChoked() {
    return choked;
  }

  /** @return immutable station map; empty on failure */
  public Map<Station, ReleaseState> getStations() {
    return stations;
  }

  /** @return immutable diagnostics */
  public List<Diagnostic> getDiagnostics() {
    return diagnostics;
  }

  /** @return equilibrium throat sound speed in m/s, or null when unavailable */
  public Double getThroatSoundSpeedMs() {
    return throatSoundSpeedMs;
  }
}
