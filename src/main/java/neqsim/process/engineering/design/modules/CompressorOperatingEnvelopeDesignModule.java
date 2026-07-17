package neqsim.process.engineering.design.modules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.calculation.EngineeringCalculationContext;
import neqsim.process.engineering.design.EngineeringDesignConstraint;
import neqsim.process.engineering.design.EngineeringDesignModule;
import neqsim.process.engineering.design.EngineeringDesignModuleResult;
import neqsim.process.engineering.design.EngineeringDesignState;
import neqsim.process.engineering.design.EngineeringDesignUpdate;
import neqsim.process.engineering.designcase.DesignCaseResult;
import neqsim.process.engineering.designcase.EngineeringCaseRunReport;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.processmodel.ProcessSystem;

/** Verifies a charted compressor operating envelope and anti-surge demand across every design case. */
public final class CompressorOperatingEnvelopeDesignModule implements EngineeringDesignModule {
  private static final long serialVersionUID = 1000L;
  private final String compressorTag;
  private final double minimumSurgeMarginFraction;
  private final double minimumStonewallMarginFraction;
  private final double maximumDischargeTemperatureC;
  private final double surgeControlMarginFraction;

  public CompressorOperatingEnvelopeDesignModule(String compressorTag, double minimumSurgeMarginFraction,
      double minimumStonewallMarginFraction, double maximumDischargeTemperatureC,
      double surgeControlMarginFraction) {
    this.compressorTag = requireText(compressorTag, "compressorTag");
    this.minimumSurgeMarginFraction = nonNegative(minimumSurgeMarginFraction, "minimumSurgeMarginFraction");
    this.minimumStonewallMarginFraction = nonNegative(minimumStonewallMarginFraction,
        "minimumStonewallMarginFraction");
    this.maximumDischargeTemperatureC = finite(maximumDischargeTemperatureC, "maximumDischargeTemperatureC");
    this.surgeControlMarginFraction = nonNegative(surgeControlMarginFraction, "surgeControlMarginFraction");
  }

  @Override
  public String getId() {
    return "35-compressor-operating-envelope-" + compressorTag;
  }

  @Override
  public EngineeringDesignModuleResult evaluate(ProcessSystem process, EngineeringCaseRunReport caseReport,
      EngineeringDesignState state, EngineeringCalculationContext context) {
    if (!(process.getUnit(compressorTag) instanceof Compressor)) {
      throw new IllegalArgumentException(compressorTag + " is not a Compressor");
    }
    Compressor compressor = (Compressor) process.getUnit(compressorTag);
    if (compressor.getCompressorChart() == null || !compressor.getCompressorChart().isUseCompressorChart()) {
      throw new IllegalStateException("A governed compressor chart is required for " + compressorTag);
    }
    if (compressor.getCompressorChart().getSurgeCurve() == null
        || !compressor.getCompressorChart().getSurgeCurve().isActive()) {
      throw new IllegalStateException("An active surge curve is required for " + compressorTag);
    }
    if (compressor.getCompressorChart().getStoneWallCurve() == null
        || !compressor.getCompressorChart().getStoneWallCurve().isActive()) {
      throw new IllegalStateException("An active stonewall curve is required for " + compressorTag);
    }

    String surgeMetric = compressorTag + ".surgeMargin";
    String stonewallMetric = compressorTag + ".stonewallMargin";
    String controlLineMetric = compressorTag + ".controlLineMargin";
    String recycleMetric = compressorTag + ".requiredRecycleFraction";
    String recycleDutyMetric = compressorTag + ".recycleCoolerDuty";
    String temperatureMetric = compressorTag + ".dischargeTemperature";
    String extrapolationMetric = compressorTag + ".chartExtrapolated";
    String speedMetric = compressorTag + ".speed";
    String headMetric = compressorTag + ".polytropicHead";
    String efficiencyMetric = compressorTag + ".polytropicEfficiency";

    CaseValue minimumSurge = minimum(caseReport, surgeMetric);
    CaseValue minimumStonewall = minimum(caseReport, stonewallMetric);
    CaseValue minimumControlLine = minimum(caseReport, controlLineMetric);
    CaseValue maximumRecycle = maximum(caseReport, recycleMetric);
    CaseValue maximumRecycleDuty = maximum(caseReport, recycleDutyMetric);
    CaseValue maximumTemperature = maximum(caseReport, temperatureMetric);
    CaseValue maximumExtrapolation = maximum(caseReport, extrapolationMetric);
    CaseValue maximumSpeed = maximum(caseReport, speedMetric);
    List<Map<String, Object>> operatingPoints = operatingPoints(caseReport, headMetric, speedMetric, efficiencyMetric,
        surgeMetric, stonewallMetric, controlLineMetric, recycleMetric, temperatureMetric, extrapolationMetric);

    String surgeKey = compressorTag + ".minimumSurgeMargin";
    String stonewallKey = compressorTag + ".minimumStonewallMargin";
    String controlLineKey = compressorTag + ".minimumControlLineMargin";
    String recycleKey = compressorTag + ".maximumRequiredRecycleFraction";
    String recycleDutyKey = compressorTag + ".maximumRecycleCoolerDuty";
    String temperatureKey = compressorTag + ".maximumDischargeTemperature";
    String extrapolationKey = compressorTag + ".chartExtrapolationCount";
    String speedKey = compressorTag + ".maximumSpeed";
    String controlMarginKey = compressorTag + ".surgeControlMargin";

    return EngineeringDesignModuleResult
        .builder(getId(), "Compressor map, surge, stonewall and anti-surge case-envelope verification", "2.0")
        .addUpdate(EngineeringDesignUpdate.builder(controlMarginKey, surgeControlMarginFraction, "fraction")
            .applier(new EngineeringDesignUpdate.Applier() {
              private static final long serialVersionUID = 1000L;

              @Override
              public void apply(ProcessSystem working, double value) {
                ((Compressor) working.getUnit(compressorTag)).setSurgeControlMargin(value);
              }
            }).build())
        .addUpdate(EngineeringDesignUpdate.builder(surgeKey, minimumSurge.value, "fraction")
            .governingCaseId(minimumSurge.caseId).build())
        .addUpdate(EngineeringDesignUpdate.builder(stonewallKey, minimumStonewall.value, "fraction")
            .governingCaseId(minimumStonewall.caseId).build())
        .addUpdate(EngineeringDesignUpdate.builder(controlLineKey, minimumControlLine.value, "fraction")
            .governingCaseId(minimumControlLine.caseId).build())
        .addUpdate(EngineeringDesignUpdate.builder(recycleKey, maximumRecycle.value, "fraction")
            .governingCaseId(maximumRecycle.caseId).build())
        .addUpdate(EngineeringDesignUpdate.builder(recycleDutyKey, maximumRecycleDuty.value, "kW")
            .governingCaseId(maximumRecycleDuty.caseId).build())
        .addUpdate(EngineeringDesignUpdate.builder(temperatureKey, maximumTemperature.value, "C")
            .governingCaseId(maximumTemperature.caseId).build())
        .addUpdate(EngineeringDesignUpdate.builder(extrapolationKey, maximumExtrapolation.value, "flag")
            .governingCaseId(maximumExtrapolation.caseId).build())
        .addUpdate(EngineeringDesignUpdate.builder(speedKey, maximumSpeed.value, "rpm")
            .governingCaseId(maximumSpeed.caseId).build())
        .addConstraint(new EngineeringDesignConstraint(compressorTag + ".surge-margin",
            "Minimum distance to surge line", surgeKey, minimumSurgeMarginFraction, "fraction",
            EngineeringDesignConstraint.Comparison.MINIMUM))
        .addConstraint(new EngineeringDesignConstraint(compressorTag + ".stonewall-margin",
            "Minimum distance to stonewall/choke", stonewallKey, minimumStonewallMarginFraction, "fraction",
            EngineeringDesignConstraint.Comparison.MINIMUM))
        .addConstraint(new EngineeringDesignConstraint(compressorTag + ".chart-extrapolation",
            "No compressor-map extrapolation", extrapolationKey, 0.0, "flag",
            EngineeringDesignConstraint.Comparison.MAXIMUM))
        .addConstraint(new EngineeringDesignConstraint(compressorTag + ".discharge-temperature",
            "Maximum compressor discharge temperature", temperatureKey, maximumDischargeTemperatureC, "C",
            EngineeringDesignConstraint.Comparison.MAXIMUM))
        .evidence("operatingPoints", operatingPoints)
        .evidence("surgeControlMarginFraction", Double.valueOf(surgeControlMarginFraction))
        .warning("Vendor map, transient anti-surge response, driver starting and package guarantees require approval")
        .build();
  }

