package neqsim.process.engineering.calculation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable caller-controlled CO2 pipeline transport-envelope screening result. */
public final class DnvRpF104Co2PipelineEnvelopeAssessment implements EngineeringConstraintResult, Serializable {
  private static final long serialVersionUID = 1000L;

  /** Immutable result for one ordered operating point. */
  public static final class OperatingPointAssessment implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String label;
    private final double distanceM;
    private final double pressurePaAbsolute;
    private final double temperatureK;
    private final double callerControlledMinimumSinglePhasePressurePaAbsolute;
    private final double singlePhasePressureMarginPa;
    private final double maximumAllowableOperatingPressureMarginPa;
    private final double minimumTemperatureMarginK;
    private final double maximumTemperatureMarginK;

    OperatingPointAssessment(DnvRpF104Co2PipelineEnvelopeScreeningKernel.OperatingPoint point,
        DnvRpF104Co2PipelineEnvelopeScreeningKernel.Input input) {
      label = point.getLabel();
      distanceM = point.getDistanceM();
      pressurePaAbsolute = point.getPressurePaAbsolute();
      temperatureK = point.getTemperatureK();
      callerControlledMinimumSinglePhasePressurePaAbsolute = point
          .getCallerControlledMinimumSinglePhasePressurePaAbsolute();
      singlePhasePressureMarginPa = pressurePaAbsolute - callerControlledMinimumSinglePhasePressurePaAbsolute;
      maximumAllowableOperatingPressureMarginPa = input.getMaximumAllowableOperatingPressurePaAbsolute()
          - pressurePaAbsolute;
      minimumTemperatureMarginK = temperatureK - input.getDesignMinimumTemperatureK();
      maximumTemperatureMarginK = input.getDesignMaximumTemperatureK() - temperatureK;
    }

    /** @return caller-controlled point label */
    public String getLabel() {
      return label;
    }

    /** @return distance from the profile origin in m */
    public double getDistanceM() {
      return distanceM;
    }

    /** @return operating pressure in Pa absolute */
    public double getPressurePaAbsolute() {
      return pressurePaAbsolute;
    }

    /** @return operating temperature in K */
    public double getTemperatureK() {
      return temperatureK;
    }

    /** @return caller-controlled minimum single-phase pressure boundary in Pa absolute */
    public double getCallerControlledMinimumSinglePhasePressurePaAbsolute() {
      return callerControlledMinimumSinglePhasePressurePaAbsolute;
    }

    /** @return operating pressure minus the caller-controlled phase boundary in Pa */
    public double getSinglePhasePressureMarginPa() {
      return singlePhasePressureMarginPa;
    }

    /** @return maximum allowable operating pressure minus operating pressure in Pa */
    public double getMaximumAllowableOperatingPressureMarginPa() {
      return maximumAllowableOperatingPressureMarginPa;
    }

    /** @return operating temperature minus design minimum temperature in K */
    public double getMinimumTemperatureMarginK() {
      return minimumTemperatureMarginK;
    }

    /** @return design maximum temperature minus operating temperature in K */
    public double getMaximumTemperatureMarginK() {
      return maximumTemperatureMarginK;
    }

    /** @return whether all four caller-controlled point constraints are satisfied */
    public boolean allConstraintsSatisfied() {
      return singlePhasePressureMarginPa >= 0.0 && maximumAllowableOperatingPressureMarginPa >= 0.0
          && minimumTemperatureMarginK >= 0.0 && maximumTemperatureMarginK >= 0.0;
    }

