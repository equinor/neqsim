package neqsim.process.equipment.compressor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * * CompressorChartKader2015 is a class that implements the compressor chart calculations based on
 * the Kader 2015 method. It extends the CompressorChartAlternativeMapLookupExtrapolate class and
 * provides methods to set compressor curves based on speed, flow, head, and efficiency values. See:
 * https://github.com/EvenSol/NeqSim-Colab/discussions/12
 */
public class CompressorChartKhader2015 extends CompressorChartAlternativeMapLookupExtrapolate {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(CompressorChartKhader2015.class);
  SystemInterface fluid = null;
  SystemInterface ref_fluid = null;
  StreamInterface stream = null;
  private double impellerOuterDiameter = 0.3;

  /**
   * Constructs a CompressorChartKhader2015 object with the specified fluid and impeller diameter.
   *
   * @param fluid the working fluid for the compressor
   * @param impellerdiam the outer diameter of the impeller
   */
  public CompressorChartKhader2015(SystemInterface fluid, double impellerdiam) {
    super();
    this.fluid = fluid;
    this.impellerOuterDiameter = impellerdiam;
  }

  /**
   * Constructs a CompressorChartKhader2015 object with the specified fluid and impeller diameter.
   *
   * @param fluid the working fluid for the compressor
   * @param referenceFluid the referenceFluid for the compressorcurve
   * @param impellerdiam the outer diameter of the impeller
   */
  public CompressorChartKhader2015(SystemInterface fluid, SystemInterface referenceFluid,
      double impellerdiam) {
    super();
    this.fluid = fluid;
    this.ref_fluid = referenceFluid;
    this.impellerOuterDiameter = impellerdiam;
  }

  /**
   * Constructs a CompressorChartKhader2015 object with the specified stream and impeller diameter.
   *
   * @param stream the stream for the compressor
   * @param impellerdiam the outer diameter of the impeller
   */
  public CompressorChartKhader2015(StreamInterface stream, double impellerdiam) {
    super();
    this.stream = stream;
    this.impellerOuterDiameter = impellerdiam;
  }

