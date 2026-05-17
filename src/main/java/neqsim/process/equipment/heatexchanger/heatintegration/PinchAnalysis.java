package neqsim.process.equipment.heatexchanger.heatintegration;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.heatexchanger.MultiStreamHeatExchanger2;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;

/**
 * Pinch analysis engine for heat integration of process streams.
 *
 * <p>
 * Performs classical pinch analysis (Linnhoff method) to determine minimum heating and cooling
 * utility requirements, the pinch temperature, and composite curves for a set of hot and cold
 * process streams.
 * </p>
 *
 * <h2>Algorithm</h2>
 * <ol>
 * <li>Collect all unique temperatures from hot and cold streams</li>
 * <li>Shift cold stream temperatures up by deltaT_min (minimum approach)</li>
 * <li>Create temperature intervals and compute heat balances</li>
 * <li>Cascade heat surplus/deficit to find pinch point and utility targets</li>
 * </ol>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>
 * PinchAnalysis pinch = new PinchAnalysis(10.0);
 * pinch.addHotStream("H1", 180, 80, 30);
 * pinch.addHotStream("H2", 150, 50, 15);
 * pinch.addColdStream("C1", 30, 140, 20);
 * pinch.addColdStream("C2", 60, 120, 25);
 * pinch.run();
 *
 * double Qh = pinch.getMinimumHeatingUtility();
 * double Qc = pinch.getMinimumCoolingUtility();
 * double Tpinch = pinch.getPinchTemperatureC();
 * </pre>
 *
 * <h2>Integration with ProcessSystem</h2>
 *
 * <pre>
 * // Auto-extract heating/cooling duties from a process
 * PinchAnalysis pinch = PinchAnalysis.fromProcessSystem(process, 10.0);
 * pinch.run();
 *
 * // Or add streams from a MultiStreamHeatExchanger2
 * PinchAnalysis pinch2 = new PinchAnalysis(10.0);
 * pinch2.addStreamsFromHeatExchanger(multiStreamHX);
 * pinch2.run();
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class PinchAnalysis implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger instance. */
  private static final Logger logger = LogManager.getLogger(PinchAnalysis.class);

  private List<HeatStream> hotStreams;
  private List<HeatStream> coldStreams;
  private double deltaTmin; // K (minimum approach temperature)

  // Results
  private double minimumHeatingUtility; // kW
  private double minimumCoolingUtility; // kW
  private double pinchTemperatureHot; // Kelvin (hot side of pinch)
  private double pinchTemperatureCold; // Kelvin (cold side of pinch)
  private boolean hasRun;

  // Composite curve data
  private double[] hotCompositeQ; // kW cumulative
  private double[] hotCompositeT; // K
  private double[] coldCompositeQ; // kW cumulative
  private double[] coldCompositeT; // K

  // Grand composite curve data
  private double[] grandCompositeQ; // kW
  private double[] grandCompositeT; // K (shifted)

  /**
   * Constructor for PinchAnalysis.
   *
   * @param deltaTmin_C minimum approach temperature in Celsius (or K since it is a difference)
   */
  public PinchAnalysis(double deltaTmin_C) {
    this.deltaTmin = deltaTmin_C;
    this.hotStreams = new ArrayList<>();
    this.coldStreams = new ArrayList<>();
    this.hasRun = false;
  }

  /**
   * Add a hot stream (needs cooling).
   *
   * @param name stream name
   * @param supplyTemp_C supply temperature in Celsius
   * @param targetTemp_C target temperature in Celsius
   * @param mCp heat capacity flow rate in kW/K
   */
  public void addHotStream(String name, double supplyTemp_C, double targetTemp_C, double mCp) {
    hotStreams.add(new HeatStream(name, supplyTemp_C, targetTemp_C, mCp));
  }

  /**
   * Add a cold stream (needs heating).
   *
   * @param name stream name
   * @param supplyTemp_C supply temperature in Celsius
   * @param targetTemp_C target temperature in Celsius
   * @param mCp heat capacity flow rate in kW/K
   */
  public void addColdStream(String name, double supplyTemp_C, double targetTemp_C, double mCp) {
    coldStreams.add(new HeatStream(name, supplyTemp_C, targetTemp_C, mCp));
  }

  /**
   * Add a HeatStream object directly.
   *
   * @param stream the heat stream to add
   */
  public void addStream(HeatStream stream) {
    if (stream.getType() == HeatStream.StreamType.HOT) {
      hotStreams.add(stream);
    } else {
      coldStreams.add(stream);
    }
  }

  /**
   * Add a process stream by extracting its inlet and outlet temperatures and computing the average
   * heat capacity flow rate (MCp = duty / deltaT). The stream must have been run so that its fluid
   * properties are available.
   *
   * <p>
   * The stream is classified as hot (needs cooling) or cold (needs heating) based on whether the
   * inlet temperature is higher or lower than the specified target temperature.
   * </p>
   *
   * @param name display name for this stream in the pinch analysis
   * @param stream the NeqSim process stream (must have been run)
   * @param outletTemperature_C the outlet/target temperature in Celsius
   */
  public void addProcessStream(String name, StreamInterface stream, double outletTemperature_C) {
    if (stream == null || stream.getThermoSystem() == null) {
      throw new IllegalArgumentException("Stream '" + name + "' is null or has no fluid");
    }

    double inletTemp_C = stream.getTemperature("C");
    double massFlow_kgPerSec = stream.getThermoSystem().getFlowRate("kg/sec");
    double deltaT = Math.abs(inletTemp_C - outletTemperature_C);

    if (deltaT < 1e-6) {
      logger.warn("Stream '{}' has zero temperature change, skipping", name);
      return;
    }

    // Estimate Cp from the fluid at the inlet conditions
    SystemInterface fluid = stream.getThermoSystem();
    double cp_JperKgK;
    try {
      cp_JperKgK = fluid.getCp("J/kgK");
    } catch (Exception ex) {
      // Fall back to phase-weighted Cp
      cp_JperKgK = 0.0;
      for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
        double phaseFraction = fluid.getPhase(p).getBeta();
        double phaseCp = fluid.getPhase(p).getCp("J/kgK");
        cp_JperKgK += phaseFraction * phaseCp;
      }
    }

    if (cp_JperKgK <= 0 || Double.isNaN(cp_JperKgK)) {
      logger.warn("Stream '{}' has invalid Cp ({}), skipping", name, cp_JperKgK);
      return;
    }

    // MCp in kW/K = massFlow(kg/s) * Cp(J/kgK) / 1000
    double mCp_kWperK = massFlow_kgPerSec * cp_JperKgK / 1000.0;

    if (inletTemp_C > outletTemperature_C) {
      addHotStream(name, inletTemp_C, outletTemperature_C, mCp_kWperK);
    } else {
      addColdStream(name, outletTemperature_C, inletTemp_C, mCp_kWperK);
    }
  }

  /**
   * Add streams from a two-stream HeatExchanger. The hot side and cold side are extracted from the
   * exchanger's inlet and outlet streams.
   *
   * @param heatExchanger a two-stream HeatExchanger (must have been run)
   */
  public void addStreamsFromHeatExchanger(HeatExchanger heatExchanger) {
    if (heatExchanger == null) {
      throw new IllegalArgumentException("HeatExchanger cannot be null");
    }

    // HeatExchanger stores streams as inStream[0]/inStream[1] accessed via getInStream(i),
    // and outlet streams via getOutletStreams(). It does NOT override getInletStreams().
    List<StreamInterface> outlets = heatExchanger.getOutletStreams();
    if (outlets == null || outlets.size() < 2) {
      logger.warn("HeatExchanger '{}' does not have 2 outlet streams, skipping",
          heatExchanger.getName());
      return;
    }

    String baseName = heatExchanger.getName();

    // Stream 0 is typically the hot side, stream 1 is the cold side
    for (int i = 0; i < 2; i++) {
      StreamInterface inletStream = heatExchanger.getInStream(i);
      StreamInterface outletStream = outlets.get(i);
      if (inletStream == null || outletStream == null) {
        continue;
      }
      if (inletStream.getThermoSystem() == null || outletStream.getThermoSystem() == null) {
        continue;
      }

      double inletT_C = inletStream.getTemperature("C");
      double outletT_C = outletStream.getTemperature("C");
      double massFlow = inletStream.getThermoSystem().getFlowRate("kg/sec");
      double deltaT = Math.abs(inletT_C - outletT_C);

      if (deltaT < 1e-6 || massFlow <= 0) {
        continue;
      }

      // Compute duty and MCp
      double duty_kW = Math.abs(inletStream.getThermoSystem().getEnthalpy()
          - outletStream.getThermoSystem().getEnthalpy()) / 1000.0;
      double mCp_kWperK = duty_kW / deltaT;

      String streamName = baseName + (i == 0 ? " hot-side" : " cold-side");

      if (inletT_C > outletT_C) {
        addHotStream(streamName, inletT_C, outletT_C, mCp_kWperK);
      } else {
        addColdStream(streamName, inletT_C, outletT_C, mCp_kWperK);
      }
    }
  }

  /**
   * Add streams from a MultiStreamHeatExchanger2 (or LNGHeatExchanger). Each internal stream in the
   * exchanger is added as either a hot or cold stream based on its type classification.
   *
   * @param mshe the multi-stream heat exchanger (must have been run)
   */
  public void addStreamsFromHeatExchanger(MultiStreamHeatExchanger2 mshe) {
    if (mshe == null) {
      throw new IllegalArgumentException("MultiStreamHeatExchanger2 cannot be null");
    }

    // Iterate over streams by index; stop when we hit an out-of-bounds
    for (int i = 0; i < 100; i++) {
      StreamInterface inStream;
      StreamInterface outStream;
      try {
        inStream = mshe.getInStream(i);
        outStream = mshe.getOutStream(i);
      } catch (IndexOutOfBoundsException ex) {
        break; // no more streams
      }

      if (inStream == null || outStream == null) {
        continue;
      }
      if (inStream.getThermoSystem() == null || outStream.getThermoSystem() == null) {
        continue;
      }

      double inletT_C = mshe.getInTemperature(i);
      double outletT_C = mshe.getOutTemperature(i);
      double deltaT = Math.abs(inletT_C - outletT_C);

      if (deltaT < 1e-6) {
        continue;
      }

      // Compute duty and MCp from enthalpy difference
      double duty_kW = Math
          .abs(inStream.getThermoSystem().getEnthalpy() - outStream.getThermoSystem().getEnthalpy())
          / 1000.0;
      double mCp_kWperK = duty_kW / deltaT;

      String streamName = mshe.getName() + " stream-" + i;

      if (inletT_C > outletT_C) {
        addHotStream(streamName, inletT_C, outletT_C, mCp_kWperK);
      } else {
        addColdStream(streamName, inletT_C, outletT_C, mCp_kWperK);
      }
    }
  }

  /**
   * Create a PinchAnalysis from a ProcessSystem by scanning all Heater, Cooler, HeatExchanger, and
   * MultiStreamHeatExchanger2 equipment. Each utility (heater/cooler) is added as a stream
   * representing the process-side heating or cooling requirement.
   *
   * <p>
   * The ProcessSystem must have been run before calling this method so that all stream temperatures
   * and duties are available.
   * </p>
   *
   * @param process the ProcessSystem to scan (must have been run)
   * @param deltaTmin_C minimum approach temperature in Celsius
   * @return a new PinchAnalysis populated with streams from the process
   */
  public static PinchAnalysis fromProcessSystem(ProcessSystem process, double deltaTmin_C) {
    if (process == null) {
      throw new IllegalArgumentException("ProcessSystem cannot be null");
    }

    PinchAnalysis pinch = new PinchAnalysis(deltaTmin_C);

    for (ProcessEquipmentInterface equipment : process.getUnitOperations()) {
      if (equipment instanceof MultiStreamHeatExchanger2) {
        pinch.addStreamsFromHeatExchanger((MultiStreamHeatExchanger2) equipment);
      } else if (equipment instanceof HeatExchanger) {
        pinch.addStreamsFromHeatExchanger((HeatExchanger) equipment);
      } else if (equipment instanceof Heater) {
        // Heater and Cooler (extends Heater) represent utility streams
        Heater heater = (Heater) equipment;
        List<StreamInterface> inlets = heater.getInletStreams();
        List<StreamInterface> outlets = heater.getOutletStreams();
        if (inlets == null || outlets == null || inlets.isEmpty() || outlets.isEmpty()) {
          continue;
        }

        StreamInterface inletStream = inlets.get(0);
        StreamInterface outletStream = outlets.get(0);
        if (inletStream == null || outletStream == null) {
          continue;
        }
        if (inletStream.getThermoSystem() == null || outletStream.getThermoSystem() == null) {
          continue;
        }

        double inletT_C = inletStream.getTemperature("C");
        double outletT_C = outletStream.getTemperature("C");
        double massFlow = inletStream.getThermoSystem().getFlowRate("kg/sec");
        double deltaT = Math.abs(inletT_C - outletT_C);

        if (deltaT < 1e-6 || massFlow <= 0) {
          continue;
        }

        double duty_kW = Math.abs(inletStream.getThermoSystem().getEnthalpy()
            - outletStream.getThermoSystem().getEnthalpy()) / 1000.0;
        double mCp_kWperK = duty_kW / deltaT;

        String name = heater.getName();
        boolean isCooler = equipment instanceof Cooler || inletT_C > outletT_C;

        if (isCooler) {
          // The process side needs cooling → hot stream in pinch analysis
          pinch.addHotStream(name, inletT_C, outletT_C, mCp_kWperK);
        } else {
          // The process side needs heating → cold stream in pinch analysis
          pinch.addColdStream(name, inletT_C, outletT_C, mCp_kWperK);
        }
      }
    }

    logger.info("PinchAnalysis.fromProcessSystem: found {} hot + {} cold streams from '{}'",
        pinch.getNumberOfHotStreams(), pinch.getNumberOfColdStreams(), process.getName());

    return pinch;
  }

  /**
   * Run the pinch analysis calculation.
   */
  public void run() {
    if (hotStreams.isEmpty() && coldStreams.isEmpty()) {
      throw new IllegalStateException("No streams added to pinch analysis");
    }

    // Step 1: Collect all shifted temperature levels
    TreeSet<Double> shiftedTemps = new TreeSet<>(Collections.reverseOrder());

    for (HeatStream hs : hotStreams) {
      shiftedTemps.add(hs.getSupplyTemperatureC() - deltaTmin / 2.0);
      shiftedTemps.add(hs.getTargetTemperatureC() - deltaTmin / 2.0);
    }
    for (HeatStream cs : coldStreams) {
      shiftedTemps.add(cs.getSupplyTemperatureC() + deltaTmin / 2.0);
      shiftedTemps.add(cs.getTargetTemperatureC() + deltaTmin / 2.0);
    }

    List<Double> tempLevels = new ArrayList<>(shiftedTemps);
    int nIntervals = tempLevels.size() - 1;

    if (nIntervals <= 0) {
      minimumHeatingUtility = 0.0;
      minimumCoolingUtility = 0.0;
      pinchTemperatureHot = 0.0;
      pinchTemperatureCold = 0.0;
      hasRun = true;
      return;
    }

    // Step 2: Calculate heat balance for each interval
    double[] intervalQ = new double[nIntervals];

    for (int i = 0; i < nIntervals; i++) {
      double tHigh = tempLevels.get(i);
      double tLow = tempLevels.get(i + 1);
      double dT = tHigh - tLow;

      double sumMCpHot = 0.0;
      double sumMCpCold = 0.0;

      for (HeatStream hs : hotStreams) {
        double hsHigh = hs.getSupplyTemperatureC() - deltaTmin / 2.0;
        double hsLow = hs.getTargetTemperatureC() - deltaTmin / 2.0;
        if (hsHigh >= tHigh && hsLow <= tLow) {
          sumMCpHot += hs.getHeatCapacityFlowRate();
        }
      }

      for (HeatStream cs : coldStreams) {
        double csHigh = cs.getTargetTemperatureC() + deltaTmin / 2.0;
        double csLow = cs.getSupplyTemperatureC() + deltaTmin / 2.0;
        if (csHigh >= tHigh && csLow <= tLow) {
          sumMCpCold += cs.getHeatCapacityFlowRate();
        }
      }

      intervalQ[i] = (sumMCpHot - sumMCpCold) * dT;
    }

    // Step 3: Cascade heat — first pass (infeasible)
    double[] cascade = new double[nIntervals + 1];
    cascade[0] = 0.0;
    for (int i = 0; i < nIntervals; i++) {
      cascade[i + 1] = cascade[i] + intervalQ[i];
    }

    // Step 4: Find minimum cascade value and add hot utility
    double minCascade = Double.MAX_VALUE;
    int pinchIndex = 0;
    for (int i = 0; i <= nIntervals; i++) {
      if (cascade[i] < minCascade) {
        minCascade = cascade[i];
        pinchIndex = i;
      }
    }

    double qHotUtility = -minCascade;
    if (qHotUtility < 0) {
      qHotUtility = 0.0;
    }

    // Step 5: Feasible cascade
    double[] feasibleCascade = new double[nIntervals + 1];
    feasibleCascade[0] = qHotUtility;
    for (int i = 0; i < nIntervals; i++) {
      feasibleCascade[i + 1] = feasibleCascade[i] + intervalQ[i];
    }

    minimumHeatingUtility = qHotUtility;
    minimumCoolingUtility = feasibleCascade[nIntervals];

    // Pinch temperature
    double shiftedPinchT = tempLevels.get(pinchIndex);
    pinchTemperatureHot = shiftedPinchT + deltaTmin / 2.0 + 273.15;
    pinchTemperatureCold = shiftedPinchT - deltaTmin / 2.0 + 273.15;

    // Store grand composite curve data
    grandCompositeQ = new double[nIntervals + 1];
    grandCompositeT = new double[nIntervals + 1];
    for (int i = 0; i <= nIntervals; i++) {
      grandCompositeQ[i] = feasibleCascade[i];
      grandCompositeT[i] = (i < tempLevels.size()) ? tempLevels.get(i) + 273.15 : 0.0;
    }

    // Build composite curves
    buildCompositeCurves();

    hasRun = true;
  }

  /**
   * Build hot and cold composite curves for plotting.
   */
  private void buildCompositeCurves() {
    // Hot composite
    hotCompositeT = buildCompositeTemperatures(hotStreams, false);
    hotCompositeQ = buildCompositeEnthalpy(hotStreams, hotCompositeT, false);

    // Cold composite
    coldCompositeT = buildCompositeTemperatures(coldStreams, true);
    coldCompositeQ = buildCompositeEnthalpy(coldStreams, coldCompositeT, true);

    // Shift cold composite Q by hot utility for proper overlap
    if (coldCompositeQ != null && coldCompositeQ.length > 0) {
      for (int i = 0; i < coldCompositeQ.length; i++) {
        coldCompositeQ[i] += minimumHeatingUtility;
      }
    }
  }

  /**
   * Build sorted temperature array for composite curve.
   *
   * @param streams list of streams
   * @param isCold true if cold streams
   * @return sorted temperature array in Kelvin (descending)
   */
  private double[] buildCompositeTemperatures(List<HeatStream> streams, boolean isCold) {
    TreeSet<Double> temps = new TreeSet<>(Collections.reverseOrder());
    for (HeatStream s : streams) {
      temps.add(s.getSupplyTemperature());
      temps.add(s.getTargetTemperature());
    }
    double[] result = new double[temps.size()];
    int idx = 0;
    for (Double t : temps) {
      result[idx++] = t;
    }
    return result;
  }

  /**
   * Build cumulative enthalpy array for composite curve.
   *
   * @param streams list of streams
   * @param tempArray sorted temperature array (descending)
   * @param isCold true if cold streams
   * @return cumulative enthalpy array in kW
   */
  private double[] buildCompositeEnthalpy(List<HeatStream> streams, double[] tempArray,
      boolean isCold) {
    double[] qArray = new double[tempArray.length];
    qArray[0] = 0.0;

    for (int i = 1; i < tempArray.length; i++) {
      double tHigh = tempArray[i - 1];
      double tLow = tempArray[i];
      double dT = tHigh - tLow;

      double sumMCp = 0.0;
      for (HeatStream s : streams) {
        double sHigh = Math.max(s.getSupplyTemperature(), s.getTargetTemperature());
        double sLow = Math.min(s.getSupplyTemperature(), s.getTargetTemperature());
        if (sHigh >= tHigh && sLow <= tLow) {
          sumMCp += s.getHeatCapacityFlowRate();
        }
      }

      qArray[i] = qArray[i - 1] + sumMCp * dT;
    }
    return qArray;
  }

  /**
   * Get minimum heating utility requirement.
   *
   * @return minimum hot utility in kW
   */
  public double getMinimumHeatingUtility() {
    checkHasRun();
    return minimumHeatingUtility;
  }

  /**
   * Get minimum cooling utility requirement.
   *
   * @return minimum cold utility in kW
   */
  public double getMinimumCoolingUtility() {
    checkHasRun();
    return minimumCoolingUtility;
  }

  /**
   * Get the maximum heat recovery possible between hot and cold streams.
   *
   * @return maximum heat recovery in kW
   */
  public double getMaximumHeatRecovery() {
    checkHasRun();
    double totalHot = 0.0;
    for (HeatStream hs : hotStreams) {
      totalHot += hs.getEnthalpyChange();
    }
    return totalHot - minimumCoolingUtility;
  }

  /**
   * Get pinch temperature on the hot side in Kelvin.
   *
   * @return hot-side pinch temperature in Kelvin
   */
  public double getPinchTemperatureHot() {
    checkHasRun();
    return pinchTemperatureHot;
  }

  /**
   * Get pinch temperature on the cold side in Kelvin.
   *
   * @return cold-side pinch temperature in Kelvin
   */
  public double getPinchTemperatureCold() {
    checkHasRun();
    return pinchTemperatureCold;
  }

  /**
   * Get pinch temperature (hot side) in Celsius.
   *
   * @return hot-side pinch temperature in Celsius
   */
  public double getPinchTemperatureC() {
    return getPinchTemperatureHot() - 273.15;
  }

  /**
   * Get the hot composite curve data.
   *
   * @return map with "Q_kW" and "T_K" arrays
   */
  public Map<String, double[]> getHotCompositeCurve() {
    checkHasRun();
    Map<String, double[]> result = new HashMap<>();
    result.put("Q_kW", hotCompositeQ);
    result.put("T_K", hotCompositeT);
    return result;
  }

  /**
   * Get the cold composite curve data.
   *
   * @return map with "Q_kW" and "T_K" arrays
   */
  public Map<String, double[]> getColdCompositeCurve() {
    checkHasRun();
    Map<String, double[]> result = new HashMap<>();
    result.put("Q_kW", coldCompositeQ);
    result.put("T_K", coldCompositeT);
    return result;
  }

  /**
   * Get the grand composite curve data.
   *
   * @return map with "Q_kW" and "T_K" arrays
   */
  public Map<String, double[]> getGrandCompositeCurve() {
    checkHasRun();
    Map<String, double[]> result = new HashMap<>();
    result.put("Q_kW", grandCompositeQ);
    result.put("T_K", grandCompositeT);
    return result;
  }

  /**
   * Get the number of hot streams.
   *
   * @return number of hot streams
   */
  public int getNumberOfHotStreams() {
    return hotStreams.size();
  }

  /**
   * Get the number of cold streams.
   *
   * @return number of cold streams
   */
  public int getNumberOfColdStreams() {
    return coldStreams.size();
  }

  /**
   * Get full results as JSON string.
   *
   * @return JSON representation of pinch analysis results
   */
  public String toJson() {
    checkHasRun();
    Map<String, Object> results = new HashMap<>();
    results.put("deltaTmin_C", deltaTmin);
    results.put("minimumHeatingUtility_kW", minimumHeatingUtility);
    results.put("minimumCoolingUtility_kW", minimumCoolingUtility);
    results.put("maximumHeatRecovery_kW", getMaximumHeatRecovery());
    results.put("pinchTemperatureHot_C", pinchTemperatureHot - 273.15);
    results.put("pinchTemperatureCold_C", pinchTemperatureCold - 273.15);
    results.put("numberOfHotStreams", hotStreams.size());
    results.put("numberOfColdStreams", coldStreams.size());

    List<Map<String, Object>> streamData = new ArrayList<>();
    for (HeatStream hs : hotStreams) {
      Map<String, Object> sd = new HashMap<>();
      sd.put("name", hs.getName());
      sd.put("type", "HOT");
      sd.put("supplyTemp_C", hs.getSupplyTemperatureC());
      sd.put("targetTemp_C", hs.getTargetTemperatureC());
      sd.put("MCp_kWperK", hs.getHeatCapacityFlowRate());
      sd.put("duty_kW", hs.getEnthalpyChange());
      streamData.add(sd);
    }
    for (HeatStream cs : coldStreams) {
      Map<String, Object> sd = new HashMap<>();
      sd.put("name", cs.getName());
      sd.put("type", "COLD");
      sd.put("supplyTemp_C", cs.getSupplyTemperatureC());
      sd.put("targetTemp_C", cs.getTargetTemperatureC());
      sd.put("MCp_kWperK", cs.getHeatCapacityFlowRate());
      sd.put("duty_kW", cs.getEnthalpyChange());
      streamData.add(sd);
    }
    results.put("streams", streamData);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(results);
  }

  /**
   * Check that analysis has been run.
   *
   * @throws IllegalStateException if run() has not been called
   */
  private void checkHasRun() {
    if (!hasRun) {
      throw new IllegalStateException("Pinch analysis has not been run yet. Call run() first.");
    }
  }
}
