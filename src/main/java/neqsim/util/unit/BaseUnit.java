package neqsim.util.unit;

/**
 * <p>
 * BaseUnit class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class BaseUnit implements Unit, neqsim.thermo.ThermodynamicConstantsInterface {
    private static final long serialVersionUID = 1000;

    protected double SIvalue = 0.0, invalue = 0.0, factor = 1.0;
    protected String inunit = null;

    /**
     * <p>
     * Constructor for BaseUnit.
     * </p>
     */
    public BaseUnit() {}

    /**
     * <p>
     * Constructor for BaseUnit.
     * </p>
     *
     * @param value a double
     * @param name a {@link java.lang.String} object
     */
    public BaseUnit(double value, String name) {
        this.invalue = value;
        this.inunit = name;
    }

    /** {@inheritDoc} */
    @Override
    public double getSIvalue() {
        return SIvalue;
    }

    /** {@inheritDoc} */
    @Override
    public double getValue(String fromunit) {
        return 0.0;
    }

    /** {@inheritDoc} */
    @Override
    public double getValue(double val, String fromunit, String tounit) {
        return 0.0;
    }
}
