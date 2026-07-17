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
    private final double dynamicViscosityPaS;
    private final double absoluteRoughnessM;
    private final boolean detailedHydraulics;

    public Case(String id, double volumeFlowM3s, double referencePressureDropBar, double referenceDiameterM,
        double operatingPressureBara, double densityKgM3) {
      this.id = text(id, "id");
      this.volumeFlowM3s = nonNegative(volumeFlowM3s, "volumeFlowM3s");
      this.referencePressureDropBar = nonNegative(referencePressureDropBar, "referencePressureDropBar");
      this.referenceDiameterM = positive(referenceDiameterM, "referenceDiameterM");
      this.operatingPressureBara = positive(operatingPressureBara, "operatingPressureBara");
      this.densityKgM3 = positive(densityKgM3, "densityKgM3");
      dynamicViscosityPaS = Double.NaN;
      absoluteRoughnessM = Double.NaN;
      detailedHydraulics = false;
    }

    private Case(String id, double volumeFlowM3s, double operatingPressureBara, double densityKgM3,
        double dynamicViscosityPaS, double absoluteRoughnessM, boolean detailedHydraulics) {
      this.id = text(id, "id");
      this.volumeFlowM3s = nonNegative(volumeFlowM3s, "volumeFlowM3s");
      referencePressureDropBar = 0.0;
      referenceDiameterM = 1.0;
      this.operatingPressureBara = positive(operatingPressureBara, "operatingPressureBara");
      this.densityKgM3 = positive(densityKgM3, "densityKgM3");
      this.dynamicViscosityPaS = positive(dynamicViscosityPaS, "dynamicViscosityPaS");
      this.absoluteRoughnessM = nonNegative(absoluteRoughnessM, "absoluteRoughnessM");
      this.detailedHydraulics = detailedHydraulics;
    }

    /** Creates a Darcy-Weisbach case using governed density, viscosity and absolute roughness. */
    public static Case detailed(String id, double volumeFlowM3s, double operatingPressureBara, double densityKgM3,
        double dynamicViscosityPaS, double absoluteRoughnessM) {
      return new Case(id, volumeFlowM3s, operatingPressureBara, densityKgM3, dynamicViscosityPaS, absoluteRoughnessM,
          true);
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
    private String simultaneousDemandGroup = "";
    private final List<Case> cases = new ArrayList<Case>();

    public Segment(String tag, boolean gasService, boolean reliefInlet, double lengthM, double equivalentLengthM,
        double elevationChangeM, double multiphaseMultiplier, double reliefSetPressureBarg) {
      this.tag = text(tag, "tag");
      this.gasService = gasService;
      this.reliefInlet = reliefInlet;
      this.lengthM = positive(lengthM, "lengthM");
      this.equivalentLengthM = nonNegative(equivalentLengthM, "equivalentLengthM");
      if (!Double.isFinite(elevationChangeM)) {
        throw new IllegalArgumentException("elevationChangeM must be finite");
      }
      this.elevationChangeM = elevationChangeM;
      this.multiphaseMultiplier = positive(multiphaseMultiplier, "multiphaseMultiplier");
      this.reliefSetPressureBarg = nonNegative(reliefSetPressureBarg, "reliefSetPressureBarg");
    }

    public Segment addCase(Case value) {
      if (value == null) {
        throw new IllegalArgumentException("case must not be null");
      }
      cases.add(value);
      return this;
    }

    /** Adds a declared utility/header demand group whose simultaneous flow is applied to every case. */
    public Segment simultaneousDemandGroup(String value) {
      simultaneousDemandGroup = text(value, "simultaneousDemandGroup");
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
      Map<String, Double> demands = new LinkedHashMap<String, Double>();
      if (simultaneousDemands != null) {
        for (Map.Entry<String, Double> demand : simultaneousDemands.entrySet()) {
          String group = text(demand.getKey(), "simultaneousDemandGroup");
          Double demandValue = demand.getValue();
          if (demandValue == null) {
            throw new IllegalArgumentException("Simultaneous demand for " + group + " must not be null");
          }
          demands.put(group, Double.valueOf(nonNegative(demandValue.doubleValue(), "simultaneousDemand")));
        }
      }
      this.simultaneousDemands = Collections.unmodifiableMap(demands);
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
    return "2.0";
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
    if (productionQualification(context)) {
      for (Segment segment : input.segments) {
        for (Case hydraulicCase : segment.cases) {
          if (!hydraulicCase.detailedHydraulics) {
            result.addBlocker("PIPING_DETAILED_CASE_" + segment.tag + "_" + hydraulicCase.id,
                "Production qualification cannot use reference-diameter pressure-drop scaling",
                "Supply density, viscosity and roughness with Case.detailed");
          }
        }
        if (!segment.simultaneousDemandGroup.isEmpty()
            && !input.simultaneousDemands.containsKey(segment.simultaneousDemandGroup)) {
          result.addBlocker("PIPING_SIMULTANEOUS_DEMAND_" + segment.tag,
              "Declared simultaneous-demand group has no governed demand", "Supply the header demand case");
        }
      }
      if (context.getEvidenceReferences().isEmpty() || context.getStandardReferences().isEmpty()) {
        result.addBlocker("PIPING_PRODUCTION_EVIDENCE", "Piping qualification evidence is incomplete",
            "Attach standards, line-list/hydraulic evidence and independent benchmark references");
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
      selections.put(segment.tag, select(segment, ordered, input.rulePack, input.simultaneousDemands));
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

  private static Map<String, Object> select(Segment segment, List<Candidate> candidates, PipingRulePack rules,
      Map<String, Double> simultaneousDemands) {
    for (Candidate candidate : candidates) {
      Map<String, Object> checks = evaluate(segment, candidate, rules, simultaneousDemands);
      if (Boolean.TRUE.equals(checks.get("allConstraintsSatisfied"))) {
        checks.put("candidate", candidate.toMap());
        checks.put("governingStatus", "CALCULATED_REVIEW_REQUIRED");
        return checks;
      }
    }
    throw new IllegalStateException("No piping candidate satisfies all cases for " + segment.tag);
  }

  private static Map<String, Object> evaluate(Segment segment, Candidate candidate, PipingRulePack rules,
      Map<String, Double> simultaneousDemands) {
    double maximumVelocity = 0.0;
    double minimumVelocity = Double.POSITIVE_INFINITY;
    double maximumGradient = 0.0;
    double maximumDrop = 0.0;
    String governingCase = "";
    boolean pressureRating = true;
    double maximumReynoldsNumber = 0.0;
    double maximumFrictionFactor = 0.0;
    double simultaneousDemand = segment.simultaneousDemandGroup.isEmpty() ? 0.0
        : value(simultaneousDemands, segment.simultaneousDemandGroup);
    for (Case hydraulicCase : segment.cases) {
      double effectiveFlow = hydraulicCase.volumeFlowM3s + simultaneousDemand;
      double velocity = 4.0 * effectiveFlow / (Math.PI * candidate.insideDiameterM * candidate.insideDiameterM);
      double effectiveLength = segment.lengthM + segment.equivalentLengthM;
      double drop;
      if (hydraulicCase.detailedHydraulics) {
        double reynolds = hydraulicCase.densityKgM3 * velocity * candidate.insideDiameterM
            / hydraulicCase.dynamicViscosityPaS;
        double friction = frictionFactor(reynolds, hydraulicCase.absoluteRoughnessM, candidate.insideDiameterM);
        drop = friction * effectiveLength / candidate.insideDiameterM * hydraulicCase.densityKgM3 * velocity * velocity
            / 2.0 / 1.0e5 * segment.multiphaseMultiplier;
        maximumReynoldsNumber = Math.max(maximumReynoldsNumber, reynolds);
        maximumFrictionFactor = Math.max(maximumFrictionFactor, friction);
      } else {
        drop = hydraulicCase.referencePressureDropBar
            * Math.pow(hydraulicCase.referenceDiameterM / candidate.insideDiameterM, 5.0) * effectiveLength
            / segment.lengthM * segment.multiphaseMultiplier;
      }
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
    result.put("maximumReynoldsNumber", Double.valueOf(maximumReynoldsNumber));
    result.put("maximumDarcyFrictionFactor", Double.valueOf(maximumFrictionFactor));
    result.put("simultaneousDemandGroup", segment.simultaneousDemandGroup);
    result.put("simultaneousDemandM3s", Double.valueOf(simultaneousDemand));
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

  private static double frictionFactor(double reynolds, double roughness, double diameter) {
    if (reynolds <= 0.0) {
      return 0.0;
    }
    if (reynolds < 2300.0) {
      return 64.0 / reynolds;
    }
    double argument = roughness / Math.max(3.7 * diameter, 1.0e-12) + 5.74 / Math.pow(reynolds, 0.9);
    return 0.25 / Math.pow(Math.log10(Math.max(argument, 1.0e-12)), 2.0);
  }

  private static double value(Map<String, Double> values, String key) {
    Double value = values.get(key);
    if (value == null || !Double.isFinite(value.doubleValue()) || value.doubleValue() < 0.0) {
      throw new IllegalArgumentException("Missing finite non-negative simultaneous demand for group " + key);
    }
    return value.doubleValue();
  }

  private static double maximumDensity(Segment segment) {
    double maximum = 0.0;
    for (Case hydraulicCase : segment.cases) {
      maximum = Math.max(maximum, hydraulicCase.densityKgM3);
    }
    return Math.max(maximum, 1.0e-12);
  }

  private static boolean productionQualification(EngineeringCalculationContext context) {
    return context != null && "true".equalsIgnoreCase(context.getAttributes().get("productionQualification"));
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
