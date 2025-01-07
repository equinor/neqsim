package neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops;

import java.awt.FlowLayout;
import java.text.DecimalFormat;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.datapresentation.jfreechart.Graph2b;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.BaseOperation;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * pTphaseEnvelopeNew class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PTphaseEnvelopeNew extends BaseOperation {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PTphaseEnvelopeNew.class);

  Graph2b graph2 = null;
  SystemInterface system;
  double[] cricondenTherm = new double[3];
  double[] cricondenBar = new double[3];
  double phaseFraction = 1e-10;
  int i;
  int j = 0;
  int nummer = 0;
  int iterations = 0;
  int maxNumberOfIterations = 10000;
  double lowPres = 1.0;
  boolean outputToFile = false;
  JProgressBar monitor;
  JFrame mainFrame;
  String fileName = "c:/file";
  JPanel mainPanel;
  double temp = 0;
  double pres = 0;
  double[][] points = new double[2][];
  double[] pointsH = new double[10000];
  double[][] pointsH2 = new double[4][];
  double[] pointsV = new double[10000];

  double[][] pointsV2 = new double[4][];
  double[] pointsS = new double[10000];
  double[][] pointsS2 = new double[4][];
  public double[][] points2 = new double[4][];
  int np = 0;
  int speceq = 0;

  /**
   * <p>
   * Constructor for pTphaseEnvelopeNew.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param name a {@link java.lang.String} object
   * @param phaseFraction a double
   * @param lowPres a double
   */
  public PTphaseEnvelopeNew(SystemInterface system, String name, double phaseFraction,
      double lowPres) {
    this.system = system;
    this.phaseFraction = phaseFraction;

    this.lowPres = lowPres;

    if (name != null) {
      outputToFile = true;
      fileName = name;
    }
    mainFrame = new JFrame("Progress Bar");
    mainPanel = new JPanel();
    mainPanel.setSize(200, 100);
    mainFrame.getContentPane().setLayout(new FlowLayout());
    mainPanel.setLayout(new FlowLayout());
    mainFrame.setSize(200, 100);
    monitor = new JProgressBar(0, 00);
    monitor.setSize(200, 100);
    monitor.setStringPainted(true);
    mainPanel.add(monitor);
    mainFrame.getContentPane().add(mainPanel);
    mainFrame.setVisible(true);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    try {
      points[0] = new double[10000];
      points[1] = new double[10000];

      system.setPressure(lowPres);

      ThermodynamicOperations testOps = new ThermodynamicOperations(system);

      try {
        testOps.bubblePointTemperatureFlash();
      } catch (Exception ex) {
        ex.toString();
        return;
      }
      logger.info("temperature bubT = " + system.getTemperature());

      SysNewtonRhapsonPhaseEnvelope2 nonLinSolver = new SysNewtonRhapsonPhaseEnvelope2(system);
      nonLinSolver.solve(1);
      nonLinSolver.calcInc(1);

      for (np = 1; np < 5; np++) {
        if (np % 5 == 0) {
          monitor.setValue(np);
          monitor.setString("Calculated points: " + np);
        }

        nonLinSolver.calcInc(np);
        nonLinSolver.solve(np);

        if (system.getTemperature() > cricondenTherm[0]) {
          cricondenTherm[1] = system.getPressure();
          cricondenTherm[0] = system.getTemperature();
        }
        if (system.getPressure() > cricondenBar[1]) {
          cricondenBar[0] = system.getTemperature();
          cricondenBar[1] = system.getPressure();
        }

        if (Double.isNaN(system.getTemperature())) { // || system.getPressure() < lowPres) {
          points[0][np - 1] = points[0][np - 3];
          points[1][np - 1] = points[1][np - 3];
          pointsH[np - 1] = pointsH[np - 3];
          pointsV[np - 1] = pointsV[np - 3];
          pointsS[np - 1] = pointsS[np - 3];

          // logger.info("avbryter" + np);
          break;
        }
        // logger.info("Ideal pres: " + getPressure());
        // logger.info("temp: " + system.getTemperature());
        points[0][np - 1] = system.getTemperature();
        points[1][np - 1] = system.getPressure();
        pointsH[np - 1] =
            system.getPhase(1).getEnthalpy() / system.getPhase(1).getNumberOfMolesInPhase()
                / system.getPhase(1).getMolarMass() / 1e3;
        pointsV[np - 1] = system.getPhase(1).getDensity();
        pointsS[np - 1] =
            system.getPhase(1).getEntropy() / system.getPhase(1).getNumberOfMolesInPhase()
                / system.getPhase(1).getMolarMass() / 1e3;
      }

      int ncr = nonLinSolver.getNpCrit();
      int ncr2 = np - ncr;

      logger.info("ncr: " + ncr + "  ncr2 . " + ncr2);
      points2[0] = new double[ncr + 1];
      points2[1] = new double[ncr + 1];
      pointsH2[0] = new double[ncr + 1];
      pointsH2[1] = new double[ncr + 1];
      pointsS2[0] = new double[ncr + 1];
      pointsS2[1] = new double[ncr + 1];
      pointsV2[0] = new double[ncr + 1];
      pointsV2[1] = new double[ncr + 1];
      if (ncr2 > 2) {
        points2[2] = new double[ncr2 - 2];
        points2[3] = new double[ncr2 - 2];
        pointsH2[2] = new double[ncr2 - 2];
        pointsH2[3] = new double[ncr2 - 2];
        pointsV2[2] = new double[ncr2 - 2];
        pointsV2[3] = new double[ncr2 - 2];
        pointsS2[2] = new double[ncr2 - 2];
        pointsS2[3] = new double[ncr2 - 2];
      } else {
        points2[2] = new double[0];
        points2[3] = new double[0];
        pointsH2[2] = new double[0];
        pointsH2[3] = new double[0];
        pointsV2[2] = new double[0];
        pointsV2[3] = new double[0];
        pointsS2[2] = new double[0];
        pointsS2[3] = new double[0];
      }

      for (int i = 0; i < ncr; i++) {
        points2[0][i] = points[0][i];
        points2[1][i] = points[1][i];

        pointsH2[1][i] = points[1][i];
        pointsH2[0][i] = pointsH[i];

        pointsS2[1][i] = points[1][i];
        pointsS2[0][i] = pointsS[i];

        pointsV2[1][i] = points[1][i];
        pointsV2[0][i] = pointsV[i];
      }

      system.setTemperature(system.getTC() + 0.001);
      system.setPressure(system.getPC() + 0.001);
      system.init(3);

      points2[0][ncr] = system.getTC();
      points2[1][ncr] = system.getPC();

      pointsH2[1][ncr] = system.getPC();
      pointsH2[0][ncr] = system.getPhase(1).getEnthalpy()
          / system.getPhase(1).getNumberOfMolesInPhase() / system.getPhase(1).getMolarMass() / 1e3;

      pointsS2[1][ncr] = system.getPC();
      pointsS2[0][ncr] = system.getPhase(1).getEntropy()
          / system.getPhase(1).getNumberOfMolesInPhase() / system.getPhase(1).getMolarMass() / 1e3;

      pointsV2[1][ncr] = system.getPC();
      pointsV2[0][ncr] = system.getPhase(1).getDensity();

      if (ncr2 > 2) {
        points2[2][0] = system.getTC();
        points2[3][0] = system.getPC();
        pointsH2[3][0] = system.getPC();
        pointsH2[2][0] =
            system.getPhase(1).getEnthalpy() / system.getPhase(1).getNumberOfMolesInPhase()
                / system.getPhase(1).getMolarMass() / 1e3;
        pointsS2[3][0] = system.getPC();
        pointsS2[2][0] =
            system.getPhase(1).getEntropy() / system.getPhase(1).getNumberOfMolesInPhase()
                / system.getPhase(1).getMolarMass() / 1e3;
        pointsV2[3][0] = system.getPC();
        pointsV2[2][0] = system.getPhase(1).getDensity();

        for (int i = 1; i < (ncr2 - 2); i++) {
          points2[2][i] = points[0][i + ncr - 1];
          points2[3][i] = points[1][i + ncr - 1];

          pointsH2[3][i] = points[1][i + ncr - 1];
          pointsH2[2][i] = pointsH[i + ncr - 1];

          pointsS2[3][i] = points[1][i + ncr - 1];
          pointsS2[2][i] = pointsS[i + ncr - 1];

          pointsV2[3][i] = points[1][i + ncr - 1];
          pointsV2[2][i] = pointsV[i + ncr - 1];
        }
      }
      // monitor.close();
      mainFrame.setVisible(false);
      /*
       * if (outputToFile) { String name1 = new String(); name1 = fileName + "Dew.nc"; file1 = new
       * neqsim.dataPresentation.fileHandeling.createNetCDF.netCDF2D.NetCdf2D();
       * file1.setOutputFileName(name1); file1.setXvalues(points2[2], "temp", "sec");
       * file1.setYvalues(points2[3], "pres", "meter"); file1.createFile();
       *
       * String name2 = new String(); name2 = fileName + "Bub.nc"; file2 = new
       * neqsim.dataPresentation.fileHandeling.createNetCDF.netCDF2D.NetCdf2D();
       * file2.setOutputFileName(name2); file2.setXvalues(points2[0], "temp", "sec");
       * file2.setYvalues(points2[1], "pres", "meter"); file2.createFile(); }
       */
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
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
    String[] navn = {"bubble point", "dew point", "bubble point", "dew point"};
    String title =
        "PT-graph  TC=" + String.valueOf(nf.format(TC)) + " PC=" + String.valueOf(nf.format(PC));
    String title3 =
        "PH-graph  TC=" + String.valueOf(nf.format(TC)) + " PC=" + String.valueOf(nf.format(PC));
    String title4 = "Density-graph  TC=" + String.valueOf(nf.format(TC)) + " PC="
        + String.valueOf(nf.format(PC));
    String title5 =
        "PS-graph  TC=" + String.valueOf(nf.format(TC)) + " PC=" + String.valueOf(nf.format(PC));

    // logger.info("start flash");
    // logger.info("Tferdig..");

    Graph2b graph3 = new Graph2b(pointsH2, navn, title3, "Enthalpy [kJ/kg]", "Pressure [bara]");
    graph3.setVisible(true);
    graph3.saveFigure((neqsim.util.util.FileSystemSettings.tempDir + "NeqSimTempFig4.png"));

    Graph2b graph4 = new Graph2b(pointsV2, navn, title4, "Density [kg/m^3]", "Pressure [bara]");
    graph4.setVisible(true);
    graph4.saveFigure(neqsim.util.util.FileSystemSettings.tempDir + "NeqSimTempFig2.png");

    Graph2b graph5 = new Graph2b(pointsS2, navn, title5, "Entropy [kJ/kg*K]", "Pressure [bara]");
    graph5.setVisible(true);
    graph5.saveFigure(neqsim.util.util.FileSystemSettings.tempDir + "NeqSimTempFig3.png");

    graph2 = new Graph2b(points2, navn, title, "Temperature [K]", "Pressure [bara]");
    graph2.setVisible(true);
    graph2.saveFigure(neqsim.util.util.FileSystemSettings.tempDir + "NeqSimTempFig1.png");

    /*
     * JDialog dialog = new JDialog(); Container dialogContentPane = dialog.getContentPane();
     * dialogContentPane.setLayout(new FlowLayout()); JFreeChartPanel chartPanel =
     * graph4.getChartPanel(); dialogContentPane.add(chartPanel); dialog.show();
     */
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {}

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    DecimalFormat nf = new DecimalFormat();
    nf.setMaximumFractionDigits(1);
    nf.applyPattern("####.#");

    double TC = system.getTC();
    double PC = system.getPC();
    logger.info("tc : " + TC + "  PC : " + PC);
    String[] navn = {"bubble point", "dew point", "bubble point", "dew point"};
    String title = "PT-graph. TC=" + String.valueOf(nf.format(TC)) + "K, PC="
        + String.valueOf(nf.format(PC) + " bara");
    graph2 = new Graph2b(points2, navn, title, "Temperature [K]", "Pressure [bara]");
    return graph2.getChart();
  }

  /** {@inheritDoc} */
  @Override
  public double[][] getPoints(int i) {
    return points2;
  }

  /** {@inheritDoc} */
  @Override
  public double[] get(String name) {
    if (name.equals("bubT")) {
      return points2[0];
    }
    if (name.equals("bubP")) {
      return points2[1];
    }
    if (name.equals("dewT")) {
      return points2[2];
    }
    if (name.equals("dewP")) {
      return points2[3];
    }
    if (name.equals("dewH")) {
      return pointsH2[2];
    }
    if (name.equals("dewDens")) {
      return pointsV2[2];
    }
    if (name.equals("dewS")) {
      return pointsS2[2];
    }
    if (name.equals("bubH")) {
      return pointsH2[0];
    }
    if (name.equals("bubDens")) {
      return pointsV2[0];
    }
    if (name.equals("bubS")) {
      return pointsS2[0];
    }
    if (name.equals("cricondentherm")) {
      return cricondenTherm;
    }
    if (name.equals("cricondenbar")) {
      return cricondenBar;
    } else {
      return null;
    }
  }

  /** {@inheritDoc} */
  @Override
  public String[][] getResultTable() {
    return null;
  }
}
