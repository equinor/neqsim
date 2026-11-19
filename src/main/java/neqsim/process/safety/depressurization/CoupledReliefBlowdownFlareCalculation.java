package neqsim.process.safety.depressurization;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.calculation.CalculationReadiness;
import neqsim.process.engineering.calculation.EngineeringCalculationContext;
import neqsim.process.engineering.calculation.EngineeringCalculationModule;
import neqsim.process.engineering.calculation.EngineeringCalculationResult;
import neqsim.process.safety.depressurization.CoupledReliefBlowdownFlareInput.ReliefStudyEntry;
import neqsim.process.safety.depressurization.CoupledReliefBlowdownFlareInput.DischargeWindow;
import neqsim.process.safety.depressurization.DynamicBlowdownFlareStudyDataSource.BlowdownSource;
import neqsim.process.safety.overpressure.OverpressureStudyResult;
import neqsim.process.safety.overpressure.ReliefScenario;

/** Readiness-gated calculation coupling PSV scenarios, blowdown sources, flare hydraulics, and capacity. */
public final class CoupledReliefBlowdownFlareCalculation
    implements EngineeringCalculationModule<CoupledReliefBlowdownFlareInput, CoupledReliefBlowdownFlareResult> {
  private static final String METHOD = "NeqSim coupled relief-blowdown-flare envelope";
  private static final String METHOD_VERSION = "1.1";
  private final DynamicBlowdownFlareStudyRunner dynamicRunner;

  public CoupledReliefBlowdownFlareCalculation() {
    this(DynamicBlowdownFlareStudyRunner.builder().build());
  }

  public CoupledReliefBlowdownFlareCalculation(DynamicBlowdownFlareStudyRunner dynamicRunner) {
    if (dynamicRunner == null) {
      throw new IllegalArgumentException("dynamicRunner must not be null");
    }
    this.dynamicRunner = dynamicRunner;
  }

  @Override
  public String getMethod() {
    return METHOD;
  }

  @Override
  public String getMethodVersion() {
    return METHOD_VERSION;
  }

  @Override
  public CalculationReadiness assess(CoupledReliefBlowdownFlareInput input, EngineeringCalculationContext context) {
    CalculationReadiness.Builder readiness = CalculationReadiness.builder();
    if (input == null) {
      return readiness.addBlocker("COUPLED-INPUT-001", "Coupled study input is missing",
          "Provide relief studies and a governed dynamic blowdown/flare data source.").build();
    }
    if (input.getReliefStudies().isEmpty()) {
      readiness.addBlocker("COUPLED-RELIEF-001", "No overpressure protection studies are configured",
          "Add credible, hazard-review-supported relief scenarios for each protected item.");
    }
    if (input.getDynamicStudy() == null) {
      readiness.addBlocker("COUPLED-DYNAMIC-001", "No dynamic blowdown/flare study is configured",
          "Provide vessel inventories, BDV data, header geometry, flare basis, and evidence.");
    } else if (!input.getDynamicStudy().readiness().isReadyForCalculation()) {
      readiness.addBlocker("COUPLED-DYNAMIC-002", "Dynamic study has calculation-readiness blockers",
          "Close the blockers reported by DynamicBlowdownFlareStudyDataSource.readiness().");
    }
    if (!input.isScenarioSelectionReviewed()) {
      readiness.addWarning("COUPLED-HAZOP-001", "Scenario credibility and concurrency are not marked reviewed",
          "Approve credible causes and simultaneous groups through HAZOP/relief-system engineering.");
    }
    if (input.getEvidenceReferences().isEmpty()) {
      readiness.addWarning("COUPLED-EVIDENCE-001", "No study-level evidence references are attached",
          "Attach hazard review, relief register, line list, flare basis, and calculation revision references.");
    }
    return readiness.build();
  }

  @Override
  public EngineeringCalculationResult<CoupledReliefBlowdownFlareResult> calculate(CoupledReliefBlowdownFlareInput input,
      EngineeringCalculationContext context) {
    EngineeringCalculationContext effectiveContext = context == null ? EngineeringCalculationContext.builder().build()
        : context;
    CalculationReadiness readiness = assess(input, effectiveContext);
    EngineeringCalculationResult.Builder<CoupledReliefBlowdownFlareResult> result = EngineeringCalculationResult
        .<CoupledReliefBlowdownFlareResult>builder(input == null ? "missing-coupled-study" : input.getStudyId(), METHOD,
            METHOD_VERSION)
        .context(effectiveContext).readiness(readiness);
    if (!readiness.isReady()) {
      return result.status(EngineeringCalculationResult.Status.BLOCKED)
          .message("Coupled relief, blowdown, and flare calculation is blocked by missing controlled inputs.").build();
    }

    List<OverpressureStudyResult> reliefResults = new ArrayList<OverpressureStudyResult>();
    Map<String, FlareDischargeTimeline> events = new LinkedHashMap<String, FlareDischargeTimeline>();
    List<String> findings = new ArrayList<String>();
    boolean capacityAcceptable = true;
    for (ReliefStudyEntry entry : input.getReliefStudies()) {
      OverpressureStudyResult relief = entry.getStudy().evaluate();
      reliefResults.add(relief);
      ReliefScenario governing = entry.getSelectedScenario() == null ? relief.getMaximumLoadScenario()
          : entry.getSelectedScenario();
      if (governing == null) {
        capacityAcceptable = false;
        findings.add(entry.getStudy().getItem().getName() + " has no credible governing relief scenario.");
        continue;
      }
      events.computeIfAbsent(entry.getConcurrencyGroup(), name -> new FlareDischargeTimeline()).addRelief(entry,
          governing);
      if (!relief.isCapacityAdequate() || relief.getAcceptance() == null || !relief.getAcceptance().isAccepted()) {
        capacityAcceptable = false;
        findings.add(entry.getStudy().getItem().getName() + " relief sizing or accumulated-pressure check failed.");
      }
    }

    DynamicBlowdownFlareStudyHandoff dynamic = dynamicRunner.run(input.getDynamicStudy());
    addBlowdownEvents(input, dynamic, events);
    Map<String, Double> groupLoads = new LinkedHashMap<String, Double>();
    List<FlareDisposalCaseResult> cases = new ArrayList<FlareDisposalCaseResult>();
    FlareDisposalCaseResult governing = null;
    for (Map.Entry<String, FlareDischargeTimeline> event : events.entrySet()) {
      FlareDisposalCaseResult disposal = event.getValue().evaluate(event.getKey(), input.getDynamicStudy());
      cases.add(disposal);
      if (disposal.getPeakSteadyMassFlowKgPerS() > 0.0) {
        groupLoads.put(event.getKey(), disposal.getPeakSteadyMassFlowKgPerS());
      }
      if (governing == null || disposal.getPeakMassFlowKgPerS() > governing.getPeakMassFlowKgPerS()) {
        governing = disposal;
      }
      if (disposal.getCapacityStatus() != FlareDisposalCaseResult.CapacityStatus.WITHIN_LIMIT) {
        capacityAcceptable = false;
        findings.add(event.getKey() + " disposal capacity: " + disposal.getCapacityChecks());
      }
    }
    if (governing == null) {
      return result.status(EngineeringCalculationResult.Status.FAILED).message("No discharge cases could be evaluated.")
          .build();
    }
    boolean reliefAtPeak = governing.getContributionsKgPerS().keySet().stream().anyMatch(tag -> tag.startsWith("PSV:"));
    boolean blowdownAtPeak = governing.getContributionsKgPerS().keySet().stream()
        .anyMatch(tag -> tag.startsWith("BDV:"));
    String governingBasis = reliefAtPeak && blowdownAtPeak ? "COINCIDENT_RELIEF_AND_BLOWDOWN"
        : reliefAtPeak ? "STEADY_RELIEF_CONCURRENCY_GROUP" : "DYNAMIC_BLOWDOWN_PEAK";
    CoupledReliefBlowdownFlareResult value = new CoupledReliefBlowdownFlareResult(input.getStudyId(), reliefResults,
        groupLoads, dynamic, governing, governingBasis, cases, capacityAcceptable, findings);
    EngineeringCalculationResult.Status status = readiness.requiresReview() || !capacityAcceptable
        ? EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED
        : EngineeringCalculationResult.Status.CALCULATED;
    return result.status(status).value(value).input("reliefStudyCount", Integer.valueOf(reliefResults.size()))
        .input("concurrencyGroupCount", Integer.valueOf(groupLoads.size()))
        .input("steadyPeakKgPerS", maximum(groupLoads)).input("dynamicPeakKgPerS", dynamicPeak(dynamic))
        .message("Calculated loads require process-safety, piping, flare, and mechanical engineering approval.")
        .build();
  }

  private static double maximum(Map<String, Double> values) {
    double maximum = 0.0;
    for (Double value : values.values()) {
      maximum = Math.max(maximum, value.doubleValue());
    }
    return maximum;
  }

  private static double dynamicPeak(DynamicBlowdownFlareStudyHandoff handoff) {
    Map<String, Object> result = handoff.getResult();
    if (result == null) {
      return 0.0;
    }
    Map<String, Object> combined = map(result.get("combinedLoad"));
    Object peak = combined.get("peakTotalMassFlowKgPerS");
    return peak instanceof Number ? ((Number) peak).doubleValue() : 0.0;
  }

  private static void addBlowdownEvents(CoupledReliefBlowdownFlareInput input, DynamicBlowdownFlareStudyHandoff handoff,
      Map<String, FlareDischargeTimeline> events) {
    Map<String, Object> histories = map(handoff.getResult().get("sourceResults"));
    // Legacy studies define a separate simultaneous-BDV contingency. Avoid collisions with user event names.
    String standalone = "STANDALONE_BLOWDOWN";
    List<String> named = new ArrayList<String>(events.keySet());
    for (DischargeWindow window : input.getBlowdownWindows().values()) {
      named.add(window.getEvent());
    }
    while (named.contains(standalone)) {
      standalone += "_";
    }
    for (BlowdownSource source : input.getDynamicStudy().getSources()) {
      DischargeWindow window = input.getBlowdownWindows().get(source.getSourceId());
      String event = window == null ? standalone : window.getEvent();
      events.computeIfAbsent(event, ignored -> new FlareDischargeTimeline()).addBlowdown(source, window,
          map(histories.get(source.getSourceId())));
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<String, Object>();
  }
}
