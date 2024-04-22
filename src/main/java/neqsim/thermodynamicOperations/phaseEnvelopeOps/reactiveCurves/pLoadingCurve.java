package neqsim.thermodynamicOperations.phaseEnvelopeOps.reactiveCurves;


import java.text.DecimalFormat;






import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.OperationInterface;

/**
 * <p>
 * pLoadingCurve class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class pLoadingCurve implements OperationInterface {
  private static final long serialVersionUID = 1000;
  

  SystemInterface system;
  int i;
  int j = 0;
  int nummer = 0;
  int iterations = 0;
  int maxNumberOfIterations = 10000;
  double gibbsEnergy = 0;
  double gibbsEnergyOld = 0;
  double Kold;
  double deviation = 0;
  double g0 = 0;
  double g1 = 0;
  double lnOldOldK[];
  double lnK[];
  double[] lnOldK;
  double oldDeltalnK[];
  double deltalnK[];
  double[] tm = {1, 1};
  double beta = 1e-5;
  int lowestGibbsEnergyPhase = 0; // lowestGibbsEnergyPhase
  
  
  

  double temp = 0;
  double pres = 0;
  double startPres = 0;
  double[][] points = new double[35][];

  boolean moreLines = false;
  // points[2] = new double[1000];
  int speceq = 0;

  /**
   * <p>
   * Constructor for pLoadingCurve.
   * </p>
   */
  public pLoadingCurve() {}

  /**
   * <p>
   * Constructor for pLoadingCurve.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public pLoadingCurve(SystemInterface system) {}

  /** {@inheritDoc} */
  @Override
  public void run() {
    int numbPoints = 50;
    double inscr = 0.2275;
    points[0] = new double[numbPoints];
    points[1] = new double[numbPoints];

    for (int k = 0; k < system.getPhases()[1].getNumberOfComponents(); k++) {
      points[k + 2] = new double[numbPoints];
      points[k + 2 + system.getPhases()[1].getNumberOfComponents()] = new double[numbPoints];
    }

    double molMDEA = system.getPhases()[1].getComponents()[2].getNumberOfMolesInPhase();
    system.getChemicalReactionOperations().solveChemEq(0);

    for (int i = 1; i < points[0].length; i++) {
      system.init_x_y();
      system.getChemicalReactionOperations().solveChemEq(1);

      points[0][i] = (inscr * (i - 1)) / molMDEA;
      points[1][i] = (system.getPhases()[1].getComponents()[0].getFugacityCoefficient()
          * system.getPhases()[1].getComponents()[0].getx() * system.getPressure());

      for (int k = 0; k < system.getPhases()[1].getNumberOfComponents(); k++) {
        points[k + 2][i] = system.getPhases()[1].getComponents()[k].getx();
        points[k + 2 + system.getPhases()[1].getNumberOfComponents()][i] =
            system.getPhases()[1].getActivityCoefficient(k, 1);
      }
      
      system.setPressure(points[1][i]);
      
      system.addComponent(0, inscr, 1);
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
  public double[] get(String name) {
    return null;
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

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    return system;
  }

  /** {@inheritDoc} */
  @Override
  public void addData(String name, double[][] data) {}
}
