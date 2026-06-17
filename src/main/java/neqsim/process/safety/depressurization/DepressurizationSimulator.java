package neqsim.process.safety.depressurization;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Transient depressurization (blowdown) simulator for pressurized vessels.
 *
 * <p>
 * Solves the coupled mass and energy balance for a vessel discharging through an orifice or
 * blowdown valve. Tracks fluid temperature, pressure, inventory, and (optionally) a lumped wall
 * metal temperature. Reports compliance against API 521 §5.20 / BS EN ISO 23251 acceptance
 * criteria:
 * <ul>
 * <li>50% of initial absolute pressure within 15 minutes (depressurization rate criterion)</li>
 * <li>Reach 7 barg within 15 minutes (fire-case criterion)</li>
 * <li>Minimum metal temperature does not fall below MDMT (cold blowdown criterion)</li>
 * </ul>
 *
 * <p>
 * Mass flow uses isenthalpic compressible orifice relations (choked / subsonic). The internal
 * energy balance uses Q_fire (optional, from API 521) minus enthalpy of discharged fluid.
 *
 * <p>
 * <b>References:</b> API STD 521 (7th Ed), BS EN ISO 23251, NORSOK P-001.
 *
 * @author ESOL
 * @version 1.0
 */
public class DepressurizationSimulator implements Serializable {
  private static final long serialVersionUID = 1L;

  private final SystemInterface fluid;
  private final double vesselVolume; // m3
  private final double orificeDiameter; // m
  private final double dischargeCoefficient;
  private final double backPressure; // Pa absolute
  private double fireHeatInput; // W (constant; user can scale by API 521 §4.3 if needed)
  private double wallMass; // kg of metal (0 = ignore wall thermal mass)
  private double wallCp = 470.0; // J/(kg.K) carbon steel default
  private double wallHeatTransferCoeff = 50.0; // W/(m2.K) inside film
  private double wallArea = 0.0; // m2 (internal heat transfer area)
  private double timeStep = 1.0; // s
  private double maxTime = 900.0; // s (15 min default)
  private double minPressure = 1.5e5; // Pa absolute - stop when reached

  /**
   * Construct a depressurization simulator.
   *
   * @param fluid initial fluid in the vessel; must already have mixing rule set
   * @param vesselVolume internal vessel volume in m³
   * @param orificeDiameter blowdown orifice diameter in m
   * @param dischargeCoefficient orifice Cd (typical 0.61–0.85)
   * @param backPressure downstream absolute pressure in Pa (typically flare header)
   */
  public DepressurizationSimulator(SystemInterface fluid, double vesselVolume,
      double orificeDiameter, double dischargeCoefficient, double backPressure) {
    if (fluid == null) {
      throw new IllegalArgumentException("fluid must not be null");
    }
    if (vesselVolume <= 0.0 || orificeDiameter <= 0.0) {
      throw new IllegalArgumentException("vesselVolume and orificeDiameter must be positive");
    }
    this.fluid = fluid;
    this.vesselVolume = vesselVolume;
    this.orificeDiameter = orificeDiameter;
    this.dischargeCoefficient = dischargeCoefficient;
    this.backPressure = backPressure;
  }

  /**
   * Set fire heat input per API 521 §4.3 (constant heat duty applied to the fluid).
   *
   * @param qFire fire heat input in W (positive value)
   * @return this simulator for chaining
   */
  public DepressurizationSimulator setFireHeatInput(double qFire) {
    this.fireHeatInput = qFire;
    return this;
  }

  /**
   * Enable lumped-wall thermal modelling for metal temperature tracking (cold blowdown / MDMT).
   *
   * @param wallMass total wall metal mass in kg
   * @param wallArea internal heat transfer area in m²
   * @param wallSpecificHeat metal specific heat in J/(kg·K) (470 for carbon steel)
   * @param insideHTC inside film heat transfer coefficient in W/(m²·K)
   * @return this simulator for chaining
   */
  public DepressurizationSimulator setWall(double wallMass, double wallArea,
      double wallSpecificHeat, double insideHTC) {
    this.wallMass = wallMass;
    this.wallArea = wallArea;
    this.wallCp = wallSpecificHeat;
    this.wallHeatTransferCoeff = insideHTC;
    return this;
  }

  /**
   * Set the integration timestep (default 1 s).
   *
   * @param dt timestep in s
   * @return this simulator for chaining
   */
  public DepressurizationSimulator setTimeStep(double dt) {
    this.timeStep = dt;
    return this;
  }

