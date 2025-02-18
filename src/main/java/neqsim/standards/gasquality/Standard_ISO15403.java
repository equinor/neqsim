package neqsim.standards.gasquality;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * Standard_ISO15403 class.
 * </p>
 *
 * @author ESOL
 */
public class Standard_ISO15403 extends neqsim.standards.Standard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private double NM;
  private double MON;

  /**
   * <p>
   * Constructor for Standard_ISO15403.
   * </p>
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public Standard_ISO15403(SystemInterface thermoSystem) {
    super("Standard_ISO15403",
        "Natural gas — Natural gas for use as a compressed fuel for vehicles", thermoSystem);
  }

  private double getMolefraction(String name) {
    if (thermoSystem.getPhase(0).hasComponent(name)) {
      return thermoSystem.getComponent(name).getz();
    } else {
      return 0.0;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    MON = 137.78 * getMolefraction("methane") + 29.948 * getMolefraction("ethane")
        - 18.193 * getMolefraction("propane")
        - 167.062 * (getMolefraction("n-butane") + getMolefraction("i-butane"))
        + 181.233 * getMolefraction("CO2") + 26.944 * getMolefraction("nitrogen");

    NM = 1.445 * MON - 103.42;
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    if (returnParameter.equals("MON")) {
      return MON;
    } else if (returnParameter.equals("NM")) {
      return NM;
    } else {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this, "getValue",
          "returnParameter", "parameter not supported"));
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter, String returnUnit) {
    return getValue(returnParameter);
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit(String returnParameter) {
    return "";
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    return true;
  }
}
