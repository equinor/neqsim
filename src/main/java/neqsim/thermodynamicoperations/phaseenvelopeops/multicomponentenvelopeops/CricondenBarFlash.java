package neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops;

import Jama.Matrix;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * CricondenBarFlash class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class CricondenBarFlash extends PTphaseEnvelope {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  int neq = 0;
  double beta = 0;
  Matrix u;
  Matrix uold;
  Matrix uini;
  SystemInterface system;
  int numberOfComponents;
  int crico = 0;
  double DT = 1E-6;
  double funcT;
  double dfuncdT;
  double funcP;
  double dfuncdP;

  double f;
  double f1;
  double dfdt;
  double dfdt1;
  double DDQ;
  double T;
  double P;
  double Tini;
  double Pini;
  double T1;

  int ITER;
  int ITERX;
  int ITERT;
  int ITERP;

  // double [] cricondenBar ;
  // double [] cricondenBarX = new double [100] ;
  // double [] cricondenBarY = new double [100] ;

  /**
   * <p>
   * Constructor for CricondenBarFlash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param name a {@link java.lang.String} object
   * @param phaseFraction a double
   * @param cricondenBar an array of type double
   * @param cricondenBarX an array of type double
   * @param cricondenBarY an array of type double
   */
  public CricondenBarFlash(SystemInterface system, String name, double phaseFraction,
      double[] cricondenBar, double[] cricondenBarX, double[] cricondenBarY) {
    this.system = system;
    this.numberOfComponents = system.getPhase(0).getNumberOfComponents();
    u = new Matrix(numberOfComponents, 1); // this is the K values
    this.cricondenBar = cricondenBar;
    this.cricondenBarX = cricondenBarX;
    this.cricondenBarY = cricondenBarY;
    this.beta = phaseFraction;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    // input values
    T = cricondenBar[0];
    P = cricondenBar[1];
    Tini = T;
    Pini = P;
    bubblePointFirst = false;
    system.setBeta(beta);
    system.setPhaseType(0, PhaseType.OIL);
    system.setPhaseType(1, PhaseType.GAS);

    for (int ii = 0; ii < numberOfComponents; ii++) {
      u.set(ii, 0, cricondenBarY[ii] / cricondenBarX[ii]);
      system.getPhase(0).getComponent(ii).setK(cricondenBarY[ii] / cricondenBarX[ii]);
      system.getPhase(0).getComponent(ii).setx(cricondenBarX[ii]);
      system.getPhase(1).getComponent(ii).setx(cricondenBarY[ii]);
      uini = u.copy();
    }
    setNewX();
    /*
     * //iter X for (int iterX=0 ; iterX <= 10000 ; iterX++ ){
     *
     * system.setTemperature(T); system.setPressure(P);
     *
     * uold = u.copy(); init(); setNewK(); setNewX();
     *
     * double sumK=0.; for (int i=0 ; i < numberOfComponents ; i++ ){ sumK=
     * sumK+(uold.get(i,0)-u.get(i,0))*(uold.get(i,0)-u.get(i,0)); } if (iterX == 10000 ){ ITERX=-1;
     * u=uini.copy(); setNewX(); break; } if (sumK <= 1E-7){ ITERX=iterX; setNewX(); break; } }
     */

    // starting loops
    for (int iter = 0; iter < 1000; iter++) {
      // iter T
      // solve dQ/dT=0 with Newton method, numerical derivatives
      for (int iterT = 0; iterT <= 1000; iterT++) {
        system.setTemperature(T);
        system.setPressure(P);
        init();
        funcT();
        f = funcT;
        dfdt = dfuncdT;

        T1 = T + DT;

        system.setTemperature(T1);
        system.setPressure(P);
        init();
        funcT();
        f1 = funcT;
        dfdt1 = dfuncdT;

        DDQ = (dfdt1 - dfdt) / DT;

        if (iterT == 1000) {
          ITERT = -1;
          break;
        }
        if (Math.abs(dfdt / DDQ) < 1E-7) {
          ITERT = iterT;
          break;
        }

        if (Math.abs(dfdt) < 1E-7) {
          ITERT = iterT;
          break;
        }

        T = T - dfdt / DDQ;
      }

      // iter P,X
      // solve Q=0 with Newton method, numerical derivatives
      for (int iterP = 0; iterP <= 10000; iterP++) {
        system.setTemperature(T);
        system.setPressure(P);

        uold = u.copy();
        init();
        setNewK();

        double sumK = 0.;

        for (int i = 0; i < numberOfComponents; i++) {
          sumK = sumK + (uold.get(i, 0) - u.get(i, 0)) * (uold.get(i, 0) - u.get(i, 0));
        }

        setNewX();
        init();
        funcP();

        if ((Math.abs(funcP / dfuncdP) < 1E-7) || (sumK <= 1E-10)) {
          ITERP = iterP;
          break;
        }

        P = P - funcP / dfuncdP;

        if (P <= 0) {
          P = P + funcP / dfuncdP;
          u = u.copy();
          setNewX();
          // iterP = 10000;
          break;
        }

        if (iterP == 10000) {
          ITERP = -1;
          break;
        }
      }

      // Test Convergence
      system.setTemperature(T);
      system.setPressure(P);
      init();
      funcT();
      funcP();

      if (Math.abs(dfuncdT) <= 1E-7 && Math.abs(funcP) <= 1E-7 && Math.abs(dfuncdP) >= 1E-7) {
        /*
         * System.out.println("T        :  " + T); System.out.println("P        :  " + P);
         * System.out.println("dfuncdT  :  " + dfuncdT); System.out.println("dfuncdP  :  " +
         * dfuncdP); System.out.println("funcT    :  " + funcT); System.out.println("funcP    :  " +
         * funcP);
         *
         * System.out.println(ITERX); System.out.println(ITER); System.out.println(ITERT);
         * System.out.println(ITERP);
         */

        cricondenBar[0] = T;
        cricondenBar[1] = P;

        break;
      } else if (Math.abs(dfuncdT) <= 1E-7 && Math.abs(funcP) <= 1E-7
          && Math.abs(dfuncdP) <= 1E-7) {
        T = -1;
        P = -1;
        /*
         * System.out.println("T        :  " + T); System.out.println("P        :  " + P);
         * System.out.println("dfuncdT  :  " + dfuncdT); System.out.println("dfuncdP  :  " +
         * dfuncdP); System.out.println("funcT    :  " + funcT); System.out.println("funcP    :  " +
         * funcP);
         */
        cricondenBar[0] = T;
        cricondenBar[1] = P;

        break;
      }
    }
  }

  /**
   * <p>
   * setNewK.
   * </p>
   */
  public void setNewK() {
    for (int j = 0; j < numberOfComponents; j++) {
      double kap = system.getPhase(0).getComponent(j).getFugacityCoefficient()
          / system.getPhase(1).getComponent(j).getFugacityCoefficient();
      system.getPhase(0).getComponent(j).setK(kap);
      u.set(j, 0, kap);
    }
  }

  /**
   * <p>
   * setNewX.
   * </p>
   */
  public void setNewX() {
    double sumx = 0.;
    double sumy = 0.;
    double[] xx = new double[numberOfComponents];
    double[] yy = new double[numberOfComponents];

    for (int j = 0; j < numberOfComponents; j++) {
      xx[j] = system.getPhase(0).getComponent(j).getz()
          / (1.0 - system.getBeta() + system.getBeta() * u.get(j, 0));
      yy[j] = system.getPhase(1).getComponent(j).getz() * u.get(j, 0)
          / (1.0 - system.getBeta() + system.getBeta() * u.get(j, 0));

      xx[j] = system.getPhase(0).getComponent(j).getz()
          / (1.0 - system.getBeta() + system.getBeta() * system.getPhase(0).getComponent(j).getK());
      yy[j] = system.getPhase(1).getComponent(j).getz() * system.getPhase(0).getComponent(j).getK()
          / (1.0 - system.getBeta() + system.getBeta() * system.getPhase(0).getComponent(j).getK());

      sumx = sumx + xx[j];
      sumy = sumy + yy[j];
    }

    for (int j = 0; j < numberOfComponents; j++) {
      system.getPhase(0).getComponent(j).setx(xx[j] / sumx);
      system.getPhase(1).getComponent(j).setx(yy[j] / sumy);

      xx[j] = system.getPhase(0).getComponent(j).getx();
      yy[j] = system.getPhase(1).getComponent(j).getx();
    }
  }

  /**
   * <p>
   * init. Calls system.init(3);
   * </p>
   */
  public void init() {
    // setNewX();
    system.init(3);
  }

  /**
   * <p>
   * funcT.
   * </p>
   */
  public void funcT() {
    funcT = -1.0;
    dfuncdT = 0.0;

    for (int j = 0; j < numberOfComponents; j++) {
      double xxf = system.getPhase(0).getComponent(j).getx();
      double yyf = system.getPhase(1).getComponent(j).getx();
      /*
       * double voll=system.getPhase(0).getMolarVolume(); double
       * volv=system.getPhase(1).getMolarVolume();
       *
       * double T=system.getPhase(0).getPressure(); double P=system.getPhase(1).getTemperature() ;
       */
      double fugl = system.getPhase(0).getComponent(j).getLogFugacityCoefficient();
      double fugv = system.getPhase(1).getComponent(j).getLogFugacityCoefficient();

      double fugTl = system.getPhase(0).getComponent(j).getdfugdt();
      double fugTv = system.getPhase(1).getComponent(j).getdfugdt();

      funcT = funcT + xxf + xxf * (Math.log(yyf) - Math.log(xxf) + fugv - fugl);
      dfuncdT = dfuncdT + xxf * (fugTv - fugTl);
    }
  }

  /**
   * <p>
   * funcP.
   * </p>
   */
  public void funcP() {
    funcP = -1.0;
    dfuncdP = 0.0;

    for (int j = 0; j < numberOfComponents; j++) {
      double xx = system.getPhase(0).getComponent(j).getx();
      double yy = system.getPhase(1).getComponent(j).getx();

      double fugl = system.getPhase(0).getComponent(j).getLogFugacityCoefficient();
      double fugv = system.getPhase(1).getComponent(j).getLogFugacityCoefficient();

      double fugPl = system.getPhase(0).getComponent(j).getdfugdp();
      double fugPv = system.getPhase(1).getComponent(j).getdfugdp();

      funcP = funcP + xx + xx * (Math.log(yy) - Math.log(xx) + fugv - fugl);
      dfuncdP = dfuncdP + xx * (fugPv - fugPl);
    }
  }
}
