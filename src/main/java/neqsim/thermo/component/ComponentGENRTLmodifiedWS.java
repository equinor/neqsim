/*
 * ComponentGENRTLmodifiedHV.java
 *
 * Created on 18. juli 2000, 18:36
 */

package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class ComponentGENRTLmodifiedWS extends ComponentGeNRTL {

    private static final long serialVersionUID = 1000;

    /** Creates new ComponentGENRTLmodifiedHV */
    public ComponentGENRTLmodifiedWS() {
    }

    public ComponentGENRTLmodifiedWS(String component_name, double moles, double molesInPhase, int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);
    }

    @Override
	public double getlnGammadt() {
        return dlngammadt;
    }

    @Override
	public double getlnGammadn(int k) {
        return dlngammadn[k];
    }

    @Override
	public double getGamma(PhaseInterface phase, int numberOfComponents, double temperature, double pressure,
            int phasetype, double[][] WSalpha, double[][] WSgij, double[][] intparam, String[][] mixRule) {
        double[][] WSgijT = new double[numberOfComponents][numberOfComponents];
        return getGamma(phase, numberOfComponents, temperature, pressure, phasetype, WSalpha, WSgij, WSgijT, intparam,
                mixRule);
    }

    public double getGamma(PhaseInterface phase, int numberOfComponents, double temperature, double pressure,
            int phasetype, double[][] WSalpha, double[][] WSgij, double[][] WSgijT, double[][] intparam,
            String[][] mixRule) {
        double type = phase.getInitType();
        double A = 0, B = 0, C = 0, D = 0, E = 0, F = 0, ny = 0, tau = 0, tau2 = 0, G = 0, G2 = 0, alpha = 0, Dij = 0,
                Djj = 0, Dji = 0, Dii = 0, DijT = 0, DjjT = 0, DjiT = 0, DiiT = 0, gij = 0, gjj = 0, gji = 0, gii = 0,
                F2T = 0, tot2 = 0;
        int i, j, l, k, delta = 0;
        double dAdT = 0, dBdT = 0, dEdT, dCdT = 0, dFdT = 0, dDdT = 0;
        double dtaudt = 0, dtau2dt = 0, dGdt = 0, dG2dt = 0;
        double[][] Gmatrix = new double[numberOfComponents][numberOfComponents];
        double[][] tauMatrix = new double[numberOfComponents][numberOfComponents];
        dlngammadn = new double[numberOfComponents];
        ComponentInterface[] comp_Array = phase.getcomponentArray();
        double lngammaold = 0, dlngammadtold = 0, dA2dTetter = 0, dA3dTetter = 0, dA4dTetter = 0, dA5dTetter = 0,
                dA6dTetter = 0;
        // for(int w=0;w<3;w++){
        F = 0;
        dFdT = 0;
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
        double dA2dT = 0, dA3dT = 0, dA4dT = 0, dA5dT = 0, dA6dT = 0;

        // PhaseGEEosInterface phaseny = (PhaseGEEosInterface) phase.getPhase();
        // PhaseGEInterface GEPhase = phaseny.getGEphase();

        // ComponentGeNRTLInterface[] compArray = (ComponentGeNRTLInterface[])
        // GEPhase.getcomponentArray();
        // PhaseGEInterface GEphase = new PhaseGEInterface();
        // PhaseGEInterface phaseny = (PhaseGEInterface) phase.getPhase();

        for (j = 0; j < numberOfComponents; j++) {
            Dij = WSgij[this.getComponentNumber()][j];
            // System.out.println("Dij" + Dij);
            Dji = WSgij[j][this.getComponentNumber()];
            // System.out.println("Dji" + Dji);
            DijT = WSgijT[this.getComponentNumber()][j];
            DjiT = WSgijT[j][this.getComponentNumber()];
            // gji = HVgij[j][this.getComponentNumber()];
            // gjj = HVgii[j][j];
            alpha = WSalpha[j][this.getComponentNumber()]; // new HV + T*(gji-gii)
            tau = Dji / (temperature) + DjiT; // + Math.pow(gji, b)
            tau2 = Dij / (temperature) + DijT;
            dtaudt = -tau / temperature + DjiT / temperature;
            dtau2dt = -tau2 / temperature + DijT / temperature;
            // System.out.println("method GE1" + tau);

            G = Math.exp(-alpha * tau);
            dGdt = dtaudt * -alpha * G;
            G2 = Math.exp(-alpha * tau2);
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
            dEdT = dG2dt * comp_Array[j].getx();

            C = 0;
            D = 0;
            dCdT = 0;
            dDdT = 0;
            // System.out.println("hei");

            for (l = 0; l < numberOfComponents; l++) {

                Dij = WSgij[l][j];
                DijT = WSgijT[l][j];
                alpha = WSalpha[l][j];
                tau = Dij / (temperature) + DijT;
                dtaudt = -tau / temperature + DijT / temperature;

                G = Math.exp(-alpha * tau);
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

            if (mixRule[this.getComponentNumber()][j].equals("WS")) {
                tau2 = WSgij[this.getComponentNumber()][j] / (temperature) + WSgijT[this.getComponentNumber()][j];
            }
            dtau2dt = -tau2 / temperature + WSgijT[this.getComponentNumber()][j] / temperature;

            F += E / C * (tau2 - D / C);
            dFdT += (dEdT / C - E / (C * C) * dCdT) * (tau2 - D / C)
                    + E / C * (dtau2dt - (dDdT / C - D / (C * C) * dCdT));
            // F2T = F2T - 2*2*A/Math.pow(C,2) + 2*2*E*D/Math.pow(C,3); // A til A2;
        }

        lngamma = A / B + F;
        // dlngammadt = dAdT/B - A/(B*B)*dBdT + dFdT;
        dlngammadt = (dAdT / B - A / (B * B) * dBdT + dA2dTetter - dA3dTetter + dA4dTetter - dA5dTetter - dA6dTetter);
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

        // phaseny.getExessGibbsEnergy(numberOfComponents, temperature, pressure,
        // phasetype)
        gamma = Math.exp(lngamma);

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
                    Ftemp += comp_Array[f].getx() * Gmatrix[p][f] * sum2 * Gmatrix[this.getComponentNumber()][f]
                            / (sum * sum * sum);
                    Gtemp += comp_Array[f].getx() * Gmatrix[p][f] * tauMatrix[p][f]
                            * Gmatrix[this.getComponentNumber()][f] / (sum * sum);
                }
                dlngammadn[p] = (dAdn / B - A / (B * B) * dBdn) + dEdn / Ctemp - Dtemp
                        - Etemp * Gmatrix[this.getComponentNumber()][p] / (Ctemp * Ctemp) + 2.0 * Ftemp - Gtemp;// E/(C*C)*dCdn[p]*(tau2-D/C)
                                                                                                                // +
                                                                                                                // E/C*(-dDdn[p]/C
                                                                                                                // +
                                                                                                                // D/(C*C)*dCdn[p]);
                dlngammadn[p] /= (nt);
            }
            // System.out.println("Dlngamdn: " + dlngammadn[p] + " x: " +
            // comp_Array[p].getx()+ " length: ");
        }

        return gamma;
    }
}