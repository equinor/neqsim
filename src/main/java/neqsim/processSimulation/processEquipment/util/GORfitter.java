package neqsim.processSimulation.processEquipment.util;

import neqsim.processSimulation.measurementDevice.MultiPhaseMeter;
import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * GORfitter class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class GORfitter extends ProcessEquipmentBaseClass {
	private static final long serialVersionUID = 1000;

	public StreamInterface inletStream = null;
	public StreamInterface outletStream = null;
	double pressure = 1.01325, temperature = 15.0;
	private String referenceConditions = "standard"; // "actual";
	private boolean fitAsGVF = false;

	private double GOR = 120.0, GVF;
	String unitT = "C", unitP = "bara";

	public GORfitter() {
		super("GOR fitter");
	}

	/**
	 * <p>
	 * Constructor for GORfitter.
	 * </p>
	 *
	 * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
	 *        object
	 */
	public GORfitter(StreamInterface stream) {
		this();
		this.inletStream = stream;
		this.outletStream = stream.clone();
	}

	public double getGFV() {
		return GVF;
	}

	/**
	 * <p>
	 * Constructor for GORfitter.
	 * </p>
	 *
	 * @param name a {@link java.lang.String} object
	 * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
	 *        object
	 */
	public GORfitter(String name, StreamInterface stream) {
		this(stream);
		this.name = name;
	}

	/**
	 * <p>
	 * Setter for the field <code>inletStream</code>.
	 * </p>
	 *
	 * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
	 *        object
	 */
	public void setInletStream(StreamInterface inletStream) {
		this.inletStream = inletStream;
		try {
			this.outletStream = inletStream.clone();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * <p>
	 * getOutStream.
	 * </p>
	 *
	 * @return a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
	 */
	public StreamInterface getOutStream() {
		return outletStream;
	}

	/**
	 * <p>
	 * Getter for the field <code>pressure</code>.
	 * </p>
	 *
	 * @return a double
	 */
	public double getPressure() {
		return pressure;
	}

	/**
	 * <p>
	 * Setter for the field <code>pressure</code>.
	 * </p>
	 *
	 * @param pressure a double
	 * @param unitP a {@link java.lang.String} object
	 */
	public void setPressure(double pressure, String unitP) {
		this.pressure = pressure;
		this.unitP = unitP;
	}

	/**
	 * <p>
	 * getTemperature.
	 * </p>
	 *
	 * @return a double
	 */
	public double getTemperature() {
		return temperature;
	}

	/**
	 * <p>
	 * Setter for the field <code>temperature</code>.
	 * </p>
	 *
	 * @param temperature a double
	 * @param unitT a {@link java.lang.String} object
	 */
	public void setTemperature(double temperature, String unitT) {
		this.temperature = temperature;
		this.unitT = unitT;
	}

	/** {@inheritDoc} */
	@Override
	public void run() {
		SystemInterface tempFluid = inletStream.getThermoSystem().clone();
		double flow = tempFluid.getFlowRate("kg/sec");

		if(GOR<1e-15) {
		  outletStream.setThermoSystem(tempFluid);
		  return;
		}
		if(flow<1e-6) {
		  outletStream.setThermoSystem(tempFluid);
		  return;
		}
		if(GOR==0 && tempFluid.hasPhaseType("gas")) {
		  tempFluid.removePhase(0);
		  ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempFluid);
	        try {
	            thermoOps.TPflash();
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	        outletStream.setThermoSystem(tempFluid);
	        return;
		}

		if (!getReferenceConditions().equals("actual")) {
			tempFluid.setTemperature(15.0, "C");
			tempFluid.setPressure(1.01325, "bara");
		}
		ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempFluid);
		try {
			thermoOps.TPflash();
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (!tempFluid.hasPhaseType("gas") || !tempFluid.hasPhaseType("oil")) {
			outletStream = inletStream.clone();
			return;
		}
		tempFluid.initPhysicalProperties("density");
		double currGOR = tempFluid.getPhase("gas").getCorrectedVolume()
				/ tempFluid.getPhase("oil").getCorrectedVolume();

		if (fitAsGVF) {
			GOR = tempFluid.getPhase("oil").getCorrectedVolume() * getGOR()
					/ (tempFluid.getPhase("oil").getCorrectedVolume()
							- tempFluid.getPhase("oil").getCorrectedVolume() * getGOR());
			// GVF*Vo/(Vo-GVF*Vo)
			// currGOR = tempFluid.getPhase("gas").getCorrectedVolume()
			// / (tempFluid.getPhase("oil").getCorrectedVolume() +
			// tempFluid.getPhase("gas").getCorrectedVolume());
		}

		double dev = getGOR() / currGOR;
		// System.out.println("dev "+dev);

		double[] moleChange = new double[tempFluid.getNumberOfComponents()];
		for (int i = 0; i < tempFluid.getNumberOfComponents(); i++) {
			moleChange[i] = (dev - 1.0)
					* tempFluid.getPhase("gas").getComponent(i).getNumberOfMolesInPhase();
		}
		tempFluid.init(0);
		for (int i = 0; i < tempFluid.getNumberOfComponents(); i++) {
			tempFluid.addComponent(i, moleChange[i]);
		}
		tempFluid.setPressure((inletStream.getThermoSystem()).getPressure());
		tempFluid.setTemperature((inletStream.getThermoSystem()).getTemperature());
		tempFluid.setTotalFlowRate(flow, "kg/sec");
		try {
			thermoOps.TPflash();
		} catch (Exception e) {
			e.printStackTrace();
		}
		outletStream.setThermoSystem(tempFluid);
		GVF = tempFluid.getPhase("gas").getCorrectedVolume()
				/ (tempFluid.getPhase("oil").getCorrectedVolume()
						+ tempFluid.getPhase("gas").getCorrectedVolume());
		return;
	}


	/**
	 * <p>
	 * main.
	 * </p>
	 *
	 * @param args an array of {@link java.lang.String} objects
	 */
	public static void main(String[] args) {
		SystemInterface testFluid = new SystemSrkEos(338.15, 50.0);
		testFluid.addComponent("nitrogen", 1.205);
		testFluid.addComponent("CO2", 1.340);
		testFluid.addComponent("methane", 87.974);
		testFluid.addComponent("ethane", 5.258);
		testFluid.addComponent("propane", 3.283);
		testFluid.addComponent("i-butane", 0.082);
		testFluid.addComponent("n-butane", 0.487);
		testFluid.addComponent("i-pentane", 0.056);
		testFluid.addComponent("n-pentane", 1.053);
		testFluid.addComponent("nC10", 4.053);
		testFluid.setMixingRule(2);
		testFluid.setMultiPhaseCheck(true);

		testFluid.setTemperature(90.0, "C");
		testFluid.setPressure(60.0, "bara");
		testFluid.setTotalFlowRate(1e6, "kg/hr");

		Stream stream_1 = new Stream("Stream1", testFluid);

		MultiPhaseMeter multiPhaseMeter = new MultiPhaseMeter("test", stream_1);
		multiPhaseMeter.setTemperature(90.0, "C");
		multiPhaseMeter.setPressure(60.0, "bara");

		GORfitter gORFItter = new GORfitter("test", stream_1);
		gORFItter.setTemperature(15.0, "C");
		gORFItter.setPressure(1.01325, "bara");
		gORFItter.setReferenceConditions("actual");
		// gORFItter.setGVF(0.1);
		gORFItter.setGOR(10.1);

        Stream stream_2 = new Stream("stream_2", gORFItter.getOutStream());

		MultiPhaseMeter multiPhaseMeter2 = new MultiPhaseMeter("test", stream_2);
		multiPhaseMeter2.setTemperature(90.0, "C");
		multiPhaseMeter2.setPressure(60.0, "bara");

		neqsim.processSimulation.processSystem.ProcessSystem operations =
				new neqsim.processSimulation.processSystem.ProcessSystem();
		operations.add(stream_1);
		operations.add(multiPhaseMeter);
		operations.add(gORFItter);
		operations.add(stream_2);
		operations.add(multiPhaseMeter2);
		operations.run();
		System.out.println("GOR " + multiPhaseMeter.getMeasuredValue("GOR"));
		System.out.println("GOR_std " + multiPhaseMeter.getMeasuredValue("GOR_std"));
		System.out.println("GOR2 " + multiPhaseMeter2.getMeasuredValue("GOR"));
		System.out.println("GOR2_std " + multiPhaseMeter2.getMeasuredValue("GOR_std"));
		System.out.println("stream_2 flow " + stream_2.getFlowRate("kg/hr"));
	}

	/**
	 * <p>
	 * getGOR.
	 * </p>
	 *
	 * @return a double
	 */
	public double getGOR() {
		return GOR;
	}

	/**
	 * <p>
	 * setGOR.
	 * </p>
	 *
	 * @param gOR a double
	 */
	public void setGOR(double gOR) {
		fitAsGVF = false;
		this.GOR = gOR;
	}

	public void setGVF(double gvf) {
		fitAsGVF = true;
		this.GOR = gvf;
	}

	/**
	 * @return the referenceConditions
	 */
	public String getReferenceConditions() {
		return referenceConditions;
	}

	/**
	 * @param referenceConditions the referenceConditions to set
	 */
	public void setReferenceConditions(String referenceConditions) {
		this.referenceConditions = referenceConditions;
	}

	/**
	 * @return the fitAsGVF
	 */
	public boolean isFitAsGVF() {
		return fitAsGVF;
	}

	/**
	 * @param fitAsGVF the fitAsGVF to set
	 */
	public void setFitAsGVF(boolean fitAsGVF) {
		this.fitAsGVF = fitAsGVF;
	}
}
