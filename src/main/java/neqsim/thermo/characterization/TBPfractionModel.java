package neqsim.thermo.characterization;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * TBPfractionModel class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
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
   * Base model for something.
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
   * PedersenTBPModelSRK
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
      //Søreide correlation
      double calculated_TB = (1928.3 - 1.695e5 * Math.pow(molarMass, -0.03522)
      * Math.pow(density, 3.266) * Math.exp(-4.922e-3 * molarMass  - 4.7685 * density
          + 3.462e-3 * molarMass * density));
      return calculated_TB/1.8;
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
      return 97.58*Math.pow(molarMass,0.3323)*Math.pow(density,0.04609);
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
   * Lee-Kesler property estimation method
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
  }

  /**
   * Two property estimation method
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
  }

  /**
   * <p>
   * getModel.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @return a {@link neqsim.thermo.characterization.TBPModelInterface} object
   */
  public TBPModelInterface getModel(String name) {
    this.name = name;
    if (name.equals("PedersenSRK")) {
      return new PedersenTBPModelSRK();
    } else if (name.equals("PedersenSRKHeavyOil")) {
      logger.info("using SRK heavy oil TBp.................");
      return new PedersenTBPModelSRKHeavyOil();
    } else if (name.equals("PedersenPR")) {
      return new PedersenTBPModelPR();
    } else if (name.equals("PedersenPR2")) {
      return new PedersenTBPModelPR2();
    } else if (name.equals("PedersenPRHeavyOil")) {
      logger.info("using PR heavy oil TBp.................");
      return new PedersenTBPModelPRHeavyOil();
    } else if (name.equals("RiaziDaubert")) {
      return new RiaziDaubert();
    } else if (name.equals("Lee-Kesler")) {
      return new LeeKesler();
    } else if (name.equals("Twu")) {
      return new TwuModel();
    } else {
      // System.out.println("not a valid TBPModelName.................");
      return new PedersenTBPModelSRK();
    }
  }
}
