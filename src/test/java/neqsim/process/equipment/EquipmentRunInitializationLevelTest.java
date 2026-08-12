package neqsim.process.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Recycle;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Guards initialization work whose removal has not demonstrated a representative end-to-end process benefit.
 */
class EquipmentRunInitializationLevelTest {
  private static final class InitTrackingSystemSrkEos extends SystemSrkEos {
    private static final long serialVersionUID = 1000L;
    private AtomicInteger firstInitLevel = new AtomicInteger(-1);

    InitTrackingSystemSrkEos(double temperature, double pressure) {
      super(temperature, pressure);
    }

    @Override
    public InitTrackingSystemSrkEos clone() {
      InitTrackingSystemSrkEos cloned = (InitTrackingSystemSrkEos) super.clone();
      cloned.firstInitLevel = firstInitLevel;
      return cloned;
    }

    @Override
    public void init(int initType) {
      super.init(initType);
      if (firstInitLevel != null) {
        firstInitLevel.compareAndSet(-1, initType);
      }
    }

    void resetFirstInitLevel() {
      firstInitLevel.set(-1);
    }

    int getFirstInitLevel() {
      return firstInitLevel.get();
    }
  }

  private static InitTrackingSystemSrkEos createGas(double temperature, double pressure) {
    InitTrackingSystemSrkEos fluid = new InitTrackingSystemSrkEos(temperature, pressure);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.12);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-heptane", 0.03);
    fluid.setMixingRule("classic");
    return fluid;
  }

  @Test
  void pumpRetainsDerivativeWarmStateBeforeFollowingFlashes() {
    InitTrackingSystemSrkEos fluid = new InitTrackingSystemSrkEos(303.15, 3.0);
    fluid.addComponent("n-pentane", 0.45);
    fluid.addComponent("n-hexane", 0.55);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("pump feed", fluid);
    feed.setFlowRate(30000.0, "kg/hr");
    feed.run();

    Pump pump = new Pump("pump", feed);
    pump.setOutletPressure(18.0);
    pump.calculateAsCompressor(false);
    fluid.resetFirstInitLevel();
    pump.run();

    assertEquals(3, fluid.getFirstInitLevel(),
        "Pump inlet derivative state must not be discarded without end-to-end evidence");
  }

  @Test
  void lowFlowPumpRetainsDerivativeWarmState() {
    InitTrackingSystemSrkEos fluid = new InitTrackingSystemSrkEos(303.15, 3.0);
    fluid.addComponent("n-pentane", 0.45);
    fluid.addComponent("n-hexane", 0.55);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("low-flow pump feed", fluid);
    feed.setFlowRate(1.0, "kg/hr");
    feed.run();

    Pump pump = new Pump("low-flow pump", feed);
    pump.setOutletPressure(18.0);
    pump.setMinimumFlow(2.0);
    fluid.resetFirstInitLevel();
    pump.run();

    assertEquals(3, fluid.getFirstInitLevel(),
        "Low-flow pump derivative state must not be discarded without end-to-end evidence");
  }

  @Test
  void recycleRetainsQualifiedEnthalpyInitializationLevel() {
    InitTrackingSystemSrkEos fluid = createGas(298.15, 70.0);
    Stream feed = new Stream("recycle feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.run();

    Recycle recycle = new Recycle("recycle");
    recycle.addStream(feed);
    fluid.resetFirstInitLevel();
    recycle.calcMixStreamEnthalpy();

    assertEquals(3, fluid.getFirstInitLevel(),
        "Recycle initialization must remain qualified by representative convergence evidence");
  }
}
