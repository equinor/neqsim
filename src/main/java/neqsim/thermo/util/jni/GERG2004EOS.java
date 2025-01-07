/*
 * GERG2004EOS.java
 *
 * Created on 19. september 2006, 12:18
 */

package neqsim.thermo.util.jni;

import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * GERG2004EOS class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class GERG2004EOS {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * POTDX.
   * </p>
   *
   * @param c1 a double
   * @param c2 a double
   * @param c3 a double
   * @param c4 a double
   * @param c5 a double
   * @param c6 a double
   * @param c7 a double
   * @param c8 a double
   * @param c9 a double
   * @param c10 a double
   * @param c11 a double
   * @param c12 a double
   * @param c13 a double
   * @param c14 a double
   * @param c15 a double
   * @param c16 a double
   * @param c17 a double
   * @param c18 a double
   * @param c19 a double
   * @param c20 a double
   * @return a double
   */
  public static native double POTDX(double c1, double c2, double c3, double c4, double c5,
      double c6, double c7, double c8, double c9, double c10, double c11, double c12, double c13,
      double c14, double c15, double c16, double c17, double c18, double c19, double c20);

  /**
   * <p>
   * ZOTPX.
   * </p>
   *
   * @param c1 a double
   * @param c2 a double
   * @param c3 a double
   * @param c4 a double
   * @param c5 a double
   * @param c6 a double
   * @param c7 a double
   * @param c8 a double
   * @param c9 a double
   * @param c10 a double
   * @param c11 a double
   * @param c12 a double
   * @param c13 a double
   * @param c14 a double
   * @param c15 a double
   * @param c16 a double
   * @param c17 a double
   * @param c18 a double
   * @param c19 a double
   * @param c20 a double
   * @param IPHASE a int
   * @return a double
   */
  public static native double ZOTPX(double c1, double c2, double c3, double c4, double c5,
      double c6, double c7, double c8, double c9, double c10, double c11, double c12, double c13,
      double c14, double c15, double c16, double c17, double c18, double c19, double c20,
      int IPHASE);

  /**
   * <p>
   * HOTPX.
   * </p>
   *
   * @param c1 a double
   * @param c2 a double
   * @param c3 a double
   * @param c4 a double
   * @param c5 a double
   * @param c6 a double
   * @param c7 a double
   * @param c8 a double
   * @param c9 a double
   * @param c10 a double
   * @param c11 a double
   * @param c12 a double
   * @param c13 a double
   * @param c14 a double
   * @param c15 a double
   * @param c16 a double
   * @param c17 a double
   * @param c18 a double
   * @param c19 a double
   * @param c20 a double
   * @param IPHASE a int
   * @return a double
   */
  public static native double HOTPX(double c1, double c2, double c3, double c4, double c5,
      double c6, double c7, double c8, double c9, double c10, double c11, double c12, double c13,
      double c14, double c15, double c16, double c17, double c18, double c19, double c20,
      int IPHASE);

  /**
   * <p>
   * SOTPX.
   * </p>
   *
   * @param c1 a double
   * @param c2 a double
   * @param c3 a double
   * @param c4 a double
   * @param c5 a double
   * @param c6 a double
   * @param c7 a double
   * @param c8 a double
   * @param c9 a double
   * @param c10 a double
   * @param c11 a double
   * @param c12 a double
   * @param c13 a double
   * @param c14 a double
   * @param c15 a double
   * @param c16 a double
   * @param c17 a double
   * @param c18 a double
   * @param c19 a double
   * @param c20 a double
   * @param IPHASE a int
   * @return a double
   */
  public static native double SOTPX(double c1, double c2, double c3, double c4, double c5,
      double c6, double c7, double c8, double c9, double c10, double c11, double c12, double c13,
      double c14, double c15, double c16, double c17, double c18, double c19, double c20,
      int IPHASE);

  /**
   * <p>
   * CPOTPX.
   * </p>
   *
   * @param c1 a double
   * @param c2 a double
   * @param c3 a double
   * @param c4 a double
   * @param c5 a double
   * @param c6 a double
   * @param c7 a double
   * @param c8 a double
   * @param c9 a double
   * @param c10 a double
   * @param c11 a double
   * @param c12 a double
   * @param c13 a double
   * @param c14 a double
   * @param c15 a double
   * @param c16 a double
   * @param c17 a double
   * @param c18 a double
   * @param c19 a double
   * @param c20 a double
   * @param IPHASE a int
   * @return a double
   */
  public static native double CPOTPX(double c1, double c2, double c3, double c4, double c5,
      double c6, double c7, double c8, double c9, double c10, double c11, double c12, double c13,
      double c14, double c15, double c16, double c17, double c18, double c19, double c20,
      int IPHASE);

  /**
   * <p>
   * WOTPX.
   * </p>
   *
   * @param c1 a double
   * @param c2 a double
   * @param c3 a double
   * @param c4 a double
   * @param c5 a double
   * @param c6 a double
   * @param c7 a double
   * @param c8 a double
   * @param c9 a double
   * @param c10 a double
   * @param c11 a double
   * @param c12 a double
   * @param c13 a double
   * @param c14 a double
   * @param c15 a double
   * @param c16 a double
   * @param c17 a double
   * @param c18 a double
   * @param c19 a double
   * @param c20 a double
   * @param IPHASE a int
   * @return a double
   */
  public static native double WOTPX(double c1, double c2, double c3, double c4, double c5,
      double c6, double c7, double c8, double c9, double c10, double c11, double c12, double c13,
      double c14, double c15, double c16, double c17, double c18, double c19, double c20,
      int IPHASE);

  /**
   * <p>
   * RJTOTPX.
   * </p>
   *
   * @param c1 a double
   * @param c2 a double
   * @param c3 a double
   * @param c4 a double
   * @param c5 a double
   * @param c6 a double
   * @param c7 a double
   * @param c8 a double
   * @param c9 a double
   * @param c10 a double
   * @param c11 a double
   * @param c12 a double
   * @param c13 a double
   * @param c14 a double
   * @param c15 a double
   * @param c16 a double
   * @param c17 a double
   * @param c18 a double
   * @param c19 a double
   * @param c20 a double
   * @param IPHASE a int
   * @return a double
   */
  public static native double RJTOTPX(double c1, double c2, double c3, double c4, double c5,
      double c6, double c7, double c8, double c9, double c10, double c11, double c12, double c13,
      double c14, double c15, double c16, double c17, double c18, double c19, double c20,
      int IPHASE);

  /**
   * <p>
   * GOTPX.
   * </p>
   *
   * @param c1 a double
   * @param c2 a double
   * @param c3 a double
   * @param c4 a double
   * @param c5 a double
   * @param c6 a double
   * @param c7 a double
   * @param c8 a double
   * @param c9 a double
   * @param c10 a double
   * @param c11 a double
   * @param c12 a double
   * @param c13 a double
   * @param c14 a double
   * @param c15 a double
   * @param c16 a double
   * @param c17 a double
   * @param c18 a double
   * @param c19 a double
   * @param c20 a double
   * @param IPHASE a int
   * @return a double
   */
  public static native double GOTPX(double c1, double c2, double c3, double c4, double c5,
      double c6, double c7, double c8, double c9, double c10, double c11, double c12, double c13,
      double c14, double c15, double c16, double c17, double c18, double c19, double c20,
      int IPHASE);

  /**
   * <p>
   * UOTPX.
   * </p>
   *
   * @param c1 a double
   * @param c2 a double
   * @param c3 a double
   * @param c4 a double
   * @param c5 a double
   * @param c6 a double
   * @param c7 a double
   * @param c8 a double
   * @param c9 a double
   * @param c10 a double
   * @param c11 a double
   * @param c12 a double
   * @param c13 a double
   * @param c14 a double
   * @param c15 a double
   * @param c16 a double
   * @param c17 a double
   * @param c18 a double
   * @param c19 a double
   * @param c20 a double
   * @param IPHASE a int
   * @return a double
   */
  public static native double UOTPX(double c1, double c2, double c3, double c4, double c5,
      double c6, double c7, double c8, double c9, double c10, double c11, double c12, double c13,
      double c14, double c15, double c16, double c17, double c18, double c19, double c20,
      int IPHASE);

  /**
   * <p>
   * AOTPX.
   * </p>
   *
   * @param c1 a double
   * @param c2 a double
   * @param c3 a double
   * @param c4 a double
   * @param c5 a double
   * @param c6 a double
   * @param c7 a double
   * @param c8 a double
   * @param c9 a double
   * @param c10 a double
   * @param c11 a double
   * @param c12 a double
   * @param c13 a double
   * @param c14 a double
   * @param c15 a double
   * @param c16 a double
   * @param c17 a double
   * @param c18 a double
   * @param c19 a double
   * @param c20 a double
   * @param IPHASE a int
   * @return a double
   */
  public static native double AOTPX(double c1, double c2, double c3, double c4, double c5,
      double c6, double c7, double c8, double c9, double c10, double c11, double c12, double c13,
      double c14, double c15, double c16, double c17, double c18, double c19, double c20,
      int IPHASE);

  /**
   * <p>
   * SFUGOTPX.
   * </p>
   *
   * @param c1 a double
   * @param c2 a double
   * @param c3 a double
   * @param c4 a double
   * @param c5 a double
   * @param c6 a double
   * @param c7 a double
   * @param c8 a double
   * @param c9 a double
   * @param c10 a double
   * @param c11 a double
   * @param c12 a double
   * @param c13 a double
   * @param c14 a double
   * @param c15 a double
   * @param c16 a double
   * @param c17 a double
   * @param c18 a double
   * @param c19 a double
   * @param c20 a double
   * @param IPHASE a int
   * @return an array of type double
   */
  public static native double[] SFUGOTPX(double c1, double c2, double c3, double c4, double c5,
      double c6, double c7, double c8, double c9, double c10, double c11, double c12, double c13,
      double c14, double c15, double c16, double c17, double c18, double c19, double c20,
      int IPHASE);

  /**
   * <p>
   * SPHIOTPX.
   * </p>
   *
   * @param c1 a double
   * @param c2 a double
   * @param c3 a double
   * @param c4 a double
   * @param c5 a double
   * @param c6 a double
   * @param c7 a double
   * @param c8 a double
   * @param c9 a double
   * @param c10 a double
   * @param c11 a double
   * @param c12 a double
   * @param c13 a double
   * @param c14 a double
   * @param c15 a double
   * @param c16 a double
   * @param c17 a double
   * @param c18 a double
   * @param c19 a double
   * @param c20 a double
   * @param IPHASE a int
   * @return an array of type double
   */
  public static native double[] SPHIOTPX(double c1, double c2, double c3, double c4, double c5,
      double c6, double c7, double c8, double c9, double c10, double c11, double c12, double c13,
      double c14, double c15, double c16, double c17, double c18, double c19, double c20,
      int IPHASE);

  /**
   * <p>
   * CVOTPX.
   * </p>
   *
   * @param c1 a double
   * @param c2 a double
   * @param c3 a double
   * @param c4 a double
   * @param c5 a double
   * @param c6 a double
   * @param c7 a double
   * @param c8 a double
   * @param c9 a double
   * @param c10 a double
   * @param c11 a double
   * @param c12 a double
   * @param c13 a double
   * @param c14 a double
   * @param c15 a double
   * @param c16 a double
   * @param c17 a double
   * @param c18 a double
   * @param c19 a double
   * @param c20 a double
   * @param IPHASE a int
   * @return a double
   */
  public static native double CVOTPX(double c1, double c2, double c3, double c4, double c5,
      double c6, double c7, double c8, double c9, double c10, double c11, double c12, double c13,
      double c14, double c15, double c16, double c17, double c18, double c19, double c20,
      int IPHASE);

  /**
   * <p>
   * SALLOTPX.
   * </p>
   *
   * @param c1 a double
   * @param c2 a double
   * @param c3 a double
   * @param c4 a double
   * @param c5 a double
   * @param c6 a double
   * @param c7 a double
   * @param c8 a double
   * @param c9 a double
   * @param c10 a double
   * @param c11 a double
   * @param c12 a double
   * @param c13 a double
   * @param c14 a double
   * @param c15 a double
   * @param c16 a double
   * @param c17 a double
   * @param c18 a double
   * @param c19 a double
   * @param c20 a double
   * @param IPHASE a int
   * @return an array of type double
   */
  public static native double[] SALLOTPX(double c1, double c2, double c3, double c4, double c5,
      double c6, double c7, double c8, double c9, double c10, double c11, double c12, double c13,
      double c14, double c15, double c16, double c17, double c18, double c19, double c20,
      int IPHASE);

  public String[] nameList = {"methane", "nitrogen", "CO2", "ethane", "propane", "n-butane",
      "i-butane", "n-pentane", "i-pentane", "n-hexane", "n-heptane", "n-octane", "hydrogen",
      "oxygen", "CO", "water", "helium", "argon"};

  /**
   * <p>
   * Constructor for GERG2004EOS.
   * </p>
   */
  public GERG2004EOS() {
    // TODO: does not work
    System.loadLibrary("test2");
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @SuppressWarnings("unused")
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    GERG2004EOS gergEOS = new GERG2004EOS();
    double c1 = 298.0, c2 = 0.1, c3 = 0.90, c4 = 0.1, c5 = 0, c6 = 0, c7 = 0, c8 = 0, c9 = 0,
        c10 = 0, c11 = 0, c12 = 0, c13 = 0, c14 = 0, c15 = 0, c16 = 0, c17 = 0, c18 = 0, c19 = 0,
        c20 = 0;
    // double a2 = gergEOS.POTDX (c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12,
    // c13, c14, c15, c16, c17, c18, c19, c20);
    double a4 = GERG2004EOS.SALLOTPX(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14,
        c15, c16, c17, c18, c19, c20, 2)[3];
    double a2 = GERG2004EOS.ZOTPX(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15,
        c16, c17, c18, c19, c20, 2);
    double[] a3 = new double[18];
    // a3 = gergEOS.SPHIOTPX(c1, c2,
    // c3,c4,c5,c6,c7,c8,c9,c10,c11,c12,c13,c14,c15,c16,c17,c18,c19,c20,-2);
    // System.out.println("potdx " + a);
  }

  /**
   * <p>
   * Getter for the field <code>nameList</code>.
   * </p>
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[] getNameList() {
    return nameList;
  }
}
