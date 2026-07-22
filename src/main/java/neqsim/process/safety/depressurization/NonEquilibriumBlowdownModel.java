package neqsim.process.safety.depressurization;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.util.fire.FireHeatLoadCalculator;
import neqsim.process.util.heattransfer.VesselHeatTransferCorrelations;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Non-equilibrium (two-temperature) blowdown model for a fire-exposed pressurized vessel.
 *
 * <p>
 * Conventional blowdown models assume a single fluid temperature. Experiments and rigorous simulations (Haque,
 * Richardson and Saville's BLOWDOWN; the partial-pressure / non-equilibrium method, NEM) show that during
 * depressurizing fire exposure the gas (vapour) space and the liquid space evolve at markedly different temperatures:
 * the vapour can superheat by several hundred kelvin while the boiling liquid stays pinned near its saturation
 * temperature. This gas–liquid temperature bifurcation is missed by single-temperature models and is the governing
 * input for low-metal- temperature and wall-rupture assessments.
 * </p>
 *
 * <p>
 * The model carries two temperature zones (vapour and liquid) at a shared, vapour-controlled pressure. Each zone is a
 * child {@link SystemInterface} (extracted with {@code phaseToSystem}) flashed independently each step for its
 * properties. Heat enters through a lumped wall (split between the unwetted vapour-side and the wetted liquid-side
 * area), the wetted wall drives nucleate boiling (Rohsenow,
 * {@link VesselHeatTransferCorrelations#rohsenowNucleateBoilingHeatFlux(double, double, double, double, double, double, double, double, double, double)}),
 * and a series-resistance interfacial term couples the two zones. Boil-off transfers moles from the liquid zone to the
 * vapour zone, and the vapour discharges through a choked / subsonic orifice.
 * </p>
 *
 * <p>
 * This is a screening-grade NEM: it captures the qualitative gas–liquid bifurcation and depressurization trend rather
 * than a rigorous coupled VLE march, and all flashes are guarded so a transient property failure degrades a single step
 * instead of aborting the run.
 * </p>
 *
 * <p>
 * <b>References:</b> Haque, Richardson, Saville et al. (1992), Trans. IChemE 70(B); Mahgerefteh et al. blowdown
 * studies; API STD 521 §4.3.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class NonEquilibriumBlowdownModel implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final Logger logger = LogManager.getLogger(NonEquilibriumBlowdownModel.class);
  private static final double GAS_CONSTANT = 8.314;

  /**
   * Lower physical floor for any zone temperature in K. The explicit integration is stiff for the wetted-wall boiling
   * coupling; this floor keeps a divergent step from producing an unphysical (negative absolute) temperature in a
   * screening run.
   */
  private static final double MIN_ZONE_TEMPERATURE_K = 100.0;

  private final SystemInterface fluid;
  private final double vesselVolume;
  private final double orificeDiameter;
  private final double dischargeCoefficient;
  private final double backPressure;

  private double fireEmissivity = 0.9;
  private double fireFlameTempK = 1100.0;
  private double fireConvCoeff = 30.0;
  private double fireExposedArea = 0.0;

  private double wallMass = 0.0;
  private double wallCp = 470.0;
  private double insideGasCoefficient = 30.0;
  private double interfacialCoefficient = 200.0;
  private double interfaceArea = 0.0;

  private double latentHeat = 350000.0;
  private double surfaceTension = 0.015;
  private double surfaceFluidConstant = 0.011;
  private double criticalHeatFlux = 1.0e6;
  private double effectiveBoilingCoefficient = 5000.0;

  private double timeStep = 1.0;
  private double maxTime = 900.0;
  private double minPressure = 1.5e5;

  /**
   * Constructs a non-equilibrium blowdown model.
   *
   * @param fluid initial two-phase fluid in the vessel; mixing rule must already be set
   * @param vesselVolume internal vessel volume in m³; must be positive
   * @param orificeDiameter blowdown orifice diameter in m; must be positive
   * @param dischargeCoefficient orifice discharge coefficient (typically 0.61 to 0.85)
   * @param backPressure downstream absolute pressure in Pa
   * @throws IllegalArgumentException if {@code fluid} is null or a dimension is not positive
   */
  public NonEquilibriumBlowdownModel(SystemInterface fluid, double vesselVolume, double orificeDiameter,
      double dischargeCoefficient, double backPressure) {
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
   * Configures the radiative-convective fire boundary condition acting on the wall.
   *
   * @param emissivity effective flame emissivity from 0 to 1
   * @param flameTempK effective radiating flame temperature in K; must be positive
   * @param convCoeff flame-to-wall convective film coefficient in W/(m²·K)
   * @param exposedAreaM2 fire-exposed external area in m²; must be positive
   * @return this model for chaining
   * @throws IllegalArgumentException if {@code flameTempK} or {@code exposedAreaM2} is not positive
   */
  public NonEquilibriumBlowdownModel setFireExposure(double emissivity, double flameTempK, double convCoeff,
      double exposedAreaM2) {
    if (flameTempK <= 0.0 || exposedAreaM2 <= 0.0) {
      throw new IllegalArgumentException("flameTempK and exposedAreaM2 must be positive");
    }
    this.fireEmissivity = emissivity;
    this.fireFlameTempK = flameTempK;
    this.fireConvCoeff = convCoeff;
    this.fireExposedArea = exposedAreaM2;
    return this;
  }

  /**
   * Configures the lumped wall thermal mass.
   *
   * @param wallMass total wall metal mass in kg; must be positive
   * @param wallSpecificHeat metal specific heat in J/(kg·K)
   * @return this model for chaining
   * @throws IllegalArgumentException if {@code wallMass} is not positive
   */
  public NonEquilibriumBlowdownModel setWall(double wallMass, double wallSpecificHeat) {
    if (wallMass <= 0.0) {
      throw new IllegalArgumentException("wallMass must be positive");
    }
    this.wallMass = wallMass;
    this.wallCp = wallSpecificHeat;
    return this;
  }

  /**
   * Sets the inside vapour-side wall film coefficient.
   *
   * @param insideGasCoefficient vapour-side film coefficient in W/(m²·K); must be positive
   * @return this model for chaining
   * @throws IllegalArgumentException if {@code insideGasCoefficient} is not positive
   */
  public NonEquilibriumBlowdownModel setInsideGasCoefficient(double insideGasCoefficient) {
    if (insideGasCoefficient <= 0.0) {
      throw new IllegalArgumentException("insideGasCoefficient must be positive");
    }
    this.insideGasCoefficient = insideGasCoefficient;
    return this;
  }

  /**
   * Sets the gas–liquid interfacial heat-transfer coefficient and interface area.
   *
   * @param coefficient interfacial heat-transfer coefficient in W/(m²·K); must be positive
   * @param areaM2 interface area in m²; must be positive
   * @return this model for chaining
   * @throws IllegalArgumentException if an argument is not positive
   */
  public NonEquilibriumBlowdownModel setInterfacial(double coefficient, double areaM2) {
    if (coefficient <= 0.0 || areaM2 <= 0.0) {
      throw new IllegalArgumentException("interfacial coefficient and area must be positive");
    }
    this.interfacialCoefficient = coefficient;
    this.interfaceArea = areaM2;
    return this;
  }

  /**
   * Sets the latent heat of vaporization used for the boil-off mass transfer.
   *
   * @param latentHeat latent heat in J/kg; must be positive
   * @return this model for chaining
   * @throws IllegalArgumentException if {@code latentHeat} is not positive
   */
  public NonEquilibriumBlowdownModel setLatentHeat(double latentHeat) {
    if (latentHeat <= 0.0) {
      throw new IllegalArgumentException("latentHeat must be positive");
    }
    this.latentHeat = latentHeat;
    return this;
  }

  /**
   * Sets the integration time step.
   *
   * @param dt time step in s; must be positive
   * @return this model for chaining
   * @throws IllegalArgumentException if {@code dt} is not positive
   */
  public NonEquilibriumBlowdownModel setTimeStep(double dt) {
    if (dt <= 0.0) {
      throw new IllegalArgumentException("dt must be positive");
    }
    this.timeStep = dt;
    return this;
  }

  /**
   * Sets the maximum simulation time.
   *
   * @param tMax maximum time in s; must be positive
   * @return this model for chaining
   * @throws IllegalArgumentException if {@code tMax} is not positive
   */
  public NonEquilibriumBlowdownModel setMaxTime(double tMax) {
    if (tMax <= 0.0) {
      throw new IllegalArgumentException("tMax must be positive");
    }
    this.maxTime = tMax;
    return this;
  }

  /**
   * Sets the stop pressure.
   *
   * @param pMin stop pressure in Pa absolute; must be positive
   * @return this model for chaining
   * @throws IllegalArgumentException if {@code pMin} is not positive
   */
  public NonEquilibriumBlowdownModel setStopPressure(double pMin) {
    if (pMin <= 0.0) {
      throw new IllegalArgumentException("pMin must be positive");
    }
    this.minPressure = pMin;
    return this;
  }

  /**
   * Runs the non-equilibrium blowdown transient.
   *
   * @return a {@link NemResult} with the vapour-zone and liquid-zone temperature, pressure and mass trajectories
   * @throws IllegalStateException if the initial fluid does not flash into a coexisting vapour and liquid zone
   */
  public NemResult run() {
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initProperties();
    if (fluid.getNumberOfPhases() < 2 || !fluid.hasPhaseType("gas")
        || !(fluid.hasPhaseType("oil") || fluid.hasPhaseType("aqueous"))) {
      throw new IllegalStateException(
          "NonEquilibriumBlowdownModel requires an initial coexisting vapour and liquid phase");
    }
    // Charge the fluid so its volume fills the vessel at the initial pressure and temperature.
    // A composition supplied as mole fractions carries only about one mole in total, which would
    // leave the vessel near-empty and collapse the pressure to back-pressure in the first step.
    double initialFluidVolume = fluid.getVolume("m3");
    if (initialFluidVolume > 0.0) {
      double chargeScale = vesselVolume / initialFluidVolume;
      // Scale each component's moles so the composition and molar mass are preserved. Using
      // setTotalNumberOfMoles() instead corrupts the per-component inventory, which collapses the
      // molar mass to zero and breaks every mass-based balance downstream.
      scaleMoles(fluid, chargeScale);
      ops.TPflash();
      fluid.initProperties();
    }
    SystemInterface gas = fluid.phaseToSystem("gas");
    SystemInterface liquid = fluid.hasPhaseType("oil") ? fluid.phaseToSystem("oil") : fluid.phaseToSystem("aqueous");
    ThermodynamicOperations gasOps = new ThermodynamicOperations(gas);
    ThermodynamicOperations liqOps = new ThermodynamicOperations(liquid);

    double pPa = fluid.getPressure() * 1.0e5;
    double tGas = fluid.getTemperature();
    double tLiq = fluid.getTemperature();
    double tWall = fluid.getTemperature();
    double effInterfaceArea = interfaceArea > 0.0 ? interfaceArea : Math.pow(vesselVolume, 2.0 / 3.0);
    double effExternalArea = fireExposedArea > 0.0 ? fireExposedArea : 4.0 * Math.pow(vesselVolume, 2.0 / 3.0);
    double effWallMass = wallMass > 0.0 ? wallMass : 1.0;
    final double orificeArea = Math.PI * 0.25 * orificeDiameter * orificeDiameter;

    NemResult res = new NemResult();
    res.initialPressureBara = pPa / 1.0e5;

    double t = 0.0;
    res.append(t, pPa / 1.0e5, tGas, tLiq, tWall, massOf(gas), massOf(liquid));

    while (t < maxTime && pPa > minPressure && massOf(gas) > 1.0e-6) {
      // --- Vapour-zone properties ---
      double rhoGas;
      double mwGas;
      double cpGasMass;
      double gamma;
      double z;
      try {
        gas.setTemperature(tGas);
        gas.setPressure(pPa / 1.0e5);
        gasOps.TPflash();
        gas.initProperties();
        rhoGas = gas.getDensity("kg/m3");
        mwGas = gas.getMolarMass();
        double cpMol = gas.getCp("J/molK");
        double cvMol = gas.getCv("J/molK");
        gamma = cvMol > 0.0 ? cpMol / cvMol : 1.3;
        cpGasMass = mwGas > 0.0 ? cpMol / mwGas : 2000.0;
        z = (rhoGas > 0.0 && mwGas > 0.0) ? pPa * mwGas / (rhoGas * GAS_CONSTANT * tGas) : 1.0;
        if (z <= 0.0 || Double.isNaN(z)) {
          z = 1.0;
        }
      } catch (Exception ex) {
        logger.debug("vapour-zone flash fallback at t={}: {}", t, ex.getMessage());
        res.flashFallbackCount++;
        break;
      }

      // --- Liquid-zone properties ---
      double rhoLiq;
      double mwLiq;
      double cpLiqMass;
      try {
        liquid.setTemperature(tLiq);
        liquid.setPressure(pPa / 1.0e5);
        liqOps.TPflash();
        liquid.initProperties();
        rhoLiq = liquid.getDensity("kg/m3");
        mwLiq = liquid.getMolarMass();
        double cpMolLiq = liquid.getCp("J/molK");
        cpLiqMass = mwLiq > 0.0 ? cpMolLiq / mwLiq : 2500.0;
      } catch (Exception ex) {
        logger.debug("liquid-zone flash fallback at t={}: {}", t, ex.getMessage());
        res.flashFallbackCount++;
        break;
      }

      double massGas = massOf(gas);
      double massLiq = massOf(liquid);
      double vLiq = rhoLiq > 0.0 ? massLiq / rhoLiq : 0.0;
      double vGas = Math.max(vesselVolume - vLiq, 1.0e-6);

      // --- Vapour-controlled pressure (EOS-consistent) ---
      // A constant-temperature, constant-volume flash of the vapour zone returns the real-gas
      // pressure for the current mole inventory. This avoids the multiplicative compressibility
      // feedback of the analytic ideal-gas form, which compounds small inventory errors into
      // non-physical pressure spikes over many steps.
      try {
        gas.setTemperature(tGas);
        gasOps.TVflash(vGas, "m3");
        pPa = gas.getPressure() * 1.0e5;
      } catch (Exception ex) {
        logger.debug("vapour-zone TVflash fallback at t={}: {}", t, ex.getMessage());
        res.flashFallbackCount++;
        break;
      }
      if (pPa < backPressure) {
        pPa = backPressure;
      }

      // --- Orifice discharge of vapour ---
      double critRatio = Math.pow(2.0 / (gamma + 1.0), gamma / (gamma - 1.0));
      double pRatio = backPressure / pPa;
      double mDot;
      if (pRatio <= critRatio) {
        mDot = dischargeCoefficient * orificeArea * pPa * Math.sqrt(gamma * mwGas / (z * GAS_CONSTANT * tGas))
            * Math.pow(2.0 / (gamma + 1.0), (gamma + 1.0) / (2.0 * (gamma - 1.0)));
      } else {
        double term = (2.0 * gamma / (gamma - 1.0))
            * (Math.pow(pRatio, 2.0 / gamma) - Math.pow(pRatio, (gamma + 1.0) / gamma));
        if (term < 0.0) {
          term = 0.0;
        }
        mDot = dischargeCoefficient * orificeArea * pPa * Math.sqrt(mwGas / (z * GAS_CONSTANT * tGas))
            * Math.sqrt(term);
      }
      if (mDot < 0.0 || Double.isNaN(mDot)) {
        mDot = 0.0;
      }
      double dmOut = Math.min(mDot * timeStep, massGas);

      // --- Fire heat to the wall ---
      double qRad = FireHeatLoadCalculator.generalizedStefanBoltzmannHeatFlux(fireEmissivity, 1.0, fireFlameTempK,
          tWall);
      double qConv = fireConvCoeff * (fireFlameTempK - tWall);
      double fireFlux = Math.max(qRad + qConv, 0.0);
      double qFire = fireFlux * effExternalArea;

      // --- Wall area split between unwetted (vapour) and wetted (liquid) ---
      double wettedFrac = Math.max(0.0, Math.min(1.0, vLiq / vesselVolume));
      double aWetted = effExternalArea * wettedFrac;
      double aUnwetted = effExternalArea * (1.0 - wettedFrac);
      double qWallToGas = insideGasCoefficient * aUnwetted * (tWall - tGas);

      // --- Wetted-wall nucleate boiling (Rohsenow with effective-HTC fallback) ---
      double superheat = tWall - tLiq;
      double boilFlux;
      try {
        double muLiq = liquid.getPhase(0).getViscosity("kg/msec");
        double kLiq = liquid.getPhase(0).getThermalConductivity("W/mK");
        double prLiq = (kLiq > 0.0 && muLiq > 0.0) ? muLiq * cpLiqMass / kLiq : 5.0;
        if (muLiq > 0.0 && rhoLiq > 0.0 && superheat > 0.0) {
          boilFlux = VesselHeatTransferCorrelations.rohsenowNucleateBoilingHeatFlux(muLiq, latentHeat, rhoLiq, rhoGas,
              surfaceTension, cpLiqMass, superheat, prLiq, surfaceFluidConstant, 1.7);
        } else {
          boilFlux = effectiveBoilingCoefficient * Math.max(superheat, 0.0);
        }
      } catch (Exception ex) {
        logger.debug("Rohsenow fallback at t={}: {}", t, ex.getMessage());
        boilFlux = effectiveBoilingCoefficient * Math.max(superheat, 0.0);
      }
      boilFlux = Math.min(boilFlux, criticalHeatFlux);
      double qWallToLiq = boilFlux * aWetted;

      // --- Series-resistance interfacial coupling (vapour to liquid when vapour is hotter) ---
      double qInterface = interfacialCoefficient * effInterfaceArea * (tGas - tLiq);

      // --- Boil-off mass transfer (wall heat into the liquid drives vaporization) ---
      double dmVap = Math.min(Math.max(qWallToLiq, 0.0) * timeStep / latentHeat, 0.5 * massLiq);

      // --- Wall energy balance ---
      double dWall = (qFire - qWallToGas - qWallToLiq) * timeStep / (effWallMass * wallCp);
      tWall += dWall;
      // The wall cannot exceed the flame temperature that heats it, nor fall below the floor.
      tWall = Math.max(MIN_ZONE_TEMPERATURE_K, Math.min(tWall, fireFlameTempK));

      // --- Vapour energy balance (fire via unwetted wall, minus interfacial, plus mixing of cold
      // boil-off) ---
      if (massGas > 1.0e-9 && cpGasMass > 0.0) {
        double dUgas = (qWallToGas - qInterface) * timeStep + dmVap * cpGasMass * (tLiq - tGas);
        tGas += dUgas / (massGas * cpGasMass);
      }
      // The vapour is heated only by the unwetted wall, so it cannot exceed the wall temperature.
      tGas = Math.max(MIN_ZONE_TEMPERATURE_K, Math.min(tGas, tWall));

      // --- Liquid energy balance (interfacial gain, wall heat consumed by latent vaporization) ---
      if (massLiq > 1.0e-9 && cpLiqMass > 0.0) {
        double dUliq = (qInterface + qWallToLiq) * timeStep - dmVap * latentHeat;
        tLiq += dUliq / (massLiq * cpLiqMass);
      }
      // The liquid is heated by the wetted wall and the warmer vapour, so it cannot exceed either.
      tLiq = Math.max(MIN_ZONE_TEMPERATURE_K, Math.min(tLiq, Math.max(tWall, tGas)));

      // --- Mole bookkeeping: discharge from vapour, transfer boil-off liquid -> vapour ---
      if (massGas > 0.0) {
        scaleMoles(gas, (massGas - dmOut) / massGas);
      }
      transferMoles(liquid, gas, dmVap, mwLiq);

      t += timeStep;
      res.append(t, pPa / 1.0e5, tGas, tLiq, tWall, massOf(gas), massOf(liquid));
    }

    res.recordFinal(tGas, tLiq);
    return res;
  }

  /**
   * Computes the total mass of a fluid in kg from its mole inventory and molar mass.
   *
   * @param system the fluid system
   * @return total mass in kg
   */
  private static double massOf(SystemInterface system) {
    return system.getNumberOfMoles() * system.getMolarMass();
  }

  /**
   * Scales the total mole inventory of a fluid by a factor while preserving composition.
   *
   * @param system the fluid system to scale
   * @param factor multiplicative scaling factor; values not greater than zero, NaN or equal to one are ignored
   */
  private static void scaleMoles(SystemInterface system, double factor) {
    if (factor <= 0.0 || Double.isNaN(factor) || Math.abs(factor - 1.0) < 1.0e-12) {
      return;
    }
    int nc = system.getNumberOfComponents();
    for (int i = 0; i < nc; i++) {
      double cur = system.getComponent(i).getNumberOfmoles();
      system.addComponent(i, cur * (factor - 1.0));
    }
    system.init(0);
  }

  /**
   * Transfers a mass of fluid from a source system to a target system using the source composition.
   *
   * @param source the donor fluid (liquid zone)
   * @param target the receiving fluid (vapour zone)
   * @param massKg mass to transfer in kg; non-positive values are ignored
   * @param sourceMolarMass source molar mass in kg/mol
   */
  private static void transferMoles(SystemInterface source, SystemInterface target, double massKg,
      double sourceMolarMass) {
    if (massKg <= 0.0 || sourceMolarMass <= 0.0) {
      return;
    }
    double sourceTotalMoles = source.getNumberOfMoles();
    if (sourceTotalMoles <= 0.0) {
      return;
    }
    double molesToMove = massKg / sourceMolarMass;
    int nc = source.getNumberOfComponents();
    for (int i = 0; i < nc; i++) {
      double frac = source.getComponent(i).getNumberOfmoles() / sourceTotalMoles;
      double delta = molesToMove * frac;
      if (delta <= 0.0) {
        continue;
      }
      source.addComponent(i, -delta);
      target.addComponent(i, delta);
    }
    source.init(0);
    target.init(0);
  }

  /**
   * Time-series result of a non-equilibrium blowdown run.
   *
   * @author ESOL
   * @version 1.0
   */
  public static class NemResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Time stamps in s. */
    public final List<Double> timeS = new ArrayList<Double>();
    /** Pressure history in bara. */
    public final List<Double> pressureBara = new ArrayList<Double>();
    /** Vapour-zone temperature history in K. */
    public final List<Double> gasTemperatureK = new ArrayList<Double>();
    /** Liquid-zone temperature history in K. */
    public final List<Double> liquidTemperatureK = new ArrayList<Double>();
    /** Wall metal temperature history in K. */
    public final List<Double> wallTemperatureK = new ArrayList<Double>();
    /** Vapour-zone mass history in kg. */
    public final List<Double> gasMassKg = new ArrayList<Double>();
    /** Liquid-zone mass history in kg. */
    public final List<Double> liquidMassKg = new ArrayList<Double>();
    /** Initial absolute pressure in bara. */
    public double initialPressureBara;
    /** Number of steps that hit the guarded flash fallback. */
    public int flashFallbackCount = 0;
    /** Final vapour-zone temperature in K. */
    public double finalGasTemperatureK;
    /** Final liquid-zone temperature in K. */
    public double finalLiquidTemperatureK;
    /** Maximum vapour-minus-liquid temperature bifurcation reached in K. */
    public double maxTemperatureBifurcationK = 0.0;

    /**
     * Appends a state to the trajectory.
     *
     * @param t time in s
     * @param pBara pressure in bara
     * @param tGas vapour-zone temperature in K
     * @param tLiq liquid-zone temperature in K
     * @param tWall wall temperature in K
     * @param mGas vapour-zone mass in kg
     * @param mLiq liquid-zone mass in kg
     */
    void append(double t, double pBara, double tGas, double tLiq, double tWall, double mGas, double mLiq) {
      timeS.add(t);
      pressureBara.add(pBara);
      gasTemperatureK.add(tGas);
      liquidTemperatureK.add(tLiq);
      wallTemperatureK.add(tWall);
      gasMassKg.add(mGas);
      liquidMassKg.add(mLiq);
      double bif = tGas - tLiq;
      if (bif > maxTemperatureBifurcationK) {
        maxTemperatureBifurcationK = bif;
      }
    }

    /**
     * Records the final zone temperatures.
     *
     * @param tGas final vapour-zone temperature in K
     * @param tLiq final liquid-zone temperature in K
     */
    void recordFinal(double tGas, double tLiq) {
      this.finalGasTemperatureK = tGas;
      this.finalLiquidTemperatureK = tLiq;
    }
  }
}
