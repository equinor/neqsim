package neqsim.thermo.system;

/**
 * This class defines a thermodynamic system using the UMR-PRU with MC paramters equation of state
 * 
 * @author Even Solbraa
 */
public class SystemUMRPRUMCEos extends SystemUMRPRUEos {
    private static final long serialVersionUID = 1000;

    public SystemUMRPRUMCEos() {
        super();
        setBmixType(1);
        modelName = "UMR-PRU-MC-EoS";
        attractiveTermNumber = 13;
    }

    /**
     * <p>
     * Constructor for SystemUMRPRUMCEos.
     * </p>
     *
     * @param T a double
     * @param P a double
     */
    public SystemUMRPRUMCEos(double T, double P) {
        super(T, P);
        setBmixType(1);
        modelName = "UMR-PRU-MC-EoS";
        attractiveTermNumber = 13;
        CapeOpenProperties11 = new String[] {"speedOfSound", "jouleThomsonCoefficient",
                "internalEnergy", "internalEnergy.Dtemperature", "gibbsEnergy", "helmholtzEnergy",
                "fugacityCoefficient", "logFugacityCoefficient",
                "logFugacityCoefficient.Dtemperature", "logFugacityCoefficient.Dpressure",
                "logFugacityCoefficient.Dmoles", "enthalpy", "enthalpy.Dtemperature", "entropy",
                "heatCapacityCp", "heatCapacityCv", "density", "volume"};
    }

    /**
     * <p>
     * Constructor for SystemUMRPRUMCEos.
     * </p>
     *
     * @param T a double
     * @param P a double
     * @param solidCheck a boolean
     */
    public SystemUMRPRUMCEos(double T, double P, boolean solidCheck) {
        super(T, P, solidCheck);
        setBmixType(1);
        attractiveTermNumber = 13;
        modelName = "UMR-PRU-MC-EoS";
    }

    /** {@inheritDoc} */
    @Override
    public SystemUMRPRUMCEos clone() {
        SystemUMRPRUMCEos clonedSystem = null;
        try {
            clonedSystem = (SystemUMRPRUMCEos) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return clonedSystem;
    }
}
