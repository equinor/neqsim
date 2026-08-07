package neqsim.mcp.server;

import java.util.HashSet;
import java.util.Set;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import neqsim.mcp.runners.McpRequestContext;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Resolves the caller identity for one MCP tool invocation and binds it to
 * {@link McpRequestContext}.
 *
 * <p>
 * Identity is never accepted as a tool argument. It is resolved from, in order:
 * </p>
 * <ol>
 * <li>the Quarkus {@link SecurityIdentity} established by the OIDC bearer-token filter on the HTTP
 * transport — the enterprise path used by Microsoft Copilot Studio and other remote clients;</li>
 * <li>the {@code NEQSIM_MCP_API_KEY} environment variable — the service-to-service and CI path for
 * the stdio transport, where no HTTP request context exists;</li>
 * <li>anonymous, which is correct for local desktop use with security enforcement disabled.</li>
 * </ol>
 *
 * @author Even Solbraa
 * @version 1.0
 */
@ApplicationScoped
public class McpIdentityResolver {

  /** Environment variable carrying a service credential for the stdio transport. */
  static final String API_KEY_ENV = "NEQSIM_MCP_API_KEY";

  /** Claim holding the tenant or project scope in Entra ID / generic OIDC tokens. */
  private static final String TENANT_CLAIM = "tid";

  /** Quarkus security identity, unsatisfied or context-less on the stdio transport. */
  @Inject
  Instance<SecurityIdentity> securityIdentity;

  /**
   * Binds the resolved principal to the current thread for the duration of one tool invocation.
   *
   * <p>
   * Never throws: identity resolution must not be able to fail a calculation. Any failure degrades
   * to the anonymous principal, which the security layer then rejects when enforcement is on.
   * </p>
   */
  public void bindCurrentPrincipal() {
    McpRequestContext.Principal principal = resolve();
    McpRequestContext.set(principal);
  }

  /**
   * Resolves the caller identity from the available transport context.
   *
   * @return the resolved principal, or {@link McpRequestContext.Principal#anonymous()}
   */
  private McpRequestContext.Principal resolve() {
    McpRequestContext.Principal fromToken = resolveFromSecurityIdentity();
    if (fromToken != null) {
      return fromToken;
    }
    String envKey = System.getenv(API_KEY_ENV);
    if (envKey != null && !envKey.trim().isEmpty()) {
      return McpRequestContext.Principal.ofApiKey(envKey.trim());
    }
    return McpRequestContext.Principal.anonymous();
  }

  /**
   * Resolves the principal from a validated OIDC bearer token, when one is present.
   *
   * @return a principal built from token claims, or null when no authenticated identity is available
   */
  private McpRequestContext.Principal resolveFromSecurityIdentity() {
    try {
      if (securityIdentity == null || securityIdentity.isUnsatisfied()) {
        return null;
      }
      SecurityIdentity identity = securityIdentity.get();
      if (identity == null || identity.isAnonymous() || identity.getPrincipal() == null) {
        return null;
      }
      String subject = identity.getPrincipal().getName();
      if (subject == null || subject.trim().isEmpty()) {
        return null;
      }
      Set<String> roles = new HashSet<String>(identity.getRoles());
      return McpRequestContext.Principal.ofClaims(subject, tenantClaim(identity), roles, issuerClaim(identity));
    } catch (Exception e) {
      // No active request context (stdio transport) or OIDC disabled — fall through to anonymous.
      return null;
    }
  }

  /**
   * Reads the tenant claim from a JWT-backed identity.
   *
   * @param identity the authenticated identity
   * @return the tenant claim value, or null when unavailable
   */
  private String tenantClaim(SecurityIdentity identity) {
    JsonWebToken token = asJwt(identity);
    if (token == null) {
      return null;
    }
    Object tenant = token.getClaim(TENANT_CLAIM);
    return tenant != null ? String.valueOf(tenant) : null;
  }

  /**
   * Reads the issuer claim from a JWT-backed identity.
   *
   * @param identity the authenticated identity
   * @return the issuer claim value, or null when unavailable
   */
  private String issuerClaim(SecurityIdentity identity) {
    JsonWebToken token = asJwt(identity);
    return token != null ? token.getIssuer() : null;
  }

  /**
   * Returns the identity principal as a JWT when the token type supports claims.
   *
   * @param identity the authenticated identity
   * @return the JWT principal, or null for non-JWT identities
   */
  private JsonWebToken asJwt(SecurityIdentity identity) {
    if (identity.getPrincipal() instanceof JsonWebToken) {
      return (JsonWebToken) identity.getPrincipal();
    }
    return null;
  }
}
