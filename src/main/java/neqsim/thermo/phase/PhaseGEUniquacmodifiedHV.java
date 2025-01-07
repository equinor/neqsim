/*
 * PhaseGEUniquacmodifiedHV.java
 *
 * Created on 18. juli 2000, 20:30
 */

package neqsim.thermo.phase;

/**
 * <p>
 * PhaseGEUniquacmodifiedHV class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseGEUniquacmodifiedHV extends PhaseGEUniquac {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for PhaseGEUniquacmodifiedHV.
   * </p>
   */
  public PhaseGEUniquacmodifiedHV() {}

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, molesInPhase, compNumber);
    // todo: does not work? does not add component.
    // componentArray[compNumber] = new ComponentGEUniquacmodifiedHV(name, moles, molesInPhase,
    // compNumber);
    // creates PhaseGEUniquac type component
  }

  /** {@inheritDoc} */
  @Override
  public double getExcessGibbsEnergy(PhaseInterface phase, int numberOfComponents,
      double temperature, double pressure, PhaseType pt) {
    double GE = 0;

    /*
     * ComponentGEInterface[] comp_Array = (ComponentGEInterface[]) this.getcomponentArray();
     *
     * for (int i = 0; i < numberOfComponents; i++) { GE = GE + comp_Array[i].getx() *
     * Math.log(comp_Array[i].getGamma(phase, numberOfComponents, temperature, pressure, pt)); }
     */

    return R * temperature * GE * numberOfMolesInPhase;
  }
}
