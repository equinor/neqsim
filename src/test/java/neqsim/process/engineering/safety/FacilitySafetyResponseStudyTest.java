package neqsim.process.engineering.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import neqsim.process.engineering.calculation.EngineeringCalculationContext;
import neqsim.process.engineering.calculation.EngineeringCalculationResult;
import neqsim.process.equipment.compressor.AntiSurge;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.logic.action.TripValveAction;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.logic.voting.VotingPattern;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.depressurization.CoupledReliefBlowdownFlareCalculation;
import neqsim.process.safety.depressurization.CoupledReliefBlowdownFlareInput;
import neqsim.process.safety.depressurization.CoupledReliefBlowdownFlareResult;
import neqsim.process.safety.depressurization.DynamicBlowdownFlareStudyDataSource;
import neqsim.process.safety.depressurization.DynamicBlowdownFlareStudyDataSource.BlowdownSource;
import neqsim.process.safety.depressurization.DynamicBlowdownFlareStudyRunner;
import neqsim.process.safety.overpressure.BlockedOutletRelief;
import neqsim.process.safety.overpressure.OverpressureProtectionStudy;
import neqsim.process.safety.overpressure.ProtectedItem;
import neqsim.process.safety.overpressure.ReliefScenario;
import neqsim.process.safety.scenario.DynamicSafetyScenario;
import neqsim.process.safety.scenario.DynamicSafetyScenarioResult;
import neqsim.process.safety.scenario.DynamicSafetyScenarioRunner;
import neqsim.process.safety.scenario.DynamicScenarioCriterion;
import neqsim.process.safety.sif.ClosedLoopSafetyFunction;
import neqsim.process.safety.sif.SafetyFunctionChannel;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;

/** Verifies the cross-system facility response handoff using executed NeqSim calculations. */
class FacilitySafetyResponseStudyTest {

  @Test
  void joinsEsdCompressorTripBlowdownReliefFlareAndProcessConstraints() {
    DynamicSafetyScenarioResult esdResult = runEsdScenario();
    CoupledReliefBlowdownFlareResult disposalResult = runDisposalStudy();
    AntiSurge antiSurge = antiSurgeWithTripDemand();
    CompressorTripResponse compressorTrip = CompressorTripResponse.capture("K-101", antiSurge, true,
        Double.valueOf(1.8), 3.0, "C&E-K-101-REV-4");

    FacilitySafetyResponseStudy result = FacilitySafetyResponseStudy.builder("FACILITY-ESD-001")
        .scenarioSelectionReviewed(true).addEvidenceReference("HAZOP-TRAIN-A-REV-6")
        .addEvidenceReference("SRS-ESD-001-REV-3").addProtectionResult("2oo3 inlet ESD", esdResult)
        .disposalResult(disposalResult).addCompressorTripResponse(compressorTrip)
        .addConstraint(ProcessSafetyConstraint.minimum("MDMT-V-101", "Vessel minimum metal temperature", "degC", -20.0,
            -29.0, "V-101-DATASHEET-REV-2"))
        .addConstraint(ProcessSafetyConstraint.minimum("HYDRATE-MARGIN", "Minimum hydrate margin", "degC", 5.0, 3.0,
            "FLOW-ASSURANCE-BASIS-REV-5"))
        .build();

    assertTrue(esdResult.isPassed());
    assertTrue(disposalResult.isCapacityAcceptable());
    assertTrue(result.isTechnicallyAcceptable());
    assertTrue(result.isEvidenceComplete());
    assertTrue(result.isReadyForEngineeringReview());
    assertTrue(result.toJson().contains("dynamic_safety_scenario_result.v2"));
    assertTrue(result.toJson().contains("coupled_relief_blowdown_flare_result.v1"));
    assertTrue(result.toJson().contains("engineeringApprovalRequired"));
    assertTrue(result.getFindings().contains("Accountable engineering approval remains required"));
  }

  @Test
  void antiSurgeTripThresholdMustBePositive() {
    AntiSurge antiSurge = new AntiSurge();

    assertThrows(IllegalArgumentException.class, () -> antiSurge.setMaxSurgeCyclesBeforeTrip(0));
    assertEquals(3, antiSurge.getMaxSurgeCyclesBeforeTrip());
  }

