/*
 * System_SRK_EOS.java
 *
 * Created on 8. april 2000, 23:05
 */

package neqsim.thermo.system;
/**
 *
 * @author  Even Solbraa
 * @version
 */

/**
 * This class defines a thermodynamic system using the UMR-PRU with MC paramters
 * equation of state
 */
public class SystemUMRPRUMCEos extends SystemUMRPRUEos {

    private static final long serialVersionUID = 1000;

    /** Creates a thermodynamic system using the SRK equation of state. */
    // SystemSrkEos clonedSystem;

    public SystemUMRPRUMCEos() {
        super();
        setBmixType(1);
        modelName = "UMR-PRU-MC-EoS";
        attractiveTermNumber = 13;
    }

    public SystemUMRPRUMCEos(double T, double P) {
        super(T, P);
        setBmixType(1);
        modelName = "UMR-PRU-MC-EoS";
        attractiveTermNumber = 13;
        CapeOpenProperties11 = new String[] { "speedOfSound", "jouleThomsonCoefficient", "internalEnergy",
                "internalEnergy.Dtemperature", "gibbsEnergy", "helmholtzEnergy", "fugacityCoefficient",
                "logFugacityCoefficient", "logFugacityCoefficient.Dtemperature", "logFugacityCoefficient.Dpressure",
                "logFugacityCoefficient.Dmoles", "enthalpy", "enthalpy.Dtemperature", "entropy", "heatCapacityCp",
                "heatCapacityCv", "density", "volume" };
    }

    public SystemUMRPRUMCEos(double T, double P, boolean solidCheck) {
        super(T, P, solidCheck);
        setBmixType(1);
        attractiveTermNumber = 13;
        modelName = "UMR-PRU-MC-EoS";
    }

    @Override
	public Object clone() {
        SystemUMRPRUMCEos clonedSystem = null;
        try {
            clonedSystem = (SystemUMRPRUMCEos) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return clonedSystem;
    }

}