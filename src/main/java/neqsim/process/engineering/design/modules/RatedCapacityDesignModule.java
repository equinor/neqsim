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

/** Generic discrete rating selection for pumps, exchangers, heaters, columns, tanks, and utilities. */
public final class RatedCapacityDesignModule implements EngineeringDesignModule {
  private static final long serialVersionUID = 1000L;
  private final String equipmentTag;
  private final String metricId;
  private final String capacityName;
  private final String unit;
  private final double marginFraction;
  private final double[] candidates;

  public RatedCapacityDesignModule(String equipmentTag, String metricId, String capacityName, String unit,
      double marginFraction, double[] candidates) {
    this.equipmentTag = equipmentTag;
    this.metricId = metricId;
    this.capacityName = capacityName;
    this.unit = unit;
    this.marginFraction = marginFraction;
    this.candidates = candidates.clone();
  }

  @Override
  public String getId() {
    return "35-rated-capacity-" + equipmentTag + "-" + capacityName;
  }

  @Override
  public EngineeringDesignModuleResult evaluate(ProcessSystem process, EngineeringCaseRunReport caseReport,
      EngineeringDesignState state, EngineeringCalculationContext context) {
    EngineeringDesignEnvelope.GoverningValue governing = DesignModuleSupport.requireMetric(caseReport, metricId);
    double required = governing.getValue() * (1.0 + marginFraction);
    double selected = select(required);
    String ratingKey = equipmentTag + "." + capacityName;
    String utilizationKey = ratingKey + "Utilization";
    return EngineeringDesignModuleResult.builder(getId(), "Governing-case discrete equipment rating", "1.0")
        .addUpdate(EngineeringDesignUpdate.builder(ratingKey, required, unit).candidates(candidates)
            .governingCaseId(governing.getDesignCaseId()).build())
        .addUpdate(EngineeringDesignUpdate.builder(utilizationKey, governing.getValue() / selected, "fraction")
            .governingCaseId(governing.getDesignCaseId()).build())
        .addConstraint(new EngineeringDesignConstraint(ratingKey + ".capacity", "Rated capacity exceeds duty",
            utilizationKey, 1.0, "fraction", EngineeringDesignConstraint.Comparison.MAXIMUM))
        .evidence("marginFraction", Double.valueOf(marginFraction)).build();
  }

  private double select(double required) {
    double selected = Double.POSITIVE_INFINITY;
    for (double candidate : candidates) {
      if (candidate >= required) {
        selected = Math.min(selected, candidate);
      }
    }
    if (!Double.isFinite(selected)) {
      throw new IllegalStateException("No rating candidate satisfies " + equipmentTag + "." + capacityName);
    }
    return selected;
  }
}
