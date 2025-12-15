package neqsim.process.equipment.tank;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.util.fire.TransientWallHeatTransfer;
import neqsim.process.util.fire.VesselHeatTransferCalculator;
import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * VesselDepressurization models dynamic filling and depressurization of pressure vessels.
 *
 * <p>
 * This class provides a comprehensive model for vessel dynamics including:
 * <ul>
 * <li>Isothermal process (constant temperature)</li>
 * <li>Isenthalpic/Adiabatic process (constant enthalpy)</li>
 * <li>Isentropic process (constant entropy, with PV work)</li>
 * <li>Isenergetic process (constant internal energy)</li>
 * <li>Energy balance (full heat transfer modeling)</li>
 * </ul>
 *
 * <p>
 * The implementation follows the approach of HydDown for hydrogen vessel calculations but extends
 * it to support multi-component mixtures using NeqSim's thermodynamic backend.
 * </p>
 *
 * <p>
 * Reference: Andreasen, A. (2021). HydDown: A Python package for calculation of hydrogen (or other
 * gas) pressure vessel filling and discharge. Journal of Open Source Software, 6(66), 3695.
 * </p>
 *
 * @see <a href="https://doi.org/10.21105/joss.03695">HydDown - JOSS Paper</a>
 *
 *      <p>
 *      Usage example:
 * 
 *      <pre>
 *      SystemInterface gas = new SystemSrkEos(298.0, 100.0);
 *      gas.addComponent("hydrogen", 1.0);
 *      gas.setMixingRule("classic");
 * 
 *      Stream feed = new Stream("feed", gas);
 *      feed.setFlowRate(0.0, "kg/hr"); // Closed vessel
 *      feed.run();
 * 
 *      VesselDepressurization vessel = new VesselDepressurization("HP Vessel", feed);
 *      vessel.setVolume(0.1); // 100 liters
 *      vessel.setCalculationType(CalculationType.ENERGY_BALANCE);
 *      vessel.setVesselProperties(0.01, 7800.0, 500.0, 45.0); // Steel vessel
 *      vessel.setOrificeDiameter(0.005); // 5mm orifice
 *      vessel.setBackPressure(1.0); // 1 bara
 *      vessel.run();
 * 
 *      // Transient simulation
 *      for (double t = 0; t &lt;= 100; t += 0.5) {
 *        vessel.runTransient(0.5, UUID.randomUUID());
 *        System.out.println(vessel.getPressure() + " " + vessel.getTemperature());
 *      }
 *      </pre>
 *
 * @author ESOL
 * @see <a href="https://github.com/andr1976/HydDown">HydDown</a>
 */
