package neqsim.process.safety.processsafetysystem;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Result from NORSOK S-001 Clause 10.4.7 secondary pressure protection screening.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class S001SecondaryPressureProtectionResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final double maximumEventPressureBara;
  private final double designPressureBara;
  private final double testPressureBara;
  private final double demandFrequencyPerYear;
  private final double targetFrequencyPerYear;
  private final boolean pressureBasisComplete;
  private final boolean testPressureValid;
  private final boolean pressureWithinTestPressure;
  private final boolean frequencyConfigured;
  private final boolean frequencyCriterionMet;
  private final boolean leakageAssessed;
  private final boolean leakageRoutedSafe;
  private final boolean proofTestConfigured;
  private final boolean proofTestCriterionMet;
  private final boolean acceptable;

  /**
   * Creates a secondary pressure protection result.
   *
   * @param maximumEventPressureBara maximum event pressure in bara
   * @param designPressureBara design pressure in bara
   * @param testPressureBara pressure test pressure in bara
   * @param demandFrequencyPerYear demand frequency per year
   * @param targetFrequencyPerYear target frequency per year
   * @param pressureBasisComplete true when pressure basis is complete
   * @param testPressureValid true when test pressure is at least design pressure
   * @param pressureWithinTestPressure true when event pressure is no higher than test pressure
   * @param frequencyConfigured true when demand and target frequencies are configured
   * @param frequencyCriterionMet true when demand frequency is at or below target
   * @param leakageAssessed true when leakage has been assessed
   * @param leakageRoutedSafe true when leakage is routed to a safe location
   * @param proofTestConfigured true when proof-test interval is configured
   * @param proofTestCriterionMet true when proof-test interval is acceptable
   * @param acceptable true when all configured criteria are acceptable
   */
  public S001SecondaryPressureProtectionResult(double maximumEventPressureBara,
      double designPressureBara, double testPressureBara, double demandFrequencyPerYear,
      double targetFrequencyPerYear, boolean pressureBasisComplete, boolean testPressureValid,
      boolean pressureWithinTestPressure, boolean frequencyConfigured,
      boolean frequencyCriterionMet, boolean leakageAssessed, boolean leakageRoutedSafe,
      boolean proofTestConfigured, boolean proofTestCriterionMet, boolean acceptable) {
    this.maximumEventPressureBara = maximumEventPressureBara;
    this.designPressureBara = designPressureBara;
    this.testPressureBara = testPressureBara;
    this.demandFrequencyPerYear = demandFrequencyPerYear;
    this.targetFrequencyPerYear = targetFrequencyPerYear;
    this.pressureBasisComplete = pressureBasisComplete;
    this.testPressureValid = testPressureValid;
    this.pressureWithinTestPressure = pressureWithinTestPressure;
    this.frequencyConfigured = frequencyConfigured;
    this.frequencyCriterionMet = frequencyCriterionMet;
    this.leakageAssessed = leakageAssessed;
    this.leakageRoutedSafe = leakageRoutedSafe;
    this.proofTestConfigured = proofTestConfigured;
    this.proofTestCriterionMet = proofTestCriterionMet;
    this.acceptable = acceptable;
  }

  /**
   * Tests whether pressure basis is complete.
   *
   * @return true when event, design, and test pressure were supplied
   */
  public boolean isPressureBasisComplete() {
    return pressureBasisComplete;
  }

  /**
   * Tests whether event pressure is within test pressure.
   *
   * @return true when event pressure is no higher than test pressure
   */
  public boolean isPressureWithinTestPressure() {
    return pressureWithinTestPressure;
  }

  /**
   * Tests whether frequency criterion is configured.
   *
   * @return true when frequency comparison can be made
   */
  public boolean isFrequencyConfigured() {
    return frequencyConfigured;
  }

  /**
   * Tests whether frequency criterion is met.
   *
   * @return true when demand frequency is at or below target
   */
  public boolean isFrequencyCriterionMet() {
    return frequencyCriterionMet;
  }

  /**
   * Tests whether leakage basis is acceptable.
   *
   * @return true when leakage is assessed and routed to a safe location
   */
  public boolean isLeakageBasisAcceptable() {
    return leakageAssessed && leakageRoutedSafe;
  }

  /**
   * Tests whether proof-test criterion is met.
   *
   * @return true when proof-test criterion is met or not configured
   */
  public boolean isProofTestCriterionMet() {
    return proofTestCriterionMet;
  }

  /**
   * Tests whether the secondary pressure protection is acceptable.
   *
   * @return true when all screening criteria are acceptable
   */
  public boolean isAcceptable() {
    return acceptable;
  }

  /**
   * Converts the result to an ordered map.
   *
   * @return ordered map for reporting
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("standard", "NORSOK S-001");
    map.put("clause", "10.4.7");
    map.put("maximumEventPressureBara", maximumEventPressureBara);
    map.put("designPressureBara", designPressureBara);
    map.put("testPressureBara", testPressureBara);
    map.put("demandFrequencyPerYear", demandFrequencyPerYear);
    map.put("targetFrequencyPerYear", targetFrequencyPerYear);
    map.put("pressureBasisComplete", pressureBasisComplete);
    map.put("testPressureValid", testPressureValid);
    map.put("pressureWithinTestPressure", pressureWithinTestPressure);
    map.put("frequencyConfigured", frequencyConfigured);
    map.put("frequencyCriterionMet", frequencyCriterionMet);
    map.put("leakageAssessed", leakageAssessed);
    map.put("leakageRoutedSafe", leakageRoutedSafe);
    map.put("proofTestConfigured", proofTestConfigured);
    map.put("proofTestCriterionMet", proofTestCriterionMet);
    map.put("acceptable", acceptable);
    return map;
  }

  /**
   * Converts the result to pretty JSON.
   *
   * @return JSON representation of the result
   */
  public String toJson() {
    Gson gson = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues()
        .create();
    return gson.toJson(toMap());
  }
}