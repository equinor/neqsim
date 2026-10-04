package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Contract tests for residuals evaluated from published terminal state.
 *
 * @author NeqSim
 * @version 1.0
 */
class DirectTerminalSpecificationResidualTest {
  /** Verify both ratios reflect actual outlet flow rather than their configured target. */
  @Test
  void ratiosUsePublishedMolarFlows() {
    DistillationColumn column = new DistillationColumn("ratios", 2, true, true);
    column.setCondenserRefluxRatio(1.8);
    column.setReboilerBoilupRatio(1.2);
    column.getCondenser().setCachedGasOutStream(stream(10.0));
    column.getCondenser().setCachedLiquidOutStream(stream(20.0));
    column.getReboiler().setCachedGasOutStream(stream(30.0));
    column.getReboiler().setCachedLiquidOutStream(stream(10.0));
    column.updateSpecificationResidualDiagnostics();
    assertEquals(0.2, column.getLastTopSpecificationResidual(), 1.0e-12);
    assertEquals(1.8, column.getLastBottomSpecificationResidual(), 1.0e-12);
  }

  /** Verify signed duty errors are reported in watts at either end. */
  @Test
  void dutiesUsePublishedHeatBalance() {
    DistillationColumn column = new DistillationColumn("duties", 2, true, true);
    column.setTopSpecification(new ColumnSpecification(ColumnSpecification.SpecificationType.DUTY,
        ColumnSpecification.ProductLocation.TOP, -100.0));
    column.setBottomSpecification(new ColumnSpecification(ColumnSpecification.SpecificationType.DUTY,
        ColumnSpecification.ProductLocation.BOTTOM, 200.0));
    column.getCondenser().duty = -80.0;
    column.getReboiler().duty = 175.0;
    column.updateSpecificationResidualDiagnostics();
    assertEquals(20.0, column.getLastTopSpecificationResidual(), 0.0);
    assertEquals(-25.0, column.getLastBottomSpecificationResidual(), 0.0);
  }

  /** Verify a dry terminal cannot report an achieved ratio. */
  @Test
  void zeroOverZeroIsUnavailable() {
    DistillationColumn column = new DistillationColumn("dry condenser", 2, true, true);
    column.setCondenserRefluxRatio(1.8);
    column.getCondenser().setCachedGasOutStream(stream(0.0));
    column.getCondenser().setCachedLiquidOutStream(stream(0.0));
    column.updateSpecificationResidualDiagnostics();
    assertTrue(Double.isNaN(column.getLastTopSpecificationResidual()));
  }

  /**
   * Create a stream with a known molar inventory for the residual contract.
   *
   * @param flow molar flow in mol/hr
   * @return stream with specified molar flow
   */
  private Stream stream(double flow) {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 10.0);
    fluid.addComponent("propane", 1.0);
    fluid.setMixingRule("classic");
    Stream stream = new Stream("terminal", fluid);
    stream.setFlowRate(flow, "mol/hr");
    return stream;
  }
}
