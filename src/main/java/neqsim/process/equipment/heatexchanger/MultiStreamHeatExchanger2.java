/*
 * MultiStreamHeatExchanger2.java
 *
 * Created on [Date]
 */

package neqsim.process.equipment.heatexchanger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.util.monitor.MultiStreamHeatExchanger2Response;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

// ================================================================
// ---- UNIT INITIATION ----
// ================================================================

/**
 * <p>
 * MultiStreamHeatExchanger2 class.
 * </p>
 *
 * @author esol
 */
public class MultiStreamHeatExchanger2 extends Heater implements MultiStreamHeatExchangerInterface {
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(MultiStreamHeatExchanger2.class);

  SystemInterface thermoSystem;

  private double tolerance = 1e-3;
  private int maxIterations = 1000;
  private double jacobiDelta = 1e-4;

  /** Message used when the Jacobian is singular. */
  private static final String SINGULAR_JACOBIAN_MSG = "Jacobian determinant is zero";

  private final double extremeEnergy = 0.3;
  private final double extremeUA = 2.0;
  private final int extremeAttempts = 1000;

  private List<Double> prevOutletTemps = null;
  private int stallCounter = 0;
  private final int stallLimit = 50;
  private double localRange = 5.0;
  private double damping = 1.0;
  private Double approachTemperature = 5.0;
  private Double UA = null;

  private double hotLoad;
  private double coldLoad;
  private java.util.Map<String, java.util.List<java.util.Map<String, Object>>> compositeCurvePoints =
      new java.util.HashMap<>();
  private List<Double> hInlet = new ArrayList<>();
  private List<SystemInterface> fluidInlet = new ArrayList<>();

  private List<Double> allLoad = new ArrayList<>();
  private List<Double> hotTempAll = new ArrayList<>();
  private List<Double> coldTempAll = new ArrayList<>();
  private List<Double> tempDiff = new ArrayList<>();

  private List<StreamInterface> inStreams = new ArrayList<>();
  private List<StreamInterface> outStreams = new ArrayList<>();
  private List<String> streamTypes = new ArrayList<>();
  private List<Double> inletTemps = new ArrayList<>();
  private List<Double> outletTemps = new ArrayList<>();
  private List<Object> unknownOutlets = new ArrayList<>();
  private List<Double> pressures = new ArrayList<>();
  private List<Double> massFlows = new ArrayList<>();
  private List<Double> streamLoads = new ArrayList<>();

  /**
   * Constructor for MultiStreamHeatExchanger2.
   *
   * @param name Name of the heat exchanger
   */
  public MultiStreamHeatExchanger2(String name) {
    super(name);
  }

  /** {@inheritDoc} */
  @Override
  public void addInStream(StreamInterface inStream) {}

  /**
   * Adds an inlet stream to the multi-stream heat exchanger.
   *
   * @param inStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   * @param streamType a {@link java.lang.String} object
   * @param outletTemp a {@link java.lang.Double} object
   */
  public void addInStreamMSHE(StreamInterface inStream, String streamType, Double outletTemp) {
    this.inStreams.add(inStream);
    StreamInterface outStream = inStream.clone();
    outStreams.add(outStream);
    streamTypes.add(streamType);
    inletTemps.add(inStream.getFluid().getTemperature("C"));
    outletTemps.add(outletTemp);
    unknownOutlets.add(outletTemp == null);
    pressures.add(inStream.getFluid().getPressure("bara"));
    massFlows.add(inStream.getFlowRate("kg/sec"));
    streamLoads.add(0.0);
  }

  /**
   * <p>
   * setTemperatureApproach.
   * </p>
   *
   * @param temperatureApproach a double
   */
  public void setTemperatureApproach(double temperatureApproach) {
    this.approachTemperature = temperatureApproach;
  }

  /** {@inheritDoc} */
  @Override
  public void setUAvalue(double UAvalue) {
    this.UA = UAvalue;
  }

