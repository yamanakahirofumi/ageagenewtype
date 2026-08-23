package net.hero.genai.workflow;

import net.hero.genai.ollama.ChatStreamListener;
import net.hero.genai.ollama.OllamaApiService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

public final class WorkflowServiceTest {

    private WorkflowService service;

    @BeforeEach
    public void setUp() {
        service = WorkflowService.getInstance();
        service.cancelWorkflow();
    }

    @Test
    @DisplayName("LoadBuiltInWorkflows should successfully parse and load predefined workflows")
    public void loadBuiltInWorkflows_OnInitialization_ShouldLoadPredefinedWorkflows() {
        // Arrange & Act
        service.loadBuiltInWorkflows();
        List<Workflow> list = service.getPredefinedWorkflows();

        // Assert
        assertNotNull(list);
        assertFalse(list.isEmpty());
        Workflow wf = list.stream()
                .filter(w -> "source-code-creation".equals(w.id()))
                .findFirst()
                .orElse(null);
        assertNotNull(wf);
        assertEquals("ソースコード作成・修正ワークフロー", wf.name());
        assertEquals(6, wf.steps().size());
    }

    @Test
    @DisplayName("MatchWorkflow with prompt containing trigger keywords should return the matched workflow")
    public void matchWorkflow_WithValidTriggerKeyword_ShouldReturnMatchedWorkflow() {
        // Arrange
        service.loadBuiltInWorkflows();

        // Act
        Workflow matched = service.matchWorkflow("新しいクラスのソースコードを作成してください。");

        // Assert
        assertNotNull(matched);
        assertEquals("source-code-creation", matched.id());
    }

    @Test
    @DisplayName("MatchWorkflow with unmatched prompt should return null")
    public void matchWorkflow_WithUnmatchedPrompt_ShouldReturnNull() {
        // Arrange
        service.loadBuiltInWorkflows();

        // Act
        Workflow matched = service.matchWorkflow("今日の天気はどうですか？");

        // Assert
        assertNull(matched);
    }

    @Test
    @DisplayName("ParseWorkflowJson should correctly parse nested workflow structure")
    public void parseWorkflowJson_ValidJsonString_ShouldCorrectlyParseWorkflow() {
        // Arrange
        String json = """
            {
              "id": "test-workflow",
              "name": "Test Workflow",
              "description": "A workflow for unit testing",
              "trigger_keywords": ["test", "verify"],
              "steps": [
                {
                  "phase": 1,
                  "name": "Phase 1 Output",
                  "type": "output",
                  "description": "Generate output artifact",
                  "verify_step_id": "verify-phase-1"
                },
                {
                  "phase": 2,
                  "name": "Phase 1 Verify",
                  "type": "verify",
                  "description": "Verify generated output",
                  "verify_step_id": null
                }
              ]
            }
            """;

        // Act
        Workflow wf = WorkflowService.parseWorkflowJson(json);

        // Assert
        assertNotNull(wf);
        assertEquals("test-workflow", wf.id());
        assertEquals("Test Workflow", wf.name());
        assertEquals("A workflow for unit testing", wf.description());
        assertEquals(2, wf.triggerKeywords().size());
        assertEquals("test", wf.triggerKeywords().get(0));

        assertEquals(2, wf.steps().size());
        WorkflowStep step1 = wf.steps().get(0);
        assertEquals(1, step1.phase());
        assertEquals("Phase 1 Output", step1.name());
        assertEquals("output", step1.type());
        assertEquals("verify-phase-1", step1.verifyStepId());

        WorkflowStep step2 = wf.steps().get(1);
        assertEquals(2, step2.phase());
        assertEquals("Phase 1 Verify", step2.name());
        assertEquals("verify", step2.type());
        assertNull(step2.verifyStepId());
    }

