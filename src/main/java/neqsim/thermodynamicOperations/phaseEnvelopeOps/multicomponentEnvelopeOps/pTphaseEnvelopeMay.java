package neqsim.thermodynamicOperations.phaseEnvelopeOps.multicomponentEnvelopeOps;

import java.text.DecimalFormat;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.dataPresentation.JFreeChart.graph2b;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.BaseOperation;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * pTphaseEnvelopeMay class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class pTphaseEnvelopeMay extends BaseOperation {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(pTphaseEnvelopeMay.class);

  double maxPressure = 1000.0;
  double minPressure = 1.0;
  double[][] copiedPoints = null;
  graph2b graph2 = null;
  SystemInterface system;
  boolean bubblePointFirst = true;
  boolean hascopiedPoints = false;
  double[] cricondenTherm = new double[3];
  double[] cricondenBar = new double[3];
  double phaseFraction = 1e-10;
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
  double lowPres = 1.0;
  double lnOldOldK[];
  double lnK[];
  boolean outputToFile = false;
  double[] lnOldK;
  double[] lnKwil;
  double oldDeltalnK[];
  double deltalnK[];
  double[] tm = {1, 1};
  double beta = 1e-5;
  int lowestGibbsEnergyPhase = 0; // lowestGibbsEnergyPhase
  JProgressBar monitor;
  JFrame mainFrame;
  String fileName = "c:/file";
  JPanel mainPanel;
  double temp = 0;
  double pres = 0;
  double startPres = 0;
  double[][] points = new double[2][];
  double[] pointsH;
  double[][] pointsH2 = new double[4][];
  double[] pointsV;
  double[][] pointsV2 = new double[4][];
  double[] pointsS;
  double[][] pointsS2 = new double[4][];
  public double[][] points2 = new double[4][];
  double[][] points3 = new double[8][];
  boolean moreLines = false;
  boolean restart = true;
  int np = 0;
  // points[2] = new double[1000];
  int speceq = 0;
  String[] navn = {"bubble point", "dew point", "bubble point", "dew point", "dew points"};
  int npfirst;
  int ncrfirst;
  double Tcfirst;
  double Pcfirst;
  double Tmin = 0.0;

  /**
   * <p>
   * Constructor for pTphaseEnvelopeMay.
   * </p>
   */
  public pTphaseEnvelopeMay() {}

  /**
   * <p>
   * Constructor for pTphaseEnvelopeMay.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param name a {@link java.lang.String} object
   * @param phaseFraction a double
   * @param lowPres a double
   * @param bubfirst a boolean
   */
  public pTphaseEnvelopeMay(SystemInterface system, String name, double phaseFraction,
      double lowPres, boolean bubfirst) {
    this.bubblePointFirst = bubfirst;
    if (name != null) {
      outputToFile = true;
      fileName = name;
    }
    this.system = system;
    this.phaseFraction = phaseFraction;
    lnOldOldK = new double[system.getPhase(0).getNumberOfComponents()];
    lnOldK = new double[system.getPhase(0).getNumberOfComponents()];
    lnK = new double[system.getPhase(0).getNumberOfComponents()];
    this.lowPres = lowPres;
    oldDeltalnK = new double[system.getPhase(0).getNumberOfComponents()];
    deltalnK = new double[system.getPhase(0).getNumberOfComponents()];
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    speceq = 0; // initialization
    try {
      points[0] = new double[10000]; // declarations for points
      points[1] = new double[10000]; // declarations for points

      pointsH = new double[10000]; // declarations for points
      pointsV = new double[10000]; // declarations for points
      pointsS = new double[10000]; // declarations for points
      system.init(0); // initialization

      // selects the most volatile and least volatile component based on Tc values
      // afterwards it uses them to define the speceq of the first point
      // based on the desired first point, dew/bubble
      for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
        if (system.getPhase(0).getComponent(i).getIonicCharge() == 0) {
          if (bubblePointFirst == true && system.getPhase(0).getComponents()[speceq]
              .getTC() > system.getPhase(0).getComponents()[i].getTC()) {
            speceq = system.getPhase(0).getComponent(i).getComponentNumber();
          }
          if (bubblePointFirst == false && system.getPhase(0).getComponents()[speceq]
              .getTC() < system.getPhase(0).getComponents()[i].getTC()) {
            speceq = system.getPhase(0).getComponent(i).getComponentNumber();
          }
        }
      }

      // initialized the first step of the phase envelope
      // pressure is already defined
      // temperature is the Antoine vapor pressure of the selected component
      // (least or most volatile.
      pres = lowPres;
      // temp =
      // system.getPhase(0).getComponent(speceq).getAntoineVaporTemperature(pres);
      temp = tempKWilson(phaseFraction, pres);

      if (Double.isNaN(temp)) {
        temp = system.getPhase(0).getComponent(speceq).getTC() - 20.0;
      }
      system.setTemperature(temp);
      system.setPressure(pres);

      ThermodynamicOperations testOps = new ThermodynamicOperations(system);

      // this part converges the first phase envelope point.
      // if the phase fraction is more than 0.5 it does a dew point initialization
      // else a bubble point initialization

      for (int i = 0; i < 5; i++) {
        try {
          if (phaseFraction < 0.5) {
            temp += i * 2;
            system.setTemperature(temp);
            testOps.bubblePointTemperatureFlash();
          } else {
            temp += i * 2;
            system.setTemperature(temp);
            testOps.dewPointTemperatureFlash();
          }
        } catch (Exception ex) {
          ex.toString();
        }
        double tempNy = system.getTemperature();

        if (!Double.isNaN(tempNy)) {
          temp = tempNy;
          break;
        }
      }

      // this part sets the first envelope point into the system
      system.setBeta(phaseFraction);
      system.setPressure(pres);
      system.setTemperature(temp);

      sysNewtonRhapsonPhaseEnvelope nonLinSolver =
          new sysNewtonRhapsonPhaseEnvelope(system, 2, system.getPhase(0).getNumberOfComponents());
      startPres = system.getPressure();
      nonLinSolver.setu();
      for (np = 1; np < 9980; np++) {
        try {
          // solves the np point of the envelope
          nonLinSolver.calcInc(np);
          nonLinSolver.solve(np);

          // double TT = system.getPhase(0).getTemperature();
          // double PP = system.getPhase(0).getPressure();
        } catch (Exception e0) {
          // the envelope crushed.
          // this part keeps the old values
          // restarts the envelope from the other side
          // and then stops

          if (restart) {
            restart = false;
            // keep values
            Tmin = points[0][np - 2];
            npfirst = np - 1;
            ncrfirst = nonLinSolver.getNpCrit();
            if (ncrfirst == 0) {
              ncrfirst = npfirst;
            }
            Tcfirst = system.getTC();
            Pcfirst = system.getPC();
            hascopiedPoints = true;
            copiedPoints = new double[5][np - 1];
            for (int i = 0; i < np - 1; i++) {
              copiedPoints[0][i] = points[0][i];
              copiedPoints[1][i] = points[1][i];
              copiedPoints[2][i] = pointsH[i];
              copiedPoints[3][i] = pointsS[i];
              copiedPoints[4][i] = pointsV[i];
            }
            // new settings
            phaseFraction = 1.0 - phaseFraction;
            if (bubblePointFirst) {
              bubblePointFirst = false;
            } else {
              bubblePointFirst = true;
            }
            run();
            break;
          } else {
            np = np - 1;
            break;
          }
        }

        // check for critical point
        double Kvallc = system.getPhase(0).getComponent(nonLinSolver.lc).getx()
            / system.getPhase(1).getComponent(nonLinSolver.lc).getx();
        double Kvalhc = system.getPhase(0).getComponent(nonLinSolver.hc).getx()
            / system.getPhase(1).getComponent(nonLinSolver.hc).getx();
        // double densV = system.getPhase(0).getDensity();
        // double densL = system.getPhase(1).getDensity();

        // System.out.println(np + " " + system.getTemperature() + " " +
        // system.getPressure() + " " + densV + " " + densL );

        if ((nonLinSolver.etterCP == false)) {
          if (Kvallc < 1.05 && Kvalhc > 0.95) {
            // close to the critical point
            // invert phase types and find the CP Temp and Press

            // System.out.println("critical point");
            nonLinSolver.npCrit = np;
            system.invertPhaseTypes();
            nonLinSolver.etterCP = true;
            // the critical point is found from interpolation plynimials based on K=1 of
            // the
            // most or least volatile component
            nonLinSolver.calcCrit();
          }
        }
        if (nonLinSolver.calcCP) {
          nonLinSolver.calcCP = false;
          nonLinSolver.npCrit = np;
          nonLinSolver.calcCrit();
        }

        // stores cricondenbar and cricondentherm
        // HERE the new cricoT and crico P values will be called instead
        if (system.getTemperature() > cricondenTherm[0]) {
          cricondenTherm[1] = system.getPressure();
          cricondenTherm[0] = system.getTemperature();
        } else {
          nonLinSolver.ettercricoT = true;
        }
        if (system.getPressure() > cricondenBar[1]) {
          cricondenBar[0] = system.getTemperature();
          cricondenBar[1] = system.getPressure();
        }

        // Exit criteria
        if ((system.getPressure() < minPressure && nonLinSolver.ettercricoT)) {
          break;
        }
        if (system.getPressure() > maxPressure) {
          break;
        }
        if (system.getTemperature() > Tmin && !restart) {
          break;
        }

        // Keeps the calculated points
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

      try {
        int ncr = nonLinSolver.getNpCrit();
        if (ncr == 0) {
          ncr = np;
        }
        int ncr2 = np - ncr;
        if (hascopiedPoints) {
          // if it enters here the envelope crashed and restarted
          // reallocate to have all values
          points2 = new double[8][];
          pointsH2 = new double[8][];
          pointsS2 = new double[8][];
          pointsV2 = new double[8][];
        }

        // points2 are plotted
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
          pointsS2[2] = new double[ncr2 - 2];
          pointsS2[3] = new double[ncr2 - 2];
          pointsV2[2] = new double[ncr2 - 2];
          pointsV2[3] = new double[ncr2 - 2];
        } else {
          points2[2] = new double[0];
          points2[3] = new double[0];
          pointsH2[2] = new double[0];
          pointsH2[3] = new double[0];
          pointsS2[2] = new double[0];
          pointsS2[3] = new double[0];
          pointsV2[2] = new double[0];
          pointsV2[3] = new double[0];
        }

        for (int i = 0; i < ncr; i++) {
          // second branch up to critical point
          points2[0][i] = points[0][i];
          points2[1][i] = points[1][i];
          pointsH2[1][i] = points[1][i];
          pointsH2[0][i] = pointsH[i];
          pointsS2[1][i] = points[1][i];
          pointsS2[0][i] = pointsS[i];
          pointsV2[1][i] = points[1][i];
          pointsV2[0][i] = pointsV[i];
        }
        if (ncr2 > 2) {
          for (int i = 1; i < (ncr2 - 2); i++) {
            // second branch after the critical point
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

        if (hascopiedPoints) {
          if (ncrfirst > npfirst) {
            ncr = copiedPoints[0].length - 1;
            ncr2 = npfirst - ncr;
            npfirst = npfirst - 1;
          } else {
            ncr = ncrfirst;
            ncr2 = npfirst - ncr;
          }

          points2[4] = new double[ncr + 1];
          points2[5] = new double[ncr + 1];
          pointsH2[4] = new double[ncr + 1];
          pointsH2[5] = new double[ncr + 1];
          pointsS2[4] = new double[ncr + 1];
          pointsS2[5] = new double[ncr + 1];
          pointsV2[4] = new double[ncr + 1];
          pointsV2[5] = new double[ncr + 1];

          if (ncr2 > 2) {
            points2[6] = new double[ncr2 - 2];
            points2[7] = new double[ncr2 - 2];
            pointsH2[6] = new double[ncr2 - 2];
            pointsH2[7] = new double[ncr2 - 2];
            pointsS2[6] = new double[ncr2 - 2];
            pointsS2[7] = new double[ncr2 - 2];
            pointsV2[6] = new double[ncr2 - 2];
            pointsV2[7] = new double[ncr2 - 2];
          } else {
            points2[6] = new double[0];
            points2[7] = new double[0];
            pointsH2[6] = new double[0];
            pointsH2[7] = new double[0];
            pointsS2[6] = new double[0];
            pointsS2[7] = new double[0];
            pointsV2[6] = new double[0];
            pointsV2[7] = new double[0];
          }

          for (int i = 0; i < ncr; i++) {
            // first branch up to the critical point
            points2[4][i] = copiedPoints[0][i];
            points2[5][i] = copiedPoints[1][i];
            pointsH2[5][i] = copiedPoints[1][i];
            pointsH2[4][i] = copiedPoints[2][i];
            pointsS2[5][i] = copiedPoints[1][i];
            pointsS2[4][i] = copiedPoints[3][i];
            pointsV2[5][i] = copiedPoints[1][i];
            pointsV2[4][i] = copiedPoints[4][i];
          }
          if (ncr2 > 2) {
            for (int i = 1; i < (ncr2 - 2); i++) {
              // first branch after the critical point
              points2[6][i] = copiedPoints[0][i + ncr - 1];
              points2[7][i] = copiedPoints[1][i + ncr - 1];
              pointsH2[7][i] = copiedPoints[1][i + ncr - 1];
              pointsH2[6][i] = copiedPoints[2][i + ncr - 1];
              pointsS2[7][i] = copiedPoints[1][i + ncr - 1];
              pointsS2[6][i] = copiedPoints[3][i + ncr - 1];
              pointsV2[7][i] = copiedPoints[1][i + ncr - 1];
              pointsV2[6][i] = copiedPoints[4][i + ncr - 1];
            }
          }
        }

        // critical point
        system.setTemperature(system.getTC());
        system.setPressure(system.getPC());

        system.init(3);

        points2[0][ncr] = system.getTC();
        points2[1][ncr] = system.getPC();

        if (ncr2 > 2) {
          points2[2][0] = system.getTC();
          points2[3][0] = system.getPC();
        }
      } catch (Exception e2) {
        // double nef = 0.;
        logger.error("error", e2);
      }
      /*
       * try { if (outputToFile) { // update this String name1 = new String(); name1 = fileName +
       * "Dew.nc"; file1 = new
       * neqsim.dataPresentation.fileHandeling.createNetCDF.netCDF2D.NetCdf2D();
       * file1.setOutputFileName(name1); file1.setXvalues(points2[2], "temp", "sec");
       * file1.setYvalues(points2[3], "pres", "meter"); file1.createFile();
       * 
       * String name2 = new String(); name2 = fileName + "Bub.nc"; file2 = new
       * neqsim.dataPresentation.fileHandeling.createNetCDF.netCDF2D.NetCdf2D();
       * file2.setOutputFileName(name2); file2.setXvalues(points2[0], "temp", "sec");
       * file2.setYvalues(points2[1], "pres", "meter"); file2.createFile(); } } catch (Exception e3)
       * { // double nef = 0.; logger.error("error", e3); }
       */
    } catch (Exception e4) {
      // double nef = 0.;
      logger.error("error", e4);
    }
  }

  /**
   * <p>
   * calcHydrateLine.
   * </p>
   */
  public void calcHydrateLine() {
    ThermodynamicOperations opsHyd = new ThermodynamicOperations(system);
    try {
      opsHyd.hydrateEquilibriumLine(10.0, 300.0);
    } catch (Exception ex) {
      logger.error("error", ex);
    }

    // double[][] hydData = opsHyd.getData();
  }

  /** {@inheritDoc} */
  @Override
  public void displayResult() {
    DecimalFormat nf = new DecimalFormat();
    nf.setMaximumFractionDigits(1);
    nf.applyPattern("####.#");
    if (bubblePointFirst) {
      // bubble point side
      navn[0] = "bubble point 2";
      navn[1] = "dew point 2";
      navn[2] = "dew point 1";
      navn[3] = "bubble point 1";
    } else {
      // dew point side and does not crash
      navn[0] = "dew point";
      navn[1] = "bubble point";
      navn[2] = "dew point";
      navn[3] = "bubbl point";
    }

    double TC = system.getTC();
    double PC = system.getPC();

    String title =
        "PT-graph  TC=" + String.valueOf(nf.format(TC)) + " PC=" + String.valueOf(nf.format(PC));
    String title3 =
        "PH-graph  TC=" + String.valueOf(nf.format(TC)) + " PC=" + String.valueOf(nf.format(PC));
    String title4 = "Density-graph  TC=" + String.valueOf(nf.format(TC)) + " PC="
        + String.valueOf(nf.format(PC));
    String title5 =
        "PS-graph  TC=" + String.valueOf(nf.format(TC)) + " PC=" + String.valueOf(nf.format(PC));

    graph2b graph3 = new graph2b(pointsH2, navn, title3, "Enthalpy [kJ/kg]", "Pressure [bara]");
    graph3.setVisible(true);
    // graph3.saveFigure(new String(util.util.FileSystemSettings.tempDir +
    // "NeqSimTempFig4.png"));

    graph2b graph4 = new graph2b(pointsV2, navn, title4, "Density [kg/m^3]", "Pressure [bara]");
    graph4.setVisible(true);
    // graph4.saveFigure(util.util.FileSystemSettings.tempDir +
    // "NeqSimTempFig2.png");

    graph2b graph5 = new graph2b(pointsS2, navn, title5, "Entropy [kJ/kg*K]", "Pressure [bara]");
    graph5.setVisible(true);
    // graph5.saveFigure(util.util.FileSystemSettings.tempDir +
    // "NeqSimTempFig3.png");

    graph2 = new graph2b(points2, navn, title, "Temperature [K]", "Pressure [bara]");
    graph2.setVisible(true);
    // graph2.saveFigure(util.util.FileSystemSettings.tempDir +
    // "NeqSimTempFig1.png");

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
  public double[][] getPoints(int i) {
    return points2;
  }

  /** {@inheritDoc} */
  @Override
  public void addData(String name, double[][] data) {
    double[][] localPoints = new double[points2.length + data.length][];
    navn[localPoints.length / 2 - 1] = name;
    System.arraycopy(points2, 0, localPoints, 0, points2.length);
    System.arraycopy(data, 0, localPoints, points2.length, data.length);
    points2 = localPoints;
  }

  /** {@inheritDoc} */
  @Override
  public double[] get(String name) {
    if (name.equals("dewT")) {
      return points2[0];
    }
    if (name.equals("dewP")) {
      return points2[1];
    }
    if (name.equals("bubT")) {
      return points2[2];
    }
    if (name.equals("bubP")) {
      return points2[3];
    }
    if (name.equals("dewT2")) {
      return points2[4];
    }
    if (name.equals("dewP2")) {
      return points2[5];
    }
    if (name.equals("bubT2")) {
      return points2[6];
    }
    if (name.equals("bubP2")) {
      return points2[7];
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
    }
    if (name.equals("criticalPoint1")) {
      return new double[] {system.getTC(), system.getPC()};
    }
    if (name.equals("criticalPoint2")) {
      return new double[] {0, 0};
    } else {
      return null;
    }
  }

  /**
   * Getter for property bubblePointFirst.
   *
   * @return Value of property bubblePointFirst.
   */
  public boolean isBubblePointFirst() {
    return bubblePointFirst;
  }

  /**
   * Setter for property bubblePointFirst.
   *
   * @param bubblePointFirst New value of property bubblePointFirst.
   */
  public void setBubblePointFirst(boolean bubblePointFirst) {
    this.bubblePointFirst = bubblePointFirst;
  }

  /** {@inheritDoc} */
  @Override
  public String[][] getResultTable() {
    return null;
  }

  /**
   * <p>
   * tempKWilson.
   * </p>
   *
   * @param beta a double
   * @param P a double
   * @return a double
   */
  public double tempKWilson(double beta, double P) {
    // Initiallizes the temperature of a saturation point for given pressure
    // based on K values of Wilson
    // see Michelsen book thermodynamics & computational aspects

    double initTc = 0.;
    double initPc = 0.;
    double initAc = 0.;
    double Tstart = 0.;
    double Tstartold = 0.;
    double initT = 0;
    double dinitT = 0;
    int numberOfComponents = system.getPhase(0).getNumberOfComponents();
    int lc = 0;
    int hc = 0;

    double[] Kwil = new double[numberOfComponents];

    double min = 100000.;
    double max = 0.;

    for (int i = 0; i < numberOfComponents; i++) {
      if (system.getPhase(0).getComponents()[i].getTC() > max) {
        max = system.getPhase(0).getComponents()[i].getTC();
        hc = i;
      }
      if (system.getPhase(0).getComponents()[i].getTC() < min) {
        min = system.getPhase(0).getComponents()[i].getTC();
        lc = i;
      }
    }

    if (beta <= 0.5) {
      initTc = system.getPhase(0).getComponents()[lc].getTC(); // closer to bubble point get
                                                               // the lightest
                                                               // component
      initPc = system.getPhase(0).getComponents()[lc].getPC();
      initAc = system.getPhase(0).getComponents()[lc].getAcentricFactor();
    } else if (beta > 0.5) {
      initTc = system.getPhase(0).getComponents()[hc].getTC(); // closer to dew point get the
                                                               // heaviest component
      initPc = system.getPhase(0).getComponents()[hc].getPC();
      initAc = system.getPhase(0).getComponents()[hc].getAcentricFactor();
    }
    Tstart = initTc * 5.373 * (1 + initAc) / (5.373 * (1 + initAc) - Math.log(P / initPc)); // initial
                                                                                            // T
                                                                                            // based
                                                                                            // on
                                                                                            // the
                                                                                            // lighterst/heaviest
                                                                                            // component

    // solve for Tstart with Newton
    for (int i = 0; i < 1000; i++) {
      initT = 0.;
      dinitT = 0.;
      for (int j = 0; j < numberOfComponents; j++) {
        Kwil[j] = system.getPhase(0).getComponents()[j].getPC() / P
            * Math.exp(5.373 * (1. + system.getPhase(0).getComponents()[j].getAcentricFactor())
                * (1. - system.getPhase(0).getComponents()[j].getTC() / Tstart));
        system.getPhases()[0].getComponents()[j].setK(Math.log(Kwil[j]));
      }

      for (int j = 0; j < numberOfComponents; j++) {
        if (beta < 0.5) {
          initT = initT + system.getPhase(0).getComponents()[j].getz() * Kwil[j];
          dinitT = dinitT + system.getPhase(0).getComponents()[j].getz() * Kwil[j] * 5.373
              * (1 + system.getPhase(0).getComponents()[j].getAcentricFactor())
              * system.getPhase(0).getComponents()[j].getTC() / (Tstart * Tstart);
        } else {
          initT = initT + system.getPhase(0).getComponents()[j].getz() / Kwil[j];
          dinitT = dinitT - system.getPhase(0).getComponents()[j].getz() / Kwil[j] * 5.373
              * (1 + system.getPhase(0).getComponents()[j].getAcentricFactor())
              * system.getPhase(0).getComponents()[j].getTC() / (Tstart * Tstart);
        }
      }

      initT = initT - 1.;
      if (Math.abs(initT / dinitT) > 0.1 * Tstart) {
        Tstart = Tstart - 0.001 * initT / dinitT;
      } else {
        Tstart = Tstart - initT / dinitT;
      }

      if (Math.abs(Tstart - Tstartold) < 1.e-5) {
        return Tstart;
      }
      Tstartold = Tstart;
    }

    return Tstart;
  }

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
    graph2 = new graph2b(points2, navn, title, "Temperature [K]", "Pressure [bara]");
    return graph2.getChart();
  }
}
