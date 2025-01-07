/*
 * ComponentGENRTLmodifiedHV.java
 *
 * Created on 18. juli 2000, 18:36
 */

package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;

/**
 * <p>
 * ComponentGENRTLmodifiedHV class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentGENRTLmodifiedHV extends ComponentGeNRTL {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for ComponentGENRTLmodifiedHV.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentGENRTLmodifiedHV(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
  }

  /** {@inheritDoc} */
  @Override
  public double getGamma(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure, PhaseType pt, double[][] HValpha, double[][] HVgij, double[][] intparam,
      String[][] mixRule) {
    double[][] HVgijT = new double[numberOfComponents][numberOfComponents];
    return getGamma(phase, numberOfComponents, temperature, pressure, pt, HValpha, HVgij, HVgijT,
        intparam, mixRule);
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
   * @param HVgijT an array of type double
   * @param intparam an array of type double
   * @param mixRule an array of {@link java.lang.String} objects
   * @return a double
   */
  public double getGamma(PhaseInterface phase, int numberOfComponents, double temperature,
      double pressure, PhaseType pt, double[][] HValpha, double[][] HVgij, double[][] HVgijT,
      double[][] intparam, String[][] mixRule) {
    int type = phase.getInitType();
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
    double DijT = 0;
    double DjiT = 0;
    double gij = 0;
    double gjj = 0;
    double gji = 0;
    double gii = 0;
    int j;
    int l;
    double dAdT = 0;
    double dBdT = 0;
    double dCdT = 0;
    double dCdTdT = 0;
    double dDdT = 0;
    double dDdTdT = 0;
    double dBdTdT = 0;
    // double dEdT;
    double dAdTdT = 0;
    double dtaudt = 0;
    double dtaudtdt = 0.0;
    double dtau2dt = 0;
    double dtau2dtdt = 0.0;
    double dGdt = 0;
    double dG2dt = 0;
    double dGdtdt = 0;
    double dG2dtdt = 0.0;
    double[][] Gmatrix = new double[numberOfComponents][numberOfComponents];
    double[][] tauMatrix = new double[numberOfComponents][numberOfComponents];
    dlngammadn = new double[numberOfComponents];
    ComponentEosInterface[] comp_Array = (ComponentEosInterface[]) phase.getcomponentArray();
    double dA2dTetter = 0;
    double dA2dTdTetter = 0;
    double dA3dTetter = 0;
    double dA3dTdTetter = 0.0;
    double dA4dTetter = 0;
    double dA4dTdTetter = 0;
    double dA5dTetter = 0;
    double dA5dTdTetter = 0;
    double dA6dTetter = 0;
    double dA6dTdTetter = 0;
    // for(int w=0;w<3;w++){
    F = 0;
    // double dFdT = 0;
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
    double dA2dTdT = 0.0;
    double dA3dT = 0;
    double dA3dTdT = 0;
    double dA4dT = 0;
    double dA4dTdT = 0;
    double dA5dT = 0;
    double dA5dTdT = 0;
    double dA6dT = 0;
    double dA6dTdT = 0.0;
    double deltaEOS =
        1.0 / (comp_Array[0].getDeltaEosParameters()[1] - comp_Array[0].getDeltaEosParameters()[0])
            * Math.log((1.0 + comp_Array[0].getDeltaEosParameters()[1])
                / (1.0 + comp_Array[0].getDeltaEosParameters()[0]));
    // PhaseGEEosInterface phaseny = (PhaseGEEosInterface) phase.getPhase();
    // PhaseGEInterface GEPhase = phaseny.getGEphase();

    // ComponentGeNRTLInterface[] compArray = (ComponentGeNRTLInterface[])
    // GEPhase.getcomponentArray();
    // PhaseGEInterface GEphase = new PhaseGEInterface();
    // PhaseGEInterface phaseny = (PhaseGEInterface) phase.getPhase();

    for (j = 0; j < numberOfComponents; j++) {
      if (mixRule[j][componentNumber].trim().equals("HV")) {
        Dij = HVgij[componentNumber][j];
        // System.out.println("comp " + this.getComponentName() + " comp2 " +
        // comp_Array[j].getComponentName() + " dij " + Dij);
        Dji = HVgij[j][componentNumber];
        DijT = HVgijT[componentNumber][j];
        DjiT = HVgijT[j][componentNumber];
        // gji = HVgij[j][componentNumber];
        // gjj = HVgii[j][j];
        alpha = HValpha[j][componentNumber]; // new HV + T*(gji-gii)
        tau = Dji / (temperature) + DjiT;
        dtaudt = -Dji / (temperature * temperature);
        dtaudtdt = 2.0 * Dji / (temperature * temperature * temperature);

        tau2 = Dij / (temperature) + DijT;
        dtau2dt = -Dij / (temperature * temperature);
        dtau2dtdt = 2.0 * Dij / (temperature * temperature * temperature);
        // System.out.println("method GE1" + tau);
      } else {
        gii = -deltaEOS * comp_Array[componentNumber].aT(temperature)
            / comp_Array[componentNumber].getb();
        double dgiidt = -deltaEOS * comp_Array[componentNumber].diffaT(temperature)
            / comp_Array[componentNumber].getb();
        double dgiidtdt = -deltaEOS * comp_Array[componentNumber].diffdiffaT(temperature)
            / comp_Array[componentNumber].getb();
        gjj = -deltaEOS * comp_Array[j].aT(temperature) / comp_Array[j].getb();
        double dgjjdt = -deltaEOS * comp_Array[j].diffaT(temperature) / comp_Array[j].getb();
        double dgjjdtdt = -deltaEOS * comp_Array[j].diffdiffaT(temperature) / comp_Array[j].getb();

        gij = -2.0 * Math.sqrt(comp_Array[componentNumber].getb() * comp_Array[j].getb())
            / (comp_Array[componentNumber].getb() + comp_Array[j].getb()) * Math.pow(gii * gjj, 0.5)
            * (1.0 - intparam[j][componentNumber]);

        gji = -2.0 * Math.sqrt(comp_Array[j].getb() * comp_Array[componentNumber].getb())
            / (comp_Array[j].getb() + comp_Array[componentNumber].getb()) * Math.sqrt(gii * gjj)
            * (1 - intparam[j][componentNumber]);
        alpha = 0.0;
        tau = (gji - gii) / (R * temperature);
        tau2 = (gij - gjj) / (R * temperature);

        if (phase.getInitType() > 1) {
          double dgijdt =
              -2.0 * Math.sqrt(comp_Array[componentNumber].getb() * comp_Array[j].getb())
                  / (comp_Array[componentNumber].getb() + comp_Array[j].getb()) * 1.0
                  / Math.sqrt(gii * gjj) * (1.0 - intparam[j][componentNumber])
                  * (dgiidt * gjj + dgjjdt * gii) * 0.5;
          double dgijdtdt =
              -2.0 * Math.sqrt(comp_Array[componentNumber].getb() * comp_Array[j].getb())
                  / (comp_Array[componentNumber].getb() + comp_Array[j].getb())
                  * ((1.0 / Math.pow(gii * gjj, 1.5) * (1.0 - intparam[j][componentNumber])
                      * Math.pow((dgiidt * gjj + dgjjdt * gii), 2.0) * 0.5 * -0.5)
                      + (1.0 / Math.sqrt(gii * gjj) * (1.0 - intparam[j][componentNumber])
                          * (dgiidtdt * gjj + dgiidt * dgjjdt + dgjjdtdt * gii + dgjjdt * dgiidt)
                          * 0.5));

          double dgjidt =
              -2.0 * Math.sqrt(comp_Array[j].getb() * comp_Array[componentNumber].getb())
                  / (comp_Array[j].getb() + comp_Array[componentNumber].getb()) * 1.0
                  / Math.sqrt(gii * gjj) * (1.0 - intparam[j][componentNumber])
                  * (dgiidt * gjj + dgjjdt * gii) * 0.5;

          double dgjidtdt =
              -2.0 * Math.pow(comp_Array[j].getb() * comp_Array[componentNumber].getb(), 0.5)
                  / (comp_Array[j].getb() + comp_Array[componentNumber].getb())
                  * ((1.0 / Math.pow(gii * gjj, 1.5) * (1.0 - intparam[j][componentNumber])
                      * Math.pow(dgiidt * gjj + dgjjdt * gii, 2.0) * 0.5 * -0.5)
                      + (1.0 / Math.sqrt(gii * gjj) * (1.0 - intparam[j][componentNumber])
                          * (dgiidtdt * gjj + dgiidt * dgjjdt + dgjjdtdt * gii + dgjjdt * dgiidt)
                          * 0.5));

          dtaudt = -dgiidt / (R * temperature) + gii / (R * temperature * temperature)
              + dgjidt / (R * temperature) - gji / (R * temperature * temperature);
          dtaudtdt = -dgiidtdt / (R * temperature) + dgiidt / (R * temperature * temperature)
              + dgiidt / (R * temperature * temperature)
              - 2.0 * gii / (R * temperature * temperature * temperature)
              + dgjidtdt / (R * temperature) - dgjidt / (R * temperature * temperature)
              - dgjidt / (R * temperature * temperature)
              + 2 * gji / (R * temperature * temperature * temperature);
          dtau2dt = -dgjjdt / (R * temperature) + gjj / (R * temperature * temperature)
              + dgijdt / (R * temperature) - gij / (R * temperature * temperature);
          dtau2dtdt = -dgjjdtdt / (R * temperature) + dgjjdt / (R * temperature * temperature)
              + dgjjdt / (R * temperature * temperature)
              - 2 * gjj / (R * temperature * temperature * temperature)
              + dgijdtdt / (R * temperature) - dgijdt / (R * temperature * temperature)
              - dgijdt / (R * temperature * temperature)
              + 2 * gij / (R * temperature * temperature * temperature);
        }
      }

      G = comp_Array[j].getb() * Math.exp(-alpha * tau);
      dGdt = dtaudt * (-alpha) * G;
      dGdtdt = dtaudtdt * (-alpha) * G + dtaudt * (-alpha) * dGdt;
      G2 = comp_Array[componentNumber].getb() * Math.exp(-alpha * tau2);
      dG2dt = dtau2dt * (-alpha) * G2;
      dG2dtdt = dtau2dtdt * (-alpha) * G2 + dtau2dt * (-alpha) * dG2dt;
      A += tau * G * comp_Array[j].getx();
      B += G * comp_Array[j].getx();
      E = G2 * comp_Array[j].getx();

      if (phase.getInitType() > 1) {
        dAdT = dAdT + comp_Array[j].getx() * dGdt * tau + comp_Array[j].getx() * G * dtaudt;
        dAdTdT = dAdTdT + comp_Array[j].getx() * dGdtdt * tau + comp_Array[j].getx() * dGdt * dtaudt
            + comp_Array[j].getx() * dGdt * dtaudt + comp_Array[j].getx() * G * dtaudtdt;
        dA2dT = comp_Array[j].getx() * dG2dt * tau2 + comp_Array[j].getx() * G2 * dtau2dt;
        dA2dTdT = comp_Array[j].getx() * dG2dtdt * tau2 + comp_Array[j].getx() * dG2dt * dtau2dt
            + comp_Array[j].getx() * dG2dt * dtau2dt + comp_Array[j].getx() * G2 * dtau2dtdt;
        dA3dT = tau2 * G2 * comp_Array[j].getx();
        dA3dTdT = dtau2dt * G2 * comp_Array[j].getx() + tau2 * dG2dt * comp_Array[j].getx();
        dA4dT = 2.0 * comp_Array[j].getx() * G2;
        dA4dTdT = 2.0 * comp_Array[j].getx() * dG2dt;
        dA5dT = comp_Array[j].getx() * dG2dt;
        dA5dTdT = comp_Array[j].getx() * dG2dtdt;
        dA6dT = comp_Array[j].getx() * G2;
        dA6dTdT = comp_Array[j].getx() * dG2dt;
        dBdT += dGdt * comp_Array[j].getx();
        dBdTdT += dGdtdt * comp_Array[j].getx();
      }
      // dEdT = dG2dt * comp_Array[j].getx();

      C = 0;
      D = 0;
      dCdT = 0;
      dCdTdT = 0;
      dDdT = 0;
      dDdTdT = 0;
      // System.out.println("hei");

      for (l = 0; l < numberOfComponents; l++) {
        if (mixRule[l][j].equals("HV")) {
          Dij = HVgij[l][j];
          DijT = HVgijT[l][j];
          alpha = HValpha[l][j];
          tau = Dij / (temperature) + DijT;
          dtaudt = -Dij / (temperature * temperature);
          dtaudtdt = 2.0 * Dij / (temperature * temperature * temperature);
        } else {
          gjj = -deltaEOS * comp_Array[j].aT(temperature) / comp_Array[j].getb();
          gii = -deltaEOS * comp_Array[l].aT(temperature) / comp_Array[l].getb();
          gij = -2.0 * Math.sqrt(comp_Array[l].getb() * comp_Array[j].getb())
              / (comp_Array[l].getb() + comp_Array[j].getb()) * Math.sqrt(gii * gjj)
              * (1.0 - intparam[l][j]);
          tau = (gij - gjj) / (R * temperature);

          if (phase.getInitType() > 1) {
            double dgjjdt = -deltaEOS * comp_Array[j].diffaT(temperature) / comp_Array[j].getb();
            double dgjjdtdt =
                -deltaEOS * comp_Array[j].diffdiffaT(temperature) / comp_Array[j].getb();
            double dgiidt = -deltaEOS * comp_Array[l].diffaT(temperature) / comp_Array[l].getb();
            double dgiidtdt =
                -deltaEOS * comp_Array[l].diffdiffaT(temperature) / comp_Array[l].getb();

            double dgijdt = -2.0 * Math.sqrt(comp_Array[l].getb() * comp_Array[j].getb())
                / (comp_Array[l].getb() + comp_Array[j].getb()) * 1.0 / Math.sqrt(gii * gjj)
                * (1.0 - intparam[l][j]) * (dgiidt * gjj + dgjjdt * gii) * 0.5;
            double dgijdtdt = -2.0 * Math.pow(comp_Array[l].getb() * comp_Array[j].getb(), 0.5)
                / (comp_Array[l].getb() + comp_Array[j].getb())
                * ((1.0 / Math.pow(gii * gjj, 1.5) * (1.0 - intparam[l][j])
                    * Math.pow(dgiidt * gjj + dgjjdt * gii, 2.0) * 0.5 * -0.5)
                    + (1.0 / Math.sqrt(gii * gjj) * (1.0 - intparam[l][j])
                        * (dgiidtdt * gjj + dgiidt * dgjjdt + dgjjdtdt * gii + dgjjdt * dgiidt)
                        * 0.5));
            alpha = 0.0;

            dtaudt = -dgjjdt / (R * temperature) + gjj / (R * temperature * temperature)
                + dgijdt / (R * temperature) - gij / (R * temperature * temperature);
            dtaudtdt = -dgjjdtdt / (R * temperature) + dgjjdt / (R * temperature * temperature)
                + dgjjdt / (R * temperature * temperature)
                - 2 * dgjjdt / (R * temperature * temperature * temperature)
                + dgijdtdt / (R * temperature) - dgijdt / (R * temperature * temperature)
                - dgijdt / (R * temperature * temperature)
                + 2 * gij / (R * temperature * temperature * temperature);
          }
        }

        G = comp_Array[l].getb() * Math.exp(-alpha * tau);
        dGdt = dtaudt * -alpha * G;
        Gmatrix[l][j] = G;
        tauMatrix[l][j] = tau;

        C += G * comp_Array[l].getx();
        D += G * tau * comp_Array[l].getx();
        if (phase.getInitType() > 1) {
          dCdT += dGdt * comp_Array[l].getx();
          dCdTdT += dGdtdt * comp_Array[l].getx();
          dDdT += comp_Array[l].getx() * dGdt * tau + comp_Array[l].getx() * G * dtaudt;
          dDdTdT += comp_Array[l].getx() * dGdtdt * tau + comp_Array[l].getx() * dGdt * dtaudt
              + comp_Array[l].getx() * dGdt * dtaudt + comp_Array[l].getx() * G * dtaudtdt;
        }
      }
      // System.out.println("tesing gij");
      if (phase.getInitType() > 1) {
        dA2dTetter += dA2dT / C;
        dA2dTdTetter += dA2dTdT / C - dA2dT / Math.pow(C, 2.0) * dCdT;
        dA3dTetter += dA3dT * dCdT / (C * C);
        dA3dTdTetter += dA3dTdT * dCdT / (C * C) + dA3dT * dCdTdT / (C * C)
            - 2 * dA3dT * dCdT * dCdT / (C * C * C);
        dA4dTetter += dA4dT * dCdT * D / (C * C * C);
        dA4dTdTetter += dA4dTdT * dCdT * D / (C * C * C) + dA4dT * dCdTdT * D / (C * C * C)
            + dA4dT * dCdT * dDdT / (C * C * C) - 3.0 * dA4dT * dCdT * D / (C * C * C * C) * dCdT;
        dA5dTetter += dA5dT * D / (C * C);
        dA5dTdTetter +=
            dA5dTdT * D / (C * C) + dA5dT * dDdT / (C * C) - 2 * dA5dT * D / (C * C) * dCdT;
        dA6dTetter += dA6dT * dDdT / (C * C);
        dA6dTdTetter += dA6dTdT * dDdT / (C * C) + dA6dT * dDdTdT / (C * C)
            - 2 * dA6dT * dDdT / (C * C * C) * dCdT;
      }
      if (mixRule[componentNumber][j].equals("HV")) {
        tau2 = HVgij[componentNumber][j] / (temperature) + HVgijT[componentNumber][j];
        dtau2dt = -HVgij[componentNumber][j] / (temperature * temperature);
      } else {
        gii = -deltaEOS * comp_Array[componentNumber].aT(temperature)
            / comp_Array[componentNumber].getb();
        double dgiidt = -deltaEOS * comp_Array[componentNumber].diffaT(temperature)
            / comp_Array[componentNumber].getb();
        gjj = -deltaEOS * comp_Array[j].aT(temperature) / comp_Array[j].getb();
        double dgjjdt = -deltaEOS * comp_Array[j].diffaT(temperature) / comp_Array[j].getb();
        gij = -2.0 * Math.pow(comp_Array[componentNumber].getb() * comp_Array[j].getb(), 0.5)
            / (comp_Array[componentNumber].getb() + comp_Array[j].getb()) * Math.pow(gii * gjj, 0.5)
            * (1.0 - intparam[componentNumber][j]);
        tau2 = (gij - gjj) / (R * temperature);

        double dgijdt = -2.0 * Math.sqrt(comp_Array[componentNumber].getb() * comp_Array[j].getb())
            / (comp_Array[componentNumber].getb() + comp_Array[j].getb()) * 1.0
            / Math.sqrt(gii * gjj) * (1.0 - intparam[componentNumber][j])
            * (dgiidt * gjj + dgjjdt * gii) * 0.5;
        dtau2dt = -dgjjdt / (R * temperature) + gjj / (R * temperature * temperature)
            + dgijdt / (R * temperature) - gij / (R * temperature * temperature);
      }

      F += E / C * (tau2 - D / C);
      // dFdT += (dEdT / C - E / (C * C) * dCdT) * (tau2 - D / C)+ E / C * (dtau2dt - (dDdT / C - D
      // / (C * C) * dCdT));
      // F2T = F2T - 2*2*A/Math.pow(C,2) + 2*2*E*D/Math.pow(C,3); // A til A2;
    }

    lngamma = A / B + F;
    if (phase.getInitType() > 1) {
      dlngammadt = (dAdT / B - A / (B * B) * dBdT + dA2dTetter - dA3dTetter + dA4dTetter
          - dA5dTetter - dA6dTetter);
      dlngammadtdt = dAdTdT / B - dBdT * dAdT / Math.pow(B, 2.0) - dAdT / (B * B) * dBdT
          + 2 * dBdT * A / Math.pow(B, 3.0) * dBdT - A / (B * B) * dBdTdT + 0 * dA2dTdTetter
          - 0 * dA3dTdTetter + 0 * dA4dTdTetter - 0 * dA5dTdTetter - 0 * dA6dTdTetter;
    }

    gamma = Math.exp(lngamma);
    // System.out.println("gamma " + gamma);

    // if derivates....
    if (type == 3) {
      double dAdn = 0;
      double dBdn = 0;
      double Etemp = 0;
      double dEdn = 0;
      double Ctemp = 0;
      double Dtemp = 0;
      double Ftemp = 0;
      double Gtemp = 0;

      for (int p = 0; p < numberOfComponents; p++) {
        dAdn = tauMatrix[p][componentNumber] * Gmatrix[p][componentNumber];
        dBdn = Gmatrix[p][componentNumber];
        dEdn = Gmatrix[componentNumber][p] * tauMatrix[componentNumber][p];
        // dFdn = Gmatrix[componentNumber][p];
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
          Dtemp += Gmatrix[p][f] * Gmatrix[componentNumber][f] * tauMatrix[componentNumber][f]
              * comp_Array[f].getx() / (sum * sum);
          Ftemp += comp_Array[f].getx() * Gmatrix[p][f] * sum2 * Gmatrix[componentNumber][f]
              / (sum * sum * sum);
          Gtemp += comp_Array[f].getx() * Gmatrix[p][f] * tauMatrix[p][f]
              * Gmatrix[componentNumber][f] / (sum * sum);
        }
        dlngammadn[p] = (dAdn / B - A / (B * B) * dBdn) + dEdn / Ctemp - Dtemp
            - Etemp * Gmatrix[componentNumber][p] / (Ctemp * Ctemp) + 2.0 * Ftemp - Gtemp;
        // E/(C*C)*dCdn[p]*(tau2-D/C) + E/C*(-dDdn[p]/C + D/(C*C)*dCdn[p]);
        dlngammadn[p] /= (nt);
      }
      // System.out.println("Dlngamdn: " + dlngammadn[p] + " x: " +
      // comp_Array[p].getx()+ " length: ");
    }

    return gamma;
  }
}
