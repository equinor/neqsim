package neqsim.process.equipment.pump;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.compressor.CompressorChartAlternativeMapLookupExtrapolate;

/**
 * <p>
 * CompressorChartAlternativeMapLookupExtrapolate class.
 * </p>
 *
 * @author ESOL
 */
public class PumpChartAlternativeMapLookupExtrapolate
    extends CompressorChartAlternativeMapLookupExtrapolate implements PumpChartInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PumpChartAlternativeMapLookupExtrapolate.class);
  boolean usePumpChart = false;

  /** {@inheritDoc} */
  @Override
  public double getHead(double flow, double speed) {
    // Implement the method logic here
    return getPolytropicHead(flow, speed);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isUsePumpChart() {
    // Implement the method logic here
    return usePumpChart;
  }

  /** {@inheritDoc} */
  @Override
  public double getEfficiency(double flow, double speed) {
    // Implement the method logic here
    return getPolytropicEfficiency(flow, speed);
  }

  /** {@inheritDoc} */
  @Override
  public void setUsePumpChart(boolean usePumpChart) {
    this.usePumpChart = usePumpChart;
  }
}