  private DynamicSafetyScenarioResult runEsdScenario() {
    ProcessSystem base = esdProcess();
    DynamicSafetyScenario scenario = DynamicSafetyScenario.builder("SIF-HP-101", "HP inlet isolation")
        .durationSeconds(6.0).timeStepSeconds(1.0).triggerTimeSeconds(0.0)
        .initiatingEvent(new DynamicSafetyScenario.ProcessManipulator() {
          private static final long serialVersionUID = 1000L;

          @Override
          public void apply(ProcessSystem process) {
            ((Stream) process.getUnit("FEED")).setPressure(70.0, "bara");
          }
        }).addLogic(new DynamicSafetyScenario.LogicFactory() {
          private static final long serialVersionUID = 1000L;

          @Override
          public ClosedLoopSafetyFunction create(ProcessSystem process) {
            ESDValve valve = (ESDValve) process.getUnit("ESDV-101");
            ESDLogic finalElements = new ESDLogic("ESD-101 final elements");
            finalElements.addAction(new TripValveAction(valve), 0.0);
            return ClosedLoopSafetyFunction
                .builder("SIF-HP-101", "2oo3 high-pressure isolation", process, VotingPattern.TWO_OUT_OF_THREE,
                    finalElements)
                .addChannel(highPressureChannel("PT-101A", SafetyFunctionChannel.FaultMode.HEALTHY))
                .addChannel(highPressureChannel("PT-101B", SafetyFunctionChannel.FaultMode.HEALTHY))
                .addChannel(highPressureChannel("PT-101C", SafetyFunctionChannel.FaultMode.BYPASSED))
                .logicSolverDelaySeconds(0.5).build();
          }
        }).addCriterion(DynamicScenarioCriterion
            .builder("esdv-closed", "Inlet ESD valve closed", "%", new DynamicScenarioCriterion.Extractor() {
              private static final long serialVersionUID = 1000L;

              @Override
              public double extract(ProcessSystem process) {
                return ((ESDValve) process.getUnit("ESDV-101")).getPercentValveOpening();
              }
            }).acceptanceRange(null, Double.valueOf(5.0)).deadlineSeconds(4.0).build())
        .addEvidenceReference("SRS-SIF-HP-101").build();
    return DynamicSafetyScenarioRunner.run(base, scenario);
  }

  private CoupledReliefBlowdownFlareResult runDisposalStudy() {
    SystemInterface fluid = fluid(313.15, 60.0);
    ReliefScenario scenario = new BlockedOutletRelief().setName("Blocked outlet").setInflowRateKgPerS(2.0)
        .setReliefPressureBara(75.0).setReliefTemperatureC(30.0).setFluid(fluid).calculate();
    OverpressureProtectionStudy relief = new OverpressureProtectionStudy(
        new ProtectedItem("V-101", 80.0).setReliefSetPressureBara(75.0).setBackPressureBara(1.5)).addScenario(scenario);
    BlowdownSource source = BlowdownSource.builder("V-101", fluid).equipmentTag("V-101").vesselVolumeM3(2.0)
        .orificeDiameterM(0.012).dischargeCoefficient(0.72).backPressureBara(1.5).stopPressureBara(1.5)
        .api521FireCase(8.0, true, true).psvBasis(75.0, 0.21, false, false).build();
    DynamicBlowdownFlareStudyDataSource dynamic = DynamicBlowdownFlareStudyDataSource.builder("dynamic")
        .addSource(source).flareHeader(0.30, 1.5, 288.15, 0.020, 1.30).flareGeometry(0.5, 35.0, 0.20)
        .flareDesignCapacity(5.0e8, 500.0, 30000.0).build();
    CoupledReliefBlowdownFlareInput input = CoupledReliefBlowdownFlareInput.builder("coupled-1")
        .addReliefStudy(relief, "FIRE-ZONE-1").dynamicStudy(dynamic).scenarioSelectionReviewed(true)
        .addEvidenceReference("RELIEF-BASIS-REV-4").build();
    CoupledReliefBlowdownFlareCalculation calculation = new CoupledReliefBlowdownFlareCalculation(
        DynamicBlowdownFlareStudyRunner.builder().timeStepSeconds(5.0).maxTimeSeconds(60.0).build());
    EngineeringCalculationResult<CoupledReliefBlowdownFlareResult> calculationResult = calculation.calculate(input,
        EngineeringCalculationContext.builder().designCaseId("FIRE").build());
    return calculationResult.getValue();
  }

  private AntiSurge antiSurgeWithTripDemand() {
    AntiSurge antiSurge = new AntiSurge();
    antiSurge.setMaxSurgeCyclesBeforeTrip(2);
    antiSurge.setSurge(true);
    antiSurge.setSurge(false);
    antiSurge.setSurge(true);
    assertTrue(antiSurge.shouldTrip());
    return antiSurge;
  }

  private SafetyFunctionChannel highPressureChannel(String tag, SafetyFunctionChannel.FaultMode faultMode) {
    return SafetyFunctionChannel.highTrip(tag, "bara", 60.0, new SafetyFunctionChannel.SignalExtractor() {
      private static final long serialVersionUID = 1000L;

      @Override
      public double extract(ProcessSystem process) {
        return ((Stream) process.getUnit("FEED")).getPressure("bara");
      }
    }).responseDelaySeconds(1.0).faultMode(faultMode).build();
  }

  private ProcessSystem esdProcess() {
    Stream feed = new Stream("FEED", fluid(300.0, 50.0));
    feed.setFlowRate(1000.0, "kg/hr");
    ESDValve isolationValve = new ESDValve("ESDV-101", feed);
    isolationValve.setCv(500.0);
    isolationValve.setStrokeTime(2.0);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(isolationValve);
    process.run();
    return process;
  }

  private SystemInterface fluid(double temperatureK, double pressureBara) {
    SystemInterface fluid = new SystemSrkEos(temperatureK, pressureBara);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    return fluid;
  }
}
