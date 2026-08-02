package neqsim.process.engineering.calculation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable assessment of caller-controlled pipe-soil demand and resistance envelopes. */
public final class DnvRpF114PipeSoilInteractionAssessment implements EngineeringConstraintResult, Serializable {
  private static final long serialVersionUID = 1000L;

  /** Immutable result for one location and design situation. */
  public static final class InteractionCaseAssessment implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String label;
    private final double distanceM;
    private final String designSituation;
    private final double verticalDemandNPerM;
    private final double verticalResistanceNPerM;
    private final double verticalMarginNPerM;
    private final double verticalUtilization;
    private final double axialDemandNPerM;
    private final double axialResistanceNPerM;
    private final double axialMarginNPerM;
    private final double axialUtilization;
    private final double lateralDemandNPerM;
    private final double lateralResistanceNPerM;
    private final double lateralMarginNPerM;
    private final double lateralUtilization;

    InteractionCaseAssessment(DnvRpF114PipeSoilInteractionScreeningKernel.InteractionCase value) {
      label = value.getLabel();
      distanceM = value.getDistanceM();
      designSituation = value.getDesignSituation();
      verticalDemandNPerM = value.getVerticalDemandNPerM();
      verticalResistanceNPerM = value.getVerticalResistanceNPerM();
      verticalMarginNPerM = verticalResistanceNPerM - verticalDemandNPerM;
      verticalUtilization = verticalDemandNPerM / verticalResistanceNPerM;
      axialDemandNPerM = value.getAxialDemandNPerM();
      axialResistanceNPerM = value.getAxialResistanceNPerM();
      axialMarginNPerM = axialResistanceNPerM - axialDemandNPerM;
      axialUtilization = axialDemandNPerM / axialResistanceNPerM;
      lateralDemandNPerM = value.getLateralDemandNPerM();
      lateralResistanceNPerM = value.getLateralResistanceNPerM();
      lateralMarginNPerM = lateralResistanceNPerM - lateralDemandNPerM;
      lateralUtilization = lateralDemandNPerM / lateralResistanceNPerM;
    }

    /** @return caller-controlled case label */
    public String getLabel() {
      return label;
    }

    /** @return distance from the route origin in m */
    public double getDistanceM() {
      return distanceM;
    }

    /** @return caller-controlled design-situation identifier */
    public String getDesignSituation() {
      return designSituation;
    }

    /** @return vertical demand magnitude in N/m */
    public double getVerticalDemandNPerM() {
      return verticalDemandNPerM;
    }

    /** @return externally established vertical resistance in N/m */
    public double getVerticalResistanceNPerM() {
      return verticalResistanceNPerM;
    }

    /** @return vertical resistance minus demand in N/m */
    public double getVerticalMarginNPerM() {
      return verticalMarginNPerM;
    }

    /** @return vertical demand divided by resistance */
    public double getVerticalUtilization() {
      return verticalUtilization;
    }

    /** @return axial demand magnitude in N/m */
    public double getAxialDemandNPerM() {
      return axialDemandNPerM;
    }

    /** @return externally established axial resistance in N/m */
    public double getAxialResistanceNPerM() {
      return axialResistanceNPerM;
    }

    /** @return axial resistance minus demand in N/m */
    public double getAxialMarginNPerM() {
      return axialMarginNPerM;
    }

    /** @return axial demand divided by resistance */
    public double getAxialUtilization() {
      return axialUtilization;
    }

    /** @return lateral demand magnitude in N/m */
    public double getLateralDemandNPerM() {
      return lateralDemandNPerM;
    }

    /** @return externally established lateral resistance in N/m */
    public double getLateralResistanceNPerM() {
      return lateralResistanceNPerM;
    }

    /** @return lateral resistance minus demand in N/m */
    public double getLateralMarginNPerM() {
      return lateralMarginNPerM;
    }

    /** @return lateral demand divided by resistance */
    public double getLateralUtilization() {
      return lateralUtilization;
    }

    /** @return whether all three caller-controlled resistance constraints are satisfied */
    public boolean allConstraintsSatisfied() {
      return verticalMarginNPerM >= 0.0 && axialMarginNPerM >= 0.0 && lateralMarginNPerM >= 0.0;
    }

