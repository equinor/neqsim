package neqsim.process.engineering.calculation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import neqsim.process.mechanicaldesign.designstandards.StandardApplicability;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardSupportLevel;
import neqsim.process.mechanicaldesign.designstandards.StandardType;

/** Edition-aware caller-controlled global-buckling response screening for DNV-RP-F110. */
public final class DnvRpF110GlobalBucklingResponseScreeningKernel implements
    EquipmentDesignKernel<DnvRpF110GlobalBucklingResponseScreeningKernel.Input, DnvRpF110GlobalBucklingResponseAssessment> {
  private static final long serialVersionUID = 1000L;
  private static final String IMPLEMENTED_EDITION = "2019-09+AMD:2021-09";

  /** Pipeline interface configuration represented by an external global structural analysis. */
  public enum PipelineConfiguration {
    /** Pipeline exposed on the seabed. */
    EXPOSED,
    /** Pipeline buried or covered. */
    BURIED
  }

  /** Project global-buckling design strategy represented by an external analysis. */
  public enum DesignStrategy {
    /** Global buckling is permitted only in a controlled and verified manner. */
    CONTROLLED_BUCKLING,
    /** Global buckling is prevented for the analysed design situations. */
    BUCKLING_PREVENTION
  }

  /** Immutable externally analysed global-buckling response case. */
  public static final class BucklingCase implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String label;
    private final double distanceM;
    private final String designSituation;
    private final PipelineConfiguration configuration;
    private final DesignStrategy strategy;
    private final double effectiveCompressiveForceN;
    private final double callerControlledAllowableCompressiveForceN;
    private final double peakLongitudinalStrainFraction;
    private final double callerControlledAllowableLongitudinalStrainFraction;
    private final double peakGlobalDisplacementM;
    private final double callerControlledAllowableGlobalDisplacementM;
    private final double requiredFeedInLengthM;
    private final double availableFeedInLengthM;

    /**
     * Create one caller-controlled external-analysis response case.
     *
     * @param label unique case label
     * @param distanceM route distance in m
     * @param designSituation design-situation identifier
     * @param configuration exposed or buried pipeline configuration
     * @param strategy controlled-buckling or prevention strategy
     * @param effectiveCompressiveForceN effective compressive-force magnitude in N
     * @param callerControlledAllowableCompressiveForceN externally established allowable force in N
     * @param peakLongitudinalStrainFraction peak longitudinal strain as a fraction
     * @param callerControlledAllowableLongitudinalStrainFraction externally established allowable strain
     * @param peakGlobalDisplacementM peak global displacement magnitude in m
     * @param callerControlledAllowableGlobalDisplacementM externally established allowable displacement in m
     * @param requiredFeedInLengthM externally derived required feed-in length in m
     * @param availableFeedInLengthM externally established available feed-in length in m
     */
    public BucklingCase(String label, double distanceM, String designSituation, PipelineConfiguration configuration,
        DesignStrategy strategy, double effectiveCompressiveForceN, double callerControlledAllowableCompressiveForceN,
        double peakLongitudinalStrainFraction, double callerControlledAllowableLongitudinalStrainFraction,
        double peakGlobalDisplacementM, double callerControlledAllowableGlobalDisplacementM,
        double requiredFeedInLengthM, double availableFeedInLengthM) {
      this.label = label;
      this.distanceM = distanceM;
      this.designSituation = designSituation;
      this.configuration = configuration;
      this.strategy = strategy;
      this.effectiveCompressiveForceN = effectiveCompressiveForceN;
      this.callerControlledAllowableCompressiveForceN = callerControlledAllowableCompressiveForceN;
      this.peakLongitudinalStrainFraction = peakLongitudinalStrainFraction;
      this.callerControlledAllowableLongitudinalStrainFraction = callerControlledAllowableLongitudinalStrainFraction;
      this.peakGlobalDisplacementM = peakGlobalDisplacementM;
      this.callerControlledAllowableGlobalDisplacementM = callerControlledAllowableGlobalDisplacementM;
      this.requiredFeedInLengthM = requiredFeedInLengthM;
      this.availableFeedInLengthM = availableFeedInLengthM;
    }

    /** @return unique case label */
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

    /** @return exposed or buried pipeline configuration */
    public PipelineConfiguration getConfiguration() {
      return configuration;
    }

    /** @return controlled-buckling or prevention strategy */
    public DesignStrategy getStrategy() {
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

    /** @return peak longitudinal strain as a fraction */
    public double getPeakLongitudinalStrainFraction() {
      return peakLongitudinalStrainFraction;
    }

    /** @return caller-controlled allowable longitudinal strain as a fraction */
    public double getCallerControlledAllowableLongitudinalStrainFraction() {
      return callerControlledAllowableLongitudinalStrainFraction;
    }

    /** @return peak global displacement magnitude in m */
    public double getPeakGlobalDisplacementM() {
      return peakGlobalDisplacementM;
    }

    /** @return caller-controlled allowable global displacement magnitude in m */
    public double getCallerControlledAllowableGlobalDisplacementM() {
      return callerControlledAllowableGlobalDisplacementM;
    }

    /** @return externally derived required feed-in length in m */
    public double getRequiredFeedInLengthM() {
      return requiredFeedInLengthM;
    }

    /** @return externally established available feed-in length in m */
    public double getAvailableFeedInLengthM() {
      return availableFeedInLengthM;
    }
  }

  /** Immutable, unit-explicit input for one global-buckling response-envelope screen. */
  public static final class Input implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final StandardEdition edition;
    private final String equipmentType;
    private final double pipelineOuterDiameterM;
    private final double steelWallThicknessM;
    private final List<BucklingCase> bucklingCases;
    private final boolean applicabilityVerified;
    private final boolean operatingEnvelopeAndEffectiveForceVerified;
    private final boolean pipePropertiesAndAsLaidGeometryVerified;
    private final boolean pipeSoilInteractionVerified;
    private final boolean imperfectionTriggerAndStrategyVerified;
    private final boolean globalStructuralModelVerified;
    private final boolean designSituationsAndLoadCombinationsVerified;
    private final boolean localCapacityAndStrainCriteriaVerified;
    private final boolean uncertaintySensitivityAndBuckleSharingVerified;
    private final boolean installationInterventionMonitoringAndLifecycleReviewed;

    private Input(Builder builder) {
      edition = builder.edition;
      equipmentType = builder.equipmentType;
      pipelineOuterDiameterM = builder.pipelineOuterDiameterM;
      steelWallThicknessM = builder.steelWallThicknessM;
      bucklingCases = Collections.unmodifiableList(new ArrayList<BucklingCase>(builder.bucklingCases));
      applicabilityVerified = builder.applicabilityVerified;
      operatingEnvelopeAndEffectiveForceVerified = builder.operatingEnvelopeAndEffectiveForceVerified;
      pipePropertiesAndAsLaidGeometryVerified = builder.pipePropertiesAndAsLaidGeometryVerified;
      pipeSoilInteractionVerified = builder.pipeSoilInteractionVerified;
      imperfectionTriggerAndStrategyVerified = builder.imperfectionTriggerAndStrategyVerified;
      globalStructuralModelVerified = builder.globalStructuralModelVerified;
      designSituationsAndLoadCombinationsVerified = builder.designSituationsAndLoadCombinationsVerified;
      localCapacityAndStrainCriteriaVerified = builder.localCapacityAndStrainCriteriaVerified;
      uncertaintySensitivityAndBuckleSharingVerified = builder.uncertaintySensitivityAndBuckleSharingVerified;
      installationInterventionMonitoringAndLifecycleReviewed = builder.installationInterventionMonitoringAndLifecycleReviewed;
    }

    /** @param edition F110 edition @param equipmentType pipeline type @return input builder */
    public static Builder builder(StandardEdition edition, String equipmentType) {
      return new Builder(edition, equipmentType);
    }

    /** @return selected edition */
    public StandardEdition getEdition() {
      return edition;
    }

    /** @return supported equipment type */
    public String getEquipmentType() {
      return equipmentType;
    }

    /** @return pipeline outside diameter in m */
    public double getPipelineOuterDiameterM() {
      return pipelineOuterDiameterM;
    }

    /** @return structural steel wall thickness in m */
    public double getSteelWallThicknessM() {
      return steelWallThicknessM;
    }

    /** @return immutable ordered buckling response cases */
    public List<BucklingCase> getBucklingCases() {
      return Collections.unmodifiableList(new ArrayList<BucklingCase>(bucklingCases));
    }

    /** @return whether F110 applicability was externally verified */
    public boolean isApplicabilityVerified() {
      return applicabilityVerified;
    }

    /** @return whether operating envelope and effective force were externally verified */
    public boolean isOperatingEnvelopeAndEffectiveForceVerified() {
      return operatingEnvelopeAndEffectiveForceVerified;
    }

    /** @return whether pipe properties and as-laid geometry were externally verified */
    public boolean isPipePropertiesAndAsLaidGeometryVerified() {
      return pipePropertiesAndAsLaidGeometryVerified;
    }

    /** @return whether pipe-soil response was externally verified */
    public boolean isPipeSoilInteractionVerified() {
      return pipeSoilInteractionVerified;
    }

    /** @return whether imperfection, trigger, and strategy bases were externally verified */
    public boolean isImperfectionTriggerAndStrategyVerified() {
      return imperfectionTriggerAndStrategyVerified;
    }

    /** @return whether the global structural model was externally verified */
    public boolean isGlobalStructuralModelVerified() {
      return globalStructuralModelVerified;
    }

    /** @return whether design situations and load combinations were externally verified */
    public boolean isDesignSituationsAndLoadCombinationsVerified() {
      return designSituationsAndLoadCombinationsVerified;
    }

    /** @return whether local capacity and strain criteria were externally verified */
    public boolean isLocalCapacityAndStrainCriteriaVerified() {
      return localCapacityAndStrainCriteriaVerified;
    }

    /** @return whether uncertainty, sensitivity, and buckle sharing were externally verified */
    public boolean isUncertaintySensitivityAndBuckleSharingVerified() {
      return uncertaintySensitivityAndBuckleSharingVerified;
    }

    /** @return whether installation, intervention, monitoring, and lifecycle were reviewed */
    public boolean isInstallationInterventionMonitoringAndLifecycleReviewed() {
      return installationInterventionMonitoringAndLifecycleReviewed;
    }

    /** Builder retaining raw values for fail-closed readiness assessment. */
    public static final class Builder {
      private final StandardEdition edition;
      private final String equipmentType;
      private double pipelineOuterDiameterM = Double.NaN;
      private double steelWallThicknessM = Double.NaN;
      private final List<BucklingCase> bucklingCases = new ArrayList<BucklingCase>();
      private boolean applicabilityVerified;
      private boolean operatingEnvelopeAndEffectiveForceVerified;
      private boolean pipePropertiesAndAsLaidGeometryVerified;
      private boolean pipeSoilInteractionVerified;
      private boolean imperfectionTriggerAndStrategyVerified;
      private boolean globalStructuralModelVerified;
      private boolean designSituationsAndLoadCombinationsVerified;
      private boolean localCapacityAndStrainCriteriaVerified;
      private boolean uncertaintySensitivityAndBuckleSharingVerified;
      private boolean installationInterventionMonitoringAndLifecycleReviewed;

      private Builder(StandardEdition edition, String equipmentType) {
        if (edition == null || edition.getStandardType() != StandardType.DNV_RP_F110) {
          throw new IllegalArgumentException("edition must identify DNV-RP-F110");
        }
        if (equipmentType == null || equipmentType.trim().isEmpty()) {
          throw new IllegalArgumentException("equipmentType cannot be null or blank");
        }
        this.edition = edition;
        this.equipmentType = equipmentType.trim();
      }

      /** @param value pipeline outside diameter in m @return this builder */
      public Builder pipelineOuterDiameterM(double value) {
        pipelineOuterDiameterM = value;
        return this;
      }

      /** @param value structural steel wall thickness in m @return this builder */
      public Builder steelWallThicknessM(double value) {
        steelWallThicknessM = value;
        return this;
      }

      /** @param value one external-analysis response case @return this builder */
      public Builder addBucklingCase(BucklingCase value) {
        bucklingCases.add(value);
        return this;
      }

      /** @param values external-analysis response cases @return this builder */
      public Builder bucklingCases(List<BucklingCase> values) {
        bucklingCases.clear();
        if (values != null) {
          bucklingCases.addAll(values);
        }
        return this;
      }

      /** @param value verification state @return this builder */
      public Builder applicabilityVerified(boolean value) {
        applicabilityVerified = value;
        return this;
      }

      /** @param value verification state @return this builder */
      public Builder operatingEnvelopeAndEffectiveForceVerified(boolean value) {
        operatingEnvelopeAndEffectiveForceVerified = value;
        return this;
      }

      /** @param value verification state @return this builder */
      public Builder pipePropertiesAndAsLaidGeometryVerified(boolean value) {
        pipePropertiesAndAsLaidGeometryVerified = value;
        return this;
      }

      /** @param value verification state @return this builder */
      public Builder pipeSoilInteractionVerified(boolean value) {
        pipeSoilInteractionVerified = value;
        return this;
      }

      /** @param value verification state @return this builder */
      public Builder imperfectionTriggerAndStrategyVerified(boolean value) {
        imperfectionTriggerAndStrategyVerified = value;
        return this;
      }

      /** @param value verification state @return this builder */
      public Builder globalStructuralModelVerified(boolean value) {
        globalStructuralModelVerified = value;
        return this;
      }

      /** @param value verification state @return this builder */
      public Builder designSituationsAndLoadCombinationsVerified(boolean value) {
        designSituationsAndLoadCombinationsVerified = value;
        return this;
      }

      /** @param value verification state @return this builder */
      public Builder localCapacityAndStrainCriteriaVerified(boolean value) {
        localCapacityAndStrainCriteriaVerified = value;
        return this;
      }

      /** @param value verification state @return this builder */
      public Builder uncertaintySensitivityAndBuckleSharingVerified(boolean value) {
        uncertaintySensitivityAndBuckleSharingVerified = value;
        return this;
      }

      /** @param value review state @return this builder */
      public Builder installationInterventionMonitoringAndLifecycleReviewed(boolean value) {
        installationInterventionMonitoringAndLifecycleReviewed = value;
        return this;
      }

      /** @return immutable input */
      public Input build() {
        return new Input(this);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public StandardType standard() {
    return StandardType.DNV_RP_F110;
  }

  /** {@inheritDoc} */
  @Override
  public StandardSupportLevel maturity() {
    return StandardSupportLevel.SCREENING;
  }

  /** {@inheritDoc} */
  @Override
  public boolean supports(StandardEdition edition) {
    return edition != null && edition.getStandardType() == standard()
        && IMPLEMENTED_EDITION.equalsIgnoreCase(edition.getEdition()) && edition.getAmendments().isEmpty();
  }

  /** {@inheritDoc} */
  @Override
  public StandardApplicability applicability(Input input) {
    return StandardApplicability.assess(standard(), input == null ? null : input.getEquipmentType());
  }

  /** {@inheritDoc} */
  @Override
  public String getMethod() {
    return "dnv-rp-f110-global-buckling-response-envelope-screening";
  }

  /** {@inheritDoc} */
  @Override
  public String getMethodVersion() {
    return "1.0.0";
  }

  /** {@inheritDoc} */
  @Override
  public CalculationReadiness assess(Input input, EngineeringCalculationContext context) {
    CalculationReadiness.Builder readiness = CalculationReadiness.builder();
    if (input == null) {
      return readiness.addBlocker("DNV_RP_F110_INPUT_MISSING", "DNV-RP-F110 global-buckling input is required",
          "Provide pipe geometry, external structural-analysis response cases, limits, and evidence flags").build();
    }
    StandardApplicability applicability = applicability(input);
    if (!applicability.isApplicable()) {
      readiness.addBlocker("DNV_RP_F110_NOT_APPLICABLE", applicability.getReason(),
          "Use a catalogued submarine pipeline or pipe equipment type");
    }
    if (!supports(input.getEdition())) {
      readiness.addBlocker("DNV_RP_F110_EDITION_NOT_IMPLEMENTED",
          "The kernel implements " + IMPLEMENTED_EDITION + ", not " + input.getEdition().getDisplayName(),
          "Select the catalogued unamended edition or implement a controlled method version");
    }
    if (!positive(input.getPipelineOuterDiameterM()) || !positive(input.getSteelWallThicknessM())
        || input.getSteelWallThicknessM() * 2.0 >= input.getPipelineOuterDiameterM()) {
      readiness.addBlocker("DNV_RP_F110_PIPE_GEOMETRY_INVALID",
          "Pipeline diameter and wall thickness must be finite, positive, and geometrically valid",
          "Supply verified structural dimensions in m");
    }
    validateBucklingCases(input, readiness);
    validateEvidence(input, readiness);
    readiness.addWarning("DNV_RP_F110_CALLER_CONTROLLED_RESPONSE",
        "Every response, allowable value, and available feed-in length is caller-controlled external evidence",
        "Retain structural models, pipe-soil basis, limit derivation, uncertainty, and accountable approvals");
    readiness.addWarning("DNV_RP_F110_SCREENING_ONLY",
        "The result is a response-envelope screen and is not a DNV-RP-F110 or DNV-ST-F101 conformity assessment",
        "Complete independent global/local structural, geotechnical, installation, and integrity reviews");
    return readiness.build();
  }

  private static void validateBucklingCases(Input input, CalculationReadiness.Builder readiness) {
    if (input.getBucklingCases().isEmpty()) {
      readiness.addBlocker("DNV_RP_F110_CASES_EMPTY", "At least one global-buckling response case is required",
          "Supply controlled-buckling or buckling-prevention cases from an approved structural model");
      return;
    }
    Set<String> labels = new HashSet<String>();
    for (int index = 0; index < input.getBucklingCases().size(); index++) {
      BucklingCase value = input.getBucklingCases().get(index);
      if (value == null) {
        readiness.addBlocker("DNV_RP_F110_CASE_MISSING", "Buckling cases cannot be null",
            "Replace the missing case at index " + index);
        continue;
      }
      String label = text(value.getLabel());
      if (label.isEmpty() || !labels.add(label)) {
        readiness.addBlocker("DNV_RP_F110_CASE_LABEL_INVALID", "Case labels must be non-blank and unique",
            "Correct the label at index " + index);
      }
      if (!nonNegative(value.getDistanceM()) || text(value.getDesignSituation()).isEmpty()
          || value.getConfiguration() == null || value.getStrategy() == null) {
        readiness.addBlocker("DNV_RP_F110_CASE_BASIS_INVALID",
            "Case distance, design situation, configuration, and strategy must be explicit and valid",
            "Correct the route and strategy basis for " + caseName(label, index));
      }
      if (!nonNegative(value.getEffectiveCompressiveForceN())
          || !positive(value.getCallerControlledAllowableCompressiveForceN())
          || !nonNegative(value.getPeakLongitudinalStrainFraction())
          || !positive(value.getCallerControlledAllowableLongitudinalStrainFraction())
          || !nonNegative(value.getPeakGlobalDisplacementM())
          || !positive(value.getCallerControlledAllowableGlobalDisplacementM())
          || !nonNegative(value.getRequiredFeedInLengthM()) || !positive(value.getAvailableFeedInLengthM())) {
        readiness.addBlocker("DNV_RP_F110_CASE_VALUES_INVALID",
            "Response demands must be finite and non-negative and allowable/available values finite and positive",
            "Correct force, strain, displacement, and feed-in inputs for " + caseName(label, index));
      }
    }
  }

  private static void validateEvidence(Input input, CalculationReadiness.Builder readiness) {
    evidence(input.isApplicabilityVerified(), readiness, "APPLICABILITY",
        "Global-buckling applicability and pipeline configuration have not been verified",
        "Verify pipeline scope, exposed/buried condition, design strategy, and lifecycle phase");
    evidence(input.isOperatingEnvelopeAndEffectiveForceVerified(), readiness, "OPERATING_ENVELOPE",
        "Pressure-temperature envelope and effective axial-force derivation have not been verified",
        "Verify installation and operating pressure, temperature, contents, residual lay tension, and force basis");
    evidence(input.isPipePropertiesAndAsLaidGeometryVerified(), readiness, "PIPE_GEOMETRY",
        "Pipe properties, route geometry, and as-laid imperfections have not been verified",
        "Verify dimensions, material response, coating, route curvature, out-of-straightness, and surveys");
    evidence(input.isPipeSoilInteractionVerified(), readiness, "PIPE_SOIL",
        "The F114-aligned pipe-soil interaction basis has not been verified",
        "Verify vertical, axial, and lateral response, drainage/time/cyclic effects, and uncertainty");
    evidence(input.isImperfectionTriggerAndStrategyVerified(), readiness, "TRIGGER_STRATEGY",
        "Imperfection, buckle trigger, prevention, and sharing strategy have not been verified",
        "Verify trigger geometry/capacity, mitigation, buckle spacing, initiation, and sharing basis");
    evidence(input.isGlobalStructuralModelVerified(), readiness, "GLOBAL_MODEL",
        "The global structural model and response extraction have not been verified",
        "Verify elements, boundary conditions, nonlinearities, mesh, imperfections, convergence, and benchmarks");
    evidence(input.isDesignSituationsAndLoadCombinationsVerified(), readiness, "LOAD_CASES",
        "Design situations, functional/environmental loads, and combinations have not been verified",
        "Verify installation, start-up, operation, shutdown, accidental, hydrotest, and environmental cases");
    evidence(input.isLocalCapacityAndStrainCriteriaVerified(), readiness, "LOCAL_CAPACITY",
        "Local capacity, strain, fatigue, and allowable-response criteria have not been verified",
        "Verify every supplied limit against DNV-ST-F101, project factors, cycles, welds, and fatigue basis");
    evidence(input.isUncertaintySensitivityAndBuckleSharingVerified(), readiness, "UNCERTAINTY",
        "Uncertainty, sensitivity, probabilistic, and buckle-sharing assessments have not been verified",
        "Verify parameter bounds, route variability, competing buckles, robustness, and model uncertainty");
    evidence(input.isInstallationInterventionMonitoringAndLifecycleReviewed(), readiness, "LIFECYCLE",
        "Installation, intervention, monitoring, integrity, and lifecycle implications have not been reviewed",
        "Complete constructability, trigger/intervention, survey, monitoring, inspection, and operating reviews");
  }

  private static void evidence(boolean verified, CalculationReadiness.Builder readiness, String suffix, String message,
      String action) {
    if (!verified) {
      readiness.addBlocker("DNV_RP_F110_" + suffix + "_NOT_VERIFIED", message, action);
    }
  }

  /** {@inheritDoc} */
  @Override
  public EngineeringCalculationResult<DnvRpF110GlobalBucklingResponseAssessment> calculate(Input input,
      EngineeringCalculationContext context) {
    EngineeringCalculationContext effectiveContext = context == null ? EngineeringCalculationContext.builder().build()
        : context;
    CalculationReadiness readiness = assess(input, effectiveContext);
    EngineeringCalculationResult.Builder<DnvRpF110GlobalBucklingResponseAssessment> result = EngineeringCalculationResult
        .<DnvRpF110GlobalBucklingResponseAssessment>builder("dnv-rp-f110-global-buckling-response-envelope-screening",
            getMethod(), getMethodVersion())
        .context(effectiveContext).readiness(readiness);
    if (input == null || !readiness.isReady()) {
      return result.status(EngineeringCalculationResult.Status.BLOCKED)
          .message("DNV-RP-F110 global-buckling screening is blocked until readiness findings are resolved").build();
    }
    DnvRpF110GlobalBucklingResponseAssessment assessment = new DnvRpF110GlobalBucklingResponseAssessment(input);
    if (!numericallyValid(assessment)) {
      CalculationReadiness numericalReadiness = CalculationReadiness.builder().merge(readiness)
          .addBlocker("DNV_RP_F110_NUMERICAL_RESULT_INVALID",
              "Global-buckling response screening produced a non-finite result",
              "Review force, strain, displacement, feed-in, allowable values, magnitudes, and units")
          .build();
      return result.readiness(numericalReadiness).status(EngineeringCalculationResult.Status.BLOCKED)
          .message("DNV-RP-F110 screening is blocked by an invalid numerical result").build();
    }
    return result.status(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED).value(assessment)
        .input("standard", input.getEdition().getDisplayName()).input("equipmentType", input.getEquipmentType())
        .input("pipeGeometry", pipeGeometryMap(input)).input("bucklingCases", bucklingCaseMaps(input))
        .warning("Constraint status is caller-controlled and is not a DNV-RP-F110 or ST-F101 compliance decision")
        .warning("The kernel does not calculate effective axial force, critical buckling, Hobbs/FE response, soil "
            + "resistance, trigger performance, buckle sharing, fatigue, local capacity, or allowable limits")
        .warning("DNV-RP-F114 pipe-soil interaction and all DNV-ST-F101 pressure containment, collapse, propagation "
            + "buckling, local buckling, load interaction, fatigue, pressure-case, de-rating, safety-class, ovality, "
            + "fabrication, and installation-strain checks are not replaced")
        .message("DNV-RP-F110 caller-controlled global-buckling response screen completed; review remains required")
        .build();
  }

  private static Map<String, Object> pipeGeometryMap(Input input) {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("pipelineOuterDiameterM", Double.valueOf(input.getPipelineOuterDiameterM()));
    values.put("steelWallThicknessM", Double.valueOf(input.getSteelWallThicknessM()));
    return values;
  }

  private static List<Map<String, Object>> bucklingCaseMaps(Input input) {
    List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
    for (BucklingCase bucklingCase : input.getBucklingCases()) {
      Map<String, Object> value = new LinkedHashMap<String, Object>();
      value.put("label", bucklingCase.getLabel());
      value.put("distanceM", Double.valueOf(bucklingCase.getDistanceM()));
      value.put("designSituation", bucklingCase.getDesignSituation());
      value.put("configuration", bucklingCase.getConfiguration().name());
      value.put("strategy", bucklingCase.getStrategy().name());
      value.put("effectiveCompressiveForceN", Double.valueOf(bucklingCase.getEffectiveCompressiveForceN()));
      value.put("callerControlledAllowableCompressiveForceN",
          Double.valueOf(bucklingCase.getCallerControlledAllowableCompressiveForceN()));
      value.put("peakLongitudinalStrainFraction", Double.valueOf(bucklingCase.getPeakLongitudinalStrainFraction()));
      value.put("callerControlledAllowableLongitudinalStrainFraction",
          Double.valueOf(bucklingCase.getCallerControlledAllowableLongitudinalStrainFraction()));
      value.put("peakGlobalDisplacementM", Double.valueOf(bucklingCase.getPeakGlobalDisplacementM()));
      value.put("callerControlledAllowableGlobalDisplacementM",
          Double.valueOf(bucklingCase.getCallerControlledAllowableGlobalDisplacementM()));
      value.put("requiredFeedInLengthM", Double.valueOf(bucklingCase.getRequiredFeedInLengthM()));
      value.put("availableFeedInLengthM", Double.valueOf(bucklingCase.getAvailableFeedInLengthM()));
      values.add(value);
    }
    return values;
  }

  private static boolean numericallyValid(DnvRpF110GlobalBucklingResponseAssessment assessment) {
    return Double.isFinite(assessment.getMaximumCompressiveForceUtilization())
        && Double.isFinite(assessment.getMaximumLongitudinalStrainUtilization())
        && Double.isFinite(assessment.getMaximumGlobalDisplacementUtilization())
        && Double.isFinite(assessment.getMaximumFeedInLengthUtilization());
  }

  private static boolean positive(double value) {
    return Double.isFinite(value) && value > 0.0;
  }

  private static boolean nonNegative(double value) {
    return Double.isFinite(value) && value >= 0.0;
  }

  private static String text(String value) {
    return value == null ? "" : value.trim();
  }

  private static String caseName(String label, int index) {
    return label.isEmpty() ? "case index " + index : "case " + label;
  }
}
