/**
 * Subsea equipment package for SURF (Subsea, Umbilicals, Risers, Flowlines) field development.
 *
 * <p>
 * This package provides comprehensive equipment classes for modeling subsea production systems,
 * including:
 * </p>
 *
 * <h2>Pipeline Infrastructure</h2>
 * <ul>
 * <li>{@link neqsim.process.equipment.subsea.PLET} - Pipeline End Termination structures</li>
 * <li>{@link neqsim.process.equipment.subsea.PLEM} - Pipeline End Manifold structures</li>
 * <li>{@link neqsim.process.equipment.subsea.SubseaJumper} - Rigid and flexible jumper
 * connections</li>
 * <li>{@link neqsim.process.equipment.subsea.FlexiblePipe} - Dynamic and static flexible pipes</li>
 * </ul>
 *
 * <h2>Production Equipment</h2>
 * <ul>
 * <li>{@link neqsim.process.equipment.subsea.SubseaManifold} - Production/test manifolds</li>
 * <li>{@link neqsim.process.equipment.subsea.SubseaTree} - Christmas tree (wellhead control)</li>
 * <li>{@link neqsim.process.equipment.subsea.SubseaBooster} - Subsea pumps and compressors</li>
 * </ul>
 *
 * <h2>Control Systems</h2>
 * <ul>
 * <li>{@link neqsim.process.equipment.subsea.Umbilical} - Control umbilicals (hydraulic,
 * electrical, chemical)</li>
 * </ul>
 *
 * <h2>Design Standards Support</h2>
 * <p>
 * All equipment classes support mechanical design per industry standards:
 * </p>
 * <ul>
 * <li>DNV-ST-F101 - Submarine Pipeline Systems</li>
 * <li>API RP 17A/D/G - Subsea Production Systems Design</li>
 * <li>API RP 17B/J/K - Flexible Pipe Specifications</li>
 * <li>API RP 17E - Subsea Umbilicals</li>
 * <li>API RP 17Q/V - Subsea Equipment Qualification and Boosting</li>
 * <li>NORSOK U-001 - Subsea Production Systems</li>
 * <li>ISO 13628 - Petroleum and Natural Gas Industries - Subsea Production Systems</li>
 * </ul>
 *
 * <h2>Usage Pattern</h2>
 *
 * <pre>{@code
 * // Create subsea production system
 * Stream wellStream = new Stream("Well-1", wellFluid);
 *
 * // Configure subsea tree
 * SubseaTree tree = new SubseaTree("Well-1 Tree", wellStream);
 * tree.setTreeType(SubseaTree.TreeType.HORIZONTAL);
 * tree.setPressureRating(SubseaTree.PressureRating.PR10000);
 *
 * // Connect to manifold via jumper
 * SubseaJumper jumper = new SubseaJumper("Well-1 Jumper", tree.getOutletStream());
 * jumper.setJumperType(SubseaJumper.JumperType.RIGID_M_SHAPE);
 *
 * // Create manifold
 * SubseaManifold manifold = new SubseaManifold("Production Manifold");
 * manifold.setManifoldType(SubseaManifold.ManifoldType.PRODUCTION_TEST);
 * manifold.addWellConnection(jumper.getOutletStream());
 *
 * // Connect to PLET
 * PLET plet = new PLET("Export PLET", manifold.getOutletStream());
 * plet.setConnectionType(PLET.ConnectionType.VERTICAL_HUB);
 *
 * // Run system
 * ProcessSystem process = new ProcessSystem();
 * process.add(tree);
 * process.add(jumper);
 * process.add(manifold);
 * process.add(plet);
 * process.run();
 *
 * // Get mechanical design reports
 * String treeDesign = tree.getMechanicalDesign().toJson();
 * String manifoldDesign = manifold.getMechanicalDesign().toJson();
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see neqsim.process.mechanicaldesign.subsea
 */
package neqsim.process.equipment.subsea;
