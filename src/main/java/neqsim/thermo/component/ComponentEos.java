/*
 * ComponentEos.java
 *
 * Created on 14. mai 2000, 21:27
 */
package neqsim.thermo.component;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermCPAstatoil;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermGERG;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermInterface;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermMatCop;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermMatCopPR;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermMatCopPRUMR;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermMollerup;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermPr;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermPr1978;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermPrDanesh;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermPrDelft1998;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermPrGassem2001;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermRk;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermSchwartzentruber;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermSrk;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermTwu;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermTwuCoon;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermTwuCoonParam;
import neqsim.thermo.component.atractiveEosTerm.AtractiveTermUMRPRU;
import neqsim.thermo.component.atractiveEosTerm.AttractiveTermTwuCoonStatoil;
import neqsim.thermo.phase.PhaseInterface;

/**
 * @author Even Solbraa
 * @version
 */
abstract class ComponentEos extends Component implements ComponentEosInterface {
    private static final long serialVersionUID = 1000;

    /**
     * Creates new ComponentEos
     */
    public double a = 1, b = 1, m = 0, alpha = 0, aT = 1, aDiffT = 0, Bi = 0, Ai = 0, AiT = 0,
            aDiffDiffT = 0;
    public double[] Aij = new double[MAX_NUMBER_OF_COMPONENTS];
    public double[] Bij = new double[MAX_NUMBER_OF_COMPONENTS];
    protected double delta1 = 0, delta2 = 0;
    protected double aDern = 0, aDerT = 0, aDerTT = 0, aDerTn = 0, bDern = 0, bDerTn = 0;
    protected double dAdndn[] = new double[MAX_NUMBER_OF_COMPONENTS];
    protected double dBdndn[] = new double[MAX_NUMBER_OF_COMPONENTS];
    private AtractiveTermInterface atractiveParameter;
    static Logger logger = LogManager.getLogger(ComponentEos.class);

    public ComponentEos() {}

