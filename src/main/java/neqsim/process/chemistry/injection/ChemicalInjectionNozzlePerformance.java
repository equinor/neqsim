package neqsim.process.chemistry.injection;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Screening model for a liquid chemical injected into a flowing gas line through an injection quill or an atomizing
 * spray nozzle.
 *
 * <p>
 * Gas-phase chemical treatment (H2S scavenger, corrosion inhibitor, hydrate inhibitor sprayed into a gas stream) only
 * works if the chemical is actually dispersed in the gas. A bare injection quill releases a coarse liquid jet that is
 * stripped into large drops, which settle out and run along the pipe wall; an atomizing nozzle produces a fine spray
 * whose drops stay entrained long enough to contact the gas. This class quantifies that difference from first
 * principles so a quill-to-atomizer modification can be evaluated instead of asserted.
 * </p>
 *
 * <p>
 * It computes:
 * </p>
 * <ul>
 * <li>gas velocity in the injection line and the industry screening rule that a bare quill needs a high gas velocity to
 * disperse the chemical at all;</li>
 * <li>drop Sauter mean diameter, from the Lefebvre pressure-swirl correlation for an atomizing nozzle, or from the
 * critical-Weber aerodynamic breakup limit for a bare quill;</li>
 * <li>the gas-liquid interfacial area created per unit volume of gas;</li>
 * <li>the distance a drop travels before it settles to the pipe wall, which is shortened by the fact that
 * insertion-length limits force the nozzle off the pipe centreline;</li>
 * <li>a bounded dispersion index in the range 0 to 1 suitable for {@code H2SScavenger.setMixingEfficiency}.</li>
 * </ul>
 *
 * <p>
 * Correlations used, all from the open literature:
 * </p>
 * <ul>
 * <li>Pressure-swirl Sauter mean diameter, Lefebvre, <i>Atomization and Sprays</i>:
 * {@code SMD = 2.25 sigma^0.25 muL^0.25 mdot^0.25 dP^-0.5 rhoG^-0.25}.</li>
 * <li>Aerodynamic breakup limit for an unatomized jet: {@code dmax = We_crit sigma / (rhoG u^2)} with a critical Weber
 * number of 12 for bag breakup.</li>
 * <li>Drop settling by the Stokes law with a Schiller-Naumann drag correction outside the creeping flow range.</li>
 * <li>Specific interfacial area of a spray: {@code a = 6 QL / (SMD QG)}.</li>
 * </ul>
 *
 * <p>
 * The dispersion index is a screening index, not a measured mixing efficiency. It is intended to rank injection
 * arrangements against each other and to feed a scavenger performance model; it does not replace CFD or a field test.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class ChemicalInjectionNozzlePerformance implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Gravitational acceleration in m/s2. */
  private static final double G = 9.80665;

  /** Critical Weber number for bag breakup of an unatomized liquid jet. */
  private static final double WE_CRITICAL = 12.0;

  /**
   * Minimum gas velocity in m/s below which a bare injection quill is not recommended and an atomizing nozzle should be
   * used instead.
   */
  public static final double QUILL_MIN_GAS_VELOCITY = 10.0;

  /** Lower bound in micrometre of the drop size band recommended for gas-phase chemical treating. */
  public static final double DROPLET_BAND_MIN_MICRON = 10.0;

  /** Preferred lower bound in micrometre of the drop size band. */
  public static final double DROPLET_PREFERRED_MIN_MICRON = 20.0;

  /** Preferred upper bound in micrometre of the drop size band. */
  public static final double DROPLET_PREFERRED_MAX_MICRON = 40.0;

  /** Upper bound in micrometre of the drop size band. */
  public static final double DROPLET_BAND_MAX_MICRON = 50.0;

  /**
   * Injection device family.
   */
  public enum InjectionDevice {
    /** Open quill or open-ended pipe stub with no atomizing tip. */
    PLAIN_QUILL,
    /** Pressure-swirl nozzle producing a solid cone spray. */
    FULL_CONE_NOZZLE,
    /** Pressure-swirl nozzle producing an annular cone spray. */
    HOLLOW_CONE_NOZZLE;
  }

  // ─── Inputs ─────────────────────────────────────────────

  private InjectionDevice device = InjectionDevice.PLAIN_QUILL;
  private double pipeInnerDiameter = 0.4889;
  private double gasVolumeFlow = 2.2;
  private double gasDensity = 12.0;
  private double gasViscosity = 1.2e-5;
  private double chemicalVolumeFlow = 235.0;
  private double chemicalDensity = 1050.0;
  private double chemicalViscosity = 5.0e-3;
  private double surfaceTension = 0.035;
  private double nozzleDifferentialPressure = 6.5;
  private double sprayConeAngle = 90.0;
  private double insertionDepth = 0.135;
  private double evaluationLength = Double.NaN;

  // ─── Outputs ────────────────────────────────────────────

  private double gasVelocity = 0.0;
  private double sauterMeanDiameter = 0.0;
  private double interfacialArea = 0.0;
  private double dropletSettlingVelocity = 0.0;
  private double wallImpingementLength = 0.0;
  private double coverageFraction = 0.0;
  private double suspensionIndex = 0.0;
  private double dispersionIndex = 0.0;
  private final List<String> warnings = new ArrayList<String>();
  private boolean evaluated = false;

  // ─── Setters ────────────────────────────────────────────

  /**
   * Sets the injection device family.
   *
   * @param device injection device; a plain quill has no atomizing tip
   */
  public void setInjectionDevice(InjectionDevice device) {
    this.device = device;
  }

  /**
   * Sets the inner diameter of the pipe the chemical is injected into.
   *
   * @param metre inner diameter in m, must be positive
   */
  public void setPipeInnerDiameter(double metre) {
    this.pipeInnerDiameter = metre;
  }

  /**
   * Sets the actual volumetric gas flow at line conditions.
   *
   * @param m3PerSecond gas flow in m3/s at the pressure and temperature of the injection point
   */
  public void setGasVolumeFlow(double m3PerSecond) {
    this.gasVolumeFlow = m3PerSecond;
  }

  /**
   * Sets the gas density at line conditions.
   *
   * @param kgPerM3 density in kg/m3, typically taken from a flashed NeqSim stream
   */
  public void setGasDensity(double kgPerM3) {
    this.gasDensity = kgPerM3;
  }

  /**
   * Sets the gas dynamic viscosity at line conditions.
   *
   * @param pascalSecond viscosity in Pa s
   */
  public void setGasViscosity(double pascalSecond) {
    this.gasViscosity = pascalSecond;
  }

  /**
   * Sets the injected chemical volumetric flow.
   *
   * @param litrePerHour chemical flow in l/h
   */
  public void setChemicalVolumeFlow(double litrePerHour) {
    this.chemicalVolumeFlow = litrePerHour;
  }

  /**
   * Sets the injected chemical density.
   *
   * @param kgPerM3 density in kg/m3
   */
  public void setChemicalDensity(double kgPerM3) {
    this.chemicalDensity = kgPerM3;
  }

  /**
   * Sets the injected chemical dynamic viscosity.
   *
   * @param pascalSecond viscosity in Pa s
   */
  public void setChemicalViscosity(double pascalSecond) {
    this.chemicalViscosity = pascalSecond;
  }

  /**
   * Sets the chemical surface tension against the gas.
   *
   * @param newtonPerMetre surface tension in N/m
   */
  public void setSurfaceTension(double newtonPerMetre) {
    this.surfaceTension = newtonPerMetre;
  }

  /**
   * Sets the pressure drop available across the injection device.
   *
   * @param bar differential pressure in bar; for a nozzle this is the atomizing energy
   */
  public void setNozzleDifferentialPressure(double bar) {
    this.nozzleDifferentialPressure = bar;
  }

  /**
   * Sets the included spray cone angle of the nozzle.
   *
   * @param degrees full included angle in degrees, for example 90 or 110
   */
  public void setSprayConeAngle(double degrees) {
    this.sprayConeAngle = degrees;
  }

  /**
   * Sets how far the injection point reaches into the pipe, measured from the pipe wall.
   *
   * <p>
   * Chemical injection piping specifications commonly limit the insertion length, so on a large pipe the injection
   * point cannot reach the centreline. The offset from the centreline is {@code D/2 - insertionDepth} and it shortens
   * the distance a drop can travel before it reaches the wall.
   * </p>
   *
   * @param metre insertion depth in m measured from the inner pipe wall
   */
  public void setInsertionDepth(double metre) {
    this.insertionDepth = metre;
  }

  /**
   * Sets the straight length over which the chemical has to stay entrained, typically the run to the next bend or to
   * the equipment being protected.
   *
   * @param metre required contacting length in m; when not set, ten pipe diameters are used
   */
  public void setEvaluationLength(double metre) {
    this.evaluationLength = metre;
  }

  // ─── Evaluation ─────────────────────────────────────────

  /**
   * Evaluates the injection arrangement.
   *
   * <p>
   * Call the getters after this method returns. Repeated calls recompute from the current inputs.
   * </p>
   */
  public void evaluate() {
    warnings.clear();

    double area = Math.PI * pipeInnerDiameter * pipeInnerDiameter / 4.0;
    gasVelocity = gasVolumeFlow / Math.max(1.0e-12, area);

    double chemicalMassFlow = chemicalVolumeFlow / 1000.0 / 3600.0 * chemicalDensity;
    double deltaPressurePa = nozzleDifferentialPressure * 1.0e5;

    if (device == InjectionDevice.PLAIN_QUILL) {
      sauterMeanDiameter = aerodynamicBreakupDiameter();
    } else {
      sauterMeanDiameter = lefebvreSauterMeanDiameter(chemicalMassFlow, deltaPressurePa);
      double aerodynamicLimit = aerodynamicBreakupDiameter();
      if (aerodynamicLimit < sauterMeanDiameter) {
        sauterMeanDiameter = aerodynamicLimit;
      }
    }

    double chemicalVolumeFlowSi = chemicalVolumeFlow / 1000.0 / 3600.0;
    interfacialArea = 6.0 * chemicalVolumeFlowSi / Math.max(1.0e-12, sauterMeanDiameter * gasVolumeFlow);

    dropletSettlingVelocity = settlingVelocity(sauterMeanDiameter);

    double fallHeight = dropFallHeight();
    wallImpingementLength = gasVelocity * fallHeight / Math.max(1.0e-9, dropletSettlingVelocity);

    double requiredLength = Double.isNaN(evaluationLength) ? 10.0 * pipeInnerDiameter : evaluationLength;
    suspensionIndex = Math.min(1.0, wallImpingementLength / Math.max(1.0e-9, requiredLength));

    coverageFraction = sprayCoverageFraction(requiredLength);

    dispersionIndex = coverageFraction * suspensionIndex;
    if (device == InjectionDevice.PLAIN_QUILL && gasVelocity < QUILL_MIN_GAS_VELOCITY) {
      dispersionIndex *= gasVelocity / QUILL_MIN_GAS_VELOCITY;
    }
    dispersionIndex = Math.max(0.0, Math.min(1.0, dispersionIndex));

    collectWarnings();
    evaluated = true;
  }

  /**
   * Returns the Sauter mean diameter from the Lefebvre pressure-swirl correlation.
   *
   * @param chemicalMassFlow chemical mass flow in kg/s
   * @param deltaPressurePa differential pressure across the nozzle in Pa
   * @return Sauter mean diameter in m
   */
  private double lefebvreSauterMeanDiameter(double chemicalMassFlow, double deltaPressurePa) {
    double dp = Math.max(1.0e3, deltaPressurePa);
    double mdot = Math.max(1.0e-9, chemicalMassFlow);
    return 2.25 * Math.pow(surfaceTension, 0.25) * Math.pow(chemicalViscosity, 0.25) * Math.pow(mdot, 0.25)
        * Math.pow(dp, -0.5) * Math.pow(Math.max(1.0e-6, gasDensity), -0.25);
  }

  /**
   * Returns the largest drop that survives aerodynamic breakup in the gas stream.
   *
   * @return maximum stable drop diameter in m
   */
  private double aerodynamicBreakupDiameter() {
    double dynamicHead = gasDensity * gasVelocity * gasVelocity;
    if (dynamicHead <= 1.0e-9) {
      return pipeInnerDiameter;
    }
    return Math.min(pipeInnerDiameter, WE_CRITICAL * surfaceTension / dynamicHead);
  }

  /**
   * Returns the terminal settling velocity of a drop, using the Stokes law with a Schiller-Naumann drag correction when
   * the drop Reynolds number leaves the creeping flow range.
   *
   * @param diameter drop diameter in m
   * @return terminal settling velocity in m/s
   */
  private double settlingVelocity(double diameter) {
    double stokes = (chemicalDensity - gasDensity) * G * diameter * diameter / (18.0 * Math.max(1.0e-9, gasViscosity));
    double velocity = stokes;
    for (int i = 0; i < 50; i++) {
      double reynolds = gasDensity * velocity * diameter / Math.max(1.0e-12, gasViscosity);
      double correction = 1.0 + 0.15 * Math.pow(Math.max(1.0e-9, reynolds), 0.687);
      double updated = stokes / correction;
      if (Math.abs(updated - velocity) < 1.0e-9 * Math.max(1.0e-9, velocity)) {
        velocity = updated;
        break;
      }
      velocity = updated;
    }
    return Math.max(1.0e-9, velocity);
  }

  /**
   * Returns the vertical distance a drop released at the injection point must fall before it reaches the pipe wall.
   *
   * @return fall height in m
   */
  private double dropFallHeight() {
    double depth = Math.max(0.0, Math.min(pipeInnerDiameter, insertionDepth));
    return Math.max(0.01 * pipeInnerDiameter, Math.min(depth, pipeInnerDiameter - depth));
  }

  /**
   * Returns the fraction of the pipe cross section swept by the spray at the required contacting length, accounting for
   * the off-centre position of the injection point.
   *
   * @param requiredLength contacting length in m
   * @return covered fraction of the pipe cross section, between 0 and 1
   */
  private double sprayCoverageFraction(double requiredLength) {
    if (device == InjectionDevice.PLAIN_QUILL) {
      double jetFraction = Math.min(1.0, gasVelocity / QUILL_MIN_GAS_VELOCITY);
      return 0.15 * jetFraction;
    }
    double axial = Math.min(requiredLength, Math.max(1.0e-6, wallImpingementLength));
    double sprayRadius = axial * Math.tan(Math.toRadians(sprayConeAngle / 2.0));
    double pipeRadius = pipeInnerDiameter / 2.0;
    double offset = Math.abs(pipeRadius - dropFallHeight());
    double covered = overlapArea(sprayRadius, pipeRadius, offset);
    if (device == InjectionDevice.HOLLOW_CONE_NOZZLE) {
      covered *= 0.7;
    }
    return Math.max(0.0, Math.min(1.0, covered / (Math.PI * pipeRadius * pipeRadius)));
  }

  /**
   * Returns the overlap area of two circles whose centres are a given distance apart.
   *
   * @param r1 radius of the first circle in m
   * @param r2 radius of the second circle in m
   * @param distance distance between the centres in m
   * @return overlap area in m2
   */
  private static double overlapArea(double r1, double r2, double distance) {
    if (r1 <= 0.0 || r2 <= 0.0) {
      return 0.0;
    }
    if (distance >= r1 + r2) {
      return 0.0;
    }
    if (distance <= Math.abs(r1 - r2)) {
      double small = Math.min(r1, r2);
      return Math.PI * small * small;
    }
    double d = distance;
    double a1 = r1 * r1 * Math.acos((d * d + r1 * r1 - r2 * r2) / (2.0 * d * r1));
    double a2 = r2 * r2 * Math.acos((d * d + r2 * r2 - r1 * r1) / (2.0 * d * r2));
    double a3 = 0.5 * Math.sqrt(Math.max(0.0, (-d + r1 + r2) * (d + r1 - r2) * (d - r1 + r2) * (d + r1 + r2)));
    return a1 + a2 - a3;
  }

  /**
   * Populates the warning list from the evaluated results.
   */
  private void collectWarnings() {
    if (device == InjectionDevice.PLAIN_QUILL && gasVelocity < QUILL_MIN_GAS_VELOCITY) {
      warnings.add("gas_velocity_below_quill_limit: gas velocity " + round(gasVelocity, 1) + " m/s is below the "
          + QUILL_MIN_GAS_VELOCITY + " m/s screening limit for a bare quill; fit an atomizing nozzle");
    }
    double micron = sauterMeanDiameter * 1.0e6;
    if (micron > DROPLET_BAND_MAX_MICRON) {
      warnings.add("droplets_too_coarse: Sauter mean diameter " + round(micron, 1) + " micron exceeds the "
          + DROPLET_BAND_MAX_MICRON + " micron band; drops settle to the wall instead of contacting the gas");
    } else if (micron < DROPLET_BAND_MIN_MICRON) {
      warnings.add("mist_carryover_risk: Sauter mean diameter " + round(micron, 1) + " micron is below the "
          + DROPLET_BAND_MIN_MICRON + " micron band; mist may carry over into downstream scrubbers");
    }
    if (suspensionIndex < 1.0) {
      warnings.add("early_wall_impingement: drops reach the pipe wall after " + round(wallImpingementLength, 1)
          + " m, short of the required contacting length");
    }
    if (coverageFraction < 0.5) {
      warnings.add("partial_cross_section_coverage: the spray reaches only " + round(100.0 * coverageFraction, 0)
          + " percent of the pipe cross section; consider a wider cone, a second injection point"
          + " or a static remixer downstream");
    }
    if (insertionDepth < 0.45 * pipeInnerDiameter) {
      warnings.add("off_centre_injection: the injection point sits "
          + round(1000.0 * (pipeInnerDiameter / 2.0 - insertionDepth), 0)
          + " mm off the pipe centreline, which shortens the drop flight path");
    }
  }

  /**
   * Rounds a value to a given number of decimals.
   *
   * @param value value to round
   * @param decimals number of decimals
   * @return rounded value
   */
  private static double round(double value, int decimals) {
    double factor = Math.pow(10.0, decimals);
    return Math.round(value * factor) / factor;
  }

  // ─── Getters ────────────────────────────────────────────

  /**
   * Returns the gas velocity in the injection line.
   *
   * @return velocity in m/s
   */
  public double getGasVelocity() {
    return gasVelocity;
  }

  /**
   * Returns the Sauter mean diameter of the injected drops.
   *
   * @return drop diameter in m
   */
  public double getSauterMeanDiameter() {
    return sauterMeanDiameter;
  }

  /**
   * Returns the Sauter mean diameter of the injected drops in micrometre.
   *
   * @return drop diameter in micrometre
   */
  public double getSauterMeanDiameterMicron() {
    return sauterMeanDiameter * 1.0e6;
  }

  /**
   * Returns the gas-liquid interfacial area created per unit volume of gas.
   *
   * @return specific interfacial area in m2/m3
   */
  public double getInterfacialArea() {
    return interfacialArea;
  }

  /**
   * Returns the terminal settling velocity of a drop of the Sauter mean diameter.
   *
   * @return settling velocity in m/s
   */
  public double getDropletSettlingVelocity() {
    return dropletSettlingVelocity;
  }

  /**
   * Returns the distance a drop travels along the pipe before it reaches the wall.
   *
   * @return wall impingement length in m
   */
  public double getWallImpingementLength() {
    return wallImpingementLength;
  }

  /**
   * Returns the fraction of the pipe cross section swept by the spray.
   *
   * @return covered fraction between 0 and 1
   */
  public double getCoverageFraction() {
    return coverageFraction;
  }

  /**
   * Returns how much of the required contacting length the drops survive.
   *
   * @return suspension index between 0 and 1
   */
  public double getSuspensionIndex() {
    return suspensionIndex;
  }

  /**
   * Returns the bounded dispersion index of the injection arrangement.
   *
   * <p>
   * The index is the product of the cross-section coverage and the drop suspension index, penalised for a bare quill in
   * a low velocity line. It is a screening index suitable as the mixing efficiency input of a scavenger or inhibitor
   * performance model.
   * </p>
   *
   * @return dispersion index between 0 and 1
   */
  public double getDispersionIndex() {
    return dispersionIndex;
  }

  /**
   * Returns the warnings raised by the last evaluation.
   *
   * @return list of warning strings, empty when no warning applies
   */
  public List<String> getWarnings() {
    return new ArrayList<String>(warnings);
  }

  /**
   * Returns whether the model has been evaluated.
   *
   * @return true when {@link #evaluate()} has been called
   */
  public boolean isEvaluated() {
    return evaluated;
  }

  /**
   * Returns the evaluated results as a map.
   *
   * @return map of result names to values
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("device", device.name());
    map.put("pipeInnerDiameter_m", pipeInnerDiameter);
    map.put("gasVelocity_m_per_s", gasVelocity);
    map.put("nozzleDifferentialPressure_bar", nozzleDifferentialPressure);
    map.put("sauterMeanDiameter_micron", getSauterMeanDiameterMicron());
    map.put("interfacialArea_m2_per_m3", interfacialArea);
    map.put("dropletSettlingVelocity_m_per_s", dropletSettlingVelocity);
    map.put("wallImpingementLength_m", wallImpingementLength);
    map.put("coverageFraction", coverageFraction);
    map.put("suspensionIndex", suspensionIndex);
    map.put("dispersionIndex", dispersionIndex);
    map.put("warnings", getWarnings());
    return map;
  }
}
