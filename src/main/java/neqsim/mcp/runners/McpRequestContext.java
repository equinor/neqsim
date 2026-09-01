package neqsim.mcp.runners;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Per-request caller identity for the NeqSim MCP server.
 *
 * <p>
 * The MCP tool facade does not accept credentials as tool arguments — secrets must never travel through model-visible
 * tool parameters. Instead the transport layer (stdio launcher, HTTP filter, or OAuth resource-server filter) resolves
 * the caller once and binds a {@link Principal} to the serving thread. Governance code such as
 * {@link IndustrialProfile#enforceAccess(String)} and {@link SecurityRunner#checkAccess(String, String)} then reads the
 * bound principal instead of receiving a hard-coded null credential.
 * </p>
 *
 * <p>
 * When nothing is bound, {@link #current()} returns {@link Principal#anonymous()}. That keeps local desktop usage
 * (security disabled) working unchanged while allowing enforcement to fail closed when security is enabled.
 * </p>
 *
 * <p>
 * Typical transport usage:
 * </p>
 *
 * <pre>
 * McpRequestContext.set(Principal.ofApiKey(apiKeyFromHeader));
 * try {
 *   return tool.invoke(arguments);
 * } finally {
 *   McpRequestContext.clear();
 * }
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class McpRequestContext {

  /** Thread-bound principal for the request currently being served. */
  private static final ThreadLocal<Principal> CURRENT = new ThreadLocal<Principal>();

  /**
   * Private constructor — utility class.
   */
  private McpRequestContext() {
  }

  /**
   * Binds a principal to the current thread for the duration of one MCP request.
   *
   * @param principal the resolved caller identity, or null to clear the binding
   */
  public static void set(Principal principal) {
    if (principal == null) {
      CURRENT.remove();
    } else {
      CURRENT.set(principal);
    }
  }

  /**
   * Returns the principal bound to the current thread.
   *
   * @return the bound principal, or {@link Principal#anonymous()} when nothing is bound
   */
  public static Principal current() {
    Principal principal = CURRENT.get();
    return principal != null ? principal : Principal.anonymous();
  }

  /**
   * Returns the credential of the current principal.
   *
   * @return the API key or bearer subject credential, or null when the caller is anonymous
   */
  public static String currentCredential() {
    return current().getCredential();
  }

  /**
   * Returns the audit subject of the current principal.
   *
   * @return a non-null subject identifier suitable for audit records
   */
  public static String currentSubject() {
    return current().getSubject();
  }

  /**
   * Removes any principal bound to the current thread. Transports must call this in a finally block so pooled threads
   * never leak identity between requests.
   */
  public static void clear() {
    CURRENT.remove();
  }

  /**
   * Immutable caller identity resolved by the transport layer.
   */
  public static final class Principal {

    /** Shared anonymous principal used when no identity is bound. */
    private static final Principal ANONYMOUS = new Principal(null, "anonymous", "default",
        Collections.<String>emptySet(), null, false);

    /** Raw credential presented by the caller (API key, or bearer token subject). */
    private final String credential;

    /** Stable subject identifier used for audit records. */
    private final String subject;

    /** Tenant or project scope the request belongs to. */
    private final String tenant;

    /** Roles granted to the caller. */
    private final Set<String> roles;

    /** Token issuer, when the identity came from an OIDC provider. */
    private final String issuer;

    /** Whether the transport actually authenticated this caller. */
    private final boolean authenticated;

    /**
     * Creates a principal.
     *
     * @param credential raw credential, or null for anonymous callers
     * @param subject stable subject identifier, must not be null
     * @param tenant tenant or project scope, must not be null
     * @param roles granted roles, must not be null
     * @param issuer token issuer, or null when not applicable
     * @param authenticated whether the transport authenticated the caller
     */
    private Principal(String credential, String subject, String tenant, Set<String> roles, String issuer,
        boolean authenticated) {
      this.credential = credential;
      this.subject = subject;
      this.tenant = tenant;
      this.roles = Collections.unmodifiableSet(new HashSet<String>(roles));
      this.issuer = issuer;
      this.authenticated = authenticated;
    }

    /**
     * Returns the shared anonymous principal.
     *
     * @return an unauthenticated principal with no credential
     */
    public static Principal anonymous() {
      return ANONYMOUS;
    }

    /**
     * Creates an authenticated principal from an API key presented at the transport layer.
     *
     * @param apiKey the API key, must not be null or empty
     * @return a principal carrying the API key as its credential
     */
    public static Principal ofApiKey(String apiKey) {
      if (apiKey == null || apiKey.trim().isEmpty()) {
        return ANONYMOUS;
      }
      return new Principal(apiKey, "apikey:" + shortHint(apiKey), "default", Collections.<String>emptySet(), null,
          true);
    }

    /**
     * Creates an authenticated principal from validated OIDC/OAuth token claims.
     *
     * @param subject the token subject claim, must not be null or empty
     * @param tenant the tenant or project scope, may be null
     * @param roles granted roles, may be null
     * @param issuer the token issuer claim, may be null
     * @return a principal carrying the subject as its credential
     */
    public static Principal ofClaims(String subject, String tenant, Set<String> roles, String issuer) {
      if (subject == null || subject.trim().isEmpty()) {
        return ANONYMOUS;
      }
      Set<String> grantedRoles = roles != null ? roles : Collections.<String>emptySet();
      String scope = tenant != null && !tenant.trim().isEmpty() ? tenant : "default";
      return new Principal(subject, subject, scope, grantedRoles, issuer, true);
    }

    /**
     * Returns a short non-reversible hint of a credential for audit records.
     *
     * @param value the credential
     * @return the first characters of the credential, safe to log
     */
    private static String shortHint(String value) {
      return value.substring(0, Math.min(8, value.length()));
    }

    /**
     * Returns the raw credential.
     *
     * @return the credential, or null for anonymous callers
     */
    public String getCredential() {
      return credential;
    }

    /**
     * Returns the audit subject.
     *
     * @return a non-null subject identifier
     */
    public String getSubject() {
      return subject;
    }

    /**
     * Returns the tenant or project scope.
     *
     * @return a non-null scope identifier
     */
    public String getTenant() {
      return tenant;
    }

    /**
     * Returns the granted roles.
     *
     * @return an unmodifiable set of role names
     */
    public Set<String> getRoles() {
      return roles;
    }

    /**
     * Returns the token issuer.
     *
     * @return the issuer, or null when the identity did not come from a token
     */
    public String getIssuer() {
      return issuer;
    }

    /**
     * Returns whether the transport authenticated this caller.
     *
     * @return true when the identity was verified by the transport
     */
    public boolean isAuthenticated() {
      return authenticated;
    }
  }
}
