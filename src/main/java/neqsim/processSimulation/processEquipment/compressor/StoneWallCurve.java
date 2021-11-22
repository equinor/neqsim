package neqsim.processSimulation.processEquipment.compressor;

import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.apache.logging.log4j.*;

public class StoneWallCurve implements java.io.Serializable {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(StoneWallCurve.class);
    double[] flow;
    double[] head;
    double[] chartConditions = null;
    private boolean isActive = false;
    final WeightedObservedPoints flowFitter = new WeightedObservedPoints();
    PolynomialFunction flowFitterFunc = null;

    public StoneWallCurve() {
        // flow = new double[] {453.2, 600.0, 750.0};
        // head = new double[] {1000.0, 900.0, 800.0};
    }

    public StoneWallCurve(double[] flow, double[] head) {
        this.flow = flow;
        this.head = head;
    }

    public void setCurve(double[] chartConditions, double[] flow, double[] head) {
        this.chartConditions = chartConditions;
        for (int i = 0; i < flow.length; i++) {
            flowFitter.add(head[i], flow[i]);
        }
        PolynomialCurveFitter fitter = PolynomialCurveFitter.create(2);
        flowFitterFunc = new PolynomialFunction(fitter.fit(flowFitter.toList()));
        isActive = true;
    }

    public double getStoneWallFlow(double head) {
        return flowFitterFunc.value(head);
    }

    public boolean isStoneWall(double head, double flow) {
        if (getStoneWallFlow(head) < flow)
            return true;
        else
            return false;
    }

    public static void main(String[] args) {
        // TODO Auto-generated method stub
    }

    boolean isActive() {
        return isActive;
    }

    void setActive(boolean isActive) {
        this.isActive = isActive;
    }
}
