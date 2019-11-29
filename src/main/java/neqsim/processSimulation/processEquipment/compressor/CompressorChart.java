package neqsim.processSimulation.processEquipment.compressor;

import java.util.*;

import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class CompressorChart {

	ArrayList<CompressorCurve> chartValues = new ArrayList<CompressorCurve>();
	SurgeCurve surgeCurve;
	boolean isSurge = false;
	boolean isStoneWall = false;
	double refMW;
	double refTemperature;
	double refPressure;
	double refZ;

	public CompressorChart() {

		double speed = 1000.0;
		double[] flow = new double[] { 453.2, 600.0, 750.0 };
		double[] head = new double[] { 1000.0, 900.0, 800.0 };
		double[] polytropicEfficiency = new double[] { 78.0, 79.0, 78.0 };
		CompressorCurve curve = new CompressorCurve(speed, flow, head, polytropicEfficiency);
		chartValues.add(curve);

		double[] surgeFlow = new double[] { 453.2, 600.0, 750.0 };
		double[] surgeHead = new double[] { 1000.0, 900.0, 800.0 };
		surgeCurve = new SurgeCurve(surgeFlow, surgeHead);
	}

	public void addCurve(double speed, double[] flow, double[] head, double[] polytropicEfficiency) {
		CompressorCurve curve = new CompressorCurve(speed, flow, head, polytropicEfficiency);
		chartValues.add(curve);
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

	public static void main(String[] args) {
		// TODO Auto-generated method stub

		SystemInterface testFluid = new SystemSrkEos(298.15, 50.0);

		testFluid.addComponent("methane", 1.0);
		testFluid.setMixingRule(2);
		testFluid.setTotalFlowRate(1.0e6, "MSm3/day");

		Stream stream_1 = new Stream("Stream1", testFluid);
		Compressor comp1 = new Compressor(stream_1);
		comp1.setUsePolytropicCalc(true);
		comp1.setPower(1000000000000.0);
		comp1.getAntiSurge().setActive(true);
		// comp1.setOutletPressure(100.0);

		// CompressorChart compChart = new CompressorChart();
		// compChart.addCurve(1000.0, new double[] {453.2, 600.0, 750.0}, new double[]
		// {1000.0, 900.0, 800.0}, new double[] {78.0, 79.0, 78.0});
		// compChart.addSurgeCurve(new double[] {453.2, 600.0, 750.0}, new double[]
		// {1000.0, 900.0, 800.0});
		// compChart.setReferenceConditions(double refMW, double refTemperature, double refPressure, double refZ)
		// comp1.setCompressorChart(compChart);

		neqsim.processSimulation.processSystem.ProcessSystem operations = new neqsim.processSimulation.processSystem.ProcessSystem();
		operations.add(stream_1);
		operations.add(comp1);
		operations.run();
		operations.displayResult();

	}

}
