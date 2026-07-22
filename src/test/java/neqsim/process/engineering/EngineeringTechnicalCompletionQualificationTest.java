package neqsim.process.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import neqsim.process.engineering.calculation.EngineeringCalculationContext;
import neqsim.process.engineering.calculation.EngineeringCalculationResult;
import neqsim.process.engineering.instrumentation.ValveInstrumentQualificationCalculation;
import neqsim.process.engineering.mechanical.MechanicalIntegrityQualificationCalculation;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringGraphBuilder;
import neqsim.process.engineering.model.EngineeringIds;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.engineering.piping.TransientPipingQualificationCalculation;
import neqsim.process.engineering.production.EngineeringProductionReadinessAssessment;
import neqsim.process.engineering.production.EngineeringProductionReadinessBasis;
import neqsim.process.engineering.rotating.CompressorProtectionQualificationCalculation;
import neqsim.process.engineering.safety.FlareConsequenceCalculation;
import neqsim.process.processmodel.ProcessSystem;
import org.junit.jupiter.api.Test;

/** Tests the technical production-completion calculations and readiness gates. */
class EngineeringTechnicalCompletionQualificationTest {

  @Test
  void qualifiesDistributedTransientAndDetectsUnresolvedTimeScale() {
    EngineeringCalculationContext context = productionContext();
    TransientPipingQualificationCalculation calculation = new TransientPipingQualificationCalculation();
    EngineeringCalculationResult<TransientPipingQualificationCalculation.Result> passing = calculation
        .calculate(transientInput(0.5), context);

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, passing.getStatus());
    assertTrue(passing.getValue().allConstraintsSatisfied());
    assertTrue(passing.getValue().toMap().toString().contains("LINE_PACK_MASS_BALANCE"));

    EngineeringCalculationResult<TransientPipingQualificationCalculation.Result> underResolved = calculation
        .calculate(transientInput(2.0), context);
    assertFalse(underResolved.getValue().allConstraintsSatisfied());
    assertTrue(underResolved.getValue().toMap().toString().contains("TRANSIENT_TIME_RESOLUTION=false"));

