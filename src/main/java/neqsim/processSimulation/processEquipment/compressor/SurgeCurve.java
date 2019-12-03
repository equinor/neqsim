package neqsim.processSimulation.processEquipment.compressor;

import java.util.ArrayList;

import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.apache.log4j.Logger;

public class SurgeCurve {
		
	static Logger logger = Logger.getLogger(SurgeCurve.class);
	double[] flow;
	double[] head;
	double[] chartConditions = null;
	
	final WeightedObservedPoints flowFitter = new WeightedObservedPoints();
	PolynomialFunction flowFitterFunc = null;
	
	public SurgeCurve() {
		//flow = new double[] {453.2, 600.0, 750.0};
		//head = new double[] {1000.0, 900.0, 800.0};
	}
	
	public SurgeCurve(double[] flow, double[] head) {
		this.flow = flow;
		this.head = head;
	}
	
	public void setCurve(double[] chartConditions, double[] flow, double[] head) {
		this.chartConditions = chartConditions;
		for(int i=0;i<flow.length;i++) {			
			flowFitter.add(head[i],flow[i]);
		}
		 PolynomialCurveFitter fitter=PolynomialCurveFitter.create(2);
		 flowFitterFunc = new PolynomialFunction(fitter.fit(flowFitter.toList()));
	}
	
	public double getSurgeFlow(double head){
		return flowFitterFunc.value(head);
	}
	
	public boolean isSurge(double head, double flow) {
		if(getSurgeFlow(head)>flow) return true;
		else return false;
	}
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
