package neqsim.processSimulation.processEquipment.compressor;

import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>StoneWallCurve class.</p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class StoneWallCurve implements java.io.Serializable {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(StoneWallCurve.class);
    double[] flow;
    double[] head;
    double[] chartConditions = null;
    private boolean isActive = false;
    final WeightedObservedPoints flowFitter = new WeightedObservedPoints();
    PolynomialFunction flowFitterFunc = null;

    /**
     * <p>Constructor for StoneWallCurve.</p>
     */
    public StoneWallCurve() {
        // flow = new double[] {453.2, 600.0, 750.0};
        // head = new double[] {1000.0, 900.0, 800.0};
    }

    /**
     * <p>Constructor for StoneWallCurve.</p>
     *
     * @param flow an array of {@link double} objects
     * @param head an array of {@link double} objects
     */
    public StoneWallCurve(double[] flow, double[] head) {
        this.flow = flow;
        this.head = head;
    }

    /**
     * <p>setCurve.</p>
     *
     * @param chartConditions an array of {@link double} objects
     * @param flow an array of {@link double} objects
     * @param head an array of {@link double} objects
     */
    public void setCurve(double[] chartConditions, double[] flow, double[] head) {
        this.chartConditions = chartConditions;
        for (int i = 0; i < flow.length; i++) {
            flowFitter.add(head[i], flow[i]);
        }
        PolynomialCurveFitter fitter = PolynomialCurveFitter.create(2);
        flowFitterFunc = new PolynomialFunction(fitter.fit(flowFitter.toList()));
        isActive = true;
    }

    /**
     * <p>getStoneWallFlow.</p>
     *
     * @param head a double
     * @return a double
     */
    public double getStoneWallFlow(double head) {
        return flowFitterFunc.value(head);
    }

    /**
     * <p>isStoneWall.</p>
     *
     * @param head a double
     * @param flow a double
     * @return a boolean
     */
    public boolean isStoneWall(double head, double flow) {
        if (getStoneWallFlow(head) < flow)
            return true;
        else
            return false;
    }

    boolean isActive() {
        return isActive;
    }

    void setActive(boolean isActive) {
        this.isActive = isActive;
    }
}
