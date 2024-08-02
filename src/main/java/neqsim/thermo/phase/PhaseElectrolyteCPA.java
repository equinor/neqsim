package neqsim.thermo.phase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.NormOps_DDRM;
import org.ejml.simple.SimpleMatrix;
import neqsim.thermo.component.ComponentCPAInterface;
import neqsim.thermo.component.ComponentElectrolyteCPA;
import neqsim.thermo.mixingRule.CPAMixing;
import neqsim.thermo.mixingRule.CPAMixingInterface;

/**
 * <p>
 * PhaseElectrolyteCPA class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseElectrolyteCPA extends PhaseModifiedFurstElectrolyteEos
    implements PhaseCPAInterface {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(PhaseElectrolyteCPA.class);

  public CPAMixing cpaSelect = new CPAMixing();
  public CPAMixingInterface cpamix;
  double gcpavv = 0.0;
  double gcpavvv = 0.0;
  double gcpa = 0.0;
  double hcpatot = 1.0;
  double FCPA = 0.0;
  double dFCPAdTdV;
  double dFCPAdTdT = 0.0;
  double dFCPAdT = 0;
  double dFCPAdV = 0;
  double dFCPAdVdV = 0.0;
  double dFCPAdVdVdV = 0.0;
  private double gcpav = 0.0;
  int cpaon = 1;
  int oldTotalNumberOfAccociationSites = 0;
  private int totalNumberOfAccociationSites = 0;
  int[][][] selfAccociationScheme = null;
  int[][][][] crossAccociationScheme = null;
  int[] moleculeNumber = null;
  int[] assSiteNumber = null;
  private double[][] gvector = null;
  private double[][] delta = null;
  private double[][] deltaNog = null;
  private double[][] deltadT = null;
  private double[][] deltadTdT = null;
  private double[][][] Klkni = null;
  private SimpleMatrix KlkTVMatrix = null;
  private SimpleMatrix KlkTTMatrix = null;
  private SimpleMatrix KlkTMatrix = null;
  private SimpleMatrix udotTimesmMatrix = null;
  private SimpleMatrix mVector = null;
  private SimpleMatrix udotMatrix = null;
  private SimpleMatrix uMatrix = null;
  private SimpleMatrix QMatksiksiksi = null;
  private SimpleMatrix KlkVVVMatrix = null;
  private SimpleMatrix KlkVVMatrix = null;
  private SimpleMatrix udotTimesmiMatrix = null;
  private SimpleMatrix ksiMatrix = null;
  private SimpleMatrix KlkMatrix = null;
  private SimpleMatrix hessianMatrix = null;
  private SimpleMatrix hessianInvers = null;
  private SimpleMatrix KlkVMatrix = null;
  DMatrixRMaj corr2Matrix = null;
  DMatrixRMaj corr3Matrix = null;
  DMatrixRMaj corr4Matrix = null;

  /**
   * <p>
   * Constructor for PhaseElectrolyteCPA.
   * </p>
   */
  public PhaseElectrolyteCPA() {
    super();
  }

  /** {@inheritDoc} */
  @Override
  public PhaseElectrolyteCPA clone() {
    PhaseElectrolyteCPA clonedPhase = null;
    try {
      clonedPhase = (PhaseElectrolyteCPA) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    // clonedPhase.cpaSelect = (CPAMixing) cpaSelect.clone();
    // clonedPhase.cpamix = (CPAMixingInterface) cpamix.clone();

    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void setMixingRule(int type) {
    super.setMixingRule(type);
    cpamix = cpaSelect.getMixingRule(1, this);
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
    if (initType == 0) {
      setTotalNumberOfAccociationSites(0);
      selfAccociationScheme = new int[numberOfComponents][0][0];
      crossAccociationScheme = new int[numberOfComponents][numberOfComponents][0][0];
      for (int i = 0; i < numberOfComponents; i++) {
        if (getComponent(i).getNumberOfmoles() < 1e-100) {
          getComponent(i).setNumberOfAssociationSites(0);
        } else {
          getComponent(i)
              .setNumberOfAssociationSites(getComponent(i).getOrginalNumberOfAssociationSites());
          setTotalNumberOfAccociationSites(
              getTotalNumberOfAccociationSites() + getComponent(i).getNumberOfAssociationSites());
          selfAccociationScheme[i] = cpaSelect.setAssociationScheme(i, this);
          for (int j = 0; j < numberOfComponents; j++) {
            crossAccociationScheme[i][j] = cpaSelect.setCrossAssociationScheme(i, j, this);
          }
        }
      }

      if (getTotalNumberOfAccociationSites() != oldTotalNumberOfAccociationSites) {
        mVector = new SimpleMatrix(getTotalNumberOfAccociationSites(), 1);
        KlkMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(),
            getTotalNumberOfAccociationSites());
        KlkVMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(),
            getTotalNumberOfAccociationSites());
        KlkVVMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(),
            getTotalNumberOfAccociationSites());
        KlkVVVMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(),
            getTotalNumberOfAccociationSites());
        hessianMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(),
            getTotalNumberOfAccociationSites());
        KlkTMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(),
            getTotalNumberOfAccociationSites());
        KlkTTMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(),
            getTotalNumberOfAccociationSites());
        KlkTVMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(),
            getTotalNumberOfAccociationSites());
        corr2Matrix = new DMatrixRMaj(getTotalNumberOfAccociationSites(), 1);
        corr3Matrix = new DMatrixRMaj(getTotalNumberOfAccociationSites(), 1);
        corr4Matrix = new DMatrixRMaj(getTotalNumberOfAccociationSites(), 1);
        Klkni =
            new double[numberOfComponents][getTotalNumberOfAccociationSites()][getTotalNumberOfAccociationSites()];
        ksiMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(), 1);
        uMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(), 1);
        udotMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(), 1);
        moleculeNumber = new int[getTotalNumberOfAccociationSites()];
        assSiteNumber = new int[getTotalNumberOfAccociationSites()];
        gvector = new double[getTotalNumberOfAccociationSites()][1];
        udotTimesmMatrix = new SimpleMatrix(getTotalNumberOfAccociationSites(), 1);
        udotTimesmiMatrix =
            new SimpleMatrix(getNumberOfComponents(), getTotalNumberOfAccociationSites());
        delta = new double[getTotalNumberOfAccociationSites()][getTotalNumberOfAccociationSites()];
        deltaNog =
            new double[getTotalNumberOfAccociationSites()][getTotalNumberOfAccociationSites()];
        deltadT =
            new double[getTotalNumberOfAccociationSites()][getTotalNumberOfAccociationSites()];
        deltadTdT =
            new double[getTotalNumberOfAccociationSites()][getTotalNumberOfAccociationSites()];
        QMatksiksiksi = new SimpleMatrix(getTotalNumberOfAccociationSites(), 1);
      }
      oldTotalNumberOfAccociationSites = getTotalNumberOfAccociationSites();

      int temp = 0;
      for (int i = 0; i < numberOfComponents; i++) {
        for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
          moleculeNumber[temp + j] = i;
          assSiteNumber[temp + j] = j;
        }
        temp += getComponent(i).getNumberOfAssociationSites();
      }
    }

    if (cpamix == null) {
      cpamix = cpaSelect.getMixingRule(1, this);
    }

    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
    if (initType > 0 && isConstantPhaseVolume()) {
      volInit();
      calcDelta();
      solveX();
      super.init(totalNumberOfMoles, numberOfComponents, 1, pt, beta);
      gcpa = calc_g();
      // lngcpa = Math.log(gcpa);
      setGcpav(calc_lngV());
      gcpavv = calc_lngVV();
      gcpavvv = calc_lngVVV();
    }

    if (initType > 0) {
      hcpatot = calc_hCPA();
    }

    if (initType > 1) {
      volInit();
      initCPAMatrix(initType);
      // hcpatotdT = calc_hCPAdT();
      // super.init(totalNumberOfMoles, numberOfComponents, type, pt, beta);
    }
    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
  }

  /**
   * <p>
   * calcDelta.
   * </p>
   */
  public void calcDelta() {
    for (int i = 0; i < totalNumberOfAccociationSites; i++) {
      for (int j = i; j < totalNumberOfAccociationSites; j++) {
        deltaNog[i][j] = cpamix.calcDeltaNog(assSiteNumber[i], assSiteNumber[j], moleculeNumber[i],
            moleculeNumber[j], this, getTemperature(), getPressure(), numberOfComponents);
        deltaNog[j][i] = delta[i][j];
      }
    }
  }

  /**
   * <p>
   * initCPAMatrix.
   * </p>
   *
   * @param type a int
   */
  public void initCPAMatrix(int type) {
    if (getTotalNumberOfAccociationSites() == 0) {
      FCPA = 0.0;
      dFCPAdTdV = 0.0;
      dFCPAdTdT = 0.0;
      dFCPAdT = 0;
      dFCPAdV = 0;
      dFCPAdVdV = 0.0;
      dFCPAdVdVdV = 0.0;

      return;
    }

    int temp = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
        uMatrix.set(temp + j, 0,
            Math.log(ksiMatrix.get(temp + j, 0)) - ksiMatrix.get(temp + j, 0) + 1.0);
        gvector[temp + j][0] = mVector.get(temp + j, 0) * udotMatrix.get(temp + j, 0);
      }
      temp += getComponent(i).getNumberOfAssociationSites();
    }
    for (int i = 0; i < getNumberOfComponents(); i++) {
      for (int j = 0; j < getTotalNumberOfAccociationSites(); j++) {
        if (moleculeNumber[j] == i) {
          udotTimesmiMatrix.set(i, j, udotMatrix.get(j, 0));
        } else {
          udotTimesmiMatrix.set(i, j, 0.0);
        }
      }
    }

    for (int i = 0; i < getTotalNumberOfAccociationSites(); i++) {
      for (int j = i; j < getTotalNumberOfAccociationSites(); j++) {
        delta[i][j] = deltaNog[i][j] * getGcpa();
        delta[j][i] = delta[i][j];
        if (type > 1) {
          deltadT[i][j] = cpamix.calcDeltadT(assSiteNumber[i], assSiteNumber[j], moleculeNumber[i],
              moleculeNumber[j], this, getTemperature(), getPressure(), numberOfComponents);
          deltadT[j][i] = deltadT[i][j];

          deltadTdT[i][j] =
              cpamix.calcDeltadTdT(assSiteNumber[i], assSiteNumber[j], moleculeNumber[i],
                  moleculeNumber[j], this, getTemperature(), getPressure(), numberOfComponents);
          deltadTdT[j][i] = deltadTdT[i][j];
        }
      }
    }

    double gdv1 = getGcpav() - 1.0 / getTotalVolume();
    double gdv2 = gdv1 * gdv1;
    double gdv3 = gdv2 * gdv1;
    double totVol = getTotalVolume();
    for (int i = 0; i < getTotalNumberOfAccociationSites(); i++) {
      for (int j = i; j < getTotalNumberOfAccociationSites(); j++) {
        KlkVMatrix.set(i, j, KlkMatrix.get(i, j) * gdv1);
        KlkVMatrix.set(j, i, KlkVMatrix.get(i, j));

        KlkVVMatrix.set(i, j, KlkMatrix.get(i, j) * gdv2
            + KlkMatrix.get(i, j) * (gcpavv + 1.0 / getTotalVolume() / getTotalVolume()));
        KlkVVMatrix.set(j, i, KlkVVMatrix.get(i, j));

        KlkVVVMatrix.set(i, j,
            KlkMatrix.get(i, j) * gdv3
                + 3.0 * KlkMatrix.get(i, j) * (getGcpav() - 1.0 / getTotalVolume())
                    * (gcpavv + 1.0 / (totVol * totVol))
                + KlkMatrix.get(i, j) * (gcpavvv - 2.0 / (totVol * totVol * totVol)));
        KlkVVVMatrix.set(j, i, KlkVVVMatrix.get(i, j));

        if (type > 1) {
          double tempVar = deltadT[i][j] / delta[i][j];
          double tempVardT = deltadTdT[i][j] / delta[i][j]
              - (deltadT[i][j] * deltadT[i][j]) / (delta[i][j] * delta[i][j]);

          if (!Double.isNaN(tempVar)) {
            // KlkdT[i][j] = KlkMatrix.getMatrix().unsafe_get(i, j) * tempVar;
            // KlkdT[j][i] = KlkdT[i][j];

            KlkTMatrix.set(i, j, KlkMatrix.get(i, j) * tempVar);
            KlkTMatrix.set(j, i, KlkTMatrix.get(i, j));

            KlkTVMatrix.set(i, j, KlkMatrix.get(i, j) * tempVar * (gcpav - 1.0 / getTotalVolume()));
            KlkTVMatrix.set(j, i, KlkTVMatrix.get(i, j));

            KlkTTMatrix.set(i, j, KlkMatrix.get(i, j) * (tempVar * tempVar + tempVardT));
            KlkTTMatrix.set(j, i, KlkTTMatrix.get(i, j));
          }

          if (type > 2) {
            for (int p = 0; p < numberOfComponents; p++) {
              double t1 = 0.0;
              double t2 = 0.0;
              if (moleculeNumber[i] == p) {
                t1 = 1.0 / mVector.get(i, 0);
              }
              if (moleculeNumber[j] == p) {
                t2 = 1.0 / mVector.get(j, 0);
              }
              Klkni[p][i][j] = KlkMatrix.get(i, j)
                  * (t1 + t2 + ((ComponentElectrolyteCPA) getComponent(p)).calc_lngi(this));
              Klkni[p][j][i] = Klkni[p][i][j];
            }
          }
        }
      }
      QMatksiksiksi.set(i, 0, 2.0 * mVector.get(i, 0)
          / (ksiMatrix.get(i, 0) * ksiMatrix.get(i, 0) * ksiMatrix.get(i, 0)));
    }

    SimpleMatrix ksiMatrixTranspose = ksiMatrix.transpose();

    // dXdV
    SimpleMatrix KlkVMatrixksi = KlkVMatrix.mult(ksiMatrix);
    SimpleMatrix XV = hessianInvers.mult(KlkVMatrixksi);
    SimpleMatrix XVtranspose = XV.transpose();

    SimpleMatrix QCPA =
        mVector.transpose().mult(uMatrix.minus(ksiMatrix.elementMult(udotMatrix).scale(0.5)));
    FCPA = QCPA.get(0, 0);

    SimpleMatrix tempMatrix = ksiMatrixTranspose.mult(KlkVMatrixksi).scale(-0.5);
    dFCPAdV = tempMatrix.get(0, 0);
    SimpleMatrix KlkVVMatrixTImesKsi = KlkVVMatrix.mult(ksiMatrix);
    SimpleMatrix tempMatrixVV = ksiMatrixTranspose.mult(KlkVVMatrixTImesKsi).scale(-0.5)
        .minus(KlkVMatrixksi.transpose().mult(XV));
    dFCPAdVdV = tempMatrixVV.get(0, 0);

    SimpleMatrix QVVV = ksiMatrixTranspose.mult(KlkVVVMatrix.mult(ksiMatrix)).scale(-0.5);
    SimpleMatrix QVVksi = KlkVVMatrixTImesKsi.scale(-1.0);
    SimpleMatrix QksiVksi = KlkVMatrix.scale(-1.0);

    SimpleMatrix mat1 = QVVksi.transpose().mult(XV).scale(3.0);
    SimpleMatrix mat2 = XVtranspose.mult(QksiVksi.mult(XV)).scale(3.0);
    SimpleMatrix mat4 = XVtranspose.mult(QMatksiksiksi.mult(XVtranspose)).mult(XV);

    SimpleMatrix dFCPAdVdVdVMatrix = QVVV.plus(mat1).plus(mat2).plus(mat2).plus(mat4);
    dFCPAdVdVdV = dFCPAdVdVdVMatrix.get(0, 0);
    temp = 0;

    if (type == 1) {
      return;
    }
    for (int p = 0; p < numberOfComponents; p++) {
      for (int kk = 0; kk < getComponent(p).getNumberOfAssociationSites(); kk++) {
        ((ComponentCPAInterface) getComponent(p)).setXsitedV(kk, XV.get(temp + kk, 0));
      }
      temp += getComponent(p).getNumberOfAssociationSites();
    }

    // KlkTMatrix = new SimpleMatrix(KlkdT);
    SimpleMatrix KlkTMatrixTImesKsi = KlkTMatrix.mult(ksiMatrix);
    // dQdT
    SimpleMatrix tempMatrix2 = ksiMatrixTranspose.mult(KlkTMatrixTImesKsi).scale(-0.5);
    dFCPAdT = tempMatrix2.get(0, 0);

    // SimpleMatrix KlkTVMatrix = new SimpleMatrix(KlkdTdV);
    // SimpleMatrix tempMatrixTV =
    // ksiMatrixTranspose.mult(KlkTVMatrix.mult(ksiMatrix)).scale(-0.5).minus(KlkTMatrixTImesKsi.transpose().mult(XV));
    // dFCPAdTdV = tempMatrixTV.get(0, 0);
    // dXdT
    SimpleMatrix XT = hessianInvers.mult(KlkTMatrixTImesKsi);
    // dQdTdT
    SimpleMatrix tempMatrixTT = ksiMatrixTranspose.mult(KlkTTMatrix.mult(ksiMatrix)).scale(-0.5)
        .minus(KlkTMatrixTImesKsi.transpose().mult(XT));
    dFCPAdTdT = tempMatrixTT.get(0, 0);

    SimpleMatrix tempMatrixTV = ksiMatrixTranspose.mult(KlkTVMatrix.mult(ksiMatrix)).scale(-0.5)
        .minus(KlkTMatrixTImesKsi.transpose().mult(XV));
    dFCPAdTdV = tempMatrixTV.get(0, 0);

    temp = 0;
    for (int p = 0; p < numberOfComponents; p++) {
      for (int kk = 0; kk < getComponent(p).getNumberOfAssociationSites(); kk++) {
        ((ComponentCPAInterface) getComponent(p)).setXsitedT(kk, XT.get(temp + kk, 0));
      }
      temp += getComponent(p).getNumberOfAssociationSites();
    }

    if (type == 2) {
      return;
    }

    // if(true) return;
    for (int p = 0; p < numberOfComponents; p++) {
      SimpleMatrix KiMatrix = new SimpleMatrix(Klkni[p]);
      // KiMatrix.print(10,10);
      // Matrix dQdniMatrix =
      // (ksiMatrix.transpose().times(KiMatrix.times(ksiMatrix)).times(-0.5)); // this
      // methods misses one part of ....
      // dQdniMatrix.print(10,10);
      // KiMatrix.print(10, 10);
      // miMatrix.getMatrix(assSites, assSites, 0, totalNumberOfAccociationSites -
      // 1).print(10, 10);
      // Matrix tempMatrix20 = miMatrix.getMatrix(assSites, assSites, 0,
      // totalNumberOfAccociationSites -
      // 1).times(uMatrix).minus(ksiMatrix.transpose().times(KiMatrix.times(ksiMatrix)).times(-0.5));
      // ksiMatrix.transpose().times(KlkTMatrix.times(ksiMatrix)).times(-0.5);
      // System.out.println("dQdn ");
      // tempMatrix20.print(10, 10);
      SimpleMatrix tempMatrix4 = KiMatrix.mult(ksiMatrix);
      // udotTimesmiMatrix.getMatrix(assSites, assSites, 0,
      // totalNumberOfAccociationSites - 1).print(10, 10);
      SimpleMatrix tempMatrix5 =
          udotTimesmiMatrix.extractVector(true, p).transpose().minus(tempMatrix4);
      // tempMki[0] = mki[p];
      // Matrix amatrix = new Matrix(croeneckerProduct(tempMki,
      // udotMatrix.getArray()));
      // System.out.println("aMatrix ");
      // amatrix.transpose().print(10, 10);
      // System.out.println("temp4 matrix");
      // tempMatrix4.print(10, 10);
      // Matrix tempMatrix5 = amatrix.minus(tempMatrix4);
      SimpleMatrix tempMatrix6 = hessianInvers.mult(tempMatrix5); // .scale(-1.0);
      // System.out.println("dXdni");
      // tempMatrix4.print(10, 10);
      // tempMatrix5.print(10, 10);
      // System.out.println("dXdn ");
      // tempMatrix6.print(10, 10);
      int temp2 = 0;
      for (int compp = 0; compp < numberOfComponents; compp++) {
        for (int kk = 0; kk < getComponent(compp).getNumberOfAssociationSites(); kk++) {
          ((ComponentCPAInterface) getComponent(compp)).setXsitedni(kk, p,
              -1.0 * tempMatrix6.get(temp2 + kk, 0));
        }
        temp2 += getComponent(compp).getNumberOfAssociationSites();
      }
      // assSites += getComponent(p).getNumberOfAssociationSites();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, moles, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentElectrolyteCPA(name, moles, molesInPhase, compNumber);
  }

  /** {@inheritDoc} */
  @Override
  public double getF() {
    return super.getF() + cpaon * FCPA();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdT() {
    return super.dFdT() + cpaon * dFCPAdT();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdTdV() {
    return super.dFdTdV() + cpaon * dFCPAdTdV();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdV() {
    double dv2 = dFCPAdV();
    return super.dFdV() + cpaon * dv2;
  }

  /** {@inheritDoc} */
  @Override
  public double dFdVdV() {
    return super.dFdVdV() + cpaon * dFCPAdVdV();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdVdVdV() {
    return super.dFdVdVdV() + cpaon * dFCPAdVdVdV();
  }

  /** {@inheritDoc} */
  @Override
  public double dFdTdT() {
    return super.dFdTdT() + cpaon * dFCPAdTdT();
  }

  /**
   * <p>
   * FCPA.
   * </p>
   *
   * @return a double
   */
  public double FCPA() {
    /*
     * double tot = 0.0; double ans = 0.0; for (int i = 0; i < numberOfComponents; i++) { tot = 0.0;
     * for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) { double xai =
     * ((ComponentSrkCPA) getComponent(i)).getXsite()[j]; tot += (Math.log(xai) - 1.0 / 2.0 * xai +
     * 1.0 / 2.0); } ans += getComponent(i).getNumberOfMolesInPhase() * tot; } return ans;
     */
    return FCPA;
  }

  /**
   * <p>
   * dFCPAdV.
   * </p>
   *
   * @return a double
   */
  public double dFCPAdV() {
    // return 1.0 / (2.0 * getTotalVolume()) * (1.0 - getTotalVolume() * getGcpav())
    // * hcpatot;
    return dFCPAdV;
  }

  /**
   * <p>
   * dFCPAdVdV.
   * </p>
   *
   * @return a double
   */
  public double dFCPAdVdV() {
    // double sum1 = -1.0 / 2.0 * gcpavv * hcpatot;
    // return -1.0 / getTotalVolume() * dFCPAdV() + sum1;
    return dFCPAdVdV;
  }

  /**
   * <p>
   * dFCPAdVdVdV.
   * </p>
   *
   * @return a double
   */
  public double dFCPAdVdVdV() {
    return dFCPAdVdVdV;
    // return -1.0 / getTotalVolume() * dFCPAdVdV() + 1.0 /
    // Math.pow(getTotalVolume(), 2.0) * dFCPAdV() - hcpatot / (2.0 *
    // Math.pow(getTotalVolume(), 2.0)) * (-getGcpav() - getTotalVolume() * gcpavv)
    // + hcpatot / (2.0 * getTotalVolume()) * (-gcpavv - getTotalVolume() * gcpavvv
    // - gcpavv);
  }

  /**
   * <p>
   * dFCPAdT.
   * </p>
   *
   * @return a double
   */
  public double dFCPAdT() {
    /*
     * double tot = 0.0; double ans = 0.0; for (int i = 0; i < numberOfComponents; i++) { tot = 0.0;
     * for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) { double xai =
     * ((ComponentSrkCPA) getComponent(i)).getXsite()[j]; double xaidT = ((ComponentSrkCPA)
     * getComponent(i)).getXsitedT()[j]; tot += 1.0 / xai * xaidT - 0.5 * xaidT; // - 1.0 / 2.0 *
     * xai + 1.0 / 2.0); } ans += getComponent(i).getNumberOfMolesInPhase() * tot; }
     * System.out.println("dFCPAdT1  " + ans + " dfcpa2 " +dFCPAdT); return ans;
     */
    return dFCPAdT;
  }

  /**
   * <p>
   * dFCPAdTdT.
   * </p>
   *
   * @return a double
   */
  public double dFCPAdTdT() {
    return dFCPAdTdT;
  }

  /**
   * <p>
   * dFCPAdTdV.
   * </p>
   *
   * @return a double
   */
  public double dFCPAdTdV() {
    // System.out.println("dFCPAdTdV1 " + dFCPAdTdV + " dFCPAdTdV " +( 1.0 / (2.0 *
    // getTotalVolume()) * (1.0 - getTotalVolume() * getGcpav()) * hcpatotdT));
    return dFCPAdTdV;
    /*
     * if (totalNumberOfAccociationSites > 0) { return 1.0 / (2.0 * getTotalVolume()) * (1.0 -
     * getTotalVolume() * getGcpav()) * hcpatotdT; } else { return 0; }
     */
  }

  /**
   * <p>
   * calc_hCPA.
   * </p>
   *
   * @return a double
   */
  public double calc_hCPA() {
    double htot = 0.0;
    double tot = 0.0;
    for (int i = 0; i < numberOfComponents; i++) {
      htot = 0.0;
      for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
        htot += (1.0 - ((ComponentCPAInterface) getComponent(i)).getXsite()[j]);
      }
      tot += getComponent(i).getNumberOfMolesInPhase() * htot;
    }
    return tot;
  }

  /**
   * <p>
   * calcXsitedV.
   * </p>
   */
  public void calcXsitedV() {
    if (totalNumberOfAccociationSites > 0) {
      initCPAMatrix(1);
    }
  }

  /**
   * <p>
   * solveX.
   * </p>
   *
   * @return a boolean
   */
  public boolean solveX() {
    if (getTotalNumberOfAccociationSites() == 0) {
      return true;
    }

    boolean solvedX = solveX2(5);
    if (solvedX) {
      // return true;
    }

    DMatrixRMaj mat1 = KlkMatrix.getMatrix();
    DMatrixRMaj mat2 = ksiMatrix.getMatrix();
    // second order method not working correctly and not used t the moment b ecause of numerical
    // stability
    int temp = 0;
    int iter = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
        mVector.set(temp + j, 0, getComponent(i).getNumberOfMolesInPhase());
      }
      temp += getComponent(i).getNumberOfAssociationSites();
    }
    double Klk = 0.0;
    double totvolume = getTotalVolume();
    for (int i = 0; i < getTotalNumberOfAccociationSites(); i++) {
      for (int j = i; j < getTotalNumberOfAccociationSites(); j++) {
        Klk = mVector.get(i, 0) * mVector.get(j, 0) / totvolume * delta[i][j];
        KlkMatrix.set(i, j, Klk);
        KlkMatrix.set(j, i, Klk);
      }
    }
    boolean solved = true;
    // SimpleMatrix corrMatrix = null;
    do {
      solved = true;
      iter++;
      temp = 0;
      for (int i = 0; i < numberOfComponents; i++) {
        for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
          ksiMatrix.set(temp + j, 0, ((ComponentCPAInterface) getComponent(i)).getXsite()[j]);
          // ksiMatrix.getMatrix().unsafe_set(temp + j, 0,
          // ksiMatrix.getMatrix().unsafe_get(temp + j, 0));
          udotMatrix.set(temp + j, 0, 1.0 / ksiMatrix.get(temp + j, 0) - 1.0);
          udotTimesmMatrix.set(temp + j, 0, mVector.get(temp + j, 0) * udotMatrix.get(temp + j, 0));
        }
        temp += getComponent(i).getNumberOfAssociationSites();
      }

      for (int i = 0; i < getTotalNumberOfAccociationSites(); i++) {
        for (int j = i; j < getTotalNumberOfAccociationSites(); j++) {
          int krondelt = 0;
          if (i == j) {
            krondelt = 1;
          }
          hessianMatrix.set(i, j,
              -mVector.get(i, 0) / (ksiMatrix.get(i, 0) * ksiMatrix.get(i, 0)) * krondelt
                  - KlkMatrix.get(i, j));
          hessianMatrix.set(j, i, hessianMatrix.get(i, j));
        }
      }

      // ksiMatrix = new SimpleMatrix(ksi);
      // SimpleMatrix hessianMatrix = new SimpleMatrix(hessian);
      try {
        hessianInvers = hessianMatrix.invert();
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
        return false;
      }

      CommonOps_DDRM.mult(mat1, mat2, corr2Matrix);
      CommonOps_DDRM.subtract(udotTimesmMatrix.getDDRM(), corr2Matrix, corr3Matrix);
      CommonOps_DDRM.mult(hessianInvers.getDDRM(), corr3Matrix, corr4Matrix);
      // SimpleMatrix gMatrix = udotTimesmMatrix.minus(KlkMatrix.mult(ksiMatrix));
      // corrMatrix =
      // hessianInvers.mult(udotTimesmMatrix.minus(KlkMatrix.mult(ksiMatrix))); //.scale(-1.0);
      temp = 0;
      // System.out.println("print SimpleMatrix ...");
      // corrMatrix.print(10, 10);
      // SimpleMatrix simp = new SimpleMatrix(corr4Matrix);
      // System.out.println("print CommonOps ...");
      // simp.print(10,10);
      for (int i = 0; i < numberOfComponents; i++) {
        for (int j = 0; j < getComponent(i).getNumberOfAssociationSites(); j++) {
          double newX = ksiMatrix.get(temp + j, 0) - corr4Matrix.unsafe_get((temp + j), 0);
          if (newX < 0) {
            newX = 1e-10;
            solved = false;
          }
          ((ComponentCPAInterface) getComponent(i)).setXsite(j, newX);
        }
        temp += getComponent(i).getNumberOfAssociationSites();
      }
      // System.out.println("corrmatrix error " );
      // System.out.println("error " + corrMatrix.norm1());
    } while ((NormOps_DDRM.normF(corr4Matrix) > 1e-12 || !solved) && iter < 100);

    // System.out.println("iter " + iter + " error " + NormOps.normF(corr4Matrix));
    // // corrMatrix.print(10, 10);
    // ksiMatrix.print(10, 10);
    return true;
  }

  /**
   * <p>
   * solveX2.
   * </p>
   *
   * @param maxIter a int
   * @return a boolean
   */
  public boolean solveX2(int maxIter) {
    double err = .0;
    int iter = 0;
    // if (delta == null) {
    // initCPAMatrix(1);
    double old = 0.0;
    double neeval = 0.0;
    // }
    do {
      iter++;
      err = 0.0;
      for (int i = 0; i < getTotalNumberOfAccociationSites(); i++) {
        old =
            ((ComponentCPAInterface) getComponent(moleculeNumber[i])).getXsite()[assSiteNumber[i]];
        neeval = 0;
        for (int j = 0; j < getTotalNumberOfAccociationSites(); j++) {
          neeval += getComponent(moleculeNumber[j]).getNumberOfMolesInPhase() * delta[i][j]
              * ((ComponentCPAInterface) getComponent(moleculeNumber[j]))
                  .getXsite()[assSiteNumber[j]];
        }
        neeval = 1.0 / (1.0 + 1.0 / getTotalVolume() * neeval);
        ((ComponentCPAInterface) getComponent(moleculeNumber[i])).setXsite(assSiteNumber[i],
            neeval);
        err += Math.abs((old - neeval) / neeval);
      }
    } while (Math.abs(err) > 1e-10 && iter < maxIter);
    // System.out.println("iter " + iter);
    // if (Math.abs(err)
    // < 1e-12) {
    // return true;
    // } else {
    // System.out.println("did not solve for Xi in iterations: " + iter);
    // System.out.println("error: " + err);
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public double getHcpatot() {
    return hcpatot;
  }

  /**
   * Setter for property hcpatot.
   *
   * @param hcpatot New value of property hcpatot.
   */
  public void setHcpatot(double hcpatot) {
    this.hcpatot = hcpatot;
  }

  /** {@inheritDoc} */
  @Override
  public double getGcpa() {
    return gcpa;
  }

  /**
   * <p>
   * calcRootVolFinder.
   * </p>
   *
   * @param pt the PhaseType of the phase
   * @return a double
   */
  public double calcRootVolFinder(PhaseType pt) {
    double solvedBonVHigh = 0.0;
    double solvedBonVlow = 1.0;
    double oldh = 1;
    // double[][] matrix = new double[2][2000];
    double BonV = 1.0 - 1e-10;
    try {
      // molarVolume(pressure, temperature, A, B, pt);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    double BonVold = BonV;
    double Btemp = 0;
    double h = 1;
    Btemp = getB();
    getA();
    setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
    for (int i = 0; i < 2000; i++) {
      BonVold = BonV;
      BonV = 1.0 - (i + 1e-6) * 1.0 / 2000.0;
      setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
      Z = pressure * getMolarVolume() / (R * temperature);
      gcpa = calc_g();
      // lngcpa =
      // Math.log(gcpa);
      setGcpav(calc_lngV());
      gcpavv = calc_lngVV();
      gcpavvv = calc_lngVVV();
      volInit();
      do {
      } while (!solveX());

      h = BonV - Btemp / numberOfMolesInPhase * dFdV()
          - pressure * Btemp / (numberOfMolesInPhase * R * temperature);

      if (Math.signum(h) * Math.signum(oldh) < 0 && i > 2) {
        if (solvedBonVlow < 1e-3) {
          solvedBonVlow = (BonV + BonVold) / 2.0;
          if (pt == PhaseType.GAS) {
            break;
          }
        } else {
          solvedBonVHigh = (BonV + BonVold) / 2.0;
          if (pt == PhaseType.LIQUID) {
            break;
          }
        }
      }
      solvedBonVHigh = (BonV + BonVold) / 2.0;
      oldh = h;
      // matrix[0][i] = BonV;
      // matrix[1][i] = h;
    }
    if (solvedBonVlow < 1e-3) {
      solvedBonVlow = solvedBonVHigh;
    }
    // dataPresentation.fileHandeling.createTextFile.TextFile file = new
    // dataPresentation.fileHandeling.createTextFile.TextFile();
    // file.setValues(matrix);
    // file.setOutputFileName("D:/temp/temp2.txt");
    // file.createFile();
    if (pt == PhaseType.GAS) {
      return solvedBonVlow;
    } else {
      return solvedBonVHigh;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume2(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    double BonV =
        pt == PhaseType.LIQUID ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
            : pressure * getB() / (numberOfMolesInPhase * temperature * R);
    if (BonV < 0) {
      BonV = 1.0e-8;
    }

    if (BonV > 1.1) {
      BonV = 1.1;
    }
    double BonVold = BonV;
    double Btemp = 0;
    double h = 0;
    double dh = 0;
    double dhh = 0;
    double d1 = 0;
    double d2 = 0;
    Btemp = getB();
    if (Btemp < 0) {
      logger.info("b negative in volume calc");
    }
    calcDelta();

    setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
    int iterations = 0;

    do {
      volInit();
      iterations++;
      gcpa = calc_g();
      if (gcpa < 0) {
        setMolarVolume(1.0 / Btemp / numberOfMolesInPhase);
        gcpa = calc_g();
      }

      // double lngcpa = Math.log(gcpa);
      setGcpav(calc_lngV());
      gcpavv = calc_lngVV();
      gcpavvv = calc_lngVVV();

      solveX();

      hcpatot = calc_hCPA();

      initCPAMatrix(1);
      volInit();
      double BonV2 = BonV * BonV;
      BonVold = BonV;
      h = BonV - Btemp / numberOfMolesInPhase * dFdV()
          - pressure * Btemp / (numberOfMolesInPhase * R * temperature);
      dh = 1.0 + Btemp / (BonV2) * (Btemp / numberOfMolesInPhase * dFdVdV());
      dhh = -2.0 * Btemp / (BonV2 * BonV) * (Btemp / numberOfMolesInPhase * dFdVdV())
          - (Btemp * Btemp) / (BonV2 * BonV2) * (Btemp / numberOfMolesInPhase * dFdVdVdV());

      d1 = -h / dh;
      d2 = -dh / dhh;
      // System.out.println("d1" + d1 + " d2 " + d2 + " d1 / d2 " + (d1 / d2));
      if (Math.abs(d1 / d2) <= 1.0) {
        BonV += d1 * (1.0 + 0.5 * d1 / d2);
      } else if (d1 / d2 < -1) {
        BonV += 0.5 * d1;
      } else if (d1 > d2) {
        BonV += d2;
        double hnew = h + d2 * dh;
        if (Math.abs(hnew) > Math.abs(h)) {
          BonV = pt == PhaseType.GAS ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
              : pressure * getB() / (numberOfMolesInPhase * temperature * R);
        }
      } else {
        BonV += 0.5 * d1;
      }
      if (Math.abs((BonV - BonVold) / BonVold) > 0.1) {
        BonV = BonVold + 0.1 * (BonV - BonVold);
      }

      if (BonV > 1.1) {
        if (iterations < 3) {
          BonV = (BonVold + BonV) / 2.0;
        } else {
          BonV = pt == PhaseType.GAS ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
              : pressure * getB() / (numberOfMolesInPhase * temperature * R);
        }
      }

      if (BonV < 0) {
        if (iterations < 3) {
          BonV = Math.abs(BonVold + BonV) / 2.0;
        } else {
          BonV = pt == PhaseType.GAS ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
              : pressure * getB() / (numberOfMolesInPhase * temperature * R);
        }
      }

      setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
      Z = pressure * getMolarVolume() / (R * temperature);

      // System.out.println("Z" + Z);
    } while (Math.abs((BonV - BonVold) / BonV) > 1.0e-10 && iterations < 100);
    // System.out.println("Z" + Z + " iterations " + iterations + " BonV " + BonV);
    // System.out.println("pressure " + Z*R*temperature/getMolarVolume());
    // System.out.println("volume " + getTotalVolume() + " molar volume " +
    // getMolarVolume());
    // if(iterations>=100) throw new util.exception.TooManyIterationsException();
    // System.out.println("error in volume " +
    // (-pressure+R*temperature/getMolarVolume()-R*temperature*dFdV())); // + "
    // firstterm " + (R*temperature/molarVolume) + " second " +
    // R*temperature*dFdV());

    if (Double.isNaN(getMolarVolume())) {
      // System.out.println("BonV: " + BonV + " "+" itert: " + iterations +" " +h + "
      // " +dh + " B " + Btemp + " D " + Dtemp + " gv" + gV() + " fv " + fv() + " fvv"
      // + fVV());
      throw new neqsim.util.exception.IsNaNException(this, "molarVolume2", "Molar volume");
      // System.out.println("BonV: " + BonV + " "+" itert: " + iterations +" " +h + "
      // " +dh + " B " + Btemp + " D " + Dtemp + " gv" + gV() + " fv " + fv() + " fvv"
      // + fVV());
    }

    return getMolarVolume();
  }

  /** {@inheritDoc} */
  @Override
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    double BonV =
        pt == PhaseType.LIQUID ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
            : pressure * getB() / (numberOfMolesInPhase * temperature * R);
    // if (pressure > 1000) {
    // BonV = 0.9999;
    // }

    // double calcRooBonVtVolFinder = calcRootVolFinder(pt);
    // BonV = calcRooBonVtVolFinder;
    // double BonVInit = BonV;
    if (BonV <= 0) {
      BonV = 1.0e-8;
    } else if (BonV >= 1.0) {
      BonV = 0.9999;
    }
    double BonVold;
    double h = 0;
    double dh = 0;
    double dhh = 0;
    double d1 = 0;
    double d2 = 0;
    double Btemp = getB();
    if (Btemp < 0) {
      logger.info("b negative in volume calc");
    }
    calcDelta();

    setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
    int iterations = 0;

    do {
      iterations++;
      volInit();
      gcpa = calc_g();
      if (gcpa < 0) {
        setMolarVolume(1.0 / Btemp / numberOfMolesInPhase);
        gcpa = calc_g();
      }

      // lngcpa =
      // Math.log(gcpa);
      setGcpav(calc_lngV());
      gcpavv = calc_lngVV();
      gcpavvv = calc_lngVVV();

      if (getTotalNumberOfAccociationSites() > 0) {
        solveX();
      }

      initCPAMatrix(1);
      double BonV2 = BonV * BonV;
      BonVold = BonV;
      h = BonV - Btemp / numberOfMolesInPhase * dFdV()
          - pressure * Btemp / (numberOfMolesInPhase * R * temperature);
      dh = 1.0 + Btemp / (BonV2) * (Btemp / numberOfMolesInPhase * dFdVdV());
      dhh = -2.0 * Btemp / (BonV2 * BonV) * (Btemp / numberOfMolesInPhase * dFdVdV())
          - (Btemp * Btemp) / (BonV2 * BonV2) * (Btemp / numberOfMolesInPhase * dFdVdVdV());

      d1 = -h / dh;
      d2 = -dh / dhh;
      // System.out.println("h " + h + " iter " + iterations + " " + d1 + " d2 " + d2
      // + " d1 / d2 " + (d1 / d2));
      if (Math.abs(d1 / d2) <= 1.0) {
        BonV += d1 * (1.0 + 0.5 * d1 / d2);
      } else if (d1 / d2 < -1) {
        BonV += 0.5 * d1;
      } else if (d1 > d2) {
        BonV += d2;
        double hnew = h + d2 * dh;
        if (Math.abs(hnew) > Math.abs(h)) {
          BonV = pt == PhaseType.GAS ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
              : pressure * getB() / (numberOfMolesInPhase * temperature * R);
        }
      } else {
        BonV += 0.5 * d1;
      }
      if (Math.abs((BonV - BonVold) / BonVold) > 0.1) {
        BonV = BonVold + 0.1 * (BonV - BonVold);
      }

      if (BonV > 0.9999) {
        if (iterations < 3) {
          BonV = (BonVold + BonV) / 2.0;
        } else {
          return molarVolumeChangePhase(pressure, temperature, A, B, pt);
          // BonV = 0.9999;
          // BonV = pt == 1 ? 2.0 / (2.0 + temperature /
          // getPseudoCriticalTemperature()) : pressure * getB() / (numberOfMolesInPhase *
          // temperature * R);
        }
      } else if (BonV < 0) {
        if (iterations < 3) {
          BonV = Math.abs(BonVold + BonV) / 2.0;
        } else {
          return molarVolumeChangePhase(pressure, temperature, A, B, pt);
          // BonV = pt == 1 ? 2.0 / (2.0 + temperature /
          // getPseudoCriticalTemperature()) : pressure * getB() / (numberOfMolesInPhase *
          // temperature * R);
        }
      }
      setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
      Z = pressure * getMolarVolume() / (R * temperature);
    } while ((Math.abs((BonV - BonVold) / BonV) > 1.0e-10 || Math.abs(h) > 1e-9)
        && iterations < 100);

    if (Math.abs(h) > 1e-9) {
      // System.out.println("h failed " + "Z" + Z + " iterations " + iterations + "
      // BonV " + BonV);
      return molarVolumeChangePhase(pressure, temperature, A, B, pt);
    }
    // System.out.println("Z" + Z + " iterations " + iterations + " BonV " + BonV);
    // System.out.println("pressure " + Z*R*temperature/getMolarVolume());
    // System.out.println("volume " + getTotalVolume() + " molar volume " +
    // getMolarVolume());
    // if(iterations>=100) throw new util.exception.TooManyIterationsException();
    // System.out.println("error in volume " +
    // (-pressure+R*temperature/getMolarVolume()-R*temperature*dFdV())); // + "
    // firstterm " + (R*temperature/molarVolume) + " second " +
    // R*temperature*dFdV());
    // System.out.println("BonV: " + BonV + " "+" itert: " + iterations +" " +h + " " +dh + " B
    // " + Btemp + " gv" + gV() + " fv " + fv() + " fvv" + fVV());

    if (Double.isNaN(getMolarVolume())) {
      throw new neqsim.util.exception.IsNaNException(this, "molarVolume", "Molar volume");
      // System.out.println("BonV: " + BonV + " "+" itert: " + iterations +" " +h + "
      // " +dh + " B " + Btemp + " D " + Dtemp + " gv" + gV() + " fv " + fv() + " fvv"
      // + fVV());
    }
    return getMolarVolume();
  }

  /**
   * <p>
   * molarVolumeChangePhase.
   * </p>
   *
   * @param pressure a double
   * @param temperature a double
   * @param A a double
   * @param B a double
   * @param pt the PhaseType of the phase
   * @return a double
   * @throws neqsim.util.exception.IsNaNException if any.
   * @throws neqsim.util.exception.TooManyIterationsException if any.
   */
  public double molarVolumeChangePhase(double pressure, double temperature, double A, double B,
      PhaseType pt) throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    // double BonV = pt == 1 ? 2.0 / (2.0 + temperature /
    // getPseudoCriticalTemperature()) : pressure * getB() / (numberOfMolesInPhase *
    // temperature * R);
    double BonV = calcRootVolFinder(pt);
    // double BonVInit = BonV;
    if (BonV < 0) {
      BonV = 1.0e-8;
    }

    if (BonV >= 1.0) {
      BonV = 0.9999;
    }
    double BonVold = BonV;
    double Btemp = 0;
    double h = 0;
    double dh = 0;
    double dhh = 0;
    double d1 = 0;
    double d2 = 0;
    Btemp = getB();
    if (Btemp < 0) {
      logger.info("b negative in volume calc");
    }
    calcDelta();

    setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
    int iterations = 0;

    do {
      volInit();
      iterations++;
      gcpa = calc_g();
      if (gcpa < 0) {
        setMolarVolume(1.0 / Btemp / numberOfMolesInPhase);
        gcpa = calc_g();
      }

      // lngcpa =
      // Math.log(gcpa);
      setGcpav(calc_lngV());
      gcpavv = calc_lngVV();
      gcpavvv = calc_lngVVV();

      solveX();

      initCPAMatrix(1);
      double BonV2 = BonV * BonV;
      BonVold = BonV;
      h = BonV - Btemp / numberOfMolesInPhase * dFdV()
          - pressure * Btemp / (numberOfMolesInPhase * R * temperature);
      dh = 1.0 + Btemp / (BonV2) * (Btemp / numberOfMolesInPhase * dFdVdV());
      dhh = -2.0 * Btemp / (BonV2 * BonV) * (Btemp / numberOfMolesInPhase * dFdVdV())
          - (Btemp * Btemp) / (BonV2 * BonV2) * (Btemp / numberOfMolesInPhase * dFdVdVdV());

      d1 = -h / dh;
      d2 = -dh / dhh;
      // System.out.println("d1" + d1 + " d2 " + d2 + " d1 / d2 " + (d1 / d2));
      if (Math.abs(d1 / d2) <= 1.0) {
        BonV += d1 * (1.0 + 0.5 * d1 / d2);
      } else if (d1 / d2 < -1) {
        BonV += 0.5 * d1;
      } else if (d1 > d2) {
        BonV += d2;
        double hnew = h + d2 * dh;
        if (Math.abs(hnew) > Math.abs(h)) {
          BonV = pt == PhaseType.GAS ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
              : pressure * getB() / (numberOfMolesInPhase * temperature * R);
        }
      } else {
        BonV += 0.5 * d1;
      }
      if (Math.abs((BonV - BonVold) / BonVold) > 0.1) {
        BonV = BonVold + 0.1 * (BonV - BonVold);
      }

      if (BonV > 1.1) {
        if (iterations < 3) {
          BonV = (BonVold + BonV) / 2.0;
        } else {
          BonV = pt == PhaseType.GAS ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
              : pressure * getB() / (numberOfMolesInPhase * temperature * R);
        }
      }

      if (BonV < 0) {
        if (iterations < 3) {
          BonV = Math.abs(BonVold + BonV) / 2.0;
        } else {
          BonV = pt == PhaseType.GAS ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
              : pressure * getB() / (numberOfMolesInPhase * temperature * R);
        }
      }

      setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
      Z = pressure * getMolarVolume() / (R * temperature);

      // System.out.println("Z " + Z + "h " + h + " BONV " + (Math.abs((BonV -
      // BonVold) / BonV)));
    } while ((Math.abs((BonV - BonVold) / BonV) > 1.0e-10) && iterations < 100);

    /*
     * if (Math.abs(h) > 1e-8) { if (pt == 0) { molarVolume(pressure, temperature, A, B, 1); } else
     * { molarVolume(pressure, temperature, A, B, 0); } return getMolarVolume(); }
     */
    // System.out.println("Z" + Z + " iterations " + iterations + " BonV " + BonV);
    // System.out.println("pressure " + Z*R*temperature/getMolarVolume());
    // System.out.println("volume " + getTotalVolume() + " molar volume " +
    // getMolarVolume());
    // if(iterations>=100) throw new util.exception.TooManyIterationsException();
    // System.out.println("error in volume " +
    // (-pressure+R*temperature/getMolarVolume()-R*temperature*dFdV())); // + "
    // firstterm " + (R*temperature/molarVolume) + " second " +
    // R*temperature*dFdV());
    // System.out.println("BonV: " + BonV + " "+" itert: " + iterations +" " +h + " " +dh + " B
    // " + Btemp + " gv" + gV() + " fv " + fv() + " fvv" + fVV());
    if (Double.isNaN(getMolarVolume())) {
      throw new neqsim.util.exception.IsNaNException(this, "molarVolumeChangePhase",
          "Molar volume");
      // System.out.println("BonV: " + BonV + " "+" itert: " + iterations +" " +h + "
      // " +dh + " B " + Btemp + " D " + Dtemp + " gv" + gV() + " fv " + fv() + " fvv"
      // + fVV());
    }

    return getMolarVolume();
  }

  /** {@inheritDoc} */
  @Override
  public double getGcpav() {
    return gcpav;
  }

  /**
   * <p>
   * Setter for the field <code>gcpav</code>.
   * </p>
   *
   * @param gcpav a double
   */
  public void setGcpav(double gcpav) {
    this.gcpav = gcpav;
  }

  /** {@inheritDoc} */
  @Override
  public CPAMixingInterface getCpamix() {
    return cpamix;
  }

  /** {@inheritDoc} */
  @Override
  public double calcPressure() {
    gcpa = calc_g();
    // lngcpa =
    // Math.log(gcpa);
    setGcpav(calc_lngV());
    gcpavv = calc_lngVV();
    gcpavvv = calc_lngVVV();
    solveX();
    hcpatot = calc_hCPA();

    initCPAMatrix(1);
    return super.calcPressure();
  }

  /** {@inheritDoc} */
  @Override
  public int getCrossAssosiationScheme(int comp1, int comp2, int site1, int site2) {
    if (comp1 == comp2) {
      return selfAccociationScheme[comp1][site1][site2];
    } else {
      return crossAccociationScheme[comp1][comp2][site1][site2];
    }
  }

  /**
   * <p>
   * croeneckerProduct.
   * </p>
   *
   * @param a an array of type double
   * @param b an array of type double
   * @return an array of type double
   */
  public double[][] croeneckerProduct(double[][] a, double[][] b) {
    int aLength = a.length;
    int aCols = a[0].length;
    int bLength = b.length;
    int bCols = b[0].length;
    double[][] result = new double[aLength * bLength][(aCols) * (bCols)];
    for (int z = 0; z < aLength; z++) {
      for (int i = 0; i < aCols; i++) {
        for (int j = 0; j < bLength; j++) {
          for (int k = 0; k < bCols; k++) {
            result[j + (z * bLength)][k + (i * bCols)] = a[z][i] * b[j][k];
          }
        }
      }
    }
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public int getTotalNumberOfAccociationSites() {
    return totalNumberOfAccociationSites;
  }

  /** {@inheritDoc} */
  @Override
  public void setTotalNumberOfAccociationSites(int totalNumberOfAccociationSites) {
    this.totalNumberOfAccociationSites = totalNumberOfAccociationSites;
  }

  /**
   * <p>
   * calc_g.
   * </p>
   *
   * @return a double
   */
  public double calc_g() {
    double x = 1.9 / 4.0 * getB() / getTotalVolume();
    double g = 1.0 / (1.0 - x);
    // System.out.println("g " + g);
    return g;
  }

  /**
   * <p>
   * calc_lngV.
   * </p>
   *
   * @return a double
   */
  public double calc_lngV() {
    double x = 1.9 / 4.0 * getB() / getTotalVolume();
    double gv = (x / getTotalVolume()) / (1.0 - x);
    return -gv;
  }

  /**
   * <p>
   * calc_lngVV.
   * </p>
   *
   * @return a double
   */
  public double calc_lngVV() {
    double x = 1.9 / 4.0 * getB() / getTotalVolume();
    double xV = -1.9 / 4.0 * getB() / (getTotalVolume() * getTotalVolume());
    double u = 1.0 - x;

    double val = -x / (getTotalVolume() * getTotalVolume() * u) + xV / (getTotalVolume() * u)
        - x / (getTotalVolume() * u * u) * (-1.0) * xV;
    return -val;

    // double gvv
    // =0.225625/Math.pow(1.0-0.475*getB()/getTotalVolume(),2.0)*Math.pow(getB(),2.0)/(Math.pow(getTotalVolume(),4.0))+0.95/(1.0-0.475*getB()/getTotalVolume())*getB()/(Math.pow(getTotalVolume(),3.0));
    // System.out.println("val2 " + gvv);
    // return gvv;
  }

  /**
   * <p>
   * calc_lngVVV.
   * </p>
   *
   * @return a double
   */
  public double calc_lngVVV() {
    double totVol = getTotalVolume();
    double totVol4 = totVol * totVol * totVol * totVol;
    double totVol5 = totVol4 * totVol;

    double temp1 = 1.0 - 0.475 * getB() / getTotalVolume();
    double gvv =
        -0.21434375 / (temp1 * temp1 * temp1) * getB() * getB() * getB() / (totVol5 * totVol)
            - 0.135375E1 / (temp1 * temp1) * getB() * getB() / (totVol5)
            - 0.285E1 / (temp1) * getB() / (totVol4);
    return gvv;
  }
}
