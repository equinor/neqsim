/*
 * Heater.java
 *
 * Created on 15. mars 2001, 14:17
 */
package neqsim.processSimulation.processEquipment.heatExchanger;

import neqsim.processSimulation.conditionMonitor.ConditionMonitorSpecifications;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class HeatExchanger extends Heater implements ProcessEquipmentInterface, HeatExchangerInterface {

	private static final long serialVersionUID = 1000;

	boolean setTemperature = false;
	StreamInterface[] outStream;
	StreamInterface[] inStream;
	SystemInterface system;
	double NTU;
	protected double temperatureOut = 0, dT = 0.0;
	double dH = 0.0;
	private double UAvalue = 500.0;
	double duty = 0.0;
	boolean firstTime = true;
	public double guessOutTemperature = 273.15 + 130.0;
	int outStreamSpecificationNumber = 0;
	public double thermalEffectiveness = 0.0;
	private String flowArrangement = "concentric tube counterflow";

	/**
	 * Creates new Heater
	 */
	public HeatExchanger() {
		outStream = new Stream[2];
		inStream = new Stream[2];
	}

	public HeatExchanger(StreamInterface inStream1) {
		outStream = new Stream[2];
		inStream = new Stream[2];
		this.inStream[0] = inStream1;
		this.inStream[1] = inStream1;
		outStream[0] = (StreamInterface) inStream1.clone();
		outStream[1] = (StreamInterface) inStream1.clone();
	}

	public HeatExchanger(StreamInterface inStream1, StreamInterface inStream2) {
		outStream = new Stream[2];
		inStream = new Stream[2];
		this.inStream[0] = inStream1;
		this.inStream[1] = inStream2;
		outStream[0] = (StreamInterface) inStream1.clone();
		outStream[1] = (StreamInterface) inStream2.clone();
	}

	public void addInStream(StreamInterface inStream) {
		this.inStream[1] = inStream;
	}

	public void setFeedStream(int number, StreamInterface inStream) {
		this.inStream[number] = inStream;
		outStream[number] = (StreamInterface) inStream.clone();
	}

	@Override
	public void setName(String name) {
		outStream[0].setName(name + "_Sout1");
		outStream[1].setName(name + "_Sout2");
		this.name = name;
	}

	@Override
	public void setdT(double dT) {
		this.dT = dT;
	}

	@Override
	public StreamInterface getOutStream(int i) {
		return outStream[i];
	}

	public StreamInterface getInStream(int i) {
		return inStream[i];
	}

	@Override
	public void setOutTemperature(double temperature) {
		this.temperatureOut = temperature;
	}

	public void getOutTemperature(int i) {
		outStream[i].getThermoSystem().getTemperature();
	}

	public void getInTemperature(int i) {
		inStream[i].getThermoSystem().getTemperature();
	}

	/**
	 * @param outStream the outStream to set
	 */
	public void setOutStream(int streamNumber, StreamInterface outStream) {
		this.outStream[streamNumber] = outStream;
		outStreamSpecificationNumber = streamNumber;
	}

	public void runSpecifiedStream() {
		int nonOutStreamSpecifiedStreamNumber = 0;
		if (outStreamSpecificationNumber == 0) {
			nonOutStreamSpecifiedStreamNumber = 1;
		}

		SystemInterface systemOut0 = (SystemInterface) inStream[nonOutStreamSpecifiedStreamNumber].getThermoSystem()
				.clone();
		// SystemInterface systemOut1 = (SystemInterface)
		// inStream[outStreamSpecificationNumber].getThermoSystem().clone();

		if (getSpecification().equals("out stream")) {
			outStream[outStreamSpecificationNumber]
					.setFlowRate(getInStream(outStreamSpecificationNumber).getFlowRate("kg/sec"), "kg/sec");
			outStream[outStreamSpecificationNumber].run();
			temperatureOut = outStream[outStreamSpecificationNumber].getTemperature();
			// system = (SystemInterface)
			// outStream[outStreamSpecificationNumber].getThermoSystem().clone();
		}

		double deltaEnthalpy = outStream[outStreamSpecificationNumber].getFluid().getEnthalpy()
				- inStream[outStreamSpecificationNumber].getFluid().getEnthalpy();
		double enthalpyOutRef = inStream[nonOutStreamSpecifiedStreamNumber].getFluid().getEnthalpy() - deltaEnthalpy;

		ThermodynamicOperations testOps = new ThermodynamicOperations(systemOut0);
		testOps.PHflash(enthalpyOutRef);
		System.out.println("out temperature " + systemOut0.getTemperature("C"));
		outStream[nonOutStreamSpecifiedStreamNumber].setFluid(systemOut0);
	}

	@Override
	public void run() {
		if (getSpecification().equals("out stream")) {
			runSpecifiedStream();
			return;
		}

		// inStream[0].run();
		// inStream[1].displayResult();
		if (firstTime) {
			firstTime = false;
			SystemInterface systemOut0 = (SystemInterface) inStream[0].getThermoSystem().clone();
			outStream[0].setThermoSystem(systemOut0);
			outStream[0].getThermoSystem().setTemperature(guessOutTemperature);
			outStream[0].run();
			run();
			return;
		}

		double cP0 = inStream[0].getThermoSystem().getCp();
		double cP1 = inStream[1].getThermoSystem().getCp();
		int streamToCalculate = 0, streamToSet = 1;

		if (cP0 < cP1) {
			// streamToCalculate = 1;
			// streamToSet = 0;
		}
		SystemInterface systemOut0 = (SystemInterface) inStream[streamToSet].getThermoSystem().clone();
		SystemInterface systemOut1 = (SystemInterface) inStream[streamToCalculate].getThermoSystem().clone();

		// systemOut1.setTemperature(inTemp1);
		outStream[streamToSet].setThermoSystem(systemOut0);
		outStream[streamToCalculate].setThermoSystem(systemOut1);
		double temper = inStream[streamToCalculate].getThermoSystem().getTemperature();
		outStream[streamToSet].setTemperature(inStream[streamToCalculate].getThermoSystem().getTemperature(), "K");
		outStream[streamToSet].getThermoSystem()
				.setTemperature(inStream[streamToCalculate].getThermoSystem().getTemperature());
		if (!outStream[streamToSet].getSpecification().equals("TP")) {
			outStream[streamToSet].runTPflash();
		}
		outStream[streamToSet].run();
		double dEntalphy1 = outStream[streamToSet].getThermoSystem().getEnthalpy()
				- inStream[streamToSet].getThermoSystem().getEnthalpy();
		double C1 = Math.abs(dEntalphy1) / Math.abs((outStream[streamToSet].getThermoSystem().getTemperature()
				- inStream[streamToSet].getThermoSystem().getTemperature()));

		double temper2 = inStream[streamToSet].getThermoSystem().getTemperature();
		outStream[streamToCalculate].setTemperature(inStream[streamToSet].getThermoSystem().getTemperature(), "K");
		outStream[streamToCalculate].getThermoSystem()
				.setTemperature(inStream[streamToSet].getThermoSystem().getTemperature());
		if (!outStream[streamToCalculate].getSpecification().equals("TP")) {
			outStream[streamToCalculate].runTPflash();
		}
		outStream[streamToCalculate].run();
		double dEntalphy2 = outStream[streamToCalculate].getThermoSystem().getEnthalpy()
				- inStream[streamToCalculate].getThermoSystem().getEnthalpy();
		double C2 = Math.abs(dEntalphy2) / Math.abs(outStream[streamToCalculate].getThermoSystem().getTemperature()
				- inStream[streamToCalculate].getThermoSystem().getTemperature());
		double Cmin = C1;
		double Cmax = C2;
		if (C2 < C1) {
			Cmin = C2;
			Cmax = C1;
		}
		double Cr = Cmin / Cmax;
		if (Math.abs(dEntalphy1) > Math.abs(dEntalphy2)) {
			int streamCHange = streamToCalculate;
			streamToCalculate = streamToSet;
			streamToSet = streamCHange;
		}

		double dEntalphy = outStream[streamToSet].getThermoSystem().getEnthalpy()
				- inStream[streamToSet].getThermoSystem().getEnthalpy();
		NTU = UAvalue / Cmin;

		thermalEffectiveness = calcThermalEffectivenes(NTU, Cr);
		// double corrected_Entalphy = dEntalphy;// *
		// inStream[1].getThermoSystem().getNumberOfMoles() /
		// inStream[0].getThermoSystem().getNumberOfMoles();
		dEntalphy = thermalEffectiveness * dEntalphy;
		// System.out.println("dent " + dEntalphy);
		ThermodynamicOperations testOps = new ThermodynamicOperations(outStream[streamToCalculate].getThermoSystem());
		testOps.PHflash(inStream[streamToCalculate].getThermoSystem().getEnthalpy() - dEntalphy, 0);

		if (Math.abs(thermalEffectiveness - 1.0) > 1e-10) {
			testOps = new ThermodynamicOperations(outStream[streamToSet].getThermoSystem());
			testOps.PHflash(inStream[streamToSet].getThermoSystem().getEnthalpy() + dEntalphy, 0);
		}
		duty = dEntalphy;
		// outStream[0].displayResult();
		// outStream[1].displayResult();
		// System.out.println("temperatur Stream 1 out " +
		// outStream[0].getTemperature());
		// System.out.println("temperatur Stream 0 out " +
		// outStream[1].getTemperature());
		// outStream[0].setThermoSystem(systemOut0);
		// System.out.println("temperature out " +
		// outStream[streamToCalculate].getTemperature());
		/*
		 * if (systemOut0.getTemperature() <= inTemp1 - dT) {
		 * systemOut0.setTemperature(inTemp1); outStream[0].setThermoSystem(systemOut0);
		 * outStream[0].run(); //inStream[0].run();
		 * 
		 * dEntalphy = outStream[0].getThermoSystem().getEnthalpy() -
		 * inStream[0].getThermoSystem().getEnthalpy(); corrected_Entalphy = dEntalphy *
		 * inStream[0].getThermoSystem().getNumberOfMoles() /
		 * inStream[1].getThermoSystem().getNumberOfMoles();
		 * 
		 * systemOut1 = (SystemInterface) inStream[1].getThermoSystem().clone();
		 * System.out.println("dent " + dEntalphy); testOps = new
		 * ThermodynamicOperations(systemOut1); testOps.PHflash(systemOut1.getEnthalpy()
		 * - corrected_Entalphy, 0); outStream[1].setThermoSystem(systemOut1);
		 * System.out.println("temperatur out " + outStream[1].getTemperature()); }
		 */
	}

	@Override
	public double getDuty() {
		return duty;
	}

	@Override
	public void displayResult() {
		outStream[0].displayResult();
		outStream[1].displayResult();
	}

	/**
	 * @return the UAvalue
	 */
	public double getUAvalue() {
		return UAvalue;
	}

	/**
	 * @param UAvalue the UAvalue to set
	 */
	public void setUAvalue(double UAvalue) {
		this.UAvalue = UAvalue;
	}

	public double getGuessOutTemperature() {
		return guessOutTemperature;
	}

	public void setGuessOutTemperature(double guessOutTemperature) {
		this.guessOutTemperature = guessOutTemperature;
	}

	@Override
	public double getEntropyProduction(String unit) {
		//
		double entrop = 0.0;

		for (int i = 0; i < 2; i++) {
			inStream[i].run();
			inStream[i].getFluid().init(3);
			outStream[i].run();
			outStream[i].getFluid().init(3);
			entrop += outStream[i].getThermoSystem().getEntropy(unit) - inStream[i].getThermoSystem().getEntropy(unit);
		}

		int stream1 = 0;
		int stream2 = 1;
		if (inStream[0].getTemperature() < inStream[1].getTemperature()) {
			stream2 = 0;
			stream1 = 1;
		}
		double heatTransferEntropyProd = Math.abs(getDuty())
				* (1.0 / inStream[stream2].getTemperature() - 1.0 / (inStream[stream1].getTemperature()));
		// System.out.println("heat entropy " + heatTransferEntropyProd);

		return entrop + heatTransferEntropyProd;
	}

	@Override
	public double getMassBalance(String unit) {
		//
		double mass = 0.0;

		for (int i = 0; i < 2; i++) {
			inStream[i].run();
			inStream[i].getFluid().init(3);
			outStream[i].run();
			outStream[i].getFluid().init(3);
			mass += outStream[i].getThermoSystem().getFlowRate(unit) - inStream[i].getThermoSystem().getFlowRate(unit);
		}
		return mass;
	}

	@Override
	public void runConditionAnalysis(ProcessEquipmentInterface refExchanger) {
		double heatBalanceError = 0.0;
		HeatExchanger refEx = (HeatExchanger) refExchanger;
		for (int i = 0; i < 2; i++) {
			inStream[i].getFluid().initProperties();
			outStream[i].getFluid().initProperties();
			heatBalanceError += outStream[i].getThermoSystem().getEnthalpy()
					- inStream[i].getThermoSystem().getEnthalpy();

			if (Math.abs(refEx.getInStream(i).getTemperature("C")
					- getInStream(i).getTemperature("C")) > ConditionMonitorSpecifications.HXmaxDeltaT) {
				conditionAnalysisMessage += ConditionMonitorSpecifications.HXmaxDeltaT_ErrorMsg;
			} else if (Math.abs(refEx.getOutStream(i).getTemperature("C")
					- getOutStream(i).getTemperature("C")) > ConditionMonitorSpecifications.HXmaxDeltaT) {
				conditionAnalysisMessage += ConditionMonitorSpecifications.HXmaxDeltaT_ErrorMsg;
			}
		}
		if (Math.abs(heatBalanceError) > 1.0) {
			String error = "Heat balance not fulfilled. Error: " + heatBalanceError + " ";
			conditionAnalysisMessage += error;
		}

		conditionAnalysisMessage += "/analysis ended/";

		// this.run();
		double duty1 = Math
				.abs(outStream[0].getThermoSystem().getEnthalpy() - inStream[0].getThermoSystem().getEnthalpy());
		double duty2 = Math
				.abs(outStream[1].getThermoSystem().getEnthalpy() - inStream[1].getThermoSystem().getEnthalpy());
		thermalEffectiveness = ((HeatExchanger) refExchanger).getThermalEffectiveness() * (duty1 + duty2) / 2.0
				/ Math.abs(((HeatExchanger) refExchanger).getDuty());
	}

	public void runConditionAnalysis() {
		runConditionAnalysis(this);
	}

	public double getThermalEffectiveness() {
		return thermalEffectiveness;
	}

	public void setThermalEffectiveness(double thermalEffectiveness) {
		this.thermalEffectiveness = thermalEffectiveness;
	}

	String getFlowArrangement() {
		return flowArrangement;
	}

	void setFlowArrangement(String flowArrangement) {
		this.flowArrangement = flowArrangement;
	}

	public double calcThermalEffectivenes(double NTU, double Cr) {
		if(Cr==0.0){
			return 1.0 - Math.exp(-NTU);
		}
		if (flowArrangement.equals("concentric tube counterflow")) {
			if(Cr==1.0) return NTU/(1.0 + NTU);
			else return (1.0 - Math.exp(-NTU * (1 - Cr))) / (1.0 - Cr * Math.exp(-NTU * (1 - Cr)));
		} 
		else if (flowArrangement.equals("concentric tube paralellflow")) {
			return (1.0 - Math.exp(-NTU * (1 + Cr))) / ( (1 + Cr));
		} 
		else if (flowArrangement.equals("shell and tube")) {
			return (1.0 - Math.exp(-NTU * (1 - Cr))) / (1.0 - Cr * Math.exp(-NTU * (1 - Cr)));
		}
		else
			return (1.0 - Math.exp(-NTU * (1 - Cr))) / (1.0 - Cr * Math.exp(-NTU * (1 - Cr)));
	}

}