package neqsim.process.util.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.reservoir.WellFlow;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.measurementdevice.PushButton;
import neqsim.process.measurementdevice.SensorFaultType;
import neqsim.thermo.system.SystemInterface;

/**
 * Whole-plant component balances during well turndown, export rejection and latched shutdown.
 *
 * @author NeqSim
 * @version 1.0
 */
class WellToExportDynamicsTest {
  /** Replays identical physical event times on two grids and compares the final vessel pressure. */
  @Test
  void conservesInventoriesThroughLoadRejectionAndShutdown() {
    assertEquals(replay(0.1), replay(0.05), 0.025);
  }

  /**
   * Exercises real compressor-map feedback and actual final elements with independently summed component rates.
   *
   * @param dt fixed physical interval in seconds
   * @return final production separator pressure in bara
   */
  private static double replay(double dt) {
    WellToExportDynamics plant = new WellToExportDynamics();
    CompressorAutomationStudy.Train train = plant.getGasTrains().get(0);
    WellFlow well = (WellFlow) plant.getGathering().getUnit("W-101");
    Separator vessel = (Separator) plant.getHost().getUnit("V-101");
    Pump pump = (Pump) plant.getOilExport().getUnit("P-101");
    Separator[] inventories = {vessel, train.getSuctionScrubber(), train.getDischargeScrubber()};
    StreamInterface[] sinks = {pump.getOutletStream(), train.getExportValve().getOutletStream(),
        train.getSuctionDrain().getOutletStream(), train.getDischargeDrain().getOutletStream(),
        plant.getBlowdownValve().getOutletStream(), plant.getReliefValve().getOutletStream()};
    int components = vessel.getThermoSystem().getNumberOfComponents();
    for (int step = 0; step < Math.round(7.0 / dt); step++) {
      if (step == Math.round(0.5 / dt)) {
        well.setOutletPressure(65.0, "bara");
      }
      if (step == Math.round(1.0 / dt)) {
        assertFalse(pump.getInletStream().getFluid().hasPhaseType("gas"));
        assertTrue(pump.getOutletStream().getFlowRate("kg/sec") > 1.0);
        assertTrue(train.getExportValve().getOutletStream().getFlowRate("kg/sec") > 4.0);
        train.getExportValve().setOutletPressure(70.0, "bara");
      }
      if (step == Math.round(4.0 / dt)) {
        assertTrue(train.getInletValve().isEnergized());
        assertTrue(train.getAntiSurgeValve().getPercentValveOpening() > 10.0);
        assertTrue(train.getAntiSurgeValve().getOutletStream().getFlowRate("kg/sec") > 1.0);
        assertTrue(train.getCompressor().getSpeed() < 10000.0);
        assertFalse(plant.getBlowdownValve().isActivated());
        ((PushButton) plant.getHost().getMeasurementDevice("HS-101")).push();
      }
      double[] before = inventoryMoles(inventories, components);
      plant.runTransient(dt);
      double[] after = inventoryMoles(inventories, components);
      for (int i = 0; i < components; i++) {
        String component = vessel.getThermoSystem().getComponent(i).getComponentName();
        double net = well.getOutletStream().getFluid().getComponent(component).getNumberOfmoles();
        for (StreamInterface sink : sinks) {
          net -= sink.getFluid().getComponent(component).getNumberOfmoles();
        }
        assertEquals(net * dt, after[i] - before[i], 1.0e-5, component + " at step " + step);
      }
    }
    assertEquals(7.0, plant.getGathering().getTime(), 1.0e-10);
    assertEquals(7.0, plant.getHost().getTime(), 1.0e-10);
    assertEquals(7.0, plant.getOilExport().getTime(), 1.0e-10);
    assertEquals(7.0, train.getProcess().getTime(), 1.0e-10);
    assertEquals(0.0, train.getCompressor().getSpeed(), 0.0);
    assertTrue(pump.isTripped());
    assertEquals(0.0, pump.getDynamicPower(), 1.0e-9);
    assertEquals(100.0, train.getAntiSurgeValve().getPercentValveOpening(), 0.0);
    assertEquals(0.0, train.getExportValve().getPercentValveOpening(), 0.0);
    assertEquals(0.0, well.getOutletStream().getFlowRate("kg/sec"), 1.0e-10);
    assertEquals(0.0, pump.getOutletStream().getFlowRate("kg/sec"), 1.0e-10);
    assertTrue(plant.getBlowdownValve().getOutletStream().getFlowRate("kg/sec") > 0.5);
    assertEquals(0.0, plant.getReliefValve().getOutletStream().getFlowRate("kg/sec"), 1.0e-10);
    return vessel.getPressure();
  }

