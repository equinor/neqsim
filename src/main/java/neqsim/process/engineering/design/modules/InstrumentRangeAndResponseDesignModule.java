package neqsim.process.engineering.design.modules;

import neqsim.process.engineering.calculation.EngineeringCalculationContext;
import neqsim.process.engineering.design.EngineeringDesignConstraint;
import neqsim.process.engineering.design.EngineeringDesignModule;
import neqsim.process.engineering.design.EngineeringDesignModuleResult;
import neqsim.process.engineering.design.EngineeringDesignState;
import neqsim.process.engineering.design.EngineeringDesignUpdate;
import neqsim.process.engineering.designcase.EngineeringCaseRunReport;
import neqsim.process.engineering.designcase.EngineeringDesignEnvelope;
import neqsim.process.processmodel.ProcessSystem;

/** Selects an instrument range and verifies a declared process-safety-time response budget. */
public final class InstrumentRangeAndResponseDesignModule implements EngineeringDesignModule {
  private static final long serialVersionUID = 1000L;
  private final String instrumentTag;
  private final String measuredEquipmentTag;
  private final String maximumValueMetricId;
  private final String unit;
  private final double rangeMarginFraction;
  private final double uncertaintyFractionOfSpan;
  private final double processSafetyTimeSeconds;
  private final double sensorLogicAndFinalElementSeconds;
  private final double[] rangeCandidates;

  public InstrumentRangeAndResponseDesignModule(String instrumentTag, String measuredEquipmentTag,
      String maximumValueMetricId, String unit, double rangeMarginFraction, double uncertaintyFractionOfSpan,
      double processSafetyTimeSeconds, double sensorLogicAndFinalElementSeconds, double[] rangeCandidates) {
    this.instrumentTag = instrumentTag;
    this.measuredEquipmentTag = measuredEquipmentTag;
    this.maximumValueMetricId = maximumValueMetricId;
    this.unit = unit;
    this.rangeMarginFraction = rangeMarginFraction;
    this.uncertaintyFractionOfSpan = uncertaintyFractionOfSpan;
    this.processSafetyTimeSeconds = processSafetyTimeSeconds;
    this.sensorLogicAndFinalElementSeconds = sensorLogicAndFinalElementSeconds;
    this.rangeCandidates = rangeCandidates.clone();
  }

  @Override
  public String getId() {
    return "60-instrument-design-" + instrumentTag;
  }

  @Override
  public EngineeringDesignModuleResult evaluate(ProcessSystem process, EngineeringCaseRunReport caseReport,
      EngineeringDesignState state, EngineeringCalculationContext context) {
    EngineeringDesignEnvelope.GoverningValue maximum = DesignModuleSupport.requireMetric(caseReport,
        maximumValueMetricId);
    double requiredUpperRange = maximum.getValue() * (1.0 + rangeMarginFraction);
    double selectedUpperRange = select(requiredUpperRange);
    double uncertainty = selectedUpperRange * uncertaintyFractionOfSpan;
    double responseMargin = processSafetyTimeSeconds - sensorLogicAndFinalElementSeconds;
    String rangeKey = instrumentTag + ".upperRangeValue";
    String uncertaintyKey = instrumentTag + ".measurementUncertainty";
    String responseKey = instrumentTag + ".responseTimeMargin";

    return EngineeringDesignModuleResult.builder(getId(), "Instrument range and SIF response-budget screening", "1.0")
        .addUpdate(EngineeringDesignUpdate.builder(rangeKey, requiredUpperRange, unit).candidates(rangeCandidates)
            .governingCaseId(maximum.getDesignCaseId()).build())
        .addUpdate(EngineeringDesignUpdate.builder(uncertaintyKey, uncertainty, unit)
            .governingCaseId(maximum.getDesignCaseId()).build())
        .addUpdate(EngineeringDesignUpdate.builder(responseKey, responseMargin, "s").build())
        .addConstraint(new EngineeringDesignConstraint(instrumentTag + ".range", "Instrument range covers envelope",
            rangeKey, maximum.getValue(), unit, EngineeringDesignConstraint.Comparison.MINIMUM))
        .addConstraint(new EngineeringDesignConstraint(instrumentTag + ".response-budget",
            "Declared response remains within process safety time", responseKey, 0.0, "s",
            EngineeringDesignConstraint.Comparison.MINIMUM))
        .evidence("measuredEquipmentTag", measuredEquipmentTag).evidence("standard", "NORSOK I-001; IEC 61511")
        .warning(
            "Certified accuracy, architecture, voting, diagnostics, proof test and SIL verification are project data")
        .build();
  }

  private double select(double required) {
    double selected = Double.POSITIVE_INFINITY;
    for (double candidate : rangeCandidates) {
      if (candidate >= required) {
        selected = Math.min(selected, candidate);
      }
    }
    if (!Double.isFinite(selected)) {
      throw new IllegalStateException("No instrument range candidate satisfies " + required + " " + unit);
    }
    return selected;
  }
}
