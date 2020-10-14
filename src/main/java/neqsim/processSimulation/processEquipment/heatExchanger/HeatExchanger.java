/*
 * Heater.java
 *
 * Created on 15. mars 2001, 14:17
 */
package neqsim.processSimulation.processEquipment.heatExchanger;

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
	protected double temperatureOut = 0, dT = 0.0;
	double dH = 0.0;
	private double UAvalue = 500.0;
	double thermalEffectivness = 0.6;
	boolean firstTime = true;
	public double guessOutTemperature = 273.15 + 130.0;

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
		this.inStream[1] = (StreamInterface) inStream;
	}

	public void setFeedStream(int number, StreamInterface inStream) {
		this.inStream[number] = inStream;
		outStream[number] = (StreamInterface) inStream.clone();
	}

	public void setName(String name) {
		outStream[0].setName(name + "_Sout1");
		outStream[1].setName(name + "_Sout2");
		this.name = name;
	}

	public void setdT(double dT) {
		this.dT = dT;
	}

	public StreamInterface getOutStream(int i) {
		return outStream[i];
	}

	public void setOutTemperature(double temperature) {
		this.temperatureOut = temperature;
	}

	public void getOutTemperature(int i) {
		outStream[i].getThermoSystem().getTemperature();
	}

	public void getInTemperature(int i) {
		inStream[i].getThermoSystem().getTemperature();
	}

	public void run() {

		// inStream[0].run();
		// inStream[1].displayResult();
		if (firstTime) {
			inStream[0].getThermoSystem().setTemperature(guessOutTemperature);
			inStream[0].run();
			SystemInterface systemOut0 = (SystemInterface) inStream[0].getThermoSystem().clone();
			outStream[0].setThermoSystem(systemOut0);
			firstTime = false;
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
		outStream[streamToSet].run();
		double dEntalphy1 = outStream[streamToSet].getThermoSystem().getEnthalpy()
				- inStream[streamToSet].getThermoSystem().getEnthalpy();
		double C1 = Math.abs(dEntalphy1) / Math.abs((outStream[streamToSet].getThermoSystem().getTemperature()
				- inStream[streamToSet].getThermoSystem().getTemperature()));

		double temper2 = inStream[streamToSet].getThermoSystem().getTemperature();
		outStream[streamToCalculate].setTemperature(inStream[streamToSet].getThermoSystem().getTemperature(), "K");
		outStream[streamToCalculate].getThermoSystem()
				.setTemperature(inStream[streamToSet].getThermoSystem().getTemperature());
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
		double NTU = UAvalue / Cmin;
		double eeff = (1.0 - Math.exp(-NTU * (1 + Cr))) / (1.0 - Cr * Math.exp(-NTU * (1 + Cr)));
		//System.out.println("effeciency " + eeff);
		thermalEffectivness = eeff;
		// double corrected_Entalphy = dEntalphy;// *
		// inStream[1].getThermoSystem().getNumberOfMoles() /
		// inStream[0].getThermoSystem().getNumberOfMoles();
		dEntalphy = thermalEffectivness * dEntalphy;
		// System.out.println("dent " + dEntalphy);
		ThermodynamicOperations testOps = new ThermodynamicOperations(outStream[streamToCalculate].getThermoSystem());
		testOps.PHflash(inStream[streamToCalculate].getThermoSystem().getEnthalpy() - dEntalphy, 0);

		if (Math.abs(thermalEffectivness - 1.0) > 1e-10) {
			testOps = new ThermodynamicOperations(outStream[streamToSet].getThermoSystem());
			testOps.PHflash(inStream[streamToSet].getThermoSystem().getEnthalpy() + dEntalphy, 0);
		}

		//System.out.println("temperatur Stream 1 out " + outStream[0].getTemperature());
		//System.out.println("temperatur Stream 0 out " + outStream[1].getTemperature());
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

}
