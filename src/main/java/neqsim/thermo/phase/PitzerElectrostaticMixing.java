package neqsim.thermo.phase;

/**
 * Higher-order electrostatic mixing term for unequal-charge ions in the Pitzer model.
 *
 * <p>
 * Implements the Chebyshev approximation used by PHRQPITZ and PHREEQC for Pitzer's nonsymmetric mixing terms
 * {@code Etheta} and {@code d(Etheta)/dI}. The term is zero for equal-charge pairs and contains no fitted interaction
 * parameter.
 * </p>
 */
public final class PitzerElectrostaticMixing {
  /** Per-thread recurrence workspace: six result slots plus two 22-term recurrences. */
  private static final ThreadLocal<double[]> WORKSPACE = new ThreadLocal<double[]>() {
    @Override
    protected double[] initialValue() {
      return new double[50];
    }
  };
  /** Chebyshev coefficients from the public-domain PHREEQC 3 implementation. */
  private static final double[] COEFFICIENTS = { 1.925154014814667, -0.060076477753119, -0.029779077456514,
      -0.007299499690937, 0.000388260636404, 0.000636874599598, 0.000036583601823, -0.000045036975204,
      -0.000004537895710, 0.000002937706971, 0.000000396566462, -0.000000202099617, -0.000000025267769,
      0.000000013522610, 0.000000001229405, -0.000000000821969, -0.000000000050847, 0.000000000046333,
      0.000000000001943, -0.000000000002563, -0.000000000010991, 0.628023320520852, 0.462762985338493,
      0.150044637187895, -0.028796057604906, -0.036552745910311, -0.001668087945272, 0.006519840398744,
      0.001130378079086, -0.000887171310131, -0.000242107641309, 0.000087294451594, 0.000034682122751,
      -0.000004583768938, -0.000003548684306, -0.000000250453880, 0.000000216991779, 0.000000080779570,
      0.000000004558555, -0.000000006944757, -0.000000002849257, 0.000000000237816 };

  private PitzerElectrostaticMixing() {
  }

  /**
   * Calculate the nonsymmetric electrostatic mixing term and its ionic-strength derivative.
   *
   * @param chargeJ signed charge of the first same-sign ion
   * @param chargeK signed charge of the second same-sign ion
   * @param ionicStrength molal ionic strength in mol/kg
   * @param aPhi Pitzer Debye-Huckel osmotic coefficient parameter
   * @param result caller-owned array receiving {@code Etheta} at index 0 and {@code d(Etheta)/dI} at index 1
   */
  public static void calculate(double chargeJ, double chargeK, double ionicStrength, double aPhi, double[] result) {
    if (result == null || result.length < 2) {
      throw new IllegalArgumentException("Etheta result array must contain at least two entries");
    }
    if (!Double.isFinite(chargeJ) || !Double.isFinite(chargeK) || chargeJ * chargeK <= 0.0) {
      throw new IllegalArgumentException("Etheta requires two finite same-sign ionic charges");
    }
    if (!Double.isFinite(ionicStrength) || ionicStrength < 0.0 || !Double.isFinite(aPhi) || aPhi < 0.0) {
      throw new IllegalArgumentException("Etheta requires finite non-negative ionic strength and Aphi");
    }
    result[0] = 0.0;
    result[1] = 0.0;
    if (Math.abs(chargeJ - chargeK) < 1.0e-12 || ionicStrength == 0.0 || aPhi == 0.0) {
      return;
    }

    double xConstant = 6.0 * aPhi * Math.sqrt(ionicStrength);
    double chargeProduct = chargeJ * chargeK;
    double[] workspace = WORKSPACE.get();
    evaluateIntegral(xConstant * chargeProduct, workspace, 0);
    evaluateIntegral(xConstant * chargeJ * chargeJ, workspace, 2);
    evaluateIntegral(xConstant * chargeK * chargeK, workspace, 4);

    result[0] = chargeProduct * (workspace[0] - 0.5 * workspace[2] - 0.5 * workspace[4]) / (4.0 * ionicStrength);
    result[1] = chargeProduct * (workspace[1] - 0.5 * workspace[3] - 0.5 * workspace[5])
        / (8.0 * ionicStrength * ionicStrength) - result[0] / ionicStrength;
  }

  /**
   * Evaluate the Pitzer J integral and its scaled derivative.
   *
   * @param x positive electrostatic argument
   * @param workspace per-thread recurrence workspace
   * @param resultOffset result index for J; J-prime is written to the following index
   */
  private static void evaluateIntegral(double x, double[] workspace, int resultOffset) {
    double transformed;
    double derivativeScale;
    int offset;
    if (x <= 1.0) {
      double power = Math.pow(x, 0.2);
      transformed = 4.0 * power - 2.0;
      derivativeScale = 0.4 * power;
      offset = 0;
    } else {
      double power = Math.pow(x, -0.1);
      transformed = (40.0 * power - 22.0) / 9.0;
      derivativeScale = -2.0 * power / 9.0;
      offset = 21;
    }

    int bOffset = 6;
    int dOffset = 28;
    workspace[bOffset + 21] = 0.0;
    workspace[dOffset + 20] = 0.0;
    workspace[dOffset + 21] = 0.0;
    workspace[bOffset + 20] = COEFFICIENTS[offset + 20];
    workspace[bOffset + 19] = transformed * COEFFICIENTS[offset + 20] + COEFFICIENTS[offset + 19];
    workspace[dOffset + 19] = COEFFICIENTS[offset + 20];
    for (int i = 18; i >= 0; i--) {
      workspace[bOffset + i] = transformed * workspace[bOffset + i + 1] - workspace[bOffset + i + 2]
          + COEFFICIENTS[offset + i];
      workspace[dOffset + i] = workspace[bOffset + i + 1] + transformed * workspace[dOffset + i + 1]
          - workspace[dOffset + i + 2];
    }
    workspace[resultOffset] = x / 4.0 - 1.0 + 0.5 * (workspace[bOffset] - workspace[bOffset + 2]);
    workspace[resultOffset + 1] = x / 4.0 + derivativeScale * (workspace[dOffset] - workspace[dOffset + 2]);
  }
}
