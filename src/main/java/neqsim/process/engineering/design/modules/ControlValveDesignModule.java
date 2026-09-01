package neqsim.process.engineering.design.modules;

import neqsim.process.engineering.calculation.EngineeringCalculationContext;
import neqsim.process.engineering.design.EngineeringDesignConstraint;
import neqsim.process.engineering.design.EngineeringDesignModule;
import neqsim.process.engineering.design.EngineeringDesignModuleResult;
import neqsim.process.engineering.design.EngineeringDesignState;
import neqsim.process.engineering.design.EngineeringDesignUpdate;
import neqsim.process.engineering.designcase.EngineeringCaseRunReport;
import neqsim.process.engineering.designcase.EngineeringDesignEnvelope;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;

/** Selects a control-valve Cv using NeqSim's configured IEC 60534 sizing method. */
public final class ControlValveDesignModule implements EngineeringDesignModule {
  private static final long serialVersionUID = 1000L;
  private final String valveTag;
  private final String requiredCvMetricId;
  private final double designOpeningPercent;
  private final double maximumOpeningPercent;
  private final double[] cvCandidates;

  public ControlValveDesignModule(String valveTag, String requiredCvMetricId, double designOpeningPercent,
      double maximumOpeningPercent) {
    this(valveTag, requiredCvMetricId, designOpeningPercent, maximumOpeningPercent,
        DesignModuleSupport.defaultCvCandidates());
  }

  public ControlValveDesignModule(String valveTag, String requiredCvMetricId, double designOpeningPercent,
      double maximumOpeningPercent, double[] cvCandidates) {
    this.valveTag = valveTag;
    this.requiredCvMetricId = requiredCvMetricId;
    this.designOpeningPercent = designOpeningPercent;
    this.maximumOpeningPercent = maximumOpeningPercent;
    this.cvCandidates = cvCandidates.clone();
  }

  @Override
  public String getId() {
    return "40-control-valve-design-" + valveTag;
  }

  @Override
  public EngineeringDesignModuleResult evaluate(ProcessSystem process, EngineeringCaseRunReport caseReport,
      EngineeringDesignState state, EngineeringCalculationContext context) {
    if (!(process.getUnit(valveTag) instanceof ThrottlingValve)) {
      throw new IllegalArgumentException(valveTag + " is not a ThrottlingValve");
    }
    EngineeringDesignEnvelope.GoverningValue required = DesignModuleSupport.requireMetric(caseReport,
        requiredCvMetricId);
    double selectedCv = select(required.getValue());
    double predictedOpening = designOpeningPercent * required.getValue() / selectedCv;
    String cvKey = valveTag + ".selectedCv";
    String openingKey = valveTag + ".governingOpening";

    return EngineeringDesignModuleResult.builder(getId(), "IEC 60534 valve sizing with discrete Cv selection", "1.0")
        .addUpdate(EngineeringDesignUpdate.builder(cvKey, required.getValue(), "Cv").candidates(cvCandidates)
            .governingCaseId(required.getDesignCaseId()).applier(new EngineeringDesignUpdate.Applier() {
              private static final long serialVersionUID = 1000L;

              @Override
              public void apply(ProcessSystem working, double value) {
                ((ThrottlingValve) working.getUnit(valveTag)).setCv(value);
              }
            }).build())
        .addUpdate(EngineeringDesignUpdate.builder(openingKey, predictedOpening, "%")
            .governingCaseId(required.getDesignCaseId()).build())
        .addConstraint(
            new EngineeringDesignConstraint(valveTag + ".maximum-opening", "Maximum predicted control-valve opening",
                openingKey, maximumOpeningPercent, "%", EngineeringDesignConstraint.Comparison.MAXIMUM))
        .evidence("selectedCv", Double.valueOf(selectedCv)).evidence("standard", "IEC 60534")
        .warning("Actuator thrust, shutoff differential pressure, noise, flashing and cavitation require final review")
        .build();
  }

  private double select(double required) {
    double selected = Double.POSITIVE_INFINITY;
    for (double candidate : cvCandidates) {
      if (candidate >= required) {
        selected = Math.min(selected, candidate);
      }
    }
    if (!Double.isFinite(selected)) {
      throw new IllegalStateException("No Cv candidate satisfies " + required + " for " + valveTag);
    }
    return selected;
  }
}
