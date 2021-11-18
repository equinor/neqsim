/*
 * System_SRK_EOS.java
 *
 * Created on 8. april 2000, 23:14
 */

package neqsim.thermo.component;

import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhasePCSAFT;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class ComponentPCSAFT extends ComponentSrk {
        private static final long serialVersionUID = 1000;

        private double dSAFTi = 1.0, dmSAFTdi = 1.0;
        double dghsSAFTdi, dnSAFTdi = 1.0, dahsSAFTdi = 1.0, dlogghsSAFTdi = 1.0;

        private double F1dispVolTermdn = 1.0, F1dispSumTermdn = 1.0, F1dispI1dn = 1.0,
                        F2dispI2dn = 1.0, F2dispZHCdn = 1.0, F2dispVolTermdn = 1.0,
                        F2dispSumTermdn = 1;

        int useHS = 1, useDISP1 = 1, useDISP2 = 1;

        /**
         * Creates new System_SRK_EOS Ev liten fil ja.
         */

        public ComponentPCSAFT() {
                super();
        }

        public ComponentPCSAFT(double moles) {
                numberOfMoles = moles;
        }

        public ComponentPCSAFT(String component_name, double moles, double molesInPhase,
                        int compnumber) {
                super(component_name, moles, molesInPhase, compnumber);
        }

        public ComponentPCSAFT(int number, double TC, double PC, double M, double a, double moles) {
                super(number, TC, PC, M, a, moles);
        }

        @Override
        public Object clone() {
                ComponentPCSAFT clonedComponent = null;
                try {
                        clonedComponent = (ComponentPCSAFT) super.clone();
                } catch (Exception e) {
                        logger.error("Cloning failed.", e);
                }

                return clonedComponent;
        }

        @Override
        public void init(double temperature, double pressure, double totalNumberOfMoles,
                        double beta, int type) {
                setdSAFTi(getSigmaSAFTi()
                                * (1.0 - 0.12 * Math.exp(-3.0 * getEpsikSAFT() / temperature)));
                super.init(temperature, pressure, totalNumberOfMoles, beta, type);
        }

        @Override
        public void Finit(PhaseInterface phase, double temp, double pres, double totMoles,
                        double beta, int numberOfComponents, int type) {
                super.Finit(phase, temp, pres, totMoles, beta, numberOfComponents, type);
                setDnSAFTdi(calcdnSAFTdi(phase, numberOfComponents, temp, pres));
                setDghsSAFTdi(calcdghsSAFTdi(phase, numberOfComponents, temp, pres));
                setDlogghsSAFTdi(1.0 / ((PhasePCSAFT) phase).getGhsSAFT() * getDghsSAFTdi());
                setDmSAFTdi(calcdmSAFTdi(phase, numberOfComponents, temp, pres));
                setdahsSAFTdi(calcdahsSAFTdi(phase, numberOfComponents, temp, pres));

                F1dispVolTermdn = 1.0 * ThermodynamicConstantsInterface.avagadroNumber
                                / ((PhasePCSAFT) phase).getVolumeSAFT();
                F2dispVolTermdn = F1dispVolTermdn;
                F1dispSumTermdn = calcF1dispSumTermdn(phase, numberOfComponents, temp, pres);
                F2dispSumTermdn = calcF2dispSumTermdn(phase, numberOfComponents, temp, pres);
                F1dispI1dn = ((PhasePCSAFT) phase).calcF1dispI1dN() * getDnSAFTdi()
                                + ((PhasePCSAFT) phase).calcF1dispI1dm() * getDmSAFTdi();
                F2dispI2dn = ((PhasePCSAFT) phase).calcF2dispI2dN() * getDnSAFTdi()
                                + ((PhasePCSAFT) phase).calcF2dispI2dm() * getDmSAFTdi();

                F2dispZHCdn = ((PhasePCSAFT) phase).getF2dispZHCdN() * getDnSAFTdi()
                                + ((PhasePCSAFT) phase).getF2dispZHCdm() * getDmSAFTdi();
                // System.out.println("fugasity " + getFugasityCoefficient());
        }

        @Override
        public double dFdN(PhaseInterface phase, int numberOfComponents, double temperature,
                        double pressure) {
                // System.out.println("term getF " +
                // ((PhasePCSAFT)phase).getF()/phase.getNumberOfMolesInPhase());
                // System.out.println("term getF21 " +
                // dF_HC_SAFTdN(phase,numberOfComponents,temperature,pressure));
                // System.out.println("term getF22 " +
                // dF_DISP1_SAFTdN(phase,numberOfComponents,temperature,pressure));
                // System.out.println("term getF23 " +
                // dF_DISP2_SAFTdN(phase,numberOfComponents,temperature,pressure));
                //
                // System.out.println("term furgacity coef " + getFugasityCoefficient());
                return useHS * dF_HC_SAFTdN(phase, numberOfComponents, temperature, pressure)
                                + useDISP1 * dF_DISP1_SAFTdN(phase, numberOfComponents, temperature,
                                                pressure)
                                + useDISP2 * dF_DISP2_SAFTdN(phase, numberOfComponents, temperature,
                                                pressure);
        }

        public double dF_HC_SAFTdN(PhaseInterface phase, int numberOfComponents, double temperature,
                        double pressure) {
                return ((PhasePCSAFT) phase).F_HC_SAFT() / phase.getNumberOfMolesInPhase() + phase
                                .getNumberOfMolesInPhase()
                                * (getDmSAFTdi() * ((PhasePCSAFT) phase).getAHSSAFT()
                                                + ((PhasePCSAFT) phase).getmSAFT() * getdahsSAFTdi()
                                                - (mSAFTi - 1.0) / phase.getNumberOfMolesInPhase()
                                                                * Math.log(((PhasePCSAFT) phase)
                                                                                .getGhsSAFT())
                                                + ((PhasePCSAFT) phase).getMmin1SAFT()
                                                                / phase.getNumberOfMolesInPhase()
                                                                * Math.log(((PhasePCSAFT) phase)
                                                                                .getGhsSAFT())
                                                - ((PhasePCSAFT) phase).getMmin1SAFT()
                                                                * getDlogghsSAFTdi());/// (ThermodynamicConstantsInterface.R*temperature);
        }

        public double dF_DISP1_SAFTdN(PhaseInterface phase, int numberOfComponents,
                        double temperature, double pressure) {
                return ((PhasePCSAFT) phase).F_DISP1_SAFT() / phase.getNumberOfMolesInPhase()
                                + phase.getNumberOfMolesInPhase() * ((-2.0
                                                * ThermodynamicConstantsInterface.pi
                                                * F1dispVolTermdn
                                                * ((PhasePCSAFT) phase).getF1dispSumTerm()
                                                * ((PhasePCSAFT) phase).getF1dispI1()
                                                - 2.0 * ThermodynamicConstantsInterface.pi
                                                                * ((PhasePCSAFT) phase)
                                                                                .getF1dispVolTerm()
                                                                * F1dispSumTermdn
                                                                * ((PhasePCSAFT) phase)
                                                                                .getF1dispI1()
                                                - 2.0 * ThermodynamicConstantsInterface.pi
                                                                * ((PhasePCSAFT) phase)
                                                                                .getF1dispVolTerm()
                                                                * ((PhasePCSAFT) phase)
                                                                                .getF1dispSumTerm()
                                                                * F1dispI1dn));/// (ThermodynamicConstantsInterface.R*temperature);
        }

        public double calcdmSAFTdi(PhaseInterface phase, int nuberOfComponents, double temp,
                        double pres) {
                return mSAFTi / phase.getNumberOfMolesInPhase() - ((PhasePCSAFT) phase).getmSAFT()
                                / phase.getNumberOfMolesInPhase();
        }

        public double dF_DISP2_SAFTdN(PhaseInterface phase, int numberOfComponents,
                        double temperature, double pressure) {
                return ((PhasePCSAFT) phase).F_DISP2_SAFT() / phase.getNumberOfMolesInPhase()
                                + phase.getNumberOfMolesInPhase()
                                                * ((-ThermodynamicConstantsInterface.pi
                                                                * getDmSAFTdi()
                                                                * ((PhasePCSAFT) phase)
                                                                                .getF1dispVolTerm()
                                                                * ((PhasePCSAFT) phase)
                                                                                .getF2dispSumTerm()
                                                                * ((PhasePCSAFT) phase)
                                                                                .getF2dispI2()
                                                                * ((PhasePCSAFT) phase)
                                                                                .getF2dispZHC()
                                                                - ThermodynamicConstantsInterface.pi
                                                                                * ((PhasePCSAFT) phase)
                                                                                                .getmSAFT()
                                                                                * F2dispVolTermdn
                                                                                * ((PhasePCSAFT) phase)
                                                                                                .getF2dispSumTerm()
                                                                                * ((PhasePCSAFT) phase)
                                                                                                .getF2dispI2()
                                                                                * ((PhasePCSAFT) phase)
                                                                                                .getF2dispZHC()
                                                                - ThermodynamicConstantsInterface.pi
                                                                                * ((PhasePCSAFT) phase)
                                                                                                .getmSAFT()
                                                                                * ((PhasePCSAFT) phase)
                                                                                                .getF1dispVolTerm()
                                                                                * F2dispSumTermdn
                                                                                * ((PhasePCSAFT) phase)
                                                                                                .getF2dispI2()
                                                                                * ((PhasePCSAFT) phase)
                                                                                                .getF2dispZHC()
                                                                - ThermodynamicConstantsInterface.pi
                                                                                * ((PhasePCSAFT) phase)
                                                                                                .getmSAFT()
                                                                                * ((PhasePCSAFT) phase)
                                                                                                .getF1dispVolTerm()
                                                                                * ((PhasePCSAFT) phase)
                                                                                                .getF2dispSumTerm()
                                                                                * F2dispI2dn
                                                                                * ((PhasePCSAFT) phase)
                                                                                                .getF2dispZHC()
                                                                - ThermodynamicConstantsInterface.pi
                                                                                * ((PhasePCSAFT) phase)
                                                                                                .getmSAFT()
                                                                                * ((PhasePCSAFT) phase)
                                                                                                .getF1dispVolTerm()
                                                                                * ((PhasePCSAFT) phase)
                                                                                                .getF2dispSumTerm()
                                                                                * ((PhasePCSAFT) phase)
                                                                                                .getF2dispI2()
                                                                                * F2dispZHCdn));/// (ThermodynamicConstantsInterface.R*temperature);
        }

        public double calcF1dispSumTermdn(PhaseInterface phase, int numberOfComponents,
                        double temperature, double pressure) {
                double temp1 = 0.0;
                for (int i = 0; i < phase.getNumberOfComponents(); i++) {
                        temp1 += phase.getComponent(i).getNumberOfMolesInPhase()
                                        * phase.getComponent(i).getmSAFTi() * getmSAFTi()
                                        * Math.sqrt(getEpsikSAFT() / temperature
                                                        * phase.getComponent(i).getEpsikSAFT()
                                                        / temperature)
                                        * (1.0 - ((PhaseEosInterface) phase).getMixingRule()
                                                        .getBinaryInteractionParameter(
                                                                        componentNumber, i))
                                        * Math.pow(0.5 * (phase.getComponent(i).getSigmaSAFTi()
                                                        + getSigmaSAFTi()), 3.0);
                }
                return -2.0 / Math.pow(phase.getNumberOfMolesInPhase(), 1.0)
                                * ((PhasePCSAFT) phase).getF1dispSumTerm()
                                + 2.0 * temp1 / Math.pow(phase.getNumberOfMolesInPhase(), 2.0);
        }

        public double calcF2dispSumTermdn(PhaseInterface phase, int numberOfComponents,
                        double temperature, double pressure) {
                double temp1 = 0.0;
                for (int i = 0; i < phase.getNumberOfComponents(); i++) {
                        temp1 += phase.getComponent(i).getNumberOfMolesInPhase()
                                        * phase.getComponent(i).getmSAFTi() * getmSAFTi()
                                        * getEpsikSAFT() / temperature
                                        * phase.getComponent(i).getEpsikSAFT() / temperature
                                        * Math.pow((1.0 - ((PhaseEosInterface) phase)
                                                        .getMixingRule()
                                                        .getBinaryInteractionParameter(
                                                                        componentNumber, i)),
                                                        2.0)
                                        * Math.pow(0.5 * (phase.getComponent(i).getSigmaSAFTi()
                                                        + getSigmaSAFTi()), 3.0);
                        // System.out.println("kij "+
                        // ((PhaseEosInterface)phase).getMixingRule().getBinaryInteractionParameter(componentNumber,
                        // i));
                }
                return -2.0 / Math.pow(phase.getNumberOfMolesInPhase(), 1.0)
                                * ((PhasePCSAFT) phase).getF2dispSumTerm()
                                + 2.0 * temp1 / Math.pow(phase.getNumberOfMolesInPhase(), 2.0);
        }

        public double calcdghsSAFTdi(PhaseInterface phase, int nuberOfComponents, double temp,
                        double pres) {
                double temp1 = ((PhasePCSAFT) phase).getDgHSSAFTdN();
                return temp1 * getDnSAFTdi();
        }

        public double calcdahsSAFTdi(PhaseInterface phase, int nuberOfComponents, double temp,
                        double pres) {
                double temp1 = ((4.0 - 6.0 * ((PhasePCSAFT) phase).getNSAFT())
                                * Math.pow(1.0 - ((PhasePCSAFT) phase).getNSAFT(), 2.0)
                                - (4.0 * ((PhasePCSAFT) phase).getNSAFT() - 3.0
                                                * Math.pow(((PhasePCSAFT) phase).getNSAFT(), 2.0))
                                                * (-2.0)
                                                * (1.0 - ((PhasePCSAFT) phase).getNSAFT()));
                return temp1 / Math.pow(1.0 - ((PhasePCSAFT) phase).getNSAFT(), 4.0)
                                * getDnSAFTdi();
        }

        public double calcdnSAFTdi(PhaseInterface phase, int nuberOfComponents, double temp,
                        double pres) {
                double temp1 = phase.getNumberOfMolesInPhase()
                                / ((PhasePCSAFT) phase).getVolumeSAFT() * getmSAFTi()
                                * Math.pow(getdSAFTi(), 3.0) * 1.0 / phase.getNumberOfMolesInPhase()
                                + 1.0 / ((PhasePCSAFT) phase).getVolumeSAFT()
                                                * ((PhasePCSAFT) phase).getDSAFT()
                                - 1.0 / Math.pow(phase.getNumberOfMolesInPhase(), 1.0)
                                                * phase.getNumberOfMolesInPhase()
                                                / ((PhasePCSAFT) phase).getVolumeSAFT()
                                                * ((PhasePCSAFT) phase).getDSAFT();
                return ThermodynamicConstantsInterface.pi / 6.0
                                * ThermodynamicConstantsInterface.avagadroNumber * (temp1);
        }

        public double getdSAFTi() {
                return dSAFTi;
        }

        public void setdSAFTi(double di) {
                this.dSAFTi = di;
        }

        public double getDghsSAFTdi() {
                return dghsSAFTdi;
        }

        public void setDghsSAFTdi(double dghsSAFTdi) {
                this.dghsSAFTdi = dghsSAFTdi;
        }

        public double getDnSAFTdi() {
                return dnSAFTdi;
        }

        public void setDnSAFTdi(double dnSAFTdi) {
                this.dnSAFTdi = dnSAFTdi;
        }

        public double getdahsSAFTdi() {
                return dahsSAFTdi;
        }

        public void setdahsSAFTdi(double dahsSAFTdi) {
                this.dahsSAFTdi = dahsSAFTdi;
        }

        public double getDmSAFTdi() {
                return dmSAFTdi;
        }

        public void setDmSAFTdi(double dmSAFTdi) {
                this.dmSAFTdi = dmSAFTdi;
        }

        public double getDlogghsSAFTdi() {
                return dlogghsSAFTdi;
        }

        public void setDlogghsSAFTdi(double dlogghsSAFTdi) {
                this.dlogghsSAFTdi = dlogghsSAFTdi;
        }
}
