package neqsim.process.equipment.compressor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * CompressorChart class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class CompressorChart implements CompressorChartInterface, java.io.Serializable {
  /**
   * Generates the surge curve by taking the head value at the lowest flow for each speed from the
   * compressor chart values.
   */
  public void generateSurgeCurve() {
    int n = chartValues.size();
    java.util.TreeMap<Double, Double> uniqueSurgePoints = new java.util.TreeMap<>();
    for (int i = 0; i < n; i++) {
      CompressorCurve curve = chartValues.get(i);
      // Find index of lowest flow (usually index 0, but robust for unsorted)
      int minIdx = 0;
      for (int j = 1; j < curve.flow.length; j++) {
        if (curve.flow[j] < curve.flow[minIdx]) {
          minIdx = j;
        }
      }
      double flowVal = curve.flow[minIdx];
      double headVal = curve.head[minIdx];
      // Only add if not already present (ensures one point per speed, no duplicate flows)
      if (!uniqueSurgePoints.containsKey(flowVal)) {
        uniqueSurgePoints.put(flowVal, headVal);
      }
    }
    double[] surgeFlow = new double[uniqueSurgePoints.size()];
    double[] surgeHead = new double[uniqueSurgePoints.size()];
    int idx = 0;
    for (java.util.Map.Entry<Double, Double> entry : uniqueSurgePoints.entrySet()) {
      surgeFlow[idx] = entry.getKey();
      surgeHead[idx] = entry.getValue();
      idx++;
    }
    setSurgeCurve(new SafeSplineSurgeCurve(surgeFlow, surgeHead));
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Generates the stone wall curve by taking the head value at the highest flow for each speed from
   * the compressor chart values.
   * </p>
   */
  @Override
  public void generateStoneWallCurve() {
    int n = chartValues.size();
    TreeMap<Double, Double> uniqueStoneWallPoints = new TreeMap<>();
    for (int i = 0; i < n; i++) {
      CompressorCurve curve = chartValues.get(i);
      int maxIdx = 0;
      for (int j = 1; j < curve.flow.length; j++) {
        if (curve.flow[j] > curve.flow[maxIdx]) {
          maxIdx = j;
        }
      }
      double flowVal = curve.flow[maxIdx];
      double headVal = curve.head[maxIdx];
      if (!uniqueStoneWallPoints.containsKey(flowVal)) {
        uniqueStoneWallPoints.put(flowVal, headVal);
      }
    }
    double[] stoneFlow = new double[uniqueStoneWallPoints.size()];
    double[] stoneHead = new double[uniqueStoneWallPoints.size()];
    int idx = 0;
    for (Map.Entry<Double, Double> entry : uniqueStoneWallPoints.entrySet()) {
      stoneFlow[idx] = entry.getKey();
      stoneHead[idx] = entry.getValue();
      idx++;
    }
    setStoneWallCurve(new SafeSplineStoneWallCurve(stoneFlow, stoneHead));
  }

  /** Serialization version UID. */

  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(CompressorChart.class);

  /** Reference gas density in kg/m3 for power calculations. */
  private double referenceDensity = Double.NaN;

  /** Inlet pressure in bara for pressure ratio calculations. */
  private double inletPressure = Double.NaN;

  /** Polytropic exponent for pressure ratio calculations. */
  private double polytropicExponent = Double.NaN;

  /** Inlet temperature in Kelvin for discharge temperature calculations. */
  private double inletTemperature = Double.NaN;

  /** Heat capacity ratio (gamma = Cp/Cv) for temperature calculations. */
  private double gamma = Double.NaN;

  /** Calculated power curves [speed][points] in kW. */
  private double[][] powerCurves = null;

  /** Calculated pressure ratio curves [speed][points]. */
  private double[][] pressureRatioCurves = null;

  /** Calculated discharge temperature curves [speed][points] in Kelvin. */
  private double[][] dischargeTemperatureCurves = null;

  ArrayList<CompressorCurve> chartValues = new ArrayList<CompressorCurve>();
  ArrayList<Double> chartSpeeds = new ArrayList<Double>();
  SafeSplineSurgeCurve surgeCurve = new SafeSplineSurgeCurve();
  StoneWallCurve stoneWallCurve = new SafeSplineStoneWallCurve();
  // private SurgeCurve surgeCurve = new SurgeCurve();
  boolean isSurge = false;
  double maxSpeedCurve = 0;
  double minSpeedCurve = 1e10;
  boolean isStoneWall = false;
  double refMW;
  private String headUnit = "meter";
  private boolean useCompressorChart = false;
  double refTemperature;
  double refPressure;
  double referenceSpeed = 1000.0;
  double refZ;
  private boolean useRealKappa = false;
  double[] chartConditions = null;
  final WeightedObservedPoints reducedHeadFitter = new WeightedObservedPoints();
  final WeightedObservedPoints reducedFlowFitter = new WeightedObservedPoints();
  final WeightedObservedPoints fanLawCorrectionFitter = new WeightedObservedPoints();
  final WeightedObservedPoints reducedPolytropicEfficiencyFitter = new WeightedObservedPoints();
  PolynomialFunction reducedHeadFitterFunc = null;
  PolynomialFunction reducedPolytropicEfficiencyFunc = null;
  PolynomialFunction fanLawCorrectionFunc = null;
  double[] speed;
  double[][] flow;
  double[][] flowPolytropicEfficiency;
  double[][] head;
  double[][] polytropicEfficiency;
  double[][] redflow;
  double[][] redflowPolytropicEfficiency;
  double[][] redhead;
  double[][] redpolytropicEfficiency;

  /**
   * <p>
   * Constructor for CompressorChart.
   * </p>
   */
  public CompressorChart() {}

  /** {@inheritDoc} */
  @Override
  public void addCurve(double speed, double[] flow, double[] head, double[] polytropicEfficiency) {
    CompressorCurve curve = new CompressorCurve(speed, flow, head, flow, polytropicEfficiency);
    chartValues.add(curve);
  }

  /** {@inheritDoc} */
  @Override
  public void addCurve(double speed, double[] flow, double[] head,
      double[] flowPolytropicEfficiency, double[] polytropicEfficiency) {
    CompressorCurve curve =
        new CompressorCurve(speed, flow, head, flowPolytropicEfficiency, polytropicEfficiency);
    chartValues.add(curve);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * This method initializes the compressor performance curves, including speed, flow, head, and
   * polytropic efficiency.
   * </p>
   *
   * <p>
   * The method takes chart conditions and initializes internal variables for different performance
   * parameters based on input arrays for speed, flow, head, and polytropic efficiency. It also
   * normalizes these parameters by calculating reduced values based on speed.
   * </p>
   */
  @Override
  public void setCurves(double[] chartConditions, double[] speed, double[][] flow, double[][] head,
      double[][] polyEff) {
    this.setCurves(chartConditions, speed, flow, head, flow, polyEff);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * This method initializes the compressor performance curves, including speed, flow, head, and
   * polytropic efficiency.
   * </p>
   *
   * <p>
   * The method takes chart conditions and initializes internal variables for different performance
   * parameters based on input arrays for speed, flow, head, and polytropic efficiency. It also
   * normalizes these parameters by calculating reduced values based on speed.
   * </p>
   */
  @Override
  public void setCurves(double[] chartConditions, double[] speed, double[][] flow, double[][] head,
      double[][] flowPolyEff, double[][] polyEff) {
    this.speed = speed;
    this.head = head;
    this.polytropicEfficiency = polyEff;
    this.flow = flow;
    this.flowPolytropicEfficiency = flowPolyEff;

    // Clear existing curves and fitters before adding new ones
    chartValues.clear();
    reducedHeadFitter.clear();
    reducedPolytropicEfficiencyFitter.clear();
    fanLawCorrectionFitter.clear();
    maxSpeedCurve = 0;
    minSpeedCurve = Double.MAX_VALUE;

    // Dynamically initialize arrays based on the maximum length of flow, head, and polyEff
    int maxLength = 0;
    for (double[] f : flow) {
      if (f.length > maxLength) {
        maxLength = f.length;
      }
    }

    int maxLengthPolyEff = 0;
    for (double[] f : flowPolytropicEfficiency) {
      if (f.length > maxLengthPolyEff) {
        maxLengthPolyEff = f.length;
      }
    }

    this.redhead = new double[head.length][maxLength];
    this.redpolytropicEfficiency = new double[polyEff.length][maxLength];
    this.redflow = new double[flow.length][maxLength];
    this.redflowPolytropicEfficiency = new double[polyEff.length][maxLength];

    for (int i = 0; i < speed.length; i++) {
      if (speed[i] > maxSpeedCurve) {
        maxSpeedCurve = speed[i];
      }
      if (speed[i] < minSpeedCurve) {
        minSpeedCurve = speed[i];
      }
      CompressorCurve curve =
          new CompressorCurve(speed[i], flow[i], head[i], flowPolyEff[i], polyEff[i]);
      chartValues.add(curve);

      for (int j = 0; j < flow[i].length; j++) { // Handle differing lengths for each speed
        redflow[i][j] = flow[i][j] / speed[i];
        redhead[i][j] = head[i][j] / speed[i] / speed[i];
        reducedHeadFitter.add(redflow[i][j], redhead[i][j]);
        double flowFanLaw = flow[i][j] * speed[i] / speed[0];
        // TODO: MLLU: not correct. speed[0] should be the requested speed
        fanLawCorrectionFitter.add(speed[i] / speed[0], flow[i][j] / flowFanLaw);
      }

      for (int j = 0; j < flowPolytropicEfficiency[i].length; j++) { // Handle differing lengths for
                                                                     // each speed
        redflowPolytropicEfficiency[i][j] = flowPolyEff[i][j] / speed[i];
        redpolytropicEfficiency[i][j] = polyEff[i][j];
        reducedPolytropicEfficiencyFitter.add(redflowPolytropicEfficiency[i][j],
            redpolytropicEfficiency[i][j]);
      }

      // Fill remaining slots with default values (e.g., 0) if arrays are shorter
      for (int j = flowPolytropicEfficiency[i].length; j < maxLengthPolyEff; j++) {
        redflowPolytropicEfficiency[i][j] = 0;
        redpolytropicEfficiency[i][j] = 0;
      }
    }

    referenceSpeed = (maxSpeedCurve + minSpeedCurve) / 2.0;

    PolynomialCurveFitter fitter = PolynomialCurveFitter.create(2);

    reducedHeadFitterFunc = new PolynomialFunction(fitter.fit(reducedHeadFitter.toList()));
    reducedPolytropicEfficiencyFunc =
        new PolynomialFunction(fitter.fit(reducedPolytropicEfficiencyFitter.toList()));
    fanLawCorrectionFunc = new PolynomialFunction(fitter.fit(fanLawCorrectionFitter.toList()));
    setUseCompressorChart(true);
  }

  /**
   * <p>
   * fitReducedCurve.
   * </p>
   */
  public void fitReducedCurve() {}

  /** {@inheritDoc} */
  @Override
  public double getPolytropicHead(double flow, double speed) {
    // double flowCorrection = fanLawCorrectionFunc.value(speed/referenceSpeed);
    // System.out.println("flow correction " + flowCorrection);
    return reducedHeadFitterFunc.value(flow / speed) * speed * speed;
    // return reducedHeadFitterFunc.value(flowCorrection * flow / speed) * speed *
    // speed;
  }

  /** {@inheritDoc} */
  @Override
  public double getPolytropicEfficiency(double flow, double speed) {
    // double flowCorrection = fanLawCorrectionFunc.value(speed/referenceSpeed);
    return reducedPolytropicEfficiencyFunc.value(flow / speed);
    // return reducedPolytropicEfficiencyFunc.value(reducedHeadFitterFunc*flow /
    // speed);
  }

  /** {@inheritDoc} */
  @Override
  public int getSpeed(double flow, double head) {
    return (int) Math.round(getSpeedValue(flow, head));
  }

  /**
   * Calculate the speed required to achieve a given head at a given flow rate.
   *
   * <p>
   * This method uses fan law relationships: Head ∝ Speed². The algorithm uses a robust
   * Newton-Raphson method with:
   * <ul>
   * <li>Fan-law based initial guess for fast convergence</li>
   * <li>Bounds protection to prevent divergence</li>
   * <li>Damped updates for stability</li>
   * <li>Bisection fallback if Newton-Raphson fails</li>
   * </ul>
   *
   * <p>
   * The method works both within and outside the defined speed curve range by using the underlying
   * fan law extrapolation of the compressor map.
   * </p>
   *
   * @param flow the volumetric flow rate in m³/hr
   * @param head the required polytropic head in the chart's head unit (kJ/kg or meter)
   * @return the calculated speed in RPM (as double for precision)
   */
  public double getSpeedValue(double flow, double head) {
    // Fan law: H = f(Q/N) * N², so N = sqrt(H / f(Q/N))
    // For initial guess, use reference speed scaled by sqrt of head ratio
    double refHead = getPolytropicHead(flow, referenceSpeed);
    double initialGuess;
    if (refHead > 0 && head > 0) {
      // Fan law scaling: N2/N1 = sqrt(H2/H1) at constant Q/N
      initialGuess = referenceSpeed * Math.sqrt(head / refHead);
    } else {
      initialGuess = referenceSpeed;
    }

    // Bounds for speed search - strict enforcement at chart boundaries
    // No extrapolation beyond chart range - if required speed is outside, it's invalid
    double speedLowerBound = minSpeedCurve;
    double speedUpperBound = maxSpeedCurve;
    if (speedLowerBound <= 0) {
      speedLowerBound = 100; // Minimum reasonable speed
    }

    // Clamp initial guess to reasonable bounds
    double newspeed = Math.max(speedLowerBound, Math.min(speedUpperBound, initialGuess));

    // Newton-Raphson with damping and bounds protection
    int maxIter = 50;
    double tolerance = 1e-6;
    double dampingFactor = 0.7; // Damping to improve stability

    double oldspeed = newspeed * 1.01; // Slightly different for gradient calculation
    double oldhead = getPolytropicHead(flow, oldspeed);
    double olderror = oldhead - head;

    for (int iter = 0; iter < maxIter; iter++) {
      double newhead = getPolytropicHead(flow, newspeed);
      double error = newhead - head;

      // Check convergence
      if (Math.abs(error) < tolerance) {
        return newspeed;
      }

      // Calculate gradient (dError/dSpeed)
      double derrordspeed = (error - olderror) / (newspeed - oldspeed);

      // Protect against zero or very small gradient
      if (Math.abs(derrordspeed) < 1e-10) {
        // Use fan law derivative: dH/dN = 2*H/N
        derrordspeed = 2.0 * newhead / newspeed;
        if (Math.abs(derrordspeed) < 1e-10) {
          break; // Cannot compute gradient, exit to fallback
        }
      }

      // Calculate Newton-Raphson update with damping
      double speedUpdate = dampingFactor * error / derrordspeed;

      // Limit step size to prevent large jumps
      double maxStep = 0.3 * newspeed; // Max 30% change per iteration
      speedUpdate = Math.max(-maxStep, Math.min(maxStep, speedUpdate));

      // Store old values for next gradient calculation
      oldspeed = newspeed;
      olderror = error;

      // Update speed with bounds protection
      newspeed = newspeed - speedUpdate;
      newspeed = Math.max(speedLowerBound, Math.min(speedUpperBound, newspeed));

      // Check for stagnation
      if (Math.abs(newspeed - oldspeed) < 1e-10) {
        break;
      }
    }

    // Fallback: bisection method if Newton-Raphson didn't converge well
    double headAtLower = getPolytropicHead(flow, speedLowerBound);
    double headAtUpper = getPolytropicHead(flow, speedUpperBound);

    // Extend bounds if target head is outside current range
    if (head < headAtLower && head < headAtUpper) {
      // Need lower speed - extend lower bound
      speedLowerBound = speedLowerBound * 0.5;
      speedUpperBound = (headAtLower < headAtUpper) ? speedLowerBound * 2 : speedUpperBound;
    } else if (head > headAtLower && head > headAtUpper) {
      // Need higher speed - extend upper bound
      speedUpperBound = speedUpperBound * 2.0;
    }

    // Bisection search
    for (int iter = 0; iter < 50; iter++) {
      double midspeed = (speedLowerBound + speedUpperBound) / 2.0;
      double midhead = getPolytropicHead(flow, midspeed);

      if (Math.abs(midhead - head) < tolerance) {
        return midspeed;
      }

      // Determine which half contains the solution
      // Head increases with speed (fan law), so:
      if (midhead < head) {
        speedLowerBound = midspeed; // Need higher speed
      } else {
        speedUpperBound = midspeed; // Need lower speed
      }

      // Check convergence
      if (speedUpperBound - speedLowerBound < 1.0) {
        return midspeed;
      }
    }

    // Return best estimate
    return (speedLowerBound + speedUpperBound) / 2.0;
  }

  /** {@inheritDoc} */
  @Override
  public double getFlow(double head, double speed, double guessFlow) {
    int iter = 1;
    double error = 1.0;
    double derrordflow = 1.0;
    double newflow = guessFlow;
    double newhead = 0.0;
    double oldflow = newflow * 1.1;
    double oldhead = getPolytropicHead(oldflow, speed);
    double olderror = oldhead - head;
    do {
      iter++;
      newhead =
          getPolytropicHead(newflow, speed) / (getPolytropicEfficiency(newflow, speed) / 100.0);
      error = newhead - head;
      derrordflow = (error - olderror) / (newflow - oldflow);
      if (Math.abs(derrordflow) < 1e-10) {
        break; // Avoid division by zero
      }
      oldflow = newflow;
      olderror = error;
      newflow -= error / derrordflow;
      // Prevent negative flow during iteration
      if (newflow < 0.0) {
        newflow = guessFlow * 0.1;
      }
    } while (Math.abs(error) > 1e-6 && iter < 100);

    return Math.max(0.0, newflow);
  }

  /**
   * <p>
   * addSurgeCurve.
   * </p>
   *
   * @param flow an array of type double
   * @param head an array of type double
   */
  public void addSurgeCurve(double[] flow, double[] head) {
    // surgeCurve = new SurgeCurve(flow, head);
    surgeCurve = new SafeSplineSurgeCurve(flow, head);
  }

  /**
   * <p>
   * polytropicEfficiency.
   * </p>
   *
   * @param flow a double
   * @param speed a double
   * @return a double
   */
  public double polytropicEfficiency(double flow, double speed) {
    return 100.0;
  }

  /**
   * <p>
   * checkSurge1.
   * </p>
   *
   * @param flow a double
   * @param head a double
   * @return a boolean
   */
  public boolean checkSurge1(double flow, double head) {
    return false;
  }

  /**
   * <p>
   * checkSurge2.
   * </p>
   *
   * @param flow a double
   * @param speed a double
   * @return a boolean
   */
  public boolean checkSurge2(double flow, double speed) {
    return false;
  }

  /**
   * <p>
   * checkStoneWall.
   * </p>
   *
   * @param flow a double
   * @param speed a double
   * @return a boolean
   */
  public boolean checkStoneWall(double flow, double speed) {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void setReferenceConditions(double refMW, double refTemperature, double refPressure,
      double refZ) {
    this.refMW = refMW;
    this.refTemperature = refTemperature;
    this.refPressure = refPressure;
    this.refZ = refZ;
  }

  /** {@inheritDoc} */
  @Override
  public SafeSplineSurgeCurve getSurgeCurve() {
    return surgeCurve;
  }

  /** {@inheritDoc} */
  @Override
  public void setSurgeCurve(SafeSplineSurgeCurve surgeCurve) {
    this.surgeCurve = surgeCurve;
  }

  /** {@inheritDoc} */
  @Override
  public StoneWallCurve getStoneWallCurve() {
    return stoneWallCurve;
  }

  /** {@inheritDoc} */
  @Override
  public void setStoneWallCurve(StoneWallCurve stoneWallCurve) {
    this.stoneWallCurve = stoneWallCurve;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isUseCompressorChart() {
    return useCompressorChart;
  }

  /** {@inheritDoc} */
  @Override
  public void setUseCompressorChart(boolean useCompressorChart) {
    this.useCompressorChart = useCompressorChart;
  }

  /** {@inheritDoc} */
  @Override
  public String getHeadUnit() {
    return headUnit;
  }

  /** {@inheritDoc} */
  @Override
  public void setHeadUnit(String headUnit) {
    if (headUnit.equals("meter") || headUnit.equals("kJ/kg")) {
      this.headUnit = headUnit;
    } else {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this,
          "setHeadUnit", "headUnit", "does not support value " + headUnit));
    }
    this.headUnit = headUnit;
  }

  /** {@inheritDoc} */
  @Override
  public boolean useRealKappa() {
    return useRealKappa;
  }

  /** {@inheritDoc} */
  @Override
  public void setUseRealKappa(boolean useRealKappa) {
    this.useRealKappa = useRealKappa;
  }

  /** {@inheritDoc} */
  @Override
  public void plot() {
    neqsim.datapresentation.jfreechart.Graph2b graph =
        new neqsim.datapresentation.jfreechart.Graph2b(flow, head,
            Arrays.stream(speed).mapToObj(String::valueOf).toArray(String[]::new), "head vs flow",
            "flow", "head");
    graph.setVisible(true);
    neqsim.datapresentation.jfreechart.Graph2b graph2 =
        new neqsim.datapresentation.jfreechart.Graph2b(flow, polytropicEfficiency,
            Arrays.stream(speed).mapToObj(String::valueOf).toArray(String[]::new), "eff vs flow",
            "flow", "eff");
    graph2.setVisible(true);
    neqsim.datapresentation.jfreechart.Graph2b graph3 =
        new neqsim.datapresentation.jfreechart.Graph2b(redflow, redhead,
            Arrays.stream(speed).mapToObj(String::valueOf).toArray(String[]::new),
            "red head vs red flow", "red flow", "red head");
    graph3.setVisible(true);
    neqsim.datapresentation.jfreechart.Graph2b graph4 =
        new neqsim.datapresentation.jfreechart.Graph2b(redflow, polytropicEfficiency,
            Arrays.stream(speed).mapToObj(String::valueOf).toArray(String[]::new),
            "red eff vs red dflow", "red flow", "red eff");
    graph4.setVisible(true);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Arrays.hashCode(chartConditions);
    result = prime * result + Arrays.deepHashCode(flow);
    result = prime * result + Arrays.deepHashCode(head);
    result = prime * result + Arrays.deepHashCode(polytropicEfficiency);
    result = prime * result + Arrays.deepHashCode(redflow);
    result = prime * result + Arrays.deepHashCode(redhead);
    result = prime * result + Arrays.deepHashCode(redpolytropicEfficiency);
    result = prime * result + Arrays.hashCode(speed);
    result = prime * result + Objects.hash(chartValues, fanLawCorrectionFunc, headUnit, isStoneWall,
        isSurge, maxSpeedCurve, minSpeedCurve, reducedHeadFitterFunc,
        reducedPolytropicEfficiencyFunc, refMW, refPressure, refTemperature, refZ, referenceSpeed,
        stoneWallCurve, surgeCurve, useCompressorChart, useRealKappa);
    // result = prime * result + Objects.hash(fanLawCorrectionFitter,
    // reducedFlowFitter,reducedHeadFitter,reducedPolytropicEfficiencyFitter )
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    CompressorChart other = (CompressorChart) obj;
    return Arrays.equals(chartConditions, other.chartConditions)
        && Objects.equals(chartValues, other.chartValues)
        && Objects.equals(fanLawCorrectionFunc, other.fanLawCorrectionFunc)
        && Arrays.deepEquals(flow, other.flow) && Arrays.deepEquals(head, other.head)
        && Objects.equals(headUnit, other.headUnit) && isStoneWall == other.isStoneWall
        && isSurge == other.isSurge
        && Double.doubleToLongBits(maxSpeedCurve) == Double.doubleToLongBits(other.maxSpeedCurve)
        && Double.doubleToLongBits(minSpeedCurve) == Double.doubleToLongBits(other.minSpeedCurve)
        && Arrays.deepEquals(polytropicEfficiency, other.polytropicEfficiency)
        && Arrays.deepEquals(redflow, other.redflow) && Arrays.deepEquals(redhead, other.redhead)
        && Arrays.deepEquals(redpolytropicEfficiency, other.redpolytropicEfficiency)
        && Objects.equals(reducedHeadFitterFunc, other.reducedHeadFitterFunc)
        && Objects.equals(reducedPolytropicEfficiencyFunc, other.reducedPolytropicEfficiencyFunc)
        && Double.doubleToLongBits(refMW) == Double.doubleToLongBits(other.refMW)
        && Double.doubleToLongBits(refPressure) == Double.doubleToLongBits(other.refPressure)
        && Double.doubleToLongBits(refTemperature) == Double.doubleToLongBits(other.refTemperature)
        && Double.doubleToLongBits(refZ) == Double.doubleToLongBits(other.refZ)
        && Double.doubleToLongBits(referenceSpeed) == Double.doubleToLongBits(other.referenceSpeed)
        && Arrays.equals(speed, other.speed) && Objects.equals(stoneWallCurve, other.stoneWallCurve)
        && Objects.equals(surgeCurve, other.surgeCurve)
        && useCompressorChart == other.useCompressorChart && useRealKappa == other.useRealKappa;
    // && Objects.equals(fanLawCorrectionFitter, other.fanLawCorrectionFitter)
    // && Objects.equals(reducedFlowFitter, other.reducedFlowFitter)
    // && Objects.equals(reducedHeadFitter, other.reducedHeadFitter)
    // && Objects.equals(reducedPolytropicEfficiencyFitter,
    // other.reducedPolytropicEfficiencyFitter)
  }

  /**
   * <p>
   * Getter for the field <code>maxSpeedCurve</code>.
   * </p>
   *
   * @return a double
   */
  public double getMaxSpeedCurve() {
    return maxSpeedCurve;
  }

  /**
   * <p>
   * Setter for the field <code>maxSpeedCurve</code>.
   * </p>
   *
   * @param maxSpeedCurve a double
   */
  public void setMaxSpeedCurve(double maxSpeedCurve) {
    this.maxSpeedCurve = maxSpeedCurve;
  }

  /** {@inheritDoc} */
  @Override
  public double getMinSpeedCurve() {
    return minSpeedCurve;
  }

  /**
   * <p>
   * Setter for the field <code>minSpeedCurve</code>.
   * </p>
   *
   * @param minSpeedCurve a double
   */
  public void setMinSpeedCurve(double minSpeedCurve) {
    this.minSpeedCurve = minSpeedCurve;
  }

  /**
   * <p>
   * Get the surge flow (minimum flow) at a specific speed.
   * </p>
   * <p>
   * This method finds the compressor curve closest to the specified speed and returns the minimum
   * flow on that curve. This is useful for single speed compressors where the surge curve is not
   * active, as well as for multi-speed compressors to get the surge point at a specific speed.
   * </p>
   *
   * @param speed The compressor speed in RPM
   * @return The surge flow (minimum flow) at the specified speed in m3/hr, or Double.NaN if no
   *         curves exist
   */
  public double getSurgeFlowAtSpeed(double speed) {
    if (chartValues.isEmpty()) {
      return Double.NaN;
    }

    // Find the curve closest to the specified speed
    CompressorCurve closestCurve = chartValues.get(0);
    double minSpeedDiff = Math.abs(closestCurve.speed - speed);

    for (CompressorCurve curve : chartValues) {
      double speedDiff = Math.abs(curve.speed - speed);
      if (speedDiff < minSpeedDiff) {
        minSpeedDiff = speedDiff;
        closestCurve = curve;
      }
    }

    // Return the minimum flow on this curve (first element is typically minimum)
    double minFlow = closestCurve.flow[0];
    for (double flow : closestCurve.flow) {
      if (flow < minFlow) {
        minFlow = flow;
      }
    }
    return minFlow;
  }

  /**
   * <p>
   * Get the surge head (polytropic head at minimum flow) at a specific speed.
   * </p>
   * <p>
   * This method finds the compressor curve closest to the specified speed and returns the
   * polytropic head at the minimum flow point (surge point) on that curve.
   * </p>
   *
   * @param speed The compressor speed in RPM
   * @return The surge head at the specified speed in kJ/kg or meter (depending on headUnit), or
   *         Double.NaN if no curves exist
   */
  public double getSurgeHeadAtSpeed(double speed) {
    if (chartValues.isEmpty()) {
      return Double.NaN;
    }

    // Find the curve closest to the specified speed
    CompressorCurve closestCurve = chartValues.get(0);
    double minSpeedDiff = Math.abs(closestCurve.speed - speed);

    for (CompressorCurve curve : chartValues) {
      double speedDiff = Math.abs(curve.speed - speed);
      if (speedDiff < minSpeedDiff) {
        minSpeedDiff = speedDiff;
        closestCurve = curve;
      }
    }

    // Find the minimum flow index
    int minFlowIdx = 0;
    double minFlow = closestCurve.flow[0];
    for (int i = 1; i < closestCurve.flow.length; i++) {
      if (closestCurve.flow[i] < minFlow) {
        minFlow = closestCurve.flow[i];
        minFlowIdx = i;
      }
    }
    return closestCurve.head[minFlowIdx];
  }

  /**
   * <p>
   * Get the stone wall flow (maximum flow) at a specific speed.
   * </p>
   * <p>
   * This method finds the compressor curve closest to the specified speed and returns the maximum
   * flow on that curve (choke limit). This is useful for single speed compressors where the stone
   * wall curve is not active.
   * </p>
   *
   * @param speed The compressor speed in RPM
   * @return The stone wall flow (maximum flow) at the specified speed in m3/hr, or Double.NaN if no
   *         curves exist
   */
  public double getStoneWallFlowAtSpeed(double speed) {
    if (chartValues.isEmpty()) {
      return Double.NaN;
    }

    // Find the curve closest to the specified speed
    CompressorCurve closestCurve = chartValues.get(0);
    double minSpeedDiff = Math.abs(closestCurve.speed - speed);

    for (CompressorCurve curve : chartValues) {
      double speedDiff = Math.abs(curve.speed - speed);
      if (speedDiff < minSpeedDiff) {
        minSpeedDiff = speedDiff;
        closestCurve = curve;
      }
    }

    // Return the maximum flow on this curve (last element is typically maximum)
    double maxFlow = closestCurve.flow[0];
    for (double flow : closestCurve.flow) {
      if (flow > maxFlow) {
        maxFlow = flow;
      }
    }
    return maxFlow;
  }

  /**
   * <p>
   * Get the stone wall head (polytropic head at maximum flow) at a specific speed.
   * </p>
   * <p>
   * This method finds the compressor curve closest to the specified speed and returns the
   * polytropic head at the maximum flow point (choke limit) on that curve.
   * </p>
   *
   * @param speed The compressor speed in RPM
   * @return The stone wall head at the specified speed in kJ/kg or meter (depending on headUnit),
   *         or Double.NaN if no curves exist
   */
  public double getStoneWallHeadAtSpeed(double speed) {
    if (chartValues.isEmpty()) {
      return Double.NaN;
    }

    // Find the curve closest to the specified speed
    CompressorCurve closestCurve = chartValues.get(0);
    double minSpeedDiff = Math.abs(closestCurve.speed - speed);

    for (CompressorCurve curve : chartValues) {
      double speedDiff = Math.abs(curve.speed - speed);
      if (speedDiff < minSpeedDiff) {
        minSpeedDiff = speedDiff;
        closestCurve = curve;
      }
    }

    // Find the maximum flow index
    int maxFlowIdx = 0;
    double maxFlow = closestCurve.flow[0];
    for (int i = 1; i < closestCurve.flow.length; i++) {
      if (closestCurve.flow[i] > maxFlow) {
        maxFlow = closestCurve.flow[i];
        maxFlowIdx = i;
      }
    }
    return closestCurve.head[maxFlowIdx];
  }

  /** {@inheritDoc} */
  @Override
  public double[] getSpeeds() {
    return speed;
  }

  /** {@inheritDoc} */
  @Override
  public double[][] getFlows() {
    return flow;
  }

  /** {@inheritDoc} */
  @Override
  public double[][] getHeads() {
    return head;
  }

  /** {@inheritDoc} */
  @Override
  public double[][] getPolytropicEfficiencies() {
    return polytropicEfficiency;
  }

  /** {@inheritDoc} */
  @Override
  public double[] getChartConditions() {
    return chartConditions;
  }

  /** {@inheritDoc} */
  @Override
  public void setReferenceDensity(double density) {
    this.referenceDensity = density;
    // Recalculate power curves if data is available
    if (flow != null && head != null && polytropicEfficiency != null) {
      calculatePowerCurves();
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getReferenceDensity() {
    return referenceDensity;
  }

  /** {@inheritDoc} */
  @Override
  public void setInletPressure(double pressure) {
    this.inletPressure = pressure;
    // Recalculate pressure ratio curves if data is available
    if (flow != null && head != null && !Double.isNaN(polytropicExponent)) {
      calculatePressureRatioCurves();
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getInletPressure() {
    return inletPressure;
  }

  /** {@inheritDoc} */
  @Override
  public void setPolytropicExponent(double exponent) {
    this.polytropicExponent = exponent;
    // Recalculate pressure ratio curves if data is available
    if (flow != null && head != null && !Double.isNaN(inletPressure)) {
      calculatePressureRatioCurves();
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getPolytropicExponent() {
    return polytropicExponent;
  }

  /** {@inheritDoc} */
  @Override
  public double[][] getPowers() {
    if (powerCurves == null && flow != null) {
      calculatePowerCurves();
    }
    return powerCurves;
  }

  /** {@inheritDoc} */
  @Override
  public double[][] getPressureRatios() {
    if (pressureRatioCurves == null && flow != null) {
      calculatePressureRatioCurves();
    }
    return pressureRatioCurves;
  }

  /**
   * Calculate power curves from flow, head, and efficiency.
   *
   * <p>
   * Power is calculated as: P = (density * volumeFlow * head) / efficiency where volumeFlow is in
   * m3/hr, head is in kJ/kg (or converted from meters), and efficiency is in fraction (0-1).
   * </p>
   */
  private void calculatePowerCurves() {
    if (flow == null || head == null || polytropicEfficiency == null) {
      return;
    }

    // Use reference density if set, otherwise use a default or estimate
    double density = Double.isNaN(referenceDensity) ? 50.0 : referenceDensity; // Default ~50 kg/m3
                                                                               // for natural gas

    powerCurves = new double[flow.length][];

    for (int i = 0; i < flow.length; i++) {
      powerCurves[i] = new double[flow[i].length];
      for (int j = 0; j < flow[i].length; j++) {
        double volumeFlow = flow[i][j]; // m3/hr
        double headValue = head[i][j]; // kJ/kg or meter

        // Convert head from meters to kJ/kg if needed
        double headKJperKg = headValue;
        if ("meter".equalsIgnoreCase(headUnit) || "m".equalsIgnoreCase(headUnit)) {
          headKJperKg = headValue * 9.81 / 1000.0; // m * g / 1000 = kJ/kg
        }

        // Efficiency as fraction (input is in %)
        double effFraction = polytropicEfficiency[i][j] / 100.0;
        if (effFraction <= 0) {
          effFraction = 0.75; // Default if invalid
        }

        // Power = density * volumeFlow * head / efficiency
        // Units: kg/m3 * m3/hr * kJ/kg / 1 = kJ/hr
        // Convert to kW: kJ/hr / 3600 = kW
        double massFlow = density * volumeFlow / 3600.0; // kg/s
        powerCurves[i][j] = massFlow * headKJperKg / effFraction; // kW
      }
    }
  }

  /**
   * Calculate pressure ratio curves from head and gas properties.
   *
   * <p>
   * Pressure ratio is calculated from polytropic head using: PR = [1 + (n-1)/n * H /
   * (Z*R*T/MW)]^(n/(n-1)) Simplified: PR = exp(n/(n-1) * ln(1 + (n-1)/n * H * MW / (Z*R*T)))
   * </p>
   */
  private void calculatePressureRatioCurves() {
    if (flow == null || head == null) {
      return;
    }

    // Use reference values if available
    double n = Double.isNaN(polytropicExponent) ? 1.3 : polytropicExponent;
    double zRT_MW = 8314.0 * (refTemperature > 0 ? refTemperature : 300.0)
        / (refMW > 0 ? refMW : 20.0) * (refZ > 0 ? refZ : 0.9); // J/kg = m2/s2

    pressureRatioCurves = new double[flow.length][];

    for (int i = 0; i < flow.length; i++) {
      pressureRatioCurves[i] = new double[flow[i].length];
      for (int j = 0; j < flow[i].length; j++) {
        double headValue = head[i][j]; // kJ/kg or meter

        // Convert head to J/kg
        double headJperKg;
        if ("meter".equalsIgnoreCase(headUnit) || "m".equalsIgnoreCase(headUnit)) {
          headJperKg = headValue * 9.81; // m * g = J/kg
        } else {
          headJperKg = headValue * 1000.0; // kJ/kg * 1000 = J/kg
        }

        // Polytropic head relation: H = n/(n-1) * Z*R*T/MW * [(P2/P1)^((n-1)/n) - 1]
        // Solving for pressure ratio: PR = [1 + (n-1)/n * H / (Z*R*T/MW)]^(n/(n-1))
        double exponentRatio = (n - 1.0) / n;
        double term = 1.0 + exponentRatio * headJperKg / zRT_MW;

        if (term > 0) {
          pressureRatioCurves[i][j] = Math.pow(term, 1.0 / exponentRatio);
        } else {
          pressureRatioCurves[i][j] = 1.0; // Invalid, return unity
        }
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setInletTemperature(double temperature) {
    this.inletTemperature = temperature;
    // Recalculate discharge temperature curves if data is available
    if (flow != null && pressureRatioCurves != null) {
      calculateDischargeTemperatureCurves();
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getInletTemperature() {
    return inletTemperature;
  }

  /** {@inheritDoc} */
  @Override
  public void setGamma(double gamma) {
    this.gamma = gamma;
    // Recalculate discharge temperature curves if data is available
    if (flow != null && pressureRatioCurves != null) {
      calculateDischargeTemperatureCurves();
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getGamma() {
    return gamma;
  }

  /** {@inheritDoc} */
  @Override
  public double[][] getDischargeTemperatures() {
    if (dischargeTemperatureCurves == null && flow != null) {
      // Ensure pressure ratio curves are calculated first
      if (pressureRatioCurves == null) {
        calculatePressureRatioCurves();
      }
      calculateDischargeTemperatureCurves();
    }
    return dischargeTemperatureCurves;
  }

  /**
   * Calculate discharge temperature curves from pressure ratio and gas properties.
   *
   * <p>
   * Discharge temperature is calculated using the polytropic process relation: T2 = T1 *
   * PR^((n-1)/n) where PR is pressure ratio and n is polytropic exponent related to efficiency:
   * (n-1)/n = (gamma-1)/gamma / eta_polytropic
   * </p>
   *
   * <p>
   * This is critical for:
   * </p>
   * <ul>
   * <li>Downstream equipment design</li>
   * <li>Material temperature limits</li>
   * <li>Intercooler requirements</li>
   * </ul>
   */
  private void calculateDischargeTemperatureCurves() {
    if (flow == null || pressureRatioCurves == null) {
      return;
    }

    // Use inlet temperature if set, otherwise use reference or default
    double t1 = Double.isNaN(inletTemperature) ? (refTemperature > 0 ? refTemperature : 300.0)
        : inletTemperature;

    // Use gamma if set, otherwise estimate from polytropic exponent or default
    double k = Double.isNaN(gamma) ? 1.3 : gamma;

    dischargeTemperatureCurves = new double[flow.length][];

    for (int i = 0; i < flow.length; i++) {
      dischargeTemperatureCurves[i] = new double[flow[i].length];
      for (int j = 0; j < flow[i].length; j++) {
        double pr = pressureRatioCurves[i][j];

        // Get efficiency at this point
        double effFraction = 0.75; // Default
        if (polytropicEfficiency != null && polytropicEfficiency[i] != null
            && j < polytropicEfficiency[i].length) {
          effFraction = polytropicEfficiency[i][j] / 100.0;
          if (effFraction <= 0 || effFraction > 1.0) {
            effFraction = 0.75;
          }
        }

        // Calculate polytropic exponent from efficiency and gamma
        // (n-1)/n = (k-1)/k / eta_p
        double isentropicExponent = (k - 1.0) / k;
        double polytropicExp = isentropicExponent / effFraction;

        // T2 = T1 * PR^((n-1)/n)
        if (pr > 0) {
          dischargeTemperatureCurves[i][j] = t1 * Math.pow(pr, polytropicExp);
        } else {
          dischargeTemperatureCurves[i][j] = t1; // No compression
        }
      }
    }
  }
}