  /**
   * Sets the compressor curves based on the provided chart conditions, speed, flow, head,
   * flowPolytrpicEfficiency and polytropic efficiency values.
   *
   * <p>
   * <b>Mathematical background (see Kader 2015):</b><br>
   * The method normalizes compressor map data using the following relations:
   * <ul>
   * <li><b>Corrected Head Factor:</b> H<sub>corr</sub> = H / c<sub>s</sub><sup>2</sup></li>
   * <li><b>Corrected Flow Factor:</b> Q<sub>corr</sub> = Q / (c<sub>s</sub> D<sup>2</sup>)</li>
   * <li><b>Corrected Flow Factor for Efficiency:</b> Q<sub>corr,eff</sub> = Q<sub>eff</sub> /
   * (c<sub>s</sub> D<sup>2</sup>)</li>
   * <li><b>Polytropic Efficiency:</b> &eta;<sub>p</sub> = &eta;<sub>p</sub></li>
   * <li><b>Machine Mach Number:</b> Ma = N D / c<sub>s</sub></li>
   * </ul>
   * where:
   * <ul>
   * <li>H = head</li>
   * <li>Q = flow</li>
   * <li>Q<sub>eff</sub> = flow for efficiency</li>
   * <li>N = speed</li>
   * <li>D = impeller outer diameter</li>
   * <li>c<sub>s</sub> = sound speed of the fluid</li>
   * </ul>
   * These dimensionless numbers allow for comparison and extrapolation of compressor performance
   * across different conditions, as described in Kader (2015) and the referenced NeqSim discussion.
   *
   * @param chartConditions array with temperature, pressure, (optionally density, molecular weight)
   * @param speed array of speeds
   * @param flow 2D array of flows
   * @param head 2D array of heads
   * @param flowPolyEff 2D array of flows for efficiency
   * @param polyEff 2D array of polytropic efficiencies
   */
  public void setCurves(double[] chartConditions, double[] speed, double[][] flow, double[][] head,
      double[][] flowPolyEff, double[][] polyEff) {
    if (fluid == null && stream != null) {
      fluid = stream.getFluid();
    }
    if (ref_fluid == null) {
      ref_fluid = createDefaultFluid(chartConditions);
    }
    // ref_fluid = createDe
    ref_fluid.setTemperature(chartConditions[0], "C");
    ref_fluid.setPressure(chartConditions[1], "bara");
    ThermodynamicOperations ops = new ThermodynamicOperations(ref_fluid);
    ops.TPflash();
    ref_fluid.initThermoProperties();

    double fluidSoundSpeed = ref_fluid.getPhase(0).getSoundSpeed();

    for (int i = 0; i < speed.length; i++) {
      double[] machNumberCorrectedHeadFactor = new double[flow[i].length];
      double[] machNumberCorrectedFlowFactor = new double[flow[i].length];
      double[] machNumberCorrectedFlowFactorEfficiency = new double[flow[i].length];
      double[] polEff = new double[flow[i].length];
      for (int j = 0; j < flow[i].length; j++) {
        machNumberCorrectedHeadFactor[j] = head[i][j] / fluidSoundSpeed / fluidSoundSpeed;
        machNumberCorrectedFlowFactor[j] =
            flow[i][j] / 3600.0 / fluidSoundSpeed / impellerOuterDiameter / impellerOuterDiameter;
        machNumberCorrectedFlowFactorEfficiency[j] =
            flowPolyEff[i][j] / fluidSoundSpeed / impellerOuterDiameter / impellerOuterDiameter;
        polEff[j] = polyEff[i][j];
      }
      double machineMachNumber = speed[i] / 60.0 * impellerOuterDiameter / fluidSoundSpeed;

      CompressorCurve curve = new CompressorCurve(machineMachNumber, machNumberCorrectedFlowFactor,
          machNumberCorrectedHeadFactor, machNumberCorrectedFlowFactorEfficiency, polEff);
      chartValues.add(curve);
      chartSpeeds.add(speed[i]);
    }

    setUseCompressorChart(true);
  }

  /**
   * Returns a list of corrected compressor curves (dimensionless) based on the provided chart
   * conditions and map data. Each CorrectedCurve contains the machine Mach number, corrected flow
   * factor, corrected head factor, corrected flow factor for efficiency, and polytropic efficiency
   * arrays for each speed.
   *
   * @param chartConditions array with temperature, pressure, (optionally density, molecular weight)
   * @param speed array of speeds
   * @param flow 2D array of flows
   * @param head 2D array of heads
   * @param flowPolyEff 2D array of flows for efficiency
   * @param polyEff 2D array of polytropic efficiencies
   * @return List of CorrectedCurve objects containing dimensionless map data
   */
  public java.util.List<CorrectedCurve> getCorrectedCurves(double[] chartConditions, double[] speed,
      double[][] flow, double[][] head, double[][] flowPolyEff, double[][] polyEff) {
    if (ref_fluid == null) {
      ref_fluid = createDefaultFluid(chartConditions);
    }
    ref_fluid.setTemperature(chartConditions[0], "C");
    ref_fluid.setPressure(chartConditions[1], "bara");
    ThermodynamicOperations ops = new ThermodynamicOperations(ref_fluid);
    ops.TPflash();
    ref_fluid.initThermoProperties();
    double fluidSoundSpeed = ref_fluid.getPhase(0).getSoundSpeed();
    java.util.List<CorrectedCurve> correctedCurves = new java.util.ArrayList<>();
    for (int i = 0; i < speed.length; i++) {
      double[] machNumberCorrectedHeadFactor = new double[flow[i].length];
      double[] machNumberCorrectedFlowFactor = new double[flow[i].length];
      double[] machNumberCorrectedFlowFactorEfficiency = new double[flow[i].length];
      double[] polEff = new double[flow[i].length];
      for (int j = 0; j < flow[i].length; j++) {
        machNumberCorrectedHeadFactor[j] = head[i][j] / fluidSoundSpeed / fluidSoundSpeed;
        machNumberCorrectedFlowFactor[j] =
            flow[i][j] / 3600.0 / fluidSoundSpeed / impellerOuterDiameter / impellerOuterDiameter;
        machNumberCorrectedFlowFactorEfficiency[j] =
            flowPolyEff[i][j] / fluidSoundSpeed / impellerOuterDiameter / impellerOuterDiameter;
        polEff[j] = polyEff[i][j];
      }
      double machineMachNumber = speed[i] / 60.0 * impellerOuterDiameter / fluidSoundSpeed;
      correctedCurves.add(new CorrectedCurve(machineMachNumber, machNumberCorrectedFlowFactor,
          machNumberCorrectedHeadFactor, machNumberCorrectedFlowFactorEfficiency, polEff));
    }
    return correctedCurves;
  }

