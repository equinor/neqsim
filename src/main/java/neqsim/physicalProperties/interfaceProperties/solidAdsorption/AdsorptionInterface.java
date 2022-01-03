package neqsim.physicalProperties.interfaceProperties.solidAdsorption;

/**
 * <p>AdsorptionInterface interface.</p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public interface AdsorptionInterface extends neqsim.thermo.ThermodynamicConstantsInterface {

    /**
     * <p>calcAdorption.</p>
     *
     * @param phase a int
     */
    public void calcAdorption(int phase);

    /**
     * <p>getSurfaceExess.</p>
     *
     * @param component a int
     * @return a double
     */
    public double getSurfaceExess(int component);

    /**
     * <p>setSolidMaterial.</p>
     *
     * @param solidM a {@link java.lang.String} object
     */
    public void setSolidMaterial(String solidM);

    /**
     * <p>getSurfaceExcess.</p>
     *
     * @param componentName a {@link java.lang.String} object
     * @return a double
     */
    public double getSurfaceExcess(String componentName);
}