  /**
   * Set the maximum simulation time (default 900 s = 15 min).
   *
   * @param tMax maximum time in s
   * @return this simulator for chaining
   */
  public DepressurizationSimulator setMaxTime(double tMax) {
    this.maxTime = tMax;
    return this;
  }

  /**
   * Set the lower-bound stop pressure in Pa (default 1.5 bara = ~atmospheric).
   *
   * @param pMin stop pressure in Pa absolute
   * @return this simulator for chaining
   */
  public DepressurizationSimulator setStopPressure(double pMin) {
    this.minPressure = pMin;
    return this;
  }

  /**
   * Run the transient simulation and return the time-series result.
   *
   * @return result containing time, pressure, temperature, mass and metal-temperature trajectories
   */
  public DepressurizationResult run() {
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initProperties();

    double t = 0.0;
    double p0Pa = fluid.getPressure() * 1.0e5;
    double pPa = p0Pa;
    double tempK = fluid.getTemperature();
    double density = fluid.getDensity("kg/m3");
    double mass = density * vesselVolume;
    double wallTemp = tempK; // start at fluid temperature

    DepressurizationResult res = new DepressurizationResult();
    res.initialPressureBara = p0Pa / 1.0e5;
    res.append(t, pPa / 1.0e5, tempK, mass, wallTemp, 0.0);

    final double area = Math.PI * 0.25 * orificeDiameter * orificeDiameter;

    while (t < maxTime && pPa > minPressure && mass > 1.0e-6) {
      // Determine compressibility, gamma, MW from current state
      double mw = fluid.getMolarMass(); // kg/mol
      double cp = fluid.getCp("J/molK");
      double cv = fluid.getCv("J/molK");
      double gamma = (cv > 0.0) ? cp / cv : 1.3;
      double R = 8.314;
      double z = pPa * mw / (density * R * tempK);
      if (z <= 0.0 || Double.isNaN(z)) {
        z = 1.0;
      }

      // Critical pressure ratio
      double critRatio = Math.pow(2.0 / (gamma + 1.0), gamma / (gamma - 1.0));
      double pRatio = backPressure / pPa;
      double mDot;
      if (pRatio <= critRatio) {
        // Choked
        mDot = dischargeCoefficient * area * pPa
            * Math.sqrt(gamma * mw / (z * R * tempK))
            * Math.pow(2.0 / (gamma + 1.0), (gamma + 1.0) / (2.0 * (gamma - 1.0)));
      } else {
        // Subsonic
        double term = (2.0 * gamma / (gamma - 1.0))
            * (Math.pow(pRatio, 2.0 / gamma) - Math.pow(pRatio, (gamma + 1.0) / gamma));
        if (term < 0.0) {
          term = 0.0;
        }
        mDot = dischargeCoefficient * area * pPa
            * Math.sqrt(mw / (z * R * tempK)) * Math.sqrt(term);
      }
      if (mDot < 0.0 || Double.isNaN(mDot)) {
        mDot = 0.0;
      }

      // Mass balance
      double dm = mDot * timeStep;
      if (dm > mass) {
        dm = mass;
      }
      double newMass = mass - dm;

      // Specific enthalpy of discharged fluid (J/kg)
      double hSpec = fluid.getEnthalpy() / mass;

      // Energy balance: dU = -h*dm + Q_fire*dt + Q_wall*dt
      double qWall = wallMass > 0.0
          ? wallHeatTransferCoeff * wallArea * (wallTemp - tempK) : 0.0;
      double dU = (-hSpec * dm) + (fireHeatInput * timeStep) + (qWall * timeStep);

      // Update wall temperature (if modelled)
      if (wallMass > 0.0) {
        // Simple lumped-wall: heat lost from wall to fluid
        double dWallTemp = -qWall * timeStep / (wallMass * wallCp);
        wallTemp += dWallTemp;
      }

      // New internal energy
      double newU = (fluid.getInternalEnergy()) + dU;

      // Update fluid state by VU flash (constant volume, new internal energy)
      fluid.setTotalNumberOfMoles(newMass / mw);
      try {
        ops.VUflash(vesselVolume, newU, "m3", "J");
        fluid.initProperties();
      } catch (Exception ex) {
        // Fallback: do an isothermal expansion approximation
        pPa = newMass * R * tempK / (mw * vesselVolume) * z;
        if (pPa < backPressure) {
          pPa = backPressure;
        }
        fluid.setPressure(pPa / 1.0e5);
        ops.TPflash();
        fluid.initProperties();
      }
      tempK = fluid.getTemperature();
      pPa = fluid.getPressure() * 1.0e5;
      density = fluid.getDensity("kg/m3");
      mass = newMass;

      t += timeStep;
      res.append(t, pPa / 1.0e5, tempK, mass, wallTemp, mDot);
    }

    // Acceptance evaluation per API 521 §5.20
    res.evaluate(p0Pa);
    return res;
  }

