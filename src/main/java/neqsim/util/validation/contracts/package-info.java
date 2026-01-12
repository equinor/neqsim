/**
 * Module contracts for AI-friendly validation.
 * 
 * <p>
 * This package provides contract interfaces that define preconditions and postconditions for NeqSim
 * modules. AI agents can use these contracts to:
 * </p>
 * 
 * <ul>
 * <li>Validate setup before execution</li>
 * <li>Understand what each module requires and provides</li>
 * <li>Self-correct when requirements are not met</li>
 * </ul>
 * 
 * <h2>Available Contracts:</h2>
 * <ul>
 * <li>{@link neqsim.util.validation.contracts.ThermodynamicSystemContract} - For SystemInterface
 * implementations</li>
 * <li>{@link neqsim.util.validation.contracts.StreamContract} - For StreamInterface
 * implementations</li>
 * <li>{@link neqsim.util.validation.contracts.SeparatorContract} - For Separator equipment</li>
 * </ul>
 * 
 * <h2>Usage Pattern:</h2>
 * 
 * <pre>
 * {@code
 * // Before running
 * StreamContract contract = StreamContract.getInstance();
 * ValidationResult pre = contract.checkPreconditions(stream);
 * if (!pre.isValid()) {
 *   System.out.println(pre.getReport());
 *   // Fix issues based on remediation hints
 * }
 * 
 * stream.run();
 * 
 * // After running
 * ValidationResult post = contract.checkPostconditions(stream);
 * if (!post.isValid()) {
 *   // Handle output issues
 * }
 * }
 * </pre>
 * 
 * @since 1.0
 */
package neqsim.util.validation.contracts;
