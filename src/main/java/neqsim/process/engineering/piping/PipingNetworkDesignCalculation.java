package neqsim.process.engineering.piping;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.calculation.CalculationReadiness;
import neqsim.process.engineering.calculation.EngineeringCalculationContext;
import neqsim.process.engineering.calculation.EngineeringCalculationModule;
import neqsim.process.engineering.calculation.EngineeringCalculationResult;

/** Selects the smallest permissible line candidates across a governed hydraulic network and all cases. */
public final class PipingNetworkDesignCalculation implements
    EngineeringCalculationModule<PipingNetworkDesignCalculation.Input, PipingNetworkDesignCalculation.Result> {

  /** One pipe schedule candidate. */
  public static final class Candidate implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String nominalSize;
    private final String schedule;
    private final double insideDiameterM;
    private final double maximumAllowablePressureBar;

    public Candidate(String nominalSize, String schedule, double insideDiameterM, double maximumAllowablePressureBar) {
      this.nominalSize = text(nominalSize, "nominalSize");
      this.schedule = text(schedule, "schedule");
      this.insideDiameterM = positive(insideDiameterM, "insideDiameterM");
      this.maximumAllowablePressureBar = positive(maximumAllowablePressureBar, "maximumAllowablePressureBar");
    }

    Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("nominalSize", nominalSize);
      result.put("schedule", schedule);
      result.put("insideDiameterM", Double.valueOf(insideDiameterM));
      result.put("maximumAllowablePressureBar", Double.valueOf(maximumAllowablePressureBar));
      return result;
    }
  }

  /** One converged hydraulic case for one segment. */
  public static final class Case implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String id;
    private final double volumeFlowM3s;
    private final double referencePressureDropBar;
    private final double referenceDiameterM;
    private final double operatingPressureBara;
    private final double densityKgM3;

    public Case(String id, double volumeFlowM3s, double referencePressureDropBar, double referenceDiameterM,
        double operatingPressureBara, double densityKgM3) {
      this.id = text(id, "id");
      this.volumeFlowM3s = nonNegative(volumeFlowM3s, "volumeFlowM3s");
      this.referencePressureDropBar = nonNegative(referencePressureDropBar, "referencePressureDropBar");
      this.referenceDiameterM = positive(referenceDiameterM, "referenceDiameterM");
      this.operatingPressureBara = positive(operatingPressureBara, "operatingPressureBara");
      this.densityKgM3 = positive(densityKgM3, "densityKgM3");
    }
  }

  /** One physical network segment including fittings, elevation and service checks. */
  public static final class Segment implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String tag;
    private final boolean gasService;
    private final boolean reliefInlet;
    private final double lengthM;
    private final double equivalentLengthM;
    private final double elevationChangeM;
    private final double multiphaseMultiplier;
    private final double reliefSetPressureBarg;
    private final List<Case> cases = new ArrayList<Case>();

    public Segment(String tag, boolean gasService, boolean reliefInlet, double lengthM, double equivalentLengthM,
        double elevationChangeM, double multiphaseMultiplier, double reliefSetPressureBarg) {
      this.tag = text(tag, "tag");
      this.gasService = gasService;
      this.reliefInlet = reliefInlet;
      this.lengthM = positive(lengthM, "lengthM");
      this.equivalentLengthM = nonNegative(equivalentLengthM, "equivalentLengthM");
      this.elevationChangeM = elevationChangeM;
      this.multiphaseMultiplier = positive(multiphaseMultiplier, "multiphaseMultiplier");
      this.reliefSetPressureBarg = reliefSetPressureBarg;
    }

    public Segment addCase(Case value) {
      if (value == null) {
        throw new IllegalArgumentException("case must not be null");
      }
      cases.add(value);
      return this;
    }
  }

  /** Entire connected network input, including simultaneous utility/header demands. */
  public static final class Input implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String networkId;
    private final PipingRulePack rulePack;
    private final List<Candidate> candidates;
    private final List<Segment> segments;
    private final Map<String, Double> simultaneousDemands;

    public Input(String networkId, PipingRulePack rulePack, List<Candidate> candidates, List<Segment> segments,
        Map<String, Double> simultaneousDemands) {
      this.networkId = text(networkId, "networkId");
      this.rulePack = rulePack;
      this.candidates = candidates == null ? Collections.<Candidate>emptyList()
          : Collections.unmodifiableList(new ArrayList<Candidate>(candidates));
      this.segments = segments == null ? Collections.<Segment>emptyList()
          : Collections.unmodifiableList(new ArrayList<Segment>(segments));
      this.simultaneousDemands = simultaneousDemands == null ? Collections.<String, Double>emptyMap()
          : Collections.unmodifiableMap(new LinkedHashMap<String, Double>(simultaneousDemands));
    }
  }

  /** Selected line schedules and governing checks. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String networkId;
    private final Map<String, Map<String, Object>> selections;
    private final double simultaneousDemand;
    private final String rulePackId;

    Result(String networkId, Map<String, Map<String, Object>> selections, double simultaneousDemand,
        String rulePackId) {
      this.networkId = networkId;
      this.selections = Collections.unmodifiableMap(new LinkedHashMap<String, Map<String, Object>>(selections));
      this.simultaneousDemand = simultaneousDemand;
      this.rulePackId = rulePackId;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("networkId", networkId);
      result.put("rulePackId", rulePackId);
      result.put("selections", selections);
      result.put("simultaneousDemand", Double.valueOf(simultaneousDemand));
      result.put("approvalStatus", "REVIEW_REQUIRED");
      result.put("transientSlugReviewRequired", Boolean.TRUE);
      result.put("vibrationAndNoiseReviewRequired", Boolean.TRUE);
      return result;
    }
  }

  @Override
  public String getMethod() {
    return "network-level-piping-candidate-selection";
  }

  @Override
  public String getMethodVersion() {
    return "1.0";
  }

  @Override
  public CalculationReadiness assess(Input input, EngineeringCalculationContext context) {
    CalculationReadiness.Builder result = CalculationReadiness.builder();
    if (input == null) {
      return result.addBlocker("PIPING_NETWORK_INPUT", "Piping network input is required", "Define the network")
          .build();
    }
    if (input.rulePack == null) {
      result.addBlocker("PIPING_RULE_PACK", "A versioned piping rule pack is required", "Select project rules");
    }
    if (input.candidates.isEmpty()) {
      result.addBlocker("PIPING_CANDIDATES", "Pipe schedule candidates are required", "Supply piping class sizes");
    }
    if (input.segments.isEmpty()) {
      result.addBlocker("PIPING_SEGMENTS", "At least one network segment is required", "Define connected segments");
    }
    for (Segment segment : input.segments) {
      if (segment.cases.isEmpty()) {
        result.addBlocker("PIPING_CASES_" + segment.tag, "Segment has no converged hydraulic cases",
            "Attach operating and design cases");
      }
    }
    return result.build();
  }

  @Override
  public EngineeringCalculationResult<Result> calculate(Input input, EngineeringCalculationContext context) {
    CalculationReadiness readiness = assess(input, context);
    EngineeringCalculationResult.Builder<Result> result = EngineeringCalculationResult
        .<Result>builder("piping-network:" + (input == null ? "unassigned" : input.networkId), getMethod(),
            getMethodVersion())
        .context(context).readiness(readiness);
    if (!readiness.isReady()) {
      return result.status(EngineeringCalculationResult.Status.BLOCKED).build();
    }
    List<Candidate> ordered = new ArrayList<Candidate>(input.candidates);
    Collections.sort(ordered, new Comparator<Candidate>() {
      @Override
      public int compare(Candidate first, Candidate second) {
        return Double.compare(first.insideDiameterM, second.insideDiameterM);
      }
    });
    Map<String, Map<String, Object>> selections = new LinkedHashMap<String, Map<String, Object>>();
    for (Segment segment : input.segments) {
      selections.put(segment.tag, select(segment, ordered, input.rulePack));
    }
    double demand = 0.0;
    for (Double value : input.simultaneousDemands.values()) {
      demand += value.doubleValue();
    }
    Result value = new Result(input.networkId, selections, demand, String.valueOf(input.rulePack.toMap().get("id")));
    return result.status(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED).value(value)
        .input("rulePack", input.rulePack.toMap()).input("segmentCount", Integer.valueOf(input.segments.size()))
        .warning("Two-phase transients, acoustic/vibration screening and stress analysis require final verification")
        .build();
  }

  private static Map<String, Object> select(Segment segment, List<Candidate> candidates, PipingRulePack rules) {
    for (Candidate candidate : candidates) {
      Map<String, Object> checks = evaluate(segment, candidate, rules);
      if (Boolean.TRUE.equals(checks.get("allConstraintsSatisfied"))) {
        checks.put("candidate", candidate.toMap());
        checks.put("governingStatus", "CALCULATED_REVIEW_REQUIRED");
        return checks;
      }
    }
    throw new IllegalStateException("No piping candidate satisfies all cases for " + segment.tag);
  }

  private static Map<String, Object> evaluate(Segment segment, Candidate candidate, PipingRulePack rules) {
    double maximumVelocity = 0.0;
    double minimumVelocity = Double.POSITIVE_INFINITY;
    double maximumGradient = 0.0;
    double maximumDrop = 0.0;
    String governingCase = "";
    boolean pressureRating = true;
    for (Case hydraulicCase : segment.cases) {
      double velocity = 4.0 * hydraulicCase.volumeFlowM3s
          / (Math.PI * candidate.insideDiameterM * candidate.insideDiameterM);
      double effectiveLength = segment.lengthM + segment.equivalentLengthM;
      double drop = hydraulicCase.referencePressureDropBar
          * Math.pow(hydraulicCase.referenceDiameterM / candidate.insideDiameterM, 5.0) * effectiveLength
          / segment.lengthM * segment.multiphaseMultiplier;
      drop = Math.max(drop + hydraulicCase.densityKgM3 * 9.80665 * segment.elevationChangeM / 1.0e5, 0.0);
      double gradient = drop / Math.max(effectiveLength / 1000.0, 1.0e-12);
      if (velocity > maximumVelocity || drop > maximumDrop) {
        governingCase = hydraulicCase.id;
      }
      maximumVelocity = Math.max(maximumVelocity, velocity);
      minimumVelocity = Math.min(minimumVelocity, velocity);
      maximumGradient = Math.max(maximumGradient, gradient);
      maximumDrop = Math.max(maximumDrop, drop);
      pressureRating &= candidate.maximumAllowablePressureBar >= hydraulicCase.operatingPressureBara;
    }
    boolean velocity = maximumVelocity <= rules.maximumVelocity(segment.gasService);
    double erosionVelocityLimit = 100.0 / Math.sqrt(maximumDensity(segment));
    boolean erosionVelocity = maximumVelocity <= erosionVelocityLimit;
    boolean minimumVelocitySatisfied = segment.gasService || minimumVelocity >= rules.getMinimumLiquidVelocityMPerS();
    boolean gradient = maximumGradient <= rules.getMaximumPressureGradientBarPerKm();
    boolean reliefLoss = !segment.reliefInlet
        || maximumDrop <= rules.getMaximumReliefInletLossFraction() * Math.max(segment.reliefSetPressureBarg, 1.0e-12);
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("governingCaseId", governingCase);
    result.put("maximumVelocityMPerS", Double.valueOf(maximumVelocity));
    result.put("minimumVelocityMPerS", Double.valueOf(minimumVelocity));
    result.put("maximumPressureGradientBarPerKm", Double.valueOf(maximumGradient));
    result.put("maximumPressureDropBar", Double.valueOf(maximumDrop));
    result.put("elevationChangeM", Double.valueOf(segment.elevationChangeM));
    result.put("velocitySatisfied", Boolean.valueOf(velocity));
    result.put("minimumVelocitySatisfied", Boolean.valueOf(minimumVelocitySatisfied));
    result.put("pressureGradientSatisfied", Boolean.valueOf(gradient));
    result.put("pressureRatingSatisfied", Boolean.valueOf(pressureRating));
    result.put("erosionVelocityLimitMPerS", Double.valueOf(erosionVelocityLimit));
    result.put("erosionVelocitySatisfied", Boolean.valueOf(erosionVelocity));
    result.put("noiseScreenRequired", Boolean.valueOf(segment.gasService && maximumVelocity > 15.0));
    result.put("vibrationScreenRequired", Boolean.valueOf(maximumGradient > 0.30));
    result.put("transientInventoryM3",
        Double.valueOf(Math.PI * candidate.insideDiameterM * candidate.insideDiameterM / 4.0 * segment.lengthM));
    result.put("liquidSlugAssessmentRequired", Boolean.TRUE);
    result.put("reliefInletLossSatisfied", Boolean.valueOf(reliefLoss));
    result.put("allConstraintsSatisfied", Boolean
        .valueOf(velocity && erosionVelocity && minimumVelocitySatisfied && gradient && pressureRating && reliefLoss));
    return result;
  }

  private static double maximumDensity(Segment segment) {
    double maximum = 0.0;
    for (Case hydraulicCase : segment.cases) {
      maximum = Math.max(maximum, hydraulicCase.densityKgM3);
    }
    return Math.max(maximum, 1.0e-12);
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
}
