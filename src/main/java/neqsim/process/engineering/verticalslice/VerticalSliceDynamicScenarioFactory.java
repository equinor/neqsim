package neqsim.process.engineering.verticalslice;

import neqsim.process.equipment.valve.BlowdownValve;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.logic.ProcessLogic;
import neqsim.process.logic.action.ActivateBlowdownAction;
import neqsim.process.logic.action.ParallelActionGroup;
import neqsim.process.logic.action.TripValveAction;
import neqsim.process.logic.esd.ESDLogic;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.scenario.DynamicSafetyScenario;
import neqsim.process.safety.scenario.DynamicScenarioCriterion;

/** Creates executable safe-state scenarios bound to equipment in an isolated vertical-slice process copy. */
public final class VerticalSliceDynamicScenarioFactory {
  private VerticalSliceDynamicScenarioFactory() {
  }

  /**
   * Creates an ESD scenario that isolates compressor suction/discharge and opens the blowdown valve against deadlines.
   */
  public static DynamicSafetyScenario emergencyShutdown(final String scenarioId,
      final InletCompressionExportSlicePolicy policy, double durationSeconds, double timeStepSeconds,
      double triggerTimeSeconds, final double isolationDeadlineSeconds, final double blowdownDeadlineSeconds,
      String evidenceReference) {
    return DynamicSafetyScenario.builder(scenarioId, "Compressor trip, isolation and blowdown safe-state verification")
        .durationSeconds(durationSeconds).timeStepSeconds(timeStepSeconds).triggerTimeSeconds(triggerTimeSeconds)
        .initiatingEvent(new DynamicSafetyScenario.ProcessManipulator() {
          private static final long serialVersionUID = 1000L;

          @Override
          public void apply(ProcessSystem process) {
            require(process, policy.getSuctionEsdValveTag(), ESDValve.class);
            require(process, policy.getDischargeEsdValveTag(), ESDValve.class);
            require(process, policy.getBlowdownValveTag(), BlowdownValve.class);
          }
        }).addLogic(new DynamicSafetyScenario.LogicFactory() {
          private static final long serialVersionUID = 1000L;

          @Override
          public ProcessLogic create(ProcessSystem process) {
            ESDValve suction = require(process, policy.getSuctionEsdValveTag(), ESDValve.class);
            ESDValve discharge = require(process, policy.getDischargeEsdValveTag(), ESDValve.class);
            BlowdownValve blowdown = require(process, policy.getBlowdownValveTag(), BlowdownValve.class);
            ESDLogic logic = new ESDLogic("ESD-" + scenarioId);
            ParallelActionGroup safeState = new ParallelActionGroup("Isolate and depressurize compressor segment");
            safeState.addAction(new TripValveAction(suction));
            safeState.addAction(new TripValveAction(discharge));
            safeState.addAction(new ActivateBlowdownAction(blowdown));
            logic.addAction(safeState, 0.0);
            return logic;
          }
        }).addCriterion(DynamicScenarioCriterion
            .builder("suction-isolated", "Suction ESD valve reaches safe closed position", "%",
                new DynamicScenarioCriterion.Extractor() {
                  private static final long serialVersionUID = 1000L;

                  @Override
                  public double extract(ProcessSystem process) {
                    return require(process, policy.getSuctionEsdValveTag(), ESDValve.class).getPercentValveOpening();
                  }
                })
            .acceptanceRange(null, Double.valueOf(1.0)).deadlineSeconds(isolationDeadlineSeconds).build())
        .addCriterion(DynamicScenarioCriterion
            .builder("discharge-isolated", "Discharge ESD valve reaches safe closed position", "%",
                new DynamicScenarioCriterion.Extractor() {
                  private static final long serialVersionUID = 1000L;

                  @Override
                  public double extract(ProcessSystem process) {
                    return require(process, policy.getDischargeEsdValveTag(), ESDValve.class).getPercentValveOpening();
                  }
                })
            .acceptanceRange(null, Double.valueOf(1.0)).deadlineSeconds(isolationDeadlineSeconds).build())
        .addCriterion(DynamicScenarioCriterion
            .builder("blowdown-open", "Blowdown valve reaches safe open position", "%",
                new DynamicScenarioCriterion.Extractor() {
                  private static final long serialVersionUID = 1000L;

                  @Override
                  public double extract(ProcessSystem process) {
                    return require(process, policy.getBlowdownValveTag(), BlowdownValve.class)
                        .getPercentValveOpening();
                  }
                })
            .acceptanceRange(Double.valueOf(90.0), null).deadlineSeconds(blowdownDeadlineSeconds).build())
        .addEvidenceReference(evidenceReference).build();
  }

  private static <T> T require(ProcessSystem process, String tag, Class<T> type) {
    Object value = process.getUnit(tag);
    if (!type.isInstance(value)) {
      throw new IllegalStateException(tag + " is missing or is not " + type.getSimpleName());
    }
    return type.cast(value);
  }
}
