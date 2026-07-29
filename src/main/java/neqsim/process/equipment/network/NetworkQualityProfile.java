package neqsim.process.equipment.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Named, point-specific gas or oil quality specification.
 */
public class NetworkQualityProfile implements Serializable {
  private static final long serialVersionUID = 1000L;

  private String name;
  private String version;
  private String effectiveFrom;
  private String effectiveTo;
  private String provenance;
  private final List<String> namedExceptions = new ArrayList<String>();
  private final List<NetworkQualityLimit> limits = new ArrayList<NetworkQualityLimit>();

  /**
   * Create a named quality profile.
   *
   * @param name profile name
   */
  public NetworkQualityProfile(String name) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Quality profile name cannot be empty");
    }
    this.name = name;
  }

  /** @return profile name */
  public String getName() {
    return name;
  }

  /**
   * Set effective-date and version metadata.
   *
   * @param profileVersion version identifier
   * @param from ISO-8601 effective start
   * @param to ISO-8601 effective end, or null
   * @return this profile
   */
  public NetworkQualityProfile withEffectivePeriod(String profileVersion, String from, String to) {
    version = profileVersion;
    effectiveFrom = from;
    effectiveTo = to;
    return this;
  }

  /**
   * Set governing provenance.
   *
   * @param value provenance description or URI
   * @return this profile
   */
  public NetworkQualityProfile withProvenance(String value) {
    provenance = value;
    return this;
  }

  /**
   * Add a named exception or qualification.
   *
   * @param exception exception text
   * @return this profile
   */
  public NetworkQualityProfile addNamedException(String exception) {
    namedExceptions.add(exception);
    return this;
  }

  /**
   * Add an upper limit.
   *
   * @param metric metric
   * @param upper upper limit
   * @param unit unit
   * @return this profile
   */
  public NetworkQualityProfile addUpperLimit(NetworkQualityMetric metric, double upper, String unit) {
    return addUpperLimit(metric, upper, unit, null);
  }

  /**
   * Add an upper limit with a reference condition.
   *
   * @param metric metric
   * @param upper upper limit
   * @param unit unit
   * @param reference reference condition
   * @return this profile
   */
  public NetworkQualityProfile addUpperLimit(NetworkQualityMetric metric, double upper, String unit,
      QualityReference reference) {
    limits.add(new NetworkQualityLimit(metric, null, upper, unit, reference, null, null, provenance));
    return this;
  }

  /**
   * Add a lower limit.
   *
   * @param metric metric
   * @param lower lower limit
   * @param unit unit
   * @return this profile
   */
  public NetworkQualityProfile addLowerLimit(NetworkQualityMetric metric, double lower, String unit) {
    limits.add(new NetworkQualityLimit(metric, lower, null, unit, null, null, null, provenance));
    return this;
  }

  /**
   * Add a bounded metric.
   *
   * @param metric metric
   * @param lower lower limit
   * @param upper upper limit
   * @param unit unit
   * @param reference reference condition
   * @return this profile
   */
  public NetworkQualityProfile addRange(NetworkQualityMetric metric, double lower, double upper, String unit,
      QualityReference reference) {
    limits.add(new NetworkQualityLimit(metric, lower, upper, unit, reference, null, null, provenance));
    return this;
  }

  /**
   * Add a component-specific mole-percent limit.
   *
   * @param componentName NeqSim component name
   * @param upperMolPercent upper limit in mol%
   * @return this profile
   */
  public NetworkQualityProfile addComponentUpperLimit(String componentName, double upperMolPercent) {
    limits.add(new NetworkQualityLimit(GasQualityMetric.COMPONENT_MOLE_PERCENT, null, upperMolPercent, "mol%", null,
        componentName, "EOS composition", provenance));
    return this;
  }

  /**
   * Add a governed measured or assay-backed attribute limit.
   *
   * @param domain gas or oil
   * @param attributeName stable attribute name
   * @param lower optional lower limit
   * @param upper optional upper limit
   * @param unit unit
   * @param method test/calculation method
   * @return this profile
   */
  public NetworkQualityProfile addMeasuredAttributeLimit(String domain, String attributeName, Double lower,
      Double upper, String unit, String method) {
    NetworkQualityMetric metric = "oil".equalsIgnoreCase(domain) ? OilQualityMetric.MEASURED_ATTRIBUTE
        : GasQualityMetric.MEASURED_ATTRIBUTE;
    limits.add(new NetworkQualityLimit(metric, lower, upper, unit, null, attributeName, method, provenance));
    return this;
  }

  /** @return version identifier */
  public String getVersion() {
    return version;
  }

  /** @return effective start */
  public String getEffectiveFrom() {
    return effectiveFrom;
  }

  /** @return effective end */
  public String getEffectiveTo() {
    return effectiveTo;
  }

  /** @return provenance */
  public String getProvenance() {
    return provenance;
  }

  /** @return immutable named exceptions */
  public List<String> getNamedExceptions() {
    return Collections.unmodifiableList(namedExceptions);
  }

  /** @return immutable quality limits */
  public List<NetworkQualityLimit> getLimits() {
    return Collections.unmodifiableList(limits);
  }

  /** @return stable JSON representation */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }

  /**
   * Restore a profile from JSON.
   *
   * @param json serialized profile
   * @return profile
   */
  public static NetworkQualityProfile fromJson(String json) {
    return new Gson().fromJson(json, NetworkQualityProfile.class);
  }
}
