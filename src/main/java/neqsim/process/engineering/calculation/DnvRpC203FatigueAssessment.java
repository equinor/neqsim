package neqsim.process.engineering.calculation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable DNV-RP-C203 S-N and Palmgren-Miner fatigue screening result. */
public final class DnvRpC203FatigueAssessment implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Immutable result for one stress-range spectrum bin. */
  public static final class BinResult implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String label;
    private final double nominalStressRangeMPa;
    private final double effectiveStressRangeMPa;
    private final double numberOfCycles;
    private final double cyclesToFailure;
    private final double minerDamage;

    BinResult(DnvRpC203FatigueDesignKernel.StressBin bin, double stressRangeFactor,
        DnvRpC203FatigueDesignKernel.SnCurve curve) {
      label = bin.getLabel();
      nominalStressRangeMPa = bin.getNominalStressRangeMPa();
      effectiveStressRangeMPa = nominalStressRangeMPa * stressRangeFactor;
      numberOfCycles = bin.getNumberOfCycles();
      cyclesToFailure = curve.cyclesToFailure(effectiveStressRangeMPa);
      minerDamage = numberOfCycles / cyclesToFailure;
    }

    /** @return bin label */
    public String getLabel() {
      return label;
    }

    /** @return nominal stress range in MPa */
    public double getNominalStressRangeMPa() {
      return nominalStressRangeMPa;
    }

    /** @return stress range after all supplied factors in MPa */
    public double getEffectiveStressRangeMPa() {
      return effectiveStressRangeMPa;
    }

    /** @return cycles during the assessed exposure */
    public double getNumberOfCycles() {
      return numberOfCycles;
    }

    /** @return cycles to failure from the supplied S-N curve */
    public double getCyclesToFailure() {
      return cyclesToFailure;
    }

    /** @return un-factored Palmgren-Miner damage contribution */
    public double getMinerDamage() {
      return minerDamage;
    }

    /** @return serializable bin result */
    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("label", label);
      result.put("nominalStressRangeMPa", Double.valueOf(nominalStressRangeMPa));
      result.put("effectiveStressRangeMPa", Double.valueOf(effectiveStressRangeMPa));
      result.put("numberOfCycles", Double.valueOf(numberOfCycles));
      result.put("cyclesToFailure", Double.valueOf(cyclesToFailure));
      result.put("minerDamage", Double.valueOf(minerDamage));
      return result;
    }
  }

  private final String standardEdition;
  private final String curveIdentifier;
  private final double combinedStressRangeFactor;
  private final double designFatigueFactor;
  private final double minerDamageLimit;
  private final double assessedExposureYears;
  private final List<BinResult> bins;
  private final double rawMinerDamage;
  private final double designMinerDamage;
  private final double damageUtilization;
  private final boolean withinDamageLimit;
  private final double estimatedDesignFatigueLifeYears;
  private final String governingBinLabel;

  DnvRpC203FatigueAssessment(DnvRpC203FatigueDesignKernel.Input input) {
    standardEdition = input.getEdition().getDisplayName();
    curveIdentifier = input.getSnCurve().getIdentifier();
    combinedStressRangeFactor = input.getStressConcentrationFactor() * input.getThicknessCorrectionFactor()
        * input.getOtherStressRangeFactor();
    designFatigueFactor = input.getDesignFatigueFactor();
    minerDamageLimit = input.getMinerDamageLimit();
    assessedExposureYears = input.getAssessedExposureYears();

    List<BinResult> results = new ArrayList<BinResult>();
    double damage = 0.0;
    double largestContribution = -1.0;
    String governingLabel = "";
    for (DnvRpC203FatigueDesignKernel.StressBin bin : input.getStressBins()) {
      BinResult result = new BinResult(bin, combinedStressRangeFactor, input.getSnCurve());
      results.add(result);
      damage += result.getMinerDamage();
      if (result.getMinerDamage() > largestContribution) {
        largestContribution = result.getMinerDamage();
        governingLabel = result.getLabel();
      }
    }
    bins = Collections.unmodifiableList(results);
    rawMinerDamage = damage;
    designMinerDamage = rawMinerDamage * designFatigueFactor;
    damageUtilization = designMinerDamage / minerDamageLimit;
    withinDamageLimit = damageUtilization <= 1.0;
    estimatedDesignFatigueLifeYears = assessedExposureYears / damageUtilization;
    governingBinLabel = governingLabel;
  }

  /** @return explicit standard edition */
  public String getStandardEdition() {
    return standardEdition;
  }

  /** @return caller-controlled curve identifier */
  public String getCurveIdentifier() {
    return curveIdentifier;
  }

  /** @return product of SCF, thickness, and other supplied stress-range factors */
  public double getCombinedStressRangeFactor() {
    return combinedStressRangeFactor;
  }

  /** @return design fatigue factor */
  public double getDesignFatigueFactor() {
    return designFatigueFactor;
  }

  /** @return Palmgren-Miner damage limit */
  public double getMinerDamageLimit() {
    return minerDamageLimit;
  }

  /** @return years represented by the input spectrum */
  public double getAssessedExposureYears() {
    return assessedExposureYears;
  }

  /** @return immutable per-bin results */
  public List<BinResult> getBins() {
    return Collections.unmodifiableList(new ArrayList<BinResult>(bins));
  }

  /** @return un-factored cumulative Palmgren-Miner damage */
  public double getRawMinerDamage() {
    return rawMinerDamage;
  }

  /** @return raw damage multiplied by the design fatigue factor */
  public double getDesignMinerDamage() {
    return designMinerDamage;
  }

  /** @return factored damage divided by the supplied damage limit */
  public double getDamageUtilization() {
    return damageUtilization;
  }

  /** @return whether factored damage is within the supplied limit */
  public boolean isWithinDamageLimit() {
    return withinDamageLimit;
  }

  /** @return linear-extrapolated design fatigue life in years */
  public double getEstimatedDesignFatigueLifeYears() {
    return estimatedDesignFatigueLifeYears;
  }

  /** @return label of the largest Miner-damage contribution */
  public String getGoverningBinLabel() {
    return governingBinLabel;
  }

  /** @return serializable assessment representation */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("standardEdition", standardEdition);
    result.put("curveIdentifier", curveIdentifier);
    result.put("combinedStressRangeFactor", Double.valueOf(combinedStressRangeFactor));
    result.put("designFatigueFactor", Double.valueOf(designFatigueFactor));
    result.put("minerDamageLimit", Double.valueOf(minerDamageLimit));
    result.put("assessedExposureYears", Double.valueOf(assessedExposureYears));
    List<Map<String, Object>> binMaps = new ArrayList<Map<String, Object>>();
    for (BinResult bin : bins) {
      binMaps.add(bin.toMap());
    }
    result.put("bins", binMaps);
    result.put("rawMinerDamage", Double.valueOf(rawMinerDamage));
    result.put("designMinerDamage", Double.valueOf(designMinerDamage));
    result.put("damageUtilization", Double.valueOf(damageUtilization));
    result.put("withinDamageLimit", Boolean.valueOf(withinDamageLimit));
    result.put("estimatedDesignFatigueLifeYears", Double.valueOf(estimatedDesignFatigueLifeYears));
    result.put("governingBinLabel", governingBinLabel);
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }
}
