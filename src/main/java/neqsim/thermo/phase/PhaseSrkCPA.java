package neqsim.thermo.phase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.NormOps_DDRM;
import org.ejml.simple.SimpleMatrix;
import neqsim.thermo.component.ComponentCPAInterface;
import neqsim.thermo.component.ComponentSrkCPA;
import neqsim.thermo.mixingrule.CPAMixingRules;
import neqsim.thermo.mixingrule.CPAMixingRulesInterface;

/**
 * <p>
 * PhaseSrkCPA class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class PhaseSrkCPA extends PhaseSrkEos implements PhaseCPAInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PhaseSrkCPA.class);

  public CPAMixingRules cpaSelect = new CPAMixingRules();
  public CPAMixingRulesInterface cpamix;
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
  double gcpav = 0.0;
  double tempTotVol = 0;
  private double[] dFdNtemp = {0, 0};
  int cpaon = 1;
  int oldTotalNumberOfAccociationSites = 0;
  int totalNumberOfAccociationSites = 0;
  int[][][] selfAccociationScheme = null;
  int[][][][] crossAccociationScheme = null;
  int[] activeAccosComp = null; // new int[100];
  private double[] lngi;
  int[] moleculeNumber = null;
  int[] assSiteNumber = null;
  private double[][] gvector = null;
  private double[][] delta = null;
  private double[][] deltaNog = null;
  private double[][] deltadT = null;
  private double[][] deltadTdT = null;
  double[][][] Klkni = null;
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
  private DMatrixRMaj corr2Matrix = null;
  private DMatrixRMaj corr3Matrix = null;
  private DMatrixRMaj corr4Matrix = null;

  /**
   * <p>
   * Constructor for PhaseSrkCPA.
   * </p>
   */
  public PhaseSrkCPA() {
    thermoPropertyModelName = "SRK-CPA-EoS";
  }

  /** {@inheritDoc} */
  @Override
  public PhaseSrkCPA clone() {
    PhaseSrkCPA clonedPhase = null;
    try {
      clonedPhase = (PhaseSrkCPA) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    if (activeAccosComp != null) {
      clonedPhase.activeAccosComp = activeAccosComp.clone();
      System.arraycopy(this.activeAccosComp, 0, clonedPhase.activeAccosComp, 0,
          activeAccosComp.length);
    }
    // clonedPhase.cpaSelect = (CPAMixing) cpaSelect.clone();
    // clonedPhase.cpamix = (CPAMixingInterface) cpamix.clone();
    // clonedPhase.cpamix = cpaSelect.getMixingRule(1, this);

    return clonedPhase;
  }

  /** {@inheritDoc} */
  @Override
  public void init(double totalNumberOfMoles, int numberOfComponents, int initType, PhaseType pt,
      double beta) {
    boolean changedAssosiationStatus = false;

    if (initType == 0) {
      activeAccosComp = new int[numberOfComponents];
      for (int i = 0; i < numberOfComponents; i++) {
        if (componentArray[i].getNumberOfmoles() < 1e-50) {
          componentArray[i].setNumberOfAssociationSites(0);
          if (activeAccosComp[i] == 1) {
            activeAccosComp[i] = 0;
            changedAssosiationStatus = true;
          }
        } else {
          if (activeAccosComp[i] == 0) {
            changedAssosiationStatus = true;
            activeAccosComp[i] = 1;
          }
        }
      }

      if (changedAssosiationStatus || lngi == null) {
        setTotalNumberOfAccociationSites(0);
        selfAccociationScheme = new int[numberOfComponents][0][0];
        crossAccociationScheme = new int[numberOfComponents][numberOfComponents][0][0];
        for (int i = 0; i < numberOfComponents; i++) {
          if (componentArray[i].getNumberOfmoles() < 1e-50) {
            componentArray[i].setNumberOfAssociationSites(0);
          } else {
            componentArray[i].setNumberOfAssociationSites(
                componentArray[i].getOrginalNumberOfAssociationSites());
            setTotalNumberOfAccociationSites(getTotalNumberOfAccociationSites()
                + componentArray[i].getNumberOfAssociationSites());
            selfAccociationScheme[i] = cpaSelect.setAssociationScheme(i, this);
            for (int j = 0; j < numberOfComponents; j++) {
              crossAccociationScheme[i][j] = cpaSelect.setCrossAssociationScheme(i, j, this);
            }
          }
        }
      }

      for (int i = 0; i < numberOfComponents; i++) {
        for (int j = 0; j < componentArray[i].getNumberOfAssociationSites(); j++) {
          ((ComponentSrkCPA) componentArray[i]).setXsite(j, 1.0);
          ((ComponentSrkCPA) componentArray[i]).setXsitedV(j, 0.0);
          ((ComponentSrkCPA) componentArray[i]).setXsitedT(j, 0.0);
        }
      }

      if (changedAssosiationStatus || lngi == null || mVector == null) {
        lngi = new double[numberOfComponents];
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
        delta = new double[getTotalNumberOfAccociationSites()][getTotalNumberOfAccociationSites()];
        deltaNog =
            new double[getTotalNumberOfAccociationSites()][getTotalNumberOfAccociationSites()];
        deltadT =
            new double[getTotalNumberOfAccociationSites()][getTotalNumberOfAccociationSites()];
        deltadTdT =
            new double[getTotalNumberOfAccociationSites()][getTotalNumberOfAccociationSites()];
        QMatksiksiksi = new SimpleMatrix(getTotalNumberOfAccociationSites(), 1);
        udotTimesmiMatrix =
            new SimpleMatrix(numberOfComponents, getTotalNumberOfAccociationSites());

        oldTotalNumberOfAccociationSites = getTotalNumberOfAccociationSites();

        int temp = 0;
        for (int i = 0; i < numberOfComponents; i++) {
          for (int j = 0; j < componentArray[i].getNumberOfAssociationSites(); j++) {
            moleculeNumber[temp + j] = i;
            assSiteNumber[temp + j] = j;
          }
          temp += componentArray[i].getNumberOfAssociationSites();
        }
      }
    }

    if (cpamix == null) {
      // NB! Hardcoded mixing rule type
      cpamix = cpaSelect.getMixingRule(1, this);
    }
    if (initType > 0) {
      calcDelta();
    }

    super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);

    if (initType > 0 && isConstantPhaseVolume()) {
      solveX();
      super.init(totalNumberOfMoles, numberOfComponents, 1, pt, beta);
      gcpa = calc_g();
      gcpav = calc_lngV();
      gcpavv = calc_lngVV();
      gcpavvv = calc_lngVVV();
    }

    if (initType > 0) {
      hcpatot = calc_hCPA();
    }

    if (initType > 1) {
      initCPAMatrix(initType);
      super.init(totalNumberOfMoles, numberOfComponents, initType, pt, beta);
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
    if (totalNumberOfAccociationSites == 0) {
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
    double tempVar1;
    double tempVar2;
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < componentArray[i].getNumberOfAssociationSites(); j++) {
        tempVar1 = ksiMatrix.get(temp + j, 0);
        tempVar2 = udotMatrix.get(temp + j, 0);
        uMatrix.set(temp + j, 0, Math.log(tempVar1) - tempVar1 + 1.0);
        gvector[temp + j][0] = mVector.get(temp + j, 0) * tempVar2;

        if (moleculeNumber[temp + j] == i) {
          udotTimesmiMatrix.set(i, temp + j, tempVar2);
        } else {
          udotTimesmiMatrix.set(i, temp + j, 0.0);
        }
      }
      temp += componentArray[i].getNumberOfAssociationSites();
    }

    if (type > 2) {
      for (int p = 0; p < numberOfComponents; p++) {
        lngi[p] = ((ComponentSrkCPA) componentArray[p]).calc_lngi(this);
      }
    }

    for (int i = 0; i < totalNumberOfAccociationSites; i++) {
      for (int j = i; j < totalNumberOfAccociationSites; j++) {
        delta[i][j] = deltaNog[i][j] * gcpa;
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

    double totalVolume = getTotalVolume();
    double totalVolume2 = totalVolume * totalVolume;
    double totalVolume3 = totalVolume2 * totalVolume;
    double gdv1 = getGcpav() - 1.0 / totalVolume;
    double gdv2 = gdv1 * gdv1;
    double gdv3 = gdv2 * gdv1;
    double Klk = 0.0;
    double tempVar;
    double tempKsiRead = 0.0;
    for (int i = 0; i < totalNumberOfAccociationSites; i++) {
      for (int j = i; j < totalNumberOfAccociationSites; j++) {
        Klk = KlkMatrix.get(i, j);
        tempVar = Klk * gdv1;
        KlkVMatrix.set(i, j, tempVar);
        KlkVMatrix.set(j, i, tempVar);

        tempVar = Klk * gdv2 + Klk * (gcpavv + 1.0 / totalVolume2);
        KlkVVMatrix.set(i, j, tempVar);
        KlkVVMatrix.set(j, i, tempVar);

        tempVar =
            Klk * gdv3 + 3.0 * Klk * (gcpav - 1.0 / totalVolume) * (gcpavv + 1.0 / (totalVolume2))
                + Klk * (gcpavvv - 2.0 / (totalVolume3));
        KlkVVVMatrix.set(i, j, tempVar);
        KlkVVVMatrix.set(j, i, tempVar);

        if (type > 1) {
          tempVar = deltadT[i][j] / delta[i][j];

          if (Math.abs(tempVar) > 1e-50) {
            double tempVardT = deltadTdT[i][j] / delta[i][j]
                - (deltadT[i][j] * deltadT[i][j]) / (delta[i][j] * delta[i][j]);

            tempVar2 = Klk * tempVar;
            KlkTMatrix.set(i, j, tempVar2);
            KlkTMatrix.set(j, i, tempVar2);

            tempVar2 = Klk * tempVar * (gcpav - 1.0 / totalVolume);
            KlkTVMatrix.set(i, j, tempVar2);
            KlkTVMatrix.set(j, i, tempVar2);

            tempVar2 = Klk * (tempVar * tempVar + tempVardT);
            KlkTTMatrix.set(i, j, tempVar2);
            KlkTTMatrix.set(j, i, tempVar2);
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
              Klkni[p][i][j] = Klk * (t1 + t2 + lngi[p]); // ((ComponentSrkCPA)
                                                          // getComponent(p)).calc_lngi(this));
              Klkni[p][j][i] = Klkni[p][i][j];
            }
          }
        }
      }
      tempKsiRead = ksiMatrix.get(i, 0);
      QMatksiksiksi.set(i, 0, 2.0 * mVector.get(i, 0) / (tempKsiRead * tempKsiRead * tempKsiRead));
    }

    SimpleMatrix ksiMatrixTranspose = ksiMatrix.transpose();

    // dXdV
    SimpleMatrix KlkVMatrixksi = KlkVMatrix.mult(ksiMatrix);
    SimpleMatrix XV = hessianInvers.mult(KlkVMatrixksi);
    SimpleMatrix XVtranspose = XV.transpose();

    FCPA = mVector.transpose().mult(uMatrix.minus(ksiMatrix.elementMult(udotMatrix).scale(0.5)))
        .get(0, 0); // QCPA.get(0,
                    // 0); //*0.5;

    dFCPAdV = ksiMatrixTranspose.mult(KlkVMatrixksi).get(0, 0) * (-0.5);
    SimpleMatrix KlkVVMatrixTImesKsi = KlkVVMatrix.mult(ksiMatrix);
    dFCPAdVdV = ksiMatrixTranspose.mult(KlkVVMatrixTImesKsi).scale(-0.5)
        .minus(KlkVMatrixksi.transpose().mult(XV)).get(0, 0);

    SimpleMatrix QVVV = ksiMatrixTranspose.mult(KlkVVVMatrix.mult(ksiMatrix)); // .scale(-0.5);
    SimpleMatrix QVVksi = KlkVVMatrixTImesKsi.scale(-1.0);
    SimpleMatrix QksiVksi = KlkVMatrix.scale(-1.0);

    dFCPAdVdVdV = -0.5 * QVVV.get(0, 0) + QVVksi.transpose().mult(XV).get(0, 0) * 3.0
        + XVtranspose.mult(QksiVksi.mult(XV)).get(0, 0) * 3.0
        + XVtranspose.mult(QMatksiksiksi.mult(XVtranspose)).mult(XV).get(0, 0);

    if (type == 1) {
      return;
    }

    temp = 0;
    for (int p = 0; p < numberOfComponents; p++) {
      for (int kk = 0; kk < getComponent(p).getNumberOfAssociationSites(); kk++) {
        ((ComponentCPAInterface) getComponent(p)).setXsitedV(kk, XV.get(temp + kk, 0));
      }
      temp += getComponent(p).getNumberOfAssociationSites();
    }

    // KlkTMatrix = new SimpleMatrix(KlkdT);
    SimpleMatrix KlkTMatrixTImesKsi = KlkTMatrix.mult(ksiMatrix);
    // dQdT
    SimpleMatrix tempMatrix2 = ksiMatrixTranspose.mult(KlkTMatrixTImesKsi); // .scale(-0.5);
    dFCPAdT = tempMatrix2.get(0, 0) * (-0.5);

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

    // int assSites = 0;
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
      // //
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
  public void setMixingRule(int type) {
    super.setMixingRule(type);
    // NB! Ignores input type
    cpamix = cpaSelect.getMixingRule(1, this);
  }

  /**
   * <p>
   * calcDelta.
   * </p>
   */
  public void calcDelta() {
    for (int i = 0; i < getTotalNumberOfAccociationSites(); i++) {
      for (int j = i; j < getTotalNumberOfAccociationSites(); j++) {
        deltaNog[i][j] = cpamix.calcDeltaNog(assSiteNumber[i], assSiteNumber[j], moleculeNumber[i],
            moleculeNumber[j], this, getTemperature(), getPressure(), numberOfComponents);
        deltaNog[j][i] = deltaNog[i][j];
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addComponent(String name, double moles, double molesInPhase, int compNumber) {
    super.addComponent(name, moles, molesInPhase, compNumber);
    componentArray[compNumber] = new ComponentSrkCPA(name, moles, molesInPhase, compNumber);
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

  // @Override
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
     * for (int j = 0; j < componentArray[i].getNumberOfAssociationSites(); j++) { double xai =
     * ((ComponentSrkCPA) componentArray[i]).getXsite()[j]; tot += (Math.log(xai) - 1.0 / 2.0 * xai
     * + 1.0 / 2.0); } ans += componentArray[i].getNumberOfMolesInPhase() * tot; } return ans;
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
     * for (int j = 0; j < componentArray[i].getNumberOfAssociationSites(); j++) { double xai =
     * ((ComponentSrkCPA) componentArray[i]).getXsite()[j]; double xaidT = ((ComponentSrkCPA)
     * componentArray[i]).getXsitedT()[j]; tot += 1.0 / xai * xaidT - 0.5 * xaidT; // - 1.0 / 2.0 *
     * xai + 1.0 / 2.0); } ans += componentArray[i].getNumberOfMolesInPhase() * tot; }
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
   * Calculate and return dFdNtemp. NB! Does not set field <code>dFdNtemp</code>.
   *
   * @return double[]
   */
  private double[] calcdFdNtemp() {
    double tot1 = 0.0;
    double tot2 = 0.0;
    double tot3 = 0.0;
    double tot4 = 0.0;
    // double temp, temp2;
    for (int k = 0; k < getNumberOfComponents(); k++) {
      tot2 = 0.0;
      tot3 = 0.0;
      // temp = ((ComponentSrkCPA) getComponent(k)).calc_lngi(this);
      // temp2 = ((ComponentSrkCPA) getComponent(k)).calc_lngidV(this);
      for (int i = 0; i < getComponent(k).getNumberOfAssociationSites(); i++) {
        tot2 -= 1.0 * ((ComponentSrkCPA) getComponent(k)).getXsitedV()[i];
        tot3 += (1.0 - ((ComponentSrkCPA) getComponent(k)).getXsite()[i]) * 1.0;
      }
      tot1 += 1.0 / 2.0 * tot2 * getComponent(k).getNumberOfMolesInPhase();
      tot4 += 0.5 * getComponent(k).getNumberOfMolesInPhase() * tot3;
    }
    return new double[] {-tot1, -tot4};
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
      for (int j = 0; j < componentArray[i].getNumberOfAssociationSites(); j++) {
        htot += (1.0 - ((ComponentSrkCPA) componentArray[i]).getXsite()[j]);
      }
      tot += componentArray[i].getNumberOfMolesInPhase() * htot;
    }
    return tot;
  }

  /**
   * <p>
   * calc_g.
   * </p>
   *
   * @return a double
   */
  public double calc_g() {
    tempTotVol = getMolarVolume();
    double temp = 1.0 - getb() / 4.0 / tempTotVol;
    return (2.0 - getb() / 4.0 / tempTotVol) / (2.0 * temp * temp * temp);
  }

  /**
   * <p>
   * calc_lngV.
   * </p>
   *
   * @return a double
   */
  public double calc_lngV() {
    tempTotVol = getTotalVolume();
    // gv = -2.0 * getB() * (10.0 * getTotalVolume() - getB()) / getTotalVolume() /
    // ((8.0 * getTotalVolume() - getB()) * (4.0 * getTotalVolume() - getB()));
    return 1.0 / (2.0 - getB() / (4.0 * tempTotVol)) * getB() / (4.0 * tempTotVol * tempTotVol)
        - 3.0 / (1.0 - getB() / (4.0 * tempTotVol)) * getB() / (4.0 * tempTotVol * tempTotVol);
  }

  /**
   * <p>
   * calc_lngVV.
   * </p>
   *
   * @return a double
   */
  public double calc_lngVV() {
    tempTotVol = getTotalVolume();
    return 2.0
        * (640.0 * Math.pow(tempTotVol, 3.0) - 216.0 * getB() * tempTotVol * tempTotVol
            + 24.0 * Math.pow(getB(), 2.0) * tempTotVol - Math.pow(getB(), 3.0))
        * getB() / (tempTotVol * tempTotVol) / Math.pow(8.0 * tempTotVol - getB(), 2.0)
        / Math.pow(4.0 * tempTotVol - getB(), 2.0);
  }

  /**
   * <p>
   * calc_lngVVV.
   * </p>
   *
   * @return a double
   */
  public double calc_lngVVV() {
    tempTotVol = getTotalVolume();
    return 4.0
        * (Math.pow(getB(), 5.0) + 17664.0 * Math.pow(tempTotVol, 4.0) * getB()
            - 4192.0 * Math.pow(tempTotVol, 3.0) * Math.pow(getB(), 2.0)
            + 528.0 * Math.pow(getB(), 3.0) * tempTotVol * tempTotVol
            - 36.0 * tempTotVol * Math.pow(getB(), 4.0) - 30720.0 * Math.pow(tempTotVol, 5.0))
        * getB() / (Math.pow(tempTotVol, 3.0)) / Math.pow(-8.0 * tempTotVol + getB(), 3.0)
        / Math.pow(-4.0 * tempTotVol + getB(), 3.0);
  }

  /**
   * <p>
   * calcXsitedV.
   * </p>
   */
  public void calcXsitedV() {
    if (getTotalNumberOfAccociationSites() > 0) {
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
    if (totalNumberOfAccociationSites == 0) {
      return true;
    }

    boolean solvedX = solveX2(15);

    DMatrixRMaj mVectorMat = mVector.getMatrix();
    DMatrixRMaj ksiMatrixMat = ksiMatrix.getMatrix();

    // ksiMatrix.print();
    // second order method not working correctly and not used t the moment b ecause of numerical
    // stability
    int temp = 0;
    int iter = 0;
    for (int i = 0; i < numberOfComponents; i++) {
      for (int j = 0; j < componentArray[i].getNumberOfAssociationSites(); j++) {
        mVectorMat.unsafe_set(temp + j, 0, componentArray[i].getNumberOfMolesInPhase());
      }
      temp += componentArray[i].getNumberOfAssociationSites();
    }

    DMatrixRMaj mat1 = KlkMatrix.getMatrix();
    double Klk = 0.0;
    double totvolume = getTotalVolume();
    double tempVari;
    double tempVarj;
    for (int i = 0; i < getTotalNumberOfAccociationSites(); i++) {
      tempVari = mVectorMat.unsafe_get(i, 0);
      for (int j = i; j < getTotalNumberOfAccociationSites(); j++) {
        tempVarj = mVectorMat.unsafe_get(j, 0);
        Klk = tempVari * tempVarj / totvolume * delta[i][j];
        mat1.unsafe_set(i, j, Klk);
        mat1.unsafe_set(j, i, Klk);
      }
    }
    boolean solved = true;
    // SimpleMatrix corrMatrix = null;
    do {
      solved = true;
      iter++;
      temp = 0;
      double ksi = 0;

      double temp1;
      double temp2;
      for (int i = 0; i < numberOfComponents; i++) {
        temp1 = componentArray[i].getNumberOfMolesInPhase();
        for (int j = 0; j < componentArray[i].getNumberOfAssociationSites(); j++) {
          ksi = ((ComponentSrkCPA) componentArray[i]).getXsite()[j];
          ksiMatrixMat.unsafe_set(temp + j, 0, ksi);
          // ksiMatrix.getMatrix().unsafe_set(temp + j, 0,
          // ksiMatrix.getMatrix().unsafe_get(temp + j, 0));
          tempVari = 1.0 / ksi - 1.0;
          udotMatrix.set(temp + j, 0, tempVari);
          udotTimesmMatrix.set(temp + j, 0, temp1 * tempVari);
        }
        temp += componentArray[i].getNumberOfAssociationSites();
      }

      int krondelt;
      for (int i = 0; i < getTotalNumberOfAccociationSites(); i++) {
        temp1 = mVectorMat.unsafe_get(i, 0);
        temp2 = ksiMatrix.get(i, 0);
        for (int j = i; j < getTotalNumberOfAccociationSites(); j++) {
          krondelt = 0;
          if (i == j) {
            krondelt = 1;
          }
          tempVari = -temp1 / (temp2 * temp2) * krondelt - mat1.unsafe_get(i, j);
          hessianMatrix.set(i, j, tempVari);
          hessianMatrix.set(j, i, tempVari);
        }
      }

      // ksiMatrix = new SimpleMatrix(ksi);
      // SimpleMatrix hessianMatrix = new SimpleMatrix(hessian);
      try {
        hessianInvers = hessianMatrix.invert();
      } catch (Exception ex) {
        // logger.error(ex.getMessage(), ex);
        return false;
      }
      if (solvedX) {
        // System.out.println("solvedX ");
        return true;
      }

      DMatrixRMaj mat2 = ksiMatrix.getMatrix();
      CommonOps_DDRM.mult(mat1, mat2, corr2Matrix);
      CommonOps_DDRM.subtract(udotTimesmMatrix.getDDRM(), corr2Matrix, corr3Matrix);
      CommonOps_DDRM.mult(hessianInvers.getDDRM(), corr3Matrix, corr4Matrix);
      // SimpleMatrix gMatrix = udotTimesmMatrix.minus(KlkMatrix.mult(ksiMatrix));
      // corrMatrix =
      // hessianInvers.mult(udotTimesmMatrix.minus(KlkMatrix.mult(ksiMatrix)));
      // //.scale(-1.0);
      temp = 0;
      // System.out.println("print SimpleMatrix ...");
      // corrMatrix.print(10, 10);
      // SimpleMatrix simp = new SimpleMatrix(corr4Matrix);
      // System.out.println("print CommonOps ...");
      // simp.print(10,10);
      double newX;
      for (int i = 0; i < numberOfComponents; i++) {
        for (int j = 0; j < componentArray[i].getNumberOfAssociationSites(); j++) {
          newX = ksiMatrix.get(temp + j, 0) - corr4Matrix.unsafe_get((temp + j), 0);
          if (newX < 0) {
            newX = 1e-10;
            solved = false;
          }
          ((ComponentCPAInterface) componentArray[i]).setXsite(j, newX);
        }
        temp += componentArray[i].getNumberOfAssociationSites();
      }
      // System.out.println("corrmatrix error " );
      // System.out.println("error " + NormOps_DDRM.normF(corr4Matrix));
    } while ((NormOps_DDRM.normF(corr4Matrix) > 1e-12 || !solved) && iter < 100);

    // System.out.println("iter " + iter + " error " +
    // NormOps_DDRM.normF(corr4Matrix)); // corrMatrix.print(10, 10);
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
    double totalVolume = getTotalVolume();
    int iter = 0;
    // if (delta == null) {
    // initCPAMatrix(1);
    double old = 0.0;
    double neeval = 0.0;
    // }
    do {
      iter++;
      err = 0.0;
      for (int i = 0; i < totalNumberOfAccociationSites; i++) {
        old = ((ComponentSrkCPA) componentArray[moleculeNumber[i]]).getXsite()[assSiteNumber[i]];
        neeval = 0.0;
        for (int j = 0; j < totalNumberOfAccociationSites; j++) {
          neeval += componentArray[moleculeNumber[j]].getNumberOfMolesInPhase() * delta[i][j]
              * ((ComponentSrkCPA) componentArray[moleculeNumber[j]]).getXsite()[assSiteNumber[j]];
        }
        neeval = 1.0 / (1.0 + 1.0 / totalVolume * neeval);
        ((ComponentSrkCPA) componentArray[moleculeNumber[i]]).setXsite(assSiteNumber[i], neeval);
        err += Math.abs((old - neeval) / neeval);
      }
    } while (Math.abs(err) > 1e-12 && iter < maxIter);
    // System.out.println("iter " + iter);
    if (Math.abs(err) < 1e-12) {
      return true;
    } else {
      // System.out.println("did not solve for Xi in iterations: " + iter);
      // System.out.println("error: " + err);
      return false;
    }
  }

  /**
   * <p>
   * Getter for the field <code>dFdNtemp</code>. Set value by calling function molarVolume.
   * </p>
   *
   * @return the dFdNtemp
   */
  public double[] getdFdNtemp() {
    return dFdNtemp;
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
    // double Dtemp = 0, dh = 0, gvvv = 0, fvvv = 0, dhh = 0;
    // double d1 = 0, d2 = 0;
    Btemp = getB();
    // Dtemp = getA();
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
    // dataPresentation.filehandling.TextFile file = new
    // dataPresentation.filehandling.TextFile();
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
  public double molarVolume(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    double BonV =
        pt == PhaseType.LIQUID ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
            : pressure * getB() / (numberOfMolesInPhase * temperature * R);

    if (BonV < 0) {
      BonV = 1.0e-8;
    }

    if (BonV >= 1.0) {
      BonV = 0.9999;
    }
    double BonVold;
    double BonV2;
    double h = 0;
    double dh = 0;
    double dhh = 0;
    double d1 = 0;
    double d2 = 0;
    double Btemp = getB();
    if (Btemp < 0) {
      logger.info("b negative in volume calc");
    }

    setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
    int iterations = 0;
    int maxIterations = 300;
    do {
      iterations++;
      gcpa = calc_g();
      if (gcpa < 0) {
        setMolarVolume(1.0 / Btemp / numberOfMolesInPhase);
        gcpa = calc_g();
      }

      // lngcpa =
      // Math.log(gcpa);
      gcpav = calc_lngV();
      gcpavv = calc_lngVV();
      gcpavvv = calc_lngVVV();

      if (totalNumberOfAccociationSites > 0) {
        solveX();
      }

      initCPAMatrix(1);

      BonV2 = BonV * BonV;
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
        return molarVolumeChangePhase(pressure, temperature, A, B, pt);
        // BonV += d2;
        // double hnew = h + d2 * dh;
        // if (Math.abs(hnew) > Math.abs(h)) {
        // BonV = pt == 1 ? 2.0 / (2.0 + temperature /
        // getPseudoCriticalTemperature()) : pressure * getB() / (numberOfMolesInPhase *
        // temperature * R);
        // }
      } else {
        BonV += 0.5 * d1;
      }
      if (Math.abs((BonV - BonVold) / BonV) > 0.1) {
        BonV = BonVold + 0.1 * (BonV - BonVold);
      }
      if (BonV < 0) {
        if (iterations < 10) {
          // System.out.println(iterations + " BonV " + BonV);
          BonV = (BonVold + BonV) / 2.0;
        } else {
          return molarVolumeChangePhase(pressure, temperature, A, B, pt);
        }
      }

      if (BonV >= 1.0) {
        if (iterations < 10) {
          BonV = (BonVold + BonV) / 2.0;
        } else {
          return molarVolumeChangePhase(pressure, temperature, A, B, pt);
        }
      }
      /*
       * if (BonV > 0.9999) { if (iterations < 10) { BonV = (BonVold + BonV) / 2.0; } else { // BonV
       * = calcRootVolFinder(pt); // BonV = molarVolumeChangePhase(pressure, temperature, A, B, pt);
       * // BonV = 0.9999; // BonV = pt == 1 ? 2.0 / (2.0 + temperature /
       * getPseudoCriticalTemperature()) : pressure * getB() / (numberOfMolesInPhase * temperature *
       * R); } } else if (BonV < 0) { if (iterations < 10) { BonV = Math.abs(BonVold + BonV) / 2.0;
       * } else { // BonV = calcRootVolFinder(pt); // return molarVolumeChangePhase(pressure,
       * temperature, A, B, pt); // BonV = pt == 1 ? 2.0 / (2.0 + temperature /
       * getPseudoCriticalTemperature()) : pressure * getB() / (numberOfMolesInPhase * temperature *
       * R); } }
       */
      setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
      Z = pressure * getMolarVolume() / (R * temperature);
    } while ((Math.abs((BonV - BonVold) / BonV) > 1.0e-10 || Math.abs(h) > 1e-12)
        && iterations < maxIterations);

    // System.out.println("h failed " + h + " Z" + Z + " iterations " + iterations +
    // " BonV " + BonV);
    // if (Math.abs(h) > 1e-12) {
    // System.out.println("h failed " + h + " Z" + Z + " iterations " + iterations +
    // " BonV " + BonV);
    // return molarVolumeChangePhase(pressure, temperature, A, B, pt);
    // return molarVolumeChangePhase(pressure, temperature, A, B, pt);
    // }
    // System.out.println("Z" + Z + " iterations " + iterations + " BonV " + BonV);
    // System.out.println("pressure " + Z*R*temperature/getMolarVolume());
    // System.out.println("volume " + getTotalVolume() + " molar volume " +
    // getMolarVolume());
    // if(iterations>=100) throw new util.exception.TooManyIterationsException();
    // System.out.println("error in volume " +
    // (-pressure+R*temperature/getMolarVolume()-R*temperature*dFdV())); // + "
    // firstterm " + (R*temperature/molarVolume) + " second " +
    // R*temperature*dFdV());
    // System.out.println("BonV: " + BonV + " "+" itert: " + iterations +" " +h + "
    // " +dh + " B " + Btemp + " gv" + gV() + " fv " + fv() + " fvv" + fVV());
    if (Double.isNaN(getMolarVolume())) {
      throw new neqsim.util.exception.IsNaNException(this, "molarVolume", "Molar volume");
      // System.out.println("BonV: " + BonV + " "+" itert: " + iterations +" " +h + "
      // " +dh + " B " + Btemp + " D " + Dtemp + " gv" + gV() + " fv " + fv() + " fvv"
      // + fVV());
    }
    dFdNtemp = calcdFdNtemp();
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
    double BonV = pt == PhaseType.GAS ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
        : pressure * getB() / (numberOfMolesInPhase * temperature * R);
    // double BonV = calcRootVolFinder(pt);
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
    // double fvvv = 0, gvvv = 0;
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
  public double molarVolume2(double pressure, double temperature, double A, double B, PhaseType pt)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    Z = pt == PhaseType.LIQUID ? 1.0 : 1.0e-5;
    setMolarVolume(Z * R * temperature / pressure);
    // super.molarVolume(pressure,temperature, A, B, phase);
    int iterations = 0;
    double err = 0.0;
    double dErrdV = 0.0;
    double deltaV = 0;

    do {
      A = calcA(this, temperature, pressure, numberOfComponents);
      B = calcB(this, temperature, pressure, numberOfComponents);

      double dFdV = dFdV();
      double dFdVdV = dFdVdV();
      // double dFdVdVdV = dFdVdVdV();
      // double factor1 = 1.0e0, factor2 = 1.0e0;
      err = -R * temperature * dFdV + R * temperature / getMolarVolume() - pressure;

      // System.out.println("pressure " + -R * temperature * dFdV + " " + R *
      // temperature / getMolarVolume());
      // -pressure;
      dErrdV = -R * temperature * dFdVdV
          - R * temperature * numberOfMolesInPhase / Math.pow(getVolume(), 2.0);

      // System.out.println("errdV " + dErrdV);
      // System.out.println("err " + err);
      deltaV = -err / dErrdV;

      setMolarVolume(getMolarVolume() + deltaV / numberOfMolesInPhase);

      Z = pressure * getMolarVolume() / (R * temperature);
      if (Z < 0) {
        Z = 1e-6;
        setMolarVolume(Z * R * temperature / pressure);
      }

      // System.out.println("Z " + Z);
    } while (Math.abs(err) > 1.0e-8 || iterations < 100);
    // System.out.println("Z " + Z);
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
  public CPAMixingRulesInterface getCpaMixingRule() {
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
   * initOld2.
   * </p>
   *
   * @param totalNumberOfMoles a double
   * @param numberOfComponents a int
   * @param type a int
   * @param pt the PhaseType of the phase
   * @param beta a double
   */
  public void initOld2(double totalNumberOfMoles, int numberOfComponents, int type, PhaseType pt,
      double beta) {
    // type = 0 start init, type = 1 gi nye betingelser
    if (type == 0) {
      setTotalNumberOfAccociationSites(0);
      selfAccociationScheme = new int[numberOfComponents][0][0];
      crossAccociationScheme = new int[numberOfComponents][numberOfComponents][0][0];
      for (int i = 0; i < numberOfComponents; i++) {
        if (componentArray[i].getNumberOfmoles() < 1e-50) {
          componentArray[i].setNumberOfAssociationSites(0);
        } else {
          componentArray[i]
              .setNumberOfAssociationSites(componentArray[i].getOrginalNumberOfAssociationSites());
          setTotalNumberOfAccociationSites(
              getTotalNumberOfAccociationSites() + componentArray[i].getNumberOfAssociationSites());
          selfAccociationScheme[i] = cpaSelect.setAssociationScheme(i, this);
          for (int j = 0; j < numberOfComponents; j++) {
            crossAccociationScheme[i][j] = cpaSelect.setCrossAssociationScheme(i, j, this);
          }
        }
      }

      // had to remove if below - dont understand why.. Even
      // if (getTotalNumberOfAccociationSites() != oldTotalNumberOfAccociationSites) {
      mVector = new SimpleMatrix(getTotalNumberOfAccociationSites(), 1);
      KlkMatrix =
          new SimpleMatrix(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
      KlkVMatrix =
          new SimpleMatrix(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
      KlkVVMatrix =
          new SimpleMatrix(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
      KlkVVVMatrix =
          new SimpleMatrix(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
      hessianMatrix =
          new SimpleMatrix(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
      KlkTMatrix =
          new SimpleMatrix(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
      KlkTTMatrix =
          new SimpleMatrix(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
      KlkTVMatrix =
          new SimpleMatrix(getTotalNumberOfAccociationSites(), getTotalNumberOfAccociationSites());
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
      delta = new double[getTotalNumberOfAccociationSites()][getTotalNumberOfAccociationSites()];
      deltaNog = new double[getTotalNumberOfAccociationSites()][getTotalNumberOfAccociationSites()];
      deltadT = new double[getTotalNumberOfAccociationSites()][getTotalNumberOfAccociationSites()];
      deltadTdT =
          new double[getTotalNumberOfAccociationSites()][getTotalNumberOfAccociationSites()];
      QMatksiksiksi = new SimpleMatrix(getTotalNumberOfAccociationSites(), 1);
      // }
      udotTimesmiMatrix =
          new SimpleMatrix(getNumberOfComponents(), getTotalNumberOfAccociationSites());

      oldTotalNumberOfAccociationSites = getTotalNumberOfAccociationSites();

      int temp = 0;
      for (int i = 0; i < numberOfComponents; i++) {
        for (int j = 0; j < componentArray[i].getNumberOfAssociationSites(); j++) {
          moleculeNumber[temp + j] = i;
          assSiteNumber[temp + j] = j;
        }
        temp += componentArray[i].getNumberOfAssociationSites();
      }
    }
    if (cpamix == null) {
      // NB! Hardcoded mixing rule type
      cpamix = cpaSelect.getMixingRule(1, this);
    }

    super.init(totalNumberOfMoles, numberOfComponents, type, pt, beta);
    if (type > 0 && isConstantPhaseVolume()) {
      calcDelta();
      solveX();
      super.init(totalNumberOfMoles, numberOfComponents, 1, pt, beta);
      gcpa = calc_g();
      // lngcpa = Math.log(gcpa);
      setGcpav(calc_lngV());
      gcpavv = calc_lngVV();
      gcpavvv = calc_lngVVV();
    }

    if (type > 0) {
      hcpatot = calc_hCPA();
    }

    if (type > 1) {
      initCPAMatrix(type);
      // hcpatotdT = calc_hCPAdT();
      // super.init(totalNumberOfMoles, numberOfComponents, type, phase, beta);
    }
  }

  /**
   * <p>
   * initCPAMatrixOld.
   * </p>
   *
   * @param type a int
   */
  public void initCPAMatrixOld(int type) {
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
      for (int j = 0; j < componentArray[i].getNumberOfAssociationSites(); j++) {
        uMatrix.set(temp + j, 0,
            Math.log(ksiMatrix.get(temp + j, 0)) - ksiMatrix.get(temp + j, 0) + 1.0);
        gvector[temp + j][0] = mVector.get(temp + j, 0) * udotMatrix.get(temp + j, 0);
      }
      temp += componentArray[i].getNumberOfAssociationSites();
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

    double totalVolume = getTotalVolume();
    double totalVolume2 = totalVolume * totalVolume;
    double totalVolume3 = totalVolume2 * totalVolume;
    double gdv1 = getGcpav() - 1.0 / totalVolume;
    double gdv2 = gdv1 * gdv1;
    double gdv3 = gdv2 * gdv1;
    // double Klk = 0.0;
    for (int i = 0; i < getTotalNumberOfAccociationSites(); i++) {
      for (int j = i; j < getTotalNumberOfAccociationSites(); j++) {
        KlkVMatrix.set(i, j, KlkMatrix.get(i, j) * gdv1);
        KlkVMatrix.set(j, i, KlkVMatrix.get(i, j));

        KlkVVMatrix.set(i, j, KlkMatrix.get(i, j) * gdv2
            + KlkMatrix.get(i, j) * (gcpavv + 1.0 / totalVolume / totalVolume));
        KlkVVMatrix.set(j, i, KlkVVMatrix.get(i, j));

        KlkVVVMatrix.set(i, j,
            KlkMatrix.get(i, j) * gdv3
                + 3.0 * KlkMatrix.get(i, j) * (getGcpav() - 1.0 / totalVolume)
                    * (gcpavv + 1.0 / (totalVolume2))
                + KlkMatrix.get(i, j) * (gcpavvv - 2.0 / (totalVolume3)));
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

            KlkTVMatrix.set(i, j, KlkMatrix.get(i, j) * tempVar * (gcpav - 1.0 / totalVolume));
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
                  * (t1 + t2 + ((ComponentSrkCPA) getComponent(p)).calc_lngi(this));
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

    // int assSites = 0;
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
      // //
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

  /**
   * <p>
   * solveXOld.
   * </p>
   *
   * @return a boolean
   */
  public boolean solveXOld() {
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
      for (int j = 0; j < componentArray[i].getNumberOfAssociationSites(); j++) {
        mVector.set(temp + j, 0, componentArray[i].getNumberOfMolesInPhase());
      }
      temp += componentArray[i].getNumberOfAssociationSites();
    }
    double Klk = 0.0;
    double totalVolume = getTotalVolume();
    for (int i = 0; i < getTotalNumberOfAccociationSites(); i++) {
      for (int j = i; j < getTotalNumberOfAccociationSites(); j++) {
        Klk = mVector.get(i, 0) * mVector.get(j, 0) / totalVolume * delta[i][j];
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
        for (int j = 0; j < componentArray[i].getNumberOfAssociationSites(); j++) {
          ksiMatrix.set(temp + j, 0, ((ComponentSrkCPA) componentArray[i]).getXsite()[j]);
          // ksiMatrix.getMatrix().unsafe_set(temp + j, 0,
          // ksiMatrix.getMatrix().unsafe_get(temp + j, 0));
          udotMatrix.set(temp + j, 0, 1.0 / ksiMatrix.get(temp + j, 0) - 1.0);
          udotTimesmMatrix.set(temp + j, 0, mVector.get(temp + j, 0) * udotMatrix.get(temp + j, 0));
        }
        temp += componentArray[i].getNumberOfAssociationSites();
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
      // hessianInvers.mult(udotTimesmMatrix.minus(KlkMatrix.mult(ksiMatrix)));
      // //.scale(-1.0);
      temp = 0;
      // System.out.println("print SimpleMatrix ...");
      // corrMatrix.print(10, 10);
      // SimpleMatrix simp = new SimpleMatrix(corr4Matrix);
      // System.out.println("print CommonOps ...");
      // simp.print(10,10);
      for (int i = 0; i < numberOfComponents; i++) {
        for (int j = 0; j < componentArray[i].getNumberOfAssociationSites(); j++) {
          double newX = ksiMatrix.get(temp + j, 0) - corr4Matrix.unsafe_get((temp + j), 0);
          if (newX < 0) {
            newX = 1e-10;
            solved = false;
          }
          ((ComponentCPAInterface) componentArray[i]).setXsite(j, newX);
        }
        temp += componentArray[i].getNumberOfAssociationSites();
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
   * solveX2Old.
   * </p>
   *
   * @param maxIter a int
   * @return a boolean
   */
  public boolean solveX2Old(int maxIter) {
    double err = .0;
    int iter = 0;
    // if (delta == null) {
    // initCPAMatrix(1);
    double old = 0.0;
    double neeval = 0.0;
    double totalVolume = getTotalVolume();
    // }
    do {
      iter++;
      err = 0.0;
      for (int i = 0; i < getTotalNumberOfAccociationSites(); i++) {
        old = ((ComponentSrkCPA) getComponent(moleculeNumber[i])).getXsite()[assSiteNumber[i]];
        neeval = 0;
        for (int j = 0; j < getTotalNumberOfAccociationSites(); j++) {
          neeval += getComponent(moleculeNumber[j]).getNumberOfMolesInPhase() * delta[i][j]
              * ((ComponentSrkCPA) getComponent(moleculeNumber[j])).getXsite()[assSiteNumber[j]];
        }
        neeval = 1.0 / (1.0 + 1.0 / totalVolume * neeval);
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

  /**
   * <p>
   * molarVolumeOld.
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
  public double molarVolumeOld(double pressure, double temperature, double A, double B,
      PhaseType pt) throws neqsim.util.exception.IsNaNException,
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
    if (BonV < 0) {
      BonV = 1.0e-8;
    }

    if (BonV >= 1.0) {
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
          // return molarVolumeChangePhase(pressure, temperature, A, B, pt);
          // BonV = 0.9999;
          // BonV = pt == 1 ? 2.0 / (2.0 + temperature /
          // getPseudoCriticalTemperature()) : pressure * getB() / (numberOfMolesInPhase *
          // temperature * R);
        }
      } else if (BonV < 0) {
        if (iterations < 3) {
          BonV = Math.abs(BonVold + BonV) / 2.0;
        } else {
          // return molarVolumeChangePhase(pressure, temperature, A, B, pt);
          // BonV = pt == 1 ? 2.0 / (2.0 + temperature /
          // getPseudoCriticalTemperature()) : pressure * getB() / (numberOfMolesInPhase *
          // temperature * R);
        }
      }
      setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
      Z = pressure * getMolarVolume() / (R * temperature);
    } while ((Math.abs((BonV - BonVold) / BonV) > 1.0e-10 || Math.abs(h) > 1e-12)
        && iterations < 100);

    if (Math.abs(h) > 1e-12) {
      // System.out.println("h failed " + "Z" + Z + " iterations " + iterations + "
      // BonV " + BonV);
      // return molarVolumeChangePhase(pressure, temperature, A, B, pt);
    }
    // System.out.println("Z" + Z + " iterations " + iterations + " BonV " + BonV);
    // System.out.println("pressure " + Z*R*temperature/getMolarVolume());
    // System.out.println("volume " + totalVolume + " molar volume " +
    // getMolarVolume());
    // if(iterations>=100) throw new util.exception.TooManyIterationsException();
    // System.out.println("error in volume " +
    // (-pressure+R*temperature/getMolarVolume()-R*temperature*dFdV())); // + "
    // firstterm " + (R*temperature/molarVolume) + " second " +
    // R*temperature*dFdV());
    // System.out.println("BonV: " + BonV + " "+" itert: " + iterations +" " +h + " " +dh + " B
    // " + Btemp + " gv" + gV() + " fv " + fv() + " fvv" + fVV());

    if (Double.isNaN(getMolarVolume())) {
      throw new neqsim.util.exception.IsNaNException(this, "molarVolumeOld", "Molar volume");
      // System.out.println("BonV: " + BonV + " "+" itert: " + iterations +" " +h + "
      // " +dh + " B " + Btemp + " D " + Dtemp + " gv" + gV() + " fv " + fv() + " fvv"
      // + fVV());
    }
    return getMolarVolume();
  }
}
