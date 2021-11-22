package neqsim.processSimulation.processEquipment.pump;

import java.awt.Container;
import java.awt.FlowLayout;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author esol
 * @version
 */
public class Pump extends ProcessEquipmentBaseClass implements PumpInterface {
    private static final long serialVersionUID = 1000;

    SystemInterface thermoSystem;
    private StreamInterface inStream;
    StreamInterface outStream;
    double dH = 0.0;
    double pressure = 0.0;
    private double molarFlow = 10.0;
    private double speed = 1000.0;

    private double outTemperature = 298.15;
    private boolean useOutTemperature = false;
    private boolean calculateAsCompressor = true;
    public double isentropicEfficiency = 1.0;
    public boolean powerSet = false;
    private String pressureUnit = "bara";
    private PumpChart pumpChart = new PumpChart();

    /**
     * Creates new ThrottelValve
     */
    public Pump() {}

    public Pump(StreamInterface inletStream) {
        setInletStream(inletStream);
    }

    public Pump(String name, StreamInterface inletStream) {
        super(name);
        setInletStream(inletStream);
    }

    @Override
    public void setInletStream(StreamInterface inletStream) {
        this.inStream = inletStream;

        this.outStream = (StreamInterface) inletStream.clone();
    }

    @Override
    public void setOutletPressure(double pressure) {
        this.pressure = pressure;
    }

    @Override
    public double getEnergy() {
        return dH;
    }

    @Override
    public double getPower() {
        return dH;
    }

    public double getPower(String unit) {
        if (unit.equals("W")) {
            return dH;
        } else if (unit.equals("kW")) {
            return dH / 1000.0;
        } else if (unit.equals("MW")) {
            return dH / 1.0e6;
        }
        return dH;
    }

    public double getDuty() {
        return dH;
    }

    @Override
    public StreamInterface getOutStream() {
        return outStream;
    }

    public void calculateAsCompressor(boolean setPumpCalcType) {
        calculateAsCompressor = setPumpCalcType;
    }

    @Override
    public void run() {
        // System.out.println("pump running..");
        inStream.getThermoSystem().init(3);
        double hinn = inStream.getThermoSystem().getEnthalpy();
        double entropy = inStream.getThermoSystem().getEntropy();

        if (useOutTemperature) {
            thermoSystem = (SystemInterface) inStream.getThermoSystem().clone();
            ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
            // thermoSystem.setTotalNumberOfMoles(molarFlow);
            thermoSystem.setTemperature(outTemperature);
            thermoSystem.setPressure(pressure, pressureUnit);
            thermoOps.TPflash();
            thermoSystem.init(3);
        } else {
            if (calculateAsCompressor) {
                thermoSystem = (SystemInterface) inStream.getThermoSystem().clone();
                thermoSystem.setPressure(pressure, pressureUnit);
                // System.out.println("entropy inn.." + entropy);
                ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
                thermoOps.PSflash(entropy);
                // double densOutIdeal = getThermoSystem().getDensity();
                if (!powerSet) {
                    dH = (getThermoSystem().getEnthalpy() - hinn) / isentropicEfficiency;
                }
                double hout = hinn + dH;
                isentropicEfficiency = (getThermoSystem().getEnthalpy() - hinn) / dH;
                dH = hout - hinn;
                thermoOps = new ThermodynamicOperations(getThermoSystem());
                thermoOps.PHflash(hout, 0);
            } else if (pumpChart.isUsePumpChart()) {
                double pumpHead = 0.0;
                pumpHead = getPumpChart().getHead(thermoSystem.getFlowRate("m3/hr"), getSpeed());
                isentropicEfficiency =
                        getPumpChart().getEfficiency(thermoSystem.getFlowRate("m3/hr"), getSpeed());
                double deltaP = pumpHead * 1000.0 * ThermodynamicConstantsInterface.gravity / 1.0E5;
                thermoSystem = (SystemInterface) inStream.getThermoSystem().clone();
                thermoSystem.setPressure(inStream.getPressure() + deltaP);
                double dH = thermoSystem.getFlowRate("kg/sec") / thermoSystem.getDensity("kg/m3")
                        * (thermoSystem.getPressure("Pa")
                                - inStream.getThermoSystem().getPressure("Pa"))
                        / (isentropicEfficiency / 100.0);
                ThermodynamicOperations thermoOps = new ThermodynamicOperations(getThermoSystem());
                double hout = hinn + dH;
                thermoOps.PHflash(hout, 0);
                thermoSystem.init(3);
            } else {
                thermoSystem = (SystemInterface) inStream.getThermoSystem().clone();
                thermoSystem.setPressure(pressure, pressureUnit);
                double dH = thermoSystem.getFlowRate("kg/sec") / thermoSystem.getDensity("kg/m3")
                        * (thermoSystem.getPressure("Pa")
                                - inStream.getThermoSystem().getPressure("Pa"))
                        / isentropicEfficiency;
                ThermodynamicOperations thermoOps = new ThermodynamicOperations(getThermoSystem());
                double hout = hinn + dH;
                thermoOps.PHflash(hout, 0);
                thermoSystem.init(3);
            }
        }

        // double entropy= inletStream.getThermoSystem().getEntropy();
        // thermoSystem.setPressure(pressure);
        // System.out.println("entropy inn.." + entropy);
        // thermoOps.PSflash(entropy);
        dH = thermoSystem.getEnthalpy() - hinn;
        outStream.setThermoSystem(thermoSystem);

        // outStream.run();
    }

