/*
 * PipeFlowVisualization.java
 *
 * Created on 26. oktober 2000, 20:09
 */
package neqsim.fluidMechanics.util.fluidMechanicsVisualization.flowSystemVisualization.onePhaseFlowVisualization.pipeFlowVisualization;

/**
 * <p>
 * PipeFlowVisualization class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class PipeFlowVisualization extends
        neqsim.fluidMechanics.util.fluidMechanicsVisualization.flowSystemVisualization.onePhaseFlowVisualization.OnePhaseFlowVisualization {
    double[][] pressurePoint = new double[10][10];
    double[][] velocityPoint = new double[10][10];
    double[][] temperaturePoint = new double[10][10];
    public double[][][] bulkComposition;
    double[] xPlace = new double[10];
    double[] timeArray = new double[10];
    neqsim.dataPresentation.visAD.visAdInterface plot;

    /**
     * <p>
     * Constructor for PipeFlowVisualization.
     * </p>
     */
    public PipeFlowVisualization() {}

    /**
     * <p>
     * Constructor for PipeFlowVisualization.
     * </p>
     *
     * @param nodes a int
     * @param timeSteps a int
     */
    public PipeFlowVisualization(int nodes, int timeSteps) {
        flowSystem = new neqsim.fluidMechanics.flowSystem.FlowSystemInterface[timeSteps];
        flowNodes =
                new neqsim.fluidMechanics.util.fluidMechanicsVisualization.flowNodeVisualization.onePhaseFlowNodeVisualization.onePhasePipeFlowNodeVisualization.OnePhasePipeFlowNodeVisualization[timeSteps][nodes];
        absTime = new double[timeSteps];
        for (int i = 0; i < timeSteps; i++) {
            for (int j = 0; j < nodes; j++) {
                flowNodes[i][j] =
                        new neqsim.fluidMechanics.util.fluidMechanicsVisualization.flowNodeVisualization.onePhaseFlowNodeVisualization.onePhasePipeFlowNodeVisualization.OnePhasePipeFlowNodeVisualization();
            }
        }
        System.out.println("nodes " + nodes);
        System.out.println("times " + time);
    }

    /** {@inheritDoc} */
    @Override
    public void setPoints() {
        pressurePoint = new double[time][flowNodes[0].length];
        temperaturePoint = new double[time][flowNodes[0].length];
        velocityPoint = new double[time][flowNodes[0].length];
        xPlace = new double[flowNodes[0].length];
        timeArray = new double[time];
        bulkComposition =
                new double[flowNodes[0][0].getNumberOfComponents()][time][flowNodes[0].length];

        for (int j = 0; j < time; j++) {
            timeArray[j] = absTime[j];
            for (int i = 0; i < flowNodes[j].length; i++) {
                xPlace[i] = flowNodes[j][i].getDistanceToCenterOfNode();
                pressurePoint[j][i] = flowNodes[j][i].getPressure(0);
                temperaturePoint[j][i] = flowNodes[j][i].getTemperature(0);
                velocityPoint[j][i] = flowNodes[j][i].getVelocity(0);
                for (int p = 0; p < flowNodes[0][0].getNumberOfComponents(); p++) {
                    bulkComposition[p][j][i] = flowNodes[j][i].getBulkComposition(p, 0);
                }
            }
        }
    }

    /**
     * <p>
     * calcPoints.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     */
    public void calcPoints(String name) {
        double[][] points = new double[1][1];
        setPoints();

        if (name.equals("pressure")) {
            points = pressurePoint;
        }
        if (name.equals("temperature")) {
            points = temperaturePoint;
        }
        if (name.equals("velocity")) {
            points = velocityPoint;
        }
        if (name.equals("composition")) {
            points = bulkComposition[0];
        }

        try {
            System.out.println("points: " + points.length);

            if (pressurePoint.length > 1) {
                System.out.println("3D plot ");
                plot = new neqsim.dataPresentation.visAD.visAd3D.visAd3DPlot("title[0]", "title[1]",
                        "title[2]");
                ((neqsim.dataPresentation.visAD.visAd3D.visAd3DPlot) plot).setXYvals(150, 160,
                        points[0].length, 10, 20, points.length);
                ((neqsim.dataPresentation.visAD.visAd3D.visAd3DPlot) plot).setZvals(points);
            } else {
                System.out.println("2D plot ");
                plot = new neqsim.dataPresentation.visAD.visAd2D.visAd2dBaseClass("title[1]",
                        "title[2]");
                ((neqsim.dataPresentation.visAD.visAd2D.visAd2dBaseClass) plot)
                        .setLineXYVals(xPlace, points[0]);
                ((neqsim.dataPresentation.visAD.visAd2D.visAd2dBaseClass) plot).setXYVals(xPlace,
                        points[0]);
            }
        } catch (Exception e) {
            System.out.println(e.toString());
            System.out.println("plotting failed");
        }
    }

    /** {@inheritDoc} */
    @Override
    public void displayResult(String name) {
        double[][] points = new double[1][1];
        setPoints();

        if (name.equals("pressure")) {
            points = pressurePoint;
        }
        if (name.equals("temperature")) {
            points = temperaturePoint;
        }
        if (name.equals("velocity")) {
            points = velocityPoint;
        }
        if (name.equals("composition")) {
            points = bulkComposition[0];
        }

        try {
            System.out.println("points: " + points.length);

            if (pressurePoint.length > 1) {
                System.out.println("3D plot ");
                plot = new neqsim.dataPresentation.visAD.visAd3D.visAd3DPlot("title[0]", "title[1]",
                        "title[2]");
                ((neqsim.dataPresentation.visAD.visAd3D.visAd3DPlot) plot).setXYvals(150, 160,
                        points[0].length, 10, 20, points.length);
                ((neqsim.dataPresentation.visAD.visAd3D.visAd3DPlot) plot).setZvals(points);
            } else {
                System.out.println("2D plot ");
                plot = new neqsim.dataPresentation.visAD.visAd2D.visAd2dBaseClass("title[1]",
                        "title[2]");
                ((neqsim.dataPresentation.visAD.visAd2D.visAd2dBaseClass) plot)
                        .setLineXYVals(xPlace, points[0]);
                ((neqsim.dataPresentation.visAD.visAd2D.visAd2dBaseClass) plot).setXYVals(xPlace,
                        points[0]);
            }
            plot.init();
        } catch (Exception e) {
            System.out.println(e.toString());
            System.out.println("plotting failed");
        }
    }

    /** {@inheritDoc} */
    @Override
    public void createNetCdfFile(String name) {
        calcPoints(name);
        neqsim.dataPresentation.fileHandeling.createNetCDF.netCDF3D.NetCdf3D file =
                new neqsim.dataPresentation.fileHandeling.createNetCDF.netCDF3D.NetCdf3D();
        file.setOutputFileName(name);
        file.setXvalues(timeArray, "time", "sec");
        file.setYvalues(xPlace, "length", "meter");
        file.setZvalues(temperaturePoint, "temperature [K]", "sec");
        file.setZvalues(pressurePoint, "pressure [bar]", "sec");
        file.setZvalues(velocityPoint, "velocity [m/sec]", "sec");
        if (absTime.length > 1) {
            for (int p = 0; p < flowNodes[0][0].getNumberOfComponents(); p++) {
                file.setZvalues(bulkComposition[p], ("comp " + p), "sec");
            }
        }
        file.createFile();
    }
}
