    /*
 * PhaseSrkEos.java
 *
 * Created on 3. juni 2000, 14:38
 */

package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentPRvolcor;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class PhasePrEosvolcor extends PhasePrEos {

    private static final long serialVersionUID = 1000;

    /** Creates new PhaseSrkEos */
    public PhasePrEosvolcor() {
        super();
        thermoPropertyModelName = "PR-EoS-volcorr";
    }

    public double calcg() {
        return Math.log(1.0 - (getb()-getc()) / molarVolume);
    }

    public double calcf() {
        return (1.0 / (R * getB() * (delta1 - delta2))
                * Math.log((1.0 + (delta1 * getb() + getc()) / molarVolume) / (1.0 + (delta2 * getb()+getc()) / (molarVolume))));
    }
    
    @Override
    public double dFdV() {
        //return super.dFdV();
        return -numberOfMolesInPhase * gV() - getA() / temperature * fv();

    }

    public double getc() {return 0.7;}
    public double loc_C() {return 0.7;}
    public double getC() {return 0.7;}
    @Override
    public double gV() {
        return (getb()-getc()) / (molarVolume * (numberOfMolesInPhase * molarVolume + loc_C() - getB() ));
        //molarvolume is m^3/mol/10^5
        //old is-->return getb() / (molarVolume * (numberOfMolesInPhase * molarVolume - loc_B));
        //aks Dr. Soolbra whats the difference between getb and loc_B and
        // why the molar volume in the bracket is multiplied by the numberofmolesinphase (is it because of the units of molarvolume?)
    }

    @Override
    public double gVV() {
        double val1 = numberOfMolesInPhase * getMolarVolume();
        double val2 = val1 + getC() - getB();
        return -1.0 / (val2 * val2) + 1.0 / (val1 * val1);

        //old is -->double val1 = numberOfMolesInPhase * getMolarVolume();
        //double val2 = val1 + getC - getB();
        //return -1.0 / (val2 * val2) + 1.0 / (val1 * val1);
    }

    public double gVVV() {
        double val1 = numberOfMolesInPhase * getMolarVolume();
        double val2 = val1 + getC()  - getB();
        return 2.0 / (val2 * val2 * val2) - 2.0 / (val1 * val1 * val1);
    }

    @Override
    public double fv() {
       return -1.0 / (R * (numberOfMolesInPhase * molarVolume + delta1 * getB() + loc_C())
        * (numberOfMolesInPhase * molarVolume + delta2 * getB() + loc_C()));     
       
       //OLD IS--> return -1.0 / (R * (numberOfMolesInPhase * molarVolume + delta1 * loc_B)
      // * (numberOfMolesInPhase * molarVolume + delta2 * loc_B));
    }
    
    @Override
    public double fVV() {
        double val1 = (numberOfMolesInPhase * molarVolume + delta1 * getB() + loc_C());
        double val2 = (numberOfMolesInPhase * molarVolume + delta2 * getB() + loc_C());
        return 1.0 / (R * getB() * (delta1 - delta2)) * (-1.0 / (val1 * val1) + 1.0 / (val2 * val2));      
        
        //old is-->double val1 = (numberOfMolesInPhase * molarVolume + delta1 * loc_B);
        //double val2 = (numberOfMolesInPhase * molarVolume + delta2 * loc_B);
        //return 1.0 / (R * loc_B * (delta1 - delta2)) * (-1.0 / (val1 * val1) + 1.0 / (val2 * val2));
    }

    public double fVVV() {
        double val1 = numberOfMolesInPhase * molarVolume + getB() * delta1 + getC();
        double val2 = numberOfMolesInPhase * molarVolume + getB() * delta2 + getC();
        return 1.0 / (R * getB() * (delta1 - delta2)) * (2.0 / (val1 * val1 * val1) - 2.0 / (val2 * val2 * val2));
        
        //old is -->double val1 = numberOfMolesInPhase * molarVolume + getB() * delta1;
        //double val2 = numberOfMolesInPhase * molarVolume + getB() * delta2;
        //return 1.0 / (R * getB() * (delta1 - delta2)) * (2.0 / (val1 * val1 * val1) - 2.0 / (val2 * val2 * val2));
    }

    @Override
    public double dFdVdV() {
        return -numberOfMolesInPhase * gVV() - getA() * fVV() / temperature;
    }

    @Override
    public double dFdVdVdV() {
        return -numberOfMolesInPhase * gVVV() - getA() * fVVV() / temperature;
    }

    @Override
    public double dFdTdV() {
        return super.dFdTdV();
    }

    @Override
    public double F() {
        return super.F();
    }

    @Override
    public double molarVolume(double pressure, double temperature, double A, double B, int phase)
            throws neqsim.util.exception.IsNaNException, neqsim.util.exception.TooManyIterationsException {
                //BonV is the B/V-->look michelsen book page 94 eq. 137 (in other words the small b)
        double BonV = phase == 0 ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
                : pressure * getB() / (numberOfMolesInPhase * temperature * R);

        if (BonV < 0) {
            BonV = 1.0e-4;
        }
        if (BonV > 1.0) {
            BonV = 1.0 - 1.0e-4;
        }

        double BonVold = BonV, Btemp = getB(), h, dh, dhh, d1, d2, BonV2;
        int iterations = 0;

        if (Btemp < 0) {
            logger.info("b negative in volume calc");
        }
        setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
        boolean changeFase = false;
        double error = 1.0, errorOld = 1.0e10;

        do {
            errorOld = error;
            iterations++;
            BonVold = BonV;
            BonV2 = BonV * BonV;
            h = BonV - Btemp / numberOfMolesInPhase * dFdV()
                    - pressure * Btemp / (numberOfMolesInPhase * R * temperature);
            dh = 1.0 + Btemp / (BonV2) * (Btemp / numberOfMolesInPhase * dFdVdV());
            dhh = -2.0 * Btemp / (BonV2 * BonV) * (Btemp / numberOfMolesInPhase * dFdVdV())
                    - Btemp * Btemp / (BonV2 * BonV2) * (Btemp / numberOfMolesInPhase * dFdVdVdV());

            d1 = -h / dh;
            d2 = -dh / dhh;

            if (Math.abs(d1 / d2) <= 1.0) {
                BonV += d1 * (1.0 + 0.5 * d1 / d2);
            } else if (d1 / d2 < -1) {
                BonV += d1 * (1.0 + 0.5 * -1.0);
            } else if (d1 > d2) {
                BonV += d2;
                double hnew = h + d2 * dh;
                if (Math.abs(hnew) > Math.abs(h)) {
                    BonV = phase == 1 ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
                            : pressure * getB() / (numberOfMolesInPhase * temperature * R);
                }
            } else {
                BonV += d1 * (0.1);
            }

            if (BonV > 1) {
                BonV = 1.0 - 1.0e-6;
                BonVold = 100;
            }
            if (BonV < 0) {
                // BonV = Math.abs(BonV);
                BonV = 1.0e-10;
                BonVold = 10;
            }

            error = Math.abs((BonV - BonVold) / BonVold);
            // logger.info("error " + error);

            if (iterations > 150 && error > errorOld && !changeFase) {
                changeFase = true;
                BonVold = 10.0;
                BonV = phase == 1 ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
                        : pressure * getB() / (numberOfMolesInPhase * temperature * R);
            }

            setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
            Z = pressure * getMolarVolume() / (R * temperature);
            // logger.info("Math.abs((BonV - BonVold)) " + Math.abs((BonV - BonVold)));
        } while (Math.abs((BonV - BonVold) / BonVold) > 1.0e-10 && iterations < 300);
        // logger.info("pressure " + Z*R*temperature/molarVolume);
        // logger.info("error in volume " +
        // (-pressure+R*temperature/molarVolume-R*temperature*dFdV()) + " firstterm " +
        // (R*temperature/molarVolume) + " second " + R*temperature*dFdV());
        if (iterations >= 300) {
            throw new neqsim.util.exception.TooManyIterationsException(errorOld, phaseTypeName, iterations);
        }
        if (Double.isNaN(getMolarVolume())) {
            // A = calcA(this, temperature, pressure, numberOfComponents);
            // molarVolume(pressure, temperature, A, B, phase);
            throw new neqsim.util.exception.IsNaNException(phaseTypeName);
            // logger.info("BonV: " + BonV + " "+" itert: " + iterations +" " +h + " " +dh +
            // " B " + Btemp + " D " + Dtemp + " gv" + gV() + " fv " + fv() + " fvv" +
            // fVV());
        }
        return getMolarVolume();
    }


    @Override
    public double dFdTdT() {
        return super.dFdTdT();
    }

    @Override
    public double dFdT() {
        return super.dFdT();
    }


    @Override
    public PhasePrEosvolcor clone() {
      PhasePrEosvolcor clonedPhase = null;
        try {
            clonedPhase = (PhasePrEosvolcor) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return clonedPhase;
    }

    @Override
    public void addcomponent(String componentName, double moles, double molesInPhase, int compNumber) {
        super.addcomponent(molesInPhase);
        componentArray[compNumber] = new ComponentPRvolcor(componentName, moles, molesInPhase, compNumber);
    }

}