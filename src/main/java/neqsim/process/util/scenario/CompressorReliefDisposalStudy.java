package neqsim.process.util.scenario;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.calculation.EngineeringCalculationContext;
import neqsim.process.engineering.calculation.EngineeringCalculationResult;
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
import neqsim.thermo.system.SystemInterface;

/**
 * Connects an executed compressor reference case to the governed relief, blowdown and flare calculation.
 *
 * <p>
 * The connection is deliberately opt-in. It copies the actual post-scenario discharge-scrubber fluid and derives the
 * protected volume from the compressor study's declared cylindrical geometry. All valve, header, flare and acceptance
 * inputs remain explicit in {@link Design}; no plant or vendor value is inferred. The original compressor result and
 * source train are never mutated.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class CompressorReliefDisposalStudy {
  private CompressorReliefDisposalStudy() {
  }

  /**
   * Runs the connected calculation at the declared physical timestep.
   *
   * @param compressorResult completed compressor scenario retaining the actual configured copy
   * @param design explicit synthetic connection and acceptance basis
   * @return immutable connected-study evidence
   */
  public static Result run(CompressorAutomationStudy.Result compressorResult, Design design) {
    if (compressorResult == null || compressorResult.getConfiguredTrain() == null) {
      throw new IllegalArgumentException("A completed compressor scenario is required");
    }
    if (design == null) {
      throw new IllegalArgumentException("A relief and disposal design is required");
    }
    design.validate();

    CompressorAutomationStudy.Train train = compressorResult.getConfiguredTrain();
    SystemInterface fluid = train.getDischargeScrubber().getThermoSystem().clone();
    SystemInterface reliefFluid = fluid.hasPhaseType("gas") ? fluid.phaseToSystem("gas") : fluid.clone();
    double initialPressureBara = fluid.getPressure();
    double initialTemperatureK = fluid.getTemperature();
    double protectedVolumeM3 = cylinderVolume(train.getDesign().getDischargeDiameterM(),
        train.getDesign().getDischargeLengthM());

    ReliefScenario blockedOutlet = new BlockedOutletRelief().setName("Synthetic blocked compressor export")
        .setInflowRateKgPerS(train.getDesign().getFeedFlowKgPerSecond())
        .setReliefPressureBara(design.reliefSetPressureBara).setReliefTemperatureC(initialTemperatureK - 273.15)
        .setFluid(reliefFluid).calculate();
    ProtectedItem item = new ProtectedItem(design.protectedEquipmentTag, design.maximumAllowablePressureBara)
        .setReliefSetPressureBara(design.reliefSetPressureBara).setBackPressureBara(design.backPressureBara)
        .setDesignTemperatureC(initialTemperatureK - 273.15);
    OverpressureProtectionStudy reliefStudy = new OverpressureProtectionStudy(item).addScenario(blockedOutlet);

    BlowdownSource source = BlowdownSource.builder(design.sourceId, fluid).equipmentTag(design.protectedEquipmentTag)
        .vesselVolumeM3(protectedVolumeM3).orificeDiameterM(design.blowdownOrificeDiameterM)
        .dischargeCoefficient(design.blowdownDischargeCoefficient).backPressureBara(design.backPressureBara)
        .stopPressureBara(design.stopPressureBara).fireHeatInputW(design.fireHeatInputW)
        .wallModel(design.wallMassKg, design.wallAreaM2, design.wallSpecificHeatJPerKgK,
            design.wallHeatTransferCoefficientWPerM2K)
        .psvBasis(design.reliefSetPressureBara, design.psvOverpressureFraction, design.balancedBellowsPsv,
            design.ruptureDiskInstalled)
        .build();
    DynamicBlowdownFlareStudyDataSource.Builder dynamicBuilder = DynamicBlowdownFlareStudyDataSource
        .builder(design.studyId + "-dynamic").addSource(source)
        .flareHeader(design.headerDiameterM, design.headerPressureBara, design.headerTemperatureK,
            design.headerMolarMassKgPerMol, design.headerGamma)
        .maxAllowableHeaderMach(design.maximumHeaderMach)
        .flareGeometry(design.flareTipDiameterM, design.flareFlameHeightM, design.flareRadiantFraction)
        .flareDesignCapacity(design.flareDesignHeatDutyW, design.flareDesignMassFlowKgPerSecond,
            design.flareDesignMolarFlowMolPerSecond)
        .sourceDiagramsReviewed(design.sourceDiagramsReviewed).pidTopologyVerified(design.pidTopologyVerified)
        .lineEquipmentListsReviewed(design.lineEquipmentListsReviewed)
        .pipingSpecificationRowsReviewed(design.pipingSpecificationRowsReviewed)
        .vesselInventoryReviewed(design.vesselInventoryReviewed)
        .valveSizingBasisReviewed(design.valveSizingBasisReviewed).psvBasisReviewed(design.psvBasisReviewed)
        .flareSystemBasisReviewed(design.flareSystemBasisReviewed).fireCaseReviewed(design.fireCaseReviewed)
        .standardsReviewed(design.standardsReviewed).humanReviewRequired(true)
        .addAssumption("Synthetic public reference train; all connection values are declared in Design.")
        .addGap("Vendor BDV, PSV, flare-network, MDMT and site hazard-review data are not supplied.");
    for (String assumption : design.assumptions) {
      dynamicBuilder.addAssumption(assumption);
    }
    for (String gap : design.gaps) {
      dynamicBuilder.addGap(gap);
    }
    DynamicBlowdownFlareStudyDataSource dynamic = dynamicBuilder.build();
    CoupledReliefBlowdownFlareInput.Builder inputBuilder = CoupledReliefBlowdownFlareInput.builder(design.studyId)
        .addReliefStudy(reliefStudy, design.concurrencyGroup).dynamicStudy(dynamic)
        .scenarioSelectionReviewed(design.scenarioSelectionReviewed);
    for (String reference : design.evidenceReferences) {
      inputBuilder.addEvidenceReference(reference);
    }
    CoupledReliefBlowdownFlareInput input = inputBuilder.build();
    CoupledReliefBlowdownFlareCalculation calculation = new CoupledReliefBlowdownFlareCalculation(
        DynamicBlowdownFlareStudyRunner.builder().timeStepSeconds(design.timeStepSeconds)
            .maxTimeSeconds(design.maximumTimeSeconds).build());
    EngineeringCalculationResult<CoupledReliefBlowdownFlareResult> calculationResult = calculation.calculate(input,
        EngineeringCalculationContext.builder().designCaseId(design.designCaseId).build());
    return new Result(design, compressorResult, protectedVolumeM3, initialPressureBara, initialTemperatureK,
        calculationResult);
  }

  private static double cylinderVolume(double diameterM, double lengthM) {
    return Math.PI * diameterM * diameterM * lengthM / 4.0;
  }

  /** Explicit synthetic connection, capacity and review inputs. */
  public static final class Design implements Serializable {
    private static final long serialVersionUID = 1L;
    private String studyId = "COMPRESSOR-RELIEF-DISPOSAL-SYNTHETIC";
    private String designCaseId = "BLOCKED-OUTLET-BLOWDOWN";
    private String sourceId = "30-V-102-BDV";
    private String protectedEquipmentTag = "30-V-102";
    private String concurrencyGroup = "SYNTHETIC-COMPRESSOR-AREA";
    private double maximumAllowablePressureBara = 75.0;
    private double reliefSetPressureBara = 70.0;
    private double psvOverpressureFraction = 0.10;
    private double blowdownOrificeDiameterM = 0.025;
    private double blowdownDischargeCoefficient = 0.72;
    private double backPressureBara = 1.5;
    private double stopPressureBara = 1.5;
    private double fireHeatInputW = 0.0;
    private double wallMassKg = 3200.0;
    private double wallAreaM2 = 22.0;
    private double wallSpecificHeatJPerKgK = 470.0;
    private double wallHeatTransferCoefficientWPerM2K = 50.0;
    private double headerDiameterM = 0.30;
    private double headerPressureBara = 1.5;
    private double headerTemperatureK = 288.15;
    private double headerMolarMassKgPerMol = 0.020;
    private double headerGamma = 1.30;
    private double maximumHeaderMach = 0.50;
    private double flareTipDiameterM = 0.50;
    private double flareFlameHeightM = 35.0;
    private double flareRadiantFraction = 0.20;
    private double flareDesignHeatDutyW = 5.0e8;
    private double flareDesignMassFlowKgPerSecond = 500.0;
    private double flareDesignMolarFlowMolPerSecond = 30000.0;
    private double timeStepSeconds = 1.0;
    private double maximumTimeSeconds = 900.0;
    private boolean balancedBellowsPsv;
    private boolean ruptureDiskInstalled;
    private boolean scenarioSelectionReviewed;
    private boolean sourceDiagramsReviewed;
    private boolean pidTopologyVerified;
    private boolean lineEquipmentListsReviewed;
    private boolean pipingSpecificationRowsReviewed;
    private boolean vesselInventoryReviewed;
    private boolean valveSizingBasisReviewed;
    private boolean psvBasisReviewed;
    private boolean flareSystemBasisReviewed;
    private boolean fireCaseReviewed;
    private boolean standardsReviewed;
    private final List<String> assumptions = new ArrayList<String>();
    private final List<String> gaps = new ArrayList<String>();
    private final List<String> evidenceReferences = new ArrayList<String>();

    /** Creates the declared public synthetic reference basis. */
    public Design() {
    }

    /** @param value stable study id @return this design */
    public Design setStudyId(String value) {
      studyId = value;
      return this;
    }

    /** @param value stable design-case id @return this design */
    public Design setDesignCaseId(String value) {
      designCaseId = value;
      return this;
    }

    /**
     * Sets protected-equipment and concurrency identities.
     *
     * @param source stable blowdown-source id
     * @param equipment protected equipment tag
     * @param group reviewed simultaneous-load group id
     * @return this design
     */
    public Design setIdentities(String source, String equipment, String group) {
      sourceId = source;
      protectedEquipmentTag = equipment;
      concurrencyGroup = group;
      return this;
    }

    /**
     * Sets pressure protection and disposal boundary inputs, all absolute bara.
     *
     * @param mawpBara maximum allowable working pressure
     * @param setBara PSV set pressure
     * @param disposalBackPressureBara disposal-system backpressure
     * @param terminalPressureBara blowdown integration stop pressure
     * @return this design
     */
    public Design setPressureBasis(double mawpBara, double setBara, double disposalBackPressureBara,
        double terminalPressureBara) {
      maximumAllowablePressureBara = mawpBara;
      reliefSetPressureBara = setBara;
      backPressureBara = disposalBackPressureBara;
      stopPressureBara = terminalPressureBara;
      return this;
    }

    /**
     * Sets the PSV correction basis.
     *
     * @param overpressureFraction declared fractional overpressure
     * @param balancedBellows true for balanced-bellows basis
     * @param ruptureDisk true when an upstream rupture disk is present
     * @return this design
     */
    public Design setPsvBasis(double overpressureFraction, boolean balancedBellows, boolean ruptureDisk) {
      psvOverpressureFraction = overpressureFraction;
      balancedBellowsPsv = balancedBellows;
      ruptureDiskInstalled = ruptureDisk;
      return this;
    }

    /** @param diameterM BDV equivalent diameter in m @param cd discharge coefficient @return this design */
    public Design setBlowdownValve(double diameterM, double cd) {
      blowdownOrificeDiameterM = diameterM;
      blowdownDischargeCoefficient = cd;
      return this;
    }

    /**
     * Sets fire and lumped-wall thermal inputs.
     *
     * @param fireDutyW constant heat input in W
     * @param massKg wall mass in kg
     * @param areaM2 internal heat-transfer area in m2
     * @param heatCapacityJPerKgK wall heat capacity in J/(kg K)
     * @param coefficientWPerM2K internal heat-transfer coefficient in W/(m2 K)
     * @return this design
     */
    public Design setThermalBasis(double fireDutyW, double massKg, double areaM2, double heatCapacityJPerKgK,
        double coefficientWPerM2K) {
      fireHeatInputW = fireDutyW;
      wallMassKg = massKg;
      wallAreaM2 = areaM2;
      wallSpecificHeatJPerKgK = heatCapacityJPerKgK;
      wallHeatTransferCoefficientWPerM2K = coefficientWPerM2K;
      return this;
    }

    /**
     * Sets the common flare-header screening basis.
     *
     * @param diameterM internal diameter in m
     * @param pressureBara absolute pressure in bara
     * @param temperatureK temperature in K
     * @param molarMassKgPerMol representative molar mass in kg/mol
     * @param gamma representative isentropic exponent
     * @param maximumMach declared screening limit
     * @return this design
     */
    public Design setFlareHeader(double diameterM, double pressureBara, double temperatureK, double molarMassKgPerMol,
        double gamma, double maximumMach) {
      headerDiameterM = diameterM;
      headerPressureBara = pressureBara;
      headerTemperatureK = temperatureK;
      headerMolarMassKgPerMol = molarMassKgPerMol;
      headerGamma = gamma;
      maximumHeaderMach = maximumMach;
      return this;
    }

    /**
     * Sets flare geometry and declared capacities.
     *
     * @param tipDiameterM tip diameter in m
     * @param flameHeightM flame height in m
     * @param radiantFraction radiant fraction
     * @param heatDutyW design heat capacity in W
     * @param massFlowKgPerSecond design mass capacity in kg/s
     * @param molarFlowMolPerSecond design molar capacity in mol/s
     * @return this design
     */
    public Design setFlareSystem(double tipDiameterM, double flameHeightM, double radiantFraction, double heatDutyW,
        double massFlowKgPerSecond, double molarFlowMolPerSecond) {
      flareTipDiameterM = tipDiameterM;
      flareFlameHeightM = flameHeightM;
      flareRadiantFraction = radiantFraction;
      flareDesignHeatDutyW = heatDutyW;
      flareDesignMassFlowKgPerSecond = massFlowKgPerSecond;
      flareDesignMolarFlowMolPerSecond = molarFlowMolPerSecond;
      return this;
    }

    /**
     * Sets every independent review flag without inferring review from model execution.
     *
     * @param scenarioSelection scenario credibility/concurrency review
     * @param sourceDiagrams detailed drawing review
     * @param topology topology verification
     * @param lineLists line/equipment-list review
     * @param pipingSpecifications piping/valve specification review
     * @param inventory protected-inventory review
     * @param valveSizing BDV sizing review
     * @param psvBasis PSV basis review
     * @param flareBasis flare-system basis review
     * @param fireCase fire-case review
     * @param standards controlled standards review
     * @return this design
     */
    public Design setReviewState(boolean scenarioSelection, boolean sourceDiagrams, boolean topology, boolean lineLists,
        boolean pipingSpecifications, boolean inventory, boolean valveSizing, boolean psvBasis, boolean flareBasis,
        boolean fireCase, boolean standards) {
      scenarioSelectionReviewed = scenarioSelection;
      sourceDiagramsReviewed = sourceDiagrams;
      pidTopologyVerified = topology;
      lineEquipmentListsReviewed = lineLists;
      pipingSpecificationRowsReviewed = pipingSpecifications;
      vesselInventoryReviewed = inventory;
      valveSizingBasisReviewed = valveSizing;
      psvBasisReviewed = psvBasis;
      flareSystemBasisReviewed = flareBasis;
      fireCaseReviewed = fireCase;
      standardsReviewed = standards;
      return this;
    }

    /** @param value physical integration timestep in s @return this design */
    public Design setTimeStepSeconds(double value) {
      timeStepSeconds = value;
      return this;
    }

    /** @param value maximum simulation time in s @return this design */
    public Design setMaximumTimeSeconds(double value) {
      maximumTimeSeconds = value;
      return this;
    }

    /** @param value BDV equivalent diameter in m @return this design */
    public Design setBlowdownOrificeDiameterM(double value) {
      blowdownOrificeDiameterM = value;
      return this;
    }

    /** @param value governed evidence reference @return this design */
    public Design addEvidenceReference(String value) {
      if (value != null && !value.trim().isEmpty() && !evidenceReferences.contains(value.trim())) {
        evidenceReferences.add(value.trim());
      }
      return this;
    }

    /** @param value explicit assumption @return this design */
    public Design addAssumption(String value) {
      if (value != null && !value.trim().isEmpty()) {
        assumptions.add(value.trim());
      }
      return this;
    }

    /** @param value explicit unresolved gap @return this design */
    public Design addGap(String value) {
      if (value != null && !value.trim().isEmpty()) {
        gaps.add(value.trim());
      }
      return this;
    }

    /** @return physical timestep in s */
    public double getTimeStepSeconds() {
      return timeStepSeconds;
    }

    /** @return explicit input snapshot */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("studyId", studyId);
      map.put("designCaseId", designCaseId);
      map.put("sourceId", sourceId);
      map.put("protectedEquipmentTag", protectedEquipmentTag);
      map.put("concurrencyGroup", concurrencyGroup);
      map.put("maximumAllowablePressureBara", Double.valueOf(maximumAllowablePressureBara));
      map.put("reliefSetPressureBara", Double.valueOf(reliefSetPressureBara));
      map.put("psvOverpressureFraction", Double.valueOf(psvOverpressureFraction));
      map.put("blowdownOrificeDiameterM", Double.valueOf(blowdownOrificeDiameterM));
      map.put("blowdownDischargeCoefficient", Double.valueOf(blowdownDischargeCoefficient));
      map.put("backPressureBara", Double.valueOf(backPressureBara));
      map.put("stopPressureBara", Double.valueOf(stopPressureBara));
      map.put("fireHeatInputW", Double.valueOf(fireHeatInputW));
      map.put("wallMassKg", Double.valueOf(wallMassKg));
      map.put("wallAreaM2", Double.valueOf(wallAreaM2));
      map.put("wallSpecificHeatJPerKgK", Double.valueOf(wallSpecificHeatJPerKgK));
      map.put("wallHeatTransferCoefficientWPerM2K", Double.valueOf(wallHeatTransferCoefficientWPerM2K));
      map.put("headerDiameterM", Double.valueOf(headerDiameterM));
      map.put("headerPressureBara", Double.valueOf(headerPressureBara));
      map.put("headerTemperatureK", Double.valueOf(headerTemperatureK));
      map.put("headerMolarMassKgPerMol", Double.valueOf(headerMolarMassKgPerMol));
      map.put("headerGamma", Double.valueOf(headerGamma));
      map.put("maximumHeaderMach", Double.valueOf(maximumHeaderMach));
      map.put("flareTipDiameterM", Double.valueOf(flareTipDiameterM));
      map.put("flareFlameHeightM", Double.valueOf(flareFlameHeightM));
      map.put("flareRadiantFraction", Double.valueOf(flareRadiantFraction));
      map.put("flareDesignHeatDutyW", Double.valueOf(flareDesignHeatDutyW));
      map.put("flareDesignMassFlowKgPerSecond", Double.valueOf(flareDesignMassFlowKgPerSecond));
      map.put("flareDesignMolarFlowMolPerSecond", Double.valueOf(flareDesignMolarFlowMolPerSecond));
      map.put("timeStepSeconds", Double.valueOf(timeStepSeconds));
      map.put("maximumTimeSeconds", Double.valueOf(maximumTimeSeconds));
      map.put("scenarioSelectionReviewed", Boolean.valueOf(scenarioSelectionReviewed));
      map.put("sourceDiagramsReviewed", Boolean.valueOf(sourceDiagramsReviewed));
      map.put("pidTopologyVerified", Boolean.valueOf(pidTopologyVerified));
      map.put("lineEquipmentListsReviewed", Boolean.valueOf(lineEquipmentListsReviewed));
      map.put("pipingSpecificationRowsReviewed", Boolean.valueOf(pipingSpecificationRowsReviewed));
      map.put("vesselInventoryReviewed", Boolean.valueOf(vesselInventoryReviewed));
      map.put("valveSizingBasisReviewed", Boolean.valueOf(valveSizingBasisReviewed));
      map.put("psvBasisReviewed", Boolean.valueOf(psvBasisReviewed));
      map.put("flareSystemBasisReviewed", Boolean.valueOf(flareSystemBasisReviewed));
      map.put("fireCaseReviewed", Boolean.valueOf(fireCaseReviewed));
      map.put("standardsReviewed", Boolean.valueOf(standardsReviewed));
      map.put("assumptions", new ArrayList<String>(assumptions));
      map.put("gaps", new ArrayList<String>(gaps));
      map.put("evidenceReferences", new ArrayList<String>(evidenceReferences));
      return Collections.unmodifiableMap(map);
    }

    private void validate() {
      for (String text : new String[] {studyId, designCaseId, sourceId, protectedEquipmentTag, concurrencyGroup}) {
        if (text == null || text.trim().isEmpty()) {
          throw new IllegalArgumentException("Stable study and equipment identities are required");
        }
      }
      for (double value : new double[] {maximumAllowablePressureBara, reliefSetPressureBara, blowdownOrificeDiameterM,
          blowdownDischargeCoefficient, backPressureBara, stopPressureBara, wallMassKg, wallAreaM2,
          wallSpecificHeatJPerKgK, wallHeatTransferCoefficientWPerM2K, headerDiameterM, headerPressureBara,
          headerTemperatureK, headerMolarMassKgPerMol, headerGamma, maximumHeaderMach, flareTipDiameterM,
          flareFlameHeightM, flareRadiantFraction, flareDesignHeatDutyW, flareDesignMassFlowKgPerSecond,
          flareDesignMolarFlowMolPerSecond, timeStepSeconds, maximumTimeSeconds}) {
        if (!Double.isFinite(value) || value <= 0.0) {
          throw new IllegalArgumentException("Connection design values must be finite and positive");
        }
      }
      if (!Double.isFinite(fireHeatInputW) || fireHeatInputW < 0.0
          || reliefSetPressureBara > maximumAllowablePressureBara || stopPressureBara < backPressureBara
          || psvOverpressureFraction <= 0.0 || psvOverpressureFraction >= 1.0 || maximumHeaderMach >= 1.0
          || flareRadiantFraction >= 1.0) {
        throw new IllegalArgumentException("Pressure, heat, fraction or Mach ordering is invalid");
      }
    }
  }

  /** Immutable executed connection evidence. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<String, Object> designSnapshot;
    private final Map<String, Object> compressorFingerprint;
    private final double protectedVolumeM3;
    private final double initialPressureBara;
    private final double initialTemperatureK;
    private final EngineeringCalculationResult<CoupledReliefBlowdownFlareResult> calculationResult;

    private Result(Design design, CompressorAutomationStudy.Result compressorResult, double protectedVolumeM3,
        double initialPressureBara, double initialTemperatureK,
        EngineeringCalculationResult<CoupledReliefBlowdownFlareResult> calculationResult) {
      this.designSnapshot = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(design.toMap()));
      this.compressorFingerprint = compressorFingerprint(compressorResult);
      this.protectedVolumeM3 = protectedVolumeM3;
      this.initialPressureBara = initialPressureBara;
      this.initialTemperatureK = initialTemperatureK;
      this.calculationResult = calculationResult;
    }

    /** @return typed existing coupled calculation result */
    public EngineeringCalculationResult<CoupledReliefBlowdownFlareResult> getCalculationResult() {
      return calculationResult;
    }

    /** @return strict structured result and qualification evidence */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("schemaVersion", "neqsim_compressor_relief_disposal_study.v1");
      map.put("design", new LinkedHashMap<String, Object>(designSnapshot));
      map.put("compressorCase", new LinkedHashMap<String, Object>(compressorFingerprint));
      map.put("protectedVolumeM3", Double.valueOf(protectedVolumeM3));
      map.put("initialPressureBara", Double.valueOf(initialPressureBara));
      map.put("initialTemperatureK", Double.valueOf(initialTemperatureK));
      map.put("calculation", calculationMap(calculationResult));
      map.put("metrics", metrics(calculationResult));
      map.put("sourceTrainMutated", Boolean.FALSE);
      map.put("qualification", "PUBLIC_SYNTHETIC_REFERENCE; ENGINEERING_REVIEW_AND_VENDOR_DATA_REQUIRED");
      return Collections.unmodifiableMap(map);
    }

    private static Map<String, Object> metrics(EngineeringCalculationResult<CoupledReliefBlowdownFlareResult> result) {
      Map<String, Object> metrics = new LinkedHashMap<String, Object>();
      if (result == null || result.getValue() == null) {
        metrics.put("status", "NOT_EVALUATED");
        metrics.put("reason", "COUPLED_CALCULATION_BLOCKED");
        return metrics;
      }
      Map<String, Object> value = result.getValue().toMap();
      Map<String, Object> dynamicStudy = map(value.get("dynamicStudy"));
      Map<String, Object> dynamicResult = map(dynamicStudy.get("result"));
      Map<String, Object> sourceResults = map(dynamicResult.get("sourceResults"));
      Map<String, Object> source = sourceResults.isEmpty() ? new LinkedHashMap<String, Object>()
          : map(sourceResults.values().iterator().next());
      Map<String, Object> profile = map(source.get("profile"));
      Map<String, Object> combined = map(dynamicResult.get("combinedLoad"));
      Map<String, Object> flare = map(dynamicResult.get("flareLoad"));
      metrics.put("status", result.getValue().isCapacityAcceptable() ? "PASS" : "FAIL");
      metrics.put("initialMassKg", firstNumber(profile.get("massKg")));
      metrics.put("finalMassKg", lastNumber(profile.get("massKg")));
      metrics.put("initialPressureBara", firstNumber(profile.get("pressureBara")));
      metrics.put("finalPressureBara", lastNumber(profile.get("pressureBara")));
      metrics.put("releasedMassKg", source.get("totalMassDischargedKg"));
      metrics.put("massClosureErrorKg", massClosure(profile, source));
      metrics.put("peakDisposalLoadKgPerSecond", combined.get("peakTotalMassFlowKgPerS"));
      metrics.put("peakDisposalTimeSeconds", combined.get("peakTimeS"));
      metrics.put("timeToHalfPressureSeconds", source.get("timeToHalfPressureS"));
      metrics.put("timeTo7BargSeconds", source.get("timeTo7BargS"));
      metrics.put("halfPressureCriterionMet", source.get("halfPressureCriterionMet"));
      metrics.put("sevenBargCriterionMet", source.get("sevenBargCriterionMet"));
      metrics.put("minimumFluidTemperatureK", source.get("minFluidTemperatureK"));
      metrics.put("minimumWallTemperatureK", source.get("minWallTemperatureK"));
      metrics.put("headerMach", combined.get("headerMach"));
      metrics.put("headerMachAcceptable", combined.get("headerMachAcceptable"));
      metrics.put("totalMassToFlareKg", flare.get("totalMassToFlareKg"));
      metrics.put("sourceToFlareIntegrationDifferenceKg",
          difference(source.get("totalMassDischargedKg"), flare.get("totalMassToFlareKg")));
      metrics.put("flareCapacity", flare.get("capacity"));
      metrics.put("engineeringApprovalRequired", Boolean.TRUE);
      return metrics;
    }

    private static Map<String, Object> calculationMap(
        EngineeringCalculationResult<CoupledReliefBlowdownFlareResult> result) {
      Map<String, Object> map = new LinkedHashMap<String, Object>(result.toMap());
      map.put("value", result.getValue() == null ? null : result.getValue().toMap());
      return map;
    }

    private static Map<String, Object> compressorFingerprint(CompressorAutomationStudy.Result result) {
      Map<String, Object> source = result.toMap();
      Map<String, Object> fingerprint = new LinkedHashMap<String, Object>();
      fingerprint.put("schemaVersion", source.get("schemaVersion"));
      fingerprint.put("profile", source.get("profile"));
      fingerprint.put("timeStepSeconds", source.get("timeStepSeconds"));
      fingerprint.put("durationSeconds", source.get("durationSeconds"));
      fingerprint.put("eventSeconds", source.get("eventSeconds"));
      fingerprint.put("sampleCount", Integer.valueOf(result.getSamples().size()));
      fingerprint.put("nativePassed", Boolean.valueOf(result.getNativeResult().isPassed()));
      return Collections.unmodifiableMap(fingerprint);
    }

    private static Double massClosure(Map<String, Object> profile, Map<String, Object> source) {
      Double initial = firstNumber(profile.get("massKg"));
      Double terminal = lastNumber(profile.get("massKg"));
      Object released = source.get("totalMassDischargedKg");
      if (initial == null || terminal == null || !(released instanceof Number)) {
        return null;
      }
      return Double.valueOf(initial.doubleValue() - terminal.doubleValue() - ((Number) released).doubleValue());
    }

    private static Double difference(Object first, Object second) {
      if (!(first instanceof Number) || !(second instanceof Number)) {
        return null;
      }
      return Double.valueOf(((Number) first).doubleValue() - ((Number) second).doubleValue());
    }

    private static Double firstNumber(Object value) {
      if (!(value instanceof List) || ((List<?>) value).isEmpty()) {
        return null;
      }
      Object first = ((List<?>) value).get(0);
      return first instanceof Number ? Double.valueOf(((Number) first).doubleValue()) : null;
    }

    private static Double lastNumber(Object value) {
      if (!(value instanceof List) || ((List<?>) value).isEmpty()) {
        return null;
      }
      List<?> list = (List<?>) value;
      Object last = list.get(list.size() - 1);
      return last instanceof Number ? Double.valueOf(((Number) last).doubleValue()) : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
      return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<String, Object>();
    }
  }
}
