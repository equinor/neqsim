package neqsim.process.electricaldesign.loadanalysis;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a single electrical load item in a load list.
 *
 * <p>
 * Each load item captures the power consumption for one piece of process equipment, including rated,
 * absorbed, and design powers, plus demand and diversity factors used for plant-wide aggregation.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class LoadItem implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private String tagNumber;
  private String description;
  private String loadCategory;

  // === Power values ===
  private double ratedPowerKW;
  private double absorbedPowerKW;
  private double apparentPowerKVA;
  private double powerFactor;

  // === Factors ===
  private double demandFactor = 1.0;
  private double diversityFactor = 1.0;

  // === Electrical ===
  private double ratedVoltageV;
  private double ratedCurrentA;
  private boolean continuousDuty = true;
  private boolean hasVFD = false;
  private boolean isSpare = false;

  /**
   * Construct a LoadItem.
   *
   * @param tagNumber equipment tag number
   * @param description load description
   * @param ratedPowerKW rated power in kW
   */
  public LoadItem(String tagNumber, String description, double ratedPowerKW) {
    this.tagNumber = tagNumber;
    this.description = description;
    this.ratedPowerKW = ratedPowerKW;
    this.absorbedPowerKW = ratedPowerKW;
  }

  /**
   * Get the maximum demand power contribution.
   *
   * @return maximum demand in kW
   */
  public double getMaxDemandKW() {
    if (isSpare) {
      return 0.0;
    }
    return absorbedPowerKW * demandFactor * diversityFactor;
  }

  /**
   * Get the maximum demand in kVA.
   *
   * @return maximum demand in kVA
   */
  public double getMaxDemandKVA() {
    if (powerFactor > 0) {
      return getMaxDemandKW() / powerFactor;
    }
    return getMaxDemandKW();
  }

  /**
   * Convert to a map.
   *
   * @return map of load item parameters
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("tagNumber", tagNumber);
    map.put("description", description);
    map.put("loadCategory", loadCategory);
    map.put("ratedPowerKW", ratedPowerKW);
    map.put("absorbedPowerKW", absorbedPowerKW);
    map.put("apparentPowerKVA", apparentPowerKVA);
    map.put("powerFactor", powerFactor);
    map.put("demandFactor", demandFactor);
    map.put("diversityFactor", diversityFactor);
    map.put("maxDemandKW", getMaxDemandKW());
    map.put("maxDemandKVA", getMaxDemandKVA());
    map.put("ratedVoltageV", ratedVoltageV);
    map.put("continuousDuty", continuousDuty);
    map.put("hasVFD", hasVFD);
    map.put("isSpare", isSpare);
    return map;
  }

  // === Getters and Setters ===

  /**
   * Get tag number.
   *
   * @return tag number
   */
  public String getTagNumber() {
    return tagNumber;
  }

  /**
   * Set tag number.
   *
   * @param tagNumber tag number
   */
  public void setTagNumber(String tagNumber) {
    this.tagNumber = tagNumber;
  }

  /**
   * Get description.
   *
   * @return description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Set description.
   *
   * @param description description
   */
  public void setDescription(String description) {
    this.description = description;
  }

  /**
   * Get load category.
   *
   * @return load category
   */
  public String getLoadCategory() {
    return loadCategory;
  }

  /**
   * Set load category.
   *
   * @param loadCategory load category (e.g., "Continuous", "Intermittent", "Standby")
   */
  public void setLoadCategory(String loadCategory) {
    this.loadCategory = loadCategory;
  }

  /**
   * Get rated power in kW.
   *
   * @return rated power in kW
   */
  public double getRatedPowerKW() {
    return ratedPowerKW;
  }

  /**
   * Set rated power in kW.
   *
   * @param ratedPowerKW rated power in kW
   */
  public void setRatedPowerKW(double ratedPowerKW) {
    this.ratedPowerKW = ratedPowerKW;
  }

  /**
   * Get absorbed power in kW.
   *
   * @return absorbed power in kW
   */
  public double getAbsorbedPowerKW() {
    return absorbedPowerKW;
  }

  /**
   * Set absorbed power in kW.
   *
   * @param absorbedPowerKW absorbed power in kW
   */
  public void setAbsorbedPowerKW(double absorbedPowerKW) {
    this.absorbedPowerKW = absorbedPowerKW;
  }

  /**
   * Get apparent power in kVA.
   *
   * @return apparent power in kVA
   */
  public double getApparentPowerKVA() {
    return apparentPowerKVA;
  }

  /**
   * Set apparent power in kVA.
   *
   * @param apparentPowerKVA apparent power in kVA
   */
  public void setApparentPowerKVA(double apparentPowerKVA) {
    this.apparentPowerKVA = apparentPowerKVA;
  }

  /**
   * Get power factor.
   *
   * @return power factor
   */
  public double getPowerFactor() {
    return powerFactor;
  }

  /**
   * Set power factor.
   *
   * @param powerFactor power factor
   */
  public void setPowerFactor(double powerFactor) {
    this.powerFactor = powerFactor;
  }

  /**
   * Get demand factor.
   *
   * @return demand factor (0-1)
   */
  public double getDemandFactor() {
    return demandFactor;
  }

  /**
   * Set demand factor.
   *
   * @param demandFactor demand factor (0-1)
   */
  public void setDemandFactor(double demandFactor) {
    this.demandFactor = demandFactor;
  }

  /**
   * Get diversity factor.
   *
   * @return diversity factor (0-1)
   */
  public double getDiversityFactor() {
    return diversityFactor;
  }

  /**
   * Set diversity factor.
   *
   * @param diversityFactor diversity factor (0-1)
   */
  public void setDiversityFactor(double diversityFactor) {
    this.diversityFactor = diversityFactor;
  }

  /**
   * Get rated voltage in V.
   *
   * @return rated voltage in V
   */
  public double getRatedVoltageV() {
    return ratedVoltageV;
  }

  /**
   * Set rated voltage in V.
   *
   * @param ratedVoltageV rated voltage in V
   */
  public void setRatedVoltageV(double ratedVoltageV) {
    this.ratedVoltageV = ratedVoltageV;
  }

  /**
   * Get rated current in A.
   *
   * @return rated current in A
   */
  public double getRatedCurrentA() {
    return ratedCurrentA;
  }

  /**
   * Set rated current in A.
   *
   * @param ratedCurrentA rated current in A
   */
  public void setRatedCurrentA(double ratedCurrentA) {
    this.ratedCurrentA = ratedCurrentA;
  }

  /**
   * Check if continuous duty.
   *
   * @return true if continuous duty
   */
  public boolean isContinuousDuty() {
    return continuousDuty;
  }

  /**
   * Set continuous duty flag.
   *
   * @param continuousDuty true for continuous duty
   */
  public void setContinuousDuty(boolean continuousDuty) {
    this.continuousDuty = continuousDuty;
  }

  /**
   * Check if VFD equipped.
   *
   * @return true if VFD equipped
   */
  public boolean isHasVFD() {
    return hasVFD;
  }

  /**
   * Set VFD equipped flag.
   *
   * @param hasVFD true if VFD equipped
   */
  public void setHasVFD(boolean hasVFD) {
    this.hasVFD = hasVFD;
  }

  /**
   * Check if this is a spare load.
   *
   * @return true if spare
   */
  public boolean isSpare() {
    return isSpare;
  }

  /**
   * Set spare flag.
   *
   * @param spare true if spare
   */
  public void setSpare(boolean spare) {
    isSpare = spare;
  }
}
