package neqsim.process.safety.depressurization;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import neqsim.process.util.heattransfer.VesselHeatTransferCorrelations;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Transient filling (charging) simulator for pressurized vessels.
 *
 * <p>
 * Solves the coupled mass and energy balance for a vessel being charged with gas through an inlet nozzle. The
 * control-volume energy balance for a vessel with inflow only is
 *
 * <p>
 * {@code dU = h_in * dm_in + Q_wall * dt}
 *
 * <p>
 * where {@code h_in} is the specific stagnation enthalpy of the supply gas. Because enthalpy (not internal energy)
 * crosses the boundary, the flow work {@code p*v} of the incoming stream is captured automatically, which reproduces
 * the characteristic gas-temperature rise during fast filling (the classic charging problem).
 *
 * <p>
 * An optional lumped wall model tracks the metal temperature, and the internal gas-to-wall film coefficient may either
 * be a fixed value or computed each step from the Woodfield mixed-convection correlation for charging (see
 * {@link VesselHeatTransferCorrelations}). Liner temperature limits (relevant for Type III / Type IV composite hydrogen
 * cylinders) are checked against configurable minimum and maximum temperatures.
 *
 * <p>
 * <b>References:</b> Woodfield, Monde and Mitsutake (2007), J. Therm. Sci. Tech. 2(2), 180; SAE J2601; ISO 19880-1.
 *
 * @author ESOL
 * @version 1.0
 */
public class VesselFillingSimulator implements Serializable {
  private static final long serialVersionUID = 1L;

  private final SystemInterface fluid;
  private final double vesselVolume; // m3
  private double inletTemperatureK = 288.15; // supply gas stagnation temperature
  private double supplyPressureBara = Double.NaN; // supply pressure for inlet enthalpy
  private double inletMassFlow = 0.0; // kg/s charged into the vessel
  private double targetPressurePa = Double.POSITIVE_INFINITY; // stop pressure
  private double inletNozzleDiameter = 0.0; // m (for Woodfield HTC)
  private double vesselHeight = 0.0; // m (for Woodfield Rayleigh number)
  private boolean useWoodfieldHtc = false;

  private double wallMass = 0.0; // kg of metal (0 = ignore wall thermal mass)
  private double wallCp = 470.0; // J/(kg.K) carbon steel default
  private double wallHeatTransferCoeff = 50.0; // W/(m2.K) inside film (fixed fallback)
  private double wallArea = 0.0; // m2 internal heat transfer area

  private double linerMinTemperatureK = Double.NEGATIVE_INFINITY;
  private double linerMaxTemperatureK = Double.POSITIVE_INFINITY;

  private double timeStep = 1.0; // s
  private double maxTime = 600.0; // s

  /**
   * Construct a vessel filling simulator.
   *
   * @param fluid initial residual gas in the vessel (same composition as the fill gas); must already have its mixing
   * rule set
   * @param vesselVolume internal vessel volume in m³; must be positive
   * @throws IllegalArgumentException if {@code fluid} is null or {@code vesselVolume} is not positive
   */
  public VesselFillingSimulator(SystemInterface fluid, double vesselVolume) {
    if (fluid == null) {
      throw new IllegalArgumentException("fluid must not be null");
    }
    if (vesselVolume <= 0.0) {
      throw new IllegalArgumentException("vesselVolume must be positive");
    }
    this.fluid = fluid;
    this.vesselVolume = vesselVolume;
  }

  /**
   * Set the supply gas inlet conditions and charging mass flow.
   *
   * @param inletTemperatureK supply gas stagnation temperature in K; must be positive
   * @param supplyPressureBara supply pressure in bara used to evaluate the inlet enthalpy; must be positive
   * @param massFlowKgPerS charging mass flow into the vessel in kg/s; must be positive
   * @return this simulator for chaining
   * @throws IllegalArgumentException if any argument is not positive
   */
  public VesselFillingSimulator setInletConditions(double inletTemperatureK, double supplyPressureBara,
      double massFlowKgPerS) {
    if (inletTemperatureK <= 0.0 || supplyPressureBara <= 0.0 || massFlowKgPerS <= 0.0) {
      throw new IllegalArgumentException("inletTemperatureK, supplyPressureBara and massFlowKgPerS must be positive");
    }
    this.inletTemperatureK = inletTemperatureK;
    this.supplyPressureBara = supplyPressureBara;
    this.inletMassFlow = massFlowKgPerS;
    return this;
  }

  /**
   * Set the target final absolute pressure at which to stop charging.
   *
   * @param targetBara target absolute pressure in bara; must be positive
   * @return this simulator for chaining
   * @throws IllegalArgumentException if {@code targetBara} is not positive
   */
  public VesselFillingSimulator setTargetPressure(double targetBara) {
    if (targetBara <= 0.0) {
      throw new IllegalArgumentException("targetBara must be positive");
    }
    this.targetPressurePa = targetBara * 1.0e5;
    return this;
  }

