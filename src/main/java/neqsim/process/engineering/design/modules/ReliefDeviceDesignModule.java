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

/** Selects an API-style PSV orifice candidate as a converged physical engineering design variable. */
public final class ReliefDeviceDesignModule implements EngineeringDesignModule {
  private static final long serialVersionUID = 1000L;
  private final String deviceTag;
  private final String protectedEquipmentTag;
  private final String requiredAreaMetricId;
  private final double[] orificeCandidatesIn2;

  public ReliefDeviceDesignModule(String deviceTag, String protectedEquipmentTag, String requiredAreaMetricId,
      double... orificeCandidatesIn2) {
    this.deviceTag = text(deviceTag, "deviceTag");
    this.protectedEquipmentTag = text(protectedEquipmentTag, "protectedEquipmentTag");
    this.requiredAreaMetricId = text(requiredAreaMetricId, "requiredAreaMetricId");
    if (orificeCandidatesIn2 == null || orificeCandidatesIn2.length == 0) {
      throw new IllegalArgumentException("At least one PSV orifice candidate is required");
    }
    this.orificeCandidatesIn2 = orificeCandidatesIn2.clone();
    for (double candidate : this.orificeCandidatesIn2) {
      if (!Double.isFinite(candidate) || candidate <= 0.0) {
        throw new IllegalArgumentException("PSV orifice candidates must be finite and positive");
      }
    }
  }

  @Override
  public String getId() {
    return "55-relief-device-design-" + deviceTag;
  }

  @Override
  public EngineeringDesignModuleResult evaluate(ProcessSystem process, EngineeringCaseRunReport caseReport,
      EngineeringDesignState state, EngineeringCalculationContext context) {
    EngineeringDesignEnvelope.GoverningValue required = DesignModuleSupport.requireMetric(caseReport,
        requiredAreaMetricId);
    double selected = select(required.getValue());
    String areaKey = deviceTag + ".selectedOrificeArea";
    String utilizationKey = deviceTag + ".orificeAreaUtilization";
    return EngineeringDesignModuleResult.builder(getId(), "API 520 project-basis discrete orifice selection", "1.0")
        .addUpdate(EngineeringDesignUpdate.builder(areaKey, required.getValue(), "in2").candidates(orificeCandidatesIn2)
            .governingCaseId(required.getDesignCaseId()).build())
        .addUpdate(EngineeringDesignUpdate.builder(utilizationKey, required.getValue() / selected, "fraction")
            .governingCaseId(required.getDesignCaseId()).build())
        .addConstraint(new EngineeringDesignConstraint(deviceTag + ".orifice-capacity",
            "Selected PSV orifice covers the governing scenario", utilizationKey, 1.0, "fraction",
            EngineeringDesignConstraint.Comparison.MAXIMUM))
        .evidence("protectedEquipmentTag", protectedEquipmentTag)
        .evidence("standard", "API 520 project-applicable edition")
        .warning("Fluid model, stability, inlet loss, backpressure and certified capacity require final verification")
        .build();
  }

  private double select(double required) {
    double selected = Double.POSITIVE_INFINITY;
    for (double candidate : orificeCandidatesIn2) {
      if (candidate >= required) {
        selected = Math.min(selected, candidate);
      }
    }
    if (!Double.isFinite(selected)) {
      throw new IllegalStateException("No PSV orifice candidate satisfies " + required + " in2 for " + deviceTag);
    }
    return selected;
  }

  private static String text(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
