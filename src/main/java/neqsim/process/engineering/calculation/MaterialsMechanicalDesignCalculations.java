package neqsim.process.engineering.calculation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed materials-selection and preliminary mechanical-design calculations. */
public final class MaterialsMechanicalDesignCalculations {
  private MaterialsMechanicalDesignCalculations() {
  }

  /** Governed process/environment input for materials screening. */
  public static final class MaterialInput implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String equipmentTag;
    private final String governingCaseId;
    private final double co2MoleFraction;
    private final double h2sMoleFraction;
    private final double chlorideMgL;
    private final boolean freeWaterPresent;
    private final boolean sourServiceDesignated;
    private final double operatingTemperatureC;
    private final double designTemperatureC;
    private final double depressurizationMinimumTemperatureC;
    private final double designPressureBara;
    private final double corrosionAllowanceMm;
    private final double designLifeYears;
    private final String externalEnvironment;

    public MaterialInput(String equipmentTag, String governingCaseId, double co2MoleFraction, double h2sMoleFraction,
        double chlorideMgL, boolean freeWaterPresent, boolean sourServiceDesignated, double operatingTemperatureC,
        double designTemperatureC, double depressurizationMinimumTemperatureC, double designPressureBara,
        double corrosionAllowanceMm, double designLifeYears, String externalEnvironment) {
      this.equipmentTag = text(equipmentTag, "equipmentTag");
      this.governingCaseId = text(governingCaseId, "governingCaseId");
      this.co2MoleFraction = fraction(co2MoleFraction, "co2MoleFraction");
      this.h2sMoleFraction = fraction(h2sMoleFraction, "h2sMoleFraction");
      this.chlorideMgL = nonNegative(chlorideMgL, "chlorideMgL");
      this.freeWaterPresent = freeWaterPresent;
      this.sourServiceDesignated = sourServiceDesignated;
      this.operatingTemperatureC = operatingTemperatureC;
      this.designTemperatureC = designTemperatureC;
      this.depressurizationMinimumTemperatureC = depressurizationMinimumTemperatureC;
      this.designPressureBara = positive(designPressureBara, "designPressureBara");
      this.corrosionAllowanceMm = nonNegative(corrosionAllowanceMm, "corrosionAllowanceMm");
      this.designLifeYears = positive(designLifeYears, "designLifeYears");
      this.externalEnvironment = text(externalEnvironment, "externalEnvironment");
    }
  }

  /** Review-required material class and governing degradation mechanisms. */
  public static final class MaterialResult implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final Map<String, Object> values;

    MaterialResult(Map<String, Object> values) {
      this.values = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(values));
    }

    public Map<String, Object> toMap() {
      return new LinkedHashMap<String, Object>(values);
    }
  }

  /** NORSOK M-001:2025-oriented degradation-mechanism and material-class screening. */
  public static final class MaterialSelection implements EngineeringCalculationModule<MaterialInput, MaterialResult> {
    @Override
    public String getMethod() {
      return "material-selection-and-degradation-screening";
    }

    @Override
    public String getMethodVersion() {
      return "2.0";
    }

    @Override
    public CalculationReadiness assess(MaterialInput input, EngineeringCalculationContext context) {
      CalculationReadiness.Builder result = CalculationReadiness.builder();
      if (input == null) {
        result.addBlocker("MATERIAL_INPUT", "Materials-selection input is required",
            "Supply composition, phases, environment and design conditions");
      }
      if (productionQualification(context)) {
        if (context.getEvidenceReferences().isEmpty() || context.getStandardReferences().isEmpty()) {
          result.addBlocker("MATERIAL_PRODUCTION_EVIDENCE", "Materials qualification evidence is incomplete",
              "Attach the corrosion assessment, process composition and applicable standards");
        }
        if (!"approved".equalsIgnoreCase(context.getAttributes().get("corrosionAssessment"))) {
          result.addBlocker("MATERIAL_CORROSION_ASSESSMENT", "Corrosion assessment is not approved",
              "Complete degradation-mechanism and corrosion-loop review");
        }
      }
      return result.build();
    }

    @Override
    public EngineeringCalculationResult<MaterialResult> calculate(MaterialInput input,
        EngineeringCalculationContext context) {
      CalculationReadiness readiness = assess(input, context);
      EngineeringCalculationResult.Builder<MaterialResult> result = EngineeringCalculationResult
          .<MaterialResult>builder("materials:" + (input == null ? "unassigned" : input.equipmentTag), getMethod(),
              getMethodVersion())
          .context(context).readiness(readiness);
      if (!readiness.isReady()) {
        return result.status(EngineeringCalculationResult.Status.BLOCKED).build();
      }
      List<String> mechanisms = new ArrayList<String>();
      if (input.freeWaterPresent && input.co2MoleFraction > 0.0) {
        mechanisms.add("WET_CO2_CORROSION");
      }
      if (input.sourServiceDesignated || input.h2sMoleFraction > 0.0) {
        mechanisms.add("H2S_SOUR_SERVICE_CRACKING");
      }
      if (input.freeWaterPresent && input.chlorideMgL > 1000.0) {
        mechanisms.add("CHLORIDE_PITTING_OR_SCC_SCREEN");
      }
      if (input.externalEnvironment.toUpperCase().contains("MARINE")) {
        mechanisms.add("EXTERNAL_MARINE_CORROSION_AND_CUI");
      }
      if (input.depressurizationMinimumTemperatureC < -29.0) {
        mechanisms.add("LOW_TEMPERATURE_BRITTLE_FRACTURE");
      }
      String materialClass = "CARBON_STEEL_WITH_CORROSION_ALLOWANCE";
      if (input.sourServiceDesignated || input.h2sMoleFraction > 0.0) {
        materialClass = "SOUR_SERVICE_CARBON_STEEL_ISO_15156_SCREEN";
      }
      if (input.chlorideMgL > 10000.0 && input.designTemperatureC > 60.0) {
        materialClass = "CORROSION_RESISTANT_ALLOY_SCREEN_REQUIRED";
      }
      Map<String, Object> values = new LinkedHashMap<String, Object>();
      values.put("equipmentTag", input.equipmentTag);
      values.put("governingCaseId", input.governingCaseId);
      values.put("preliminaryMaterialClass", materialClass);
      values.put("degradationMechanisms", mechanisms);
      values.put("corrosionAllowanceMm", Double.valueOf(input.corrosionAllowanceMm));
      values.put("designLifeYears", Double.valueOf(input.designLifeYears));
      values.put("designPressureBara", Double.valueOf(input.designPressureBara));
      values.put("designTemperatureC", Double.valueOf(input.designTemperatureC));
      values.put("minimumDesignMetalTemperatureC", Double.valueOf(input.depressurizationMinimumTemperatureC));
      values.put("standard", "NORSOK M-001:2025; ISO 15156 project-applicable editions");
      values.put("finalMetallurgyApproved", Boolean.FALSE);
      values.put("approvalStatus", "REVIEW_REQUIRED");
      return result.status(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED)
          .value(new MaterialResult(values)).warning("Corrosion loops, welding and final metallurgy require approval")
          .build();
    }
  }

  /** Governed preliminary pressure-equipment input. */
  public static final class MechanicalInput implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String equipmentTag;
    private final String governingCaseId;
    private final double designPressureBara;
    private final double designTemperatureC;
    private final double insideDiameterM;
    private final double tangentLengthM;
    private final double allowableStressMpa;
    private final double weldEfficiency;
    private final double corrosionAllowanceMm;
    private final double minimumDesignMetalTemperatureC;
    private final double nozzleDesignFlowM3s;

    public MechanicalInput(String equipmentTag, String governingCaseId, double designPressureBara,
        double designTemperatureC, double insideDiameterM, double tangentLengthM, double allowableStressMpa,
        double weldEfficiency, double corrosionAllowanceMm, double minimumDesignMetalTemperatureC,
        double nozzleDesignFlowM3s) {
      this.equipmentTag = text(equipmentTag, "equipmentTag");
      this.governingCaseId = text(governingCaseId, "governingCaseId");
      this.designPressureBara = positive(designPressureBara, "designPressureBara");
      this.designTemperatureC = designTemperatureC;
      this.insideDiameterM = positive(insideDiameterM, "insideDiameterM");
      this.tangentLengthM = positive(tangentLengthM, "tangentLengthM");
      this.allowableStressMpa = positive(allowableStressMpa, "allowableStressMpa");
      this.weldEfficiency = positive(weldEfficiency, "weldEfficiency");
      if (weldEfficiency > 1.0) {
        throw new IllegalArgumentException("weldEfficiency must not exceed one");
      }
      this.corrosionAllowanceMm = nonNegative(corrosionAllowanceMm, "corrosionAllowanceMm");
      this.minimumDesignMetalTemperatureC = minimumDesignMetalTemperatureC;
      this.nozzleDesignFlowM3s = nonNegative(nozzleDesignFlowM3s, "nozzleDesignFlowM3s");
    }
  }

  /** Preliminary MAWP, thickness, nozzle, weight, footprint and thermal-treatment result. */
  public static final class MechanicalResult implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final Map<String, Object> values;

    MechanicalResult(Map<String, Object> values) {
      this.values = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(values));
    }

    public Map<String, Object> toMap() {
      return new LinkedHashMap<String, Object>(values);
    }
  }

  /** Thin-shell preliminary mechanical calculation; not a code design or fabrication release. */
  public static final class PreliminaryMechanical
      implements EngineeringCalculationModule<MechanicalInput, MechanicalResult> {
    @Override
    public String getMethod() {
      return "preliminary-pressure-equipment-mechanical-design";
    }

    @Override
    public String getMethodVersion() {
      return "2.0";
    }

    @Override
    public CalculationReadiness assess(MechanicalInput input, EngineeringCalculationContext context) {
      CalculationReadiness.Builder result = CalculationReadiness.builder();
      if (input == null) {
        result.addBlocker("MECHANICAL_INPUT", "Mechanical-design input is required",
            "Supply governed pressure, temperature and geometry");
      }
      if (productionQualification(context)) {
        if (context.getEvidenceReferences().isEmpty() || context.getStandardReferences().isEmpty()) {
          result.addBlocker("MECHANICAL_PRODUCTION_EVIDENCE", "Mechanical qualification evidence is incomplete",
              "Attach design-code, allowable-stress, load and geometry evidence");
        }
        if (!"approved".equalsIgnoreCase(context.getAttributes().get("designCodeBasis"))) {
          result.addBlocker("MECHANICAL_DESIGN_CODE", "Design-code basis is not approved",
              "Approve the applicable pressure-equipment code and load cases");
        }
      }
      return result.build();
    }

    @Override
    public EngineeringCalculationResult<MechanicalResult> calculate(MechanicalInput input,
        EngineeringCalculationContext context) {
      CalculationReadiness readiness = assess(input, context);
      EngineeringCalculationResult.Builder<MechanicalResult> result = EngineeringCalculationResult
          .<MechanicalResult>builder("mechanical:" + (input == null ? "unassigned" : input.equipmentTag), getMethod(),
              getMethodVersion())
          .context(context).readiness(readiness);
      if (!readiness.isReady()) {
        return result.status(EngineeringCalculationResult.Status.BLOCKED).build();
      }
      double pressureMpa = Math.max(input.designPressureBara - 1.01325, 0.0) * 0.1;
      double structuralThicknessMm = pressureMpa * input.insideDiameterM * 1000.0
          / Math.max(2.0 * input.allowableStressMpa * input.weldEfficiency - 1.2 * pressureMpa, 1.0e-12);
      double nominalThicknessMm = Math.ceil(structuralThicknessMm + input.corrosionAllowanceMm);
      double availableStructuralThicknessMm = Math.max(nominalThicknessMm - input.corrosionAllowanceMm, 0.0);
      double mawpMpa = 2.0 * input.allowableStressMpa * input.weldEfficiency * availableStructuralThicknessMm
          / Math.max(input.insideDiameterM * 1000.0 + 1.2 * availableStructuralThicknessMm, 1.0e-12);
      double shellMassKg = Math.PI * input.insideDiameterM * input.tangentLengthM * nominalThicknessMm / 1000.0
          * 7850.0;
      double nozzleDiameterM = Math.sqrt(4.0 * input.nozzleDesignFlowM3s / Math.max(Math.PI * 5.0, 1.0e-12));
      Map<String, Object> values = new LinkedHashMap<String, Object>();
      values.put("equipmentTag", input.equipmentTag);
      values.put("governingCaseId", input.governingCaseId);
      values.put("designPressureBara", Double.valueOf(input.designPressureBara));
      values.put("designTemperatureC", Double.valueOf(input.designTemperatureC));
      values.put("preliminaryMawpBarg", Double.valueOf(mawpMpa * 10.0));
      values.put("requiredStructuralThicknessMm", Double.valueOf(structuralThicknessMm));
      values.put("preliminaryNominalThicknessMm", Double.valueOf(nominalThicknessMm));
      values.put("corrosionAllowanceMm", Double.valueOf(input.corrosionAllowanceMm));
      values.put("minimumDesignMetalTemperatureC", Double.valueOf(input.minimumDesignMetalTemperatureC));
      values.put("preliminaryNozzleInsideDiameterM", Double.valueOf(nozzleDiameterM));
      values.put("approximateShellMassKg", Double.valueOf(shellMassKg));
      values.put("footprintLengthM", Double.valueOf(input.tangentLengthM + input.insideDiameterM));
      values.put("footprintWidthM", Double.valueOf(1.5 * input.insideDiameterM));
      values.put("insulationRequired", Boolean.valueOf(input.designTemperatureC > 60.0));
      values.put("heatTracingRequired", Boolean.valueOf(input.designTemperatureC < 5.0));
      values.put("codeDesignComplete", Boolean.FALSE);
      values.put("approvalStatus", "REVIEW_REQUIRED");
      return result.status(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED)
          .value(new MechanicalResult(values))
          .warning("Code formula, external loads, fatigue, buckling, nozzles and fabrication require final design")
          .build();
    }
  }

  private static String text(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  private static boolean productionQualification(EngineeringCalculationContext context) {
    return context != null && "true".equalsIgnoreCase(context.getAttributes().get("productionQualification"));
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

  private static double fraction(double value, String field) {
    if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
      throw new IllegalArgumentException(field + " must be between zero and one");
    }
    return value;
  }
}
