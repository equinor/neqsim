package neqsim.process.engineering.calculation;

import java.util.Arrays;
import neqsim.process.engineering.production.EngineeringBenchmarkSuite;
import neqsim.process.engineering.production.EngineeringValidationBenchmark;
import neqsim.process.engineering.production.EngineeringValidationBenchmark.SourceClass;
import neqsim.process.mechanicaldesign.compressor.CompressorCasingDesignCalculator;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardType;
import neqsim.process.mechanicaldesign.pump.PumpApi610DesignCalculator;
import neqsim.process.mechanicaldesign.pump.PumpApi610DesignCalculator.Api610PumpType;
import neqsim.process.mechanicaldesign.pump.PumpApi610DesignCalculator.AssessmentStatus;
import neqsim.process.mechanicaldesign.pump.PumpApi610DesignCalculator.BearingType;
import neqsim.process.mechanicaldesign.pump.PumpApi610DesignCalculator.DataSource;
import neqsim.process.safety.overpressure.ProtectedItem;
import neqsim.process.safety.overpressure.ReliefCause;
import neqsim.process.safety.overpressure.ReliefPhase;
import neqsim.process.safety.overpressure.ReliefScenario;

/** Executable non-qualification regression suite for the common standard design kernels. */
public final class StandardDesignKernelVerificationSuite {
  private static final String SUITE_ID = "standard-design-kernel-regression";
  private static final String REVISION = "1";
  private static final String SOURCE_REFERENCE = "NEQSIM-STANDARD-KERNEL-REGRESSION";
  private static final double FAILURE_SENTINEL = 1.0e100;
  private static final double SQUARE_METRES_PER_SQUARE_INCH = 6.4516e-4;

  private StandardDesignKernelVerificationSuite() {
    // Utility class.
  }

  /**
   * Execute deterministic regression and unit-equivalence cases for every registered standard kernel.
   *
   * <p>
   * The returned cases use {@link SourceClass#REGRESSION_BASELINE}. Consequently
   * {@link EngineeringBenchmarkSuite.Report#areAllBenchmarksPassed()} may be true while
   * {@link EngineeringBenchmarkSuite.Report#isPassed()} remains false. Independent controlled evidence is still
   * required for method qualification.
   * </p>
   *
   * @return evaluated numeric regression report
   */
  public static EngineeringBenchmarkSuite.Report evaluateRegression() {
    PumpApi610DesignKernel pumpKernel = new PumpApi610DesignKernel();
    Api521ReliefDesignKernel reliefKernel = new Api521ReliefDesignKernel();
    Api526OrificeSelectionKernel orificeKernel = new Api526OrificeSelectionKernel();
    Api617CompressorDesignKernel compressorKernel = new Api617CompressorDesignKernel();
    Api12JSeparatorDesignKernel separatorKernel = new Api12JSeparatorDesignKernel();

    EngineeringBenchmarkSuite suite = new EngineeringBenchmarkSuite(SUITE_ID, REVISION)
        .requireMethod(methodKey(pumpKernel)).requireMethod(methodKey(reliefKernel)).requireMethod(methodKey(orificeKernel))
        .requireMethod(methodKey(compressorKernel)).requireMethod(methodKey(separatorKernel));
    suite.add(pumpBenchmark(pumpKernel));
    suite.add(reliefBenchmark(reliefKernel));
    suite.add(orificeBenchmark(orificeKernel));
    suite.add(compressorBenchmark(compressorKernel));
    suite.add(separatorBenchmark(separatorKernel));
    return suite.evaluate();
  }

  private static EngineeringValidationBenchmark pumpBenchmark(PumpApi610DesignKernel kernel) {
    PumpApi610DesignKernel.Input input = new PumpApi610DesignKernel.Input(
        StandardEdition.defaultEdition(StandardType.API_610), "Pump", pumpConfiguration());
    EngineeringCalculationResult<PumpApi610DesignAssessment> result = kernel.calculate(input, null);
    PumpApi610DesignAssessment value = result.getValue();
    return baseline("api-610-rated-duty", kernel)
        .check("calculatedReviewRequired", 1.0, calculated(result), "flag", 0.0, 0.0)
        .check("selectedDriverPower", 30.0, value == null ? FAILURE_SENTINEL : value.getSelectedDriverPowerKw(), "kW",
            1.0e-12, 1.0e-12)
        .check("screeningPass", 1.0,
            value != null && value.getAssessmentStatus() == AssessmentStatus.PASS ? 1.0 : 0.0, "flag", 0.0, 0.0)
        .build();
  }

