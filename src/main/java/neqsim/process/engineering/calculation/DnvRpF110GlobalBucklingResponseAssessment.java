package neqsim.process.engineering.calculation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable assessment of caller-controlled global-buckling response envelopes. */
public final class DnvRpF110GlobalBucklingResponseAssessment implements EngineeringConstraintResult, Serializable {
  private static final long serialVersionUID = 1000L;

  /** Immutable result for one externally analysed buckling case. */
  public static final class BucklingCaseAssessment implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String label;
    private final double distanceM;
    private final String designSituation;
    private final DnvRpF110GlobalBucklingResponseScreeningKernel.PipelineConfiguration configuration;
    private final DnvRpF110GlobalBucklingResponseScreeningKernel.DesignStrategy strategy;
    private final double effectiveCompressiveForceN;
    private final double callerControlledAllowableCompressiveForceN;
    private final double compressiveForceMarginN;
    private final double compressiveForceUtilization;
    private final double peakLongitudinalStrainFraction;
    private final double callerControlledAllowableLongitudinalStrainFraction;
    private final double longitudinalStrainMarginFraction;
    private final double longitudinalStrainUtilization;
    private final double peakGlobalDisplacementM;
    private final double callerControlledAllowableGlobalDisplacementM;
    private final double globalDisplacementMarginM;
    private final double globalDisplacementUtilization;
    private final double requiredFeedInLengthM;
    private final double availableFeedInLengthM;
    private final double feedInLengthMarginM;
    private final double feedInLengthUtilization;

    BucklingCaseAssessment(DnvRpF110GlobalBucklingResponseScreeningKernel.BucklingCase value) {
      label = value.getLabel();
      distanceM = value.getDistanceM();
      designSituation = value.getDesignSituation();
      configuration = value.getConfiguration();
      strategy = value.getStrategy();
      effectiveCompressiveForceN = value.getEffectiveCompressiveForceN();
      callerControlledAllowableCompressiveForceN = value.getCallerControlledAllowableCompressiveForceN();
      compressiveForceMarginN = callerControlledAllowableCompressiveForceN - effectiveCompressiveForceN;
      compressiveForceUtilization = effectiveCompressiveForceN / callerControlledAllowableCompressiveForceN;
      peakLongitudinalStrainFraction = value.getPeakLongitudinalStrainFraction();
      callerControlledAllowableLongitudinalStrainFraction = value
          .getCallerControlledAllowableLongitudinalStrainFraction();
      longitudinalStrainMarginFraction = callerControlledAllowableLongitudinalStrainFraction
          - peakLongitudinalStrainFraction;
      longitudinalStrainUtilization = peakLongitudinalStrainFraction
          / callerControlledAllowableLongitudinalStrainFraction;
      peakGlobalDisplacementM = value.getPeakGlobalDisplacementM();
      callerControlledAllowableGlobalDisplacementM = value.getCallerControlledAllowableGlobalDisplacementM();
      globalDisplacementMarginM = callerControlledAllowableGlobalDisplacementM - peakGlobalDisplacementM;
      globalDisplacementUtilization = peakGlobalDisplacementM / callerControlledAllowableGlobalDisplacementM;
      requiredFeedInLengthM = value.getRequiredFeedInLengthM();
      availableFeedInLengthM = value.getAvailableFeedInLengthM();
      feedInLengthMarginM = availableFeedInLengthM - requiredFeedInLengthM;
      feedInLengthUtilization = requiredFeedInLengthM / availableFeedInLengthM;
    }

    /** @return caller-controlled case label */
    public String getLabel() {
      return label;
    }

    /** @return route distance in m */
    public double getDistanceM() {
      return distanceM;
    }

    /** @return design-situation identifier */
    public String getDesignSituation() {
      return designSituation;
    }

    /** @return exposed or buried configuration */
    public DnvRpF110GlobalBucklingResponseScreeningKernel.PipelineConfiguration getConfiguration() {
      return configuration;
    }

    /** @return controlled-buckling or prevention strategy */
    public DnvRpF110GlobalBucklingResponseScreeningKernel.DesignStrategy getStrategy() {
      return strategy;
    }

    /** @return effective compressive-force magnitude in N */
    public double getEffectiveCompressiveForceN() {
      return effectiveCompressiveForceN;
    }

    /** @return caller-controlled allowable compressive-force magnitude in N */
    public double getCallerControlledAllowableCompressiveForceN() {
      return callerControlledAllowableCompressiveForceN;
    }

    /** @return allowable minus effective compressive force in N */
    public double getCompressiveForceMarginN() {
      return compressiveForceMarginN;
    }

