/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.processSimulation.processEquipment.distillation;

import java.util.ArrayList;
import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.mixer.Mixer;
import neqsim.processSimulation.processEquipment.mixer.MixerInterface;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author ESOL
 */
public class DistillationColumn extends ProcessEquipmentBaseClass
		implements ProcessEquipmentInterface, DistillationInterface {

	private static final long serialVersionUID = 1000;
	private boolean doInitializion = true;
	boolean hasReboiler = false, hasCondenser = false;
	protected ArrayList<SimpleTray> trays = new ArrayList<SimpleTray>(0);
	double condenserCoolingDuty = 10.0;
	private double reboilerTemperature = 273.15;
	private double condeserTemperature = 270.15;
	double topTrayPressure = -1.0, bottomTrayPressure = -1.0;
	int numberOfTrays = 1;
	private int feedTrayNumber = 1;
	StreamInterface stream_3 = new Stream(), gasOutStream = new Stream(), liquidOutStream = new Stream(),
			feedStream = null;
	boolean stream_3isset = false;
	neqsim.processSimulation.processSystem.ProcessSystem distoperations;
	Heater heater;
	Separator separator2;

	public DistillationColumn(int numberOfTraysLocal, boolean hasReboiler, boolean hasCondenser) {
		this.hasReboiler = hasReboiler;
		this.hasCondenser = hasCondenser;
		distoperations = new neqsim.processSimulation.processSystem.ProcessSystem();
		this.numberOfTrays = numberOfTraysLocal;
		if (hasReboiler) {
			trays.add(new Reboiler());
			this.numberOfTrays++;
		}
		for (int i = 0; i < numberOfTraysLocal; i++) {
			trays.add(new SimpleTray());
		}
		if (hasCondenser) {
			trays.add(new Condenser());
			this.numberOfTrays++;
		}
		for (int i = 0; i < this.numberOfTrays; i++) {
			distoperations.add((ProcessEquipmentInterface) trays.get(i));
		}
	}

	public void addFeedStream(StreamInterface inputStream, int feedTrayNumber) {
		feedStream = inputStream;
		getTray(feedTrayNumber).addStream(inputStream);
		setFeedTrayNumber(feedTrayNumber);
		double moles = inputStream.getThermoSystem().getTotalNumberOfMoles();
		gasOutStream.setThermoSystem((SystemInterface) inputStream.getThermoSystem().clone());
		gasOutStream.getThermoSystem().setTotalNumberOfMoles(moles / 2.0);
		liquidOutStream.setThermoSystem((SystemInterface) inputStream.getThermoSystem().clone());
		liquidOutStream.getThermoSystem().setTotalNumberOfMoles(moles / 2.0);
	}

	public void init() {
		if (!isDoInitializion()) {
			return;
		}
		setDoInitializion(false);
		((Runnable) trays.get(feedTrayNumber)).run();
		((MixerInterface) trays.get(numberOfTrays - 1))
				.addStream(((SimpleTray) trays.get(feedTrayNumber)).getGasOutStream());
		((Mixer) trays.get(numberOfTrays - 1)).getStream(0).getThermoSystem().setTotalNumberOfMoles(
				((Mixer) trays.get(numberOfTrays - 1)).getStream(0).getThermoSystem().getTotalNumberOfMoles() * (1.0));
		((MixerInterface) trays.get(0)).addStream(((SimpleTray) trays.get(feedTrayNumber)).getLiquidOutStream());
		int streamNumbReboil = ((Reboiler) trays.get(0)).getNumberOfInputStreams() - 1;
		((Mixer) trays.get(0)).getStream(streamNumbReboil).getThermoSystem().setTotalNumberOfMoles(
				((Mixer) trays.get(0)).getStream(streamNumbReboil).getThermoSystem().getTotalNumberOfMoles() * (1.0));

		((Runnable) trays.get(numberOfTrays - 1)).run();
		((Runnable) trays.get(0)).run();

		condeserTemperature = ((MixerInterface) trays.get(numberOfTrays - 1)).getThermoSystem().getTemperature();
		reboilerTemperature = ((MixerInterface) trays.get(0)).getThermoSystem().getTemperature();

		double deltaTemp = (reboilerTemperature - condeserTemperature) / (numberOfTrays * 1.0);
		double feedTrayTemperature = getTray(getFeedTrayNumber()).getThermoSystem().getTemperature();

		double deltaTempCondeser = (feedTrayTemperature - condeserTemperature)
				/ (numberOfTrays * 1.0 - feedTrayNumber - 1);
		double deltaTempReboiler = (reboilerTemperature - feedTrayTemperature) / (feedTrayNumber * 1.0);

		double delta = 0;
		for (int i = feedTrayNumber + 1; i < numberOfTrays; i++) {
			delta += deltaTempCondeser;
			((Mixer) trays.get(i))
					.setTemperature(getTray(getFeedTrayNumber()).getThermoSystem().getTemperature() - delta);
		}
		delta = 0;
		for (int i = feedTrayNumber - 1; i >= 0; i--) {
			delta += deltaTempReboiler;
			((Mixer) trays.get(i))
					.setTemperature(getTray(getFeedTrayNumber()).getThermoSystem().getTemperature() + delta);
		}

		for (int i = 1; i < numberOfTrays - 1; i++) {
			((MixerInterface) trays.get(i)).addStream(((SimpleTray) trays.get(i - 1)).getGasOutStream());
			((SimpleTray) trays.get(i)).init();
			((Runnable) trays.get(i)).run();
		}

		((MixerInterface) trays.get(numberOfTrays - 1)).replaceStream(0,
				((SimpleTray) trays.get(numberOfTrays - 2)).getGasOutStream());
		((SimpleTray) trays.get(numberOfTrays - 1)).init();
		((Runnable) trays.get(numberOfTrays - 1)).run();

		for (int i = numberOfTrays - 2; i >= 1; i--) {
			((MixerInterface) trays.get(i)).addStream(((SimpleTray) trays.get(i + 1)).getLiquidOutStream());
			((SimpleTray) trays.get(i)).init();
			((Runnable) trays.get(i)).run();
		}
		int streamNumb = ((Reboiler) trays.get(0)).getNumberOfInputStreams() - 1;
		((MixerInterface) trays.get(0)).replaceStream(streamNumb, ((SimpleTray) trays.get(1)).getLiquidOutStream());
		((SimpleTray) trays.get(0)).init();
		((Runnable) trays.get(0)).run();

	}

	public StreamInterface getGasOutStream() {
		return gasOutStream;
	}

	public StreamInterface getLiquidOutStream() {
		return liquidOutStream;
	}

	public SimpleTray getTray(int trayNumber) {
		return (SimpleTray) trays.get(trayNumber);
	}

	public void setNumberOfTrays(int number) {
		int oldNumberOfTrays = numberOfTrays;

		int tempNumberOfTrays = number;

		if (hasReboiler) {
			tempNumberOfTrays++;
		}
		if (hasCondenser) {
			tempNumberOfTrays++;
		}

		int change = tempNumberOfTrays - oldNumberOfTrays;

		if (change > 0) {
			for (int i = 0; i < change; i++) {
				trays.add(1, new SimpleTray());
			}
		} else if (change < 0) {
			for (int i = 0; i > change; i--) {
				trays.remove(1);
			}
		}
		numberOfTrays = tempNumberOfTrays;

		setDoInitializion(true);
		init();
	}

	public void setTopCondeserDuty(double duty) {
		condenserCoolingDuty = duty;
	}

	public void setTopPressure(double topPressure) {
		topTrayPressure = topPressure;
	}

	public void setBottomPressure(double bottomPressure) {
		bottomTrayPressure = bottomPressure;
	}

	public void run() {
		// trays.get(0).getStream(0).getFluid().display();
		double dp = 0.0;
		if (bottomTrayPressure > 0 && topTrayPressure > 0) {
			dp = (bottomTrayPressure - topTrayPressure)/(numberOfTrays-1.0);
		}

		for (int i = 0; i < numberOfTrays; i++) {
			trays.get(i).setPressure(bottomTrayPressure - i * dp);
		}
		getTray(feedTrayNumber).getStream(0).setThermoSystem((SystemInterface) feedStream.getThermoSystem().clone());
		if (isDoInitializion()) {
			this.init();
		}
		double err = 1.0;
		int iter = 0;
		double[] oldtemps = new double[numberOfTrays];

		do {
			iter++;
			err = 0.0;
			for (int i = 0; i < numberOfTrays; i++) {
				oldtemps[i] = ((MixerInterface) trays.get(i)).getThermoSystem().getTemperature();
			}
			/*
			 * for (int i = feedTrayNumber; i < numberOfTrays; i++) { ((Runnable)
			 * trays.get(i)).run(); if (i < numberOfTrays - 1) { ((Mixer) trays.get(i +
			 * 1)).replaceStream(0, ((SimpleTray) trays.get(i)).getGasOutStream()); } }
			 */
			((Runnable) trays.get(feedTrayNumber)).run();
			for (int i = feedTrayNumber; i > 1; i--) {
				((Mixer) trays.get(i - 1)).replaceStream(1, ((SimpleTray) trays.get(i)).getLiquidOutStream());
				trays.get(i - 1).setPressure(bottomTrayPressure - (i - 1) * dp);
				((Runnable) trays.get(i - 1)).run();
			}
			int streamNumb = ((Reboiler) trays.get(0)).getNumberOfInputStreams() - 1;

			((Mixer) trays.get(0)).replaceStream(streamNumb, ((SimpleTray) trays.get(1)).getLiquidOutStream());
			((Runnable) trays.get(0)).run();

			for (int i = 1; i <= numberOfTrays - 1; i++) {
				int replaceStream = 0;
				if (i == feedTrayNumber)
					replaceStream = 1;
				((Mixer) trays.get(i)).replaceStream(replaceStream, ((SimpleTray) trays.get(i - 1)).getGasOutStream());
				((Runnable) trays.get(i)).run();
			}

			for (int i = numberOfTrays - 2; i == 1; i--) {
				int replaceStream = 1;
				if (i == feedTrayNumber)
					replaceStream = 2;
				((Mixer) trays.get(i)).replaceStream(replaceStream,
						((SimpleTray) trays.get(i + 1)).getLiquidOutStream());
				((Runnable) trays.get(i)).run();
			}

			((Mixer) trays.get(0)).replaceStream(streamNumb, ((SimpleTray) trays.get(1)).getLiquidOutStream());
			((Runnable) trays.get(0)).run();
			/*
			 * ((Mixer) trays.get(feedTrayNumber)).replaceStream(2, ((SimpleTray)
			 * trays.get(feedTrayNumber + 1)).getLiquidOutStream()); ((Runnable)
			 * trays.get(feedTrayNumber)).run();
			 * 
			 * for (int i = feedTrayNumber + 1; i < numberOfTrays - 1; i++) { ((Mixer)
			 * trays.get(i)).replaceStream(1, ((SimpleTray) trays.get(i +
			 * 1)).getLiquidOutStream()); ((Runnable) trays.get(i)).run(); }
			 */
			for (int i = 0; i < numberOfTrays; i++) {
				err += Math.abs(oldtemps[i] - ((MixerInterface) trays.get(i)).getThermoSystem().getTemperature());
			}
			System.out.println("error iter " + err + " iteration " + iter);
			// massBalanceCheck();
		} while (err > 1e-2 && iter < 10);

		for (int i = 0; i <= numberOfTrays - 1; i++) {
			// ((Mixer) trays.get(i)).getFluid().display();
			// System.out.println("tray " + i + " temperature " + ((Mixer)
			// trays.get(i)).getFluid().getTemperature("C"));

		}

		// massBalanceCheck();

		gasOutStream.setThermoSystem((SystemInterface) ((SimpleTray) trays.get(numberOfTrays - 1)).getGasOutStream()
				.getThermoSystem().clone());
		liquidOutStream.setThermoSystem(
				(SystemInterface) ((SimpleTray) trays.get(0)).getLiquidOutStream().getThermoSystem().clone());
		// System.out.println("feed flow rate "
		// +((Stream)feedStream).getFlowRate("kg/hr"));
		// System.out.println("feed stripping rate "
		// +((Stream)((Mixer)trays.get(0)).getStream(0)).getFlowRate("kg/hr"));
		// System.out.println("flow rate from condenser " +
		// getCondenser().getGasOutStream().getFlowRate("kg/hr"));

		// System.out.println("flow rate from reboiler " +
		// getReboiler().getLiquidOutStream().getFlowRate("kg/hr"));

		// gasOutStream = ((SimpleTray) trays.get(numberOfTrays - 1)).getGasOutStream();
		// liquidOutStream = ((SimpleTray) trays.get(0)).getLiquidOutStream();
	}

	public void displayResult() {
		distoperations.displayResult();
	}

	public void massBalanceCheck() {

		double[] massInput = new double[numberOfTrays];
		double[] massOutput = new double[numberOfTrays];
		double[] massBalance = new double[numberOfTrays];
		double local = 0.0;
		for (int i = 0; i < numberOfTrays; i++) {
			int numberOfInputStreams = trays.get(i).getNumberOfInputStreams();
			for (int j = 0; j < numberOfInputStreams; j++) {
				massInput[i] += trays.get(i).getStream(j).getFluid().getFlowRate("kg/hr");
				local = trays.get(i).getStream(j).getFluid().getFlowRate("kg/hr");
				// System.out.println(local);
			}
			massOutput[i] += trays.get(i).getGasOutStream().getFlowRate("kg/hr");
			massOutput[i] += trays.get(i).getLiquidOutStream().getFlowRate("kg/hr");
			massBalance[i] = massInput[i] - massOutput[i];
			System.out.println("tray " + i + " number of input streams " + numberOfInputStreams + " massinput "
					+ massInput[i] + " massoutput " + massOutput[i] + " massbalance " + massBalance[i] + " gasout "
					+ trays.get(i).getGasOutStream().getFlowRate("kg/hr") + " liquidout "
					+ trays.get(i).getLiquidOutStream().getFlowRate("kg/hr") + " pressure "+ trays.get(i).getGasOutStream().getPressure() + " temperature "+ trays.get(i).getGasOutStream().getTemperature("C"));
		}
	}

	public static void main(String[] args) {
		neqsim.thermo.system.SystemInterface testSystem = new neqsim.thermo.system.SystemSrkEos((273.15 - 0.0), 15.000);
		// testSystem.addComponent("methane", 10.00);
		testSystem.addComponent("ethane", 10.0);
		testSystem.addComponent("CO2", 10.0);
		testSystem.addComponent("propane", 20.0);
		// testSystem.addComponent("i-butane", 5.0);
		// testSystem.addComponent("n-hexane", 15.0);
		// testSystem.addComponent("n-heptane", 30.0);
		// testSystem.addComponent("n-octane", 4.0);
		// testSystem.addComponent("n-nonane", 3.0);
		testSystem.createDatabase(true);
		testSystem.setMixingRule(2);
		ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
		ops.TPflash();
		testSystem.display();
		Stream stream_1 = new Stream("Stream1", testSystem);

		DistillationColumn column = new DistillationColumn(5, true, true);
		column.addFeedStream(stream_1, 3);
		// column.getReboiler().setHeatInput(520000.0);
		((Reboiler) column.getReboiler()).setRefluxRatio(0.5);
		// column.getCondenser().setHeatInput(-70000.0);
		// ((Condenser) column.getCondenser()).setRefluxRatio(0.2);

		neqsim.processSimulation.processSystem.ProcessSystem operations = new neqsim.processSimulation.processSystem.ProcessSystem();
		operations.add(stream_1);
		operations.add(column);
		operations.run();

		column.displayResult();
		System.out.println("reboiler duty" + ((Reboiler) column.getReboiler()).getDuty());
		System.out.println("condeser duty" + ((Condenser) column.getCondenser()).getDuty());

	}

	public SimpleTray getReboiler() {
		return (SimpleTray) trays.get(0);
	}

	public SimpleTray getCondenser() {
		return (SimpleTray) trays.get(trays.size() - 1);
	}

	/**
	 * @return the reboilerTemperature
	 */
	public double getReboilerTemperature() {
		return reboilerTemperature;
	}

	/**
	 * @param reboilerTemperature the reboilerTemperature to set
	 */
	public void setReboilerTemperature(double reboilerTemperature) {
		this.reboilerTemperature = reboilerTemperature;
	}

	/**
	 * @return the condeserTemperature
	 */
	public double getCondenserTemperature() {
		return condeserTemperature;
	}

	/**
	 * @param condeserTemperature the condeserTemperature to set
	 */
	public void setCondenserTemperature(double condeserTemperature) {
		this.condeserTemperature = condeserTemperature;
	}

	/**
	 * @return the feedTrayNumber
	 */
	public int getFeedTrayNumber() {
		return feedTrayNumber;
	}

	/**
	 * @param feedTrayNumber the feedTrayNumber to set
	 */
	public void setFeedTrayNumber(int feedTrayNumber) {
		this.feedTrayNumber = feedTrayNumber;
	}

	public boolean isDoInitializion() {
		return doInitializion;
	}

	public void setDoInitializion(boolean doInitializion) {
		this.doInitializion = doInitializion;
	}
}
