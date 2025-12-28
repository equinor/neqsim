package neqsim.util.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class, method, or field as exposed for AI/ML integration.
 * 
 * <p>
 * AI agents can use reflection to discover methods marked with this annotation and understand their
 * purpose, parameters, and expected behavior.
 * </p>
 * 
 * <h2>Usage Examples:</h2>
 * 
 * <pre>
 * {@code
 * &#64;AIExposable(
 *   description = "Calculate vapor-liquid equilibrium at given T and P",
 *   category = "thermodynamics",
 *   example = "ops.TPflash()"
 * )
 * public void TPflash() { ... }
 * }
 * </pre>
 * 
 * @author NeqSim
 * @version 1.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
public @interface AIExposable {

  /**
   * Human-readable description of what this element does.
   * 
   * @return description text
   */
  String description() default "";

  /**
   * Category for grouping related functionality.
   * 
   * <p>
   * Common categories: "thermodynamics", "process", "optimization", "properties", "flash"
   * </p>
   * 
   * @return category name
   */
  String category() default "general";

  /**
   * Example code showing how to use this element.
   * 
   * @return example code snippet
   */
  String example() default "";

  /**
   * Priority for AI suggestion (higher = more likely to suggest).
   * 
   * <p>
   * Range: 0-100. Default is 50 (normal priority).
   * </p>
   * 
   * @return priority value
   */
  int priority() default 50;

  /**
   * Whether this method/class is safe for automated execution.
   * 
   * <p>
   * Methods that modify state or have side effects should be marked as non-safe.
   * </p>
   * 
   * @return true if safe to auto-execute
   */
  boolean safe() default true;

  /**
   * Tags for search and discovery.
   * 
   * @return array of tags
   */
  String[] tags() default {};
}
