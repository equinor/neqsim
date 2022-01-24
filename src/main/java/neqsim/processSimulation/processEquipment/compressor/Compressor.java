package neqsim.processSimulation.processEquipment.compressor;

import java.awt.Container;
import java.awt.FlowLayout;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.processSimulation.mechanicalDesign.compressor.CompressorMechanicalDesign;
import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * Compressor class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class Compressor extends ProcessEquipmentBaseClass implements CompressorInterface {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(Compressor.class);
    public SystemInterface thermoSystem;
    public StreamInterface inletStream;
    public StreamInterface outStream;
    private double outTemperature = 298.15;
    private boolean useOutTemperature = false;
    public double dH = 0.0;
    public double inletEnthalpy = 0;
    public double pressure = 0.0;
    private int speed = 3000;
    public double isentropicEfficiency = 1.0, polytropicEfficiency = 1.0;
    public boolean usePolytropicCalc = false;
    public boolean powerSet = false;
    private CompressorChartInterface compressorChart = new CompressorChart();
    private AntiSurge antiSurge = new AntiSurge();
    private double polytropicHead = 0;
    private double polytropicFluidHead = 0, polytropicHeadMeter = 0.0;
    private double polytropicExponent = 0;
    private int numberOfCompressorCalcSteps = 40;
    private boolean useRigorousPolytropicMethod = false;
    private boolean useGERG2008 = false;
    private String pressureUnit = "bara";
    private String polytropicMethod = "detailed";

    /**
     * <p>
     * Constructor for Compressor.
     * </p>
     */
    public Compressor() {
        mechanicalDesign = new CompressorMechanicalDesign(this);
    }

    /**
     * <p>
     * Constructor for Compressor.
     * </p>
     *
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    public Compressor(StreamInterface inletStream) {
        this();
        setInletStream(inletStream);
    }

    /**
     * <p>
     * Constructor for Compressor.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    public Compressor(String name, StreamInterface inletStream) {
        this();
        this.name = name;
        setInletStream(inletStream);
    }

    /**
     * <p>
     * Constructor for Compressor.
     * </p>
     *
     * @param interpolateMapLookup a boolean
     */
    public Compressor(boolean interpolateMapLookup) {
        this();
        if (interpolateMapLookup) {
            compressorChart = new CompressorChartAlternativeMapLookup();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setInletStream(StreamInterface inletStream) {
        this.inletStream = inletStream;
        try {
            this.outStream = inletStream.clone();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * <p>
     * solveAntiSurge.
     * </p>
     */
    public void solveAntiSurge() {
        if (getAntiSurge().isActive()) {
            // ....
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setOutletPressure(double pressure) {
        this.pressure = pressure;
    }

    /**
     * <p>
     * setOutletPressure.
     * </p>
     *
     * @param pressure a double
     * @param unit a {@link java.lang.String} object
     */
    public void setOutletPressure(double pressure, String unit) {
        this.pressure = pressure;
        this.pressureUnit = unit;
    }

    /**
     * <p>
     * getOutletPressure.
     * </p>
     *
     * @return a double
     */
    public double getOutletPressure() {
        return pressure;
    }

    /** {@inheritDoc} */
    @Override
    public double getEnergy() {
        return getTotalWork();
    }

    /**
     * <p>
     * getPower.
     * </p>
     *
     * @return a double
     */
    public double getPower() {
        return getTotalWork();
    }

    /**
     * <p>
     * getPower.
     * </p>
     *
     * @param unit a {@link java.lang.String} object
     * @return a double
     */
    public double getPower(String unit) {
        double conversionFactor = 1.0;
        if (unit.equals("MW")) {
            conversionFactor = 1.0 / 1.0e6;
        } else if (unit.equals("kW")) {
            conversionFactor = 1.0 / 1.0e3;
        }
        return conversionFactor * getPower();
    }

    /**
     * <p>
     * setPower.
     * </p>
     *
     * @param p a double
     */
    public void setPower(double p) {
        powerSet = true;
        dH = p;
    }

    /** {@inheritDoc} */
    @Override
    public StreamInterface getOutStream() {
        return outStream;
    }

    /**
     * <p>
     * getInStream.
     * </p>
     *
     * @return a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    public StreamInterface getInStream() {
        return inletStream;
    }

    /**
     * Calculates polytropic or isentropic efficiency
     *
     * @param outTemperature a double
     * @return a double
     */
    public double solveEfficiency(double outTemperature) {
        double funk = 0.0, funkOld = 0.0;
        double newPoly;
        double dfunkdPoly = 100.0, dPoly = 100.0, oldPoly = outTemperature;
        useOutTemperature = false;
        run();
        useOutTemperature = true;
        int iter = 0;
        boolean useOld = usePolytropicCalc;
        // usePolytropicCalc = true;
        // System.out.println("use polytropic " + usePolytropicCalc);
        do {
            iter++;
            funk = getThermoSystem().getTemperature() - outTemperature;
            dfunkdPoly = (funk - funkOld) / dPoly;
            newPoly = polytropicEfficiency - funk / dfunkdPoly;
            if (iter <= 1) {
                newPoly = polytropicEfficiency + 0.01;
            }
            oldPoly = polytropicEfficiency;
            polytropicEfficiency = newPoly;
            isentropicEfficiency = newPoly;
            dPoly = polytropicEfficiency - oldPoly;
            funkOld = funk;
            useOutTemperature = false;
            run();
            useOutTemperature = true;
            // System.out.println("temperature compressor " +
            // getThermoSystem().getTemperature() + " funk " + funk + " polytropic " +
            // polytropicEfficiency);
        } while ((Math.abs((getThermoSystem().getTemperature() - outTemperature)) > 1e-5
                || iter < 3) && (iter < 50));
        usePolytropicCalc = useOld;
        return newPoly;
    }

    /**
     * <p>
     * findOutPressure.
     * </p>
     *
     * @param hinn a double
     * @param hout a double
     * @param polytropicEfficiency a double
     * @return a double
     */
    public double findOutPressure(double hinn, double hout, double polytropicEfficiency) {
        double entropy = getThermoSystem().getEntropy();
        getThermoSystem().setPressure(getThermoSystem().getPressure() + 1.0, pressureUnit);

        // System.out.println("entropy inn.." + entropy);
        ThermodynamicOperations thermoOps = new ThermodynamicOperations(getThermoSystem());
        thermoOps.PSflash(entropy);

        double houtGuess = hinn + dH / polytropicEfficiency;
        thermoOps.PHflash(houtGuess, 0);
        System.out.println("TEMPERATURE .." + getThermoSystem().getTemperature());
        return getThermoSystem().getPressure();
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        thermoSystem = inletStream.getThermoSystem().clone();
        ThermodynamicOperations thermoOps = new ThermodynamicOperations(getThermoSystem());
        thermoOps = new ThermodynamicOperations(getThermoSystem());
        getThermoSystem().init(3);
        getThermoSystem().initPhysicalProperties("density");
        double presinn = getThermoSystem().getPressure();
        double hinn = getThermoSystem().getEnthalpy();
        double densInn = getThermoSystem().getDensity();
        double entropy = getThermoSystem().getEntropy();

        if (useGERG2008 && thermoSystem.getNumberOfPhases() == 1) {
            double[] gergProps;
            gergProps = getThermoSystem().getPhase(0).getProperties_GERG2008();
            hinn = gergProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
            entropy = gergProps[8] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
            densInn = getThermoSystem().getPhase(0).getDensity_GERG2008();
        }

        inletEnthalpy = hinn;
        boolean surgeCheck = false;
        double orginalMolarFLow = thermoSystem.getTotalNumberOfMoles();
        double fractionAntiSurge = 0.0;
        double kappa = 0.0;
        if (useOutTemperature) {
            if (useRigorousPolytropicMethod) {
                solveEfficiency(outTemperature);
                polytropicHead = getPower() / getThermoSystem().getFlowRate("kg/sec") / 1000.0
                        * getPolytropicEfficiency();
                polytropicFluidHead = polytropicHead;
                polytropicHeadMeter =
                        polytropicFluidHead * 1000.0 / ThermodynamicConstantsInterface.gravity;
                return;
            } else {
                double MW = thermoSystem.getMolarMass();
                thermoSystem.setPressure(getOutletPressure(), pressureUnit);
                thermoOps.PSflash(entropy);
                if (useGERG2008 && thermoSystem.getNumberOfPhases() == 1) {
                    thermoOps.PSflashGERG2008(entropy);
                }
                thermoSystem.initPhysicalProperties("density");
                double densOutIsentropic = thermoSystem.getDensity("kg/m3");
                double enthalpyOutIsentropic = thermoSystem.getEnthalpy();
                if (useGERG2008 && thermoSystem.getNumberOfPhases() == 1) {
                    double[] gergProps;
                    gergProps = getThermoSystem().getPhase(0).getProperties_GERG2008();
                    densOutIsentropic = getThermoSystem().getPhase(0).getDensity_GERG2008();
                    enthalpyOutIsentropic =
                            gergProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
                }
                thermoSystem.setTemperature(outTemperature);
                thermoOps.TPflash();
                thermoSystem.init(2);
                thermoSystem.initPhysicalProperties("density");
                double outEnthalpy = thermoSystem.getEnthalpy();
                double densOut = thermoSystem.getDensity("kg/m3");
                if (useGERG2008 && thermoSystem.getNumberOfPhases() == 1) {
                    double[] gergProps;
                    gergProps = getThermoSystem().getPhase(0).getProperties_GERG2008();
                    outEnthalpy =
                            gergProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
                    densOut = getThermoSystem().getPhase(0).getDensity_GERG2008();
                }
                dH = outEnthalpy - inletEnthalpy;
                // System.out.println("total power " +
                // dH/getThermoSystem().getFlowRate("kg/sec"));

                double n = Math.log(getOutletPressure() / presinn) / Math.log(densOut / densInn);
                double CF =
                        (enthalpyOutIsentropic - inletEnthalpy) / thermoSystem.getFlowRate("kg/sec")
                                / (n / (n - 1.0) * (getOutletPressure() * 1e5 / densOutIsentropic
                                        - presinn * 1e5 / densInn));

                double F1 = thermoSystem.getTotalNumberOfMoles();
                double polytropicPower = F1 * MW * (n / (n - 1.0)) * CF * presinn * 1e5 / densInn
                        * (Math.pow((getOutletPressure() / presinn), (n - 1.0) / n) - 1.0);
                // System.out.println("polytropic power " +
                // polytropicPower/getThermoSystem().getFlowRate("kg/sec"));
                polytropicEfficiency = polytropicPower / getThermoSystem().getFlowRate("kg/sec")
                        / (dH / getThermoSystem().getFlowRate("kg/sec"));
                isentropicEfficiency = (enthalpyOutIsentropic - inletEnthalpy) / dH;

                // isentropicEfficiency = (getThermoSystem().getEnthalpy() - hinn) / dH;

                double k = Math.log(getOutletPressure() / presinn)
                        / Math.log(densOutIsentropic / densInn);
                double term1 = Math.pow(getOutletPressure() / presinn, (n - 1.0) / n) - 1.0;
                double term2 = n / (n - 1.0) * (k - 1.0) / k;
                double term3 = Math.pow(getOutletPressure() / presinn, (k - 1.0) / k) - 1.0;
                double polyPow = term1 * term2 / term3 * isentropicEfficiency;
                polytropicEfficiency = polyPow;
                polytropicPower = dH * polytropicEfficiency;
                // System.out.println("polytropic eff " + polytropicEfficiency);
                // System.out.println("isentropic eff " + isentropicEfficiency);
                polytropicFluidHead =
                        polytropicPower / getThermoSystem().getFlowRate("kg/sec") / 1000.0;
                polytropicHeadMeter = polytropicFluidHead * 1000.0 / 9.81;
                polytropicHead = polytropicFluidHead;
                if (getCompressorChart().isUseCompressorChart()) {
                    if (getCompressorChart().getHeadUnit().equals("meter")) {
                        polytropicHead = polytropicHeadMeter;
                    } else {
                        polytropicHead = polytropicFluidHead;
                    }
                }
                outStream.setThermoSystem(getThermoSystem());

                return;
            }
        }
        if (compressorChart.isUseCompressorChart()) {
            do {
                double actualFlowRate = thermoSystem.getFlowRate("m3/hr");
                double z_inlet = thermoSystem.getZ();
                double MW = thermoSystem.getMolarMass();

                if (getCompressorChart().useRealKappa()) {
                    kappa = thermoSystem.getGamma();
                } else {
                    kappa = thermoSystem.getGamma2();
                }
                if (useGERG2008 && thermoSystem.getNumberOfPhases() == 1) {
                    double[] gergProps;
                    gergProps = getThermoSystem().getPhase(0).getProperties_GERG2008();
                    actualFlowRate *= gergProps[1] / z_inlet;
                    kappa = gergProps[14];
                    z_inlet = gergProps[1];
                }

                double polytropEff =
                        getCompressorChart().getPolytropicEfficiency(actualFlowRate, getSpeed());
                setPolytropicEfficiency(polytropEff / 100.0);
                polytropicHead = getCompressorChart().getPolytropicHead(actualFlowRate, getSpeed());
                double temperature_inlet = thermoSystem.getTemperature();
                double n = 1.0 / (1.0 - (kappa - 1.0) / kappa * 1.0 / (polytropEff / 100.0));
                polytropicExponent = n;
                if (getCompressorChart().getHeadUnit().equals("meter")) {
                    polytropicFluidHead = polytropicHead / 1000.0 * 9.81;
                    polytropicHeadMeter = polytropicHead;
                } else {
                    polytropicFluidHead = polytropicHead;
                    polytropicHeadMeter = polytropicHead * 1000.0 / 9.81;
                }
                double pressureRatio = Math.pow(
                        (polytropicFluidHead * 1000.0
                                + (n / (n - 1.0) * z_inlet * 8.314 * (temperature_inlet) / MW))
                                / (n / (n - 1.0) * z_inlet * 8.314 * (temperature_inlet) / MW),
                        n / (n - 1.0));
                setOutletPressure(thermoSystem.getPressure() * pressureRatio);
                if (getAntiSurge().isActive()) {
                    logger.info("surge flow "
                            + getCompressorChart().getSurgeCurve().getSurgeFlow(polytropicHead)
                            + " m3/hr");
                    surgeCheck = isSurge(polytropicHead, actualFlowRate);
                }
                if (getCompressorChart().getStoneWallCurve().isActive()) {
                    // logger.info("stone wall? " + isStoneWall(polytropicHead,
                    // thermoSystem.getFlowRate("m3/hr")));
                }
                if (surgeCheck && getAntiSurge().isActive()) {
                	thermoSystem.setTotalFlowRate(getAntiSurge().getSurgeControlFactor()
                    		* getCompressorChart().getSurgeCurve().getSurgeFlow(polytropicFluidHead), "Am3/hr");
                    thermoSystem.init(3);
                    fractionAntiSurge =
                            thermoSystem.getTotalNumberOfMoles() / orginalMolarFLow - 1.0;
                    getAntiSurge().setCurrentSurgeFraction(fractionAntiSurge);
                }

                powerSet = true;
                dH = polytropicFluidHead * 1000.0 * thermoSystem.getMolarMass()
                        / getPolytropicEfficiency() * thermoSystem.getTotalNumberOfMoles();
            } while (surgeCheck && getAntiSurge().isActive());
        }

        if (usePolytropicCalc) {
            if (powerSet) {
                double hout = hinn * (1 - 0 + fractionAntiSurge) + dH;
                thermoSystem.setPressure(pressure, pressureUnit);
                thermoOps = new ThermodynamicOperations(getThermoSystem());
                thermoOps.PHflash(hout, 0);
                if (useGERG2008 && thermoSystem.getNumberOfPhases() == 1) {
                    thermoOps.PHflashGERG2008(hout);
                }
            } else {
                if (polytropicMethod.equals("detailed")) {
                    int numbersteps = numberOfCompressorCalcSteps;
                    double dp = (pressure - getThermoSystem().getPressure()) / (1.0 * numbersteps);
                    for (int i = 0; i < numbersteps; i++) {
                        entropy = getThermoSystem().getEntropy();
                        hinn = getThermoSystem().getEnthalpy();
                        if (useGERG2008 && thermoSystem.getNumberOfPhases() == 1) {
                            double[] gergProps;
                            gergProps = getThermoSystem().getPhase(0).getProperties_GERG2008();
                            hinn = gergProps[7]
                                    * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
                            entropy = gergProps[8]
                                    * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
                        }
                        getThermoSystem().setPressure(getThermoSystem().getPressure() + dp,
                                pressureUnit);
                        thermoOps = new ThermodynamicOperations(getThermoSystem());
                        if (useGERG2008 && thermoSystem.getNumberOfPhases() == 1) {
                            thermoOps.PSflashGERG2008(entropy);
                        } else {
                            thermoOps.PSflash(entropy);
                        }
                        double newEnt = getThermoSystem().getEnthalpy();
                        if (useGERG2008 && thermoSystem.getNumberOfPhases() == 1) {
                            double[] gergProps;
                            gergProps = getThermoSystem().getPhase(0).getProperties_GERG2008();
                            newEnt = gergProps[7]
                                    * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
                        }
                        double hout = hinn + (newEnt - hinn) / polytropicEfficiency;
                        thermoOps.PHflash(hout, 0);
                        if (useGERG2008 && thermoSystem.getNumberOfPhases() == 1) {
                            thermoOps.PHflashGERG2008(hout);
                        }
                    }
                } else if (polytropicMethod.equals("schultz")) {
                    double schultzX = thermoSystem.getTemperature() / thermoSystem.getVolume()
                            * thermoSystem.getdVdTpn() - 1.0;
                    double schultzY = -thermoSystem.getPressure() / thermoSystem.getVolume()
                            * thermoSystem.getdVdPtn();
                    thermoSystem.setPressure(getOutletPressure(), pressureUnit);
                    thermoOps.PSflash(entropy);
                    thermoSystem.initProperties();
                    double densOutIsentropic = thermoSystem.getDensity("kg/m3");
                    double enthalpyOutIsentropic = thermoSystem.getEnthalpy();
                    if (useGERG2008 && thermoSystem.getNumberOfPhases() == 1) {
                        thermoOps.PSflashGERG2008(entropy);
                        double[] gergProps;
                        gergProps = getThermoSystem().getPhase(0).getProperties_GERG2008();
                        densOutIsentropic = getThermoSystem().getPhase(0).getDensity_GERG2008();
                        enthalpyOutIsentropic = gergProps[7]
                                * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
                    }
                    double isenthalpicvolumeexponent = Math.log(getOutletPressure() / presinn)
                            / Math.log(densOutIsentropic / densInn);
                    double nV = (1.0 + schultzX) / (1.0 / isenthalpicvolumeexponent
                            * (1.0 / polytropicEfficiency + schultzX)
                            - schultzY * (1.0 / polytropicEfficiency - 1.0));
                    double term = nV / (nV - 1.0);
                    double term2 =
                            1e5 * (getOutletPressure() / densOutIsentropic - presinn / densInn);
                    double term3 = isenthalpicvolumeexponent / (isenthalpicvolumeexponent - 1.0);
                    double CF = (enthalpyOutIsentropic - inletEnthalpy) / (term2 * term3);
                    dH = term * CF * 1e5 * presinn / densInn
                            * (Math.pow(getOutletPressure() / presinn, 1.0 / term) - 1.0)
                            / polytropicEfficiency;
                    double hout = hinn + dH;
                    thermoOps = new ThermodynamicOperations(getThermoSystem());
                    thermoOps.PHflash(hout, 0);
                    if (useGERG2008 && thermoSystem.getNumberOfPhases() == 1) {
                        thermoOps.PHflashGERG2008(hout);
                    }
                } else {
                    thermoSystem.setPressure(getOutletPressure(), pressureUnit);
                    thermoOps.PSflash(entropy);
                    thermoSystem.initProperties();
                    double densOutIsentropic = thermoSystem.getDensity("kg/m3");
                    double enthalpyOutIsentropic = thermoSystem.getEnthalpy();
                    if (useGERG2008 && thermoSystem.getNumberOfPhases() == 1) {
                        thermoOps.PSflashGERG2008(entropy);
                        double[] gergProps;
                        gergProps = getThermoSystem().getPhase(0).getProperties_GERG2008();
                        densOutIsentropic = getThermoSystem().getPhase(0).getDensity_GERG2008();
                        enthalpyOutIsentropic = gergProps[7]
                                * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
                    }
                    double isenthalpicvolumeexponent = Math.log(getOutletPressure() / presinn)
                            / Math.log(densOutIsentropic / densInn);
                    double term = isenthalpicvolumeexponent / (isenthalpicvolumeexponent - 1.0)
                            * (polytropicEfficiency);
                    double term2 =
                            1e5 * (getOutletPressure() / densOutIsentropic - presinn / densInn);
                    double CF = (enthalpyOutIsentropic - inletEnthalpy) / (term * term2);
                    dH = term * CF * 1e5 * presinn / densInn
                            * (Math.pow(getOutletPressure() / presinn, 1.0 / term) - 1.0);
                    double hout = hinn + dH;
                    thermoOps = new ThermodynamicOperations(getThermoSystem());
                    thermoOps.PHflash(hout, 0);
                    if (useGERG2008 && thermoSystem.getNumberOfPhases() == 1) {
                        thermoOps.PHflashGERG2008(hout);
                    }
                }
            }
        } else {
            getThermoSystem().setPressure(pressure, pressureUnit);
            // System.out.println("entropy inn.." + entropy);
            thermoOps = new ThermodynamicOperations(getThermoSystem());
            thermoOps.PSflash(entropy);
            if (useGERG2008 && thermoSystem.getNumberOfPhases() == 1) {
                thermoOps.PSflashGERG2008(entropy);
            }
            // double densOutIdeal = getThermoSystem().getDensity();
            double newEnt = getThermoSystem().getEnthalpy();
            if (!powerSet) {
                dH = (getThermoSystem().getEnthalpy() - hinn) / isentropicEfficiency;
                if (useGERG2008 && thermoSystem.getNumberOfPhases() == 1) {
                    double[] gergProps;
                    gergProps = getThermoSystem().getPhase(0).getProperties_GERG2008();
                    newEnt = gergProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
                    dH = (newEnt - hinn) / isentropicEfficiency;
                }
            }
            double hout = hinn + dH;
            isentropicEfficiency = (newEnt - hinn) / dH;
            dH = hout - hinn;
            thermoOps = new ThermodynamicOperations(getThermoSystem());
            thermoOps.PHflash(hout, 0);
            if (useGERG2008 && thermoSystem.getNumberOfPhases() == 1) {
                thermoOps.PHflashGERG2008(hout);
            }
        }
        // thermoSystem.display();

        if (getCompressorChart().isUseCompressorChart() && getAntiSurge().isActive()) {
            thermoSystem.setTotalNumberOfMoles(orginalMolarFLow);
            thermoSystem.init(3);
        }
        thermoSystem.initProperties();
        outStream.setThermoSystem(getThermoSystem());

        polytropicFluidHead = getPower() / getThermoSystem().getFlowRate("kg/sec") / 1000.0
                * getPolytropicEfficiency();
        polytropicHeadMeter = polytropicFluidHead * 1000.0 / 9.81;
    }

    /**
     * <p>
     * generateCompressorCurves.
     * </p>
     */
    public void generateCompressorCurves() {
        double flowRef = getThermoSystem().getFlowRate("m3/hr");
        double factor = flowRef / 4000.0;
        double[] chartConditions = new double[] {0.3, 1.0, 1.0, 1.0};
        double[] speed = new double[] {12913, 12298, 11683, 11098, 10453, 9224, 8609, 8200};
        double[][] flow = new double[][] {
                {2789.1285, 3174.0375, 3689.2288, 4179.4503, 4570.2768, 4954.7728, 5246.0329,
                        5661.0331},
                {2571.1753, 2943.7254, 3440.2675, 3837.4448, 4253.0898, 4668.6643, 4997.1926,
                        5387.4952},
                {2415.3793, 2763.0706, 3141.7095, 3594.7436, 4047.6467, 4494.1889, 4853.7353,
                        5138.7858},
                {2247.2043, 2799.7342, 3178.3428, 3656.1551, 4102.778, 4394.1591, 4648.3224,
                        4840.4998},
                {2072.8397, 2463.9483, 2836.4078, 3202.5266, 3599.6333, 3978.0203, 4257.0022,
                        4517.345},
                {1835.9552, 2208.455, 2618.1322, 2940.8034, 3244.7852, 3530.1279, 3753.3738,
                        3895.9746},
                {1711.3386, 1965.8848, 2356.9431, 2685.9247, 3008.5154, 3337.2855, 3591.5092},
                {1636.5807, 2002.8708, 2338.0319, 2642.1245, 2896.4894, 3113.6264, 3274.8764,
                        3411.2977}};

        for (int i = 0; i < flow.length; i++) {
            for (int j = 0; j < flow[i].length; j++) {
                flow[i][j] *= factor;
            }
        }

        double[][] head = new double[][] {
                {80.0375, 78.8934, 76.2142, 71.8678, 67.0062, 60.6061, 53.0499, 39.728},
                {72.2122, 71.8369, 68.9009, 65.8341, 60.7167, 54.702, 47.2749, 35.7471},
                {65.1576, 64.5253, 62.6118, 59.1619, 54.0455, 47.0059, 39.195, 31.6387},
                {58.6154, 56.9627, 54.6647, 50.4462, 44.4322, 38.4144, 32.9084, 28.8109},
                {52.3295, 51.0573, 49.5283, 46.3326, 42.3685, 37.2502, 31.4884, 25.598},
                {40.6578, 39.6416, 37.6008, 34.6603, 30.9503, 27.1116, 23.2713, 20.4546},
                {35.2705, 34.6359, 32.7228, 31.0645, 27.0985, 22.7482, 18.0113},
                {32.192, 31.1756, 29.1329, 26.833, 23.8909, 21.3324, 18.7726, 16.3403},};

        for (int i = 0; i < head.length; i++) {
            for (int j = 0; j < head[i].length; j++) {
                head[i][j] *= factor / 5.0;
            }
        }
        double[][] polyEff = new double[][] {
                {77.2452238409573, 79.4154186459363, 80.737960012489, 80.5229826589649,
                        79.2210931638144, 75.4719133864634, 69.6034181197298, 58.7322388482707},
                {77.0107837113504, 79.3069974136389, 80.8941189021135, 80.7190194665918,
                        79.5313242980328, 75.5912622896367, 69.6846136362097, 60.0043057990909},
                {77.0043065299874, 79.1690958847856, 80.8038169975675, 80.6543975614197,
                        78.8532389102705, 73.6664774270613, 66.2735600426727, 57.671664571658},
                {77.0716623789093, 80.4629750233093, 81.1390811169072, 79.6374242667478,
                        75.380928428817, 69.5332969549779, 63.7997587622339, 58.8120614497758},
                {76.9705872525642, 79.8335492585324, 80.9468133671171, 80.5806471927835,
                        78.0462158225426, 73.0403707523258, 66.5572286338589, 59.8624822515064},
                {77.5063036680357, 80.2056198362559, 81.0339108025933, 79.6085962687939,
                        76.3814534404405, 70.8027503005902, 64.6437367160571, 60.5299349982342},
                {77.8175271586685, 80.065165942218, 81.0631362122632, 79.8955051771299,
                        76.1983240929369, 69.289982774309, 60.8567149372229},
                {78.0924334304045, 80.9353551568667, 80.7904437766234, 78.8639325223295,
                        75.2170936751143, 70.3105081673411, 65.5507568533569, 61.0391468300337}};

        getCompressorChart().setCurves(chartConditions, speed, flow, head, polyEff);
        getCompressorChart().setHeadUnit("kJ/kg");
    }

    /** {@inheritDoc} */
    @Override
    public void displayResult() {
        DecimalFormat nf = new DecimalFormat();
        nf.setMaximumFractionDigits(5);
        nf.applyPattern("#.#####E0");

        JDialog dialog = new JDialog(new JFrame(), "Results from TPflash");
        Container dialogContentPane = dialog.getContentPane();
        dialogContentPane.setLayout(new FlowLayout());

        getThermoSystem().initPhysicalProperties();
        String[][] table = new String[50][5];
        String[] names = {"", "Phase 1", "Phase 2", "Phase 3", "Unit"};
        table[0][0] = "";
        table[0][1] = "";
        table[0][2] = "";
        table[0][3] = "";
        StringBuffer buf = new StringBuffer();
        FieldPosition test = new FieldPosition(0);

        for (int i = 0; i < getThermoSystem().getNumberOfPhases(); i++) {
            for (int j = 0; j < getThermoSystem().getPhases()[0].getNumberOfComponents(); j++) {
                table[j + 1][0] = getThermoSystem().getPhases()[0].getComponents()[j].getName();
                buf = new StringBuffer();
                table[j + 1][i + 1] =
                        nf.format(getThermoSystem().getPhases()[i].getComponents()[j].getx(), buf,
                                test).toString();
                table[j + 1][4] = "[-]";
            }
            buf = new StringBuffer();
            table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 2][0] = "Density";
            table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 2][i + 1] =
                    nf.format(getThermoSystem().getPhases()[i].getPhysicalProperties().getDensity(),
                            buf, test).toString();
            table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 2][4] = "[kg/m^3]";

            // Double.longValue(thermoSystem.getPhases()[i].getBeta());
            buf = new StringBuffer();
            table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 3][0] =
                    "PhaseFraction";
            table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 3][i + 1] =
                    nf.format(getThermoSystem().getPhases()[i].getBeta(), buf, test).toString();
            table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 3][4] = "[-]";

            buf = new StringBuffer();
            table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 4][0] = "MolarMass";
            table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 4][i + 1] =
                    nf.format(getThermoSystem().getPhases()[i].getMolarMass() * 1000, buf, test)
                            .toString();
            table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 4][4] = "[kg/kmol]";

            buf = new StringBuffer();
            table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 5][0] = "Cp";
            table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 5][i + 1] = nf
                    .format((getThermoSystem().getPhases()[i].getCp()
                            / getThermoSystem().getPhases()[i].getNumberOfMolesInPhase() * 1.0
                            / getThermoSystem().getPhases()[i].getMolarMass() * 1000), buf, test)
                    .toString();
            table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 5][4] = "[kJ/kg*K]";

            buf = new StringBuffer();
            table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 7][0] = "Viscosity";
            table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 7][i + 1] = nf.format(
                    (getThermoSystem().getPhases()[i].getPhysicalProperties().getViscosity()), buf,
                    test).toString();
            table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 7][4] = "[kg/m*sec]";

            buf = new StringBuffer();
            table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 8][0] = "Conductivity";
            table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 8][i + 1] = nf.format(
                    getThermoSystem().getPhases()[i].getPhysicalProperties().getConductivity(), buf,
                    test).toString();
            table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 8][4] = "[W/m*K]";

            buf = new StringBuffer();
            table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 10][0] = "Pressure";
            table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 10][i + 1] =
                    Double.toString(getThermoSystem().getPhases()[i].getPressure());
            table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 10][4] = "[bar]";

            buf = new StringBuffer();
            table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 11][0] = "Temperature";
            table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 11][i + 1] =
                    Double.toString(getThermoSystem().getPhases()[i].getTemperature());
            table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 11][4] = "[K]";
            Double.toString(getThermoSystem().getPhases()[i].getTemperature());

            buf = new StringBuffer();
            table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 13][0] = "Stream";
            table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 13][i + 1] = name;
            table[getThermoSystem().getPhases()[0].getNumberOfComponents() + 13][4] = "-";
        }

        JTable Jtab = new JTable(table, names);
        JScrollPane scrollpane = new JScrollPane(Jtab);
        dialogContentPane.add(scrollpane);
        dialog.pack();
        dialog.setVisible(true);
    }

    /** {@inheritDoc} */
    @Override
    public String[][] getResultTable() {
        return thermoSystem.getResultTable();
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return name;
    }

    /**
     * <p>
     * getTotalWork.
     * </p>
     *
     * @return a double
     */
    public double getTotalWork() {
    	double multi = 1.0;
    	if(getAntiSurge().isActive()) {
    		multi=1.0+getAntiSurge().getCurrentSurgeFraction();
    	}
        if (useGERG2008 && thermoSystem.getNumberOfPhases() == 1) {
            double[] gergProps;
            gergProps = getThermoSystem().getPhase(0).getProperties_GERG2008();
            double enth = gergProps[7] * getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
            return (enth - inletEnthalpy)*multi;
        } else
            return multi*(getThermoSystem().getEnthalpy() - inletEnthalpy);
    }

    /** {@inheritDoc} */
    @Override
    public void runTransient() {}

    /** {@inheritDoc} */
    @Override
    public double getIsentropicEfficiency() {
        return isentropicEfficiency;
    }

    /** {@inheritDoc} */
    @Override
    public void setIsentropicEfficiency(double isentropicEfficientcy) {
        this.isentropicEfficiency = isentropicEfficientcy;
    }

    /**
     * <p>
     * usePolytropicCalc.
     * </p>
     *
     * @return the usePolytropicCalc
     */
    public boolean usePolytropicCalc() {
        return usePolytropicCalc;
    }

    /**
     * <p>
     * Setter for the field <code>usePolytropicCalc</code>.
     * </p>
     *
     * @param usePolytropicCalc the usePolytropicCalc to set
     */
    public void setUsePolytropicCalc(boolean usePolytropicCalc) {
        this.usePolytropicCalc = usePolytropicCalc;
    }

    /** {@inheritDoc} */
    @Override
    public double getPolytropicEfficiency() {
        return polytropicEfficiency;
    }

    /** {@inheritDoc} */
    @Override
    public void setPolytropicEfficiency(double polytropicEfficiency) {
        this.polytropicEfficiency = polytropicEfficiency;
    }

    /** {@inheritDoc} */
    @Override
    public SystemInterface getThermoSystem() {
        return thermoSystem;
    }

    /**
     * <p>
     * Getter for the field <code>compressorChart</code>.
     * </p>
     *
     * @return a
     *         {@link neqsim.processSimulation.processEquipment.compressor.CompressorChartInterface}
     *         object
     */
    public CompressorChartInterface getCompressorChart() {
        return compressorChart;
    }

    /**
     * <p>
     * Setter for the field <code>compressorChart</code>.
     * </p>
     *
     * @param compressorChart a
     *        {@link neqsim.processSimulation.processEquipment.compressor.CompressorChart} object
     */
    public void setCompressorChart(CompressorChart compressorChart) {
        this.compressorChart = compressorChart;
    }

    /** {@inheritDoc} */
    @Override
    public AntiSurge getAntiSurge() {
        return antiSurge;
    }

    /**
     * <p>
     * isSurge.
     * </p>
     *
     * @param flow a double
     * @param head a double
     * @return a boolean
     */
    public boolean isSurge(double flow, double head) {
        getAntiSurge().setSurge(getCompressorChart().getSurgeCurve().isSurge(flow, head));
        return getAntiSurge().isSurge();
    }

    /**
     * <p>
     * isStoneWall.
     * </p>
     *
     * @param flow a double
     * @param head a double
     * @return a boolean
     */
    public boolean isStoneWall(double flow, double head) {
        return getCompressorChart().getStoneWallCurve().isStoneWall(flow, head);
    }

    /**
     * <p>
     * Setter for the field <code>antiSurge</code>.
     * </p>
     *
     * @param antiSurge a {@link neqsim.processSimulation.processEquipment.compressor.AntiSurge}
     *        object
     */
    public void setAntiSurge(AntiSurge antiSurge) {
        this.antiSurge = antiSurge;
    }

    /**
     * <p>
     * Getter for the field <code>speed</code>.
     * </p>
     *
     * @return a int
     */
    public int getSpeed() {
        return speed;
    }

    /**
     * <p>
     * Setter for the field <code>speed</code>.
     * </p>
     *
     * @param speed a int
     */
    public void setSpeed(int speed) {
        this.speed = speed;
    }

    /**
     * <p>
     * Getter for the field <code>polytropicHead</code>.
     * </p>
     *
     * @param unit a {@link java.lang.String} object
     * @return a double
     */
    public double getPolytropicHead(String unit) {
        if (unit.equals("kJ/kg"))
            return polytropicFluidHead;
        else if (unit.equals("meter"))
            return polytropicHeadMeter;
        else
            return polytropicHead;
    }

    /**
     * <p>
     * Getter for the field <code>polytropicHead</code>.
     * </p>
     *
     * @return a double
     */
    public double getPolytropicHead() {
        return polytropicHead;
    }

    /**
     * <p>
     * Getter for the field <code>polytropicFluidHead</code>.
     * </p>
     *
     * @return a double
     */
    public double getPolytropicFluidHead() {
        return polytropicFluidHead;
    }

    /**
     * <p>
     * Getter for the field <code>polytropicExponent</code>.
     * </p>
     *
     * @return a double
     */
    public double getPolytropicExponent() {
        return polytropicExponent;
    }

    /**
     * <p>
     * Getter for the field <code>polytropicHeadMeter</code>.
     * </p>
     *
     * @return a double
     */
    public double getPolytropicHeadMeter() {
        return polytropicHeadMeter;
    }

    /**
     * <p>
     * Setter for the field <code>polytropicHeadMeter</code>.
     * </p>
     *
     * @param polytropicHeadMeter a double
     */
    public void setPolytropicHeadMeter(double polytropicHeadMeter) {
        this.polytropicHeadMeter = polytropicHeadMeter;
    }

    /**
     * <p>
     * Getter for the field <code>outTemperature</code>.
     * </p>
     *
     * @return a double
     */
    public double getOutTemperature() {
        if (useOutTemperature)
            return outTemperature;
        else
            return getThermoSystem().getTemperature();
    }

    /**
     * <p>
     * Setter for the field <code>outTemperature</code>.
     * </p>
     *
     * @param outTemperature a double
     */
    public void setOutTemperature(double outTemperature) {
        useOutTemperature = true;
        this.outTemperature = outTemperature;
    }

    /**
     * <p>
     * useOutTemperature.
     * </p>
     *
     * @param useOutTemperature a boolean
     */
    public void useOutTemperature(boolean useOutTemperature) {
        this.useOutTemperature = useOutTemperature;
    }

    /**
     * <p>
     * Getter for the field <code>numberOfCompressorCalcSteps</code>.
     * </p>
     *
     * @return a int
     */
    public int getNumberOfCompressorCalcSteps() {
        return numberOfCompressorCalcSteps;
    }

    /**
     * <p>
     * Setter for the field <code>numberOfCompressorCalcSteps</code>.
     * </p>
     *
     * @param numberOfCompressorCalcSteps a int
     */
    public void setNumberOfCompressorCalcSteps(int numberOfCompressorCalcSteps) {
        this.numberOfCompressorCalcSteps = numberOfCompressorCalcSteps;
    }

    /**
     * <p>
     * isUseRigorousPolytropicMethod.
     * </p>
     *
     * @return a boolean
     */
    public boolean isUseRigorousPolytropicMethod() {
        return useRigorousPolytropicMethod;
    }

    /**
     * <p>
     * Setter for the field <code>useRigorousPolytropicMethod</code>.
     * </p>
     *
     * @param useRigorousPolytropicMethod a boolean
     */
    public void setUseRigorousPolytropicMethod(boolean useRigorousPolytropicMethod) {
        this.useRigorousPolytropicMethod = useRigorousPolytropicMethod;
    }

    /** {@inheritDoc} */
    @Override
    public void setPressure(double pressure) {
        setOutletPressure(pressure);
    }

    /**
     * <p>
     * Setter for the field <code>pressure</code>.
     * </p>
     *
     * @param pressure a double
     * @param unit a {@link java.lang.String} object
     */
    public void setPressure(double pressure, String unit) {
        setOutletPressure(pressure);
        pressureUnit = unit;
    }

    /** {@inheritDoc} */
    @Override
    public double getEntropyProduction(String unit) {
        return outStream.getThermoSystem().getEntropy(unit)
                - inletStream.getThermoSystem().getEntropy(unit);
    }

    /** {@inheritDoc} */
    @Override
    public double getExergyChange(String unit, double sourrondingTemperature) {
        return outStream.getThermoSystem().getExergy(sourrondingTemperature, unit)
                - inletStream.getThermoSystem().getExergy(sourrondingTemperature, unit);
    }

    /**
     * <p>
     * Getter for the field <code>polytropicMethod</code>.
     * </p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getPolytropicMethod() {
        return polytropicMethod;
    }

    /**
     * <p>
     * Setter for the field <code>polytropicMethod</code>.
     * </p>
     *
     * @param polytropicMethod a {@link java.lang.String} object
     */
    public void setPolytropicMethod(String polytropicMethod) {
        this.polytropicMethod = polytropicMethod;
    }

    public boolean isUseGERG2008() {
        return useGERG2008;
    }

    public void setUseGERG2008(boolean useGERG2008) {
        this.useGERG2008 = useGERG2008;
    }
}
