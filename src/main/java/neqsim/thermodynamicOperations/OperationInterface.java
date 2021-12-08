/*
 * OperationInterface.java
 *
 * Created on 2. oktober 2000, 22:14
 */

package neqsim.thermodynamicOperations;

import neqsim.thermo.system.SystemInterface;

/**
 * @author Even Solbraa
 * @version
 */
public interface OperationInterface extends Runnable, java.io.Serializable {
    public void displayResult();

    public double[][] getPoints(int i);

    public void addData(String name, double[][] data);

    public String[][] getResultTable();

    public void createNetCdfFile(String name);

    public void printToFile(String name);

    public double[] get(String name);

    public org.jfree.chart.JFreeChart getJFreeChart(String name);

    public SystemInterface getThermoSystem();
}
