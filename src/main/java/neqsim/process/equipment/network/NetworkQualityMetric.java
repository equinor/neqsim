package neqsim.process.equipment.network;

import java.io.Serializable;

/**
 * Common contract for discoverable gas and oil network quality metrics.
 */
public interface NetworkQualityMetric extends Serializable {
  /**
   * Get the stable serialized metric key.
   *
   * @return metric key
   */
  String getKey();

  /**
   * Get the default reporting unit.
   *
   * @return unit
   */
  String getDefaultUnit();

  /**
   * Get the quality domain.
   *
   * @return {@code gas} or {@code oil}
   */
  String getDomain();
}
