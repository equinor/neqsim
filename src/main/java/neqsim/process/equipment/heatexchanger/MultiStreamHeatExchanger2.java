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
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;


// ================================================================
// ---- UNIT INITIATION ----
// ================================================================

/**
 * @param name MSHE2
 */
public class MultiStreamHeatExchanger2 extends Heater implements MultiStreamHeatExchangerInterface {
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(MultiStreamHeatExchanger2.class);

  SystemInterface thermoSystem;

  private double tolerance = 1e-3;
  private int maxIterations = 50000;
  private double jacobiDelta = 1e-4;

  private final double extremeEnergy = 0.3;
  private final double extremeUA = 2.0;
  private final int extremeAttempts = 20;

  private double damping = 0.5;

  private Double approachTemperature = null;
  private Double UA = null;

  private double hotLoad;
  private double coldLoad;
  private java.util.Map<String, java.util.List<java.util.Map<String, Object>>> compositeCurvePoints =
      new java.util.HashMap<>();
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
  public void addInStream(StreamInterface inStream) {}

  /** {@inheritDoc} */
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

  public void setTemperatureApproach(double temperatureApproach) {
    this.approachTemperature = temperatureApproach;
  }

  public void setUAvalue(double UAvalue) {
    this.UA = UAvalue;
  }

  // ================================================================
  // ---- RUN SOLVER SELECTION ----
  // ================================================================

