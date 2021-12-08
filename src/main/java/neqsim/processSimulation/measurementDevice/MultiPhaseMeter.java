package neqsim.processSimulation.measurementDevice;

import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class MultiPhaseMeter extends MeasurementDeviceBaseClass {
	private static final long serialVersionUID = 1000;

	protected StreamInterface stream = null;
	double pressure = 10.0, temperature = 298.15;
	String unitT, unitP;

	/**
	 * Creates a new instance of MultiPhaseMeter
	 */
	public MultiPhaseMeter() {
		name = "Mutli Phase Meter";
	}

	public MultiPhaseMeter(StreamInterface stream) {
		this();
		name = "Mutli Phase Meter";
		this.stream = stream;
	}

	public MultiPhaseMeter(String name, StreamInterface stream) {
		this();
		this.name = name;
		this.stream = stream;
	}

	public double getPressure() {
		return pressure;
	}

	public void setPressure(double pressure, String unitP) {
		this.pressure = pressure;
		this.unitP = unitP;
	}

	public double getTemperautre() {
		return pressure;
	}

	public void setTemperature(double temperature, String unitT) {
		this.temperature = temperature;
		this.unitT = unitT;
	}

	@Override
	public double getMeasuredValue() {
		return stream.getThermoSystem().getFlowRate("kg/hr");
	}

	@Override
	public double getMeasuredValue(String measurement) {
		if (measurement.equals("mass rate")) {
			return stream.getThermoSystem().getFlowRate("kg/hr");
		}

		if (measurement.equals("GOR")) {
			SystemInterface tempFluid = (SystemInterface) stream.getThermoSystem().clone();
			tempFluid.setTemperature(temperature, unitT);
			tempFluid.setPressure(pressure, unitP);
			ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempFluid);
			try {
				thermoOps.TPflash();
			} catch (Exception e) {
				e.printStackTrace();
			}
			// tempFluid.display();
			if (!tempFluid.hasPhaseType("gas")) {
				return 0.0;
			}
			if (!tempFluid.hasPhaseType("oil")) {
				return Double.NaN;
			}
			tempFluid.initPhysicalProperties("density");
			return tempFluid.getPhase("gas").getCorrectedVolume() / tempFluid.getPhase("oil").getCorrectedVolume();
		}
		if (measurement.equals("gasDensity") || measurement.equals("oilDensity")
				|| measurement.equals("waterDensity")) {
			SystemInterface tempFluid = (SystemInterface) stream.getThermoSystem().clone();
			tempFluid.setTemperature(temperature, unitT);
			tempFluid.setPressure(pressure, unitP);
			ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempFluid);
			try {
				thermoOps.TPflash();
			} catch (Exception e) {
				e.printStackTrace();
			}
			tempFluid.initPhysicalProperties();
			if (measurement.equals("gasDensity")) {
				if (!tempFluid.hasPhaseType("gas")) {
					return 0.0;
				} else {
					return tempFluid.getPhase("gas").getDensity("kg/m3");
				}
			}
			if (measurement.equals("oilDensity")) {
				if (!tempFluid.hasPhaseType("oil")) {
					return 0.0;
				} else {
					return tempFluid.getPhase("oil").getDensity("kg/m3");
				}
			}
			if (measurement.equals("waterDensity")) {
				if (!tempFluid.hasPhaseType("aqueous")) {
					return 0.0;
				} else {
					return tempFluid.getPhase("aqueous").getDensity("kg/m3");
				}
			}
			return 0.0;
		} else if (measurement.equals("GOR_std")) {
			SystemInterface tempFluid = (SystemInterface) stream.getThermoSystem().clone();
			tempFluid.setTemperature(15.0, "C");
			tempFluid.setPressure(1.01325, "bara");
			ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempFluid);
			try {
				thermoOps.TPflash();
			} catch (Exception e) {
				e.printStackTrace();
			}
			// tempFluid.display();
			if (!tempFluid.hasPhaseType("gas")) {
				return 0.0;
			}
			if (!tempFluid.hasPhaseType("oil")) {
				return Double.NaN;
			}
			tempFluid.initPhysicalProperties("density");
			return tempFluid.getPhase("gas").getCorrectedVolume() / tempFluid.getPhase("oil").getCorrectedVolume();
		} else
			return 0.0;
	}

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

		testFluid.setTemperature(24.0, "C");
		testFluid.setPressure(48.0, "bara");
		testFluid.setTotalFlowRate(4.5, "MSm3/day");

		Stream stream_1 = new Stream("Stream1", testFluid);

		MultiPhaseMeter multiPhaseMeter = new MultiPhaseMeter("test", stream_1);
		multiPhaseMeter.setTemperature(90.0, "C");
		multiPhaseMeter.setPressure(60.0, "bara");

		neqsim.processSimulation.processSystem.ProcessSystem operations = new neqsim.processSimulation.processSystem.ProcessSystem();
		operations.add(stream_1);
		operations.add(multiPhaseMeter);
		operations.run();
		System.out.println("GOR " + multiPhaseMeter.getMeasuredValue("GOR"));
		System.out.println("GOR_std " + multiPhaseMeter.getMeasuredValue("GOR_std"));
	}
}
