package neqsim.process.engineering.safety;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import neqsim.process.engineering.SafetyFunctionDesign;
import neqsim.process.engineering.SafetyFunctionDesign.Subsystem;
import neqsim.process.engineering.SafetyFunctionDesign.SubsystemType;
import neqsim.process.engineering.production.EngineeringBenchmarkSuite;
import neqsim.process.engineering.production.EngineeringValidationBenchmark.SourceClass;
import neqsim.process.engineering.safety.lifecycle.HazopLopaSrsWorkflow;
import neqsim.process.engineering.safety.lifecycle.HazopLopaSrsWorkflow.SrsDesignInputs;
import neqsim.process.engineering.safety.lifecycle.LopaScenarioDefinition;
import neqsim.process.engineering.safety.lifecycle.ProtectionLayerDefinition;
import neqsim.process.engineering.safety.lifecycle.ProtectionLayerDefinition.LayerType;
import neqsim.process.engineering.safety.lifecycle.SafetyRequirementSpecificationDraft.TripDirection;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.logic.action.TripValveAction;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.logic.voting.VotingPattern;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.risk.sis.LOPAResult;
import neqsim.process.safety.scenario.DynamicSafetyScenario;
import neqsim.process.safety.scenario.DynamicSafetyScenarioResult;
import neqsim.process.safety.scenario.DynamicSafetyScenarioRunner;
import neqsim.process.safety.scenario.DynamicScenarioCriterion;
import neqsim.process.safety.sif.ClosedLoopSafetyFunction;
import neqsim.process.safety.sif.SafetyFunctionChannel;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;

/** Tests safety benchmark qualification with analytical and executed external reference values. */
class SafetyVerificationBenchmarkSuiteTest {

  @Test
  void qualifiesOnlyWhenAllThreeIndependentReviewedBenchmarksPass() {
    EngineeringBenchmarkSuite.Report report = new SafetyVerificationBenchmarkSuite("SAFETY-BENCH-001", "A",
        SourceClass.INDEPENDENT_CALCULATION, "INDEPENDENT-CALC-PACKAGE-REV-A", "A", "REVIEW-PS-042")
        .addSifPfd("ONE-OUT-OF-ONE-PFD", oneOutOfOneDesign(), 1.0e-3, 1.0e-12, 1.0e-9)
        .addLopaFrequency("TWO-IPL-LOPA", lopaResult(), 1.0e-4, 1.0e-12, 1.0e-9)
        .addDynamicResponse("ESD-VALVE-STROKE", runEsdScenario(), "esdv-closed", 3.0, 1.0e-12, 1.0e-9).evaluate();

    assertTrue(report.isPassed());
    assertTrue(report.getMissingQualifyingMethods().isEmpty());
    assertTrue((Boolean) report.toMap().get("engineeringApprovalRequired"));
  }

  @Test
  void regressionBaselineCannotQualifyAsIndependentEvidence() {
    EngineeringBenchmarkSuite.Report report = new SafetyVerificationBenchmarkSuite("SAFETY-BENCH-REG", "A",
        SourceClass.REGRESSION_BASELINE, "NEQSIM-PRIOR-OUTPUT", "A", "REVIEW-PS-042")
        .addSifPfd("ONE-OUT-OF-ONE-PFD", oneOutOfOneDesign(), 1.0e-3, 1.0e-12, 1.0e-9)
        .addLopaFrequency("TWO-IPL-LOPA", lopaResult(), 1.0e-4, 1.0e-12, 1.0e-9)
        .addDynamicResponse("ESD-VALVE-STROKE", runEsdScenario(), "esdv-closed", 3.0, 1.0e-12, 1.0e-9).evaluate();

    assertFalse(report.isPassed());
    assertFalse(report.getMissingQualifyingMethods().isEmpty());
  }

  private SafetyFunctionDesign oneOutOfOneDesign() {
    SafetyFunctionDesign design = new SafetyFunctionDesign("SIF-BENCH", "SRS-BENCH", 2);
    design.addSubsystem(new Subsystem("sensor", SubsystemType.SENSOR, 1, 1, 2.0e-6, 0.0, 1000.0, 8.0, 0.0));
    return design;
  }

  private LOPAResult lopaResult() {
    LopaScenarioDefinition scenario = LopaScenarioDefinition.builder("LOPA-BENCH", "NODE-101", "MORE-PRESSURE")
        .equipmentTag("V-101").initiatingEvent("Control valve fails open", 0.1).consequence("Separator overpressure")
        .targetFrequencyPerYear(1.0e-5).frequencyBasisReference("INDEPENDENT-CALC-PACKAGE-REV-A")
        .addProtectionLayer(layer("IPL-BPCS", "BPCS pressure control", LayerType.BPCS, 0.1))
        .addProtectionLayer(layer("IPL-PSV", "Pressure relief valve", LayerType.RELIEF, 0.01)).build();
    SrsDesignInputs srs = SrsDesignInputs.builder("SRS-BENCH", "SIF-BENCH", "LOPA-BENCH")
        .trip("pressure", TripDirection.HIGH, 70.0, "bara").safeState("Inlet isolated").maximumResponseTimeSeconds(5.0)
        .votingArchitecture("2oo3").proofTestIntervalHours(8760.0).resetPolicy("Manual reset after cause cleared")
        .bypassPolicy("Controlled permit and compensation").build();
    return HazopLopaSrsWorkflow.run(scenario, srs).getLopaResult();
  }

  private ProtectionLayerDefinition layer(String id, String name, LayerType type, double pfd) {
    return ProtectionLayerDefinition.builder(id, name, type, pfd).independentFromInitiatingEvent(true)
        .independentFromOtherLayers(true).specific(true).auditable(true).proofTestIntervalHours(8760.0)
        .evidenceReference("INDEPENDENT-CALC-PACKAGE-REV-A").build();
  }

  private DynamicSafetyScenarioResult runEsdScenario() {
    ProcessSystem base = process();
    DynamicSafetyScenario scenario = DynamicSafetyScenario.builder("SIF-BENCH", "HP inlet isolation")
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
            ESDLogic finalElements = new ESDLogic("ESD final elements");
            finalElements.addAction(new TripValveAction((ESDValve) process.getUnit("ESDV-101")), 0.0);
            return ClosedLoopSafetyFunction
                .builder("SIF-BENCH", "2oo3 high-pressure isolation", process, VotingPattern.TWO_OUT_OF_THREE,
                    finalElements)
                .addChannel(channel("PT-A")).addChannel(channel("PT-B")).addChannel(channel("PT-C"))
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
        .addEvidenceReference("SRS-BENCH").build();
    return DynamicSafetyScenarioRunner.run(base, scenario);
  }

  private SafetyFunctionChannel channel(String tag) {
    return SafetyFunctionChannel.highTrip(tag, "bara", 60.0, new SafetyFunctionChannel.SignalExtractor() {
      private static final long serialVersionUID = 1000L;

      @Override
      public double extract(ProcessSystem process) {
        return ((Stream) process.getUnit("FEED")).getPressure("bara");
      }
    }).responseDelaySeconds(1.0).build();
  }

  private ProcessSystem process() {
    SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("FEED", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    ESDValve valve = new ESDValve("ESDV-101", feed);
    valve.setCv(500.0);
    valve.setStrokeTime(2.0);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(valve);
    process.run();
    return process;
  }
}