  /**
   * Enable lumped-wall thermal modelling for metal temperature tracking.
   *
   * @param wallMass total wall metal mass in kg
   * @param wallArea internal heat transfer area in m²
   * @param wallSpecificHeat metal specific heat in J/(kg·K)
   * @param insideHTC fixed inside film heat transfer coefficient in W/(m²·K) used unless the Woodfield correlation is
   * enabled
   * @return this simulator for chaining
   */
  public VesselFillingSimulator setWall(double wallMass, double wallArea, double wallSpecificHeat, double insideHTC) {
    this.wallMass = wallMass;
    this.wallArea = wallArea;
    this.wallCp = wallSpecificHeat;
    this.wallHeatTransferCoeff = insideHTC;
    return this;
  }

  /**
   * Enable the Woodfield mixed-convection correlation for the internal film coefficient during filling. The coefficient
   * is recomputed each timestep from the inlet-jet Reynolds number and the vessel-height Rayleigh number.
   *
   * @param inletNozzleDiameterM inlet nozzle diameter in m; must be positive
   * @param vesselHeightM internal vessel height in m; must be positive
   * @return this simulator for chaining
   * @throws IllegalArgumentException if either dimension is not positive
   */
  public VesselFillingSimulator setWoodfieldHeatTransfer(double inletNozzleDiameterM, double vesselHeightM) {
    if (inletNozzleDiameterM <= 0.0 || vesselHeightM <= 0.0) {
      throw new IllegalArgumentException("inletNozzleDiameterM and vesselHeightM must be positive");
    }
    this.inletNozzleDiameter = inletNozzleDiameterM;
    this.vesselHeight = vesselHeightM;
    this.useWoodfieldHtc = true;
    return this;
  }

  /**
   * Set the liner (or metal) temperature limits used for acceptance checking during filling. For a Type IV composite
   * hydrogen cylinder the liner limits are typically −40 °C to +85 °C.
   *
   * @param minTemperatureK minimum allowable gas temperature in K
   * @param maxTemperatureK maximum allowable gas temperature in K
   * @return this simulator for chaining
   * @throws IllegalArgumentException if {@code maxTemperatureK} is not greater than {@code minTemperatureK}
   */
  public VesselFillingSimulator setLinerTemperatureLimits(double minTemperatureK, double maxTemperatureK) {
    if (maxTemperatureK <= minTemperatureK) {
      throw new IllegalArgumentException("maxTemperatureK must be greater than minTemperatureK");
    }
    this.linerMinTemperatureK = minTemperatureK;
    this.linerMaxTemperatureK = maxTemperatureK;
    return this;
  }

  /**
   * Set the integration timestep (default 1 s).
   *
   * @param dt timestep in s; must be positive
   * @return this simulator for chaining
   * @throws IllegalArgumentException if {@code dt} is not positive
   */
  public VesselFillingSimulator setTimeStep(double dt) {
    if (dt <= 0.0) {
      throw new IllegalArgumentException("dt must be positive");
    }
    this.timeStep = dt;
    return this;
  }

  /**
   * Set the maximum simulation time (default 600 s).
   *
   * @param tMax maximum time in s; must be positive
   * @return this simulator for chaining
   * @throws IllegalArgumentException if {@code tMax} is not positive
   */
  public VesselFillingSimulator setMaxTime(double tMax) {
    if (tMax <= 0.0) {
      throw new IllegalArgumentException("tMax must be positive");
    }
    this.maxTime = tMax;
    return this;
  }

