/*
 * Copyright 2018 ESOL.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * pTphaseEnvelope.java
 *
 * Created on 14. oktober 2000, 21:59
 */
package neqsim.thermodynamicOperations.phaseEnvelopeOps.multicomponentEnvelopeOps;

import java.text.*;
import javax.swing.*;
import neqsim.dataPresentation.JFreeChart.graph2b;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.BaseOperation;
import neqsim.thermodynamicOperations.OperationInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.log4j.Logger;
import org.jfree.chart.JFreeChart;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class pTphaseEnvelope extends BaseOperation implements OperationInterface, java.io.Serializable {

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(pTphaseEnvelope.class);

    double maxPressure = 500.0;
    double[][] copiedPoints = null;
    graph2b graph2 = null;
    SystemInterface system;
    boolean bubblePointFirst = true;
    boolean hascopiedPoints = false;
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
    //points[2] = new double[1000];
    int speceq = 0;
    String[] navn = {"bubble point", "dew point", "bubble point", "dew point", "dew points"};

    /**
     * Creates new bubblePointFlash
     */
    public pTphaseEnvelope() {
    }

    public pTphaseEnvelope(SystemInterface system, String name, double phaseFraction, double lowPres, boolean bubfirst) {
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
        /*
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
         * */
    }

    public void run() {
        speceq = 0;
        try {

            points[0] = new double[10000];
            points[1] = new double[10000];

            pointsH = new double[10000];
            pointsV = new double[10000];
            pointsS = new double[10000];
            system.init(0);
            for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
                if (system.getPhase(0).getComponent(i).getIonicCharge() == 0) {
                    if (bubblePointFirst == true && system.getPhase(0).getComponents()[speceq].getTC() > system.getPhase(0).getComponents()[i].getTC()) {
                        speceq = system.getPhase(0).getComponent(i).getComponentNumber();
                    }
                    if (bubblePointFirst == false && system.getPhase(0).getComponents()[speceq].getTC() < system.getPhase(0).getComponents()[i].getTC()) {
                        speceq = system.getPhase(0).getComponent(i).getComponentNumber();
                    }
                }
            }

            
            pres = lowPres;
            temp = system.getPhase(0).getComponent(speceq).getAntoineVaporTemperature(pres);
            if(Double.isNaN(temp)) {
                temp = system.getPhase(0).getComponent(speceq).getTC()-20.0;
            }
            //temp =  system.getTemperature();//
            logger.info("antoine temperature: " + system.getTemperature());
            system.setPressure(pres);

            //system.setBeta(1e-10);

            ThermodynamicOperations testOps = new ThermodynamicOperations(system);

            system.init(0);
            for (int i = 0; i < 1000; i++) {
                temp += i * 4;
                system.setTemperature(temp);
                try {
                    if (bubblePointFirst && phaseFraction < 0.5) {
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
            system.setBeta(phaseFraction);
            system.setPressure(pres);
            system.setTemperature(temp);
            logger.info("start pressure: " + system.getPressure());
            logger.info("start temperature: " + system.getTemperature());

            //system.setBeta(0,phaseFraction);
            sysNewtonRhapsonPhaseEnvelope nonLinSolver = new sysNewtonRhapsonPhaseEnvelope(system, 2, system.getPhase(0).getNumberOfComponents());
            //  nonLinSolver.solve(1);

            startPres = system.getPressure();
            for (np = 1; np < 9980; np++) {

                if (np % 5 == 0) {
                    // monitor.setValue(np);
                    // monitor.setString("Calculated points: " + np);
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

                if (Double.isNaN(system.getTemperature()) || Double.isNaN(system.getTemperature()) || system.getPressure() < startPres || system.getPressure() > maxPressure) {
                    points[0][np - 1] = points[0][np - 3];
                    points[1][np - 1] = points[1][np - 3];
                    pointsH[np - 1] = pointsH[np - 3];
                    pointsV[np - 1] = pointsV[np - 3];
                    pointsS[np - 1] = pointsS[np - 3];
                    if (Double.isNaN(system.getTemperature()) || Double.isNaN(system.getTemperature())) {
                        if (this.bubblePointFirst) {
                            copiedPoints = new double[2][np - 1];
                            for (int i = 0; i < np - 1; i++) {
                                copiedPoints[0][i] = points[0][i];
                                copiedPoints[1][i] = points[1][i];
                            }
                            phaseFraction = 1.0 - phaseFraction;
                            this.bubblePointFirst = false;
                            hascopiedPoints = true;
                            run();
                            return;
                        }
                    }
                    //         logger.info("avbryter" +  np);
                    break;
                }
                //    logger.info("Ideal pres: " + getPressure());
                // logger.info("temp: " + system.getTemperature());
                points[0][np - 1] = system.getTemperature();
                points[1][np - 1] = system.getPressure();
                pointsH[np - 1] = system.getPhase(1).getEnthalpy() / system.getPhase(1).getNumberOfMolesInPhase() / system.getPhase(1).getMolarMass() / 1e3;
                pointsV[np - 1] = system.getPhase(1).getDensity();
                pointsS[np - 1] = system.getPhase(1).getEntropy() / system.getPhase(1).getNumberOfMolesInPhase() / system.getPhase(1).getMolarMass() / 1e3;

            }

            int ncr = nonLinSolver.getNpCrit();
            int ncr2 = np - ncr;
            if (hascopiedPoints) {
                points2 = new double[6][];
                points2[4] = copiedPoints[0];
                points2[5] = copiedPoints[1];
            }
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
            pointsH2[0][ncr] = system.getPhase(1).getEnthalpy() / system.getPhase(1).getNumberOfMolesInPhase() / system.getPhase(1).getMolarMass() / 1e3;


            pointsS2[1][ncr] = system.getPC();
            pointsS2[0][ncr] = system.getPhase(1).getEntropy() / system.getPhase(1).getNumberOfMolesInPhase() / system.getPhase(1).getMolarMass() / 1e3;

            pointsV2[1][ncr] = system.getPC();
            pointsV2[0][ncr] = system.getPhase(1).getDensity();

            if (ncr2 > 2) {
                points2[2][0] = system.getTC();
                points2[3][0] = system.getPC();
                pointsH2[3][0] = system.getPC();
                pointsH2[2][0] = system.getPhase(1).getEnthalpy() / system.getPhase(1).getNumberOfMolesInPhase() / system.getPhase(1).getMolarMass() / 1e3;
                pointsS2[3][0] = system.getPC();
                pointsS2[2][0] = system.getPhase(1).getEntropy() / system.getPhase(1).getNumberOfMolesInPhase() / system.getPhase(1).getMolarMass() / 1e3;
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


            //        monitor.close();
            // mainFrame.setVisible(false);

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
            logger.error("error",e);
        }
    }

    public void calcHydrateLine() {

        ThermodynamicOperations opsHyd = new ThermodynamicOperations(system);
        try {
            opsHyd.hydrateEquilibriumLine(10.0, 300.0);
        } catch (Exception e) {
            logger.error("error",e);
        }

        double[][] hydData = opsHyd.getData();

    }

    public void displayResult() {
        DecimalFormat nf = new DecimalFormat();
        nf.setMaximumFractionDigits(1);
        nf.applyPattern("####.#");
        if (!bubblePointFirst) {
            navn[0] = "dew point";
            navn[1] = "bubble point";
            navn[2] = "bubble point";
            // navn[3] = "bubble point";
        }
        double TC = system.getTC();
        double PC = system.getPC();
        logger.info("tc : " + TC + "  PC : " + PC);

        String title2 = "";
        String title = "PT-graph  TC=" + String.valueOf(nf.format(TC)) + " PC=" + String.valueOf(nf.format(PC));
        String title3 = "PH-graph  TC=" + String.valueOf(nf.format(TC)) + " PC=" + String.valueOf(nf.format(PC));
        String title4 = "Density-graph  TC=" + String.valueOf(nf.format(TC)) + " PC=" + String.valueOf(nf.format(PC));
        String title5 = "PS-graph  TC=" + String.valueOf(nf.format(TC)) + " PC=" + String.valueOf(nf.format(PC));

        //    logger.info("start flash");
        //    logger.info("Tferdig..");



        graph2b graph3 = new graph2b(pointsH2, navn, title3, "Enthalpy [kJ/kg]", "Pressure [bara]");
        graph3.setVisible(true);
        //graph3.saveFigure(new String(util.util.FileSystemSettings.tempDir + "NeqSimTempFig4.png"));

        graph2b graph4 = new graph2b(pointsV2, navn, title4, "Density [kg/m^3]", "Pressure [bara]");
        graph4.setVisible(true);
        //graph4.saveFigure(util.util.FileSystemSettings.tempDir + "NeqSimTempFig2.png");

        graph2b graph5 = new graph2b(pointsS2, navn, title5, "Entropy [kJ/kg*K]", "Pressure [bara]");
        graph5.setVisible(true);
        //graph5.saveFigure(util.util.FileSystemSettings.tempDir + "NeqSimTempFig3.png");

        graph2 = new graph2b(points2, navn, title, "Temperature [K]", "Pressure [bara]");
        graph2.setVisible(true);
        //graph2.saveFigure(util.util.FileSystemSettings.tempDir + "NeqSimTempFig1.png");

        /*
         * JDialog dialog = new JDialog(); Container dialogContentPane =
         * dialog.getContentPane(); dialogContentPane.setLayout(new
         * FlowLayout()); JFreeChartPanel chartPanel = graph4.getChartPanel();
         * dialogContentPane.add(chartPanel); dialog.show();
         */
    }

    public void printToFile(String name) {
    }

    public JFreeChart getJFreeChart(String type) {
        DecimalFormat nf = new DecimalFormat();
        nf.setMaximumFractionDigits(1);
        nf.applyPattern("####.#");
        if (!bubblePointFirst) {
            navn[0] = "dew point";
            navn[1] = "bubble point";
            navn[2] = "bubble point";
            // navn[3] = "bubble point";
        }

        double TC = system.getTC();
        double PC = system.getPC();
        logger.info("tc : " + TC + "  PC : " + PC);
        String title2 = "";
        //String title = "PT-graph. TC=" + title2.valueOf(nf.format(TC)) + "K, PC=" + title2.valueOf(nf.format(PC) + " bara");
        String title = "";//Phase envelope";

        graph2 = new graph2b(points2, navn, title, "Temperature [K]", "Pressure [bara]");
        return graph2.getChart();
    }

    public double[][] getPoints(int i) {
        return points2;
    }

    public void addData(String name, double[][] data) {
        double[][] localPoints = new double[points2.length + data.length][];
        navn[localPoints.length / 2 - 1] = name;
        System.arraycopy(points2, 0, localPoints, 0, points2.length);
        System.arraycopy(data, 0, localPoints, points2.length, data.length);
        points2 = localPoints;
    }

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
        }
        if (name.equals("criticalPoint1")) {
            return new double[]{system.getTC(), system.getPC()};
        }
        if (name.equals("criticalPoint2")) {
            return new double[]{0, 0};
        } else {
            return null;
        }
    }

    public void createNetCdfFile(String name) {
        fileName = name;
    }

    /**
     * Getter for property bubblePointFirst.
     *
     * @return Value of property bubblePointFirst.
     *
     */
    public boolean isBubblePointFirst() {
        return bubblePointFirst;
    }

    /**
     * Setter for property bubblePointFirst.
     *
     * @param bubblePointFirst New value of property bubblePointFirst.
     *
     */
    public void setBubblePointFirst(boolean bubblePointFirst) {
        this.bubblePointFirst = bubblePointFirst;
    }

    public String[][] getResultTable() {
        return null;
    }
}
