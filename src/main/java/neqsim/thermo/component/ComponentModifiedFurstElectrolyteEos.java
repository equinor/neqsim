/*
 * ComponentModifiedFurstElectrolyteEos.java
 *
 * Created on 26. februar 2001, 17:59
 */

package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseModifiedFurstElectrolyteEos;

/**
 * @author Even Solbraa
 * @version
 */
public class ComponentModifiedFurstElectrolyteEos extends ComponentSrk {
        private static final long serialVersionUID = 1000;

        double Wi = 0, WiT = 0.0, epsi = 0, epsiV = 0, epsIonici = 0, epsIoniciV = 0, dEpsdNi = 0,
                        ionicCoVolume = 0, solventdiElectricdn = 0.0, solventdiElectricdndT = 0,
                        diElectricdn = 0, diElectricdndV = 0, diElectricdndT = 0, bornterm = 0,
                        alphai = 0.0, alphaiT = 0.0, alphaiV = 0.0, XLRi = 0.0, XBorni = 0.0;
        double sr2On = 1.0, lrOn = 1.0, bornOn = 1.0;

        /** Creates new ComponentModifiedFurstElectrolyteEos */
        public ComponentModifiedFurstElectrolyteEos() {}

    /** Creates new ComponentModifiedFurstElectrolyteEos */
    public ComponentModifiedFurstElectrolyteEos() {
    }

    public ComponentModifiedFurstElectrolyteEos(double moles) {
        super(moles);
        numberOfMoles = moles;
    }

    public ComponentModifiedFurstElectrolyteEos(String component_name, double moles, double molesInPhase,
            int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);
        ionicCoVolume = this.getIonicDiameter();
        if (ionicCharge != 0) {
            setIsIon(true);
        }
        b = ionicCharge != 0
                ? (neqsim.thermo.util.constants.FurstElectrolyteConstants.furstParams[0]
                        * Math.pow(getIonicDiameter(), 3.0)
                        + neqsim.thermo.util.constants.FurstElectrolyteConstants.furstParams[1]) * 1e5
                : b;
        a = ionicCharge != 0 ? 1.0e-35 : a;
        setAtractiveParameter(new neqsim.thermo.component.atractiveEosTerm.AtractiveTermSchwartzentruber(this));
        lennardJonesMolecularDiameter = ionicCharge != 0
                ? Math.pow((6.0 * b / 1.0e5) / (pi * avagadroNumber), 1.0 / 3.0) * 1e10
                : lennardJonesMolecularDiameter;

