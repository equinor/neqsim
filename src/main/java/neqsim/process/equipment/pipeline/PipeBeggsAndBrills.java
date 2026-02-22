package neqsim.process.equipment.pipeline;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.util.monitor.PipeBeggsBrillsResponse;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Pipeline simulation using Beggs and Brill empirical correlations for multiphase flow.
 *
 * <p>
 * This class implements the Beggs and Brill (1973) correlation for pressure drop and liquid holdup
 * prediction in multiphase pipeline flow. It supports both single-phase and multiphase (gas-liquid)
 * flow in horizontal, inclined, and vertical pipes.
 * </p>
 *
 * <h2>Reference</h2>
 * <p>
 * Beggs, H.D. and Brill, J.P., "A Study of Two-Phase Flow in Inclined Pipes", Journal of Petroleum
 * Technology, May 1973, pp. 607-617. SPE-4007-PA.
 * </p>
 *
 * <h2>Calculation Modes</h2>
 * <p>
 * The pipeline supports two primary calculation modes via {@link CalculationMode}:
 * </p>
 * <ul>
 * <li><b>CALCULATE_OUTLET_PRESSURE</b> (default) - Given inlet conditions and flow rate, calculate
 * the outlet pressure</li>
 * <li><b>CALCULATE_FLOW_RATE</b> - Given inlet and outlet pressures, calculate the flow rate using
 * iterative methods</li>
 * </ul>
 *
 * <h2>Flow Regime Determination</h2>
 * <p>
 * The Beggs and Brill correlation classifies flow into four regimes based on the Froude number (Fr)
 * and input liquid volume fraction (λL):
 * </p>
 * <ul>
 * <li><b>SEGREGATED</b> - Stratified, wavy, or annular flow where phases are separated</li>
 * <li><b>INTERMITTENT</b> - Plug or slug flow with alternating liquid slugs and gas pockets</li>
 * <li><b>DISTRIBUTED</b> - Bubble or mist flow where one phase is dispersed in the other</li>
 * <li><b>TRANSITION</b> - Flow in transition zone between segregated and intermittent</li>
 * <li><b>SINGLE_PHASE</b> - Only gas or only liquid present</li>
 * </ul>
 * <p>
 * Flow regime boundaries are defined by correlations L1-L4:
 * </p>
 * 
 * <pre>
 * L1 = 316 × λL^0.302
 * L2 = 0.0009252 × λL^(-2.4684)
 * L3 = 0.1 × λL^(-1.4516)
 * L4 = 0.5 × λL^(-6.738)
 * </pre>
 *
 * <h2>Pressure Drop Calculation</h2>
 * <p>
 * Total pressure drop consists of three components:
 * </p>
 * 
 * <pre>
 * ΔP_total = ΔP_friction + ΔP_hydrostatic + ΔP_acceleration
 * </pre>
 * <p>
 * where:
 * </p>
 * <ul>
 * <li><b>Friction pressure drop</b> - Uses two-phase friction factor with slip correction</li>
 * <li><b>Hydrostatic pressure drop</b> - Based on mixture density and elevation change</li>
 * <li><b>Acceleration pressure drop</b> - Usually negligible, included in friction term</li>
 * </ul>
 *
 * <h3>Liquid Holdup Calculation</h3>
 * <p>
 * Liquid holdup (EL) is calculated based on flow regime:
 * </p>
 * <ul>
 * <li><b>Segregated:</b> EL = 0.98 × λL^0.4846 / Fr^0.0868</li>
 * <li><b>Intermittent:</b> EL = 0.845 × λL^0.5351 / Fr^0.0173</li>
 * <li><b>Distributed:</b> EL = 1.065 × λL^0.5824 / Fr^0.0609</li>
 * </ul>
 * <p>
 * Inclination correction factor (Bθ) is applied for non-horizontal pipes.
 * </p>
 *
 * <h3>Friction Factor Calculation</h3>
 * <p>
 * The friction factor is calculated using:
 * </p>
 * <ul>
 * <li><b>Laminar (Re &lt; 2300):</b> f = 64/Re</li>
 * <li><b>Transition (2300-4000):</b> Linear interpolation</li>
 * <li><b>Turbulent (Re &gt; 4000):</b> Haaland equation</li>
 * </ul>
 * <p>
 * Two-phase friction factor: f_tp = f × exp(S), where S is a slip correction factor.
 * </p>
 *
 * <h2>Heat Transfer Modes</h2>
 * <p>
 * The pipeline supports five heat transfer calculation modes via {@link HeatTransferMode}:
 * </p>
 * <ul>
 * <li><b>ADIABATIC</b> - No heat transfer (Q=0). Temperature changes only from Joule-Thomson
 * effect.</li>
 * <li><b>ISOTHERMAL</b> - Constant temperature along the pipe (outlet T = inlet T).</li>
 * <li><b>SPECIFIED_U</b> - Use a user-specified overall heat transfer coefficient (U-value).</li>
 * <li><b>ESTIMATED_INNER_H</b> - Calculate inner h from flow conditions using Gnielinski
 * correlation for turbulent flow, use as U.</li>
 * <li><b>DETAILED_U</b> - Calculate inner h from flow, then compute overall U including pipe wall
 * conduction, insulation (if present), and outer convection resistances.</li>
 * </ul>
 *
 * <h3>NTU-Effectiveness Method</h3>
 * <p>
 * Heat transfer is calculated using the analytical NTU (Number of Transfer Units) method:
 * </p>
 * 
 * <pre>
 * NTU = U × A / (ṁ × Cp)
 * T_out = T_wall + (T_in - T_wall) × exp(-NTU)
 * </pre>
 * <p>
 * This provides an exact analytical solution for constant wall temperature boundary conditions.
 * </p>
 *
 * <h3>Inner Heat Transfer Coefficient</h3>
 * <p>
 * For ESTIMATED_INNER_H and DETAILED_U modes, the inner convective heat transfer coefficient is
 * calculated using:
 * </p>
 * <ul>
 * <li><b>Laminar flow (Re &lt; 2300)</b>: Nu = 3.66 (fully developed pipe flow)</li>
 * <li><b>Transition (2300 &lt; Re &lt; 3000)</b>: Linear interpolation</li>
 * <li><b>Turbulent flow (Re &gt; 3000)</b>: Gnielinski correlation: Nu = (f/8)(Re-1000)Pr / [1 +
 * 12.7(f/8)^0.5(Pr^(2/3)-1)]</li>
 * <li><b>Two-phase flow</b>: Shah/Martinelli enhancement factor applied</li>
 * </ul>
 *
 * <h3>Overall U-Value (DETAILED_U mode)</h3>
 * <p>
 * The overall heat transfer coefficient includes thermal resistances in series:
 * </p>
 * 
 * <pre>
 * 1/U = 1/h_inner + R_wall + R_insulation + 1/h_outer
 * 
 * R_wall = r_i × ln(r_o/r_i) / k_wall
 * R_insulation = r_i × ln(r_ins/r_o) / k_ins
 * </pre>
 * <p>
 * where:
 * </p>
 * <ul>
 * <li>h_inner = inner convective coefficient from flow calculation</li>
 * <li>R_wall = pipe wall conductive resistance (cylindrical geometry)</li>
 * <li>R_insulation = insulation layer resistance (if thickness &gt; 0)</li>
 * <li>h_outer = outer convective coefficient (e.g., seawater ~500 W/(m²·K))</li>
 * </ul>
 *
 * <h2>Energy Equation Components</h2>
 * <p>
 * The energy balance can include three optional components:
 * </p>
 * <ul>
 * <li><b>Wall heat transfer</b> - Heat exchange with surroundings using NTU-effectiveness
 * method</li>
 * <li><b>Joule-Thomson effect</b> - Temperature change due to gas expansion (cooling): ΔT_JT =
 * -μ_JT × ΔP</li>
 * <li><b>Friction heating</b> - Viscous dissipation adding energy to the fluid: Q_friction =
 * ΔP_friction × V̇</li>
 * </ul>
 *
 * <h3>Joule-Thomson Coefficient</h3>
 * <p>
 * The JT coefficient is calculated from rigorous thermodynamics (mass-weighted average across
 * phases). Typical values:
 * </p>
 * <ul>
 * <li>Methane: ~4×10⁻⁶ K/Pa (0.4 K/bar)</li>
 * <li>Natural gas: 3-5×10⁻⁶ K/Pa</li>
 * <li>CO₂: ~10⁻⁵ K/Pa (1 K/bar)</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Example 1: Basic Horizontal Pipeline</h3>
 * 
 * <pre>{@code
 * // Create fluid system
 * SystemInterface fluid = new SystemSrkEos(303.15, 50.0);
 * fluid.addComponent("methane", 0.9);
 * fluid.addComponent("ethane", 0.1);
 * fluid.setMixingRule("classic");
 * 
 * // Create inlet stream
 * Stream inlet = new Stream("inlet", fluid);
 * inlet.setFlowRate(50000, "kg/hr");
 * inlet.run();
 * 
 * // Create pipeline
 * PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("pipeline", inlet);
 * pipe.setDiameter(0.2032); // 8 inch
 * pipe.setLength(10000.0); // 10 km
 * pipe.setElevation(0.0); // horizontal
 * pipe.setNumberOfIncrements(20);
 * pipe.run();
 * 
 * System.out.println("Pressure drop: " + pipe.getPressureDrop() + " bar");
 * System.out.println("Flow regime: " + pipe.getFlowRegime());
 * }</pre>
 *
 * <h3>Example 2: Pipeline with Heat Transfer</h3>
 * 
 * <pre>{@code
 * // Hot fluid in cold environment
 * PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("subsea_pipe", hotStream);
 * pipe.setDiameter(0.1524); // 6 inch
 * pipe.setLength(5000.0); // 5 km
 * pipe.setElevation(0.0); // horizontal
 * pipe.setConstantSurfaceTemperature(5.0, "C"); // Sea temperature
 * pipe.setHeatTransferCoefficient(25.0); // W/(m²·K) - SPECIFIED_U mode
 * pipe.run();
 * 
 * System.out.println("Inlet T: " + pipe.getInletStream().getTemperature("C") + " °C");
 * System.out.println("Outlet T: " + pipe.getOutletStream().getTemperature("C") + " °C");
 * }</pre>
 *
 * <h3>Example 3: Detailed U-Value with Insulation</h3>
 * 
 * <pre>{@code
 * pipe.setConstantSurfaceTemperature(5.0, "C"); // Seawater
 * pipe.setOuterHeatTransferCoefficient(500.0); // Seawater forced convection
 * pipe.setPipeWallThermalConductivity(45.0); // Carbon steel
 * pipe.setInsulation(0.05, 0.04); // 50mm foam, k=0.04 W/(m·K)
 * pipe.setHeatTransferMode(HeatTransferMode.DETAILED_U);
 * pipe.run();
 * }</pre>
 *
 * <h3>Example 4: Inclined Pipeline (Riser)</h3>
 * 
 * <pre>{@code
 * // Vertical riser
 * PipeBeggsAndBrills riser = new PipeBeggsAndBrills("riser", feedStream);
 * riser.setDiameter(0.1524); // 6 inch
 * riser.setLength(500.0); // 500 m length
 * riser.setElevation(500.0); // 500 m vertical rise
 * riser.setAngle(90.0); // Vertical
 * riser.run();
 * 
 * System.out.println("Hydrostatic head: " + riser.getSegmentPressure(0)
 *     - riser.getSegmentPressure(riser.getNumberOfIncrements()) + " bar");
 * }</pre>
 *
 * <h3>Example 5: Adiabatic with Joule-Thomson Effect</h3>
 * 
 * <pre>{@code
 * // High-pressure gas expansion
 * pipe.setHeatTransferMode(HeatTransferMode.ADIABATIC);
 * pipe.setIncludeJouleThomsonEffect(true);
 * pipe.run();
 * 
 * // For natural gas with ~20 bar pressure drop:
 * // Expected JT cooling: ~8-10 K
 * }</pre>
 *
 * <h3>Example 6: Calculate Flow Rate from Pressures</h3>
 * 
 * <pre>{@code
 * pipe.setCalculationMode(CalculationMode.CALCULATE_FLOW_RATE);
 * pipe.setSpecifiedOutletPressure(40.0, "bara"); // Target outlet pressure
 * pipe.setMaxFlowIterations(50);
 * pipe.setFlowConvergenceTolerance(1e-4);
 * pipe.run();
 * 
 * System.out.println("Calculated flow: " + pipe.getOutletStream().getFlowRate("kg/hr") + " kg/hr");
 * }</pre>
 *
 * <h2>Transient Simulation</h2>
 * <p>
 * The class supports transient (time-dependent) simulation using the {@code runTransient()} method.
 * This solves the time-dependent mass, momentum, and energy conservation equations using an
 * explicit finite difference scheme.
 * </p>
 *
 * <h2>Typical Parameter Values</h2>
 * <table border="1">
 * <caption>Typical Heat Transfer Coefficients</caption>
 * <tr>
 * <th>Environment</th>
 * <th>h [W/(m²·K)]</th>
 * </tr>
 * <tr>
 * <td>Still air (natural convection)</td>
 * <td>5-25</td>
 * </tr>
 * <tr>
 * <td>Forced air</td>
 * <td>25-250</td>
 * </tr>
 * <tr>
 * <td>Still water</td>
 * <td>100-500</td>
 * </tr>
 * <tr>
 * <td>Seawater (flowing)</td>
 * <td>500-1000</td>
 * </tr>
 * <tr>
 * <td>Buried in soil</td>
 * <td>1-5</td>
 * </tr>
 * </table>
 *
 * <table border="1">
 * <caption>Typical Thermal Conductivities</caption>
 * <tr>
 * <th>Material</th>
 * <th>k [W/(m·K)]</th>
 * </tr>
 * <tr>
 * <td>Carbon steel</td>
 * <td>45-50</td>
 * </tr>
 * <tr>
 * <td>Stainless steel</td>
 * <td>15-20</td>
 * </tr>
 * <tr>
 * <td>Mineral wool insulation</td>
 * <td>0.03-0.05</td>
 * </tr>
 * <tr>
 * <td>Polyurethane foam</td>
 * <td>0.02-0.03</td>
 * </tr>
 * <tr>
 * <td>Concrete coating</td>
 * <td>1.0-1.5</td>
 * </tr>
 * </table>
 *
 * <p>
 * The pipeline implements CapacityConstrainedEquipment (inherited from Pipeline) with constraints:
 * </p>
 * <ul>
 * <li>Velocity - SOFT limit based on erosional velocity</li>
 * <li>LOF (Likelihood of Failure) - SOFT limit for flow-induced vibration</li>
 * <li>FRMS - SOFT limit for flow-induced vibration intensity</li>
 * <li>Volume flow - DESIGN limit from mechanical design</li>
 * <li>Pressure drop - DESIGN limit</li>
 * </ul>
 *
 * @author Even Solbraa, Sviatoslav Eroshkin
 * @version $Id: $Id
 * @see Pipeline
 * @see HeatTransferMode
 * @see CalculationMode
 * @see FlowRegime
 */
public class PipeBeggsAndBrills extends Pipeline implements neqsim.process.design.AutoSizeable {
  private static final long serialVersionUID = 1001;

  /** Flow regimes available in Beggs and Brill correlations. */
  public enum FlowRegime {
    SEGREGATED, INTERMITTENT, DISTRIBUTED, TRANSITION, SINGLE_PHASE, UNKNOWN
  }

  /** Calculation modes for pipeline simulation. */
  public enum CalculationMode {
    /** Calculate outlet pressure from inlet conditions and flow rate (default). */
    CALCULATE_OUTLET_PRESSURE,
    /** Calculate flow rate from inlet and specified outlet pressure. */
    CALCULATE_FLOW_RATE
  }

  /**
   * Heat transfer calculation modes for pipeline thermal modeling.
   *
   * <p>
   * Controls how temperature changes along the pipeline are calculated:
   * <ul>
   * <li>ADIABATIC: No heat transfer (Q=0), temperature changes only due to Joule-Thomson
   * effect</li>
   * <li>ISOTHERMAL: Constant temperature along the pipe (outlet T = inlet T)</li>
   * <li>SPECIFIED_U: Use a user-specified overall heat transfer coefficient (U-value)</li>
   * <li>ESTIMATED_INNER_H: Calculate inner h from flow (Gnielinski correlation), use as U</li>
   * <li>DETAILED_U: Calculate inner h from flow, then compute overall U including pipe wall,
   * insulation, and outer convection resistances</li>
   * </ul>
   */
  public enum HeatTransferMode {
    /**
     * No heat transfer - adiabatic pipe. Temperature changes only from Joule-Thomson effect.
     */
    ADIABATIC,
    /** Constant temperature along the pipe - isothermal operation. */
    ISOTHERMAL,
    /** Use a user-specified overall heat transfer coefficient. */
    SPECIFIED_U,
    /**
     * Calculate inner h from flow conditions (Gnielinski correlation), use as U.
     */
    ESTIMATED_INNER_H,
    /**
     * Calculate detailed overall U-value including inner convection, pipe wall conduction,
     * insulation (if present), and outer convection.
     */
    DETAILED_U
  }

