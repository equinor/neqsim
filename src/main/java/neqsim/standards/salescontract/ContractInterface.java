/*
 * ContractInterface.java
 *
 * Created on 15. juni 2004, 21:43
 */

package neqsim.standards.salescontract;

/**
 * ContractInterface interface.
 *
 * @author ESOL
 * @version $Id: $Id
 */
public interface ContractInterface {
  /**
   * setContract.
   *
   * @param name a {@link java.lang.String} object
   */
  public void setContract(String name);

  /**
   * getWaterDewPointTemperature.
   *
   * @return a double
   */
  public double getWaterDewPointTemperature();

  /**
   * setWaterDewPointTemperature.
   *
   * @param waterDewPointTemperature a double
   */
  public void setWaterDewPointTemperature(double waterDewPointTemperature);

  /**
   * getWaterDewPointSpecPressure.
   *
   * @return a double
   */
  public double getWaterDewPointSpecPressure();

  /**
   * setWaterDewPointSpecPressure.
   *
   * @param waterDewPointSpecPressure a double
   */
  public void setWaterDewPointSpecPressure(double waterDewPointSpecPressure);

  /**
   * runCheck.
   */
  public void runCheck();

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
   * getSpecificationsNumber.
   *
   * @return a int
   */
  public int getSpecificationsNumber();

  /**
   * setSpecificationsNumber.
   *
   * @param specificationsNumber a int
   */
  public void setSpecificationsNumber(int specificationsNumber);

  /**
   * display.
   */
  public void display();

  /**
   * getContractName.
   *
   * @return a {@link java.lang.String} object
   */
  public String getContractName();

  /**
   * setContractName.
   *
   * @param contractName a {@link java.lang.String} object
   */
  public void setContractName(String contractName);

  /**
   * Prints the contract.
   */
  public default void prettyPrint() {
    neqsim.thermo.util.readwrite.TablePrinter.printTable(getResultTable());
  }
}
