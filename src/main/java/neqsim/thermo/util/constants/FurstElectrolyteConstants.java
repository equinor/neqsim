/*
 * FurstElectrolyteConstants.java
 *
 * Created on 29. oktober 2001, 14:08
 */

package neqsim.thermo.util.constants;

/**
 * <p>
 * FurstElectrolyteConstants class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public final class FurstElectrolyteConstants implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  // public static double[] furstParams = {0.0982e-6, 7.003e-6, 77.22e-6,
  // -25.314e-6, -0.05813e-6, -44.383e-6};
  /** Constant <code>furstParams</code>. */
  public static double[] furstParams =
      {0.0000001117, 0.0000053771, 0.0000699219, 0.0000043984, -0.0000000606, -0.0000217950};
  // public static double[] furstParams = {0.0000001018, 0.0000064366,
  // 0.0001103447, -0.0001631115, -0.0000000565, -0.0000565052};
  // public static double[] furstParams = {0.10688e-6, 6.5665e-6, 35.09e-6,
  // 6.004e-6, -0.04304e-6, -27.510e-6};
  // public static double[] furstParams = {8.806E-8, 6.905E-6, 2.064E-5, 2.285E-4,
  // -5.531E-8, -4.321E-5};
  // public static double[] furstParams = {8.806E-8, 6.905E-6, 35.09e-6, 6.004e-6,
  // -5.531E-8, -4.321E-5};
  // public static double[] furstParams = {8.717E-8, 8.309E-6, 2.435E-5,
  // 2.024E-4, -5.757E-8, -5.693E-5};
  // public static double[] furstParams = {9.8647e-8, 6.9638e-6, 7.713e-5,
  // -2.501e-5, -5.813E-8, -4.447E-5}; //{9.8647e-8, 6.9638e-6, 7.713E-5,
  // -2.501E-5, -5.813E-8, -4.447E-5};
  // public static double[] furstParams = {9.463E-8, 6.694E-6, -1.021E-5,
  // 4.137E-4, -5.172E-8, -5.832E-5};
  // public static double[] furstParamsCPA = {0.0000000752, 0.0000037242,
  // 0.0000250998, 0.0000198635, -0.0000000311, -0.0000006630}; // before fitting
  // 2015
  /** Constant <code>furstParamsCPA</code>. */
  public static double[] furstParamsCPA =
      {0.00000014880379801585537, 0.000005016259143319152, 0.00004614450758742748,
          -0.00006428039395924042, -0.000000039695971380410286, -0.000021035816766450363};
  /** Constant <code>furstParamsCPA_MDEA</code>. */
  public static double[] furstParamsCPA_MDEA =
      {0.0000000752, 0.0000037242, -0.0004725836, 0.0026038239, -0.0000002479, -0.0000082501};

  // 0.0000001880, 0.0000014139, 0.0000284666, 0.0000389043, -0.0000000451,
  // 0.0000088136

  /**
   * Dummy constructor, not for use. Class is to be considered static.
   */
  private FurstElectrolyteConstants() {}

  /**
   * <p>
   * setFurstParam.
   * </p>
   *
   * @param i a int
   * @param value a double
   */
  public static void setFurstParam(int i, double value) {
    furstParams[i] = value;
  }

  /**
   * <p>
   * getFurstParam.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public static double getFurstParam(int i) {
    return furstParams[i];
  }

  /**
   * <p>
   * getFurstParamMDEA.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public static double getFurstParamMDEA(int i) {
    return furstParamsCPA_MDEA[i];
  }

  /**
   * <p>
   * Setter for the field <code>furstParams</code>.
   * </p>
   *
   * @param type a {@link java.lang.String} object
   */
  public static void setFurstParams(String type) {
    if (type.equalsIgnoreCase("electrolyteCPA")) {
      furstParams = furstParamsCPA;
    }
  }
}