  int iteration;

  private double nominalDiameter;

  private Boolean PipeSpecSet = false;

  // Inlet pressure of the pipeline (initialization)
  private double inletPressure = Double.NaN;

  private double totalPressureDrop = 0;

  // Outlet properties initialization [K] and [bar]
  protected double temperatureOut = 270;
  protected double pressureOut = 0.0;

  // Calculation mode and specified outlet pressure
  private CalculationMode calculationMode = CalculationMode.CALCULATE_OUTLET_PRESSURE;
  private double specifiedOutletPressure = Double.NaN;
  private String specifiedOutletPressureUnit = "bara";
  private int maxFlowIterations = 50;
  private double flowConvergenceTolerance = 1e-4;

  // Unit for maximum flow
  String maxflowunit = "kg/hr";

  // Inside diameter of the pipe [m]
  private double insideDiameter = Double.NaN;

  // Thickness diameter of the pipe [m]
  private double pipeThickness = Double.NaN;

  // Roughness of the pipe wall [m]
  private double pipeWallRoughness = 1e-5;

  // Flag to run isothermal calculations
  private boolean runIsothermal = true;

  // Flow pattern of the fluid in the pipe
  private FlowRegime regime;

  // Volume fraction of liquid in the input mixture
  private double inputVolumeFractionLiquid;

  // Froude number of the mixture
  private double mixtureFroudeNumber;

  // Specification of the pipe
  private String pipeSpecification = "LD201";

  // Ref. Beggs and Brills
  private double A;

  // Area of the pipe [m2]
  private double area;

  // Superficial gas velocity in the pipe [m/s]
  private double supGasVel;

  // Superficial liquid velocity in the pipe [m/s]
  private double supLiquidVel;

  // Density of the mixture [kg/m3]
  private double mixtureDensity;

  // Hydrostatic pressure drop in the pipe [bar]
  private double hydrostaticPressureDrop;

  // Holdup ref. Beggs and Brills
  private double El = 0;

  // Superficial mixture velocity in the pipe [m/s]
  private double supMixVel;

  // Frictional pressure loss in the pipe [bar]
  private double frictionPressureLoss;

  // Total pressure drop in the pipe [bar]
  private double pressureDrop;

  // Number of pipe increments for calculations
  private int numberOfIncrements = 5;

  // Length of the pipe [m]
  private double totalLength = Double.NaN;

  // Elevation of the pipe [m]
  private double totalElevation = Double.NaN;

  // Angle of the pipe [degrees]
  private double angle = Double.NaN;

  // Density of the liquid in the mixture in case of water and oil phases present
  // together
  private double mixtureLiquidDensity;

  // Viscosity of the liquid in the mixture in case of water and oil phases
  // present together
  private double mixtureLiquidViscosity;

  // Mass fraction of oil in the mixture in case of water and oil phases present
  // together
  private double mixtureOilMassFraction;

  // Volume fraction of oil in the mixture in case of water and oil phases present
  // together
  private double mixtureOilVolumeFraction;

  private double cumulativeLength;

  private double cumulativeElevation;

  // For segment calculation
  double length;
  double elevation;

  // Results initialization (for each segment)

  private List<Double> pressureProfile;
  private List<Double> temperatureProfile;
  private List<Double> pressureDropProfile;
  private List<FlowRegime> flowRegimeProfile;

  private List<Double> liquidSuperficialVelocityProfile;
  private List<Double> gasSuperficialVelocityProfile;
  private List<Double> mixtureSuperficialVelocityProfile;

  private List<Double> mixtureViscosityProfile;
  private List<Double> mixtureDensityProfile;
  private List<Double> liquidDensityProfile;

  private List<Double> liquidHoldupProfile;
  private List<Double> mixtureReynoldsNumber;

  private List<Double> lengthProfile;
  private List<Double> elevationProfile;
  private List<Integer> incrementsProfile;

  private boolean transientInitialized = false;
  private List<Double> transientPressureProfile;
  private List<Double> transientTemperatureProfile;
  private List<Double> transientMassFlowProfile;
  private List<Double> transientVelocityProfile;
  private List<Double> transientDensityProfile;
  private double segmentLengthMeters = Double.NaN;
  private double crossSectionArea = Double.NaN;

  private static final double MIN_TRANSIT_VELOCITY = 1.0e-3;
  private static final double MIN_DENSITY = 1.0e-6;

  /** Flag indicating if pipeline has been auto-sized. */
  private boolean autoSized = false;

  // Flag to run isothermal calculations
  private boolean runAdiabatic = true;
  private boolean runConstantSurfaceTemperature = false;

  private double constantSurfaceTemperature;

  private double heatTransferCoefficient;

  private HeatTransferMode heatTransferMode = HeatTransferMode.ESTIMATED_INNER_H;

  // Joule-Thomson effect: temperature change during gas expansion
  // When enabled, JT coefficient is calculated from gas phase thermodynamics
  private boolean includeJouleThomsonEffect = false;

  // Friction heating parameters for viscous dissipation
  // When enabled, friction pressure losses are converted to thermal energy in the
  // fluid
  private boolean includeFrictionHeating = false;

  // Heat transfer parameters
  double Tmi; // medium temperature
  double Tmo; // outlet temperature
  double Ts; // wall temperature
  double error; // error in heat transfer
  double iterationT; // iteration in heat transfer
  double dTlm; // log mean temperature difference
  double cp; // heat capacity
  double q1; // heat transfer
  double q2;
  double ReNoSlip;
  double S = 0;
  double rhoNoSlip = 0;
  double muNoSlip = 0;
  double thermalConductivity;
  double Pr; // Prandtl number
  double frictionFactor;
  double frictionTwoPhase;
  double Nu;
  double criticalPressure;
  double hmax;
  double X;

  // Overall heat transfer coefficient parameters
  private double outerHeatTransferCoefficient = Double.NaN; // W/(m²·K) - external convection
  private double pipeWallThermalConductivity = 45.0; // W/(m·K) - default carbon steel
  private double insulationThickness = 0.0; // m - insulation thickness
  private double insulationThermalConductivity = 0.04; // W/(m·K) - default mineral wool

  /**
   * Constructor for PipeBeggsAndBrills.
   *
   * @param name name of pipe
   */
  public PipeBeggsAndBrills(String name) {
    super(name);
  }

  /**
   * Constructor for PipeBeggsAndBrills.
   *
   * @param name name of pipe
   * @param inStream input stream
   */
  public PipeBeggsAndBrills(String name, StreamInterface inStream) {
    super(name, inStream);
  }

