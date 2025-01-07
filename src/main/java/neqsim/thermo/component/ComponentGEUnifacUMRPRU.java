package neqsim.thermo.component;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.atomelement.UNIFACgroup;
import neqsim.thermo.phase.PhaseGEUnifac;
import neqsim.thermo.phase.PhaseGEUnifacUMRPRU;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;

/**
 * <p>
 * ComponentGEUnifacUMRPRU class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentGEUnifacUMRPRU extends ComponentGEUnifac {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ComponentGEUnifacUMRPRU.class);

  int numberOfUnifacSubGroups = 139;
  double[][] aij = null;
  double[][] aijdT = null;
  double[][] aijdTdT = null;
  double[][] tempExpaij = null;
  double oldTemperature = -10.0;
  double old2Temperature = -10;
  double[] sum2Comp = null;
  double[] sum2Mix = null;
  double[] sum2CompdT = null;
  double[] sum2CompdTdT = null;
  double[] sum2MixdT = null;
  double[] sum2MixdTdT = null;

  /**
   * <p>
   * Constructor for ComponentGEUnifacUMRPRU.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentGEUnifacUMRPRU(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
    // System.out.println("finished reading UNIFAC ");
    if (name.contains("_PC")) {
      double number = getMolarMass() / 0.014;
      int intNumb = (int) Math.round(number) - 2;
      unifacGroups.clear();
      unifacGroups.add(new UNIFACgroup(1, 2));
      unifacGroups.add(new UNIFACgroup(2, intNumb));
      unifacGroupsArray = unifacGroups.toArray(unifacGroupsArray);
      // System.out.println("adding unifac pseudo.." + intNumb);
      for (int i = 0; i < getNumberOfUNIFACgroups(); i++) {
        getUnifacGroup(i).calcXComp(this);
      }
      for (int i = 0; i < getNumberOfUNIFACgroups(); i++) {
        getUnifacGroup(i).calcQComp(this);
      }
      return;
    }
    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase()) {
      java.sql.ResultSet dataSet = null;
      try {
        dataSet =
            database.getResultSet(("SELECT * FROM unifaccompumrpru WHERE Name='" + name + "'"));
        dataSet.next();
        // dataSet.getClob("name");
      } catch (Exception ex) {
        dataSet.close();
        dataSet =
            database.getResultSet(("SELECT * FROM unifaccompumrpru WHERE Name='" + name + "'"));
        dataSet.next();
        logger.error("Something went wrong. Closing database.", ex);
      }
      unifacGroups.clear();
      for (int p = 1; p < numberOfUnifacSubGroups; p++) {
        int temp = Integer.parseInt(dataSet.getString("sub" + Integer.toString(p)));
        if (temp > 0) {
          unifacGroups.add(new UNIFACgroup(p, temp));
          // System.out.println("compUMR " + name + " adding UNIFAC group " +
          // p);
        }
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    unifacGroupsArray = unifacGroups.toArray(unifacGroupsArray);
    for (int i = 0; i < getNumberOfUNIFACgroups(); i++) {
      getUnifacGroup(i).calcXComp(this);
    }
    for (int i = 0; i < getNumberOfUNIFACgroups(); i++) {
      getUnifacGroup(i).calcQComp(this);
    }
  }

  /**
   * <p>
   * calcTempExpaij.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void calcTempExpaij(PhaseInterface phase) {
    if (tempExpaij == null) {
      tempExpaij = new double[getNumberOfUNIFACgroups()][getNumberOfUNIFACgroups()];

      sum2Comp = new double[getNumberOfUNIFACgroups()];
      sum2Mix = new double[getNumberOfUNIFACgroups()];

      sum2CompdT = new double[getNumberOfUNIFACgroups()];
      sum2MixdT = new double[getNumberOfUNIFACgroups()];

      sum2CompdTdT = new double[getNumberOfUNIFACgroups()];
      sum2MixdTdT = new double[getNumberOfUNIFACgroups()];
    }
    for (int i = 0; i < getNumberOfUNIFACgroups(); i++) {
      for (int j = 0; j < getNumberOfUNIFACgroups(); j++) {
        tempExpaij[i][j] = Math.exp(-1.0 / phase.getTemperature()
            * getaij(getUnifacGroup(i).getGroupIndex(), getUnifacGroup(j).getGroupIndex()));
      }
    }
  }

  /**
   * <p>
   * calcSum2Comp.
   * </p>
   */
  public void calcSum2Comp() {
    UNIFACgroup[] unifacGroupsLocal = getUnifacGroups();
    for (int i = 0; i < unifacGroupsLocal.length; i++) {
      sum2Comp[i] = 0;
      sum2Mix[i] = 0;
      for (int j = 0; j < unifacGroupsLocal.length; j++) {
        sum2Comp[i] += unifacGroupsLocal[j].getQComp() * tempExpaij[j][i];
        sum2Mix[i] += unifacGroupsLocal[j].getQMix() * tempExpaij[j][i];
      }
    }
  }

  /**
   * <p>
   * calcSum2CompdTdT.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void calcSum2CompdTdT(PhaseInterface phase) {
    double der;
    double tempVar;
    double tempVar2;
    double tempSqared = phase.getTemperature() * phase.getTemperature();
    double temp3pow = tempSqared * phase.getTemperature();
    UNIFACgroup[] unifacGroupsLocal = getUnifacGroups();
    for (int i = 0; i < unifacGroupsLocal.length; i++) {
      sum2CompdT[i] = 0;
      sum2CompdTdT[i] = 0;
      sum2MixdT[i] = 0;
      sum2MixdTdT[i] = 0;
      for (int j = 0; j < unifacGroupsLocal.length; j++) {
        tempVar2 =
            getaij(unifacGroupsLocal[j].getGroupIndex(), unifacGroupsLocal[i].getGroupIndex());
        tempVar =
            getaijdT(unifacGroupsLocal[j].getGroupIndex(), unifacGroupsLocal[i].getGroupIndex());

        double Xsum2CompdT = unifacGroupsLocal[j].getQComp()
            * (tempVar2 / tempSqared - tempVar / phase.getTemperature()) * tempExpaij[j][i];
        sum2CompdT[i] += Xsum2CompdT;
        der =
            tempVar / tempSqared - 2.0 * tempVar2 / temp3pow
                - getaijdTdT(unifacGroupsLocal[j].getGroupIndex(),
                    unifacGroupsLocal[i].getGroupIndex()) / phase.getTemperature()
                + tempVar / tempSqared;
        sum2CompdTdT[i] += (tempVar2 / tempSqared - tempVar / phase.getTemperature()) * Xsum2CompdT
            + der * unifacGroupsLocal[j].getQComp() * tempExpaij[j][i];

        double Xsum2MixdT = unifacGroupsLocal[j].getQMix()
            * (tempVar2 / tempSqared - tempVar / phase.getTemperature()) * tempExpaij[j][i];
        sum2MixdT[i] += Xsum2MixdT;
        sum2MixdTdT[i] += (tempVar2 / tempSqared - tempVar / phase.getTemperature()) * Xsum2MixdT
            + der * unifacGroupsLocal[j].getQMix() * tempExpaij[j][i];
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void calclnGammak(int k, PhaseInterface phase) {
    double sum1Comp = 0.0;
    double sum1Mix = 0.0;
    double sum3Comp = 0.0;
    double sum3Mix = 0.0;
    UNIFACgroup[] unifacGroupsLocal = getUnifacGroups();
    for (int i = 0; i < unifacGroupsLocal.length; i++) {
      sum1Comp += unifacGroupsLocal[i].getQComp() * tempExpaij[i][k];
      sum1Mix += unifacGroupsLocal[i].getQMix() * tempExpaij[i][k];
      sum3Comp += unifacGroupsLocal[i].getQComp() * tempExpaij[k][i] / sum2Comp[i];
      sum3Mix += unifacGroupsLocal[i].getQMix() * tempExpaij[k][i] / sum2Mix[i];
    }
    double tempGammaComp = unifacGroupsLocal[k].getQ() * (1.0 - Math.log(sum1Comp) - sum3Comp);
    double tempGammaMix = unifacGroupsLocal[k].getQ() * (1.0 - Math.log(sum1Mix) - sum3Mix);
    getUnifacGroup(k).setLnGammaComp(tempGammaComp);
    getUnifacGroup(k).setLnGammaMix(tempGammaMix);
  }

  /**
   * <p>
   * calclnGammakdn.
   * </p>
   *
   * @param k a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param derCompNumb a int
   */
  public void calclnGammakdn(int k, PhaseInterface phase, int derCompNumb) {
    double sum1Mix = 0.0;
    double sum1Mixdn = 0.0;
    double sum3Mix = 0.0;
    UNIFACgroup[] unifacGroupsLocal = getUnifacGroups();
    for (int i = 0; i < unifacGroupsLocal.length; i++) {
      sum1Mix += unifacGroupsLocal[i].getQMix() * tempExpaij[i][k];
      sum1Mixdn += unifacGroupsLocal[i].QMixdN[derCompNumb] * tempExpaij[i][k];
      double sum2Mixdn = 0.0;
      for (int j = 0; j < unifacGroupsLocal.length; j++) {
        // sum2Mix += getUnifacGroup(j).getQMix() * tempExpaij[j][i];
        sum2Mixdn += unifacGroupsLocal[j].QMixdN[derCompNumb] * tempExpaij[j][i];
      }
      sum3Mix += (unifacGroupsLocal[i].QMixdN[derCompNumb] * tempExpaij[k][i] * sum2Mix[i]
          - unifacGroupsLocal[i].getQMix() * tempExpaij[k][i] * sum2Mixdn)
          / (sum2Mix[i] * sum2Mix[i]);
    }
    double tempGammaMix = unifacGroupsLocal[k].getQ() * (-sum1Mixdn / sum1Mix - sum3Mix);
    // getUnifacGroup(k).setLnGammaComp(tempGammaComp);
    unifacGroupsLocal[k].setLnGammaMixdn(tempGammaMix, derCompNumb);
  }

  /**
   * <p>
   * calclnGammakdTdT.
   * </p>
   *
   * @param k a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void calclnGammakdTdT(int k, PhaseInterface phase) {
    double sum1Comp = 0.0;
    double sum1CompdT = 0.0;
    double sum1CompdTdT = 0.0;
    double sum1Mix = 0.0;
    double sum1MixdT = 0.0;
    double sum1MixdTdT = 0.0;
    double sum2Comp2;
    double sum2Mix2;
    double sum3Comp = 0;
    double sum3CompdT = 0.0;
    double sum3Mix = 0;
    double sum3MixdT = 0.0;
    double tempSqared = phase.getTemperature() * phase.getTemperature();
    double temp3pow = tempSqared * phase.getTemperature();
    double Xsum1CompdT;
    double Xsum1MixdT;
    double vComp;
    double der;
    double vdTComp;
    double vdTdTComp;
    double vMix;
    double vdTMix;
    double vdTdTMix;
    double tempVar1;
    double tempVar2;
    double tempVar1dT;
    double tempVar2dT;
    UNIFACgroup[] unifacGroupsLocal = getUnifacGroups();
    for (int i = 0; i < unifacGroupsLocal.length; i++) {
      tempVar1 = getaij(unifacGroupsLocal[i].getGroupIndex(), unifacGroupsLocal[k].getGroupIndex());
      tempVar2 = getaij(unifacGroupsLocal[k].getGroupIndex(), unifacGroupsLocal[i].getGroupIndex());
      tempVar1dT =
          getaijdT(unifacGroupsLocal[i].getGroupIndex(), unifacGroupsLocal[k].getGroupIndex());
      tempVar2dT =
          getaijdT(unifacGroupsLocal[k].getGroupIndex(), unifacGroupsLocal[i].getGroupIndex());
      sum1Comp += unifacGroupsLocal[i].getQComp() * tempExpaij[i][k];
      Xsum1CompdT = unifacGroupsLocal[i].getQComp()
          * (tempVar1 / tempSqared - tempVar1dT / phase.getTemperature()) * tempExpaij[i][k];
      sum1CompdT += Xsum1CompdT;
      der =
          tempVar1dT / tempSqared - 2.0 * tempVar1 / temp3pow
              - getaijdTdT(unifacGroupsLocal[i].getGroupIndex(),
                  unifacGroupsLocal[k].getGroupIndex()) / phase.getTemperature()
              + tempVar1dT / tempSqared;
      sum1CompdTdT += (tempVar1 / tempSqared - tempVar1dT / phase.getTemperature()) * Xsum1CompdT
          + der * unifacGroupsLocal[i].getQComp() * tempExpaij[i][k];

      sum1Mix += unifacGroupsLocal[i].getQMix() * tempExpaij[i][k];
      Xsum1MixdT = unifacGroupsLocal[i].getQMix()
          * (tempVar1 / tempSqared - tempVar1dT / phase.getTemperature()) * tempExpaij[i][k];
      sum1MixdT += Xsum1MixdT;
      sum1MixdTdT += (tempVar1 / tempSqared - tempVar1dT / phase.getTemperature()) * Xsum1MixdT
          + der * unifacGroupsLocal[i].getQMix() * tempExpaij[i][k];

      vComp = unifacGroupsLocal[i].getQComp() * tempExpaij[k][i];
      vdTComp = unifacGroupsLocal[i].getQComp()
          * (tempVar2 / tempSqared - tempVar2dT / phase.getTemperature()) * tempExpaij[k][i];
      der = tempVar2dT / tempSqared - 2.0 * tempVar2 / temp3pow
          - getaijdTdT(unifacGroupsLocal[k].getGroupIndex(), getUnifacGroup(i).getGroupIndex())
              / phase.getTemperature()
          + tempVar2dT / tempSqared;
      vdTdTComp = (tempVar2 / tempSqared - tempVar2dT / phase.getTemperature()) * vdTComp
          + der * getUnifacGroup(i).getQComp() * tempExpaij[k][i];

      vMix = unifacGroupsLocal[i].getQMix() * tempExpaij[k][i];
      vdTMix = unifacGroupsLocal[i].getQMix()
          * (tempVar2 / tempSqared - tempVar2dT / phase.getTemperature()) * tempExpaij[k][i];
      vdTdTMix = (tempVar2 / tempSqared - tempVar2dT / phase.getTemperature()) * vdTMix
          + der * unifacGroupsLocal[i].getQMix() * tempExpaij[k][i];

      sum3Comp += (vdTComp / sum2Comp[i] - vComp / Math.pow(sum2Comp[i], 2.0) * sum2CompdT[i]);
      sum3Mix += (vdTMix / sum2Mix[i] - vMix / Math.pow(sum2Mix[i], 2.0) * sum2MixdT[i]);
      sum2Comp2 = sum2Comp[i] * sum2Comp[i];
      sum3CompdT += (vdTdTComp / sum2Comp[i] - vdTComp / sum2Comp2 * sum2CompdT[i]
          - vdTComp / sum2Comp2 * sum2CompdT[i])
          + 2.0 * vComp / (sum2Comp2 * sum2Comp[i]) * sum2CompdT[i] * sum2CompdT[i]
          - vComp / sum2Comp2 * sum2CompdTdT[i];

      sum2Mix2 = sum2Mix[i] * sum2Mix[i];
      sum3MixdT += vdTdTMix / sum2Mix[i] - vdTMix / sum2Mix2 * sum2MixdT[i]
          - vdTMix / sum2Mix2 * sum2MixdT[i]
          + 2.0 * vMix / (sum2Mix2 * sum2Mix[i]) * sum2MixdT[i] * sum2MixdT[i]
          - vMix / sum2Mix2 * sum2MixdTdT[i];
    }

    double tempGammaComp = unifacGroupsLocal[k].getQ() * (-1.0 / sum1Comp * sum1CompdT - sum3Comp);
    double tempGammaMix = unifacGroupsLocal[k].getQ() * (-1.0 / sum1Mix * sum1MixdT - sum3Mix);

    unifacGroupsLocal[k].setLnGammaCompdT(tempGammaComp);
    unifacGroupsLocal[k].setLnGammaMixdT(tempGammaMix);

    double tempGammaCompdT = unifacGroupsLocal[k].getQ() * (-1.0 / sum1Comp * sum1CompdTdT
        + 1.0 / (sum1Comp * sum1Comp) * sum1CompdT * sum1CompdT - sum3CompdT);

    double tempGammaMixdT = unifacGroupsLocal[k].getQ() * (-1.0 / sum1Mix * sum1MixdTdT
        + 1.0 / (sum1Mix * sum1Mix) * sum1MixdT * sum1MixdT - sum3MixdT);
    unifacGroupsLocal[k].setLnGammaCompdTdT(tempGammaCompdT);
    unifacGroupsLocal[k].setLnGammaMixdTdT(tempGammaMixdT);
  }

  // TODO impement dlngammadn

  /** {@inheritDoc} */
  @Override
  public double getGamma(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure, PhaseType pt) {
    int initType = phase.getInitType();
    double lngammaCombinational;
    double lngammaResidual;
    double lngammaResidualdT;
    double lngammaResidualdTdT;
    gamma = 1.0;
    lngamma = 0.0;
    dlngammadt = 0.0;
    dlngammadtdt = 0.0;
    ComponentGEUnifac[] compArray = (ComponentGEUnifac[]) phase.getcomponentArray();

    if (this.getx() < 1e-100) {
      return gamma;
    }

    double V = this.getx() * this.getR() / ((PhaseGEUnifacUMRPRU) phase).getVCommontemp();
    double F = this.getx() * this.getQ() / ((PhaseGEUnifacUMRPRU) phase).getFCommontemp();

    lngammaCombinational = -10.0 / 2.0 * getQ() * (Math.log(V / F) + 1.0 - V / F);

    for (int i = 0; i < getNumberOfUNIFACgroups(); i++) {
      // getUnifacGroup(i).calcXComp(this);.
      // getUnifacGroup(i).calcQComp(this);
      getUnifacGroup(i)
          .setQMix(((PhaseGEUnifacUMRPRU) phase).getQmix(getUnifacGroup(i).getGroupName()));
      // getUnifacGroup(i).calcQMix((PhaseGEUnifac) phase);
    }

    if (Math.abs(temperature - oldTemperature) > 1e-10) {
      calcUnifacGroupParams(phase);
      calcTempExpaij(phase);
    }

    lngammaResidual = 0.0;

    calcSum2Comp();
    for (int i = 0; i < getNumberOfUNIFACgroups(); i++) {
      calclnGammak(i, phase);
      lngammaResidual += getUnifacGroup(i).getN()
          * (getUnifacGroup(i).getLnGammaMix() - getUnifacGroup(i).getLnGammaComp());
    }

    lngamma = lngammaResidual + lngammaCombinational;

    if (Double.isNaN(lngamma)) {
      logger.warn("gamma NaN......");
      lngamma = 0.0;
      gamma = 1.0;
      dlngammadt = 0;
      dlngammadtdt = 0;
      return gamma;
    }

    gamma = Math.exp(lngamma);
    if (gamma < 1e-10) {
      gamma = 1.0; // this code has been added to
    }
    if (initType > 1) {
      if (Math.abs(temperature - old2Temperature) > 1e-10) {
        calcUnifacGroupParamsdT(phase);
      }

      calcSum2CompdTdT(phase);
      for (int i = 0; i < getNumberOfUNIFACgroups(); i++) {
        calclnGammakdTdT(i, phase);
      }
      lngammaResidualdT = 0.0;
      lngammaResidualdTdT = 0.0;
      for (int i = 0; i < getNumberOfUNIFACgroups(); i++) {
        lngammaResidualdT += getUnifacGroup(i).getN()
            * (getUnifacGroup(i).getLnGammaMixdT() - getUnifacGroup(i).getLnGammaCompdT());
        lngammaResidualdTdT += getUnifacGroup(i).getN()
            * (getUnifacGroup(i).getLnGammaMixdTdT() - getUnifacGroup(i).getLnGammaCompdTdT());
      }

      dlngammadt = lngammaResidualdT;
      dlngammadtdt = lngammaResidualdTdT;

      if (Double.isNaN(dlngammadt)) {
        dlngammadt = 0.0;
      }
      if (Double.isNaN(dlngammadtdt)) {
        dlngammadtdt = 0.0;
      }
      old2Temperature = phase.getTemperature();
    }

    if (initType > 2) {
      dlngammadn = new double[numberOfComponents];

      for (int ii = 0; ii < getNumberOfUNIFACgroups(); ii++) {
        // getUnifacGroup(ii).calcQMixdN((PhaseGEUnifac) phase);
        getUnifacGroup(ii)
            .setQMixdN(((PhaseGEUnifacUMRPRU) phase).getQmixdN(getUnifacGroup(ii).getGroupName()));
      }
      for (int i = 0; i < phase.getNumberOfComponents(); i++) {
        double lngammaResidualdn = 0.0;
        double lngammaCombinationaldn =
            -10.0 / 2.0 * getQ() / compArray[i].getNumberOfMolesInPhase() * (V / F - 1.0)
                * (V / (getx() * this.getR()) * compArray[i].getx() * compArray[i].getR()
                    - F / (getx() * this.getQ()) * compArray[i].getx() * compArray[i].getQ());
        for (int ii = 0; ii < getNumberOfUNIFACgroups(); ii++) {
          calclnGammakdn(ii, phase, i);
          lngammaResidualdn += getUnifacGroup(ii).getN() * getUnifacGroup(ii).getLnGammaMixdn(i);
        }

        double dlnGammadn = lngammaCombinationaldn + lngammaResidualdn;
        if (Double.isNaN(dlnGammadn)) {
          dlnGammadn = 0.0;
        }
        setlnGammadn(i, dlnGammadn);
      }
    }

    oldTemperature = temperature;
    return gamma;
  }

  /**
   * <p>
   * calcUnifacGroupParams.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void calcUnifacGroupParams(PhaseInterface phase) {
    if (aij == null) {
      aij = new double[getNumberOfUNIFACgroups()][getNumberOfUNIFACgroups()];
    }
    for (int i = 0; i < getNumberOfUNIFACgroups(); i++) {
      for (int j = 0; j < getNumberOfUNIFACgroups(); j++) {
        aij[i][j] = calcaij(phase, i, j);
      }
    }
  }

  /**
   * <p>
   * calcUnifacGroupParamsdT.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void calcUnifacGroupParamsdT(PhaseInterface phase) {
    if (aijdT == null) {
      aijdT = new double[getNumberOfUNIFACgroups()][getNumberOfUNIFACgroups()];
      aijdTdT = new double[getNumberOfUNIFACgroups()][getNumberOfUNIFACgroups()];
    }
    for (int i = 0; i < getNumberOfUNIFACgroups(); i++) {
      for (int j = 0; j < getNumberOfUNIFACgroups(); j++) {
        aijdT[i][j] = calcaijdT(phase, i, j);
        aijdTdT[i][j] = calcaijdTdT(phase, i, j);
      }
    }
  }

  /**
   * <p>
   * calcGammaNumericalDerivatives.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @param pt the PhaseType of the phase
   */
  public void calcGammaNumericalDerivatives(PhaseInterface phase, int numberOfComponents,
      double temperature, double pressure, PhaseType pt) {
    phase.setInitType(1);
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      double dn = getNumberOfMolesInPhase() / 1e6;
      phase.addMoles(getComponentNumber(), dn);
      x = getNumberOfmoles() / getNumberOfMolesInPhase();
      getGamma(phase, numberOfComponents, temperature, pressure, pt);
      double oldGamma = lngamma;
      phase.addMoles(getComponentNumber(), dn);

      x = getNumberOfmoles() / getNumberOfMolesInPhase();

      getGamma(phase, numberOfComponents, temperature, pressure, pt);

      double dlnGammadn = (oldGamma - lngamma) / dn;
      // System.out.println("dlnGammadn " + dlnGammadn);
      setlnGammadn(i, dlnGammadn);
    }
  }

  /**
   * <p>
   * Getter for the field <code>aij</code>.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getaij(int i, int j) {
    return aij[i][j];
  }

  /**
   * <p>
   * Getter for the field <code>aijdT</code>.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getaijdT(int i, int j) {
    return aijdT[i][j];
  }

  /**
   * <p>
   * Getter for the field <code>aijdTdT</code>.
   * </p>
   *
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double getaijdTdT(int i, int j) {
    return aijdTdT[i][j];
  }

  /**
   * <p>
   * calcaij.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double calcaij(PhaseInterface phase, int i, int j) {
    double temp = phase.getTemperature() - 298.15;
    return ((PhaseGEUnifac) phase).getAij(i, j) + ((PhaseGEUnifac) phase).getBij(i, j) * temp
        + ((PhaseGEUnifac) phase).getCij(i, j) * temp * temp;
  }

  /**
   * <p>
   * calcaijdT.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double calcaijdT(PhaseInterface phase, int i, int j) {
    return ((PhaseGEUnifac) phase).getBij(i, j)
        + 2.0 * ((PhaseGEUnifac) phase).getCij(i, j) * (phase.getTemperature() - 298.15);
  }

  /**
   * <p>
   * calcaijdTdT.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param i a int
   * @param j a int
   * @return a double
   */
  public double calcaijdTdT(PhaseInterface phase, int i, int j) {
    return 2.0 * ((PhaseGEUnifac) phase).getCij(i, j);
  }
}
