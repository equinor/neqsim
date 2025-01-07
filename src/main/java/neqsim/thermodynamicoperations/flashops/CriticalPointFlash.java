package neqsim.thermodynamicoperations.flashops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ejml.simple.SimpleMatrix;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * CriticalPointFlash class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class CriticalPointFlash extends Flash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(CriticalPointFlash.class);

  SimpleMatrix Mmatrix = null;
  SimpleMatrix HeidemannMmatrix = null;
  SimpleMatrix Nmatrix = null;
  SimpleMatrix fmatrix = null;
  int numberOfComponents;
  double Vc0;
  double Tc0;

  /**
   * <p>
   * Constructor for CriticalPointFlash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public CriticalPointFlash(SystemInterface system) {
    this.system = system;
    // clonedsystem = system.clone();
    numberOfComponents = system.getPhase(0).getNumberOfComponents();
    Mmatrix = new SimpleMatrix(numberOfComponents, numberOfComponents);
    Nmatrix = new SimpleMatrix(numberOfComponents, numberOfComponents);
    HeidemannMmatrix = new SimpleMatrix(numberOfComponents, numberOfComponents);
    fmatrix = new SimpleMatrix(numberOfComponents, 1);
  }

  /**
   * <p>
   * calcMmatrixHeidemann.
   * </p>
   */
  public void calcMmatrixHeidemann() {
    Tc0 = system.getPhase(0).getPseudoCriticalTemperature();
    Vc0 = 4 * system.getPhase(0).getB() / system.getPhase(0).getNumberOfMolesInPhase();

    system.setUseTVasIndependentVariables(true);
    system.setNumberOfPhases(1);
    system.setTemperature(Tc0);
    system.getPhase(0).setTotalVolume(Vc0 * system.getTotalNumberOfMoles());
    system.init(3);
    double dt = 1.0;

    for (int iii = 0; iii < 100; iii++) {
      system.setTemperature(system.getTemperature() + dt);
      system.init(3);
      // system.getPressure();

      double dij = 0;
      double tempJ = 0;
      for (int i = 0; i < numberOfComponents; i++) {
        for (int j = 0; j < numberOfComponents; j++) {
          dij = i == j ? 1.0 : 0.0; // Kroneckers delta

          tempJ = (dij / system.getPhase(0).getComponent(i).getNumberOfMolesInPhase()
              - 1.0 / system.getPhase(0).getNumberOfMolesInPhase()
              + (system.getPhase(0).getComponent(i).getdfugdn(j)
                  + system.getPhase(0).getComponent(i).getdfugdp()
                      * system.getPhase(0).getComponent(j).getVoli()
                      * system.getPhase(0).getdPdVTn() * -1));
          // tempJ = ((PhaseSrkEos)system.getPhase(0)).dFdNdN(i,j);

          HeidemannMmatrix.set(i, j, tempJ);
          // Math.sqrt(system.getPhase(0).getComponent(i).getz() *
          // system.getPhase(0).getComponent(j).getz()) *
        }
      }
      HeidemannMmatrix.print();
      logger.info("Q det " + HeidemannMmatrix.determinant() + " temperature "
          + system.getTemperature() + " pressure " + system.getPressure());
    }
  }

  /**
   * <p>
   * calcMmatrix.
   * </p>
   */
  public void calcMmatrix() {
    double dij = 0;
    double tempJ = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < numberOfComponents; j++) {
        dij = i == j ? 1.0 : 0.0; // Kroneckers delta

        tempJ = (dij / system.getPhase(0).getComponent(i).getNumberOfMolesInPhase()
            - 1.0 / system.getPhase(0).getNumberOfMolesInPhase()
            + (system.getPhase(0).getComponent(i).getdfugdn(j)
                + system.getPhase(0).getComponent(i).getdfugdp()
                    * system.getPhase(0).getComponent(j).getVoli() * system.getPhase(0).getdPdVTn()
                    * -1.0));

        Mmatrix.set(i, j, Math.sqrt(
            system.getPhase(0).getComponent(i).getz() * system.getPhase(0).getComponent(j).getz())
            * tempJ);
        // Math.sqrt(system.getPhase(0).getComponent(i).getz() *
        // system.getPhase(0).getComponent(j).getz()) *
      }
    }
  }

  /**
   * <p>
   * calcdpd.
   * </p>
   *
   * @return a double
   */
  public double calcdpd() {
    double[] oldz = system.getMolarRate();
    i = Mmatrix.eig().getNumberOfEigenvalues();
    SimpleMatrix eigenVector = Mmatrix.eig().getEigenVector(0);

    double[] newz1 = new double[numberOfComponents];
    double[] newz2 = new double[numberOfComponents];
    double sperturb = 1e-3;
    for (int ii = 0; ii < numberOfComponents; ii++) {
      newz1[ii] = system.getPhase(0).getComponent(ii).getz()
          + sperturb * eigenVector.get(ii) * Math.sqrt(system.getPhase(0).getComponent(ii).getz());
      newz2[ii] = system.getPhase(0).getComponent(ii).getz()
          - sperturb * eigenVector.get(ii) * Math.sqrt(system.getPhase(0).getComponent(ii).getz());
    }

    system.setMolarComposition(newz1);
    system.init(3);
    calcMmatrix();
    // eigenVector = Mmatrix.eig().getEigenVector(0);
    SimpleMatrix evalMatrix = eigenVector.transpose().mult(Mmatrix).mult(eigenVector);
    double perturb1 = evalMatrix.get(0, 0);

    system.setMolarComposition(newz2);
    system.init(3);
    calcMmatrix();
    // eigenVector = Mmatrix.eig().getEigenVector(0);
    evalMatrix = eigenVector.transpose().mult(Mmatrix).mult(eigenVector);
    double perturb2 = evalMatrix.get(0, 0);

    system.setMolarComposition(oldz);
    system.init(3);

    double dtpddsss = (perturb1 + perturb2) / (sperturb * sperturb);
    return dtpddsss;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    system.init(0);
    system.setTotalNumberOfMoles(1.0);
    system.init(3);

    calcMmatrixHeidemann();
    system.setNumberOfPhases(1);

    Tc0 = system.getPhase(0).getPseudoCriticalTemperature();
    Vc0 = 4 * system.getPhase(0).getB() / system.getPhase(0).getNumberOfMolesInPhase();

    system.setUseTVasIndependentVariables(true);
    system.setNumberOfPhases(1);
    system.setTemperature(Tc0);
    system.getPhase(0).setTotalVolume(Vc0 * system.getTotalNumberOfMoles());
    system.init(3);
    system.init(3);
    // system.display();

    for (int k = 0; k < 13; k++) {
      double detM;
      double olddetM;
      double ddetdT;
      double dT = 0.1;
      calcMmatrix();
      // int i = Mmatrix.eig().getNumberOfEigenvalues();
      SimpleMatrix eigenVector = Mmatrix.eig().getEigenVector(0);
      SimpleMatrix evalMatrix = eigenVector.transpose().mult(Mmatrix).mult(eigenVector);
      detM = Mmatrix.determinant(); // evalMatrix.get(0, 0);
      int iter = 0;
      system.setTemperature(system.getTemperature() + dT);

      // double dTOld = 111110;

      do {
        system.init(3);
        iter++;
        olddetM = detM;
        calcMmatrix();
        i = Mmatrix.eig().getNumberOfEigenvalues();
        eigenVector = Mmatrix.eig().getEigenVector(0);
        evalMatrix = eigenVector.transpose().mult(Mmatrix).mult(eigenVector);
        detM = Mmatrix.determinant(); // evalMatrix.get(0, 0);
        ddetdT = (detM - olddetM) / dT;
        // dTOld = dT;
        dT = -detM / ddetdT;
        if (Math.abs(dT) > 5.0) {
          // dT = Math.signum(dT) * 5.0;
        }
        double oldTemp = system.getTemperature();
        system.setTemperature(oldTemp + dT);
        logger.info("Temperature " + oldTemp + " dT " + dT + " evalMatrix " + evalMatrix.get(0, 0));
      } while (Math.abs(dT) > 1e-8 && iter < 112); // && (Math.abs(dT) < Math.abs(dTOld) ||
      // iter < 3));

      double dVc = Vc0 / 100.0;
      double ddetdV;
      double oldVal;
      system.init(3);
      double valstart = calcdpd();
      iter = 0;
      system.getPhase(0).setTotalVolume(system.getPhase(0).getTotalVolume() + dVc);
      double dVOld = 1111110;
      do {
        oldVal = valstart;
        system.init(3);
        iter++;
        valstart = calcdpd();
        ddetdV = (valstart - oldVal) / dVc;
        dVOld = dVc;
        dVc = -valstart / ddetdV;
        system.getPhase(0).setTotalVolume(system.getPhase(0).getVolume() + 0.5 * dVc);
        logger.info("Volume " + system.getPhase(0).getVolume() + " dVc " + dVc + " tddpp "
            + valstart + " pressure " + system.getPressure());
      } while (Math.abs(dVc) > 1e-5 && iter < 112 && (Math.abs(dVc) < Math.abs(dVOld) || iter < 3));
    }
    system.display();
    // solve(fmatrix);
    // dnMatrix.print(10, 10);
    // system.display();
    // clonedsystem.display();
  }
}