    public ComponentEos(String component_name, double moles, double molesInPhase, int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);
    }

    public ComponentEos(int number, double TC, double PC, double M, double a, double moles) {
        super(number, TC, PC, M, a, moles);
    }

    @Override
    public ComponentEos clone() {

        ComponentEos clonedComponent = null;
        try {
            clonedComponent = (ComponentEos) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        clonedComponent.setAtractiveParameter(
                (AtractiveTermInterface) this.getAtractiveParameter().clone());

        return clonedComponent;
    }

    @Override
    public void init(double temp, double pres, double totMoles, double beta, int type) {
        super.init(temp, pres, totMoles, beta, type);
        a = calca();
        b = calcb();
        reducedTemperature = reducedTemperature(temp);
        reducedPressure = reducedPressure(pres);
        aT = a * alpha(temp);
        if (type >= 2) {
            aDiffT = diffaT(temp);
            aDiffDiffT = diffdiffaT(temp);
        }
    }

    @Override
    public void Finit(PhaseInterface phase, double temp, double pres, double totMoles, double beta,
            int numberOfComponents, int type) {
        Bi = phase.calcBi(componentNumber, phase, temp, pres, numberOfComponents);
        Ai = phase.calcAi(componentNumber, phase, temp, pres, numberOfComponents);
        if (type >= 2) {
            AiT = phase.calcAiT(componentNumber, phase, temp, pres, numberOfComponents);
        }
        double totVol = phase.getMolarVolume() * phase.getNumberOfMolesInPhase();
        voli = -(-R * temp * dFdNdV(phase, numberOfComponents, temp, pres)
                + R * temp / (phase.getMolarVolume() * phase.getNumberOfMolesInPhase()))
                / (-R * temp * phase.dFdVdV()
                        - phase.getNumberOfMolesInPhase() * R * temp / (totVol * totVol));

        if (type >= 3) {
            for (int j = 0; j < numberOfComponents; j++) {
                Aij[j] = phase.calcAij(componentNumber, j, phase, temp, pres, numberOfComponents);
                Bij[j] = phase.calcBij(componentNumber, j, phase, temp, pres, numberOfComponents);
            }
        }
    }

    @Override
    public void setAtractiveTerm(int i) {
        atractiveTermNumber = i;
        if (i == 0) {
            setAtractiveParameter(new AtractiveTermSrk(this));
        } else if (i == 1) {
            setAtractiveParameter(new AtractiveTermPr(this));
        } else if (i == 2) {
            setAtractiveParameter(
                    new AtractiveTermSchwartzentruber(this, getSchwartzentruberParams()));
        } else if (i == 3) {
            setAtractiveParameter(new AtractiveTermMollerup(this, getSchwartzentruberParams()));
        } else if (i == 4) {
            setAtractiveParameter(new AtractiveTermMatCop(this, getMatiascopemanParams()));
        } else if (i == 5) {
            setAtractiveParameter(new AtractiveTermRk(this));
        } else if (i == 6) {
            setAtractiveParameter(new AtractiveTermPr1978(this));
        } else if (i == 7) {
            setAtractiveParameter(new AtractiveTermPrDelft1998(this));
        } else if (i == 8) {
            setAtractiveParameter(new AtractiveTermPrGassem2001(this));
        } else if (i == 9) {
            setAtractiveParameter(new AtractiveTermPrDanesh(this));
        } else if (i == 10) {
            setAtractiveParameter(new AtractiveTermGERG(this));
        } else if (i == 11) {
            setAtractiveParameter(new AtractiveTermTwuCoon(this));
        } else if (i == 12) {
            setAtractiveParameter(new AtractiveTermTwuCoonParam(this, getTwuCoonParams()));
        } else if (i == 13) {
            setAtractiveParameter(new AtractiveTermMatCopPR(this, getMatiascopemanParamsPR()));
        } else if (i == 14) {
            setAtractiveParameter(new AtractiveTermTwu(this));
        } else if (i == 15) {
            setAtractiveParameter(new AtractiveTermCPAstatoil(this));
        } else if (i == 16) {
            setAtractiveParameter(new AtractiveTermUMRPRU(this));
        } else if (i == 17) {
            setAtractiveParameter(new AtractiveTermMatCopPRUMR(this));
        } else if (i == 18) {
            if (componentName.equals("mercury")) {
                setAtractiveParameter(new AttractiveTermTwuCoonStatoil(this, getTwuCoonParams()));
            } else {
                setAtractiveParameter(new AtractiveTermSrk(this));
            }
        } else {
            logger.error("error selecting an alpha formultaion term");
            logger.info("ok setting alpha function");
        }
    }

    @Override
    public AtractiveTermInterface getAtractiveTerm() {
        return this.getAtractiveParameter();
    }

    double reducedTemperature(double temperature) {
        return temperature / criticalTemperature;
    }

    double reducedPressure(double pressure) {
        return pressure / criticalPressure;
    }

    @Override
    public double geta() {
        return a;
    }

    @Override
    public double getb() {
        return b;
    }

    @Override
    public double dFdN(PhaseInterface phase, int numberOfComponents, double temperature,
            double pressure) {
        return phase.Fn() + phase.FB() * getBi() + phase.FD() * getAi();
    }

    @Override
    public double dFdNdT(PhaseInterface phase, int numberOfComponents, double temperature,
            double pressure) {
        return (phase.FBT() + phase.FBD() * phase.getAT()) * getBi() + phase.FDT() * getAi()
                + phase.FD() * getAiT();
    }

    @Override
    public double dFdNdV(PhaseInterface phase, int numberOfComponents, double temperature,
            double pressure) {
        return phase.FnV() + phase.FBV() * getBi() + phase.FDV() * getAi();
    }

    @Override
    public double dFdNdN(int j, PhaseInterface phase, int numberOfComponents, double temperature,
            double pressure) {
        ComponentEosInterface[] comp_Array = (ComponentEosInterface[]) phase.getcomponentArray();
        return phase.FnB() * (getBi() + comp_Array[j].getBi())
                + phase.FBD() * (getBi() * comp_Array[j].getAi() + comp_Array[j].getBi() * getAi())
                + phase.FB() * getBij(j) + phase.FBB() * getBi() * comp_Array[j].getBi()
                + phase.FD() * getAij(j);
    }

    @Override
    public double getAi() {
        return Ai;
    }

    @Override
    public double getAiT() {
        return AiT;
    }

    @Override
    public double getBi() {
        return Bi;
    }

    @Override
    public double getBij(int j) {
        return Bij[j];
    }

    @Override
    public double getAij(int j) {
        return Aij[j];
    }

    @Override
    public double getaDiffT() {
        return aDiffT;
    }

    @Override
    public double getaDiffDiffT() {
        return aDiffDiffT;
    }

    @Override
    public double getaT() {
        return aT;
    }

    @Override
    public double fugcoef(PhaseInterface phase) {
        double temperature = phase.getTemperature(), pressure = phase.getPressure();
        logFugasityCoeffisient = dFdN(phase, phase.getNumberOfComponents(), temperature, pressure)
                - Math.log(pressure * phase.getMolarVolume() / (R * temperature));
        fugasityCoeffisient = Math.exp(logFugasityCoeffisient);
        return fugasityCoeffisient;
    }

    @Override
    public double logfugcoefdP(PhaseInterface phase) {
        double temperature = phase.getTemperature(), pressure = phase.getPressure();
        int numberOfComponents = phase.getNumberOfComponents();
        double vol, voli, b, a, yaij = 0, coef;
        vol = phase.getMolarVolume();
        voli = getVoli();
        dfugdp = voli / R / temperature - 1.0 / pressure;
        return dfugdp;
    }

    @Override
    public double logfugcoefdT(PhaseInterface phase) {
        double temperature = phase.getTemperature(), pressure = phase.getPressure();
        int numberOfComponents = phase.getNumberOfComponents();
        double vol, voli, b, a, yaij = 0, coef;
        vol = phase.getMolarVolume();
        voli = getVoli();
        dfugdt = (this.dFdNdT(phase, numberOfComponents, temperature, pressure) + 1.0 / temperature
                - voli / R / temperature
                        * (-R * temperature * phase.dFdTdV() + pressure / temperature));
        return dfugdt;
    }

    @Override
    public double[] logfugcoefdN(PhaseInterface phase) {
        double temperature = phase.getTemperature(), pressure = phase.getPressure();
        int numberOfComponents = phase.getNumberOfComponents();
        ComponentEosInterface[] comp_Array = (ComponentEosInterface[]) phase.getComponents();

        for (int i = 0; i < numberOfComponents; i++) {
            // System.out.println("dfdndn " + dFdNdN(i, phase, numberOfComponents,
            // temperature, pressure) + " n " + phase.getNumberOfMolesInPhase() + " voli " +
            // getVoli()/R/temperature + " dFdNdV " + comp_Array[i].dFdNdV(phase,
            // numberOfComponents, temperature, pressure));
            dfugdn[i] = (this.dFdNdN(i, phase, numberOfComponents, temperature, pressure)
                    + 1.0 / phase.getNumberOfMolesInPhase()
                    - getVoli() / R / temperature
                            * (-R * temperature * comp_Array[i].dFdNdV(phase, numberOfComponents,
                                    temperature, pressure)
                                    + R * temperature / phase.getTotalVolume()));
            // System.out.println("dfugdn " + dfugdn[i]);
            // System.out.println("dFdndn " + dFdNdN(i, phase, numberOfComponents,
            // temperature, pressure) + " voli " + voli + " dFdvdn " +
            // comp_Array[i].dFdNdV(phase, numberOfComponents, temperature, pressure) + "
            // dfugdn " + dfugdn[i]);
            dfugdx[i] = dfugdn[i] * phase.getNumberOfMolesInPhase();
        }
        // System.out.println("diffN: " + 1 + dfugdn[0]);
        return dfugdn;
    }

    // Method added by Neeraj
    /*
     * public double getdfugdn(int i){ double[] dfugdnv = this.logfugcoefdN(phase); //return 0.0001;
     * return dfugdnv[i]; }
     */
    // Added By Neeraj
    @Override
    public double logfugcoefdNi(PhaseInterface phase, int k) {
        double temperature = phase.getTemperature(), pressure = phase.getPressure();
        int numberOfComponents = phase.getNumberOfComponents();
        double vol, voli, b, a, yaij = 0, coef;
        ComponentEosInterface[] comp_Array = (ComponentEosInterface[]) phase.getcomponentArray();
        vol = phase.getMolarVolume();
        voli = getVoli();

        dfugdn[k] =
                (this.dFdNdN(k, phase, numberOfComponents, temperature, pressure)
                        + 1.0 / phase.getNumberOfMolesInPhase()
                        - voli / R / temperature * (-R * temperature
                                * comp_Array[k].dFdNdV(phase, numberOfComponents, temperature,
                                        pressure)
                                + R * temperature / (vol * phase.getNumberOfMolesInPhase())));
        dfugdx[k] = dfugdn[k] * (phase.getNumberOfMolesInPhase());
        // System.out.println("Main dfugdn "+dfugdn[k]);
        return dfugdn[k];
    }

    @Override
    public double getAder() {
        return aDern;
    }

    @Override
    public void setAder(double val) {
        aDern = val;
    }

    @Override
    public double getdAdndn(int j) {
        return dAdndn[j];
    }

    @Override
    public void setdAdndn(int jComp, double val) {
        dAdndn[jComp] = val;
    }

    @Override
    public void setdAdT(double val) {
        aDerT = val;
    }

    @Override
    public double getdAdT() {
        return aDerT;
    }

    @Override
    public void setdAdTdn(double val) {
        aDerTn = val;
    }

    @Override
    public double getdAdTdn() {
        return aDerTn;
    }

    public double getdAdTdT() {
        return aDerT;
    }

    @Override
    public void setdAdTdT(double val) {
        aDerTT = val;
    }

    @Override
    public double getBder() {
        return bDern;
    }

    @Override
    public void setBder(double val) {
        bDern = val;
    }

    @Override
    public double getdBdndn(int j) {
        return dBdndn[j];
    }

    @Override
    public void setdBdndn(int jComp, double val) {
        dBdndn[jComp] = val;
    }

    @Override
    public double getdBdT() {
        return 1;
    }

    @Override
    public void setdBdTdT(double val) {}

    @Override
    public double getdBdndT() {
        return bDerTn;
    }

    @Override
    public void setdBdndT(double val) {
        bDerTn = val;
    }

    public double alpha(double temperature) {
        return getAtractiveParameter().alpha(temperature);
    }

    @Override
    public double aT(double temperature) {
        return getAtractiveParameter().aT(temperature);
    }

    public double diffalphaT(double temperature) {
        return getAtractiveParameter().diffalphaT(temperature);
    }

    public double diffdiffalphaT(double temperature) {
        return getAtractiveParameter().diffdiffalphaT(temperature);
    }

    @Override
    public double diffaT(double temperature) {
        return getAtractiveParameter().diffaT(temperature);
    }

    @Override
    public double diffdiffaT(double temperature) {
        return getAtractiveParameter().diffdiffaT(temperature);
    }

    @Override
    public double[] getDeltaEosParameters() {
        double[] param = {delta1, delta2};
        return param;
    }

    /**
     * Setter for property a.
     *
     * @param a New value of property a.
     */
    @Override
    public void seta(double a) {
        this.a = a;
    }

    /**
     * Setter for property b.
     *
     * @param b New value of property b.
     */
    @Override
    public void setb(double b) {
        this.b = b;
    }

    @Override
    public abstract double calca();

    @Override
    public abstract double calcb();

    @Override
    public double getSurfaceTenisionInfluenceParameter(double temperature) {
        double a_inf = -3.471 + 4.927 * getCriticalCompressibilityFactor()
                + 13.085 * Math.pow(getCriticalCompressibilityFactor(), 2.0)
                - 2.067 * getAcentricFactor() + 1.891 * Math.pow(getAcentricFactor(), 2.0);
        double b_inf = -1.690 + 2.311 * getCriticalCompressibilityFactor()
                + 5.644 * Math.pow(getCriticalCompressibilityFactor(), 2.0)
                - 1.027 * getAcentricFactor() + 1.424 * Math.pow(getAcentricFactor(), 2.0);
        double c_inf = -0.318 + 0.299 * getCriticalCompressibilityFactor()
                + 1.710 * Math.pow(getCriticalCompressibilityFactor(), 2.0)
                - 0.174 * getAcentricFactor() + 0.157 * Math.pow(getAcentricFactor(), 2.0);
        double TR = 1.0 - temperature / getTC();
        if (TR < 1) {
            TR = 0.5;
        }

        double scale1 = aT * 1e-5 * Math.pow(b * 1e-5, 2.0 / 3.0)
                * Math.exp(a_inf + b_inf * Math.log(TR) + c_inf * (Math.pow(Math.log(TR), 2.0)))
                / Math.pow(ThermodynamicConstantsInterface.avagadroNumber, 8.0 / 3.0);

        // System.out.println("scale1 " + scale1);
        // return scale1;
        // getAtractiveTerm().alpha(temperature)*1e-5 * Math.pow(b*1e-5, 2.0 / 3.0) *
        // Math.exp(a_inf + b_inf * Math.log(TR) + c_inf * (Math.pow(Math.log(TR),
        // 2.0)))/ Math.pow(ThermodynamicConstantsInterface.avagadroNumber, 8.0 / 3.0);

        double AA = -1.0e-16 / (1.2326 + 1.3757 * getAcentricFactor());
        double BB = 1.0e-16 / (0.9051 + 1.541 * getAcentricFactor());
        double scale2 = getAtractiveTerm().alpha(temperature) * 1e-5 * Math.pow(b * 1e-5, 2.0 / 3.0)
                * (AA * TR + BB);

        // System.out.println("scale2 " + scale2);
        return aT * 1e-5 * Math.pow(b * 1e-5, 2.0 / 3.0) * (AA * TR + BB);/// Math.pow(ThermodynamicConstantsInterface.avagadroNumber,
                                                                          /// 2.0 / 3.0);
    }

    public double getAresnTV(PhaseInterface phase) {
        return R * phase.getTemperature() * dFdN(phase, phase.getNumberOfComponents(),
                phase.getTemperature(), phase.getPressure());
    }

    @Override
    public double getChemicalPotential(PhaseInterface phase) {
        double entalp = getHID(phase.getTemperature()) * numberOfMolesInPhase;
        double entrop = numberOfMolesInPhase * getIdEntropy(phase.getTemperature());
        double chempot = ((entalp - phase.getTemperature() * entrop)
                + numberOfMolesInPhase * R * phase.getTemperature()
                        * Math.log(numberOfMolesInPhase * R * phase.getTemperature()
                                / phase.getVolume() / referencePressure)
                + getAresnTV(phase) * numberOfMolesInPhase) / numberOfMolesInPhase;
        // double chempot2 = super.getChemicalPotential(phase);
        // System.out.println("d " + chempot + " " + chempot2);
        return ((entalp - phase.getTemperature() * entrop)
                + numberOfMolesInPhase * R * phase.getTemperature()
                        * Math.log(numberOfMolesInPhase * R * phase.getTemperature()
                                / phase.getVolume() / referencePressure)
                + getAresnTV(phase) * numberOfMolesInPhase) / numberOfMolesInPhase;
        // return dF;
    }

    public double getdUdnSV(PhaseInterface phase) {
        return getChemicalPotential(phase);
    }

    public double getdUdSdnV(PhaseInterface phase) {
        return -1.0 / phase.FTT() * dFdNdT(phase, phase.getNumberOfComponents(),
                phase.getTemperature(), phase.getPressure());
    }

    public double getdUdVdnS(PhaseInterface phase) {
        return 1.0 / phase.FTT() * dFdNdV(phase, phase.getNumberOfComponents(),
                phase.getTemperature(), phase.getPressure());
    }

    public double getdUdndnSV(PhaseInterface phase, int compNumb1, int compNumb2) {
        return dFdNdN(compNumb2, phase, phase.getNumberOfComponents(), phase.getTemperature(),
                phase.getPressure())
                - dFdNdT(phase, phase.getNumberOfComponents(), phase.getTemperature(),
                        phase.getPressure()) * 1.0 / phase.FTT();// *
        // phase.getComponent(compNumb2).getF;
    }

    public AtractiveTermInterface getAtractiveParameter() {
        return atractiveParameter;
    }

    public void setAtractiveParameter(AtractiveTermInterface atractiveParameter) {
        this.atractiveParameter = atractiveParameter;
    }
}
