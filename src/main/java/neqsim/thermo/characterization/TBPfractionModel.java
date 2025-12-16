package neqsim.thermo.characterization;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * TBP (True Boiling Point) Fraction Model for petroleum characterization.
 *
 * <p>
 * This class provides multiple correlation models for estimating critical properties (Tc, Pc, ω) of
 * petroleum pseudo-components based on molecular weight and density. These properties are essential
 * for equation of state calculations in process simulation.
 * </p>
 *
 * <h2>Background and Theory</h2>
 * <p>
 * Petroleum fluids contain thousands of individual hydrocarbon components that cannot all be
 * individually characterized. Instead, heavy fractions (typically C7+) are lumped into
 * pseudo-components. TBP models estimate the critical properties needed for equation of state
 * calculations from easily measured properties like molecular weight (MW) and specific gravity
 * (SG).
 * </p>
 *
 * <h2>Available Models</h2>
 * <table border="1" summary="TBP Model Comparison">
 * <tr>
 * <th>Model</th>
 * <th>Best For</th>
 * <th>Key Features</th>
 * <th>Reference</th>
 * </tr>
 * <tr>
 * <td>PedersenSRK</td>
 * <td>General SRK EOS</td>
 * <td>Default, automatic light/heavy switching at MW=1120</td>
 * <td>Pedersen et al. (1984)</td>
 * </tr>
 * <tr>
 * <td>PedersenPR</td>
 * <td>General PR EOS</td>
 * <td>Optimized for Peng-Robinson EOS</td>
 * <td>Pedersen et al. (1984)</td>
 * </tr>
 * <tr>
 * <td>Lee-Kesler</td>
 * <td>General purpose</td>
 * <td>Uses Watson K-factor, wide applicability</td>
 * <td>Kesler &amp; Lee (1976)</td>
 * </tr>
 * <tr>
 * <td>RiaziDaubert</td>
 * <td>Light fractions (MW &lt; 300)</td>
 * <td>Simple exponential form, falls back to Pedersen for heavy</td>
 * <td>Riazi &amp; Daubert (1980)</td>
 * </tr>
 * <tr>
 * <td>Twu</td>
 * <td>Paraffinic fluids (Kw &gt; 12)</td>
 * <td>n-alkane reference with perturbation corrections</td>
 * <td>Twu (1984)</td>
 * </tr>
 * <tr>
 * <td>Cavett</td>
 * <td>Refining industry</td>
 * <td>API gravity corrections, hybrid Lee-Kesler approach</td>
 * <td>Cavett (1962)</td>
 * </tr>
 * <tr>
 * <td>Standing</td>
 * <td>Reservoir engineering</td>
 * <td>Simple power-law, quick estimates</td>
 * <td>Standing (1977)</td>
 * </tr>
 * </table>
 *
 * <h2>Key Correlations</h2>
 *
 * <h3>Critical Temperature</h3>
 * <p>
 * <b>Pedersen:</b> T<sub>c</sub> = a<sub>0</sub>·ρ + a<sub>1</sub>·ln(M) + a<sub>2</sub>·M +
 * a<sub>3</sub>/M
 * </p>
 * <p>
 * <b>Lee-Kesler:</b> T<sub>c</sub> = 189.8 + 450.6·SG + (0.4244 + 0.1174·SG)·T<sub>b</sub> +
 * (0.1441 - 1.0069·SG)·10<sup>5</sup>/T<sub>b</sub>
 * </p>
 * <p>
 * <b>Riazi-Daubert:</b> T<sub>c</sub> = (5/9)·554.4·exp(-1.3478×10<sup>-4</sup>·M -
 * 0.61641·SG)·M<sup>0.2998</sup>·SG<sup>1.0555</sup>
 * </p>
 *
 * <h3>Critical Pressure</h3>
 * <p>
 * <b>Pedersen:</b> P<sub>c</sub> = exp(b<sub>0</sub> + b<sub>1</sub>·ρ<sup>b<sub>4</sub></sup> +
 * b<sub>2</sub>/M + b<sub>3</sub>/M²)
 * </p>
 * <p>
 * <b>Lee-Kesler:</b> ln(P<sub>c</sub>) = 3.3864 - 0.0566/SG - f(T<sub>b</sub>, SG)
 * </p>
 *
 * <h3>Acentric Factor</h3>
 * <p>
 * <b>Edmister:</b> ω =
 * (3/7)·log<sub>10</sub>(P<sub>c</sub>/P<sub>ref</sub>)/(T<sub>c</sub>/T<sub>b</sub> - 1) - 1
 * </p>
 * <p>
 * <b>Kesler-Lee (T<sub>br</sub> &lt; 0.8):</b> ω = (ln(P<sub>br</sub>) - 5.92714 +
 * 6.09649/T<sub>br</sub> + ...)/(15.2518 - ...)
 * </p>
 *
 * <h3>Watson Characterization Factor</h3>
 * <p>
 * K<sub>w</sub> = (1.8·T<sub>b</sub>)<sup>1/3</sup>/SG
 * </p>
 * <p>
 * Used to characterize fluid type:
 * </p>
 * <ul>
 * <li>K<sub>w</sub> &gt; 12.5: Paraffinic (gas condensates)</li>
 * <li>K<sub>w</sub> 11.5-12.5: Mixed/intermediate</li>
 * <li>K<sub>w</sub> 10.5-11.5: Naphthenic</li>
 * <li>K<sub>w</sub> &lt; 10.5: Aromatic</li>
 * </ul>
 *
 * <h2>Model Selection Guidelines</h2>
 * <ul>
 * <li><b>Default choice:</b> PedersenSRK (for SRK EOS) or PedersenPR (for PR EOS)</li>
 * <li><b>Gas condensates:</b> Twu model (K<sub>w</sub> &gt; 12)</li>
 * <li><b>Heavy oils (MW &gt; 500):</b> PedersenSRKHeavyOil or PedersenPRHeavyOil</li>
 * <li><b>Light fractions (MW &lt; 300):</b> RiaziDaubert or Lee-Kesler</li>
 * <li><b>Reservoir engineering:</b> Standing (simple, widely used)</li>
 * <li><b>Refining applications:</b> Cavett (API gravity-based)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>
 * {@code
 * // Create a fluid with SRK equation of state
 * SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
 *
 * // Set the TBP model (optional, defaults to PedersenSRK for SRK EOS)
 * fluid.getCharacterization().setTBPModel("PedersenSRK");
 *
 * // Add a TBP fraction: name, moles, MW (kg/mol), density (g/cm³)
 * fluid.addTBPfraction("C7", 1.0, 0.092, 0.73); // C7 fraction
 * fluid.addTBPfraction("C10", 0.5, 0.142, 0.78); // C10 fraction
 *
 * // The model automatically calculates Tc, Pc, ω for each fraction
 * System.out.println("C7 Tc = " + fluid.getComponent("C7_PC").getTC() + " K");
 * System.out.println("C7 Pc = " + fluid.getComponent("C7_PC").getPC() + " bar");
 *
 * // Get model recommendation based on fluid properties
 * TBPfractionModel model = new TBPfractionModel();
 * String recommended = model.recommendTBPModel(0.200, 0.85, "SRK");
 * }
 * </pre>
 *
 * <h2>Typical Property Ranges</h2>
 * <table border="1" summary="Typical Property Ranges">
 * <tr>
 * <th>Component</th>
 * <th>MW (g/mol)</th>
 * <th>SG</th>
 * <th>T<sub>c</sub> (K)</th>
 * <th>P<sub>c</sub> (bar)</th>
 * <th>ω</th>
 * </tr>
 * <tr>
 * <td>C7</td>
 * <td>96-100</td>
 * <td>0.72-0.74</td>
 * <td>540-560</td>
 * <td>27-30</td>
 * <td>0.30-0.35</td>
 * </tr>
 * <tr>
 * <td>C10</td>
 * <td>134-142</td>
 * <td>0.76-0.79</td>
 * <td>600-640</td>
 * <td>20-25</td>
 * <td>0.45-0.55</td>
 * </tr>
 * <tr>
 * <td>C20</td>
 * <td>275-285</td>
 * <td>0.85-0.87</td>
 * <td>750-800</td>
 * <td>12-15</td>
 * <td>0.85-0.95</td>
 * </tr>
 * </table>
 *
 * <h2>References</h2>
 * <ol>
 * <li>Pedersen, K.S., Thomassen, P., Fredenslund, A. (1984). "Thermodynamics of Petroleum Mixtures
 * Containing Heavy Hydrocarbons." Ind. Eng. Chem. Process Des. Dev., 23, 566-573.</li>
 * <li>Kesler, M.G., Lee, B.I. (1976). "Improve Prediction of Enthalpy of Fractions." Hydrocarbon
 * Processing, 55(3), 153-158.</li>
 * <li>Riazi, M.R., Daubert, T.E. (1980). "Simplify Property Predictions." Hydrocarbon Processing,
 * 59(3), 115-116.</li>
 * <li>Twu, C.H. (1984). "An Internally Consistent Correlation for Predicting the Critical
 * Properties..." Fluid Phase Equilibria, 16, 137-150.</li>
 * <li>Cavett, R.H. (1962). "Physical Data for Distillation Calculations." Proc. 27th API Meeting,
 * San Francisco.</li>
 * <li>Standing, M.B. (1977). "Volumetric and Phase Behavior of Oil Field Hydrocarbon Systems." SPE,
 * Dallas.</li>
 * </ol>
 *
 * @author ESOL
 * @version $Id: $Id
 * @see neqsim.thermo.characterization.Characterise
 * @see neqsim.thermo.characterization.PlusFractionModel
 */
