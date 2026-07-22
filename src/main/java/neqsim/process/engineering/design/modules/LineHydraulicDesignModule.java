package neqsim.process.engineering.design.modules;

import neqsim.process.engineering.calculation.EngineeringCalculationContext;
import neqsim.process.engineering.design.EngineeringDesignConstraint;
import neqsim.process.engineering.design.EngineeringDesignModule;
import neqsim.process.engineering.design.EngineeringDesignModuleResult;
import neqsim.process.engineering.design.EngineeringDesignState;
import neqsim.process.engineering.design.EngineeringDesignUpdate;
import neqsim.process.engineering.designcase.EngineeringCaseRunReport;
import neqsim.process.engineering.designcase.EngineeringDesignEnvelope;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.pipeline.PipeLineInterface;
import neqsim.process.processmodel.ProcessSystem;

/** Selects a discrete line diameter from velocity and simulated pressure-gradient constraints. */
public final class LineHydraulicDesignModule implements EngineeringDesignModule {
  private static final long serialVersionUID = 1000L;
  private final String lineTag;
  private final String volumeFlowMetricId;
  private final String pressureDropMetricId;
  private final double maximumVelocityMPerS;
  private final double maximumPressureGradientBarPerKm;
  private final double[] diameterCandidates;

  public LineHydraulicDesignModule(String lineTag, String volumeFlowMetricId, String pressureDropMetricId,
      double maximumVelocityMPerS, double maximumPressureGradientBarPerKm) {
    this(lineTag, volumeFlowMetricId, pressureDropMetricId, maximumVelocityMPerS, maximumPressureGradientBarPerKm,
        DesignModuleSupport.defaultPipeDiametersMeters());
  }

  public LineHydraulicDesignModule(String lineTag, String volumeFlowMetricId, String pressureDropMetricId,
      double maximumVelocityMPerS, double maximumPressureGradientBarPerKm, double[] diameterCandidates) {
    this.lineTag = lineTag;
    this.volumeFlowMetricId = volumeFlowMetricId;
    this.pressureDropMetricId = pressureDropMetricId;
    this.maximumVelocityMPerS = maximumVelocityMPerS;
    this.maximumPressureGradientBarPerKm = maximumPressureGradientBarPerKm;
    this.diameterCandidates = diameterCandidates.clone();
  }

  @Override
  public String getId() {
    return "20-line-hydraulics-" + lineTag;
  }

  @Override
  public EngineeringDesignModuleResult evaluate(ProcessSystem process, EngineeringCaseRunReport caseReport,
      EngineeringDesignState state, EngineeringCalculationContext context) {
    final ProcessEquipmentInterface equipment = process.getUnit(lineTag);
    if (!(equipment instanceof PipeLineInterface)) {
      throw new IllegalArgumentException(lineTag + " is not a PipeLineInterface");
    }
    PipeLineInterface line = (PipeLineInterface) equipment;
    EngineeringDesignEnvelope.GoverningValue flow = DesignModuleSupport.requireMetric(caseReport, volumeFlowMetricId);
    EngineeringDesignEnvelope.GoverningValue pressureDrop = DesignModuleSupport.requireMetric(caseReport,
        pressureDropMetricId);
    double velocityDiameter = Math.sqrt(4.0 * Math.max(flow.getValue(), 0.0) / (Math.PI * maximumVelocityMPerS));
    double lengthKm = Math.max(line.getLength() / 1000.0, 1.0e-9);
    double actualGradient = Math.max(pressureDrop.getValue(), 0.0) / lengthKm;
    double currentDiameter = Math.max(line.getDiameter(), diameterCandidates[0]);
    double pressureDiameter = actualGradient <= maximumPressureGradientBarPerKm ? currentDiameter
        : currentDiameter * Math.pow(actualGradient / maximumPressureGradientBarPerKm, 0.2);
    double requiredDiameter = Math.max(velocityDiameter, pressureDiameter);
    double selectedDiameter = select(requiredDiameter);
    double selectedVelocity = 4.0 * flow.getValue() / (Math.PI * selectedDiameter * selectedDiameter);
    double predictedGradient = actualGradient * Math.pow(currentDiameter / selectedDiameter, 5.0);
    String diameterKey = lineTag + ".insideDiameter";
    String velocityKey = lineTag + ".designVelocity";
    String gradientKey = lineTag + ".predictedPressureGradient";

    return EngineeringDesignModuleResult.builder(getId(), "NORSOK P-002 constrained discrete line sizing", "1.0")
        .addUpdate(EngineeringDesignUpdate.builder(diameterKey, requiredDiameter, "m").candidates(diameterCandidates)
            .governingCaseId(flow.getDesignCaseId()).applier(new EngineeringDesignUpdate.Applier() {
              private static final long serialVersionUID = 1000L;

              @Override
              public void apply(ProcessSystem working, double value) {
                ((PipeLineInterface) working.getUnit(lineTag)).setDiameter(value);
              }
            }).build())
        .addUpdate(EngineeringDesignUpdate.builder(velocityKey, selectedVelocity, "m/s")
            .governingCaseId(flow.getDesignCaseId()).build())
        .addUpdate(EngineeringDesignUpdate.builder(gradientKey, predictedGradient, "bar/km")
            .governingCaseId(pressureDrop.getDesignCaseId()).build())
        .addConstraint(new EngineeringDesignConstraint(lineTag + ".velocity-limit", "Maximum design velocity",
            velocityKey, maximumVelocityMPerS, "m/s", EngineeringDesignConstraint.Comparison.MAXIMUM))
        .addConstraint(
            new EngineeringDesignConstraint(lineTag + ".pressure-gradient-limit", "Maximum pressure gradient",
                gradientKey, maximumPressureGradientBarPerKm, "bar/km", EngineeringDesignConstraint.Comparison.MAXIMUM))
        .evidence("standard", "NORSOK P-002 project rule pack")
        .evidence("simulatedPressureGradient_barPerKm", Double.valueOf(actualGradient)).build();
  }

  private double select(double required) {
    double selected = Double.NaN;
    for (double candidate : diameterCandidates) {
      if (candidate >= required && (!Double.isFinite(selected) || candidate < selected)) {
        selected = candidate;
      }
    }
    if (!Double.isFinite(selected)) {
      throw new IllegalStateException("No line diameter candidate satisfies " + required + " m for " + lineTag);
    }
    return selected;
  }
}
