package neqsim.process.engineering.design.modules;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import neqsim.process.engineering.calculation.EngineeringCalculationContext;
import neqsim.process.engineering.design.EngineeringDesignConstraint;
import neqsim.process.engineering.design.EngineeringDesignModule;
import neqsim.process.engineering.design.EngineeringDesignModuleResult;
import neqsim.process.engineering.design.EngineeringDesignState;
import neqsim.process.engineering.design.EngineeringDesignUpdate;
import neqsim.process.engineering.designcase.EngineeringCaseRunReport;
import neqsim.process.engineering.designcase.EngineeringDesignEnvelope;
import neqsim.process.engineering.piping.PipingRulePack;
import neqsim.process.equipment.pipeline.PipeLineInterface;
import neqsim.process.processmodel.ProcessSystem;

/** Designs a connected set of lines together and applies selected diameters during design-loop convergence. */
public final class PipingNetworkDesignModule implements EngineeringDesignModule {
  private static final long serialVersionUID = 1000L;

  /** One line in the converged network. */
  public static final class SegmentDefinition implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String lineTag;
    private final String flowMetricId;
    private final String pressureDropMetricId;
    private final boolean gasService;
    private final boolean reliefInlet;
    private final double reliefSetPressureBarg;

    public SegmentDefinition(String lineTag, String flowMetricId, String pressureDropMetricId, boolean gasService,
        boolean reliefInlet, double reliefSetPressureBarg) {
      this.lineTag = text(lineTag, "lineTag");
      this.flowMetricId = text(flowMetricId, "flowMetricId");
      this.pressureDropMetricId = text(pressureDropMetricId, "pressureDropMetricId");
      if (reliefInlet && (!Double.isFinite(reliefSetPressureBarg) || reliefSetPressureBarg <= 0.0)) {
        throw new IllegalArgumentException("reliefSetPressureBarg must be finite and positive for a relief inlet");
      }
      this.gasService = gasService;
      this.reliefInlet = reliefInlet;
      this.reliefSetPressureBarg = reliefSetPressureBarg;
    }

    public static SegmentDefinition processLine(String lineTag, boolean gasService) {
      return new SegmentDefinition(lineTag, lineTag + ".inletVolumeFlow", lineTag + ".pressureDrop", gasService, false,
          0.0);
    }

    public static SegmentDefinition reliefInlet(String lineTag, boolean gasService, double setPressureBarg) {
      return new SegmentDefinition(lineTag, lineTag + ".inletVolumeFlow", lineTag + ".pressureDrop", gasService, true,
          setPressureBarg);
    }

