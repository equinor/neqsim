package neqsim.process.operations;

import java.io.Serializable;
import java.util.Objects;
import neqsim.process.measurementdevice.InstrumentTagRole;

/**
 * Defines how a logical P&amp;ID or operating tag maps to NeqSim and plant data.
 *
 * <p>
 * A binding can point to an existing {@link neqsim.process.measurementdevice.MeasurementDeviceInterface}
 * tag, a {@link neqsim.process.automation.ProcessAutomation} address, or both. This lets agents and
 * applications keep private historian tag names outside public models while still running generic
 * operational studies.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class OperationalTagBinding implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String logicalTag;
  private final String historianTag;
  private final String pidReference;
  private final String automationAddress;
  private final String unit;
  private final InstrumentTagRole role;
  private final String description;

  /**
   * Creates a binding from a builder.
   *
   * @param builder builder containing validated binding fields
   */
  private OperationalTagBinding(Builder builder) {
    logicalTag = requireText(builder.logicalTag, "logicalTag");
    historianTag = clean(builder.historianTag);
    pidReference = clean(builder.pidReference);
    automationAddress = clean(builder.automationAddress);
    unit = clean(builder.unit);
    role = builder.role == null ? InstrumentTagRole.VIRTUAL : builder.role;
    description = clean(builder.description);
  }

  /**
   * Starts a builder for a new operational tag binding.
   *
   * @param logicalTag stable logical tag name used in public workflows and reports
   * @return builder instance
   */
  public static Builder builder(String logicalTag) {
    return new Builder(logicalTag);
  }

  /**
   * Returns the logical tag name.
   *
   * @return public logical tag name
   */
  public String getLogicalTag() {
    return logicalTag;
  }

  /**
   * Returns the private historian tag name, if configured.
   *
   * @return historian tag or empty string
   */
  public String getHistorianTag() {
    return historianTag;
  }

  /**
   * Returns the source P&amp;ID or document reference for this tag.
   *
   * @return P&amp;ID reference or empty string
   */
  public String getPidReference() {
    return pidReference;
  }

  /**
   * Returns the NeqSim automation address, if configured.
   *
   * @return automation address or empty string
   */
  public String getAutomationAddress() {
    return automationAddress;
  }

  /**
   * Returns the engineering unit used by this binding.
   *
   * @return unit string or empty string for the model default
   */
  public String getUnit() {
    return unit;
  }

  /**
   * Returns the tag role for field-data integration.
   *
   * @return input, benchmark, or virtual role
   */
  public InstrumentTagRole getRole() {
    return role;
  }

  /**
   * Returns a human-readable description.
   *
   * @return description or empty string
   */
  public String getDescription() {
    return description;
  }

  /**
   * Checks whether a historian tag is configured.
   *
   * @return true when the binding has a historian tag
   */
  public boolean hasHistorianTag() {
    return !historianTag.isEmpty();
  }

  /**
   * Checks whether a NeqSim automation address is configured.
   *
   * @return true when the binding has an automation address
   */
  public boolean hasAutomationAddress() {
    return !automationAddress.isEmpty();
  }

  /**
   * Cleans nullable text to a trimmed non-null value.
   *
   * @param text text to clean
   * @return trimmed text or empty string
   */
  private static String clean(String text) {
    return text == null ? "" : text.trim();
  }

  /**
   * Requires a non-empty text value.
   *
   * @param text text to validate
   * @param fieldName field name used in error messages
   * @return trimmed text
   * @throws IllegalArgumentException if the text is null or empty
   */
  private static String requireText(String text, String fieldName) {
    String value = clean(text);
    if (value.isEmpty()) {
      throw new IllegalArgumentException(fieldName + " must not be empty");
    }
    return value;
  }

  /** Builder for {@link OperationalTagBinding}. */
  public static final class Builder {
    private final String logicalTag;
    private String historianTag = "";
    private String pidReference = "";
    private String automationAddress = "";
    private String unit = "";
    private InstrumentTagRole role = InstrumentTagRole.VIRTUAL;
    private String description = "";

    /**
     * Creates a builder.
     *
     * @param logicalTag stable logical tag name
     */
    private Builder(String logicalTag) {
      this.logicalTag = logicalTag;
    }

    /**
     * Sets the historian tag used by the plant-data source.
     *
     * @param historianTag historian tag name, or empty when unavailable
     * @return this builder
     */
    public Builder historianTag(String historianTag) {
      this.historianTag = historianTag;
      return this;
    }

    /**
     * Sets the P&amp;ID or document reference.
     *
     * @param pidReference source reference such as a symbol tag or line identifier
     * @return this builder
     */
    public Builder pidReference(String pidReference) {
      this.pidReference = pidReference;
      return this;
    }

    /**
     * Sets the NeqSim automation address.
     *
     * @param automationAddress address such as {@code "Valve.percentValveOpening"}
     * @return this builder
     */
    public Builder automationAddress(String automationAddress) {
      this.automationAddress = automationAddress;
      return this;
    }

    /**
     * Sets the engineering unit.
     *
     * @param unit unit string accepted by the target NeqSim variable
     * @return this builder
     */
    public Builder unit(String unit) {
      this.unit = unit;
      return this;
    }

    /**
     * Sets the integration role.
     *
     * @param role input, benchmark, or virtual role
     * @return this builder
     */
    public Builder role(InstrumentTagRole role) {
      this.role = role;
      return this;
    }

    /**
     * Sets a human-readable description.
     *
     * @param description binding description
     * @return this builder
     */
    public Builder description(String description) {
      this.description = description;
      return this;
    }

    /**
     * Builds the immutable binding.
     *
     * @return operational tag binding
     */
    public OperationalTagBinding build() {
      return new OperationalTagBinding(this);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof OperationalTagBinding)) {
      return false;
    }
    OperationalTagBinding other = (OperationalTagBinding) obj;
    return logicalTag.equals(other.logicalTag);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(logicalTag);
  }
}