    @Test
    @DisplayName("StartWorkflow and CancelWorkflow should correctly transition active state and initialize steps")
    public void startWorkflow_And_CancelWorkflow_ShouldManageActiveState() {
        // Arrange
        service.loadBuiltInWorkflows();
        Workflow wf = service.getPredefinedWorkflows().get(0);

        // Act & Assert (Start)
        service.startWorkflow(wf);
        assertEquals(wf, service.getActiveWorkflow());
        assertEquals(-1, service.getCurrentStepIndex());
        assertEquals(wf.steps().size(), service.getStepStatuses().size());
        assertEquals(WorkflowStepStatus.PENDING, service.getStepStatuses().get(0));

        // Act & Assert (Cancel)
        service.cancelWorkflow();
        assertNull(service.getActiveWorkflow());
        assertEquals(-1, service.getCurrentStepIndex());
        assertTrue(service.getStepStatuses().isEmpty());
    }

    @Test
    @DisplayName("AdvanceStep should increment step index and return true if more steps exist")
    public void advanceStep_SequentialAdvancement_ShouldIncrementIndexAndReturnTrueUntilEnd() {
        // Arrange
        service.loadBuiltInWorkflows();
        Workflow wf = service.getPredefinedWorkflows().get(0);
        service.startWorkflow(wf);

        // Act & Assert (Step 1)
        boolean hasNext1 = service.advanceStep();
        assertTrue(hasNext1);
        assertEquals(0, service.getCurrentStepIndex());

        // Act & Assert (Step 2 to 6)
        for (int i = 1; i < 6; i++) {
            assertTrue(service.advanceStep());
            assertEquals(i, service.getCurrentStepIndex());
        }

        // Act & Assert (Past End)
        assertFalse(service.advanceStep());
        assertNull(service.getActiveWorkflow());
        assertEquals(-1, service.getCurrentStepIndex());
    }

    @Test
    @DisplayName("ParseDeterminationResultJson with valid GATHERING JSON should parse correctly")
    public void parseDeterminationResultJson_ValidGatheringJson_ShouldParseCorrectly() {
        // Arrange
        String json = "{\"status\": \"GATHERING\", \"decision\": null, \"message\": \"具体的な要件を教えていただけますか？\"}";

        // Act
        WorkflowService.DeterminationResult result = WorkflowService.parseDeterminationResultJson(json);

        // Assert
        assertNotNull(result);
        assertEquals("GATHERING", result.status());
        assertNull(result.decision());
        assertEquals("具体的な要件を教えていただけますか？", result.message());
    }

    @Test
    @DisplayName("ParseDeterminationResultJson with valid DETERMINED JSON should parse correctly")
    public void parseDeterminationResultJson_ValidDeterminedJson_ShouldParseCorrectly() {
        // Arrange
        String json = "{\"status\": \"DETERMINED\", \"decision\": \"source-code-creation\", \"message\": \"ソースコード作成を開始します。\"}";

        // Act
        WorkflowService.DeterminationResult result = WorkflowService.parseDeterminationResultJson(json);

        // Assert
        assertNotNull(result);
        assertEquals("DETERMINED", result.status());
        assertEquals("source-code-creation", result.decision());
        assertEquals("ソースコード作成を開始します。", result.message());
    }

    @Test
    @DisplayName("StartWorkflow with custom user request should store and initialize correctly")
    public void startWorkflow_WithUserRequest_ShouldStoreAndInitializeCorrectly() {
        // Arrange
        service.loadBuiltInWorkflows();
        Workflow wf = service.getPredefinedWorkflows().get(0);
        String customRequest = "Create a custom calculator class in Java.";

        // Act
        service.startWorkflow(wf, customRequest);

        // Assert
        assertEquals(wf, service.getActiveWorkflow());
        assertEquals(customRequest, service.getUserRequest());
        assertEquals(-1, service.getCurrentStepIndex());
        assertEquals(wf.steps().size(), service.getStepStatuses().size());
        assertEquals(WorkflowStepStatus.PENDING, service.getStepStatuses().get(0));
    }

