package neqsim.processSimulation.processEquipment.absorber;

import java.util.ArrayList;

import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processEquipment.util.StreamSaturatorUtil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class ReactiveAbsorber extends SimpleAbsorber {

	protected ArrayList<StreamInterface> streams = new ArrayList(0);
	protected Stream gasInStream;
	protected Stream solventInStream;
	private Stream gasOutStream;
	private Stream solventOutStream;
	int solventStreamNumber = 0;
	protected Stream mixedStream;
	
	public void addGasInStream(StreamInterface newStream) {
		gasInStream = (Stream) newStream;
		gasOutStream = (Stream) newStream.clone();
		addStream(newStream);
	}

	public void addSolventInStream(StreamInterface newStream) {
		solventInStream = (Stream) newStream;
		solventOutStream = (Stream) newStream.clone();
		addStream(newStream);
		solventStreamNumber = streams.size() - 1;
	}
	
    @Override
	public void addStream(StreamInterface newStream) {
        streams.add(newStream);
        if (numberOfInputStreams == 0) {
            mixedStream = (Stream) streams.get(0).clone();
            mixedStream.getThermoSystem().setNumberOfPhases(2);
            mixedStream.getThermoSystem().reInitPhaseType();
            mixedStream.getThermoSystem().init(0);
            mixedStream.getThermoSystem().init(3);
        }

        numberOfInputStreams++;
    }
	
	  public void mixStream() {
	        int index = 0;
	        String compName = new String();

	        for (int k = 1; k < streams.size(); k++) {

	            for (int i = 0; i < streams.get(k).getThermoSystem().getPhases()[0]
	                    .getNumberOfComponents(); i++) {

	                boolean gotComponent = false;
	                String componentName = streams.get(k).getThermoSystem()
	                        .getPhases()[0].getComponents()[i].getName();
	                // System.out.println("adding: " + componentName);
	                int numberOfPhases = streams.get(k).getThermoSystem().getNumberOfPhases();

	                double moles = streams.get(k).getThermoSystem().getPhases()[0]
	                        .getComponents()[i].getNumberOfmoles();
	                // System.out.println("moles: " + moles + " " +
	                // mixedStream.getThermoSystem().getPhases()[0].getNumberOfComponents());
	                for (int p = 0; p < mixedStream.getThermoSystem().getPhases()[0].getNumberOfComponents(); p++) {
	                    if (mixedStream.getThermoSystem().getPhases()[0].getComponents()[p].getName()
	                            .equals(componentName)) {
	                        gotComponent = true;
	                        index = streams.get(0).getThermoSystem().getPhases()[0]
	                                .getComponents()[p].getComponentNumber();
	                        compName = streams.get(0).getThermoSystem()
	                                .getPhases()[0].getComponents()[p].getComponentName();

	                    }
	                }

	                if (gotComponent) {
	                    // System.out.println("adding moles starting....");
	                    mixedStream.getThermoSystem().addComponent(compName, moles);
	                    // mixedStream.getThermoSystem().init_x_y();
	                    // System.out.println("adding moles finished");
	                } else {
	                    // System.out.println("ikke gaa hit");
	                    mixedStream.getThermoSystem().addComponent(compName, moles);
	                }
	            }
	        }
	        mixedStream.getThermoSystem().init_x_y();
	        mixedStream.getThermoSystem().initBeta();
	        mixedStream.getThermoSystem().init(2);
	    }
	
	  @Override
		public void run() {
	        try {
	            double y0 = 0.0, y1 = 0.0, yN = gasInStream.getThermoSystem().getPhase(0).getComponent("water").getx();
	            double absorptionEffiency = 0.0;
	            
	            mixedStream.setThermoSystem(((SystemInterface) streams.get(0).getThermoSystem().clone()));
	            mixedStream.getThermoSystem().setNumberOfPhases(2);
	            mixedStream.getThermoSystem().reInitPhaseType();
	            mixedStream.getThermoSystem().init(0);
	            mixStream();
	            mixedStream.getThermoSystem().chemicalReactionInit();
	            mixedStream.getThermoSystem().createDatabase(true);
	            mixedStream.getThermoSystem().setMixingRule(10);
	            ThermodynamicOperations testOps = new ThermodynamicOperations(mixedStream.getThermoSystem());
	            testOps.TPflash();
	            mixedStream.getThermoSystem().display();
	        }
	        catch(Exception e) {
	        	e.printStackTrace();
	        }
	  }

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		neqsim.thermo.system.SystemInterface feedGas = new neqsim.thermo.system.SystemElectrolyteCPAstatoil(273.15 + 40.0,
				50.00);
		feedGas.addComponent("CO2", 1.42);
		feedGas.addComponent("methane", 90.88);
		feedGas.addComponent("ethane", 8.07);
		feedGas.addComponent("propane", 2.54);
		feedGas.addComponent("water", 0.0);
		feedGas.addComponent("MDEA", 0.0);
		feedGas.setMixingRule(10);
		feedGas.setMultiPhaseCheck(false);

		Stream dryFeedGas = new Stream("dry feed gas", feedGas);
		dryFeedGas.setFlowRate(10.0, "MSm3/day");
		dryFeedGas.setTemperature(40.0, "C");
		dryFeedGas.setPressure(50.0, "bara");

		StreamSaturatorUtil saturatedFeedGas = new StreamSaturatorUtil(dryFeedGas);
		saturatedFeedGas.setName("water saturator");

		Stream waterSaturatedFeedGas = new Stream(saturatedFeedGas.getOutStream());
		waterSaturatedFeedGas.setName("water saturated feed gas");

		neqsim.thermo.system.SystemInterface feedMdeaFluid = (neqsim.thermo.system.SystemInterface) feedGas.clone();
		feedMdeaFluid.setMolarComposition(new double[] { 0.0, 0.0, 0.0, 0.0, 0.8, 0.2 });

		Stream MDEAFeed = new Stream("lean TEG to absorber", feedMdeaFluid);
		MDEAFeed.setFlowRate(50000.0, "kg/hr");
		MDEAFeed.setTemperature(40.0, "C");
		MDEAFeed.setPressure(50.0, "bara");

		ReactiveAbsorber reactAbsorber = new ReactiveAbsorber();
		reactAbsorber.addGasInStream(waterSaturatedFeedGas);
		reactAbsorber.addSolventInStream(MDEAFeed);

		neqsim.processSimulation.processSystem.ProcessSystem operations = new neqsim.processSimulation.processSystem.ProcessSystem();
		operations.add(dryFeedGas);
		operations.add(saturatedFeedGas);
		operations.add(waterSaturatedFeedGas);
		operations.add(reactAbsorber);

		operations.run();
	}

}