  // ----------------------------------------------------------------------
  // Result holder
  // ----------------------------------------------------------------------

  /**
   * Time-series result of a depressurization run with API 521 acceptance flags.
   */
  public static class DepressurizationResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Initial absolute pressure in bara. */
    public double initialPressureBara;
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
    /** Instantaneous discharge mass flow in kg/s. */
    public final List<Double> massFlowKgPerS = new ArrayList<>();

    /** Time to reach 50% of initial absolute pressure (s). NaN if not reached. */
    public double timeToHalfPressure = Double.NaN;
    /** Time to reach 7 barg (8 bara). NaN if not reached. */
    public double timeTo7BargS = Double.NaN;
    /** Minimum fluid temperature seen during blowdown (K). */
    public double minFluidTemperatureK = Double.POSITIVE_INFINITY;
    /** Minimum wall metal temperature seen during blowdown (K). */
    public double minWallTemperatureK = Double.POSITIVE_INFINITY;

    /** True if fluid pressure halved within 15 minutes (API 521 §5.20.2). */
    public boolean halfPressureCriterionMet;
    /** True if reached 7 barg within 15 minutes (fire case). */
    public boolean sevenBargCriterionMet;

    void append(double t, double pBara, double tempK, double mass, double wallTempK,
        double mDot) {
      time.add(t);
      pressureBara.add(pBara);
      temperatureK.add(tempK);
      massKg.add(mass);
      this.wallTempK.add(wallTempK);
      massFlowKgPerS.add(mDot);
      if (tempK < minFluidTemperatureK) {
        minFluidTemperatureK = tempK;
      }
      if (wallTempK < minWallTemperatureK) {
        minWallTemperatureK = wallTempK;
      }
    }

    void evaluate(double p0Pa) {
      double halfP = 0.5 * p0Pa;
      double sevenBargPa = 8.0e5;
      for (int i = 0; i < time.size(); i++) {
        double pPa = pressureBara.get(i) * 1.0e5;
        if (Double.isNaN(timeToHalfPressure) && pPa <= halfP) {
          timeToHalfPressure = time.get(i);
        }
        if (Double.isNaN(timeTo7BargS) && pPa <= sevenBargPa) {
          timeTo7BargS = time.get(i);
        }
      }
      halfPressureCriterionMet =
          !Double.isNaN(timeToHalfPressure) && timeToHalfPressure <= 900.0;
      sevenBargCriterionMet = !Double.isNaN(timeTo7BargS) && timeTo7BargS <= 900.0;
    }

    /**
     * Check minimum metal temperature against an MDMT threshold.
     *
     * @param mdmtK the design MDMT in K
     * @return true if minimum wall temperature is greater than or equal to MDMT
     */
    public boolean meetsMDMT(double mdmtK) {
      return minWallTemperatureK >= mdmtK;
    }

    /**
     * Evaluate this depressurization result against STS0131 fire escalation acceptance criteria.
     *
     * @param criteria configured STS0131 acceptance criteria
     * @return acceptance result with pass or fail flags and interpolated values
     * @throws IllegalArgumentException if {@code criteria} is null
     */
    public STS0131AcceptanceResult evaluateSTS0131(STS0131AcceptanceCriteria criteria) {
      if (criteria == null) {
        throw new IllegalArgumentException("criteria must not be null");
      }
      return criteria.evaluate(this);
    }

    /**
     * Build a brief human-readable summary of the API 521 acceptance results.
     *
     * @return summary string
     */
    public String summary() {
      StringBuilder sb = new StringBuilder();
      sb.append("Depressurization summary (API 521 §5.20):\n");
      sb.append(String.format("  Initial pressure        : %.2f bara%n", initialPressureBara));
      sb.append(String.format("  Time to half pressure   : %.1f s%n", timeToHalfPressure));
      sb.append(String.format("  Time to 7 barg          : %.1f s%n", timeTo7BargS));
      sb.append(String.format("  Min fluid temperature   : %.2f K%n", minFluidTemperatureK));
      sb.append(String.format("  Min wall  temperature   : %.2f K%n", minWallTemperatureK));
      sb.append("  Half-pressure criterion : ").append(halfPressureCriterionMet).append('\n');
      sb.append("  7 barg criterion        : ").append(sevenBargCriterionMet).append('\n');
      return sb.toString();
    }
  }
}
