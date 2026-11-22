package neqsim.process.fielddevelopment.integrated;

import java.io.Serializable;

/**
 * Gas deviation factor as a function of pressure and temperature, used by material-balance drives and well models.
 *
 * <p>
 * Implementations must be smooth and well defined for {@code p} between 0 and the initial reservoir pressure. Use
 * {@link DranchukAbouKassemZ} for lean sweet gas or supply any other correlation or a table built from a NeqSim flash.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 * @see RealGasMaterialBalanceDrive
 */
public interface GasZFactor extends Serializable {

  /**
   * Returns the gas deviation factor.
   *
   * @param pressureBara absolute pressure in bara
   * @param temperatureK temperature in K
   * @return Z factor (dimensionless, positive)
   */
  double z(double pressureBara, double temperatureK);
}