    @Override
    public void displayResult() {

        DecimalFormat nf = new DecimalFormat();
        nf.setMaximumFractionDigits(5);
        nf.applyPattern("#.#####E0");

        JDialog dialog = new JDialog(new JFrame(), "Results from TPflash");
        Container dialogContentPane = dialog.getContentPane();
        dialogContentPane.setLayout(new FlowLayout());

        thermoSystem.initPhysicalProperties();
        String[][] table = new String[50][5];
        String[] names = {"", "Phase 1", "Phase 2", "Phase 3", "Unit"};
        table[0][0] = "";
        table[0][1] = "";
        table[0][2] = "";
        table[0][3] = "";
        StringBuffer buf = new StringBuffer();
        FieldPosition test = new FieldPosition(0);

        for (int i = 0; i < thermoSystem.getNumberOfPhases(); i++) {
            for (int j = 0; j < thermoSystem.getPhases()[0].getNumberOfComponents(); j++) {
                table[j + 1][0] = thermoSystem.getPhases()[0].getComponents()[j].getName();
                buf = new StringBuffer();
                table[j + 1][i + 1] =
                        nf.format(thermoSystem.getPhases()[i].getComponents()[j].getx(), buf, test)
                                .toString();
                table[j + 1][4] = "[-]";
            }
            buf = new StringBuffer();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 2][0] = "Density";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 2][i + 1] =
                    nf.format(thermoSystem.getPhases()[i].getPhysicalProperties().getDensity(), buf,
                            test).toString();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 2][4] = "[kg/m^3]";

            // Double.longValue(thermoSystem.getPhases()[i].getBeta());
            buf = new StringBuffer();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 3][0] = "PhaseFraction";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 3][i + 1] =
                    nf.format(thermoSystem.getPhases()[i].getBeta(), buf, test).toString();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 3][4] = "[-]";

            buf = new StringBuffer();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 4][0] = "MolarMass";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 4][i + 1] =
                    nf.format(thermoSystem.getPhases()[i].getMolarMass() * 1000, buf, test)
                            .toString();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 4][4] = "[kg/kmol]";

            buf = new StringBuffer();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 5][0] = "Cp";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 5][i + 1] = nf
                    .format((thermoSystem.getPhases()[i].getCp()
                            / thermoSystem.getPhases()[i].getNumberOfMolesInPhase() * 1.0
                            / thermoSystem.getPhases()[i].getMolarMass() * 1000), buf, test)
                    .toString();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 5][4] = "[kJ/kg*K]";

            buf = new StringBuffer();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 7][0] = "Viscosity";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 7][i + 1] =
                    nf.format((thermoSystem.getPhases()[i].getPhysicalProperties().getViscosity()),
                            buf, test).toString();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 7][4] = "[kg/m*sec]";

            buf = new StringBuffer();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 8][0] = "Conductivity";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 8][i + 1] =
                    nf.format(thermoSystem.getPhases()[i].getPhysicalProperties().getConductivity(),
                            buf, test).toString();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 8][4] = "[W/m*K]";

            buf = new StringBuffer();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 10][0] = "Pressure";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 10][i + 1] =
                    Double.toString(thermoSystem.getPhases()[i].getPressure());
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 10][4] = "[bar]";

            buf = new StringBuffer();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 11][0] = "Temperature";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 11][i + 1] =
                    Double.toString(thermoSystem.getPhases()[i].getTemperature());
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 11][4] = "[K]";
            Double.toString(thermoSystem.getPhases()[i].getTemperature());

            buf = new StringBuffer();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 13][0] = "Stream";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 13][i + 1] = name;
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 13][4] = "-";
        }

        JTable Jtab = new JTable(table, names);
        JScrollPane scrollpane = new JScrollPane(Jtab);
        dialogContentPane.add(scrollpane);
        dialog.pack();
        dialog.setVisible(true);
    }

    @Override
    public void runTransient() {}

    public double getMolarFlow() {
        return molarFlow;
    }

    public void setMolarFlow(double molarFlow) {
        this.molarFlow = molarFlow;
    }

    @Override
    public SystemInterface getThermoSystem() {
        return thermoSystem;
    }

    /**
     * @return the isentropicEfficientcy
     */
    public double getIsentropicEfficiency() {
        return isentropicEfficiency;
    }

    /**
     * @param isentropicEfficientcy the isentropicEfficientcy to set
     */
    public void setIsentropicEfficiency(double isentropicEfficientcy) {
        this.isentropicEfficiency = isentropicEfficientcy;
    }

    public double getOutTemperature() {
        if (useOutTemperature)
            return outTemperature;
        else
            return getThermoSystem().getTemperature();
    }

    public void setOutTemperature(double outTemperature) {
        useOutTemperature = true;
        this.outTemperature = outTemperature;
    }

    @Override
    public double getEntropyProduction(String unit) {
        return outStream.getThermoSystem().getEntropy(unit)
                - inStream.getThermoSystem().getEntropy(unit);
    }

    @Override
    public void setPressure(double pressure) {
        setOutletPressure(pressure);
    }

    public void setPressure(double pressure, String unit) {
        setOutletPressure(pressure);
        pressureUnit = unit;
    }

    public static void main(String[] args) {
        // Create the input fluid to the TEG process and saturate it with water at
        // scrubber conditions
        neqsim.thermo.system.SystemInterface feedGas =
                new neqsim.thermo.system.SystemSrkEos(273.15 + 20.0, 10.00);
        feedGas.addComponent("water", 1.0);

        neqsim.processSimulation.processEquipment.stream.Stream feedGasStream =
                new neqsim.processSimulation.processEquipment.stream.Stream("feed fluid", feedGas);
        feedGasStream.setFlowRate(4000.0 * 1000, "kg/hr");
        feedGasStream.setTemperature(20.0, "C");
        feedGasStream.setPressure(1.0, "bara");
        feedGasStream.run();

        Pump pump1 = new Pump(feedGasStream);
        pump1.setOutletPressure(12.6);
        pump1.calculateAsCompressor(false);

        pump1.run();

        System.out.println("Pump duty " + pump1.getDuty() / 1E3 + " kW");
        System.out.println(
                "Pump outlet temperature " + pump1.getOutStream().getTemperature("C") + " C");

        double[] chartConditions = new double[] {0.3, 1.0, 1.0, 1.0};
        double[] speed = new double[] {350.0, 1000.0};
        double[][] flow = new double[][] {
                {2789.1285, 3174.0375, 3689.2288, 4179.4503, 4570.2768, 4954.7728, 5246.0329,
                        5661.0331},
                {2571.1753, 2943.7254, 3440.2675, 3837.4448, 4253.0898, 4668.6643, 4997.1926,
                        5387.4952}};
        double[][] head = new double[][] {
                {80.0375, 78.8934, 76.2142, 71.8678, 67.0062, 60.6061, 53.0499, 39.728},
                {72.2122, 71.8369, 68.9009, 65.8341, 60.7167, 54.702, 47.2749, 35.7471},
                {65.1576, 64.5253, 62.6118, 59.1619, 54.0455, 47.0059, 39.195, 31.6387},
                {58.6154, 56.9627, 54.6647, 50.4462, 44.4322, 38.4144, 32.9084, 28.8109},
                {52.3295, 51.0573, 49.5283, 46.3326, 42.3685, 37.2502, 31.4884, 25.598},
                {40.6578, 39.6416, 37.6008, 34.6603, 30.9503, 27.1116, 23.2713, 20.4546},
                {35.2705, 34.6359, 32.7228, 31.0645, 27.0985, 22.7482, 18.0113},
                {32.192, 31.1756, 29.1329, 26.833, 23.8909, 21.3324, 18.7726, 16.3403},};
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

        // double[] chartConditions = new double[] { 0.3, 1.0, 1.0, 1.0 };
        // double[] speed = new double[] { 13402.0 };
        // double[][] flow = new double[][] { { 1050.0, 1260.0, 1650.0, 1950.0 } };
        // double[][] head = new double[][] { { 8555.0, 8227.0, 6918.0, 5223.0 } };
        // double[][] head = new double[][] { { 85.0, 82.0, 69.0, 52.0 } };
        // double[][] polyEff = new double[][] { { 66.8, 69.0, 66.4, 55.6 } };
        pump1.getPumpChart().setCurves(chartConditions, speed, flow, head, polyEff);
        pump1.getPumpChart().setHeadUnit("meter");
        pump1.setSpeed(500);
        pump1.run();
        System.out.println("Pressure out " + pump1.getOutStream().getPressure("bara") + " bara");
        System.out.println("Pump duty " + pump1.getDuty() / 1E3 + " kW");
        System.out.println(
                "Pump outlet temperature " + pump1.getOutStream().getTemperature("C") + " C");
    }

    public PumpChart getPumpChart() {
        return pumpChart;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public double getSpeed() {
        return speed;
    }

    public StreamInterface getInStream() {
        return inStream;
    }
}