public class VesselDepressurization extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(VesselDepressurization.class);

  /**
   * Calculation type for vessel thermodynamics.
   */
  public enum CalculationType {
    /** Constant temperature process. */
    ISOTHERMAL,
    /** Constant enthalpy process (adiabatic, no PV work). */
    ISENTHALPIC,
    /** Constant entropy process (adiabatic, with PV work). */
    ISENTROPIC,
    /** Constant internal energy process. */
    ISENERGETIC,
    /** Full energy balance with heat transfer. */
    ENERGY_BALANCE
  }

  /**
   * Heat transfer type for energy balance calculations.
   */
  public enum HeatTransferType {
    /** No heat transfer (adiabatic). */
    ADIABATIC,
    /** Fixed overall U-value. */
    FIXED_U,
    /** Fixed heat rate. */
    FIXED_Q,
    /** Calculated natural/mixed convection. */
    CALCULATED,
    /** Transient wall temperature with 1-D conduction. */
    TRANSIENT_WALL
  }

  /**
   * Vessel orientation.
   */
  public enum VesselOrientation {
    /** Vertical vessel (height is characteristic length). */
    VERTICAL,
    /** Horizontal vessel (diameter is characteristic length). */
    HORIZONTAL
  }

  /**
   * Flow direction.
   */
  public enum FlowDirection {
    /** Discharge (blowdown). */
    DISCHARGE,
    /** Filling (pressurization). */
    FILLING
  }

  /**
   * Preset material properties for common vessel constructions.
   *
   * <p>
   * Usage:
   * 
   * <pre>
   * vessel.setVesselMaterial(VesselMaterial.CARBON_STEEL);
   * </pre>
   */
  public enum VesselMaterial {
    /** Carbon steel (SA-516 Gr. 70). */
    CARBON_STEEL(7850.0, 490.0, 45.0),
    /** Stainless steel 304. */
    STAINLESS_304(8000.0, 500.0, 16.2),
    /** Stainless steel 316. */
    STAINLESS_316(8000.0, 500.0, 16.3),
    /** Duplex stainless steel (22Cr). */
    DUPLEX_22CR(7800.0, 500.0, 19.0),
    /** Aluminum 6061-T6. */
    ALUMINUM_6061(2700.0, 896.0, 167.0),
    /** Titanium Grade 2. */
    TITANIUM_GR2(4510.0, 520.0, 16.4),
    /** CFRP (Carbon Fiber Reinforced Polymer) for Type IV vessels. */
    CFRP(1600.0, 1000.0, 1.0),
    /** Fiberglass/GRP. */
    FIBERGLASS(1900.0, 900.0, 0.3);

    private final double density; // kg/m³
    private final double heatCapacity; // J/(kg·K)
    private final double thermalConductivity; // W/(m·K)

    VesselMaterial(double density, double heatCapacity, double thermalConductivity) {
      this.density = density;
      this.heatCapacity = heatCapacity;
      this.thermalConductivity = thermalConductivity;
    }

    /** @return Material density [kg/m³] */
    public double getDensity() {
      return density;
    }

    /** @return Material heat capacity [J/(kg·K)] */
    public double getHeatCapacity() {
      return heatCapacity;
    }

    /** @return Material thermal conductivity [W/(m·K)] */
    public double getThermalConductivity() {
      return thermalConductivity;
    }
  }

  /**
   * Preset liner materials for Type III/IV composite vessels.
   */
  public enum LinerMaterial {
    /** High-density polyethylene (HDPE). */
    HDPE(945.0, 1584.0, 0.385),
    /** Nylon (PA6). */
    NYLON(1130.0, 1700.0, 0.25),
    /** Aluminum liner for Type III. */
    ALUMINUM(2700.0, 896.0, 167.0);

    private final double density;
    private final double heatCapacity;
    private final double thermalConductivity;

    LinerMaterial(double density, double heatCapacity, double thermalConductivity) {
      this.density = density;
      this.heatCapacity = heatCapacity;
      this.thermalConductivity = thermalConductivity;
    }

    /** @return Material density [kg/m³] */
    public double getDensity() {
      return density;
    }

    /** @return Material heat capacity [J/(kg·K)] */
    public double getHeatCapacity() {
      return heatCapacity;
    }

    /** @return Material thermal conductivity [W/(m·K)] */
    public double getThermalConductivity() {
      return thermalConductivity;
    }
  }

  // Vessel geometry
  private double volume = 1.0; // m³
  private double vesselLength = 2.0; // m
  private double vesselDiameter = 0.8; // m
  private VesselOrientation orientation = VesselOrientation.VERTICAL;

  // Vessel wall properties
  private double wallThickness = 0.015; // m
  private double wallDensity = 7800.0; // kg/m³ (steel)
  private double wallHeatCapacity = 500.0; // J/(kg*K)
  private double wallThermalConductivity = 45.0; // W/(m*K) (carbon steel)

  // Composite wall (liner + shell for Type III/IV vessels)
  private boolean hasLiner = false;
  private double linerThickness = 0.005; // m
  private double linerDensity = 950.0; // kg/m³ (HDPE)
  private double linerHeatCapacity = 1800.0; // J/(kg*K)
  private double linerThermalConductivity = 0.4; // W/(m*K)

  // Operating conditions
  private double backPressure = 101325.0; // Pa (atmospheric)
  private double ambientTemperature = 298.15; // K
  private double externalHeatTransferCoeff = 10.0; // W/(m²*K) (natural convection in air)

  // Fire case (API 521)
  private boolean fireCase = false;
  private double fireHeatFlux = 0.0; // W/m² incident heat flux
  private double wetSurfaceFraction = 1.0; // Fraction of surface wetted (for fire relief)

  // Orifice/valve parameters
  private double orificeDiameter = 0.01; // m
  private double dischargeCoefficient = 0.61; // Sharp-edge orifice
  private double valveOpeningTime = 0.0; // s, time for valve to fully open (0 = instant)
  private double valveOpeningFraction = 1.0; // Current valve opening (0-1)

  // Calculation settings
  private CalculationType calculationType = CalculationType.ENERGY_BALANCE;
  private HeatTransferType heatTransferType = HeatTransferType.CALCULATED;
  private FlowDirection flowDirection = FlowDirection.DISCHARGE;

  // Fixed heat transfer parameters
  private double fixedU = 10.0; // W/(m²*K)
  private double fixedQ = 0.0; // W

  // State variables
  private SystemInterface thermoSystem;
  private StreamInterface outletStream;
  private double wallTemperature;
  private TransientWallHeatTransfer wallModel;
  private double currentTime = 0.0;

  // Two-phase modeling support
  private boolean twoPhaseHeatTransfer = false;
  private double initialLiquidLevel = 0.0; // fraction of vessel height (0-1)
  private double initialBeta = 1.0; // Initial vapor fraction from feed stream
  private double gasWallTemperature;
  private double liquidWallTemperature;
  private TransientWallHeatTransfer gasWallModel;
  private TransientWallHeatTransfer liquidWallModel;
  private double liquidVolumeFraction = 0.0;

  // History for output
  private List<Double> timeHistory = new ArrayList<>();
  private List<Double> pressureHistory = new ArrayList<>();
  private List<Double> temperatureHistory = new ArrayList<>();
  private List<Double> massHistory = new ArrayList<>();
  private List<Double> wallTemperatureHistory = new ArrayList<>();
  private List<Double> massFlowHistory = new ArrayList<>();
  private List<Double> heatRateHistory = new ArrayList<>();
  private List<Double> gasWallTemperatureHistory = new ArrayList<>();
  private List<Double> liquidWallTemperatureHistory = new ArrayList<>();
  private List<Double> liquidLevelHistory = new ArrayList<>();

  /**
   * Constructor for VesselDepressurization.
   *
   * @param name Name of the vessel
   */
  public VesselDepressurization(String name) {
    super(name);
    setCalculateSteadyState(false);
  }

  /**
   * Constructor for VesselDepressurization with inlet stream.
   *
   * @param name Name of the vessel
   * @param inletStream Initial fluid stream
   */
  public VesselDepressurization(String name, StreamInterface inletStream) {
    this(name);
    setInletStream(inletStream);
  }

  /**
   * Sets the inlet stream and initializes the vessel.
   *
   * @param inletStream Inlet stream
   */
  public void setInletStream(StreamInterface inletStream) {
    this.thermoSystem = inletStream.getThermoSystem().clone();
    this.initialBeta = inletStream.getThermoSystem().getBeta();
    this.outletStream = new Stream(getName() + "_outlet", thermoSystem.clone());
    this.wallTemperature = thermoSystem.getTemperature();
    initializeWallModel();
  }

  /**
   * Initializes the transient wall heat transfer model.
   */
  private void initializeWallModel() {
    if (hasLiner) {
      wallModel = new TransientWallHeatTransfer(linerThickness, linerThermalConductivity,
          linerDensity, linerHeatCapacity, wallThickness, wallThermalConductivity, wallDensity,
          wallHeatCapacity, wallTemperature, 11);
    } else {
      wallModel = new TransientWallHeatTransfer(wallThickness, wallThermalConductivity, wallDensity,
          wallHeatCapacity, wallTemperature, 11);
    }

    // Initialize two-phase wall models if enabled
    if (twoPhaseHeatTransfer) {
      gasWallTemperature = wallTemperature;
      liquidWallTemperature = wallTemperature;

      if (hasLiner) {
        gasWallModel = new TransientWallHeatTransfer(linerThickness, linerThermalConductivity,
            linerDensity, linerHeatCapacity, wallThickness, wallThermalConductivity, wallDensity,
            wallHeatCapacity, gasWallTemperature, 11);
        liquidWallModel = new TransientWallHeatTransfer(linerThickness, linerThermalConductivity,
            linerDensity, linerHeatCapacity, wallThickness, wallThermalConductivity, wallDensity,
            wallHeatCapacity, liquidWallTemperature, 11);
      } else {
        gasWallModel = new TransientWallHeatTransfer(wallThickness, wallThermalConductivity,
            wallDensity, wallHeatCapacity, gasWallTemperature, 11);
        liquidWallModel = new TransientWallHeatTransfer(wallThickness, wallThermalConductivity,
            wallDensity, wallHeatCapacity, liquidWallTemperature, 11);
      }
    }
  }

  /**
   * Enables two-phase heat transfer modeling with separate gas and liquid wall zones.
   *
   * <p>
   * When enabled, the model tracks separate wall temperatures for the gas-wetted and liquid-wetted
   * portions of the vessel. Heat transfer to the gas phase uses natural convection, while heat
   * transfer to the liquid phase uses natural convection or nucleate boiling correlations.
   * </p>
   *
   * @param enabled true to enable two-phase heat transfer modeling
   */
  public void setTwoPhaseHeatTransfer(boolean enabled) {
    this.twoPhaseHeatTransfer = enabled;
    if (thermoSystem != null) {
      initializeWallModel();
    }
  }

  /**
   * Sets the initial liquid level as a fraction of vessel height.
   *
   * <p>
   * For a vertical vessel, this is the fraction of the height filled with liquid. For a horizontal
   * vessel, this is the fraction of the diameter filled with liquid (from bottom).
   * </p>
   *
   * @param level Liquid level fraction (0.0 = empty, 1.0 = full)
   */
  public void setInitialLiquidLevel(double level) {
    if (level < 0.0 || level > 1.0) {
      throw new IllegalArgumentException("Liquid level must be between 0 and 1");
    }
    this.initialLiquidLevel = level;
  }

  /**
   * Gets the current liquid level as a fraction of vessel height.
   *
   * @return Liquid level fraction (0-1)
   */
  public double getLiquidLevel() {
    return calculateLiquidLevel();
  }

  /**
   * Gets the gas-wetted wall temperature.
   *
   * @return Gas wall temperature [K]
   */
  public double getGasWallTemperature() {
    if (twoPhaseHeatTransfer && gasWallModel != null) {
      return gasWallModel.getInnerWallTemperature();
    }
    return getWallTemperature();
  }

  /**
   * Gets the liquid-wetted wall temperature.
   *
   * @return Liquid wall temperature [K]
   */
  public double getLiquidWallTemperature() {
    if (twoPhaseHeatTransfer && liquidWallModel != null) {
      return liquidWallModel.getInnerWallTemperature();
    }
    return getWallTemperature();
  }

  /**
   * Gets the gas wall temperature history.
   *
   * @return List of gas wall temperatures [K]
   */
  public List<Double> getGasWallTemperatureHistory() {
    return gasWallTemperatureHistory;
  }

  /**
   * Gets the liquid wall temperature history.
   *
   * @return List of liquid wall temperatures [K]
   */
  public List<Double> getLiquidWallTemperatureHistory() {
    return liquidWallTemperatureHistory;
  }

  /**
   * Gets the liquid level history.
   *
   * @return List of liquid level fractions (0-1)
   */
  public List<Double> getLiquidLevelHistory() {
    return liquidLevelHistory;
  }

  /**
   * Calculates the current liquid level based on phase volumes.
   *
   * @return Liquid level fraction (0-1)
   */
  private double calculateLiquidLevel() {
    if (thermoSystem.getNumberOfPhases() < 2) {
      // Single phase
      if (thermoSystem.getPhase(0).getType().equals(neqsim.thermo.phase.PhaseType.GAS)) {
        return 0.0;
      } else {
        return 1.0;
      }
    }

    // Two-phase: calculate liquid volume fraction
    double liquidMoles = 0.0;
    double gasMoles = 0.0;

    for (int i = 0; i < thermoSystem.getNumberOfPhases(); i++) {
      if (thermoSystem.getPhase(i).getType().equals(neqsim.thermo.phase.PhaseType.GAS)) {
        gasMoles = thermoSystem.getPhase(i).getNumberOfMolesInPhase();
      } else {
        liquidMoles = thermoSystem.getPhase(i).getNumberOfMolesInPhase();
      }
    }

    if (liquidMoles + gasMoles == 0) {
      return 0.0;
    }

    // Calculate liquid volume fraction
    double liquidVolume = 0.0;
    double totalVolume = 0.0;

    for (int i = 0; i < thermoSystem.getNumberOfPhases(); i++) {
      double phaseVolume = thermoSystem.getPhase(i).getVolume() * 1e-5; // L to m³
      totalVolume += phaseVolume;
      if (!thermoSystem.getPhase(i).getType().equals(neqsim.thermo.phase.PhaseType.GAS)) {
        liquidVolume += phaseVolume;
      }
    }

    this.liquidVolumeFraction = liquidVolume / totalVolume;

    // Convert volume fraction to level (height fraction)
    // For vertical vessel: level ≈ volume fraction
    // For horizontal vessel: need to solve for level given volume fraction in a cylinder
    if (orientation == VesselOrientation.VERTICAL) {
      return liquidVolumeFraction;
    } else {
      // Horizontal cylinder: solve V/V_total = f(h/D) where h is liquid height
      // Using iterative approximation
      return calculateHorizontalLiquidLevel(liquidVolumeFraction);
    }
  }

  /**
   * Calculates liquid level for horizontal cylinder given volume fraction.
   *
   * @param volumeFraction Liquid volume fraction (0-1)
   * @return Liquid level fraction (0-1) based on height
   */
  private double calculateHorizontalLiquidLevel(double volumeFraction) {
    // For a horizontal cylinder, liquid level (h/D) relates to volume fraction through:
    // V_liquid / V_total = (1/pi) * [arccos(1-2*x) - (1-2*x)*sqrt(4*x - 4*x^2)]
    // where x = h/D (level fraction)
    // Use Newton-Raphson iteration to solve for x given V_liquid/V_total

    if (volumeFraction <= 0.0) {
      return 0.0;
    }
    if (volumeFraction >= 1.0) {
      return 1.0;
    }

    double x = volumeFraction; // Initial guess
    for (int i = 0; i < 20; i++) {
      double term1 = 1.0 - 2.0 * x;
      double sqrtTerm = Math.sqrt(Math.max(0, 4.0 * x - 4.0 * x * x));
      double f = (Math.acos(term1) - term1 * sqrtTerm) / Math.PI - volumeFraction;

      // Derivative df/dx
      double dfNum = 2.0 / Math.sqrt(1.0 - term1 * term1) + 2.0 * sqrtTerm
          + term1 * (4.0 - 8.0 * x) / (2.0 * sqrtTerm);
      double df = dfNum / Math.PI;

      if (Math.abs(df) < 1e-10) {
        break;
      }
      double xNew = x - f / df;
      if (Math.abs(xNew - x) < 1e-8) {
        break;
      }
      x = Math.max(0.0, Math.min(1.0, xNew));
    }
    return x;
  }

  /**
   * Calculates the wetted (liquid contact) wall area.
   *
   * @return Wetted wall area [m²]
   */
  private double getWettedWallArea() {
    double level = calculateLiquidLevel();
    if (level <= 0.0) {
      return 0.0;
    }
    if (level >= 1.0) {
      return getWallArea();
    }

    if (orientation == VesselOrientation.VERTICAL) {
      // Vertical cylinder: wetted area is proportional to level
      double cylinderArea = Math.PI * vesselDiameter * vesselLength * level;
      // Bottom end is always wetted if there's liquid
      double bottomArea = Math.PI * vesselDiameter * vesselDiameter / 4.0;
      return cylinderArea + bottomArea;
    } else {
      // Horizontal cylinder: wetted perimeter * length
      // Wetted angle theta where cos(theta/2) = 1 - 2*level
      double theta = 2.0 * Math.acos(1.0 - 2.0 * level);
      double wettedPerimeter = vesselDiameter * theta / 2.0;
      return wettedPerimeter * vesselLength;
    }
  }

  /**
   * Calculates the unwetted (gas contact) wall area.
   *
   * @return Unwetted wall area [m²]
   */
  private double getUnwettedWallArea() {
    return getWallArea() - getWettedWallArea();
  }

  /**
   * Sets the vessel volume.
   *
   * @param volume Volume [m³]
   */
  public void setVolume(double volume) {
    this.volume = volume;
  }

  /**
   * Gets the vessel volume.
   *
   * @return Volume [m³]
   */
  public double getVolume() {
    return this.volume;
  }

  /**
   * Sets the vessel geometry.
   *
   * @param length Vessel length [m]
   * @param diameter Vessel diameter [m]
   * @param orientation Vessel orientation
   */
  public void setVesselGeometry(double length, double diameter, VesselOrientation orientation) {
    this.vesselLength = length;
    this.vesselDiameter = diameter;
    this.orientation = orientation;
    this.volume = Math.PI * diameter * diameter / 4.0 * length;
  }

  /**
   * Sets the vessel wall properties.
   *
   * @param thickness Wall thickness [m]
   * @param density Wall material density [kg/m³]
   * @param heatCapacity Wall material heat capacity [J/(kg*K)]
   * @param thermalConductivity Wall thermal conductivity [W/(m*K)]
   */
  public void setVesselProperties(double thickness, double density, double heatCapacity,
      double thermalConductivity) {
    this.wallThickness = thickness;
    this.wallDensity = density;
    this.wallHeatCapacity = heatCapacity;
    this.wallThermalConductivity = thermalConductivity;
    initializeWallModel();
  }

  /**
   * Sets the vessel wall properties using a preset material.
   *
   * <p>
   * Example:
   * 
   * <pre>
   * vessel.setVesselMaterial(0.015, VesselMaterial.CARBON_STEEL);
   * </pre>
   *
   * @param thickness Wall thickness [m]
   * @param material Preset material from {@link VesselMaterial}
   */
  public void setVesselMaterial(double thickness, VesselMaterial material) {
    setVesselProperties(thickness, material.getDensity(), material.getHeatCapacity(),
        material.getThermalConductivity());
  }

  /**
   * Sets the liner properties for Type III/IV vessels.
   *
   * @param thickness Liner thickness [m]
   * @param density Liner density [kg/m³]
   * @param heatCapacity Liner heat capacity [J/(kg*K)]
   * @param thermalConductivity Liner thermal conductivity [W/(m*K)]
   */
  public void setLinerProperties(double thickness, double density, double heatCapacity,
      double thermalConductivity) {
    this.hasLiner = true;
    this.linerThickness = thickness;
    this.linerDensity = density;
    this.linerHeatCapacity = heatCapacity;
    this.linerThermalConductivity = thermalConductivity;
    initializeWallModel();
  }

  /**
   * Sets the liner properties using a preset material.
   *
   * <p>
   * Example for Type IV vessel (CFRP shell with HDPE liner):
   * 
   * <pre>
   * vessel.setVesselMaterial(0.017, VesselMaterial.CFRP);
   * vessel.setLinerMaterial(0.007, LinerMaterial.HDPE);
   * </pre>
   *
   * @param thickness Liner thickness [m]
   * @param material Preset liner material from {@link LinerMaterial}
   */
  public void setLinerMaterial(double thickness, LinerMaterial material) {
    setLinerProperties(thickness, material.getDensity(), material.getHeatCapacity(),
        material.getThermalConductivity());
  }

  /**
   * Sets the orifice diameter for flow calculations.
   *
   * @param diameter Orifice diameter [m]
   */
  public void setOrificeDiameter(double diameter) {
    this.orificeDiameter = diameter;
  }

  /**
   * Sets the discharge coefficient.
   *
   * @param cd Discharge coefficient (typically 0.6-0.65 for sharp-edge orifice)
   */
  public void setDischargeCoefficient(double cd) {
    this.dischargeCoefficient = cd;
  }

  /**
   * Sets the back pressure for discharge calculations.
   *
   * @param pressure Back pressure [bara]
   */
  public void setBackPressure(double pressure) {
    this.backPressure = pressure * 1e5; // Convert to Pa
  }

  /**
   * Sets the ambient temperature.
   *
   * @param temperature Ambient temperature [K]
   */
  public void setAmbientTemperature(double temperature) {
    this.ambientTemperature = temperature;
  }

  /**
   * Sets the external heat transfer coefficient.
   *
   * @param h External film coefficient [W/(m²*K)]
   */
  public void setExternalHeatTransferCoefficient(double h) {
    this.externalHeatTransferCoeff = h;
  }

  /**
   * Enables fire case simulation per API 521.
   *
   * <p>
   * In fire case, external heat flux is applied to the vessel surface, representing pool fire or
   * jet fire scenarios. The heat input is calculated as: Q = fireHeatFlux * wetSurfaceFraction *
   * wallArea
   * </p>
   *
   * @param enable True to enable fire case
   */
  public void setFireCase(boolean enable) {
    this.fireCase = enable;
  }

  /**
   * Sets the fire heat flux per API 521.
   *
   * <p>
   * Typical values per API 521:
   * <ul>
   * <li>Pool fire (adequate drainage): 25,000 W/m² (25 kW/m²)</li>
   * <li>Pool fire (inadequate drainage): 43,200 W/m² (43.2 kW/m²)</li>
   * <li>Jet fire: 100,000+ W/m² (100+ kW/m²)</li>
   * </ul>
   *
   * @param heatFlux Heat flux [W/m²]
   */
  public void setFireHeatFlux(double heatFlux) {
    this.fireHeatFlux = heatFlux;
    this.fireCase = true;
  }

  /**
   * Sets the fire heat flux with unit.
   *
   * @param heatFlux Heat flux value
   * @param unit Unit ("W/m2", "kW/m2", "BTU/hr/ft2")
   */
  public void setFireHeatFlux(double heatFlux, String unit) {
    double flux = heatFlux;
    if ("kW/m2".equalsIgnoreCase(unit)) {
      flux = heatFlux * 1000.0;
    } else if ("BTU/hr/ft2".equalsIgnoreCase(unit)) {
      flux = heatFlux * 3.15459; // BTU/hr/ft² to W/m²
    }
    setFireHeatFlux(flux);
  }

  /**
   * Sets the wetted surface fraction for fire relief calculations.
   *
   * <p>
   * This is the fraction of vessel surface that is wetted by liquid and can absorb fire heat. Per
   * API 521, only wetted surface should be considered for relief valve sizing.
   * </p>
   *
   * @param fraction Wetted fraction (0-1), typically 0.25-0.5 for horizontal vessels
   */
  public void setWettedSurfaceFraction(double fraction) {
    this.wetSurfaceFraction = Math.max(0.0, Math.min(1.0, fraction));
  }

  /**
   * Checks if fire case is enabled.
   *
   * @return True if fire case simulation is active
   */
  public boolean isFireCase() {
    return fireCase;
  }

  /**
   * Gets the current fire heat input rate.
   *
   * @param unit Unit ("W", "kW", "MW")
   * @return Fire heat input rate
   */
  public double getFireHeatInput(String unit) {
    double qFire = fireHeatFlux * wetSurfaceFraction * getWallArea();
    if ("kW".equalsIgnoreCase(unit)) {
      return qFire / 1000.0;
    } else if ("MW".equalsIgnoreCase(unit)) {
      return qFire / 1e6;
    }
    return qFire;
  }

  /**
   * Sets the valve opening time for ESD valve dynamics.
   *
   * <p>
   * Models gradual valve opening at start of blowdown. The effective orifice area ramps from 0 to
   * full over this time period.
   * </p>
   *
   * @param time Opening time [s], 0 for instant opening
   */
  public void setValveOpeningTime(double time) {
    this.valveOpeningTime = time;
  }

  /**
   * Sets the calculation type.
   *
   * @param type Calculation type
   */
  public void setCalculationType(CalculationType type) {
    this.calculationType = type;
  }

  /**
   * Sets the heat transfer type.
   *
   * @param type Heat transfer type
   */
  public void setHeatTransferType(HeatTransferType type) {
    this.heatTransferType = type;
  }

  /**
   * Sets the flow direction.
   *
   * @param direction Flow direction
   */
  public void setFlowDirection(FlowDirection direction) {
    this.flowDirection = direction;
  }

  /**
   * Sets a fixed overall heat transfer coefficient.
   *
   * @param u U-value [W/(m²*K)]
   */
  public void setFixedU(double u) {
    this.fixedU = u;
    this.heatTransferType = HeatTransferType.FIXED_U;
  }

  /**
   * Sets a fixed heat rate.
   *
   * @param q Heat rate [W] (positive = heat into vessel)
   */
  public void setFixedQ(double q) {
    this.fixedQ = q;
    this.heatTransferType = HeatTransferType.FIXED_Q;
  }

  /**
   * Gets the current vessel pressure.
   *
   * @return Pressure [Pa]
   */
  public double getPressure() {
    return thermoSystem.getPressure() * 1e5; // bar to Pa
  }

  /**
   * Gets the current vessel pressure in specified unit.
   *
   * @param unit Pressure unit ("Pa", "bar", "bara", "barg", "psi")
   * @return Pressure in specified unit
   */
  public double getPressure(String unit) {
    double pPa = getPressure();
    switch (unit.toLowerCase()) {
      case "bar":
      case "bara":
        return pPa / 1e5;
      case "barg":
        return pPa / 1e5 - 1.01325;
      case "psi":
        return pPa / 6894.76;
      default:
        return pPa;
    }
  }

  /**
   * Gets the current fluid temperature.
   *
   * @return Temperature [K]
   */
  public double getTemperature() {
    return thermoSystem.getTemperature();
  }

  /**
   * Gets the current fluid temperature in specified unit.
   *
   * @param unit Temperature unit ("K", "C", "F")
   * @return Temperature in specified unit
   */
  public double getTemperature(String unit) {
    double tK = getTemperature();
    switch (unit.toUpperCase()) {
      case "C":
        return tK - 273.15;
      case "F":
        return (tK - 273.15) * 9.0 / 5.0 + 32.0;
      default:
        return tK;
    }
  }

  /**
   * Gets the current wall temperature.
   *
   * @return Wall temperature [K]
   */
  public double getWallTemperature() {
    if (heatTransferType == HeatTransferType.TRANSIENT_WALL && wallModel != null) {
      return wallModel.getInnerWallTemperature();
    }
    return wallTemperature;
  }

  /**
   * Gets the current mass in the vessel.
   *
   * @return Mass [kg]
   */
  public double getMass() {
    return thermoSystem.getTotalNumberOfMoles() * thermoSystem.getMolarMass();
  }

  /**
   * Gets the current density.
   *
   * @return Density [kg/m³]
   */
  public double getDensity() {
    return getMass() / volume;
  }

  /**
   * Gets the outlet stream.
   *
   * <p>
   * The outlet stream represents the gas leaving the vessel through the orifice. It can be
   * connected to downstream equipment like a Flare or Mixer (flare header).
   * </p>
   *
   * <p>
   * Example integration with flare system:
   * 
   * <pre>
   * VesselDepressurization vessel = new VesselDepressurization("Tank", feed);
   * vessel.run();
   * 
   * // Connect to flare
   * Flare flare = new Flare("Emergency Flare", vessel.getOutletStream());
   * flare.setFlameHeight(50.0);
   * 
   * // Or connect via flare header
   * Mixer flareHeader = new Mixer("Flare Header");
   * flareHeader.addStream(vessel.getOutletStream());
   * Flare flare = new Flare("Flare", flareHeader.getOutletStream());
   * </pre>
   *
   * @return Outlet stream with current discharge conditions
   */
  public StreamInterface getOutletStream() {
    return outletStream;
  }

  /**
   * Gets the current mass discharge rate.
   *
   * <p>
   * This is the instantaneous mass flow rate through the orifice, useful for flare capacity checks
   * and header sizing.
   * </p>
   *
   * @param unit Flow rate unit ("kg/s", "kg/hr", "kg/min")
   * @return Mass discharge rate in specified unit
   */
  public double getDischargeRate(String unit) {
    double mdot = massFlowHistory.isEmpty() ? 0.0 : massFlowHistory.get(massFlowHistory.size() - 1);
    switch (unit.toLowerCase()) {
      case "kg/hr":
        return mdot * 3600.0;
      case "kg/min":
        return mdot * 60.0;
      case "kg/s":
      default:
        return mdot;
    }
  }

  /**
   * Gets the peak mass discharge rate from simulation history.
   *
   * <p>
   * This is critical for flare header sizing - the maximum flow rate determines the required
   * capacity.
   * </p>
   *
   * @param unit Flow rate unit ("kg/s", "kg/hr", "kg/min")
   * @return Peak mass discharge rate in specified unit
   */
  public double getPeakDischargeRate(String unit) {
    double peakMdot = massFlowHistory.stream().max(Double::compare).orElse(0.0);
    switch (unit.toLowerCase()) {
      case "kg/hr":
        return peakMdot * 3600.0;
      case "kg/min":
        return peakMdot * 60.0;
      case "kg/s":
      default:
        return peakMdot;
    }
  }

  /**
   * Gets the total mass discharged from simulation history.
   *
   * @param unit Mass unit ("kg", "tonnes")
   * @return Total mass discharged
   */
  public double getTotalMassDischarged(String unit) {
    if (massHistory.isEmpty()) {
      return 0.0;
    }
    double totalKg = massHistory.get(0) - massHistory.get(massHistory.size() - 1);
    switch (unit.toLowerCase()) {
      case "tonnes":
        return totalKg / 1000.0;
      case "kg":
      default:
        return totalKg;
    }
  }

  /**
   * Gets the time to reach a target pressure.
   *
   * <p>
   * Useful for blowdown time calculations per API 521.
   * </p>
   *
   * @param targetPressure Target pressure [bar]
   * @return Time to reach target pressure [s], or -1 if not reached
   */
  public double getTimeToReachPressure(double targetPressure) {
    double targetPa = targetPressure * 1e5;
    for (int i = 0; i < pressureHistory.size(); i++) {
      if (pressureHistory.get(i) <= targetPa) {
        return timeHistory.get(i);
      }
    }
    return -1.0; // Not reached
  }

  /**
   * Gets the minimum temperature reached during blowdown.
   *
   * <p>
   * Critical for MDMT (Minimum Design Metal Temperature) assessment.
   * </p>
   *
   * @param unit Temperature unit ("K", "C")
   * @return Minimum temperature reached
   */
  public double getMinimumTemperatureReached(String unit) {
    double minK = temperatureHistory.stream().min(Double::compare).orElse(0.0);
    if ("C".equalsIgnoreCase(unit)) {
      return minK - 273.15;
    }
    return minK;
  }

  /**
   * Gets the minimum wall temperature reached during blowdown.
   *
   * <p>
   * Critical for MDMT assessment - wall temperature may be limiting for brittle fracture.
   * </p>
   *
   * @param unit Temperature unit ("K", "C")
   * @return Minimum wall temperature reached
   */
  public double getMinimumWallTemperatureReached(String unit) {
    double minK = wallTemperatureHistory.stream().min(Double::compare).orElse(0.0);
    if (twoPhaseHeatTransfer && !liquidWallTemperatureHistory.isEmpty()) {
      double minLiqWall = liquidWallTemperatureHistory.stream().min(Double::compare).orElse(0.0);
      minK = Math.min(minK, minLiqWall);
    }
    if ("C".equalsIgnoreCase(unit)) {
      return minK - 273.15;
    }
    return minK;
  }

  /**
   * Gets the time history.
   *
   * @return List of time values [s]
   */
  public List<Double> getTimeHistory() {
    return timeHistory;
  }

  /**
   * Gets the pressure history.
   *
   * @return List of pressure values [Pa]
   */
  public List<Double> getPressureHistory() {
    return pressureHistory;
  }

  /**
   * Gets the temperature history.
   *
   * @return List of temperature values [K]
   */
  public List<Double> getTemperatureHistory() {
    return temperatureHistory;
  }

  /**
   * Gets the mass history.
   *
   * @return List of mass values [kg]
   */
  public List<Double> getMassHistory() {
    return massHistory;
  }

  /**
   * Clears the history arrays.
   */
  public void clearHistory() {
    timeHistory.clear();
    pressureHistory.clear();
    temperatureHistory.clear();
    massHistory.clear();
    wallTemperatureHistory.clear();
    massFlowHistory.clear();
    heatRateHistory.clear();
    gasWallTemperatureHistory.clear();
    liquidWallTemperatureHistory.clear();
    liquidLevelHistory.clear();
    currentTime = 0.0;
  }

  /**
   * Calculates the mass flow rate through the orifice.
   *
   * @return Mass flow rate [kg/s] (positive for discharge)
   */
  private double calculateMassFlowRate() {
    double P1 = thermoSystem.getPressure() * 1e5; // Pa
    double P2 = backPressure; // Pa
    double T = thermoSystem.getTemperature();
    double Z = thermoSystem.getZ();
    double MW = thermoSystem.getMolarMass(); // kg/mol
    double gamma = thermoSystem.getGamma(); // Cp/Cv

    // No flow if pressure difference is negligible
    if (Math.abs(P1 - P2) < 1.0) {
      return 0.0;
    }

    double pUpstream, pDownstream;
    if (flowDirection == FlowDirection.DISCHARGE) {
      pUpstream = P1;
      pDownstream = P2;
    } else {
      pUpstream = P2;
      pDownstream = P1;
    }

    // No flow if upstream <= downstream
    if (pUpstream <= pDownstream) {
      return 0.0;
    }

    // Critical pressure ratio
    double criticalRatio = Math.pow(2.0 / (gamma + 1.0), gamma / (gamma - 1.0));
    double pressureRatio = pDownstream / pUpstream;

    double orificeArea = Math.PI * orificeDiameter * orificeDiameter / 4.0;
    double massFlowRate;

    // Gas constant R = 8.314 J/(mol·K) - MW is in kg/mol from NeqSim
    double R = 8.314; // J/(mol·K)

    if (pressureRatio <= criticalRatio) {
      // Choked (sonic) flow
      double factor = Math
          .sqrt(gamma * (2.0 / (gamma + 1.0)) * Math.pow(2.0 / (gamma + 1.0), 2.0 / (gamma - 1.0)));
      massFlowRate =
          dischargeCoefficient * orificeArea * pUpstream * Math.sqrt(MW / (Z * R * T)) * factor;
    } else {
      // Subsonic flow
      double factor = Math.sqrt(2.0 * gamma / (gamma - 1.0) * (Math.pow(pressureRatio, 2.0 / gamma)
          - Math.pow(pressureRatio, (gamma + 1.0) / gamma)));
      massFlowRate =
          dischargeCoefficient * orificeArea * pUpstream * Math.sqrt(MW / (Z * R * T)) * factor;
    }

    // Apply valve opening dynamics (if opening time is specified)
    if (valveOpeningTime > 0 && currentTime < valveOpeningTime) {
      // Linear ramp from 0 to full opening
      valveOpeningFraction = currentTime / valveOpeningTime;
      massFlowRate *= valveOpeningFraction;
    } else {
      valveOpeningFraction = 1.0;
    }

    return (flowDirection == FlowDirection.DISCHARGE) ? massFlowRate : -massFlowRate;
  }

  /**
   * Calculates the internal heat transfer coefficient.
   *
   * @return Internal film coefficient [W/(m²*K)]
   */
  private double calculateInternalHeatTransferCoeff() {
    if (heatTransferType == HeatTransferType.ADIABATIC) {
      return 0.0;
    }

    double L = (orientation == VesselOrientation.VERTICAL) ? vesselLength : vesselDiameter;
    double Twall = getWallTemperature();
    double Tfluid = thermoSystem.getTemperature();

    // Get fluid properties
    double k, Cp, mu, rho;
    try {
      // Assuming gas phase dominates
      k = thermoSystem.getPhase(0).getThermalConductivity();
      Cp = thermoSystem.getPhase(0).getCp() / thermoSystem.getPhase(0).getNumberOfMolesInPhase()
          / thermoSystem.getPhase(0).getMolarMass() * 1000.0; // J/(kg*K)
      mu = thermoSystem.getPhase(0).getViscosity();
      rho = thermoSystem.getPhase(0).getDensity("kg/m3");
    } catch (Exception e) {
      // Fallback to typical gas values
      k = 0.02;
      Cp = 1000.0;
      mu = 1e-5;
      rho = 10.0;
    }

    boolean isVertical = (orientation == VesselOrientation.VERTICAL);

    if (flowDirection == FlowDirection.FILLING) {
      double massFlowRate = Math.abs(calculateMassFlowRate());
      return VesselHeatTransferCalculator.calculateMixedConvectionCoefficient(L, Twall, Tfluid,
          massFlowRate, orificeDiameter, k, Cp, mu, rho, isVertical);
    } else {
      return VesselHeatTransferCalculator.calculateInternalFilmCoefficient(L, Twall, Tfluid, k, Cp,
          mu, rho, isVertical);
    }
  }

  /**
   * Calculates the gas phase heat transfer coefficient.
   *
   * @return Gas phase film coefficient [W/(m²*K)]
   */
  private double calculateGasHeatTransferCoeff() {
    if (heatTransferType == HeatTransferType.ADIABATIC) {
      return 0.0;
    }

    double L = (orientation == VesselOrientation.VERTICAL) ? vesselLength : vesselDiameter;
    double Twall = twoPhaseHeatTransfer ? gasWallTemperature : getWallTemperature();
    double Tfluid = thermoSystem.getTemperature();

    // Get gas phase properties
    double k = 0.02, Cp = 1000.0, mu = 1e-5, rho = 10.0;
    try {
      for (int i = 0; i < thermoSystem.getNumberOfPhases(); i++) {
        if (thermoSystem.getPhase(i).getType().equals(neqsim.thermo.phase.PhaseType.GAS)) {
          k = thermoSystem.getPhase(i).getThermalConductivity();
          Cp = thermoSystem.getPhase(i).getCp() / thermoSystem.getPhase(i).getNumberOfMolesInPhase()
              / thermoSystem.getPhase(i).getMolarMass() * 1000.0;
          mu = thermoSystem.getPhase(i).getViscosity();
          rho = thermoSystem.getPhase(i).getDensity("kg/m3");
          break;
        }
      }
    } catch (Exception e) {
      // Use fallback values
    }

    boolean isVertical = (orientation == VesselOrientation.VERTICAL);

    if (flowDirection == FlowDirection.FILLING) {
      double massFlowRate = Math.abs(calculateMassFlowRate());
      return VesselHeatTransferCalculator.calculateMixedConvectionCoefficient(L, Twall, Tfluid,
          massFlowRate, orificeDiameter, k, Cp, mu, rho, isVertical);
    } else {
      return VesselHeatTransferCalculator.calculateInternalFilmCoefficient(L, Twall, Tfluid, k, Cp,
          mu, rho, isVertical);
    }
  }

  /**
   * Calculates the liquid phase heat transfer coefficient.
   *
   * <p>
   * Uses natural convection or nucleate boiling correlation depending on wall superheat.
   * </p>
   *
   * @return Liquid phase film coefficient [W/(m²*K)]
   */
  private double calculateLiquidHeatTransferCoeff() {
    if (heatTransferType == HeatTransferType.ADIABATIC) {
      return 0.0;
    }

    double L = (orientation == VesselOrientation.VERTICAL) ? vesselLength * calculateLiquidLevel()
        : vesselDiameter;
    if (L <= 0.01) {
      L = 0.01; // Minimum characteristic length
    }
    double Twall = twoPhaseHeatTransfer ? liquidWallTemperature : getWallTemperature();
    double Tfluid = thermoSystem.getTemperature();

    // Get liquid phase properties
    double k = 0.1, Cp = 2000.0, mu = 1e-3, rho = 500.0;
    double satTemp = Tfluid; // Assume at saturation for two-phase

    try {
      for (int i = 0; i < thermoSystem.getNumberOfPhases(); i++) {
        if (!thermoSystem.getPhase(i).getType().equals(neqsim.thermo.phase.PhaseType.GAS)) {
          k = thermoSystem.getPhase(i).getThermalConductivity();
          Cp = thermoSystem.getPhase(i).getCp() / thermoSystem.getPhase(i).getNumberOfMolesInPhase()
              / thermoSystem.getPhase(i).getMolarMass() * 1000.0;
          mu = thermoSystem.getPhase(i).getViscosity();
          rho = thermoSystem.getPhase(i).getDensity("kg/m3");
          break;
        }
      }
    } catch (Exception e) {
      // Use fallback values
    }

    boolean isVertical = (orientation == VesselOrientation.VERTICAL);

    // Use natural convection for liquid, potentially enhanced by boiling
    double hConv = VesselHeatTransferCalculator.calculateInternalFilmCoefficient(L, Twall, Tfluid,
        k, Cp, mu, rho, isVertical);

    // Check for boiling conditions (wall above saturation)
    if (Twall > satTemp + 1.0) {
      // Use wetted wall correlation (includes boiling)
      return VesselHeatTransferCalculator.calculateWettedWallFilmCoefficient(Twall, Tfluid, satTemp,
          L, k, Cp, mu, rho, isVertical);
    }

    return hConv;
  }

  /**
   * Calculates the heat transfer rate to the fluid.
   *
   * @return Heat rate [W] (positive = heat into fluid)
   */
  private double calculateHeatRate() {
    double Q = 0.0;

    switch (heatTransferType) {
      case ADIABATIC:
        Q = 0.0;
        break;
      case FIXED_Q:
        Q = fixedQ;
        break;
      case FIXED_U:
        double area = getWallArea();
        Q = fixedU * area * (ambientTemperature - thermoSystem.getTemperature());
        break;
      case CALCULATED:
      case TRANSIENT_WALL:
        if (twoPhaseHeatTransfer && thermoSystem.getNumberOfPhases() > 1) {
          Q = calculateTwoPhaseHeatRate();
        } else {
          double hInner = calculateInternalHeatTransferCoeff();
          double wallArea = getWallArea();
          double Twall = getWallTemperature();
          Q = hInner * wallArea * (Twall - thermoSystem.getTemperature());
        }
        break;
      default:
        Q = 0.0;
    }

    // Add fire heat input if fire case is enabled (API 521)
    if (fireCase && fireHeatFlux > 0) {
      double fireHeat = fireHeatFlux * wetSurfaceFraction * getWallArea();
      Q += fireHeat;
    }

    return Q;
  }

  /**
   * Calculates heat transfer rate for two-phase conditions.
   *
   * <p>
   * Computes separate heat transfer rates for the gas-wetted and liquid-wetted wall areas, using
   * appropriate correlations for each phase.
   * </p>
   *
   * @return Total heat rate [W] (positive = heat into fluid)
   */
  private double calculateTwoPhaseHeatRate() {
    double Tfluid = thermoSystem.getTemperature();

    // Heat from gas-wetted wall
    double hGas = calculateGasHeatTransferCoeff();
    double gasArea = getUnwettedWallArea();
    double TwallGas = twoPhaseHeatTransfer ? gasWallTemperature : getWallTemperature();
    double Qgas = hGas * gasArea * (TwallGas - Tfluid);

    // Heat from liquid-wetted wall
    double hLiquid = calculateLiquidHeatTransferCoeff();
    double liquidArea = getWettedWallArea();
    double TwallLiquid = twoPhaseHeatTransfer ? liquidWallTemperature : getWallTemperature();
    double Qliquid = hLiquid * liquidArea * (TwallLiquid - Tfluid);

    return Qgas + Qliquid;
  }

  /**
   * Gets the total inner wall surface area.
   *
   * @return Wall area [m²]
   */
  private double getWallArea() {
    // Cylindrical vessel with hemispherical ends approximation
    double cylinderArea = Math.PI * vesselDiameter * vesselLength;
    double endArea = Math.PI * vesselDiameter * vesselDiameter;
    return cylinderArea + endArea;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // Initialize vessel at current state - use init(1) to preserve phase structure
    thermoSystem.init(1);
    thermoSystem.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);

    // Adjust total moles to match specified volume
    double currentVolume = thermoSystem.getVolume() * 1e-5; // L to m³
    double scaleFactor = volume / currentVolume;
    thermoSystem.setTotalNumberOfMoles(thermoSystem.getTotalNumberOfMoles() * scaleFactor);
    thermoSystem.init(1);

    // If initial liquid level is specified, set up two-phase system
    // For pure components, check if feed was two-phase (initialBeta < 1.0)
    // For mixtures, check current number of phases
    boolean shouldInitializeTwoPhase =
        initialLiquidLevel > 0.0 && (thermoSystem.getNumberOfPhases() > 1
            || (thermoSystem.getNumberOfComponents() == 1 && initialBeta < 1.0));

    if (shouldInitializeTwoPhase) {
      // For pure components, ensure we have two phases before initializing liquid level
      if (thermoSystem.getNumberOfComponents() == 1 && thermoSystem.getNumberOfPhases() == 1) {
        thermoSystem.setNumberOfPhases(2);
        thermoSystem.setBeta(initialBeta);
        thermoSystem.init(1);
      }
      initializeWithLiquidLevel();
    } else {
      // Final initialization with full property calculations
      thermoSystem.init(3);
    }

    // Record initial state
    recordState(0.0, 0.0);

    // Update outlet stream
    updateOutletStream();

    setCalculationIdentifier(id);
  }

  /**
   * Initializes the vessel with the specified initial liquid level.
   *
   * <p>
   * This method adjusts the moles in each phase to match the target liquid and gas volumes based on
   * the specified initial liquid level fraction. Similar to the approach used in Separator.
   * </p>
   */
  private void initializeWithLiquidLevel() {
    // Calculate target liquid and gas volumes based on level
    double targetLiquidVolume = calculateLiquidVolumeFromLevel(initialLiquidLevel);
    double targetGasVolume = volume - targetLiquidVolume;

    if (targetLiquidVolume <= 0.0 || targetGasVolume <= 0.0) {
      return; // Can't have valid two-phase with specified level
    }

    thermoSystem.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);

    // Scale moles in each phase to match target volumes
    for (int j = 0; j < thermoSystem.getNumberOfPhases(); j++) {
      double currentPhaseVolume = thermoSystem.getPhase(j).getVolume() * 1e-5; // L to m³
      double targetVolume;

      if (thermoSystem.getPhase(j).getType().equals(neqsim.thermo.phase.PhaseType.GAS)) {
        targetVolume = targetGasVolume;
      } else {
        targetVolume = targetLiquidVolume;
      }

      if (currentPhaseVolume > 0.0) {
        double relFact = targetVolume / currentPhaseVolume;
        for (int i = 0; i < thermoSystem.getPhase(j).getNumberOfComponents(); i++) {
          double molesInPhase = thermoSystem.getPhase(j).getComponent(i).getNumberOfMolesInPhase();
          thermoSystem.addComponent(i, (relFact - 1.0) * molesInPhase, j);
        }
      }
    }

    // Re-flash at constant T, P to equilibrate phases
    ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem);
    if (thermoSystem.getNumberOfComponents() > 1) {
      ops.TPflash();
    } else {
      // For pure components, preserve the vapor fraction from the feed
      // TPflash won't change phase distribution for pure components at saturation
      thermoSystem.setBeta(initialBeta);
    }
    thermoSystem.init(3);
    thermoSystem.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
  }

  /**
   * Calculates the liquid volume corresponding to a given liquid level fraction.
   *
   * @param levelFraction Liquid level fraction (0-1)
   * @return Liquid volume [m³]
   */
  private double calculateLiquidVolumeFromLevel(double levelFraction) {
    if (levelFraction <= 0.0) {
      return 0.0;
    }
    if (levelFraction >= 1.0) {
      return volume;
    }

    if (orientation == VesselOrientation.VERTICAL) {
      // Vertical cylinder: liquid volume is proportional to level
      return volume * levelFraction;
    } else {
      // Horizontal cylinder: liquid volume from segment formula
      // V_liquid / V_total = (1/pi) * [arccos(1-2*h) - (1-2*h)*sqrt(4*h - 4*h^2)]
      // where h is the level fraction (height/diameter)
      double h = levelFraction;
      double term1 = 1.0 - 2.0 * h;
      double sqrtTerm = Math.sqrt(Math.max(0, 4.0 * h - 4.0 * h * h));
      double volumeFraction = (Math.acos(term1) - term1 * sqrtTerm) / Math.PI;
      return volume * volumeFraction;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt, UUID id) {
    if (dt <= 0.0) {
      return;
    }

    // Store initial state for energy balance
    double initialMass = getMass();
    double initialU = thermoSystem.getInternalEnergy();
    double initialH = thermoSystem.getEnthalpy();
    double initialS = thermoSystem.getEntropy();
    double initialT = thermoSystem.getTemperature();

    // Calculate mass flow
    double massFlowRate = calculateMassFlowRate();
    double deltaMass = massFlowRate * dt;

    // Don't remove more mass than available
    if (deltaMass > initialMass * 0.99) {
      deltaMass = initialMass * 0.99;
      massFlowRate = deltaMass / dt;
    }

    // Update mass (adjust moles proportionally for all components in all phases)
    double newMass = initialMass - deltaMass;
    double moleFactor = newMass / initialMass;

    // Scale moles for each component in each phase
    for (int j = 0; j < thermoSystem.getMaxNumberOfPhases(); j++) {
      for (int i = 0; i < thermoSystem.getPhase(j).getNumberOfComponents(); i++) {
        double currentMoles = thermoSystem.getPhase(j).getComponent(i).getNumberOfMolesInPhase();
        double scaledMoles = currentMoles * moleFactor;
        thermoSystem.getPhase(j).getComponent(i).setNumberOfmoles(scaledMoles);
        thermoSystem.getPhase(j).getComponent(i).setNumberOfMolesInPhase(scaledMoles);
      }
    }
    thermoSystem.setTotalNumberOfMoles(thermoSystem.getTotalNumberOfMoles() * moleFactor);

    // Reinitialize with new moles before flash
    thermoSystem.init(0);
    thermoSystem.init(1);

    // Calculate new density
    double newDensity = newMass / volume;

    // Calculate heat transfer
    double Q = calculateHeatRate();
    double heatInput = Q * dt;

    // Update thermodynamic state based on calculation type
    ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem);

    try {
      switch (calculationType) {
        case ISOTHERMAL:
          // Constant temperature - solve for new pressure at fixed T and V
          thermoSystem.setTemperature(initialT);
          ops.TVflash(volume, "m3");
          break;

        case ISENTHALPIC:
          // Constant enthalpy (per unit mass) - specific h stays constant
          double targetH = initialH / initialMass * newMass;
          ops.VHflash(volume, targetH, "m3", "J");
          break;

        case ISENTROPIC:
          // Constant entropy (per unit mass) - specific s stays constant
          double targetS = initialS / initialMass * newMass;
          ops.VSflash(volume, targetS, "m3", "J/K");
          break;

        case ISENERGETIC:
          // Constant internal energy
          ops.VUflash(volume, initialU, "m3", "J");
          break;

        case ENERGY_BALANCE:
          // Full energy balance: U_new = U_old - mdot*h_out*dt + Q*dt
          double hOut = (flowDirection == FlowDirection.DISCHARGE) ? initialH / initialMass
              : thermoSystem.getEnthalpy() / newMass;
          double newU = initialU - massFlowRate * hOut * dt + heatInput;
          ops.VUflash(volume, newU, "m3", "J");
          break;
      }
    } catch (Exception e) {
      logger.warn("Flash calculation failed: " + e.getMessage());
      // Fallback to TPflash at current conditions
      try {
        ops.TPflash();
      } catch (Exception e2) {
        logger.error("TPflash also failed: " + e2.getMessage());
      }
    }

    thermoSystem.init(3);

    // Update wall temperature
    if (heatTransferType == HeatTransferType.TRANSIENT_WALL && wallModel != null) {
      if (twoPhaseHeatTransfer && thermoSystem.getNumberOfPhases() > 1) {
        // Update gas wall model
        double hGas = calculateGasHeatTransferCoeff();
        gasWallModel.advanceTimeStep(dt, thermoSystem.getTemperature(), hGas, ambientTemperature,
            externalHeatTransferCoeff);
        gasWallTemperature = gasWallModel.getMeanWallTemperature();

        // Update liquid wall model
        double hLiquid = calculateLiquidHeatTransferCoeff();
        liquidWallModel.advanceTimeStep(dt, thermoSystem.getTemperature(), hLiquid,
            ambientTemperature, externalHeatTransferCoeff);
        liquidWallTemperature = liquidWallModel.getMeanWallTemperature();

        // Average wall temperature weighted by area
        double gasArea = getUnwettedWallArea();
        double liquidArea = getWettedWallArea();
        double totalArea = gasArea + liquidArea;
        if (totalArea > 0) {
          wallTemperature =
              (gasWallTemperature * gasArea + liquidWallTemperature * liquidArea) / totalArea;
        }
      } else {
        double hInner = calculateInternalHeatTransferCoeff();
        wallModel.advanceTimeStep(dt, thermoSystem.getTemperature(), hInner, ambientTemperature,
            externalHeatTransferCoeff);
        wallTemperature = wallModel.getMeanWallTemperature();
      }
    } else if (calculationType == CalculationType.ENERGY_BALANCE) {
      if (twoPhaseHeatTransfer && thermoSystem.getNumberOfPhases() > 1) {
        // Two-phase wall energy balance
        double gasArea = getUnwettedWallArea();
        double liquidArea = getWettedWallArea();
        double wallThicknessTotal = hasLiner ? wallThickness + linerThickness : wallThickness;

        // Gas wall energy balance
        double gasWallMass = wallDensity * gasArea * wallThicknessTotal;
        if (gasWallMass > 0) {
          double QinGas =
              externalHeatTransferCoeff * gasArea * (ambientTemperature - gasWallTemperature);
          double QoutGas = calculateGasHeatTransferCoeff() * gasArea
              * (gasWallTemperature - thermoSystem.getTemperature());
          gasWallTemperature += (QinGas - QoutGas) * dt / (gasWallMass * wallHeatCapacity);
        }

        // Liquid wall energy balance
        double liquidWallMass = wallDensity * liquidArea * wallThicknessTotal;
        if (liquidWallMass > 0) {
          double QinLiquid =
              externalHeatTransferCoeff * liquidArea * (ambientTemperature - liquidWallTemperature);
          double QoutLiquid = calculateLiquidHeatTransferCoeff() * liquidArea
              * (liquidWallTemperature - thermoSystem.getTemperature());
          liquidWallTemperature +=
              (QinLiquid - QoutLiquid) * dt / (liquidWallMass * wallHeatCapacity);
        }

        // Average wall temperature
        double totalArea = gasArea + liquidArea;
        if (totalArea > 0) {
          wallTemperature =
              (gasWallTemperature * gasArea + liquidWallTemperature * liquidArea) / totalArea;
        }
      } else {
        // Simple wall energy balance
        double wallMass = wallDensity * getWallArea()
            * (hasLiner ? wallThickness + linerThickness : wallThickness);
        double Qin =
            externalHeatTransferCoeff * getWallArea() * (ambientTemperature - wallTemperature);
        double Qout = calculateInternalHeatTransferCoeff() * getWallArea()
            * (wallTemperature - thermoSystem.getTemperature());
        wallTemperature += (Qin - Qout) * dt / (wallMass * wallHeatCapacity);
      }
    }

    // Update time and record state
    currentTime += dt;
    recordState(massFlowRate, Q);

    // Update outlet stream
    updateOutletStream();

    setCalculationIdentifier(id);
  }

  /**
   * Records the current state to history arrays.
   *
   * @param massFlowRate Current mass flow rate [kg/s]
   * @param heatRate Current heat rate [W]
   */
  private void recordState(double massFlowRate, double heatRate) {
    timeHistory.add(currentTime);
    pressureHistory.add(getPressure());
    temperatureHistory.add(getTemperature());
    massHistory.add(getMass());
    wallTemperatureHistory.add(getWallTemperature());
    massFlowHistory.add(massFlowRate);
    heatRateHistory.add(heatRate);

    // Two-phase specific data
    if (twoPhaseHeatTransfer) {
      gasWallTemperatureHistory.add(getGasWallTemperature());
      liquidWallTemperatureHistory.add(getLiquidWallTemperature());
      liquidLevelHistory.add(calculateLiquidLevel());
    }
  }

  /**
   * Updates the outlet stream with current conditions.
   */
  private void updateOutletStream() {
    SystemInterface outSystem = thermoSystem.clone();
    outSystem.setPressure(backPressure / 1e5);
    ThermodynamicOperations ops = new ThermodynamicOperations(outSystem);
    try {
      // Isenthalpic expansion through orifice
      ops.PHflash(thermoSystem.getEnthalpy() / thermoSystem.getTotalNumberOfMoles(), 0);
    } catch (Exception e) {
      logger.warn("Outlet flash failed: " + e.getMessage());
    }
    outletStream.setThermoSystem(outSystem);
    outletStream.setFlowRate(Math.abs(calculateMassFlowRate()), "kg/sec");
  }

  /**
   * Gets the vent temperature (temperature after expansion through orifice).
   *
   * @return Vent temperature [K]
   */
  public double getVentTemperature() {
    return outletStream.getTemperature();
  }

  /**
   * Gets the current vapor fraction.
   *
   * @return Vapor mole fraction (0-1)
   */
  public double getVaporFraction() {
    if (thermoSystem.getNumberOfPhases() == 1) {
      return thermoSystem.getPhase(0).getType().equals(neqsim.thermo.phase.PhaseType.GAS) ? 1.0
          : 0.0;
    }
    return thermoSystem.getBeta();
  }

  /**
   * Gets the current internal energy.
   *
   * @return Internal energy [J]
   */
  public double getInternalEnergy() {
    return thermoSystem.getInternalEnergy();
  }

  /**
   * Gets the current enthalpy.
   *
   * @return Enthalpy [J]
   */
  public double getEnthalpy() {
    return thermoSystem.getEnthalpy();
  }

  /**
   * Gets the current entropy.
   *
   * @return Entropy [J/K]
   */
  public double getEntropy() {
    return thermoSystem.getEntropy();
  }

  /**
   * Gets the thermoSystem.
   *
   * @return The thermodynamic system
   */
  public SystemInterface getThermoSystem() {
    return thermoSystem;
  }

  // ============================================================================
  // SIMULATION HELPERS AND VALIDATION
  // ============================================================================

  /**
   * Result object containing simulation history data.
   *
   * <p>
   * This provides a structured way to access all simulation results after running
   * {@link #runSimulation(double, double)}.
   * </p>
   */
  public static class SimulationResult {
    private final List<Double> time;
    private final List<Double> pressure;
    private final List<Double> temperature;
    private final List<Double> mass;
    private final List<Double> wallTemperature;
    private final List<Double> massFlowRate;
    private final List<Double> gasWallTemperature;
    private final List<Double> liquidWallTemperature;
    private final List<Double> liquidLevel;
    private final double endTime;
    private final double timeStep;

    SimulationResult(List<Double> time, List<Double> pressure, List<Double> temperature,
        List<Double> mass, List<Double> wallTemperature, List<Double> massFlowRate,
        List<Double> gasWallTemperature, List<Double> liquidWallTemperature,
        List<Double> liquidLevel, double endTime, double timeStep) {
      this.time = time;
      this.pressure = pressure;
      this.temperature = temperature;
      this.mass = mass;
      this.wallTemperature = wallTemperature;
      this.massFlowRate = massFlowRate;
      this.gasWallTemperature = gasWallTemperature;
      this.liquidWallTemperature = liquidWallTemperature;
      this.liquidLevel = liquidLevel;
      this.endTime = endTime;
      this.timeStep = timeStep;
    }

    /** @return Time history [s] */
    public List<Double> getTime() {
      return time;
    }

    /** @return Pressure history [bar] */
    public List<Double> getPressure() {
      return pressure;
    }

    /** @return Temperature history [K] */
    public List<Double> getTemperature() {
      return temperature;
    }

    /** @return Mass history [kg] */
    public List<Double> getMass() {
      return mass;
    }

    /** @return Wall temperature history [K] */
    public List<Double> getWallTemperature() {
      return wallTemperature;
    }

    /** @return Mass flow rate history [kg/s] */
    public List<Double> getMassFlowRate() {
      return massFlowRate;
    }

    /** @return Gas wall temperature history [K] (two-phase only) */
    public List<Double> getGasWallTemperature() {
      return gasWallTemperature;
    }

    /** @return Liquid wall temperature history [K] (two-phase only) */
    public List<Double> getLiquidWallTemperature() {
      return liquidWallTemperature;
    }

    /** @return Liquid level history [0-1] (two-phase only) */
    public List<Double> getLiquidLevel() {
      return liquidLevel;
    }

    /** @return Simulation end time [s] */
    public double getEndTime() {
      return endTime;
    }

    /** @return Time step used [s] */
    public double getTimeStep() {
      return timeStep;
    }

    /** @return Number of data points */
    public int size() {
      return time.size();
    }

    /** @return Initial pressure [bar] */
    public double getInitialPressure() {
      return pressure.get(0);
    }

    /** @return Final pressure [bar] */
    public double getFinalPressure() {
      return pressure.get(pressure.size() - 1);
    }

    /** @return Initial temperature [K] */
    public double getInitialTemperature() {
      return temperature.get(0);
    }

    /** @return Final temperature [K] */
    public double getFinalTemperature() {
      return temperature.get(temperature.size() - 1);
    }

    /** @return Total mass discharged [kg] */
    public double getMassDischarged() {
      return mass.get(0) - mass.get(mass.size() - 1);
    }

    /** @return Fraction of mass discharged [0-1] */
    public double getMassDischargedFraction() {
      return 1.0 - mass.get(mass.size() - 1) / mass.get(0);
    }

    /** @return Minimum temperature reached [K] */
    public double getMinTemperature() {
      return temperature.stream().min(Double::compare).orElse(0.0);
    }

    /** @return Minimum wall temperature reached [K] */
    public double getMinWallTemperature() {
      return wallTemperature.stream().min(Double::compare).orElse(0.0);
    }
  }

  /**
   * Runs a complete simulation and returns structured results.
   *
   * <p>
   * This is a convenience method that handles the time loop and data collection internally. It
   * provides a cleaner API compared to manually calling {@link #runTransient(double, UUID)} in a
   * loop.
   * </p>
   *
   * <p>
   * Example:
   * 
   * <pre>
   * vessel.run();
   * SimulationResult result = vessel.runSimulation(300.0, 0.5); // 5 min, 0.5s steps
   * System.out.println("Min temp: " + result.getMinTemperature());
   * System.out.println("Mass discharged: " + result.getMassDischarged() + " kg");
   * </pre>
   *
   * @param endTime Total simulation time [s]
   * @param dt Time step [s]
   * @return SimulationResult containing all history data
   */
  public SimulationResult runSimulation(double endTime, double dt) {
    return runSimulation(endTime, dt, 1);
  }

  /**
   * Runs a complete simulation with configurable output interval.
   *
   * @param endTime Total simulation time [s]
   * @param dt Time step [s]
   * @param recordInterval Record data every N steps (1 = every step)
   * @return SimulationResult containing history data
   */
  public SimulationResult runSimulation(double endTime, double dt, int recordInterval) {
    List<Double> timeData = new ArrayList<>();
    List<Double> pressureData = new ArrayList<>();
    List<Double> temperatureData = new ArrayList<>();
    List<Double> massData = new ArrayList<>();
    List<Double> wallTempData = new ArrayList<>();
    List<Double> massFlowData = new ArrayList<>();
    List<Double> gasWallData = new ArrayList<>();
    List<Double> liqWallData = new ArrayList<>();
    List<Double> liquidLevelData = new ArrayList<>();

    UUID uuid = UUID.randomUUID();
    int step = 0;

    // Record initial state
    timeData.add(0.0);
    pressureData.add(getPressure("bar"));
    temperatureData.add(getTemperature());
    massData.add(getMass());
    wallTempData.add(getWallTemperature());
    massFlowData.add(0.0);
    gasWallData.add(getGasWallTemperature());
    liqWallData.add(getLiquidWallTemperature());
    liquidLevelData.add(getLiquidLevel());

    double prevMass = getMass();

    for (double t = dt; t <= endTime + dt / 2; t += dt) {
      runTransient(dt, uuid);
      step++;

      if (step % recordInterval == 0) {
        timeData.add(t);
        pressureData.add(getPressure("bar"));
        temperatureData.add(getTemperature());
        double currentMass = getMass();
        massData.add(currentMass);
        wallTempData.add(getWallTemperature());
        massFlowData.add((prevMass - currentMass) / dt);
        gasWallData.add(getGasWallTemperature());
        liqWallData.add(getLiquidWallTemperature());
        liquidLevelData.add(getLiquidLevel());
        prevMass = currentMass;
      }
    }

    return new SimulationResult(timeData, pressureData, temperatureData, massData, wallTempData,
        massFlowData, gasWallData, liqWallData, liquidLevelData, endTime, dt);
  }

  /**
   * Validates the vessel configuration and throws exceptions for invalid setups.
   *
   * <p>
   * Call this method before running a simulation to catch common configuration errors:
   * <ul>
   * <li>Back pressure higher than initial pressure</li>
   * <li>Unreasonably large orifice compared to vessel</li>
   * <li>Invalid liquid level settings</li>
   * <li>Missing heat transfer configuration for ENERGY_BALANCE mode</li>
   * </ul>
   *
   * @throws IllegalStateException if configuration is invalid
   */
  public void validate() {
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    // Check basic setup
    if (thermoSystem == null) {
      errors.add("No fluid system set. Call setInletStream() first.");
    }

    // Check pressure logic
    double initialPressure = thermoSystem != null ? thermoSystem.getPressure() / 1e5 : 0;
    double backPressureBar = backPressure / 1e5;
    if (backPressureBar >= initialPressure) {
      errors.add(String.format(
          "Back pressure (%.1f bar) >= initial pressure (%.1f bar). No flow will occur.",
          backPressureBar, initialPressure));
    }

    // Check orifice size
    double orificeArea = Math.PI * orificeDiameter * orificeDiameter / 4.0;
    double vesselCrossSection = Math.PI * vesselDiameter * vesselDiameter / 4.0;
    if (orificeArea > vesselCrossSection * 0.5) {
      warnings.add(String.format(
          "Orifice area (%.1f%% of vessel cross-section) is very large. "
              + "Results may not represent realistic orifice flow.",
          100 * orificeArea / vesselCrossSection));
    }

    // Check two-phase settings
    if (initialLiquidLevel > 0 && !twoPhaseHeatTransfer) {
      warnings.add("Initial liquid level is set but two-phase heat transfer is disabled. "
          + "Consider calling setTwoPhaseHeatTransfer(true).");
    }

    if (twoPhaseHeatTransfer && initialLiquidLevel <= 0) {
      warnings.add("Two-phase heat transfer is enabled but initial liquid level is 0. "
          + "Gas and liquid wall temperatures will be identical.");
    }

    // Check heat transfer settings
    if (calculationType == CalculationType.ENERGY_BALANCE) {
      if (heatTransferType == HeatTransferType.CALCULATED
          || heatTransferType == HeatTransferType.TRANSIENT_WALL) {
        if (vesselLength <= 0 || vesselDiameter <= 0) {
          errors.add("Vessel geometry required for calculated heat transfer. "
              + "Call setVesselGeometry().");
        }
      }
    }

    // Check volume consistency
    double calculatedVolume = Math.PI * vesselDiameter * vesselDiameter / 4.0 * vesselLength;
    if (Math.abs(calculatedVolume - volume) / volume > 0.01) {
      warnings.add(String
          .format("Vessel volume (%.3f m³) differs from geometry-calculated volume (%.3f m³). "
              + "Heat transfer area may be inconsistent.", volume, calculatedVolume));
    }

    // Log warnings
    for (String warning : warnings) {
      logger.warn("Validation warning: {}", warning);
    }

    // Throw on errors
    if (!errors.isEmpty()) {
      throw new IllegalStateException(
          "Vessel configuration errors:\n- " + String.join("\n- ", errors));
    }
  }

  /**
   * Validates configuration and returns warnings without throwing exceptions.
   *
   * @return List of warning messages (empty if no issues)
   */
  public List<String> validateWithWarnings() {
    List<String> warnings = new ArrayList<>();

    try {
      validate();
    } catch (IllegalStateException e) {
      warnings.add("ERROR: " + e.getMessage());
    }

    // Additional non-critical warnings
    if (thermoSystem != null) {
      double initialPressure = thermoSystem.getPressure() / 1e5;
      if (initialPressure > 700) {
        warnings.add(String.format(
            "Very high pressure (%.0f bar). Ensure equation of state is valid for this range.",
            initialPressure));
      }

      double temperature = thermoSystem.getTemperature();
      if (temperature < 100) {
        warnings.add(String.format("Very low temperature (%.1f K). Check for cryogenic validity.",
            temperature));
      }
    }

    return warnings;
  }

  /**
   * Creates a two-phase pure component system at its saturation conditions.
   *
   * <p>
   * This is a convenience method that handles the bubble point calculation and phase setup
   * automatically. For pure components, a simple TPflash won't produce two phases - you need to be
   * exactly at saturation and explicitly set the phase fraction.
   * </p>
   *
   * <p>
   * Example:
   * 
   * <pre>
   * // Create CO2 at 250K with 60% vapor, 40% liquid
   * SystemInterface co2 = VesselDepressurization.createTwoPhaseFluid("CO2", 250.0, 0.6);
   * Stream feed = new Stream("feed", co2);
   * </pre>
   *
   * @param componentName Component name (e.g., "CO2", "propane", "methane")
   * @param temperature Temperature [K]
   * @param vaporFraction Desired vapor mole fraction (0 = all liquid, 1 = all vapor)
   * @return Configured two-phase thermodynamic system
   */
  public static SystemInterface createTwoPhaseFluid(String componentName, double temperature,
      double vaporFraction) {
    // Create initial system to find bubble point
    SystemInterface init = new neqsim.thermo.system.SystemSrkEos(temperature, 1.0);
    init.addComponent(componentName, 1.0);
    init.setMixingRule("classic");
    init.createDatabase(true);

    // Calculate bubble point pressure
    ThermodynamicOperations ops = new ThermodynamicOperations(init);
    try {
      ops.bubblePointPressureFlash(false);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Could not find saturation pressure for " + componentName + " at " + temperature + " K",
          e);
    }

    double saturationPressure = init.getPressure();

    // Create final system at saturation
    SystemInterface system = new neqsim.thermo.system.SystemSrkEos(temperature, saturationPressure);
    system.addComponent(componentName, 1.0);
    system.setMixingRule("classic");
    system.createDatabase(true);

    // Force two-phase and set vapor fraction
    system.setNumberOfPhases(2);
    system.setBeta(vaporFraction);
    system.init(0);
    system.init(1);

    return system;
  }

  /**
   * Creates a two-phase pure component system at specified pressure.
   *
   * <p>
   * If the specified pressure corresponds to saturation conditions, two phases will form. The
   * temperature will be the saturation temperature at the given pressure.
   * </p>
   *
   * @param componentName Component name
   * @param pressure Pressure [bar]
   * @param vaporFraction Desired vapor mole fraction (0 = all liquid, 1 = all vapor)
   * @return Configured two-phase thermodynamic system
   */
  public static SystemInterface createTwoPhaseFluidAtPressure(String componentName, double pressure,
      double vaporFraction) {
    // Create initial system to find dew point temperature
    SystemInterface init = new neqsim.thermo.system.SystemSrkEos(300.0, pressure);
    init.addComponent(componentName, 1.0);
    init.setMixingRule("classic");
    init.createDatabase(true);

    // Calculate dew point temperature (same as bubble point for pure component)
    ThermodynamicOperations ops = new ThermodynamicOperations(init);
    try {
      ops.dewPointTemperatureFlash();
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Could not find saturation temperature for " + componentName + " at " + pressure + " bar",
          e);
    }

    double saturationTemp = init.getTemperature();

    // Create final system at saturation
    SystemInterface system = new neqsim.thermo.system.SystemSrkEos(saturationTemp, pressure);
    system.addComponent(componentName, 1.0);
    system.setMixingRule("classic");
    system.createDatabase(true);

    // Force two-phase and set vapor fraction
    system.setNumberOfPhases(2);
    system.setBeta(vaporFraction);
    system.init(0);
    system.init(1);

    return system;
  }

  // ================================================================================
  // Export and Analysis Methods
  // ================================================================================

  /**
   * Exports transient simulation results to CSV format.
   *
   * <p>
   * The CSV includes time, pressure, temperature, mass, wall temperature, mass flow rate, and heat
   * rate. This can be used for post-processing in Excel, Python pandas, or other tools.
   * </p>
   *
   * @return CSV formatted string with header row
   */
  public String exportToCSV() {
    StringBuilder csv = new StringBuilder();

    // Header
    csv.append(
        "Time[s],Pressure[bar],Temperature[K],Temperature[C],Mass[kg],WallTemp[K],MassFlow[kg/s],HeatRate[W]");
    if (twoPhaseHeatTransfer) {
      csv.append(",GasWallTemp[K],LiquidWallTemp[K],LiquidLevel[-]");
    }
    csv.append("\n");

    // Data rows
    int n = timeHistory.size();
    for (int i = 0; i < n; i++) {
      csv.append(String.format("%.2f,%.4f,%.2f,%.2f,%.4f,%.2f,%.6f,%.2f", timeHistory.get(i),
          pressureHistory.get(i) / 1e5, // Pa to bar
          temperatureHistory.get(i), temperatureHistory.get(i) - 273.15, massHistory.get(i),
          wallTemperatureHistory.size() > i ? wallTemperatureHistory.get(i) : 0.0,
          massFlowHistory.size() > i ? massFlowHistory.get(i) : 0.0,
          heatRateHistory.size() > i ? heatRateHistory.get(i) : 0.0));

      if (twoPhaseHeatTransfer) {
        csv.append(String.format(",%.2f,%.2f,%.4f",
            gasWallTemperatureHistory.size() > i ? gasWallTemperatureHistory.get(i) : 0.0,
            liquidWallTemperatureHistory.size() > i ? liquidWallTemperatureHistory.get(i) : 0.0,
            liquidLevelHistory.size() > i ? liquidLevelHistory.get(i) : 0.0));
      }
      csv.append("\n");
    }

    return csv.toString();
  }

  /**
   * Exports transient results to JSON format.
   *
   * @return JSON formatted string
   */
  public String exportToJSON() {
    StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"vessel\": \"").append(getName()).append("\",\n");
    json.append("  \"volume_m3\": ").append(String.format("%.4f", volume)).append(",\n");
    json.append("  \"orifice_mm\": ").append(String.format("%.2f", orificeDiameter * 1000))
        .append(",\n");
    json.append("  \"data\": [\n");

    int n = timeHistory.size();
    for (int i = 0; i < n; i++) {
      json.append("    {");
      json.append("\"t\": ").append(String.format("%.2f", timeHistory.get(i))).append(", ");
      json.append("\"P_bar\": ").append(String.format("%.4f", pressureHistory.get(i) / 1e5))
          .append(", ");
      json.append("\"T_K\": ").append(String.format("%.2f", temperatureHistory.get(i)))
          .append(", ");
      json.append("\"m_kg\": ").append(String.format("%.4f", massHistory.get(i))).append(", ");
      json.append("\"mdot_kgs\": ")
          .append(String.format("%.6f", massFlowHistory.size() > i ? massFlowHistory.get(i) : 0.0));
      json.append("}");
      if (i < n - 1) {
        json.append(",");
      }
      json.append("\n");
    }
    json.append("  ]\n");
    json.append("}\n");

    return json.toString();
  }

  // ================================================================================
  // Orifice Sizing and Optimization Methods
  // ================================================================================

  /**
   * Calculates required orifice diameter to meet API 521 blowdown time requirement.
   *
   * <p>
   * API 521 requires depressurization to 50% of initial pressure (or 6.9 barg, whichever is lower)
   * within 15 minutes for fire case.
   * </p>
   *
   * @param targetTime Target blowdown time [s], typically 900s (15 min)
   * @param pressureReduction Fraction of pressure to reduce (0.5 = 50%)
   * @return Required orifice diameter [m]
   */
  public double calculateRequiredOrificeDiameter(double targetTime, double pressureReduction) {
    double originalOrifice = orificeDiameter;
    double initialPressure = thermoSystem.getPressure();
    double targetPressure = initialPressure * (1.0 - pressureReduction);

    // Binary search for orifice size
    double minOrifice = 0.001; // 1 mm
    double maxOrifice = 0.2; // 200 mm
    double tolerance = 0.0001; // 0.1 mm

    UUID uuid = UUID.randomUUID();
    SystemInterface originalSystem = thermoSystem.clone();

    while (maxOrifice - minOrifice > tolerance) {
      double testOrifice = (minOrifice + maxOrifice) / 2.0;
      setOrificeDiameter(testOrifice);

      // Reset to initial state
      thermoSystem = originalSystem.clone();
      clearHistory();

      // Simulate until target pressure or time exceeded
      double dt = 1.0;
      double time = 0;
      while (thermoSystem.getPressure() > targetPressure && time < targetTime * 2) {
        runTransient(dt, uuid);
        time += dt;
      }

      if (time <= targetTime) {
        // Orifice too big, try smaller
        maxOrifice = testOrifice;
      } else {
        // Orifice too small, try larger
        minOrifice = testOrifice;
      }
    }

    double requiredOrifice = (minOrifice + maxOrifice) / 2.0;

    // Restore original state
    setOrificeDiameter(originalOrifice);
    thermoSystem = originalSystem.clone();
    clearHistory();

    return requiredOrifice;
  }

  /**
   * Runs sensitivity analysis on orifice size.
   *
   * <p>
   * Calculates blowdown time for a range of orifice diameters. Useful for selecting optimal orifice
   * size considering multiple constraints.
   * </p>
   *
   * @param orificeSizes Array of orifice diameters to test [m]
   * @param targetPressure Target pressure [bar]
   * @return Array of blowdown times [s] for each orifice size
   */
  public double[] runOrificeSensitivity(double[] orificeSizes, double targetPressure) {
    double[] times = new double[orificeSizes.length];
    double originalOrifice = orificeDiameter;
    SystemInterface originalSystem = thermoSystem.clone();
    UUID uuid = UUID.randomUUID();

    for (int i = 0; i < orificeSizes.length; i++) {
      setOrificeDiameter(orificeSizes[i]);
      thermoSystem = originalSystem.clone();
      clearHistory();

      double dt = 1.0;
      double time = 0;
      double maxTime = 7200; // 2 hour max
      while (thermoSystem.getPressure() / 1e5 > targetPressure && time < maxTime) {
        runTransient(dt, uuid);
        time += dt;
      }
      times[i] = time;
    }

    // Restore original state
    setOrificeDiameter(originalOrifice);
    thermoSystem = originalSystem.clone();
    clearHistory();

    return times;
  }

  // ================================================================================
  // Two-Phase Flow Analysis
  // ================================================================================

  /**
   * Checks for liquid rainout conditions in the outlet stream.
   *
   * <p>
   * Liquid rainout can occur when two-phase flow exits the vessel during depressurization. This can
   * cause problems in flare headers (liquid accumulation) and at the flare tip (unstable burning).
   * </p>
   *
   * @return True if outlet stream contains liquid phase
   */
  public boolean hasLiquidRainout() {
    if (outletStream == null || outletStream.getFluid() == null) {
      return false;
    }
    return outletStream.getFluid().hasPhaseType("aqueous")
        || outletStream.getFluid().hasPhaseType("oil")
        || outletStream.getFluid().hasPhaseType("liquid");
  }

  /**
   * Gets the liquid mass fraction in the outlet stream.
   *
   * @return Liquid mass fraction (0-1), 0 if all vapor
   */
  public double getOutletLiquidFraction() {
    if (outletStream == null || outletStream.getFluid() == null) {
      return 0.0;
    }
    SystemInterface fluid = outletStream.getFluid();
    if (!fluid.hasPhaseType("gas")) {
      return 1.0;
    }

    double totalMass = 0;
    double liquidMass = 0;
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      double phaseMass = fluid.getPhase(i).getMass();
      totalMass += phaseMass;
      if (!fluid.getPhase(i).getPhaseTypeName().equals("gas")) {
        liquidMass += phaseMass;
      }
    }

    return totalMass > 0 ? liquidMass / totalMass : 0.0;
  }

  /**
   * Gets the peak liquid fraction observed during blowdown.
   *
   * <p>
   * This indicates the worst-case liquid loading on the flare header. Values above 0.1 (10%) may
   * require liquid knockout facilities.
   * </p>
   *
   * @return Peak liquid mass fraction in outlet
   */
  public double getPeakOutletLiquidFraction() {
    if (liquidLevelHistory.isEmpty()) {
      return 0.0;
    }
    return liquidLevelHistory.stream().max(Double::compare).orElse(0.0);
  }

  /**
   * Estimates flare header velocity.
   *
   * <p>
   * Typical design limits:
   * <ul>
   * <li>Flare headers: 0.3-0.6 Mach (approximately 100-200 m/s)</li>
   * <li>Relief valve outlets: 0.5 Mach max</li>
   * </ul>
   *
   * @param headerDiameter Flare header internal diameter [m]
   * @param unit Velocity unit ("m/s", "ft/s")
   * @return Gas velocity in header at current flow rate
   */
  public double getFlareHeaderVelocity(double headerDiameter, String unit) {
    if (outletStream == null || outletStream.getFluid() == null) {
      return 0.0;
    }

    SystemInterface fluid = outletStream.getFluid();
    if (!fluid.hasPhaseType("gas")) {
      return 0.0;
    }

    double gasDensity = fluid.getPhase("gas").getDensity("kg/m3");
    double massFlow = getDischargeRate("kg/s");
    double headerArea = Math.PI * headerDiameter * headerDiameter / 4.0;

    double volumetricFlow = massFlow / gasDensity;
    double velocity = volumetricFlow / headerArea;

    if ("ft/s".equalsIgnoreCase(unit)) {
      return velocity * 3.281;
    }
    return velocity;
  }

  /**
   * Estimates Mach number in flare header.
   *
   * @param headerDiameter Flare header internal diameter [m]
   * @return Mach number
   */
  public double getFlareHeaderMach(double headerDiameter) {
    if (outletStream == null || outletStream.getFluid() == null) {
      return 0.0;
    }

    SystemInterface fluid = outletStream.getFluid();
    if (!fluid.hasPhaseType("gas")) {
      return 0.0;
    }

    double velocity = getFlareHeaderVelocity(headerDiameter, "m/s");
    double sonicVelocity = fluid.getPhase("gas").getSoundSpeed();

    return velocity / sonicVelocity;
  }

  // ================================================================================
  // Hydrate and CO2 Freezing Risk Assessment
  // ================================================================================

  /**
   * Calculates hydrate formation temperature at current vessel conditions.
   *
   * <p>
   * Hydrate formation during depressurization can occur when temperature drops below the hydrate
   * equilibrium temperature. This can cause flow restrictions in blowdown lines and safety hazards.
   * </p>
   *
   * @return Hydrate formation temperature [K], or -1 if calculation fails or no hydrate formers
   */
  public double getHydrateFormationTemperature() {
    if (thermoSystem == null) {
      return -1.0;
    }

    // Check if water is present - required for hydrate formation
    boolean hasWater = false;
    for (int i = 0; i < thermoSystem.getNumberOfComponents(); i++) {
      String name = thermoSystem.getComponent(i).getComponentName().toLowerCase();
      if (name.equals("water") || name.equals("h2o")) {
        if (thermoSystem.getComponent(i).getz() > 0.0001) {
          hasWater = true;
          break;
        }
      }
    }

    if (!hasWater) {
      return -1.0; // No water = no hydrate risk
    }

    try {
      // Clone system to avoid modifying the vessel state
      SystemInterface testSystem = thermoSystem.clone();
      testSystem.setHydrateCheck(true);

      ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
      ops.hydrateFormationTemperature();

      return testSystem.getTemperature();
    } catch (Exception e) {
      logger.debug("Hydrate temperature calculation failed: " + e.getMessage());
      return -1.0;
    }
  }

  /**
   * Calculates hydrate formation temperature at current conditions with unit conversion.
   *
   * @param unit Temperature unit ("K", "C")
   * @return Hydrate formation temperature in specified unit
   */
  public double getHydrateFormationTemperature(String unit) {
    double tempK = getHydrateFormationTemperature();
    if (tempK < 0) {
      return tempK;
    }
    if ("C".equalsIgnoreCase(unit)) {
      return tempK - 273.15;
    }
    return tempK;
  }

  /**
   * Checks if current vessel temperature is below hydrate formation temperature.
   *
   * @return True if hydrate formation risk exists (T_vessel &lt; T_hydrate)
   */
  public boolean hasHydrateRisk() {
    double hydrateTemp = getHydrateFormationTemperature();
    if (hydrateTemp < 0) {
      return false; // Could not calculate, assume no risk
    }
    return thermoSystem.getTemperature() < hydrateTemp;
  }

  /**
   * Gets the hydrate subcooling (margin below hydrate temperature).
   *
   * <p>
   * Positive values indicate hydrate risk. Negative values indicate safety margin.
   * </p>
   *
   * @param unit Temperature unit ("K", "C")
   * @return Subcooling = T_hydrate - T_vessel
   */
  public double getHydrateSubcooling(String unit) {
    double hydrateTemp = getHydrateFormationTemperature();
    if (hydrateTemp < 0) {
      return 0.0;
    }
    double subcooling = hydrateTemp - thermoSystem.getTemperature();
    // Subcooling is already in K (same magnitude as C difference)
    return subcooling;
  }

  /**
   * Gets the minimum hydrate subcooling (maximum risk) observed during blowdown history.
   *
   * <p>
   * Tracks the point where temperature was furthest below hydrate curve during the entire
   * depressurization.
   * </p>
   *
   * @return Maximum subcooling observed [K or C]
   */
  public double getMaxHydrateSubcoolingDuringBlowdown() {
    double maxSubcooling = 0.0;

    // Check each history point
    for (int i = 0; i < temperatureHistory.size() && i < pressureHistory.size(); i++) {
      double T = temperatureHistory.get(i);
      double P = pressureHistory.get(i) / 1e5; // Pa to bar

      // Calculate hydrate temp at this P, T
      try {
        SystemInterface testSystem = thermoSystem.clone();
        testSystem.setTemperature(T);
        testSystem.setPressure(P);
        testSystem.setHydrateCheck(true);

        ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
        ops.hydrateFormationTemperature();

        double hydrateTemp = testSystem.getTemperature();
        double subcooling = hydrateTemp - T;
        if (subcooling > maxSubcooling) {
          maxSubcooling = subcooling;
        }
      } catch (Exception e) {
        // Skip this point
      }
    }
    return maxSubcooling;
  }

  /**
   * Gets the CO2 freezing (dry ice) temperature at current pressure.
   *
   * <p>
   * CO2 triple point is at 5.18 bar, -56.6°C (216.55 K). Below this pressure, CO2 sublimates
   * directly from solid to vapor. At higher pressures, the solid-liquid melting line applies.
   * </p>
   *
   * <p>
   * Key points on CO2 phase diagram:
   * <ul>
   * <li>Sublimation at 1 bar: -78.5°C (194.7 K)</li>
   * <li>Triple point: 5.18 bar, -56.6°C (216.55 K)</li>
   * <li>Melting at 100 bar: approx -55°C (218 K)</li>
   * <li>Melting at 500 bar: approx -45°C (228 K)</li>
   * </ul>
   * The CO2 melting curve has a very small slope (~0.02 K/bar).
   *
   * @return CO2 freezing temperature [K], or -1 if no CO2 in system
   */
  public double getCO2FreezingTemperature() {
    if (thermoSystem == null) {
      return -1.0;
    }

    // Check if CO2 is present
    double co2MoleFrac = 0.0;
    for (int i = 0; i < thermoSystem.getNumberOfComponents(); i++) {
      String name = thermoSystem.getComponent(i).getComponentName().toLowerCase();
      if (name.equals("co2") || name.equals("carbon dioxide")) {
        co2MoleFrac = thermoSystem.getComponent(i).getz();
        break;
      }
    }

    if (co2MoleFrac < 0.0001) {
      return -1.0; // No significant CO2
    }

    double P = thermoSystem.getPressure(); // bar

    // CO2 phase diagram - accurate approximations
    double triplePointP = 5.18; // bar
    double triplePointT = 216.55; // K (-56.6°C)

    if (P < triplePointP) {
      // Below triple point - sublimation curve (solid-vapor)
      // Using Antoine-like equation fitted to CO2 sublimation data
      // ln(P) = 15.96 - 1512/T approximately
      // Solving for T: T = 1512 / (15.96 - ln(P))
      if (P <= 0.001) {
        P = 0.001; // Prevent log of zero
      }
      double T_sublim = 1512.0 / (15.96 - Math.log(P));
      // Clamp to reasonable range
      T_sublim = Math.max(150.0, Math.min(T_sublim, triplePointT));
      return T_sublim;
    } else {
      // Above triple point - melting curve (solid-liquid)
      // CO2 melting curve: very small slope, approximately 0.02 K/bar
      // T_melt ≈ 216.55 + 0.02 * (P - 5.18)
      double T_melt = triplePointT + 0.02 * (P - triplePointP);
      return T_melt;
    }
  }

  /**
   * Gets the CO2 freezing temperature with unit conversion.
   *
   * @param unit Temperature unit ("K", "C")
   * @return CO2 freezing temperature
   */
  public double getCO2FreezingTemperature(String unit) {
    double tempK = getCO2FreezingTemperature();
    if (tempK < 0) {
      return tempK;
    }
    if ("C".equalsIgnoreCase(unit)) {
      return tempK - 273.15;
    }
    return tempK;
  }

  /**
   * Checks if current temperature is below CO2 freezing point.
   *
   * @return True if CO2 freezing (dry ice) risk exists
   */
  public boolean hasCO2FreezingRisk() {
    double freezeTemp = getCO2FreezingTemperature();
    if (freezeTemp < 0) {
      return false; // No CO2 or could not calculate
    }
    return thermoSystem.getTemperature() < freezeTemp;
  }

  /**
   * Gets the CO2 freezing subcooling (margin below freezing point).
   *
   * @return Subcooling [K] = T_freeze - T_vessel (positive = freezing risk)
   */
  public double getCO2FreezingSubcooling() {
    double freezeTemp = getCO2FreezingTemperature();
    if (freezeTemp < 0) {
      return 0.0;
    }
    return freezeTemp - thermoSystem.getTemperature();
  }

  /**
   * Comprehensive flow assurance risk assessment during blowdown.
   *
   * <p>
   * Returns a summary of all flow assurance risks including:
   * <ul>
   * <li>Hydrate formation risk</li>
   * <li>CO2 freezing (dry ice) risk</li>
   * <li>MDMT (minimum design metal temperature) concerns</li>
   * <li>Liquid rainout in flare header</li>
   * </ul>
   *
   * @return Map of risk assessments with descriptions
   */
  public java.util.Map<String, String> assessFlowAssuranceRisks() {
    java.util.Map<String, String> risks = new java.util.LinkedHashMap<>();

    // Hydrate risk
    double hydrateTemp = getHydrateFormationTemperature("C");
    double fluidTemp = thermoSystem.getTemperature() - 273.15;
    if (hydrateTemp > -200) {
      double subcooling = hydrateTemp - fluidTemp;
      if (subcooling > 0) {
        risks.put("HYDRATE",
            String.format("RISK: Fluid (%.1f°C) is %.1f°C below hydrate temp (%.1f°C)", fluidTemp,
                subcooling, hydrateTemp));
      } else {
        risks.put("HYDRATE", String.format("OK: %.1f°C margin above hydrate temp (%.1f°C)",
            -subcooling, hydrateTemp));
      }
    } else {
      risks.put("HYDRATE", "N/A: No hydrate formers or calculation failed");
    }

    // CO2 freezing risk
    double freezeTemp = getCO2FreezingTemperature("C");
    if (freezeTemp > -200) {
      double subcooling = freezeTemp - fluidTemp;
      if (subcooling > 0) {
        risks.put("CO2_FREEZING",
            String.format("RISK: Fluid (%.1f°C) is %.1f°C below CO2 freeze (%.1f°C)", fluidTemp,
                subcooling, freezeTemp));
      } else {
        risks.put("CO2_FREEZING",
            String.format("OK: %.1f°C margin above CO2 freeze (%.1f°C)", -subcooling, freezeTemp));
      }
    } else {
      risks.put("CO2_FREEZING", "N/A: No CO2 in system");
    }

    // MDMT assessment
    double minFluidTemp = getMinimumTemperatureReached("C");
    double minWallTemp = getMinimumWallTemperatureReached("C");
    if (minWallTemp < -29) {
      risks.put("MDMT", String.format(
          "CRITICAL: Wall temp (%.1f°C) requires special materials (CS limit -29°C)", minWallTemp));
    } else if (minWallTemp < 0) {
      risks.put("MDMT", String.format(
          "WARNING: Wall temp (%.1f°C) below 0°C - verify CS impact properties", minWallTemp));
    } else {
      risks.put("MDMT",
          String.format("OK: Wall temp (%.1f°C) within normal CS range", minWallTemp));
    }

    // Liquid rainout
    if (hasLiquidRainout()) {
      double liqFrac = getOutletLiquidFraction() * 100;
      risks.put("LIQUID_RAINOUT",
          String.format("WARNING: %.1f%% liquid in outlet - consider knockout drum", liqFrac));
    } else {
      risks.put("LIQUID_RAINOUT", "OK: No liquid in outlet stream");
    }

    return risks;
  }
}
