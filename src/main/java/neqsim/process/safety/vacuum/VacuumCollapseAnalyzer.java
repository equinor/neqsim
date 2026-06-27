package neqsim.process.safety.vacuum;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.unit.PressureUnit;
import neqsim.util.unit.TemperatureUnit;

/**
 * Vacuum-collapse (external-pressure / implosion) screening for a blocked-in vessel that cools.
 *
 * <p>
 * Implements the HAZOP "Less Pressure" deviation caused by cooling or condensation of a blocked-in (rigid,
 * fixed-volume) vapour space. When a vessel is isolated full of vapour and then loses heat — ambient cooling after
 * shutdown, contact with a cold medium, condensing steam, or hot vapour drawn into a cold line — the vapour contracts
 * and condenses at constant volume, so the internal pressure falls. If the internal pressure drops below the vessel
 * external-pressure (vacuum) rating the shell can buckle/implode.
 * </p>
 *
 * <p>
 * The analysis performs a constant-volume cooling sweep with NeqSim {@code TVflash}: the blocked-in inventory is held
 * at the fixed vessel volume while the temperature is reduced from the initial state to the cold-end temperature. At
 * each step the equilibrium internal pressure (including any vapour condensation) is computed. The minimum internal
 * pressure is compared with atmospheric pressure (to quantify the vacuum) and with the vessel external-pressure rating
 * (the verdict). A screening-level make-up (pad-gas / vacuum-breaker) requirement is estimated to keep the vessel above
 * a chosen set point.
 * </p>
 *
 * <p>
 * This is a screening calculation. It assumes a rigid vessel, a closed (blocked-in) inventory with no in-breathing, and
 * thermodynamic equilibrium at each temperature. It does not size the vacuum-relief device transient; the make-up
 * estimate is an ideal-gas inventory difference for orientation only.
 * </p>
 *
 * <p>
 * <b>References:</b>
 * <ul>
 * <li>API STD 521 — Pressure-relieving and Depressuring Systems (vacuum / external pressure cases)</li>
 * <li>API STD 2000 — Venting Atmospheric and Low-pressure Storage Tanks (thermal in-breathing)</li>
 * <li>ASME Section VIII Div. 1, UG-28 — design for external pressure</li>
 * <li>IEC 61882 — HAZOP "Less Pressure" deviation</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class VacuumCollapseAnalyzer implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final Logger logger = LogManager.getLogger(VacuumCollapseAnalyzer.class);

  /** Universal gas constant in J/(mol·K). */
  private static final double R_GAS = 8.314462618;
  /** Default atmospheric pressure in bara. */
  private static final double STANDARD_ATMOSPHERE_BARA = 1.01325;
  /** Default make-up gas molar mass (nitrogen) in kg/mol. */
  private static final double DEFAULT_MAKEUP_MOLAR_MASS = 0.0280134;

  private final SystemInterface fluid;
  private final double vesselVolumeM3;

  private double initialPressureBara = Double.NaN;
  private double initialTemperatureK = Double.NaN;
  private double coldEndTemperatureK = Double.NaN;

  private double atmosphericPressureBara = STANDARD_ATMOSPHERE_BARA;
  private double externalRatingBara = 0.0;
  private boolean ratingProvided = false;

  private double makeupSetpointBara = STANDARD_ATMOSPHERE_BARA;
  private boolean makeupSetpointProvided = false;
  private double makeupMolarMass = DEFAULT_MAKEUP_MOLAR_MASS;

  private int coolingSteps = 20;

  /**
   * Construct a vacuum-collapse analyzer.
   *
   * @param fluid blocked-in inventory; must already have components and a mixing rule configured
   * @param vesselVolumeM3 rigid internal vessel volume in m3; must be positive
   * @throws IllegalArgumentException if the fluid is null or the volume is non-positive
   */
  public VacuumCollapseAnalyzer(SystemInterface fluid, double vesselVolumeM3) {
    if (fluid == null) {
      throw new IllegalArgumentException("fluid must not be null");
    }
    if (vesselVolumeM3 <= 0.0) {
      throw new IllegalArgumentException("vesselVolumeM3 must be positive");
    }
    this.fluid = fluid;
    this.vesselVolumeM3 = vesselVolumeM3;
  }

  /**
   * Sets the initial blocked-in pressure and temperature.
   *
   * @param pressure pressure value in the supplied unit; must be positive
   * @param pressureUnit pressure unit supported by {@link PressureUnit}, for example bara or barg
   * @param temperature temperature value in the supplied unit
   * @param temperatureUnit temperature unit supported by {@link TemperatureUnit}, for example K or C
   * @return this analyzer for chaining
   * @throws IllegalArgumentException if pressure or temperature is non-physical
   */
  public VacuumCollapseAnalyzer setInitialConditions(double pressure, String pressureUnit, double temperature,
      String temperatureUnit) {
    this.initialPressureBara = new PressureUnit(pressure, pressureUnit).getValue("bara");
    this.initialTemperatureK = new TemperatureUnit(temperature, temperatureUnit).getValue("K");
    if (initialPressureBara <= 0.0 || initialTemperatureK <= 0.0) {
      throw new IllegalArgumentException("initial pressure and temperature must be positive absolute values");
    }
    return this;
  }

  /**
   * Sets the cold-end temperature the blocked-in vessel cools down to.
   *
   * @param temperature temperature value in the supplied unit
   * @param temperatureUnit temperature unit supported by {@link TemperatureUnit}, for example K or C
   * @return this analyzer for chaining
   * @throws IllegalArgumentException if the temperature is non-physical
   */
  public VacuumCollapseAnalyzer setColdEndTemperature(double temperature, String temperatureUnit) {
    this.coldEndTemperatureK = new TemperatureUnit(temperature, temperatureUnit).getValue("K");
    if (coldEndTemperatureK <= 0.0) {
      throw new IllegalArgumentException("cold-end temperature must be a positive absolute value");
    }
    return this;
  }

  /**
   * Sets the vessel external-pressure (vacuum) rating: the lowest absolute internal pressure the shell tolerates. Use 0
   * bara for a full-vacuum-rated vessel.
   *
   * @param pressure rating value in the supplied unit; must be in [0, atmospheric]
   * @param pressureUnit pressure unit supported by {@link PressureUnit}
   * @return this analyzer for chaining
   * @throws IllegalArgumentException if the rating is negative
   */
  public VacuumCollapseAnalyzer setExternalPressureRating(double pressure, String pressureUnit) {
    double bara = new PressureUnit(pressure, pressureUnit).getValue("bara");
    if (bara < 0.0) {
      throw new IllegalArgumentException("external pressure rating must be non-negative absolute pressure");
    }
    this.externalRatingBara = bara;
    this.ratingProvided = true;
    return this;
  }

  /**
   * Sets the atmospheric reference pressure used to quantify the vacuum depth.
   *
   * @param pressure atmospheric pressure value in the supplied unit; must be positive
   * @param pressureUnit pressure unit supported by {@link PressureUnit}
   * @return this analyzer for chaining
   * @throws IllegalArgumentException if the pressure is non-positive
   */
  public VacuumCollapseAnalyzer setAtmosphericPressure(double pressure, String pressureUnit) {
    double bara = new PressureUnit(pressure, pressureUnit).getValue("bara");
    if (bara <= 0.0) {
      throw new IllegalArgumentException("atmospheric pressure must be positive");
    }
    this.atmosphericPressureBara = bara;
    return this;
  }

  /**
   * Sets the make-up / pad-gas set point: the internal pressure that vacuum protection should restore.
   *
   * @param pressure set-point value in the supplied unit; must be positive
   * @param pressureUnit pressure unit supported by {@link PressureUnit}
   * @return this analyzer for chaining
   * @throws IllegalArgumentException if the pressure is non-positive
   */
  public VacuumCollapseAnalyzer setMakeupSetpoint(double pressure, String pressureUnit) {
    double bara = new PressureUnit(pressure, pressureUnit).getValue("bara");
    if (bara <= 0.0) {
      throw new IllegalArgumentException("make-up set point must be positive");
    }
    this.makeupSetpointBara = bara;
    this.makeupSetpointProvided = true;
    return this;
  }

  /**
   * Sets the molar mass of the make-up gas used for the screening pad-gas estimate (default nitrogen).
   *
   * @param molarMassKgPerMol make-up gas molar mass in kg/mol; must be positive
   * @return this analyzer for chaining
   * @throws IllegalArgumentException if the molar mass is non-positive
   */
  public VacuumCollapseAnalyzer setMakeupGasMolarMass(double molarMassKgPerMol) {
    if (molarMassKgPerMol <= 0.0) {
      throw new IllegalArgumentException("make-up gas molar mass must be positive");
    }
    this.makeupMolarMass = molarMassKgPerMol;
    return this;
  }

  /**
   * Sets the number of temperature steps in the cooling sweep.
   *
   * @param steps number of cooling steps; must be at least 1
   * @return this analyzer for chaining
   * @throws IllegalArgumentException if steps is less than 1
   */
  public VacuumCollapseAnalyzer setCoolingSteps(int steps) {
    if (steps < 1) {
      throw new IllegalArgumentException("coolingSteps must be at least 1");
    }
    this.coolingSteps = steps;
    return this;
  }

  /**
   * Runs the constant-volume cooling sweep and evaluates the vacuum-collapse verdict.
   *
   * @return the vacuum-collapse result
   * @throws IllegalStateException if required setup is missing or the cold-end temperature is not below the initial
   * temperature
   */
  public VacuumCollapseResult analyze() {
    SystemInterface state = fluid.clone();
    double pInitBara = Double.isNaN(initialPressureBara) ? state.getPressure() : initialPressureBara;
    double tInitK = Double.isNaN(initialTemperatureK) ? state.getTemperature() : initialTemperatureK;
    if (Double.isNaN(coldEndTemperatureK)) {
      throw new IllegalStateException("cold-end temperature must be set via setColdEndTemperature");
    }
    if (coldEndTemperatureK >= tInitK) {
      throw new IllegalStateException("cold-end temperature must be below the initial temperature for a cooling case");
    }

    List<String> warnings = new ArrayList<String>();
    if (!ratingProvided) {
      warnings.add(
          "No external-pressure rating supplied; assuming full-vacuum-rated (0 bara). Provide setExternalPressureRating "
              + "for a real verdict.");
    }

    ThermodynamicOperations ops = new ThermodynamicOperations(state);
    state.setPressure(pInitBara, "bara");
    state.setTemperature(tInitK);
    ops.TPflash();
    state.initProperties();

    // Align the mole basis so the inventory exactly fills the rigid vessel volume at the
    // initial blocked-in conditions. Scaling is done per component (preserving composition);
    // setTotalNumberOfMoles must not be used because it leaves component moles unchanged and
    // corrupts the average molar mass and density.
    double volumeM3 = state.getVolume("m3");
    if (volumeM3 > 0.0) {
      scaleMoles(state, vesselVolumeM3 / volumeM3);
      ops.TPflash();
      state.initProperties();
    }

    List<CoolingPoint> curve = new ArrayList<CoolingPoint>();
    curve.add(new CoolingPoint(tInitK, state.getPressure("bara"), vaporMoleFraction(state)));

    double minPressureBara = state.getPressure("bara");
    double minPressureTemperatureK = tInitK;
    double finalPressureBara = minPressureBara;

    for (int i = 1; i <= coolingSteps; i++) {
      double tK = tInitK + (coldEndTemperatureK - tInitK) * i / coolingSteps;
      state.setTemperature(tK);
      double pBara;
      try {
        ops.TVflash(vesselVolumeM3, "m3");
        pBara = state.getPressure("bara");
      } catch (Exception ex) {
        warnings.add("TVflash failed at " + String.format("%.1f", tK) + " K; cooling sweep truncated ("
            + ex.getMessage() + ").");
        logger.warn("TVflash failed during vacuum-collapse cooling sweep at {} K: {}", tK, ex.getMessage());
        break;
      }
      curve.add(new CoolingPoint(tK, pBara, vaporMoleFraction(state)));
      finalPressureBara = pBara;
      if (pBara < minPressureBara) {
        minPressureBara = pBara;
        minPressureTemperatureK = tK;
      }
    }

    double vacuumDepthBar = Math.max(0.0, atmosphericPressureBara - minPressureBara);
    double marginToRatingBar = minPressureBara - externalRatingBara;
    boolean vacuumPresent = minPressureBara < atmosphericPressureBara;
    boolean withinRating = minPressureBara >= externalRatingBara;

    VacuumVerdict verdict;
    if (!vacuumPresent) {
      verdict = VacuumVerdict.NO_VACUUM;
    } else if (withinRating) {
      verdict = VacuumVerdict.VACUUM_WITHIN_RATING;
    } else {
      verdict = VacuumVerdict.VACUUM_EXCEEDS_RATING;
    }

    double setpoint = makeupSetpointProvided ? makeupSetpointBara : atmosphericPressureBara;
    double makeupMoles = 0.0;
    if (minPressureBara < setpoint) {
      double dpPa = (setpoint - minPressureBara) * 1.0e5;
      makeupMoles = vesselVolumeM3 * dpPa / (R_GAS * minPressureTemperatureK);
    }
    double makeupMassKg = makeupMoles * makeupMolarMass;

    if (verdict == VacuumVerdict.VACUUM_EXCEEDS_RATING) {
      warnings.add("Internal pressure falls below the external-pressure rating; vacuum protection (vacuum breaker, "
          + "pad-gas make-up, or full-vacuum design) is required.");
    } else if (verdict == VacuumVerdict.VACUUM_WITHIN_RATING) {
      warnings.add("Partial vacuum develops but stays within the external-pressure rating; confirm the rating basis "
          + "and consider vacuum protection for defence in depth.");
    }

    return new VacuumCollapseResult(pInitBara, tInitK, coldEndTemperatureK, finalPressureBara, minPressureBara,
        minPressureTemperatureK, atmosphericPressureBara, externalRatingBara, ratingProvided, vacuumDepthBar,
        marginToRatingBar, vacuumPresent, withinRating, verdict, setpoint, makeupMoles, makeupMassKg, makeupMolarMass,
        curve, warnings);
  }

  /**
   * Returns the vapour mole fraction of the current state.
   *
   * @param state the fluid state after a flash
   * @return vapour mole fraction in [0, 1]; 0 when no gas phase is present
   */
  private double vaporMoleFraction(SystemInterface state) {
    if (!state.hasPhaseType("gas")) {
      return 0.0;
    }
    double total = state.getTotalNumberOfMoles();
    if (total <= 0.0) {
      return Double.NaN;
    }
    return state.getPhase("gas").getNumberOfMolesInPhase() / total;
  }

  /**
   * Scales the absolute inventory of the closed system by the given factor while preserving composition.
   *
   * <p>
   * Implemented as per-component mole additions because {@code setTotalNumberOfMoles} only sets the scalar total and
   * leaves the component moles unchanged, corrupting the average molar mass and density.
   * </p>
   *
   * @param state the fluid state to scale
   * @param factor multiplicative scaling factor; values &lt;= 0, NaN, or equal to 1 are ignored
   */
  private void scaleMoles(SystemInterface state, double factor) {
    if (factor <= 0.0 || Double.isNaN(factor) || Math.abs(factor - 1.0) < 1.0e-12) {
      return;
    }
    int nc = state.getNumberOfComponents();
    for (int i = 0; i < nc; i++) {
      double cur = state.getComponent(i).getNumberOfmoles();
      state.addComponent(i, cur * (factor - 1.0));
    }
    state.init(0);
  }

  /**
   * Verdict of a vacuum-collapse screening.
   */
  public enum VacuumVerdict {
    /** Internal pressure stays at or above atmospheric; no vacuum develops. */
    NO_VACUUM,
    /** A partial vacuum develops but stays within the external-pressure rating. */
    VACUUM_WITHIN_RATING,
    /** Internal pressure falls below the external-pressure rating; the shell may collapse. */
    VACUUM_EXCEEDS_RATING
  }

  /**
   * A single point on the constant-volume cooling curve.
   */
  public static class CoolingPoint implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Temperature in K. */
    public final double temperatureK;
    /** Equilibrium internal pressure in bara. */
    public final double pressureBara;
    /** Vapour mole fraction in [0, 1]. */
    public final double vaporMoleFraction;

    /**
     * Construct a cooling-curve point.
     *
     * @param temperatureK temperature in K
     * @param pressureBara internal pressure in bara
     * @param vaporMoleFraction vapour mole fraction in [0, 1]
     */
    public CoolingPoint(double temperatureK, double pressureBara, double vaporMoleFraction) {
      this.temperatureK = temperatureK;
      this.pressureBara = pressureBara;
      this.vaporMoleFraction = vaporMoleFraction;
    }
  }
}