  private static List<Map<String, Object>> operatingPoints(EngineeringCaseRunReport report, String... metricIds) {
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (DesignCaseResult designCase : report.getEnvelope().getCaseResults()) {
      if (!"CALCULATED".equals(designCase.getStatus())) {
        continue;
      }
      Map<String, Object> point = new LinkedHashMap<String, Object>();
      point.put("caseId", designCase.getDesignCase().getId());
      point.put("caseType", designCase.getDesignCase().getType().name());
      for (String metricId : metricIds) {
        Double value = designCase.getValues().get(metricId);
        if (value == null || !Double.isFinite(value.doubleValue())) {
          throw new IllegalStateException("Missing finite compressor metric " + metricId + " for case "
              + designCase.getDesignCase().getId());
        }
        point.put(metricId.substring(metricId.lastIndexOf('.') + 1), value);
      }
      result.add(point);
    }
    if (result.isEmpty()) {
      throw new IllegalStateException("No calculated compressor operating points are available");
    }
    return result;
  }

  private static CaseValue minimum(EngineeringCaseRunReport report, String metricId) {
    return extremum(report, metricId, false);
  }

  private static CaseValue maximum(EngineeringCaseRunReport report, String metricId) {
    return extremum(report, metricId, true);
  }

  private static CaseValue extremum(EngineeringCaseRunReport report, String metricId, boolean maximum) {
    CaseValue selected = null;
    for (DesignCaseResult designCase : report.getEnvelope().getCaseResults()) {
      Double value = designCase.getValues().get(metricId);
      if (value == null || !Double.isFinite(value.doubleValue())) {
        continue;
      }
      if (selected == null || maximum && value.doubleValue() > selected.value
          || !maximum && value.doubleValue() < selected.value) {
        selected = new CaseValue(designCase.getDesignCase().getId(), value.doubleValue());
      }
    }
    if (selected == null) {
      throw new IllegalStateException("No finite case value is available for " + metricId);
    }
    return selected;
  }

  private static final class CaseValue {
    private final String caseId;
    private final double value;

    CaseValue(String caseId, double value) {
      this.caseId = caseId;
      this.value = value;
    }
  }

  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  private static double nonNegative(double value, String field) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(field + " must be finite and non-negative");
    }
    return value;
  }

  private static double finite(double value, String field) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(field + " must be finite");
    }
    return value;
  }
}