        // if(ionicCharge>0) stokesCationicDiameter = stokesCationicDiameter/3.0;
    }

    public ComponentModifiedFurstElectrolyteEos(int number, double TC, double PC, double M, double a, double moles) {
        super(number, TC, PC, M, a, moles);
    }

    public void initFurstParam() {
        b = ionicCharge != 0
                ? (neqsim.thermo.util.constants.FurstElectrolyteConstants.furstParams[0]
                        * Math.pow(getIonicDiameter(), 3.0)
                        + neqsim.thermo.util.constants.FurstElectrolyteConstants.furstParams[1]) * 1e5
                : b;
        lennardJonesMolecularDiameter = ionicCharge != 0
                ? Math.pow((6.0 * b / 1.0e5) / (pi * avagadroNumber), 1.0 / 3.0) * 1e10
                : lennardJonesMolecularDiameter;
    }

    @Override
    public ComponentModifiedFurstElectrolyteEos clone() {
        ComponentModifiedFurstElectrolyteEos clonedComponent = null;
        try {
            clonedComponent = (ComponentModifiedFurstElectrolyteEos) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        public ComponentModifiedFurstElectrolyteEos(String component_name, double moles,
                        double molesInPhase, int compnumber) {
                super(component_name, moles, molesInPhase, compnumber);
                ionicCoVolume = this.getIonicDiameter();
                if (ionicCharge != 0) {
                        setIsIon(true);
                }
                b = ionicCharge != 0
                                ? (neqsim.thermo.util.constants.FurstElectrolyteConstants.furstParams[0]
                                                * Math.pow(getIonicDiameter(), 3.0)
                                                + neqsim.thermo.util.constants.FurstElectrolyteConstants.furstParams[1])
                                                * 1e5
                                : b;
                a = ionicCharge != 0 ? 1.0e-35 : a;
                setAtractiveParameter(
                                new neqsim.thermo.component.atractiveEosTerm.AtractiveTermSchwartzentruber(
                                                this));
                lennardJonesMolecularDiameter = ionicCharge != 0
                                ? Math.pow((6.0 * b / 1.0e5) / (pi * avagadroNumber), 1.0 / 3.0)
                                                * 1e10
                                : lennardJonesMolecularDiameter;

                // if(ionicCharge>0) stokesCationicDiameter = stokesCationicDiameter/3.0;
        }

        public ComponentModifiedFurstElectrolyteEos(int number, double TC, double PC, double M,
                        double a, double moles) {
                super(number, TC, PC, M, a, moles);
        }

        public void initFurstParam() {
                b = ionicCharge != 0
                                ? (neqsim.thermo.util.constants.FurstElectrolyteConstants.furstParams[0]
                                                * Math.pow(getIonicDiameter(), 3.0)
                                                + neqsim.thermo.util.constants.FurstElectrolyteConstants.furstParams[1])
                                                * 1e5
                                : b;
                lennardJonesMolecularDiameter = ionicCharge != 0
                                ? Math.pow((6.0 * b / 1.0e5) / (pi * avagadroNumber), 1.0 / 3.0)
                                                * 1e10
                                : lennardJonesMolecularDiameter;
        }

        @Override
        public Object clone() {
                ComponentModifiedFurstElectrolyteEos clonedComponent = null;
                try {
                        clonedComponent = (ComponentModifiedFurstElectrolyteEos) super.clone();
                } catch (Exception e) {
                        logger.error("Cloning failed.", e);
                }

                return clonedComponent;
        }

        @Override
        public double calca() {
                if (ionicCharge != 0) {
                        return a;
                } else {
                        return super.calca();
                }
        }

        @Override
        public double calcb() {
                if (ionicCharge != 0) {
                        return b;
                } else {
                        return super.calcb();
                }
        }

        @Override
        public void init(double temperature, double pressure, double totalNumberOfMoles,
                        double beta, int type) {
                super.init(temperature, pressure, totalNumberOfMoles, beta, type);
        }

        @Override
        public void Finit(PhaseInterface phase, double temp, double pres, double totMoles,
                        double beta, int numberOfComponents, int type) {
                Wi = ((PhaseModifiedFurstElectrolyteEos) phase).calcWi(componentNumber, phase, temp,
                                pres, numberOfComponents);
                WiT = ((PhaseModifiedFurstElectrolyteEos) phase).calcWiT(componentNumber, phase,
                                temp, pres, numberOfComponents);
                epsi = dEpsdNi(phase, numberOfComponents, temp, pres);
                epsiV = dEpsdNidV(phase, numberOfComponents, temp, pres);
                epsIonici = dEpsIonicdNi(phase, numberOfComponents, temp, pres);
                epsIoniciV = dEpsIonicdNidV(phase, numberOfComponents, temp, pres);
                solventdiElectricdn =
                                calcSolventdiElectricdn(phase, numberOfComponents, temp, pres);
                solventdiElectricdndT =
                                calcSolventdiElectricdndT(phase, numberOfComponents, temp, pres);
                diElectricdn = calcdiElectricdn(phase, numberOfComponents, temp, pres);
                diElectricdndV = calcdiElectricdndV(phase, numberOfComponents, temp, pres);
                diElectricdndT = calcdiElectricdndT(phase, numberOfComponents, temp, pres);
                alphai = -(electronCharge * electronCharge * avagadroNumber) / (vacumPermittivity
                                * Math.pow(((PhaseModifiedFurstElectrolyteEos) phase)
                                                .getDiElectricConstant(), 2.0)
                                * R * temp) * diElectricdn;
                alphaiT = -electronCharge * electronCharge * avagadroNumber / (vacumPermittivity
                                * Math.pow(((PhaseModifiedFurstElectrolyteEos) phase)
                                                .getDiElectricConstant(), 2.0)
                                * R * temp) * diElectricdndT
                                + electronCharge * electronCharge * avagadroNumber
                                                / (vacumPermittivity * Math.pow(
                                                                ((PhaseModifiedFurstElectrolyteEos) phase)
                                                                                .getDiElectricConstant(),
                                                                2.0) * R * temp * temp)
                                                * diElectricdn
                                + 2.0 * electronCharge * electronCharge * avagadroNumber
                                                / (vacumPermittivity * Math.pow(
                                                                ((PhaseModifiedFurstElectrolyteEos) phase)
                                                                                .getDiElectricConstant(),
                                                                3.0) * R * temp)
                                                * diElectricdn
                                                * ((PhaseModifiedFurstElectrolyteEos) phase)
                                                                .getDiElectricConstantdT();
                alphaiV = -electronCharge * electronCharge * avagadroNumber / (vacumPermittivity
                                * Math.pow(((PhaseModifiedFurstElectrolyteEos) phase)
                                                .getDiElectricConstant(), 2.0)
                                * R * temp) * diElectricdndV
                                + 2.0 * electronCharge * electronCharge * avagadroNumber
                                                / (vacumPermittivity * Math.pow(
                                                                ((PhaseModifiedFurstElectrolyteEos) phase)
                                                                                .getDiElectricConstant(),
                                                                3.0) * R * temp)
                                                * diElectricdn
                                                * ((PhaseModifiedFurstElectrolyteEos) phase)
                                                                .getDiElectricConstantdV();
                XLRi = calcXLRdN(phase, numberOfComponents, temp, pres);
                if (getLennardJonesMolecularDiameter() > 0) {
                        XBorni = ionicCharge * ionicCharge
                                        / (getLennardJonesMolecularDiameter() * 1e-10);
                }
                super.Finit(phase, temp, pres, totMoles, beta, numberOfComponents, type);
        }

        public double dAlphaLRdndn(int j, PhaseInterface phase, int numberOfComponents,
                        double temperature, double pressure) {
                double temp = 2.0 * electronCharge * electronCharge * avagadroNumber
                                / (vacumPermittivity * Math
                                                .pow(((PhaseModifiedFurstElectrolyteEos) phase)
                                                                .getDiElectricConstant(), 3.0)
                                                * R * temperature)
                                * diElectricdn
                                * ((ComponentModifiedFurstElectrolyteEos) ((PhaseModifiedFurstElectrolyteEos) phase)
                                                .getComponents()[j]).getDiElectricConstantdn()
                                - electronCharge * electronCharge * avagadroNumber
                                                / (vacumPermittivity * Math.pow(
                                                                ((PhaseModifiedFurstElectrolyteEos) phase)
                                                                                .getDiElectricConstant(),
                                                                2.0) * R * temperature)
                                                * calcdiElectricdndn(j, phase, numberOfComponents,
                                                                temperature, pressure);
                return temp;
        }

        @Override
        public double dFdN(PhaseInterface phase, int numberOfComponents, double temperature,
                        double pressure) {
                double Fsup = 0, FSR2 = 0, FLR = 0, FBorn = 0;
                Fsup = super.dFdN(phase, numberOfComponents, temperature, pressure);
                FSR2 = dFSR2dN(phase, numberOfComponents, temperature, pressure);
                FLR = dFLRdN(phase, numberOfComponents, temperature, pressure);
                FBorn = dFBorndN(phase, numberOfComponents, temperature, pressure);
                // System.out.println("phase " + phase.getPhaseType());
                // System.out.println("name " + componentName);
                // System.out.println("Fsup: " + super.dFdN(phase,
                // numberOfComponents,temperature, pressure));
                // if(componentName.equals("Na+")){
                // System.out.println("FnSR: " + dFSR2dN(phase, numberOfComponents, temperature,
                // pressure));
                // System.out.println("FLRn: " + dFLRdN(phase, numberOfComponents,temperature,
                // pressure));
                // }
                return Fsup + sr2On * FSR2 + lrOn * FLR + bornOn * FBorn;
        }

        @Override
        public double dFdNdT(PhaseInterface phase, int numberOfComponents, double temperature,
                        double pressure) {
                return super.dFdNdT(phase, numberOfComponents, temperature, pressure)
                                + sr2On * dFSR2dNdT(phase, numberOfComponents, temperature,
                                                pressure)
                                + lrOn * dFLRdNdT(phase, numberOfComponents, temperature, pressure)
                                + bornOn * dFBorndNdT(phase, numberOfComponents, temperature,
                                                pressure);
        }

        @Override
        public double dFdNdV(PhaseInterface phase, int numberOfComponents, double temperature,
                        double pressure) {
                return super.dFdNdV(phase, numberOfComponents, temperature, pressure)
                                + sr2On * dFSR2dNdV(phase, numberOfComponents, temperature,
                                                pressure)
                                + lrOn * dFLRdNdV(phase, numberOfComponents, temperature, pressure);
        }

        @Override
        public double dFdNdN(int j, PhaseInterface phase, int numberOfComponents,
                        double temperature, double pressure) {
                return super.dFdNdN(j, phase, numberOfComponents, temperature, pressure)
                                + sr2On * dFSR2dNdN(j, phase, numberOfComponents, temperature,
                                                pressure)
                                + lrOn * dFLRdNdN(j, phase, numberOfComponents, temperature,
                                                pressure)
                                + bornOn * dFBorndNdN(j, phase, numberOfComponents, temperature,
                                                pressure);
        }

        // Long Range term equations and derivatives
        public double dFLRdN(PhaseInterface phase, int numberOfComponents, double temperature,
                        double pressure) {
                return ((PhaseModifiedFurstElectrolyteEos) phase).FLRXLR() * XLRi
                                + ((PhaseModifiedFurstElectrolyteEos) phase).dFdAlphaLR() * alphai;
                // + ((PhaseModifiedFurstElectrolyteEos) phase).FLRGammaLR()*gammaLRdn;
        }

        public double dFLRdNdT(PhaseInterface phase, int numberOfComponents, double temperature,
                        double pressure) {
                return ((PhaseModifiedFurstElectrolyteEos) phase).dFdAlphaLRdX() * XLRi
                                * ((PhaseModifiedFurstElectrolyteEos) phase).getAlphaLRT()
                                + ((PhaseModifiedFurstElectrolyteEos) phase).dFdAlphaLR() * alphaiT;
        }

        public double dFLRdNdV(PhaseInterface phase, int numberOfComponents, double temperature,
                        double pressure) {
                return 1e-5 * (((PhaseModifiedFurstElectrolyteEos) phase).dFdAlphaLRdX() * XLRi
                                * ((PhaseModifiedFurstElectrolyteEos) phase).getAlphaLRV()
                                + ((PhaseModifiedFurstElectrolyteEos) phase).dFdAlphaLR()
                                                * alphaiV);
        }

        public double dFLRdNdN(int j, PhaseInterface phase, int numberOfComponents,
                        double temperature, double pressure) {
                return ((PhaseModifiedFurstElectrolyteEos) phase).dFdAlphaLRdX() * XLRi
                                * ((ComponentModifiedFurstElectrolyteEos) ((PhaseModifiedFurstElectrolyteEos) phase)
                                                .getComponents()[j]).getAlphai()
                                + ((PhaseModifiedFurstElectrolyteEos) phase).dFdAlphaLR()
                                                * dAlphaLRdndn(j, phase, numberOfComponents,
                                                                temperature, pressure)
                                + ((PhaseModifiedFurstElectrolyteEos) phase).dFdAlphaLRdX() * alphai
                                                * ((ComponentModifiedFurstElectrolyteEos) ((PhaseModifiedFurstElectrolyteEos) phase)
                                                                .getComponents()[j]).getXLRi();
        }

        public double calcXLRdN(PhaseInterface phase, int numberOfComponents, double temperature,
                        double pressure) {
                return Math.pow(getIonicCharge(), 2.0)
                                * ((PhaseModifiedFurstElectrolyteEos) phase).getShieldingParameter()
                                / (1.0 + ((PhaseModifiedFurstElectrolyteEos) phase)
                                                .getShieldingParameter()
                                                * getLennardJonesMolecularDiameter() * 1e-10);
        }

        public double FLRN() {
                return 0.0;
        }

        public double calcSolventdiElectricdn(PhaseInterface phase, int numberOfComponents,
                        double temperature, double pressure) {
                if (getIonicCharge() != 0) {
                        return 0.0;
                }
                double ans2 = 0.0;
                for (int i = 0; i < numberOfComponents; i++) {
                        if (phase.getComponent(i).getIonicCharge() == 0) {
                                ans2 += phase.getComponent(i).getNumberOfMolesInPhase();
                        }
                }
                // return 0.0;
                return getDiElectricConstant(temperature) / ans2
                                - ((PhaseModifiedFurstElectrolyteEos) phase)
                                                .getSolventDiElectricConstant() / ans2;
        }

        public double calcSolventdiElectricdndn(int j, PhaseInterface phase, int numberOfComponents,
                        double temperature, double pressure) {
                if (getIonicCharge() != 0
                                || ((PhaseModifiedFurstElectrolyteEos) phase).getComponents()[j]
                                                .getIonicCharge() != 0) {
                        return 0.0;
                }
                double ans2 = 0.0;
                for (int i = 0; i < numberOfComponents; i++) {
                        if (phase.getComponent(i).getIonicCharge() == 0) {
                                ans2 += phase.getComponent(i).getNumberOfMolesInPhase();
                        }
                }
                // return 0.0;
                return -getDiElectricConstant(temperature) / (ans2 * ans2)
                                - ((PhaseModifiedFurstElectrolyteEos) phase).getComponents()[j]
                                                .getDiElectricConstant(temperature) / (ans2 * ans2)
                                + 2.0 * ((PhaseModifiedFurstElectrolyteEos) phase)
                                                .getSolventDiElectricConstant() / (ans2 * ans2);
        }

        public double calcSolventdiElectricdndT(PhaseInterface phase, int numberOfComponents,
                        double temperature, double pressure) {
                if (getIonicCharge() != 0) {
                        return 0.0;
                }
                double ans2 = 0.0;
                for (int i = 0; i < numberOfComponents; i++) {
                        if (phase.getComponent(i).getIonicCharge() == 0) {
                                ans2 += phase.getComponent(i).getNumberOfMolesInPhase();
                        }
                }
                // return 0.0;
                return getDiElectricConstantdT(temperature) / ans2
                                - ((PhaseModifiedFurstElectrolyteEos) phase)
                                                .getSolventDiElectricConstantdT() / ans2;
        }

        public double calcdiElectricdn(PhaseInterface phase, int numberOfComponents,
                        double temperature, double pressure) {
                double X = (1.0 - ((PhaseModifiedFurstElectrolyteEos) phase).getEpsIonic()) / (1.0
                                + ((PhaseModifiedFurstElectrolyteEos) phase).getEpsIonic() / 2.0);
                double Y = ((PhaseModifiedFurstElectrolyteEos) phase).getSolventDiElectricConstant()
                                - 1.0;
                double dXdf = getEpsIonici() * -3.0 / 2.0 / Math
                                .pow(((PhaseModifiedFurstElectrolyteEos) phase).getEpsIonic() / 2.0
                                                + 1.0, 2.0);
                double dYdf = getSolventDiElectricConstantdn();
                return dYdf * X + Y * dXdf;
        }

        public double calcdiElectricdndV(PhaseInterface phase, int numberOfComponents,
                        double temperature, double pressure) {
                double dXdf = ((PhaseModifiedFurstElectrolyteEos) phase).getEpsIonicdV() * -3.0
                                / 2.0
                                / Math.pow(((PhaseModifiedFurstElectrolyteEos) phase).getEpsIonic()
                                                / 2.0 + 1.0, 2.0);
                double dYdf = getSolventDiElectricConstantdn();
                double d1 = ((PhaseModifiedFurstElectrolyteEos) phase)
                                .getSolventDiElectricConstant();
                double d2 = epsIoniciV * -3.0 / 2.0 / Math
                                .pow(((PhaseModifiedFurstElectrolyteEos) phase).getEpsIonic() / 2.0
                                                + 1.0, 2.0);
                double d3 = ((PhaseModifiedFurstElectrolyteEos) phase).getEpsIonicdV() * epsIonici
                                * 3.0 / 2.0
                                / Math.pow(((PhaseModifiedFurstElectrolyteEos) phase).getEpsIonic()
                                                / 2.0 + 1.0, 3.0);
                return dYdf * dXdf + d1 * d2 + d1 * d3;
        }

        public double calcdiElectricdndn(int j, PhaseInterface phase, int numberOfComponents,
                        double temperature, double pressure) {
                double dYdf = ((ComponentModifiedFurstElectrolyteEos) ((PhaseModifiedFurstElectrolyteEos) phase)
                                .getComponents()[j]).getSolventDiElectricConstantdn();
                double dXdfdfj = getEpsIonici() * -3.0 / 2.0 / Math
                                .pow(((PhaseModifiedFurstElectrolyteEos) phase).getEpsIonic() / 2.0
                                                + 1.0, 2.0);

                double dXdf = ((ComponentModifiedFurstElectrolyteEos) ((PhaseModifiedFurstElectrolyteEos) phase)
                                .getComponents()[j]).getEpsIonici() * getEpsIonici() * 3.0 / 2.0
                                / Math.pow(((PhaseModifiedFurstElectrolyteEos) phase).getEpsIonic()
                                                / 2.0 + 1.0, 3.0);
                double d1 = ((PhaseModifiedFurstElectrolyteEos) phase)
                                .getSolventDiElectricConstant();

                double d2 = ((ComponentModifiedFurstElectrolyteEos) ((PhaseModifiedFurstElectrolyteEos) phase)
                                .getComponents()[j]).getEpsIonici() * -3.0 / 2.0
                                / Math.pow(((PhaseModifiedFurstElectrolyteEos) phase).getEpsIonic()
                                                / 2.0 + 1.0, 2.0);
                double d5 = getSolventDiElectricConstantdn();

                double d3 = calcSolventdiElectricdndn(j, phase, numberOfComponents, temperature,
                                pressure);
                double d4 = (1.0 - ((PhaseModifiedFurstElectrolyteEos) phase).getEpsIonic()) / (1.0
                                + ((PhaseModifiedFurstElectrolyteEos) phase).getEpsIonic() / 2.0);

                return dYdf * dXdfdfj + dXdf * d1 + d2 * d5 + d3 * d4;
        }

        public double calcdiElectricdndT(PhaseInterface phase, int numberOfComponents,
                        double temperature, double pressure) {
                double X = (1.0 - ((PhaseModifiedFurstElectrolyteEos) phase).getEpsIonic()) / (1.0
                                + ((PhaseModifiedFurstElectrolyteEos) phase).getEpsIonic() / 2.0);
                double Y = ((PhaseModifiedFurstElectrolyteEos) phase)
                                .getSolventDiElectricConstantdT();
                double dXdf = getEpsIonici() * -3.0 / 2.0 / Math
                                .pow(((PhaseModifiedFurstElectrolyteEos) phase).getEpsIonic() / 2.0
                                                + 1.0, 2.0);
                double dYdf = solventdiElectricdndT;
                return dYdf * X + Y * dXdf;
        }

        // a little simplified
        public double calcGammaLRdn(PhaseInterface phase, int numberOfComponents,
                        double temperature, double pressure) {
                return 0.0;
                // if(((PhaseModifiedFurstElectrolyteEos) phase).getPhaseType()==1) return 0.0;
                // double temp =
                // Math.pow(getIonicCharge()/(1.0+((PhaseModifiedFurstElectrolyteEos)
                // phase).getShieldingParameter()*getLennardJonesMolecularDiameter()*1e-10),2.0);
                // return 1.0/(8.0*((PhaseModifiedFurstElectrolyteEos)
                // phase).getShieldingParameter() + 4.0 *
                // Math.pow(((PhaseModifiedFurstElectrolyteEos)
                // phase).calcShieldingParameter2(),2.0) * 2.0 *
                // getLennardJonesMolecularDiameter()*1e-10) *
                // ((temp * ((PhaseModifiedFurstElectrolyteEos)
                // phase).getAlphaLR2()/(((PhaseModifiedFurstElectrolyteEos)
                // phase).getMolarVolume()*1e-5*((PhaseModifiedFurstElectrolyteEos)
                // phase).getNumberOfMolesInPhase()) * avagadroNumber)
                // + 4.0 * Math.pow(((PhaseModifiedFurstElectrolyteEos)
                // phase).getShieldingParameter(),2.0) / ((PhaseModifiedFurstElectrolyteEos)
                // phase).getAlphaLR2() * alphai );
                // Math.pow(getIonicCharge()/(1.0+((PhaseModifiedFurstElectrolyteEos)
                // phase).getShieldingParameter()*getLennardJonesMolecularDiameter()*1e-10),2.0)
                // + alphai*temp) /(8.0*((PhaseModifiedFurstElectrolyteEos)
                // phase).getShieldingParameter());
                // + 2.0*getLennardJonesMolecularDiameter()*1e-10 * 4.0 *
                // Math.pow(((PhaseModifiedFurstElectrolyteEos)
                // phase).calcShieldingParameter2(),2.0))*
        }

        // Short Range term equations and derivatives
        public double dFSR2dN(PhaseInterface phase, int numberOfComponents, double temperature,
                        double pressure) {
                return ((PhaseModifiedFurstElectrolyteEos) phase).FSR2eps() * epsi
                                + ((PhaseModifiedFurstElectrolyteEos) phase).FSR2W() * Wi;
        }

        public double dFSR2dNdT(PhaseInterface phase, int numberOfComponents, double temperature,
                        double pressure) {
                return ((PhaseModifiedFurstElectrolyteEos) phase).FSR2W() * WiT
                                + ((PhaseModifiedFurstElectrolyteEos) phase).FSR2epsW() * epsi
                                                * ((PhaseModifiedFurstElectrolyteEos) phase)
                                                                .getWT();
        }

        public double dFSR2dNdV(PhaseInterface phase, int numberOfComponents, double temperature,
                        double pressure) {
                return 1.0e-5 * (+((PhaseModifiedFurstElectrolyteEos) phase).FSR2epseps() * epsi
                                * ((PhaseModifiedFurstElectrolyteEos) phase).getEpsdV()
                                + ((PhaseModifiedFurstElectrolyteEos) phase).FSR2eps() * epsiV
                                + ((PhaseModifiedFurstElectrolyteEos) phase).FSR2epsW() * Wi
                                                * ((PhaseModifiedFurstElectrolyteEos) phase)
                                                                .getEpsdV()
                                + ((PhaseModifiedFurstElectrolyteEos) phase).FSR2epsV() * epsi
                                + ((PhaseModifiedFurstElectrolyteEos) phase).FSR2VW() * Wi);
        }

        public double dFSR2dNdN(int j, PhaseInterface phase, int numberOfComponents,
                        double temperature, double pressure) {
                return ((PhaseModifiedFurstElectrolyteEos) phase).FSR2epseps() * epsi
                                * ((ComponentModifiedFurstElectrolyteEos) ((PhaseModifiedFurstElectrolyteEos) phase)
                                                .getComponents()[j]).getEpsi()
                                + ((PhaseModifiedFurstElectrolyteEos) phase).FSR2epsW() * Wi
                                                * ((ComponentModifiedFurstElectrolyteEos) ((PhaseModifiedFurstElectrolyteEos) phase)
                                                                .getComponents()[j]).getEpsi()
                                + ((PhaseModifiedFurstElectrolyteEos) phase).FSR2W()
                                                * ((PhaseModifiedFurstElectrolyteEos) phase)
                                                                .calcWij(componentNumber, j, phase,
                                                                                temperature,
                                                                                pressure,
                                                                                numberOfComponents)
                                + ((PhaseModifiedFurstElectrolyteEos) phase).FSR2epsW() * epsi
                                                * ((PhaseModifiedFurstElectrolyteEos) phase).calcWi(
                                                                j, phase, temperature, pressure,
                                                                numberOfComponents);
        }

        public double dEpsdNi(PhaseInterface phase, int numberOfComponents, double temperature,
                        double pressure) {
                return avagadroNumber * pi / 6.0
                                * Math.pow(lennardJonesMolecularDiameter * 1.0e-10, 3.0)
                                * (1.0 / (phase.getMolarVolume() * 1.0e-5
                                                * phase.getNumberOfMolesInPhase()));
        }

        public double dEpsdNidV(PhaseInterface phase, int numberOfComponents, double temperature,
                        double pressure) {
                return (-avagadroNumber * pi / 6.0
                                * Math.pow(lennardJonesMolecularDiameter * 1.0e-10, 3.0)
                                * (1.0 / (Math.pow(
                                                phase.getMolarVolume() * 1.0e-5
                                                                * phase.getNumberOfMolesInPhase(),
                                                2.0))));
        }

        public double dEpsIonicdNi(PhaseInterface phase, int numberOfComponents, double temperature,
                        double pressure) {
                if (ionicCharge == 0) {
                        return 0.0;
                } else {
                        return pi / 6.0 * (avagadroNumber
                                        * Math.pow(lennardJonesMolecularDiameter * 1.0e-10, 3.0))
                                        * (1.0 / (phase.getMolarVolume() * 1.0e-5
                                                        * phase.getNumberOfMolesInPhase()));
                }
        }

        public double dEpsIonicdNidV(PhaseInterface phase, int numberOfComponents,
                        double temperature, double pressure) {
                if (ionicCharge == 0) {
                        return 0.0;
                }
                return (-avagadroNumber * pi / 6.0
                                * Math.pow(lennardJonesMolecularDiameter * 1e-10, 3.0) / (Math.pow(
                                                phase.getMolarVolume() * 1e-5
                                                                * phase.getNumberOfMolesInPhase(),
                                                2.0)));
        }

        // Born term equations and derivatives
        public double dFBorndN(PhaseInterface phase, int numberOfComponents, double temperature,
                        double pressure) {
                return ((PhaseModifiedFurstElectrolyteEos) phase).FBornX() * getXBorni()
                                + ((PhaseModifiedFurstElectrolyteEos) phase).FBornD()
                                                * solventdiElectricdn;
        }

        public double dFBorndNdT(PhaseInterface phase, int numberOfComponents, double temperature,
                        double pressure) {
                return (((PhaseModifiedFurstElectrolyteEos) phase).FBornTX() * XBorni
                                + ((PhaseModifiedFurstElectrolyteEos) phase).FBornDX() * XBorni
                                                * ((PhaseModifiedFurstElectrolyteEos) phase)
                                                                .getSolventDiElectricConstantdT())
                                + ((PhaseModifiedFurstElectrolyteEos) phase).FBornTD()
                                                * getSolventDiElectricConstantdn()
                                + ((PhaseModifiedFurstElectrolyteEos) phase).FBornD()
                                                * solventdiElectricdndT
                                + ((PhaseModifiedFurstElectrolyteEos) phase).FBornDD()
                                                * getSolventDiElectricConstantdn()
                                                * ((PhaseModifiedFurstElectrolyteEos) phase)
                                                                .getSolventDiElectricConstantdT();
        }

        public double dFBorndNdN(int j, PhaseInterface phase, int numberOfComponents,
                        double temperature, double pressure) {
                return ((PhaseModifiedFurstElectrolyteEos) phase).FBornDD() * solventdiElectricdn
                                * ((ComponentModifiedFurstElectrolyteEos) ((PhaseModifiedFurstElectrolyteEos) phase)
                                                .getComponents()[j])
                                                                .getSolventDiElectricConstantdn()
                                + ((PhaseModifiedFurstElectrolyteEos) phase).FBornDX() * XBorni
                                                * ((ComponentModifiedFurstElectrolyteEos) ((PhaseModifiedFurstElectrolyteEos) phase)
                                                                .getComponents()[j])
                                                                                .getSolventDiElectricConstantdn()
                                + ((PhaseModifiedFurstElectrolyteEos) phase).FBornDX()
                                                * solventdiElectricdn
                                                * ((ComponentModifiedFurstElectrolyteEos) ((PhaseModifiedFurstElectrolyteEos) phase)
                                                                .getComponents()[j]).getXBorni()
                                + ((PhaseModifiedFurstElectrolyteEos) phase).FBornD()
                                                * calcSolventdiElectricdndn(j, phase,
                                                                numberOfComponents, temperature,
                                                                pressure);
        }

        public double getIonicCoVolume() {
                return ionicCoVolume;
        }

        public double getDiElectricConstantdn() {
                return diElectricdn;
        }

        public double getSolventDiElectricConstantdn() {
                return solventdiElectricdn;
        }

        public double getBornVal() {
                return bornterm;
        }

        public double getEpsi() {
                return epsi;
        }

        public double getEpsIonici() {
                return epsIonici;
        }

        public double getAlphai() {
                return alphai;
        }

        public double getXLRi() {
                return XLRi;
        }

        public double getXBorni() {
                return XBorni;
        }
}
