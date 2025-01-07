package neqsim.thermodynamicoperations.phaseenvelopeops.reactivecurves;

import java.awt.FlowLayout;
import java.text.DecimalFormat;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.datapresentation.jfreechart.Graph2b;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.OperationInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * pLoadingCurve class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PloadingCurve implements OperationInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PloadingCurve.class);

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
  JProgressBar monitor;
  JFrame mainFrame;
  JPanel mainPanel;

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
  public PloadingCurve() {}

  /**
   * <p>
   * Constructor for pLoadingCurve.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public PloadingCurve(SystemInterface system) {
    this.system = system;
    lnOldOldK = new double[system.getPhases()[0].getNumberOfComponents()];
    lnOldK = new double[system.getPhases()[0].getNumberOfComponents()];
    lnK = new double[system.getPhases()[0].getNumberOfComponents()];
    oldDeltalnK = new double[system.getPhases()[0].getNumberOfComponents()];
    deltalnK = new double[system.getPhases()[0].getNumberOfComponents()];
    mainFrame = new JFrame("Progress Bar");
    mainPanel = new JPanel();
    mainPanel.setSize(200, 100);
    mainFrame.getContentPane().setLayout(new FlowLayout());
    mainPanel.setLayout(new FlowLayout());
    mainFrame.setSize(200, 100);
    monitor = new JProgressBar(0, 1000);
    monitor.setSize(200, 100);
    monitor.setStringPainted(true);
    mainPanel.add(monitor);
    mainFrame.getContentPane().add(mainPanel);
    mainFrame.setVisible(true);
  }

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
      points[1][i] = (system.getPhases()[1].getComponent(0).getFugacityCoefficient()
          * system.getPhases()[1].getComponent(0).getx() * system.getPressure());

      for (int k = 0; k < system.getPhases()[1].getNumberOfComponents(); k++) {
        points[k + 2][i] = system.getPhases()[1].getComponent(k).getx();
        points[k + 2 + system.getPhases()[1].getNumberOfComponents()][i] =
            system.getPhases()[1].getActivityCoefficient(k, 1);
      }
      logger.info("point: " + points[0][i] + "  " + points[1][i]);
      system.setPressure(points[1][i]);
      logger.info("ph: " + system.getPhases()[1].getpH());
      system.addComponent(0, inscr, 1);
    }
    mainFrame.setVisible(false);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    DecimalFormat nf = new DecimalFormat();
    nf.setMaximumFractionDigits(1);
    nf.applyPattern("####.#");

    double TC = system.getTC();
    double PC = system.getPC();
    logger.info("tc : " + TC + "  PC : " + PC);
    String[] navn = {"CO2 fugacity", "", "", ""};
    String title = "CO2 vapour pressure";

    Graph2b graph2 = new Graph2b(points, navn, title, "loading [-]", "Fugacity CO2 [bar]");
    graph2.setVisible(true);
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {
    neqsim.datapresentation.DataHandling printDat = new neqsim.datapresentation.DataHandling();
    printDat.printToFile(points, name);
  }

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
