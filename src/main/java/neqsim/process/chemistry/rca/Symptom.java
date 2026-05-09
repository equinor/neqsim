package neqsim.process.chemistry.rca;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Symptom describing an observed problem in well, flow assurance or process operation.
 *
 * <p>
 * A symptom carries a category, a free-text description, optional numeric measurements (deposit
 * mass, temperature, pH, corrosion rate, etc.), and a confidence score. The
 * {@link RootCauseAnalyser} uses these symptoms together with chemistry / process context to rank
 * candidate root causes.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class Symptom implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Symptom category.
   */
  public enum Category {
    /** Solid deposit observed (filter cake, plug, scale). */
    DEPOSIT,
    /** Pipe / vessel wall thinning observed in inspection. */
    CORROSION,
    /** Stable oil/water emulsion at separator. */
    EMULSION,
    /** pH excursion (low or high). */
    PH_EXCURSION,
    /** Pressure drop or flow restriction. */
    FLOW_RESTRICTION,
    /** H2S detected at outlet above target. */
    H2S_BREAKTHROUGH,
    /** Unexpected colour / appearance of fluid sample. */
    SAMPLE_APPEARANCE,
    /** Off-spec product (BS&W, salt, RVP). */
    OFF_SPEC,
    /** Other. */
    OTHER;
  }

  private final Category category;
  private final String description;
  private final Map<String, Double> measurements;
  private double confidence = 1.0;

  /**
   * Builds a symptom.
   *
   * @param category symptom category
   * @param description plain-language description
   */
  public Symptom(Category category, String description) {
    this.category = category;
    this.description = description;
    this.measurements = new LinkedHashMap<String, Double>();
  }

  /**
   * Adds a numeric measurement.
   *
   * @param key measurement key
   * @param value value
   * @return this for chaining
   */
  public Symptom withMeasurement(String key, double value) {
    measurements.put(key, value);
    return this;
  }

  /**
   * Sets the observation confidence.
   *
   * @param confidence 0..1
   * @return this for chaining
   */
  public Symptom withConfidence(double confidence) {
    this.confidence = confidence;
    return this;
  }

  /**
   * Returns the category.
   *
   * @return category
   */
  public Category getCategory() {
    return category;
  }

  /**
   * Returns the description.
   *
   * @return description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Returns the measurement value or NaN if absent.
   *
   * @param key measurement key
   * @return value or NaN
   */
  public double getMeasurement(String key) {
    Double v = measurements.get(key);
    return v == null ? Double.NaN : v.doubleValue();
  }

  /**
   * Returns the measurement map.
   *
   * @return ordered map
   */
  public Map<String, Double> getMeasurements() {
    return new LinkedHashMap<String, Double>(measurements);
  }

  /**
   * Returns the confidence score.
   *
   * @return 0..1
   */
  public double getConfidence() {
    return confidence;
  }

  /**
   * Returns a structured map for JSON.
   *
   * @return ordered map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("category", category.name());
    map.put("description", description);
    map.put("measurements", measurements);
    map.put("confidence", confidence);
    return map;
  }

  /**
   * Convenience constructor for a list of symptoms.
   *
   * @param symptoms varargs of symptoms
   * @return list
   */
  public static List<Symptom> of(Symptom... symptoms) {
    List<Symptom> list = new ArrayList<Symptom>();
    for (Symptom s : symptoms) {
      list.add(s);
    }
    return list;
  }
}
