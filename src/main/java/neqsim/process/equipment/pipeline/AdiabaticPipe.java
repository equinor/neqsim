package neqsim.process.equipment.pipeline;

import java.util.UUID;
import com.google.gson.GsonBuilder;
import neqsim.fluidmechanics.flowsystem.FlowSystemInterface;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Single-phase adiabatic pipe model.
 *
 * <p>
 * This class models a simple adiabatic (no heat transfer) pipe for single-phase flow using basic
 * gas flow equations. It calculates pressure drop from friction and elevation changes.
 * </p>
 *
 * <h2>Calculation Modes</h2>
 * <ul>
 * <li><b>Calculate outlet pressure</b> - Given inlet conditions and flow rate</li>
 * <li><b>Calculate flow rate</b> - Given inlet and outlet pressures (when outlet pressure is
 * set)</li>
 * </ul>
 *
 * <p>
 * The pipeline implements CapacityConstrainedEquipment (inherited from Pipeline) with constraints:
 * </p>
 * <ul>
 * <li>Velocity - SOFT limit based on erosional velocity</li>
 * <li>LOF (Likelihood of Failure) - SOFT limit for flow-induced vibration</li>
 * <li>FRMS - SOFT limit for flow-induced vibration intensity</li>
 * <li>Volume flow - DESIGN limit from mechanical design</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * // Create gas stream
 * SystemInterface gas = new SystemSrkEos(278.15, 220.0);
 * gas.addComponent("methane", 24.0, "MSm^3/day");
 * gas.setMixingRule("classic");
 *
 * Stream inlet = new Stream("inlet", gas);
 * inlet.run();
 *
 * // Create adiabatic pipe
 * AdiabaticPipe pipe = new AdiabaticPipe("pipeline", inlet);
 * pipe.setLength(700000.0); // 700 km
 * pipe.setDiameter(0.7112); // ~28 inches
 * pipe.setPipeWallRoughness(5e-6);
 * pipe.run();
 *
 * System.out.println("Outlet pressure: " + pipe.getOutletPressure("bara") + " bara");
 * }</pre>
 *
 * @author Even Solbraa
 * @version 2.0
 */