  /**
   * <p>
   * Setter for the field <code>pipeSpecification</code>.
   * </p>
   *
   * @param nominalDiameter a double in inch
   * @param pipeSec a {@link java.lang.String} object
   */
  public void setPipeSpecification(double nominalDiameter, String pipeSec) {
    this.pipeSpecification = pipeSec;
    this.nominalDiameter = nominalDiameter;
    this.PipeSpecSet = true;

    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase()) {
      java.sql.ResultSet dataSet =
          database.getResultSet("SELECT * FROM pipedata where Size='" + nominalDiameter + "'");
      try {
        if (dataSet.next()) {
          this.pipeThickness = Double.parseDouble(dataSet.getString(pipeSpecification)) / 1000;
          this.insideDiameter =
              (Double.parseDouble(dataSet.getString("OD"))) / 1000 - 2 * this.pipeThickness;
        }
      } catch (NumberFormatException e) {
        logger.error(e.getMessage());
      } catch (SQLException e) {
        logger.error(e.getMessage());
      }
    } catch (SQLException e) {
      logger.error(e.getMessage());
    }
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    return outStream.getThermoSystem();
  }

  /** {@inheritDoc} */
  @Override
  public void setElevation(double elevation) {
    this.totalElevation = elevation;
  }

  /** {@inheritDoc} */
  @Override
  public void setLength(double length) {
    this.totalLength = length;
  }

  /** {@inheritDoc} */
  @Override
  public void setDiameter(double diameter) {
    insideDiameter = diameter;
  }

  /**
   * <p>
   * setThickness.
   * </p>
   *
   * @param pipeThickness the thickness to set
   */
  public void setThickness(double pipeThickness) {
    this.pipeThickness = pipeThickness;
  }

  /**
   * <p>
   * getThickness.
   * </p>
   *
   * @return a double
   */
  public double getThickness() {
    return this.pipeThickness;
  }

  /**
   * <p>
   * Setter for the field <code>angle</code>.
   * </p>
   *
   * @param angle a double
   */
  public void setAngle(double angle) {
    this.angle = angle;
  }

  /**
   * <p>
   * Setter for the field <code>pipeWallRoughness</code>.
   * </p>
   *
   * @param pipeWallRoughness the pipeWallRoughness to set
   */
  public void setPipeWallRoughness(double pipeWallRoughness) {
    this.pipeWallRoughness = pipeWallRoughness;
  }

  /**
   * <p>
   * Setter for the field <code>numberOfIncrements</code>.
   * </p>
   *
   * @param numberOfIncrements a int
   */
  public void setNumberOfIncrements(int numberOfIncrements) {
    this.numberOfIncrements = numberOfIncrements;
  }

  /**
   * Sets whether to run isothermal calculations.
   *
   * @param runIsothermal a boolean
   * @deprecated Use {@link #setHeatTransferMode(HeatTransferMode)} with
   *             {@link HeatTransferMode#ISOTHERMAL} instead
   */
  @Deprecated
  public void setRunIsothermal(boolean runIsothermal) {
    this.runIsothermal = runIsothermal;
    if (runIsothermal) {
      this.heatTransferMode = HeatTransferMode.ISOTHERMAL;
    }
  }

  /**
   * <p>
   * Setter for the field <code>constantSurfaceTemperature</code>.
   * </p>
   *
   * @param temperature a double
   * @param unit a {@link java.lang.String} object
   */
  public void setConstantSurfaceTemperature(double temperature, String unit) {
    if (unit.equals("K")) {
      this.constantSurfaceTemperature = temperature;
    } else if (unit.equals("C")) {
      this.constantSurfaceTemperature = temperature + 273.15;
    } else {
      throw new RuntimeException("unit not supported " + unit);
    }
    this.runIsothermal = false;
    this.runAdiabatic = false;
    this.runConstantSurfaceTemperature = true;
  }

  /**
   * Sets the heat transfer calculation mode.
   *
   * <p>
   * Available modes:
   * <ul>
   * <li>ADIABATIC: No heat transfer (Q=0)</li>
   * <li>ISOTHERMAL: Constant temperature along the pipe</li>
   * <li>SPECIFIED_U: Use a user-specified overall U-value</li>
   * <li>ESTIMATED_INNER_H: Calculate h from flow (Gnielinski), use as U</li>
   * <li>DETAILED_U: Calculate full U including wall, insulation, outer convection</li>
   * </ul>
   *
   * @param mode the heat transfer calculation mode
   */
  public void setHeatTransferMode(HeatTransferMode mode) {
    this.heatTransferMode = mode;
    // Update legacy flags for backward compatibility
    switch (mode) {
      case ADIABATIC:
        this.runAdiabatic = true;
        this.runIsothermal = false;
        this.runConstantSurfaceTemperature = false;
        break;
      case ISOTHERMAL:
        this.runIsothermal = true;
        this.runAdiabatic = false;
        this.runConstantSurfaceTemperature = false;
        break;
      case SPECIFIED_U:
      case ESTIMATED_INNER_H:
      case DETAILED_U:
        this.runIsothermal = false;
        this.runAdiabatic = false;
        this.runConstantSurfaceTemperature = true;
        break;
    }
  }

  /**
   * Gets the current heat transfer calculation mode.
   *
   * @return the heat transfer mode
   */
  public HeatTransferMode getHeatTransferMode() {
    return heatTransferMode;
  }

  /**
   * Sets the overall heat transfer coefficient (U-value) and switches to SPECIFIED_U mode.
   *
   * <p>
   * This is the effective U-value used in the heat transfer equation Q = U * A * LMTD. When set,
   * the mode automatically changes to SPECIFIED_U, meaning this value is used directly without
   * flow-based calculation.
   * </p>
   *
   * @param heatTransferCoefficient the overall heat transfer coefficient in W/(m²·K)
   * @throws IllegalArgumentException if heatTransferCoefficient is negative
   */
  public void setHeatTransferCoefficient(double heatTransferCoefficient) {
    if (heatTransferCoefficient < 0) {
      throw new IllegalArgumentException(
          "Heat transfer coefficient must be non-negative, got: " + heatTransferCoefficient);
    }
    this.heatTransferCoefficient = heatTransferCoefficient;
    this.heatTransferMode = HeatTransferMode.SPECIFIED_U;
  }

  /**
   * Sets the specified outlet pressure and switches to flow rate calculation mode. When outlet
   * pressure is specified, the run() method will iterate to find the flow rate that achieves the
   * specified outlet pressure.
   *
   * @param pressure the desired outlet pressure in bara
   */
  public void setOutletPressure(double pressure) {
    this.specifiedOutletPressure = pressure;
    this.specifiedOutletPressureUnit = "bara";
    this.calculationMode = CalculationMode.CALCULATE_FLOW_RATE;
  }

  /**
   * Sets the specified outlet pressure with unit and switches to flow rate calculation mode. When
   * outlet pressure is specified, the run() method will iterate to find the flow rate that achieves
   * the specified outlet pressure.
   *
   * @param pressure the desired outlet pressure
   * @param unit the pressure unit (e.g., "bara", "barg", "Pa", "MPa")
   */
  public void setOutletPressure(double pressure, String unit) {
    this.specifiedOutletPressure = pressure;
    this.specifiedOutletPressureUnit = unit;
    this.calculationMode = CalculationMode.CALCULATE_FLOW_RATE;
  }

  /**
   * Gets the specified outlet pressure.
   *
   * @return the specified outlet pressure in the unit set, or NaN if not specified
   */
  public double getSpecifiedOutletPressure() {
    return specifiedOutletPressure;
  }

  /**
   * Gets the specified outlet pressure unit.
   *
   * @return the pressure unit
   */
  public String getSpecifiedOutletPressureUnit() {
    return specifiedOutletPressureUnit;
  }

  /**
   * Sets the calculation mode for the pipeline.
   *
   * @param mode the calculation mode (CALCULATE_OUTLET_PRESSURE or CALCULATE_FLOW_RATE)
   */
  public void setCalculationMode(CalculationMode mode) {
    this.calculationMode = mode;
  }

  /**
   * Gets the current calculation mode.
   *
   * @return the calculation mode
   */
  public CalculationMode getCalculationMode() {
    return calculationMode;
  }

  /**
   * Sets the maximum number of iterations for flow rate calculation when outlet pressure is
   * specified.
   *
   * @param maxIterations the maximum number of iterations
   */
  public void setMaxFlowIterations(int maxIterations) {
    this.maxFlowIterations = maxIterations;
  }

  /**
   * Sets the convergence tolerance for flow rate calculation when outlet pressure is specified.
   *
   * @param tolerance the relative convergence tolerance (default 1e-4)
   */
  public void setFlowConvergenceTolerance(double tolerance) {
    this.flowConvergenceTolerance = tolerance;
  }

  /**
   * Converts the input values from the system measurement units to imperial units. Needed because
   * the main equations and coefficients are developed for imperial system
   * <p>
   * The conversions applied are:
   * </p>
   * <ul>
   * <li>Inside Diameter (m) - (feet): multiplied by 3.2808399</li>
   * <li>Angle (m) - (feet): multiplied by 0.01745329</li>
   * <li>Elevation (m) - (feet): multiplied by 3.2808399</li>
   * <li>Length (m) - (feet): multiplied by 3.2808399</li>
   * <li>Pipe Wall Roughness (m) - (feet): multiplied by 3.2808399</li>
   * </ul>
   */
  public void convertSystemUnitToImperial() {
    insideDiameter = insideDiameter * 3.2808399;
    angle = 0.01745329 * angle;
    elevation = elevation * 3.2808399;
    length = length * 3.2808399;
    pipeWallRoughness = pipeWallRoughness * 3.2808399;
  }

  /**
   * Converts the input values from imperial units to the system measurement units. Needed because
   * the main equations and coefficients are developed for imperial system
   * <p>
   * The conversions applied are the inverse of those in the {@link #convertSystemUnitToImperial()}
   * method:
   * </p>
   * <ul>
   * <li>Inside Diameter (ft - m): divided by 3.2808399</li>
   * <li>Angle (ft - m): divided by 0.01745329</li>
   * <li>Elevation (ft - m): divided by 3.2808399</li>
   * <li>Length (ft - m): divided by 3.2808399</li>
   * <li>Pipe Wall Roughness (ft - m): divided by 3.2808399</li>
   * <li>Pressure Drop (lb/inch) -(bar): multiplied by 1.48727E-05</li>
   * </ul>
   */
  public void convertSystemUnitToMetric() {
    insideDiameter = insideDiameter / 3.2808399;
    angle = angle / 0.01745329;
    elevation = elevation / 3.2808399;
    length = length / 3.2808399;
    pipeWallRoughness = pipeWallRoughness / 3.2808399;
    pressureDrop = pressureDrop * 1.48727E-05;
    // Also convert friction and hydrostatic components (same conversion as
    // pressureDrop)
    frictionPressureLoss = frictionPressureLoss * 1.48727E-05;
    hydrostaticPressureDrop = hydrostaticPressureDrop * 1.48727E-05;
  }

  /**
   * <p>
   * calculateMissingValue.
   * </p>
   */
  public void calculateMissingValue() {
    if (Double.isNaN(totalLength)) {
      totalLength = calculateLength();
    } else if (Double.isNaN(totalElevation)) {
      totalElevation = calculateElevation();
    } else if (Double.isNaN(angle)) {
      angle = calculateAngle();
    }
    if (Math.abs(totalElevation) > Math.abs(totalLength)) {
      throw new RuntimeException(
          new neqsim.util.exception.InvalidInputException("PipeBeggsAndBrills", "calcMissingValue",
              "elevation", "- cannot be higher than length of the pipe" + length));
    }
    if (Double.isNaN(totalElevation) || Double.isNaN(totalLength) || Double.isNaN(angle)
        || Double.isNaN(insideDiameter)) {
      throw new RuntimeException(
          new neqsim.util.exception.InvalidInputException("PipeBeggsAndBrills", "calcMissingValue",
              "elevation or length or angle or inlet diameter", "cannot be null"));
    }
  }

  /**
   * Calculates the length based on the elevation and angle.
   *
   * @return the calculated length.
   */
  private double calculateLength() {
    return totalElevation / Math.sin(Math.toRadians(angle));
  }

  /**
   * Calculates the elevation based on the length and angle.
   *
   * @return the calculated elevation.
   */
  private double calculateElevation() {
    return totalLength * Math.sin(Math.toRadians(angle));
  }

  /**
   * Calculates the angle based on the length and elevation.
   *
   * @return the calculated angle.
   */
  private double calculateAngle() {
    return Math.toDegrees(Math.asin(totalElevation / totalLength));
  }

  /**
   * <p>
   * calcFlowRegime.
   * </p>
   *
   * @return the determined flow regime
   */
  public FlowRegime calcFlowRegime() {
    // Calc input volume fraction
    area = (Math.PI / 4.0) * Math.pow(insideDiameter, 2.0);
    if (system.getNumberOfPhases() != 1) {
      if (system.getNumberOfPhases() == 3) {
        supLiquidVel =
            (system.getPhase(1).getFlowRate("ft3/sec") + system.getPhase(2).getFlowRate("ft3/sec"))
                / area;
      } else {
        supLiquidVel = system.getPhase(1).getFlowRate("ft3/sec") / area;
      }

      supGasVel = system.getPhase(0).getFlowRate("ft3/sec") / area;
      supMixVel = supLiquidVel + supGasVel;

      mixtureFroudeNumber = Math.pow(supMixVel, 2) / (32.174 * insideDiameter);
      inputVolumeFractionLiquid = supLiquidVel / supMixVel;
    } else {
      if (system.hasPhaseType("gas")) {
        supGasVel = system.getPhase(0).getFlowRate("ft3/sec") / area;
        supMixVel = supGasVel;
        inputVolumeFractionLiquid = 0.0;
        regime = FlowRegime.SINGLE_PHASE;
      } else {
        // Single-phase liquid: only phase is at index 0
        supLiquidVel = system.getPhase(0).getFlowRate("ft3/sec") / area;
        supMixVel = supLiquidVel;
        inputVolumeFractionLiquid = 1.0;
        regime = FlowRegime.SINGLE_PHASE;
      }
    }

    liquidSuperficialVelocityProfile.add(supLiquidVel / 3.2808399); // to meters
    gasSuperficialVelocityProfile.add(supGasVel / 3.2808399);
    mixtureSuperficialVelocityProfile.add(supMixVel / 3.2808399);

    // Beggs and Brill (1973) flow regime boundary correlations
    // Reference: Beggs, H.D. and Brill, J.P., "A Study of Two-Phase Flow in
    // Inclined Pipes",
    // Journal of Petroleum Technology, May 1973, pp. 607-617
    // L1, L2, L3, L4 define boundaries between Segregated, Intermittent,
    // Distributed regimes
    double L1 = 316 * Math.pow(inputVolumeFractionLiquid, 0.302);
    double L2 = 0.0009252 * Math.pow(inputVolumeFractionLiquid, -2.4684);
    double L3 = 0.1 * Math.pow(inputVolumeFractionLiquid, -1.4516);
    double L4 = 0.5 * Math.pow(inputVolumeFractionLiquid, -6.738);

    if (regime != FlowRegime.SINGLE_PHASE) {
      if ((inputVolumeFractionLiquid < 0.01 && mixtureFroudeNumber < L1)
          || (inputVolumeFractionLiquid >= 0.01 && mixtureFroudeNumber < L2)) {
        regime = FlowRegime.SEGREGATED;
      } else if ((inputVolumeFractionLiquid < 0.4 && inputVolumeFractionLiquid >= 0.01
          && mixtureFroudeNumber <= L1 && mixtureFroudeNumber > L3)
          || (inputVolumeFractionLiquid >= 0.4 && mixtureFroudeNumber <= L4
              && mixtureFroudeNumber > L3)) {
        regime = FlowRegime.INTERMITTENT;
      } else if ((inputVolumeFractionLiquid < 0.4 && mixtureFroudeNumber >= L4)
          || (inputVolumeFractionLiquid >= 0.4 && mixtureFroudeNumber > L4)) {
        regime = FlowRegime.DISTRIBUTED;
      } else if (mixtureFroudeNumber > L2 && mixtureFroudeNumber < L3) {
        regime = FlowRegime.TRANSITION;
      } else if (inputVolumeFractionLiquid < 0.1 || inputVolumeFractionLiquid > 0.9) {
        regime = FlowRegime.INTERMITTENT;
      } else if (mixtureFroudeNumber > 110) {
        regime = FlowRegime.INTERMITTENT;
      } else {
        throw new RuntimeException(new neqsim.util.exception.InvalidOutputException(
            "PipeBeggsAndBrills", "run: calcFlowRegime", "FlowRegime", "Flow regime is not found"));
      }
    }

    A = (L3 - mixtureFroudeNumber) / (L3 - L2);

    flowRegimeProfile.add(regime);
    return regime;
  }

  /**
   * <p>
   * calcHydrostaticPressureDifference.
   * </p>
   *
   * @return a double
   */
  public double calcHydrostaticPressureDifference() {
    double B = 1 - A;

    double BThetta;

    if (regime == FlowRegime.SEGREGATED) {
      El = 0.98 * Math.pow(inputVolumeFractionLiquid, 0.4846)
          / Math.pow(mixtureFroudeNumber, 0.0868);
    } else if (regime == FlowRegime.INTERMITTENT) {
      El = 0.845 * Math.pow(inputVolumeFractionLiquid, 0.5351)
          / (Math.pow(mixtureFroudeNumber, 0.0173));
    } else if (regime == FlowRegime.DISTRIBUTED) {
      El = 1.065 * Math.pow(inputVolumeFractionLiquid, 0.5824)
          / (Math.pow(mixtureFroudeNumber, 0.0609));
    } else if (regime == FlowRegime.TRANSITION) {
      El = A * 0.98 * Math.pow(inputVolumeFractionLiquid, 0.4846)
          / Math.pow(mixtureFroudeNumber, 0.0868)
          + B * 0.845 * Math.pow(inputVolumeFractionLiquid, 0.5351)
              / (Math.pow(mixtureFroudeNumber, 0.0173));
    } else if (regime == FlowRegime.SINGLE_PHASE) {
      // For single-phase flow, liquid holdup equals liquid volume fraction
      // Gas: El = 0, Liquid: El = 1
      El = inputVolumeFractionLiquid;
    }

    if (regime != FlowRegime.SINGLE_PHASE) {
      double SG;
      if (system.getNumberOfPhases() == 3) {
        mixtureOilMassFraction = system.getPhase(1).getFlowRate("kg/hr")
            / (system.getPhase(1).getFlowRate("kg/hr") + system.getPhase(2).getFlowRate("kg/hr"));
        mixtureOilVolumeFraction = system.getPhase(1).getVolume()
            / (system.getPhase(1).getVolume() + system.getPhase(2).getVolume());

        mixtureLiquidViscosity = system.getPhase(1).getViscosity("cP") * mixtureOilVolumeFraction
            + (system.getPhase(2).getViscosity("cP")) * (1 - mixtureOilVolumeFraction);

        mixtureLiquidDensity = (system.getPhase(1).getDensity("lb/ft3") * mixtureOilMassFraction
            + system.getPhase(2).getDensity("lb/ft3") * (1 - mixtureOilMassFraction));

        SG = (mixtureLiquidDensity) / (1000 * 0.0624279606);
      } else {
        SG = system.getPhase(1).getDensity("lb/ft3") / (1000 * 0.0624279606);
      }

      double APIgrav = (141.5 / (SG)) - 131.0;
      double sigma68 = 39.0 - 0.2571 * APIgrav;
      double sigma100 = 37.5 - 0.2571 * APIgrav;
      double sigma;

      if (system.getTemperature("C") * (9.0 / 5.0) + 32.0 > 100.0) {
        sigma = sigma100;
      } else if (system.getTemperature("C") * (9.0 / 5.0) + 32.0 < 68.0) {
        sigma = sigma68;
      } else {
        sigma = sigma68 + (system.getTemperature("C") * (9.0 / 5.0) + 32.0 - 68.0)
            * (sigma100 - sigma68) / (100.0 - 68.0);
      }
      double pressureCorrection = 1.0 - 0.024 * Math.pow((system.getPressure("psi")), 0.45);
      sigma = sigma * pressureCorrection;
      double Nvl = 1.938 * supLiquidVel
          * Math.pow(system.getPhase(1).getDensity() * 0.0624279606 / (32.2 * sigma), 0.25);
      double betta = 0;

      if (elevation > 0) {
        if (regime == FlowRegime.SEGREGATED) {
          double logArg = 0.011 * Math.pow(Nvl, 3.539)
              / (Math.pow(inputVolumeFractionLiquid, 3.768) * Math.pow(mixtureFroudeNumber, 1.614));
          if (logArg > 0) {
            betta = (1 - inputVolumeFractionLiquid) * Math.log(logArg);
          }
        } else if (regime == FlowRegime.INTERMITTENT) {
          double logArg = 2.96 * Math.pow(inputVolumeFractionLiquid, 0.305)
              * Math.pow(mixtureFroudeNumber, 0.0978) / (Math.pow(Nvl, 0.4473));
          if (logArg > 0) {
            betta = (1 - inputVolumeFractionLiquid) * Math.log(logArg);
          }
        } else if (regime == FlowRegime.DISTRIBUTED) {
          betta = 0;
        }
      } else {
        double logArg = 4.70 * Math.pow(Nvl, 0.1244)
            / (Math.pow(inputVolumeFractionLiquid, 0.3692) * Math.pow(mixtureFroudeNumber, 0.5056));
        if (logArg > 0) {
          betta = (1 - inputVolumeFractionLiquid) * Math.log(logArg);
        }
      }
      betta = (betta > 0) ? betta : 0;
      BThetta = 1 + betta * (Math.sin(1.8 * angle * 0.01745329)
          - (1.0 / 3.0) * Math.pow(Math.sin(1.8 * angle * 0.01745329), 3.0));

      El = BThetta * El;
      if (system.getNumberOfPhases() == 3) {
        mixtureDensity =
            mixtureLiquidDensity * El + system.getPhase(0).getDensity("lb/ft3") * (1 - El);
      } else {
        mixtureDensity = system.getPhase(1).getDensity("lb/ft3") * El
            + system.getPhase(0).getDensity("lb/ft3") * (1 - El);
      }
    } else {
      // Single-phase: only phase is at index 0
      mixtureDensity = system.getPhase(0).getDensity("lb/ft3");
    }
    hydrostaticPressureDrop = mixtureDensity * 32.2 * elevation; // 32.2 - g

    liquidHoldupProfile.add(El);

    return hydrostaticPressureDrop;
  }

  /**
   * <p>
   * calcFrictionPressureLoss.
   * </p>
   *
   * @return a double
   */
  public double calcFrictionPressureLoss() {
    double S = 0;
    double rhoNoSlip = 0;
    double muNoSlip = 0;

    if (system.getNumberOfPhases() != 1) {
      if (regime != FlowRegime.SINGLE_PHASE) {
        double y = inputVolumeFractionLiquid / (Math.pow(El, 2));
        if (1 < y && y < 1.2) {
          S = Math.log(2.2 * y - 1.2);
        } else {
          S = Math.log(y) / (-0.0523 + 3.18 * Math.log(y) - 0.872 * Math.pow(Math.log(y), 2.0)
              + 0.01853 * Math.pow(Math.log(y), 4));
        }
        if (system.getNumberOfPhases() == 3) {
          rhoNoSlip = mixtureLiquidDensity * inputVolumeFractionLiquid
              + (system.getPhase(0).getDensity("lb/ft3")) * (1 - inputVolumeFractionLiquid);
          muNoSlip = mixtureLiquidViscosity * inputVolumeFractionLiquid
              + (system.getPhase(0).getViscosity("cP")) * (1 - inputVolumeFractionLiquid);
          liquidDensityProfile.add(mixtureLiquidDensity * 16.01846);
        } else {
          rhoNoSlip = (system.getPhase(1).getDensity("lb/ft3")) * inputVolumeFractionLiquid
              + (system.getPhase(0).getDensity("lb/ft3")) * (1 - inputVolumeFractionLiquid);
          muNoSlip = system.getPhase(1).getViscosity("cP") * inputVolumeFractionLiquid
              + (system.getPhase(0).getViscosity("cP")) * (1 - inputVolumeFractionLiquid);
          liquidDensityProfile.add((system.getPhase(1).getDensity("lb/ft3")) * 16.01846);
        }
      } else {
        rhoNoSlip = (system.getPhase(1).getDensity("lb/ft3")) * inputVolumeFractionLiquid
            + (system.getPhase(0).getDensity("lb/ft3")) * (1 - inputVolumeFractionLiquid);
        muNoSlip = system.getPhase(1).getViscosity("cP") * inputVolumeFractionLiquid
            + (system.getPhase(0).getViscosity("cP")) * (1 - inputVolumeFractionLiquid);
        liquidDensityProfile.add((system.getPhase(1).getDensity("lb/ft3")) * 16.01846);
      }
    } else {
      // Single-phase: only phase is at index 0
      rhoNoSlip = (system.getPhase(0).getDensity("lb/ft3"));
      muNoSlip = (system.getPhase(0).getViscosity("cP"));
      if (system.hasPhaseType("gas")) {
        liquidDensityProfile.add(0.0);
      } else {
        liquidDensityProfile.add(rhoNoSlip * 16.01846);
      }
    }

    mixtureViscosityProfile.add(muNoSlip);
    mixtureDensityProfile.add(rhoNoSlip * 16.01846);

    // Reynolds number calculation with unit conversions:
    // - rhoNoSlip is in lb/ft³, supMixVel in ft/s, insideDiameter in ft
    // - muNoSlip is in cP (centipoise) = 0.001 Pa·s = 0.001 kg/(m·s)
    // - Factor 16/(3.28²) ≈ 1.486 converts (lb/ft³)*(ft/s)*(ft) to kg/(m·s) to
    // match viscosity
    // Derivation: 1 lb = 0.4536 kg, 1 ft = 0.3048 m
    // (lb/ft³)*(ft/s)*(ft) = lb/(ft·s) = 0.4536/0.3048 kg/(m·s) ≈ 1.488 kg/(m·s)
    ReNoSlip = rhoNoSlip * supMixVel * insideDiameter * (16.0 / (3.28 * 3.28)) / (0.001 * muNoSlip);

    mixtureReynoldsNumber.add(ReNoSlip);

    double E = pipeWallRoughness / insideDiameter;

    // Calculate friction factor with proper flow regime handling
    if (Math.abs(ReNoSlip) < 1e-10) {
      frictionFactor = 0.0;
    } else if (Math.abs(ReNoSlip) < 2300) {
      // Laminar flow
      frictionFactor = 64.0 / ReNoSlip;
    } else if (Math.abs(ReNoSlip) < 4000) {
      // Transition zone - interpolate between laminar and turbulent
      double fLaminar = 64.0 / 2300.0;
      double fTurbulent =
          Math.pow(1 / (-1.8 * Math.log10(Math.pow(E / 3.7, 1.11) + (6.9 / 4000.0))), 2);
      frictionFactor = fLaminar + (fTurbulent - fLaminar) * (ReNoSlip - 2300.0) / 1700.0;
    } else {
      // Turbulent flow - Haaland equation
      // f = (1 / (-1.8 * log10((ε/D/3.7)^1.11 + 6.9/Re)))^2
      frictionFactor =
          Math.pow(1 / (-1.8 * Math.log10(Math.pow(E / 3.7, 1.11) + (6.9 / ReNoSlip))), 2);
    }
    frictionTwoPhase = frictionFactor * Math.exp(S);

    frictionPressureLoss =
        frictionTwoPhase * Math.pow(supMixVel, 2) * rhoNoSlip * (length) / (2 * insideDiameter);
    return frictionPressureLoss;
  }

  /**
   * <p>
   * calcPressureDrop.
   * </p>
   *
   * @return a double
   */
  public double calcPressureDrop() {
    convertSystemUnitToImperial();
    regime = FlowRegime.UNKNOWN;
    calcFlowRegime();
    hydrostaticPressureDrop = calcHydrostaticPressureDifference();
    frictionPressureLoss = calcFrictionPressureLoss();
    pressureDrop = (hydrostaticPressureDrop + frictionPressureLoss);
    convertSystemUnitToMetric();
    iteration = iteration + 1;
    return pressureDrop;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // Input validation
    if (insideDiameter <= 0) {
      throw new RuntimeException(
          new neqsim.util.exception.InvalidInputException("PipeBeggsAndBrills", "run",
              "insideDiameter", "must be positive, got: " + insideDiameter));
    }
    if (numberOfIncrements <= 0) {
      throw new RuntimeException(
          new neqsim.util.exception.InvalidInputException("PipeBeggsAndBrills", "run",
              "numberOfIncrements", "must be positive, got: " + numberOfIncrements));
    }

    if (calculationMode == CalculationMode.CALCULATE_FLOW_RATE) {
      runWithSpecifiedOutletPressure(id);
    } else {
      runWithSpecifiedFlowRate(id);
    }
  }

  /**
   * Run pipeline calculation with specified flow rate (calculate outlet pressure). This is the
   * default calculation mode.
   *
   * @param id calculation identifier
   */
  private void runWithSpecifiedFlowRate(UUID id) {
    iteration = 0;
    transientInitialized = false;

    pressureProfile = new ArrayList<>();
    temperatureProfile = new ArrayList<>();

    pressureDropProfile = new ArrayList<>();
    flowRegimeProfile = new ArrayList<>();

    liquidSuperficialVelocityProfile = new ArrayList<>();
    gasSuperficialVelocityProfile = new ArrayList<>();
    mixtureSuperficialVelocityProfile = new ArrayList<>();

    mixtureViscosityProfile = new ArrayList<>();
    mixtureDensityProfile = new ArrayList<>();
    liquidDensityProfile = new ArrayList<>();
    liquidHoldupProfile = new ArrayList<>();
    mixtureReynoldsNumber = new ArrayList<>();

    lengthProfile = new ArrayList<>();
    elevationProfile = new ArrayList<>();
    incrementsProfile = new ArrayList<>();

    calculateMissingValue();
    double enthalpyInlet = Double.NaN;

    // Calculate effective length including fittings equivalent length
    // The equivalent length from fittings (bends, valves, etc.) is added to the physical length
    // for pressure drop calculations. Note: elevation is NOT affected by fittings.
    double effectiveTotalLength = totalLength + getEquivalentLength();
    length = effectiveTotalLength / numberOfIncrements;
    elevation = totalElevation / numberOfIncrements;
    system = inStream.getThermoSystem().clone();
    ThermodynamicOperations testOps = new ThermodynamicOperations(system);
    testOps.TPflash();
    system.initProperties();

    if (!runIsothermal) {
      enthalpyInlet = system.getEnthalpy();
    }
    double pipeInletPressure = system.getPressure();
    cumulativeLength = 0.0;
    cumulativeElevation = 0.0;
    pressureProfile.add(system.getPressure()); // pressure at segment 0
    temperatureProfile.add(system.getTemperature()); // temperature at segment 0
    pressureDropProfile.add(0.0); // DP at segment 0
    for (int i = 1; i <= numberOfIncrements; i++) {
      lengthProfile.add(cumulativeLength);
      elevationProfile.add(cumulativeElevation);
      incrementsProfile.add(i - 1);

      cumulativeLength += length;
      cumulativeElevation += elevation;

      inletPressure = system.getPressure();
      pressureDrop = calcPressureDrop();
      pressureDropProfile.add(pressureDrop);
      pressureOut = inletPressure - pressureDrop;
      pressureProfile.add(pressureOut);

      if (pressureOut < 0) {
        throw new RuntimeException(new neqsim.util.exception.InvalidOutputException(
            "PipeBeggsAndBrills", "run: calcOutletPressure", "pressure out",
            "- Outlet pressure is negative" + pressureOut));
      }

      system.setPressure(pressureOut);
      if (!runIsothermal) {
        enthalpyInlet = calcHeatBalance(enthalpyInlet, system, testOps);
        // testOps.PHflash(enthalpyInlet);
        temperatureProfile.add(system.getTemperature());
      } else {
        testOps.TPflash();
      }
      system.initProperties();
    }
    totalPressureDrop = pipeInletPressure - system.getPressure();
    calcPressureDrop(); // to initialize final parameters
    lengthProfile.add(cumulativeLength);
    elevationProfile.add(cumulativeElevation);
    incrementsProfile.add(getNumberOfIncrements());
    system.initProperties();
    outStream.setThermoSystem(system);
    outStream.setCalculationIdentifier(id);
  }

  /**
   * Run pipeline calculation with specified outlet pressure (calculate flow rate). Uses bisection
   * method to find the flow rate that achieves the target outlet pressure.
   *
   * @param id calculation identifier
   */
  private void runWithSpecifiedOutletPressure(UUID id) {
    if (Double.isNaN(specifiedOutletPressure)) {
      throw new RuntimeException(
          new neqsim.util.exception.InvalidInputException("PipeBeggsAndBrills", "run",
              "specifiedOutletPressure", "must be set when using CALCULATE_FLOW_RATE mode"));
    }

    // Convert specified outlet pressure to bara
    double targetPressure = specifiedOutletPressure;
    if (!specifiedOutletPressureUnit.equals("bara")) {
      // Create a temporary system to convert pressure units
      SystemInterface tempSystem = inStream.getThermoSystem().clone();
      tempSystem.setPressure(specifiedOutletPressure, specifiedOutletPressureUnit);
      targetPressure = tempSystem.getPressure("bara");
    }

    double inletPressureBara = inStream.getThermoSystem().getPressure("bara");
    if (targetPressure >= inletPressureBara) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(
          "PipeBeggsAndBrills", "run", "specifiedOutletPressure",
          "must be less than inlet pressure (" + inletPressureBara + " bara)"));
    }
    if (targetPressure <= 0) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(
          "PipeBeggsAndBrills", "run", "specifiedOutletPressure", "must be positive"));
    }

    // Save original flow rate
    String flowUnit = "kg/hr";
    double originalFlowRate = inStream.getFlowRate(flowUnit);

    // Use bisection method to find flow rate
    // Start with a wide range
    double flowLow = 1.0; // Minimum 1 kg/hr
    double flowHigh = originalFlowRate * 100.0; // Up to 100x original

    // First, find a valid low flow rate (where outlet pressure > target)
    double pressureAtLowFlow = tryCalculatePressure(flowLow, flowUnit, id);
    if (pressureAtLowFlow < targetPressure) {
      // Even at minimum flow, can't achieve target pressure
      inStream.setFlowRate(originalFlowRate, flowUnit);
      inStream.run();
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(
          "PipeBeggsAndBrills", "run", "specifiedOutletPressure",
          "cannot be achieved - pressure drop too high even at minimum flow"));
    }

    // Find a valid high flow rate (where outlet pressure < target)
    // Start from a reasonable multiple and increase if needed
    flowHigh = originalFlowRate * 2.0;
    double pressureAtHighFlow = tryCalculatePressure(flowHigh, flowUnit, id);

    // If pressure is still too high, increase flow rate
    int boundSearchIter = 0;
    while (pressureAtHighFlow > targetPressure && boundSearchIter < 20) {
      flowHigh *= 2.0;
      pressureAtHighFlow = tryCalculatePressure(flowHigh, flowUnit, id);
      boundSearchIter++;
    }

    // If we couldn't find a high bound with positive pressure that gives low enough
    // outlet pressure
    // it means we need very high flow (or it's infeasible)
    if (pressureAtHighFlow > targetPressure) {
      inStream.setFlowRate(originalFlowRate, flowUnit);
      inStream.run();
      throw new RuntimeException(
          new neqsim.util.exception.InvalidInputException("PipeBeggsAndBrills", "run",
              "specifiedOutletPressure", "cannot be achieved - requires extremely high flow rate"));
    }

    // Bisection iteration
    double flowMid = 0;
    double pressureMid = 0;
    int iterCount = 0;

    while (iterCount < maxFlowIterations) {
      flowMid = (flowLow + flowHigh) / 2.0;
      pressureMid = tryCalculatePressure(flowMid, flowUnit, id);

      double relativeError = Math.abs(pressureMid - targetPressure) / targetPressure;

      if (relativeError < flowConvergenceTolerance) {
        // Converged
        break;
      }

      if (pressureMid > targetPressure) {
        // Need more pressure drop, increase flow
        flowLow = flowMid;
      } else {
        // Need less pressure drop, decrease flow
        flowHigh = flowMid;
      }

      // Check if bounds have converged
      if (Math.abs(flowHigh - flowLow) / flowMid < flowConvergenceTolerance) {
        break;
      }

      iterCount++;
    }

    // Final run with converged flow rate - already done in tryCalculatePressure
    // Just ensure the state is set correctly
    inStream.setFlowRate(flowMid, flowUnit);
    inStream.run();
    runWithSpecifiedFlowRate(id);
  }

  /**
   * Helper method to calculate outlet pressure for a given flow rate, handling exceptions when
   * pressure goes negative (indicating flow rate is too high).
   *
   * @param flowRate the flow rate to test
   * @param flowUnit the unit for flow rate
   * @param id calculation identifier
   * @return the outlet pressure, or a very low value if calculation fails (pressure went negative)
   */
  private double tryCalculatePressure(double flowRate, String flowUnit, UUID id) {
    inStream.setFlowRate(flowRate, flowUnit);
    inStream.run();
    try {
      runWithSpecifiedFlowRate(id);
      return getOutletPressure();
    } catch (RuntimeException e) {
      // If calculation fails (e.g., negative pressure), return very low pressure
      // This helps the bisection algorithm know this flow rate is too high
      return -1e6; // Return a very negative value to indicate "too high flow"
    }
  }

  /**
   * Calculates the Nusselt number using the Gnielinski correlation for turbulent pipe flow. Valid
   * for 0.5 &lt; Pr &lt; 2000 and 3000 &lt; Re &lt; 5E6.
   *
   * @param Re Reynolds number
   * @param Pr Prandtl number
   * @return the Nusselt number
   */
  private double calcGnielinskiNu(double Re, double Pr) {
    // Gnielinski correlation: Nu = (f/8)(Re-1000)Pr / (1 +
    // 12.7*(f/8)^0.5*(Pr^(2/3)-1))
    // Uses the friction factor already calculated
    double f = frictionFactor;
    if (f <= 0) {
      // Fallback: use Petukhov friction factor approximation
      f = Math.pow(0.790 * Math.log(Re) - 1.64, -2);
    }
    return ((f / 8.0) * (Re - 1000.0) * Pr)
        / (1.0 + 12.7 * Math.pow(f / 8.0, 0.5) * (Math.pow(Pr, 2.0 / 3.0) - 1.0));
  }

  /**
   * Estimates the inner heat transfer coefficient for the given system.
   *
   * <p>
   * For single-phase flow, uses standard correlations:
   * <ul>
   * <li>Laminar (Re &lt; 2300): Nu = 3.66 (fully developed)</li>
   * <li>Transition (2300-3000): Linear interpolation</li>
   * <li>Turbulent (Re &gt; 3000): Gnielinski correlation</li>
   * </ul>
   *
   * <p>
   * For two-phase flow, uses Shah correlation enhancement factor.
   *
   * @param system the thermodynamic system for which the heat transfer coefficient is to be
   *        estimated
   * @return the estimated inner heat transfer coefficient [W/(m²·K)]
   */
  public double estimateHeatTransferCoefficent(SystemInterface system) {
    cp = system.getCp("J/kgK");
    thermalConductivity = system.getThermalConductivity();
    // Prandtl number: Pr = μ * Cp / k
    // viscosity in cP * 0.001 converts to Pa.s (kg/(m.s))
    Pr = 0.001 * system.getViscosity("cP") * cp / thermalConductivity;

    if (ReNoSlip < 2300) {
      // Laminar flow - constant Nusselt for fully developed pipe flow
      Nu = 3.66;
    } else if (ReNoSlip < 3000) {
      // Transition zone - interpolate between laminar and turbulent
      double NuLaminar = 3.66;
      double NuTurbulent = calcGnielinskiNu(3000, Pr);
      Nu = NuLaminar + (NuTurbulent - NuLaminar) * (ReNoSlip - 2300) / 700.0;
    } else {
      // Turbulent flow - Gnielinski correlation (valid for 0.5 < Pr < 2000, 3000 < Re
      // < 5E6)
      Nu = calcGnielinskiNu(ReNoSlip, Pr);
    }
    double innerHTC = Nu * thermalConductivity / insideDiameter;

    // Two-phase flow enhancement using Shah correlation approach
    if (system.getNumberOfPhases() > 1) {
      innerHTC = calcTwoPhaseHeatTransferCoefficient(system, innerHTC);
    }

    heatTransferCoefficient = innerHTC;

    // Calculate overall heat transfer coefficient if DETAILED_U mode
    if (heatTransferMode == HeatTransferMode.DETAILED_U) {
      heatTransferCoefficient = calcOverallHeatTransferCoefficient(innerHTC);
    }

    return heatTransferCoefficient;
  }

  /**
   * Calculates the two-phase heat transfer coefficient using Shah correlation.
   *
   * <p>
   * The Shah correlation provides enhancement factors for convective heat transfer in two-phase
   * flow. It accounts for the increased turbulence and interfacial effects in gas-liquid flow.
   *
   * @param system the thermodynamic system
   * @param singlePhaseHTC the single-phase heat transfer coefficient [W/(m²·K)]
   * @return the two-phase heat transfer coefficient [W/(m²·K)]
   */
  private double calcTwoPhaseHeatTransferCoefficient(SystemInterface system,
      double singlePhaseHTC) {
    // Get vapor quality (mass fraction of gas)
    X = system.getPhase(0).getFlowRate("kg/sec") / system.getFlowRate("kg/sec");

    // Protect against edge cases
    if (X <= 0.001 || X >= 0.999) {
      return singlePhaseHTC;
    }

    // Lockhart-Martinelli parameter for turbulent-turbulent flow
    double rhoGas = system.getPhase(0).getDensity("kg/m3");
    double rhoLiq = mixtureLiquidDensity > 0 ? mixtureLiquidDensity : system.getDensity("kg/m3");
    double muGas = 0.001 * system.getPhase(0).getViscosity("cP");
    double muLiq = 0.001 * mixtureLiquidViscosity;

    if (muLiq <= 0 || rhoLiq <= 0) {
      // Fallback to simple empirical correlation if properties unavailable
      double enhancement = 1.0 + 2.0 * X * (1.0 - X);
      return singlePhaseHTC * enhancement;
    }

    // Martinelli parameter Xtt
    double Xtt = Math.pow((1.0 - X) / X, 0.9) * Math.pow(rhoGas / rhoLiq, 0.5)
        * Math.pow(muLiq / muGas, 0.1);

    // Shah correlation enhancement factor E
    double E;
    if (Xtt > 0.1) {
      E = 1.0 + 3.8 / Math.pow(Xtt, 0.45);
    } else {
      // High quality region - use modified correlation
      E = 2.0 + 3.0 / Math.pow(Xtt, 0.5);
    }

    // Limit enhancement factor to reasonable range
    E = Math.min(E, 10.0);

    return singlePhaseHTC * E;
  }

  /**
   * Calculates the overall heat transfer coefficient including inner convection, pipe wall
   * conduction, insulation (if present), and outer convection.
   *
   * <p>
   * The overall U-value is based on the inner surface area and accounts for:
   * <ul>
   * <li>Inner convective resistance: 1/h_i</li>
   * <li>Pipe wall conductive resistance: (r_o/r_i) × ln(r_o/r_i) / k_wall</li>
   * <li>Insulation resistance (if present): (r_ins/r_i) × ln(r_ins/r_o) / k_ins</li>
   * <li>Outer convective resistance: (r_o/r_i) / h_o or (r_ins/r_i) / h_o</li>
   * </ul>
   *
   * @param innerHTC the inner heat transfer coefficient [W/(m²·K)]
   * @return the overall heat transfer coefficient based on inner area [W/(m²·K)]
   */
  private double calcOverallHeatTransferCoefficient(double innerHTC) {
    double ri = insideDiameter / 2.0; // inner radius

    // Handle missing pipe thickness - use 1% of diameter as default
    double wallThickness = pipeThickness;
    if (Double.isNaN(wallThickness) || wallThickness <= 0) {
      wallThickness = 0.01 * insideDiameter; // Default: 1% of diameter
    }
    double ro = ri + wallThickness; // outer radius of pipe wall
    double rins = ro + insulationThickness; // outer radius including insulation

    // Inner convective resistance
    double Ri = 1.0 / innerHTC;

    // Pipe wall conductive resistance (cylindrical)
    double Rwall = 0.0;
    if (pipeThickness > 0 && pipeWallThermalConductivity > 0) {
      Rwall = (ri / pipeWallThermalConductivity) * Math.log(ro / ri);
    }

    // Insulation resistance
    double Rins = 0.0;
    if (insulationThickness > 0 && insulationThermalConductivity > 0) {
      Rins = (ri / insulationThermalConductivity) * Math.log(rins / ro);
    }

    // Outer convective resistance
    double Ro = 0.0;
    if (!Double.isNaN(outerHeatTransferCoefficient) && outerHeatTransferCoefficient > 0) {
      double routermost = insulationThickness > 0 ? rins : ro;
      Ro = ri / (outerHeatTransferCoefficient * routermost);
    }

    // Total resistance and overall U-value
    double Rtotal = Ri + Rwall + Rins + Ro;
    return 1.0 / Rtotal;
  }

  /**
   * Sets the outer (external) heat transfer coefficient for calculating overall U-value.
   *
   * <p>
   * This is the convective heat transfer coefficient on the outside of the pipe (or insulation).
   * Typical values:
   * <ul>
   * <li>Still air: 5-10 W/(m²·K)</li>
   * <li>Moving air (wind): 10-50 W/(m²·K)</li>
   * <li>Still water: 100-500 W/(m²·K)</li>
   * <li>Flowing water (subsea): 200-1000 W/(m²·K)</li>
   * </ul>
   *
   * @param coefficient the outer heat transfer coefficient [W/(m²·K)]
   * @throws IllegalArgumentException if coefficient is negative
   */
  public void setOuterHeatTransferCoefficient(double coefficient) {
    if (coefficient < 0) {
      throw new IllegalArgumentException(
          "Outer heat transfer coefficient must be non-negative, got: " + coefficient);
    }
    this.outerHeatTransferCoefficient = coefficient;
  }

  /**
   * Gets the outer (external) heat transfer coefficient.
   *
   * @return the outer heat transfer coefficient [W/(m²·K)]
   */
  public double getOuterHeatTransferCoefficient() {
    return outerHeatTransferCoefficient;
  }

  /**
   * Sets the pipe wall thermal conductivity.
   *
   * <p>
   * Typical values:
   * <ul>
   * <li>Carbon steel: 45-50 W/(m·K)</li>
   * <li>Stainless steel: 15-20 W/(m·K)</li>
   * <li>Duplex steel: 15-17 W/(m·K)</li>
   * </ul>
   *
   * @param conductivity the thermal conductivity [W/(m·K)]
   */
  public void setPipeWallThermalConductivity(double conductivity) {
    this.pipeWallThermalConductivity = conductivity;
  }

  /**
   * Gets the pipe wall thermal conductivity.
   *
   * @return the thermal conductivity [W/(m·K)]
   */
  public double getPipeWallThermalConductivity() {
    return pipeWallThermalConductivity;
  }

  /**
   * Sets the insulation layer properties.
   *
   * <p>
   * Typical thermal conductivity values:
   * <ul>
   * <li>Mineral wool: 0.03-0.05 W/(m·K)</li>
   * <li>Polyurethane foam: 0.02-0.03 W/(m·K)</li>
   * <li>Polypropylene (wet insulation): 0.22-0.25 W/(m·K)</li>
   * <li>Syntactic foam (subsea): 0.10-0.15 W/(m·K)</li>
   * </ul>
   *
   * @param thickness the insulation thickness [m]
   * @param conductivity the thermal conductivity [W/(m·K)]
   * @throws IllegalArgumentException if thickness or conductivity is negative
   */
  public void setInsulation(double thickness, double conductivity) {
    if (thickness < 0) {
      throw new IllegalArgumentException(
          "Insulation thickness must be non-negative, got: " + thickness);
    }
    if (conductivity < 0) {
      throw new IllegalArgumentException(
          "Insulation thermal conductivity must be non-negative, got: " + conductivity);
    }
    this.insulationThickness = thickness;
    this.insulationThermalConductivity = conductivity;
  }

  /**
   * Gets the insulation thickness.
   *
   * @return the insulation thickness [m]
   */
  public double getInsulationThickness() {
    return insulationThickness;
  }

  /**
   * Gets the insulation thermal conductivity.
   *
   * @return the thermal conductivity [W/(m·K)]
   */
  public double getInsulationThermalConductivity() {
    return insulationThermalConductivity;
  }

  /**
   * Enables or disables use of detailed overall heat transfer coefficient calculation.
   *
   * <p>
   * When enabled (true), switches to DETAILED_U mode which includes pipe wall resistance,
   * insulation resistance (if set), and outer convection resistance (if set). When disabled
   * (false), switches to ESTIMATED_INNER_H mode which uses only the inner convective heat transfer
   * coefficient.
   *
   * @param use true to use DETAILED_U mode, false to use ESTIMATED_INNER_H mode
   * @deprecated Use {@link #setHeatTransferMode(HeatTransferMode)} instead
   */
  @Deprecated
  public void setUseOverallHeatTransferCoefficient(boolean use) {
    if (use) {
      this.heatTransferMode = HeatTransferMode.DETAILED_U;
    } else {
      this.heatTransferMode = HeatTransferMode.ESTIMATED_INNER_H;
    }
  }

  /**
   * Gets whether detailed overall heat transfer coefficient is being used.
   *
   * @return true if using DETAILED_U mode
   * @deprecated Use {@link #getHeatTransferMode()} instead
   */
  @Deprecated
  public boolean isUseOverallHeatTransferCoefficient() {
    return heatTransferMode == HeatTransferMode.DETAILED_U;
  }

  /**
   * Calculates the temperature difference between the outlet and inlet of the system.
   *
   * <p>
   * Uses the analytical solution for a pipe with constant wall temperature (like a heat exchanger):
   * </p>
   *
   * <pre>
   * T_out = T_wall + (T_in - T_wall) * exp(-U * A / (m_dot * Cp))
   * </pre>
   *
   * <p>
   * This is derived from the energy balance dQ = U*(T-Ts)*dA = -m_dot*Cp*dT integrated along the
   * pipe length.
   * </p>
   *
   * @param system the thermodynamic system for which the temperature difference is to be calculated
   * @return the temperature difference between the outlet and inlet (negative for cooling)
   */
  public double calcTemperatureDifference(SystemInterface system) {
    double cpLocal = system.getCp("J/kgK");
    double Tmi = system.getTemperature("C");
    double Ts = constantSurfaceTemperature - 273.15;

    // Handle case where surface temperature equals inlet temperature (no heat
    // transfer)
    if (Math.abs(Ts - Tmi) < 0.01) {
      return 0.0;
    }

    // Calculate heat transfer coefficient based on mode
    switch (heatTransferMode) {
      case ADIABATIC:
        // No heat transfer - return 0 temperature difference
        return 0.0;
      case ISOTHERMAL:
        // Constant temperature - return 0 temperature difference
        return 0.0;
      case ESTIMATED_INNER_H:
      case DETAILED_U:
        // Calculate h from flow conditions
        heatTransferCoefficient = estimateHeatTransferCoefficent(system);
        break;
      case SPECIFIED_U:
        // Use the user-specified value (already set)
        break;
    }

    // Protect against zero or negative heat transfer coefficient
    if (heatTransferCoefficient <= 0) {
      return 0.0;
    }

    double massFlowRate = system.getFlowRate("kg/sec");
    if (massFlowRate <= 0) {
      return 0.0;
    }

    // Heat transfer area (inner surface)
    double heatTransferArea = Math.PI * insideDiameter * length;

    // Number of Transfer Units (NTU)
    // NTU = U*A / (m_dot * Cp)
    double NTU = heatTransferCoefficient * heatTransferArea / (massFlowRate * cpLocal);

    // Analytical solution for constant wall temperature:
    // T_out = T_wall + (T_in - T_wall) * exp(-NTU)
    // Therefore: T_out - T_in = (T_wall - T_in) * (1 - exp(-NTU))
    double Tmo = Ts + (Tmi - Ts) * Math.exp(-NTU);

    return Tmo - Tmi;
  }

  /**
   * Calculates the heat balance for the given system.
   *
   * <p>
   * This method calculates the enthalpy change due to:
   * <ul>
   * <li>Wall heat transfer (LMTD method) - when not adiabatic</li>
   * <li>Joule-Thomson effect - cooling/heating due to pressure change (calculated from
   * thermodynamics)</li>
   * <li>Friction heating - viscous dissipation</li>
   * </ul>
   *
   * <p>
   * The final PHflash operation determines the equilibrium state at the new enthalpy and pressure,
   * which inherently accounts for heat of vaporization/condensation in two-phase flow. Phase
   * changes (liquid evaporation or vapor condensation) are properly handled through the enthalpy
   * balance.
   * </p>
   *
   * @param enthalpy the initial enthalpy of the system
   * @param system the thermodynamic system for which the heat balance is to be calculated
   * @param testOps the thermodynamic operations to be performed
   * @return the calculated enthalpy after performing the heat balance
   */
  public double calcHeatBalance(double enthalpy, SystemInterface system,
      ThermodynamicOperations testOps) {
    double Cp = system.getCp("J/kgK");
    double massFlowRate = system.getFlowRate("kg/sec");

    // 1. Wall heat transfer (LMTD-based calculation)
    if (!runAdiabatic) {
      enthalpy = enthalpy + massFlowRate * Cp * calcTemperatureDifference(system);
    }

    // 2. Joule-Thomson effect: temperature change due to pressure drop
    // JT coefficient calculated from mixture thermodynamics (mass-weighted average)
    // dH_JT = m_dot * Cp * μ_JT * dP (where dP is pressure drop, positive value)
    if (includeJouleThomsonEffect) {
      try {
        double jouleThomsonCoeff = 0.0;
        double totalMassFlow = system.getFlowRate("kg/sec");

        // Calculate mass-weighted average JT coefficient across all phases
        for (int phaseNum = 0; phaseNum < system.getNumberOfPhases(); phaseNum++) {
          double phaseMassFlow = system.getPhase(phaseNum).getFlowRate("kg/sec");
          double phaseFraction = phaseMassFlow / totalMassFlow;
          double phaseJT = system.getPhase(phaseNum).getJouleThomsonCoefficient("K/Pa");
          if (!Double.isNaN(phaseJT) && !Double.isInfinite(phaseJT)) {
            jouleThomsonCoeff += phaseFraction * phaseJT;
          }
        }

        if (jouleThomsonCoeff > 0) {
          double pressureDropPa = pressureDrop * 1e5; // bar to Pa
          double dT_JT = -jouleThomsonCoeff * pressureDropPa; // Cooling for expansion
          enthalpy = enthalpy + massFlowRate * Cp * dT_JT;
        }
      } catch (Exception ex) {
        // Skip JT effect if calculation fails
      }
    }

    // 3. Friction heating: viscous dissipation adds energy to the fluid
    // Q_friction = dP_friction * volumetric_flow_rate
    // Note: Use only friction pressure drop, not total (which includes hydrostatic)
    // Hydrostatic pressure change is reversible work, not dissipation
    if (includeFrictionHeating) {
      // frictionPressureLoss is already in bar (after convertSystemUnitToMetric)
      double frictionPressureDropPa = Math.abs(frictionPressureLoss) * 1e5; // bar to Pa
      double volumetricFlowRate = massFlowRate / system.getDensity("kg/m3");
      double frictionHeat = frictionPressureDropPa * volumetricFlowRate;
      enthalpy = enthalpy + frictionHeat;
    }

    // PHflash finds equilibrium at new enthalpy - this inherently handles
    // heat of vaporization/condensation for two-phase flow
    testOps.PHflash(enthalpy);
    return enthalpy;
  }

  /**
   * Sets whether to include Joule-Thomson effect in energy calculations.
   *
   * <p>
   * The Joule-Thomson effect accounts for temperature change during gas expansion. For natural gas,
   * this typically results in cooling during pressure drop. The JT coefficient is automatically
   * calculated from the gas phase thermodynamics using NeqSim's rigorous equation of state,
   * providing accurate values for the actual fluid composition and conditions.
   * </p>
   *
   * <p>
   * Typical Joule-Thomson coefficients (calculated automatically):
   * <ul>
   * <li>Methane: ~4×10⁻⁶ K/Pa (0.4 K/bar)</li>
   * <li>Natural gas: 3-5×10⁻⁶ K/Pa</li>
   * <li>CO2: ~10⁻⁵ K/Pa (1 K/bar)</li>
   * </ul>
   *
   * @param include true to include JT effect, false otherwise
   */
  public void setIncludeJouleThomsonEffect(boolean include) {
    this.includeJouleThomsonEffect = include;
  }

  /**
   * Gets whether Joule-Thomson effect is included in energy calculations.
   *
   * <p>
   * When enabled, the energy equation accounts for temperature change due to gas expansion,
   * typically resulting in cooling for natural gas flows. The JT coefficient is automatically
   * calculated from the gas phase thermodynamics.
   * </p>
   *
   * @return true if JT effect is included in the energy balance
   * @see #setIncludeJouleThomsonEffect(boolean)
   */
  public boolean isIncludeJouleThomsonEffect() {
    return includeJouleThomsonEffect;
  }

  /**
   * Sets whether to include friction heating in energy calculations.
   *
   * <p>
   * Friction heating accounts for viscous dissipation, where mechanical energy lost to friction is
   * converted to thermal energy in the fluid. The heat added is calculated as: Q_friction =
   * ΔP_friction × Q_volumetric
   * </p>
   *
   * <p>
   * For typical pipeline conditions, friction heating is a small effect (typically 0.01-0.1 K per
   * bar of friction pressure drop) compared to wall heat transfer or Joule-Thomson cooling.
   * However, for high-velocity or long pipelines, it may become significant.
   * </p>
   *
   * @param include true to include friction heating, false otherwise
   */
  public void setIncludeFrictionHeating(boolean include) {
    this.includeFrictionHeating = include;
  }

  /**
   * Gets whether friction heating is included in energy calculations.
   *
   * <p>
   * When enabled, the energy equation accounts for viscous dissipation, where friction pressure
   * losses are converted to thermal energy in the fluid.
   * </p>
   *
   * @return true if friction heating is included in the energy balance
   * @see #setIncludeFrictionHeating(boolean)
   */
  public boolean isIncludeFrictionHeating() {
    return includeFrictionHeating;
  }

  private void initializeTransientState(UUID id) {
    run(id);

    transientPressureProfile = new ArrayList<>(pressureProfile);
    transientTemperatureProfile = new ArrayList<>(temperatureProfile);

    if (transientTemperatureProfile.size() < numberOfIncrements + 1) {
      double fallbackTemperature;
      if (!transientTemperatureProfile.isEmpty()) {
        fallbackTemperature =
            transientTemperatureProfile.get(transientTemperatureProfile.size() - 1);
      } else {
        fallbackTemperature = getInletStream().getThermoSystem().getTemperature();
      }
      while (transientTemperatureProfile.size() < numberOfIncrements + 1) {
        transientTemperatureProfile.add(fallbackTemperature);
      }
    }
    transientMassFlowProfile = new ArrayList<>();
    transientVelocityProfile = new ArrayList<>();
    transientDensityProfile = new ArrayList<>();

    crossSectionArea = (Math.PI / 4.0) * Math.pow(insideDiameter, 2.0);
    segmentLengthMeters = totalLength / Math.max(1, numberOfIncrements);

    SystemInterface inlet = getInletStream().getThermoSystem().clone();
    inlet.initProperties();
    double steadyFlow = inlet.getFlowRate("kg/sec");
    double steadyDensity = Math.max(MIN_DENSITY, inlet.getDensity("kg/m3"));
    double baseVelocity = steadyFlow / (steadyDensity * crossSectionArea);
    baseVelocity = Math.max(MIN_TRANSIT_VELOCITY, baseVelocity);

    for (int i = 0; i < transientPressureProfile.size(); i++) {
      transientMassFlowProfile.add(steadyFlow);
    }

    if (mixtureSuperficialVelocityProfile != null && !mixtureSuperficialVelocityProfile.isEmpty()) {
      for (int i = 0; i < numberOfIncrements; i++) {
        double velocityFeetPerSecond = mixtureSuperficialVelocityProfile
            .get(Math.min(i, mixtureSuperficialVelocityProfile.size() - 1));
        transientVelocityProfile
            .add(Math.max(MIN_TRANSIT_VELOCITY, velocityFeetPerSecond * 0.3048));
      }
    } else {
      for (int i = 0; i < numberOfIncrements; i++) {
        transientVelocityProfile.add(baseVelocity);
      }
    }

    if (mixtureDensityProfile != null && !mixtureDensityProfile.isEmpty()) {
      for (int i = 0; i < numberOfIncrements; i++) {
        transientDensityProfile.add(Math.max(MIN_DENSITY,
            mixtureDensityProfile.get(Math.min(i, mixtureDensityProfile.size() - 1))));
      }
    } else {
      for (int i = 0; i < numberOfIncrements; i++) {
        transientDensityProfile.add(Math.max(MIN_DENSITY, steadyDensity));
      }
    }

    transientInitialized = true;
  }

  private void ensureTransientState(UUID id) {
    if (!transientInitialized || transientPressureProfile == null
        || transientPressureProfile.size() != numberOfIncrements + 1
        || transientTemperatureProfile == null
        || transientTemperatureProfile.size() != numberOfIncrements + 1
        || transientMassFlowProfile == null
        || transientMassFlowProfile.size() != numberOfIncrements + 1
        || transientVelocityProfile == null || transientVelocityProfile.size() != numberOfIncrements
        || transientDensityProfile == null
        || transientDensityProfile.size() != numberOfIncrements) {
      initializeTransientState(id);
      return;
    }
    if (Double.isNaN(crossSectionArea)) {
      crossSectionArea = (Math.PI / 4.0) * Math.pow(insideDiameter, 2.0);
    }
    if (Double.isNaN(segmentLengthMeters) || segmentLengthMeters <= 0) {
      segmentLengthMeters = totalLength / Math.max(1, numberOfIncrements);
    }
  }

  /**
   * Calculates friction pressure drop for transient simulation. Uses simplified correlations that
   * don't depend on steady-state flow regime detection.
   *
   * @param velocity mixture velocity in m/s
   * @param density mixture density in kg/m3
   * @param viscosity mixture viscosity in Pa.s (not cP)
   * @param segmentLength length of segment in m
   * @return friction pressure drop in bar
   */
  private double calcTransientFrictionPressureDrop(double velocity, double density,
      double viscosity, double segmentLength) {
    if (velocity < MIN_TRANSIT_VELOCITY || density < MIN_DENSITY || viscosity <= 0) {
      return 0.0;
    }

    // Calculate Reynolds number
    double Re = density * Math.abs(velocity) * insideDiameter / viscosity;

    // Calculate friction factor
    double f;
    double E = pipeWallRoughness / insideDiameter;

    if (Re < 1e-10) {
      f = 0.0;
    } else if (Re < 2300) {
      // Laminar flow
      f = 64.0 / Re;
    } else if (Re < 4000) {
      // Transition zone
      double fLaminar = 64.0 / 2300.0;
      double fTurbulent =
          Math.pow(1 / (-1.8 * Math.log10(Math.pow(E / 3.7, 1.11) + (6.9 / 4000.0))), 2);
      f = fLaminar + (fTurbulent - fLaminar) * (Re - 2300.0) / 1700.0;
    } else {
      // Turbulent flow - Haaland equation
      f = Math.pow(1 / (-1.8 * Math.log10(Math.pow(E / 3.7, 1.11) + (6.9 / Re))), 2);
    }

    // Darcy-Weisbach: ΔP = f * (L/D) * (ρv²/2)
    // Result in Pa, convert to bar
    double dpFrictionPa =
        f * (segmentLength / insideDiameter) * (density * velocity * velocity / 2.0);
    return dpFrictionPa / 1e5; // Convert Pa to bar
  }

  /**
   * Calculates hydrostatic pressure drop for transient simulation.
   *
   * @param density mixture density in kg/m3
   * @param elevationChange elevation change in m (positive = uphill)
   * @return hydrostatic pressure drop in bar
   */
  private double calcTransientHydrostaticPressureDrop(double density, double elevationChange) {
    // ΔP_hydro = ρ * g * Δh
    // Result in Pa, convert to bar
    double dpHydroPa = density * 9.81 * elevationChange;
    return dpHydroPa / 1e5; // Convert Pa to bar
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt, UUID id) {
    if (getCalculateSteadyState()) {
      run(id);
      increaseTime(dt);
      return;
    }
    ensureTransientState(id);

    SystemInterface inletSystem = getInletStream().getThermoSystem().clone();
    inletSystem.initProperties();

    double inletPressureBoundary = inletSystem.getPressure();
    double inletTemperatureBoundary = inletSystem.getTemperature();
    double inletMassFlowBoundary = inletSystem.getFlowRate("kg/sec");
    double inletDensityBoundary = Math.max(MIN_DENSITY, inletSystem.getDensity("kg/m3"));
    double inletViscosityBoundary = inletSystem.getViscosity("kg/msec"); // Pa.s

    double inletVelocity = inletMassFlowBoundary / (inletDensityBoundary * crossSectionArea);
    inletVelocity = Math.max(MIN_TRANSIT_VELOCITY, inletVelocity);

    // Segment elevation change (same for all segments)
    double segmentElevation = totalElevation / Math.max(1, numberOfIncrements);

    List<Double> updatedPressure = new ArrayList<>(transientPressureProfile);
    List<Double> updatedTemperature = new ArrayList<>(transientTemperatureProfile);
    List<Double> updatedMassFlow = new ArrayList<>(transientMassFlowProfile);
    List<Double> updatedVelocity = new ArrayList<>(transientVelocityProfile);
    List<Double> updatedDensity = new ArrayList<>(transientDensityProfile);

    // Store viscosity for friction calculations
    // Use inlet viscosity for all segments - viscosity changes relatively slowly
    // compared to density and pressure in typical pipeline conditions.
    // A more rigorous approach would require full thermodynamic flash at each
    // segment.
    List<Double> segmentViscosities = new ArrayList<>();
    for (int i = 0; i < numberOfIncrements; i++) {
      // Use inlet viscosity as best estimate - avoids incorrect density-ratio scaling
      // which would give wrong trends (gas viscosity increases with T, opposite of
      // density)
      segmentViscosities.add(inletViscosityBoundary);
    }

    updatedPressure.set(0, inletPressureBoundary);
    updatedTemperature.set(0, inletTemperatureBoundary);
    updatedMassFlow.set(0, inletMassFlowBoundary);

    for (int segment = 0; segment < numberOfIncrements; segment++) {
      // Get upstream values - use already-updated values for processed segments
      double upstreamPressure = updatedPressure.get(segment);
      double upstreamTemperature = updatedTemperature.get(segment);
      double upstreamMassFlow = updatedMassFlow.get(segment);

      // Get current downstream values from previous state
      double downstreamPressure = transientPressureProfile.get(segment + 1);
      double downstreamTemperature = transientTemperatureProfile.get(segment + 1);
      double downstreamMassFlow = transientMassFlowProfile.get(segment + 1);

      // Get segment properties
      double segmentDensity = transientDensityProfile.get(segment);
      double segmentVelocity = transientVelocityProfile.get(segment);
      double segmentViscosity = segmentViscosities.get(segment);
      segmentVelocity = Math.max(MIN_TRANSIT_VELOCITY, segmentVelocity);

      // Calculate transit time and relaxation factor
      double tau = segmentLengthMeters / segmentVelocity;
      double relaxation = tau > 0.0 ? Math.min(1.0, dt / tau) : 1.0;

      // Calculate pressure losses for this segment
      double dpFriction = calcTransientFrictionPressureDrop(segmentVelocity, segmentDensity,
          segmentViscosity, segmentLengthMeters);
      double dpHydrostatic = calcTransientHydrostaticPressureDrop(segmentDensity, segmentElevation);
      double totalSegmentDp = dpFriction + dpHydrostatic;

      // Wave transport with pressure losses:
      // The advected pressure is reduced by friction and hydrostatic losses
      double advectedPressure = upstreamPressure - totalSegmentDp;

      // Apply relaxation for wave propagation
      double newDownstreamPressure =
          downstreamPressure + relaxation * (advectedPressure - downstreamPressure);

      // Ensure pressure doesn't go negative
      newDownstreamPressure = Math.max(0.1, newDownstreamPressure);
      updatedPressure.set(segment + 1, newDownstreamPressure);

      // Mass flow propagation - with mass conservation enforcement
      // For incompressible/weakly compressible flow, mass flow should be continuous
      double newMassFlow =
          downstreamMassFlow + relaxation * (upstreamMassFlow - downstreamMassFlow);
      updatedMassFlow.set(segment + 1, newMassFlow);

      // Temperature propagation with advective transport and heat transfer
      double advectedTemperature = upstreamTemperature;

      // Apply heat transfer if not adiabatic and surface temperature is set
      if (heatTransferMode != HeatTransferMode.ADIABATIC
          && heatTransferMode != HeatTransferMode.ISOTHERMAL
          && !Double.isNaN(constantSurfaceTemperature) && constantSurfaceTemperature > 0) {
        // Calculate heat transfer using NTU-effectiveness method
        double Twall = constantSurfaceTemperature; // in Kelvin
        double Tin = advectedTemperature; // in Kelvin

        // Get heat transfer coefficient (use specified or estimate)
        double U = heatTransferCoefficient;
        if (U <= 0 || Double.isNaN(U)) {
          U = 25.0; // Default reasonable value W/(m²·K) for subsea pipe
        }

        // Heat transfer area for this segment
        double A = Math.PI * insideDiameter * segmentLengthMeters;

        // Estimate Cp from inlet (simplified - full approach would need flash)
        double segmentCp = inletSystem.getCp("J/kgK");
        double segmentMassFlow = Math.max(1e-6, newMassFlow);

        // NTU = U*A / (m_dot * Cp)
        double NTU = U * A / (segmentMassFlow * segmentCp);

        // Analytical solution: T_out = T_wall + (T_in - T_wall) * exp(-NTU)
        advectedTemperature = Twall + (Tin - Twall) * Math.exp(-NTU);
      }

      // Apply relaxation for wave propagation
      updatedTemperature.set(segment + 1,
          downstreamTemperature + relaxation * (advectedTemperature - downstreamTemperature));

      // Update velocity based on updated mass flow and density
      double targetVelocity =
          newMassFlow / (Math.max(MIN_DENSITY, segmentDensity) * crossSectionArea);
      double relaxedVelocity = segmentVelocity + relaxation * (targetVelocity - segmentVelocity);
      updatedVelocity.set(segment, Math.max(MIN_TRANSIT_VELOCITY, relaxedVelocity));

      // Update density - use already-updated upstream density for consistency
      double upstreamDensity;
      if (segment == 0) {
        upstreamDensity = inletDensityBoundary;
      } else {
        upstreamDensity = updatedDensity.get(segment - 1); // Use already-updated value
      }
      double relaxedDensity = segmentDensity + relaxation * (upstreamDensity - segmentDensity);
      updatedDensity.set(segment, Math.max(MIN_DENSITY, relaxedDensity));
    }

    // Update transient profiles
    transientPressureProfile = updatedPressure;
    transientTemperatureProfile = updatedTemperature;
    transientMassFlowProfile = updatedMassFlow;
    transientVelocityProfile = updatedVelocity;
    transientDensityProfile = updatedDensity;

    // Update steady-state profiles for output
    pressureProfile = new ArrayList<>(transientPressureProfile);
    temperatureProfile = new ArrayList<>(transientTemperatureProfile);

    pressureDropProfile = new ArrayList<>();
    pressureDropProfile.add(0.0);
    for (int i = 0; i < numberOfIncrements; i++) {
      pressureDropProfile
          .add(transientPressureProfile.get(i) - transientPressureProfile.get(i + 1));
    }

    mixtureSuperficialVelocityProfile = new ArrayList<>();
    for (int i = 0; i < numberOfIncrements; i++) {
      mixtureSuperficialVelocityProfile.add(transientVelocityProfile.get(i) / 0.3048);
    }

    mixtureDensityProfile = new ArrayList<>(transientDensityProfile);

    double outletPressure = transientPressureProfile.get(transientPressureProfile.size() - 1);
    double outletTemperature =
        transientTemperatureProfile.get(transientTemperatureProfile.size() - 1);
    double outletMassFlow = transientMassFlowProfile.get(transientMassFlowProfile.size() - 1);

    SystemInterface outletSystem = system;
    if (outletSystem == null) {
      outletSystem = inletSystem.clone();
    } else {
      outletSystem = outletSystem.clone();
    }
    outletSystem.setPressure(outletPressure);
    outletSystem.setTemperature(outletTemperature);
    outletSystem.setTotalFlowRate(outletMassFlow, "kg/sec");
    outletSystem.setMolarComposition(inletSystem.getMolarComposition());
    outletSystem.initProperties();

    system = outletSystem;
    outStream.setThermoSystem(outletSystem);
    outStream.setCalculationIdentifier(id);

    totalPressureDrop = transientPressureProfile.get(0) - outletPressure;
    pressureOut = outletPressure;
    temperatureOut = outletTemperature;

    increaseTime(dt);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    system.display();
  }

  /**
   * <p>
   * getInletSuperficialVelocity.
   * </p>
   *
   * @return a double
   */
  public double getInletSuperficialVelocity() {
    return getInletStream().getThermoSystem().getFlowRate("kg/sec")
        / getInletStream().getThermoSystem().getDensity("kg/m3")
        / (Math.PI / 4.0 * Math.pow(insideDiameter, 2.0));
  }

  /**
   * Getter for the field <code>heatTransferCoefficient</code>.
   *
   * @return the heat transfer coefficient
   */
  public double getHeatTransferCoefficient() {
    return heatTransferCoefficient;
  }

  /**
   * <p>
   * getOutletSuperficialVelocity.
   * </p>
   *
   * @return a double
   */
  public double getOutletSuperficialVelocity() {
    return getSegmentMixtureSuperficialVelocity(numberOfIncrements);
  }

  /**
   * <p>
   * getNumberOfIncrements.
   * </p>
   *
   * @return a double
   */
  public int getNumberOfIncrements() {
    return numberOfIncrements;
  }

  /**
   * <p>
   * Getter for the field <code>angle</code>.
   * </p>
   *
   * @return angle in degrees
   */
  public double getAngle() {
    return angle;
  }

  /** {@inheritDoc} */
  @Override
  public double getLength() {
    return cumulativeLength;
  }

  /** {@inheritDoc} */
  @Override
  public double getElevation() {
    return cumulativeElevation;
  }

  /** {@inheritDoc} */
  @Override
  public double getDiameter() {
    return insideDiameter;
  }

  /**
   * Get the flow regime as an enum.
   *
   * @return flow regime enum value
   */
  public FlowRegime getFlowRegimeEnum() {
    return regime;
  }

  /** {@inheritDoc} */
  @Override
  public String getFlowRegime() {
    return regime != null ? regime.toString() : "UNKNOWN";
  }

  /**
   * <p>
   * Getter for the field <code>LastSegmentPressureDrop</code>.
   * </p>
   *
   * @return pressure drop last segment
   */
  public double getLastSegmentPressureDrop() {
    return pressureDrop;
  }

  /** {@inheritDoc} */
  @Override
  public double getPressureDrop() {
    return totalPressureDrop;
  }

  /** {@inheritDoc} */
  @Override
  public double[] getPressureProfile() {
    if (pressureProfile == null) {
      return new double[0];
    }
    double[] result = new double[pressureProfile.size()];
    for (int i = 0; i < pressureProfile.size(); i++) {
      result[i] = pressureProfile.get(i);
    }
    return result;
  }

  /**
   * Get the pressure profile as a list.
   *
   * @return list of pressure values
   */
  public List<Double> getPressureProfileList() {
    return new ArrayList<>(pressureProfile);
  }

  /**
   * <p>
   * getSegmentPressure.
   * </p>
   *
   * @param index segment number
   * @return segment pressure as double
   */
  public Double getSegmentPressure(int index) {
    if (index >= 0 && index < pressureProfile.size()) {
      return pressureProfile.get(index);
    } else {
      throw new IndexOutOfBoundsException("Index is out of bounds.");
    }
  }

  /**
   * Get Pressure drop profile.
   *
   * @return ArrayList of pressure drop profile.
   */
  public List<Double> getPressureDropProfile() {
    return new ArrayList<>(pressureDropProfile);
  }

  /**
   * <p>
   * getSegmentPressureDrop.
   * </p>
   *
   * @param index segment number
   * @return Double
   */
  public Double getSegmentPressureDrop(int index) {
    if (index >= 0 && index < pressureDropProfile.size()) {
      return pressureDropProfile.get(index);
    } else {
      throw new IndexOutOfBoundsException("Index is out of bounds.");
    }
  }

  /** {@inheritDoc} */
  @Override
  public double[] getTemperatureProfile() {
    if (temperatureProfile == null) {
      return new double[0];
    }
    double[] result = new double[temperatureProfile.size()];
    for (int i = 0; i < temperatureProfile.size(); i++) {
      result[i] = temperatureProfile.get(i);
    }
    return result;
  }

  /**
   * Get the temperature profile as a list.
   *
   * @return list of temperatures
   */
  public List<Double> getTemperatureProfileList() {
    return new ArrayList<>(temperatureProfile);
  }

  /**
   * <p>
   * getSegmentTemperature.
   * </p>
   *
   * @param index segment number
   * @return Double
   */
  public Double getSegmentTemperature(int index) {
    if (index >= 0 && index < temperatureProfile.size()) {
      return temperatureProfile.get(index);
    } else {
      throw new IndexOutOfBoundsException("Index is out of bounds.");
    }
  }

  /**
   * <p>
   * Getter for the field <code>flowRegimeProfile</code>.
   * </p>
   *
   * @return list of flow regime names
   */
  public List<FlowRegime> getFlowRegimeProfileList() {
    return new ArrayList<>(flowRegimeProfile);
  }

  /**
   * <p>
   * getSegmentFlowRegime.
   * </p>
   *
   * @param index segment number
   * @return String
   */
  public FlowRegime getSegmentFlowRegime(int index) {
    if (index >= 0 && index < flowRegimeProfile.size()) {
      return flowRegimeProfile.get(index);
    } else {
      throw new IndexOutOfBoundsException("Index is out of bounds.");
    }
  }

  /**
   * <p>
   * Getter for the field <code>liquidSuperficialVelocityProfile</code>.
   * </p>
   *
   * @return list of liquid superficial velocity profile
   */
  public List<Double> getLiquidSuperficialVelocityProfile() {
    return new ArrayList<>(liquidSuperficialVelocityProfile);
  }

  /**
   * <p>
   * Getter for the field <code>gasSuperficialVelocityProfile</code>.
   * </p>
   *
   * @return list of gas superficial velocities
   */
  public List<Double> getGasSuperficialVelocityProfile() {
    return new ArrayList<>(gasSuperficialVelocityProfile);
  }

  /**
   * <p>
   * Getter for the field <code>mixtureSuperficialVelocityProfile</code>.
   * </p>
   *
   * @return list of mixture superficial velocity profile
   */
  public List<Double> getMixtureSuperficialVelocityProfile() {
    return new ArrayList<>(mixtureSuperficialVelocityProfile);
  }

  /**
   * <p>
   * Getter for the field <code>mixtureViscosityProfile</code>.
   * </p>
   *
   * @return list of mixture viscosity
   */
  public List<Double> getMixtureViscosityProfile() {
    return new ArrayList<>(mixtureViscosityProfile);
  }

  /**
   * <p>
   * Getter for the field <code>mixtureDensityProfile</code>.
   * </p>
   *
   * @return list of density profile
   */
  public List<Double> getMixtureDensityProfile() {
    return new ArrayList<>(mixtureDensityProfile);
  }

  /**
   * <p>
   * Getter for the field <code>liquidDensityProfile</code>.
   * </p>
   *
   * @return a {@link java.util.List} object
   */
  public List<Double> getLiquidDensityProfile() {
    return new ArrayList<>(liquidDensityProfile);
  }

  /** {@inheritDoc} */
  @Override
  public double[] getLiquidHoldupProfile() {
    if (liquidHoldupProfile == null) {
      return new double[0];
    }
    double[] result = new double[liquidHoldupProfile.size()];
    for (int i = 0; i < liquidHoldupProfile.size(); i++) {
      result[i] = liquidHoldupProfile.get(i);
    }
    return result;
  }

  /**
   * Get the liquid holdup profile as a list.
   *
   * @return list of hold-up values
   */
  public List<Double> getLiquidHoldupProfileList() {
    return new ArrayList<>(liquidHoldupProfile);
  }

  /**
   * <p>
   * Getter for the field <code>mixtureReynoldsNumber</code>.
   * </p>
   *
   * @return list of reynold numbers
   */
  public List<Double> getMixtureReynoldsNumber() {
    return new ArrayList<>(mixtureReynoldsNumber);
  }

  /**
   * <p>
   * Getter for the field <code>lengthProfile</code>.
   * </p>
   *
   * @return list of length profile
   */
  public List<Double> getLengthProfile() {
    return new ArrayList<>(lengthProfile);
  }

  /**
   * <p>
   * Getter for the field <code>incrementsProfile</code>.
   * </p>
   *
   * @return list of increments profile
   */
  public List<Integer> getIncrementsProfile() {
    return new ArrayList<>(incrementsProfile);
  }

  /**
   * <p>
   * Getter for the field <code>elevationProfile</code>.
   * </p>
   *
   * @return list of elevation profile
   */
  public List<Double> getElevationProfile() {
    return new ArrayList<>(elevationProfile);
  }

  /**
   * <p>
   * getSegmentLiquidSuperficialVelocity.
   * </p>
   *
   * @param index segment number
   * @return Double
   */
  public Double getSegmentLiquidSuperficialVelocity(int index) {
    if (index >= 0 && index < liquidSuperficialVelocityProfile.size()) {
      return liquidSuperficialVelocityProfile.get(index);
    } else {
      throw new IndexOutOfBoundsException("Index is out of bounds.");
    }
  }

  /**
   * <p>
   * getSegmentGasSuperficialVelocity.
   * </p>
   *
   * @param index segment number
   * @return Double
   */
  public Double getSegmentGasSuperficialVelocity(int index) {
    if (index >= 0 && index < gasSuperficialVelocityProfile.size()) {
      return gasSuperficialVelocityProfile.get(index);
    } else {
      throw new IndexOutOfBoundsException("Index is out of bounds.");
    }
  }

  /**
   * <p>
   * getSegmentMixtureSuperficialVelocity.
   * </p>
   *
   * @param index segment number
   * @return Double
   */
  public Double getSegmentMixtureSuperficialVelocity(int index) {
    if (index >= 0 && index < mixtureSuperficialVelocityProfile.size()) {
      return mixtureSuperficialVelocityProfile.get(index);
    } else {
      throw new IndexOutOfBoundsException("Index is out of bounds.");
    }
  }

  /**
   * <p>
   * getSegmentMixtureViscosity.
   * </p>
   *
   * @param index segment number
   * @return Double
   */
  public Double getSegmentMixtureViscosity(int index) {
    if (index >= 0 && index < mixtureViscosityProfile.size()) {
      return mixtureViscosityProfile.get(index);
    } else {
      throw new IndexOutOfBoundsException("Index is out of bounds.");
    }
  }

  /**
   * <p>
   * getSegmentMixtureDensity.
   * </p>
   *
   * @param index segment number
   * @return Double
   */
  public Double getSegmentMixtureDensity(int index) {
    if (index >= 0 && index < mixtureDensityProfile.size()) {
      return mixtureDensityProfile.get(index);
    } else {
      throw new IndexOutOfBoundsException("Index is out of bounds.");
    }
  }

  /**
   * <p>
   * getSegmentLiquidDensity.
   * </p>
   *
   * @param index segment number
   * @return Double
   */
  public Double getSegmentLiquidDensity(int index) {
    if (index >= 0 && index < liquidDensityProfile.size()) {
      return liquidDensityProfile.get(index);
    } else {
      throw new IndexOutOfBoundsException("Index is out of bounds.");
    }
  }

  /**
   * <p>
   * getSegmentLiquidHoldup.
   * </p>
   *
   * @param index segment number
   * @return Double
   */
  public Double getSegmentLiquidHoldup(int index) {
    if (index >= 0 && index < liquidHoldupProfile.size()) {
      return liquidHoldupProfile.get(index);
    } else {
      throw new IndexOutOfBoundsException("Index is out of bounds.");
    }
  }

  /**
   * <p>
   * getSegmentMixtureReynoldsNumber.
   * </p>
   *
   * @param index segment number
   * @return Double
   */
  public Double getSegmentMixtureReynoldsNumber(int index) {
    if (index >= 0 && index < mixtureReynoldsNumber.size()) {
      return mixtureReynoldsNumber.get(index);
    } else {
      throw new IndexOutOfBoundsException("Index is out of bounds.");
    }
  }

  /**
   * <p>
   * getSegmentLength.
   * </p>
   *
   * @param index segment number
   * @return Double
   */
  public Double getSegmentLength(int index) {
    if (index >= 0 && index < lengthProfile.size()) {
      return lengthProfile.get(index);
    } else {
      throw new IndexOutOfBoundsException("Index is out of bounds.");
    }
  }

  /**
   * <p>
   * getSegmentElevation.
   * </p>
   *
   * @param index segment number
   * @return Double
   */
  public Double getSegmentElevation(int index) {
    if (index >= 0 && index < elevationProfile.size()) {
      return elevationProfile.get(index);
    } else {
      throw new IndexOutOfBoundsException("Index is out of bounds.");
    }
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

  /** Maximum design AIV (Acoustic-Induced Vibration) power level in kW. */
  private double maxDesignAIV = 25.0;

  /**
   * Rhone-Poulenc velocity calculator. When non-null, maximum velocity is determined from the
   * Rhone-Poulenc curves instead of API RP 14E.
   */
  private RhonePoulencVelocity rhonePoulencVelocity = null;

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
   * Calculate erosional velocity per API RP 14E.
   *
   * <p>
   * The erosional velocity is calculated using the formula:
   * </p>
   * 
   * <pre>
   * V_e = C / sqrt(rho_mix)
   * </pre>
   * 
   * where C is typically 100-150 for continuous service.
   *
   * @param cFactor erosional C-factor (typically 100-150)
   * @return erosional velocity in m/s
   */
  public double getErosionalVelocity(double cFactor) {
    if (mixtureDensity <= 0 || Double.isNaN(mixtureDensity)) {
      // Use inlet stream density if mixture density not yet calculated
      if (getInletStream() != null && getInletStream().getFluid() != null) {
        double density = getInletStream().getFluid().getDensity("kg/m3");
        return density > 0 ? cFactor / Math.sqrt(density) : 0.0;
      }
      return 0.0;
    }
    // mixtureDensity is stored in lb/ft³ internally, convert to kg/m³
    double densityKgM3 = mixtureDensity * 16.0185; // lb/ft³ to kg/m³
    return densityKgM3 > 0 ? cFactor / Math.sqrt(densityKgM3) : 0.0;
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
   * @return mixture superficial velocity in m/s
   */
  public double getMixtureVelocity() {
    // supMixVel is stored in ft/s internally, convert to m/s
    return supMixVel / 3.2808399;
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
    if (mixtureDensity > 0) {
      densityKgM3 = mixtureDensity * 16.0185; // lb/ft³ to kg/m³
    } else if (getInletStream() != null && getInletStream().getFluid() != null) {
      densityKgM3 = getInletStream().getFluid().getDensity("kg/m3");
    } else {
      return Double.NaN;
    }

    // Calculate gas volume fraction (GVF)
    double GVF = 1.0 - inputVolumeFractionLiquid;
    if (Double.isNaN(GVF)) {
      GVF = 0.9; // Assume mostly gas
    }

    // Calculate flow velocity factor (FVF)
    double FVF = 1.0;
    if (GVF > 0.88) {
      if (GVF > 0.99) {
        // Gas-only: use viscosity-based correction
        double viscosity = 0.001; // Default gas viscosity
        if (getInletStream() != null && getInletStream().getFluid() != null) {
          try {
            viscosity = getInletStream().getFluid().getViscosity("kg/msec");
          } catch (Exception e) {
            // Use default
          }
        }
        FVF = Math.sqrt(viscosity / Math.sqrt(0.001));
      } else {
        FVF = -27.882 * GVF * GVF + 45.545 * GVF - 17.495;
      }
    } else if (GVF < 0.2) {
      FVF = 0.2 + 4 * GVF;
    }

    // External diameter in mm
    double outerDiameter;
    if (insideDiameter > 0 && pipeThickness > 0) {
      outerDiameter = (insideDiameter + 2 * pipeThickness) * 1000.0; // m to mm
    } else {
      // Estimate OD from ID with typical 5% wall thickness
      outerDiameter = insideDiameter * 1.1 * 1000.0; // m to mm
    }
    // Convert from internal ft units if needed
    if (outerDiameter > 100) {
      // Already in mm
    } else {
      outerDiameter = outerDiameter / 3.2808399 * 1000.0; // ft to mm
    }

    // Wall thickness in mm
    double wallThicknessMM;
    if (pipeThickness > 0) {
      wallThicknessMM = pipeThickness * 1000.0;
    } else {
      wallThicknessMM = outerDiameter * 0.05; // Assume 5% D/t ratio
    }
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
   * <p>
   * FRMS provides an alternative measure of vibration intensity based on mixture properties. Higher
   * values indicate greater vibration risk.
   * </p>
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

    // Calculate gas volume fraction (GVF)
    double GVF = 1.0 - inputVolumeFractionLiquid;
    if (Double.isNaN(GVF)) {
      GVF = 0.9; // Assume mostly gas
    }

    // Get liquid density (use mixture if no liquid)
    double liquidDensity;
    if (mixtureLiquidDensity > 0) {
      liquidDensity = mixtureLiquidDensity * 16.0185; // lb/ft³ to kg/m³
    } else if (mixtureDensity > 0) {
      liquidDensity = mixtureDensity * 16.0185;
    } else if (getInletStream() != null && getInletStream().getFluid() != null) {
      liquidDensity = getInletStream().getFluid().getDensity("kg/m3");
    } else {
      return Double.NaN;
    }

    // Get inside diameter in meters
    double diameterM = insideDiameter / 3.2808399; // ft to m

    double C = Math.min(Math.min(1, 5 * (1 - GVF)), 5 * GVF) * frmsConstant;
    return C * Math.pow(diameterM, 1.6) * Math.pow(liquidDensity, 0.6) * Math.pow(mixVelocity, 1.2);
  }

  /**
   * Calculate Acoustic-Induced Vibration (AIV) power level.
   *
   * <p>
   * AIV occurs at pressure-reducing elements (valves, orifices, restrictions) where high-velocity
   * gas flow creates acoustic energy. This is most relevant for:
   * </p>
   * <ul>
   * <li>High pressure drop across the pipe segment</li>
   * <li>Gas-dominated flows (GVF &gt; 0.8)</li>
   * <li>Downstream of control valves, chokes, or restrictions</li>
   * </ul>
   *
   * <p>
   * AIV Power Level interpretation (per Energy Institute Guidelines):
   * </p>
   * <ul>
   * <li>&lt; 1 kW: Low risk - no special measures required</li>
   * <li>1 - 10 kW: Medium risk - screening required</li>
   * <li>10 - 25 kW: High risk - detailed assessment required</li>
   * <li>&gt; 25 kW: Very high risk - design modifications needed</li>
   * </ul>
   *
   * @return AIV acoustic power level in kW
   */
  public double calculateAIV() {
    if (getInletStream() == null || getOutletStream() == null) {
      return 0.0;
    }

    double p1 = getInletStream().getPressure("Pa"); // Upstream pressure in Pa
    double p2 = getOutletStream().getPressure("Pa"); // Downstream pressure in Pa

    if (p1 <= 0 || p2 <= 0 || p1 <= p2) {
      return 0.0; // No pressure drop or invalid pressures
    }

    double tempK = getInletStream().getTemperature("K");
    double mdot = getInletStream().getFlowRate("kg/sec"); // Mass flow rate in kg/s

    // Pressure ratio for acoustic power calculation
    double pressureRatio = (p1 - p2) / p1;
    if (pressureRatio > 1.0) {
      pressureRatio = 1.0;
    }

    // Energy Institute formula for acoustic power level
    // W_acoustic = 3.2e-9 * mdot * P1 * (dP/P1)^3.6 * (T/273.15)^0.8
    double acousticPowerWatts =
        3.2e-9 * mdot * p1 * Math.pow(pressureRatio, 3.6) * Math.pow(tempK / 273.15, 0.8);

    return acousticPowerWatts / 1000.0; // Return in kW
  }

  /**
   * Calculate AIV Likelihood of Failure based on acoustic power and pipe geometry.
   *
   * <p>
   * AIV LOF interpretation:
   * </p>
   * <ul>
   * <li>&lt; 0.3: Low risk</li>
   * <li>0.3 - 0.5: Medium risk - monitoring recommended</li>
   * <li>0.5 - 0.7: High risk - detailed assessment required</li>
   * <li>&gt; 0.7: Very high risk - design changes needed</li>
   * </ul>
   *
   * @return AIV likelihood of failure (0.0-1.0)
   */
  public double calculateAIVLikelihoodOfFailure() {
    double acousticPowerKW = calculateAIV();

    // Get pipe geometry
    double thickness = pipeThickness > 0 ? pipeThickness : insideDiameter * 0.05 / 3.2808399;
    double externalDiameter = insideDiameter / 3.2808399 + 2 * thickness;
    double dtRatio = externalDiameter / thickness;

    // Screening parameter: acoustic power * (D/t)^2
    double screeningParam = acousticPowerKW * 1000 * Math.pow(dtRatio, 2); // Convert to W

    // LOF based on screening parameter thresholds
    if (screeningParam < 1e4) {
      return 0.1;
    } else if (screeningParam < 1e5) {
      return 0.3;
    } else if (screeningParam < 1e6) {
      return 0.6;
    } else {
      return 0.9;
    }
  }

  /**
   * Set maximum design AIV power level for capacity constraints. Clears cached constraints so they
   * will be recreated with the new value on next access.
   *
   * @param aivKW maximum AIV power level in kW
   */
  public void setMaxDesignAIV(double aivKW) {
    this.maxDesignAIV = aivKW;
    // Invalidate cached constraints so they get recreated with the new design value
    clearCapacityConstraints();
  }

  /**
   * Get maximum design AIV power level.
   *
   * @return maximum AIV power level in kW
   */
  public double getMaxDesignAIV() {
    return maxDesignAIV;
  }

  /**
   * Get comprehensive FIV analysis results as a map.
   *
   * @return map containing all FIV/AIV analysis results
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
      result.put("rhonePoulencServiceType",
          rhonePoulencVelocity.getServiceType().name());
      double rpMaxVel = getRhonePoulencMaxVelocity();
      result.put("rhonePoulencVelocityRatio",
          rpMaxVel > 0 ? getMixtureVelocity() / rpMaxVel : Double.NaN);
    }

    // FIV (Flow-Induced Vibration) analysis
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

    // AIV (Acoustic-Induced Vibration) analysis
    double aivPower = calculateAIV();
    result.put("AIV_power_kW", aivPower);
    if (aivPower < 1.0) {
      result.put("AIV_risk", "LOW");
    } else if (aivPower < 10.0) {
      result.put("AIV_risk", "MEDIUM");
    } else if (aivPower < 25.0) {
      result.put("AIV_risk", "HIGH");
    } else {
      result.put("AIV_risk", "VERY_HIGH");
    }
    result.put("AIV_LOF", calculateAIVLikelihoodOfFailure());

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

  // ============================================================================
  // Rhone-Poulenc Maximum Velocity
  // ============================================================================

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
  public void setRhonePoulencServiceType(
      RhonePoulencVelocity.ServiceType serviceType) {
    this.rhonePoulencVelocity = new RhonePoulencVelocity(serviceType);
    clearCapacityConstraints();
  }

  /**
   * Enable Rhone-Poulenc maximum velocity calculation with default non-corrosive gas settings.
   *
   * <p>
   * Equivalent to calling
   * {@code setRhonePoulencServiceType(ServiceType.NON_CORROSIVE_GAS)}.
   * </p>
   */
  public void useRhonePoulencVelocity() {
    this.rhonePoulencVelocity =
        new RhonePoulencVelocity(RhonePoulencVelocity.ServiceType.NON_CORROSIVE_GAS);
    clearCapacityConstraints();
  }

  /**
   * Enable Rhone-Poulenc maximum velocity calculation using tabulated data with log-log
   * interpolation for higher accuracy.
   *
   * @param serviceType the gas service type
   * @param useInterpolation true to use tabulated interpolation, false for power-law formula
   */
  public void setRhonePoulencServiceType(
      RhonePoulencVelocity.ServiceType serviceType, boolean useInterpolation) {
    this.rhonePoulencVelocity = new RhonePoulencVelocity(serviceType);
    this.rhonePoulencVelocity.setUseInterpolation(useInterpolation);
    clearCapacityConstraints();
  }

  /**
   * Disable Rhone-Poulenc maximum velocity and revert to API RP 14E erosional velocity.
   */
  public void disableRhonePoulencVelocity() {
    this.rhonePoulencVelocity = null;
    clearCapacityConstraints();
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
   * This method uses the current gas/mixture density from the simulation to look up the maximum
   * velocity from the Rhone-Poulenc correlation. If Rhone-Poulenc is not enabled, returns 0.0.
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
   * <p>
   * Uses mixture density from Beggs and Brill calculations if available; otherwise falls back to
   * the inlet stream density.
   * </p>
   *
   * @return gas density in kg/m3, or 0.0 if unavailable
   */
  private double getGasDensityForVelocity() {
    if (mixtureDensity > 0 && !Double.isNaN(mixtureDensity)) {
      // mixtureDensity is stored in lb/ft3 internally
      return mixtureDensity * 16.0185;
    }
    if (getInletStream() != null && getInletStream().getFluid() != null) {
      return getInletStream().getFluid().getDensity("kg/m3");
    }
    return 0.0;
  }

  /**
   * Set maximum design velocity for capacity constraints. Clears cached constraints so they will be
   * recreated with the new value on next access.
   *
   * @param velocity maximum velocity in m/s
   */
  public void setMaxDesignVelocity(double velocity) {
    this.maxDesignVelocity = velocity;
    // Invalidate cached constraints so they get recreated with the new design value
    clearCapacityConstraints();
  }

  /**
   * Set maximum design LOF for capacity constraints. Clears cached constraints so they will be
   * recreated with the new value on next access.
   *
   * @param lof maximum LOF value
   */
  public void setMaxDesignLOF(double lof) {
    this.maxDesignLOF = lof;
    // Invalidate cached constraints so they get recreated with the new design value
    clearCapacityConstraints();
  }

  /**
   * Set maximum design FRMS for capacity constraints. Clears cached constraints so they will be
   * recreated with the new value on next access.
   *
   * @param frms maximum FRMS value
   */
  public void setMaxDesignFRMS(double frms) {
    this.maxDesignFRMS = frms;
    // Invalidate cached constraints so they get recreated with the new design value
    clearCapacityConstraints();
  }

  /**
   * Override parent's capacity constraint initialization to add FIV/FRMS/AIV constraints.
   */
  @Override
  protected void initializeCapacityConstraints() {
    // Velocity constraint (SOFT limit - uses Rhone-Poulenc if enabled, else erosional)
    addCapacityConstraint(new neqsim.process.equipment.capacity.CapacityConstraint("velocity",
        "m/s", neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.SOFT)
            .setDesignValue(maxDesignVelocity).setMaxValue(getMaxAllowableVelocity())
            .setWarningThreshold(0.9)
            .setDescription("Mixture velocity vs " + getMaxVelocityMethod() + " limit")
            .setValueSupplier(() -> getMixtureVelocity()));

    // LOF (Likelihood of Failure) - FIV constraint for multiphase flow
    addCapacityConstraint(new neqsim.process.equipment.capacity.CapacityConstraint("LOF", "-",
        neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.SOFT)
            .setDesignValue(maxDesignLOF).setMaxValue(1.5).setWarningThreshold(0.5)
            .setDescription("FIV LOF - flow-induced vibration (>1.0 = high risk)")
            .setValueSupplier(() -> calculateLOF()));

    // FRMS (Flow-induced vibration RMS) - for multiphase flow
    addCapacityConstraint(new neqsim.process.equipment.capacity.CapacityConstraint("FRMS", "-",
        neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.SOFT)
            .setDesignValue(maxDesignFRMS).setMaxValue(750.0).setWarningThreshold(0.8)
            .setDescription("FIV FRMS - vibration intensity")
            .setValueSupplier(() -> calculateFRMS()));

    // AIV (Acoustic-Induced Vibration) - for gas/high pressure drop
    addCapacityConstraint(new neqsim.process.equipment.capacity.CapacityConstraint("AIV", "kW",
        neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.SOFT)
            .setDesignValue(maxDesignAIV).setMaxValue(50.0).setWarningThreshold(0.4)
            .setDescription("AIV acoustic power (<1kW=low, 1-10kW=medium, >25kW=very high risk)")
            .setValueSupplier(() -> calculateAIV()));

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

  /**
   * Reinitialize capacity constraints with current design values.
   *
   * <p>
   * Call this method after changing design limits (velocity, LOF, FRMS, AIV) if constraints have
   * already been accessed. This clears existing constraints and recreates them based on current
   * settings.
   * </p>
   *
   * <p>
   * Note: The setter methods (setMaxDesignVelocity, setMaxDesignLOF, etc.) automatically clear the
   * constraints, so this method is only needed if you want to explicitly reinitialize.
   * </p>
   */
  public void reinitializeCapacityConstraints() {
    clearCapacityConstraints();
    initializeCapacityConstraints();
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(new PipeBeggsBrillsResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    PipeBeggsBrillsResponse res = new PipeBeggsBrillsResponse(this);
    res.applyConfig(cfg);
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(res);
  }

  // ============================================================================
  // AutoSizeable Implementation
  // ============================================================================

  /**
   * Auto-sizes the pipeline based on current flow conditions.
   *
   * <p>
   * This method calculates the required pipe diameter to achieve target velocity criteria. The
   * sizing is based on erosion velocity limits and pressure drop constraints.
   * </p>
   *
   * @param safetyFactor safety factor to apply (e.g., 1.2 for 20% margin)
   */
  @Override
  public void autoSize(double safetyFactor) {
    if (getInletStream() == null) {
      throw new IllegalStateException("Cannot auto-size pipeline without inlet stream");
    }

    // Run the pipeline first to establish operating conditions
    run();

    // Calculate optimal diameter based on velocity criteria
    double volumetricFlowRate = getInletStream().getFluid().getFlowRate("m3/sec");

    // Handle zero flow case - use minimum default diameter
    if (Double.isNaN(volumetricFlowRate) || volumetricFlowRate <= 0) {
      // For zero-flow pipe, set reasonable minimum design values
      double minDesignDiameter = 0.0254 * 2.0; // 2 inch minimum
      setDiameter(minDesignDiameter);

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
    if (getInletStream().getFluid().hasPhaseType("gas")
        && !getInletStream().getFluid().hasPhaseType("oil")
        && !getInletStream().getFluid().hasPhaseType("aqueous")) {
      // Gas pipeline - typical 15-20 m/s
      targetVelocity = 15.0 / safetyFactor;
    } else if (!getInletStream().getFluid().hasPhaseType("gas")) {
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
    setDiameter(standardDiameter * 0.0254); // Convert back to meters

    // Re-run to update calculations with new diameter
    run();

    // Set design values for capacity constraints
    try {
      double currentVelocity = getMixtureVelocity();
      double currentVolumeFlow = outStream != null ? outStream.getFlowRate("m3/hr") : 0.0;
      double currentPressureDrop = getPressureDrop();

      // Set default values if current values are invalid
      if (Double.isNaN(currentVelocity) || currentVelocity <= 0) {
        currentVelocity = targetVelocity;
      }
      if (Double.isNaN(currentVolumeFlow) || currentVolumeFlow <= 0) {
        currentVolumeFlow = volumetricFlowRate * 3600.0; // m3/sec to m3/hr
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
      // Continue without constraint design values
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
    // Standard NPS sizes (nominal pipe size in inches) - using inside diameter
    // approximations
    double[] standardSizes = {0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 2.5, 3.0, 4.0, 6.0, 8.0, 10.0, 12.0,
        14.0, 16.0, 18.0, 20.0, 24.0, 30.0, 36.0, 42.0, 48.0};

    // Find the next size up that meets the requirement
    for (double size : standardSizes) {
      if (size >= calculatedDiameterInches) {
        return size;
      }
    }

    // If larger than all standard sizes, round to nearest 6 inches
    return Math.ceil(calculatedDiameterInches / 6.0) * 6.0;
  }

  /** {@inheritDoc} */
  @Override
  public void autoSize() {
    autoSize(1.2); // Default 20% safety factor
  }

  /** {@inheritDoc} */
  @Override
  public void autoSize(String company, String trDocument) {
    // Company-specific sizing criteria
    double safetyFactor = 1.2;

    // Company-specific safety factors could be loaded from database
    if ("Equinor".equalsIgnoreCase(company)) {
      safetyFactor = 1.25; // Example: Equinor requires 25% margin
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
    report.append("Pipeline Sizing Report for: ").append(getName()).append("\n");
    report.append("=========================================\n");

    if (!autoSized) {
      report.append("Pipeline has not been auto-sized.\n");
      return report.toString();
    }

    report.append("Auto-sized: Yes\n");

    // Operating conditions
    if (getInletStream() != null) {
      report.append("\nOperating Conditions:\n");
      report.append(String.format("  Inlet Pressure: %.2f bara\n", inletPressure));
      report.append(String.format("  Outlet Pressure: %.2f bara\n", pressureOut));
      report.append(String.format("  Pressure Drop: %.2f bar (%.1f%%)\n", totalPressureDrop,
          (totalPressureDrop / inletPressure) * 100));
      report.append(
          String.format("  Flow Rate: %.2f kg/hr\n", getInletStream().getFlowRate("kg/hr")));
    }

    // Geometry
    report.append("\nGeometry:\n");
    report.append(String.format("  Inside Diameter: %.1f mm (%.2f inch)\n", insideDiameter * 1000,
        insideDiameter / 0.0254));
    report.append(String.format("  Length: %.1f m\n", totalLength));
    report.append(String.format("  Elevation: %.1f m\n", totalElevation));

    // Velocities
    report.append("\nFlow Velocities:\n");
    report.append(String.format("  Superficial Gas Velocity: %.2f m/s\n", supGasVel));
    report.append(String.format("  Superficial Liquid Velocity: %.2f m/s\n", supLiquidVel));
    report.append(String.format("  Mixture Velocity: %.2f m/s\n", supMixVel));

    // Flow regime
    report.append("\nFlow Characteristics:\n");
    report.append(String.format("  Flow Regime: %s\n", regime));
    report.append(String.format("  Liquid Holdup: %.3f\n", El));
    report.append(String.format("  Mixture Froude Number: %.3f\n", mixtureFroudeNumber));

    return report.toString();
  }

  /** {@inheritDoc} */
  @Override
  public String getSizingReportJson() {
    java.util.Map<String, Object> reportData = new java.util.LinkedHashMap<String, Object>();

    reportData.put("equipmentName", getName());
    reportData.put("equipmentType", "PipeBeggsAndBrills");
    reportData.put("autoSized", autoSized);

    if (autoSized && getInletStream() != null) {
      // Operating conditions
      java.util.Map<String, Object> operating = new java.util.LinkedHashMap<String, Object>();
      operating.put("inletPressure_bara", inletPressure);
      operating.put("outletPressure_bara", pressureOut);
      operating.put("pressureDrop_bar", totalPressureDrop);
      operating.put("pressureDropPercent", (totalPressureDrop / inletPressure) * 100);
      operating.put("flowRate_kghr", getInletStream().getFlowRate("kg/hr"));
      reportData.put("operatingConditions", operating);

      // Geometry
      java.util.Map<String, Object> geometry = new java.util.LinkedHashMap<String, Object>();
      geometry.put("insideDiameter_mm", insideDiameter * 1000);
      geometry.put("insideDiameter_inch", insideDiameter / 0.0254);
      geometry.put("length_m", totalLength);
      geometry.put("elevation_m", totalElevation);
      geometry.put("angle_degrees", angle);
      reportData.put("geometry", geometry);

      // Velocities
      java.util.Map<String, Object> velocities = new java.util.LinkedHashMap<String, Object>();
      velocities.put("superficialGasVelocity_ms", supGasVel);
      velocities.put("superficialLiquidVelocity_ms", supLiquidVel);
      velocities.put("mixtureVelocity_ms", supMixVel);
      reportData.put("velocities", velocities);

      // Flow characteristics
      java.util.Map<String, Object> flowChars = new java.util.LinkedHashMap<String, Object>();
      flowChars.put("flowRegime", regime != null ? regime.toString() : "UNKNOWN");
      flowChars.put("liquidHoldup", El);
      flowChars.put("mixtureFroudeNumber", mixtureFroudeNumber);
      reportData.put("flowCharacteristics", flowChars);
    }

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(reportData);
  }
}
