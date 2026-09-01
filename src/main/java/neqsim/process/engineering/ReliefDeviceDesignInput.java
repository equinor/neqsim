package neqsim.process.engineering;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Installed PSV/relief-device piping and disposal-system design input. */
public final class ReliefDeviceDesignInput implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String deviceTag;
  private final String equipmentTag;
  private double selectedOrificeAreaIn2 = Double.NaN;
  private double inletDiameterM = Double.NaN;
  private double inletLengthM = Double.NaN;
  private double inletResistanceCoefficient = 0.0;
  private double outletDiameterM = Double.NaN;
  private double outletLengthM = Double.NaN;
  private double outletResistanceCoefficient = 0.0;
  private double allowableInletLossPercent = 3.0;
  private double allowableBuiltUpBackPressurePercent = 10.0;
  private String concurrencyGroup = "";
  private String fireZone = "";
  private String twoPhaseMethod = "NOT_APPLICABLE_OR_NOT_DEFINED";
  private String evidenceReference = "";

  public ReliefDeviceDesignInput(String deviceTag, String equipmentTag) {
    this.deviceTag = requireText(deviceTag, "deviceTag");
    this.equipmentTag = requireText(equipmentTag, "equipmentTag");
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }

  private static double requirePositive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be positive");
    }
    return value;
  }

  private static double requireNonNegative(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be non-negative");
    }
    return value;
  }

  public ReliefDeviceDesignInput setSelectedOrificeAreaIn2(double value) {
    selectedOrificeAreaIn2 = requirePositive(value, "selectedOrificeAreaIn2");
    return this;
  }

  public ReliefDeviceDesignInput setInletPiping(double diameterM, double lengthM, double resistanceCoefficient) {
    inletDiameterM = requirePositive(diameterM, "inletDiameterM");
    inletLengthM = requireNonNegative(lengthM, "inletLengthM");
    inletResistanceCoefficient = requireNonNegative(resistanceCoefficient, "inletResistanceCoefficient");
    return this;
  }

  public ReliefDeviceDesignInput setOutletPiping(double diameterM, double lengthM, double resistanceCoefficient) {
    outletDiameterM = requirePositive(diameterM, "outletDiameterM");
    outletLengthM = requireNonNegative(lengthM, "outletLengthM");
    outletResistanceCoefficient = requireNonNegative(resistanceCoefficient, "outletResistanceCoefficient");
    return this;
  }

  public ReliefDeviceDesignInput setAllowableInletLossPercent(double value) {
    allowableInletLossPercent = requirePositive(value, "allowableInletLossPercent");
    return this;
  }

  public ReliefDeviceDesignInput setAllowableBuiltUpBackPressurePercent(double value) {
    allowableBuiltUpBackPressurePercent = requirePositive(value, "allowableBuiltUpBackPressurePercent");
    return this;
  }

  public ReliefDeviceDesignInput setConcurrencyGroup(String value) {
    concurrencyGroup = requireText(value, "concurrencyGroup");
    return this;
  }

  public ReliefDeviceDesignInput setFireZone(String value) {
    fireZone = requireText(value, "fireZone");
    return this;
  }

  public ReliefDeviceDesignInput setTwoPhaseMethod(String value) {
    twoPhaseMethod = requireText(value, "twoPhaseMethod");
    return this;
  }

  public ReliefDeviceDesignInput setEvidenceReference(String value) {
    evidenceReference = requireText(value, "evidenceReference");
    return this;
  }

  public List<String> getMissingFields() {
    List<String> missing = new ArrayList<String>();
    if (!Double.isFinite(selectedOrificeAreaIn2)) {
      missing.add("selectedOrificeArea");
    }
    if (!Double.isFinite(inletDiameterM) || !Double.isFinite(inletLengthM)) {
      missing.add("inletPipingGeometry");
    }
    if (!Double.isFinite(outletDiameterM) || !Double.isFinite(outletLengthM)) {
      missing.add("outletPipingGeometry");
    }
    if (concurrencyGroup.isEmpty()) {
      missing.add("concurrencyGroup");
    }
    if (evidenceReference.isEmpty()) {
      missing.add("evidenceReference");
    }
    return missing;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("deviceTag", deviceTag);
    map.put("equipmentTag", equipmentTag);
    map.put("selectedOrificeAreaIn2", selectedOrificeAreaIn2);
    map.put("inletDiameterM", inletDiameterM);
    map.put("inletLengthM", inletLengthM);
    map.put("inletResistanceCoefficient", inletResistanceCoefficient);
    map.put("outletDiameterM", outletDiameterM);
    map.put("outletLengthM", outletLengthM);
    map.put("outletResistanceCoefficient", outletResistanceCoefficient);
    map.put("allowableInletLossPercent", allowableInletLossPercent);
    map.put("allowableBuiltUpBackPressurePercent", allowableBuiltUpBackPressurePercent);
    map.put("concurrencyGroup", concurrencyGroup);
    map.put("fireZone", fireZone);
    map.put("twoPhaseMethod", twoPhaseMethod);
    map.put("evidenceReference", evidenceReference);
    map.put("missingFields", getMissingFields());
    return map;
  }

  public String getDeviceTag() {
    return deviceTag;
  }

  public String getEquipmentTag() {
    return equipmentTag;
  }

  public double getSelectedOrificeAreaIn2() {
    return selectedOrificeAreaIn2;
  }

  public double getInletDiameterM() {
    return inletDiameterM;
  }

  public double getInletLengthM() {
    return inletLengthM;
  }

  public double getInletResistanceCoefficient() {
    return inletResistanceCoefficient;
  }

  public double getOutletDiameterM() {
    return outletDiameterM;
  }

  public double getOutletLengthM() {
    return outletLengthM;
  }

  public double getOutletResistanceCoefficient() {
    return outletResistanceCoefficient;
  }

  public double getAllowableInletLossPercent() {
    return allowableInletLossPercent;
  }

  public double getAllowableBuiltUpBackPressurePercent() {
    return allowableBuiltUpBackPressurePercent;
  }

  public String getConcurrencyGroup() {
    return concurrencyGroup;
  }

  public String getFireZone() {
    return fireZone;
  }

  public String getTwoPhaseMethod() {
    return twoPhaseMethod;
  }

  public String getEvidenceReference() {
    return evidenceReference;
  }
}