public class AdiabaticPipe extends Pipeline implements neqsim.process.design.AutoSizeable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double inletPressure = 0;
  boolean setTemperature = false;
  boolean setPressureOut = false;
  protected double temperatureOut = 270;
  protected double pressureOut = 0.0;
  double dH = 0.0;
  String pipeSpecification = "AP02";

  // Use parent's length, diameter, roughness, inletElevation, outletElevation
  // Override with local insideDiameter for backward compatibility
  double insideDiameter = 0.1;
  double pipeWallRoughnessLocal = 1e-5;

  /**
   * Constructor for AdiabaticPipe.
   *
   * @param name name of pipe
   */
  public AdiabaticPipe(String name) {
    super(name);
    this.adiabatic = true;
  }

  /**
   * Constructor for AdiabaticPipe.
   *
   * @param name name of pipe
   * @param inStream input stream
   */
  public AdiabaticPipe(String name, StreamInterface inStream) {
    this(name);
    this.inStream = inStream;
    outStream = inStream.clone();
  }

  /** {@inheritDoc} */
  @Override
  public void setLength(double length) {
    super.setLength(length);
  }

  /** {@inheritDoc} */
  @Override
  public double getLength() {
    return super.getLength();
  }

  /** {@inheritDoc} */
  @Override
  public void setDiameter(double diameter) {
    super.setDiameter(diameter);
    this.insideDiameter = diameter;
  }

  /** {@inheritDoc} */
  @Override
  public double getDiameter() {
    return insideDiameter;
  }

  /** {@inheritDoc} */
  @Override
  public void setPipeWallRoughness(double roughness) {
    super.setPipeWallRoughness(roughness);
    this.pipeWallRoughnessLocal = roughness;
  }

  /** {@inheritDoc} */
  @Override
  public double getPipeWallRoughness() {
    return pipeWallRoughnessLocal;
  }

  /** {@inheritDoc} */
  @Override
  public void setInletElevation(double inletElevation) {
    super.setInletElevation(inletElevation);
  }

  /** {@inheritDoc} */
  @Override
  public double getInletElevation() {
    return super.getInletElevation();
  }

  /** {@inheritDoc} */
  @Override
  public void setOutletElevation(double outletElevation) {
    super.setOutletElevation(outletElevation);
  }

  /** {@inheritDoc} */
  @Override
  public double getOutletElevation() {
    return super.getOutletElevation();
  }

  /** {@inheritDoc} */
  @Override
  public void setPipeSpecification(double nominalDiameter, String pipeSec) {
    pipeSpecification = pipeSec;
    insideDiameter = nominalDiameter / 1000.0;
    super.setDiameter(insideDiameter);
  }

  /** {@inheritDoc} */
  @Override
  public void setOutletTemperature(double temperature) {
    setTemperature = true;
    this.temperatureOut = temperature;
  }

  /** {@inheritDoc} */
  @Override
  public void setOutletPressure(double pressure) {
    setPressureOut = true;
    this.pressureOut = pressure;
  }

  /**
   * Calculate the wall friction factor using the Haaland equation.
   *
   * @param reynoldsNumber the Reynolds number
   * @return the Darcy friction factor
   */
  public double calcWallFrictionFactor(double reynoldsNumber) {
    if (Math.abs(reynoldsNumber) < 1e-10) {
      flowRegime = "no-flow";
      return 0.0;
    }
    double relativeRoughnes = getPipeWallRoughness() / insideDiameter;
    if (Math.abs(reynoldsNumber) < 2300) {
      flowRegime = "laminar";
      return 64.0 / reynoldsNumber;
    } else if (Math.abs(reynoldsNumber) < 4000) {
      // Transition zone - interpolate between laminar and turbulent
      flowRegime = "transition";
      double fLaminar = 64.0 / 2300.0;
      double fTurbulent = Math.pow(
          (1.0 / (-1.8 * Math.log10(6.9 / 4000.0 + Math.pow(relativeRoughnes / 3.7, 1.11)))), 2.0);
      return fLaminar + (fTurbulent - fLaminar) * (reynoldsNumber - 2300.0) / 1700.0;
    } else {
      flowRegime = "turbulent";
      return Math.pow((1.0
          / (-1.8 * Math.log10(6.9 / reynoldsNumber + Math.pow(relativeRoughnes / 3.7, 1.11)))),
          2.0);
    }
  }

  /**
   * Calculate the outlet pressure based on friction and elevation losses.
   *
   * <p>
   * This method calculates the pressure drop using the general flow equation for compressible gas
   * flow, accounting for:
   * </p>
   * <ul>
   * <li>Friction pressure loss (using effective length including fittings)</li>
   * <li>Elevation pressure change (hydrostatic head)</li>
   * </ul>
   *
   * <p>
   * The effective length includes both physical pipe length and equivalent length from fittings:
   * </p>
   * 
   * <pre>
   * L_eff = L_physical + Σ(L/D)_i × D
   * </pre>
   *
   * @return the outlet pressure in bara
   */
  public double calcPressureOut() {
    // Use effective length (physical + fittings equivalent length)
    double effectiveLength = getEffectiveLength();

    double area = Math.PI / 4.0 * Math.pow(insideDiameter, 2.0);
    velocity = system.getPhase(0).getTotalVolume() / area / 1.0e5;
    reynoldsNumber = velocity * insideDiameter
        / system.getPhase(0).getPhysicalProperties().getKinematicViscosity();
    frictionFactor = calcWallFrictionFactor(reynoldsNumber);

    // Pressure drop calculation with effective length
    double dp = Math
        .pow(4.0 * system.getPhase(0).getNumberOfMolesInPhase() * system.getPhase(0).getMolarMass()
            / neqsim.thermo.ThermodynamicConstantsInterface.pi, 2.0)
        * frictionFactor * effectiveLength * system.getPhase(0).getZ()
        * neqsim.thermo.ThermodynamicConstantsInterface.R / system.getPhase(0).getMolarMass()
        * system.getTemperature() / Math.pow(insideDiameter, 5.0);
    double dp_gravity =
        system.getDensity("kg/m3") * neqsim.thermo.ThermodynamicConstantsInterface.gravity
            * (inletElevation - outletElevation);
    return Math.sqrt(Math.pow(inletPressure * 1e5, 2.0) - dp) / 1.0e5 + dp_gravity / 1.0e5;
  }

  /**
   * Calculate the flow rate required to achieve the specified outlet pressure.
   *
   * <p>
   * Uses bisection iteration to find the flow rate that achieves the target outlet pressure.
   * </p>
   *
   * @return the calculated flow rate in the current system units
   */
  public double calcFlow() {
    double originalFlowRate = system.getFlowRate("kg/sec");
    if (originalFlowRate <= 0) {
      originalFlowRate = 1.0;
    }

    double flowLow = originalFlowRate * 0.001;
    double flowHigh = originalFlowRate * 100.0;

    system.setTotalFlowRate(flowLow, "kg/sec");
    system.init(3);
    system.initPhysicalProperties();
    double pOutLow = calcPressureOut();

    system.setTotalFlowRate(flowHigh, "kg/sec");
    system.init(3);
    system.initPhysicalProperties();
    double pOutHigh = calcPressureOut();

    int boundIter = 0;
    while (pOutLow < pressureOut && boundIter < 20) {
      flowLow /= 10.0;
      system.setTotalFlowRate(flowLow, "kg/sec");
      system.init(3);
      system.initPhysicalProperties();
      pOutLow = calcPressureOut();
      boundIter++;
    }

    boundIter = 0;
    while (pOutHigh > pressureOut && boundIter < 20) {
      flowHigh *= 10.0;
      system.setTotalFlowRate(flowHigh, "kg/sec");
      system.init(3);
      system.initPhysicalProperties();
      pOutHigh = calcPressureOut();
      boundIter++;
    }

    double flowMid = 0;
    double pOutMid = 0;
    int maxIter = 50;
    double tolerance = 1e-4;

    for (int i = 0; i < maxIter; i++) {
      flowMid = (flowLow + flowHigh) / 2.0;
      system.setTotalFlowRate(flowMid, "kg/sec");
      system.init(3);
      system.initPhysicalProperties();
      pOutMid = calcPressureOut();

      double relError = Math.abs(pOutMid - pressureOut) / pressureOut;
      if (relError < tolerance) {
        break;
      }

      if (pOutMid > pressureOut) {
        flowLow = flowMid;
      } else {
        flowHigh = flowMid;
      }

      if (Math.abs(flowHigh - flowLow) / flowMid < tolerance) {
        break;
      }
    }

    return system.getFlowRate("kg/sec");
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    system = inStream.getThermoSystem().clone();
    inletPressure = system.getPressure();
    if (setTemperature) {
      system.setTemperature(this.temperatureOut);
    }

    double oldPressure = 0.0;
    int iter = 0;
    if (!setPressureOut) {
      do {
        iter++;
        oldPressure = system.getPressure();
        system.init(3);
        system.initPhysicalProperties();
        system.setPressure(calcPressureOut());
      } while (Math.abs(system.getPressure() - oldPressure) > 1e-2 && iter < 25);
    } else {
      calcFlow();
      system.setPressure(pressureOut);
      system.init(3);
    }
    ThermodynamicOperations testOps = new ThermodynamicOperations(system);
    testOps.TPflash();
    if (setPressureOut) {
      inStream.getThermoSystem().setTotalFlowRate(system.getFlowRate("kg/sec"), "kg/sec");
    }

    // Calculate pressure drop
    pressureDrop = inletPressure - system.getPressure();

    outStream.setThermoSystem(system);
    outStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    system.display();
  }

  /** {@inheritDoc} */
  @Override
  public FlowSystemInterface getPipe() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public void setInitialFlowPattern(String flowPattern) {
    // Not applicable for single-phase adiabatic pipe
  }

  /** {@inheritDoc} */
  @Override
  public double getPressureDrop() {
    return pressureDrop;
  }

  /** {@inheritDoc} */
  @Override
  public String getFlowRegime() {
    return flowRegime;
  }

  /** {@inheritDoc} */
  @Override
  public double getVelocity() {
    return velocity;
  }

  /** {@inheritDoc} */
  @Override
  public double getReynoldsNumber() {
    return reynoldsNumber;
  }

  /** {@inheritDoc} */
  @Override
  public double getFrictionFactor() {
    return frictionFactor;
  }

  // ============================================================================
  // Flow-Induced Vibration (FIV) Calculations
  // ============================================================================

  /** Support arrangement for FIV calculations. */
  private String supportArrangement = "Medium stiff";

  /** Maximum design velocity in m/s. */
  private double maxDesignVelocity = 20.0;

  /** Maximum design LOF value. */
  private double maxDesignLOF = 1.0;

  /** Maximum design FRMS value. */
  private double maxDesignFRMS = 500.0;

  /**
   * Rhone-Poulenc velocity calculator. When non-null, maximum velocity is determined from the
   * Rhone-Poulenc curves instead of API RP 14E.
   */
  private RhonePoulencVelocity rhonePoulencVelocity = null;

  /** Wall thickness in meters. */
  private double pipeWallThickness = 0.01;

  /** Flag indicating if pipe has been auto-sized. */
  private boolean autoSized = false;

  /**
   * Get support arrangement for FIV calculations.
   *
   * @return support arrangement (Stiff, Medium stiff, Medium, Flexible)
   */
  public String getSupportArrangement() {
    return supportArrangement;
  }

  /**
   * Set support arrangement for FIV calculations.
   *
   * @param arrangement support arrangement (Stiff, Medium stiff, Medium, Flexible)
   */
  public void setSupportArrangement(String arrangement) {
    this.supportArrangement = arrangement;
  }

  /**
   * Enable Rhone-Poulenc maximum velocity calculation for gas pipes.
   *
   * <p>
   * When enabled, the maximum allowable velocity is determined from the Rhone-Poulenc curves
   * instead of the API RP 14E erosional velocity. The Rhone-Poulenc method uses a power-law
   * correlation between gas density and maximum velocity, with service-type-dependent constants.
   * </p>
   *
   * @param serviceType the gas service type (NON_CORROSIVE_GAS or CORROSIVE_GAS)
   */
  public void setRhonePoulencServiceType(RhonePoulencVelocity.ServiceType serviceType) {
    this.rhonePoulencVelocity = new RhonePoulencVelocity(serviceType);
  }

  /**
   * Enable Rhone-Poulenc maximum velocity calculation with default non-corrosive gas settings.
   *
   * <p>
   * Equivalent to calling {@code setRhonePoulencServiceType(ServiceType.NON_CORROSIVE_GAS)}.
   * </p>
   */
  public void useRhonePoulencVelocity() {
    this.rhonePoulencVelocity =
        new RhonePoulencVelocity(RhonePoulencVelocity.ServiceType.NON_CORROSIVE_GAS);
  }

  /**
   * Enable Rhone-Poulenc maximum velocity calculation using tabulated data with log-log
   * interpolation for higher accuracy.
   *
   * @param serviceType the gas service type
   * @param useInterpolation true to use tabulated interpolation, false for power-law formula
   */
  public void setRhonePoulencServiceType(RhonePoulencVelocity.ServiceType serviceType,
      boolean useInterpolation) {
    this.rhonePoulencVelocity = new RhonePoulencVelocity(serviceType);
    this.rhonePoulencVelocity.setUseInterpolation(useInterpolation);
  }

  /**
   * Disable Rhone-Poulenc maximum velocity and revert to API RP 14E erosional velocity.
   */
  public void disableRhonePoulencVelocity() {
    this.rhonePoulencVelocity = null;
  }

  /**
   * Check if Rhone-Poulenc maximum velocity is enabled.
   *
   * @return true if Rhone-Poulenc method is active
   */
  public boolean isRhonePoulencEnabled() {
    return rhonePoulencVelocity != null;
  }

  /**
   * Get the Rhone-Poulenc velocity calculator, or null if not enabled.
   *
   * @return the RhonePoulencVelocity calculator or null
   */
  public RhonePoulencVelocity getRhonePoulencCalculator() {
    return rhonePoulencVelocity;
  }

  /**
   * Calculate the maximum allowable gas velocity using the Rhone-Poulenc curves.
   *
   * <p>
   * This method uses the current gas density from the simulation to look up the maximum velocity
   * from the Rhone-Poulenc correlation. If Rhone-Poulenc is not enabled, this returns 0.0.
   * </p>
   *
   * @return maximum allowable velocity in m/s, or 0.0 if not enabled or density unavailable
   */
  public double getRhonePoulencMaxVelocity() {
    if (rhonePoulencVelocity == null) {
      return 0.0;
    }
    double density = getGasDensityForVelocity();
    if (density <= 0) {
      return 0.0;
    }
    return rhonePoulencVelocity.getMaxVelocity(density);
  }

  /**
   * Get the effective maximum allowable velocity using the currently configured method.
   *
   * <p>
   * Returns Rhone-Poulenc max velocity if enabled, otherwise the API RP 14E erosional velocity.
   * </p>
   *
   * @return maximum allowable velocity in m/s
   */
  public double getMaxAllowableVelocity() {
    if (rhonePoulencVelocity != null) {
      double rpVel = getRhonePoulencMaxVelocity();
      return rpVel > 0 ? rpVel : getErosionalVelocity();
    }
    return getErosionalVelocity();
  }

  /**
   * Get the name of the currently active maximum velocity method.
   *
   * @return "RHONE_POULENC" or "API_RP_14E"
   */
  public String getMaxVelocityMethod() {
    return rhonePoulencVelocity != null ? "RHONE_POULENC" : "API_RP_14E";
  }

  /**
   * Get the gas density used for velocity calculations.
   *
   * @return gas density in kg/m3, or 0.0 if unavailable
   */
  private double getGasDensityForVelocity() {
    if (system != null) {
      return system.getDensity("kg/m3");
    }
    if (inStream != null && inStream.getFluid() != null) {
      return inStream.getFluid().getDensity("kg/m3");
    }
    return 0.0;
  }

  /**
   * Set pipe wall thickness.
   *
   * @param thickness wall thickness in meters
   */
  public void setPipeWallThickness(double thickness) {
    this.pipeWallThickness = thickness;
  }

  /**
   * Get pipe wall thickness.
   *
   * @return wall thickness in meters
   */
  public double getPipeWallThickness() {
    return pipeWallThickness;
  }

  /**
   * Calculate erosional velocity per API RP 14E.
   *
   * @param cFactor erosional C-factor (typically 100-150)
   * @return erosional velocity in m/s
   */
  public double getErosionalVelocity(double cFactor) {
    if (system == null && inStream != null) {
      double density = inStream.getFluid().getDensity("kg/m3");
      return density > 0 ? cFactor / Math.sqrt(density) : 0.0;
    }
    if (system != null) {
      double density = system.getDensity("kg/m3");
      return density > 0 ? cFactor / Math.sqrt(density) : 0.0;
    }
    return 0.0;
  }

  /**
   * Calculate erosional velocity with default C-factor of 100.
   *
   * @return erosional velocity in m/s
   */
  public double getErosionalVelocity() {
    return getErosionalVelocity(100.0);
  }

  /**
   * Get the mixture velocity in m/s.
   *
   * @return mixture velocity in m/s
   */
  public double getMixtureVelocity() {
    return velocity;
  }

  /**
   * Calculate Likelihood of Failure (LOF) for flow-induced vibration.
   *
   * <p>
   * LOF interpretation:
   * </p>
   * <ul>
   * <li>&lt; 0.5: Low risk - acceptable</li>
   * <li>0.5 - 1.0: Medium risk - monitoring recommended</li>
   * <li>&gt; 1.0: High risk - design review required</li>
   * </ul>
   *
   * @return LOF value (dimensionless)
   */
  public double calculateLOF() {
    double mixVelocity = getMixtureVelocity();
    if (mixVelocity <= 0 || Double.isNaN(mixVelocity)) {
      return Double.NaN;
    }

    // Get mixture density in kg/m³
    double densityKgM3;
    if (system != null) {
      densityKgM3 = system.getDensity("kg/m3");
    } else if (inStream != null && inStream.getFluid() != null) {
      densityKgM3 = inStream.getFluid().getDensity("kg/m3");
    } else {
      return Double.NaN;
    }

    // For single-phase adiabatic pipe, assume GVF = 1.0 (gas)
    double GVF = 1.0;

    // Calculate flow velocity factor (FVF) for gas
    double FVF = 1.0;
    if (GVF > 0.99) {
      double viscosity = 0.001;
      if (system != null) {
        try {
          viscosity = system.getViscosity("kg/msec");
        } catch (Exception e) {
          // Use default
        }
      }
      FVF = Math.sqrt(viscosity / Math.sqrt(0.001));
    }

    // External diameter in mm
    double outerDiameter = (insideDiameter + 2 * pipeWallThickness) * 1000.0;

    // Wall thickness in mm
    double wallThicknessMM = pipeWallThickness * 1000.0;
    if (wallThicknessMM < 1.0) {
      wallThicknessMM = outerDiameter * 0.05;
    }

    // Support arrangement coefficients
    double alpha;
    double beta;
    if ("Stiff".equals(supportArrangement)) {
      alpha =
          446187 + 646 * outerDiameter + 9.17E-4 * outerDiameter * outerDiameter * outerDiameter;
      beta = 0.1 * Math.log(outerDiameter) - 1.3739;
    } else if ("Medium stiff".equals(supportArrangement)) {
      alpha = 283921 + 370 * outerDiameter;
      beta = 0.1106 * Math.log(outerDiameter) - 1.501;
    } else if ("Medium".equals(supportArrangement)) {
      alpha = 150412 + 209 * outerDiameter;
      beta = 0.0815 * Math.log(outerDiameter) - 1.3269;
    } else {
      // Flexible
      alpha = 41.21 * Math.log(outerDiameter) + 49397;
      beta = 0.0815 * Math.log(outerDiameter) - 1.3842;
    }

    double diameterOverThickness = outerDiameter / wallThicknessMM;
    double Fv = alpha * Math.pow(diameterOverThickness, beta);

    return densityKgM3 * mixVelocity * mixVelocity * FVF / Fv;
  }

  /**
   * Calculate Flow-induced vibration RMS (FRMS).
   *
   * @return FRMS value
   */
  public double calculateFRMS() {
    return calculateFRMS(6.7);
  }

  /**
   * Calculate Flow-induced vibration RMS (FRMS) with specified constant.
   *
   * @param frmsConstant FRMS constant (typically 6.7)
   * @return FRMS value
   */
  public double calculateFRMS(double frmsConstant) {
    double mixVelocity = getMixtureVelocity();
    if (mixVelocity <= 0 || Double.isNaN(mixVelocity)) {
      return Double.NaN;
    }

    // For gas-only, GVF = 1.0, so C factor is low
    double GVF = 1.0;
    double C = Math.min(Math.min(1, 5 * (1 - GVF)), 5 * GVF) * frmsConstant;

    // Get density
    double density;
    if (system != null) {
      density = system.getDensity("kg/m3");
    } else if (inStream != null && inStream.getFluid() != null) {
      density = inStream.getFluid().getDensity("kg/m3");
    } else {
      return Double.NaN;
    }

    return C * Math.pow(insideDiameter, 1.6) * Math.pow(density, 0.6) * Math.pow(mixVelocity, 1.2);
  }

  /**
   * Get comprehensive FIV analysis results as a map.
   *
   * @return map containing all FIV analysis results
   */
  public java.util.Map<String, Object> getFIVAnalysis() {
    java.util.Map<String, Object> result = new java.util.LinkedHashMap<String, Object>();

    result.put("supportArrangement", supportArrangement);
    result.put("mixtureVelocity_m_s", getMixtureVelocity());
    result.put("erosionalVelocity_m_s", getErosionalVelocity());
    result.put("velocityRatio",
        getErosionalVelocity() > 0 ? getMixtureVelocity() / getErosionalVelocity() : Double.NaN);

    // Rhone-Poulenc max velocity (if enabled)
    result.put("maxVelocityMethod", getMaxVelocityMethod());
    result.put("maxAllowableVelocity_m_s", getMaxAllowableVelocity());
    if (rhonePoulencVelocity != null) {
      result.put("rhonePoulencMaxVelocity_m_s", getRhonePoulencMaxVelocity());
      result.put("rhonePoulencServiceType", rhonePoulencVelocity.getServiceType().name());
      double rpMaxVel = getRhonePoulencMaxVelocity();
      result.put("rhonePoulencVelocityRatio",
          rpMaxVel > 0 ? getMixtureVelocity() / rpMaxVel : Double.NaN);
    }

    double lof = calculateLOF();
    result.put("LOF", lof);
    if (Double.isNaN(lof)) {
      result.put("LOF_risk", "UNKNOWN");
    } else if (lof < 0.5) {
      result.put("LOF_risk", "LOW");
    } else if (lof < 1.0) {
      result.put("LOF_risk", "MEDIUM");
    } else {
      result.put("LOF_risk", "HIGH");
    }

    result.put("FRMS", calculateFRMS());

    return result;
  }

  /**
   * Get FIV analysis as JSON string.
   *
   * @return JSON string with FIV analysis
   */
  public String getFIVAnalysisJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(getFIVAnalysis());
  }

  /**
   * Set maximum design velocity for capacity constraints.
   *
   * @param maxVelocity maximum velocity in m/s
   */
  public void setMaxDesignVelocity(double maxVelocity) {
    this.maxDesignVelocity = maxVelocity;
  }

  /**
   * Set maximum design LOF for capacity constraints.
   *
   * @param maxLOF maximum LOF value
   */
  public void setMaxDesignLOF(double maxLOF) {
    this.maxDesignLOF = maxLOF;
  }

  /**
   * Set maximum design FRMS for capacity constraints.
   *
   * @param maxFRMS maximum FRMS value
   */
  public void setMaxDesignFRMS(double maxFRMS) {
    this.maxDesignFRMS = maxFRMS;
  }

  /**
   * Override parent's capacity constraint initialization to add FIV/FRMS constraints.
   */
  @Override
  protected void initializeCapacityConstraints() {
    // Velocity constraint (SOFT limit - uses Rhone-Poulenc if enabled, else erosional)
    addCapacityConstraint(new neqsim.process.equipment.capacity.CapacityConstraint("velocity",
        "m/s", neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.SOFT)
            .setDesignValue(maxDesignVelocity).setMaxValue(getMaxAllowableVelocity())
            .setWarningThreshold(0.9)
            .setDescription("Velocity vs " + getMaxVelocityMethod() + " limit")
            .setValueSupplier(() -> getMixtureVelocity()));

    // LOF (Likelihood of Failure) - FIV constraint
    addCapacityConstraint(new neqsim.process.equipment.capacity.CapacityConstraint("LOF", "-",
        neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.SOFT)
            .setDesignValue(maxDesignLOF).setMaxValue(1.5).setWarningThreshold(0.5)
            .setDescription("LOF for flow-induced vibration (>1.0 = high risk)")
            .setValueSupplier(() -> calculateLOF()));

    // FRMS (Flow-induced vibration RMS)
    addCapacityConstraint(new neqsim.process.equipment.capacity.CapacityConstraint("FRMS", "-",
        neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.SOFT)
            .setDesignValue(maxDesignFRMS).setMaxValue(750.0).setWarningThreshold(0.8)
            .setDescription("FRMS vibration intensity").setValueSupplier(() -> calculateFRMS()));

    // Volume flow constraint from mechanical design
    if (getMechanicalDesign() != null && getMechanicalDesign().maxDesignVolumeFlow > 0) {
      addCapacityConstraint(new neqsim.process.equipment.capacity.CapacityConstraint("volumeFlow",
          "m3/hr", neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.DESIGN)
              .setDesignValue(getMechanicalDesign().maxDesignVolumeFlow).setWarningThreshold(0.9)
              .setDescription("Volume flow vs mechanical design limit").setValueSupplier(
                  () -> getOutletStream() != null ? getOutletStream().getFlowRate("m3/hr") : 0.0));
    }

    // Pressure drop constraint
    if (getMechanicalDesign() != null && getMechanicalDesign().maxDesignPressureDrop > 0) {
      addCapacityConstraint(new neqsim.process.equipment.capacity.CapacityConstraint("pressureDrop",
          "bar", neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.DESIGN)
              .setDesignValue(getMechanicalDesign().maxDesignPressureDrop).setWarningThreshold(0.9)
              .setDescription("Pressure drop vs mechanical design limit")
              .setValueSupplier(() -> getPressureDrop()));
    }
  }

  // ============================================================================
  // AutoSizeable Implementation
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public void autoSize(double safetyFactor) {
    if (inStream == null) {
      throw new IllegalStateException("Cannot auto-size pipeline without inlet stream");
    }

    // Run to establish operating conditions
    run();

    // Calculate optimal diameter based on velocity criteria
    double volumetricFlowRate = inStream.getFluid().getFlowRate("m3/sec");

    // Handle zero flow case - use minimum default diameter
    if (Double.isNaN(volumetricFlowRate) || volumetricFlowRate <= 0) {
      // For zero-flow pipe, set reasonable minimum design values
      double minDesignDiameter = 0.0254 * 2.0; // 2 inch minimum
      setDiameter(minDesignDiameter);
      setPipeWallThickness(minDesignDiameter * 0.05);

      // Set minimum design values for capacity tracking
      getMechanicalDesign().maxDesignVelocity = 10.0 * safetyFactor; // 10 m/s default
      getMechanicalDesign().maxDesignVolumeFlow = 100.0 * safetyFactor; // 100 m3/hr default
      getMechanicalDesign().maxDesignPressureDrop = 1.0; // 1 bar default

      // Clear and reinitialize capacity constraints with new design values
      clearCapacityConstraints();
      initializeCapacityConstraints();

      autoSized = true;
      return;
    }

    // Target velocity depends on fluid type (gas vs liquid)
    double targetVelocity;
    if (inStream.getFluid().hasPhaseType("gas") && !inStream.getFluid().hasPhaseType("oil")
        && !inStream.getFluid().hasPhaseType("aqueous")) {
      // Gas pipeline - typical 15-20 m/s
      targetVelocity = 15.0 / safetyFactor;
    } else if (!inStream.getFluid().hasPhaseType("gas")) {
      // Liquid pipeline - typical 2-3 m/s
      targetVelocity = 2.5 / safetyFactor;
    } else {
      // Multiphase - use mixture velocity approach
      targetVelocity = 8.0 / safetyFactor;
    }

    // Calculate required diameter: D = sqrt(4 * Q / (pi * v))
    double requiredDiameter = Math.sqrt(4.0 * volumetricFlowRate / (Math.PI * targetVelocity));

    // Apply safety factor to diameter
    double designDiameter = requiredDiameter * Math.sqrt(safetyFactor);

    // Round up to nearest standard pipe size (in inches)
    double diameterInches = designDiameter / 0.0254;
    double standardDiameter = selectStandardPipeSize(diameterInches);

    // Set the diameter
    setDiameter(standardDiameter * 0.0254);

    // Estimate wall thickness (5% of OD is typical)
    setPipeWallThickness(standardDiameter * 0.0254 * 0.05);

    // Re-run to update calculations with new diameter
    run();

    // Set design values for capacity constraints (with guards for NaN/invalid
    // values)
    try {
      double currentVelocity = getVelocity();
      double currentVolumeFlow = outStream != null ? outStream.getFlowRate("m3/hr") : 0.0;
      double currentPressureDrop = getPressureDrop();

      // Set default values if current values are invalid
      if (Double.isNaN(currentVelocity) || currentVelocity <= 0) {
        currentVelocity = 10.0; // Default 10 m/s
      }
      if (Double.isNaN(currentVolumeFlow) || currentVolumeFlow <= 0) {
        currentVolumeFlow = 100.0; // Default 100 m3/hr
      }

      getMechanicalDesign().maxDesignVelocity = currentVelocity * safetyFactor;
      getMechanicalDesign().maxDesignVolumeFlow = currentVolumeFlow * safetyFactor;
      if (!Double.isNaN(currentPressureDrop) && currentPressureDrop > 0) {
        getMechanicalDesign().maxDesignPressureDrop = currentPressureDrop * safetyFactor;
      }

      // Clear and reinitialize capacity constraints with new design values
      clearCapacityConstraints();
      initializeCapacityConstraints();
    } catch (Exception e) {
      // Silently continue if we can't calculate design values
      // The pipeline was still sized, just without constraint design values
    }

    autoSized = true;
  }

  /**
   * Selects standard pipe nominal diameter based on calculated diameter.
   *
   * @param calculatedDiameterInches calculated inside diameter in inches
   * @return nearest standard pipe nominal diameter in inches
   */
  private double selectStandardPipeSize(double calculatedDiameterInches) {
    double[] standardSizes = {0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 2.5, 3.0, 4.0, 6.0, 8.0, 10.0, 12.0,
        14.0, 16.0, 18.0, 20.0, 24.0, 30.0, 36.0, 42.0, 48.0};

    for (double size : standardSizes) {
      if (size >= calculatedDiameterInches) {
        return size;
      }
    }
    return Math.ceil(calculatedDiameterInches / 6.0) * 6.0;
  }

  /** {@inheritDoc} */
  @Override
  public void autoSize() {
    autoSize(1.2);
  }

  /** {@inheritDoc} */
  @Override
  public void autoSize(String company, String trDocument) {
    double safetyFactor = 1.2;
    if ("Equinor".equalsIgnoreCase(company)) {
      safetyFactor = 1.25;
    }
    autoSize(safetyFactor);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAutoSized() {
    return autoSized;
  }

  /** {@inheritDoc} */
  @Override
  public String getSizingReport() {
    StringBuilder report = new StringBuilder();
    report.append("AdiabaticPipe Sizing Report for: ").append(getName()).append("\n");
    report.append("=========================================\n");
    report.append("Auto-sized: ").append(autoSized).append("\n");

    if (inStream != null) {
      report.append("\nOperating Conditions:\n");
      report.append(String.format("  Inlet Pressure: %.2f bara\n", inletPressure));
      report.append(String.format("  Outlet Pressure: %.2f bara\n", pressureOut));
      report.append(String.format("  Pressure Drop: %.2f bar\n", pressureDrop));
      report.append(String.format("  Flow Rate: %.2f kg/hr\n", inStream.getFlowRate("kg/hr")));
    }

    report.append("\nGeometry:\n");
    report.append(String.format("  Inside Diameter: %.1f mm (%.2f inch)\n", insideDiameter * 1000,
        insideDiameter / 0.0254));
    report.append(String.format("  Wall Thickness: %.1f mm\n", pipeWallThickness * 1000));
    report.append(String.format("  Length: %.1f m\n", getLength()));

    report.append("\nFlow Characteristics:\n");
    report.append(String.format("  Velocity: %.2f m/s\n", velocity));
    report.append(
        String.format("  Erosional Velocity (API RP 14E): %.2f m/s\n", getErosionalVelocity()));
    if (rhonePoulencVelocity != null) {
      report.append(String.format("  Rhone-Poulenc Max Velocity: %.2f m/s (%s)\n",
          getRhonePoulencMaxVelocity(), rhonePoulencVelocity.getServiceType().name()));
    }
    report.append(String.format("  Max Allowable Velocity (%s): %.2f m/s\n", getMaxVelocityMethod(),
        getMaxAllowableVelocity()));
    report.append(String.format("  Flow Regime: %s\n", flowRegime));
    report.append(String.format("  Reynolds Number: %.0f\n", reynoldsNumber));

    report.append("\nFIV Analysis:\n");
    report.append(String.format("  Support Arrangement: %s\n", supportArrangement));
    report.append(String.format("  LOF: %.4f\n", calculateLOF()));
    report.append(String.format("  FRMS: %.2f\n", calculateFRMS()));

    return report.toString();
  }

  /** {@inheritDoc} */
  @Override
  public String getSizingReportJson() {
    java.util.Map<String, Object> reportData = new java.util.LinkedHashMap<String, Object>();

    reportData.put("equipmentName", getName());
    reportData.put("equipmentType", "AdiabaticPipe");
    reportData.put("autoSized", autoSized);

    if (inStream != null) {
      java.util.Map<String, Object> operating = new java.util.LinkedHashMap<String, Object>();
      operating.put("inletPressure_bara", inletPressure);
      operating.put("outletPressure_bara", pressureOut);
      operating.put("pressureDrop_bar", pressureDrop);
      operating.put("flowRate_kghr", inStream.getFlowRate("kg/hr"));
      reportData.put("operatingConditions", operating);

      java.util.Map<String, Object> geometry = new java.util.LinkedHashMap<String, Object>();
      geometry.put("insideDiameter_mm", insideDiameter * 1000);
      geometry.put("insideDiameter_inch", insideDiameter / 0.0254);
      geometry.put("wallThickness_mm", pipeWallThickness * 1000);
      geometry.put("length_m", getLength());
      reportData.put("geometry", geometry);

      java.util.Map<String, Object> velocities = new java.util.LinkedHashMap<String, Object>();
      velocities.put("velocity_ms", velocity);
      velocities.put("erosionalVelocity_ms", getErosionalVelocity());
      velocities.put("maxAllowableVelocity_ms", getMaxAllowableVelocity());
      velocities.put("maxVelocityMethod", getMaxVelocityMethod());
      if (rhonePoulencVelocity != null) {
        velocities.put("rhonePoulencMaxVelocity_ms", getRhonePoulencMaxVelocity());
        velocities.put("rhonePoulencServiceType", rhonePoulencVelocity.getServiceType().name());
      }
      reportData.put("velocities", velocities);

      reportData.put("fivAnalysis", getFIVAnalysis());
    }

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(reportData);
  }

  /**
   * Main method for testing.
   *
   * @param name command line arguments
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] name) {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 5.0), 220.00);
    testSystem.addComponent("methane", 24.0, "MSm^3/day");
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.init(0);

    Stream stream_1 = new Stream("Stream1", testSystem);

    AdiabaticPipe pipe = new AdiabaticPipe("pipe1", stream_1);
    pipe.setLength(700000.0);
    pipe.setDiameter(0.7112);
    pipe.setPipeWallRoughness(5e-6);
    pipe.setOutPressure(112.0);

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(stream_1);
    operations.add(pipe);
    operations.run();
    pipe.displayResult();
  }
}
