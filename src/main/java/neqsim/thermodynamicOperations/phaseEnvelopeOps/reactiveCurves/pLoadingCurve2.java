package neqsim.thermodynamicOperations.phaseEnvelopeOps.reactiveCurves;


import java.text.DecimalFormat;






import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.BaseOperation;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * pLoadingCurve2 class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class pLoadingCurve2 extends BaseOperation {
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
  int np = 0;
  // points[2] = new double[1000];
  int speceq = 0;

  /**
   * <p>
   * Constructor for pLoadingCurve2.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public pLoadingCurve2(SystemInterface system) {}

  /** {@inheritDoc} */
  @Override
  public void run() {
    int numbPoints = 50;
    double inscr = 0.2103842275;
    points[0] = new double[numbPoints];
    points[1] = new double[numbPoints];
    points[2] = new double[numbPoints];
    ThermodynamicOperations testOps = new ThermodynamicOperations(system);

    for (int k = 0; k < system.getPhases()[1].getNumberOfComponents(); k++) {
      points[k + 3] = new double[numbPoints];
      points[k + 3 + system.getPhases()[1].getNumberOfComponents()] = new double[numbPoints];
    }

    double molMDEA = system.getPhases()[1].getComponents()[2].getNumberOfMolesInPhase();
    system.getChemicalReactionOperations().solveChemEq(1);

    for (int i = 1; i < points[0].length; i++) {
      system.initBeta();
      system.init_x_y();
      try {
        testOps.bubblePointPressureFlash(false);
      } catch (Exception ex) {
        
      }
      
      points[0][i] = (inscr * (i - 1)) / molMDEA;
      points[1][i] = (system.getPressure());
      points[2][i] = (system.getPressure() * system.getPhase(0).getComponent(0).getx());

      for (int k = 0; k < system.getPhases()[1].getNumberOfComponents(); k++) {
        points[k + 3][i] = system.getPhases()[1].getComponents()[k].getx();
        points[k + 3 + system.getPhases()[1].getNumberOfComponents()][i] =
            system.getPhase(1).getActivityCoefficient(k, 1); // ,1);
      }
      
      system.setPressure(points[1][i]);
      
      system.addComponent("CO2", inscr);
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

  /*
   * public void createNetCdfFile(String name) { NetCdf2D file = new NetCdf2D();
   * file.setOutputFileName(name); file.setXvalues(points[0], "loading", "");
   * file.setYvalues(points[1], "total pressure", ""); file.setYvalues(points[2], " CO2 pressure",
   * ""); for (int k = 0; k < system.getPhases()[1].getNumberOfComponents(); k++) {
   * file.setYvalues(points[k + 3], "mol frac " +
   * system.getPhases()[1].getComponents()[k].getComponentName(), ""); file.setYvalues(points[k + 3
   * + system.getPhases()[1].getNumberOfComponents()], ("activity " +
   * system.getPhases()[1].getComponents()[k].getComponentName()), ""); } file.createFile(); }
   */

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
}
