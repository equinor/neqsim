package neqsim.documentation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/** Tests the S8 transition-chain checkpoint-transition documentation contract. */
class H2sOxygenS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionDocumentationTest {
  private static final Path DOCUMENT = Paths.get("docs", "chemicalreactions",
      "h2s_oxygen_s8_stream_application_batch_reconciliation_checkpoint_manifest_transition_chain_transition_ledger_transition_chain_checkpoint_transition.md");

  /** Verify sequence, prefix, numerical, digest, and evidence boundaries remain explicit. */
  @Test
  void testDocumentationContract() throws IOException {
    String documentation = new String(Files.readAllBytes(DOCUMENT), StandardCharsets.UTF_8);

    assertTrue(documentation.startsWith("---\n"));
    assertTrue(documentation.contains("checkpoint-series identity"));
    assertTrue(documentation.contains("candidate checkpoint sequence must increase strictly"));
    assertTrue(documentation.contains("exact prefix of the candidate chain"));
    assertTrue(documentation.contains("MessageDigest.isEqual"));
    assertTrue(documentation.contains("unchanged chain at a later checkpoint sequence or a strict append"));
    assertTrue(documentation.contains("non-increasing checkpoint sequence fail\nclosed"));
    assertTrue(documentation.contains("overflow-detecting exact integer\narithmetic"));
    assertTrue(documentation.contains("versioned canonical SHA-256 digest"));
    assertTrue(documentation.contains("allocates no checkpoint identifier or sequence"));
    assertTrue(documentation.contains("performs no flash calculation"));
    assertTrue(documentation.contains("no stream, fluid, process, pipeline, or injection mutation"));
  }
}
