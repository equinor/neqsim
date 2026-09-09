/** Process fixtures for extracted capacity and pressure-boundary documentation. */

import org.apache.logging.log4j.*;
import neqsim.process.equipment.capacity.*;
import neqsim.process.equipment.stream.*;
import neqsim.process.equipment.compressor.*;
import neqsim.process.equipment.heatexchanger.*;
import neqsim.process.equipment.separator.*;
import neqsim.process.equipment.pipeline.*;
import neqsim.process.equipment.manifold.*;
import neqsim.process.equipment.pump.*;
import neqsim.process.equipment.valve.*;
import neqsim.process.processmodel.*;
import neqsim.process.util.optimizer.*;
import neqsim.process.design.*;
import neqsim.thermo.system.*;

public class FragmentRunner {
  static Logger logger = LogManager.getLogger("FragmentRunner");
  static void setup(CapacityFragments f) {
    f.fluid = new SystemSrkEos(298.15, 50.0);
    f.fluid.addComponent("methane", 0.9);
    f.fluid.addComponent("ethane", 0.1);
    f.fluid.setMixingRule("classic");
    f.feed = new Stream("Feed", f.fluid);
    f.feed.setFlowRate(1000.0,"kg/hr");
    f.feed.run();
    f.feedStream=f.feed; f.gasStream=f.feed; f.inlet1=f.feed;f.inlet2=f.feed.clone("inlet2");
    f.compressor=new Compressor("27-KA-01",f.feed);
    f.compressor.setOutletPressure(100.0);f.compressor.setPolytropicEfficiency(0.75);
    f.compressor.setUsePolytropicCalc(true);
    f.compressor.getMechanicalDesign().setMaxDesignPower(6000.0);
    f.compressor.run();
    f.pipe=new PipeBeggsAndBrills("Pipe",f.compressor.getOutletStream());
    f.pipe.setLength(1000.0);f.pipe.setDiameter(0.4);f.pipe.run();
    f.outlet=new Stream("outlet",f.pipe.getOutletStream());
    f.process=new ProcessSystem();f.process.add(f.feed);f.process.add(f.compressor);f.process.add(f.pipe);f.process.add(f.outlet);f.process.run();
    f.processSystem=f.process;
    f.processModule=new ProcessModule("Module");f.processModule.add(f.process);
    f.model=new ProcessModel();f.model.add("Compression",f.process);f.model.run();f.plant=f.model;
    f.equipment=f.compressor; f.installedKw=6000.0;
    f.heater=new Heater("Process Heater",f.feed);f.heater.setOutTemperature(350.0);f.heater.run();
    f.heater.addCapacityConstraint(new CapacityConstraint("outletTemperature","C",CapacityConstraint.ConstraintType.SOFT).setDesignValue(120.0).setValueSupplier(()->f.heater.getOutletStream().getTemperature("C")));
    SystemSrkEos oil=new SystemSrkEos(298.15,50.0);oil.addComponent("methane",0.5);oil.addComponent("n-decane",0.5);oil.setMixingRule("classic");
    Stream mixed=new Stream("Mixed",oil);mixed.setFlowRate(1000.0,"kg/hr");mixed.run();
    f.separator=new Separator("Separator",mixed);f.separator.setInternalDiameter(1.0);f.separator.setSeparatorLength(3.0);f.separator.run();
    f.hpSeparator=f.separator; f.lpSeparator=f.separator;
    f.lpCompressor=f.compressor;f.hpCompressor=f.compressor;f.exportPipeline=f.pipe;
    f.inletManifold=new Manifold("Manifold");f.inletManifold.addStream(f.feed);f.inletManifold.setSplitFactors(new double[]{1.0});
    f.subseaManifold=f.inletManifold;f.flowline=f.pipe;f.riser=f.pipe;
    f.separationSystem=new ProcessSystem();f.separationSystem.add(f.separator);
    f.compressionSystem=new ProcessSystem();f.compressionSystem.add(f.compressor);
    f.registry=EquipmentCapacityStrategyRegistry.getInstance();
    f.constraint=new CapacityConstraint("speed","rpm",CapacityConstraint.ConstraintType.HARD).setDesignValue(10000.0).setCurrentValue(9000.0);
    SystemSrkEos water=new SystemSrkEos(298.15,10.0);water.addComponent("water",1.0);water.setMixingRule("classic");
    Stream liquid=new Stream("liquid",water);liquid.setFlowRate(1000.0,"kg/hr");liquid.run();
    f.pump=new Pump("pump",liquid);f.pump.setOutletPressure(20.0);f.pump.run();
    f.exchanger=new HeatExchanger("exchanger",f.heater.getOutletStream(),liquid);f.exchanger.setUAvalue(1000.0);f.exchanger.run();
    f.exporter=new EclipseVFPExporter(1);f.exporter.setFlowRates(new double[]{1000.0});f.exporter.setTHPs(new double[]{30.0});f.exporter.setBHPTable(new double[][][][][]{{{{{35.0}}}}});
  }
  static void pressure(PressureFragments f, boolean compression) {
    setup(f);
    f.feed.setFlowRate(100.0,"kg/hr");
    f.process=new ProcessSystem();f.process.add(f.feed);
    if(compression) {
      f.process.add(f.compressor);f.outlet=new Stream("outlet",f.compressor.getOutletStream());
    }else {
      ThrottlingValve valve=new ThrottlingValve("valve",f.feed);valve.setOutletPressure(30.0,"bara");
      valve.addCapacityConstraint(new CapacityConstraint("installedMassFlow","kg/hr",CapacityConstraint.ConstraintType.HARD).setDesignValue(400.0).setValueSupplier(()->f.feed.getFlowRate("kg/hr")));
      f.process.add(valve);f.outlet=new Stream("outlet",valve.getOutletStream());
    }
    f.process.add(f.outlet);f.process.run();
    f.optimizer=new PressureBoundaryOptimizer(f.process,f.feed,f.outlet);f.optimizer.setMinFlowRate(10.0);f.optimizer.setMaxFlowRate(500.0);f.optimizer.setAutoConfigureCompressors(false);
    f.inletPressure=50.0;f.outletPressure=30.0;f.targetPressure=30.0;
    f.table=new PressureBoundaryOptimizer.LiftCurveTable("test",new double[]{50.0},new double[]{30.0},new double[][]{{400.0}},new double[][]{{0.0}},new String[][]{{"valve"}},"bara","kg/hr");
  }

