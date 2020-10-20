/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.compressor.Compressor;
import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.mixer.Mixer;
import neqsim.processSimulation.processEquipment.pump.Pump;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator;
import neqsim.processSimulation.processEquipment.separator.TwoPhaseSeparator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processEquipment.util.Recycle;
import neqsim.processSimulation.processEquipment.util.SetPoint;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;
import neqsim.processSimulation.processSystem.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

/**
 *
 * @author esol
 */
public class OffshoreProcess3 {

	private static final long serialVersionUID = 1000;

	public static void main(String[] args) {
		neqsim.thermo.system.SystemInterface testSystem = new SystemSrkEos(354.2, 120.0);

		testSystem.addComponent("nitrogen", 1.0);
		testSystem.addComponent("CO2", 1.42);
		testSystem.addComponent("methane", 60.88);
		testSystem.addComponent("ethane", 8.07);
		testSystem.addComponent("propane", 3.54);
		testSystem.addComponent("i-butane", 0.54);
		testSystem.addComponent("n-butane", 0.2);
		testSystem.addComponent("i-pentane", 0.21);
		testSystem.addComponent("n-pentane", 0.19);
		testSystem.addComponent("n-hexane", 0.28);
		testSystem.addComponent("n-heptane", 5.0);
		testSystem.addComponent("water", 1.5);

		testSystem.createDatabase(true);
		testSystem.setMixingRule(2);
		testSystem.setMultiPhaseCheck(true);
		neqsim.thermo.system.SystemInterface fluid3 = neqsim.thermo.Fluid.create("black oil with water");
		
		Stream wellStream = new Stream("well stream", fluid3);
		wellStream.setFlowRate(14.23, "MSm3/day");
		wellStream.setTemperature(40.0, "C");
		wellStream.setPressure(120.0, "bara");

		ThrottlingValve valve = new ThrottlingValve("inlet choke valve", wellStream);
		valve.setOutletPressure(52.21);

		ThreePhaseSeparator inletSeparator = new ThreePhaseSeparator("1st stage separator", valve.getOutStream());
		inletSeparator.setEntrainment(0.01, "mole", "oil", "aqueous");
		Stream oilToInletSep = new Stream((SystemInterface) testSystem.clone());
		oilToInletSep.setFlowRate(1e-10, "kg/hr");
		inletSeparator.addStream(oilToInletSep);

		ThrottlingValve valve2 = new ThrottlingValve(inletSeparator.getOilOutStream());
		valve2.setName("oil HP to MP valve");
		valve2.setOutletPressure(15.0);

		Heater cooler1 = new Heater(valve2.getOutStream());
		cooler1.setName("oil cooler/heater to 2nd stage");
		cooler1.setOutTemperature(323.15);

		ThrottlingValve waterDPvalve = new ThrottlingValve(inletSeparator.getWaterOutStream());
		waterDPvalve.setName("Water HP to LP valve");
		waterDPvalve.setOutletPressure(1.01325);

		Separator waterStabSep = new Separator("water degasing separator", waterDPvalve.getOutStream());

		Stream waterToTreatment = new Stream(waterStabSep.getLiquidOutStream());
		waterToTreatment.setName("water to treatment");
		
		ThreePhaseSeparator mpseparator = new ThreePhaseSeparator("2nd stage separator", cooler1.getOutStream());

		Stream oilToSep = new Stream((SystemInterface) testSystem.clone());
		oilToSep.setFlowRate(1e-10, "kg/hr");
		mpseparator.addStream(oilToSep);

		ThrottlingValve valvempValve = new ThrottlingValve(mpseparator.getOilOutStream());
		valvempValve.setName("oil MP to LP valve");
		valvempValve.setOutletPressure(2.8);

		ThreePhaseSeparator lpseparator = new ThreePhaseSeparator("3rd stage separator", valvempValve.getOutStream());
		Stream stableOilStream  = (Stream) lpseparator.getOilOutStream();
		stableOilStream.setName("stable oil");
		
		Compressor lpcompressor = new Compressor(lpseparator.getGasOutStream());
		lpcompressor.setName("1st stage recompressor");
		//lpcompressor.setOutletPressure(15.0);
		SetPoint compressorPresSet2 = new SetPoint("comp pres LP set", lpcompressor, "pressure",valve2.getOutStream());
		
		
		Heater lpgasheater = new Heater(lpcompressor.getOutStream());
		lpgasheater.setOutTemperature(290.0);

		Separator lpscrubber = new Separator("2nd stage scrubber", lpgasheater.getOutStream());
		Stream liqFromlpscrubber = (Stream) lpscrubber.getLiquidOutStream();

		Recycle lpscrubberResyc = new Recycle("mpscrubberRes");
		lpscrubberResyc.addStream(liqFromlpscrubber);
		lpscrubberResyc.setOutletStream(oilToSep);

		Mixer mixermp = new Mixer("mp gas mixer");
		mixermp.addStream(lpscrubber.getGasOutStream());
		mixermp.addStream(mpseparator.getGasOutStream());

		Compressor compressor2stage = new Compressor(mixermp.getOutStream());
		compressor2stage.setName("2nd stage recompressor");
	//	compressor2stage.setOutletPressure(75.0);

		SetPoint compressorPresSet = new SetPoint("comp pres set", compressor2stage, "pressure",inletSeparator);
		
		Heater secondndstagecooler = new Heater(compressor2stage.getOutStream());
		secondndstagecooler.setName("2nd stage cooler");
		secondndstagecooler.setOutTemperature(290.0);

		Separator scrubbberfrom2ndstage = new Separator("scrubber of mix gas HP", secondndstagecooler.getOutStream());

		Mixer mixer = new Mixer("HP rich gas mixer");
		mixer.addStream(inletSeparator.getGasOutStream());
		mixer.addStream(scrubbberfrom2ndstage.getGasOutStream());

		Heater dewPointScrubberCooler = new Heater(mixer.getOutStream());
		dewPointScrubberCooler.setName("dew point scrubber cooler2");
		dewPointScrubberCooler.setOutTemperature(273.15+32.3);

		Separator mpscrubber = new Separator("dew point control scrubber", dewPointScrubberCooler.getOutStream());

		Stream liqFrommpscrubber = (Stream) mpscrubber.getLiquidOutStream();

		Recycle mpscrubberResyc = new Recycle("hpscrubberRes");
		mpscrubberResyc.addStream(liqFrommpscrubber);
		mpscrubberResyc.addStream(scrubbberfrom2ndstage.getLiquidOutStream());
		mpscrubberResyc.setOutletStream(oilToSep);

		Stream richGas = new Stream(mpscrubber.getGasOutStream());
		richGas.setName("rich gas");

		neqsim.processSimulation.processSystem.ProcessSystem operations = new neqsim.processSimulation.processSystem.ProcessSystem();
		operations.add(wellStream);
		operations.add(valve);
		operations.add(inletSeparator);
		
		operations.add(oilToInletSep);

		operations.add(valve2);
		operations.add(cooler1);
		operations.add(waterDPvalve);
		operations.add(waterStabSep);
		operations.add(waterToTreatment);
		operations.add(mpseparator);
		operations.add(oilToSep);
		operations.add(valvempValve);
		operations.add(lpseparator);
		operations.add(stableOilStream);
		operations.add(compressorPresSet2);
		operations.add(lpcompressor);
		operations.add(lpgasheater);
		operations.add(lpscrubber);
		operations.add(liqFromlpscrubber);
		operations.add(lpscrubberResyc);
		operations.add(mixermp);

		operations.add(compressorPresSet);
		operations.add(compressor2stage);
		operations.add(secondndstagecooler);
		operations.add(scrubbberfrom2ndstage);
		operations.add(mixer);
		operations.add(dewPointScrubberCooler);
		operations.add(mpscrubber);
		operations.add(liqFrommpscrubber);
		operations.add(mpscrubberResyc);
		operations.add(richGas);
		
		
	
		//ProcessSystem operations2 = operations.open("c:/temp/offshorePro.neqsim");
		//((Heater) operations2.getUnit("dew point scrubber cooler2")).setOutTemperature(298.15);
		//operations2.run();
		//((Stream) operations2.getUnit("rich gas")).phaseEnvelope();
		
		// operations.displayResult();
		// inletSeparator.displayResult();
		// richGas.displayResult();
		//richGas.displayResult();
		//mpseparator.displayResult();
	
		// oilToSep.displayResult();
		// lppump.displayResult();
		
	    operations.run();
	//	System.out.println("second stage comp power " + ((Compressor) operations.getUnit("2nd stage recompressor")).getPower()/1.0e3 + " kW");
//		System.out.println("first stage   comp power " + ((Compressor) operations.getUnit("1st stage recompressor")).getPower()/1.0e3 + " kW");
		
		System.out.println("gas from inlet separator " + ((Separator) operations.getUnit("1st stage separator")).getGasOutStream().getFluid().getFlowRate("MSm3/day"));
	
		
		// liqFromlpscrubber.displayResult();
		// richGas.phaseEnvelope();
		// richGas.displayResult();
		operations.save("c:/temp/offshorePro.neqsim");
		// ProcessSystem operations2 = operations.open("c:/temp/offshorePro.neqsim");
		// operations2.run();
		// cooler1stagecomp.getOutStream().phaseEnvelope();
	}
}