public class TBPfractionModel implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TBPfractionModel.class);

  String name = "";

  /**
   * <p>
   * Constructor for TBPfractionModel.
   * </p>
   */
  public TBPfractionModel() {}

  /**
   * Abstract base class for TBP property estimation models.
   *
   * <p>
   * This class provides default implementations for common property calculations including acentric
   * factor (Edmister and Kesler-Lee methods), critical volume, parachor parameter, and critical
   * viscosity. Subclasses must implement the specific correlations for critical temperature (Tc)
   * and critical pressure (Pc).
   * </p>
   *
   * <p>
   * Key methods that subclasses typically override:
   * <ul>
   * <li>{@link #calcTC(double, double)} - Critical temperature</li>
   * <li>{@link #calcPC(double, double)} - Critical pressure</li>
   * <li>{@link #calcTB(double, double)} - Boiling temperature</li>
   * <li>{@link #calcm(double, double)} - EOS m-parameter (for Pedersen models)</li>
   * </ul>
   * </p>
   */
  public abstract class TBPBaseModel implements TBPModelInterface, Cloneable, java.io.Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;
    private double boilingPoint = 0.0;
    protected boolean calcm = true;

    public void setBoilingPoint(double boilingPoint) {
      this.boilingPoint = boilingPoint;
    }

    public double getBoilingPoint() {
      return boilingPoint;
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
      return name;
    }

    /** {@inheritDoc} */
    @Override
    public double calcTB(double molarMass, double density) {
      if (getBoilingPoint() > 0.0) {
        return getBoilingPoint();
      }
      return Math.pow((molarMass / 5.805e-5 * Math.pow(density, 0.9371)), 1.0 / 2.3776);
    }

    /** {@inheritDoc} */
    @Override
    public double calcWatsonCharacterizationFactor(double molarMass, double density) {
      // System.out.println("boiling point " + calcTB(molarMass, density));
      return Math.pow(1.8 * calcTB(molarMass, density), 1.0 / 3.0) / density;
    }

    /** {@inheritDoc} */
    @Override
    public double calcAcentricFactorKeslerLee(double molarMass, double density) {
      double TC = calcTC(molarMass, density);
      double TB = calcTB(molarMass, density);
      double PC = calcPC(molarMass, density);
      double TBR = TB / TC;
      double PBR = ThermodynamicConstantsInterface.referencePressure / PC;
      if (TBR < 0.8) {
        return (Math.log(PBR) - 5.92714 + 6.09649 / TBR + 1.28862 * Math.log(TBR)
            - 0.169347 * Math.pow(TBR, 6.0))
            / (15.2518 - 15.6875 / TBR - 13.4721 * Math.log(TBR) + 0.43577 * Math.pow(TBR, 6.0));
      } else {
        double Kw = Math.pow(TB, 1.0 / 3.0) / density;
        return -7.904 + 0.1352 * Kw - 0.007465 * Kw * Kw + 8.359 * TBR
            + (1.408 - 0.01063 * Kw) / TBR;
      }
    }

    /** {@inheritDoc} */
    @Override
    public double calcAcentricFactor(double molarMass, double density) {
      double TC = calcTC(molarMass, density);
      double TB = calcTB(molarMass, density);
      double PC = calcPC(molarMass, density);
      return 3.0 / 7.0 * Math.log10(PC / ThermodynamicConstantsInterface.referencePressure)
          / (TC / TB - 1.0) - 1.0;
    }

    /** {@inheritDoc} */
    @Override
    public double calcCriticalVolume(double molarMass, double density) {
      double TC = calcTC(molarMass, density);
      double PC = calcPC(molarMass, density);
      double acs = calcAcentricFactor(molarMass, density); // thermoSystem.getPhase(thermoSystem.getPhaseIndex(0)).getComponent(0).getAcentricFactor();
      double criticaVol =
          (0.2918 - 0.0928 * acs) * ThermodynamicConstantsInterface.R * TC / PC * 10.0;
      if (criticaVol < 0) {
        // logger.info("acentric factor in calc critVol " + acs);
        criticaVol = (0.2918 - 0.0928) * ThermodynamicConstantsInterface.R * TC / PC * 10.0;
      }
      return criticaVol;
    }

    /** {@inheritDoc} */
    @Override
    public double calcParachorParameter(double molarMass, double density) {
      return 59.3 + 2.34 * molarMass * 1000.0;
    }

    /** {@inheritDoc} */
    @Override
    public double calcCriticalViscosity(double molarMass, double density) {
      double TC = calcTC(molarMass, density);
      double PC = calcPC(molarMass, density);
      return 7.94830 * Math.sqrt(molarMass) * Math.pow(PC, 2.0 / 3.0) / Math.pow(TC, 1.0 / 6.0)
          * 1e-7;
    }

    /** {@inheritDoc} */
    @Override
    public double calcRacketZ(SystemInterface thermoSystem, double molarMass, double density) {
      throw new RuntimeException(
          new neqsim.util.exception.NotImplementedException(this, "calcRacketZ"));
    }

    /** {@inheritDoc} */
    @Override
    public double calcm(double molarMass, double density) {
      throw new RuntimeException(new neqsim.util.exception.NotImplementedException(this, "calcm"));
    }

    /** {@inheritDoc} */
    @Override
    public boolean isCalcm() {
      return calcm;
    }
  }

  /**
   * Pedersen TBP Model for SRK equation of state.
   *
   * <p>
   * This is the default and most widely used TBP model in NeqSim. It implements the correlations
   * from Pedersen et al. (1984) for estimating critical properties of petroleum fractions when
   * using the Soave-Redlich-Kwong (SRK) equation of state.
   * </p>
   *
   * <p>
   * The model automatically switches between light oil and heavy oil coefficient sets at a
   * molecular weight threshold of 1120 g/mol to maintain accuracy across a wide range of petroleum
   * fractions.
   * </p>
   *
   * <h3>Correlations</h3>
   * <ul>
   * <li>Tc = a0·ρ + a1·ln(M) + a2·M + a3/M</li>
   * <li>Pc = exp(b0 + b1·ρ^b4 + b2/M + b3/M²)</li>
   * <li>m = c0 + c1·M + c2·ρ + c3·M²</li>
   * </ul>
   *
   * <p>
   * Reference: Pedersen, K.S., Thomassen, P., and Fredenslund, A. (1984). "Thermodynamics of
   * Petroleum Mixtures Containing Heavy Hydrocarbons." Ind. Eng. Chem. Process Des. Dev., 23,
   * 566-573.
   * </p>
   */
  public class PedersenTBPModelSRK extends TBPBaseModel {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    double[][] TBPfractionCoefOil = {{163.12, 86.052, 0.43475, -1877.4, 0.0},
        {-0.13408, 2.5019, 208.46, -3987.2, 1.0}, {0.7431, 0.0048122, 0.0096707, -3.7184e-6, 0.0}};
    double[][] TBPfractionCoefsHeavyOil = {{8.3063e2, 1.75228e1, 4.55911e-2, -1.13484e4, 0.0},
        {8.02988e-1, 1.78396, 1.56740e2, -6.96559e3, 0.25},
        {-4.7268e-2, 6.02931e-2, 1.21051, -5.76676e-3, 0}};
    double[] TPBracketcoefs = {0.29441, 0.40768};
    double[][] TBPfractionCoefs = null;

    /** {@inheritDoc} */
    @Override
    public double calcTC(double molarMass, double density) {
      // System.out.println("TC ccc " + TBPfractionCoefs[0][0]);
      if (molarMass < 1120) {
        TBPfractionCoefs = TBPfractionCoefOil;
      } else {
        TBPfractionCoefs = TBPfractionCoefsHeavyOil;
      }
      // System.out.println("coef " + TBPfractionCoefs[0][0]);
      return TBPfractionCoefs[0][0] * density + TBPfractionCoefs[0][1] * Math.log(molarMass)
          + TBPfractionCoefs[0][2] * molarMass + TBPfractionCoefs[0][3] / molarMass;
    }

    /** {@inheritDoc} */
    @Override
    public double calcPC(double molarMass, double density) {
      if (molarMass < 1120) {
        TBPfractionCoefs = TBPfractionCoefOil;
      } else {
        TBPfractionCoefs = TBPfractionCoefsHeavyOil;
      }

      return Math.exp(0.01325 + TBPfractionCoefs[1][0]
          + TBPfractionCoefs[1][1] * Math.pow(density, TBPfractionCoefs[1][4])
          + TBPfractionCoefs[1][2] / molarMass + TBPfractionCoefs[1][3] / Math.pow(molarMass, 2.0));
    }

    /** {@inheritDoc} */
    @Override
    public double calcm(double molarMass, double density) {
      if (molarMass < 1120) {
        TBPfractionCoefs = TBPfractionCoefOil;
      } else {
        TBPfractionCoefs = TBPfractionCoefsHeavyOil;
      }
      return TBPfractionCoefs[2][0] + TBPfractionCoefs[2][1] * molarMass
          + TBPfractionCoefs[2][2] * density + TBPfractionCoefs[2][3] * Math.pow(molarMass, 2.0);
    }

    /** {@inheritDoc} */
    @Override
    public double calcTB(double molarMass, double density) {
      if (getBoilingPoint() > 0.0) {
        return getBoilingPoint();
      }
      if (molarMass < 540) {
        return 2E-06 * Math.pow(molarMass, 3) - 0.0035 * Math.pow(molarMass, 2) + 2.4003 * molarMass
            + 171.74;
      } else {
        return 97.58 * Math.pow(molarMass, 0.3323) * Math.pow(density, 0.04609);
      }
    }

    /** {@inheritDoc} */
    @Override
    public double calcRacketZ(SystemInterface thermoSystem, double molarMass, double density) {
      double penelouxC = (thermoSystem.getPhase(0).getMolarVolume() - molarMass / (density * 10.0));
      // System.out.println("peneloux c " + penelouxC);
      double TC = calcTC(molarMass, density);
      // double TB = calcTB(molarMass, density);
      double PC = calcPC(molarMass, density);
      return TPBracketcoefs[0] - penelouxC
          / (TPBracketcoefs[1] * neqsim.thermo.ThermodynamicConstantsInterface.R * TC / PC);
    }
  }

  public class PedersenTBPModelSRKHeavyOil extends PedersenTBPModelSRK {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    double[][] TBPfractionCoefsHeavyOil = {{8.3063e2, 1.75228e1, 4.55911e-2, -1.13484e4, 0.0},
        {8.02988e-1, 1.78396, 1.56740e2, -6.96559e3, 0.25},
        {-4.7268e-2, 6.02931e-2, 1.21051, -5.76676e-3, 0}};
    double[][] TBPfractionCoefOil = TBPfractionCoefsHeavyOil;

    public PedersenTBPModelSRKHeavyOil() {
      TBPfractionCoefsHeavyOil = new double[][] {{8.3063e2, 1.75228e1, 4.55911e-2, -1.13484e4, 0.0},
          {8.02988e-1, 1.78396, 1.56740e2, -6.96559e3, 0.25},
          {-4.7268e-2, 6.02931e-2, 1.21051, -5.76676e-3, 0}};
      TBPfractionCoefOil = TBPfractionCoefsHeavyOil;
    }
  }

  public class PedersenTBPModelPR extends PedersenTBPModelSRK {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    public PedersenTBPModelPR() {
      double[][] TBPfractionCoefOil2 = {{73.4043, 97.3562, 0.618744, -2059.32, 0.0},
          {0.0728462, 2.18811, 163.91, -4043.23, 1.0 / 4.0},
          {0.373765, 0.00549269, 0.0117934, -4.93049e-6, 0.0}};
      double[][] TBPfractionCoefHeavyOil2 = {{9.13222e2, 1.01134e1, 4.54194e-2, -1.3587e4, 0.0},
          {1.28155, 1.26838, 1.67106e2, -8.10164e3, 0.25},
          {-2.3838e-1, 6.10147e-2, 1.32349, -6.52067e-3, 0.0}};
      double[] TPBracketcoefs2 = {0.25969, 0.50033};
      TBPfractionCoefOil = TBPfractionCoefOil2;
      TBPfractionCoefsHeavyOil = TBPfractionCoefHeavyOil2;
      TPBracketcoefs = TPBracketcoefs2;
    }
  }


  public class PedersenTBPModelPR2 extends PedersenTBPModelSRK {
    /** Serialization version UID. */

    private static final long serialVersionUID = 1000;

    public PedersenTBPModelPR2() {
      double[][] TBPfractionCoefOil2 = {{73.4043, 97.3562, 0.618744, -2059.32, 0.0},
          {0.0728462, 2.18811, 163.91, -4043.23, 1.0 / 4.0},
          {0.373765, 0.00549269, 0.0117934, -4.93049e-6, 0.0}};
      double[][] TBPfractionCoefHeavyOil2 = {{9.13222e2, 1.01134e1, 4.54194e-2, -1.3587e4, 0.0},
          {1.28155, 1.26838, 1.67106e2, -8.10164e3, 0.25},
          {-2.3838e-1, 6.10147e-2, 1.32349, -6.52067e-3, 0.0}};
      double[] TPBracketcoefs2 = {0.25969, 0.50033};
      TBPfractionCoefOil = TBPfractionCoefOil2;
      TBPfractionCoefsHeavyOil = TBPfractionCoefHeavyOil2;
      TPBracketcoefs = TPBracketcoefs2;
    }

    /** {@inheritDoc} */
    @Override
    public double calcTB(double molarMass, double density) {
      if (getBoilingPoint() > 0.0) {
        return getBoilingPoint();
      }
      // Søreide correlation
      double calculated_TB = (1928.3 - 1.695e5 * Math.pow(molarMass, -0.03522)
          * Math.pow(density, 3.266)
          * Math.exp(-4.922e-3 * molarMass - 4.7685 * density + 3.462e-3 * molarMass * density));
      return calculated_TB / 1.8;
    }
  }



  public class PedersenTBPModelPRHeavyOil extends PedersenTBPModelPR {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    public PedersenTBPModelPRHeavyOil() {
      double[][] TBPfractionCoefHeavyOil2 = {{9.13222e2, 1.01134e1, 4.54194e-2, -1.3587e4, 0.0},
          {1.28155, 1.26838, 1.67106e2, -8.10164e3, 0.25},
          {-2.3838e-1, 6.10147e-2, 1.32349, -6.52067e-3, 0.0}};
      // double[][] TBPfractionCoefOil = TBPfractionCoefHeavyOil2;
      // double[][] TBPfractionCoefsHeavyOil = TBPfractionCoefHeavyOil2;
      TBPfractionCoefOil = TBPfractionCoefHeavyOil2;
      TBPfractionCoefsHeavyOil = TBPfractionCoefHeavyOil2;
    }
  }

  /**
   * Riazi-Daubert (1980) property estimation method.
   *
   * <p>
   * The Riazi-Daubert correlations use a simple exponential-power law form that relates critical
   * properties to molecular weight (M) and specific gravity (SG). This model is particularly
   * effective for light to medium petroleum fractions with molecular weights below 300 g/mol.
   * </p>
   *
   * <h3>Key Correlations</h3>
   * <p>
   * <b>Critical Temperature:</b>
   * </p>
   * <p>
   * T<sub>c</sub> = (5/9) × 554.4 × exp(-1.3478×10<sup>-4</sup>·M - 0.61641·SG) ×
   * M<sup>0.2998</sup> × SG<sup>1.0555</sup>
   * </p>
   *
   * <p>
   * <b>Critical Pressure:</b>
   * </p>
   * <p>
   * P<sub>c</sub> = 0.068947 × 4.5203×10<sup>4</sup> × exp(-1.8078×10<sup>-3</sup>·M - 0.3084·SG) ×
   * M<sup>-0.8063</sup> × SG<sup>1.6015</sup>
   * </p>
   *
   * <p>
   * <b>Boiling Point:</b>
   * </p>
   * <p>
   * T<sub>b</sub> = 97.58 × M<sup>0.3323</sup> × SG<sup>0.04609</sup>
   * </p>
   *
   * <h3>Applicability</h3>
   * <ul>
   * <li>Molecular weight: 70-300 g/mol (light to medium fractions)</li>
   * <li>Specific gravity: 0.65-0.90</li>
   * <li>Falls back to Pedersen model for MW &gt; 300 g/mol</li>
   * </ul>
   *
   * <h3>Notes</h3>
   * <p>
   * The acentric factor is calculated using the Kesler-Lee correlation with switchover at
   * T<sub>br</sub> = 0.8. The model does not calculate the EOS m-parameter directly (calcm =
   * false).
   * </p>
   *
   * <p>
   * Reference: Riazi, M.R. and Daubert, T.E. (1980). "Simplify Property Predictions." Hydrocarbon
   * Processing, 59(3), 115-116.
   * </p>
   */
  public class RiaziDaubert extends PedersenTBPModelSRK {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    public RiaziDaubert() {
      calcm = false;
    }

    /** {@inheritDoc} */
    @Override
    public double calcTC(double molarMass, double density) {
      // molarMass=molarMass*1e3;
      if (molarMass > 300) {
        return super.calcTC(molarMass, density);
      }
      return 5.0 / 9.0 * 554.4
          * Math.exp(-1.3478e-4 * molarMass - 0.61641 * density + 0.0 * molarMass * density)
          * Math.pow(molarMass, 0.2998) * Math.pow(density, 1.0555); // Math.pow(sig1, b) *
                                                                     // Math.pow(sig2, c);
    }

    /** {@inheritDoc} */
    @Override
    public double calcPC(double molarMass, double density) {
      if (molarMass > 300) {
        return super.calcPC(molarMass, density);
      }
      return 0.068947 * 4.5203e4
          * Math.exp(-1.8078e-3 * molarMass + -0.3084 * density + 0.0 * molarMass * density)
          * Math.pow(molarMass, -0.8063) * Math.pow(density, 1.6015); // Math.pow(sig1, b)
                                                                      // * Math.pow(sig2,
                                                                      // c);
    }

    public double calcAcentricFactor2(double molarMass, double density) {
      double TC = calcTC(molarMass, density);
      double TB = calcTB(molarMass, density);
      double PC = calcPC(molarMass, density);
      return 3.0 / 7.0 * Math.log10(PC / ThermodynamicConstantsInterface.referencePressure)
          / (TC / TB - 1.0) - 1.0;
    }

    /** {@inheritDoc} */
    @Override
    public double calcTB(double molarMass, double density) {
      if (getBoilingPoint() > 0.0) {
        return getBoilingPoint();
      }
      return 97.58 * Math.pow(molarMass, 0.3323) * Math.pow(density, 0.04609);
    }

    /** {@inheritDoc} */
    @Override
    public double calcAcentricFactor(double molarMass, double density) {
      double TC = calcTC(molarMass, density);
      double TB = calcTB(molarMass, density);
      double PC = calcPC(molarMass, density);
      double TBR = TB / TC;
      double PBR = ThermodynamicConstantsInterface.referencePressure / PC;
      if (TBR < 0.8) {
        return (Math.log(PBR) - 5.92714 + 6.09649 / TBR + 1.28862 * Math.log(TBR)
            - 0.169347 * Math.pow(TBR, 6.0))
            / (15.2518 - 15.6875 / TBR - 13.4721 * Math.log(TBR) + 0.43577 * Math.pow(TBR, 6.0));
      } else {
        double Kw = Math.pow(TB, 1.0 / 3.0) / density;
        return -7.904 + 0.1352 * Kw - 0.007465 * Kw * Kw + 8.359 * TBR
            + (1.408 - 0.01063 * Kw) / TBR;
      }
    }
  }

  /**
   * Lee-Kesler property estimation method.
   *
   * <p>
   * The Lee-Kesler (1976) correlations use boiling point and specific gravity as primary inputs to
   * estimate critical properties. This model is particularly useful when Watson characterization
   * factor data is available.
   * </p>
   *
   * <h3>Correlations</h3>
   * <ul>
   * <li>Tc = 341.7 + 811·SG + (0.4244 + 0.1174·SG)·Tb + (0.4669 - 3.2623·SG)·10⁵/Tb</li>
   * <li>ln(Pc) = 8.3634 - 0.0566/SG - f(Tb, SG)</li>
   * <li>ω from Kesler-Lee correlation (different for Tbr &lt; 0.8 and Tbr ≥ 0.8)</li>
   * </ul>
   *
   * <p>
   * Reference: Kesler, M.G. and Lee, B.I. (1976). "Improve Prediction of Enthalpy of Fractions."
   * Hydrocarbon Processing, 55(3), 153-158.
   * </p>
   */
  public class LeeKesler extends TBPBaseModel {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    public LeeKesler() {
      calcm = false;
    }

    /** {@inheritDoc} */
    @Override
    public double calcTC(double molarMass, double density) {
      double sg = density;
      double TB = calcTB(molarMass, density);
      double TC =
          189.8 + 450.6 * sg + (0.4244 + 0.1174 * sg) * TB + (0.1441 - 1.0069 * sg) * 1e5 / TB;
      return TC;
    }

    /** {@inheritDoc} */
    @Override
    public double calcPC(double molarMass, double density) {
      double sg = density;
      double TB = calcTB(molarMass, density);
      double logpc =
          3.3864 - 0.0566 / sg - ((0.43639 + 4.1216 / sg + 0.21343 / sg / sg) * 1e-3 * TB)
              + ((0.47579 + 1.182 / sg + 0.15302 / sg / sg) * 1e-6 * TB * TB)
              - ((2.4505 + 9.9099 / sg / sg) * 1e-10 * TB * TB * TB);
      double PC = Math.exp(logpc) * 10;
      return PC;
    }

    /** {@inheritDoc} */
    @Override
    public double calcAcentricFactor(double molarMass, double density) {
      return super.calcAcentricFactorKeslerLee(molarMass, density);
    }

    /** {@inheritDoc} */
    @Override
    public double calcRacketZ(SystemInterface thermoSystem, double molarMass, double density) {
      double acs = calcAcentricFactor(molarMass, density);
      return 0.29056 - 0.08775 * acs;
    }
  }

  /**
   * Twu (1984) property estimation method.
   *
   * <p>
   * The Twu correlations use n-alkanes as reference compounds and apply perturbation corrections
   * based on specific gravity differences. This approach is particularly accurate for paraffinic
   * petroleum fractions and gas condensates.
   * </p>
   *
   * <p>
   * The method first calculates properties for a hypothetical n-alkane with the same boiling point,
   * then applies correction factors based on the difference between the actual specific gravity and
   * that of the reference n-alkane.
   * </p>
   *
   * <h3>Key Features</h3>
   * <ul>
   * <li>Uses n-alkane reference properties</li>
   * <li>Applies perturbation corrections for SG differences</li>
   * <li>Iteratively solves for equivalent n-alkane molecular weight</li>
   * <li>Best for paraffinic/waxy fluids with Kw &gt; 12</li>
   * </ul>
   *
   * <p>
   * Reference: Twu, C.H. (1984). "An Internally Consistent Correlation for Predicting the Critical
   * Properties and Molecular Weights of Petroleum and Coal-Tar Liquids." Fluid Phase Equilibria,
   * 16, 137-150.
   * </p>
   */
  public class TwuModel extends TBPBaseModel {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    public TwuModel() {
      calcm = false;
    }

    /** {@inheritDoc} */
    @Override
    public double calcTC(double molarMass, double density) {
      double sg = density;
      double TB = calcTB(molarMass, density);
      double MW = solveMW(TB);
      double Tcnalkane = TB * 1.0 / (0.533272 + 0.343831e-3 * TB + 2.526167e-7 * TB * TB
          - 1.65848e-10 * TB * TB * TB + 4.60774e24 * Math.pow(TB, -13));
      double phi = 1.0 - TB / Tcnalkane;
      double SGalkane =
          0.843593 - 0.128624 * phi - 3.36159 * Math.pow(phi, 3) - 13749 * Math.pow(phi, 12);
      double PCnalkane = Math.pow(0.318317 + 0.099334 * Math.sqrt(phi) + 2.89698 * phi
          + 3.0054 * phi * phi + 8.65163 * Math.pow(phi, 4), 2);
      double VCnalkane = Math.pow(
          (0.82055 + 0.715468 * phi + 2.21266 * phi * phi * phi + 13411.1 * Math.pow(phi, 14)), -8);
      double deltaST = Math.exp(5.0 * (SGalkane - sg)) - 1.0;
      double fT = deltaST * (-0.270159 * Math.pow(TB, -0.5)
          + (0.0398285 - 0.706691 * Math.pow(TB, -0.5) * deltaST));
      double TC = Tcnalkane * Math.pow(((1 + 2 * fT) / (1 - 2 * fT)), 2);
      return TC;
    }

    public double calculateTfunc(double MW_alkane, double TB) {
      double phi = Math.log(MW_alkane);
      return Math
          .exp(5.1264 + 2.71579 * phi - 0.28659 * phi * phi - 39.8544 / phi - 0.122488 / phi / phi)
          - 13.7512 * phi + 19.6197 * phi * phi - TB;
    }

    public double computeGradient(double MW_alkane, double TB) {
      double delta = 1;
      double TfuncPlus = calculateTfunc(MW_alkane + delta, TB);
      double TfuncMinus = calculateTfunc(MW_alkane - delta, TB);
      return (TfuncPlus - TfuncMinus) / (2 * delta);
    }

    public double solveMW(double TB) {
      double MW_alkane = TB / (5.8 - 0.0052 * TB);
      double tolerance = 1e-6;
      double prevMW_alkane;
      double error = 1.0;
      int iter = 0;

      do {
        iter++;
        prevMW_alkane = MW_alkane;
        double gradient = computeGradient(MW_alkane, TB);
        MW_alkane -= 0.5 * calculateTfunc(MW_alkane, TB) / gradient;
        error = Math.abs(MW_alkane - prevMW_alkane);
      } while (Math.abs(error) > tolerance && iter < 1000 || iter < 3);

      return MW_alkane;
    }

    /** {@inheritDoc} */
    @Override
    public double calcPC(double molarMass, double density) {
      double sg = density;
      double TB = calcTB(molarMass, density);
      double MW = solveMW(TB);
      double Tcnalkane = TB * 1.0 / (0.533272 + 0.343831e-3 * TB + 2.526167e-7 * TB * TB
          - 1.65848e-10 * TB * TB * TB + 4.60774e24 * Math.pow(TB, -13));
      double phi = 1.0 - TB / Tcnalkane;
      double SGalkane =
          0.843593 - 0.128624 * phi - 3.36159 * Math.pow(phi, 3) - 13749 * Math.pow(phi, 12);
      double PCnalkane = Math.pow(0.318317 + 0.099334 * Math.sqrt(phi) + 2.89698 * phi
          + 3.0054 * phi * phi + 8.65163 * Math.pow(phi, 4), 2);
      double VCnalkane = Math.pow(
          (0.82055 + 0.715468 * phi + 2.21266 * phi * phi * phi + 13411.1 * Math.pow(phi, 14)), -8);
      double deltaST = Math.exp(5.0 * (SGalkane - sg)) - 1.0;
      double fT = deltaST * (-0.270159 * Math.pow(TB, -0.5)
          + (0.0398285 - 0.706691 * Math.pow(TB, -0.5) * deltaST));
      double TC = Tcnalkane * Math.pow(((1 + 2 * fT) / (1 - 2 * fT)), 2);
      double deltaSP = Math.exp(0.5 * (SGalkane - sg)) - 1.0;
      double deltaSV = Math.exp(4.0 * (SGalkane * SGalkane - sg * sg)) - 1.0;
      double fV = deltaSV
          * (0.347776 * Math.pow(TB, -0.5) + (-0.182421 + 2.24890 * Math.pow(TB, -0.5)) * deltaSV);
      double VC = VCnalkane * Math.pow(((1 + 2 * fV) / (1 - 2 * fV)), 2);
      double fP = deltaSP * ((2.53262 - 34.4321 * Math.pow(TB, -0.5) - 0.00230193 * TB)
          + (-11.4277 + 187.934 * Math.pow(TB, -0.5) + 0.00414963 * TB) * deltaSP);
      double PC = PCnalkane * (TC / Tcnalkane) * (VCnalkane / VC)
          * Math.pow(((1 + 2 * fP) / (1 - 2 * fP)), 2);
      return PC * 10.0; // * 10 due to conversion MPa to bar
    }

    /** {@inheritDoc} */
    @Override
    public double calcCriticalVolume(double molarMass, double density) {
      double sg = density;
      double TB = calcTB(molarMass, density);
      double MW = solveMW(TB);
      double Tcnalkane = TB * 1.0 / (0.533272 + 0.343831e-3 * TB + 2.526167e-7 * TB * TB
          - 1.65848e-10 * TB * TB * TB + 4.60774e24 * Math.pow(TB, -13));
      double phi = 1.0 - TB / Tcnalkane;
      double SGalkane =
          0.843593 - 0.128624 * phi - 3.36159 * Math.pow(phi, 3) - 13749 * Math.pow(phi, 12);
      double PCnalkane = Math.pow(0.318317 + 0.099334 * Math.sqrt(phi) + 2.89698 * phi
          + 3.0054 * phi * phi + 8.65163 * Math.pow(phi, 4), 2);
      double VCnalkane = Math.pow(
          (0.82055 + 0.715468 * phi + 2.21266 * phi * phi * phi + 13411.1 * Math.pow(phi, 14)), -8);
      double deltaST = Math.exp(5.0 * (SGalkane - sg)) - 1.0;
      double fT = deltaST * (-0.270159 * Math.pow(TB, -0.5)
          + (0.0398285 - 0.706691 * Math.pow(TB, -0.5) * deltaST));
      double TC = Tcnalkane * Math.pow(((1 + 2 * fT) / (1 - 2 * fT)), 2);
      double deltaSP = Math.exp(0.5 * (SGalkane - sg)) - 1.0;
      double deltaSV = Math.exp(4.0 * (SGalkane * SGalkane - sg * sg)) - 1.0;
      double fV = deltaSV
          * (0.347776 * Math.pow(TB, -0.5) + (-0.182421 + 2.24890 * Math.pow(TB, -0.5)) * deltaSV);
      double VC = VCnalkane * Math.pow(((1 + 2 * fV) / (1 - 2 * fV)), 2);
      double fP = deltaSP * ((2.53262 - 34.4321 * Math.pow(TB, -0.5) - 0.00230193 * TB)
          + (-11.4277 + 187.934 * Math.pow(TB, -0.5) + 0.00414963 * TB) * deltaSP);
      double PC = PCnalkane * (TC / Tcnalkane) * (VCnalkane / VC)
          * Math.pow(((1 + 2 * fP) / (1 - 2 * fP)), 2);
      return VC * 1e3; // m3/mol
    }

    /** {@inheritDoc} */
    @Override
    public double calcRacketZ(SystemInterface thermoSystem, double molarMass, double density) {
      double acs = calcAcentricFactor(molarMass, density);
      return 0.29056 - 0.08775 * acs;
    }
  }

  /**
   * Cavett (1962) property estimation method with Lee-Kesler enhancements.
   *
   * <p>
   * This implementation combines the Cavett API gravity corrections with the more robust Lee-Kesler
   * correlations for improved accuracy across a wider range of petroleum fractions. The original
   * Cavett correlations are known to have issues with light fractions (C7-C10), so this hybrid
   * approach uses Lee-Kesler as the base with API gravity adjustments.
   * </p>
   *
   * <h3>Key Features</h3>
   * <ul>
   * <li>Hybrid Lee-Kesler/Cavett approach for robustness</li>
   * <li>API gravity corrections for heavy fractions (API &lt; 30°)</li>
   * <li>Riazi-Daubert boiling point correlation</li>
   * <li>Edmister acentric factor with bounds checking</li>
   * </ul>
   *
   * <h3>Correlations</h3>
   * <p>
   * <b>Critical Temperature (Lee-Kesler base with API correction):</b>
   * </p>
   * <p>
   * T<sub>c</sub> = [189.8 + 450.6·SG + (0.4244 + 0.1174·SG)·T<sub>b</sub> + (0.1441 -
   * 1.0069·SG)·10<sup>5</sup>/T<sub>b</sub>] × f(API)
   * </p>
   * <p>
   * where f(API) = 1 + 0.002·(30 - API) for API &lt; 30°, otherwise f(API) = 1
   * </p>
   *
   * <p>
   * <b>Critical Pressure (Lee-Kesler base with API correction):</b>
   * </p>
   * <p>
   * ln(P<sub>c</sub>) = 3.3864 - 0.0566/SG - g(T<sub>b</sub>, SG) with API correction
   * </p>
   *
   * <p>
   * <b>Acentric Factor (Edmister):</b>
   * </p>
   * <p>
   * ω = (3/7) × log<sub>10</sub>(P<sub>c</sub>/P<sub>ref</sub>) / (T<sub>c</sub>/T<sub>b</sub> - 1)
   * - 1
   * </p>
   * <p>
   * Bounded to range [0.0, 1.5] for physical validity
   * </p>
   *
   * <h3>API Gravity Relationship</h3>
   * <p>
   * API = 141.5/SG - 131.5
   * </p>
   * <ul>
   * <li>API &gt; 31.1°: Light crude (SG &lt; 0.87)</li>
   * <li>API 22.3-31.1°: Medium crude</li>
   * <li>API &lt; 22.3°: Heavy crude (SG &gt; 0.92)</li>
   * </ul>
   *
   * <h3>Recommended Applications</h3>
   * <ul>
   * <li>Refining industry calculations</li>
   * <li>Heavy oil characterization (API &lt; 30°)</li>
   * <li>When API gravity data is available and important</li>
   * </ul>
   *
   * <p>
   * Reference: Cavett, R.H. (1962). "Physical Data for Distillation Calculations, Vapor-Liquid
   * Equilibria." Proc. 27th API Meeting, San Francisco.
   * </p>
   */
  public class CavettModel extends TBPBaseModel {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    public CavettModel() {
      calcm = false;
    }

    /**
     * Calculate API gravity from specific gravity.
     *
     * @param sg specific gravity (water = 1.0)
     * @return API gravity in degrees
     */
    private double calcAPI(double sg) {
      return 141.5 / sg - 131.5;
    }

    /**
     * Calculate boiling point using Riazi-Daubert correlation.
     *
     * @param molarMass molar mass in g/mol
     * @param density specific gravity (g/cm³)
     * @return boiling point in Kelvin
     */
    @Override
    public double calcTB(double molarMass, double density) {
      if (getBoilingPoint() > 0.0) {
        return getBoilingPoint();
      }
      // Riazi-Daubert boiling point correlation: Tb = 97.58 * M^0.3323 * SG^0.04609
      return 97.58 * Math.pow(molarMass, 0.3323) * Math.pow(density, 0.04609);
    }

    /**
     * Calculate critical temperature using Lee-Kesler correlation with API correction.
     *
     * @param molarMass molar mass in g/mol
     * @param density specific gravity (g/cm³)
     * @return critical temperature in Kelvin
     */
    @Override
    public double calcTC(double molarMass, double density) {
      double sg = density;
      double TB = calcTB(molarMass, density);
      double API = calcAPI(density);

      // Lee-Kesler base correlation
      double TC_base =
          189.8 + 450.6 * sg + (0.4244 + 0.1174 * sg) * TB + (0.1441 - 1.0069 * sg) * 1e5 / TB;

      // Apply Cavett-style API correction for heavy fractions (API < 30)
      // Light fractions (API > 30) use pure Lee-Kesler
      if (API < 30) {
        double apiCorrection = 1.0 + 0.002 * (30 - API);
        TC_base = TC_base * apiCorrection;
      }
      return TC_base;
    }

    /**
     * Calculate critical pressure using Lee-Kesler correlation with API correction.
     *
     * @param molarMass molar mass in g/mol
     * @param density specific gravity (g/cm³)
     * @return critical pressure in bar
     */
    @Override
    public double calcPC(double molarMass, double density) {
      double sg = density;
      double TB = calcTB(molarMass, density);
      double API = calcAPI(density);

      // Lee-Kesler base correlation
      double logpc =
          3.3864 - 0.0566 / sg - ((0.43639 + 4.1216 / sg + 0.21343 / sg / sg) * 1e-3 * TB)
              + ((0.47579 + 1.182 / sg + 0.15302 / sg / sg) * 1e-6 * TB * TB)
              - ((2.4505 + 9.9099 / sg / sg) * 1e-10 * TB * TB * TB);
      double PC_base = Math.exp(logpc) * 10; // Result in bar

      // Apply Cavett-style API correction for heavy fractions
      if (API < 30) {
        double apiCorrection = 1.0 + 0.001 * (30 - API);
        PC_base = PC_base * apiCorrection;
      }
      return PC_base;
    }

    /**
     * Calculate acentric factor using Edmister correlation.
     *
     * @param molarMass molar mass in g/mol
     * @param density specific gravity (g/cm³)
     * @return acentric factor (dimensionless)
     */
    @Override
    public double calcAcentricFactor(double molarMass, double density) {
      double TC = calcTC(molarMass, density);
      double TB = calcTB(molarMass, density);
      double PC = calcPC(molarMass, density);

      // Edmister correlation: ω = (3/7) * log10(Pc/1.01325) / (Tc/Tb - 1) - 1
      double TBR = TB / TC;
      if (TBR >= 1.0) {
        return super.calcAcentricFactorKeslerLee(molarMass, density);
      }

      double omega =
          (3.0 / 7.0) * Math.log10(PC / ThermodynamicConstantsInterface.referencePressure)
              / (TC / TB - 1.0) - 1.0;

      // Bounds check
      if (omega < 0.0) {
        omega = 0.0;
      } else if (omega > 1.5) {
        omega = 1.5;
      }
      return omega;
    }

    /** {@inheritDoc} */
    @Override
    public double calcRacketZ(SystemInterface thermoSystem, double molarMass, double density) {
      double acs = calcAcentricFactor(molarMass, density);
      return 0.29056 - 0.08775 * acs;
    }
  }

  /**
   * Standing (1977) property estimation method.
   *
   * <p>
   * This implementation uses Riazi-Daubert style correlations for robust critical property
   * estimation. While named after Standing's pioneering reservoir engineering work, the actual
   * correlations follow the Riazi-Daubert exponential-power law form for better numerical stability
   * and wider applicability.
   * </p>
   *
   * <h3>Key Correlations</h3>
   * <p>
   * <b>Critical Temperature:</b>
   * </p>
   * <p>
   * T<sub>c</sub> = (5/9) × 554.4 × exp(-1.3478×10<sup>-4</sup>·M - 0.61641·SG) ×
   * M<sup>0.2998</sup> × SG<sup>1.0555</sup>
   * </p>
   *
   * <p>
   * <b>Critical Pressure:</b>
   * </p>
   * <p>
   * P<sub>c</sub> = 0.068947 × 4.5203×10<sup>4</sup> × exp(-1.8078×10<sup>-3</sup>·M - 0.3084·SG) ×
   * M<sup>-0.8063</sup> × SG<sup>1.6015</sup>
   * </p>
   *
   * <h3>Recommended Use Cases</h3>
   * <ul>
   * <li>Reservoir engineering calculations</li>
   * <li>Quick property estimates</li>
   * <li>Black oil PVT characterization</li>
   * <li>When consistency with reservoir simulation tools is needed</li>
   * </ul>
   *
   * <h3>Acentric Factor</h3>
   * <p>
   * Uses the Kesler-Lee correlation for acentric factor estimation, which provides good results
   * across a wide range of petroleum fractions. The correlation switches between two forms based on
   * reduced boiling point (T<sub>br</sub> = T<sub>b</sub>/T<sub>c</sub>):
   * </p>
   * <ul>
   * <li>T<sub>br</sub> &lt; 0.8: Vapor pressure correlation form</li>
   * <li>T<sub>br</sub> ≥ 0.8: Watson K-factor based form</li>
   * </ul>
   *
   * <p>
   * Reference: Standing, M.B. (1977). "Volumetric and Phase Behavior of Oil Field Hydrocarbon
   * Systems." SPE, Dallas.
   * </p>
   */
  public class StandingModel extends TBPBaseModel {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    public StandingModel() {
      calcm = false;
    }

    /** {@inheritDoc} */
    @Override
    public double calcTC(double molarMass, double density) {
      // Standing-type correlation using Riazi-Daubert form for robustness
      // Tc = a * exp(b*M + c*SG) * M^d * SG^e (result in K)
      // Note: molarMass is passed in g/mol from SystemThermo
      // Uses same formula as RiaziDaubert for consistency
      double TC_K = 5.0 / 9.0 * 554.4 * Math.exp(-1.3478e-4 * molarMass - 0.61641 * density)
          * Math.pow(molarMass, 0.2998) * Math.pow(density, 1.0555);
      return TC_K;
    }

    /** {@inheritDoc} */
    @Override
    public double calcPC(double molarMass, double density) {
      // Standing-type correlation using Riazi-Daubert form for robustness
      // Pc = a * exp(b*M + c*SG) * M^d * SG^e (result in bar)
      // Note: molarMass is passed in g/mol from SystemThermo
      double PC_bar = 4.5203e4 * Math.exp(-1.8078e-3 * molarMass - 0.3084 * density)
          * Math.pow(molarMass, -0.8063) * Math.pow(density, 1.6015);
      return PC_bar * 0.068947; // Convert to bar (same as RiaziDaubert)
    }

    /** {@inheritDoc} */
    @Override
    public double calcAcentricFactor(double molarMass, double density) {
      // Use Kesler-Lee correlation for acentric factor
      return super.calcAcentricFactorKeslerLee(molarMass, density);
    }

    /** {@inheritDoc} */
    @Override
    public double calcRacketZ(SystemInterface thermoSystem, double molarMass, double density) {
      double acs = calcAcentricFactor(molarMass, density);
      return 0.29056 - 0.08775 * acs;
    }
  }

  /**
   * Calculate API gravity from specific gravity.
   *
   * <p>
   * API gravity is a measure of how heavy or light a petroleum liquid is compared to water. API
   * gravity greater than 10 means the liquid is lighter than water and will float.
   * </p>
   *
   * @param specificGravity specific gravity relative to water (g/cm³)
   * @return API gravity in degrees
   */
  public static double calcAPIGravity(double specificGravity) {
    return 141.5 / specificGravity - 131.5;
  }

  /**
   * Calculate specific gravity from API gravity.
   *
   * @param apiGravity API gravity in degrees
   * @return specific gravity relative to water (g/cm³)
   */
  public static double calcSpecificGravity(double apiGravity) {
    return 141.5 / (apiGravity + 131.5);
  }

  /**
   * Calculate Watson characterization factor (Kw) from molecular weight and density.
   *
   * <p>
   * The Watson K-factor is used to characterize petroleum fractions:
   * <ul>
   * <li>Kw &gt; 12.5: Paraffinic (gas condensates)</li>
   * <li>Kw 11.5-12.5: Mixed/intermediate</li>
   * <li>Kw 10.5-11.5: Naphthenic</li>
   * <li>Kw &lt; 10.5: Aromatic</li>
   * </ul>
   * </p>
   *
   * @param molarMass molar mass in kg/mol
   * @param density density in g/cm³ (specific gravity)
   * @return Watson characterization factor (dimensionless)
   */
  public double calcWatsonKFactor(double molarMass, double density) {
    // Estimate boiling point using Pedersen correlation
    double TB;
    if (molarMass < 0.540) {
      TB = 2E-06 * Math.pow(molarMass * 1000, 3) - 0.0035 * Math.pow(molarMass * 1000, 2)
          + 2.4003 * molarMass * 1000 + 171.74;
    } else {
      TB = 97.58 * Math.pow(molarMass * 1000, 0.3323) * Math.pow(density, 0.04609);
    }
    return Math.pow(1.8 * TB, 1.0 / 3.0) / density;
  }

  /**
   * Recommend the most appropriate TBP model based on fluid properties and EOS type.
   *
   * <p>
   * This method analyzes the plus fraction properties (molecular weight, density, Watson K-factor)
   * and recommends the most suitable TBP correlation model. The recommendation considers:
   * <ul>
   * <li>Fluid type (paraffinic, naphthenic, aromatic)</li>
   * <li>Molecular weight range (light vs heavy)</li>
   * <li>Equation of state being used (SRK vs PR)</li>
   * </ul>
   * </p>
   *
   * @param avgMW average molecular weight of plus fraction in kg/mol
   * @param avgDensity average density/specific gravity in g/cm³
   * @param eosType equation of state type: "SRK" or "PR"
   * @return recommended model name as a String
   */
  public String recommendTBPModel(double avgMW, double avgDensity, String eosType) {
    double Kw = calcWatsonKFactor(avgMW, avgDensity);
    double M_gmol = avgMW * 1000; // Convert to g/mol for comparison

    // Heavy oil check (MW > 500 g/mol)
    if (M_gmol > 500) {
      if (eosType.equalsIgnoreCase("PR")) {
        logger.info("Recommending PedersenPRHeavyOil for heavy fluid (MW=" + M_gmol + " g/mol)");
        return "PedersenPRHeavyOil";
      } else {
        logger.info("Recommending PedersenSRKHeavyOil for heavy fluid (MW=" + M_gmol + " g/mol)");
        return "PedersenSRKHeavyOil";
      }
    }

    // Light fractions (MW < 300 g/mol) - Riazi-Daubert works well
    if (M_gmol < 300) {
      // Highly paraffinic - Twu model is best
      if (Kw > 12.0) {
        logger.info("Recommending Twu for paraffinic fluid (Kw=" + Kw + ")");
        return "Twu";
      }
      // Naphthenic/aromatic - Riazi-Daubert
      if (Kw < 11.0) {
        logger.info("Recommending RiaziDaubert for naphthenic/aromatic fluid (Kw=" + Kw + ")");
        return "RiaziDaubert";
      }
    }

    // Default to Pedersen models for intermediate cases
    if (eosType.equalsIgnoreCase("PR")) {
      logger.info("Recommending PedersenPR for intermediate fluid");
      return "PedersenPR";
    } else {
      logger.info("Recommending PedersenSRK for intermediate fluid");
      return "PedersenSRK";
    }
  }

  /**
   * Get list of all available TBP model names.
   *
   * @return array of available model names
   */
  public static String[] getAvailableModels() {
    return new String[] {"PedersenSRK", "PedersenSRKHeavyOil", "PedersenPR", "PedersenPR2",
        "PedersenPRHeavyOil", "RiaziDaubert", "Lee-Kesler", "Twu", "Cavett", "Standing"};
  }

  /**
   * Get a TBP model instance by name.
   *
   * <p>
   * Available models and their recommended use cases:
   * <ul>
   * <li><b>PedersenSRK</b>: General purpose for SRK EOS, default choice</li>
   * <li><b>PedersenSRKHeavyOil</b>: SRK EOS for heavy oils (MW &gt; 500 g/mol)</li>
   * <li><b>PedersenPR</b>: General purpose for PR EOS</li>
   * <li><b>PedersenPR2</b>: PR EOS with Søreide boiling point correlation</li>
   * <li><b>PedersenPRHeavyOil</b>: PR EOS for heavy oils</li>
   * <li><b>RiaziDaubert</b>: Light to medium petroleum fractions (MW &lt; 300 g/mol)</li>
   * <li><b>Lee-Kesler</b>: General purpose, uses Watson K-factor</li>
   * <li><b>Twu</b>: Paraffinic fluids, based on n-alkane reference</li>
   * <li><b>Cavett</b>: Refining industry, API gravity based</li>
   * <li><b>Standing</b>: Simple power-law, reservoir engineering</li>
   * </ul>
   * </p>
   *
   * @param name model name (case-sensitive)
   * @return TBPModelInterface instance, defaults to PedersenSRK if name not recognized
   */
  public TBPModelInterface getModel(String name) {
    this.name = name;
    if (name.equals("PedersenSRK")) {
      return new PedersenTBPModelSRK();
    } else if (name.equals("PedersenSRKHeavyOil")) {
      logger.info("using SRK heavy oil TBP model");
      return new PedersenTBPModelSRKHeavyOil();
    } else if (name.equals("PedersenPR")) {
      return new PedersenTBPModelPR();
    } else if (name.equals("PedersenPR2")) {
      return new PedersenTBPModelPR2();
    } else if (name.equals("PedersenPRHeavyOil")) {
      logger.info("using PR heavy oil TBP model");
      return new PedersenTBPModelPRHeavyOil();
    } else if (name.equals("RiaziDaubert")) {
      return new RiaziDaubert();
    } else if (name.equals("Lee-Kesler")) {
      return new LeeKesler();
    } else if (name.equals("Twu")) {
      return new TwuModel();
    } else if (name.equals("Cavett")) {
      return new CavettModel();
    } else if (name.equals("Standing")) {
      return new StandingModel();
    } else {
      logger.warn("TBP model '" + name + "' not recognized, defaulting to PedersenSRK");
      return new PedersenTBPModelSRK();
    }
  }
}
