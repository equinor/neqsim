package neqsim.thermo.util.leachman;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.OnePhasePipeLine;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.thermo.system.SystemInterface;


public class Compressor_H2_test {
  
  @Test
  void Leachman_compr_pipe_test() {

    SystemInterface Leachmanfluid = new neqsim.thermo.system.SystemGERG2008Eos(293.15, 90);

    double length = 20000.0;
    double elevation = 0.0;
    double diameter = 0.98;
    System.out.println("-----------GERG2008 EOS-----------");


    //Leachmanfluid.addComponent("hydrogen", 0.5);
    Leachmanfluid.addComponent("methane", 0.9);
    //Leachmanfluid.addComponent("ethane", 0.1);
    Leachmanfluid.init(0);
    Leachmanfluid.init(1);

    
    Leachmanfluid.setNumberOfPhases(1);
    Leachmanfluid.setMaxNumberOfPhases(1);
    Leachmanfluid.setForcePhaseTypes(true);
    Leachmanfluid.setPhaseType(0, "GAS");
    Leachmanfluid.getPhase("gas").getPhysicalProperties().setViscosityModel("PFCT");
    Leachmanfluid.getPhase("gas").initPhysicalProperties();
    

    neqsim.process.equipment.stream.Stream gasstream_leachman =
        new neqsim.process.equipment.stream.Stream("gas", Leachmanfluid);
      gasstream_leachman.setFlowRate(60.0, "MSm3/day");
      gasstream_leachman.run();

    neqsim.process.equipment.compressor.Compressor compressor_leachman_1 =
        new neqsim.process.equipment.compressor.Compressor("compressor 1", gasstream_leachman);
    // compressor.setUseLeachman(true);
    compressor_leachman_1.setOutletPressure(120.0, "bara");
    compressor_leachman_1.setIsentropicEfficiency(0.77);
    //compressor_leachman_1.setUseLeachman(true);
    compressor_leachman_1.run();

    neqsim.process.equipment.heatexchanger.Cooler cooler_1 =
        new neqsim.process.equipment.heatexchanger.Cooler("cooler_1", compressor_leachman_1.getOutletStream());
    cooler_1.setOutTemperature(273.15+20.0);
    cooler_1.run();

    neqsim.process.equipment.compressor.Compressor compressor_leachman_2 =
    new neqsim.process.equipment.compressor.Compressor("compressor 2", cooler_1.getOutStream());
    // compressor.setUseLeachman(true);
    compressor_leachman_2.setOutletPressure(150.0);
    compressor_leachman_2.setIsentropicEfficiency(0.77);
    compressor_leachman_2.run();

    neqsim.process.equipment.heatexchanger.Cooler cooler_2 =
    new neqsim.process.equipment.heatexchanger.Cooler("cooler_2", compressor_leachman_2.getOutletStream());
    cooler_2.setOutTemperature(273.15+35.0);
    cooler_2.run();


    System.out.println("Inital temperature " + gasstream_leachman.getTemperature("C"));
    System.out.println("Initial pressure " + gasstream_leachman.getPressure("bara"));
    System.out.println("Initial Volume flow " + gasstream_leachman.getFlowRate("MSm3/day") + " MSm3/day");

    System.out.println("-------Compressor 1-------");
    System.out.println("Temperature out of Compr. 1: " + compressor_leachman_1.getOutletStream().getTemperature("C") + " C");
    System.out.println("Pressure out of Compr. 1: " + compressor_leachman_1.getOutletStream().getPressure("bara") + " bara");
    System.out.println("Volume flow out of Compr. 1: " + compressor_leachman_1.getOutletStream().getFlowRate("MSm3/day") + " MSm3/day");
    System.out.println("Compressor 1 power: " + compressor_leachman_1.getPower("kW") + " kW");
    System.out.println("Compressor 1 Polytropic head: " + compressor_leachman_1.getPolytropicHead("kJ/kg") + " kJ/kg");

    System.out.println("-------Cooler 1-------");
    System.out.println("Temperature out of Cooler 1: " + (cooler_1.getOutletTemperature() - 273.15) + " C");
    System.out.println("Pressure out of Cooler 1: " + cooler_1.getOutletPressure() + " bara");
    System.out.println("Volume flow out of Cooler 1: " + cooler_1.getOutletStream().getFlowRate("MSm3/day") + " MSm3/day");
    System.out.println("Cooler 1 duty: " + cooler_1.getDuty());

    System.out.println("-------Compressor 2-------");
    System.out.println("Temperature out of Compr. 2: " + compressor_leachman_2.getOutletStream().getTemperature("C") + " C");
    System.out.println("Pressure out of Compr. 2: " + compressor_leachman_2.getOutletStream().getPressure("bara")  + " bara");
    System.out.println("Volume flow out of Compr. 2: " + compressor_leachman_2.getOutletStream().getFlowRate("MSm3/day") + " MSm3/day");
    System.out.println("Compressor 2 power: " + compressor_leachman_2.getPower("kW") + " kW");
    System.out.println("Compressor 2 Polytropic head: " + compressor_leachman_2.getPolytropicHead("kJ/kg") + "kJ/kg");

    System.out.println("-------Cooler 2-------");
    System.out.println("Temperature out of Cooler 2: " + (cooler_2.getOutletTemperature() - 273.15) + " C");
    System.out.println("Pressure out of Cooler 2: " + cooler_2.getOutletPressure() + " bara");
    System.out.println("Volume flow out of Cooler 2: " + cooler_2.getOutletStream().getFlowRate("MSm3/day") + " MSm3/day");
    System.out.println("Cooler 2 duty: " + cooler_2.getDuty());

    PipeBeggsAndBrills beggsBrilsPipe = new PipeBeggsAndBrills("simplePipeline 1", cooler_2.getOutStream());
    beggsBrilsPipe.setPipeWallRoughness(5.0e-6);

    beggsBrilsPipe.setLength(length);
    beggsBrilsPipe.setElevation(elevation);
    beggsBrilsPipe.setDiameter(diameter);
    beggsBrilsPipe.setRunIsothermal(false);
    //beggsBrilsPipe.setOuterTemperatures(new double[]{278.15, 278.15});
    beggsBrilsPipe.run();

    System.out.println("-------Beggs and Brills-------");
    System.out.println("Pipe length: " + beggsBrilsPipe.getLength()/1000 + " km");
    System.out.println("Temperature out of Pipeline: " + beggsBrilsPipe.getOutletStream().getTemperature("C") + " C");
    System.out.println("Pressure out of Pipeline: " + beggsBrilsPipe.getOutletStream().getPressure("bara") + " bara");
    System.out.println("Volume flow out of Pipeline: " + beggsBrilsPipe.getOutletStream().getFlowRate("MSm3/day") + " MSm3/day");



    
    // Define the arrays for pipeline properties
    double[] diameters = new double[]{0.98, 0.98}; // in meters
    double[] roughness = new double[]{5.0e-6, 5.0e-6}; // in meters
    double[] positions = new double[]{0.0, 600000.0}; // in meters (start and end positions)
    double[] heights = new double[]{0.0, 0.0}; // in meters
    double[] outerTemperatures = new double[]{278.15, 278.15}; // in Kelvin
    double[] outHeatU = new double[]{25.0, 25.0}; // W/m2K for outer heat transfer
    double[] wallHeatU = new double[]{35.0, 35.0}; // W/m2K for wall heat transfer

  
    // Create and configure the pipeline
    OnePhasePipeLine pipe1 = new OnePhasePipeLine("pipeline", cooler_2.getOutStream());
    pipe1.setLegPositions(positions);
    pipe1.setPipeDiameters(diameters);
    pipe1.setHeightProfile(heights);
    pipe1.setOuterTemperatures(outerTemperatures);
    pipe1.setPipeWallRoughness(roughness);
    pipe1.setPipeOuterHeatTransferCoefficients(outHeatU);
    pipe1.setPipeWallHeatTransferCoefficients(wallHeatU);
    pipe1.setNumberOfNodesInLeg(100);


    // Run the pipeline simulation
    pipe1.run();

    //System.out.println("Number of nodes " + pipeline.get());
    System.out.println("pressure " + pipe1.getOutletStream().getPressure());
    System.out.println("temperature out of pipe " + pipe1.getOutletStream().getTemperature("C"));
  }
}