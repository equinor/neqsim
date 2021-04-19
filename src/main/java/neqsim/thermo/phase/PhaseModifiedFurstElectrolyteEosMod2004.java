/*
 * PhaseModifiedFurstElectrolyteEosMod2004.java
 *
 * Created on 26. februar 2001, 17:54
 */

package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentModifiedFurstElectrolyteEos;
import org.apache.logging.log4j.*;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class PhaseModifiedFurstElectrolyteEosMod2004 extends PhaseSrkEos
        implements neqsim.thermo.ThermodynamicConstantsInterface {

    private static final long serialVersionUID = 1000;
    double gammaold = 0, alphaLRdTdV = 0;
    double W = 0, WT = 0, WTT = 0, eps = 0, epsdV = 0, epsdVdV = 0, epsIonic = 0, bornX = 0, epsIonicdV = 0,
            epsIonicdVdV = 0, alphaLR2 = 0, alphaLRdT = 0.0, alphaLRdTdT = 0.0, alphaLRdV = 0.0, XLR = 0,
            solventDiElectricConstant = 0, solventDiElectricConstantdT = 0.0, solventDiElectricConstantdTdT = 0,
            shieldingParameter = 0;
    double gamma = 0, diElectricConstantdV = 0, diElectricConstantdVdV = 0, alphaLRdVdV = 0, diElectricConstantdT = 0,
            diElectricConstantdTdT = 0.0, diElectricConstantdTdV = 0;
    neqsim.thermo.mixingRule.ElectrolyteMixingRulesInterface electrolyteMixingRule;
    double sr2On = 1.0, lrOn = 1.0, bornOn = 1.0;
    static Logger logger = LogManager.getLogger(PhaseModifiedFurstElectrolyteEosMod2004.class);
    // double gammLRdV=0.0;
    // PhaseInterface[] refPhase;// = new PhaseInterface[10];

    /** Creates new PhaseModifiedFurstElectrolyteEosMod2004 */

    public PhaseModifiedFurstElectrolyteEosMod2004() {
        super();
        electrolyteMixingRule = mixSelect.getElectrolyteMixingRule(this);
    }

    public neqsim.thermo.mixingRule.ElectrolyteMixingRulesInterface getElectrolyteMixingRule() {
        return electrolyteMixingRule;
    }

    public void reInitFurstParam() {
        for (int k = 0; k < numberOfComponents; k++) {
            ((ComponentModifiedFurstElectrolyteEos) componentArray[k]).initFurstParam();
        }
        electrolyteMixingRule = mixSelect.getElectrolyteMixingRule(this);
    }

    @Override
	public Object clone() {
        PhaseModifiedFurstElectrolyteEosMod2004 clonedPhase = null;
        try {
            clonedPhase = (PhaseModifiedFurstElectrolyteEosMod2004) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }
        // clonedPhase.electrolyteMixingRule =
        // (thermo.mixingRule.ElectrolyteMixingRulesInterface)
        // electrolyteMixingRule.clone();

        return clonedPhase;
    }

    @Override
	public void init(double totalNumberOfMoles, int numberOfComponents, int type, int phase, double beta) { // type = 0
                                                                                                            // start
                                                                                                            // init type
                                                                                                            // =1 gi nye
                                                                                                            // betingelser
        super.init(totalNumberOfMoles, numberOfComponents, type, phase, beta);
        if (type == 0) {
            electrolyteMixingRule = mixSelect.getElectrolyteMixingRule(this);
        }
    }

    public void volInit() {
        W = electrolyteMixingRule.calcW(this, temperature, pressure, numberOfComponents);
        WT = electrolyteMixingRule.calcWT(this, temperature, pressure, numberOfComponents);
        WTT = electrolyteMixingRule.calcWTT(this, temperature, pressure, numberOfComponents);
        eps = calcEps();
        epsdV = calcEpsV();
        epsdVdV = calcEpsVV();
        epsIonic = calcEpsIonic();
        epsIonicdV = calcEpsIonicdV();
        epsIonicdVdV = calcEpsIonicdVdV();
        solventDiElectricConstant = calcSolventDiElectricConstant(temperature);
        solventDiElectricConstantdT = 0 * calcSolventDiElectricConstantdT(temperature);
        solventDiElectricConstantdTdT = 0 * calcSolventDiElectricConstantdTdT(temperature);
        diElectricConstant = calcDiElectricConstant(temperature);
        diElectricConstantdT = calcDiElectricConstantdT(temperature);
        diElectricConstantdTdT = calcDiElectricConstantdTdT(temperature);
        diElectricConstantdV = calcDiElectricConstantdV(temperature);
        diElectricConstantdVdV = calcDiElectricConstantdVdV(temperature);
        diElectricConstantdTdV = calcDiElectricConstantdTdV(temperature);
        alphaLR2 = electronCharge * electronCharge * avagadroNumber
                / (vacumPermittivity * diElectricConstant * R * temperature);
        alphaLRdT = -electronCharge * electronCharge * avagadroNumber
                / (vacumPermittivity * diElectricConstant * R * temperature * temperature)
                - electronCharge * electronCharge * avagadroNumber
                        / (vacumPermittivity * diElectricConstant * diElectricConstant * R * temperature)
                        * diElectricConstantdT;
        alphaLRdV = -electronCharge * electronCharge * avagadroNumber
                / (vacumPermittivity * diElectricConstant * diElectricConstant * R * temperature)
                * diElectricConstantdV;
        alphaLRdTdT = 2.0 * electronCharge * electronCharge * avagadroNumber
                / (vacumPermittivity * diElectricConstant * R * Math.pow(temperature, 3.0))
                + electronCharge * electronCharge * avagadroNumber
                        / (vacumPermittivity * diElectricConstant * diElectricConstant * R * temperature * temperature)
                        * diElectricConstantdT
                - electronCharge * electronCharge * avagadroNumber
                        / (vacumPermittivity * diElectricConstant * diElectricConstant * R * temperature)
                        * diElectricConstantdTdT
                + electronCharge * electronCharge * avagadroNumber
                        / (vacumPermittivity * diElectricConstant * diElectricConstant * R * temperature * temperature)
                        * diElectricConstantdT
                + 2.0 * electronCharge * electronCharge * avagadroNumber
                        / (vacumPermittivity * Math.pow(diElectricConstant, 3.0) * R * temperature)
                        * Math.pow(diElectricConstantdT, 2.0);
        alphaLRdTdV = electronCharge * electronCharge * avagadroNumber
                / (vacumPermittivity * diElectricConstant * diElectricConstant * R * temperature * temperature)
                * diElectricConstantdV
                + 2.0 * electronCharge * electronCharge * avagadroNumber
                        / (vacumPermittivity * diElectricConstant * diElectricConstant * diElectricConstant * R
                                * temperature)
                        * diElectricConstantdT * diElectricConstantdV
                - electronCharge * electronCharge * avagadroNumber
                        / (vacumPermittivity * diElectricConstant * diElectricConstant * R * temperature)
                        * diElectricConstantdTdV;
        alphaLRdVdV = -electronCharge * electronCharge * avagadroNumber
                / (vacumPermittivity * diElectricConstant * diElectricConstant * R * temperature)
                * diElectricConstantdVdV
                + 2.0 * electronCharge * electronCharge * avagadroNumber
                        / (vacumPermittivity * Math.pow(diElectricConstant, 3.0) * R * temperature)
                        * diElectricConstantdV * diElectricConstantdV;
        shieldingParameter = calcShieldingParameter();
        // gammLRdV = calcGammaLRdV();
        XLR = calcXLR();
        bornX = calcBornX();
    }

    @Override
	public void addcomponent(String componentName, double moles, double molesInPhase, int compNumber) {
        super.addcomponent(molesInPhase);
        componentArray[compNumber] = new neqsim.thermo.component.ComponentModifiedFurstElectrolyteEosMod2004(
                componentName, moles, molesInPhase, compNumber);
    }

    public double calcSolventDiElectricConstant(double temperature) {
        double ans1 = 0.0, ans2 = 1e-50;
        for (int i = 0; i < numberOfComponents; i++) {
            if (componentArray[i].getIonicCharge() == 0) {
                ans1 += componentArray[i].getNumberOfMolesInPhase()
                        * componentArray[i].getDiElectricConstant(temperature);
                ans2 += componentArray[i].getNumberOfMolesInPhase();
            }
        }
        return ans1 / ans2;
    }

    public double calcSolventDiElectricConstantdT(double temperature) {
        double ans1 = 0.0, ans2 = 1e-50;
        for (int i = 0; i < numberOfComponents; i++) {
            if (componentArray[i].getIonicCharge() == 0) {
                ans1 += componentArray[i].getNumberOfMolesInPhase()
                        * componentArray[i].getDiElectricConstantdT(temperature);
                ans2 += componentArray[i].getNumberOfMolesInPhase();
            }
        }
        return 0 * ans1 / ans2;
    }

    public double calcSolventDiElectricConstantdTdT(double temperature) {
        double ans1 = 0.0, ans2 = 1e-50;
        for (int i = 0; i < numberOfComponents; i++) {
            if (componentArray[i].getIonicCharge() == 0) {
                ans1 += componentArray[i].getNumberOfMolesInPhase()
                        * componentArray[i].getDiElectricConstantdTdT(temperature);
                ans2 += componentArray[i].getNumberOfMolesInPhase();
            }
        }
        return 0 * ans1 / ans2;
    }

    public double calcEps() {
        double eps = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            eps += avagadroNumber * pi / 6.0 * componentArray[i].getNumberOfMolesInPhase()
                    * Math.pow(componentArray[i].getLennardJonesMolecularDiameter() * 1e-10, 3.0) * 1.0
                    / (numberOfMolesInPhase * getMolarVolume() * 1e-5);
        }
        return eps;
    }

    public double calcEpsV() {
        return -getEps() / (getMolarVolume() * 1e-5 * numberOfMolesInPhase);
    }

    public double calcEpsVV() {
        return 2.0 * getEps() / Math.pow(getMolarVolume() * 1e-5 * numberOfMolesInPhase, 2.0);
    }

    public double calcEpsIonic() {
        double epsIonicLoc = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            if (componentArray[i].getIonicCharge() != 0) {
                epsIonicLoc += avagadroNumber * pi / 6.0 * componentArray[i].getNumberOfMolesInPhase()
                        * Math.pow(componentArray[i].getLennardJonesMolecularDiameter() * 1e-10, 3.0)
                        / (numberOfMolesInPhase * getMolarVolume() * 1e-5);
            }
        }
        return epsIonicLoc;
    }

    public double calcEpsIonicdV() {
        return -getEpsIonic() / (getMolarVolume() * 1e-5 * numberOfMolesInPhase);
    }

    public double calcEpsIonicdVdV() {
        return 2.0 * getEpsIonic() / Math.pow(getMolarVolume() * 1e-5 * numberOfMolesInPhase, 2.0);
    }

    @Override
	public double getF() {
        return super.getF() + FSR2() * sr2On + FLR() * lrOn + FBorn() * bornOn;
    }

    @Override
	public double dFdT() {
        return super.dFdT() + dFSR2dT() * sr2On + dFLRdT() * lrOn + dFBorndT() * bornOn;
    }

    @Override
	public double dFdTdV() {
        return super.dFdTdV() + dFSR2dTdV() * sr2On + dFLRdTdV() * lrOn;
    }

    @Override
	public double dFdV() {
        return super.dFdV() + dFSR2dV() * sr2On + dFLRdV() * lrOn;
    }

    @Override
	public double dFdVdV() {
        return super.dFdVdV() + dFSR2dVdV() * sr2On + dFLRdVdV() * lrOn;
    }

    @Override
	public double dFdVdVdV() {
        return super.dFdVdVdV() + dFSR2dVdVdV() * sr2On + dFLRdVdVdV() * lrOn;
    }

    @Override
	public double dFdTdT() {
        return super.dFdTdT() + dFSR2dTdT() * sr2On + dFLRdTdT() * lrOn + dFBorndTdT() * bornOn;
    }

    public double calcXLR() {
        double ans = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            if (componentArray[i].getIonicCharge() != 0) {
                ans += componentArray[i].getNumberOfMolesInPhase() * Math.pow(componentArray[i].getIonicCharge(), 2.0)
                        * getShieldingParameter() / (1.0 + getShieldingParameter()
                                * componentArray[i].getLennardJonesMolecularDiameter() * 1e-10);
            }
        }
        return ans;
    }

    public double calcGammaLRdV() {
        if (phaseType == 1) {
            return 0.0;
        }
        // return 0.0; // problem ved ren komponent
        return 1.0 / (8.0 * getShieldingParameter())
                * (4.0 * Math.pow(getShieldingParameter(), 2.0) / getAlphaLR2() * alphaLRdV + 4.0
                        * Math.pow(getShieldingParameter(), 2.0) / (numberOfMolesInPhase * getMolarVolume() * 1e-5));
        // // Math.pow(getIonicCharge()/(1.0+((PhaseModifiedFurstElectrolyteEosMod2004)
        // phase).getShieldingParameter()*getLennardJonesMolecularDiameter()*1e-10),2.0)
        // + alphai*temp) /(8.0*((PhaseModifiedFurstElectrolyteEosMod2004)
        // phase).getShieldingParameter());
    }

    public double calcShieldingParameter() {
        // if(phaseType==1) return 0.0;
        double df = 0, f = 0;
        int ions = 0;
        int iterations = 0;
        gamma = 1e10;
        do {
            iterations++;
            gammaold = gamma;
            ions = 0;
            f = 4.0 * Math.pow(gamma, 2) / avagadroNumber;
            df = 8.0 * gamma / avagadroNumber;
            for (int i = 0; i < numberOfComponents; i++) {
                if (componentArray[i].getIonicCharge() != 0) {
                    ions++;
                    f += -getAlphaLR2() * componentArray[i].getNumberOfMolesInPhase()
                            / (getMolarVolume() * numberOfMolesInPhase * 1e-5)
                            * Math.pow(componentArray[i].getIonicCharge()
                                    / (1.0 + gamma * componentArray[i].getLennardJonesMolecularDiameter() * 1e-10),
                                    2.0);
                    df += 2.0 * getAlphaLR2() * componentArray[i].getNumberOfMolesInPhase()
                            / (getMolarVolume() * numberOfMolesInPhase * 1e-5)
                            * Math.pow(componentArray[i].getIonicCharge(), 2.0)
                            * (componentArray[i].getLennardJonesMolecularDiameter() * 1e-10)
                            / (Math.pow(1.0 + gamma * componentArray[i].getLennardJonesMolecularDiameter() * 1e-10,
                                    3.0));
                }
            }
            gamma = ions > 0 ? gammaold - 0.8 * f / df : 0;
        } while ((Math.abs(f) > 1e-10 && iterations < 1000) || iterations < 3);
        // gamma = 1e9;
        // System.out.println("gamma " +gamma + " iterations " + iterations);
        return gamma;
    }

    // public double calcShieldingParameter2(){
    // if(phaseType==1) return 0.0;
    //
    // double df=0, f=0;
    // int ions=0;
    // int iterations=0;
    // gamma=1e10;
    // do{
    // iterations++;
    // gammaold = gamma;
    // ions=0;
    // f = 4.0*Math.pow(gamma,2)/avagadroNumber;
    // df = 8.0*gamma/avagadroNumber;
    // for(int i=0;i<numberOfComponents;i++){
    // if(componentArray[i].getIonicCharge()!=0){
    // ions++;
    // f += -
    // getAlphaLR2()*componentArray[i].getNumberOfMolesInPhase()/(molarVolume*numberOfMolesInPhase*1e-5)*Math.pow(componentArray[i].getIonicCharge(),2.0)/Math.pow((1.0+gamma*componentArray[i].getLennardJonesMolecularDiameter()*1e-10),3.0);
    // df +=
    // getAlphaLR2()*componentArray[i].getNumberOfMolesInPhase()/(molarVolume*numberOfMolesInPhase*1e-5)*Math.pow(componentArray[i].getIonicCharge(),2.0)*(componentArray[i].getLennardJonesMolecularDiameter()*1e-10)/Math.pow(1.0+gamma*componentArray[i].getLennardJonesMolecularDiameter()*1e-10,4.0);
    // }
    // }
    // gamma = ions>0 ? gammaold - 0.8*f/df : 0;
    // }
    // while((Math.abs(f)>1e-10 && iterations<1000) || iterations<5);
    // //System.out.println("gama " + gamma*1e-10);
    // return gamma;
    // }

    @Override
	public double molarVolume(double pressure, double temperature, double A, double B, int phase)
            throws neqsim.util.exception.IsNaNException, neqsim.util.exception.TooManyIterationsException {

        // double BonV = phase== 0 ?
        // 2.0/(2.0+temperature/getPseudoCriticalTemperature()):0.1*pressure*getB()/(numberOfMolesInPhase*temperature*R);
        double BonV = phase == 0 ? 0.99 : 1e-5;

        if (BonV < 0) {
            BonV = 1.0e-6;
        }
        if (BonV > 1.0) {
            BonV = 1.0 - 1.0e-6;
        }
        double BonVold = BonV;
        double Btemp = 0, Dtemp = 0, h = 0, dh = 0, gvvv = 0, fvvv = 0, dhh = 0;
        double d1 = 0, d2 = 0;
        Btemp = getB();
        Dtemp = getA();
        if (Btemp <= 0) {
            logger.info("b negative in volume calc");
        }
        setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
        int iterations = 0;

        do {
            iterations++;
            this.volInit();
            BonVold = BonV;
            h = BonV - Btemp / numberOfMolesInPhase * dFdV()
                    - pressure * Btemp / (numberOfMolesInPhase * R * temperature);
            dh = 1.0 + Btemp / Math.pow(BonV, 2.0) * (Btemp / numberOfMolesInPhase * dFdVdV());
            dhh = -2.0 * Btemp / Math.pow(BonV, 3.0) * (Btemp / numberOfMolesInPhase * dFdVdV())
                    - Math.pow(Btemp, 2.0) / Math.pow(BonV, 4.0) * (Btemp / numberOfMolesInPhase * dFdVdVdV());

            d1 = -h / dh;
            d2 = -dh / dhh;

            if (Math.abs(d1 / d2) <= 1.0) {
                BonV += d1 * (1.0 + 0.5 * d1 / d2);
            } else if (d1 / d2 < -1) {
                BonV += d1 * (1.0 + 0.5 * -1.0);
            } else if (d1 / d2 > 1) {
                BonV += d2;
                double hnew = h + d2 * -h / d1;
                if (Math.abs(hnew) > Math.abs(h)) {
                    logger.info("volume correction needed....");
                    BonV = phase == 1 ? 2.0 / (2.0 + temperature / getPseudoCriticalTemperature())
                            : pressure * getB() / (numberOfMolesInPhase * temperature * R);
                }
            }

            if (BonV > 1) {
                BonV = 1.0 - 1.0e-6;
                BonVold = 10;
            }
            if (BonV < 0) {
                BonV = 1.0e-6;
                BonVold = 10;
            }

            setMolarVolume(1.0 / BonV * Btemp / numberOfMolesInPhase);
            Z = pressure * getMolarVolume() / (R * temperature);
        } while (Math.abs((BonV - BonVold) / BonV) > 1.0e-10 && iterations < 1000);
        this.volInit();
        if (iterations >= 10000) {
            throw new neqsim.util.exception.TooManyIterationsException();
        }
        if (Double.isNaN(getMolarVolume())) {
            throw new neqsim.util.exception.IsNaNException();
        }

        // if(phaseType==0) System.out.println("density " + getDensity());//"BonV: " +
        // BonV + " "+" itert: " + iterations +" " + " phase " + phaseType+ " " + h + "
        // " +dh + " B " + Btemp + " D " + Dtemp + " gv" + gV() + " fv " + fv() + " fvv"
        // + fVV());

        return getMolarVolume();
    }

    public double calcW(PhaseInterface phase, double temperature, double pressure, int numbcomp) {
        W = electrolyteMixingRule.calcW(phase, temperature, pressure, numbcomp);
        return W;
    }

    public double calcWi(int compNumb, PhaseInterface phase, double temperature, double pressure, int numbcomp) {
        return electrolyteMixingRule.calcWi(compNumb, phase, temperature, pressure, numbcomp);
    }

    public double calcWiT(int compNumb, PhaseInterface phase, double temperature, double pressure, int numbcomp) {
        return electrolyteMixingRule.calcWiT(compNumb, phase, temperature, pressure, numbcomp);
    }

    public double calcWij(int compNumb, int compNumbj, PhaseInterface phase, double temperature, double pressure,
            int numbcomp) {
        return electrolyteMixingRule.calcWij(compNumb, compNumbj, phase, temperature, pressure, numbcomp);
    }

    @Override
	public double calcDiElectricConstant(double temperature) {
        return 1.0 + (getSolventDiElectricConstant() - 1.0) * (1.0 - getEpsIonic()) / (1.0 + getEpsIonic() / 2.0);
    }

    public double calcDiElectricConstantdV(double temperature) {
        double X = (1.0 - getEpsIonic()) / (1.0 + getEpsIonic() / 2.0);
        // double Y= getSolventDiElectricConstant(); 10-2002
        double Y = getSolventDiElectricConstant() - 1.0;
        double dXdf = getEpsIonicdV() * -3.0 / 2.0 / Math.pow(getEpsIonic() / 2.0 + 1.0, 2.0);
        double dYdf = 0;
        return dYdf * X + Y * dXdf;
    }

    public double calcDiElectricConstantdVdV(double temperature) {
        double Y = getSolventDiElectricConstant() - 1.0;
        double dXdf = getEpsIonicdVdV() * -3.0 / 2.0 / Math.pow(getEpsIonic() / 2.0 + 1.0, 2.0)
                + getEpsIonicdV() * getEpsIonicdV() * 3.0 / 2.0 / Math.pow(getEpsIonic() / 2.0 + 1.0, 3.0);
        return Y * dXdf;// + Y*dXdf;
    }

    @Override
	public double calcDiElectricConstantdT(double temperature) {
        double X = (1.0 - getEpsIonic()) / (1.0 + getEpsIonic() / 2.0);
        double Y = getSolventDiElectricConstant() - 1.0;
        double dXdf = 0;
        double dYdf = getSolventDiElectricConstantdT();
        return dYdf * X + Y * dXdf;
    }

    @Override
	public double calcDiElectricConstantdTdT(double temperature) {
        return getSolventDiElectricConstantdTdT() * (1.0 - epsIonic) / (1.0 + epsIonic / 2.0);
    }

    public double calcDiElectricConstantdTdV(double temperature) {
        double Y = getSolventDiElectricConstantdT();
        double dXdf = getEpsIonicdV() * -3.0 / 2.0 / Math.pow(getEpsIonic() / 2.0 + 1.0, 2.0);
        return Y * dXdf;
    }

    public double calcBornX() {
        double ans = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            ans += componentArray[i].getNumberOfMolesInPhase() * Math.pow(componentArray[i].getIonicCharge(), 2.0)
                    / (componentArray[i].getLennardJonesMolecularDiameter() * 1e-10);
        }
        return ans;
    }

    // Long Range term equations and derivatives

    public double FLR() {
        double ans = 0.0;
        ans -= (1.0 / (4.0 * pi) * getAlphaLR2() * getXLR());
        return ans + (numberOfMolesInPhase * getMolarVolume() * 1e-5 * Math.pow(getShieldingParameter(), 3.0))
                / (3.0 * pi * avagadroNumber);
    }

    public double dFLRdT() {
        return dFdAlphaLR() * alphaLRdT;
    }

    public double dFLRdTdV() {
        return (dFdAlphaLR() * alphaLRdTdV) * 1e-5;
    }

    public double dFLRdTdT() {
        return dFdAlphaLR() * alphaLRdTdT;
    }

    public double dFLRdV() {
        return (FLRV() + dFdAlphaLR() * alphaLRdV) * 1e-5;// + FLRGammaLR()*gammLRdV +
                                                          // 0*FLRXLR()*XLRdGammaLR()*gammLRdV)*1e-5;
    }

    public double dFLRdVdV() {
        return (dFdAlphaLR() * alphaLRdVdV) * 1e-10;
    }

    public double dFLRdVdVdV() {
        return 0.0;
    }

    // first order derivatives

    public double FLRXLR() {
        return -getAlphaLR2() / (4.0 * pi);
    }

    public double FLRGammaLR() {
        return 3.0 * numberOfMolesInPhase * getMolarVolume() * 1e-5 * Math.pow(getShieldingParameter(), 2.0)
                / (3.0 * pi * avagadroNumber);
    }

    public double dFdAlphaLR() {
        return -1.0 / (4.0 * pi) * XLR;
    }

    public double dFdAlphaLRdV() {
        return 0.0;
    }

    public double dFdAlphaLRdX() {
        return -1.0 / (4.0 * pi);
    }

    public double dFdAlphaLRdGamma() {
        return 0;
    }

    public double FLRV() {
        return Math.pow(getShieldingParameter(), 3.0) / (3.0 * pi * avagadroNumber);
    }

    public double FLRVV() {
        return 0.0;
    }

    // second order derivatives

    public double dFdAlphaLRdAlphaLR() {
        return 0.0;
    }

    public double XLRdndn(int i, int j) {
        return 0.0;
    }

    public double XLRdGammaLR() {
        // if(phaseType==1) return 0.0;
        double ans = 0.0;
        double ans2 = 0.0;
        for (int i = 0; i < numberOfComponents; i++) {
            ans -= componentArray[i].getLennardJonesMolecularDiameter() * 1e-10
                    * componentArray[i].getNumberOfMolesInPhase() * Math.pow(componentArray[i].getIonicCharge(), 2.0)
                    * getShieldingParameter()
                    / Math.pow(1.0
                            + getShieldingParameter() * componentArray[i].getLennardJonesMolecularDiameter() * 1e-10,
                            2.0);
            ans2 += componentArray[i].getNumberOfMolesInPhase() * Math.pow(componentArray[i].getIonicCharge(), 2.0)
                    / (1.0 + getShieldingParameter() * componentArray[i].getLennardJonesMolecularDiameter() * 1e-10);

        }
        return ans2 + ans;
    }

    public double XBorndndn(int i, int j) {
        return 0.0;
    }

    // Short Range term equations and derivatives
    public double FSR2() {
        return getW() / (getMolarVolume() * 1e-5 * numberOfMolesInPhase * (1.0 - eps));
    }

    public double dFSR2dT() {
        return FSR2W() * WT;
    }

    public double dFSR2dTdT() {
        return FSR2W() * WTT;
    }

    public double dFSR2dV() {
        return (FSR2V() + FSR2eps() * getEpsdV()) * 1e-5;
    }

    public double dFSR2dTdV() {
        return (FSR2VW() * WT + FSR2epsW() * epsdV * WT) * 1e-5;
    }

    public double dFSR2dVdV() {
        return (FSR2VV() + 2.0 * FSR2epsV() * getEpsdV() + FSR2epseps() * Math.pow(getEpsdV(), 2.0)
                + FSR2eps() * getEpsdVdV()) * 1e-10;
    }

    public double dFSR2dVdVdV() {
        return (FSR2VVV() + 3 * FSR2epsepsV() * Math.pow(getEpsdV(), 2.0) + 3 * FSR2VVeps() * getEpsdV()
                + FSR2epsepseps() * Math.pow(getEpsdV(), 3.0)) * 1e-15;
    }

    // first order derivatives
    public double FSR2W() {
        return 1.0 / (getMolarVolume() * 1e-5 * numberOfMolesInPhase * (1.0 - eps));
    }

    public double FSR2V() {
        return -W / (Math.pow(getMolarVolume() * 1e-5 * numberOfMolesInPhase, 2.0) * (1.0 - eps));
    }

    public double FSR2T() {
        return 0.0;
    }

    public double FSR2n() {
        return 0;
    }

    public double FSR2eps() {
        return W / ((getMolarVolume() * 1e-5 * numberOfMolesInPhase) * Math.pow(1.0 - eps, 2.0));
    }

    // second order derivatives

    public double FSR2nn() {
        return 0;
    }

    public double FSR2nT() {
        return 0;
    }

    public double FSR2nV() {
        return 0;
    }

    public double FSR2neps() {
        return 0;
    }

    public double FSR2nW() {
        return 0;
    }

    public double FSR2Tn() {
        return 0;
    }

    public double FSR2TT() {
        return 0;
    }

    public double FSR2TV() {
        return 0;
    }

    public double FSR2Teps() {
        return 0;
    }

    public double FSR2TW() {
        return 0;
    }

    public double FSR2VV() {
        return 2.0 * W / (Math.pow(getMolarVolume() * 1e-5 * numberOfMolesInPhase, 3.0) * (1.0 - eps));
    }

    public double FSR2epsV() {
        return -W / (Math.pow(getMolarVolume() * 1e-5 * numberOfMolesInPhase, 2.0) * Math.pow((1.0 - eps), 2.0));
    }

    public double FSR2epsW() {
        return 1.0 / (getMolarVolume() * 1e-5 * numberOfMolesInPhase * Math.pow(1.0 - eps, 2.0));
    }

    public double FSR2WW() {
        return 0.0;
    }

    public double FSR2VW() {
        return -1.0 / (Math.pow(getMolarVolume() * 1e-5 * numberOfMolesInPhase, 2.0) * (1.0 - eps));
    }

    public double FSR2epseps() {
        return 2.0 * W / ((getMolarVolume() * 1e-5 * numberOfMolesInPhase) * Math.pow(1.0 - eps, 3.0));
    }

    public double FSR2VVV() {
        return -6.0 * W / (Math.pow(getMolarVolume() * 1e-5 * numberOfMolesInPhase, 4.0) * (1.0 - eps));
    }

    // third order derivatives
    public double FSR2epsepsV() {
        return -2.0 * W / (Math.pow(getMolarVolume() * 1e-5 * numberOfMolesInPhase, 2.0) * Math.pow((1 - eps), 3.0));
    }

    public double FSR2VVeps() {
        return 2.0 * W / (Math.pow(getMolarVolume() * 1e-5 * numberOfMolesInPhase, 3.0) * Math.pow((1 - eps), 2.0));
    }

    public double FSR2epsepseps() {
        return 6.0 * W / ((getMolarVolume() * 1e-5 * numberOfMolesInPhase) * Math.pow(1.0 - eps, 4.0));
    }

    // Born term equations and derivatives
    public double FBorn() {
        return (avagadroNumber * electronCharge * electronCharge / (4.0 * pi * vacumPermittivity * R * temperature))
                * (1.0 / getSolventDiElectricConstant() - 1.0) * bornX;
    }

    public double dFBorndT() {
        return FBornT();
    }

    public double dFBorndTdT() {
        return FBornTT();
    }

    // first order derivatives
    public double FBornT() {
        return -(avagadroNumber * electronCharge * electronCharge
                / (4.0 * pi * vacumPermittivity * R * temperature * temperature))
                * (1.0 / getSolventDiElectricConstant() - 1.0) * bornX;
    }

    public double FBornX() {
        return (avagadroNumber * electronCharge * electronCharge / (4.0 * pi * vacumPermittivity * R * temperature))
                * (1.0 / getSolventDiElectricConstant() - 1.0);
    }

    public double FBornD() {
        return -(avagadroNumber * electronCharge * electronCharge / (4.0 * pi * vacumPermittivity * R * temperature))
                * 1.0 / Math.pow(getSolventDiElectricConstant(), 2.0) * bornX;
    }

    // second order derivatives

    public double FBornTT() {
        return 2.0
                * (avagadroNumber * electronCharge * electronCharge
                        / (4.0 * pi * vacumPermittivity * R * temperature * temperature * temperature))
                * (1.0 / getSolventDiElectricConstant() - 1.0) * bornX;
    }

    public double FBornTD() {
        return (avagadroNumber * electronCharge * electronCharge
                / (4.0 * pi * vacumPermittivity * R * temperature * temperature)) * 1.0
                / Math.pow(getSolventDiElectricConstant(), 2.0) * bornX;
    }

    public double FBornTX() {
        return -(avagadroNumber * electronCharge * electronCharge
                / (4.0 * pi * vacumPermittivity * R * temperature * temperature))
                * (1.0 / getSolventDiElectricConstant() - 1.0);
    }

    public double FBornDD() {
        return 2.0
                * (avagadroNumber * electronCharge * electronCharge / (4.0 * pi * vacumPermittivity * R * temperature))
                * 1.0 / Math.pow(getSolventDiElectricConstant(), 3.0) * bornX;
    }

    public double FBornDX() {
        return -(avagadroNumber * electronCharge * electronCharge / (4.0 * pi * vacumPermittivity * R * temperature))
                * 1.0 / Math.pow(getSolventDiElectricConstant(), 2.0);
    }

    public double FBornXX() {
        return 0.0;
    }

    public double getEps() {
        return eps;
    }

    public double getEpsIonic() {
        return epsIonic;
    }

    public double getEpsIonicdV() {
        return epsIonicdV;
    }

    public double getEpsdV() {
        return epsdV;
    }

    public double getEpsdVdV() {
        return epsdVdV;
    }

    public double getSolventDiElectricConstant() {
        return solventDiElectricConstant;
    }

    public double getSolventDiElectricConstantdT() {
        return solventDiElectricConstantdT;
    }

    public double getSolventDiElectricConstantdTdT() {
        return solventDiElectricConstantdTdT;
    }

    public double getAlphaLR2() {
        return alphaLR2;
    }

    public double getW() {
        return W;
    }

    public double getWT() {
        return WT;
    }

    public double getDiElectricConstantdT() {
        return diElectricConstantdT;
    }

    public double getDiElectricConstantdV() {
        return diElectricConstantdV;
    }

    public double getXLR() {
        return XLR;
    }

    public double getShieldingParameter() {
        return shieldingParameter;
    }

    public double getAlphaLRT() {
        return alphaLRdT;
    }

    public double getAlphaLRV() {
        return alphaLRdV;
    }

    public double getDielectricT() {
        return diElectricConstantdT;
    }

    public double getDielectricV() {
        return diElectricConstantdV;
    }

    public double getDielectricConstant() {
        return diElectricConstant;
    }

    public void setFurstIonicCoefficient(double[] params) {
    }

    public double getEpsIonicdVdV() {
        return epsIonicdVdV;
    }

}
