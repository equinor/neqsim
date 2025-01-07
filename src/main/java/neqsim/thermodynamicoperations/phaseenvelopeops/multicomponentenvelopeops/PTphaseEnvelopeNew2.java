/*
 * pTphaseEnvelope.java
 *
 * Created on 14. oktober 2000, 21:59 Updated on May 2019, by Nefeli
 */

package neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops;

import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.BaseOperation;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * pTphaseEnvelopeNew2 class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class PTphaseEnvelopeNew2 extends BaseOperation {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PTphaseEnvelopeNew2.class);

  double maxPressure = 1000.0;
  double minPressure = 1.0;
  SystemInterface system;
  boolean bubblePointFirst = true;
  boolean calculatesDewPoint = true;
  double[] cricondenTherm = new double[3];
  double[] cricondenBar = new double[3];
  double[] cricondenThermX = new double[100];
  double[] cricondenThermY = new double[100];
  double[] cricondenBarX = new double[100];
  double[] cricondenBarY = new double[100];
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
  double[] lnOldOldK;
  double[] lnK;
  boolean outputToFile = false;
  double[] lnOldK;
  double[] lnKwil;
  double[] oldDeltalnK;
  double[] deltalnK;
  double[] tm = {1, 1};
  double beta = 1e-5;
  int lowestGibbsEnergyPhase = 0;
  String fileName = "c:/file";
  double temp = 0;
  double pres = 0;
  double startPres = 0;
  boolean moreLines = false;
  boolean restart = true;
  int np = 0;
  // points[2] = new double[1000];
  int speceq = 0;
  double Tcfirst;
  double Pcfirst;
  double Tmin = 0.0;

  ArrayList<Double> dewPointTemperature = new ArrayList<Double>();
  ArrayList<Double> dewPointPressure = new ArrayList<Double>();
  ArrayList<Double> bubblePointTemperature = new ArrayList<Double>();
  ArrayList<Double> bubblePointPressure = new ArrayList<Double>();

  ArrayList<Double> bubblePointEnthalpy = new ArrayList<Double>();
  ArrayList<Double> dewPointEnthalpy = new ArrayList<Double>();

  ArrayList<Double> bubblePointEntropy = new ArrayList<Double>();
  ArrayList<Double> dewPointEntropy = new ArrayList<Double>();

  ArrayList<Double> bubblePointVolume = new ArrayList<Double>();
  ArrayList<Double> dewPointVolume = new ArrayList<Double>();

  double[] cricondenThermfirst = new double[3];
  double[] cricondenBarfirst = new double[3];
  double[] cricondenThermXfirst = new double[100];
  double[] cricondenThermYfirst = new double[100];
  double[] cricondenBarXfirst = new double[100];
  double[] cricondenBarYfirst = new double[100];

  double[] dewPointTemperatureArray;
  double[] dewPointPressureArray;
  double[] dewPointEnthalpyArray;
  double[] dewPointVolumeArray;
  double[] dewPointEntropyArray;

  double[] bubblePointTemperatureArray;
  double[] bubblePointPressureArray;
  double[] bubblePointEnthalpyArray;
  double[] bubblePointVolumeArray;
  double[] bubblePointEntropyArray;

  /**
   * <p>
   * Constructor for pTphaseEnvelope.
   * </p>
   */
  public PTphaseEnvelopeNew2() {}

  /**
   * <p>
   * Constructor for pTphaseEnvelope.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param name a {@link java.lang.String} object
   * @param phaseFraction a double
   * @param lowPres a double
   * @param bubfirst a boolean
   */
  public PTphaseEnvelopeNew2(SystemInterface system, String name, double phaseFraction,
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
      system.init(0); // initialization

      // selects the most volatile and least volatile component based on Tc values
      // afterwards it uses them to define the speceq of the first point
      // based on the desired first point, dew/bubble
      for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
        if (system.getComponent(i).getz() < 1e-10) {
          continue;
        }
        if (system.getPhase(0).getComponent(i).getIonicCharge() == 0) {
          if (bubblePointFirst && system.getPhase(0).getComponent(speceq).getTC() > system
              .getPhase(0).getComponent(i).getTC()) {
            speceq = system.getPhase(0).getComponent(i).getComponentNumber();
          }
          if (!bubblePointFirst && system.getPhase(0).getComponent(speceq).getTC() < system
              .getPhase(0).getComponent(i).getTC()) {
            speceq = system.getPhase(0).getComponent(i).getComponentNumber();
          }
        }
      }

      // initialized the first step of the phase envelope
      // pressure is already defined
      // temperature is the antoine vapor pressure of the selected component
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
      // if the plasefraction is more than 0.5 it does a dew point initiallization
      // else a bubble point initiallization

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
          // ex.toString();
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

      SysNewtonRhapsonPhaseEnvelope nonLinSolver =
          new SysNewtonRhapsonPhaseEnvelope(system, 2, system.getPhase(0).getNumberOfComponents());
      startPres = system.getPressure();
      nonLinSolver.setu();

      for (np = 1; np < 9980; np++) {
        try {
          // solves the np point of the envelope
          nonLinSolver.calcInc(np);
          nonLinSolver.solve(np);

          // this catches the exceptions
          // double TT = system.getPhase(0).getTemperature();
          // double PP = system.getPhase(0).getPressure();
        } catch (Exception e0) {
          // the envelope crushed.
          // this part keeps the old values
          // restarts the envelope from the other side
          // and then stops

          if (restart) {
            calculatesDewPoint = false;
            restart = !restart;
            Tcfirst = system.getTC();
            Pcfirst = system.getPC();

            cricondenBarfirst = cricondenBar;
            cricondenBarXfirst = cricondenBarX;
            cricondenBarYfirst = cricondenBarY;

            cricondenThermfirst = cricondenTherm;
            cricondenThermXfirst = cricondenThermX;
            cricondenThermYfirst = cricondenThermY;

            // new settings
            phaseFraction = 1.0 - phaseFraction;
            bubblePointFirst = !bubblePointFirst;
            run();
            /**/
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

        if (!nonLinSolver.etterCP) {
          if (Kvallc < 1.05 && Kvalhc > 0.95) {
            // close to the critical point
            // invert phase types and find the CP Temp and Press

            // System.out.println("critical point");
            nonLinSolver.npCrit = np;
            system.invertPhaseTypes();
            nonLinSolver.etterCP = true;
            calculatesDewPoint = false;
            // the critical point is found from interpolation polynomials based on K=1 of
            // the most or least volatile component
            nonLinSolver.calcCrit();
          }
        }

        // stores critondenbar and cricondentherm
        // HERE the new cricoT and crico P values will be called instead
        if (system.getTemperature() > cricondenTherm[0]) {
          cricondenTherm[1] = system.getPressure();
          cricondenTherm[0] = system.getTemperature();
          for (int ii = 0; ii < nonLinSolver.numberOfComponents; ii++) {
            cricondenThermX[ii] = system.getPhase(1).getComponent(ii).getx();
            cricondenThermY[ii] = system.getPhase(0).getComponent(ii).getx();
          }
        } else {
          nonLinSolver.ettercricoT = true;
        }
        if (system.getPressure() > cricondenBar[1]) {
          cricondenBar[0] = system.getTemperature();
          cricondenBar[1] = system.getPressure();
          for (int ii = 0; ii < nonLinSolver.numberOfComponents; ii++) {
            cricondenBarX[ii] = system.getPhase(1).getComponent(ii).getx();
            cricondenBarY[ii] = system.getPhase(0).getComponent(ii).getx();
          }
        }

        // Exit criteria
        if ((system.getPressure() < minPressure && nonLinSolver.ettercricoT)) {
          break;
        }
        if (system.getPressure() > maxPressure) {
          break;
        }
        if (system.getTemperature() < Tmin) {
          break;
        }

        if (system.getTemperature() > 1e-6 && system.getPressure() > 1e-6
            && !(Double.isNaN(system.getTemperature()) || Double.isNaN(system.getPressure()))) {
          if (calculatesDewPoint) {
            dewPointTemperature.add(system.getTemperature());
            dewPointPressure.add(system.getPressure());
            dewPointEnthalpy
                .add(system.getPhase(1).getEnthalpy() / system.getPhase(1).getNumberOfMolesInPhase()
                    / system.getPhase(1).getMolarMass() / 1e3);
            dewPointVolume.add(system.getPhase(1).getDensity());
            dewPointEntropy
                .add(system.getPhase(1).getEntropy() / system.getPhase(1).getNumberOfMolesInPhase()
                    / system.getPhase(1).getMolarMass() / 1e3);
          } else {
            bubblePointTemperature.add(system.getTemperature());
            bubblePointPressure.add(system.getPressure());
            bubblePointEnthalpy
                .add(system.getPhase(1).getEnthalpy() / system.getPhase(1).getNumberOfMolesInPhase()
                    / system.getPhase(1).getMolarMass() / 1e3);
            bubblePointVolume.add(system.getPhase(1).getDensity());
            bubblePointEntropy
                .add(system.getPhase(1).getEntropy() / system.getPhase(1).getNumberOfMolesInPhase()
                    / system.getPhase(1).getMolarMass() / 1e3);
          }
        }
      }

      system.setTemperature(system.getTC());
      system.setPressure(system.getPC());
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
      throw ex;
    }

    dewPointTemperatureArray = new double[dewPointTemperature.size()];
    dewPointPressureArray = new double[dewPointPressure.size()];
    dewPointEnthalpyArray = new double[dewPointTemperature.size()];
    dewPointVolumeArray = new double[dewPointPressure.size()];
    dewPointEntropyArray = new double[dewPointPressure.size()];

    bubblePointTemperatureArray = new double[bubblePointTemperature.size()];
    bubblePointPressureArray = new double[bubblePointPressure.size()];
    bubblePointEnthalpyArray = new double[bubblePointPressure.size()];
    bubblePointVolumeArray = new double[bubblePointPressure.size()];
    bubblePointEntropyArray = new double[bubblePointPressure.size()];

    for (int i = 0; i < dewPointTemperature.size(); i++) {
      dewPointTemperatureArray[i] = dewPointTemperature.get(i);
      dewPointPressureArray[i] = dewPointPressure.get(i);
      dewPointEnthalpyArray[i] = dewPointEnthalpy.get(i);
      dewPointVolumeArray[i] = dewPointVolume.get(i);
      dewPointEntropyArray[i] = dewPointEntropy.get(i);
    }

    for (int i = 0; i < bubblePointTemperature.size(); i++) {
      bubblePointTemperatureArray[i] = bubblePointTemperature.get(i);
      bubblePointPressureArray[i] = bubblePointPressure.get(i);
      bubblePointEnthalpyArray[i] = bubblePointEnthalpy.get(i);
      bubblePointEntropyArray[i] = bubblePointEntropy.get(i);
      bubblePointVolumeArray[i] = bubblePointVolume.get(i);
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
      logger.error(ex.getMessage(), ex);
    }

    // double[][] hydData = opsHyd.getData();
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {}

  /** {@inheritDoc} */
  @Override
  public double[] get(String name) {
    if (name.equals("dewT")) {
      return dewPointTemperatureArray;
    }
    if (name.equals("dewP")) {
      return dewPointPressureArray;
    }
    if (name.equals("bubT")) {
      return bubblePointTemperatureArray;
    }
    if (name.equals("bubP")) {
      return bubblePointPressureArray;
    }

    if (name.equals("dewH")) {
      return dewPointEnthalpyArray;
    }
    if (name.equals("dewDens")) {
      return dewPointVolumeArray;
    }
    if (name.equals("dewS")) {
      return dewPointEntropyArray;
    }
    if (name.equals("bubH")) {
      return bubblePointEnthalpyArray;
    }
    if (name.equals("bubDens")) {
      return bubblePointVolumeArray;
    }
    if (name.equals("bubS")) {
      return bubblePointEntropyArray;
    }
    if (name.equals("cricondentherm")) {
      return cricondenTherm;
    }
    if (name.equals("cricondenthermX")) {
      return cricondenThermX;
    }
    if (name.equals("cricondenthermY")) {
      return cricondenThermY;
    }
    if (name.equals("cricondenbar")) {
      return cricondenBar;
    }
    if (name.equals("cricondenbarX")) {
      return cricondenBarX;
    }
    if (name.equals("cricondenbarY")) {
      return cricondenBarY;
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
      if (system.getPhase(0).getComponent(i).getTC() > max) {
        max = system.getPhase(0).getComponent(i).getTC();
        hc = i;
      }
      if (system.getPhase(0).getComponent(i).getTC() < min) {
        min = system.getPhase(0).getComponent(i).getTC();
        lc = i;
      }
    }

    try {
      if (beta <= 0.5) {
        // closer to bubble point get the lightest component

        initTc = system.getPhase(0).getComponent(lc).getTC();
        initPc = system.getPhase(0).getComponent(lc).getPC();
        initAc = system.getPhase(0).getComponent(lc).getAcentricFactor();
      } else {
        // closer to dew point get the heaviest component
        initTc = system.getPhase(0).getComponent(hc).getTC();
        initPc = system.getPhase(0).getComponent(hc).getPC();
        initAc = system.getPhase(0).getComponent(hc).getAcentricFactor();
      }

      // initial T based on the lightest/heaviest component
      Tstart = initTc * 5.373 * (1 + initAc) / (5.373 * (1 + initAc) - Math.log(P / initPc));

      // solve for Tstart with Newton
      for (int i = 0; i < 1000; i++) {
        initT = 0.;
        dinitT = 0.;
        for (int j = 0; j < numberOfComponents; j++) {
          Kwil[j] = system.getPhase(0).getComponent(j).getPC() / P
              * Math.exp(5.373 * (1. + system.getPhase(0).getComponent(j).getAcentricFactor())
                  * (1. - system.getPhase(0).getComponent(j).getTC() / Tstart));
          // system.getPhases()[0].getComponent(j).setK(Kwil[j]);
        }

        for (int j = 0; j < numberOfComponents; j++) {
          if (beta < 0.5) {
            initT = initT + system.getPhase(0).getComponent(j).getz() * Kwil[j];
            dinitT = dinitT + system.getPhase(0).getComponent(j).getz() * Kwil[j] * 5.373
                * (1 + system.getPhase(0).getComponent(j).getAcentricFactor())
                * system.getPhase(0).getComponent(j).getTC() / (Tstart * Tstart);
          } else {
            initT = initT + system.getPhase(0).getComponent(j).getz() / Kwil[j];
            dinitT = dinitT - system.getPhase(0).getComponent(j).getz() / Kwil[j] * 5.373
                * (1 + system.getPhase(0).getComponent(j).getAcentricFactor())
                * system.getPhase(0).getComponent(j).getTC() / (Tstart * Tstart);
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
    } catch (Exception ex) {
      Tstart = initTc * 5.373 * (1 + initAc) / (5.373 * (1 + initAc) - Math.log(P / initPc));
    }
    if (Double.isNaN(Tstart) || Double.isInfinite(Tstart)) {
      Tstart = initTc * 5.373 * (1 + initAc) / (5.373 * (1 + initAc) - Math.log(P / initPc));
    }
    return Tstart;
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {}

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }
}
