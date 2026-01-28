package neqsim.process.mechanicaldesign.heatexchanger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.heatexchanger.UtilityStreamSpecification;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * Mechanical design for a generic heat exchanger. Provides detailed sizing estimates for supported
 * exchanger configurations and selects a preferred option based on configurable criteria.
 *
 * <p>
 * The implementation supports both full two-stream heat exchangers and single-stream heaters or
 * coolers that only know their process-side conditions. When the utility side is described through
 * {@link UtilityStreamSpecification} the sizing routine derives approximate UA and approach
 * temperatures before computing a simplified geometric layout.
 * </p>
 */
public class HeatExchangerMechanicalDesign extends MechanicalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private static final double DEFAULT_OVERALL_HEAT_TRANSFER_COEFFICIENT = 500.0; // W/(m^2*K)

  private double usedOverallHeatTransferCoefficient = DEFAULT_OVERALL_HEAT_TRANSFER_COEFFICIENT;
  private double calculatedUA = Double.NaN;
  private double logMeanTemperatureDifference = Double.NaN;
  private double approachTemperature = Double.NaN;

  // ============================================================================
  // Process Design Parameters
  // ============================================================================

  /** Design pressure margin factor (e.g., 1.10 for 10% margin). */
  private double designPressureMargin = 1.10;

  /** Design temperature margin above max operating in Celsius. */
  private double designTemperatureMarginC = 25.0;

  /** Minimum approach temperature in Celsius. */
  private double minApproachTemperatureC = 10.0;

  /** Design duty margin factor (e.g., 1.10 for 10% margin). */
  private double dutyMargin = 1.10;

  /** Area margin factor (e.g., 1.15 for 15% excess area). */
  private double areaMargin = 1.15;

  // ============================================================================
  // Fouling Resistance Parameters (m²K/W per TEMA standards)
  // ============================================================================

  /** Shell side fouling resistance for water service. */
  private double foulingResistanceShellWater = 0.000176;

  /** Shell side fouling resistance for hydrocarbon service. */
  private double foulingResistanceShellHC = 0.000352;

  /** Tube side fouling resistance for water service. */
  private double foulingResistanceTubeWater = 0.000176;

  /** Tube side fouling resistance for hydrocarbon service. */
  private double foulingResistanceTubeHC = 0.000352;

  // ============================================================================
  // Velocity Limits (m/s per TEMA standards)
  // ============================================================================

  /** Maximum tube side velocity to prevent erosion. */
  private double maxTubeVelocity = 3.0;

  /** Minimum tube side velocity to prevent excessive fouling. */
  private double minTubeVelocity = 0.9;

  /** Maximum shell side velocity. */
  private double maxShellVelocity = 2.0;

  // ============================================================================
  // TEMA Classification
  // ============================================================================

  /**
   * TEMA class: "R" (refinery/severe), "C" (commercial), "B" (chemical).
   */
  private String temaClass = "R";

  /** TEMA shell type: "E", "F", "G", "H", "J", "K", "X". */
  private String temaShellType = "E";

  /** TEMA front head type: "A", "B", "C", "N", "D". */
  private String temaFrontHeadType = "A";

  /** TEMA rear head type: "L", "M", "N", "P", "S", "T", "U", "W". */
  private String temaRearHeadType = "S";

  // ============================================================================
  // Tube Bundle Parameters
  // ============================================================================

  /** Tube outer diameter in mm (standard sizes: 19.05, 25.4 mm). */
  private double tubeOuterDiameterMm = 19.05;

  /** Tube wall thickness in mm (BWG gauge). */
  private double tubeWallThicknessMm = 2.11;

  /** Maximum tube length in m. */
  private double maxTubeLengthM = 6.0;

  /** Tube pitch ratio (pitch/OD), typically 1.25-1.50. */
  private double tubePitchRatio = 1.25;

  /** Tube layout pattern: "triangular", "rotated_triangular", "square", "rotated_square". */
  private String tubeLayoutPattern = "triangular";

  /** Number of tube passes. */
  private int tubePasses = 2;

  /** Number of shell passes. */
  private int shellPasses = 1;

  /** Baffle cut as percentage of shell diameter (typically 20-35%). */
  private double baffleCutPercent = 25.0;

  /** Baffle spacing as fraction of shell diameter (typically 0.2-1.0). */
  private double baffleSpacingRatio = 0.4;

  /**
   * Ranking metric for automatic exchanger-type selection.
   */
  public enum SelectionCriterion {
    /** Select type with the smallest calculated heat-transfer area. */
    MIN_AREA,
    /** Select type with the lowest estimated dry weight. */
    MIN_WEIGHT,
    /** Select type with the lowest estimated pressure drop. */
    MIN_PRESSURE_DROP
  }

  private List<HeatExchangerType> candidateTypes =
      new ArrayList<>(Arrays.asList(HeatExchangerType.values()));
  private SelectionCriterion selectionCriterion = SelectionCriterion.MIN_AREA;
  private HeatExchangerType manualSelection;
  private List<HeatExchangerSizingResult> sizingResults = new ArrayList<>();
  private HeatExchangerSizingResult selectedSizingResult;

  /**
   * Constructor for HeatExchangerMechanicalDesign.
   *
   * @param equipment {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public HeatExchangerMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    super.calcDesign();
    ProcessEquipmentInterface equipment = getProcessEquipment();
    double duty = determineDuty(equipment);

    usedOverallHeatTransferCoefficient = DEFAULT_OVERALL_HEAT_TRANSFER_COEFFICIENT;
    calculatedUA = Double.NaN;
    logMeanTemperatureDifference = Double.NaN;
    approachTemperature = Double.NaN;

    if (equipment instanceof HeatExchanger) {
      HeatExchanger exchanger = (HeatExchanger) equipment;
      handleTwoStreamThermalData(exchanger, duty);
      finalizeThermalEstimates(duty);
      buildSizingResults(exchanger, duty, true);
    } else if (equipment instanceof Heater) {
      Heater heater = (Heater) equipment;
      UtilityStreamSpecification specification = heater.getUtilitySpecification();
      handleSingleStreamThermalData(heater, specification, duty);
      finalizeThermalEstimates(duty);
      buildSizingResults(null, duty, false);
    } else {
      sizingResults = new ArrayList<>();
      selectedSizingResult = null;
      return;
    }

    selectPreferredResult();
    applySelectedSizing();
  }

  private void handleTwoStreamThermalData(HeatExchanger exchanger, double duty) {
    double uaValue = exchanger.getUAvalue();
    if (uaValue > 0.0) {
      calculatedUA = uaValue;
    }

    double[] temperatures = determineTwoStreamTemperatures(exchanger);
    if (temperatures != null) {
      double deltaT1 = Math.abs(temperatures[0] - temperatures[3]);
      double deltaT2 = Math.abs(temperatures[1] - temperatures[2]);
      logMeanTemperatureDifference = calculateLmtd(deltaT1, deltaT2);
      approachTemperature = Math.min(deltaT1, deltaT2);
    }

    double effectiveness = exchanger.getThermalEffectiveness();
    if (duty > 0.0 && effectiveness > 0.0 && effectiveness < 0.999) {
      double deltaTmax = Math.abs(exchanger.getInTemperature(0) - exchanger.getInTemperature(1));
      if (deltaTmax > 0.0) {
        double cmin = duty / (effectiveness * deltaTmax);
        double ntu = -Math.log(1.0 - effectiveness);
        double uaFromEffectiveness = cmin * ntu;
        if (Double.isFinite(uaFromEffectiveness) && uaFromEffectiveness > 0.0) {
          if (!(calculatedUA > 0.0) || uaFromEffectiveness > calculatedUA) {
            calculatedUA = uaFromEffectiveness;
          }
        }
      }
    }
  }

  private void handleSingleStreamThermalData(Heater heater,
      UtilityStreamSpecification specification, double duty) {
    if (specification != null && specification.hasOverallHeatTransferCoefficient()) {
      usedOverallHeatTransferCoefficient = specification.getOverallHeatTransferCoefficient();
    }

    double[] temperatures = determineSingleStreamTemperatures(heater, specification, duty);
    if (temperatures != null) {
      double deltaT1 = Math.abs(temperatures[0] - temperatures[3]);
      double deltaT2 = Math.abs(temperatures[1] - temperatures[2]);
      logMeanTemperatureDifference = calculateLmtd(deltaT1, deltaT2);
      approachTemperature = Math.min(deltaT1, deltaT2);
    } else {
      double processIn =
          heater.getInletStream() != null ? heater.getInletStream().getTemperature() : Double.NaN;
      double processOut =
          heater.getOutletStream() != null ? heater.getOutletStream().getTemperature() : Double.NaN;
      double delta = Math.abs(processOut - processIn);
      if (delta > 0.0) {
        logMeanTemperatureDifference = delta;
        approachTemperature = delta;
      }
    }

    if (duty > 0.0 && logMeanTemperatureDifference > 0.0) {
      calculatedUA = duty / logMeanTemperatureDifference;
    }
  }

  private void finalizeThermalEstimates(double duty) {
    if (Double.isNaN(approachTemperature) || approachTemperature < 0.0) {
      approachTemperature = 0.0;
    }

    if (Double.isNaN(logMeanTemperatureDifference) || logMeanTemperatureDifference <= 0.0) {
      if (approachTemperature > 0.0) {
        logMeanTemperatureDifference = approachTemperature;
      } else if (duty > 0.0 && usedOverallHeatTransferCoefficient > 0.0) {
        logMeanTemperatureDifference = duty / usedOverallHeatTransferCoefficient;
      } else {
        logMeanTemperatureDifference = 1.0;
      }
    }

    if (!(calculatedUA > 0.0) && duty > 0.0 && logMeanTemperatureDifference > 0.0) {
      calculatedUA = duty / logMeanTemperatureDifference;
    }

    if (Double.isNaN(calculatedUA) || calculatedUA <= 0.0) {
      calculatedUA = usedOverallHeatTransferCoefficient;
    }
  }

  private void buildSizingResults(HeatExchanger exchanger, double duty,
      boolean useTypeSpecificCoefficient) {
    List<HeatExchangerType> typesToEvaluate = determineTypesToEvaluate();
    sizingResults = new ArrayList<>(typesToEvaluate.size());

    for (HeatExchangerType type : typesToEvaluate) {
      double typeApproach = approachTemperature > 0.0
          ? Math.max(approachTemperature, type.getAllowableApproachTemperature())
          : type.getAllowableApproachTemperature();

      double uaForType = calculatedUA;
      if (!(uaForType > 0.0) && duty > 0.0) {
        uaForType = duty / Math.max(typeApproach, 1.0);
      }
      if (Double.isNaN(uaForType) || uaForType <= 0.0) {
        uaForType = type.getTypicalOverallHeatTransferCoefficient();
      }

      double sizingCoefficient =
          useTypeSpecificCoefficient ? type.getTypicalOverallHeatTransferCoefficient()
              : Math.max(usedOverallHeatTransferCoefficient,
                  type.getTypicalOverallHeatTransferCoefficient());
      double requiredArea = Math.max(uaForType / Math.max(sizingCoefficient, 1e-6), 0.1);

      HeatExchangerSizingResult result =
          type.createSizingResult(exchanger, requiredArea, uaForType, typeApproach);
      sizingResults.add(result);
    }
  }

  private List<HeatExchangerType> determineTypesToEvaluate() {
    Set<HeatExchangerType> uniqueTypes = new LinkedHashSet<>();
    if (manualSelection != null) {
      uniqueTypes.add(manualSelection);
    }
    uniqueTypes.addAll(candidateTypes);
    if (uniqueTypes.isEmpty()) {
      uniqueTypes.addAll(Arrays.asList(HeatExchangerType.values()));
    }
    return new ArrayList<>(uniqueTypes);
  }

  private void selectPreferredResult() {
    if (sizingResults.isEmpty()) {
      selectedSizingResult = null;
      return;
    }
    if (manualSelection != null) {
      selectedSizingResult =
          sizingResults.stream().filter(result -> result.getType() == manualSelection).findFirst()
              .orElse(sizingResults.get(0));
      return;
    }
    selectedSizingResult = sizingResults.stream().min(
        (a, b) -> Double.compare(a.getMetric(selectionCriterion), b.getMetric(selectionCriterion)))
        .orElse(sizingResults.get(0));
  }

  private void applySelectedSizing() {
    if (selectedSizingResult == null) {
      return;
    }
    innerDiameter = selectedSizingResult.getInnerDiameter();
    outerDiameter = selectedSizingResult.getOuterDiameter();
    wallThickness = selectedSizingResult.getWallThickness();
    tantanLength = selectedSizingResult.getEstimatedLength();
    weightVessel = selectedSizingResult.getEstimatedWeight();

    setTantanLength(tantanLength);
    setModuleLength(selectedSizingResult.getModuleLength());
    setModuleWidth(selectedSizingResult.getModuleWidth());
    setModuleHeight(selectedSizingResult.getModuleHeight());
    setWeigthVesselShell(weightVessel);
    setWeightVessel(weightVessel);
    setWeightTotal(weightVessel);
  }

  public void setCandidateTypes(List<HeatExchangerType> types) {
    Objects.requireNonNull(types, "types");
    if (types.isEmpty()) {
      this.candidateTypes = new ArrayList<>(Arrays.asList(HeatExchangerType.values()));
    } else {
      this.candidateTypes = new ArrayList<>(new LinkedHashSet<>(types));
    }
  }

  public void setCandidateTypes(HeatExchangerType... types) {
    setCandidateTypes(Arrays.asList(types));
  }

  public List<HeatExchangerType> getCandidateTypes() {
    return Collections.unmodifiableList(candidateTypes);
  }

  public SelectionCriterion getSelectionCriterion() {
    return selectionCriterion;
  }

  public void setSelectionCriterion(SelectionCriterion selectionCriterion) {
    this.selectionCriterion = Objects.requireNonNull(selectionCriterion, "selectionCriterion");
  }

  public HeatExchangerType getManualSelection() {
    return manualSelection;
  }

  public void setManualSelection(HeatExchangerType manualSelection) {
    this.manualSelection = manualSelection;
  }

  public List<HeatExchangerSizingResult> getSizingResults() {
    return Collections.unmodifiableList(sizingResults);
  }

  public HeatExchangerSizingResult getSelectedSizingResult() {
    return selectedSizingResult;
  }

  public HeatExchangerType getSelectedType() {
    return selectedSizingResult == null ? null : selectedSizingResult.getType();
  }

  public String getSizingSummary() {
    if (sizingResults.isEmpty()) {
      return "No sizing results available.";
    }
    return sizingResults.stream()
        .map(result -> result.getType().getDisplayName() + ": area="
            + String.format("%.2f", result.getRequiredArea()) + " m2, weight="
            + String.format("%.1f", result.getEstimatedWeight()) + " kg")
        .collect(Collectors.joining("; "));
  }

  /**
   * @return Calculated UA (W/K) based on the available duty and temperature approach information.
   */
  public double getCalculatedUA() {
    return calculatedUA;
  }

  /** @return Log-mean temperature difference used for the simplified sizing (K). */
  public double getLogMeanTemperatureDifference() {
    return logMeanTemperatureDifference;
  }

  /** @return Minimum temperature difference between the hot and cold streams (K). */
  public double getApproachTemperature() {
    return approachTemperature;
  }

  /** @return Overall heat-transfer coefficient used during the calculation (W/(m^2*K)). */
  public double getUsedOverallHeatTransferCoefficient() {
    return usedOverallHeatTransferCoefficient;
  }

  private double determineDuty(ProcessEquipmentInterface equipment) {
    if (equipment instanceof HeatExchanger) {
      return Math.abs(((HeatExchanger) equipment).getDuty());
    } else if (equipment instanceof Heater) {
      return Math.abs(((Heater) equipment).getDuty());
    }
    return 0.0;
  }

  private double[] determineTwoStreamTemperatures(HeatExchanger exchanger) {
    double in0 = exchanger.getInTemperature(0);
    double out0 = exchanger.getOutTemperature(0);
    double in1 = exchanger.getInTemperature(1);
    double out1 = exchanger.getOutTemperature(1);

    double avg0 = (in0 + out0) / 2.0;
    double avg1 = (in1 + out1) / 2.0;

    if (Double.isNaN(avg0) || Double.isNaN(avg1)) {
      return null;
    }

    if (avg0 >= avg1) {
      return new double[] {in0, out0, in1, out1};
    }
    return new double[] {in1, out1, in0, out0};
  }

  private double[] determineSingleStreamTemperatures(Heater heater, UtilityStreamSpecification spec,
      double duty) {
    if (heater.getInletStream() == null || heater.getOutletStream() == null) {
      return null;
    }

    double processIn = heater.getInletStream().getTemperature();
    double processOut = heater.getOutletStream().getTemperature();
    if (Double.isNaN(processIn) || Double.isNaN(processOut)) {
      return null;
    }

    boolean heating = processOut >= processIn;
    double utilitySupply = Double.NaN;
    double utilityReturn = Double.NaN;

    if (spec != null) {
      if (spec.hasSupplyTemperature()) {
        utilitySupply = spec.getSupplyTemperature();
      } else if (spec.hasApproachTemperature()) {
        utilitySupply = heating ? processOut + spec.getApproachTemperature()
            : processOut - spec.getApproachTemperature();
      }

      if (spec.hasReturnTemperature()) {
        utilityReturn = spec.getReturnTemperature();
      }

      if (Double.isNaN(utilityReturn) && spec.hasHeatCapacityRate() && duty > 0.0
          && !Double.isNaN(utilitySupply)) {
        double deltaUtility = duty / spec.getHeatCapacityRate();
        utilityReturn = heating ? utilitySupply - deltaUtility : utilitySupply + deltaUtility;
      }

      if (Double.isNaN(utilityReturn) && !Double.isNaN(utilitySupply)) {
        utilityReturn = utilitySupply;
      }
    }

    if (Double.isNaN(utilitySupply) || Double.isNaN(utilityReturn)) {
      return null;
    }

    if (heating) {
      return new double[] {utilitySupply, utilityReturn, processIn, processOut};
    }
    return new double[] {processIn, processOut, utilitySupply, utilityReturn};
  }

  private double calculateLmtd(double deltaT1, double deltaT2) {
    double safeDeltaT1 = Math.max(deltaT1, 1e-6);
    double safeDeltaT2 = Math.max(deltaT2, 1e-6);

    if (Math.abs(safeDeltaT1 - safeDeltaT2) < 1e-6) {
      return (safeDeltaT1 + safeDeltaT2) / 2.0;
    }
    return Math.abs((safeDeltaT1 - safeDeltaT2) / Math.log(safeDeltaT1 / safeDeltaT2));
  }

  // ============================================================================
  // Process Design Parameter Getters/Setters
  // ============================================================================

  /**
   * Gets the design pressure margin factor.
   *
   * @return design pressure margin (e.g., 1.10 for 10% margin)
   */
  public double getDesignPressureMargin() {
    return designPressureMargin;
  }

  /**
   * Sets the design pressure margin factor.
   *
   * @param margin margin factor (e.g., 1.10 for 10%)
   */
  public void setDesignPressureMargin(double margin) {
    this.designPressureMargin = margin;
  }

  /**
   * Gets the design temperature margin in Celsius.
   *
   * @return temperature margin in Celsius
   */
  public double getDesignTemperatureMarginC() {
    return designTemperatureMarginC;
  }

  /**
   * Sets the design temperature margin in Celsius.
   *
   * @param marginC temperature margin in Celsius
   */
  public void setDesignTemperatureMarginC(double marginC) {
    this.designTemperatureMarginC = marginC;
  }

  /**
   * Gets the minimum approach temperature.
   *
   * @return minimum approach temperature in Celsius
   */
  public double getMinApproachTemperatureC() {
    return minApproachTemperatureC;
  }

  /**
   * Sets the minimum approach temperature.
   *
   * @param tempC minimum approach temperature in Celsius
   */
  public void setMinApproachTemperatureC(double tempC) {
    this.minApproachTemperatureC = tempC;
  }

  /**
   * Gets the duty margin factor.
   *
   * @return duty margin factor
   */
  public double getDutyMargin() {
    return dutyMargin;
  }

  /**
   * Sets the duty margin factor.
   *
   * @param margin duty margin factor
   */
  public void setDutyMargin(double margin) {
    this.dutyMargin = margin;
  }

  /**
   * Gets the area margin factor.
   *
   * @return area margin factor
   */
  public double getAreaMarginFactor() {
    return areaMargin;
  }

  /**
   * Sets the area margin factor.
   *
   * @param margin area margin factor
   */
  public void setAreaMarginFactor(double margin) {
    this.areaMargin = margin;
  }

  // ============================================================================
  // Fouling Resistance Getters/Setters
  // ============================================================================

  /**
   * Gets the shell side fouling resistance for water service.
   *
   * @return fouling resistance in m²K/W
   */
  public double getFoulingResistanceShellWater() {
    return foulingResistanceShellWater;
  }

  /**
   * Sets the shell side fouling resistance for water service.
   *
   * @param resistance fouling resistance in m²K/W
   */
  public void setFoulingResistanceShellWater(double resistance) {
    this.foulingResistanceShellWater = resistance;
  }

  /**
   * Gets the shell side fouling resistance for hydrocarbon service.
   *
   * @return fouling resistance in m²K/W
   */
  public double getFoulingResistanceShellHC() {
    return foulingResistanceShellHC;
  }

  /**
   * Sets the shell side fouling resistance for hydrocarbon service.
   *
   * @param resistance fouling resistance in m²K/W
   */
  public void setFoulingResistanceShellHC(double resistance) {
    this.foulingResistanceShellHC = resistance;
  }

  /**
   * Gets the tube side fouling resistance for water service.
   *
   * @return fouling resistance in m²K/W
   */
  public double getFoulingResistanceTubeWater() {
    return foulingResistanceTubeWater;
  }

  /**
   * Sets the tube side fouling resistance for water service.
   *
   * @param resistance fouling resistance in m²K/W
   */
  public void setFoulingResistanceTubeWater(double resistance) {
    this.foulingResistanceTubeWater = resistance;
  }

  /**
   * Gets the tube side fouling resistance for hydrocarbon service.
   *
   * @return fouling resistance in m²K/W
   */
  public double getFoulingResistanceTubeHC() {
    return foulingResistanceTubeHC;
  }

  /**
   * Sets the tube side fouling resistance for hydrocarbon service.
   *
   * @param resistance fouling resistance in m²K/W
   */
  public void setFoulingResistanceTubeHC(double resistance) {
    this.foulingResistanceTubeHC = resistance;
  }

  /**
   * Calculates the overall fouling resistance for given service types.
   *
   * @param shellServiceWater true if shell side is water service
   * @param tubeServiceWater true if tube side is water service
   * @return total fouling resistance in m²K/W
   */
  public double calculateTotalFoulingResistance(boolean shellServiceWater,
      boolean tubeServiceWater) {
    double shellFouling =
        shellServiceWater ? foulingResistanceShellWater : foulingResistanceShellHC;
    double tubeFouling = tubeServiceWater ? foulingResistanceTubeWater : foulingResistanceTubeHC;
    return shellFouling + tubeFouling;
  }

  // ============================================================================
  // Velocity Limit Getters/Setters
  // ============================================================================

  /**
   * Gets the maximum tube side velocity.
   *
   * @return maximum velocity in m/s
   */
  public double getMaxTubeVelocity() {
    return maxTubeVelocity;
  }

  /**
   * Sets the maximum tube side velocity.
   *
   * @param velocity maximum velocity in m/s
   */
  public void setMaxTubeVelocity(double velocity) {
    this.maxTubeVelocity = velocity;
  }

  /**
   * Gets the minimum tube side velocity.
   *
   * @return minimum velocity in m/s
   */
  public double getMinTubeVelocity() {
    return minTubeVelocity;
  }

  /**
   * Sets the minimum tube side velocity.
   *
   * @param velocity minimum velocity in m/s
   */
  public void setMinTubeVelocity(double velocity) {
    this.minTubeVelocity = velocity;
  }

  /**
   * Gets the maximum shell side velocity.
   *
   * @return maximum velocity in m/s
   */
  public double getMaxShellVelocity() {
    return maxShellVelocity;
  }

  /**
   * Sets the maximum shell side velocity.
   *
   * @param velocity maximum velocity in m/s
   */
  public void setMaxShellVelocity(double velocity) {
    this.maxShellVelocity = velocity;
  }

  // ============================================================================
  // TEMA Classification Getters/Setters
  // ============================================================================

  /**
   * Gets the TEMA class.
   *
   * @return TEMA class ("R", "C", or "B")
   */
  public String getTemaClass() {
    return temaClass;
  }

  /**
   * Sets the TEMA class.
   *
   * @param temaClass TEMA class ("R" refinery, "C" commercial, "B" chemical)
   */
  public void setTemaClass(String temaClass) {
    this.temaClass = temaClass;
  }

  /**
   * Gets the TEMA shell type.
   *
   * @return TEMA shell type code
   */
  public String getTemaShellType() {
    return temaShellType;
  }

  /**
   * Sets the TEMA shell type.
   *
   * @param shellType TEMA shell type ("E", "F", "G", "H", "J", "K", "X")
   */
  public void setTemaShellType(String shellType) {
    this.temaShellType = shellType;
  }

  /**
   * Gets the TEMA front head type.
   *
   * @return TEMA front head type code
   */
  public String getTemaFrontHeadType() {
    return temaFrontHeadType;
  }

  /**
   * Sets the TEMA front head type.
   *
   * @param headType TEMA front head type ("A", "B", "C", "N", "D")
   */
  public void setTemaFrontHeadType(String headType) {
    this.temaFrontHeadType = headType;
  }

  /**
   * Gets the TEMA rear head type.
   *
   * @return TEMA rear head type code
   */
  public String getTemaRearHeadType() {
    return temaRearHeadType;
  }

  /**
   * Sets the TEMA rear head type.
   *
   * @param headType TEMA rear head type
   */
  public void setTemaRearHeadType(String headType) {
    this.temaRearHeadType = headType;
  }

  /**
   * Gets the full TEMA designation (e.g., "AES").
   *
   * @return TEMA designation string
   */
  public String getTemaDesignation() {
    return temaFrontHeadType + temaShellType + temaRearHeadType;
  }

  // ============================================================================
  // Tube Bundle Parameter Getters/Setters
  // ============================================================================

  /**
   * Gets the tube outer diameter.
   *
   * @return tube OD in mm
   */
  public double getTubeOuterDiameterMm() {
    return tubeOuterDiameterMm;
  }

  /**
   * Sets the tube outer diameter.
   *
   * @param diameterMm tube OD in mm
   */
  public void setTubeOuterDiameterMm(double diameterMm) {
    this.tubeOuterDiameterMm = diameterMm;
  }

  /**
   * Gets the tube wall thickness.
   *
   * @return tube wall thickness in mm
   */
  public double getTubeWallThicknessMm() {
    return tubeWallThicknessMm;
  }

  /**
   * Sets the tube wall thickness.
   *
   * @param thicknessMm tube wall thickness in mm
   */
  public void setTubeWallThicknessMm(double thicknessMm) {
    this.tubeWallThicknessMm = thicknessMm;
  }

  /**
   * Gets the maximum tube length.
   *
   * @return maximum tube length in m
   */
  public double getMaxTubeLengthM() {
    return maxTubeLengthM;
  }

  /**
   * Sets the maximum tube length.
   *
   * @param lengthM maximum tube length in m
   */
  public void setMaxTubeLengthM(double lengthM) {
    this.maxTubeLengthM = lengthM;
  }

  /**
   * Gets the tube pitch ratio.
   *
   * @return tube pitch ratio (pitch/OD)
   */
  public double getTubePitchRatio() {
    return tubePitchRatio;
  }

  /**
   * Sets the tube pitch ratio.
   *
   * @param ratio tube pitch ratio (typically 1.25-1.50)
   */
  public void setTubePitchRatio(double ratio) {
    this.tubePitchRatio = ratio;
  }

  /**
   * Gets the tube layout pattern.
   *
   * @return tube layout pattern
   */
  public String getTubeLayoutPattern() {
    return tubeLayoutPattern;
  }

  /**
   * Sets the tube layout pattern.
   *
   * @param pattern layout pattern ("triangular", "rotated_triangular", "square", "rotated_square")
   */
  public void setTubeLayoutPattern(String pattern) {
    this.tubeLayoutPattern = pattern;
  }

  /**
   * Gets the number of tube passes.
   *
   * @return number of tube passes
   */
  public int getTubePasses() {
    return tubePasses;
  }

  /**
   * Sets the number of tube passes.
   *
   * @param passes number of tube passes
   */
  public void setTubePasses(int passes) {
    this.tubePasses = passes;
  }

  /**
   * Gets the number of shell passes.
   *
   * @return number of shell passes
   */
  public int getShellPasses() {
    return shellPasses;
  }

  /**
   * Sets the number of shell passes.
   *
   * @param passes number of shell passes
   */
  public void setShellPasses(int passes) {
    this.shellPasses = passes;
  }

  /**
   * Gets the baffle cut percentage.
   *
   * @return baffle cut as percentage of shell diameter
   */
  public double getBaffleCutPercent() {
    return baffleCutPercent;
  }

  /**
   * Sets the baffle cut percentage.
   *
   * @param percent baffle cut as percentage (typically 20-35)
   */
  public void setBaffleCutPercent(double percent) {
    this.baffleCutPercent = percent;
  }

  /**
   * Gets the baffle spacing ratio.
   *
   * @return baffle spacing as fraction of shell diameter
   */
  public double getBaffleSpacingRatio() {
    return baffleSpacingRatio;
  }

  /**
   * Sets the baffle spacing ratio.
   *
   * @param ratio baffle spacing as fraction of shell diameter (0.2-1.0)
   */
  public void setBaffleSpacingRatio(double ratio) {
    this.baffleSpacingRatio = ratio;
  }

  /**
   * Loads heat exchanger design parameters from the database.
   */
  public void loadProcessDesignParameters() {
    try {
      neqsim.util.database.NeqSimProcessDesignDataBase database =
          new neqsim.util.database.NeqSimProcessDesignDataBase();
      java.sql.ResultSet dataSet =
          database.getResultSet("SELECT * FROM technicalrequirements_process WHERE "
              + "EQUIPMENTTYPE='HeatExchanger' AND Company='" + getCompanySpecificDesignStandards()
              + "'");

      while (dataSet.next()) {
        String spec = dataSet.getString("SPECIFICATION");
        double minVal = dataSet.getDouble("MINVALUE");
        double maxVal = dataSet.getDouble("MAXVALUE");
        double value = (minVal + maxVal) / 2.0;

        switch (spec) {
          case "DesignPressureMargin":
            this.designPressureMargin = value;
            break;
          case "DesignTemperatureMargin":
            this.designTemperatureMarginC = value;
            break;
          case "MinApproachTemperature":
            this.minApproachTemperatureC = value;
            break;
          case "FoulingResistanceShellWater":
            this.foulingResistanceShellWater = value;
            break;
          case "FoulingResistanceShellHC":
            this.foulingResistanceShellHC = value;
            break;
          case "FoulingResistanceTubeWater":
            this.foulingResistanceTubeWater = value;
            break;
          case "FoulingResistanceTubeHC":
            this.foulingResistanceTubeHC = value;
            break;
          case "MaxTubeVelocity":
            this.maxTubeVelocity = value;
            break;
          case "MinTubeVelocity":
            this.minTubeVelocity = value;
            break;
          case "MaxShellVelocity":
            this.maxShellVelocity = value;
            break;
          case "MaxTubeLength":
            this.maxTubeLengthM = value;
            break;
          case "AreaMargin":
            this.areaMargin = value;
            break;
          default:
            // Ignore unknown parameters
            break;
        }
      }
      dataSet.close();
    } catch (Exception ex) {
      // Use default values if database lookup fails
    }
  }

  // ============================================================================
  // Validation Methods
  // ============================================================================

  /**
   * Validates that tube side velocity is within acceptable limits.
   *
   * @param actualVelocity actual tube side velocity in m/s
   * @return true if velocity is within acceptable range
   */
  public boolean validateTubeVelocity(double actualVelocity) {
    return actualVelocity >= minTubeVelocity && actualVelocity <= maxTubeVelocity;
  }

  /**
   * Validates that shell side velocity is within acceptable limits.
   *
   * @param actualVelocity actual shell side velocity in m/s
   * @return true if velocity is acceptable
   */
  public boolean validateShellVelocity(double actualVelocity) {
    return actualVelocity <= maxShellVelocity;
  }

  /**
   * Validates that approach temperature meets minimum requirements.
   *
   * @param actualApproach actual approach temperature in Celsius
   * @return true if approach temperature is acceptable
   */
  public boolean validateApproachTemperature(double actualApproach) {
    return actualApproach >= minApproachTemperatureC;
  }

  /**
   * Validates that tube length is within acceptable limits.
   *
   * @param actualLengthM actual tube length in meters
   * @return true if tube length is acceptable
   */
  public boolean validateTubeLength(double actualLengthM) {
    return actualLengthM <= maxTubeLengthM;
  }

  /**
   * Calculates the clean overall heat transfer coefficient (without fouling).
   *
   * @param shellHTC shell side heat transfer coefficient in W/(m²K)
   * @param tubeHTC tube side heat transfer coefficient in W/(m²K)
   * @param wallThicknessMm tube wall thickness in mm
   * @param wallConductivity tube wall thermal conductivity in W/(mK), typically 45 for CS
   * @return clean U-value in W/(m²K)
   */
  public double calculateCleanU(double shellHTC, double tubeHTC, double wallThicknessMm,
      double wallConductivity) {
    double wallResistance = (wallThicknessMm / 1000.0) / wallConductivity;
    return 1.0 / (1.0 / shellHTC + wallResistance + 1.0 / tubeHTC);
  }

  /**
   * Calculates the fouled overall heat transfer coefficient.
   *
   * @param cleanU clean U-value in W/(m²K)
   * @param shellServiceWater true if shell side is water service
   * @param tubeServiceWater true if tube side is water service
   * @return fouled U-value in W/(m²K)
   */
  public double calculateFouledU(double cleanU, boolean shellServiceWater,
      boolean tubeServiceWater) {
    double totalFouling = calculateTotalFoulingResistance(shellServiceWater, tubeServiceWater);
    return 1.0 / (1.0 / cleanU + totalFouling);
  }

  /**
   * Performs comprehensive validation of heat exchanger design.
   *
   * @return HeatExchangerValidationResult with status and any issues found
   */
  public HeatExchangerValidationResult validateDesign() {
    HeatExchangerValidationResult result = new HeatExchangerValidationResult();

    // Validate approach temperature
    if (approachTemperature > 0 && !validateApproachTemperature(approachTemperature)) {
      result.addIssue("Approach temperature " + String.format("%.1f", approachTemperature)
          + " °C below minimum " + String.format("%.1f", minApproachTemperatureC) + " °C");
    }

    // Validate tube length if set
    if (tantanLength > 0 && !validateTubeLength(tantanLength)) {
      result.addIssue("Tube length " + String.format("%.1f", tantanLength) + " m exceeds maximum "
          + String.format("%.1f", maxTubeLengthM) + " m");
    }

    // Validate design margins
    if (designPressureMargin < 1.05) {
      result.addIssue("Design pressure margin " + String.format("%.2f", designPressureMargin)
          + " below recommended 1.05");
    }

    result.setValid(result.getIssues().isEmpty());
    return result;
  }

  /**
   * Inner class to hold validation results.
   */
  public static class HeatExchangerValidationResult {
    private boolean valid = true;
    private java.util.List<String> issues = new java.util.ArrayList<>();

    public boolean isValid() {
      return valid;
    }

    public void setValid(boolean valid) {
      this.valid = valid;
    }

    public java.util.List<String> getIssues() {
      return issues;
    }

    public void addIssue(String issue) {
      issues.add(issue);
    }
  }
}

