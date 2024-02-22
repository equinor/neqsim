/*
 * StandardInterface.java
 *
 * Created on 13. juni 2004, 23:29
 */

package neqsim.standards;

import neqsim.standards.salesContract.ContractInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * StandardInterface interface.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public interface StandardInterface {
  /**
   * <p>
   * getValue.
   * </p>
   *
   * @param returnParameter a {@link java.lang.String} object
   * @param returnUnit a {@link java.lang.String} object
   * @return a double
   */
  double getValue(java.lang.String returnParameter, java.lang.String returnUnit);

  /**
   * <p>
   * getValue.
   * </p>
   *
   * @param returnParameter a {@link java.lang.String} object
   * @return a double
   */
  double getValue(java.lang.String returnParameter);

  /**
   * <p>
   * getUnit.
   * </p>
   *
   * @param returnParameter a {@link java.lang.String} object
   * @return a {@link java.lang.String} object
   */
  public String getUnit(String returnParameter);

  /**
   * <p>
   * calculate.
   * </p>
   */
  public void calculate();

  /**
   * <p>
   * isOnSpec.
   * </p>
   *
   * @return a boolean
   */
  public boolean isOnSpec();

  /**
   * <p>
   * getSalesContract.
   * </p>
   *
   * @return a {@link neqsim.standards.salesContract.ContractInterface} object
   */
  public ContractInterface getSalesContract();

  /**
   * <p>
   * setSalesContract.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public void setSalesContract(String name);

  /**
   * <p>
   * setSalesContract.
   * </p>
   *
   * @param salesContract a {@link neqsim.standards.salesContract.ContractInterface} object
   */
  public void setSalesContract(ContractInterface salesContract);

  /**
   * <p>
   * getName.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getName();

  /**
   * <p>
   * getStandardDescription.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getStandardDescription();

  /**
   * <p>
   * createTable.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @return an array of {@link java.lang.String} objects
   */
  public String[][] createTable(String name);

  /**
   * <p>
   * display.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public void display(String name);

  /**
   * <p>
   * getThermoSystem.
   * </p>
   *
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SystemInterface getThermoSystem();

  /**
   * <p>
   * setThermoSystem.
   * </p>
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void setThermoSystem(SystemInterface thermoSystem);

  /**
   * <p>
   * getResultTable.
   * </p>
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[][] getResultTable();

  /**
   * <p>
   * setResultTable.
   * </p>
   *
   * @param resultTable an array of {@link java.lang.String} objects
   */
  public void setResultTable(String[][] resultTable);

  /**
   * <p>
   * Getter for the field <code>referencePressure</code>.
   * </p>
   *
   * @return Reference pressure in bara
   */
  public double getReferencePressure();

  /**
   * <p>
   * Setter for the field <code>referencePressure</code>.
   * </p>
   *
   * @param referencePressure Reference pressure to set in in bara
   */
  public void setReferencePressure(double referencePressure);
}
