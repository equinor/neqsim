/*
 * PhysicalPropertyMethod.java
 *
 * Created on 3. august 2001, 22:49
 */

package neqsim.physicalProperties.physicalPropertyMethods;




/**
 * <p>
 * PhysicalPropertyMethod class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class PhysicalPropertyMethod implements PhysicalPropertyMethodInterface {
  private static final long serialVersionUID = 1000;
  

  /**
   * <p>
   * Constructor for PhysicalPropertyMethod.
   * </p>
   */
  public PhysicalPropertyMethod() {}

  /** {@inheritDoc} */
  @Override
  public PhysicalPropertyMethod clone() {
    PhysicalPropertyMethod properties = null;

    try {
      properties = (PhysicalPropertyMethod) super.clone();
    } catch (Exception ex) {
      
    }

    return properties;
  }

  /** {@inheritDoc} */
  @Override
  public void setPhase(
      neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface phase) {}

  /** {@inheritDoc} */
  @Override
  public void tuneModel(double val, double temperature, double pressure) {
    throw new UnsupportedOperationException("Unimplemented method 'tuneModel'");
  }
  // should contain phase objects ++ get diffusivity methods .. more ?
}
