/**
 * Mechanical design package for subsea SURF (Subsea, Umbilicals, Risers, Flowlines) equipment.
 *
 * <p>
 * This package provides mechanical design calculation classes for subsea production equipment,
 * implementing industry standards for:
 * </p>
 *
 * <h2>Pipeline Infrastructure</h2>
 * <ul>
 * <li>{@link neqsim.process.mechanicaldesign.subsea.PLETMechanicalDesign} - PLET structural and
 * foundation design</li>
 * <li>{@link neqsim.process.mechanicaldesign.subsea.PLEMMechanicalDesign} - PLEM header and
 * foundation design</li>
 * <li>{@link neqsim.process.mechanicaldesign.subsea.SubseaJumperMechanicalDesign} - Jumper stress
 * analysis</li>
 * <li>{@link neqsim.process.mechanicaldesign.subsea.FlexiblePipeMechanicalDesign} - Flexible pipe
 * layer design</li>
 * </ul>
 *
 * <h2>Production Equipment</h2>
 * <ul>
 * <li>{@link neqsim.process.mechanicaldesign.subsea.SubseaManifoldMechanicalDesign} - Manifold
 * header and structure design</li>
 * <li>{@link neqsim.process.mechanicaldesign.subsea.SubseaTreeMechanicalDesign} - Tree valve and
 * pressure containment design</li>
 * <li>{@link neqsim.process.mechanicaldesign.subsea.SubseaBoosterMechanicalDesign} - Pump/compressor
 * sizing</li>
 * </ul>
 *
 * <h2>Control Systems</h2>
 * <ul>
 * <li>{@link neqsim.process.mechanicaldesign.subsea.UmbilicalMechanicalDesign} - Umbilical tube and
 * armor design</li>
 * </ul>
 *
 * <h2>Supported Design Standards</h2>
 * <ul>
 * <li><b>DNV-ST-F101</b> - Submarine Pipeline Systems</li>
 * <li><b>DNV-ST-F201</b> - Dynamic Risers</li>
 * <li><b>DNV-RP-F109</b> - On-Bottom Stability Design</li>
 * <li><b>API Spec 17D</b> - Subsea Wellhead and Tree Equipment</li>
 * <li><b>API RP 17A</b> - Design and Operation of Subsea Production Systems</li>
 * <li><b>API RP 17B</b> - Recommended Practice for Flexible Pipe</li>
 * <li><b>API Spec 17J</b> - Specification for Unbonded Flexible Pipe</li>
 * <li><b>API Spec 17K</b> - Specification for Bonded Flexible Pipe</li>
 * <li><b>API RP 17E</b> - Specification for Subsea Umbilicals</li>
 * <li><b>API RP 17G</b> - Subsea Production System Design</li>
 * <li><b>API RP 17Q</b> - Subsea Equipment Qualification</li>
 * <li><b>API RP 17V</b> - Subsea Boosting Systems</li>
 * <li><b>ISO 13628</b> - Petroleum and Natural Gas Industries - Subsea Production Systems</li>
 * <li><b>NORSOK U-001</b> - Subsea Production Systems</li>
 * </ul>
 *
 * <h2>Usage Pattern</h2>
 *
 * <pre>{@code
 * // Create subsea tree
 * SubseaTree tree = new SubseaTree("Well-1 Tree", wellStream);
 * tree.setTreeType(SubseaTree.TreeType.HORIZONTAL);
 * tree.setPressureRating(SubseaTree.PressureRating.PR10000);
 *
 * // Initialize and run mechanical design
 * tree.initMechanicalDesign();
 * SubseaTreeMechanicalDesign design = tree.getMechanicalDesign();
 * design.readDesignSpecifications();
 * design.calcDesign();
 *
 * // Get JSON report
 * String report = design.toJson();
 *
 * // Get specific results
 * double boreWall = design.getBoreWallThickness();
 * double testPressure = design.getTestPressure();
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see neqsim.process.equipment.subsea
 */
package neqsim.process.mechanicaldesign.subsea;