    EngineeringCalculationContext missingDistributedModelApproval = EngineeringCalculationContext.builder()
        .attribute("productionQualification", "true").addStandardReference("PROJECT-PIPING-BASIS")
        .addEvidenceReference("TRANSIENT-RESULT-A").build();
    assertEquals(EngineeringCalculationResult.Status.BLOCKED,
        calculation.calculate(transientInput(0.5), missingDistributedModelApproval).getStatus());
  }

  @Test
  void qualifiesCompressorProtectionAcrossSteadyAndTransientEvidence() {
    CompressorProtectionQualificationCalculation.Input input = CompressorProtectionQualificationCalculation.Input
        .builder("K-100", "CompressorAntiSurgeApplication", "1.0")
        .addOperatingCase(new CompressorProtectionQualificationCalculation.OperatingCase("normal", 130.0, 100.0, 220.0,
            115.0, 4200.0, 0.0))
        .addOperatingCase(new CompressorProtectionQualificationCalculation.OperatingCase("turndown", 115.0, 100.0,
            200.0, 120.0, 3900.0, 0.01))
        .mapLimits(0.10, 0.10, 160.0, 0.02).driverAndStartup(5000.0, 1000.0, 1250.0).rundown(0.12, 0.10)
        .antiSurgeResponse(0.2, 0.3, 0.2, 1.0, 100.0, 90.0, 3.0).rotorDynamics(20.0, 15.0, true).settleOut(60.0, 70.0)
        .vendorGuaranteeAccepted(true).build();

    EngineeringCalculationResult<CompressorProtectionQualificationCalculation.Result> result = new CompressorProtectionQualificationCalculation()
        .calculate(input, productionContext());

    assertTrue(result.getValue().allConstraintsSatisfied());
    assertTrue(result.getValue().toMap().toString().contains("STARTUP_TORQUE=true"));
    assertTrue(result.getValue().toMap().toString().contains("ROTOR_DYNAMIC_REVIEW_APPROVED=true"));

    CompressorProtectionQualificationCalculation.Input slowProtection = CompressorProtectionQualificationCalculation.Input
        .builder("K-100", "CompressorAntiSurgeApplication", "1.0")
        .addOperatingCase(new CompressorProtectionQualificationCalculation.OperatingCase("normal", 130.0, 100.0, 220.0,
            115.0, 4200.0, 0.0))
        .mapLimits(0.10, 0.10, 160.0, 0.02).driverAndStartup(5000.0, 1000.0, 1250.0).rundown(0.12, 0.10)
        .antiSurgeResponse(0.5, 0.5, 0.5, 3.0, 100.0, 90.0, 3.0).rotorDynamics(20.0, 15.0, true).settleOut(60.0, 70.0)
        .vendorGuaranteeAccepted(true).build();
    EngineeringCalculationResult<CompressorProtectionQualificationCalculation.Result> slowResult = new CompressorProtectionQualificationCalculation()
        .calculate(slowProtection, productionContext());
    assertFalse(slowResult.getValue().allConstraintsSatisfied());
    assertTrue(slowResult.getValue().toMap().toString().contains("ANTI_SURGE_RESPONSE_TIME=false"));
  }

  @Test
  void qualifiesValveInstrumentAndMechanicalFailureModesIndependently() {
    ValveInstrumentQualificationCalculation.Input loop = ValveInstrumentQualificationCalculation.Input
        .builder("SIF-100", "ESDV-100", "PIT-100").actuator(10000.0, 15000.0, 80.0, 100.0).leakage(0.001, 0.005)
        .responseBudget(2.0, 0.5, 0.2, 5.0).transmitterRange(40.0, 90.0, 0.0, 100.0, 0.5, 1.0)
        .thermowell(3.0, 2.0, 0.60).approvals(true, true, true, true, true).build();
    EngineeringCalculationResult<ValveInstrumentQualificationCalculation.Result> loopResult = new ValveInstrumentQualificationCalculation()
        .calculate(loop, productionContext());
    assertTrue(loopResult.getValue().allConstraintsSatisfied());

    ValveInstrumentQualificationCalculation.Input slowLoop = ValveInstrumentQualificationCalculation.Input
        .builder("SIF-100", "ESDV-100", "PIT-100").actuator(10000.0, 15000.0, 80.0, 100.0).leakage(0.001, 0.005)
        .responseBudget(4.0, 1.0, 1.0, 5.0).transmitterRange(40.0, 90.0, 0.0, 100.0, 0.5, 1.0)
        .thermowell(3.0, 2.0, 0.60).approvals(true, true, true, true, true).build();
    EngineeringCalculationResult<ValveInstrumentQualificationCalculation.Result> slowLoopResult = new ValveInstrumentQualificationCalculation()
        .calculate(slowLoop, productionContext());
    assertFalse(slowLoopResult.getValue().allConstraintsSatisfied());
    assertTrue(slowLoopResult.getValue().toMap().toString().contains("PROCESS_SAFETY_TIME=false"));

    MechanicalIntegrityQualificationCalculation.Input pressureBoundary = MechanicalIntegrityQualificationCalculation.Input
        .builder("V-100", "SeparatorMechanicalDesign", "1.0").internalPressure(90.0, 105.0, 18.0, 22.0)
        .externalPressureAndBuckling(1.0, 2.5, 0.65).fatigueAndExternalLoads(0.40, 0.70)
        .nozzleReinforcement(1200.0, 1600.0).temperatureAndCorrosion(-40.0, -46.0, 3.0, 3.0)
        .approvals(true, true, true, true).build();
    EngineeringCalculationResult<MechanicalIntegrityQualificationCalculation.Result> mechanicalResult = new MechanicalIntegrityQualificationCalculation()
        .calculate(pressureBoundary, productionContext());
    assertTrue(mechanicalResult.getValue().allConstraintsSatisfied());

    MechanicalIntegrityQualificationCalculation.Input failedFatigue = MechanicalIntegrityQualificationCalculation.Input
        .builder("V-100", "SeparatorMechanicalDesign", "1.0").internalPressure(90.0, 105.0, 18.0, 22.0)
        .externalPressureAndBuckling(1.0, 2.5, 0.65).fatigueAndExternalLoads(1.20, 0.70)
        .nozzleReinforcement(1200.0, 1600.0).temperatureAndCorrosion(-40.0, -46.0, 3.0, 3.0)
        .approvals(true, true, true, true).build();
    assertFalse(new MechanicalIntegrityQualificationCalculation().calculate(failedFatigue, productionContext())
        .getValue().allConstraintsSatisfied());
  }

  @Test
  void calculatesFlareInterfacesAndFailsClosedWithoutMethodApproval() {
    FlareConsequenceCalculation.Input input = FlareConsequenceCalculation.Input.builder("flare-a")
        .radiation(100.0, 0.20, 0.90, 1.0, 100.0, 2.5).dispersion(10.0, 5.0, 0.10, 0.05, 0.01, 150.0)
        .noise(140.0, 0.005, 100.0).flareTip(100.0, 350.0, 0.50).build();
    FlareConsequenceCalculation calculation = new FlareConsequenceCalculation();
    EngineeringCalculationResult<FlareConsequenceCalculation.Result> result = calculation.calculate(input,
        productionContext());

    assertTrue(result.getValue().allConstraintsSatisfied());
    assertTrue(result.getValue().toMap().toString().contains("THERMAL_RADIATION=true"));

    EngineeringCalculationContext missingApproval = EngineeringCalculationContext.builder().designCaseId("fire")
        .attribute("productionQualification", "true").addStandardReference("PROJECT-FLARE-BASIS")
        .addEvidenceReference("FLARE-CALC-A").build();
    assertEquals(EngineeringCalculationResult.Status.BLOCKED,
        calculation.calculate(input, missingApproval).getStatus());
  }

  @Test
  void technicalCalculationsBecomeIndependentProductionReadinessGates() {
    EngineeringProductionReadinessBasis basis = passingTechnicalBasis();
    EngineeringProject project = new EngineeringProject("technical-completion", new ProcessSystem(),
        new EngineeringDesignBasis());
    project.setProductionReadinessBasis(basis);

    EngineeringProductionReadinessAssessment.Result result = EngineeringProductionReadinessAssessment.assess(project,
        basis);

    assertFalse(result.getFailedGates().contains("DISTRIBUTED_TRANSIENT_PIPING"));
    assertFalse(result.getFailedGates().contains("COMPRESSOR_PROTECTION_AND_MACHINERY"));
    assertFalse(result.getFailedGates().contains("VALVE_AND_INSTRUMENT_QUALIFICATION"));
    assertFalse(result.getFailedGates().contains("DETAILED_MECHANICAL_INTEGRITY"));
    assertFalse(result.getFailedGates().contains("FLARE_RADIATION_DISPERSION_AND_NOISE"));
    assertEquals(5, basis.getTechnicalMethodKeys().size());
    assertTrue(result.getFailedGates().contains("CLOSED_ENGINEERING_DESIGN_LOOP"));

    EngineeringGraph graph = EngineeringGraphBuilder.fromProject(project);
    EngineeringNode transientNode = graph
        .getNode(EngineeringIds.nodeId(EngineeringNode.Kind.CALCULATION, "transient-piping:export-network"));
    assertTrue(transientNode != null);
    assertEquals(Double.valueOf(1.0), transientNode.getProperties().get("resultValue"));
    assertEquals(5, basis.getTechnicalQualificationCalculations(
        EngineeringIds.nodeId(EngineeringNode.Kind.PROJECT, project.getProjectId())).size());
  }

  private EngineeringProductionReadinessBasis passingTechnicalBasis() {
    EngineeringCalculationContext context = productionContext();
    CompressorProtectionQualificationCalculation.Input compressor = CompressorProtectionQualificationCalculation.Input
        .builder("K-100", "CompressorAntiSurgeApplication", "1.0")
        .addOperatingCase(new CompressorProtectionQualificationCalculation.OperatingCase("normal", 130.0, 100.0, 220.0,
            115.0, 4200.0, 0.0))
        .mapLimits(0.10, 0.10, 160.0, 0.02).driverAndStartup(5000.0, 1000.0, 1250.0).rundown(0.12, 0.10)
        .antiSurgeResponse(0.2, 0.3, 0.2, 1.0, 100.0, 90.0, 3.0).rotorDynamics(20.0, 15.0, true).settleOut(60.0, 70.0)
        .vendorGuaranteeAccepted(true).build();
    ValveInstrumentQualificationCalculation.Input loop = ValveInstrumentQualificationCalculation.Input
        .builder("SIF-100", "ESDV-100", "PIT-100").actuator(10000.0, 15000.0, 80.0, 100.0).leakage(0.001, 0.005)
        .responseBudget(2.0, 0.5, 0.2, 5.0).transmitterRange(40.0, 90.0, 0.0, 100.0, 0.5, 1.0)
        .thermowell(3.0, 2.0, 0.60).approvals(true, true, true, true, true).build();
    MechanicalIntegrityQualificationCalculation.Input mechanical = MechanicalIntegrityQualificationCalculation.Input
        .builder("V-100", "SeparatorMechanicalDesign", "1.0").internalPressure(90.0, 105.0, 18.0, 22.0)
        .externalPressureAndBuckling(1.0, 2.5, 0.65).fatigueAndExternalLoads(0.40, 0.70)
        .nozzleReinforcement(1200.0, 1600.0).temperatureAndCorrosion(-40.0, -46.0, 3.0, 3.0)
        .approvals(true, true, true, true).build();
    FlareConsequenceCalculation.Input flare = FlareConsequenceCalculation.Input.builder("flare-a")
        .radiation(100.0, 0.20, 0.90, 1.0, 100.0, 2.5).dispersion(10.0, 5.0, 0.10, 0.05, 0.01, 150.0)
        .noise(140.0, 0.005, 100.0).flareTip(100.0, 350.0, 0.50).build();
    return new EngineeringProductionReadinessBasis()
        .transientPipingQualification(
            new TransientPipingQualificationCalculation().calculate(transientInput(0.5), context))
        .compressorProtectionQualification(
            new CompressorProtectionQualificationCalculation().calculate(compressor, context))
        .valveInstrumentQualification(new ValveInstrumentQualificationCalculation().calculate(loop, context))
        .mechanicalIntegrityQualification(
            new MechanicalIntegrityQualificationCalculation().calculate(mechanical, context))
        .flareConsequenceQualification(new FlareConsequenceCalculation().calculate(flare, context));
  }

  private TransientPipingQualificationCalculation.Input transientInput(double timeStepSeconds) {
    return TransientPipingQualificationCalculation.Input.builder("export-network", "TwoFluidPipe", "1.0")
        .geometry(1000.0, 10.0, 400.0).resolutionAndBalanceLimits(0.25, 0.01).pressureLimits(50.0, 100.0, 10.0)
        .responseLimits(1.0, 0.20, 150.0)
        .addSample(
            new TransientPipingQualificationCalculation.Sample(0.0, 10.0, 9.0, 1000.0, 70.0, 72.0, 0.05, 20.0, 80.0))
        .addSample(new TransientPipingQualificationCalculation.Sample(timeStepSeconds, 10.0, 9.0,
            1000.0 + timeStepSeconds, 69.0, 73.0, 0.05, 20.0, 85.0))
        .build();
  }

  private EngineeringCalculationContext productionContext() {
    return EngineeringCalculationContext.builder().designCaseId("production-qualification")
        .simulationFingerprint("synthetic-regression-a").attribute("productionQualification", "true")
        .attribute("distributedTransientModel", "approved").attribute("consequenceMethodApplicability", "approved")
        .addStandardReference("PROJECT-ENGINEERING-BASIS-A").addEvidenceReference("INDEPENDENT-CALCULATION-A")
        .addEvidenceReference("VENDOR-OR-HAZOP-EVIDENCE-A").build();
  }
}
