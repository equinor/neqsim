/*
 * OperationInterface.java
 *
 * Created on 2. oktober 2000, 22:14
 */

package neqsim.thermodynamicOperations;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * OperationInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface OperationInterface extends Runnable, java.io.Serializable {
    /**
     * <p>
     * displayResult.
     * </p>
     */
    public void displayResult();

    /**
     * <p>
     * getPoints.
     * </p>
     *
     * @param i a int
     * @return an array of {@link double} objects
     */
    public double[][] getPoints(int i);

    /**
     * <p>
     * addData.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @param data an array of {@link double} objects
     */
    public void addData(String name, double[][] data);

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
     * printToFile.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     */
    public void printToFile(String name);

    /**
     * <p>
     * get.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @return an array of {@link double} objects
     */
    public double[] get(String name);

    /**
     * <p>
     * getJFreeChart.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @return a {@link org.jfree.chart.JFreeChart} object
     */
    public org.jfree.chart.JFreeChart getJFreeChart(String name);

    /**
     * <p>
     * getThermoSystem.
     * </p>
     *
     * @return a {@link neqsim.thermo.system.SystemInterface} object
     */
    public SystemInterface getThermoSystem();
}