  static void strictPiping(CapacityFragments f) {
    f.fluid = new SystemSrkEos(298.15, 90.0);
    f.fluid.addComponent("methane", 0.9);
    f.fluid.addComponent("ethane", 0.1);
    f.fluid.setMixingRule("classic");
    f.feed = new Stream("Feed", f.fluid);
    f.feed.setFlowRate(1000.0, "kg/hr");
    f.compressor = new Compressor("Gathering compressor", f.feed);
    f.compressor.setOutletPressure(100.0, "bara");
    f.compressor.setUsePolytropicCalc(true);
    f.compressor.setPolytropicEfficiency(0.75);
    f.pipeline = new PipeBeggsAndBrills("Gathering line", f.compressor.getOutletStream());
    f.pipeline.setLength(1000.0);
    f.pipeline.setDiameter(0.4);
    f.pipeline.setPipeWallRoughness(1.0e-5);
    f.outlet = new Stream("Receiving boundary", f.pipeline.getOutletStream());
    f.process = new ProcessSystem();
    f.process.add(f.feed);
    f.process.add(f.compressor);
    f.process.add(f.pipeline);
    f.process.add(f.outlet);
    f.model = new ProcessModel();
    f.model.add("Gathering", f.process);
    f.model.run();
    if (!f.model.isModelConverged() || !f.pipeline.solved()
        || f.process.getCalculationIdentifier() == null
        || !f.process.getCalculationIdentifier().equals(f.pipeline.getCalculationIdentifier())) {
      throw new AssertionError("Strict piping fixture needs a completed, converged calculation");
    }
    f.calculationId = f.process.getCalculationIdentifier().toString();
    if (!(f.compressor.getPower("kW") > 0.0)
        || Math.abs(f.feed.getFlowRate("kg/hr") - f.outlet.getFlowRate("kg/hr")) > 1.0e-6) {
      throw new AssertionError("Strict piping fixture has invalid power or mass balance");
    }
    double[] pressure = f.pipeline.getPressureProfile();
    if (pressure.length < 2 || pressure[pressure.length - 1] < 70.0
        || f.pipeline.getPressureDrop() < 0.0 || f.pipeline.getPressureDrop() > 12.0) {
      throw new AssertionError("Strict piping fixture violates a receiving-pressure or pressure-drop limit");
    }
    for (double value : pressure) {
      if (!Double.isFinite(value) || value <= 0.0 || value > 120.0) {
        throw new AssertionError("Strict piping fixture violates maximum absolute pressure");
      }
    }
    for (double value : f.pipeline.getMixtureSuperficialVelocityProfile()) {
      if (!Double.isFinite(value) || value < 0.0 || value > 14.0) {
        throw new AssertionError("Strict piping fixture violates maximum mixture velocity");
      }
    }
    for (double value : f.pipeline.getTemperatureProfile()) {
      if (!Double.isFinite(value) || value < 253.15 || value > 353.15) {
        throw new AssertionError("Strict piping fixture violates a minimum or maximum temperature limit");
      }
    }
  }

  public static void main(String[] args) throws Exception {
    org.apache.logging.log4j.core.config.Configurator.setLevel("FragmentRunner",org.apache.logging.log4j.Level.INFO);
    int failures=0;int executed=0;
    for(int i=0;i<16;i++) {
      try {PressureFragments f=new PressureFragments();pressure(f,i==4);PressureFragments.class.getDeclaredMethod("example"+i).invoke(f);executed++;}
      catch(Throwable error){failures++;logger.error("Pressure fragment {} failed",i,error);}
    }
    int[] indexes={1,2,5,6,7,8,9,10,12,13,14,15,16,18,19,20,21,22,23,24,25,26,27,28,29,30,32,33,34,35,36,37,40,41,42,43,44,45,46,47,48,49,50,51,52,53,55,57,58,59};
    for(int i:indexes) {
      try {CapacityFragments f=new CapacityFragments();setup(f);if(i==53)f.compressor.setName("ExportCompressor");CapacityFragments.class.getDeclaredMethod("example"+i).invoke(f);executed++;}
      catch(Throwable error){failures++;logger.error("Capacity fragment {} failed",i,error);}
    }
    int expected = 66;
    java.lang.reflect.Method pipingExample = null;
    try {
      pipingExample = CapacityFragments.class.getDeclaredMethod("strictPipingEvidence");
    } catch (NoSuchMethodException absentInBaseDocumentation) {
      // This independently named example was added after the documentation PR base.
    }
    if (pipingExample != null) {
      expected++;
      try {
        CapacityFragments f = new CapacityFragments();
        strictPiping(f);
        pipingExample.invoke(f);
        executed++;
      } catch (Throwable error) {
        failures++;
        logger.error("Strict piping evidence fragment failed", error);
      }
    }
    logger.warn("Executed fragments: {}, failures: {}",executed,failures);
    if (failures > 0 || executed != expected) {
      throw new IllegalStateException("Expected " + expected + " executed fragments; failures: " + failures);
    }
  }
}
