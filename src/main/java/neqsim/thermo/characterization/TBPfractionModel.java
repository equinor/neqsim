/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.thermo.characterization;

import java.io.Serializable;
import neqsim.thermo.system.SystemInterface;
import org.apache.logging.log4j.*;

/**
 *
 * @author ESOL
 */
public class TBPfractionModel implements Serializable {
    private static final long serialVersionUID = 1000;
    String name = "";
    static Logger logger = LogManager.getLogger(TBPfractionModel.class);

    public TBPfractionModel() {
    }

    public abstract class TBPBaseModel implements TBPModelInterface, Cloneable, java.io.Serializable {

        protected boolean calcm = true;

        public String getName() {
            return name;
        }

        public double calcTB(double molarMass, double density) {
            return Math.pow((molarMass / 5.805e-5 * Math.pow(density, 0.9371)), 1.0 / 2.3776);
        }

        public double calcWatsonCharacterizationFactor(double molarMass, double density) {
            // System.out.println("boiling point " + calcTB(molarMass, density));
            return Math.pow(1.8 * calcTB(molarMass, density), 1.0 / 3.0) / density;
        }

        public double calcAcentricFactorKeslerLee(double molarMass, double density) {
            double TC = calcTC(molarMass, density);
            double TB = calcTB(molarMass, density);
            double PC = calcPC(molarMass, density);
            double TBR = TB / TC;
            double PBR = 1.01325 / PC;
            if (TBR < 0.8) {
                return (Math.log(PBR) - 5.92714 + 6.09649 / TBR + 1.28862 * Math.log(TBR)
                        - 0.169347 * Math.pow(TBR, 6.0))
                        / (15.2518 - 15.6875 / TBR - 13.4721 * Math.log(TBR) + 0.43577 * Math.pow(TBR, 6.0));
            } else {
                double Kw = Math.pow(TB, 1.0 / 3.0) / density;
                return -7.904 + 0.1352 * Kw - 0.007465 * Kw * Kw + 8.359 * TBR + (1.408 - 0.01063 * Kw) / TBR;
            }
        }

        public double calcAcentricFactor(double molarMass, double density) {
            double TC = calcTC(molarMass, density);
            double TB = calcTB(molarMass, density);
            double PC = calcPC(molarMass, density);
            return 3.0 / 7.0 * neqsim.MathLib.generalMath.GeneralMath.log10(PC / 1.01325) / (TC / TB - 1.0) - 1.0;
        }

        public double calcCriticalVolume(double molarMass, double density) {
            double TC = calcTC(molarMass, density);
            double PC = calcPC(molarMass, density);
            double acs = calcAcentricFactor(molarMass, density);// thermoSystem.getPhase(thermoSystem.getPhaseIndex(0)).getComponent(0).getAcentricFactor();
            double criticaVol = (0.2918 - 0.0928 * acs) * 8.314 * TC / PC * 10.0;
            if (criticaVol < 0) {
                // logger.info("acentric factor in calc critVol " + acs);
                criticaVol = (0.2918 - 0.0928) * 8.314 * TC / PC * 10.0;
            }
            return criticaVol;
        }

        public double calcParachorParameter(double molarMass, double density) {
            return 59.3 + 2.34 * molarMass * 1000.0;
        }

        public double calcCriticalViscosity(double molarMass, double density) {
            double TC = calcTC(molarMass, density);
            double PC = calcPC(molarMass, density);
            return 7.94830 * Math.sqrt(molarMass) * Math.pow(PC, 2.0 / 3.0) / Math.pow(TC, 1.0 / 6.0) * 1e-7;
        }

        public boolean isCalcm() {
            return calcm;
        }
    }

    public class PedersenTBPModelSRK extends TBPBaseModel {

        double[][] TBPfractionCoefOil = { { 163.12, 86.052, 0.43475, -1877.4, 0.0 },
                { -0.13408, 2.5019, 208.46, -3987.2, 1.0 }, { 0.7431, 0.0048122, 0.0096707, -3.7184e-6, 0.0 } };
        double[][] TBPfractionCoefsHeavyOil = { { 8.3063e2, 1.75228e1, 4.55911e-2, -1.13484e4, 0.0 },
                { 8.02988e-1, 1.78396, 1.56740e2, -6.96559e3, 0.25 },
                { -4.7268e-2, 6.02931e-2, 1.21051, -5.76676e-3, 0 } };
        double[] TPBracketcoefs = { 0.29441, 0.40768 };
        double[][] TBPfractionCoefs = null;

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

