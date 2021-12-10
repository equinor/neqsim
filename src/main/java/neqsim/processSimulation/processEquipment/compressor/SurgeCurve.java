package neqsim.processSimulation.processEquipment.compressor;

import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SurgeCurve implements java.io.Serializable {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(SurgeCurve.class);
    double[] flow;
    double[] head;
    double[] chartConditions = null;
    private boolean isActive = false;

    final WeightedObservedPoints flowFitter = new WeightedObservedPoints();
    PolynomialFunction flowFitterFunc = null;

    public SurgeCurve() {}

    public SurgeCurve(double[] flow, double[] head) {
        this.flow = flow;
        this.head = head;
    }

    public void setCurve(double[] chartConditions, double[] flow, double[] head) {
        this.flow = flow;
        this.head = head;
        this.chartConditions = chartConditions;
        for (int i = 0; i < flow.length; i++) {
            flowFitter.add(head[i], flow[i]);
        }
        PolynomialCurveFitter fitter = PolynomialCurveFitter.create(2);
        flowFitterFunc = new PolynomialFunction(fitter.fit(flowFitter.toList()));
        isActive = true;

        // trykkforhold paa y-aksen mot redused flow
        // dp over sugetrykk
        // surge kurva er invariat i plottet trykkforhold mot redused flow
        // CCC bruker dP/ (over maaleblnde som representerer flow) dP/Ps - paa x-aksen
        // trykkforhold paa y-aksen (trykk ut/trykk inn)
    }

    public double getSurgeFlow(double head) {
        return flowFitterFunc.value(head);
    }

    public boolean isSurge(double head, double flow) {
        if (getSurgeFlow(head) > flow)
            return true;
        else
            return false;
    }

    public static void main(String[] args) {
    }

    boolean isActive() {
        return isActive;
    }

    void setActive(boolean isActive) {
        this.isActive = isActive;
    }
}
