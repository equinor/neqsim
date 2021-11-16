/*
 * RateUnit.java
 *
 * Created on 25. januar 2002, 20:22
 */

package neqsim.util.unit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;

/**
 *
 * @author esol
 * @version
 */
public class RateUnit extends neqsim.util.unit.BaseUnit {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(RateUnit.class);

    double molarmass = 0.0, stddens = 0.0, boilp = 0.0;

    /** Creates new RateUnit */
    public RateUnit(double value, String name, double molarmass, double stddens, double boilp) {
        super(value, name);
        this.molarmass = molarmass;
        this.stddens = stddens;
        this.boilp = boilp;
    }

    @Override
    public double getSIvalue() {
        return getConversionFactor(inunit) / getConversionFactor("SI") * invalue;
    }

    @Override
    public double getValue(String tounit) {
        return getConversionFactor(inunit) / getConversionFactor(tounit) * invalue;
    }

    public double getConversionFactor(String name) {
        double mol_m3 = 0.0;
        double mol_Sm3 = 101325.0 / (ThermodynamicConstantsInterface.R * standardStateTemperature);
        if (boilp < 25) {
            mol_m3 = 101325.0 / (ThermodynamicConstantsInterface.R * standardStateTemperature);
        } else {
            mol_m3 = 1.0 / (molarmass) * stddens * 1000;
        }

        if (name.equals("mole/sec") || name.equals("mol/sec") || name.equals("SI")
                || name.equals("mol")) {
            factor = 1.0;
        } else if (name.equals("Nlitre/min")) {
            factor = 1.0 / 60.0 * mol_m3 / 1000.0;
        } else if (name.equals("Nlitre/sec")) {
            factor = mol_m3 / 1000.0;
        } else if (name.equals("Am3/hr")) {
            factor = 1.0 / molarmass / 3600.0 * stddens;
        } else if (name.equals("Am3/day")) {
            factor = 1.0 / molarmass / (3600.0 * 24.0) * stddens;
        } else if (name.equals("Am3/min")) {
            factor = 1.0 / molarmass / 60.0 * stddens;
        } else if (name.equals("Am3/sec")) {
            factor = 1.0 / molarmass * stddens;
        } else if (name.equals("kg/sec")) {
            factor = 1.0 / (molarmass);
        } else if (name.equals("kg/min")) {
            factor = 1.0 / 60.0 * 1.0 / (molarmass);
        } else if (name.equals("kg/hr")) {
            factor = 1.0 / molarmass / 3600.0;
        } else if (name.equals("kg/day")) {
            factor = 1.0 / molarmass / (3600.0 * 24.0);
        } else if (name.equals("Sm^3/sec") | name.equals("Sm3/sec")) {
            factor = mol_Sm3;
        } else if (name.equals("Sm^3/min") | name.equals("Sm3/min")) {
            factor = 1.0 / 60.0 * mol_Sm3;
        } else if (name.equals("Sm^3/hr") | name.equals("Sm3/hr")) {
            factor = 1.0 / 60.0 / 60.0 * mol_Sm3;
        } else if (name.equals("Sm^3/day") || name.equals("Sm3/day")) {
            factor = 1.0 / 60.0 / 60.0 / 24.0 * mol_Sm3;
        } else if (name.equals("MSm^3/day") || name.equals("MSm3/day")) {
            factor = 1.0e6 * mol_Sm3 / (3600.0 * 24.0);
        } else if (name.equals("MSm^3/hr") || name.equals("MSm3/hr")) {
            factor = 1.0e6 * mol_Sm3 / (3600.0);
        } else {
            logger.error("unit not supported " + name);
        }

        return factor;
    }
}