    public String getLineTag() {
      return lineTag;
    }
  }

  private final String networkId;
  private final PipingRulePack rulePack;
  private final List<SegmentDefinition> segments;
  private final double[] diameterCandidates;

  public PipingNetworkDesignModule(String networkId, PipingRulePack rulePack, List<SegmentDefinition> segments) {
    this(networkId, rulePack, segments, DesignModuleSupport.defaultPipeDiametersMeters());
  }

  public PipingNetworkDesignModule(String networkId, PipingRulePack rulePack, List<SegmentDefinition> segments,
      double[] diameterCandidates) {
    this.networkId = text(networkId, "networkId");
    if (rulePack == null) {
      throw new IllegalArgumentException("rulePack must not be null");
    }
    if (segments == null || segments.isEmpty() || segments.contains(null)) {
      throw new IllegalArgumentException("At least one non-null network segment is required");
    }
    if (diameterCandidates == null || diameterCandidates.length == 0) {
      throw new IllegalArgumentException("At least one diameter candidate is required");
    }
    this.rulePack = rulePack;
    this.segments = Collections.unmodifiableList(new ArrayList<SegmentDefinition>(segments));
    this.diameterCandidates = diameterCandidates.clone();
    for (double candidate : this.diameterCandidates) {
      if (!Double.isFinite(candidate) || candidate <= 0.0) {
        throw new IllegalArgumentException("Diameter candidates must be finite and positive");
      }
    }
  }

  @Override
  public String getId() {
    return "21-piping-network-design-" + networkId;
  }

  @Override
  public EngineeringDesignModuleResult evaluate(ProcessSystem process, EngineeringCaseRunReport caseReport,
      EngineeringDesignState state, EngineeringCalculationContext context) {
    EngineeringDesignModuleResult.Builder result = EngineeringDesignModuleResult
        .builder(getId(), "Network line candidate selection across converged cases", "1.0")
        .evidence("rulePack", rulePack.toMap());
    double simultaneousDemand = 0.0;
    for (final SegmentDefinition segment : segments) {
      if (!(process.getUnit(segment.lineTag) instanceof PipeLineInterface)) {
        throw new IllegalArgumentException(segment.lineTag + " is not a PipeLineInterface");
      }
      PipeLineInterface line = (PipeLineInterface) process.getUnit(segment.lineTag);
      EngineeringDesignEnvelope.GoverningValue flow = DesignModuleSupport.requireMetric(caseReport,
          segment.flowMetricId);
      EngineeringDesignEnvelope.GoverningValue pressureDrop = DesignModuleSupport.requireMetric(caseReport,
          segment.pressureDropMetricId);
      simultaneousDemand += flow.getValue();
      double maximumVelocity = rulePack.maximumVelocity(segment.gasService);
      double velocityDiameter = Math.sqrt(4.0 * flow.getValue() / Math.max(Math.PI * maximumVelocity, 1.0e-12));
      double lengthKm = Math.max(line.getLength() / 1000.0, 1.0e-12);
      double actualGradient = Math.max(pressureDrop.getValue(), 0.0) / lengthKm;
      double currentDiameter = Math.max(line.getDiameter(), diameterCandidates[0]);
      double gradientDiameter = actualGradient <= rulePack.getMaximumPressureGradientBarPerKm() ? currentDiameter
          : currentDiameter * Math.pow(actualGradient / rulePack.getMaximumPressureGradientBarPerKm(), 0.2);
      double requiredDiameter = Math.max(velocityDiameter, gradientDiameter);
      double selectedDiameter = select(requiredDiameter);
      double selectedVelocity = 4.0 * flow.getValue() / (Math.PI * selectedDiameter * selectedDiameter);
      double predictedDrop = pressureDrop.getValue() * Math.pow(currentDiameter / selectedDiameter, 5.0);
      String diameterKey = segment.lineTag + ".insideDiameter";
      String velocityKey = segment.lineTag + ".designVelocity";
      String gradientKey = segment.lineTag + ".predictedPressureGradient";
      String reliefLossKey = segment.lineTag + ".reliefInletLossFraction";
      result
          .addUpdate(EngineeringDesignUpdate.builder(diameterKey, requiredDiameter, "m").candidates(diameterCandidates)
              .governingCaseId(flow.getDesignCaseId()).applier(new EngineeringDesignUpdate.Applier() {
                private static final long serialVersionUID = 1000L;

                @Override
                public void apply(ProcessSystem working, double value) {
                  ((PipeLineInterface) working.getUnit(segment.lineTag)).setDiameter(value);
                }
              }).build());
      result.addUpdate(EngineeringDesignUpdate.builder(velocityKey, selectedVelocity, "m/s")
          .governingCaseId(flow.getDesignCaseId()).build());
      result.addUpdate(EngineeringDesignUpdate.builder(gradientKey, predictedDrop / lengthKm, "bar/km")
          .governingCaseId(pressureDrop.getDesignCaseId()).build());
      result.addConstraint(
          new EngineeringDesignConstraint(segment.lineTag + ".network-velocity", "Network segment maximum velocity",
              velocityKey, maximumVelocity, "m/s", EngineeringDesignConstraint.Comparison.MAXIMUM));
      result.addConstraint(new EngineeringDesignConstraint(segment.lineTag + ".network-pressure-gradient",
          "Network segment maximum pressure gradient", gradientKey, rulePack.getMaximumPressureGradientBarPerKm(),
          "bar/km", EngineeringDesignConstraint.Comparison.MAXIMUM));
      if (segment.reliefInlet) {
        double lossFraction = predictedDrop / Math.max(segment.reliefSetPressureBarg, 1.0e-12);
        result.addUpdate(EngineeringDesignUpdate.builder(reliefLossKey, lossFraction, "fraction")
            .governingCaseId(pressureDrop.getDesignCaseId()).build());
        result.addConstraint(new EngineeringDesignConstraint(segment.lineTag + ".relief-inlet-loss",
            "Relief inlet pressure loss fraction", reliefLossKey, rulePack.getMaximumReliefInletLossFraction(),
            "fraction", EngineeringDesignConstraint.Comparison.MAXIMUM));
      }
    }
    return result.evidence("simultaneousDemandM3s", Double.valueOf(simultaneousDemand))
        .warning("Fittings, elevations, multiphase transients, vibration/noise and stress require project verification")
        .build();
  }

  private double select(double required) {
    double selected = Double.POSITIVE_INFINITY;
    for (double candidate : diameterCandidates) {
      if (candidate >= required) {
        selected = Math.min(selected, candidate);
      }
    }
    if (!Double.isFinite(selected)) {
      throw new IllegalStateException("No network pipe candidate satisfies " + required + " m");
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
