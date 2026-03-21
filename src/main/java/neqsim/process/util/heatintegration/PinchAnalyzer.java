package neqsim.process.util.heatintegration;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Performs pinch analysis (heat integration) on a process system.
 *
 * <p>
 * Identifies hot and cold streams from process equipment, builds composite curves, calculates
 * minimum approach temperature, and recommends heat exchanger matches for energy recovery.
 * </p>
 *
 * <p>
 * Based on Linnhoff &amp; Hindmarsh (1983) pinch technology methodology.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * {@code
 * PinchAnalyzer analyzer = new PinchAnalyzer(process);
 * analyzer.setMinApproachTemperature(10.0); // 10 K
 * analyzer.analyze();
 * double pinchT = analyzer.getPinchTemperature();
 * double minHotUtility = analyzer.getMinHotUtilityDuty();
 * double minColdUtility = analyzer.getMinColdUtilityDuty();
 * String json = analyzer.toJson();
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class PinchAnalyzer implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final ProcessSystem processSystem;
  private double minApproachTemperature = 10.0; // K

  private final List<HeatStream> hotStreams = new ArrayList<>();
  private final List<HeatStream> coldStreams = new ArrayList<>();

  private double pinchTemperature = Double.NaN;
  private double minHotUtilityDuty = 0.0; // W
  private double minColdUtilityDuty = 0.0; // W
  private final List<double[]> hotCompositeCurve = new ArrayList<>();
  private final List<double[]> coldCompositeCurve = new ArrayList<>();
  private final List<double[]> grandCompositeCurve = new ArrayList<>();
  private final List<HeatExchangerMatch> matches = new ArrayList<>();
  private boolean analyzed = false;

  /**
   * Represents a heat stream (hot or cold) in the process.
   */
  public static class HeatStream implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Name of the equipment or stream. */
    public final String name;
    /** Supply temperature in K. */
    public final double supplyTemperature;
    /** Target temperature in K. */
    public final double targetTemperature;
    /** Heat duty in W (absolute value). */
    public final double duty;
    /** Heat capacity flow rate (W/K), estimated from duty and delta T. */
    public final double heatCapacityFlowRate;
    /** Whether this is a hot stream (needs cooling). */
    public final boolean isHot;

    /**
     * Creates a heat stream.
     *
     * @param name equipment name
     * @param supplyT supply temperature in K
     * @param targetT target temperature in K
     * @param duty heat duty in W (positive)
     * @param isHot true if hot stream
     */
    public HeatStream(String name, double supplyT, double targetT, double duty, boolean isHot) {
      this.name = name;
      this.supplyTemperature = supplyT;
      this.targetTemperature = targetT;
      this.duty = Math.abs(duty);
      double deltaT = Math.abs(supplyT - targetT);
      this.heatCapacityFlowRate = deltaT > 0.01 ? this.duty / deltaT : 0.0;
      this.isHot = isHot;
    }
  }

  /**
   * Represents a suggested heat exchanger match.
   */
  public static class HeatExchangerMatch implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Hot stream name. */
    public final String hotStreamName;
    /** Cold stream name. */
    public final String coldStreamName;
    /** Recoverable duty in W. */
    public final double recoverableDuty;
    /** LMTD in K. */
    public final double lmtd;

    /**
     * Creates a heat exchanger match.
     *
     * @param hotName hot stream name
     * @param coldName cold stream name
     * @param duty recoverable duty in W
     * @param lmtd log-mean temperature difference in K
     */
    public HeatExchangerMatch(String hotName, String coldName, double duty, double lmtd) {
      this.hotStreamName = hotName;
      this.coldStreamName = coldName;
      this.recoverableDuty = duty;
      this.lmtd = lmtd;
    }
  }

  /**
   * Creates a pinch analyzer for the given process system.
   *
   * @param processSystem the process system to analyze
   */
  public PinchAnalyzer(ProcessSystem processSystem) {
    this.processSystem = processSystem;
  }

  /**
   * Sets the minimum approach temperature (delta T_min).
   *
   * @param deltaT minimum approach temperature in K (default 10.0)
   */
  public void setMinApproachTemperature(double deltaT) {
    this.minApproachTemperature = deltaT;
    this.analyzed = false;
  }

  /**
   * Gets the minimum approach temperature.
   *
   * @return minimum approach temperature in K
   */
  public double getMinApproachTemperature() {
    return minApproachTemperature;
  }

  /**
   * Manually adds a hot stream to the analysis.
   *
   * @param name stream name
   * @param supplyTempK supply temperature in K
   * @param targetTempK target temperature in K
   * @param dutyW heat duty in W
   */
  public void addHotStream(String name, double supplyTempK, double targetTempK, double dutyW) {
    hotStreams.add(new HeatStream(name, supplyTempK, targetTempK, dutyW, true));
    analyzed = false;
  }

  /**
   * Manually adds a cold stream to the analysis.
   *
   * @param name stream name
   * @param supplyTempK supply temperature in K
   * @param targetTempK target temperature in K
   * @param dutyW heat duty in W
   */
  public void addColdStream(String name, double supplyTempK, double targetTempK, double dutyW) {
    coldStreams.add(new HeatStream(name, supplyTempK, targetTempK, dutyW, false));
    analyzed = false;
  }

  /**
   * Extracts hot and cold streams automatically from the process system.
   */
  public void extractStreams() {
    hotStreams.clear();
    coldStreams.clear();

    for (ProcessEquipmentInterface equip : processSystem.getUnitOperations()) {
      if (equip instanceof Cooler) {
        Cooler cooler = (Cooler) equip;
        double duty = Math.abs(cooler.getDuty());
        if (duty > 0.0 && cooler.getInletStream() != null && cooler.getOutletStream() != null) {
          double inletT = cooler.getInletStream().getTemperature();
          double outletT = cooler.getOutletStream().getTemperature();
          if (inletT > outletT) {
            hotStreams.add(new HeatStream(cooler.getName(), inletT, outletT, duty, true));
          }
        }
      } else if (equip instanceof Heater && !(equip instanceof Cooler)
          && !(equip instanceof HeatExchanger)) {
        Heater heater = (Heater) equip;
        double duty = Math.abs(heater.getDuty());
        if (duty > 0.0 && heater.getInletStream() != null && heater.getOutletStream() != null) {
          double inletT = heater.getInletStream().getTemperature();
          double outletT = heater.getOutletStream().getTemperature();
          if (outletT > inletT) {
            coldStreams.add(new HeatStream(heater.getName(), inletT, outletT, duty, false));
          }
        }
      }
    }
    analyzed = false;
  }

  /**
   * Runs the pinch analysis. Extracts streams if none are present, then calculates composite
   * curves, pinch point, utility duties, and matching.
   */
  public void analyze() {
    if (hotStreams.isEmpty() && coldStreams.isEmpty()) {
      extractStreams();
    }

    buildCompositeCurves();
    calculatePinchAndUtilities();
    generateMatches();
    analyzed = true;
  }

  /**
   * Builds temperature-enthalpy composite curves for hot and cold streams.
   */
  private void buildCompositeCurves() {
    hotCompositeCurve.clear();
    coldCompositeCurve.clear();

    buildSingleComposite(hotStreams, hotCompositeCurve, true);
    buildSingleComposite(coldStreams, coldCompositeCurve, false);
  }

  /**
   * Builds a single composite curve from a list of heat streams.
   *
   * @param streams list of heat streams
   * @param composite output composite curve as list of [Q, T] pairs
   * @param isHot true for hot composite, false for cold composite
   */
  private void buildSingleComposite(List<HeatStream> streams, List<double[]> composite,
      boolean isHot) {
    if (streams.isEmpty()) {
      return;
    }

    // Collect all temperature intervals
    List<Double> temperatures = new ArrayList<>();
    for (HeatStream s : streams) {
      if (!temperatures.contains(s.supplyTemperature)) {
        temperatures.add(s.supplyTemperature);
      }
      if (!temperatures.contains(s.targetTemperature)) {
        temperatures.add(s.targetTemperature);
      }
    }
    Collections.sort(temperatures);

    // For hot composite, temperatures should go from high to low
    if (isHot) {
      Collections.reverse(temperatures);
    }

    double cumulativeQ = 0.0;
    composite.add(new double[] {cumulativeQ, temperatures.get(0)});

    for (int i = 0; i < temperatures.size() - 1; i++) {
      double t1 = temperatures.get(i);
      double t2 = temperatures.get(i + 1);
      double intervalDT = Math.abs(t2 - t1);

      // Sum heat capacity flow rates of streams active in this interval
      double totalCpFlow = 0.0;
      for (HeatStream s : streams) {
        double sLow = Math.min(s.supplyTemperature, s.targetTemperature);
        double sHigh = Math.max(s.supplyTemperature, s.targetTemperature);
        double iLow = Math.min(t1, t2);
        double iHigh = Math.max(t1, t2);
        if (sLow <= iLow && sHigh >= iHigh) {
          totalCpFlow += s.heatCapacityFlowRate;
        }
      }

      cumulativeQ += totalCpFlow * intervalDT;
      composite.add(new double[] {cumulativeQ, t2});
    }
  }

  /**
   * Calculates pinch temperature and minimum utility duties using the problem table algorithm.
   */
  private void calculatePinchAndUtilities() {
    if (hotStreams.isEmpty() || coldStreams.isEmpty()) {
      pinchTemperature = Double.NaN;
      minHotUtilityDuty = 0.0;
      minColdUtilityDuty = 0.0;
      return;
    }

    // Collect all temperature intervals using shifted temperatures
    List<Double> shiftedTemps = new ArrayList<>();
    for (HeatStream s : hotStreams) {
      double shifted1 = s.supplyTemperature - minApproachTemperature / 2.0;
      double shifted2 = s.targetTemperature - minApproachTemperature / 2.0;
      if (!containsDouble(shiftedTemps, shifted1)) {
        shiftedTemps.add(shifted1);
      }
      if (!containsDouble(shiftedTemps, shifted2)) {
        shiftedTemps.add(shifted2);
      }
    }
    for (HeatStream s : coldStreams) {
      double shifted1 = s.supplyTemperature + minApproachTemperature / 2.0;
      double shifted2 = s.targetTemperature + minApproachTemperature / 2.0;
      if (!containsDouble(shiftedTemps, shifted1)) {
        shiftedTemps.add(shifted1);
      }
      if (!containsDouble(shiftedTemps, shifted2)) {
        shiftedTemps.add(shifted2);
      }
    }

    Collections.sort(shiftedTemps);
    Collections.reverse(shiftedTemps); // Highest first

    // Problem table: calculate net heat deficit in each interval
    List<Double> intervalDeficits = new ArrayList<>();
    for (int i = 0; i < shiftedTemps.size() - 1; i++) {
      double tHigh = shiftedTemps.get(i);
      double tLow = shiftedTemps.get(i + 1);
      double dt = tHigh - tLow;

      double hotCpSum = 0.0;
      for (HeatStream s : hotStreams) {
        double sShiftedHigh =
            Math.max(s.supplyTemperature, s.targetTemperature) - minApproachTemperature / 2.0;
        double sShiftedLow =
            Math.min(s.supplyTemperature, s.targetTemperature) - minApproachTemperature / 2.0;
        if (sShiftedHigh >= tHigh - 0.001 && sShiftedLow <= tLow + 0.001) {
          hotCpSum += s.heatCapacityFlowRate;
        }
      }

      double coldCpSum = 0.0;
      for (HeatStream s : coldStreams) {
        double sShiftedHigh =
            Math.max(s.supplyTemperature, s.targetTemperature) + minApproachTemperature / 2.0;
        double sShiftedLow =
            Math.min(s.supplyTemperature, s.targetTemperature) + minApproachTemperature / 2.0;
        if (sShiftedHigh >= tHigh - 0.001 && sShiftedLow <= tLow + 0.001) {
          coldCpSum += s.heatCapacityFlowRate;
        }
      }

      double deficit = (coldCpSum - hotCpSum) * dt;
      intervalDeficits.add(deficit);
    }

    // Cascade: find minimum cascade heat flow to determine hot utility and pinch
    double cascadeHeat = 0.0;
    double minCascade = 0.0;
    int pinchIndex = 0;
    List<Double> cascadeValues = new ArrayList<>();
    cascadeValues.add(0.0);

    for (int i = 0; i < intervalDeficits.size(); i++) {
      cascadeHeat += intervalDeficits.get(i);
      cascadeValues.add(cascadeHeat);
      if (cascadeHeat < minCascade) {
        minCascade = cascadeHeat;
        pinchIndex = i + 1;
      }
    }

    // Minimum hot utility = |minimum cascade value|
    minHotUtilityDuty = Math.abs(minCascade);

    // Adjusted cascade (add hot utility to make all values >= 0)
    double totalHotDuty = 0.0;
    for (HeatStream s : hotStreams) {
      totalHotDuty += s.duty;
    }
    double totalColdDuty = 0.0;
    for (HeatStream s : coldStreams) {
      totalColdDuty += s.duty;
    }
    minColdUtilityDuty = totalColdDuty - totalHotDuty + minHotUtilityDuty;
    if (minColdUtilityDuty < 0.0) {
      minColdUtilityDuty = 0.0;
    }

    // Pinch temperature
    if (pinchIndex >= 0 && pinchIndex < shiftedTemps.size()) {
      pinchTemperature = shiftedTemps.get(pinchIndex);
    }

    // Build grand composite curve
    grandCompositeCurve.clear();
    double adjustedCascade = minHotUtilityDuty;
    grandCompositeCurve.add(new double[] {adjustedCascade, shiftedTemps.get(0)});
    for (int i = 0; i < intervalDeficits.size(); i++) {
      adjustedCascade -= intervalDeficits.get(i);
      grandCompositeCurve
          .add(new double[] {Math.max(0.0, adjustedCascade), shiftedTemps.get(i + 1)});
    }
  }

  /**
   * Generates heat exchanger match suggestions using the pinch design method.
   */
  private void generateMatches() {
    matches.clear();

    // Simple matching: pair hot and cold streams based on temperature overlap
    List<HeatStream> unmatchedHot = new ArrayList<>(hotStreams);
    List<HeatStream> unmatchedCold = new ArrayList<>(coldStreams);

    // Sort by duty (largest first) for greedy matching
    Collections.sort(unmatchedHot, new Comparator<HeatStream>() {
      @Override
      public int compare(HeatStream a, HeatStream b) {
        return Double.compare(b.duty, a.duty);
      }
    });

    for (HeatStream hot : unmatchedHot) {
      HeatStream bestCold = null;
      double bestLmtd = 0.0;
      double bestDuty = 0.0;

      for (HeatStream cold : unmatchedCold) {
        // Check temperature feasibility with min approach
        double hotHigh = Math.max(hot.supplyTemperature, hot.targetTemperature);
        double hotLow = Math.min(hot.supplyTemperature, hot.targetTemperature);
        double coldHigh = Math.max(cold.supplyTemperature, cold.targetTemperature);
        double coldLow = Math.min(cold.supplyTemperature, cold.targetTemperature);

        if (hotHigh - coldHigh >= minApproachTemperature
            || hotLow - coldLow >= minApproachTemperature) {
          double matchDuty = Math.min(hot.duty, cold.duty);
          double dt1 = hotHigh - coldHigh;
          double dt2 = hotLow - coldLow;

          if (dt1 > 0 && dt2 > 0) {
            double lmtd = (dt1 - dt2) / Math.log(dt1 / dt2);
            if (Math.abs(dt1 - dt2) < 0.01) {
              lmtd = dt1;
            }
            if (lmtd > bestLmtd) {
              bestLmtd = lmtd;
              bestCold = cold;
              bestDuty = matchDuty;
            }
          }
        }
      }

      if (bestCold != null && bestDuty > 0) {
        matches.add(new HeatExchangerMatch(hot.name, bestCold.name, bestDuty, bestLmtd));
        unmatchedCold.remove(bestCold);
      }
    }
  }

  /**
   * Helper to check if a double list contains a value within tolerance.
   *
   * @param list the list to search
   * @param value the value to find
   * @return true if found within 0.001
   */
  private boolean containsDouble(List<Double> list, double value) {
    for (Double d : list) {
      if (Math.abs(d - value) < 0.001) {
        return true;
      }
    }
    return false;
  }

  /**
   * Gets the pinch temperature (shifted).
   *
   * @return pinch temperature in K
   */
  public double getPinchTemperature() {
    if (!analyzed) {
      analyze();
    }
    return pinchTemperature;
  }

  /**
   * Gets the minimum hot utility duty.
   *
   * @return minimum hot utility in W
   */
  public double getMinHotUtilityDuty() {
    if (!analyzed) {
      analyze();
    }
    return minHotUtilityDuty;
  }

  /**
   * Gets the minimum cold utility duty.
   *
   * @return minimum cold utility in W
   */
  public double getMinColdUtilityDuty() {
    if (!analyzed) {
      analyze();
    }
    return minColdUtilityDuty;
  }

  /**
   * Gets the list of hot streams identified.
   *
   * @return list of hot HeatStream objects
   */
  public List<HeatStream> getHotStreams() {
    return Collections.unmodifiableList(hotStreams);
  }

  /**
   * Gets the list of cold streams identified.
   *
   * @return list of cold HeatStream objects
   */
  public List<HeatStream> getColdStreams() {
    return Collections.unmodifiableList(coldStreams);
  }

  /**
   * Gets the hot composite curve as list of [Q(W), T(K)] pairs.
   *
   * @return hot composite curve data points
   */
  public List<double[]> getHotCompositeCurve() {
    if (!analyzed) {
      analyze();
    }
    return Collections.unmodifiableList(hotCompositeCurve);
  }

  /**
   * Gets the cold composite curve as list of [Q(W), T(K)] pairs.
   *
   * @return cold composite curve data points
   */
  public List<double[]> getColdCompositeCurve() {
    if (!analyzed) {
      analyze();
    }
    return Collections.unmodifiableList(coldCompositeCurve);
  }

  /**
   * Gets the grand composite curve as list of [Q(W), T(K)] pairs.
   *
   * @return grand composite curve data points
   */
  public List<double[]> getGrandCompositeCurve() {
    if (!analyzed) {
      analyze();
    }
    return Collections.unmodifiableList(grandCompositeCurve);
  }

  /**
   * Gets the suggested heat exchanger matches.
   *
   * @return list of HeatExchangerMatch suggestions
   */
  public List<HeatExchangerMatch> getMatches() {
    if (!analyzed) {
      analyze();
    }
    return Collections.unmodifiableList(matches);
  }

  /**
   * Gets the total recoverable energy from all suggested matches.
   *
   * @return total recoverable energy in W
   */
  public double getTotalRecoverableEnergy() {
    if (!analyzed) {
      analyze();
    }
    double total = 0.0;
    for (HeatExchangerMatch m : matches) {
      total += m.recoverableDuty;
    }
    return total;
  }

  /**
   * Gets the energy recovery percentage relative to total heating/cooling duty.
   *
   * @return energy recovery as a fraction (0.0 to 1.0)
   */
  public double getEnergyRecoveryFraction() {
    if (!analyzed) {
      analyze();
    }
    double totalDuty = 0.0;
    for (HeatStream s : hotStreams) {
      totalDuty += s.duty;
    }
    for (HeatStream s : coldStreams) {
      totalDuty += s.duty;
    }
    if (totalDuty <= 0) {
      return 0.0;
    }
    return 2.0 * getTotalRecoverableEnergy() / totalDuty;
  }

  /**
   * Returns the analysis results as a JSON string.
   *
   * @return JSON representation of pinch analysis results
   */
  public String toJson() {
    if (!analyzed) {
      analyze();
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("analysisType", "Pinch Analysis (Heat Integration)");
    result.put("minApproachTemperature_K", minApproachTemperature);
    result.put("pinchTemperature_K", pinchTemperature);
    result.put("minHotUtilityDuty_kW", minHotUtilityDuty / 1000.0);
    result.put("minColdUtilityDuty_kW", minColdUtilityDuty / 1000.0);
    result.put("totalRecoverableEnergy_kW", getTotalRecoverableEnergy() / 1000.0);
    result.put("energyRecoveryFraction", getEnergyRecoveryFraction());

    List<Map<String, Object>> hotList = new ArrayList<>();
    for (HeatStream s : hotStreams) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("name", s.name);
      m.put("supplyTemperature_K", s.supplyTemperature);
      m.put("targetTemperature_K", s.targetTemperature);
      m.put("duty_kW", s.duty / 1000.0);
      m.put("heatCapacityFlowRate_WperK", s.heatCapacityFlowRate);
      hotList.add(m);
    }
    result.put("hotStreams", hotList);

    List<Map<String, Object>> coldList = new ArrayList<>();
    for (HeatStream s : coldStreams) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("name", s.name);
      m.put("supplyTemperature_K", s.supplyTemperature);
      m.put("targetTemperature_K", s.targetTemperature);
      m.put("duty_kW", s.duty / 1000.0);
      m.put("heatCapacityFlowRate_WperK", s.heatCapacityFlowRate);
      coldList.add(m);
    }
    result.put("coldStreams", coldList);

    List<Map<String, Object>> matchList = new ArrayList<>();
    for (HeatExchangerMatch m : matches) {
      Map<String, Object> mm = new LinkedHashMap<>();
      mm.put("hotStream", m.hotStreamName);
      mm.put("coldStream", m.coldStreamName);
      mm.put("recoverableDuty_kW", m.recoverableDuty / 1000.0);
      mm.put("lmtd_K", m.lmtd);
      matchList.add(mm);
    }
    result.put("heatExchangerMatches", matchList);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(result);
  }
}
