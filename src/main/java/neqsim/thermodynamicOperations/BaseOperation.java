

/*
 * BaseOperation.java
 *
 * Created on 11. august 2001, 20:32
 */

package neqsim.thermodynamicOperations;

import neqsim.thermo.system.SystemInterface;

/**
 * @author esol
 * @version
 */
public abstract class BaseOperation implements OperationInterface {
    private static final long serialVersionUID = 1000;

    SystemInterface systemThermo = null;;

    /** Creates new BaseOperation */
    public BaseOperation() {}

    @Override
    public double[] get(String name) {
        return new double[3];
    }

    @Override
    public String[][] getResultTable() {
        return new String[10][3];
    }

    @Override
    public SystemInterface getThermoSystem() {
        return null;
    }

    @Override
    public org.jfree.chart.JFreeChart getJFreeChart(String name) {
        return null;
    }

    @Override
    public void printToFile(String name) {}

    @Override
    public void createNetCdfFile(String name) {}

    @Override
    public double[][] getPoints(int i) {
        return null;
    }

    @Override
    public void addData(String name, double[][] data) {}
}