    /** @return serializable case representation */
    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("label", label);
      result.put("distanceM", Double.valueOf(distanceM));
      result.put("designSituation", designSituation);
      result.put("verticalDemandNPerM", Double.valueOf(verticalDemandNPerM));
      result.put("verticalResistanceNPerM", Double.valueOf(verticalResistanceNPerM));
      result.put("verticalMarginNPerM", Double.valueOf(verticalMarginNPerM));
      result.put("verticalUtilization", Double.valueOf(verticalUtilization));
      result.put("axialDemandNPerM", Double.valueOf(axialDemandNPerM));
      result.put("axialResistanceNPerM", Double.valueOf(axialResistanceNPerM));
      result.put("axialMarginNPerM", Double.valueOf(axialMarginNPerM));
      result.put("axialUtilization", Double.valueOf(axialUtilization));
      result.put("lateralDemandNPerM", Double.valueOf(lateralDemandNPerM));
      result.put("lateralResistanceNPerM", Double.valueOf(lateralResistanceNPerM));
      result.put("lateralMarginNPerM", Double.valueOf(lateralMarginNPerM));
      result.put("lateralUtilization", Double.valueOf(lateralUtilization));
      result.put("allCallerControlledConstraintsSatisfied", Boolean.valueOf(allConstraintsSatisfied()));
      return result;
    }
  }

  private final String standardEdition;
  private final double pipelineOuterDiameterM;
  private final double submergedWeightNPerM;
  private final List<InteractionCaseAssessment> interactionCases;
  private final double minimumVerticalMarginNPerM;
  private final double maximumVerticalUtilization;
  private final double minimumAxialMarginNPerM;
  private final double maximumAxialUtilization;
  private final double minimumLateralMarginNPerM;
  private final double maximumLateralUtilization;

  DnvRpF114PipeSoilInteractionAssessment(DnvRpF114PipeSoilInteractionScreeningKernel.Input input) {
    standardEdition = input.getEdition().getDisplayName();
    pipelineOuterDiameterM = input.getPipelineOuterDiameterM();
    submergedWeightNPerM = input.getSubmergedWeightNPerM();
    List<InteractionCaseAssessment> results = new ArrayList<InteractionCaseAssessment>();
    double verticalMargin = Double.POSITIVE_INFINITY;
    double verticalUtilization = Double.NEGATIVE_INFINITY;
    double axialMargin = Double.POSITIVE_INFINITY;
    double axialUtilization = Double.NEGATIVE_INFINITY;
    double lateralMargin = Double.POSITIVE_INFINITY;
    double lateralUtilization = Double.NEGATIVE_INFINITY;
    for (DnvRpF114PipeSoilInteractionScreeningKernel.InteractionCase value : input.getInteractionCases()) {
      InteractionCaseAssessment result = new InteractionCaseAssessment(value);
      results.add(result);
      verticalMargin = Math.min(verticalMargin, result.getVerticalMarginNPerM());
      verticalUtilization = Math.max(verticalUtilization, result.getVerticalUtilization());
      axialMargin = Math.min(axialMargin, result.getAxialMarginNPerM());
      axialUtilization = Math.max(axialUtilization, result.getAxialUtilization());
      lateralMargin = Math.min(lateralMargin, result.getLateralMarginNPerM());
      lateralUtilization = Math.max(lateralUtilization, result.getLateralUtilization());
    }
    interactionCases = Collections.unmodifiableList(results);
    minimumVerticalMarginNPerM = verticalMargin;
    maximumVerticalUtilization = verticalUtilization;
    minimumAxialMarginNPerM = axialMargin;
    maximumAxialUtilization = axialUtilization;
    minimumLateralMarginNPerM = lateralMargin;
    maximumLateralUtilization = lateralUtilization;
  }

  /** @return explicit standard edition */
  public String getStandardEdition() {
    return standardEdition;
  }

  /** @return pipeline outside diameter including relevant coatings in m */
  public double getPipelineOuterDiameterM() {
    return pipelineOuterDiameterM;
  }

  /** @return caller-controlled submerged pipe weight in N/m */
  public double getSubmergedWeightNPerM() {
    return submergedWeightNPerM;
  }

  /** @return immutable ordered case results */
  public List<InteractionCaseAssessment> getInteractionCases() {
    return interactionCases;
  }

  /** @return minimum vertical resistance margin in N/m */
  public double getMinimumVerticalMarginNPerM() {
    return minimumVerticalMarginNPerM;
  }

  /** @return maximum vertical utilization */
  public double getMaximumVerticalUtilization() {
    return maximumVerticalUtilization;
  }

  /** @return minimum axial resistance margin in N/m */
  public double getMinimumAxialMarginNPerM() {
    return minimumAxialMarginNPerM;
  }

  /** @return maximum axial utilization */
  public double getMaximumAxialUtilization() {
    return maximumAxialUtilization;
  }

  /** @return minimum lateral resistance margin in N/m */
  public double getMinimumLateralMarginNPerM() {
    return minimumLateralMarginNPerM;
  }

  /** @return maximum lateral utilization */
  public double getMaximumLateralUtilization() {
    return maximumLateralUtilization;
  }

  /** {@inheritDoc} */
  @Override
  public boolean allConstraintsSatisfied() {
    if (interactionCases.isEmpty()) {
      return false;
    }
    for (InteractionCaseAssessment result : interactionCases) {
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
    for (InteractionCaseAssessment interactionCase : interactionCases) {
      caseMaps.add(interactionCase.toMap());
    }
    result.put("standardEdition", standardEdition);
    result.put("scope", "CALLER_CONTROLLED_PIPE_SOIL_RESISTANCE_ENVELOPE_SCREEN");
    result.put("pipelineOuterDiameterM", Double.valueOf(pipelineOuterDiameterM));
    result.put("submergedWeightNPerM", Double.valueOf(submergedWeightNPerM));
    result.put("interactionCases", caseMaps);
    result.put("minimumVerticalMarginNPerM", Double.valueOf(minimumVerticalMarginNPerM));
    result.put("maximumVerticalUtilization", Double.valueOf(maximumVerticalUtilization));
    result.put("minimumAxialMarginNPerM", Double.valueOf(minimumAxialMarginNPerM));
    result.put("maximumAxialUtilization", Double.valueOf(maximumAxialUtilization));
    result.put("minimumLateralMarginNPerM", Double.valueOf(minimumLateralMarginNPerM));
    result.put("maximumLateralUtilization", Double.valueOf(maximumLateralUtilization));
    result.put("allCallerControlledConstraintsSatisfied", Boolean.valueOf(allConstraintsSatisfied()));
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }
}
