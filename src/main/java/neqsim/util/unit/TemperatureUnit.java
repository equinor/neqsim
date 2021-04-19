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
public class TemperatureUnit extends neqsim.util.unit.BaseUnit {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new LengthUnit
     */
    public TemperatureUnit(double value, String name) {
        super(value, name);
    }

    @Override
	public double getValue(double val, String fromunit, String tounit) {
        invalue = val;
        return getConversionFactor(fromunit) / getConversionFactor(tounit) * invalue;
    }

    @Override
	public double getValue(String tounit) {
        if (tounit.equals("C")) {
            return getConversionFactor(inunit) / getConversionFactor("K") * invalue - 273.15;
        }
        return getConversionFactor(inunit) / getConversionFactor(tounit) * invalue;
    }

    public double getConversionFactor(String name) {

        double conversionFactor = 1.0;
        switch (name) {
        case "K":
            conversionFactor = 1.0;
            break;
        case "R":
            conversionFactor = 5.0 / 9.0;
            break;
        }
        return conversionFactor;
    }

}