  /**
   * Run the transient filling simulation and return the time-series result.
   *
   * @return result containing time, pressure, temperature, mass and metal-temperature trajectories
   * @throws IllegalStateException if the inlet conditions have not been configured
   */
  public VesselFillingResult run() {
    if (inletMassFlow <= 0.0 || Double.isNaN(supplyPressureBara)) {
      throw new IllegalStateException("inlet conditions must be configured via setInletConditions(...)");
    }
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initProperties();

    // Specific stagnation enthalpy of the supply gas (J/kg), evaluated once at the supply
    // conditions on an independent clone so the vessel inventory is not disturbed.
    double hInletSpecific = computeInletSpecificEnthalpy();

    double t = 0.0;
    double tempK = fluid.getTemperature();
    double pPa = fluid.getPressure() * 1.0e5;
    double density = fluid.getDensity("kg/m3");
    double mass = density * vesselVolume;

    // Align the fluid mole basis with the physical vessel inventory (see DepressurizationSimulator
    // for the rationale; setTotalNumberOfMoles must not be used).
    double currentMoles = fluid.getNumberOfMoles();
    double molarMass = fluid.getMolarMass();
    if (molarMass > 0.0 && currentMoles > 0.0) {
      double targetMoles = mass / molarMass;
      scaleMoles(targetMoles / currentMoles);
      ops.TPflash();
      fluid.initProperties();
      tempK = fluid.getTemperature();
      pPa = fluid.getPressure() * 1.0e5;
      density = fluid.getDensity("kg/m3");
      mass = density * vesselVolume;
    }

    double wallTemp = tempK;

    VesselFillingResult res = new VesselFillingResult();
    res.inletTemperatureK = inletTemperatureK;
    res.inletMassFlowKgPerS = inletMassFlow;
    res.append(t, pPa / 1.0e5, tempK, mass, wallTemp);

    while (t < maxTime && pPa < targetPressurePa && mass > 0.0) {
      double dmIn = inletMassFlow * timeStep;
      double newMass = mass + dmIn;

      double insideHtc = currentInsideHtc(density, mass, tempK);
      double qWall = wallMass > 0.0 ? insideHtc * wallArea * (wallTemp - tempK) : 0.0;
      double dU = (hInletSpecific * dmIn) + (qWall * timeStep);

      if (wallMass > 0.0) {
        // Wall gains the heat the fluid loses to it.
        double dWallTemp = (-qWall) * timeStep / (wallMass * wallCp);
        wallTemp += dWallTemp;
      }

      double newU = fluid.getInternalEnergy() + dU;
      if (mass > 0.0) {
        scaleMoles(newMass / mass);
      }
      try {
        ops.VUflash(vesselVolume, newU, "m3", "J");
        fluid.initProperties();
      } catch (Exception ex) {
        res.vuFlashFallbackCount++;
        // Conservative fallback: hold temperature, set pressure from ideal gas.
        double mw = fluid.getMolarMass();
        double pFallback = newMass * 8.314 * tempK / (mw * vesselVolume);
        fluid.setPressure(pFallback / 1.0e5);
        ops.TPflash();
        fluid.initProperties();
      }
      tempK = fluid.getTemperature();
      pPa = fluid.getPressure() * 1.0e5;
      density = fluid.getDensity("kg/m3");
      mass = newMass;

      t += timeStep;
      res.append(t, pPa / 1.0e5, tempK, mass, wallTemp);
    }

    res.evaluateLinerLimits(linerMinTemperatureK, linerMaxTemperatureK);
    return res;
  }

  /**
   * Computes the specific stagnation enthalpy of the supply gas at the configured inlet conditions.
   *
   * @return inlet specific enthalpy in J/kg
   */
  private double computeInletSpecificEnthalpy() {
    SystemInterface inlet = fluid.clone();
    inlet.setTemperature(inletTemperatureK);
    inlet.setPressure(supplyPressureBara);
    ThermodynamicOperations inletOps = new ThermodynamicOperations(inlet);
    inletOps.TPflash();
    inlet.initProperties();
    double moles = inlet.getNumberOfMoles();
    double mw = inlet.getMolarMass();
    double massInlet = moles * mw;
    return massInlet > 0.0 ? inlet.getEnthalpy() / massInlet : 0.0;
  }

  /**
   * Determines the internal gas-to-wall film coefficient for the current step, using either the Woodfield correlation
   * or the fixed configured value.
   *
   * @param density current gas density in kg/m³
   * @param mass current gas mass in kg
   * @param tempK current gas temperature in K
   * @return inside film heat-transfer coefficient in W/(m²·K)
   */
  private double currentInsideHtc(double density, double mass, double tempK) {
    if (!useWoodfieldHtc) {
      return wallHeatTransferCoeff;
    }
    try {
      double mu = fluid.getViscosity("kg/msec");
      double k = fluid.getThermalConductivity("W/mK");
      double cpMolar = fluid.getCp("J/molK");
      double mw = fluid.getMolarMass();
      if (mu <= 0.0 || k <= 0.0 || cpMolar <= 0.0 || mw <= 0.0 || density <= 0.0) {
        return wallHeatTransferCoeff;
      }
      double cpMass = cpMolar / mw; // J/(kg.K)
      double prandtl = cpMass * mu / k;
      double alpha = k / (density * cpMass); // thermal diffusivity
      double nu = mu / density; // kinematic viscosity
      double beta = 1.0 / tempK; // ideal-gas expansion coefficient
      double nozzleArea = Math.PI * 0.25 * inletNozzleDiameter * inletNozzleDiameter;
      double inletVelocity = inletMassFlow / (density * nozzleArea);
      double reD = VesselHeatTransferCorrelations.reynolds(density, inletVelocity, inletNozzleDiameter, mu);
      double raH = VesselHeatTransferCorrelations.rayleigh(beta, Math.abs(inletTemperatureK - tempK), vesselHeight, nu,
          alpha);
      double nuFilling = VesselHeatTransferCorrelations.woodfieldFillingNusselt(reD, raH);
      double htc = VesselHeatTransferCorrelations.heatTransferCoefficient(nuFilling, k, vesselHeight);
      return htc > 0.0 ? htc : wallHeatTransferCoeff;
    } catch (Exception ex) {
      return wallHeatTransferCoeff;
    }
  }

