/**
 * Pre-built biorefinery process modules for NeqSim.
 *
 * <p>
 * This package contains composable process modules that assemble multiple NeqSim equipment units
 * into complete biorefinery flowsheets. Each module encapsulates the configuration, process
 * topology, and result extraction for a specific biorefinery pathway.
 * </p>
 *
 * <h2>Available Modules</h2>
 *
 * <table>
 * <caption>Biorefinery process modules</caption>
 * <tr>
 * <th>Module</th>
 * <th>Pathway</th>
 * <th>Equipment Used</th>
 * </tr>
 * <tr>
 * <td>{@link neqsim.process.processmodel.biorefinery.BiogasToGridModule}</td>
 * <td>Biogas to grid injection</td>
 * <td>AnaerobicDigester, BiogasUpgrader, Compressor, Cooler</td>
 * </tr>
 * <tr>
 * <td>{@link neqsim.process.processmodel.biorefinery.GasificationSynthesisModule}</td>
 * <td>Biomass gasification + Fischer-Tropsch</td>
 * <td>BiomassGasifier, Cooler, FT reactor (simplified)</td>
 * </tr>
 * <tr>
 * <td>{@link neqsim.process.processmodel.biorefinery.WasteToEnergyCHPModule}</td>
 * <td>Waste-to-energy CHP</td>
 * <td>AnaerobicDigester, CHP engine (simplified)</td>
 * </tr>
 * </table>
 *
 * @author Even Solbraa
 * @version 1.0
 */
package neqsim.process.processmodel.biorefinery;