  /**
   * Converts a list of dimensionless (corrected) compressor curves to real (physical units) curves
   * for a given fluid and speeds.
   *
   * @return List of RealCurve objects containing physical units map data
   */
  public java.util.List<RealCurve> getRealCurvesForFluid() {
    double fluidSoundSpeed = fluid.getSoundSpeed();
    // System.out.println("Sound speed of actual fluid: " + fluidSoundSpeed + " m/s");
    java.util.List<RealCurve> realCurves = new java.util.ArrayList<>();
    for (int i = 0; i < chartValues.size(); i++) {
      CompressorCurve corr = chartValues.get(i);
      double[] flow = new double[corr.flow.length];
      double[] head = new double[corr.head.length];
      double[] flowPolyEff = new double[corr.flowPolytropicEfficiency.length];
      double[] polEff = new double[corr.polytropicEfficiency.length];
      for (int j = 0; j < flow.length; j++) {
        flow[j] =
            3600.0 * corr.flow[j] * fluidSoundSpeed * impellerOuterDiameter * impellerOuterDiameter;
        head[j] = corr.head[j] * fluidSoundSpeed * fluidSoundSpeed;
        flowPolyEff[j] = 3600.0 * corr.flowPolytropicEfficiency[j] * fluidSoundSpeed
            * impellerOuterDiameter * impellerOuterDiameter;
        polEff[j] = corr.polytropicEfficiency[j];
      }
      realCurves.add(new RealCurve(chartSpeeds.get(i), flow, head, flowPolyEff, polEff));
    }
    return realCurves;
  }

  /**
   * Pretty print all RealCurve objects for the current fluid.
   */
  public void prettyPrintRealCurvesForFluid() {
    java.util.List<RealCurve> realCurves = getRealCurvesForFluid();
    System.out.println("All RealCurve objects for current fluid:");
    for (RealCurve curve : realCurves) {
      System.out.println("RealCurve:");
      System.out.println("  Speed: " + curve.speed);
      System.out.println("  Flow: " + java.util.Arrays.toString(curve.flow));
      System.out.println("  Head: " + java.util.Arrays.toString(curve.head));
      System.out.println("  Flow Poly Eff: " + java.util.Arrays.toString(curve.flowPolyEff));
      System.out.println(
          "  Polytropic Efficiency: " + java.util.Arrays.toString(curve.polytropicEfficiency));
    }
  }

