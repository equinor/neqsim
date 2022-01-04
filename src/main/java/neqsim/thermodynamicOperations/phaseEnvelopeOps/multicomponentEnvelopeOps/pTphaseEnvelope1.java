/*
 * pTphaseEnvelope.java
 *
 * Created on 14. oktober 2000, 21:59
 */
package neqsim.thermodynamicOperations.phaseEnvelopeOps.multicomponentEnvelopeOps;

import java.awt.FlowLayout;
import java.text.DecimalFormat;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.dataPresentation.JFreeChart.graph2b;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.BaseOperation;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * pTphaseEnvelope1 class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class pTphaseEnvelope1 extends BaseOperation {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(pTphaseEnvelope1.class);

    graph2b graph2 = null;
    SystemInterface system;
    boolean bubblePointFirst = true;
    double[] cricondenTherm = new double[3];
    double[] cricondenBar = new double[3];
    double phaseFraction = 1e-10;
    neqsim.dataPresentation.fileHandeling.createNetCDF.netCDF2D.NetCdf2D file1;
    neqsim.dataPresentation.fileHandeling.createNetCDF.netCDF2D.NetCdf2D file2;
    int i, j = 0, nummer = 0, iterations = 0, maxNumberOfIterations = 10000;
    double gibbsEnergy = 0, gibbsEnergyOld = 0;
    double Kold, deviation = 0, g0 = 0, g1 = 0, lowPres = 1.0;
    double lnOldOldK[], lnK[];
    boolean outputToFile = false;
    double lnOldK[];
    double oldDeltalnK[], deltalnK[];
    double tm[] = {1, 1};
    double beta = 1e-5;
    int lowestGibbsEnergyPhase = 0; // lowestGibbsEnergyPhase
    JProgressBar monitor;
    JFrame mainFrame;
    String fileName = "c:/file";
    JPanel mainPanel;
    double temp = 0, pres = 0, startPres = 0;
    double[][] points = new double[2][];
    double[] pointsH;
    double[][] pointsH2 = new double[4][];
    double[] pointsV;
    double[][] pointsV2 = new double[4][];
    double[] pointsS;
    double[][] pointsS2 = new double[4][];
    public double[][] points2 = new double[4][];
    double[][] points3 = new double[8][];
    boolean moreLines = false;
    int np = 0;
    // points[2] = new double[1000];
    int speceq = 0;

    /**
     * <p>
     * Constructor for pTphaseEnvelope1.
     * </p>
     */
    public pTphaseEnvelope1() {}

    /**
     * <p>
     * Constructor for pTphaseEnvelope1.
     * </p>
     *
     * @param system a {@link neqsim.thermo.system.SystemInterface} object
     * @param name a {@link java.lang.String} object
     * @param phaseFraction a double
     * @param lowPres a double
     * @param bubfirst a boolean
     */
    public pTphaseEnvelope1(SystemInterface system, String name, double phaseFraction,
            double lowPres, boolean bubfirst) {
        this.bubblePointFirst = bubfirst;
        if (name != null) {
            outputToFile = true;
            fileName = name;
        }
        this.system = system;
        this.phaseFraction = phaseFraction;
        lnOldOldK = new double[system.getPhase(0).getNumberOfComponents()];
        lnOldK = new double[system.getPhase(0).getNumberOfComponents()];
        lnK = new double[system.getPhase(0).getNumberOfComponents()];
        this.lowPres = lowPres;
        oldDeltalnK = new double[system.getPhase(0).getNumberOfComponents()];
        deltalnK = new double[system.getPhase(0).getNumberOfComponents()];
        mainFrame = new JFrame("Progress Bar");
        mainPanel = new JPanel();
        mainPanel.setSize(200, 100);
        mainFrame.getContentPane().setLayout(new FlowLayout());
        mainPanel.setLayout(new FlowLayout());
        mainFrame.setSize(200, 100);
        monitor = new JProgressBar(0, 00);
        monitor.setSize(200, 100);
        monitor.setStringPainted(true);
        mainPanel.add(monitor);
        mainFrame.getContentPane().add(mainPanel);
        mainFrame.setVisible(true);
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        try {
            points[0] = new double[10000];
            points[1] = new double[10000];

            pointsH = new double[10000];
            pointsV = new double[10000];
            pointsS = new double[10000];
            system.init(0);
            for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
                if (system.getPhase(0).getComponent(i).getIonicCharge() == 0) {
                    if (system.getPhase(0).getComponents()[i]
                            .getTC() < system.getPhase(0).getComponents()[i].getTC()) {
                        speceq = system.getPhase(0).getComponent(i).getComponentNumber();
                    }
                }
            }

            pres = lowPres;
            temp = system.getPhase(0).getComponent(speceq).getAntoineVaporTemperature(pres);
            // temp = system.getTemperature();
            system.setPressure(pres);

            system.setBeta(1e-10);

            ThermodynamicOperations testOps = new ThermodynamicOperations(system);

            system.init(0);
            for (int i = 0; i < 1000; i++) {
                temp += i * 4;
                system.setTemperature(temp);
                try {
                    if (bubblePointFirst) {
                        testOps.bubblePointTemperatureFlash();
                    } else {
                        testOps.dewPointTemperatureFlash();
                    }
                } catch (Exception e) {
                    e.toString();
                }
                double tempNy = system.getTemperature();

                if (!Double.isNaN(tempNy)) {
                    temp = tempNy;
                    break;
                }
            }

            system.setPressure(pres);
            system.setTemperature(temp);
            logger.info("temp: " + system.getTemperature());

            system.setBeta(phaseFraction);

            sysNewtonRhapsonPhaseEnvelope nonLinSolver = new sysNewtonRhapsonPhaseEnvelope(system,
                    2, system.getPhase(0).getNumberOfComponents());
            nonLinSolver.solve(1);

            startPres = system.getPressure();

            for (np = 1; np < 9500; np++) {
                if (np % 5 == 0) {
                    monitor.setValue(np);
                    monitor.setString("Calculated points: " + np);
                }

                nonLinSolver.calcInc(np);
                nonLinSolver.solve(np);

                if (system.getTemperature() > cricondenTherm[0]) {
                    cricondenTherm[1] = system.getPressure();
                    cricondenTherm[0] = system.getTemperature();
                }
                if (system.getPressure() > cricondenBar[1]) {
                    cricondenBar[0] = system.getTemperature();
                    cricondenBar[1] = system.getPressure();
                }

                if (Double.isNaN(system.getTemperature()) || Double.isNaN(system.getTemperature())
                        || system.getPressure() < startPres) {
                    points[0][np - 1] = points[0][np - 3];
                    points[1][np - 1] = points[1][np - 3];
                    pointsH[np - 1] = pointsH[np - 3];
                    pointsV[np - 1] = pointsV[np - 3];
                    pointsS[np - 1] = pointsS[np - 3];

                    // logger.info("avbryter" + np);
                    break;
                }
                // logger.info("Ideal pres: " + getPressure());
                // logger.info("temp: " + system.getTemperature());
                points[0][np - 1] = system.getTemperature();
                points[1][np - 1] = system.getPressure();
                pointsH[np - 1] = system.getPhase(1).getEnthalpy()
                        / system.getPhase(1).getNumberOfMolesInPhase()
                        / system.getPhase(1).getMolarMass() / 1e3;
                pointsV[np - 1] = system.getPhase(1).getDensity();
                pointsS[np - 1] = system.getPhase(1).getEntropy()
                        / system.getPhase(1).getNumberOfMolesInPhase()
                        / system.getPhase(1).getMolarMass() / 1e3;
            }

            int ncr = nonLinSolver.getNpCrit();
            int ncr2 = np - ncr;

            logger.info("ncr: " + ncr + "  ncr2 . " + ncr2);
            points2[0] = new double[ncr + 1];
            points2[1] = new double[ncr + 1];
            pointsH2[0] = new double[ncr + 1];
            pointsH2[1] = new double[ncr + 1];
            pointsS2[0] = new double[ncr + 1];
            pointsS2[1] = new double[ncr + 1];
            pointsV2[0] = new double[ncr + 1];
            pointsV2[1] = new double[ncr + 1];
            if (ncr2 > 2) {
                points2[2] = new double[ncr2 - 2];
                points2[3] = new double[ncr2 - 2];
                pointsH2[2] = new double[ncr2 - 2];
                pointsH2[3] = new double[ncr2 - 2];
                pointsV2[2] = new double[ncr2 - 2];
                pointsV2[3] = new double[ncr2 - 2];
                pointsS2[2] = new double[ncr2 - 2];
                pointsS2[3] = new double[ncr2 - 2];
            } else {
                points2[2] = new double[0];
                points2[3] = new double[0];
                pointsH2[2] = new double[0];
                pointsH2[3] = new double[0];
                pointsV2[2] = new double[0];
                pointsV2[3] = new double[0];
                pointsS2[2] = new double[0];
                pointsS2[3] = new double[0];
            }

            for (int i = 0; i < ncr; i++) {
                points2[0][i] = points[0][i];
                points2[1][i] = points[1][i];

                pointsH2[1][i] = points[1][i];
                pointsH2[0][i] = pointsH[i];

                pointsS2[1][i] = points[1][i];
                pointsS2[0][i] = pointsS[i];

                pointsV2[1][i] = points[1][i];
                pointsV2[0][i] = pointsV[i];
            }

            system.setTemperature(system.getTC() + 0.001);
            system.setPressure(system.getPC() + 0.001);
            system.init(3);

            points2[0][ncr] = system.getTC();
            points2[1][ncr] = system.getPC();

            pointsH2[1][ncr] = system.getPC();
            pointsH2[0][ncr] =
                    system.getPhase(1).getEnthalpy() / system.getPhase(1).getNumberOfMolesInPhase()
                            / system.getPhase(1).getMolarMass() / 1e3;

            pointsS2[1][ncr] = system.getPC();
            pointsS2[0][ncr] =
                    system.getPhase(1).getEntropy() / system.getPhase(1).getNumberOfMolesInPhase()
                            / system.getPhase(1).getMolarMass() / 1e3;

            pointsV2[1][ncr] = system.getPC();
            pointsV2[0][ncr] = system.getPhase(1).getDensity();

            if (ncr2 > 2) {
                points2[2][0] = system.getTC();
                points2[3][0] = system.getPC();
                pointsH2[3][0] = system.getPC();
                pointsH2[2][0] = system.getPhase(1).getEnthalpy()
                        / system.getPhase(1).getNumberOfMolesInPhase()
                        / system.getPhase(1).getMolarMass() / 1e3;
                pointsS2[3][0] = system.getPC();
                pointsS2[2][0] = system.getPhase(1).getEntropy()
                        / system.getPhase(1).getNumberOfMolesInPhase()
                        / system.getPhase(1).getMolarMass() / 1e3;
                pointsV2[3][0] = system.getPC();
                pointsV2[2][0] = system.getPhase(1).getDensity();

                for (int i = 1; i < (ncr2 - 2); i++) {
                    points2[2][i] = points[0][i + ncr - 1];
                    points2[3][i] = points[1][i + ncr - 1];

                    pointsH2[3][i] = points[1][i + ncr - 1];
                    pointsH2[2][i] = pointsH[i + ncr - 1];

                    pointsS2[3][i] = points[1][i + ncr - 1];
                    pointsS2[2][i] = pointsS[i + ncr - 1];

                    pointsV2[3][i] = points[1][i + ncr - 1];
                    pointsV2[2][i] = pointsV[i + ncr - 1];
                }
            }
            // monitor.close();
            mainFrame.setVisible(false);

            if (outputToFile) {
                String name1 = new String();
                name1 = fileName + "Dew.nc";
                file1 = new neqsim.dataPresentation.fileHandeling.createNetCDF.netCDF2D.NetCdf2D();
                file1.setOutputFileName(name1);
                file1.setXvalues(points2[2], "temp", "sec");
                file1.setYvalues(points2[3], "pres", "meter");
                file1.createFile();

                String name2 = new String();
                name2 = fileName + "Bub.nc";
                file2 = new neqsim.dataPresentation.fileHandeling.createNetCDF.netCDF2D.NetCdf2D();
                file2.setOutputFileName(name2);
                file2.setXvalues(points2[0], "temp", "sec");
                file2.setYvalues(points2[1], "pres", "meter");
                file2.createFile();
            }
        } catch (Exception e) {
            logger.error("error", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void displayResult() {
        DecimalFormat nf = new DecimalFormat();
        nf.setMaximumFractionDigits(1);
        nf.applyPattern("####.#");

        double TC = system.getTC();
        double PC = system.getPC();
        logger.info("tc : " + TC + "  PC : " + PC);
        String[] navn = {"bubble point", "dew point", "bubble point", "dew point"};
        String title2 = "";
        String title = "PT-graph  TC=" + String.valueOf(nf.format(TC)) + " PC="
                + String.valueOf(nf.format(PC));
        String title3 = "PH-graph  TC=" + String.valueOf(nf.format(TC)) + " PC="
                + String.valueOf(nf.format(PC));
        String title4 = "Density-graph  TC=" + String.valueOf(nf.format(TC)) + " PC="
                + String.valueOf(nf.format(PC));
        String title5 = "PS-graph  TC=" + String.valueOf(nf.format(TC)) + " PC="
                + String.valueOf(nf.format(PC));

        // logger.info("start flash");
        // logger.info("Tferdig..");

        graph2b graph3 = new graph2b(pointsH2, navn, title3, "Enthalpy [kJ/kg]", "Pressure [bara]");
        graph3.setVisible(true);
        graph3.saveFigure((neqsim.util.util.FileSystemSettings.tempDir + "NeqSimTempFig4.png"));

        graph2b graph4 = new graph2b(pointsV2, navn, title4, "Density [kg/m^3]", "Pressure [bara]");
        graph4.setVisible(true);
        graph4.saveFigure(neqsim.util.util.FileSystemSettings.tempDir + "NeqSimTempFig2.png");

        graph2b graph5 =
                new graph2b(pointsS2, navn, title5, "Entropy [kJ/kg*K]", "Pressure [bara]");
        graph5.setVisible(true);
        graph5.saveFigure(neqsim.util.util.FileSystemSettings.tempDir + "NeqSimTempFig3.png");

        graph2 = new graph2b(points2, navn, title, "Temperature [K]", "Pressure [bara]");
        graph2.setVisible(true);
        graph2.saveFigure(neqsim.util.util.FileSystemSettings.tempDir + "NeqSimTempFig1.png");

        /*
         * JDialog dialog = new JDialog(); Container dialogContentPane = dialog.getContentPane();
         * dialogContentPane.setLayout(new FlowLayout()); JFreeChartPanel chartPanel =
         * graph4.getChartPanel(); dialogContentPane.add(chartPanel); dialog.show();
         */
    }

    /** {@inheritDoc} */
    @Override
    public void printToFile(String name) {}

    /** {@inheritDoc} */
    @Override
    public org.jfree.chart.JFreeChart getJFreeChart(String name) {
        DecimalFormat nf = new DecimalFormat();
        nf.setMaximumFractionDigits(1);
        nf.applyPattern("####.#");

        double TC = system.getTC();
        double PC = system.getPC();
        logger.info("tc : " + TC + "  PC : " + PC);
        String[] navn = {"bubble point", "dew point", "bubble point", "dew point"};
        String title2 = "";
        String title = "PT-graph. TC=" + String.valueOf(nf.format(TC)) + "K, PC="
                + String.valueOf(nf.format(PC) + " bara");
        graph2 = new graph2b(points2, navn, title, "Temperature [K]", "Pressure [bara]");
        return graph2.getChart();
    }

    /** {@inheritDoc} */
    @Override
    public double[][] getPoints(int i) {
        return points2;
    }

    /** {@inheritDoc} */
    @Override
    public double[] get(String name) {
        if (name.equals("bubT")) {
            return points2[0];
        }
        if (name.equals("bubP")) {
            return points2[1];
        }
        if (name.equals("dewT")) {
            return points2[2];
        }
        if (name.equals("dewP")) {
            return points2[3];
        }
        if (name.equals("dewH")) {
            return pointsH2[2];
        }
        if (name.equals("dewDens")) {
            return pointsV2[2];
        }
        if (name.equals("dewS")) {
            return pointsS2[2];
        }
        if (name.equals("bubH")) {
            return pointsH2[0];
        }
        if (name.equals("bubDens")) {
            return pointsV2[0];
        }
        if (name.equals("bubS")) {
            return pointsS2[0];
        }
        if (name.equals("cricondentherm")) {
            return cricondenTherm;
        }
        if (name.equals("cricondenbar")) {
            return cricondenBar;
        } else {
            return null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void createNetCdfFile(String name) {
        fileName = name;
    }

    /**
     * Getter for property bubblePointFirst.
     *
     * @return Value of property bubblePointFirst.
     */
    public boolean isBubblePointFirst() {
        return bubblePointFirst;
    }

    /**
     * Setter for property bubblePointFirst.
     *
     * @param bubblePointFirst New value of property bubblePointFirst.
     */
    public void setBubblePointFirst(boolean bubblePointFirst) {
        this.bubblePointFirst = bubblePointFirst;
    }

    /** {@inheritDoc} */
    @Override
    public String[][] getResultTable() {
        return null;
    }
}
