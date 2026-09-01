package neqsim.process.engineering.mechanical;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.engineering.calculation.CalculationReadiness;
import neqsim.process.engineering.calculation.EngineeringCalculationContext;
import neqsim.process.engineering.calculation.EngineeringCalculationModule;
import neqsim.process.engineering.calculation.EngineeringCalculationResult;
import neqsim.process.engineering.calculation.EngineeringConstraintResult;

/**
 * Qualifies detailed pressure-boundary evidence without replacing the applicable mechanical design code.
 *
 * <p>
 * Results from equipment-specific {@code MechanicalDesign} implementations or a project-qualified external code
 * calculation are supplied explicitly. Internal pressure, external pressure, buckling, fatigue, nozzle reinforcement,
 * external loads, MDMT, corrosion allowance and fabrication controls remain separate checks. This prevents a passing
 * membrane-thickness calculation from being reported as a complete mechanical design.
 * </p>
 */
public final class MechanicalIntegrityQualificationCalculation implements
    EngineeringCalculationModule<MechanicalIntegrityQualificationCalculation.Input, MechanicalIntegrityQualificationCalculation.Result> {

  /** Controlled pressure-boundary calculation and fabrication evidence. */
  public static final class Input implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String equipmentTag;
    private final String sourceCalculation;
    private final String sourceCalculationVersion;
    private final double designPressureBara;
    private final double calculatedMawpBara;
    private final double requiredStructuralThicknessMm;
    private final double availableStructuralThicknessMm;
    private final double externalDesignPressureBar;
    private final double allowableExternalPressureBar;
    private final double bucklingUtilization;
    private final double fatigueUsageFactor;
    private final double requiredNozzleReinforcementAreaMm2;
    private final double availableNozzleReinforcementAreaMm2;
    private final double externalLoadUtilization;
    private final double designMinimumTemperatureC;
    private final double materialQualifiedMdmtC;
    private final double requiredCorrosionAllowanceMm;
    private final double specifiedCorrosionAllowanceMm;
    private final boolean designCodeBasisApproved;
    private final boolean materialSpecificationApproved;
    private final boolean fabricationProcedureApproved;
    private final boolean ndePlanApproved;

    private Input(Builder builder) {
      equipmentTag = text(builder.equipmentTag, "equipmentTag");
      sourceCalculation = text(builder.sourceCalculation, "sourceCalculation");
      sourceCalculationVersion = text(builder.sourceCalculationVersion, "sourceCalculationVersion");
      designPressureBara = positive(builder.designPressureBara, "designPressureBara");
      calculatedMawpBara = positive(builder.calculatedMawpBara, "calculatedMawpBara");
      requiredStructuralThicknessMm = positive(builder.requiredStructuralThicknessMm, "requiredStructuralThicknessMm");
      availableStructuralThicknessMm = positive(builder.availableStructuralThicknessMm,
          "availableStructuralThicknessMm");
      externalDesignPressureBar = nonNegative(builder.externalDesignPressureBar, "externalDesignPressureBar");
      allowableExternalPressureBar = nonNegative(builder.allowableExternalPressureBar, "allowableExternalPressureBar");
      bucklingUtilization = nonNegative(builder.bucklingUtilization, "bucklingUtilization");
      fatigueUsageFactor = nonNegative(builder.fatigueUsageFactor, "fatigueUsageFactor");
      requiredNozzleReinforcementAreaMm2 = nonNegative(builder.requiredNozzleReinforcementAreaMm2,
          "requiredNozzleReinforcementAreaMm2");
      availableNozzleReinforcementAreaMm2 = nonNegative(builder.availableNozzleReinforcementAreaMm2,
          "availableNozzleReinforcementAreaMm2");
      externalLoadUtilization = nonNegative(builder.externalLoadUtilization, "externalLoadUtilization");
      designMinimumTemperatureC = finite(builder.designMinimumTemperatureC, "designMinimumTemperatureC");
      materialQualifiedMdmtC = finite(builder.materialQualifiedMdmtC, "materialQualifiedMdmtC");
      requiredCorrosionAllowanceMm = nonNegative(builder.requiredCorrosionAllowanceMm, "requiredCorrosionAllowanceMm");
      specifiedCorrosionAllowanceMm = nonNegative(builder.specifiedCorrosionAllowanceMm,
          "specifiedCorrosionAllowanceMm");
      designCodeBasisApproved = builder.designCodeBasisApproved;
      materialSpecificationApproved = builder.materialSpecificationApproved;
      fabricationProcedureApproved = builder.fabricationProcedureApproved;
      ndePlanApproved = builder.ndePlanApproved;
    }

    public static Builder builder(String equipmentTag, String sourceCalculation, String sourceCalculationVersion) {
      return new Builder(equipmentTag, sourceCalculation, sourceCalculationVersion);
    }

    /** Builder for detailed mechanical qualification input. */
    public static final class Builder {
      private final String equipmentTag;
      private final String sourceCalculation;
      private final String sourceCalculationVersion;
      private double designPressureBara;
      private double calculatedMawpBara;
      private double requiredStructuralThicknessMm;
      private double availableStructuralThicknessMm;
      private double externalDesignPressureBar;
      private double allowableExternalPressureBar;
      private double bucklingUtilization;
      private double fatigueUsageFactor;
      private double requiredNozzleReinforcementAreaMm2;
      private double availableNozzleReinforcementAreaMm2;
      private double externalLoadUtilization;
      private double designMinimumTemperatureC;
      private double materialQualifiedMdmtC;
      private double requiredCorrosionAllowanceMm;
      private double specifiedCorrosionAllowanceMm;
      private boolean designCodeBasisApproved;
      private boolean materialSpecificationApproved;
      private boolean fabricationProcedureApproved;
      private boolean ndePlanApproved;

      private Builder(String equipmentTag, String sourceCalculation, String sourceCalculationVersion) {
        this.equipmentTag = equipmentTag;
        this.sourceCalculation = sourceCalculation;
        this.sourceCalculationVersion = sourceCalculationVersion;
      }

      public Builder internalPressure(double designPressureBara, double mawpBara, double requiredThicknessMm,
          double availableThicknessMm) {
        this.designPressureBara = designPressureBara;
        calculatedMawpBara = mawpBara;
        requiredStructuralThicknessMm = requiredThicknessMm;
        availableStructuralThicknessMm = availableThicknessMm;
        return this;
      }

      public Builder externalPressureAndBuckling(double externalPressureBar, double allowablePressureBar,
          double bucklingUtilization) {
        externalDesignPressureBar = externalPressureBar;
        allowableExternalPressureBar = allowablePressureBar;
        this.bucklingUtilization = bucklingUtilization;
        return this;
      }

      public Builder fatigueAndExternalLoads(double fatigueUsage, double loadUtilization) {
        fatigueUsageFactor = fatigueUsage;
        externalLoadUtilization = loadUtilization;
        return this;
      }

      public Builder nozzleReinforcement(double requiredAreaMm2, double availableAreaMm2) {
        requiredNozzleReinforcementAreaMm2 = requiredAreaMm2;
        availableNozzleReinforcementAreaMm2 = availableAreaMm2;
        return this;
      }

      public Builder temperatureAndCorrosion(double designMinimumTemperatureC, double materialMdmtC,
          double requiredCorrosionAllowanceMm, double specifiedCorrosionAllowanceMm) {
        this.designMinimumTemperatureC = designMinimumTemperatureC;
        materialQualifiedMdmtC = materialMdmtC;
        this.requiredCorrosionAllowanceMm = requiredCorrosionAllowanceMm;
        this.specifiedCorrosionAllowanceMm = specifiedCorrosionAllowanceMm;
        return this;
      }

      public Builder approvals(boolean designCode, boolean material, boolean fabrication, boolean nde) {
        designCodeBasisApproved = designCode;
        materialSpecificationApproved = material;
        fabricationProcedureApproved = fabrication;
        ndePlanApproved = nde;
        return this;
      }

      public Input build() {
        return new Input(this);
      }
    }
  }

  /** Immutable mechanical-integrity metrics and constraints. */
  public static final class Result implements EngineeringConstraintResult, Serializable {
    private static final long serialVersionUID = 1000L;
    private final Map<String, Double> metrics;
    private final Map<String, Boolean> constraints;

    private Result(Map<String, Double> metrics, Map<String, Boolean> constraints) {
      this.metrics = Collections.unmodifiableMap(new LinkedHashMap<String, Double>(metrics));
      this.constraints = Collections.unmodifiableMap(new LinkedHashMap<String, Boolean>(constraints));
    }

    @Override
    public boolean allConstraintsSatisfied() {
      for (Boolean value : constraints.values()) {
        if (!value.booleanValue()) {
          return false;
        }
      }
      return !constraints.isEmpty();
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("metrics", new LinkedHashMap<String, Double>(metrics));
      result.put("constraints", new LinkedHashMap<String, Boolean>(constraints));
      result.put("allConstraintsSatisfied", Boolean.valueOf(allConstraintsSatisfied()));
      result.put("qualificationConstraintsSatisfied", Boolean.valueOf(allConstraintsSatisfied()));
      result.put("finalCodeDesignComplete", Boolean.FALSE);
      result.put("fitnessForConstruction", Boolean.FALSE);
      result.put("engineeringApprovalRequired", Boolean.TRUE);
      return result;
    }
  }

  @Override
  public String getMethod() {
    return "pressure-boundary-mechanical-integrity-qualification";
  }

  @Override
  public String getMethodVersion() {
    return "1.0";
  }

  @Override
  public CalculationReadiness assess(Input input, EngineeringCalculationContext context) {
    CalculationReadiness.Builder readiness = CalculationReadiness.builder();
    if (input == null) {
      return readiness.addBlocker("MECHANICAL_INTEGRITY_INPUT", "Mechanical-integrity input is required",
          "Supply the applicable code calculation and fabrication evidence").build();
    }
    if (productionQualification(context)
        && (context.getEvidenceReferences().isEmpty() || context.getStandardReferences().isEmpty())) {
      readiness.addBlocker("MECHANICAL_INTEGRITY_PRODUCTION_EVIDENCE",
          "Production qualification requires code, material, load and fabrication evidence",
          "Attach the controlled mechanical calculation and project code basis");
    }
    return readiness.build();
  }

  @Override
  public EngineeringCalculationResult<Result> calculate(Input input, EngineeringCalculationContext context) {
    CalculationReadiness readiness = assess(input, context);
    EngineeringCalculationResult.Builder<Result> output = EngineeringCalculationResult
        .<Result>builder("mechanical-integrity:" + (input == null ? "unassigned" : input.equipmentTag), getMethod(),
            getMethodVersion())
        .context(context).readiness(readiness);
    if (!readiness.isReady()) {
      return output.status(EngineeringCalculationResult.Status.BLOCKED).build();
    }
    if (input == null) {
      return output.status(EngineeringCalculationResult.Status.BLOCKED).build();
    }

    Map<String, Double> metrics = new LinkedHashMap<String, Double>();
    metrics.put("mawpMarginBar", Double.valueOf(input.calculatedMawpBara - input.designPressureBara));
    metrics.put("structuralThicknessMarginMm",
        Double.valueOf(input.availableStructuralThicknessMm - input.requiredStructuralThicknessMm));
    metrics.put("externalPressureMarginBar",
        Double.valueOf(input.allowableExternalPressureBar - input.externalDesignPressureBar));
    metrics.put("bucklingUtilization", Double.valueOf(input.bucklingUtilization));
    metrics.put("fatigueUsageFactor", Double.valueOf(input.fatigueUsageFactor));
    metrics.put("nozzleReinforcementAreaMarginMm2",
        Double.valueOf(input.availableNozzleReinforcementAreaMm2 - input.requiredNozzleReinforcementAreaMm2));
    metrics.put("externalLoadUtilization", Double.valueOf(input.externalLoadUtilization));
    metrics.put("mdmtMarginC", Double.valueOf(input.designMinimumTemperatureC - input.materialQualifiedMdmtC));
    metrics.put("corrosionAllowanceMarginMm",
        Double.valueOf(input.specifiedCorrosionAllowanceMm - input.requiredCorrosionAllowanceMm));

    Map<String, Boolean> constraints = new LinkedHashMap<String, Boolean>();
    constraints.put("INTERNAL_PRESSURE_MAWP", Boolean.valueOf(input.calculatedMawpBara >= input.designPressureBara));
    constraints.put("STRUCTURAL_THICKNESS",
        Boolean.valueOf(input.availableStructuralThicknessMm >= input.requiredStructuralThicknessMm));
    constraints.put("EXTERNAL_PRESSURE",
        Boolean.valueOf(input.allowableExternalPressureBar >= input.externalDesignPressureBar));
    constraints.put("BUCKLING", Boolean.valueOf(input.bucklingUtilization <= 1.0));
    constraints.put("FATIGUE", Boolean.valueOf(input.fatigueUsageFactor <= 1.0));
    constraints.put("NOZZLE_REINFORCEMENT",
        Boolean.valueOf(input.availableNozzleReinforcementAreaMm2 >= input.requiredNozzleReinforcementAreaMm2));
    constraints.put("EXTERNAL_LOADS", Boolean.valueOf(input.externalLoadUtilization <= 1.0));
    constraints.put("MINIMUM_DESIGN_METAL_TEMPERATURE",
        Boolean.valueOf(input.designMinimumTemperatureC >= input.materialQualifiedMdmtC));
    constraints.put("CORROSION_ALLOWANCE",
        Boolean.valueOf(input.specifiedCorrosionAllowanceMm >= input.requiredCorrosionAllowanceMm));
    constraints.put("DESIGN_CODE_BASIS_APPROVED", Boolean.valueOf(input.designCodeBasisApproved));
    constraints.put("MATERIAL_SPECIFICATION_APPROVED", Boolean.valueOf(input.materialSpecificationApproved));
    constraints.put("FABRICATION_PROCEDURE_APPROVED", Boolean.valueOf(input.fabricationProcedureApproved));
    constraints.put("NDE_PLAN_APPROVED", Boolean.valueOf(input.ndePlanApproved));

    return output.status(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED)
        .input("equipmentTag", input.equipmentTag).input("sourceCalculation", input.sourceCalculation)
        .input("sourceCalculationVersion", input.sourceCalculationVersion).value(new Result(metrics, constraints))
        .warning("Final fabrication release and construction approval remain accountable project decisions").build();
  }

  private static boolean productionQualification(EngineeringCalculationContext context) {
    return context != null && "true".equalsIgnoreCase(context.getAttributes().get("productionQualification"));
  }

  private static String text(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  private static double positive(double value, String field) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(field + " must be finite and positive");
    }
    return value;
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