    @Test
    @DisplayName("ParseDeterminationResultJson with file access needed should parse correctly")
    public void parseDeterminationResultJson_WithFileAccess_ShouldParseCorrectly() {
        // Arrange
        String json = "{\"status\": \"DETERMINED\", \"decision\": \"standard\", \"message\": \"pom.xmlを読み込みます。\", \"fileAccessNeeded\": true, \"fileAccessPath\": \"pom.xml\"}";

        // Act
        WorkflowService.DeterminationResult result = WorkflowService.parseDeterminationResultJson(json);

        // Assert
        assertNotNull(result);
        assertEquals("DETERMINED", result.status());
        assertEquals("standard", result.decision());
        assertEquals("pom.xmlを読み込みます。", result.message());
        assertTrue(result.fileAccessNeeded());
        assertEquals("pom.xml", result.fileAccessPath());
    }

    @Test
    @DisplayName("ParseDeterminationResultJson with file access not needed should parse correctly")
    public void parseDeterminationResultJson_WithNoFileAccess_ShouldParseCorrectly() {
        // Arrange
        String json = "{\"status\": \"GATHERING\", \"decision\": null, \"message\": \"どのようなソースコードを作成しますか？\", \"fileAccessNeeded\": false, \"fileAccessPath\": null}";

        // Act
        WorkflowService.DeterminationResult result = WorkflowService.parseDeterminationResultJson(json);

        // Assert
        assertNotNull(result);
        assertEquals("GATHERING", result.status());
        assertNull(result.decision());
        assertEquals("どのようなソースコードを作成しますか？", result.message());
        assertFalse(result.fileAccessNeeded());
        assertNull(result.fileAccessPath());
    }

    @Test
    @DisplayName("ClarificationActive flag transitions should manage workflow index preservation")
    public void startWorkflow_OnClarificationActive_ShouldManageIndexAndPreserve() {
        // Arrange
        service.loadBuiltInWorkflows();
        Workflow wf = service.getPredefinedWorkflows().get(0);
        service.startWorkflow(wf);

        // Step 1 starts
        assertTrue(service.advanceStep());
        assertEquals(0, service.getCurrentStepIndex());

        // LLM output is set, contains CLARIFICATION_REQUIRED.
        // We set clarificationActive = true
        service.setClarificationActive(true);
        assertTrue(service.isClarificationActive());

        // In ChatController runActiveWorkflowStep(), if isClarificationActive() is true,
        // it sets clarificationActive to false, and does NOT advance step.
        boolean hasNext = true;
        if (service.isClarificationActive()) {
            service.setClarificationActive(false);
        } else {
            hasNext = service.advanceStep();
        }

        // Assert that index is preserved (remains 0) and flag was reset
        assertTrue(hasNext);
        assertEquals(0, service.getCurrentStepIndex());
        assertFalse(service.isClarificationActive());
    }

    @Test
    @DisplayName("executeStep should include previous feedback as a revision block in the constructed prompt")
    public void executeStep_WithPreviousOutputInCurrentStep_ShouldIncludeRevisionBlock() {
        // Arrange
        service.loadBuiltInWorkflows();
        Workflow wf = service.getPredefinedWorkflows().get(0);
        service.startWorkflow(wf);
        assertTrue(service.advanceStep()); // Step index 0

        // Simulate that stepOutputs at index 0 has some feedback/revision
        service.setStepOutput(0, "[User Feedback]: Please add more details.\n\n[Previous output]: some code");

        // We can subclass OllamaApiService to capture the prompt passed to chatStream
        final String[] capturedPrompt = new String[1];
        OllamaApiService mockApiService = new OllamaApiService() {
            @Override
            public void chatStream(String baseUrl, String modelName, String prompt, ChatStreamListener listener) {
                capturedPrompt[0] = prompt;
            }
        };

        // Act
        service.executeStep("http://localhost:11434", "mock-llama3.2", mockApiService, new ChatStreamListener() {
            @Override public void onNext(String token) {}
            @Override public void onComplete(String fullResponse) {}
            @Override public void onError(Throwable error) {}
        }, () -> {});

        // Assert
        assertNotNull(capturedPrompt[0]);
        assertTrue(capturedPrompt[0].contains("=== REVISION / FEEDBACK FOR THIS STEP ==="));
        assertTrue(capturedPrompt[0].contains("[User Feedback]: Please add more details."));
    }
}
