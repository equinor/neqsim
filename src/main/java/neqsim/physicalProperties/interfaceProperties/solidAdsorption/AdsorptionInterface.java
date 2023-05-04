package neqsim.physicalProperties.interfaceProperties.solidAdsorption;

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
   * calcAdsorption.
   * </p>
   *
   * @param phase a int
   * @deprecated Replaced by {@link calcAdsorption}
   */
  @Deprecated
  public default void calcAdorption(int phase) {
    calcAdsorption(phase);
  }

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
   * getSurfaceExess.
   * </p>
   *
   * @param component a int
   * @return a double
   * @deprecated Replaced by {@link getSurfaceExcess}
   */
  @Deprecated
  public default double getSurfaceExess(int component) {
    return getSurfaceExcess(component);
  }

  /**
   * <p>
   * setSolidMaterial.
   * </p>
   *
   * @param solidM a {@link java.lang.String} object
   */
  public void setSolidMaterial(String solidM);

  /**
   * <p>
   * getSurfaceExcess.
   * </p>
   *
   * @param componentName a {@link java.lang.String} object
   * @return a double
   */
  public double getSurfaceExcess(String componentName);
}