  /**
   * Creates and initializes a default fluid for compressor chart calculations.
   *
   * @param chartConditions array with temperature, pressure, (optionally density, molecular weight)
   * @return the sound speed of the fluid
   */
  private SystemInterface createDefaultFluid(double[] chartConditions) {
    // Set moles so that the molecular weight matches chartConditions[3] (if
    // provided), by varying
    // propane
    double methaneFrac = 0.90;
    double ethaneFrac = 0.05;
    double propaneFrac = 0.05;
    double targetMolWeight = (chartConditions.length > 3) ? chartConditions[3] : -1.0;

    // Molar masses [g/mol]
    double mwMethane = 16.043;
    double mwEthane = 30.07;
    double mwPropane = 44.097;

    double x1 = methaneFrac;
    double x2 = ethaneFrac;
    double x3 = 1.0 - x1 - x2;

    // Scale all to match targetMolWeight
    if (targetMolWeight > 0.0) {
      // Scale all fractions proportionally while preserving ratios between methane/ethane
      // Let x1 = a * methaneFrac, x2 = a * ethaneFrac, x3 = 1 - x1 - x2
      double a = (targetMolWeight - mwPropane)
          / (methaneFrac * (mwMethane - mwPropane) + ethaneFrac * (mwEthane - mwPropane));
      x1 = a * methaneFrac;
      x2 = a * ethaneFrac;
      x3 = 1.0 - x1 - x2;
      // If x3 < 0, something is off
      if (x3 < 0.0 || x3 > 1.0) {
        logger.warn("Target molecular weight not achievable with given component ratios.");
        x3 = Math.max(0.0, Math.min(1.0, x3));
        double total = x1 + x2 + x3;
        x1 /= total;
        x2 /= total;
        x3 /= total;
      }
      methaneFrac = x1;
      ethaneFrac = x2;
      propaneFrac = x3;
    }
    SystemInterface localfluid = null;
    try {
      localfluid = (SystemInterface) fluid.getClass().getConstructor().newInstance();
    } catch (Exception e) {
      logger.error("Error creating fluid instance: ", e);
      throw new RuntimeException("Failed to create fluid instance", e);
    }
    localfluid.addComponent("methane", methaneFrac);
    localfluid.addComponent("ethane", ethaneFrac);
    localfluid.addComponent("propane", propaneFrac);
    localfluid.setMixingRule("classic");
    localfluid.init(0);
    localfluid.setTemperature(chartConditions[0], "C");
    localfluid.setPressure(chartConditions[1], "bara");
    ThermodynamicOperations ops = new ThermodynamicOperations(localfluid);
    ops.TPflash();
    localfluid.initThermoProperties();
    // System.out.println(
    // "Sound speed of refernece fluid: " + localfluid.getPhase(0).getSoundSpeed() + " m/s");
    // localfluid.prettyPrint();
    return localfluid;
  }

  /**
   * Calculates the polytropic head for a given flow and speed.
   *
   * <p>
   * The method first converts the input flow and speed to dimensionless numbers using the sound
   * speed and impeller diameter:
   * <ul>
   * <li><b>Corrected Flow Factor:</b> Q<sub>corr</sub> = Q / (c<sub>s</sub> D<sup>2</sup>)</li>
   * <li><b>Machine Mach Number:</b> Ma = N D / c<sub>s</sub></li>
   * </ul>
   * It then interpolates/extrapolates the polytropic head from the reference compressor curves in
   * this dimensionless space, and finally converts the result back to physical units by multiplying
   * with c<sub>s</sub><sup>2</sup>.
   *
   * @param flow volumetric flow rate
   * @param speed rotational speed
   * @return polytropic head in physical units
   */
  @Override
  public double getPolytropicHead(double flow, double speed) {
    if (fluid == null) {
      fluid = stream.getFluid();
    }
    // System.out.println("Sound speed of actiual fluid: " + fluid.getSoundSpeed() + " m/s");
    double machNumberCorrectedFlowFactor =
        flow / 3600.0 / fluid.getSoundSpeed() / impellerOuterDiameter / impellerOuterDiameter;
    double machineMachNumber = speed / 60 * impellerOuterDiameter / fluid.getSoundSpeed();
    // System.out.println("mac numer corrected flow factor: " + machNumberCorrectedFlowFactor
    // + " machine Mach number: " + machineMachNumber + " impeller diameter: "
    // + impellerOuterDiameter);
    return super.getPolytropicHead(machNumberCorrectedFlowFactor, machineMachNumber)
        * fluid.getSoundSpeed() * fluid.getSoundSpeed();
  }

