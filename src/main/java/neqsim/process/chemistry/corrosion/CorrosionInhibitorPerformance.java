package neqsim.process.chemistry.corrosion;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Empirical performance model for film-forming corrosion inhibitors.
 *
 * <p>
 * Computes the inhibited corrosion rate as:
 * </p>
 *
 * <pre>
 * {@code
 * CR_inhibited = CR_base * (1 - efficiency)
 * }
 * </pre>
 *
 * <p>
 * where {@code CR_base} is the uninhibited corrosion rate (e.g. from NORSOK M-506 model) and
 * {@code efficiency} is a function of inhibitor chemistry, dose, temperature, shear, and the
 * presence of organic acids / H2S / O2.
 * </p>
 *
 * <p>
 * The model is calibrated on rotating cylinder electrode (RCE) and high-pressure autoclave data for
 * typical sweet (CO2) and mildly sour service. It is not appropriate for severe sour, very high
 * shear (above 200 Pa), or extreme temperatures (above 175 C) without bench validation.
 * </p>
 *
 * <p>
 * Pattern: configure with setters, call {@link #evaluate()}, then read getters or {@link #toMap()}.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class CorrosionInhibitorPerformance implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Corrosion inhibitor chemistry family.
   */
  public enum InhibitorChemistry {
    /** Imidazoline (oil/water-soluble film former, broad spectrum). */
    IMIDAZOLINE,
    /** Quaternary ammonium (water-soluble, persistent film). */
    QUATERNARY_AMMONIUM,
    /** Amido-amine (oil-soluble). */
    AMIDO_AMINE,
    /** Phosphate ester (high temperature, oil-soluble). */
    PHOSPHATE_ESTER,
    /** Pyridine derivative (acid corrosion inhibitor). */
    PYRIDINE,
    /** Mercaptan / sulphur-based (sour service). */
    MERCAPTAN;
  }

  // ─── Inputs ─────────────────────────────────────────────

  private InhibitorChemistry chemistry = InhibitorChemistry.IMIDAZOLINE;
  private double doseMgL = 25.0;
  private double baseCorrosionRateMmYr = 1.0;
  private double temperatureC = 60.0;
  private double wallShearStressPa = 30.0;
  private double organicAcidPpm = 0.0;
  private double h2sPartialPressureBar = 0.0;
  private double oxygenPpb = 0.0;
  private boolean acidicService = false;

  // ─── Outputs ────────────────────────────────────────────

  private double efficiency = 0.0;
  private double inhibitedCorrosionRateMmYr = 0.0;
  private double minimumEffectiveDoseMgL = 0.0;
  private final Map<String, String> warnings = new LinkedHashMap<String, String>();
  private boolean evaluated = false;

  // ─── Setters ────────────────────────────────────────────

  /**
   * Sets the inhibitor chemistry.
   *
   * @param chemistry inhibitor family
   */
  public void setChemistry(InhibitorChemistry chemistry) {
    this.chemistry = chemistry;
  }

  /**
   * Sets the inhibitor dose.
   *
   * @param doseMgL dose in mg/L of active ingredient
   */
  public void setDoseMgL(double doseMgL) {
    this.doseMgL = doseMgL;
  }

  /**
   * Sets the uninhibited (base) corrosion rate, typically from NORSOK M-506.
   *
   * @param mmYr base CR in mm/yr
   */
  public void setBaseCorrosionRateMmYr(double mmYr) {
    this.baseCorrosionRateMmYr = mmYr;
  }

  /**
   * Sets the operating temperature.
   *
   * @param temperatureC temperature in Celsius
   */
  public void setTemperatureCelsius(double temperatureC) {
    this.temperatureC = temperatureC;
  }

  /**
   * Sets the wall shear stress.
   *
   * @param pa shear stress in Pa
   */
  public void setWallShearStressPa(double pa) {
    this.wallShearStressPa = pa;
  }

  /**
   * Sets the dissolved organic acid (acetic + propionic) concentration.
   *
   * @param ppm concentration in ppm (mass)
   */
  public void setOrganicAcidPpm(double ppm) {
    this.organicAcidPpm = ppm;
  }

  /**
   * Sets the H2S partial pressure.
   *
   * @param bar partial pressure in bar
   */
  public void setH2SPartialPressureBar(double bar) {
    this.h2sPartialPressureBar = bar;
  }

  /**
   * Sets the dissolved oxygen content.
   *
   * @param ppb concentration in ppb
   */
  public void setOxygenPpb(double ppb) {
    this.oxygenPpb = ppb;
  }

  /**
   * Flags acidic service (low pH, e.g. during stimulation).
   *
   * @param acidicService true for acid service
   */
  public void setAcidicService(boolean acidicService) {
    this.acidicService = acidicService;
  }

  /**
   * Pulls the uninhibited corrosion rate from a physics-based De Waard&ndash;Milliams calculator.
   * Calls
   * {@link neqsim.pvtsimulation.flowassurance.DeWaardMilliamsCorrosion#calculateBaselineRate()} so
   * the calculator does not need to be pre-evaluated.
   *
   * @param baseline configured De Waard&ndash;Milliams calculator
   */
  public void setFromDeWaardMilliams(
      neqsim.pvtsimulation.flowassurance.DeWaardMilliamsCorrosion baseline) {
    setBaseCorrosionRateMmYr(baseline.calculateBaselineRate());
  }

  /**
   * Convenience factory: builds a CorrosionInhibitorPerformance with operating conditions seeded
   * from a stream and a Pa-level wall shear estimate from the bulk velocity (caller still picks
   * inhibitor chemistry and dose).
   *
   * @param stream produced fluid stream
   * @param pipeIdMeters pipe inside diameter in meters (for wall-shear estimate)
   * @param velocityMps bulk flow velocity in m/s (for wall-shear estimate)
   * @return configured (but not evaluated) performance model
   */
  public static CorrosionInhibitorPerformance fromStream(
      neqsim.process.equipment.stream.StreamInterface stream, double pipeIdMeters,
      double velocityMps) {
    neqsim.process.chemistry.util.StreamChemistryAdapter ad =
        new neqsim.process.chemistry.util.StreamChemistryAdapter(stream);
    CorrosionInhibitorPerformance p = new CorrosionInhibitorPerformance();
    p.setTemperatureCelsius(ad.getTemperatureCelsius());
    p.setH2SPartialPressureBar(ad.getPartialPressureBara("H2S"));
    p.setWallShearStressPa(ad.estimateWallShearStressPa(pipeIdMeters, velocityMps));
    return p;
  }

  // ─── Evaluation ─────────────────────────────────────────

  /**
   * Computes inhibitor efficiency, inhibited corrosion rate, and minimum effective dose.
   */
  public void evaluate() {
    warnings.clear();
    minimumEffectiveDoseMgL = baseMed(chemistry);
    double maxEff = maxEfficiency(chemistry);

    // Langmuir-like adsorption isotherm
    double k = 0.10; // adsorption affinity
    double saturation = (k * doseMgL) / (1.0 + k * doseMgL);
    double rawEff = maxEff * saturation;

    // Penalties
    double tempPenalty = temperaturePenalty(chemistry, temperatureC);
    double shearPenalty = shearPenalty(wallShearStressPa);
    double oxyPenalty = oxygenPenalty(oxygenPpb);
    double acidPenalty = organicAcidPenalty(organicAcidPpm);
    double acidServicePenalty =
        acidicService && chemistry != InhibitorChemistry.PYRIDINE ? 0.6 : 1.0;

    efficiency = Math.max(0.0, Math.min(0.999,
        rawEff * tempPenalty * shearPenalty * oxyPenalty * acidPenalty * acidServicePenalty));

    inhibitedCorrosionRateMmYr = baseCorrosionRateMmYr * (1.0 - efficiency);

    // Warnings
    if (chemistry != InhibitorChemistry.PHOSPHATE_ESTER && chemistry != InhibitorChemistry.MERCAPTAN
        && temperatureC > 150.0) {
      warnings.put("thermal_degradation",
          "Most film-forming CIs degrade above 150 C; use phosphate ester or CRA upgrade");
    }
    if (wallShearStressPa > 150.0) {
      warnings.put("high_shear",
          "Shear stress above 150 Pa erodes inhibitor film; consider higher dose or CRA");
    }
    if (oxygenPpb > 50.0) {
      warnings.put("oxygen_ingress",
          "O2 above 50 ppb undermines film-forming CIs; eliminate O2 ingress or use scavenger");
    }
    if (acidicService && chemistry != InhibitorChemistry.PYRIDINE) {
      warnings.put("acidic_service",
          "Use a dedicated acid corrosion inhibitor (pyridine-based) during acid stimulation");
    }
    if (h2sPartialPressureBar > 1.0 && chemistry != InhibitorChemistry.MERCAPTAN) {
      warnings.put("sour_service",
          "H2S partial pressure above 1 bar — verify with sour-service compatible chemistry");
    }
    if (doseMgL < minimumEffectiveDoseMgL) {
      warnings.put("under_dose", "Dose below minimum effective level for this chemistry ("
          + minimumEffectiveDoseMgL + " mg/L)");
    }
    evaluated = true;
  }

  /**
   * Maximum efficiency obtainable for a given chemistry under benign conditions.
   *
   * @param chem inhibitor chemistry
   * @return max efficiency 0..1
   */
  private static double maxEfficiency(InhibitorChemistry chem) {
    switch (chem) {
      case IMIDAZOLINE:
        return 0.95;
      case QUATERNARY_AMMONIUM:
        return 0.93;
      case AMIDO_AMINE:
        return 0.92;
      case PHOSPHATE_ESTER:
        return 0.90;
      case PYRIDINE:
        return 0.97;
      case MERCAPTAN:
        return 0.93;
      default:
        return 0.85;
    }
  }

  /**
   * Minimum effective dose for a chemistry.
   *
   * @param chem inhibitor chemistry
   * @return MED in mg/L
   */
  private static double baseMed(InhibitorChemistry chem) {
    switch (chem) {
      case IMIDAZOLINE:
        return 10.0;
      case QUATERNARY_AMMONIUM:
        return 15.0;
      case AMIDO_AMINE:
        return 12.0;
      case PHOSPHATE_ESTER:
        return 25.0;
      case PYRIDINE:
        return 1000.0; // acid service uses much higher dose
      case MERCAPTAN:
        return 20.0;
      default:
        return 25.0;
    }
  }

  /**
   * Temperature penalty factor.
   *
   * @param chem chemistry
   * @param tC temperature in Celsius
   * @return penalty 0..1 (1 = no penalty)
   */
  private static double temperaturePenalty(InhibitorChemistry chem, double tC) {
    double tMax;
    switch (chem) {
      case IMIDAZOLINE:
        tMax = 150.0;
        break;
      case QUATERNARY_AMMONIUM:
        tMax = 130.0;
        break;
      case AMIDO_AMINE:
        tMax = 140.0;
        break;
      case PHOSPHATE_ESTER:
        tMax = 200.0;
        break;
      case PYRIDINE:
        tMax = 120.0;
        break;
      case MERCAPTAN:
        tMax = 175.0;
        break;
      default:
        tMax = 150.0;
    }
    if (tC <= tMax - 30.0) {
      return 1.0;
    }
    if (tC >= tMax) {
      return 0.4;
    }
    return 1.0 - 0.6 * (tC - (tMax - 30.0)) / 30.0;
  }

  /**
   * Shear stress penalty factor.
   *
   * @param tauPa shear stress in Pa
   * @return penalty 0..1
   */
  private static double shearPenalty(double tauPa) {
    if (tauPa < 50.0) {
      return 1.0;
    }
    if (tauPa > 200.0) {
      return 0.4;
    }
    return 1.0 - 0.6 * (tauPa - 50.0) / 150.0;
  }

  /**
   * Oxygen penalty factor.
   *
   * @param ppb O2 in ppb
   * @return penalty 0..1
   */
  private static double oxygenPenalty(double ppb) {
    if (ppb < 10.0) {
      return 1.0;
    }
    if (ppb > 200.0) {
      return 0.3;
    }
    return 1.0 - 0.7 * (ppb - 10.0) / 190.0;
  }

  /**
   * Organic acid penalty factor.
   *
   * @param ppm organic acid in ppm
   * @return penalty 0..1
   */
  private static double organicAcidPenalty(double ppm) {
    if (ppm < 100.0) {
      return 1.0;
    }
    if (ppm > 2000.0) {
      return 0.5;
    }
    return 1.0 - 0.5 * (ppm - 100.0) / 1900.0;
  }

  // ─── Getters ────────────────────────────────────────────

  /**
   * Returns the inhibitor efficiency.
   *
   * @return efficiency 0..1
   */
  public double getEfficiency() {
    return efficiency;
  }

  /**
   * Returns the inhibited corrosion rate.
   *
   * @return corrosion rate in mm/yr
   */
  public double getInhibitedCorrosionRateMmYr() {
    return inhibitedCorrosionRateMmYr;
  }

  /**
   * Returns the minimum effective dose for the selected chemistry.
   *
   * @return MED in mg/L
   */
  public double getMinimumEffectiveDoseMgL() {
    return minimumEffectiveDoseMgL;
  }

  /**
   * Returns warnings.
   *
   * @return ordered map of warning code → message
   */
  public Map<String, String> getWarnings() {
    return new LinkedHashMap<String, String>(warnings);
  }

  /**
   * Returns whether evaluate() has been run.
   *
   * @return true if evaluated
   */
  public boolean isEvaluated() {
    return evaluated;
  }

  /**
   * Returns a structured map for JSON output.
   *
   * @return ordered map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    Map<String, Object> in = new LinkedHashMap<String, Object>();
    in.put("chemistry", chemistry.name());
    in.put("doseMgL", doseMgL);
    in.put("baseCorrosionRateMmYr", baseCorrosionRateMmYr);
    in.put("temperatureC", temperatureC);
    in.put("wallShearStressPa", wallShearStressPa);
    in.put("organicAcidPpm", organicAcidPpm);
    in.put("h2sPartialPressureBar", h2sPartialPressureBar);
    in.put("oxygenPpb", oxygenPpb);
    in.put("acidicService", acidicService);
    map.put("inputs", in);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("efficiency", efficiency);
    out.put("inhibitedCorrosionRateMmYr", inhibitedCorrosionRateMmYr);
    out.put("minimumEffectiveDoseMgL", minimumEffectiveDoseMgL);
    map.put("outputs", out);
    map.put("warnings", warnings);
    map.put("standardsApplied", getStandardsApplied());
    return map;
  }

  /**
   * Returns the industry standards applied by this corrosion-inhibitor model.
   *
   * @return list of standards (each as an ordered map)
   */
  public java.util.List<java.util.Map<String, Object>> getStandardsApplied() {
    return neqsim.process.chemistry.util.StandardsRegistry.toMapList(
        neqsim.process.chemistry.util.StandardsRegistry.NACE_TM0169,
        neqsim.process.chemistry.util.StandardsRegistry.NACE_SP0775,
        neqsim.process.chemistry.util.StandardsRegistry.NORSOK_M506,
        neqsim.process.chemistry.util.StandardsRegistry.ASTM_G31);
  }
}
