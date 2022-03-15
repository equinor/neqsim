package neqsim.standards.gasQuality;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * SulfurSpecificationMethod class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class SulfurSpecificationMethod extends neqsim.standards.Standard {
    SystemInterface thermoSystem;
    String unit = "ppm";
    double H2Scontent = 0.0;

    /**
     * <p>
     * Constructor for SulfurSpecificationMethod.
     * </p>
     *
     * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
     */
    public SulfurSpecificationMethod(SystemInterface thermoSystem) {
        super("SulfurSpecificationMethod", "Sulfur Specification Method", thermoSystem);
    }

    /** {@inheritDoc} */
    @Override
    public void calculate() {
        thermoSystem.init(0);
    }

    /** {@inheritDoc} */
    @Override
    public double getValue(String returnParameter, java.lang.String returnUnit) {
        thermoSystem.init(0);
        if (thermoSystem.getPhase(0).hasComponent("H2S")) {
            if (returnParameter.equals("H2S content")) {
                H2Scontent = thermoSystem.getPhase(0).getComponent("H2S").getx() * 1e6;
                return H2Scontent;
            }
        }
        return 0.0;
    }

    /** {@inheritDoc} */
    @Override
    public double getValue(String returnParameter) {
        return getValue(returnParameter, "");
    }

    /** {@inheritDoc} */
    @Override
    public String getUnit(String returnParameter) {
        return unit;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isOnSpec() {
        return true;
    }
}
