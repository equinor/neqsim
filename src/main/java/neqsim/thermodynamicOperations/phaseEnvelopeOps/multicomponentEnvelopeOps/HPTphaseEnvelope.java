/*
 * HPTphaseEnvelope.java
 *
 * Created on 14. oktober 2000, 21:59
 */

package neqsim.thermodynamicOperations.phaseEnvelopeOps.multicomponentEnvelopeOps;







import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.BaseOperation;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * HPTphaseEnvelope class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class HPTphaseEnvelope extends BaseOperation {
  private static final long serialVersionUID = 1000;
  

  double[][] points = new double[10][10];
  SystemInterface system;
  ThermodynamicOperations testOps;
  
  
  
  double startPressure = 1;
  double endPressure = 0;
  double startTemperature = 160;
  double endTemperature = 0;

  /**
   * <p>
   * Constructor for HPTphaseEnvelope.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public HPTphaseEnvelope(SystemInterface system) {}

  /** {@inheritDoc} */
  @Override
  public void run() {
    int np = 0;

    for (int i = 0; i < 10; i++) {
      system.setPressure(i * 0.5 + startPressure);
      for (int j = 0; j < 10; j++) {
        np++;
        if (np % 2 == 0) {
          
          
        }

        system.setTemperature(startTemperature + j);
        testOps.TPflash();
        system.init(3);
        points[i][j] = system.getEnthalpy();
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void displayResult() {}

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {}

  /** {@inheritDoc} */
  @Override
  public double[][] getPoints(int i) {
    return points;
  }

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public String[][] getResultTable() {
    return null;
  }
}
