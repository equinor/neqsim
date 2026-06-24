package neqsim.process.safety.risk.sis.nog070;

import java.io.Serializable;
import com.google.gson.GsonBuilder;

/**
 * Result of a NOG 070 SIL determination for a Safety Instrumented Function.
 *
 * <p>
 * Compares the achieved SIL (derived from a calculated PFD<sub>avg</sub>) against the NOG 070 minimum SIL for the given
 * SIF type and reports whether the design is NOG 070 compliant.
 * </p>
 *
 * <p>
 * The SIL <em>achieved</em> from the PFD is the standard IEC 61511 mapping:
 * </p>
 *
 * <table>
 * <caption>PFD to SIL mapping (IEC 61508/61511)</caption>
 * <tr>
 * <th>PFD<sub>avg</sub> range</th>
 * <th>Achieved SIL</th>
 * </tr>
 * <tr>
 * <td>1.0e-5 &le; PFD &lt; 1.0e-4</td>
 * <td>4</td>
 * </tr>
 * <tr>
 * <td>1.0e-4 &le; PFD &lt; 1.0e-3</td>
 * <td>3</td>
 * </tr>
 * <tr>
 * <td>1.0e-3 &le; PFD &lt; 1.0e-2</td>
 * <td>2</td>
 * </tr>
 * <tr>
 * <td>1.0e-2 &le; PFD &lt; 1.0e-1</td>
 * <td>1</td>
 * </tr>
 * </table>
 *
 * @author ESOL
 * @version 1.0
 */
public class Nog070SilDetermination implements Serializable {

  private static final long serialVersionUID = 1L;

  private final Nog070SifType sifType;
  private final double pfdAvg;
  private final int achievedSil;
  private final int minimumSil;
  private final boolean compliant;
  private final String message;

  /**
   * Constructs a NOG 070 SIL determination result.
   *
   * @param sifType the SIF type catalogued by NOG 070
   * @param pfdAvg the calculated average probability of failure on demand
   * @param achievedSil the SIL derived from PFD per IEC 61508/61511
   * @param minimumSil the NOG 070 minimum SIL for the SIF type
   * @param compliant true when achievedSil &ge; minimumSil
   * @param message human readable summary
   */
  public Nog070SilDetermination(Nog070SifType sifType, double pfdAvg, int achievedSil, int minimumSil,
      boolean compliant, String message) {
    this.sifType = sifType;
    this.pfdAvg = pfdAvg;
    this.achievedSil = achievedSil;
    this.minimumSil = minimumSil;
    this.compliant = compliant;
    this.message = message;
  }

  /**
   * Evaluates a SIF against the NOG 070 minimum SIL.
   *
   * <p>
   * For {@link Nog070SifType#CUSTOM} use {@link #evaluate(Nog070SifType, double, int)} to supply the project-specific
   * minimum.
   * </p>
   *
   * @param sifType the SIF type
   * @param pfdAvg the calculated average probability of failure on demand (dimensionless)
   * @return a {@link Nog070SilDetermination} result
   */
  public static Nog070SilDetermination evaluate(Nog070SifType sifType, double pfdAvg) {
    return evaluate(sifType, pfdAvg, Nog070SilCatalogue.getMinimumSil(sifType));
  }

  /**
   * Evaluates a SIF against an explicit minimum SIL (useful for {@link Nog070SifType#CUSTOM}).
   *
   * @param sifType the SIF type
   * @param pfdAvg the calculated average probability of failure on demand (dimensionless)
   * @param minimumSil project-specific minimum SIL (1-4)
   * @return a {@link Nog070SilDetermination} result
   */
  public static Nog070SilDetermination evaluate(Nog070SifType sifType, double pfdAvg, int minimumSil) {
    if (sifType == null) {
      throw new IllegalArgumentException("sifType must not be null");
    }
    if (Double.isNaN(pfdAvg) || pfdAvg <= 0.0 || pfdAvg >= 1.0) {
      throw new IllegalArgumentException("pfdAvg must be in (0, 1), was " + pfdAvg);
    }
    if (minimumSil < 0 || minimumSil > 4) {
      throw new IllegalArgumentException("minimumSil must be in [0, 4], was " + minimumSil);
    }
    int achieved = pfdToSil(pfdAvg);
    boolean ok = achieved >= minimumSil;
    String msg;
    if (ok) {
      msg = "Compliant: achieved SIL " + achieved + " >= NOG 070 minimum SIL " + minimumSil + " for " + sifType.name();
    } else {
      msg = "NOT compliant: achieved SIL " + achieved + " < NOG 070 minimum SIL " + minimumSil + " for "
          + sifType.name() + ". Reduce PFD by factor " + Math.pow(10.0, minimumSil - achieved) + " or revise SIF.";
    }
    return new Nog070SilDetermination(sifType, pfdAvg, achieved, minimumSil, ok, msg);
  }

  /**
   * Maps a PFD<sub>avg</sub> value to a SIL per IEC 61508 Part 1 Table 3.
   *
   * @param pfdAvg average probability of failure on demand
   * @return SIL (0 if PFD &gt;= 0.1; 1..4 otherwise)
   */
  public static int pfdToSil(double pfdAvg) {
    if (pfdAvg >= 1.0e-1) {
      return 0;
    }
    if (pfdAvg >= 1.0e-2) {
      return 1;
    }
    if (pfdAvg >= 1.0e-3) {
      return 2;
    }
    if (pfdAvg >= 1.0e-4) {
      return 3;
    }
    return 4;
  }

  /**
   * @return SIF type
   */
  public Nog070SifType getSifType() {
    return sifType;
  }

  /**
   * @return calculated average PFD
   */
  public double getPfdAvg() {
    return pfdAvg;
  }

  /**
   * @return SIL achieved per IEC 61508/61511 mapping
   */
  public int getAchievedSil() {
    return achievedSil;
  }

  /**
   * @return NOG 070 minimum SIL for this SIF type
   */
  public int getMinimumSil() {
    return minimumSil;
  }

  /**
   * @return true when achievedSil &ge; minimumSil
   */
  public boolean isCompliant() {
    return compliant;
  }

  /**
   * @return human readable summary
   */
  public String getMessage() {
    return message;
  }

  /**
   * Returns the result as pretty JSON.
   *
   * @return JSON string
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}
