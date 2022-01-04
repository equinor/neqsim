package neqsim.thermo.characterization;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * Recombine class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class Recombine {
    private static final long serialVersionUID = 1000;
    SystemInterface gas, oil;
    private SystemInterface recombinedSystem = null;
    private double GOR = 1000.0;
    private double oilDesnity = 0.8;

    /**
     * <p>
     * Constructor for Recombine.
     * </p>
     *
     * @param gas a {@link neqsim.thermo.system.SystemInterface} object
     * @param oil a {@link neqsim.thermo.system.SystemInterface} object
     */
    public Recombine(SystemInterface gas, SystemInterface oil) {}

    /**
     * <p>
     * runRecombination.
     * </p>
     *
     * @return a {@link neqsim.thermo.system.SystemInterface} object
     */
    public SystemInterface runRecombination() {
        return getRecombinedSystem();
    }

    /**
     * <p>
     * getGOR.
     * </p>
     *
     * @return the GOR
     */
    public double getGOR() {
        return GOR;
    }

    /**
     * <p>
     * setGOR.
     * </p>
     *
     * @param GOR the GOR to set
     */
    public void setGOR(double GOR) {
        this.GOR = GOR;
    }

    /**
     * <p>
     * Getter for the field <code>oilDesnity</code>.
     * </p>
     *
     * @return the oilDesnity
     */
    public double getOilDesnity() {
        return oilDesnity;
    }

    /**
     * <p>
     * Setter for the field <code>oilDesnity</code>.
     * </p>
     *
     * @param oilDesnity the oilDesnity to set
     */
    public void setOilDesnity(double oilDesnity) {
        this.oilDesnity = oilDesnity;
    }

    /**
     * <p>
     * Getter for the field <code>recombinedSystem</code>.
     * </p>
     *
     * @return the recombinedSystem
     */
    public SystemInterface getRecombinedSystem() {
        return recombinedSystem;
    }
}