        public double calcPC(double molarMass, double density) {
            if (molarMass < 1120) {
                TBPfractionCoefs = TBPfractionCoefOil;
            } else {
                TBPfractionCoefs = TBPfractionCoefsHeavyOil;
            }

            return 0.01325 + Math
                    .exp(TBPfractionCoefs[1][0] + TBPfractionCoefs[1][1] * Math.pow(density, TBPfractionCoefs[1][4])
                            + TBPfractionCoefs[1][2] / molarMass + TBPfractionCoefs[1][3] / Math.pow(molarMass, 2.0));
        }

        public double calcm(double molarMass, double density) {
            if (molarMass < 1120) {
                TBPfractionCoefs = TBPfractionCoefOil;
                return TBPfractionCoefs[2][0] + TBPfractionCoefs[2][1] * molarMass + TBPfractionCoefs[2][2] * density
                        + TBPfractionCoefs[2][3] * Math.pow(molarMass, 2.0);

            } else {
                TBPfractionCoefs = TBPfractionCoefsHeavyOil;
                return TBPfractionCoefs[2][0] + TBPfractionCoefs[2][1] * Math.log(molarMass)
                        + TBPfractionCoefs[2][2] * density + TBPfractionCoefs[2][3] * Math.sqrt(molarMass);
            }
        }

        public double calcTB(double molarMass, double density) {

            if (molarMass < 90) {
                return 273.15 + 84;
            }
            if (molarMass < 107) {
                return 273.15 + 116.6;
            }
            if (molarMass < 121) {
                return 273.15 + 142.2;
            }
            if (molarMass < 134) {
                return 273.15 + 165.8;
            }
            if (molarMass < 147) {
                return 273.15 + 187.2;
            }
            if (molarMass < 161) {
                return 273.15 + 208.3;
            }

            return 97.58 * Math.pow(molarMass, 0.3323) * Math.pow(density, 0.04609);
        }

        @Override
        public double calcRacketZ(SystemInterface thermoSystem, double molarMass, double density) {
            double penelouxC = (thermoSystem.getPhase(0).getMolarVolume() - molarMass / (density * 10.0));
            // System.out.println("peneloux c " + penelouxC);
            double TC = calcTC(molarMass, density);
            double TB = calcTB(molarMass, density);
            double PC = calcPC(molarMass, density);
            return TPBracketcoefs[0]
                    - penelouxC / (TPBracketcoefs[1] * neqsim.thermo.ThermodynamicConstantsInterface.R * TC / PC);
        }
    }

    public class PedersenTBPModelSRKHeavyOil extends PedersenTBPModelSRK {

        double[][] TBPfractionCoefsHeavyOil = { { 8.3063e2, 1.75228e1, 4.55911e-2, -1.13484e4, 0.0 },
                { 8.02988e-1, 1.78396, 1.56740e2, -6.96559e3, 0.25 },
                { -4.7268e-2, 6.02931e-2, 1.21051, -5.76676e-3, 0 } };
        double[][] TBPfractionCoefOil = TBPfractionCoefsHeavyOil;

        public PedersenTBPModelSRKHeavyOil() {
            TBPfractionCoefsHeavyOil = new double[][] { { 8.3063e2, 1.75228e1, 4.55911e-2, -1.13484e4, 0.0 },
                    { 8.02988e-1, 1.78396, 1.56740e2, -6.96559e3, 0.25 },
                    { -4.7268e-2, 6.02931e-2, 1.21051, -5.76676e-3, 0 } };
            TBPfractionCoefOil = TBPfractionCoefsHeavyOil;

        }
    }

    public class PedersenTBPModelPR extends PedersenTBPModelSRK {

        public PedersenTBPModelPR() {
            double[][] TBPfractionCoefOil2 = { { 73.4043, 97.3562, 0.618744, -2059.32, 0.0 },
                    { 0.0728462, 2.18811, 163.91, -4043.23, 1.0 / 4.0 },
                    { 0.373765, 0.00549269, 0.0117934, -4.93049e-6, 0.0 } };
            double[][] TBPfractionCoefHeavyOil2 = { { 9.13222e2, 1.01134e1, 4.54194e-2, -1.3587e4, 0.0 },
                    { 1.28155, 1.26838, 1.67106e2, -8.10164e3, 0.25 },
                    { -2.3838e-1, 6.10147e-2, 1.32349, -6.52067e-3, 0.0 } };
            double[] TPBracketcoefs2 = { 0.25969, 0.50033 };
            TBPfractionCoefOil = TBPfractionCoefOil2;
            TBPfractionCoefsHeavyOil = TBPfractionCoefHeavyOil2;
            TPBracketcoefs = TPBracketcoefs2;
        }
    }

