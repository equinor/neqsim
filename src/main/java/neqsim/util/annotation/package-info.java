/**
 * Annotations for AI/ML integration.
 * 
 * <p>
 * This package provides annotations that help AI agents discover and understand NeqSim's API.
 * Methods and classes marked with these annotations can be automatically discovered and their
 * metadata used to generate structured prompts or documentation.
 * </p>
 * 
 * <h2>Available Annotations:</h2>
 * <ul>
 * <li>{@link neqsim.util.annotation.AIExposable} - Mark classes/methods as AI-accessible</li>
 * <li>{@link neqsim.util.annotation.AIParameter} - Document method parameters</li>
 * </ul>
 * 
 * <h2>Usage:</h2>
 * 
 * <pre>
 * {@code
 * // Discover all AI-exposed methods
 * AISchemaDiscovery discovery = new AISchemaDiscovery();
 * List<MethodSchema> methods = discovery.discoverMethods(SystemInterface.class);
 * 
 * // Generate prompt for AI
 * String prompt = discovery.generatePrompt("flash calculation");
 * }
 * </pre>
 * 
 * @since 1.0
 */
package neqsim.util.annotation;
