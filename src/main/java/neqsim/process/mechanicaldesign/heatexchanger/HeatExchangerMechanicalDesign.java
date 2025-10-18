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
 * <p>The implementation supports both full two-stream heat exchangers and single-stream heaters or
 * coolers that only know their process-side conditions. When the utility side is described through
 * {@link UtilityStreamSpecification} the sizing routine derives approximate UA and approach
 * temperatures before computing a simplified geometric layout.</p>
 */
public class HeatExchangerMechanicalDesign extends MechanicalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private static final double DEFAULT_OVERALL_HEAT_TRANSFER_COEFFICIENT = 500.0; // W/(m^2*K)

  private double usedOverallHeatTransferCoefficient = DEFAULT_OVERALL_HEAT_TRANSFER_COEFFICIENT;
  private double calculatedUA = Double.NaN;
  private double logMeanTemperatureDifference = Double.NaN;
  private double approachTemperature = Double.NaN;

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
      double processIn = heater.getInletStream() != null
          ? heater.getInletStream().getTemperature() : Double.NaN;
      double processOut = heater.getOutletStream() != null
          ? heater.getOutletStream().getTemperature() : Double.NaN;
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

    if (!(calculatedUA > 0.0)) {
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
      if (!(uaForType > 0.0)) {
        uaForType = type.getTypicalOverallHeatTransferCoefficient();
      }

      double sizingCoefficient = useTypeSpecificCoefficient
          ? type.getTypicalOverallHeatTransferCoefficient()
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
      selectedSizingResult = sizingResults.stream()
          .filter(result -> result.getType() == manualSelection).findFirst()
          .orElse(sizingResults.get(0));
      return;
    }
    selectedSizingResult = sizingResults.stream()
        .min((a, b) -> Double.compare(a.getMetric(selectionCriterion),
            b.getMetric(selectionCriterion)))
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
    return sizingResults.stream().map(result -> result.getType().getDisplayName() + ": area="
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
}

