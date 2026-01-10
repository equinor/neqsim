/**
 * Subsea production system modeling for field development workflow.
 *
 * <p>
 * This package provides high-level abstractions for modeling subsea production systems including
 * wells, flowlines, manifolds, and gathering networks. It integrates with the tieback analysis and
 * economics framework for end-to-end field development studies.
 * </p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 * <li>{@link neqsim.process.fielddevelopment.subsea.SubseaProductionSystem} - Main orchestrator for
 * subsea system modeling</li>
 * </ul>
 *
 * <h2>Architecture Types Supported</h2>
 * <ul>
 * <li>Direct tieback - Wells tied directly to host</li>
 * <li>Manifold cluster - Wells grouped at subsea manifold</li>
 * <li>Daisy chain - Wells connected in series</li>
 * <li>Template - Multiple wells from single seabed structure</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 * @see neqsim.process.equipment.subsea
 * @see neqsim.process.fielddevelopment.tieback
 */
package neqsim.process.fielddevelopment.subsea;
