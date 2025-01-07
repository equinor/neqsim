package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseGE;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;

/**
 * <p>
 * ComponentGeDuanSun class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentGeDuanSun extends ComponentGE {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double r = 0;

  double q = 0;

  /**
   * <p>
   * Constructor for ComponentGeDuanSun.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentGeDuanSun(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
  }

  /**
   * <p>
   * getGamma.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @param pt the PhaseType of the phase
   * @param HValpha an array of type double
   * @param HVgij an array of type double
   * @return a double
   */
  public double getGamma(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure, PhaseType pt, double[][] HValpha, double[][] HVgij) {
    if (componentName.equals("CO2")) {
      return 0.9;
    } else if (componentName.equals("water")) {
      return 1.0;
    } else {
      return 1.0;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getGamma(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure, PhaseType pt, double[][] HValpha, double[][] HVgij, double[][] intparam,
      String[][] mixRule) {
    return getGamma(phase, numberOfComponents, temperature, pressure, pt, HValpha, HVgij);
  }

  /**
   * <p>
   * getGammaNRTL.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @param pt the PhaseType of the phase
   * @param HValpha an array of type double
   * @param HVgij an array of type double
   * @return a double
   */
  public double getGammaNRTL(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure, PhaseType pt, double[][] HValpha, double[][] HVgij) {
    // double ny = 0, Djj = 0, Dii = 0, gij = 0, gjj = 0, gji = 0, gii = 0, F2T = 0, tot2 = 0;
    double A = 0;
    double B = 0;
    double C = 0;
    double D = 0;
    double E = 0;
    double F = 0;
    double tau = 0;
    double tau2 = 0;
    double G = 0;
    double G2 = 0;
    double alpha = 0;
    double Dij = 0;
    double Dji = 0;
    // int i, k, delta = 0;
    int j;

    int l = 0;
    double dAdT = 0;
    double dBdT = 0;
    double dCdT = 0;
    double dDdT = 0;
    // double dEdT, dFdT = 0;
    double dtaudt = 0;
    double dtau2dt = 0;
    double dGdt = 0;
    double dG2dt = 0;
    double[][] Gmatrix = new double[numberOfComponents][numberOfComponents];
    double[][] tauMatrix = new double[numberOfComponents][numberOfComponents];
    dlngammadn = new double[numberOfComponents];
    ComponentInterface[] comp_Array = phase.getcomponentArray();
    // double lngammaold = 0, dlngammadtold = 0;
    double dA2dTetter = 0;
    double dA3dTetter = 0;
    double dA4dTetter = 0;
    double dA5dTetter = 0;
    double dA6dTetter = 0;
    // for(int w=0;w<3;w++){
    F = 0;
    dBdT = 0;
    dAdT = 0;
    dDdT = 0;
    A = 0;
    B = 0;
    dlngammadt = 0.0;
    dA2dTetter = 0;
    dA3dTetter = 0;
    dA4dTetter = 0;
    dA5dTetter = 0;
    dA6dTetter = 0;
    double dA2dT = 0;

    // PhaseGEEosInterface phaseny = (PhaseGEEosInterface) phase.getPhase();
    // PhaseGEInterface GEPhase = phaseny.getGEphase();

    // ComponentGeNRTLInterface[] compArray = (ComponentGeNRTLInterface[])
    // GEPhase.getcomponentArray();
    // PhaseGEInterface GEphase = new PhaseGEInterface();
    // PhaseGEInterface phaseny = (PhaseGEInterface) phase.getPhase();

    double dA3dT = 0;
    double dA4dT = 0;
    double dA5dT = 0;
    double dA6dT = 0;
    for (j = 0; j < numberOfComponents; j++) {
      Dij = HVgij[this.getComponentNumber()][j];
      Dji = HVgij[j][this.getComponentNumber()];
      // gji = HVgij[j][this.getComponentNumber()];
      // gjj = HVgii[j][j];
      alpha = HValpha[j][this.getComponentNumber()];
      tau = Dji / (temperature);
      tau2 = Dij / (temperature);
      dtaudt = -tau / temperature;
      dtau2dt = -tau2 / temperature;
      // System.out.println("method GE1" + tau);

      // System.out.println("error in NRTL here ......");
      G = Math.exp(-alpha * tau); // comp_Array[j].getb()*Math.exp(-alpha*tau);
      dGdt = dtaudt * -alpha * G;
      G2 = Math.exp(-alpha * tau2); // comp_Array[this.getComponentNumber()].getb()*Math.exp(-alpha*tau2);
      dG2dt = dtau2dt * -alpha * G2;

      A += tau * G * comp_Array[j].getx();
      dAdT = dAdT + comp_Array[j].getx() * dGdt * tau + comp_Array[j].getx() * G * dtaudt;
      dA2dT = comp_Array[j].getx() * dG2dt * tau2 + comp_Array[j].getx() * G2 * dtau2dt;
      dA3dT = tau2 * G2 * comp_Array[j].getx();
      dA4dT = 2 * comp_Array[j].getx() * G2;
      dA5dT = comp_Array[j].getx() * dG2dt;
      dA6dT = comp_Array[j].getx() * G2;
      B += G * comp_Array[j].getx();
      dBdT += dGdt * comp_Array[j].getx();
      E = G2 * comp_Array[j].getx();
      // dEdT = dG2dt * comp_Array[j].getx();

      C = 0;
      D = 0;
      dCdT = 0;
      dDdT = 0;
      // System.out.println("hei");

      for (l = 0; l < numberOfComponents; l++) {
        Dij = HVgij[l][j];
        alpha = HValpha[l][j];
        tau = Dij / (temperature);
        dtaudt = -tau / temperature;

        // System.out.println("error in NRTL comp here....");
        G = Math.exp(-alpha * tau); // comp_Array[l].getb()*Math.exp(-alpha*tau);
        dGdt = dtaudt * -alpha * G;
        Gmatrix[l][j] = G;
        tauMatrix[l][j] = tau;

        C += G * comp_Array[l].getx();
        dCdT += dGdt * comp_Array[l].getx();
        D += G * tau * comp_Array[l].getx();
        dDdT += comp_Array[l].getx() * dGdt * tau + comp_Array[l].getx() * G * dtaudt;
      }
      dA2dTetter += dA2dT / C;
      dA3dTetter += dA3dT * dCdT / (C * C);

      dA4dTetter += dA4dT * dCdT * D / (C * C * C);
      dA5dTetter += dA5dT * D / (C * C);
      dA6dTetter += dA6dT * dDdT / (C * C);

      tau2 = HVgij[this.getComponentNumber()][j] / (temperature);
      dtau2dt = -tau2 / temperature;

      F += E / C * (tau2 - D / C);
      // dFdT += (dEdT / C - E / (C * C) * dCdT) * (tau2 - D / C) + E / C * (dtau2dt - (dDdT /
      // C - D / (C * C) * dCdT));
      // F2T = F2T - 2*2*A/Math.pow(C,2) + 2*2*E*D/Math.pow(C,3); // A til A2;
    }

    lngamma = A / B + F;
    // dlngammadt = dAdT/B - A/(B*B)*dBdT + dFdT;
    dlngammadt = (dAdT / B - A / (B * B) * dBdT + dA2dTetter - dA3dTetter + dA4dTetter - dA5dTetter
        - dA6dTetter);
    /*
     * if(w==0){ dlngammadtold = dlngammadt; temperature +=0.0001; }
     *
     * if(w==1){ lngammaold = lngamma; temperature -=0.0002; } }
     */
    // System.out.println("deriv: " + lngammaold + " " + lngamma + " " +
    // ((lngammaold-lngamma)/0.0002) + " " + dlngammadtold);
    // System.out.println("deriv t : " +dlngammadt);

    // tot2 = -2*A/B/B + F2T;
    // dlngammadt = (lngammaold-lngamma)/0.002;

    // phaseny.getExcessGibbsEnergy(numberOfComponents, temperature, pressure,
    // pt)
    gamma = Math.exp(lngamma);
    // System.out.println("gamma " +gamma);
    // if derivates....
    if (phase.getInitType() == 3) {
      double dAdn = 0;
      double dBdn = 0;
      double Etemp = 0;
      double dEdn = 0;
      double Ctemp = 0;
      double Dtemp = 0;
      double Ftemp = 0;
      double Gtemp = 0;

      for (int p = 0; p < numberOfComponents; p++) {
        dAdn = tauMatrix[p][this.getComponentNumber()] * Gmatrix[p][this.getComponentNumber()];
        dBdn = Gmatrix[p][this.getComponentNumber()];
        dEdn = Gmatrix[this.getComponentNumber()][p] * tauMatrix[this.getComponentNumber()][p];
        // dFdn = Gmatrix[this.getComponentNumber()][p];
        Dtemp = 0;
        Ctemp = 0;
        Etemp = 0;
        Ftemp = 0;
        Gtemp = 0;
        double nt = 0;
        for (int f = 0; f < numberOfComponents; f++) {
          nt += comp_Array[f].getNumberOfMolesInPhase();
          Ctemp += comp_Array[f].getx() * Gmatrix[f][p];
          Etemp += comp_Array[f].getx() * Gmatrix[f][p] * tauMatrix[f][p];
          double sum = 0.0;
          double sum2 = 0.0;
          for (int g = 0; g < numberOfComponents; g++) {
            sum += comp_Array[g].getx() * Gmatrix[g][f];
            sum2 += comp_Array[g].getx() * Gmatrix[g][f] * tauMatrix[g][f];
          }
          Dtemp += Gmatrix[p][f] * Gmatrix[this.getComponentNumber()][f]
              * tauMatrix[this.getComponentNumber()][f] * comp_Array[f].getx() / (sum * sum);
          Ftemp += comp_Array[f].getx() * Gmatrix[p][f] * sum2
              * Gmatrix[this.getComponentNumber()][f] / (sum * sum * sum);
          Gtemp += comp_Array[f].getx() * Gmatrix[p][f] * tauMatrix[p][f]
              * Gmatrix[this.getComponentNumber()][f] / (sum * sum);
        }
        dlngammadn[p] = (dAdn / B - A / (B * B) * dBdn) + dEdn / Ctemp - Dtemp
            - Etemp * Gmatrix[this.getComponentNumber()][p] / (Ctemp * Ctemp) + 2.0 * Ftemp - Gtemp;
        // E/(C*C)*dCdn[p]*(tau2-D/C) + E/C*(-dDdn[p]/C + D/(C*C)*dCdn[p]);
        dlngammadn[p] /= (nt);
      }
      // System.out.println("Dlngamdn: " + dlngammadn[p] + " x: " +
      // comp_Array[p].getx()+ " length: ");
    }

    return gamma;
  }

  /*
   * public double getHenryCoef(double temperature) { // System.out.println("henry " + //
   * Math.exp(henryCoefParameter[0]+henryCoefParameter[1]/temperature+
   * henryCoefParameter[2]*Math.log(temperature)+henryCoefParameter[3]*temperature )*100*0.01802);
   * if (componentName.equals("CO2")) { // return } return super.getHenryCoef(temperature); }
   */

  /** {@inheritDoc} */
  @Override
  public double fugcoef(PhaseInterface phase) {
    logger.info("fug coef "
        + gamma * getAntoineVaporPressure(phase.getTemperature()) / phase.getPressure());
    if (referenceStateType.equals("solvent")) {
      fugacityCoefficient =
          gamma * getAntoineVaporPressure(phase.getTemperature()) / phase.getPressure();
      gammaRefCor = gamma;
    } else {
      double activinf = 1.0;
      if (phase.hasComponent("water")) {
        int waternumb = phase.getComponent("water").getComponentNumber();
        activinf =
            gamma / ((PhaseGE) phase).getActivityCoefficientInfDilWater(componentNumber, waternumb);
      } else {
        activinf = gamma / ((PhaseGE) phase).getActivityCoefficientInfDil(componentNumber);
      }
      // activinf = gamma / ((PhaseGE) phase).getActivityCoefficientInfDil(componentNumber);

      // Born function
      double BORN = 0.0;
      double EPS = 0.0;
      double EPS1000 = 0.0;
      double CB = 0.0;
      double BB = 0.0;
      EPS1000 = 3.4279 * Math.pow(10.0, 2.0)
          * Math.exp((-5.0866 * Math.pow(10.0, -3.0) * phase.getTemperature()
              + 9.469 * Math.pow(10.0, -7.0) * Math.pow(phase.getTemperature(), 2.0)));
      CB = -2.0525
          + 3.1159 * Math.pow(10.0, 3.0) / (phase.getTemperature() - 1.8289 * Math.pow(10.0, 2.0));
      BB = -8.0325 * Math.pow(10.0, -3.0) + 4.21452 * Math.pow(10.0, 6.0) / phase.getTemperature()
          + 2.1417 * phase.getTemperature();
      EPS = EPS1000 + CB * Math.log((BB + phase.getPressure()) / BB + 1000.0);
      BORN = (1.0 / EPS) * (CB / ((phase.getPressure() + BB)
          * (CB * Math.log((phase.getPressure() + BB) / (BB + 1000.0)) + EPS)));

      // Average partial molar volume
      double[] Vm = {0.0, 0.0, 0.0};
      Vm[0] =
          41.84
              * (0.1 * 7.29 + (100 * 0.92) / (2600 + phase.getPressure())
                  + 2.07 / (phase.getTemperature() - 288.0)
                  - 1.23 * Math.pow(10.0, 4.0)
                      / ((2600 + phase.getPressure()) * (phase.getTemperature() - 288.0))
                  + 1.6 * BORN);
      Vm[1] = 41.84 * (0.1 * 7.0);
      Vm[2] =
          41.84 * (0.1 * 5.7889 + (100 * 6.3536) / (2600 + phase.getPressure())
              + 3.2528 / (phase.getTemperature() - 288.0)
              - 3.0417 * Math.pow(10.0, 4.0)
                  / ((2600 + phase.getPressure()) * (phase.getTemperature() - 288.0))
              + 0.3943 * BORN);

      double[] Poynteff = {0.0, 0.0, 0.0};
      Poynteff[0] =
          Vm[0] * (phase.getPressure() - 1.0) / (1000.0 * (R / 100.0) * phase.getTemperature());
      Poynteff[1] =
          Vm[1] * (phase.getPressure() - 1.0) / (1000.0 * (R / 100.0) * phase.getTemperature());
      Poynteff[2] =
          Vm[2] * (phase.getPressure() - 1.0) / (1000.0 * (R / 100.0) * phase.getTemperature());

      double[] K = {0.0, 0.0, 0.0, 0.0};
      double a1 = 0.0;
      double a2 = 0.0;
      double a3 = 0.0;
      double a4 = 0.0;
      double a5 = 0.0;
      double a6 = 0.0;
      double a7 = 0.0;
      double ACO20 = -10.52624;
      double ACO21 = 2.3547 * Math.pow(10.0, -2.0);
      double ACO22 = 3972.8;
      double ACO23 = 0.0;
      double ACO24 = -5.8746 * Math.pow(10.0, 5.0);
      double ACO25 = -1.9194 * Math.pow(10.0, -5.0);
      double AN20 = 58.453;
      double AN21 = -1.818 * Math.pow(10.0, -3.0);
      double AN22 = -3199.0;
      double AN23 = -17.909;
      double AN24 = 27460.0;
      double AN25 = 0.0;
      double AO20 = 7.5001;
      double AO21 = -7.8981 * Math.pow(10.0, -3.0);
      double AO22 = 0.0;
      double AO23 = 0.0;
      double AO24 = -2.0027 * Math.pow(10.0, 5.0);
      double AO25 = 0.0;

      if (phase.getTemperature() <= 373.15) {
        a1 = 9.31063597;
        a2 = -1.892867005 * Math.pow(10.0, -1.0);
        a3 = 1.307135652 * Math.pow(10.0, -3.0);
        a4 = -3.800223763 * Math.pow(10.0, -6.0);
        a5 = 4.0091369717 * Math.pow(10.0, -9.0);
        a6 = 2.2769246863 * Math.pow(10.0, 1.0);
        a7 = -1.1291330188 * Math.pow(10.0, -2.0);
      } else {
        a1 = -9.0283127 * Math.pow(10.0, -1.0);
        a2 = 3.6492938 * Math.pow(10.0, -2.0);
        a3 = 4.3610019 * Math.pow(10.0, -4.0);
        a4 = -3.10936036 * Math.pow(10.0, -6.0);
        a5 = 4.592053 * Math.pow(10.0, -9.0);
        a6 = 1.62996873 * Math.pow(10.0, 1.0);
        a7 = 2.81119409 * Math.pow(10.0, -2.0);
      }

      K[0] = Math.pow(10.0,
          (ACO20 + ACO21 * phase.getTemperature() + ACO22 / phase.getTemperature()
              + ACO23 * Math.log10(phase.getTemperature())
              + ACO24 / (Math.pow(phase.getTemperature(), 2.0))
              + ACO25 * Math.pow(phase.getTemperature(), 2.0)))
          * Math.exp(Poynteff[0]);
      K[1] = Math.pow(10.0,
          (AN20 + AN21 * phase.getTemperature() + AN22 / phase.getTemperature()
              + AN23 * Math.log10(phase.getTemperature())
              + AN24 / (Math.pow(phase.getTemperature(), 2.0))
              + AN25 * Math.pow(phase.getTemperature(), 2.0)))
          * Math.exp(Poynteff[1]);
      K[2] = Math.pow(10.0,
          (AO20 + AO21 * phase.getTemperature() + AO22 / phase.getTemperature()
              + AO23 * Math.log10(phase.getTemperature())
              + AO24 / (Math.pow(phase.getTemperature(), 2.0))
              + AO25 * Math.pow(phase.getTemperature(), 2.0)))
          * Math.exp(Poynteff[2]);
      K[3] = (a1 + a2 * phase.getTemperature() + a3 * Math.pow(phase.getTemperature(), 2.0)
          + a4 * Math.pow(phase.getTemperature(), 3.0) + a5 * Math.pow(phase.getTemperature(), 4.0))
          * Math.exp((phase.getPressure() - 1.0) * (a6 + a7 * phase.getTemperature())
              / (1000.0 * (R / 100.0) * phase.getTemperature()));

      if (componentName.equals("CO2")) {
        fugacityCoefficient = activinf * K[0] * gamma * (1000 / 18.02) / phase.getPressure();
        // +25.689/(gamma*K[0]))/ phase.getPressure();
      } else if (componentName.equals("nitrogen")) {
        fugacityCoefficient = activinf * K[1] * gamma * (1000 / 18.02) / phase.getPressure();
        // +50.585/(gamma*K[1]))/ phase.getPressure();
      } else if (componentName.equals("oxygen")) {
        fugacityCoefficient = activinf * K[2] * gamma * (1000 / 18.02) / phase.getPressure();
        // +46.9157/(gamma*K[2]))/ phase.getPressure();
      } else if (componentName.contentEquals("water")) {
        fugacityCoefficient = activinf * K[3] * (1000 / 18.02) / phase.getPressure();
      } else {
        fugacityCoefficient = activinf * K[3] / phase.getPressure();
      }
      // fugacityCoefficient = activinf * getHenryCoef(phase.getTemperature()) /
      // phase.getPressure(); //gamma* benyttes ikke
      gammaRefCor = activinf;
    }

    return fugacityCoefficient;
  }

  /////////////////////////////////////////////////////
  /**
   * <p>
   * getGammaPitzer.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComponents a int
   * @param temperature a double
   * @param pressure a double
   * @param pt the PhaseType of the phase
   * @param salinity a double
   * @return a double
   */
  public double getGammaPitzer(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure, PhaseType pt, double salinity) {
    double P = pressure;
    double T = temperature;
    double S = salinity;
    // double salinity2=0;

    // if(isIsIon()) {
    // salinity2 = getNumberOfMolesInPhase() /
    // (phase.getComponent("water").getNumberOfMolesInPhase()*phase.getComponent("water").getMolarMass());
    // }

    // double S=salinity;
    double lamdaCO2Na = (-0.411370585 + 0.000607632 * T + 97.5347708 / T - 0.023762247 * P / T
        + 0.017065624 * P / (630.0 - T) + 1.41335834 * Math.pow(10.0, -5.0) * T * Math.log(P));
    double lamdaN2Na = -2.4434074 + 0.0036351795 * T + 447.47364 / T - 0.000013711527 * P
        + 0.0000071037217 * Math.pow(P, 2.0) / T;
    double lamdaO2Na = 0.19997;
    double zetaN2NaCl = -0.58071053 * Math.pow(10.0, -2.0);
    double zetaO2NaCl = -1.2793 * Math.pow(10.0, -2.0);
    double zetaCO2NaCl = 0.00033639 - 1.9829898 * Math.pow(10.0, -5.0) * T + 0.002122208 * P / T
        - 0.005248733 * P / (630. - T);

    if (componentName.equals("CO2")) {
      gamma = Math.exp(2.0 * S * lamdaCO2Na + Math.pow(S, 2.0) * zetaCO2NaCl);
    } else if (componentName.equals("nitrogen")) {
      gamma = Math.exp(2.0 * S * lamdaN2Na + Math.pow(S, 2.0) * zetaN2NaCl);
    } else if (componentName.equals("oxygen")) {
      gamma = Math.exp(2.0 * S * lamdaO2Na + Math.pow(S, 2.0) * zetaO2NaCl);
    } else {
      gamma = 1.0;
    }

    // double gammaCO2=Math.exp(2.0*S*lamdaCO2Na+Math.pow(S,2.0)*zetaCO2NaCl);
    // double gammaN2=Math.exp(2.0*S*lamdaN2Na+Math.pow(S,2.0)*zetaN2NaCl);
    // double gammaO2=Math.exp(2.0*S*lamdaO2Na+Math.pow(S,2.0)*zetaO2NaCl);
    // gamma=1.0;

    lngamma = Math.log(gamma);

    // System.out.println("gamma CO2 = " + gammaCO2);
    // System.out.println("gamma N2 = " + gammaN2);
    // System.out.println("gamma O2 = " + gammaO2);

    // if (componentName.equals("CO2")) {
    // return gammaCO2;
    // }else if (componentName.equals("nitrogen")) {
    // return gammaN2;
    // }else if (componentName.equals("oxygen")) {
    // return gammaO2;
    // }else
    return gamma;
  }
  /////////////////////////////////////////////////////
  /*
   * public double fugcoefDiffPres(PhaseInterface phase, int numberOfComponents, double temperature,
   * double pressure, PhaseType pt){ dfugdp = (Math.log(fugcoef(phase, numberOfComponents,
   * temperature, pressure+0.01, pt))-Math.log(fugcoef(phase, numberOfComponents, temperature,
   * pressure-0.01, pt)))/0.02; return dfugdp; }
   *
   * public double fugcoefDiffTemp(PhaseInterface phase, int numberOfComponents, double temperature,
   * double pressure, PhaseType pt){ dfugdt = (Math.log(fugcoef(phase, numberOfComponents,
   * temperature+0.01, pressure, pt))-Math.log(fugcoef(phase, numberOfComponents, temperature-0.01,
   * pressure, pt)))/0.02; return dfugdt; }
   */

  /**
   * <p>
   * Getter for the field <code>r</code>.
   * </p>
   *
   * @return a double
   */
  public double getr() {
    return r;
  }

  /**
   * <p>
   * Getter for the field <code>q</code>.
   * </p>
   *
   * @return a double
   */
  public double getq() {
    return q;
  }

  /**
   * Getter for property lngamma.
   *
   * @return Value of property lngamma.
   */
  public double getLngamma() {
    return lngamma;
  }
}
