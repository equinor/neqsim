package neqsim.process.equipment.subsea;

import java.util.ArrayList;
import java.util.UUID;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.pipeline.AdiabaticTwoPhasePipe;
import neqsim.process.equipment.reservoir.SimpleReservoir;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Adjuster;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * SubseaWell class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class SubseaWell extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  public double height = 1000.0;
  public double length = 1200.0;
  AdiabaticTwoPhasePipe pipeline;

  /**
   * <p>
   * Constructor for SubseaWell.
   * </p>
   *
   * @param name Name of well
   * @param instream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public SubseaWell(String name, StreamInterface instream) {
    super(name);
    setInletStream(instream);
    pipeline = new AdiabaticTwoPhasePipe("pipeline", instream);
  }

  /**
   * <p>
   * Getter for the field <code>pipeline</code>.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.pipeline.AdiabaticTwoPhasePipe} object
   */
  public AdiabaticTwoPhasePipe getPipeline() {
    return pipeline;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    pipeline.run(id);
    getOutletStream().setFluid(pipeline.getOutletStream().getFluid());

    /*
     * System.out.println("stary P " ); SystemInterface fluidIn = (inStream.getFluid()).clone();
     * fluidIn.initProperties();
     *
     * double density = fluidIn.getDensity("kg/m3");
     *
     * double deltaP = density*height*neqsim.thermo.ThermodynamicConstantsInterface.gravity/1.0e5;
     *
     * System.out.println("density " +density + " delta P " + deltaP);
     *
     * fluidIn.setPressure(fluidIn.getPressure("bara")-deltaP);
     *
     * ThermodynamicOperations ops = new ThermodynamicOperations(fluidIn); ops.TPflash();
     *
     * getOutStream().setFluid(fluidIn);
     */
    setCalculationIdentifier(id);
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 100.0), 250.00);
    testSystem.addComponent("nitrogen", 0.100);
    testSystem.addComponent("methane", 70.00);
    testSystem.addComponent("ethane", 1.0);
    testSystem.addComponent("propane", 1.0);
    testSystem.addComponent("i-butane", 1.0);
    testSystem.addComponent("n-butane", 1.0);
    testSystem.addComponent("n-hexane", 0.1);
    testSystem.addComponent("n-heptane", 0.1);
    testSystem.addComponent("n-nonane", 1.0);
    testSystem.addComponent("nC10", 1.0);
    testSystem.addComponent("nC12", 3.0);
    testSystem.addComponent("nC15", 13.0);
    testSystem.addComponent("nC20", 13.0);
    testSystem.addComponent("water", 11.0);
    testSystem.setMixingRule(2);
    testSystem.setMultiPhaseCheck(true);

    SimpleReservoir reservoirOps = new SimpleReservoir("Well 1 reservoir");
    reservoirOps.setReservoirFluid(testSystem, 5.0 * 1e7, 552.0 * 1e6, 10.0e6);
    StreamInterface producedOilStream = reservoirOps.addOilProducer("oilproducer_1");
    producedOilStream.setFlowRate(3500.0 * 24.0 * 600.0, "kg/day");

    reservoirOps.run();

    System.out.println("water volume"
        + reservoirOps.getReservoirFluid().getPhase("aqueous").getVolume("m3") / 1.0e6);
    System.out
        .println("oil production  total" + reservoirOps.getOilProductionTotal("Sm3") + " Sm3");
    System.out
        .println("total produced  " + reservoirOps.getProductionTotal("MSm3 oe") + " MSm3 oe");

    SubseaWell well1 =
        new SubseaWell("oilproducer_1", reservoirOps.getOilProducer("oilproducer_1").getStream());
    well1.getPipeline().setDiameter(0.3);
    well1.getPipeline().setLength(5500.0);
    well1.getPipeline().setInletElevation(-1000.0);
    well1.getPipeline().setOutletElevation(-100.0);
    ThrottlingValve subseaChoke = new ThrottlingValve("subseaChoke", well1.getOutletStream());
    subseaChoke.setOutletPressure(90.0);
    subseaChoke.setAcceptNegativeDP(false);
    SimpleFlowLine flowLine = new SimpleFlowLine("flowLine", subseaChoke.getOutletStream());
    flowLine.getPipeline().setDiameter(0.4);
    flowLine.getPipeline().setLength(2000.0);
    flowLine.getPipeline().setInletElevation(-100.0);
    // flowLine.set
    ThrottlingValve topsideChoke = new ThrottlingValve("topsideChoke", flowLine.getOutletStream());
    topsideChoke.setOutletPressure(50.0, "bara");
    topsideChoke.setAcceptNegativeDP(false);

    Adjuster adjust = new Adjuster("adjust");
    adjust.setActivateWhenLess(true);
    adjust.setTargetVariable(flowLine.getOutletStream(), "pressure", 70.0, "bara");
    adjust.setAdjustedVariable(producedOilStream, "flow rate");

    ProcessSystem ops = new ProcessSystem();
    ops.add(well1);
    ops.add(subseaChoke);
    ops.add(flowLine);
    ops.add(topsideChoke);
    ops.add(adjust);

    ArrayList<double[]> res = new ArrayList<double[]>();
    // for(int i=0;i<152;i++) {
    // do {
    reservoirOps.runTransient(60 * 60 * 24 * 1);
    ops.run();
    res.add(new double[] {reservoirOps.getTime(), producedOilStream.getFluid().getFlowRate("kg/hr"),
        reservoirOps.getOilProductionTotal("MSm3 oe")});
    System.out.println("subsea choke DP " + subseaChoke.getDeltaPressure("bara"));
    System.out.println("topside  choke DP " + topsideChoke.getDeltaPressure("bara"));
    System.out.println("oil production " + producedOilStream.getFluid().getFlowRate("kg/hr"));
    // }
    // while(producedOilStream.getFluid().getFlowRate("kg/hr")>1.0e5);

    ProcessSystem GasOilProcess = ProcessSystem.open("c:/temp/offshorePro.neqsim");
    ((StreamInterface) GasOilProcess.getUnit("well stream"))
        .setThermoSystem(topsideChoke.getOutletStream().getFluid());
    ((StreamInterface) GasOilProcess.getUnit("well stream")).setPressure(70.0, "bara");
    ((StreamInterface) GasOilProcess.getUnit("well stream")).setTemperature(65.0, "C");
    GasOilProcess.run();

    System.out.println("power " + GasOilProcess.getPower("MW"));
    for (int i = 0; i < res.size(); i++) {
      System.out.println("time " + res.get(i)[0] + " oil production " + res.get(i)[1]
          + " total production MSm3 oe " + res.get(i)[2]);
    }
  }
}
