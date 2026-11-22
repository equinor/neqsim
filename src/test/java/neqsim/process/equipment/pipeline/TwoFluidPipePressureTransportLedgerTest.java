package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/** Accepted pressure-correction transfers must also transport named components. */
class TwoFluidPipePressureTransportLedgerTest {
  @Test
  void implicitPressureRedistributionKeepsComponentsSynchronizedWithCellMass() throws Exception {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("nitrogen", 0.2);
    fluid.setMixingRule("classic");
    Stream inlet = new Stream("pressure-ledger-feed", fluid);
    inlet.setFlowRate(0.1, "kg/sec");
    inlet.run();
    TwoFluidPipe pipe = new TwoFluidPipe("pressure-ledger", inlet);
    pipe.setLength(40.0);
    pipe.setDiameter(0.2);
    pipe.setNumberOfSections(4);
    pipe.setSteadyStateMaxWallClockTime(Double.POSITIVE_INFINITY);
    pipe.setEnableSlugTracking(false);
    pipe.setIncludeMassTransfer(false);
    pipe.setEnableJouleThomson(false);
    pipe.setHeatTransferCoefficient(0.0);
    pipe.setThermodynamicUpdateInterval(Integer.MAX_VALUE);
    pipe.setComponentTransportEnabled(true);
    pipe.run();
    pipe.setEnableCoupledPressureMomentum(true);
    pipe.setEnableInterfacialPressure(true);
    pipe.setTimeIntegrationMethod(TimeIntegrator.Method.IMEX_PRESSURE_CORRECTION);
    pipe.closeInlet();
    pipe.closeOutlet();
    Field field = TwoFluidPipe.class.getDeclaredField("sections");
    field.setAccessible(true);
    TwoFluidSection[] cells = (TwoFluidSection[]) field.get(pipe);
    double[] oldMass = new double[cells.length];
    for (int cell = 0; cell < cells.length; cell++) {
      oldMass[cell] = cells[cell].getGasMassPerLength();
      cells[cell].setPressure(5e6 + (cell % 2 == 0 ? 1000.0 : -1000.0));
      cells[cell].setGasVelocity(0.0);
      cells[cell].setGasMomentumPerLength(0.0);
    }
    pipe.runTransient(0.01, UUID.randomUUID());
    double redistributedMass = 0.0;
    for (int cell = 0; cell < cells.length; cell++) {
      redistributedMass += Math.abs(cells[cell].getGasMassPerLength() - oldMass[cell]);
    }
    assertTrue(redistributedMass > 1e-7, "The pressure correction must actually redistribute mass");
    assertEquals(0.01, pipe.getSimulationTime(), 1e-12);
    TwoFluidComponentConservationReport report = pipe.getLastComponentConservationReport();
    assertTrue(report.isConverged(), report.getMessage());
    assertTrue(report.getMaximumPhaseMassSynchronizationErrorKg() < 1e-9, report.getMessage());
    assertEquals(0.0, pipe.getLastMassBalanceReport().getResidualKg(TwoFluidMassBalanceReport.Phase.GAS), 1e-10);
  }
}
