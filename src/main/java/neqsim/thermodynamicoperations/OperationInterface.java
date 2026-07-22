/*
 * OperationInterface.java
 *
 * Created on 2. oktober 2000, 22:14
 */

package neqsim.thermodynamicoperations;

import neqsim.thermo.system.SystemInterface;

/**
 * OperationInterface interface.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface OperationInterface extends Runnable, java.io.Serializable {
  /**
   * displayResult.
   */
  public void displayResult();

  /**
   * getPoints.
   *
   * @param i a int
   * @return an array of type double
   */
  public double[][] getPoints(int i);

  /**
   * addData.
   *
   * @param name a {@link java.lang.String} object
   * @param data an array of type double
   */
  public void addData(String name, double[][] data);

  /**
   * getResultTable.
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[][] getResultTable();

  /**
   * printToFile.
   *
   * @param name a {@link java.lang.String} object
   */
  public void printToFile(String name);

  /**
   * get.
   *
   * @param name a {@link java.lang.String} object
   * @return an array of type double
   */
  public double[] get(String name);

  /**
   * Returns the named result array, or the supplied default if the key is not found or the result is {@code null}. This
   * overload makes Python/JPype usage natural: {@code pe_data.get("dewT", emptyArray)} mirrors the Python dict
   * {@code .get(key, default)} idiom.
   *
   * @param name the result key (e.g. "dewT", "bubP")
   * @param defaultValue the array to return when the key is absent or maps to {@code null}
   * @return the result array, or {@code defaultValue} if not found
   */
  default double[] get(String name, double[] defaultValue) {
    double[] result = get(name);
    return result != null ? result : defaultValue;
  }

  /**
   * getJFreeChart.
   *
   * @param name a {@link java.lang.String} object
   * @return a {@link org.jfree.chart.JFreeChart} object
   */
  public org.jfree.chart.JFreeChart getJFreeChart(String name);

  /**
   * getThermoSystem.
   *
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SystemInterface getThermoSystem();
}
