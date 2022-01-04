/*
 * System_SRK_EOS.java
 *
 * Created on 8. april 2000, 23:05
 */

package neqsim.thermo.system;
/**
 *
 * @author Even Solbraa
 * @version
 */

/**
 * This class defines a thermodynamic system using the UMR-PRU equation of state
 */
public class SystemUMRPRUEos extends SystemPrEos {
    private static final long serialVersionUID = 1000;

    /**
     * Creates a thermodynamic system using the SRK equation of state.
     */
    // SystemSrkEos clonedSystem;
    public SystemUMRPRUEos() {
        super();
        setBmixType(1);
        modelName = "UMR-PRU-EoS";
        attractiveTermNumber = 1;
    }

    /**
     * <p>
     * Constructor for SystemUMRPRUEos.
     * </p>
     *
     * @param T a double
     * @param P a double
     */
    public SystemUMRPRUEos(double T, double P) {
        super(T, P);
        setBmixType(1);
        modelName = "UMR-PRU-EoS";
        attractiveTermNumber = 1;
        CapeOpenProperties11 = new String[] {"speedOfSound", "jouleThomsonCoefficient",
                "internalEnergy", "internalEnergy.Dtemperature", "gibbsEnergy", "helmholtzEnergy",
                "fugacityCoefficient", "logFugacityCoefficient",
                "logFugacityCoefficient.Dtemperature", "logFugacityCoefficient.Dpressure",
                "logFugacityCoefficient.Dmoles", "enthalpy", "enthalpy.Dtemperature", "entropy",
                "heatCapacityCp", "heatCapacityCv", "density", "volume"};
    }

    /**
     * <p>
     * Constructor for SystemUMRPRUEos.
     * </p>
     *
     * @param T a double
     * @param P a double
     * @param solidCheck a boolean
     */
    public SystemUMRPRUEos(double T, double P, boolean solidCheck) {
        super(T, P, solidCheck);
        setBmixType(1);
        attractiveTermNumber = 1;
        modelName = "UMR-PRU-EoS";
    }

    /** {@inheritDoc} */
    @Override
    public SystemUMRPRUEos clone() {
        SystemUMRPRUEos clonedSystem = null;
        try {
            clonedSystem = (SystemUMRPRUEos) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return clonedSystem;
    }

    /**
     * <p>
     * commonInitialization.
     * </p>
     */
    public void commonInitialization() {
        setImplementedCompositionDeriativesofFugacity(true);
        setImplementedPressureDeriativesofFugacity(true);
        setImplementedTemperatureDeriativesofFugacity(true);
    }
}
