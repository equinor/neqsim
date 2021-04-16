/*
 * LengthUnit.java
 *
 * Created on 25. januar 2002, 20:23
 */
package neqsim.util.unit;

/**
 *
 * @author esol
 * @version
 */
public class PressureUnit extends neqsim.util.unit.BaseUnit {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new LengthUnit
     */
    public PressureUnit(double value, String name) {
        super(value, name);
    }

    public double getValue(double val, String fromunit, String tounit) {
        invalue = val;
        return getConversionFactor(fromunit) / getConversionFactor(tounit) * invalue;
    }

    public double getValue(String tounit) {
        if (tounit.equals("barg")) {
            return (getConversionFactor(inunit) / getConversionFactor("bara")) * invalue - 1.01325;
        } else {
            return getConversionFactor(inunit) / getConversionFactor(tounit) * invalue;
        }
    }

    public double getConversionFactor(String name) {

        double conversionFactor = 1.0;
        switch (name) {
        case "bara":
            conversionFactor = 1.0;
            break;
        case "Pa":
            conversionFactor = 1.0e-5;
            break;
        case "MPa":
            conversionFactor = 10.0;
            break;
        }
        return conversionFactor;
    }

}