    /** @return effective divided by allowable compressive force */
    public double getCompressiveForceUtilization() {
      return compressiveForceUtilization;
    }

    /** @return externally analysed peak longitudinal strain as a fraction */
    public double getPeakLongitudinalStrainFraction() {
      return peakLongitudinalStrainFraction;
    }

    /** @return caller-controlled allowable longitudinal strain as a fraction */
    public double getCallerControlledAllowableLongitudinalStrainFraction() {
      return callerControlledAllowableLongitudinalStrainFraction;
    }

    /** @return allowable minus peak longitudinal strain as a fraction */
    public double getLongitudinalStrainMarginFraction() {
      return longitudinalStrainMarginFraction;
    }

    /** @return peak divided by allowable longitudinal strain */
    public double getLongitudinalStrainUtilization() {
      return longitudinalStrainUtilization;
    }

    /** @return peak global displacement magnitude in m */
    public double getPeakGlobalDisplacementM() {
      return peakGlobalDisplacementM;
    }

    /** @return caller-controlled allowable global displacement magnitude in m */
    public double getCallerControlledAllowableGlobalDisplacementM() {
      return callerControlledAllowableGlobalDisplacementM;
    }

    /** @return allowable minus peak global displacement in m */
    public double getGlobalDisplacementMarginM() {
      return globalDisplacementMarginM;
    }

    /** @return peak divided by allowable global displacement */
    public double getGlobalDisplacementUtilization() {
      return globalDisplacementUtilization;
    }

    /** @return externally derived required feed-in length in m */
    public double getRequiredFeedInLengthM() {
      return requiredFeedInLengthM;
    }

    /** @return externally established available feed-in length in m */
    public double getAvailableFeedInLengthM() {
      return availableFeedInLengthM;
    }

    /** @return available minus required feed-in length in m */
    public double getFeedInLengthMarginM() {
      return feedInLengthMarginM;
    }

    /** @return required divided by available feed-in length */
    public double getFeedInLengthUtilization() {
      return feedInLengthUtilization;
    }

    /** @return whether all four caller-controlled response constraints are satisfied */
    public boolean allConstraintsSatisfied() {
      return compressiveForceMarginN >= 0.0 && longitudinalStrainMarginFraction >= 0.0
          && globalDisplacementMarginM >= 0.0 && feedInLengthMarginM >= 0.0;
    }

