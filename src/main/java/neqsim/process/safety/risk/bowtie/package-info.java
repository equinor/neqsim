/**
 * Bow-Tie Diagram Generation Package.
 *
 * <p>
 * This package provides bow-tie diagram generation and analysis capabilities for process safety
 * risk assessment. Bow-tie diagrams visualize the relationship between threats, hazards, and
 * consequences with their associated barriers.
 * </p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 * <li>{@link neqsim.process.safety.risk.bowtie.BowTieAnalyzer} - Main analyzer with
 * threat/consequence libraries</li>
 * <li>{@link neqsim.process.safety.risk.bowtie.BowTieModel} - Individual bow-tie model with
 * threats, consequences, and barriers</li>
 * </ul>
 *
 * <h2>Bow-Tie Structure</h2>
 * 
 * <pre>
 *   THREATS           TOP EVENT           CONSEQUENCES
 *     │                  │                    │
 *     ├──▶ Barrier ──▶   ◆   ──▶ Barrier ──▶ ├──
 *     │                  │                    │
 *     ├──▶ Barrier ──▶   │   ──▶ Barrier ──▶ ├──
 *     │                  │                    │
 * (Prevention)      (Hazard)            (Mitigation)
 * </pre>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>
 * // Create analyzer
 * BowTieAnalyzer analyzer = new BowTieAnalyzer("Platform Safety Analysis", processSystem);
 *
 * // Add available SIFs as barriers
 * analyzer.addAvailableSIF(hipps);
 * analyzer.addAvailableSIF(esd);
 *
 * // Auto-generate bow-ties from process
 * List&lt;BowTieModel&gt; bowTies = analyzer.autoGenerateFromProcess();
 *
 * // Or create manually
 * BowTieModel overpressure =
 *     analyzer.createBowTie("SEP-001-OVP", "Overpressure in HP Separator", "OVERPRESSURE");
 *
 * // Add custom threat
 * BowTieModel.Threat threat = new BowTieModel.Threat("T-001", "Blocked outlet valve", 0.1);
 * overpressure.addThreat(threat);
 *
 * // Add barrier
 * BowTieModel.Barrier hippsBarrier = new BowTieModel.Barrier("B-001", "HIPPS System", 0.001);
 * hippsBarrier.setBarrierType(BowTieModel.BarrierType.PREVENTION);
 * overpressure.addBarrier(hippsBarrier);
 *
 * // Calculate and visualize
 * analyzer.calculateRisk();
 * System.out.println(analyzer.generateReport());
 * </pre>
 *
 * <h2>Integration with SIS</h2>
 * <p>
 * The bow-tie package integrates with the SIS package to automatically assign Safety Instrumented
 * Functions (SIFs) as barriers with proper PFD values.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see neqsim.process.safety.risk.sis.SafetyInstrumentedFunction
 */
package neqsim.process.safety.risk.bowtie;
