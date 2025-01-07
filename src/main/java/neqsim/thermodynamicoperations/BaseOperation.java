/*
 * BaseOperation.java
 *
 * Created on 11. august 2001, 20:32
 */

package neqsim.thermodynamicoperations;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * Abstract BaseOperation class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public abstract class BaseOperation implements OperationInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  SystemInterface systemThermo = null;

  /**
   * <p>
   * Constructor for BaseOperation.
   * </p>
   */
  public BaseOperation() {}

  /** {@inheritDoc} */
  @Override
  public double[] get(String name) {
    return new double[3];
  }

  /** {@inheritDoc} */
  @Override
  public String[][] getResultTable() {
    return new String[10][3];
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {}

  /** {@inheritDoc} */
  @Override
  public double[][] getPoints(int i) {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public void addData(String name, double[][] data) {}
}
