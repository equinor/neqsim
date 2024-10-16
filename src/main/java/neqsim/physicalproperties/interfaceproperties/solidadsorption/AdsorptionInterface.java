package neqsim.physicalproperties.interfaceproperties.solidadsorption;

/**
 * <p>
 * AdsorptionInterface interface.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public interface AdsorptionInterface extends neqsim.thermo.ThermodynamicConstantsInterface {
  /**
   * <p>
   * calcAdsorption.
   * </p>
   *
   * @param phase a int
   */
  public void calcAdsorption(int phase);

  /**
   * <p>
   * getSurfaceExcess.
   * </p>
   *
   * @param component a int
   * @return a double
   */
  public double getSurfaceExcess(int component);

  /**
   * <p>
   * getSurfaceExcess.
   * </p>
   *
   * @param componentName a {@link java.lang.String} object
   * @return a double
   */
  public double getSurfaceExcess(String componentName);

  /**
   * <p>
   * setSolidMaterial.
   * </p>
   *
   * @param solidM a {@link java.lang.String} object
   */
  public void setSolidMaterial(String solidM);
}
