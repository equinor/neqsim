package neqsim.process.safety.dispersion;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Reference library of toxic exposure thresholds for common process chemicals.
 *
 * <p>
 * Provides IDLH (Immediately Dangerous to Life and Health, NIOSH), ERPG-2 (Emergency Response
 * Planning Guideline level-2, AIHA), and AEGL-2 (Acute Exposure Guideline Level 2, EPA) values in
 * ppm for use in dispersion screening and emergency planning.
 *
 * <p>
 * <b>Sources:</b> NIOSH Pocket Guide; AIHA ERPG &amp; WEEL Handbook (latest); EPA AEGL database.
 * Values are conservative defaults; verify against the latest published edition for design work.
 *
 * @author ESOL
 * @version 1.0
 */
public final class ToxicLibrary {

  private ToxicLibrary() {}

  /**
   * Toxic-exposure threshold record for a chemical.
   */
  public static class Thresholds implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Chemical CAS-like name. */
    public final String name;
    /** IDLH in ppm. */
    public final double idlhPpm;
    /** ERPG-2 in ppm. */
    public final double erpg2Ppm;
    /** AEGL-2 (60 min) in ppm. */
    public final double aegl2Ppm;

    /**
     * @param name chemical name
     * @param idlhPpm IDLH in ppm
     * @param erpg2Ppm ERPG-2 in ppm
     * @param aegl2Ppm AEGL-2 in ppm
     */
    public Thresholds(String name, double idlhPpm, double erpg2Ppm, double aegl2Ppm) {
      this.name = name;
      this.idlhPpm = idlhPpm;
      this.erpg2Ppm = erpg2Ppm;
      this.aegl2Ppm = aegl2Ppm;
    }
  }

  private static final Map<String, Thresholds> LIB;

  static {
    Map<String, Thresholds> m = new HashMap<>();
    // name, IDLH ppm, ERPG-2 ppm, AEGL-2 60 min ppm
    m.put("H2S", new Thresholds("Hydrogen sulfide", 100.0, 30.0, 27.0));
    m.put("hydrogen sulfide", m.get("H2S"));
    m.put("CO", new Thresholds("Carbon monoxide", 1200.0, 350.0, 83.0));
    m.put("carbon monoxide", m.get("CO"));
    m.put("Cl2", new Thresholds("Chlorine", 10.0, 3.0, 2.0));
    m.put("chlorine", m.get("Cl2"));
    m.put("NH3", new Thresholds("Ammonia", 300.0, 150.0, 160.0));
    m.put("ammonia", m.get("NH3"));
    m.put("SO2", new Thresholds("Sulfur dioxide", 100.0, 3.0, 0.75));
    m.put("sulfur dioxide", m.get("SO2"));
    m.put("HCN", new Thresholds("Hydrogen cyanide", 50.0, 10.0, 7.1));
    m.put("HF", new Thresholds("Hydrogen fluoride", 30.0, 20.0, 24.0));
    m.put("HCl", new Thresholds("Hydrogen chloride", 50.0, 20.0, 22.0));
    m.put("methanol", new Thresholds("Methanol", 6000.0, 1000.0, 530.0));
    m.put("benzene", new Thresholds("Benzene", 500.0, 150.0, 800.0));
    LIB = Collections.unmodifiableMap(m);
  }

  /**
   * Look up exposure thresholds for a chemical by name (case-insensitive).
   *
   * @param chemical chemical name or formula
   * @return Thresholds, or {@code null} if unknown
   */
  public static Thresholds get(String chemical) {
    if (chemical == null) {
      return null;
    }
    Thresholds t = LIB.get(chemical);
    if (t != null) {
      return t;
    }
    return LIB.get(chemical.toLowerCase());
  }

  /**
   * Convert a concentration in ppm (mole basis) to kg/m³.
   *
   * @param ppm concentration in ppm
   * @param molarMassKgPerMol component molar mass in kg/mol
   * @param tempK temperature in K
   * @param pressureBara pressure in bara
   * @return concentration in kg/m³
   */
  public static double ppmToKgPerM3(double ppm, double molarMassKgPerMol, double tempK,
      double pressureBara) {
    double R = 8.314;
    double pPa = pressureBara * 1.0e5;
    // n/V = p/(RT); concentration kg/m3 = (ppm/1e6) * MW * p/(R T)
    return (ppm / 1.0e6) * molarMassKgPerMol * pPa / (R * tempK);
  }
}
