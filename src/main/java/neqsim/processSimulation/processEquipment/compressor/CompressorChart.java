package neqsim.processSimulation.processEquipment.compressor;

import java.util.*;

import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.apache.log4j.Logger;

import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class CompressorChart {

	static Logger logger = Logger.getLogger(CompressorChart.class);
	ArrayList<CompressorCurve> chartValues = new ArrayList<CompressorCurve>();
	private SurgeCurve surgeCurve = new SurgeCurve();
	private StoneWallCurve stoneWallCurve = new StoneWallCurve();
	boolean isSurge = false;
	boolean isStoneWall = false;
	double refMW;

	private boolean useCompressorChart = false;
	double refTemperature;
	double refPressure;
	double refZ;
	double[] chartConditions = null;
	final WeightedObservedPoints reducedHeadFitter = new WeightedObservedPoints();
	final WeightedObservedPoints reducedFlowFitter = new WeightedObservedPoints();
	final WeightedObservedPoints reducedPolytropicEfficiencyFitter = new WeightedObservedPoints();
	PolynomialFunction reducedHeadFitterFunc = null;
	PolynomialFunction reducedPolytropicEfficiencyFunc = null;

	public CompressorChart() {
	}

	public void addCurve(double speed, double[] flow, double[] head, double[] polytropicEfficiency) {
		CompressorCurve curve = new CompressorCurve(speed, flow, head, polytropicEfficiency);
		chartValues.add(curve);
	}

	public void setCurves(double[] chartConditions, double[] speed, double[][] flow, double[][] head,
			double[][] polyEff) {
		for (int i = 0; i < speed.length; i++) {
			CompressorCurve curve = new CompressorCurve(speed[i], flow[i], head[i], polyEff[i]);
			chartValues.add(curve);
			for (int j = 0; j < flow[i].length; j++) {
				reducedHeadFitter.add(flow[i][j] / speed[i], head[i][j] / speed[i] / speed[i]);
				reducedPolytropicEfficiencyFitter.add(flow[i][j] / speed[i], polyEff[i][j]);
			}
		}
		PolynomialCurveFitter fitter = PolynomialCurveFitter.create(2);

		reducedHeadFitterFunc = new PolynomialFunction(fitter.fit(reducedHeadFitter.toList()));
		reducedPolytropicEfficiencyFunc = new PolynomialFunction(
				fitter.fit(reducedPolytropicEfficiencyFitter.toList()));
		setUseCompressorChart(true);
	}

	public void fitReducedCurve() {

	}

	public double getHead(double flow, double speed) {
		return reducedHeadFitterFunc.value(flow / speed) * speed * speed;
	}

	public double getPolytropicEfficiency(double flow, double speed) {
		return reducedPolytropicEfficiencyFunc.value(flow / speed);
	}

	public void addSurgeCurve(double[] flow, double[] head) {
		surgeCurve = new SurgeCurve(flow, head);
	}

	public double getPolytropicHead(double flow, double speed) {
		checkSurge1(flow, speed);
		return 100.0;
	}

	public double polytropicEfficiency(double flow, double speed) {
		return 100.0;
	}

	public double getSpeed(double flow, double head) {
		return 1000.0;
	}

	public boolean checkSurge1(double flow, double head) {
		return false;
	}

	public boolean checkSurge2(double flow, double speed) {
		return false;
	}

	public boolean checkStoneWall(double flow, double speed) {
		return false;
	}

	public void setReferenceConditions(double refMW, double refTemperature, double refPressure, double refZ) {
		this.refMW = refMW;
		this.refTemperature = refTemperature;
		this.refPressure = refPressure;
		this.refZ = refZ;
	}
	
	public SurgeCurve getSurgeCurve() {
		return surgeCurve;
	}

	public void setSurgeCurve(SurgeCurve surgeCurve) {
		this.surgeCurve = surgeCurve;
	}

	public StoneWallCurve getStoneWallCurve() {
		return stoneWallCurve;
	}

	public void setStoneWallCurve(StoneWallCurve stoneWallCurve) {
		this.stoneWallCurve = stoneWallCurve;
	}

	public static void main(String[] args) {
		SystemInterface testFluid = new SystemSrkEos(298.15, 50.0);

		testFluid.addComponent("methane", 1.0);
		testFluid.setMixingRule(2);
		testFluid.setTotalFlowRate(0.635, "MSm3/day");

		Stream stream_1 = new Stream("Stream1", testFluid);
		Compressor comp1 = new Compressor(stream_1);
		comp1.setUsePolytropicCalc(true);
		comp1.getAntiSurge().setActive(false);
		comp1.setSpeed(2050);

		double[] chartConditions = new double[] { 0.3, 1.0, 1.0, 1.0 };
		double[] speed = new double[] { 1000.0, 2000.0, 3000.0, 4000.0 };
		double[][] flow = new double[][] { { 453.2, 600.0, 750.0, 800.0 }, { 453.2, 600.0, 750.0, 800.0 },
				{ 453.2, 600.0, 750.0, 800.0 }, { 453.2, 600.0, 750.0, 800.0 } };
		double[][] head = new double[][] { { 10000.0, 9000.0, 8000.0, 7500.0 }, { 10000.0, 9000.0, 8000.0, 7500.0 },
				{ 10000.0, 9000.0, 8000.0, 7500.0 }, { 10000.0, 9000.0, 8000.0, 7500.0 } };
		double[][] polyEff = new double[][] { { 90.0, 91.0, 89.0, 88.0 }, { 90.0, 91.0, 89.0, 88.0 },
				{ 90.0, 91.0, 89.0, 88.1 }, { 90.0, 91.0, 89.0, 88.1 } };
		comp1.getCompressorChart().setCurves(chartConditions, speed, flow, head, polyEff);
		
		
		double[] surgeflow = new double[] { 453.2, 550.0, 700.0, 800.0 };
		double[] surgehead = new double[] { 6000.0, 7000.0, 8000.0, 10000.0 };
		comp1.getCompressorChart().getSurgeCurve().setCurve(chartConditions, surgeflow, surgehead);
	

		double[] stoneWallflow = new double[] { 923.2, 950.0, 980.0, 1000.0 };
		double[] stoneWallHead = new double[] { 6000.0, 7000.0, 8000.0, 10000.0 };
		comp1.getCompressorChart().getStoneWallCurve().setCurve(chartConditions, stoneWallflow, stoneWallHead);
		
	
		
		neqsim.processSimulation.processSystem.ProcessSystem operations = new neqsim.processSimulation.processSystem.ProcessSystem();
		operations.add(stream_1);
		operations.add(comp1);
		operations.run();
		operations.displayResult();
		
		System.out.println("power " + comp1.getPower());

	}

	boolean isUseCompressorChart() {
		return useCompressorChart;
	}

	void setUseCompressorChart(boolean useCompressorChart) {
		this.useCompressorChart = useCompressorChart;
	}

}
