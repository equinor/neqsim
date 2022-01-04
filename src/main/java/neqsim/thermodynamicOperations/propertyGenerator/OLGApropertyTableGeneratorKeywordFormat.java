package neqsim.thermodynamicOperations.propertyGenerator;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * OLGApropertyTableGeneratorKeywordFormat class.
 * </p>
 *
 * @author Kjetil Raul
 * @version $Id: $Id
 */
public class OLGApropertyTableGeneratorKeywordFormat
        extends neqsim.thermodynamicOperations.BaseOperation {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(OLGApropertyTableGeneratorKeywordFormat.class);

    SystemInterface thermoSystem = null;
    ThermodynamicOperations thermoOps = null;
    double stdPres = 1.01325, stdPresATM = 1, stdTemp = 288.15;
    double[] molfracs, MW, dens;
    String[] components;
    double GOR, GLR, stdGasDens, stdLiqDens;
    double[] pressures, temperatureLOG, temperatures, pressureLOG = null;
    double[][] ROG = null; // DROGDP, DROHLDP, DROGDT, DROHLDT;
    double[] bubP, bubT, dewP, bubPLOG, dewPLOG, bubTLOG;
    double[][] ROL, CPG, CPHL, HG, HHL, TCG, TCHL, VISG, VISHL, SIGGHL, SEG, SEHL, RS;
    double TC, PC, TCLOG, PCLOG;
    double[][][] props;
    int nProps;
    String[] names;
    String[] units;
    String[] namesKeyword;

    /**
     * <p>
     * Constructor for OLGApropertyTableGeneratorKeywordFormat.
     * </p>
     *
     * @param system a {@link neqsim.thermo.system.SystemInterface} object
     */
    public OLGApropertyTableGeneratorKeywordFormat(SystemInterface system) {
        this.thermoSystem = system;
        thermoOps = new ThermodynamicOperations(thermoSystem);
    }

    /**
     * <p>
     * setPressureRange.
     * </p>
     *
     * @param minPressure a double
     * @param maxPressure a double
     * @param numberOfSteps a int
     */
    public void setPressureRange(double minPressure, double maxPressure, int numberOfSteps) {
        pressures = new double[numberOfSteps];
        pressureLOG = new double[numberOfSteps];
        double step = (maxPressure - minPressure) / (numberOfSteps * 1.0 - 1.0);
        for (int i = 0; i < numberOfSteps; i++) {
            pressures[i] = minPressure + i * step;
            pressureLOG[i] = pressures[i] * 1e5;
        }
    }

    /**
     * <p>
     * setTemperatureRange.
     * </p>
     *
     * @param minTemperature a double
     * @param maxTemperature a double
     * @param numberOfSteps a int
     */
    public void setTemperatureRange(double minTemperature, double maxTemperature,
            int numberOfSteps) {
        temperatures = new double[numberOfSteps];
        temperatureLOG = new double[numberOfSteps];
        double step = (maxTemperature - minTemperature) / (numberOfSteps * 1.0 - 1.0);
        for (int i = 0; i < numberOfSteps; i++) {
            temperatures[i] = minTemperature + i * step;
            temperatureLOG[i] = temperatures[i] - 273.15;
        }
    }

    /**
     * <p>
     * calcPhaseEnvelope.
     * </p>
     */
    public void calcPhaseEnvelope() {
        try {
            thermoOps.calcPTphaseEnvelope();
            TCLOG = thermoSystem.getTC();
            PCLOG = thermoSystem.getPC() * 0.986923267; // convert to ATM
            TC = thermoSystem.getTC() - 273.15;
            PC = thermoSystem.getPC() * 1e5;
        } catch (Exception e) {
            logger.error("error", e);
        }

        // thermoOps.ge
    }

    /**
     * <p>
     * calcBubP.
     * </p>
     *
     * @param temperatures an array of {@link double} objects
     * @return an array of {@link double} objects
     */
    public double[] calcBubP(double[] temperatures) {
        double[] bubP = new double[temperatures.length];
        bubPLOG = new double[temperatures.length];
        for (int i = 0; i < temperatures.length; i++) {
            thermoSystem.setTemperature(temperatures[i]);
            try {
                thermoOps.bubblePointPressureFlash(false);
                bubP[i] = thermoSystem.getPressure();
                bubPLOG[i] = bubP[i] * 1e5;
            } catch (Exception e) {
                logger.error("error", e);
                bubP[i] = 0;
            }
        }
        return bubP;
    }

    /**
     * <p>
     * calcDewP.
     * </p>
     *
     * @param temperatures an array of {@link double} objects
     * @return an array of {@link double} objects
     */
    public double[] calcDewP(double[] temperatures) {
        double[] dewP = new double[temperatures.length];
        dewPLOG = new double[temperatures.length];
        for (int i = 0; i < temperatures.length; i++) {
            thermoSystem.setTemperature(temperatures[i]);
            try {
                thermoOps.dewPointPressureFlash();
                dewP[i] = thermoSystem.getPressure();
                dewPLOG[i] = dewP[i] * 1e5;
            } catch (Exception e) {
                logger.error("error", e);
                dewP[i] = 0;
            }
        }
        return dewP;
    }

    /**
     * <p>
     * calcBubT.
     * </p>
     *
     * @param pressures an array of {@link double} objects
     * @return an array of {@link double} objects
     */
    public double[] calcBubT(double[] pressures) {
        double[] bubT = new double[pressures.length];
        bubTLOG = new double[pressures.length];
        for (int i = 0; i < pressures.length; i++) {
            thermoSystem.setPressure(pressures[i]);
            try {
                thermoOps.bubblePointTemperatureFlash();
                bubT[i] = thermoSystem.getTemperature();
                bubTLOG[i] = bubT[i] - 273.15;
            } catch (Exception e) {
                logger.error("error", e);
                bubT[i] = 0.0;
            }
        }
        return bubT;
    }

    /**
     * <p>
     * initCalc.
     * </p>
     */
    public void initCalc() {
        double standgasdens, standliqdens, TC, PC;

        molfracs = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
        MW = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
        dens = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
        components = new String[thermoSystem.getPhase(0).getNumberOfComponents()];

        for (int i = 0; i < molfracs.length; i++) {
            molfracs[i] = thermoSystem.getPhase(0).getComponent(i).getz();
            components[i] = thermoSystem.getPhase(0).getComponent(i).getComponentName();
            MW[i] = thermoSystem.getPhase(0).getComponent(i).getMolarMass() * 1000;
            dens[i] = thermoSystem.getPhase(0).getComponent(i).getNormalLiquidDensity();
        }

        thermoSystem.setTemperature(stdTemp);
        thermoSystem.setPressure(stdPres);

        thermoOps.TPflash();
        thermoSystem.initPhysicalProperties();

        GOR = thermoSystem.getPhase(0).getTotalVolume() / thermoSystem.getPhase(1).getTotalVolume();
        GLR = thermoSystem.getPhase(0).getTotalVolume() / thermoSystem.getPhase(1).getTotalVolume();

        stdGasDens = thermoSystem.getPhase(0).getPhysicalProperties().getDensity();
        stdLiqDens = thermoSystem.getPhase(1).getPhysicalProperties().getDensity();
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        logger.info("Start creating arrays");

        nProps = 18;
        props = new double[nProps][pressures.length][temperatures.length];
        units = new String[nProps];
        names = new String[nProps];
        namesKeyword = new String[nProps];
        calcPhaseEnvelope();
        /*
         * ROG = new double[pressures.length][temperatures.length]; ROL = new
         * double[pressures.length][temperatures.length]; CPG = new
         * double[pressures.length][temperatures.length]; CPHL = new
         * double[pressures.length][temperatures.length]; HG = new
         * double[pressures.length][temperatures.length]; HHL = new
         * double[pressures.length][temperatures.length]; VISG = new
         * double[pressures.length][temperatures.length]; VISHL = new
         * double[pressures.length][temperatures.length]; TCG = new
         * double[pressures.length][temperatures.length]; TCHL = new
         * double[pressures.length][temperatures.length]; // SIGGHL = new
         * double[pressures.length][temperatures.length]; SEG = new
         * double[pressures.length][temperatures.length]; SEHL = new
         * double[pressures.length][temperatures.length]; RS = new
         * double[pressures.length][temperatures.length]; // DROGDP = new
         * double[pressures.length][temperatures.length]; // DROHLDP = new
         * double[pressures.length][temperatures.length]; // DROGDT = new
         * double[pressures.length][temperatures.length]; // DROHLDT = new
         * double[pressures.length][temperatures.length];
         */

        for (int i = 0; i < pressures.length; i++) {
            thermoSystem.setPressure(pressures[i]);
            for (int j = 0; j < temperatures.length; j++) {
                thermoSystem.setTemperature(temperatures[j]);
                try {
                    thermoOps.TPflash();
                } catch (Exception e) {
                    logger.error("error", e);
                }
                thermoSystem.init(3);
                thermoSystem.initPhysicalProperties();
                /*
                 * ROG[i][j] = thermoSystem.getPhase(0).getPhysicalProperties().getDensity();
                 * ROL[i][j] = thermoSystem.getPhase(1).getPhysicalProperties().getDensity(); //
                 * DROGDP[i][j] = thermoSystem.getPhase(0).getdrhodP(); // DROHLDP[i][j] =
                 * thermoSystem.getPhase(1).getdrhodP(); // DROGDT[i][j] =
                 * thermoSystem.getPhase(0).getdrhodT(); // DROHLDT[i][j] =
                 * thermoSystem.getPhase(1).getdrhodT(); CPG[i][j] =
                 * thermoSystem.getPhase(0).getCp(); CPHL[i][j] = thermoSystem.getPhase(1).getCp();
                 * HG[i][j] = thermoSystem.getPhase(0).getEnthalpy(); HHL[i][j] =
                 * thermoSystem.getPhase(1).getEnthalpy(); TCG[i][j] =
                 * thermoSystem.getPhase(0).getPhysicalProperties().getConductivity(); TCHL[i][j] =
                 * thermoSystem.getPhase(1).getPhysicalProperties().getConductivity(); VISG[i][j] =
                 * thermoSystem.getPhase(0).getPhysicalProperties().getViscosity(); VISHL[i][j] =
                 * thermoSystem.getPhase(1).getPhysicalProperties().getViscosity(); // SIGGHL[i][j]
                 * = thermoSystem.getInterphaseProperties().getSurfaceTension(0, 1); SEG[i][j] =
                 * thermoSystem.getPhase(0).getEntropy(); SEHL[i][j] =
                 * thermoSystem.getPhase(1).getEntropy(); RS[i][j] =
                 * thermoSystem.getPhase(0).getBeta();
                 */

                int k = 0;
                props[k][i][j] = thermoSystem.getPhase(0).getPhysicalProperties().getDensity();
                names[k] = "GAS DENSITY";
                units[k] = "KG/M3";
                namesKeyword[k] = "ROG";
                k++;
                props[k][i][j] = thermoSystem.getPhase(1).getPhysicalProperties().getDensity();
                names[k] = "LIQUID DENSITY";
                units[k] = "KG/M3";
                namesKeyword[k] = "ROHL";
                k++;
                props[k][i][j] = thermoSystem.getPhase(0).getdrhodP() / 1.0e5;
                names[k] = "DRHOG/DP";
                units[k] = "S2/M2";
                namesKeyword[k] = "DROGDP";
                k++;
                props[k][i][j] = thermoSystem.getPhase(1).getdrhodP() / 1.0e5;
                names[k] = "DRHOL/DP";
                units[k] = "S2/M2";
                namesKeyword[k] = "DROHLDP";
                k++;
                props[k][i][j] = thermoSystem.getPhase(0).getdrhodT();
                names[k] = "DRHOG/DT";
                units[k] = "KG/M3-K";
                namesKeyword[k] = "DROGDT";
                k++;
                props[k][i][j] = thermoSystem.getPhase(1).getdrhodT();
                names[k] = "DRHOL/DT";
                units[k] = "KG/M3-K";
                namesKeyword[k] = "DROHLDT";
                k++;
                props[k][i][j] = thermoSystem.getPhase(0).getBeta()
                        * thermoSystem.getPhase(0).getMolarMass() / thermoSystem.getMolarMass();
                names[k] = "GAS MASS FRACTION";
                units[k] = "-";
                namesKeyword[k] = "RS";
                k++;
                props[k][i][j] = thermoSystem.getPhase(0).getPhysicalProperties().getViscosity();
                names[k] = "GAS VISCOSITY";
                units[k] = "NS/M2";
                namesKeyword[k] = "VISG";
                k++;
                props[k][i][j] = thermoSystem.getPhase(1).getPhysicalProperties().getViscosity();
                names[k] = "LIQUID VISCOSITY";
                units[k] = "NS/M2";
                namesKeyword[k] = "VISHL";
                k++;
                props[k][i][j] = thermoSystem.getPhase(0).getCp()
                        / thermoSystem.getPhase(0).getNumberOfMolesInPhase()
                        / thermoSystem.getPhase(0).getMolarMass();
                names[k] = "GAS HEAT CAPACITY";
                units[k] = "J/KG-K";
                namesKeyword[k] = "CPG";
                k++;
                props[k][i][j] = thermoSystem.getPhase(1).getCp()
                        / thermoSystem.getPhase(1).getNumberOfMolesInPhase()
                        / thermoSystem.getPhase(1).getMolarMass();
                names[k] = "LIQUID HEAT CAPACITY";
                units[k] = "J/KG-K";
                namesKeyword[k] = "CPHL";
                k++;
                props[k][i][j] = thermoSystem.getPhase(0).getEnthalpy()
                        / thermoSystem.getPhase(0).getNumberOfMolesInPhase()
                        / thermoSystem.getPhase(0).getMolarMass();
                names[k] = "GAS ENTHALPY";
                units[k] = "J/KG";
                namesKeyword[k] = "HG";
                k++;
                props[k][i][j] = thermoSystem.getPhase(1).getEnthalpy()
                        / thermoSystem.getPhase(1).getNumberOfMolesInPhase()
                        / thermoSystem.getPhase(1).getMolarMass();
                names[k] = "LIQUD ENTHALPY";
                units[k] = "J/KG";
                namesKeyword[k] = "HHL";
                k++;
                props[k][i][j] = thermoSystem.getPhase(0).getPhysicalProperties().getConductivity();
                names[k] = "GAS THERMAL CONDUCTIVITY";
                units[k] = "W/M-K";
                namesKeyword[k] = "TCG";
                k++;
                props[k][i][j] = thermoSystem.getPhase(1).getPhysicalProperties().getConductivity();
                names[k] = "LIQUID THERMAL CONDUCTIVITY";
                units[k] = "W/M-K";
                namesKeyword[k] = "TCHL";
                k++;
                props[k][i][j] = thermoSystem.getInterphaseProperties().getSurfaceTension(0, 1);
                names[k] = "VAPOR-LIQUID SURFACE TENSION";
                units[k] = "N/M";
                namesKeyword[k] = "SIGGHL";
                k++;
                props[k][i][j] = thermoSystem.getPhase(0).getEntropy()
                        / thermoSystem.getPhase(0).getNumberOfMolesInPhase()
                        / thermoSystem.getPhase(0).getMolarMass();
                names[k] = "GAS ENTROPY";
                units[k] = "J/KG-K";
                namesKeyword[k] = "SEG";
                k++;
                props[k][i][j] = thermoSystem.getPhase(1).getEntropy()
                        / thermoSystem.getPhase(1).getNumberOfMolesInPhase()
                        / thermoSystem.getPhase(1).getMolarMass();
                names[k] = "LIQUID ENTROPY";
                units[k] = "J/KG-K";
                namesKeyword[k] = "SEHL";
                k++;
            }
        }
        bubP = calcBubP(temperatures);
        // dewP = calcDewP(temperatures);
        bubT = calcBubT(temperatures);
        logger.info("Finished creating arrays");
        initCalc();
    }

    /** {@inheritDoc} */
    @Override
    public void displayResult() {
        logger.info("TC " + TC + " PC " + PC);
        for (int i = 0; i < pressures.length; i++) {
            thermoSystem.setPressure(pressures[i]);
            for (int j = 0; j < temperatures.length; j++) {
                logger.info("pressure " + pressureLOG[i] + " temperature " + temperatureLOG[j]); // +
                                                                                                 // "
                                                                                                 // ROG
                                                                                                 // "
                                                                                                 // +
                                                                                                 // ROG[i][j]
                                                                                                 // +
                                                                                                 // "
                                                                                                 // ROL
                                                                                                 // "
                                                                                                 // +
                                                                                                 // ROL[i][j]);
            }
        }
        writeOLGAinpFile("");
    }

    /**
     * <p>
     * writeOLGAinpFile.
     * </p>
     *
     * @param filename a {@link java.lang.String} object
     */
    public void writeOLGAinpFile(String filename) {
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(
                "C:/Users/Kjetil Raul/Documents/Master KRB/2phaseTables/testFluidKeyCPAExtra.tab"),
                "utf-8"))) {
            writer.write("PVTTABLE LABEL = " + "\"" + "NewFluid" + "\"" + "," + "PHASE = TWO"
                    + ",\\" + "\n");
            writer.write("EOS = " + "\"" + "Equation" + "\"" + ",\\" + "\n");

            writer.write("COMPONENTS = (");
            for (int i = 0; i < molfracs.length; i++) {
                writer.write("\"" + components[i] + "\""); // How to set extra " ??
                if (i < molfracs.length - 1) {
                    writer.write(",");
                }
            }
            writer.write("),\\" + "\n");

            writer.write("MOLES = (");
            for (int i = 0; i < molfracs.length; i++) {
                writer.write(molfracs[i] + "");
                if (i < molfracs.length - 1) {
                    writer.write(",");
                }
            }
            writer.write("),\\" + "\n");

            writer.write("MOLWEIGHT = (");
            for (int i = 0; i < molfracs.length; i++) {
                writer.write(MW[i] + "");
                if (i < molfracs.length - 1) {
                    writer.write(",");
                }
            }
            writer.write(") g/mol,\\" + "\n");

            writer.write("DENSITY = (");
            for (int i = 0; i < molfracs.length; i++) {
                writer.write(dens[i] + "");
                if (i < molfracs.length - 1) {
                    writer.write(",");
                }
            }
            writer.write(") g/cm3,\\" + "\n");

            writer.write("STDPRESSURE = " + stdPresATM + " ATM,\\" + "\n");
            writer.write("STDTEMPERATURE = " + stdTemp + " K,\\" + "\n");
            writer.write("GOR = " + GOR + " Sm3/Sm3,\\" + "\n");
            writer.write("GLR = " + GLR + " Sm3/Sm3,\\" + "\n");
            writer.write("STDGASDENSITY = " + stdGasDens + " kg/m3,\\" + "\n");
            writer.write("STDOILDENSITY = " + stdLiqDens + " kg/m3,\\" + "\n");
            writer.write("CRITICALPRESSURE = " + PCLOG + " ATM,\\" + "\n");
            writer.write("CRITICALTEMPERATURE = " + TCLOG + " K,\\" + "\n");

            writer.write("MESHTYPE = STANDARD" + ",\\" + "\n");

            writer.write("PRESSURE = (");
            for (int i = 0; i < pressures.length; i++) {
                writer.write(pressureLOG[i] + "");
                if (i < pressures.length - 1) {
                    writer.write(",");
                }
            }
            writer.write(") Pa,\\" + "\n");

            writer.write("TEMPERATURE = (");
            for (int i = 0; i < temperatures.length; i++) {
                writer.write(temperatureLOG[i] + "");
                if (i < temperatures.length - 1) {
                    writer.write(",");
                }
            }
            writer.write(") C,\\" + "\n");

            writer.write("BUBBLEPRESSURES = (");
            for (int i = 0; i < temperatures.length; i++) {
                writer.write(bubPLOG[i] + "");
                if (i < temperatures.length - 1) {
                    writer.write(",");
                }
            }
            writer.write(") Pa,\\" + "\n");

            writer.write("BUBBLETEMPERATURES = (");
            for (int i = 0; i < pressures.length; i++) {
                writer.write(bubTLOG[i] + "");
                if (i < pressures.length - 1) {
                    writer.write(",");
                }
            }
            writer.write(") C,\\" + "\n");

            writer.write("COLUMNS = (PT,TM,");
            for (int k = 0; k < nProps; k++) {
                writer.write(namesKeyword[k] + "");
                if (k < nProps - 1) {
                    writer.write(",");
                }
            }
            writer.write(")" + "\n");

            for (int i = 0; i < pressures.length; i++) {
                thermoSystem.setPressure(pressures[i]);
                for (int j = 0; j < temperatures.length; j++) {
                    thermoSystem.setTemperature(temperatures[j]);
                    writer.write("PVTTABLE POINT = (");
                    writer.write(pressureLOG[i] + ",");
                    writer.write(temperatureLOG[j] + ",");
                    for (int k = 0; k < nProps; k++) {
                        writer.write(props[k][i][j] + "");
                        if (k < nProps - 1) {
                            writer.write(",");
                        }
                    }
                    writer.write(")" + "\n");
                }
            }
        } catch (IOException ex) {
            // report
        }
    }
}
