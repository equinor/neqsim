/*
 * StandardInterface.java
 *
 * Created on 13. juni 2004, 23:29
 */

package neqsim.standards;

import neqsim.standards.salescontract.ContractInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * StandardInterface interface.
 *
 * @author ESOL
 * @version $Id: $Id
 */
public interface StandardInterface {
  /**
   * getValue.
   *
   * @param returnParameter a {@link java.lang.String} object
   * @param returnUnit a {@link java.lang.String} object
   * @return a double
   */
  double getValue(String returnParameter, String returnUnit);

  /**
   * getValue.
   *
   * @param returnParameter a {@link java.lang.String} object
   * @return a double
   */
  double getValue(String returnParameter);

  /**
   * getUnit.
   *
   * @param returnParameter a {@link java.lang.String} object
   * @return a {@link java.lang.String} object
   */
  public String getUnit(String returnParameter);

  /**
   * calculate.
   */
  public void calculate();

  /**
   * isOnSpec.
   *
   * @return a boolean
   */
  public boolean isOnSpec();

  /**
   * getSalesContract.
   *
   * @return a {@link neqsim.standards.salescontract.ContractInterface} object
   */
  public ContractInterface getSalesContract();

  /**
   * setSalesContract.
   *
   * @param name a {@link java.lang.String} object
   */
  public void setSalesContract(String name);

  /**
   * setSalesContract.
   *
   * @param salesContract a {@link neqsim.standards.salescontract.ContractInterface} object
   */
  public void setSalesContract(ContractInterface salesContract);

  /**
   * getName.
   *
   * @return a {@link java.lang.String} object
   */
  public String getName();

  /**
   * getStandardDescription.
   *
   * @return a {@link java.lang.String} object
   */
  public String getStandardDescription();

  /**
   * createTable.
   *
   * @param name a {@link java.lang.String} object
   * @return an array of {@link java.lang.String} objects
   */
  public String[][] createTable(String name);

  /**
   * display.
   *
   * @param name a {@link java.lang.String} object
   */
  public void display(String name);

  /**
   * getThermoSystem.
   *
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SystemInterface getThermoSystem();

  /**
   * setThermoSystem.
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void setThermoSystem(SystemInterface thermoSystem);

  /**
   * getResultTable.
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[][] getResultTable();

  /**
   * setResultTable.
   *
   * @param resultTable an array of {@link java.lang.String} objects
   */
  public void setResultTable(String[][] resultTable);

  /**
   * Getter for the field <code>referencePressure</code>.
   *
   * @return Reference pressure in bara
   */
  public double getReferencePressure();

  /**
   * Setter for the field <code>referencePressure</code>.
   *
   * @param referencePressure Reference pressure to set in in bara
   */
  public void setReferencePressure(double referencePressure);
}