    /** @return serializable case representation */
    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("label", label);
      result.put("distanceM", Double.valueOf(distanceM));
      result.put("designSituation", designSituation);
      result.put("configuration", configuration.name());
      result.put("strategy", strategy.name());
      result.put("effectiveCompressiveForceN", Double.valueOf(effectiveCompressiveForceN));
      result.put("callerControlledAllowableCompressiveForceN",
          Double.valueOf(callerControlledAllowableCompressiveForceN));
      result.put("compressiveForceMarginN", Double.valueOf(compressiveForceMarginN));
      result.put("compressiveForceUtilization", Double.valueOf(compressiveForceUtilization));
      result.put("peakLongitudinalStrainFraction", Double.valueOf(peakLongitudinalStrainFraction));
      result.put("callerControlledAllowableLongitudinalStrainFraction",
          Double.valueOf(callerControlledAllowableLongitudinalStrainFraction));
      result.put("longitudinalStrainMarginFraction", Double.valueOf(longitudinalStrainMarginFraction));
      result.put("longitudinalStrainUtilization", Double.valueOf(longitudinalStrainUtilization));
      result.put("peakGlobalDisplacementM", Double.valueOf(peakGlobalDisplacementM));
      result.put("callerControlledAllowableGlobalDisplacementM",
          Double.valueOf(callerControlledAllowableGlobalDisplacementM));
      result.put("globalDisplacementMarginM", Double.valueOf(globalDisplacementMarginM));
      result.put("globalDisplacementUtilization", Double.valueOf(globalDisplacementUtilization));
      result.put("requiredFeedInLengthM", Double.valueOf(requiredFeedInLengthM));
      result.put("availableFeedInLengthM", Double.valueOf(availableFeedInLengthM));
      result.put("feedInLengthMarginM", Double.valueOf(feedInLengthMarginM));
      result.put("feedInLengthUtilization", Double.valueOf(feedInLengthUtilization));
      result.put("allCallerControlledConstraintsSatisfied", Boolean.valueOf(allConstraintsSatisfied()));
      return result;
    }
  }

  private final String standardEdition;
  private final double pipelineOuterDiameterM;
  private final double steelWallThicknessM;
  private final List<BucklingCaseAssessment> bucklingCases;
  private final double maximumCompressiveForceUtilization;
  private final double maximumLongitudinalStrainUtilization;
  private final double maximumGlobalDisplacementUtilization;
  private final double maximumFeedInLengthUtilization;

  DnvRpF110GlobalBucklingResponseAssessment(DnvRpF110GlobalBucklingResponseScreeningKernel.Input input) {
    standardEdition = input.getEdition().getDisplayName();
    pipelineOuterDiameterM = input.getPipelineOuterDiameterM();
    steelWallThicknessM = input.getSteelWallThicknessM();
    List<BucklingCaseAssessment> results = new ArrayList<BucklingCaseAssessment>();
    double force = Double.NEGATIVE_INFINITY;
    double strain = Double.NEGATIVE_INFINITY;
    double displacement = Double.NEGATIVE_INFINITY;
    double feedIn = Double.NEGATIVE_INFINITY;
    for (DnvRpF110GlobalBucklingResponseScreeningKernel.BucklingCase value : input.getBucklingCases()) {
      BucklingCaseAssessment result = new BucklingCaseAssessment(value);
      results.add(result);
      force = Math.max(force, result.getCompressiveForceUtilization());
      strain = Math.max(strain, result.getLongitudinalStrainUtilization());
      displacement = Math.max(displacement, result.getGlobalDisplacementUtilization());
      feedIn = Math.max(feedIn, result.getFeedInLengthUtilization());
    }
    bucklingCases = Collections.unmodifiableList(results);
    maximumCompressiveForceUtilization = force;
    maximumLongitudinalStrainUtilization = strain;
    maximumGlobalDisplacementUtilization = displacement;
    maximumFeedInLengthUtilization = feedIn;
  }

  /** @return explicit standard edition */
  public String getStandardEdition() {
    return standardEdition;
  }

  /** @return pipeline outside diameter in m */
  public double getPipelineOuterDiameterM() {
    return pipelineOuterDiameterM;
  }

  /** @return structural steel wall thickness in m */
  public double getSteelWallThicknessM() {
    return steelWallThicknessM;
  }

  /** @return immutable ordered response cases */
  public List<BucklingCaseAssessment> getBucklingCases() {
    return bucklingCases;
  }

  /** @return maximum compressive-force utilization */
  public double getMaximumCompressiveForceUtilization() {
    return maximumCompressiveForceUtilization;
  }

  /** @return maximum longitudinal-strain utilization */
  public double getMaximumLongitudinalStrainUtilization() {
    return maximumLongitudinalStrainUtilization;
  }

  /** @return maximum global-displacement utilization */
  public double getMaximumGlobalDisplacementUtilization() {
    return maximumGlobalDisplacementUtilization;
  }

  /** @return maximum feed-in-length utilization */
  public double getMaximumFeedInLengthUtilization() {
    return maximumFeedInLengthUtilization;
  }

  /** {@inheritDoc} */
  @Override
  public boolean allConstraintsSatisfied() {
    if (bucklingCases.isEmpty()) {
      return false;
    }
    for (BucklingCaseAssessment result : bucklingCases) {
      if (!result.allConstraintsSatisfied()) {
        return false;
      }
    }
    return true;
  }

  /** @return serializable assessment representation */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    List<Map<String, Object>> caseMaps = new ArrayList<Map<String, Object>>();
    for (BucklingCaseAssessment bucklingCase : bucklingCases) {
      caseMaps.add(bucklingCase.toMap());
    }
    result.put("standardEdition", standardEdition);
    result.put("scope", "CALLER_CONTROLLED_GLOBAL_BUCKLING_RESPONSE_ENVELOPE_SCREEN");
    result.put("pipelineOuterDiameterM", Double.valueOf(pipelineOuterDiameterM));
    result.put("steelWallThicknessM", Double.valueOf(steelWallThicknessM));
    result.put("bucklingCases", caseMaps);
    result.put("maximumCompressiveForceUtilization", Double.valueOf(maximumCompressiveForceUtilization));
    result.put("maximumLongitudinalStrainUtilization", Double.valueOf(maximumLongitudinalStrainUtilization));
    result.put("maximumGlobalDisplacementUtilization", Double.valueOf(maximumGlobalDisplacementUtilization));
    result.put("maximumFeedInLengthUtilization", Double.valueOf(maximumFeedInLengthUtilization));
    result.put("allCallerControlledConstraintsSatisfied", Boolean.valueOf(allConstraintsSatisfied()));
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }
}
