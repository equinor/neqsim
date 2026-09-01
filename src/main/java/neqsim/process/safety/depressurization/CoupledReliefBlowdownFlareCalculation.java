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
import neqsim.process.safety.overpressure.OverpressureStudyResult;
import neqsim.process.safety.overpressure.ReliefScenario;

/** Readiness-gated calculation coupling PSV scenarios, blowdown sources, flare hydraulics, and capacity. */
public final class CoupledReliefBlowdownFlareCalculation
    implements EngineeringCalculationModule<CoupledReliefBlowdownFlareInput, CoupledReliefBlowdownFlareResult> {
  private static final String METHOD = "NeqSim coupled relief-blowdown-flare envelope";
  private static final String METHOD_VERSION = "1.0";
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
    Map<String, Double> groupLoads = new LinkedHashMap<String, Double>();
    List<String> findings = new ArrayList<String>();
    boolean capacityAcceptable = true;
    for (ReliefStudyEntry entry : input.getReliefStudies()) {
      OverpressureStudyResult relief = entry.getStudy().evaluate();
      reliefResults.add(relief);
      ReliefScenario governing = relief.getGoverningScenario();
      if (governing == null) {
        capacityAcceptable = false;
        findings.add(entry.getStudy().getItem().getName() + " has no credible governing relief scenario.");
        continue;
      }
      Double current = groupLoads.get(entry.getConcurrencyGroup());
      groupLoads.put(entry.getConcurrencyGroup(),
          Double.valueOf((current == null ? 0.0 : current.doubleValue()) + governing.getReliefRateKgPerS()));
      if (!relief.isCapacityAdequate() || relief.getAcceptance() == null || !relief.getAcceptance().isAccepted()) {
        capacityAcceptable = false;
        findings.add(entry.getStudy().getItem().getName() + " relief sizing or accumulated-pressure check failed.");
      }
    }

    DynamicBlowdownFlareStudyHandoff dynamic = dynamicRunner.run(input.getDynamicStudy());
    double steadyPeak = maximum(groupLoads);
    double dynamicPeak = dynamicPeak(dynamic);
    boolean dynamicAcceptable = dynamicCapacityAcceptable(dynamic, findings);
    capacityAcceptable = capacityAcceptable && dynamicAcceptable;
    double governingFlow = Math.max(steadyPeak, dynamicPeak);
    String governingBasis = dynamicPeak > steadyPeak ? "DYNAMIC_BLOWDOWN_PEAK" : "STEADY_RELIEF_CONCURRENCY_GROUP";
    CoupledReliefBlowdownFlareResult value = new CoupledReliefBlowdownFlareResult(input.getStudyId(), reliefResults,
        groupLoads, dynamic, governingFlow, governingBasis, capacityAcceptable, findings);
    EngineeringCalculationResult.Status status = readiness.requiresReview() || !capacityAcceptable
        ? EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED
        : EngineeringCalculationResult.Status.CALCULATED;
    return result.status(status).value(value).input("reliefStudyCount", Integer.valueOf(reliefResults.size()))
        .input("concurrencyGroupCount", Integer.valueOf(groupLoads.size())).input("steadyPeakKgPerS", steadyPeak)
        .input("dynamicPeakKgPerS", dynamicPeak)
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

  private static boolean dynamicCapacityAcceptable(DynamicBlowdownFlareStudyHandoff handoff, List<String> findings) {
    Map<String, Object> result = handoff.getResult();
    if (result == null) {
      findings.add("Dynamic blowdown/flare calculation did not produce a result.");
      return false;
    }
    Map<String, Object> combined = map(result.get("combinedLoad"));
    Map<String, Object> flare = map(result.get("flareLoad"));
    Map<String, Object> capacity = map(flare.get("capacity"));
    boolean acceptable = !Boolean.FALSE.equals(combined.get("headerMachAcceptable"))
        && !Boolean.TRUE.equals(capacity.get("overloaded"));
    if (Boolean.FALSE.equals(combined.get("headerMachAcceptable"))) {
      findings.add("Transient flare-header Mach exceeds the configured acceptance limit.");
    }
    if (Boolean.TRUE.equals(capacity.get("overloaded"))) {
      findings.add("Transient flare load exceeds at least one configured flare capacity limit.");
    }
    return acceptable;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<String, Object>();
  }
}
