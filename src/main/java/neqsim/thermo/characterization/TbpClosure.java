package neqsim.thermo.characterization;

import neqsim.util.exception.InvalidInputException;

/**
 * Correlation used to close an under-determined TBP fraction definition.
 *
 * <p>
 * A pseudo-component is fully defined by its molar mass, specific gravity and normal boiling point, but only two of the
 * three are usually measured. A <code>TbpClosure</code> supplies the missing third property. Which closure was used
 * materially changes the resulting critical properties, so it is recorded on the fraction rather than left implicit.
 * </p>
 *
 * <p>
 * Units throughout this enum: boiling point in K, molar mass in kg/mol, density as specific gravity (relative density,
 * numerically equal to g/cm3).
 * </p>
 *
 * <p>
 * <b>Not every closure can be evaluated in both directions.</b> Solving for molar mass from (boiling point, specific
 * gravity) is well posed for all four. Solving for specific gravity from (boiling point, molar mass) requires the
 * correlation to be monotonic in specific gravity, and measurement over 0.60 to 1.00 specific gravity and 350 to 600 K
 * shows that only {@link #RIAZI_DAUBERT_1980} is. {@link #supportsDensityFromMolarMass()} reports this, and the
 * unsupported closures throw rather than return an arbitrary root.
 * </p>
 *
 * <p>
 * Accuracy against the pure components in COMP.csv, as absolute average deviation in molar mass predicted from boiling
 * point and specific gravity (see <code>devtools/validate_tbp_closures.py</code>):
 * </p>
 *
 * <table>
 * <caption>Absolute average deviation in predicted molar mass</caption>
 * <tr>
 * <th>Closure</th>
 * <th>Paraffins</th>
 * <th>Aromatics</th>
 * </tr>
 * <tr>
 * <td>RIAZI_DAUBERT_1987</td>
 * <td>1.7 %</td>
 * <td>7.8 %</td>
 * </tr>
 * <tr>
 * <td>SOREIDE</td>
 * <td>3.0 %</td>
 * <td>9.1 %</td>
 * </tr>
 * <tr>
 * <td>RIAZI_DAUBERT_1980</td>
 * <td>5.4 %</td>
 * <td>13.6 %</td>
 * </tr>
 * </table>
 *
 * <p>
 * References: Riazi, M.R. and Daubert, T.E., Hydrocarbon Processing 59(3), 115 (1980); Riazi, M.R. and Daubert, T.E.,
 * Ind. Eng. Chem. Res. 26, 755 (1987); Soreide, I., Improved Phase Behavior Predictions of Petroleum Reservoir Fluids
 * from a Cubic Equation of State, Dr.Ing. thesis, NTH (1989).
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public enum TbpClosure {
  /**
   * Riazi-Daubert (1980), <code>M = 4.5673e-5 * Tb^2.1962 * SG^-1.0164</code> with Tb in degrees Rankine and M in
   * g/mol. The only closure here that inverts analytically for specific gravity, which is why it is the default
   * wherever specific gravity is the unknown.
   */
  RIAZI_DAUBERT_1980 {
    /** {@inheritDoc} */
    @Override
    public double calcMolarMass(double boilingPoint, double density, TBPModelInterface model) {
      double boilingPointRankine = boilingPoint * 1.8;
      return 4.5673e-5 * Math.pow(boilingPointRankine, 2.1962) * Math.pow(density, -1.0164) / 1000.0;
    }

    /** {@inheritDoc} */
    @Override
    public double calcDensity(double boilingPoint, double molarMass, TBPModelInterface model) {
      double molarMassGmol = molarMass * 1000.0;
      double boilingPointRankine = boilingPoint * 1.8;
      return Math.pow(4.5673e-5 * Math.pow(boilingPointRankine, 2.1962) / molarMassGmol, -1.0 / -1.0164);
    }

    /** {@inheritDoc} */
    @Override
    public boolean supportsDensityFromMolarMass() {
      return true;
    }
  },

  /**
   * Riazi-Daubert (1987) extended form,
   * <code>M = 42.965 * exp(2.097e-4*Tb - 7.78712*SG + 2.08476e-3*Tb*SG) * Tb^1.26007 *
   * SG^4.98308</code> with Tb in K and M in g/mol. The most accurate of the four for molar mass, but not monotonic in
   * specific gravity, so it cannot be inverted for density.
   */
  RIAZI_DAUBERT_1987 {
    /** {@inheritDoc} */
    @Override
    public double calcMolarMass(double boilingPoint, double density, TBPModelInterface model) {
      double molarMassGmol = 42.965
          * Math.exp(2.097e-4 * boilingPoint - 7.78712 * density + 2.08476e-3 * boilingPoint * density)
          * Math.pow(boilingPoint, 1.26007) * Math.pow(density, 4.98308);
      return molarMassGmol / 1000.0;
    }

    /** {@inheritDoc} */
    @Override
    public double calcDensity(double boilingPoint, double molarMass, TBPModelInterface model) {
      throw new RuntimeException(new InvalidInputException("TbpClosure", "calcDensity", "molarMass",
          "cannot be inverted with RIAZI_DAUBERT_1987: molar mass turns over in specific gravity at " + "SG = "
              + (4.98308 / (7.78712 - 2.08476e-3 * boilingPoint)) + " for this boiling point, "
              + "so two specific gravities reproduce the same molar mass and the inverse has no unique "
              + "root. Use RIAZI_DAUBERT_1980 instead. Evaluating this closure forward, i.e. molar mass "
              + "from boiling point and specific gravity, is unaffected and remains the default."));
    }

    /** {@inheritDoc} */
    @Override
    public boolean supportsDensityFromMolarMass() {
      return false;
    }
  },

  /**
   * Soreide (1989), the boiling point correlation used by <code>PedersenTBPModelPR2.calcTB</code>. Inverted numerically
   * for molar mass. Not monotonic in specific gravity, so it cannot be inverted for density.
   */
  SOREIDE {
    /** {@inheritDoc} */
    @Override
    public double calcMolarMass(double boilingPoint, double density, TBPModelInterface model) {
      return solveMolarMass(this, boilingPoint, density, null);
    }

    /** {@inheritDoc} */
    @Override
    public double calcDensity(double boilingPoint, double molarMass, TBPModelInterface model) {
      throw new RuntimeException(new InvalidInputException("TbpClosure", "calcDensity", "molarMass",
          "cannot be inverted with SOREIDE: the boiling point is not monotonic in specific gravity "
              + "over 0.60 to 1.00, so the inverse has no unique root. Use RIAZI_DAUBERT_1980 instead."));
    }

    /** {@inheritDoc} */
    @Override
    public boolean supportsDensityFromMolarMass() {
      return false;
    }
  },

  /**
   * The boiling point correlation of the fluid's own selected TBP characterization model, inverted numerically for
   * molar mass. Use this to keep a fraction consistent with the rest of the characterization rather than with an
   * external correlation.
   *
   * <p>
   * Note that the Pedersen models carry no density dependence in <code>calcTB</code> below 540 g/mol, so for those the
   * returned molar mass is a function of boiling point alone.
   * </p>
   */
  TBP_MODEL {
    /** {@inheritDoc} */
    @Override
    public double calcMolarMass(double boilingPoint, double density, TBPModelInterface model) {
      if (model == null) {
        throw new RuntimeException(new InvalidInputException("TbpClosure", "calcMolarMass", "model",
            "must not be null for the TBP_MODEL closure."));
      }
      return solveMolarMass(this, boilingPoint, density, model);
    }

    /** {@inheritDoc} */
    @Override
    public double calcDensity(double boilingPoint, double molarMass, TBPModelInterface model) {
      throw new RuntimeException(new InvalidInputException("TbpClosure", "calcDensity", "molarMass",
          "cannot be inverted with TBP_MODEL: the Pedersen boiling point correlation is independent "
              + "of density below 540 g/mol, so the inverse problem has no solution. "
              + "Use RIAZI_DAUBERT_1980 instead."));
    }

    /** {@inheritDoc} */
    @Override
    public boolean supportsDensityFromMolarMass() {
      return false;
    }
  };

  /** Lower bound of the molar mass bracket used by the numerical inversions, kg/mol. */
  private static final double MOLAR_MASS_SEARCH_LOWER = 0.010;
  /** Upper bound of the molar mass bracket used by the numerical inversions, kg/mol. */
  private static final double MOLAR_MASS_SEARCH_UPPER = 0.800;

  /**
   * Molar mass of a fraction with the given normal boiling point and specific gravity.
   *
   * @param boilingPoint normal boiling point in K
   * @param density specific gravity (relative density, g/cm3)
   * @param model the fluid's TBP characterization model; used only by {@link #TBP_MODEL} and may be null for the others
   * @return molar mass in kg/mol
   */
  public abstract double calcMolarMass(double boilingPoint, double density, TBPModelInterface model);

  /**
   * Specific gravity of a fraction with the given normal boiling point and molar mass.
   *
   * @param boilingPoint normal boiling point in K
   * @param molarMass molar mass in kg/mol
   * @param model the fluid's TBP characterization model; may be null
   * @return specific gravity (relative density, g/cm3)
   * @throws RuntimeException wrapping an {@link neqsim.util.exception.InvalidInputException} when this closure is not
   * invertible for specific gravity, as reported by {@link #supportsDensityFromMolarMass()}
   */
  public abstract double calcDensity(double boilingPoint, double molarMass, TBPModelInterface model);

  /**
   * Whether {@link #calcDensity(double, double, TBPModelInterface)} is available for this closure.
   *
   * @return true when the correlation is monotonic in specific gravity and can be inverted
   */
  public abstract boolean supportsDensityFromMolarMass();

  /**
   * Boiling point predicted by the Soreide (1989) correlation.
   *
   * <p>
   * Kept as a static helper rather than an instance method because it is the forward form that the {@link #SOREIDE}
   * inversion brackets against, and it is also useful for verification.
   * </p>
   *
   * @param molarMass molar mass in kg/mol
   * @param density specific gravity (relative density, g/cm3)
   * @return normal boiling point in K
   */
  public static double calcBoilingPointSoreide(double molarMass, double density) {
    double molarMassGmol = molarMass * 1000.0;
    double boilingPointRankine = 1928.3 - 1.695e5 * Math.pow(molarMassGmol, -0.03522) * Math.pow(density, 3.266)
        * Math.exp(-4.922e-3 * molarMassGmol - 4.7685 * density + 3.462e-3 * molarMassGmol * density);
    return boilingPointRankine / 1.8;
  }

  /**
   * Boiling point used by a closure when inverting for molar mass.
   *
   * @param closure the closure whose forward correlation is wanted
   * @param molarMass molar mass in kg/mol
   * @param density specific gravity (relative density, g/cm3)
   * @param model TBP characterization model, required for {@link #TBP_MODEL}
   * @return normal boiling point in K
   */
  private static double forwardBoilingPoint(TbpClosure closure, double molarMass, double density,
      TBPModelInterface model) {
    if (closure == TBP_MODEL) {
      return model.calcTB(molarMass * 1000.0, density);
    }
    return calcBoilingPointSoreide(molarMass, density);
  }

  /**
   * Invert a boiling point correlation for molar mass by bisection.
   *
   * <p>
   * Bisection rather than Newton because the bracket is known and narrow, and because a bracketed method cannot wander
   * into the non-physical molar masses that the Soreide exponential produces outside the range.
   * </p>
   *
   * @param closure the closure being inverted
   * @param boilingPoint target normal boiling point in K
   * @param density specific gravity (relative density, g/cm3)
   * @param model TBP characterization model, required for {@link #TBP_MODEL}
   * @return molar mass in kg/mol
   * @throws RuntimeException wrapping an {@link neqsim.util.exception.InvalidInputException} when the boiling point is
   * not attainable anywhere in the search bracket
   */
  private static double solveMolarMass(TbpClosure closure, double boilingPoint, double density,
      TBPModelInterface model) {
    double lower = MOLAR_MASS_SEARCH_LOWER;
    double upper = MOLAR_MASS_SEARCH_UPPER;
    double fLower = forwardBoilingPoint(closure, lower, density, model) - boilingPoint;
    double fUpper = forwardBoilingPoint(closure, upper, density, model) - boilingPoint;

    if (fLower * fUpper > 0.0) {
      throw new RuntimeException(new InvalidInputException("TbpClosure", "calcMolarMass", "boilingPoint",
          boilingPoint + " K is not attainable with closure " + closure + " at specific gravity " + density
              + ". Attainable range is " + (fLower + boilingPoint) + " to " + (fUpper + boilingPoint)
              + " K for molar mass " + (MOLAR_MASS_SEARCH_LOWER * 1000.0) + " to " + (MOLAR_MASS_SEARCH_UPPER * 1000.0)
              + " g/mol."));
    }

    double tolerance = 1e-9;
    double molarMass = 0.5 * (lower + upper);
    for (int i = 0; i < 200 && (upper - lower) > tolerance; i++) {
      molarMass = 0.5 * (lower + upper);
      double fMid = forwardBoilingPoint(closure, molarMass, density, model) - boilingPoint;
      if (fMid == 0.0) {
        return molarMass;
      }
      if (fLower * fMid < 0.0) {
        upper = molarMass;
      } else {
        lower = molarMass;
        fLower = fMid;
      }
    }
    return 0.5 * (lower + upper);
  }
}
