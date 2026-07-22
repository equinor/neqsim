/**
 * Analytical reservoir material balance and aquifer influence functions.
 *
 * <p>
 * This package provides "inverse" reservoir-surveillance tools that regress reserves and drive mechanism from measured
 * pressure-versus-production history, complementing the forward tank models in
 * {@code neqsim.process.equipment.reservoir}:
 *
 * <ul>
 * <li>{@link neqsim.pvtsimulation.reservoirproperties.materialbalance.GasMaterialBalance} - gas P/Z straight line
 * (OGIP), Cole plot aquifer diagnostic, and Havlena-Odeh gas balance</li>
 * <li>{@link neqsim.pvtsimulation.reservoirproperties.materialbalance.OilMaterialBalance} - Havlena-Odeh oil balance
 * (OOIP), gas-cap ratio, water drive and Pirson drive indices</li>
 * <li>{@link neqsim.pvtsimulation.reservoirproperties.materialbalance.VanEverdingenHurstAquifer} - radial aquifer
 * influence functions, Carter-Tracy cumulative water influx, and ECLIPSE AQUTAB export</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 */
package neqsim.pvtsimulation.reservoirproperties.materialbalance;