  private static EngineeringValidationBenchmark reliefBenchmark(Api521ReliefDesignKernel kernel) {
    ProtectedItem item = new ProtectedItem("V-100", 100.0).setReliefSetPressureBara(100.0).setBackPressureBara(1.0);
    Api521ReliefDesignKernel.Input input = new Api521ReliefDesignKernel.Input(
        StandardEdition.defaultEdition(StandardType.API_521), "ProtectedItem", item,
        Arrays.asList(vapourScenario("blocked outlet", ReliefCause.BLOCKED_OUTLET, 1.0),
            vapourScenario("pool fire", ReliefCause.FIRE, 2.0)),
        false);
    EngineeringCalculationResult<Api521ReliefAssessment> result = kernel.calculate(input, null);
    Api521ReliefAssessment value = result.getValue();
    return baseline("api-521-governing-scenario", kernel)
        .check("calculatedReviewRequired", 1.0, calculated(result), "flag", 0.0, 0.0)
        .check("governingReliefRate", 2.0, value == null ? FAILURE_SENTINEL : value.getGoverningReliefRateKgPerS(),
            "kg/s", 1.0e-12, 1.0e-12)
        .check("capacityAdequate", 1.0, value != null && value.isCapacityAdequate() ? 1.0 : 0.0, "flag", 0.0, 0.0)
        .check("accumulatedPressureAccepted", 1.0,
            value != null && value.isAccumulatedPressureAccepted() ? 1.0 : 0.0, "flag", 0.0, 0.0)
        .build();
  }

  private static EngineeringValidationBenchmark orificeBenchmark(Api526OrificeSelectionKernel kernel) {
    StandardEdition edition = StandardEdition.defaultEdition(StandardType.API_526);
    Api526OrificeSelectionAssessment customary = kernel
        .calculate(new Api526OrificeSelectionKernel.Input(edition, "SafetyValve", 0.503,
            Api526OrificeSelectionKernel.AreaUnit.SQUARE_INCH), null)
        .getValue();
    Api526OrificeSelectionAssessment si = kernel
        .calculate(new Api526OrificeSelectionKernel.Input(edition, "SafetyReliefValve",
            0.503 * SQUARE_METRES_PER_SQUARE_INCH, Api526OrificeSelectionKernel.AreaUnit.SQUARE_METRE), null)
        .getValue();
    return baseline("api-526-boundary-and-unit-equivalence", kernel)
        .check("selectedStandardArea", 0.503,
            customary == null ? FAILURE_SENTINEL : customary.getSelectedAreaIn2(), "in2", 1.0e-12, 1.0e-12)
        .check("requiredAreaSiConversion", 0.503, si == null ? FAILURE_SENTINEL : si.getRequiredAreaIn2(), "in2",
            1.0e-12, 1.0e-12)
        .check("unitEquivalentRequiredArea", 0.0,
            customary == null || si == null ? FAILURE_SENTINEL
                : Math.abs(customary.getRequiredAreaIn2() - si.getRequiredAreaIn2()),
            "in2", 1.0e-12, 0.0)
        .check("adequate", 1.0, customary != null && customary.isAdequate() ? 1.0 : 0.0, "flag", 0.0, 0.0)
        .build();
  }

  private static EngineeringValidationBenchmark compressorBenchmark(Api617CompressorDesignKernel kernel) {
    Api617CompressorDesignKernel.Input input = new Api617CompressorDesignKernel.Input(
        StandardEdition.defaultEdition(StandardType.API_617), "Compressor", compressorConfiguration());
    EngineeringCalculationResult<Api617CompressorAssessment> result = kernel.calculate(input, null);
    Api617CompressorAssessment value = result.getValue();
    return baseline("api-617-pressure-containment", kernel)
        .check("calculatedReviewRequired", 1.0, calculated(result), "flag", 0.0, 0.0)
        .check("selectedWallThickness", 12.7,
            value == null ? FAILURE_SENTINEL : value.getSelectedWallThicknessMm(), "mm", 1.0e-12, 1.0e-12)
        .check("hydroTestPressure", 7.5, value == null ? FAILURE_SENTINEL : value.getHydroTestPressureMPa(), "MPa",
            1.0e-12, 1.0e-12)
        .build();
  }