  /**
   * Calculates the polytropic efficiency for a given flow and speed.
   *
   * <p>
   * The method first converts the input flow and speed to dimensionless numbers using the sound
   * speed and impeller diameter:
   * <ul>
   * <li><b>Corrected Flow Factor:</b> Q<sub>corr</sub> = Q / (c<sub>s</sub> D<sup>2</sup>)</li>
   * <li><b>Machine Mach Number:</b> Ma = N D / c<sub>s</sub></li>
   * </ul>
   * It then interpolates/extrapolates the polytropic efficiency from the reference compressor
   * curves in this dimensionless space.
   *
   * @param flow volumetric flow rate
   * @param speed rotational speed
   * @return polytropic efficiency (dimensionless)
   */
  @Override
  public double getPolytropicEfficiency(double flow, double speed) {
    if (fluid == null) {
      fluid = stream.getFluid();
    }
    double machNumberCorrectedFlowFactor =
        flow / 3600.0 / fluid.getSoundSpeed() / impellerOuterDiameter / impellerOuterDiameter;
    double machineMachNumber = speed / 60 * impellerOuterDiameter / fluid.getSoundSpeed();
    return super.getPolytropicEfficiency(machNumberCorrectedFlowFactor, machineMachNumber);
  }


  /**
   * Returns the outer diameter of the impeller.
   *
   * @return the impeller outer diameter
   */
  public double getImpellerOuterDiameter() {
    return impellerOuterDiameter;
  }

  /**
   * Sets the outer diameter of the impeller.
   *
   * @param impellerOuterDiameter the new outer diameter of the impeller unit meters
   */
  public void setImpellerOuterDiameter(double impellerOuterDiameter) {
    this.impellerOuterDiameter = impellerOuterDiameter;
  }

  public SystemInterface getReferenceFluid() {
    return ref_fluid;
  }

  public void setReferenceFluid(SystemInterface ref_fluid) {
    this.ref_fluid = ref_fluid;
  }

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
   * Simple POJO to hold corrected (dimensionless) compressor curve data for a given speed.
   */
  public static class CorrectedCurve {
    public final double machineMachNumber;
    public final double[] correctedFlowFactor;
    public final double[] correctedHeadFactor;
    public final double[] correctedFlowFactorEfficiency;
    public final double[] polytropicEfficiency;

    /**
     * Constructs a CorrectedCurve object with the specified dimensionless compressor map data.
     *
     * @param machineMachNumber the machine Mach number
     * @param correctedFlowFactor array of corrected flow factors
     * @param correctedHeadFactor array of corrected head factors
     * @param correctedFlowFactorEfficiency array of corrected flow factors for efficiency
     * @param polytropicEfficiency array of polytropic efficiencies
     */
    public CorrectedCurve(double machineMachNumber, double[] correctedFlowFactor,
        double[] correctedHeadFactor, double[] correctedFlowFactorEfficiency,
        double[] polytropicEfficiency) {
      this.machineMachNumber = machineMachNumber;
      this.correctedFlowFactor = correctedFlowFactor;
      this.correctedHeadFactor = correctedHeadFactor;
      this.correctedFlowFactorEfficiency = correctedFlowFactorEfficiency;
      this.polytropicEfficiency = polytropicEfficiency;
    }
  }

  /**
   * Simple POJO to hold real (physical units) compressor curve data for a given speed.
   */
  public static class RealCurve {
    public final double speed;
    public final double[] flow;
    public final double[] head;
    public final double[] flowPolyEff;
    public final double[] polytropicEfficiency;

    /**
     * Constructs a RealCurve object with the specified physical units compressor map data.
     *
     * @param speed the rotational speed
     * @param flow array of flow values
     * @param head array of head values
     * @param flowPolyEff array of flow values for efficiency
     * @param polytropicEfficiency array of polytropic efficiencies
     */
    public RealCurve(double speed, double[] flow, double[] head, double[] flowPolyEff,
        double[] polytropicEfficiency) {
      this.speed = speed;
      this.flow = flow;
      this.head = head;
      this.flowPolyEff = flowPolyEff;
      this.polytropicEfficiency = polytropicEfficiency;
    }
  }
}
