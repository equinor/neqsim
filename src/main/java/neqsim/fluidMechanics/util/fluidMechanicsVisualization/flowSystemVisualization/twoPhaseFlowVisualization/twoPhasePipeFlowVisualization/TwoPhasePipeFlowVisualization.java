/*
 * PipeFlowVisualization.java
 *
 * Created on 26. oktober 2000, 20:09
 */
package neqsim.fluidMechanics.util.fluidMechanicsVisualization.flowSystemVisualization.twoPhaseFlowVisualization.twoPhasePipeFlowVisualization;

import neqsim.dataPresentation.visAD.visAdInterface;
import neqsim.fluidMechanics.flowSystem.FlowSystem;
import neqsim.fluidMechanics.util.fluidMechanicsVisualization.flowNodeVisualization.twoPhaseFlowNodeVisualization.TwoPhaseFlowNodeVisualization;

/**
 * <p>
 * TwoPhasePipeFlowVisualization class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class TwoPhasePipeFlowVisualization extends
        neqsim.fluidMechanics.util.fluidMechanicsVisualization.flowSystemVisualization.twoPhaseFlowVisualization.TwoPhaseFlowVisualization {
    private static final long serialVersionUID = 1000;

    double[][][] pressurePoint = new double[2][10][10];
    double[][][] velocityPoint = new double[2][10][10];
    double[][][] reynoldsNumber = new double[2][10][10];
    double[][][] phaseFraction = new double[2][10][10];
    double[][][] temperaturePoint = new double[2][10][10];
    double[][][] interphaseContactLength = new double[2][10][10];
    double[][][] interfaceTemperaturePoint = new double[2][10][10];
    public double[][][][] bulkComposition, interfaceComposition, effectiveMassTransferCoefficient; // phase,
                                                                                                   // time,
                                                                                                   // component,
                                                                                                   // node
    public double[][][][] molarFlux, schmidtNumber;
    public double[][][][] totalMolarMassTransferRate;
    public double[][][][] totalVolumetricMassTransferRate;
    double[] xPlace = new double[10];
    double[] timeArray = new double[10];
    visAdInterface plot;

    /**
     * <p>
     * Constructor for TwoPhasePipeFlowVisualization.
     * </p>
     */
    public TwoPhasePipeFlowVisualization() {}

    /**
     * <p>
     * Constructor for TwoPhasePipeFlowVisualization.
     * </p>
     *
     * @param nodes a int
     * @param timeSteps a int
     */
    public TwoPhasePipeFlowVisualization(int nodes, int timeSteps) {
        flowNodes = new TwoPhaseFlowNodeVisualization[timeSteps][nodes];
        flowSystem = new FlowSystem[timeSteps];
        absTime = new double[timeSteps];
        for (int i = 0; i < timeSteps; i++) {
            for (int j = 0; j < nodes; j++) {
                flowNodes[i][j] = new TwoPhaseFlowNodeVisualization();
            }
        }
        System.out.println("nodes " + nodes);
        System.out.println("times " + time);
    }

    /** {@inheritDoc} */
    @Override
    public void setPoints() {
        pressurePoint = new double[2][time][flowNodes[0].length];
        temperaturePoint = new double[2][time][flowNodes[0].length];
        interphaseContactLength = new double[2][time][flowNodes[0].length];
        velocityPoint = new double[2][time][flowNodes[0].length];
        reynoldsNumber = new double[2][time][flowNodes[0].length];
        phaseFraction = new double[2][time][flowNodes[0].length];
        interfaceTemperaturePoint = new double[2][time][flowNodes[0].length];
        bulkComposition =
                new double[2][flowNodes[0][0].getNumberOfComponents()][time][flowNodes[0].length];
        effectiveMassTransferCoefficient =
                new double[2][flowNodes[0][0].getNumberOfComponents()][time][flowNodes[0].length];
        interfaceComposition =
                new double[2][flowNodes[0][0].getNumberOfComponents()][time][flowNodes[0].length];
        molarFlux =
                new double[2][flowNodes[0][0].getNumberOfComponents()][time][flowNodes[0].length];
        schmidtNumber =
                new double[2][flowNodes[0][0].getNumberOfComponents()][time][flowNodes[0].length];
        totalMolarMassTransferRate =
                new double[2][flowNodes[0][0].getNumberOfComponents()][time][flowNodes[0].length];
        totalVolumetricMassTransferRate =
                new double[2][flowNodes[0][0].getNumberOfComponents()][time][flowNodes[0].length];
        xPlace = new double[flowNodes[0].length];
        timeArray = new double[time];

        for (int k = 0; k < 2; k++) {
            for (int j = 0; j < time; j++) {
                timeArray[j] = j;
                for (int i = 0; i < flowNodes[j].length; i++) {
                    xPlace[i] = flowNodes[j][i].getDistanceToCenterOfNode();
                    pressurePoint[k][j][i] = flowNodes[j][i].getPressure(k);
                    interphaseContactLength[k][j][i] = flowNodes[j][i].getInterphaseContactLength();
                    temperaturePoint[k][j][i] = flowNodes[j][i].getTemperature(k);
                    velocityPoint[k][j][i] = flowNodes[j][i].getVelocity(k);
                    reynoldsNumber[k][j][i] = flowNodes[j][i].getReynoldsNumber(k);
                    phaseFraction[k][j][i] = flowNodes[j][i].getPhaseFraction(k);
                    interfaceTemperaturePoint[k][j][i] = flowNodes[j][i].getInterfaceTemperature(k);
                    for (int p = 0; p < flowNodes[0][0].getNumberOfComponents(); p++) {
                        effectiveMassTransferCoefficient[k][p][j][i] =
                                flowNodes[j][i].getEffectiveMassTransferCoefficient(p, k);
                        bulkComposition[k][p][j][i] = flowNodes[j][i].getBulkComposition(p, k);
                        interfaceComposition[k][p][j][i] =
                                flowNodes[j][i].getInterfaceComposition(p, k);
                        molarFlux[k][p][j][i] = flowNodes[j][i].getEffectiveSchmidtNumber(p, k);
                        schmidtNumber[k][p][j][i] = flowNodes[j][i].getEffectiveSchmidtNumber(p, k);
                        totalMolarMassTransferRate[k][p][j][i] =
                                flowSystem[j].getTotalMolarMassTransferRate(p, i);
                        totalVolumetricMassTransferRate[k][p][j][i] =
                                totalMolarMassTransferRate[k][p][j][i] * 60.0 / 40.87631889
                                        * 1000.0;
                    }
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void displayResult(String name) {
        // double[][] points = new double[1][1];
        setPoints();
        //
        // if(name.equals("pressure")) points = pressurePoint;
        // if(name.equals("temperature")) points = temperaturePoint;
        // if(name.equals("velocity")) points = velocityPoint;
        //
        // try{
        // System.out.println("points: " + points.length);
        //
        // if(pressurePoint.length>1){
        // System.out.println("3D plot ");
        // plot = new visAd3DPlot("title[0]", "title[1]", "title[2]");
        // ((visAd3DPlot) plot).setXYvals(150, 160, points[0].length, 10, 20,
        // points.length);
        // ((visAd3DPlot) plot).setZvals(points);
        // }
        // else{
        // System.out.println("2D plot ");
        // plot = new visAd2dBaseClass("title[1]", "title[2]");
        // ((visAd2dBaseClass) plot).setLineXYVals(xPlace, points[0]);
        // ((visAd2dBaseClass) plot).setXYVals(xPlace, points[0]);
        // }
        // plot.init();
        // }
        // catch(Exception e){
        // System.out.println(e.toString());
        // System.out.println("plotting failed");
        // }
    }

    // public void createNetCdfFile(String name){
    // dataPresentation.fileHandeling.createNetCDF.NetCdf file = new
    // dataPresentation.fileHandeling.createNetCDF.NetCdf();
    // file.setOutputFileName(name);
    // file.setXvalues(timeArray,"time","sec");
    // file.setYvalues(xPlace, "length","meter");
    // // file.setZvalues(temperaturePoint, "time","sec");
    // file.createFile();
    // }
    //
    /** {@inheritDoc} */
    @Override
    public void createNetCdfFile(String name) {
        setPoints();
        neqsim.dataPresentation.fileHandeling.createNetCDF.netCDF2D.NetCdf2D file =
                new neqsim.dataPresentation.fileHandeling.createNetCDF.netCDF2D.NetCdf2D();
        file.setOutputFileName(name);
        file.setXvalues(xPlace, "length", "meter");
        file.setYvalues(pressurePoint[0][0], "gas pressure", "sec");
        file.setYvalues(pressurePoint[1][0], "liquid pressure", "sec");
        file.setYvalues(velocityPoint[0][0], "gas velocity", "sec");
        file.setYvalues(velocityPoint[1][0], "liquid velocity", "sec");
        file.setYvalues(reynoldsNumber[0][0], "gas reynolds number", "sec");
        file.setYvalues(reynoldsNumber[1][0], "liquid reynolds number", "sec");
        file.setYvalues(temperaturePoint[0][0], "gas temperature", "sec");
        file.setYvalues(temperaturePoint[1][0], "liquid temperature", "sec");
        file.setYvalues(phaseFraction[0][0], "void fraction", "sec");
        file.setYvalues(phaseFraction[1][0], "holdup", "sec");
        file.setYvalues(interfaceTemperaturePoint[0][0], "gas interface temperature", "sec");
        file.setYvalues(interfaceTemperaturePoint[1][0], "liquid interface temperature", "sec");
        file.setYvalues(interphaseContactLength[0][0], "interphase contact length", "sec");

        for (int p = 0; p < flowNodes[0][0].getNumberOfComponents(); p++) {
            String comp = "component molefraction " + p;
            file.setYvalues(bulkComposition[0][p][0], ("gas " + comp + p), "sec");
            file.setYvalues(bulkComposition[1][p][0], ("liquid " + comp + p), "sec");
            file.setYvalues(interfaceComposition[0][p][0], ("gas (interphase) " + comp), "sec");
            file.setYvalues(interfaceComposition[1][p][0], ("liquid (interphase) " + comp), "sec");
            file.setYvalues(molarFlux[0][p][0], ("gas molar flux " + comp + p), "sec");
            file.setYvalues(molarFlux[1][p][0], ("liquid molar flux " + comp + p), "sec");
            file.setYvalues(totalVolumetricMassTransferRate[0][p][0],
                    ("total gas Volumetric Mass Transfer " + comp + p), "sec");
            file.setYvalues(totalVolumetricMassTransferRate[1][p][0],
                    ("total liq Volumetric Mass Transfer " + comp + p), "sec");
            file.setYvalues(effectiveMassTransferCoefficient[0][p][0],
                    ("eff. masstransfer coef gas " + comp + p), "sec");
            file.setYvalues(effectiveMassTransferCoefficient[1][p][0],
                    ("eff. masstransfer coef liq " + comp + p), "sec");
            file.setYvalues(schmidtNumber[0][p][0], ("gas scmidtnumber " + comp + p), "-");
            file.setYvalues(schmidtNumber[1][p][0], ("liquid scmidtnumber " + comp + p), "-");
        }
        // file.setYvalues(temperaturePoint[0][0], "time","sec");
        file.createFile();
    }
}