  // ================================================================
  // ---- RUN SOLVER SELECTION ----
  // ================================================================

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // Calculate hin for all streams and
    for (int i = 0; i < inStreams.size(); i++) {
      SystemInterface fluid = inStreams.get(i).getFluid().clone();
      fluid.initThermoProperties();
      hInlet.add(fluid.getEnthalpy("kJ/kg"));
      fluidInlet.add(fluid);
    }

    int undefinedCount = 0;
    for (Double temp : outletTemps) {
      if (temp == null) {
        undefinedCount++;
      }
    }
    if (undefinedCount == 0) {
      logger.debug("No Unknown Temperatures to Solve");
    } else if (undefinedCount == 1) {
      oneUnknown();
    } else if (undefinedCount == 2) {
      twoUnknowns();
    } else if (undefinedCount == 3) {
      threeUnknowns();
    } else {
      logger.debug("Too Many Unknown Temperatures");
    }

    for (int i = 0; i < outStreams.size(); i++) {
      outStreams.get(i).setFluid(inStreams.get(i).getFluid().clone());
      outStreams.get(i).setTemperature(outletTemps.get(i), "C");
      outStreams.get(i).setPressure(inStreams.get(i).getPressure("bara"), "bara");
      outStreams.get(i).run();
      logger.debug("Outlet temps before solving: " + outletTemps);
      logger.debug("Unknown flags: " + unknownOutlets);
    }
  }

  // ================================================================
  // ---- ONE UNKNOWN ----
  // ================================================================

  /**
   * Calculates the outlet temperatures for the heat exchanger when there is one unknown.
   */
  public void oneUnknown() {
    List<Integer> unknownIndices = new ArrayList<>();
    int idx = -1;
    for (int i = 0; i < unknownOutlets.size(); i++) {
      if ((Boolean) unknownOutlets.get(i)) {
        idx = i;
        unknownIndices.add(i);
        break;
      }
    }
    outletTemps.set(idx, initializeOutletGuess(idx));
    for (int iteration = 0; iteration < maxIterations; iteration++) {
      boolean stalled = stallDetection(unknownIndices);
      resetOfExtremesAndStalls(unknownIndices, stalled, false);
      double[] residuals = residualFunctionOneUnknown();
      if (Math.abs(residuals[0]) < tolerance) {
        return;
      }
      double[][] jacobian = numericalJacobiOneUnknown(unknownIndices);
      double[] delta;
      try {
        delta = linearSystemOneUnknown(jacobian, residuals);
      } catch (ArithmeticException e) {
        throw new RuntimeException("Jacobian is singular or poorly conditioned.");
      }

      outletTemps.set(idx, outletTemps.get(idx) - damping * delta[0]);
    }

    throw new RuntimeException("oneUnknown(): Failed to converge after maxIterations.");
  }

  private double[] residualFunctionOneUnknown() {
    return new double[] {energyDiff()};
  }

  private double[][] numericalJacobiOneUnknown(List<Integer> unknownIndices) {
    List<Double> baseTemps = new ArrayList<>();
    for (int idx : unknownIndices) {
      baseTemps.add(outletTemps.get(idx));
    }
    double[] baseResiduals = residualFunctionOneUnknown();
    List<double[]> J = new ArrayList<>();
    for (int i = 0; i < unknownIndices.size(); i++) {
      int idx = unknownIndices.get(i);
      outletTemps.set(idx, baseTemps.get(i) + jacobiDelta);
      double[] perturbed = residualFunctionOneUnknown();
      double[] column = new double[1];
      column[0] = (perturbed[0] - baseResiduals[0]) / jacobiDelta;
      J.add(column);
      outletTemps.set(idx, baseTemps.get(i));
    }
    // Transpose manually
    double[][] jacobian = new double[1][J.size()];
    for (int i = 0; i < J.size(); i++) {
      jacobian[0][i] = J.get(i)[0];
    }
    return jacobian;
  }

  private double[] linearSystemOneUnknown(double[][] A, double[] b) {
    if (Math.abs(A[0][0]) < 1e-12) {
      throw new ArithmeticException(SINGULAR_JACOBIAN_MSG);
    }
    return new double[] {b[0] / A[0][0]};
  }

  // ================================================================
  // ---- TWO UNKNOWN ----
  // ================================================================

  /**
   * Calculates the outlet temperatures for the heat exchanger when there are two unknowns.
   */
  public void twoUnknowns() {
    List<Integer> unknownIndices = new ArrayList<>();
    for (int i = 0; i < unknownOutlets.size(); i++) {
      if ((Boolean) unknownOutlets.get(i)) {
        unknownIndices.add(i);
        outletTemps.set(i, initializeOutletGuess(i));
      }
    }
    for (int iteration = 0; iteration < maxIterations; iteration++) {
      boolean stalled = stallDetection(unknownIndices);
      resetOfExtremesAndStalls(unknownIndices, stalled, false);
      double[] residuals = residualFunctionTwoUnknowns();
      if (Math.max(Math.abs(residuals[0]), Math.abs(residuals[1])) < tolerance) {
        logger.debug("✓ OK With Streams" + outletTemps);
        return;
      }
      double[][] jacobian = numericalJacobiTwoUnknowns(unknownIndices);
      double[] delta;
      try {
        delta = linearSystemTwoUnknowns(jacobian, residuals);
      } catch (ArithmeticException e) {
        throw new RuntimeException("Jacobian is singular or poorly conditioned.");
      }

      for (int i = 0; i < unknownIndices.size(); i++) {
        int idx = unknownIndices.get(i);
        outletTemps.set(idx, outletTemps.get(idx) - damping * delta[i]);
      }
    }
    throw new RuntimeException("twoUnknowns(): Failed to converge after maxIterations.");
  }

  private double[] residualFunctionTwoUnknowns() {
    return new double[] {energyDiff(), pinch() - approachTemperature};
  }

  private double[][] numericalJacobiTwoUnknowns(List<Integer> unknownIndices) {
    double[] baseTemps = new double[2];
    for (int i = 0; i < 2; i++) {
      baseTemps[i] = outletTemps.get(unknownIndices.get(i));
    }
    double[] baseResiduals = residualFunctionTwoUnknowns();
    double[][] J = new double[2][2];
    for (int i = 0; i < 2; i++) {
      int idx = unknownIndices.get(i);
      outletTemps.set(idx, baseTemps[i] + jacobiDelta);
      double[] perturbed = residualFunctionTwoUnknowns();
      for (int j = 0; j < 2; j++) {
        J[j][i] = (perturbed[j] - baseResiduals[j]) / jacobiDelta;
      }
      outletTemps.set(idx, baseTemps[i]);
    }
    return J;
  }

  private double[] linearSystemTwoUnknowns(double[][] A, double[] b) {
    double det = A[0][0] * A[1][1] - A[0][1] * A[1][0];
    if (Math.abs(det) < 1e-12) {
      // Add a small regularisation to the diagonal to avoid singular matrices
      double eps = 1e-8;
      A[0][0] += eps;
      A[1][1] += eps;
      det = A[0][0] * A[1][1] - A[0][1] * A[1][0];
      if (Math.abs(det) < 1e-12) {
        throw new ArithmeticException("Jacobian determinant is zero");
      }
    }
    double dx = b[0] * A[1][1] - b[1] * A[0][1];
    double dy = A[0][0] * b[1] - A[1][0] * b[0];
    return new double[] {dx / det, dy / det};
  }

  // ================================================================
  // ---- THREE UNKNOWN ----
  // ================================================================

  /**
   * Calculates the outlet temperatures for the heat exchanger when there are three unknowns.
   */
  public void threeUnknowns() {
    List<Integer> unknownIndices = new ArrayList<>();
    for (int i = 0; i < unknownOutlets.size(); i++) {
      if ((Boolean) unknownOutlets.get(i)) {
        unknownIndices.add(i);
        outletTemps.set(i, initializeOutletGuess(i));
      }
    }
    logger.debug("Outlet temps before solving: " + outletTemps);
    logger.debug("Unknown flags: " + unknownOutlets);
    for (int iteration = 0; iteration < maxIterations; iteration++) {
      boolean stalled = stallDetection(unknownIndices);
      resetOfExtremesAndStalls(unknownIndices, stalled, true);
      double[] residuals = residualFunctionThreeUnknowns();
      if (Math.max(Math.max(Math.abs(residuals[0]), Math.abs(residuals[1])),
          Math.abs(residuals[2])) < tolerance) {
        return;
      }

      double[][] jacobian = numericalJacobiThreeUnknowns(unknownIndices);
      double[] delta;
      try {
        delta = linearSystemThreeUnknowns(jacobian, residuals);
      } catch (ArithmeticException e) {
        throw new RuntimeException("Jacobian is singular or poorly conditioned.");
      }

      for (int i = 0; i < unknownIndices.size(); i++) {
        int idx = unknownIndices.get(i);
        outletTemps.set(idx, outletTemps.get(idx) - damping * delta[i]);
      }
    }

    throw new RuntimeException("threeUnknowns(): Failed to converge after maxIterations.");
  }

  private double[] residualFunctionThreeUnknowns() {
    return new double[] {energyDiff(), pinch() - approachTemperature, calculateUA() - UA};
  }

  private double[][] numericalJacobiThreeUnknowns(List<Integer> unknownIndices) {
    double[] baseTemps = new double[3];
    for (int i = 0; i < 3; i++) {
      baseTemps[i] = outletTemps.get(unknownIndices.get(i));
    }

    double[] baseResiduals = residualFunctionThreeUnknowns();
    double[][] J = new double[3][3];

    for (int i = 0; i < 3; i++) {
      int idx = unknownIndices.get(i);
      outletTemps.set(idx, baseTemps[i] + jacobiDelta);
      double[] perturbed = residualFunctionThreeUnknowns();
      for (int j = 0; j < 3; j++) {
        J[j][i] = (perturbed[j] - baseResiduals[j]) / jacobiDelta;
      }
      outletTemps.set(idx, baseTemps[i]);
    }

    return J;
  }

  private double[] linearSystemThreeUnknowns(double[][] A, double[] b) {
    double D = A[0][0] * (A[1][1] * A[2][2] - A[1][2] * A[2][1])
        - A[0][1] * (A[1][0] * A[2][2] - A[1][2] * A[2][0])
        + A[0][2] * (A[1][0] * A[2][1] - A[1][1] * A[2][0]);
    if (Math.abs(D) < 1e-12) {
      throw new ArithmeticException(SINGULAR_JACOBIAN_MSG);
    }

    double Dx = b[0] * (A[1][1] * A[2][2] - A[1][2] * A[2][1])
        - A[0][1] * (b[1] * A[2][2] - A[1][2] * b[2]) + A[0][2] * (b[1] * A[2][1] - A[1][1] * b[2]);

    double Dy =
        A[0][0] * (b[1] * A[2][2] - A[1][2] * b[2]) - b[0] * (A[1][0] * A[2][2] - A[1][2] * A[2][0])
            + A[0][2] * (A[1][0] * b[2] - b[1] * A[2][0]);

    double Dz =
        A[0][0] * (A[1][1] * b[2] - b[1] * A[2][1]) - A[0][1] * (A[1][0] * b[2] - b[1] * A[2][0])
            + b[0] * (A[1][0] * A[2][1] - A[1][1] * A[2][0]);

    return new double[] {Dx / D, Dy / D, Dz / D};
  }

  // ================================================================
  // ---- KEY METHODS ----
  // ================================================================

  /**
   * Calculates the energy difference between the inlet and outlet streams.
   *
   * @return a double representing the total energy difference
   */
  public double energyDiff() {
    hotLoad = 0.0;
    coldLoad = 0.0;
    for (int i = 0; i < inStreams.size(); i++) {
      double hIn = hInlet.get(i);
      double hOut = enthalpyTPFlash(i, pressures.get(i), outletTemps.get(i));
      double load = (hOut - hIn) * massFlows.get(i);
      streamLoads.set(i, load);
      if ("hot".equals(streamTypes.get(i))) {
        hotLoad += load;
      } else if ("cold".equals(streamTypes.get(i))) {
        coldLoad += load;
      }
    }
    return hotLoad + coldLoad;
  }

  /**
   * Calculates the minimum approach temperature for the heat exchanger.
   *
   * @return a double
   */
  public double pinch() {
    /* --- build the composite curves ------------------------------------ */
    compositeCurve(); // fills compositeCurvePoints

    /* --- gather every distinct load on either curve -------------------- */
    java.util.Set<Double> loadSet = new java.util.TreeSet<>();
    for (String t : new String[] {"hot", "cold"}) {
      for (Map<String, Object> p : compositeCurvePoints.get(t)) {
        loadSet.add((Double) p.get("load")); // cast because Map<String,Object>
      }
    }

    /* --- compress nearly-identical loads (<0.001 kW apart) ------------- */
    List<Double> sortedLoads = new ArrayList<>(loadSet);
    allLoad = new ArrayList<>();
    allLoad.add(sortedLoads.get(0));
    for (int i = 1; i < sortedLoads.size(); i++) {
      if (Math.abs(sortedLoads.get(i) - sortedLoads.get(i - 1)) > 1e-3) {
        allLoad.add(sortedLoads.get(i));
      }
    }

    /* --- temperatures at every common load ----------------------------- */
    hotTempAll = new ArrayList<>();
    coldTempAll = new ArrayList<>();

    for (double load : allLoad) {
      for (int j = 0; j < 2; j++) {
        String curveKey = j == 0 ? "hot" : "cold";
        List<Map<String, Object>> points = compositeCurvePoints.get(curveKey);
        List<Double> target = j == 0 ? hotTempAll : coldTempAll;

        Double exact = null;
        for (Map<String, Object> p : points) {
          if (Math.abs((Double) p.get("load") - load) < 1e-3) {
            exact = (Double) p.get("temperature");
            break;
          }
        }
        target.add(exact != null ? exact : interpolateTemperature(points, load));
      }
    }

    /* --- ΔT profile and minimum approach -------------------------------- */
    tempDiff = new ArrayList<>();
    for (int i = 0; i < hotTempAll.size(); i++) {
      tempDiff.add(hotTempAll.get(i) - coldTempAll.get(i));
    }

    double minDT = Collections.min(tempDiff);
    logger.debug("Minimum Approach Temperature = {}", minDT); // or logger.debug(...)
    return minDT;
  }

  /**
   * Calculates the overall heat transfer coefficient (UA) for the heat exchanger.
   *
   * @return the overall heat transfer coefficient (UA) value in W/K
   */
  public double calculateUA() {
    double UAvalue = 0.0;
    compositeCurve();
    pinch();

    for (int i = 1; i < allLoad.size(); i++) {
      double HIn = hotTempAll.get(i - 1);
      double CIn = coldTempAll.get(i - 1);
      double HOut = hotTempAll.get(i);
      double COut = coldTempAll.get(i);

      double deltaT1 = HIn - CIn;
      double deltaT2 = HOut - COut;
      double deltaQ = allLoad.get(i) - allLoad.get(i - 1);

      double LMTD;
      double UAinterval;

      if (Math.abs(deltaT1 - deltaT2) < 1e-4) {
        LMTD = (deltaT1 + deltaT2) / 2.0;
        UAinterval = deltaQ / LMTD;
      } else {
        LMTD = (deltaT1 - deltaT2) / Math.log(deltaT1 / deltaT2);
        if (LMTD < 0.01) {
          UAinterval = 0.0;
        } else {
          UAinterval = 1000.0 * deltaQ / LMTD; // 1000 * kW/K -> W/K
        }
      }

      UAvalue += UAinterval;
    }

    return UAvalue;
  }

  // ================================================================
  // ---- METHODS ----
  // ================================================================

  private double enthalpyTPFlash(int index, double pressure, double temperature) {
    SystemInterface thermoSystem = fluidInlet.get(index);
    thermoSystem.setPressure(pressure, "bara");
    thermoSystem.setTemperature(temperature, "C");

    ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);

    testOps.TPflash();
    thermoSystem.initThermoProperties();
    return thermoSystem.getEnthalpy("kJ/kg");
  }

  /**
   * Calculates the composite curve data for the heat exchanger.
   *
   * @return a {@link java.util.Map} object
   */
  public java.util.Map<String, java.util.List<java.util.Map<String, Object>>> compositeCurve() {
    /* fresh container ----------------------------------------------- */
    compositeCurvePoints =
        new java.util.HashMap<String, java.util.List<java.util.Map<String, Object>>>();

    /* build one curve for "hot" and one for "cold" ------------------- */
    for (String t : new String[] {"hot", "cold"}) {
      /* ---- collect every unique temperature seen on this curve ---- */
      java.util.Set<Double> tempSet = new java.util.HashSet<>();
      for (int i = 0; i < streamTypes.size(); i++) {
        if (t.equals(streamTypes.get(i))) {
          tempSet.add(inletTemps.get(i));
          tempSet.add(outletTemps.get(i));
        }
      }

      java.util.List<Double> tempPoints = new java.util.ArrayList<>(tempSet);
      java.util.Collections.sort(tempPoints); // ascending

      /* ---- find the true lowest temperature on this curve --------- */
      double minTemp = Double.MAX_VALUE; // ← FIX: no default 0 °C
      for (int i = 0; i < streamTypes.size(); i++) {
        if (t.equals(streamTypes.get(i))) {
          double candidate = t.equals("hot") ? outletTemps.get(i) // coldest point of a hot stream
              : inletTemps.get(i); // coldest point of a cold stream
          if (candidate < minTemp) {
            minTemp = candidate;
          }
        }
      }
      if (minTemp == Double.MAX_VALUE && !tempPoints.isEmpty()) {
        /* should never happen, but fall back gracefully */
        minTemp = tempPoints.get(0);
      }

      /* ---- initialise the curve with (load=0 kW, temperature=min) -- */
      double cumulativeLoad = 0.0;
      java.util.List<java.util.Map<String, Object>> curveData = new java.util.ArrayList<>();

      java.util.Map<String, Object> initialPoint = new java.util.HashMap<>();
      initialPoint.put("temperature", minTemp);
      initialPoint.put("load", 0.0);
      curveData.add(initialPoint);

      /* ---- walk each temperature interval, accumulate its load ----- */
      for (int p = 0; p < tempPoints.size() - 1; p++) {
        double tStart = tempPoints.get(p);
        double tEnd = tempPoints.get(p + 1);

        double intervalLoad = 0.0;
        for (int j = 0; j < streamTypes.size(); j++) {
          if (t.equals(streamTypes.get(j))) {
            double inlet = inletTemps.get(j);
            double outlet = outletTemps.get(j);
            double low = Math.min(inlet, outlet);
            double high = Math.max(inlet, outlet);

            /* does this stream span the entire [tStart, tEnd] window? */
            if (tStart >= low && tEnd <= high) {
              intervalLoad += intervalLoad(j, tStart, tEnd);
            }
          }
        }

        cumulativeLoad += intervalLoad;

        java.util.Map<String, Object> point = new java.util.HashMap<>();
        point.put("temperature", tEnd);
        point.put("load", cumulativeLoad);
        curveData.add(point);
      }

      /* ---- save the finished curve -------------------------------- */
      compositeCurvePoints.put(t, curveData);
    }
    return compositeCurvePoints;
  }

  private double initializeOutletGuess(int i) {
    String type = streamTypes.get(i);
    double inletTemp = inletTemps.get(i);
    if ("hot".equals(type)) {
      return inletTemp - approachTemperature;
    } else {
      return inletTemp + approachTemperature;
    }
  }

  private double intervalLoad(int i, double tempStart, double tempEnd) {
    double deltaT = Math.abs(tempEnd - tempStart);
    double fullDeltaT = Math.abs(inletTemps.get(i) - outletTemps.get(i));
    if (fullDeltaT < 1e-8) {
      return 0.0;
    }

    double interpolationFactor = deltaT / fullDeltaT;
    return Math.abs(streamLoads.get(i) * interpolationFactor);
  }

  private double calculateIntervalTemp(double loadStart, double targetLoad, double loadEnd,
      double tempStart, double tempEnd) {
    double factor = Math.abs(targetLoad - loadStart) / Math.abs(loadEnd - loadStart);
    return tempStart + (tempEnd - tempStart) * factor;
  }

  private double interpolateTemperature(List<Map<String, Object>> points, double load) {
    List<Map<String, Object>> below = new ArrayList<>();
    List<Map<String, Object>> above = new ArrayList<>();

    for (Map<String, Object> point : points) {
      double l = (Double) point.get("load");
      if (l < load) {
        below.add(point);
      }
      if (l > load) {
        above.add(point);
      }
    }

    if (below.isEmpty()) {
      return (Double) above.get(0).get("temperature");
    }
    if (above.isEmpty()) {
      return (Double) below.get(below.size() - 1).get("temperature");
    }

    double lo = (Double) below.get(below.size() - 1).get("load");
    double hi = (Double) above.get(0).get("load");

    double tLo = (Double) below.get(below.size() - 1).get("temperature");
    double tHi = (Double) above.get(0).get("temperature");

    return calculateIntervalTemp(lo, load, hi, tLo, tHi);
  }

  private void resetOfExtremesAndStalls(List<Integer> unknownIndices, boolean localMin,
      boolean UATest) {
    int attempt = 0;
    List<String> msgs = new ArrayList<>();

    while (attempt < extremeAttempts) {
      attempt++;
      msgs.clear();

      boolean directionOk = true;
      boolean energyOk = true;
      boolean heatFeasible = true;

      // 1. Check stream direction
      for (int i : unknownIndices) {
        double inT = inletTemps.get(i);
        double outT = outletTemps.get(i);
        String type = (String) streamTypes.get(i);
        if (type.equals("hot") && outT >= inT) {
          directionOk = false;
          msgs.add("hot outlet ≥ inlet");
        } else if (type.equals("cold") && outT <= inT) {
          directionOk = false;
          msgs.add("cold outlet ≤ inlet");
        }
      }

      // 2. Energy balance check (only if direction is OK)
      if (directionOk) {
        try {
          energyDiff(); // may throw
          double imbalance = Math.abs(hotLoad / coldLoad);
          energyOk = (1 - extremeEnergy) <= imbalance && imbalance <= (1 + extremeEnergy);
          if (!energyOk) {
            msgs.add(String.format("energy ratio = %.3f", imbalance));
          }
        } catch (Exception e) {
          energyOk = false;
          msgs.add("energyDiff() raised " + e.getClass().getSimpleName());
        }
      } else {
        energyOk = false;
      }

      // 3. LMTD / pinch check (only if previous two are OK)
      if (directionOk && energyOk) {
        pinch();
        for (int i = 1; i < allLoad.size(); i++) {
          double dT1 = hotTempAll.get(i - 1) - coldTempAll.get(i - 1);
          double dT2 = hotTempAll.get(i) - coldTempAll.get(i);
          if (dT1 <= tolerance || dT2 <= tolerance) {
            heatFeasible = false;
            msgs.add(
                String.format("segment %d: ΔT1=%.2f °C, ΔT2=%.2f °C (must be > 0)", i, dT1, dT2));
          }
        }
      } else {
        heatFeasible = false;
      }

      // 4. UA check (only if everything else is OK)
      boolean uaOk = true;
      if (UATest && directionOk && energyOk && heatFeasible) {
        double uaVal = calculateUA();
        double uaTol = UA * extremeUA;
        uaOk = Math.abs(uaVal - UA) < uaTol;
        if (!uaOk) {
          msgs.add(String.format("UA = %.2f (target %.2f ± %.2f)", uaVal, UA, uaTol));
        }
      } else {
        uaOk = false;
      }

      // 5. Final condition
      if (directionOk && energyOk && heatFeasible && (!UATest || uaOk) && !localMin) {
        logger.debug("✓ No reset on attempt " + attempt);
        logger.debug("With Streams " + outletTemps);
        localMin = false; // once triggered, don't persist
        return;
      } else {
        logger.debug("✗ reset on attempt " + attempt + ": " + String.join("; ", msgs));

        // Randomize outlet temps within physical bounds
        double hottestHot = Collections.max(inletTemps);
        double coldestCold = Collections.min(inletTemps);
        for (int idx : unknownIndices) {
          double inlet = inletTemps.get(idx);
          double lower;
          double upper;
          String type = (String) streamTypes.get(idx);
          if (type.equals("hot")) {
            lower = coldestCold + approachTemperature;
            upper = inlet;
          } else {
            lower = inlet;
            upper = hottestHot - approachTemperature;
          }
          double guess = lower + Math.random() * (upper - lower);
          outletTemps.set(idx, guess);
        }

        logger.debug("Outlet temps before solving: " + outletTemps);
        logger.debug("Unknown flags: " + unknownOutlets);
      }
    }

    // If all attempts fail
    throw new RuntimeException("resetOfExtremes: gave up after " + extremeAttempts
        + " attempts - last issues: " + String.join("; ", msgs));
  }

  private boolean stallDetection(List<Integer> unknownIndices) {
    if (prevOutletTemps != null) {
      double maxDelta = 0.0;
      for (int i = 0; i < unknownIndices.size(); i++) {
        int idx = unknownIndices.get(i);
        double delta = Math.abs(outletTemps.get(idx) - prevOutletTemps.get(i));
        if (delta > maxDelta) {
          maxDelta = delta;
        }
      }

      if (maxDelta <= localRange) {
        stallCounter++;
      } else {
        stallCounter = 0;
      }

      if (stallCounter >= stallLimit) {
        logger.warn("Local Minimum Detected! Stall counter reached limit.");
        stallCounter = 0;
        return true;
      }
    }

    // Update previous outlet temperatures
    prevOutletTemps = new ArrayList<>();
    for (int idx : unknownIndices) {
      prevOutletTemps.add(outletTemps.get(idx));
    }

    return false;
  }

  // ================================================================
  // ---- OUTPUT METHODS ----
  // ================================================================
  /**
   * Returns hot and cold composite curves.
   *
   * @return a {@link java.util.Map} object
   */
  public Map<String, List<Map<String, Object>>> getCompositeCurve() {
    logger.debug("Composite Curve Points: " + compositeCurve());
    return compositeCurve();
  }

  /**
   * <p>
   * getUA.
   * </p>
   *
   * @return a double
   */
  public double getUA() {
    return calculateUA();
  }

  /**
   * <p>
   * getTemperatureApproach.
   * </p>
   *
   * @return a double
   */
  public double getTemperatureApproach() {
    return pinch();
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getOutStream(int i) {
    if (i < 0 || i >= outStreams.size()) {
      throw new IndexOutOfBoundsException("Invalid outStream index: " + i);
    }
    return outStreams.get(i);
  }

  // ================================================================
  // ---- MANDATORY UNUSED ----
  // ================================================================
  /** {@inheritDoc} */
  @Override
  public StreamInterface getInStream(int i) {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public double getInTemperature(int i) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double getOutTemperature(int i) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double getDuty() {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {}

  /** {@inheritDoc} */
  @Override
  public void runConditionAnalysis() {}

  /** {@inheritDoc} */
  @Override
  public double getGuessOutTemperature() {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public void setGuessOutTemperature(double temp) {}

  /** {@inheritDoc} */
  @Override
  public void setGuessOutTemperature(double temp, String unit) {}

  /** {@inheritDoc} */
  @Override
  public void setFlowArrangement(String arrangement) {}

  /** {@inheritDoc} */
  @Override
  public String getFlowArrangement() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public void setThermalEffectiveness(double effectiveness) {}

  /** {@inheritDoc} */
  @Override
  public double getThermalEffectiveness() {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double getHotColdDutyBalance() {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public void setHotColdDutyBalance(double value) {}

  /** {@inheritDoc} */
  @Override
  public double calcThermalEffectiveness(double NTU, double Cr) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public void setUseDeltaT(boolean use) {}

  /** {@inheritDoc} */
  @Override
  public void setFeedStream(int index, StreamInterface stream) {}

  /** {@inheritDoc} */
  @Override
  public void setDeltaT(double dT) {}

  /** {@inheritDoc} */
  @Override
  public double getDeltaT() {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double getUAvalue() {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(new MultiStreamHeatExchanger2Response(this));
  }
}