  /** {@inheritDoc} */
  public void run(UUID id) {
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

  /** {@inheritDoc} */
  public void oneUnknown() {
    List<Integer> unknownIndices = new ArrayList<>();
    int idx = -1;
    for (int i = 0; i < unknownOutlets.size(); i++) {
      if ((Boolean) unknownOutlets.get(i)) {
        idx = i;
        break;
      }
    }
    outletTemps.set(idx, initializeOutletGuess(idx));
    for (int iteration = 0; iteration < maxIterations; iteration++) {
      resetOfExtremesOneAndTwoUnknowns(unknownIndices);
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
    return new double[] {b[0] / A[0][0]};
  }

  // ================================================================
  // ---- TWO UNKNOWN ----
  // ================================================================

  /** {@inheritDoc} */
  public void twoUnknowns() {
    List<Integer> unknownIndices = new ArrayList<>();
    for (int i = 0; i < unknownOutlets.size(); i++) {
      if ((Boolean) unknownOutlets.get(i)) {
        unknownIndices.add(i);
        outletTemps.set(i, initializeOutletGuess(i));
      }
    }

    for (int iteration = 0; iteration < maxIterations; iteration++) {
      logger.debug("Before reset inletTemps:" + inletTemps + "outletTemps:" + outletTemps);
      resetOfExtremesOneAndTwoUnknowns(unknownIndices);
      logger.debug(("After reset inletTemps:" + inletTemps + "outletTemps:" + outletTemps));

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
    double dx = b[0] * A[1][1] - b[1] * A[0][1];
    double dy = A[0][0] * b[1] - A[1][0] * b[0];
    return new double[] {dx / det, dy / det};
  }


  private void resetOfExtremesOneAndTwoUnknowns(List<Integer> unknownIndices) {
    int attempt = 0;
    List<String> msgs = new ArrayList<>();
    while (attempt < extremeAttempts) {
      attempt++;
      msgs.clear();

      boolean energyOk = false;
      try {
        energyDiff();
        double imbalance = Math.abs(hotLoad / coldLoad);
        energyOk = (1 - extremeEnergy) <= imbalance && imbalance <= (1 + extremeEnergy);
        if (!energyOk) {
          msgs.add(String.format("energy ratio = %.3f", imbalance));
        }
      } catch (Exception e) {
        msgs.add("energyDiff() raised " + e.getClass().getSimpleName());
      }

      boolean lmtdOk = true;
      double pinchVal = pinch();
      for (int i = 1; i < allLoad.size(); i++) {
        double H_in = hotTempAll.get(i - 1);
        double C_in = coldTempAll.get(i - 1);
        double H_out = hotTempAll.get(i);
        double C_out = coldTempAll.get(i);
        double dT1 = H_in - C_in;
        double dT2 = H_out - C_out;
        if (dT1 <= tolerance || dT2 <= tolerance) {
          lmtdOk = false;
          msgs.add(
              String.format("segment %d: ΔT1=%.2f °C, ΔT2=%.2f °C (must be > 0)", i, dT1, dT2));
        }
      }

      boolean directionOk = true;
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


      if (energyOk && lmtdOk && directionOk) {
        logger.debug("✓ No reset on attempt " + attempt);
        logger.debug("With Streams " + outletTemps);

        return;
      } else {
        logger.debug("✗ reset on attempt " + attempt + ": "
            + String.join("; ", msgs + "outlet streams" + outletTemps));

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
      }
    }


    throw new RuntimeException("resetOfExtremes: gave up after " + extremeAttempts
        + " attempts – last issues: " + String.join("; ", msgs));
  }

  // ================================================================
  // ---- THREE UNKNOWN ----
  // ================================================================

  /** {@inheritDoc} */
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
      resetOfExtremesThreeUnknowns(unknownIndices);
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


  private void resetOfExtremesThreeUnknowns(List<Integer> unknownIndices) {
    int attempt = 0;
    List<String> msgs = new ArrayList<>();

    while (attempt < extremeAttempts) {
      attempt++;
      msgs.clear();

      boolean energyOk = false;
      try {
        energyDiff();
        double imbalance = Math.abs(hotLoad / coldLoad);
        energyOk = (1 - extremeEnergy) <= imbalance && imbalance <= (1 + extremeEnergy);
        if (!energyOk)
          msgs.add(String.format("energy ratio = %.3f", imbalance));
      } catch (Exception e) {
        msgs.add("energyDiff() raised " + e.getClass().getSimpleName());
      }
      // Energy method works

      boolean lmtdOk = true;
      double pinchVal = pinch();
      for (int i = 1; i < allLoad.size(); i++) {
        double dT1 = hotTempAll.get(i - 1) - coldTempAll.get(i - 1);
        double dT2 = hotTempAll.get(i) - coldTempAll.get(i);
        if (dT1 <= tolerance || dT2 <= tolerance) {
          lmtdOk = false;
          msgs.add(
              String.format("segment %d: ΔT1=%.2f °C, ΔT2=%.2f °C (must be > 0)", i, dT1, dT2));
        }
      }

      boolean directionOk = true;
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

      boolean uaOk = false;
      double uaVal = calculateUA();
      double uaTol = UA * extremeUA;
      uaOk = Math.abs(uaVal - UA) < uaTol;
      if (!uaOk) {
        msgs.add(String.format("UA = %.2f (target %.2f ± %.2f)", uaVal, UA, uaTol));
      }

      if (energyOk && lmtdOk && directionOk && uaOk) {
        logger.debug("✓ No reset on attempt " + attempt);
        logger.debug("With Streams " + outletTemps);
        return;
      } else {
        logger.debug("✗ reset on attempt " + attempt + ": " + String.join("; ", msgs));

        double hottestHot = Collections.max(inletTemps);
        double coldestCold = Collections.min(inletTemps);
        for (int idx : unknownIndices) {
          double inlet = inletTemps.get(idx);
          double lower, upper;
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

    throw new RuntimeException("resetOfExtremes: gave up after " + extremeAttempts
        + " attempts – last issues: " + String.join("; ", msgs));
  }


  // ================================================================
  // ---- KEY METHODS ----
  // ================================================================

  /** {@inheritDoc} */
  public double energyDiff() {
    hotLoad = 0.0;
    coldLoad = 0.0;
    for (int i = 0; i < inStreams.size(); i++) {
      double hIn = enthalpyTPFlash(i, pressures.get(i), inletTemps.get(i));
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
  
  /** {@inheritDoc} */
  public double pinch() {
    /* --- build the composite curves ------------------------------------ */
    compositeCurve();                                   // fills compositeCurvePoints

    /* --- gather every distinct load on either curve -------------------- */
    java.util.Set<Double> loadSet = new java.util.TreeSet<>();
    for (String t : new String[] { "hot", "cold" }) {
      for (Map<String, Object> p : compositeCurvePoints.get(t)) {
        loadSet.add((Double) p.get("load"));            // cast because Map<String,Object>
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
    hotTempAll  = new ArrayList<>();
    coldTempAll = new ArrayList<>();

    for (double load : allLoad) {
      for (int j = 0; j < 2; j++) {
        String                     curveKey = j == 0 ? "hot"  : "cold";
        List<Map<String, Object>>  points   = compositeCurvePoints.get(curveKey);
        List<Double>               target   = j == 0 ? hotTempAll : coldTempAll;

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
    logger.debug("Minimum Approach Temperature = {}", minDT);  // or logger.debug(...)
    return minDT;
  }



  /** {@inheritDoc} */
  public double calculateUA() {
    double UAvalue = 0.0;
    energyDiff();
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
  // ---- HELPER METHODS ----
  // ================================================================


  private double enthalpyTPFlash(int index, double pressure, double temperature) {
    StreamInterface stream = (StreamInterface) inStreams.get(index);

    thermoSystem = stream.getThermoSystem().clone();
    thermoSystem.setPressure(pressure, "bara");
    thermoSystem.setTemperature(temperature, "C");

    ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);

    testOps.TPflash();
    thermoSystem.initThermoProperties();
    return thermoSystem.getEnthalpy("kJ/kg");
  }
  
  /** {@inheritDoc} */
  public void compositeCurve() {

    /* fresh container ----------------------------------------------- */
    compositeCurvePoints =
        new java.util.HashMap<String, java.util.List<java.util.Map<String, Object>>>();

    /* build one curve for "hot" and one for "cold" ------------------- */
    for (String t : new String[] { "hot", "cold" }) {

      /* ---- collect every unique temperature seen on this curve ---- */
      java.util.Set<Double> tempSet = new java.util.HashSet<>();
      for (int i = 0; i < streamTypes.size(); i++) {
        if (t.equals(streamTypes.get(i))) {
          tempSet.add(inletTemps.get(i));
          tempSet.add(outletTemps.get(i));
        }
      }

      java.util.List<Double> tempPoints = new java.util.ArrayList<>(tempSet);
      java.util.Collections.sort(tempPoints);                 // ascending

      /* ---- find the true lowest temperature on this curve --------- */
      double minTemp = Double.MAX_VALUE;                       // ← FIX: no default 0 °C
      for (int i = 0; i < streamTypes.size(); i++) {
        if (t.equals(streamTypes.get(i))) {
          double candidate =
              t.equals("hot") ? outletTemps.get(i)   // coldest point of a hot stream
                              : inletTemps.get(i);   // coldest point of a cold stream
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
      java.util.List<java.util.Map<String, Object>> curveData =
          new java.util.ArrayList<>();

      java.util.Map<String, Object> initialPoint = new java.util.HashMap<>();
      initialPoint.put("temperature", minTemp);
      initialPoint.put("load", 0.0);
      curveData.add(initialPoint);

      /* ---- walk each temperature interval, accumulate its load ----- */
      for (int p = 0; p < tempPoints.size() - 1; p++) {
        double tStart = tempPoints.get(p);
        double tEnd   = tempPoints.get(p + 1);

        double intervalLoad = 0.0;
        for (int j = 0; j < streamTypes.size(); j++) {
          if (t.equals(streamTypes.get(j))) {
            double inlet  = inletTemps.get(j);
            double outlet = outletTemps.get(j);
            double low    = Math.min(inlet, outlet);
            double high   = Math.max(inlet, outlet);

            /* does this stream span the entire [tStart, tEnd] window? */
            if (tStart >= low && tEnd <= high) {
              intervalLoad += intervalLoad(j, tStart, tEnd);
            }
          }
        }

        cumulativeLoad += intervalLoad;

        java.util.Map<String, Object> point = new java.util.HashMap<>();
        point.put("temperature", tEnd);
        point.put("load",        cumulativeLoad);
        curveData.add(point);
      }

      /* ---- save the finished curve -------------------------------- */
      compositeCurvePoints.put(t, curveData);
    }
  }




  private double initializeOutletGuess(int i) {
    String type = (String) streamTypes.get(i);
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
    if (fullDeltaT < 1e-8)
      return 0.0;

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
      if (l < load)
        below.add(point);
      if (l > load)
        above.add(point);
    }

    if (below.isEmpty())
      return (Double) above.get(0).get("temperature");
    if (above.isEmpty())
      return (Double) below.get(below.size() - 1).get("temperature");

    double lo = (Double) below.get(below.size() - 1).get("load");
    double hi = (Double) above.get(0).get("load");

    double tLo = (Double) below.get(below.size() - 1).get("temperature");
    double tHi = (Double) above.get(0).get("temperature");

    return calculateIntervalTemp(lo, load, hi, tLo, tHi);
  }


  // ================================================================
  // ---- OUTPUT METHODS ----
  // ================================================================

  /** {@inheritDoc} */
  public void getCompositeCurve() {

    System.setProperty("java.awt.headless", "true");                      // ✅ 1.00 - Always safe and necessary in headless environments
    System.setProperty("sun.java2d.fontpath", "");                        // ✅ 1.00 - Prevents broken font config access

    logger.debug("Generating composite curve data...");            // ✅ 1.00 - Basic logging

    compositeCurve();                                                    // ⚠️ 2.00 - Assumes proper stream data; medium risk if not set up

    List<Map<String, Object>> hot = compositeCurvePoints.get("hot");     // ✅ 1.00 - Standard map access
    List<Map<String, Object>> cold = compositeCurvePoints.get("cold");   // ✅ 1.00

    if (hot == null || cold == null || hot.isEmpty() || cold.isEmpty()) {// ✅ 1.00 - Simple sanity check
      System.err.println("❌ Composite curve data is missing or incomplete."); // ✅ 1.00
      return;                                                            // ✅ 1.00
    }

    XYSeries hotSeries = new XYSeries("Hot Composite Curve");            // ✅ 1.00 - Constructor
    for (Map<String, Object> point : hot) {                              // ✅ 1.00 - Loop itself is safe
      try {
        double load = (Double) point.get("load");                        // ⚠️ 2.00 - Cast risk, but caught
        double temp = (Double) point.get("temperature");                // ⚠️ 2.00
        hotSeries.add(load, temp);                                      // ✅ 1.00
      } catch (Exception e) {
        System.err.println("⚠️ Invalid hot point: " + point);           // ✅ 1.00
      }
    }

    XYSeries coldSeries = new XYSeries("Cold Composite Curve");          // ✅ 1.00
    for (Map<String, Object> point : cold) {                             // ✅ 1.00
      try {
        double load = (Double) point.get("load");                        // ⚠️ 2.00
        double temp = (Double) point.get("temperature");                // ⚠️ 2.00
        coldSeries.add(load, temp);                                     // ✅ 1.00
      } catch (Exception e) {
        System.err.println("⚠️ Invalid cold point: " + point);          // ✅ 1.00
      }
    }

    XYSeriesCollection dataset = new XYSeriesCollection();               // ✅ 1.00
    dataset.addSeries(hotSeries);                                       // ✅ 1.00
    dataset.addSeries(coldSeries);                                      // ✅ 1.00

    JFreeChart chart = ChartFactory.createXYLineChart(                  // ⚠️ 2.00 - Can fail on null or invalid dataset
        "Composite Curves",
        "Cumulative Heat Load (kW)",
        "Temperature (°C)",
        dataset
    );

    java.awt.Font safeFont = new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 12);     // ✅ 1.00 - Safe fallback font
    chart.getTitle().setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 14));    // ✅ 1.00
    chart.getXYPlot().getDomainAxis().setLabelFont(safeFont);                             // ✅ 1.00
    chart.getXYPlot().getDomainAxis().setTickLabelFont(safeFont);                         // ✅ 1.00
    chart.getXYPlot().getRangeAxis().setLabelFont(safeFont);                              // ✅ 1.00
    chart.getXYPlot().getRangeAxis().setTickLabelFont(safeFont);                          // ✅ 1.00

    XYPlot plot = chart.getXYPlot();                                 // ✅ 1.00
    plot.setDomainGridlinesVisible(true);                            // ✅ 1.00
    plot.setRangeGridlinesVisible(true);                             // ✅ 1.00

    try {
      java.io.File dir = new java.io.File("output");                // ✅ 1.00
      if (!dir.exists()) dir.mkdirs();                              // ✅ 1.00
      java.io.File file = new java.io.File(dir, "composite_curves.png"); // ✅ 1.00
      ChartUtils.saveChartAsPNG(file, chart, 900, 600);             // ⚠️ 2.00 - Only medium risk now (headless-safe, file-safe)
      logger.debug("✅ Chart saved at: " + file.getAbsolutePath()); // ✅ 1.00
    } catch (Exception e) {
      System.err.println("❌ Could not save chart: " + e.getMessage()); // ✅ 1.00
      e.printStackTrace();                                            // ✅ 1.00
    }

  }




  public void getStreamCurve() {
    energyDiff();
    compositeCurve();

    logger.debug("\nIndividual Stream Temperature Profiles:");
    for (int i = 0; i < inStreams.size(); i++) {
      String type = (String) streamTypes.get(i);
      double Tin = inletTemps.get(i);
      double Tout = outletTemps.get(i);
      double load = streamLoads.get(i);
      logger.debug("Stream %d (%s): Inlet = %.2f°C, Outlet = %.2f°C, Load = %.2f kW%n", i + 1,
          type, Tin, Tout, load);
    }
  }


  public void getPrintStreams() {
    logger.debug("\nStream Summary");
    logger.debug("%-15s %-6s %-16s %-17s %-19s %-19s %-19s%n", "Name", "Type", "Inlet (°C)",
        "Outlet (°C)", "Pressure (bara)", "Flow (kg/sec)", "Heat Load (kW)");
    logger.debug(
        "-------------------------------------------------------------------------------------------------------------");

    for (int i = 0; i < outStreams.size(); i++) {
      StreamInterface stream = (StreamInterface) outStreams.get(i);
      String name = stream.getName();
      String type = (String) streamTypes.get(i);
      double inlet = inletTemps.get(i);
      double outlet = outletTemps.get(i);
      double pressure = pressures.get(i);
      double flow = massFlows.get(i);
      double load = streamLoads.get(i);

      logger.debug("%-15s %-6s %-16.4f %-17.4f %-19.4f %-19.4f %-19.4f%n", name, type, inlet,
          outlet, pressure, flow, load);
    }
  }

  public double getUA() {
    return calculateUA();
  }


  public void getEnergyBalance() {
    double enDiff = energyDiff();
    logger.debug("Relative Error = %.5f kW%n", enDiff);
  }

  public double getTemperatureApproach() {
    return pinch();
  }

  @Override
  public StreamInterface getOutStream(int i) {
    if (i < 0 || i >= outStreams.size()) {
      throw new IndexOutOfBoundsException("Invalid outStream index: " + i);
    }
    return (StreamInterface) outStreams.get(i);
  }


  // ================================================================
  // ---- MANDITORY UNUSED ----
  // ================================================================

  @Override
  public StreamInterface getInStream(int i) {
    return null;
  }

  @Override
  public double getInTemperature(int i) {
    return 0.0;
  }

  @Override
  public double getOutTemperature(int i) {
    return 0.0;
  }

  @Override
  public double getDuty() {
    return 0.0;
  }

  @Override
  public void displayResult() {}

  @Override
  public void runConditionAnalysis() {}

  @Override
  public double getGuessOutTemperature() {
    return 0.0;
  }

  @Override
  public void setGuessOutTemperature(double temp) {}

  @Override
  public void setGuessOutTemperature(double temp, String unit) {}

  @Override
  public void setFlowArrangement(String arrangement) {}

  @Override
  public String getFlowArrangement() {
    return null;
  }

  @Override
  public void setThermalEffectiveness(double effectiveness) {}

  @Override
  public double getThermalEffectiveness() {
    return 0.0;
  }

  @Override
  public double getHotColdDutyBalance() {
    return 0.0;
  }

  @Override
  public void setHotColdDutyBalance(double value) {}

  @Override
  public double calcThermalEffectiveness(double NTU, double Cr) {
    return 0.0;
  }

  @Override
  public void setUseDeltaT(boolean use) {}

  @Override
  public void setFeedStream(int index, StreamInterface stream) {}

  @Override
  public void setDeltaT(double dT) {}

  @Override
  public double getDeltaT() {
    return 0.0;
  }

  @Override
  public double getUAvalue() {
    return 0.0;
  }
}