  /**
   * Scale the total mole inventory of the fluid by the given factor while preserving composition. See
   * {@code DepressurizationSimulator#scaleMoles(double)} for the rationale.
   *
   * @param factor multiplicative scaling factor; values ≤ 0, NaN or equal to 1 are ignored
   */
  private void scaleMoles(double factor) {
    if (factor <= 0.0 || Double.isNaN(factor) || Math.abs(factor - 1.0) < 1.0e-12) {
      return;
    }
    int nc = fluid.getNumberOfComponents();
    for (int i = 0; i < nc; i++) {
      double cur = fluid.getComponent(i).getNumberOfmoles();
      fluid.addComponent(i, cur * (factor - 1.0));
    }
    fluid.init(0);
  }

  // ----------------------------------------------------------------------
  // Result holder
  // ----------------------------------------------------------------------

  /**
   * Time-series result of a vessel filling run with liner temperature-limit flags.
   */
  public static class VesselFillingResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Supply gas stagnation temperature in K. */
    public double inletTemperatureK;
    /** Charging mass flow in kg/s. */
    public double inletMassFlowKgPerS;
    /** Number of VU-flash failures that used the conservative fallback state update. */
    public int vuFlashFallbackCount;

    /** Time stamps in seconds. */
    public final List<Double> time = new ArrayList<>();
    /** Pressure trajectory in bara. */
    public final List<Double> pressureBara = new ArrayList<>();
    /** Fluid temperature trajectory in K. */
    public final List<Double> temperatureK = new ArrayList<>();
    /** Mass inventory trajectory in kg. */
    public final List<Double> massKg = new ArrayList<>();
    /** Wall metal temperature trajectory in K (= fluid T if no wall modelled). */
    public final List<Double> wallTempK = new ArrayList<>();

    /** Maximum gas temperature seen during filling (K). */
    public double maxFluidTemperatureK = Double.NEGATIVE_INFINITY;
    /** Minimum gas temperature seen during filling (K). */
    public double minFluidTemperatureK = Double.POSITIVE_INFINITY;
    /** Maximum absolute pressure reached (bara). */
    public double maxPressureBara = Double.NEGATIVE_INFINITY;
    /** Final absolute pressure (bara). */
    public double finalPressureBara;
    /** Final inventory mass (kg). */
    public double finalMassKg;

    /** True if the gas temperature exceeded the upper liner limit at any point. */
    public boolean linerOverTemperature;
    /** True if the gas temperature fell below the lower liner limit at any point. */
    public boolean linerUnderTemperature;
    /** True if the gas temperature stayed within the configured liner limits. */
    public boolean linerLimitsMet = true;

    void append(double t, double pBara, double tempK, double mass, double wallTempK) {
      time.add(t);
      pressureBara.add(pBara);
      temperatureK.add(tempK);
      massKg.add(mass);
      this.wallTempK.add(wallTempK);
      if (tempK > maxFluidTemperatureK) {
        maxFluidTemperatureK = tempK;
      }
      if (tempK < minFluidTemperatureK) {
        minFluidTemperatureK = tempK;
      }
      if (pBara > maxPressureBara) {
        maxPressureBara = pBara;
      }
      finalPressureBara = pBara;
      finalMassKg = mass;
    }

    /**
     * Evaluate the recorded gas-temperature trajectory against the liner temperature limits.
     *
     * @param minK minimum allowable temperature in K
     * @param maxK maximum allowable temperature in K
     */
    void evaluateLinerLimits(double minK, double maxK) {
      for (int i = 0; i < temperatureK.size(); i++) {
        double tK = temperatureK.get(i);
        if (tK > maxK) {
          linerOverTemperature = true;
        }
        if (tK < minK) {
          linerUnderTemperature = true;
        }
      }
      linerLimitsMet = !linerOverTemperature && !linerUnderTemperature;
    }

    /**
     * Human-readable one-line summary of the filling result.
     *
     * @return summary string
     */
    public String summary() {
      return String.format("Filling: P %.1f->%.1f bara, mass %.2f kg, Tmax %.1f K, Tmin %.1f K, liner OK=%b",
          pressureBara.isEmpty() ? Double.NaN : pressureBara.get(0), finalPressureBara, finalMassKg,
          maxFluidTemperatureK, minFluidTemperatureK, linerLimitsMet);
    }
  }
}
