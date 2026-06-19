package neqsim.process.fielddevelopment.integrated;

import java.io.Serializable;

/**
 * A reservoir drive (material balance) model for integrated reservoir-to-market simulation.
 *
 * <p>
 * A drive model translates cumulative produced volume into an average reservoir pressure, providing the coupling that
 * lets the {@link IntegratedProductionModel} march a field forward in time. The pressure returned here is the boundary
 * pressure applied to the reservoir nodes of the {@link NetworkNewtonSolver}, so well deliverability falls naturally as
 * the reservoir depletes.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 * @see MaterialBalanceGasDrive
 * @see AquiferDrive
 * @see OilTankDrive
 */
public interface ReservoirDrive extends Serializable {

  /**
   * Returns the current average reservoir pressure.
   *
   * @return reservoir pressure in bara
   */
  double getReservoirPressure();

  /**
   * Advances the drive by producing a volume over a time step.
   *
   * @param producedVolumeSm3 surface volume produced during the step in Sm3
   * @param dtDays            length of the time step in days
   */
  void produce(double producedVolumeSm3, double dtDays);

  /**
   * Returns the cumulative produced volume.
   *
   * @return cumulative production in Sm3
   */
  double getCumulativeProduction();

  /**
   * Returns the recoverable (or initial in-place) volume basis used by the drive.
   *
   * @return reference volume in Sm3
   */
  double getInPlaceVolume();
}
