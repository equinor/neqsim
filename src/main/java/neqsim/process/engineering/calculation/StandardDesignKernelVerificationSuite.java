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
    NorsokM506CorrosionDesignKernel corrosionKernel = new NorsokM506CorrosionDesignKernel();
    Iso5167OrificeMeteringKernel meteringKernel = new Iso5167OrificeMeteringKernel();
    DnvRpC203FatigueDesignKernel fatigueKernel = new DnvRpC203FatigueDesignKernel();
    DnvRpF105FreeSpanScreeningKernel freeSpanKernel = new DnvRpF105FreeSpanScreeningKernel();
    DnvRpF101CorrodedPipelineScreeningKernel metalLossKernel = new DnvRpF101CorrodedPipelineScreeningKernel();
    DnvRpF104Co2PipelineEnvelopeScreeningKernel co2PipelineKernel = new DnvRpF104Co2PipelineEnvelopeScreeningKernel();
    DnvRpF110GlobalBucklingResponseScreeningKernel globalBucklingKernel = new DnvRpF110GlobalBucklingResponseScreeningKernel();
    DnvRpF114PipeSoilInteractionScreeningKernel pipeSoilKernel = new DnvRpF114PipeSoilInteractionScreeningKernel();
    Api2000TankVentingScreeningKernel tankVentingKernel = new Api2000TankVentingScreeningKernel();

    EngineeringBenchmarkSuite suite = new EngineeringBenchmarkSuite(SUITE_ID, REVISION)
        .requireMethod(methodKey(pumpKernel)).requireMethod(methodKey(reliefKernel))
        .requireMethod(methodKey(orificeKernel)).requireMethod(methodKey(compressorKernel))
        .requireMethod(methodKey(separatorKernel)).requireMethod(methodKey(corrosionKernel))
        .requireMethod(methodKey(meteringKernel)).requireMethod(methodKey(fatigueKernel))
        .requireMethod(methodKey(freeSpanKernel)).requireMethod(methodKey(metalLossKernel))
        .requireMethod(methodKey(tankVentingKernel)).requireMethod(methodKey(co2PipelineKernel))
        .requireMethod(methodKey(pipeSoilKernel)).requireMethod(methodKey(globalBucklingKernel));
    suite.add(pumpBenchmark(pumpKernel));
    suite.add(reliefBenchmark(reliefKernel));
    suite.add(orificeBenchmark(orificeKernel));
    suite.add(compressorBenchmark(compressorKernel));
    suite.add(separatorBenchmark(separatorKernel));
    suite.add(corrosionBenchmark(corrosionKernel));
    suite.add(meteringBenchmark(meteringKernel));
    suite.add(fatigueBenchmark(fatigueKernel));
    suite.add(freeSpanBenchmark(freeSpanKernel));
    suite.add(metalLossBenchmark(metalLossKernel));
    suite.add(tankVentingBenchmark(tankVentingKernel));
    suite.add(co2PipelineBenchmark(co2PipelineKernel));
    suite.add(pipeSoilBenchmark(pipeSoilKernel));
    suite.add(globalBucklingBenchmark(globalBucklingKernel));
    return suite.evaluate();
  }

  private static EngineeringValidationBenchmark pumpBenchmark(PumpApi610DesignKernel kernel) {
    PumpApi610DesignKernel.Input input = new PumpApi610DesignKernel.Input(
        StandardEdition.of(StandardType.API_610, "13th Ed"), "Pump", pumpConfiguration());
    EngineeringCalculationResult<PumpApi610DesignAssessment> result = kernel.calculate(input, null);
    PumpApi610DesignAssessment value = result.getValue();
    return baseline("api-610-rated-duty", kernel)
        .check("calculatedReviewRequired", 1.0, calculated(result), "flag", 0.0, 0.0)
        .check("selectedDriverPower", 30.0, value == null ? FAILURE_SENTINEL : value.getSelectedDriverPowerKw(), "kW",
            1.0e-12, 1.0e-12)
        .check("screeningPass", 1.0, value != null && value.getAssessmentStatus() == AssessmentStatus.PASS ? 1.0 : 0.0,
            "flag", 0.0, 0.0)
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
        .check("accumulatedPressureAccepted", 1.0, value != null && value.isAccumulatedPressureAccepted() ? 1.0 : 0.0,
            "flag", 0.0, 0.0)
        .build();
  }

  private static EngineeringValidationBenchmark orificeBenchmark(Api526OrificeSelectionKernel kernel) {
    StandardEdition edition = StandardEdition.of(StandardType.API_526, "7th Ed");
    Api526OrificeSelectionAssessment customary = kernel.calculate(new Api526OrificeSelectionKernel.Input(edition,
        "SafetyValve", 0.503, Api526OrificeSelectionKernel.AreaUnit.SQUARE_INCH), null).getValue();
    Api526OrificeSelectionAssessment si = kernel
        .calculate(new Api526OrificeSelectionKernel.Input(edition, "SafetyReliefValve",
            0.503 * SQUARE_METRES_PER_SQUARE_INCH, Api526OrificeSelectionKernel.AreaUnit.SQUARE_METRE), null)
        .getValue();
    return baseline("api-526-boundary-and-unit-equivalence", kernel)
        .check("selectedStandardArea", 0.503, customary == null ? FAILURE_SENTINEL : customary.getSelectedAreaIn2(),
            "in2", 1.0e-12, 1.0e-12)
        .check("requiredAreaSiConversion", 0.503, si == null ? FAILURE_SENTINEL : si.getRequiredAreaIn2(), "in2",
            1.0e-12, 1.0e-12)
        .check("unitEquivalentRequiredArea", 0.0,
            customary == null || si == null ? FAILURE_SENTINEL
                : Math.abs(customary.getRequiredAreaIn2() - si.getRequiredAreaIn2()),
            "in2", 1.0e-12, 0.0)
        .check("adequate", 1.0, customary != null && customary.isAdequate() ? 1.0 : 0.0, "flag", 0.0, 0.0).build();
  }

  private static EngineeringValidationBenchmark compressorBenchmark(Api617CompressorDesignKernel kernel) {
    Api617CompressorDesignKernel.Input input = new Api617CompressorDesignKernel.Input(
        StandardEdition.of(StandardType.API_617, "8th Ed"), "Compressor", compressorConfiguration());
    EngineeringCalculationResult<Api617CompressorAssessment> result = kernel.calculate(input, null);
    Api617CompressorAssessment value = result.getValue();
    return baseline("api-617-pressure-containment", kernel)
        .check("calculatedReviewRequired", 1.0, calculated(result), "flag", 0.0, 0.0)
        .check("selectedWallThickness", 12.7, value == null ? FAILURE_SENTINEL : value.getSelectedWallThicknessMm(),
            "mm", 1.0e-12, 1.0e-12)
        .check("hydroTestPressure", 7.5, value == null ? FAILURE_SENTINEL : value.getHydroTestPressureMPa(), "MPa",
            1.0e-12, 1.0e-12)
        .build();
  }

  private static EngineeringValidationBenchmark separatorBenchmark(Api12JSeparatorDesignKernel kernel) {
    StandardEdition edition = StandardEdition.of(StandardType.API_12J, "8th Ed");
    Api12JSeparatorAssessment micrometre = kernel.calculate(new Api12JSeparatorDesignKernel.Input(edition, "Separator",
        80.0, Api12JSeparatorDesignKernel.DiameterUnit.MICROMETRE, 0.08, false, 240.0,
        Api12JSeparatorDesignKernel.Orientation.HORIZONTAL, false), null).getValue();
    Api12JSeparatorAssessment si = kernel.calculate(new Api12JSeparatorDesignKernel.Input(edition, "Separator", 80.0e-6,
        Api12JSeparatorDesignKernel.DiameterUnit.METRE, 0.08, false, 240.0,
        Api12JSeparatorDesignKernel.Orientation.HORIZONTAL, false), null).getValue();
    return baseline("api-12j-screen-and-unit-equivalence", kernel)
        .check("gravityCutDiameter", 80.0,
            micrometre == null ? FAILURE_SENTINEL : micrometre.getGravityCutDiameterMicrometre(), "micrometre", 1.0e-12,
            1.0e-12)
        .check("kFactorUtilization", 2.0 / 3.0,
            micrometre == null ? FAILURE_SENTINEL : micrometre.getKFactorUtilization(), "fraction", 1.0e-12, 1.0e-12)
        .check("unitEquivalentCutDiameter", 0.0,
            micrometre == null || si == null ? FAILURE_SENTINEL
                : Math.abs(micrometre.getGravityCutDiameterMicrometre() - si.getGravityCutDiameterMicrometre()),
            "micrometre", 1.0e-12, 0.0)
        .check("screeningPass", 1.0, micrometre != null && micrometre.areAllScreeningCriteriaPassing() ? 1.0 : 0.0,
            "flag", 0.0, 0.0)
        .build();
  }

  private static EngineeringValidationBenchmark corrosionBenchmark(NorsokM506CorrosionDesignKernel kernel) {
    NorsokM506CorrosionDesignKernel.Input uninhibitedInput = corrosionInput(0.0);
    NorsokM506CorrosionDesignKernel.Input inhibitedInput = corrosionInput(0.8);
    EngineeringCalculationResult<NorsokM506CorrosionAssessment> uninhibited = kernel.calculate(uninhibitedInput, null);
    EngineeringCalculationResult<NorsokM506CorrosionAssessment> inhibited = kernel.calculate(inhibitedInput, null);
    NorsokM506CorrosionAssessment base = uninhibited.getValue();
    NorsokM506CorrosionAssessment treated = inhibited.getValue();
    double inhibitorRatio = base == null || treated == null ? FAILURE_SENTINEL
        : treated.getCorrectedCorrosionRateMmPerYear() / base.getCorrectedCorrosionRateMmPerYear();
    return baseline("norsok-m-506-rate-and-inhibitor-regression", kernel)
        .check("calculatedReviewRequired", 1.0, calculated(uninhibited), "flag", 0.0, 0.0)
        .check("co2PartialPressure", 2.0, base == null ? FAILURE_SENTINEL : base.getCO2PartialPressureBar(), "bar",
            1.0e-12, 1.0e-12)
        .check("correctedCorrosionRate", 13.894632683330206,
            base == null ? FAILURE_SENTINEL : base.getCorrectedCorrosionRateMmPerYear(), "mm/year", 1.0e-12, 1.0e-12)
        .check("inhibitorRatio", 0.2, inhibitorRatio, "fraction", 1.0e-12, 1.0e-12)
        .check("projectedLossIdentity", 0.0,
            base == null ? FAILURE_SENTINEL
                : Math.abs(base.getProjectedUniformWallLossMm() - 25.0 * base.getCorrectedCorrosionRateMmPerYear()),
            "mm", 1.0e-12, 0.0)
        .build();
  }

  private static EngineeringValidationBenchmark meteringBenchmark(Iso5167OrificeMeteringKernel kernel) {
    Iso5167OrificeMeteringKernel.Input input = Iso5167OrificeMeteringKernel.Input
        .builder(StandardEdition.defaultEdition(StandardType.ISO_5167_2), "Orifice")
        .serviceType(Iso5167OrificeMeteringKernel.ServiceType.LIQUID)
        .tapType(Iso5167OrificeMeteringKernel.TapType.FLANGE).pipeInternalDiameterM(0.1).orificeBoreDiameterM(0.05)
        .upstreamPressurePaAbsolute(500000.0).downstreamPressurePaAbsolute(480000.0).upstreamDensityKgPerM3(998.0)
        .upstreamDynamicViscosityPaS(0.001).singlePhase(true).conduitRunningFull(true).subsonicThroughoutMeter(true)
        .pulsatingFlow(false).geometryAndInstallationVerified(true).build();
    EngineeringCalculationResult<Iso5167OrificeMeteringAssessment> result = kernel.calculate(input, null);
    Iso5167OrificeMeteringAssessment value = result.getValue();
    return baseline("iso-5167-2-liquid-orifice", kernel)
        .check("calculatedReviewRequired", 1.0, calculated(result), "flag", 0.0, 0.0)
        .check("massFlowRate", 7.767376324178196, value == null ? FAILURE_SENTINEL : value.getMassFlowRateKgPerS(),
            "kg/s", 1.0e-12, 1.0e-12)
        .check("betaRatio", 0.5, value == null ? FAILURE_SENTINEL : value.getBetaRatio(), "fraction", 1.0e-12, 1.0e-12)
        .check("liquidExpansibility", 1.0, value == null ? FAILURE_SENTINEL : value.getExpansibilityFactor(),
            "fraction", 0.0, 0.0)
        .build();
  }

  private static EngineeringValidationBenchmark fatigueBenchmark(DnvRpC203FatigueDesignKernel kernel) {
    DnvRpC203FatigueDesignKernel.Input input = DnvRpC203FatigueDesignKernel.Input
        .builder(StandardEdition.defaultEdition(StandardType.DNV_RP_C203), "Pipeline")
        .snCurve(DnvRpC203FatigueDesignKernel.SnCurve.singleSlope("PROJECT-CONTROLLED-DEMO", 12.0, 3.0))
        .addStressBin("high range", 100.0, 1.0e5).addStressBin("moderate range", 50.0, 2.0e5)
        .stressConcentrationFactor(1.0).thicknessCorrectionFactor(1.0).otherStressRangeFactor(1.0)
        .designFatigueFactor(3.0).minerDamageLimit(1.0).assessedExposureYears(20.0).curveDefinitionVerified(true)
        .stressSpectrumVerified(true).build();
    EngineeringCalculationResult<DnvRpC203FatigueAssessment> result = kernel.calculate(input, null);
    DnvRpC203FatigueAssessment value = result.getValue();
    return baseline("dnv-rp-c203-sn-miner-regression", kernel)
        .check("calculatedReviewRequired", 1.0, calculated(result), "flag", 0.0, 0.0)
        .check("rawMinerDamage", 0.125, value == null ? FAILURE_SENTINEL : value.getRawMinerDamage(), "fraction",
            1.0e-12, 1.0e-12)
        .check("designMinerDamage", 0.375, value == null ? FAILURE_SENTINEL : value.getDesignMinerDamage(), "fraction",
            1.0e-12, 1.0e-12)
        .check("withinDamageLimit", 1.0, value != null && value.isWithinDamageLimit() ? 1.0 : 0.0, "flag", 0.0, 0.0)
        .build();
  }

  private static EngineeringValidationBenchmark freeSpanBenchmark(DnvRpF105FreeSpanScreeningKernel kernel) {
    DnvRpF105FreeSpanScreeningKernel.Input input = DnvRpF105FreeSpanScreeningKernel.Input
        .builder(StandardEdition.defaultEdition(StandardType.DNV_RP_F105), "Pipeline").spanLengthM(30.0)
        .steelOuterDiameterM(0.3239).steelWallThicknessM(0.0206).hydrodynamicDiameterM(0.3239).youngsModulusPa(207.0e9)
        .effectiveMassPerLengthKgPerM(250.0).effectiveAxialForceN(500000.0).currentVelocityMPerS(0.8)
        .waveOrbitalVelocityAmplitudeMPerS(1.2).wavePeriodS(10.0).strouhalNumber(0.2).lockInFrequencyRatioLower(0.8)
        .lockInFrequencyRatioUpper(1.2).maxCurrentReducedVelocityForScreening(4.0)
        .maxWaveReducedVelocityForScreening(3.0).spanGeometryVerified(true).structuralModelVerified(true)
        .environmentalBasisVerified(true).projectScreeningLimitsVerified(true).build();
    EngineeringCalculationResult<DnvRpF105FreeSpanAssessment> result = kernel.calculate(input, null);
    DnvRpF105FreeSpanAssessment value = result.getValue();
    return baseline("dnv-rp-f105-free-span-regression", kernel)
        .check("calculatedReviewRequired", 1.0, calculated(result), "flag", 0.0, 0.0)
        .check("fundamentalNaturalFrequency", 1.0618221449736536,
            value == null ? FAILURE_SENTINEL : value.getFundamentalNaturalFrequencyHz(), "Hz", 1.0e-12, 1.0e-12)
        .check("currentReducedVelocity", 2.326093996432868,
            value == null ? FAILURE_SENTINEL : value.getCurrentReducedVelocity(), "dimensionless", 1.0e-12, 1.0e-12)
        .check("waveReducedVelocity", 3.489140994649302,
            value == null ? FAILURE_SENTINEL : value.getWaveReducedVelocity(), "dimensionless", 1.0e-12, 1.0e-12)
        .check("detailedResponseTriggered", 1.0,
            value != null && value.isDetailedResponseAssessmentRequired() ? 1.0 : 0.0, "flag", 0.0, 0.0)
        .build();
  }

  private static EngineeringValidationBenchmark metalLossBenchmark(DnvRpF101CorrodedPipelineScreeningKernel kernel) {
    DnvRpF101CorrodedPipelineScreeningKernel.Input input = DnvRpF101CorrodedPipelineScreeningKernel.Input
        .builder(StandardEdition.defaultEdition(StandardType.DNV_RP_F101), "Pipeline").steelOuterDiameterM(0.508)
        .assessmentWallThicknessM(0.0127).measuredDefectDepthM(0.004).defectDepthAllowanceM(0.0005)
        .defectAxialLengthM(0.2).characteristicUltimateTensileStrengthPa(535.0e6).internalPressurePaAbsolute(10.1e6)
        .externalPressurePaAbsolute(0.1e6).callerControlledPressureFactor(0.72).geometryVerified(true)
        .inspectionSizingVerified(true).materialStrengthVerified(true).pressureBasisVerified(true)
        .projectFactorVerified(true).isolatedLongitudinalMetalLossApplicabilityVerified(true).build();
    EngineeringCalculationResult<DnvRpF101CorrodedPipelineAssessment> result = kernel.calculate(input, null);
    DnvRpF101CorrodedPipelineAssessment value = result.getValue();
    return baseline("dnv-rp-f101-isolated-metal-loss-regression", kernel)
        .check("calculatedReviewRequired", 1.0, calculated(result), "flag", 0.0, 0.0)
        .check("calculatedFailurePressure", 22346646.7258146,
            value == null ? FAILURE_SENTINEL : value.getCalculatedFailurePressurePa(), "Pa", 1.0e-8, 1.0e-12)
        .check("callerControlledPressureLimit", 16089585.64258651,
            value == null ? FAILURE_SENTINEL : value.getCallerControlledPressureLimitPa(), "Pa", 1.0e-8, 1.0e-12)
        .check("pressureUtilization", 0.6215200454591963,
            value == null ? FAILURE_SENTINEL : value.getPressureUtilization(), "fraction", 1.0e-15, 1.0e-12)
        .check("withinCallerControlledLimit", 1.0,
            value != null && value.isWithinCallerControlledPressureLimit() ? 1.0 : 0.0, "flag", 0.0, 0.0)
        .build();
  }

  private static EngineeringValidationBenchmark tankVentingBenchmark(Api2000TankVentingScreeningKernel kernel) {
    Api2000TankVentingScreeningKernel.Input input = Api2000TankVentingScreeningKernel.Input
        .builder(StandardEdition.defaultEdition(StandardType.API_2000), "Tank").liquidFillingRateM3PerS(0.1)
        .fillingOutbreathingVolumeRatio(1.05).liquidWithdrawalRateM3PerS(0.08).withdrawalInbreathingVolumeRatio(1.0)
        .thermalOutbreathingRateM3PerS(0.02).thermalInbreathingRateM3PerS(0.03).otherNormalOutbreathingRateM3PerS(0.005)
        .otherNormalInbreathingRateM3PerS(0.005).totalEmergencyOutbreathingRateM3PerS(0.5)
        .normalOutbreathingRatedCapacityM3PerS(0.2).normalInbreathingRatedCapacityM3PerS(0.15)
        .emergencyOutbreathingRatedCapacityM3PerS(0.6).tankMaximumPositiveGaugePressurePa(5000.0)
        .tankMaximumVacuumPressurePa(2000.0).normalOutbreathingRatedGaugePressurePa(3000.0)
        .normalInbreathingRatedVacuumPressurePa(1500.0).emergencyOutbreathingRatedGaugePressurePa(4500.0)
        .flowReferenceTemperatureK(288.15).flowReferencePressurePaAbsolute(101325.0)
        .fixedRoofNonRefrigeratedApplicabilityVerified(true).ventDemandBasisVerified(true)
        .ratedCapacityBasisVerified(true).pressureVacuumBasisVerified(true).normalCombinationBasisVerified(true)
        .emergencyCombinationBasisVerified(true).build();
    EngineeringCalculationResult<Api2000TankVentingAssessment> result = kernel.calculate(input, null);
    Api2000TankVentingAssessment value = result.getValue();
    return baseline("api-2000-tank-vent-demand-capacity-regression", kernel)
        .check("calculatedReviewRequired", 1.0, calculated(result), "flag", 0.0, 0.0)
        .check("normalOutbreathingDemand", 0.13,
            value == null ? FAILURE_SENTINEL : value.getRequiredNormalOutbreathingRateM3PerS(), "m3/s", 1.0e-15,
            1.0e-12)
        .check("normalInbreathingDemand", 0.115,
            value == null ? FAILURE_SENTINEL : value.getRequiredNormalInbreathingRateM3PerS(), "m3/s", 1.0e-15, 1.0e-12)
        .check("emergencyUtilization", 0.8333333333333334,
            value == null ? FAILURE_SENTINEL : value.getEmergencyOutbreathingUtilization(), "fraction", 1.0e-15,
            1.0e-12)
        .check("allCallerControlledConstraintsSatisfied", 1.0,
            value != null && value.allConstraintsSatisfied() ? 1.0 : 0.0, "flag", 0.0, 0.0)
        .build();
  }

  private static EngineeringValidationBenchmark co2PipelineBenchmark(
      DnvRpF104Co2PipelineEnvelopeScreeningKernel kernel) {
    DnvRpF104Co2PipelineEnvelopeScreeningKernel.Input input = DnvRpF104Co2PipelineEnvelopeScreeningKernel.Input
        .builder(StandardEdition.defaultEdition(StandardType.DNV_RP_F104), "Pipeline").co2MoleFraction(0.98)
        .minimumCo2MoleFraction(0.97).waterMoleFraction(0.0001).maximumWaterMoleFraction(0.0002)
        .otherImpuritiesWithinProjectSpecification(true).designMinimumTemperatureK(273.15)
        .designMaximumTemperatureK(323.15).maximumAllowableOperatingPressurePaAbsolute(15.0e6)
        .addOperatingPoint(
            new DnvRpF104Co2PipelineEnvelopeScreeningKernel.OperatingPoint("inlet", 0.0, 14.0e6, 293.15, 10.0e6))
        .addOperatingPoint(
            new DnvRpF104Co2PipelineEnvelopeScreeningKernel.OperatingPoint("outlet", 100000.0, 10.5e6, 283.0, 9.5e6))
        .co2PipelineApplicabilityVerified(true).compositionAndSpecificationVerified(true)
        .thermodynamicModelVerified(true).singlePhaseBoundaryInterpretationVerified(true).operatingProfileVerified(true)
        .pressureTemperatureLimitsVerified(true).materialsCorrosionAndFractureBasisVerified(true)
        .safetyConstructionOperationsAndRequalificationReviewed(true).build();
    EngineeringCalculationResult<DnvRpF104Co2PipelineEnvelopeAssessment> result = kernel.calculate(input, null);
    DnvRpF104Co2PipelineEnvelopeAssessment value = result.getValue();
    return baseline("dnv-rp-f104-co2-pipeline-envelope-regression", kernel)
        .check("calculatedReviewRequired", 1.0, calculated(result), "flag", 0.0, 0.0)
        .check("co2MoleFractionMargin", 0.01, value == null ? FAILURE_SENTINEL : value.getCo2MoleFractionMargin(),
            "fraction", 1.0e-15, 1.0e-12)
        .check("minimumSinglePhasePressureMargin", 1.0e6,
            value == null ? FAILURE_SENTINEL : value.getMinimumSinglePhasePressureMarginPa(), "Pa", 0.0, 0.0)
        .check("minimumMaopMargin", 1.0e6,
            value == null ? FAILURE_SENTINEL : value.getMinimumMaximumAllowableOperatingPressureMarginPa(), "Pa", 0.0,
            0.0)
        .check("allCallerControlledConstraintsSatisfied", 1.0,
            value != null && value.allConstraintsSatisfied() ? 1.0 : 0.0, "flag", 0.0, 0.0)
        .build();
  }

  private static EngineeringValidationBenchmark pipeSoilBenchmark(DnvRpF114PipeSoilInteractionScreeningKernel kernel) {
    DnvRpF114PipeSoilInteractionScreeningKernel.Input input = DnvRpF114PipeSoilInteractionScreeningKernel.Input
        .builder(StandardEdition.defaultEdition(StandardType.DNV_RP_F114), "Pipeline").pipelineOuterDiameterM(0.3239)
        .submergedWeightNPerM(1200.0)
        .addInteractionCase(new DnvRpF114PipeSoilInteractionScreeningKernel.InteractionCase("route section 1", 0.0,
            "installation", 200.0, 500.0, 80.0, 160.0, 120.0, 240.0))
        .addInteractionCase(new DnvRpF114PipeSoilInteractionScreeningKernel.InteractionCase("route section 2", 25000.0,
            "operation", 300.0, 600.0, 100.0, 250.0, 220.0, 275.0))
        .applicabilityVerified(true).siteInvestigationVerified(true).soilModelVerified(true)
        .pipelineConfigurationVerified(true).installationHistoryVerified(true).cyclicDrainageRateEffectsVerified(true)
        .loadDisplacementAndResistanceVerified(true).uncertaintyAndVariabilityVerified(true)
        .designActionsAndAcceptanceCriteriaVerified(true).interfacesAndLifecycleReviewed(true).build();
    EngineeringCalculationResult<DnvRpF114PipeSoilInteractionAssessment> result = kernel.calculate(input, null);
    DnvRpF114PipeSoilInteractionAssessment value = result.getValue();
    return baseline("dnv-rp-f114-pipe-soil-resistance-envelope-regression", kernel)
        .check("calculatedReviewRequired", 1.0, calculated(result), "flag", 0.0, 0.0)
        .check("minimumVerticalMargin", 300.0, value == null ? FAILURE_SENTINEL : value.getMinimumVerticalMarginNPerM(),
            "N/m", 0.0, 0.0)
        .check("maximumAxialUtilization", 0.5, value == null ? FAILURE_SENTINEL : value.getMaximumAxialUtilization(),
            "fraction", 0.0, 0.0)
        .check("maximumLateralUtilization", 0.8,
            value == null ? FAILURE_SENTINEL : value.getMaximumLateralUtilization(), "fraction", 1.0e-15, 1.0e-12)
        .check("allCallerControlledConstraintsSatisfied", 1.0,
            value != null && value.allConstraintsSatisfied() ? 1.0 : 0.0, "flag", 0.0, 0.0)
        .build();
  }

  private static EngineeringValidationBenchmark globalBucklingBenchmark(
      DnvRpF110GlobalBucklingResponseScreeningKernel kernel) {
    DnvRpF110GlobalBucklingResponseScreeningKernel.Input input = DnvRpF110GlobalBucklingResponseScreeningKernel.Input
        .builder(StandardEdition.defaultEdition(StandardType.DNV_RP_F110), "Pipeline").pipelineOuterDiameterM(0.3239)
        .steelWallThicknessM(0.0206)
        .addBucklingCase(new DnvRpF110GlobalBucklingResponseScreeningKernel.BucklingCase("controlled buckle 1", 0.0,
            "operation", DnvRpF110GlobalBucklingResponseScreeningKernel.PipelineConfiguration.EXPOSED,
            DnvRpF110GlobalBucklingResponseScreeningKernel.DesignStrategy.CONTROLLED_BUCKLING, 8.0e6, 10.0e6, 0.006,
            0.010, 4.0, 5.0, 300.0, 400.0))
        .addBucklingCase(new DnvRpF110GlobalBucklingResponseScreeningKernel.BucklingCase("buried section 1", 25000.0,
            "shutdown", DnvRpF110GlobalBucklingResponseScreeningKernel.PipelineConfiguration.BURIED,
            DnvRpF110GlobalBucklingResponseScreeningKernel.DesignStrategy.BUCKLING_PREVENTION, 6.0e6, 10.0e6, 0.004,
            0.008, 0.3, 0.5, 100.0, 200.0))
        .applicabilityVerified(true).operatingEnvelopeAndEffectiveForceVerified(true)
        .pipePropertiesAndAsLaidGeometryVerified(true).pipeSoilInteractionVerified(true)
        .imperfectionTriggerAndStrategyVerified(true).globalStructuralModelVerified(true)
        .designSituationsAndLoadCombinationsVerified(true).localCapacityAndStrainCriteriaVerified(true)
        .uncertaintySensitivityAndBuckleSharingVerified(true)
        .installationInterventionMonitoringAndLifecycleReviewed(true).build();
    EngineeringCalculationResult<DnvRpF110GlobalBucklingResponseAssessment> result = kernel.calculate(input, null);
    DnvRpF110GlobalBucklingResponseAssessment value = result.getValue();
    return baseline("dnv-rp-f110-global-buckling-response-envelope-regression", kernel)
        .check("calculatedReviewRequired", 1.0, calculated(result), "flag", 0.0, 0.0)
        .check("maximumCompressiveForceUtilization", 0.8,
            value == null ? FAILURE_SENTINEL : value.getMaximumCompressiveForceUtilization(), "fraction", 0.0, 0.0)
        .check("maximumLongitudinalStrainUtilization", 0.6,
            value == null ? FAILURE_SENTINEL : value.getMaximumLongitudinalStrainUtilization(), "fraction", 1.0e-15,
            1.0e-12)
        .check("maximumGlobalDisplacementUtilization", 0.8,
            value == null ? FAILURE_SENTINEL : value.getMaximumGlobalDisplacementUtilization(), "fraction", 0.0, 0.0)
        .check("maximumFeedInLengthUtilization", 0.75,
            value == null ? FAILURE_SENTINEL : value.getMaximumFeedInLengthUtilization(), "fraction", 0.0, 0.0)
        .check("allCallerControlledConstraintsSatisfied", 1.0,
            value != null && value.allConstraintsSatisfied() ? 1.0 : 0.0, "flag", 0.0, 0.0)
        .build();
  }

  private static NorsokM506CorrosionDesignKernel.Input corrosionInput(double inhibitorEfficiency) {
    return NorsokM506CorrosionDesignKernel.Input
        .builder(StandardEdition.defaultEdition(StandardType.NORSOK_M_506), "Pipeline").temperatureC(60.0)
        .totalPressureBara(100.0).co2MoleFraction(0.02).actualPH(4.2).flowVelocityMPerS(3.0)
        .pipeInternalDiameterM(0.254).liquidDensityKgPerM3(1000.0).liquidDynamicViscosityPaS(0.001)
        .inhibitorEfficiencyFraction(inhibitorEfficiency).exposureYears(25.0).build();
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
    calculator.setDriverCriteria(1.10, new double[] { 22.0, 30.0, 37.0 });
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