    public class PedersenTBPModelPRHeavyOil extends PedersenTBPModelPR {

        public PedersenTBPModelPRHeavyOil() {
            double[][] TBPfractionCoefHeavyOil2 = { { 9.13222e2, 1.01134e1, 4.54194e-2, -1.3587e4, 0.0 },
                    { 1.28155, 1.26838, 1.67106e2, -8.10164e3, 0.25 },
                    { -2.3838e-1, 6.10147e-2, 1.32349, -6.52067e-3, 0.0 } };
            double[][] TBPfractionCoefOil = TBPfractionCoefHeavyOil2;
            double[][] TBPfractionCoefsHeavyOil = TBPfractionCoefHeavyOil2;
            TBPfractionCoefOil = TBPfractionCoefHeavyOil2;
            TBPfractionCoefsHeavyOil = TBPfractionCoefHeavyOil2;
        }
    }

    public class RiaziDaubert extends PedersenTBPModelSRK {

        public RiaziDaubert() {
            calcm = false;
        }

        @Override
        public double calcTC(double molarMass, double density) {
            // molarMass=molarMass*1e3;
            if (molarMass > 300) {
                return super.calcTC(molarMass, density);
            }
            return 5.0 / 9.0 * 554.4 * Math.exp(-1.3478e-4 * molarMass - 0.61641 * density + 0.0 * molarMass * density)
                    * Math.pow(molarMass, 0.2998) * Math.pow(density, 1.0555);// Math.pow(sig1, b) * Math.pow(sig2, c);
        }

        @Override
        public double calcPC(double molarMass, double density) {
            if (molarMass > 300) {
                return super.calcPC(molarMass, density);
            }
            return 0.068947 * 4.5203e4
                    * Math.exp(-1.8078e-3 * molarMass + -0.3084 * density + 0.0 * molarMass * density)
                    * Math.pow(molarMass, -0.8063) * Math.pow(density, 1.6015);// Math.pow(sig1, b) * Math.pow(sig2, c);
        }

        public double calcAcentricFactor2(double molarMass, double density) {
            double TC = calcTC(molarMass, density);
            double TB = calcTB(molarMass, density);
            double PC = calcPC(molarMass, density);
            return 3.0 / 7.0 * neqsim.MathLib.generalMath.GeneralMath.log10(PC / 1.01325) / (TC / TB - 1.0) - 1.0;
        }

        public double calcTB(double molarMass, double density) {
            // Soreide method (Whitson book)
            return 5.0 / 9.0 * (1928.3 - 1.695e5 * Math.pow(molarMass, -0.03522) * Math.pow(density, 3.266)
                    * Math.exp(-4.922e-3 * molarMass - 4.7685 * density + 3.462e-3 * molarMass * density));// 97.58*Math.pow(molarMass,0.3323)*Math.pow(density,0.04609);
        }

        public double calcAcentricFactor(double molarMass, double density) {
            double TC = calcTC(molarMass, density);
            double TB = calcTB(molarMass, density);
            double PC = calcPC(molarMass, density);
            double TBR = TB / TC;
            double PBR = 1.01325 / PC;
            if (TBR < 0.8) {
                return (Math.log(PBR) - 5.92714 + 6.09649 / TBR + 1.28862 * Math.log(TBR)
                        - 0.169347 * Math.pow(TBR, 6.0))
                        / (15.2518 - 15.6875 / TBR - 13.4721 * Math.log(TBR) + 0.43577 * Math.pow(TBR, 6.0));
            } else {
                double Kw = Math.pow(TB, 1.0 / 3.0) / density;
                return -7.904 + 0.1352 * Kw - 0.007465 * Kw * Kw + 8.359 * TBR + (1.408 - 0.01063 * Kw) / TBR;
            }
        }
    }

    public TBPModelInterface getModel(String name) {
        name = name;
        if (name.equals("PedersenSRK")) {
            return new PedersenTBPModelSRK();
        } else if (name.equals("PedersenSRKHeavyOil")) {
            logger.info("using SRK heavy oil TBp.................");
            return new PedersenTBPModelSRKHeavyOil();
        } else if (name.equals("PedersenPR")) {
            return new PedersenTBPModelPR();
        } else if (name.equals("PedersenPRHeavyOil")) {
            logger.info("using PR heavy oil TBp.................");
            return new PedersenTBPModelPRHeavyOil();
        } else if (name.equals("RiaziDaubert")) {
            return new RiaziDaubert();
        } else {
            // System.out.println("not a valid TBPModelName.................");
            return new PedersenTBPModelSRK();
        }
    }
}
