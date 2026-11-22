/** Process fixtures for extracted design-to-optimization documentation. */
import neqsim.process.design.*;
import neqsim.process.design.template.*;
import neqsim.process.equipment.stream.*;
import neqsim.process.equipment.separator.*;
import neqsim.process.equipment.valve.*;
import neqsim.thermo.system.*;
public class DesignFragmentRunner {
  static void setup(DesignFragments f) {
    FragmentRunner.setup(f);
    SystemSrkEos oil=new SystemSrkEos(323.15,85.0);
    oil.addComponent("methane",0.5);oil.addComponent("n-decane",0.5);oil.setMixingRule("classic");
    f.myOilGasFluid=oil; f.myFluid=oil;
    f.template=new ThreeStageSeparationTemplate();
    f.basis=ProcessBasis.builder().setFeedFluid(oil).setFeedFlowRate(50000.0,"kg/hr")
      .setFeedPressure(85.0,"bara").setFeedTemperature(50.0,"C")
      .addStagePressure(1,80.0,"bara").addStagePressure(2,20.0,"bara").addStagePressure(3,2.0,"bara").build();
    f.process=f.template.create(f.basis);f.process.run();
    f.separator=(Separator)f.process.getUnit("HP Separator");f.separator.setName("HP-Separator");
    f.feed=(Stream)f.process.getUnit("Feed");
    f.valve=(ThrottlingValve)f.process.getUnit("HP-MP Valve");
    f.compOutput=(Stream)f.compressor.getOutletStream();
    f.optimizer=DesignOptimizer.forProcess(f.process).autoSizeEquipment(1.2).applyDefaultConstraints()
      .configureFeedRateOptimization("Feed",25000.0,80000.0,"kg/hr")
      .setObjective(DesignOptimizer.ObjectiveType.MAXIMIZE_PRODUCTION);
    f.spec=DesignSpecification.forSeparator("HP-Separator");
    f.rate=50000.0;f.pressure=85.0;f.temp=50.0;
  }
  public static void main(String[] args) throws Exception {
    org.apache.logging.log4j.core.config.Configurator.setLevel("DesignFragments",org.apache.logging.log4j.Level.INFO); org.apache.logging.log4j.core.config.Configurator.setLevel("CapacityFragments",org.apache.logging.log4j.Level.INFO); int executed=0;
    int failures=0;
    for(java.lang.reflect.Method method:DesignFragments.class.getDeclaredMethods()) {
      if(!method.getName().startsWith("example"))continue; try {DesignFragments f=new DesignFragments();setup(f);method.invoke(f);executed++;}
      catch(Throwable error){failures++;org.apache.logging.log4j.LogManager.getLogger("DesignFragments").error("Failed {}",method.getName(),error);}
    }
    org.apache.logging.log4j.LogManager.getLogger("DesignFragments").warn("Design fragments executed: {}, failures: {}",executed,failures);
    if (failures > 0 || executed != 28) {
      throw new AssertionError("Expected 28 executed design fragments; failures: " + failures);
    }
  }
}