  private static EngineeringValidationBenchmark separatorBenchmark(Api12JSeparatorDesignKernel kernel) {
    StandardEdition edition = StandardEdition.defaultEdition(StandardType.API_12J);
    Api12JSeparatorAssessment micrometre = kernel
        .calculate(new Api12JSeparatorDesignKernel.Input(edition, "Separator", 80.0,
            Api12JSeparatorDesignKernel.DiameterUnit.MICROMETRE, 0.08, false, 240.0,
            Api12JSeparatorDesignKernel.Orientation.HORIZONTAL, false), null)
        .getValue();
    Api12JSeparatorAssessment si = kernel
        .calculate(new Api12JSeparatorDesignKernel.Input(edition, "Separator", 80.0e-6,
            Api12JSeparatorDesignKernel.DiameterUnit.METRE, 0.08, false, 240.0,
            Api12JSeparatorDesignKernel.Orientation.HORIZONTAL, false), null)
        .getValue();
    return baseline("api-12j-screen-and-unit-equivalence", kernel)
        .check("gravityCutDiameter", 80.0,
            micrometre == null ? FAILURE_SENTINEL : micrometre.getGravityCutDiameterMicrometre(), "micrometre",
            1.0e-12, 1.0e-12)
        .check("kFactorUtilization", 2.0 / 3.0,
            micrometre == null ? FAILURE_SENTINEL : micrometre.getKFactorUtilization(), "fraction", 1.0e-12,
            1.0e-12)
        .check("unitEquivalentCutDiameter", 0.0,
            micrometre == null || si == null ? FAILURE_SENTINEL
                : Math.abs(micrometre.getGravityCutDiameterMicrometre() - si.getGravityCutDiameterMicrometre()),
            "micrometre", 1.0e-12, 0.0)
        .check("screeningPass", 1.0,
            micrometre != null && micrometre.areAllScreeningCriteriaPassing() ? 1.0 : 0.0, "flag", 0.0, 0.0)
        .build();
  }

  private static EngineeringValidationBenchmark.Builder baseline(String id, EquipmentDesignKernel<?, ?> kernel) {
    return EngineeringValidationBenchmark.builder(id, kernel.getMethod(), kernel.getMethodVersion())
        .source(SourceClass.REGRESSION_BASELINE, SOURCE_REFERENCE, REVISION);
  }

  private static String methodKey(EquipmentDesignKernel<?, ?> kernel) {
    return kernel.getMethod() + "@" + kernel.getMethodVersion();
  }

  private static double calculated(EngineeringCalculationResult<?> result) {
    return result.getStatus() == EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED ? 1.0 : 0.0;
  }

  private static ReliefScenario vapourScenario(String name, ReliefCause cause, double rateKgPerS) {
    return new ReliefScenario.Builder(name, cause).phase(ReliefPhase.VAPOUR).reliefRateKgPerS(rateKgPerS)
        .reliefTemperatureK(320.0).molarMassKgPerMol(0.020).compressibility(0.95).specificHeatRatio(1.25)
        .addAssumption("regression property basis").build();
  }

  private static PumpApi610DesignCalculator pumpConfiguration() {
    PumpApi610DesignCalculator calculator = new PumpApi610DesignCalculator();
    calculator.setPumpType(Api610PumpType.OH2);
    calculator.setDutyPoint(100.0, 80.0, 3000.0, 850.0, 25.0);
    calculator.setBepPoint(100.0, 80.0, DataSource.VENDOR_CURVE);
    calculator.setNpsh(6.0, 4.0, DataSource.VENDOR_CURVE);
    calculator.setPressureBasis(5.0, 20.0, 90.0, DataSource.VENDOR_CURVE);
    calculator.setHydrostaticTestPressureBara(30.0);
    calculator.setDriverCriteria(1.10, new double[] {22.0, 30.0, 37.0});
    calculator.setBearingData(BearingType.BALL, 100.0, 5.0);
    calculator.setMechanicalEvidence(0.03, 4000.0, 0.8, 2.5);
    return calculator;
  }

  private static CompressorCasingDesignCalculator compressorConfiguration() {
    CompressorCasingDesignCalculator calculator = new CompressorCasingDesignCalculator();
    calculator.setDesignPressureMPa(5.0);
    calculator.setMaxOperatingPressureMPa(4.0);
    calculator.setDesignTemperatureC(150.0);
    calculator.setMaxOperatingTemperatureC(100.0);
    calculator.setMinOperatingTemperatureC(-20.0);
    calculator.setCasingInnerDiameterMm(500.0);
    calculator.setCasingLengthMm(1500.0);
    calculator.setMaterialGrade("SA-516-70");
    calculator.setCorrosionAllowanceMm(1.5);
    calculator.setJointEfficiency(0.85);
    calculator.setSuctionNozzleSizeMm(200.0);
    calculator.setDischargeNozzleSizeMm(150.0);
    return calculator;
  }
}