  /**
   * Sums phase-equilibrium inventory across physically distinct vessels.
   *
   * @param vessels finite inventories
   * @param count number of benchmark components
   * @return component holdups in mol
   */
  private static double[] inventoryMoles(Separator[] vessels, int count) {
    double[] moles = new double[count];
    for (Separator vessel : vessels) {
      SystemInterface inventory = vessel.getThermoSystem();
      for (int i = 0; i < count; i++) {
        moles[i] += inventory.getComponent(i).getNumberOfmoles();
      }
    }
    return moles;
  }

  /** A local package ESD isolates one of two trains while twelve wells continue to feed a common host. */
  @Test
  void oneTrainTripsWhileTheOtherExports() {
    WellToExportDynamics plant = new WellToExportDynamics(12, 2);
    CompressorAutomationStudy.Train first = plant.getGasTrains().get(0);
    CompressorAutomationStudy.Train second = plant.getGasTrains().get(1);
    plant.runTransient(0.1);
    double total = 0.0;
    for (int tag = 101; tag <= 112; tag++) {
      WellFlow well = (WellFlow) plant.getGathering().getUnit("W-" + tag);
      total += well.getOutletStream().getFlowRate("MSm3/day");
    }
    assertEquals(0.55, total, 1.0e-7);
    ((PushButton) first.getProcess().getMeasurementDevice("30-HS-100")).push();
    for (int i = 0; i < 25; i++) {
      plant.runTransient(0.1);
    }
    assertEquals(0.0, first.getCompressor().getSpeed(), 0.0);
    assertEquals(0.0, first.getExportValve().getOutletStream().getMolarRate(), 0.0);
    assertEquals(100.0, first.getAntiSurgeValve().getPercentValveOpening(), 0.0);
    assertTrue(second.getInletValve().isEnergized());
    assertTrue(second.getExportValve().getOutletStream().getFlowRate("kg/sec") > 1.0);
    assertFalse(plant.getBlowdownValve().isActivated());
  }

  /** Tests independent pressure sensing, latching, and actual relief discharge to the common disposal pressure. */
  @Test
  void pressureProtectionUsesIndependentSensorAndMechanicalRelief() {
    WellToExportDynamics plant = new WellToExportDynamics();
    PressureTransmitter safety = (PressureTransmitter) plant.getHost().getMeasurementDevice("PT-101-HH");
    PressureTransmitter regulating = (PressureTransmitter) plant.getHost().getMeasurementDevice("PT-101");
    // Lower only the synthetic spring setting to exercise lift at the actual vessel pressure.
    plant.getReliefValve().setPressureSpec(30.0);
    plant.getReliefValve().setFullOpenPressure(31.0);
    plant.runTransient(0.1);
    assertTrue(plant.getReliefValve().getOutletStream().getFlowRate("kg/sec") > 0.0);
    assertFalse(plant.getBlowdownValve().isActivated());
    safety.setFault(SensorFaultType.STUCK_AT_VALUE, 37.0);
    plant.runTransient(0.1);
    assertTrue(regulating.getMeasuredValue("bara") < 36.0);
    assertTrue(plant.getBlowdownValve().isActivated());
    safety.clearFault();
    plant.runTransient(0.1);
    assertTrue(plant.getGasTrains().get(0).getInletValve().isClosing()
        || !plant.getGasTrains().get(0).getInletValve().isEnergized());
  }
}
