/**
 * LNG cargo ageing and transport simulation package.
 *
 * <p>
 * Provides a layered, transient model for simulating LNG composition and quality drift during
 * storage and marine transport. Key capabilities include:
 * </p>
 * <ul>
 * <li>Multi-layer tank model with stratification detection</li>
 * <li>Vapor space thermodynamics with EOS-backed phase equilibrium</li>
 * <li>Voyage profile coupling (ambient temperature, sea state, weather)</li>
 * <li>Rollover risk detection and prevention</li>
 * <li>BOG handling network (compressors, reliquefaction, GCU)</li>
 * <li>Heel management for cooldown and mixing</li>
 * <li>Quality KPI tracking (WI, GCV, MN, density per ISO 6976/6578)</li>
 * </ul>
 *
 * <p>
 * The main entry point is {@link neqsim.process.equipment.lng.LNGAgeingScenario} which orchestrates
 * the time-stepping simulation across all submodels.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
package neqsim.process.equipment.lng;