    /** @return serializable point representation */
    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("label", label);
      result.put("distanceM", Double.valueOf(distanceM));
      result.put("pressurePaAbsolute", Double.valueOf(pressurePaAbsolute));
      result.put("temperatureK", Double.valueOf(temperatureK));
      result.put("callerControlledMinimumSinglePhasePressurePaAbsolute",
          Double.valueOf(callerControlledMinimumSinglePhasePressurePaAbsolute));
      result.put("singlePhasePressureMarginPa", Double.valueOf(singlePhasePressureMarginPa));
      result.put("maximumAllowableOperatingPressureMarginPa",
          Double.valueOf(maximumAllowableOperatingPressureMarginPa));
      result.put("minimumTemperatureMarginK", Double.valueOf(minimumTemperatureMarginK));
      result.put("maximumTemperatureMarginK", Double.valueOf(maximumTemperatureMarginK));
      result.put("allCallerControlledPointConstraintsSatisfied", Boolean.valueOf(allConstraintsSatisfied()));
      return result;
    }
  }

  private final String standardEdition;
  private final double co2MoleFractionMargin;
  private final double waterMoleFractionMargin;
  private final boolean co2FractionWithinProjectSpecification;
  private final boolean waterFractionWithinProjectSpecification;
  private final boolean otherImpuritiesWithinProjectSpecification;
  private final List<OperatingPointAssessment> operatingPoints;
  private final double minimumSinglePhasePressureMarginPa;
  private final double minimumMaximumAllowableOperatingPressureMarginPa;
  private final double minimumLowTemperatureMarginK;
  private final double minimumHighTemperatureMarginK;

  DnvRpF104Co2PipelineEnvelopeAssessment(DnvRpF104Co2PipelineEnvelopeScreeningKernel.Input input) {
    standardEdition = input.getEdition().getDisplayName();
    co2MoleFractionMargin = input.getCo2MoleFraction() - input.getMinimumCo2MoleFraction();
    waterMoleFractionMargin = input.getMaximumWaterMoleFraction() - input.getWaterMoleFraction();
    co2FractionWithinProjectSpecification = co2MoleFractionMargin >= 0.0;
    waterFractionWithinProjectSpecification = waterMoleFractionMargin >= 0.0;
    otherImpuritiesWithinProjectSpecification = input.isOtherImpuritiesWithinProjectSpecification();

    List<OperatingPointAssessment> pointResults = new ArrayList<OperatingPointAssessment>();
    double phaseMargin = Double.POSITIVE_INFINITY;
    double maopMargin = Double.POSITIVE_INFINITY;
    double lowTemperatureMargin = Double.POSITIVE_INFINITY;
    double highTemperatureMargin = Double.POSITIVE_INFINITY;
    for (DnvRpF104Co2PipelineEnvelopeScreeningKernel.OperatingPoint point : input.getOperatingPoints()) {
      OperatingPointAssessment pointResult = new OperatingPointAssessment(point, input);
      pointResults.add(pointResult);
      phaseMargin = Math.min(phaseMargin, pointResult.getSinglePhasePressureMarginPa());
      maopMargin = Math.min(maopMargin, pointResult.getMaximumAllowableOperatingPressureMarginPa());
      lowTemperatureMargin = Math.min(lowTemperatureMargin, pointResult.getMinimumTemperatureMarginK());
      highTemperatureMargin = Math.min(highTemperatureMargin, pointResult.getMaximumTemperatureMarginK());
    }
    operatingPoints = Collections.unmodifiableList(pointResults);
    minimumSinglePhasePressureMarginPa = phaseMargin;
    minimumMaximumAllowableOperatingPressureMarginPa = maopMargin;
    minimumLowTemperatureMarginK = lowTemperatureMargin;
    minimumHighTemperatureMarginK = highTemperatureMargin;
  }

  /** @return explicit standard edition */
  public String getStandardEdition() {
    return standardEdition;
  }

  /** @return actual minus minimum project CO2 mole fraction */
  public double getCo2MoleFractionMargin() {
    return co2MoleFractionMargin;
  }

  /** @return maximum project water mole fraction minus actual water mole fraction */
  public double getWaterMoleFractionMargin() {
    return waterMoleFractionMargin;
  }

  /** @return whether the supplied CO2 fraction meets the project minimum */
  public boolean isCo2FractionWithinProjectSpecification() {
    return co2FractionWithinProjectSpecification;
  }

  /** @return whether the supplied water fraction meets the project maximum */
  public boolean isWaterFractionWithinProjectSpecification() {
    return waterFractionWithinProjectSpecification;
  }

  /** @return caller assertion for all other project impurity constraints */
  public boolean areOtherImpuritiesWithinProjectSpecification() {
    return otherImpuritiesWithinProjectSpecification;
  }

  /** @return immutable ordered operating-point results */
  public List<OperatingPointAssessment> getOperatingPoints() {
    return operatingPoints;
  }

  /** @return minimum pressure margin above the caller-controlled single-phase boundary in Pa */
  public double getMinimumSinglePhasePressureMarginPa() {
    return minimumSinglePhasePressureMarginPa;
  }

  /** @return minimum pressure margin below maximum allowable operating pressure in Pa */
  public double getMinimumMaximumAllowableOperatingPressureMarginPa() {
    return minimumMaximumAllowableOperatingPressureMarginPa;
  }

  /** @return minimum temperature margin above the design minimum in K */
  public double getMinimumLowTemperatureMarginK() {
    return minimumLowTemperatureMarginK;
  }

  /** @return minimum temperature margin below the design maximum in K */
  public double getMinimumHighTemperatureMarginK() {
    return minimumHighTemperatureMarginK;
  }

  /** {@inheritDoc} */
  @Override
  public boolean allConstraintsSatisfied() {
    if (!co2FractionWithinProjectSpecification || !waterFractionWithinProjectSpecification
        || !otherImpuritiesWithinProjectSpecification || operatingPoints.isEmpty()) {
      return false;
    }
    for (OperatingPointAssessment point : operatingPoints) {
      if (!point.allConstraintsSatisfied()) {
        return false;
      }
    }
    return true;
  }

  /** @return serializable assessment representation */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    List<Map<String, Object>> pointMaps = new ArrayList<Map<String, Object>>();
    for (OperatingPointAssessment point : operatingPoints) {
      pointMaps.add(point.toMap());
    }
    result.put("standardEdition", standardEdition);
    result.put("scope", "CALLER_CONTROLLED_CO2_TRANSPORT_ENVELOPE_SCREEN");
    result.put("co2MoleFractionMargin", Double.valueOf(co2MoleFractionMargin));
    result.put("waterMoleFractionMargin", Double.valueOf(waterMoleFractionMargin));
    result.put("co2FractionWithinProjectSpecification", Boolean.valueOf(co2FractionWithinProjectSpecification));
    result.put("waterFractionWithinProjectSpecification", Boolean.valueOf(waterFractionWithinProjectSpecification));
    result.put("otherImpuritiesWithinProjectSpecification", Boolean.valueOf(otherImpuritiesWithinProjectSpecification));
    result.put("operatingPoints", pointMaps);
    result.put("minimumSinglePhasePressureMarginPa", Double.valueOf(minimumSinglePhasePressureMarginPa));
    result.put("minimumMaximumAllowableOperatingPressureMarginPa",
        Double.valueOf(minimumMaximumAllowableOperatingPressureMarginPa));
    result.put("minimumLowTemperatureMarginK", Double.valueOf(minimumLowTemperatureMarginK));
    result.put("minimumHighTemperatureMarginK", Double.valueOf(minimumHighTemperatureMarginK));
    result.put("allCallerControlledConstraintsSatisfied", Boolean.valueOf(allConstraintsSatisfied()));
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }
}
