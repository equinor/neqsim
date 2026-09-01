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

/** Edition-aware pipe-soil demand/resistance envelope screening for DNV-RP-F114. */
public final class DnvRpF114PipeSoilInteractionScreeningKernel implements
    EquipmentDesignKernel<DnvRpF114PipeSoilInteractionScreeningKernel.Input, DnvRpF114PipeSoilInteractionAssessment> {
  private static final long serialVersionUID = 1000L;
  private static final String IMPLEMENTED_EDITION = "2021-05";

  /** Immutable externally established demand and resistance values for one design situation. */
  public static final class InteractionCase implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String label;
    private final double distanceM;
    private final String designSituation;
    private final double verticalDemandNPerM;
    private final double verticalResistanceNPerM;
    private final double axialDemandNPerM;
    private final double axialResistanceNPerM;
    private final double lateralDemandNPerM;
    private final double lateralResistanceNPerM;

    /**
     * Create one caller-controlled pipe-soil interaction case.
     *
     * @param label unique case label
     * @param distanceM distance from route origin in m
     * @param designSituation design-situation identifier
     * @param verticalDemandNPerM vertical demand magnitude in N/m
     * @param verticalResistanceNPerM externally established vertical resistance in N/m
     * @param axialDemandNPerM axial demand magnitude in N/m
     * @param axialResistanceNPerM externally established axial resistance in N/m
     * @param lateralDemandNPerM lateral demand magnitude in N/m
     * @param lateralResistanceNPerM externally established lateral resistance in N/m
     */
    public InteractionCase(String label, double distanceM, String designSituation, double verticalDemandNPerM,
        double verticalResistanceNPerM, double axialDemandNPerM, double axialResistanceNPerM, double lateralDemandNPerM,
        double lateralResistanceNPerM) {
      this.label = label;
      this.distanceM = distanceM;
      this.designSituation = designSituation;
      this.verticalDemandNPerM = verticalDemandNPerM;
      this.verticalResistanceNPerM = verticalResistanceNPerM;
      this.axialDemandNPerM = axialDemandNPerM;
      this.axialResistanceNPerM = axialResistanceNPerM;
      this.lateralDemandNPerM = lateralDemandNPerM;
      this.lateralResistanceNPerM = lateralResistanceNPerM;
    }

    /** @return unique case label */
    public String getLabel() {
      return label;
    }

    /** @return distance from route origin in m */
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

    /** @return axial demand magnitude in N/m */
    public double getAxialDemandNPerM() {
      return axialDemandNPerM;
    }

    /** @return externally established axial resistance in N/m */
    public double getAxialResistanceNPerM() {
      return axialResistanceNPerM;
    }

    /** @return lateral demand magnitude in N/m */
    public double getLateralDemandNPerM() {
      return lateralDemandNPerM;
    }

    /** @return externally established lateral resistance in N/m */
    public double getLateralResistanceNPerM() {
      return lateralResistanceNPerM;
    }
  }

  /** Immutable, unit-explicit input for one pipe-soil interaction envelope screen. */
  public static final class Input implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final StandardEdition edition;
    private final String equipmentType;
    private final double pipelineOuterDiameterM;
    private final double submergedWeightNPerM;
    private final List<InteractionCase> interactionCases;
    private final boolean applicabilityVerified;
    private final boolean siteInvestigationVerified;
    private final boolean soilModelVerified;
    private final boolean pipelineConfigurationVerified;
    private final boolean installationHistoryVerified;
    private final boolean cyclicDrainageRateEffectsVerified;
    private final boolean loadDisplacementAndResistanceVerified;
    private final boolean uncertaintyAndVariabilityVerified;
    private final boolean designActionsAndAcceptanceCriteriaVerified;
    private final boolean interfacesAndLifecycleReviewed;

    private Input(Builder builder) {
      edition = builder.edition;
      equipmentType = builder.equipmentType;
      pipelineOuterDiameterM = builder.pipelineOuterDiameterM;
      submergedWeightNPerM = builder.submergedWeightNPerM;
      interactionCases = Collections.unmodifiableList(new ArrayList<InteractionCase>(builder.interactionCases));
      applicabilityVerified = builder.applicabilityVerified;
      siteInvestigationVerified = builder.siteInvestigationVerified;
      soilModelVerified = builder.soilModelVerified;
      pipelineConfigurationVerified = builder.pipelineConfigurationVerified;
      installationHistoryVerified = builder.installationHistoryVerified;
      cyclicDrainageRateEffectsVerified = builder.cyclicDrainageRateEffectsVerified;
      loadDisplacementAndResistanceVerified = builder.loadDisplacementAndResistanceVerified;
      uncertaintyAndVariabilityVerified = builder.uncertaintyAndVariabilityVerified;
      designActionsAndAcceptanceCriteriaVerified = builder.designActionsAndAcceptanceCriteriaVerified;
      interfacesAndLifecycleReviewed = builder.interfacesAndLifecycleReviewed;
    }

    /**
     * Create a DNV-RP-F114 input builder.
     *
     * @param edition DNV-RP-F114 edition
     * @param equipmentType supported pipeline equipment type
     * @return input builder
     */
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

    /** @return pipeline outside diameter including relevant coatings in m */
    public double getPipelineOuterDiameterM() {
      return pipelineOuterDiameterM;
    }

    /** @return caller-controlled submerged pipe weight in N/m */
    public double getSubmergedWeightNPerM() {
      return submergedWeightNPerM;
    }

    /** @return immutable ordered design cases */
    public List<InteractionCase> getInteractionCases() {
      return Collections.unmodifiableList(new ArrayList<InteractionCase>(interactionCases));
    }

    /** @return whether applicability was externally verified */
    public boolean isApplicabilityVerified() {
      return applicabilityVerified;
    }

    /** @return whether site-investigation evidence was externally verified */
    public boolean isSiteInvestigationVerified() {
      return siteInvestigationVerified;
    }

    /** @return whether soil stratigraphy and parameter interpretation were externally verified */
    public boolean isSoilModelVerified() {
      return soilModelVerified;
    }

    /** @return whether pipe geometry, coating, penetration, and burial were externally verified */
    public boolean isPipelineConfigurationVerified() {
      return pipelineConfigurationVerified;
    }

    /** @return whether installation and as-laid history were externally verified */
    public boolean isInstallationHistoryVerified() {
      return installationHistoryVerified;
    }

    /** @return whether cyclic, drainage, rate, and consolidation effects were externally verified */
    public boolean isCyclicDrainageRateEffectsVerified() {
      return cyclicDrainageRateEffectsVerified;
    }

    /** @return whether load-displacement models and resistances were externally verified */
    public boolean isLoadDisplacementAndResistanceVerified() {
      return loadDisplacementAndResistanceVerified;
    }

    /** @return whether uncertainty and spatial variability were externally verified */
    public boolean isUncertaintyAndVariabilityVerified() {
      return uncertaintyAndVariabilityVerified;
    }

    /** @return whether design actions, combinations, and project acceptance criteria were verified */
    public boolean isDesignActionsAndAcceptanceCriteriaVerified() {
      return designActionsAndAcceptanceCriteriaVerified;
    }

    /** @return whether design interfaces and lifecycle implications were reviewed */
    public boolean isInterfacesAndLifecycleReviewed() {
      return interfacesAndLifecycleReviewed;
    }

    /** Builder retaining raw values for fail-closed readiness assessment. */
    public static final class Builder {
      private final StandardEdition edition;
      private final String equipmentType;
      private double pipelineOuterDiameterM = Double.NaN;
      private double submergedWeightNPerM = Double.NaN;
      private final List<InteractionCase> interactionCases = new ArrayList<InteractionCase>();
      private boolean applicabilityVerified;
      private boolean siteInvestigationVerified;
      private boolean soilModelVerified;
      private boolean pipelineConfigurationVerified;
      private boolean installationHistoryVerified;
      private boolean cyclicDrainageRateEffectsVerified;
      private boolean loadDisplacementAndResistanceVerified;
      private boolean uncertaintyAndVariabilityVerified;
      private boolean designActionsAndAcceptanceCriteriaVerified;
      private boolean interfacesAndLifecycleReviewed;

      private Builder(StandardEdition edition, String equipmentType) {
        if (edition == null || edition.getStandardType() != StandardType.DNV_RP_F114) {
          throw new IllegalArgumentException("edition must identify DNV-RP-F114");
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

      /** @param value submerged pipe weight in N/m @return this builder */
      public Builder submergedWeightNPerM(double value) {
        submergedWeightNPerM = value;
        return this;
      }

      /** @param value one design case @return this builder */
      public Builder addInteractionCase(InteractionCase value) {
        interactionCases.add(value);
        return this;
      }

      /** @param values design cases @return this builder */
      public Builder interactionCases(List<InteractionCase> values) {
        interactionCases.clear();
        if (values != null) {
          interactionCases.addAll(values);
        }
        return this;
      }

      /** @param value verification state @return this builder */
      public Builder applicabilityVerified(boolean value) {
        applicabilityVerified = value;
        return this;
      }

      /** @param value verification state @return this builder */
      public Builder siteInvestigationVerified(boolean value) {
        siteInvestigationVerified = value;
        return this;
      }

      /** @param value verification state @return this builder */
      public Builder soilModelVerified(boolean value) {
        soilModelVerified = value;
        return this;
      }

      /** @param value verification state @return this builder */
      public Builder pipelineConfigurationVerified(boolean value) {
        pipelineConfigurationVerified = value;
        return this;
      }

      /** @param value verification state @return this builder */
      public Builder installationHistoryVerified(boolean value) {
        installationHistoryVerified = value;
        return this;
      }

      /** @param value verification state @return this builder */
      public Builder cyclicDrainageRateEffectsVerified(boolean value) {
        cyclicDrainageRateEffectsVerified = value;
        return this;
      }

      /** @param value verification state @return this builder */
      public Builder loadDisplacementAndResistanceVerified(boolean value) {
        loadDisplacementAndResistanceVerified = value;
        return this;
      }

      /** @param value verification state @return this builder */
      public Builder uncertaintyAndVariabilityVerified(boolean value) {
        uncertaintyAndVariabilityVerified = value;
        return this;
      }

      /** @param value verification state @return this builder */
      public Builder designActionsAndAcceptanceCriteriaVerified(boolean value) {
        designActionsAndAcceptanceCriteriaVerified = value;
        return this;
      }

      /** @param value review state @return this builder */
      public Builder interfacesAndLifecycleReviewed(boolean value) {
        interfacesAndLifecycleReviewed = value;
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
    return StandardType.DNV_RP_F114;
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
    return "dnv-rp-f114-pipe-soil-resistance-envelope-screening";
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
      return readiness.addBlocker("DNV_RP_F114_INPUT_MISSING", "DNV-RP-F114 pipe-soil interaction input is required",
          "Provide pipe properties, externally established demands/resistances, and evidence flags").build();
    }
    StandardApplicability applicability = applicability(input);
    if (!applicability.isApplicable()) {
      readiness.addBlocker("DNV_RP_F114_NOT_APPLICABLE", applicability.getReason(),
          "Use a catalogued submarine pipeline or pipe equipment type");
    }
    if (!supports(input.getEdition())) {
      readiness.addBlocker("DNV_RP_F114_EDITION_NOT_IMPLEMENTED",
          "The kernel implements " + IMPLEMENTED_EDITION + ", not " + input.getEdition().getDisplayName(),
          "Select the catalogued unamended edition or implement a controlled method version");
    }
    if (!positive(input.getPipelineOuterDiameterM()) || !positive(input.getSubmergedWeightNPerM())) {
      readiness.addBlocker("DNV_RP_F114_PIPE_BASIS_INVALID",
          "Pipeline outside diameter and submerged weight must be finite and positive",
          "Supply verified values in m and N/m, including the relevant coatings and contents");
    }
    validateInteractionCases(input, readiness);
    validateEvidence(input, readiness);
    readiness.addWarning("DNV_RP_F114_CALLER_CONTROLLED_RESISTANCE",
        "Every vertical, axial, and lateral resistance is caller-controlled geotechnical evidence",
        "Retain site investigation, interpretation, model calibration, load-displacement data, and approvals");
    readiness.addWarning("DNV_RP_F114_SCREENING_ONLY",
        "The result is a resistance-envelope screen and is not a DNV-RP-F114 conformity assessment",
        "Complete independent geotechnical, structural, installation, operational, and integrity reviews");
    return readiness.build();
  }

  private static void validateInteractionCases(Input input, CalculationReadiness.Builder readiness) {
    if (input.getInteractionCases().isEmpty()) {
      readiness.addBlocker("DNV_RP_F114_CASES_EMPTY", "At least one pipe-soil interaction case is required",
          "Supply project design situations with externally established demand and resistance magnitudes");
      return;
    }
    Set<String> labels = new HashSet<String>();
    for (int index = 0; index < input.getInteractionCases().size(); index++) {
      InteractionCase value = input.getInteractionCases().get(index);
      if (value == null) {
        readiness.addBlocker("DNV_RP_F114_CASE_MISSING", "Interaction cases cannot be null",
            "Replace the missing case at index " + index);
        continue;
      }
      String label = text(value.getLabel());
      if (label.isEmpty() || !labels.add(label)) {
        readiness.addBlocker("DNV_RP_F114_CASE_LABEL_INVALID", "Case labels must be non-blank and unique",
            "Correct the label at index " + index);
      }
      if (!nonNegative(value.getDistanceM()) || text(value.getDesignSituation()).isEmpty()) {
        readiness.addBlocker("DNV_RP_F114_CASE_BASIS_INVALID",
            "Case distance must be finite and non-negative and design situation must be non-blank",
            "Correct the route and design-situation basis for " + caseName(label, index));
      }
      if (!nonNegative(value.getVerticalDemandNPerM()) || !positive(value.getVerticalResistanceNPerM())
          || !nonNegative(value.getAxialDemandNPerM()) || !positive(value.getAxialResistanceNPerM())
          || !nonNegative(value.getLateralDemandNPerM()) || !positive(value.getLateralResistanceNPerM())) {
        readiness.addBlocker("DNV_RP_F114_CASE_VALUES_INVALID",
            "Demand magnitudes must be finite and non-negative and resistance magnitudes finite and positive",
            "Correct all N/m values for " + caseName(label, index));
      }
    }
  }

  private static void validateEvidence(Input input, CalculationReadiness.Builder readiness) {
    evidence(input.isApplicabilityVerified(), readiness, "APPLICABILITY",
        "Exposed or buried submarine-pipeline applicability has not been verified",
        "Verify pipeline scope, route, lifecycle phase, and relevant design situations");
    evidence(input.isSiteInvestigationVerified(), readiness, "SITE_INVESTIGATION",
        "Site-investigation coverage and data quality have not been verified",
        "Verify surveys, sampling, in-situ tests, laboratory tests, coverage, and data quality");
    evidence(input.isSoilModelVerified(), readiness, "SOIL_MODEL",
        "Soil stratigraphy and parameter interpretation have not been verified",
        "Verify soil units, parameters, drainage assumptions, layering, and geohazards");
    evidence(input.isPipelineConfigurationVerified(), readiness, "PIPE_CONFIGURATION",
        "Pipe configuration at the soil interface has not been verified",
        "Verify diameter, coating, roughness, submerged weight, penetration, burial, and trench geometry");
    evidence(input.isInstallationHistoryVerified(), readiness, "INSTALLATION_HISTORY",
        "Installation and as-laid history have not been verified",
        "Verify lay method, touchdown, embedment, remoulding, trenching, backfill, and as-laid survey evidence");
    evidence(input.isCyclicDrainageRateEffectsVerified(), readiness, "TIME_EFFECTS",
        "Cyclic, drainage, rate, consolidation, and remoulding effects have not been verified",
        "Verify effects relevant to installation, operation, storms, shutdown, and repeated movement");
    evidence(input.isLoadDisplacementAndResistanceVerified(), readiness, "RESISTANCE_MODEL",
        "Load-displacement models and resistance values have not been verified",
        "Verify vertical, axial, and lateral models, calibration range, displacement level, and units");
    evidence(input.isUncertaintyAndVariabilityVerified(), readiness, "UNCERTAINTY",
        "Geotechnical uncertainty and spatial variability have not been verified",
        "Verify characteristic values, sensitivity cases, route segmentation, and model uncertainty");
    evidence(input.isDesignActionsAndAcceptanceCriteriaVerified(), readiness, "DESIGN_ACTIONS",
        "Design actions, combinations, and project acceptance criteria have not been verified",
        "Verify structural-model actions, combinations, safety format, displacement criteria, and governing cases");
    evidence(input.isInterfacesAndLifecycleReviewed(), readiness, "INTERFACES",
        "F109/F110/F105/ST-F101 interfaces and lifecycle implications have not been reviewed",
        "Complete on-bottom stability, global buckling, free-span, structural, installation, and integrity handoffs");
  }

  private static void evidence(boolean verified, CalculationReadiness.Builder readiness, String suffix, String message,
      String action) {
    if (!verified) {
      readiness.addBlocker("DNV_RP_F114_" + suffix + "_NOT_VERIFIED", message, action);
    }
  }

  /** {@inheritDoc} */
  @Override
  public EngineeringCalculationResult<DnvRpF114PipeSoilInteractionAssessment> calculate(Input input,
      EngineeringCalculationContext context) {
    EngineeringCalculationContext effectiveContext = context == null ? EngineeringCalculationContext.builder().build()
        : context;
    CalculationReadiness readiness = assess(input, effectiveContext);
    EngineeringCalculationResult.Builder<DnvRpF114PipeSoilInteractionAssessment> result = EngineeringCalculationResult
        .<DnvRpF114PipeSoilInteractionAssessment>builder("dnv-rp-f114-pipe-soil-resistance-envelope-screening",
            getMethod(), getMethodVersion())
        .context(effectiveContext).readiness(readiness);
    if (input == null || !readiness.isReady()) {
      return result.status(EngineeringCalculationResult.Status.BLOCKED)
          .message("DNV-RP-F114 pipe-soil screening is blocked until readiness findings are resolved").build();
    }
    DnvRpF114PipeSoilInteractionAssessment assessment = new DnvRpF114PipeSoilInteractionAssessment(input);
    if (!numericallyValid(assessment)) {
      CalculationReadiness numericalReadiness = CalculationReadiness.builder().merge(readiness)
          .addBlocker("DNV_RP_F114_NUMERICAL_RESULT_INVALID",
              "Demand/resistance screening produced a non-finite result",
              "Review project demands, resistances, magnitudes, and units")
          .build();
      return result.readiness(numericalReadiness).status(EngineeringCalculationResult.Status.BLOCKED)
          .message("DNV-RP-F114 pipe-soil screening is blocked by an invalid numerical result").build();
    }
    return result.status(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED).value(assessment)
        .input("standard", input.getEdition().getDisplayName()).input("equipmentType", input.getEquipmentType())
        .input("pipeBasis", pipeBasisMap(input)).input("interactionCases", interactionCaseMaps(input))
        .warning("Constraint status is caller-controlled and is not a DNV-RP-F114 compliance decision")
        .warning("The kernel does not derive soil parameters, penetration, burial response, load-displacement curves, "
            + "resistance factors, characteristic values, or design actions")
        .warning("DNV-RP-F109 on-bottom stability, DNV-RP-F110 global buckling, DNV-RP-F105 free-span, and "
            + "DNV-ST-F101 structural checks are not replaced")
        .message(
            "DNV-RP-F114 caller-controlled pipe-soil resistance-envelope screen completed; review remains required")
        .build();
  }

  private static Map<String, Object> pipeBasisMap(Input input) {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("pipelineOuterDiameterM", Double.valueOf(input.getPipelineOuterDiameterM()));
    values.put("submergedWeightNPerM", Double.valueOf(input.getSubmergedWeightNPerM()));
    return values;
  }

  private static List<Map<String, Object>> interactionCaseMaps(Input input) {
    List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
    for (InteractionCase interactionCase : input.getInteractionCases()) {
      Map<String, Object> value = new LinkedHashMap<String, Object>();
      value.put("label", interactionCase.getLabel());
      value.put("distanceM", Double.valueOf(interactionCase.getDistanceM()));
      value.put("designSituation", interactionCase.getDesignSituation());
      value.put("verticalDemandNPerM", Double.valueOf(interactionCase.getVerticalDemandNPerM()));
      value.put("verticalResistanceNPerM", Double.valueOf(interactionCase.getVerticalResistanceNPerM()));
      value.put("axialDemandNPerM", Double.valueOf(interactionCase.getAxialDemandNPerM()));
      value.put("axialResistanceNPerM", Double.valueOf(interactionCase.getAxialResistanceNPerM()));
      value.put("lateralDemandNPerM", Double.valueOf(interactionCase.getLateralDemandNPerM()));
      value.put("lateralResistanceNPerM", Double.valueOf(interactionCase.getLateralResistanceNPerM()));
      values.add(value);
    }
    return values;
  }

  private static boolean numericallyValid(DnvRpF114PipeSoilInteractionAssessment assessment) {
    return Double.isFinite(assessment.getMinimumVerticalMarginNPerM())
        && Double.isFinite(assessment.getMaximumVerticalUtilization())
        && Double.isFinite(assessment.getMinimumAxialMarginNPerM())
        && Double.isFinite(assessment.getMaximumAxialUtilization())
        && Double.isFinite(assessment.getMinimumLateralMarginNPerM())
        && Double.isFinite(assessment.getMaximumLateralUtilization());
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
