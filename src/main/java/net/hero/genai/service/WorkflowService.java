package net.hero.genai.service;

import net.hero.genai.model.Message;
import net.hero.genai.model.Workflow;
import net.hero.genai.model.WorkflowStep;
import net.hero.genai.model.WorkflowStepStatus;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class WorkflowService {

    private static final Logger LOGGER = Logger.getLogger(WorkflowService.class.getName());
    private static final WorkflowService INSTANCE = new WorkflowService();

    public record DeterminationResult(String status, String decision, String message, boolean fileAccessNeeded, String fileAccessPath) {}

    private final List<Workflow> predefinedWorkflows = new ArrayList<>();
    private Workflow activeWorkflow = null;
    private int currentStepIndex = -1;
    private final List<WorkflowStepStatus> stepStatuses = new ArrayList<>();
    private final List<String> stepOutputs = new ArrayList<>();

    // For dynamic workflow proposal approval flow
    private Workflow proposedWorkflow = null;

    // For Workflow Determination / Information Gathering separate session
    private final List<Message> determinationHistory = new ArrayList<>();
    private String pendingUserRequest = "";
    private String userRequest = "";
    private boolean standardChatMode = false;

    private WorkflowService() {
        loadBuiltInWorkflows();
    }

    public static WorkflowService getInstance() {
        return INSTANCE;
    }

    public List<Workflow> getPredefinedWorkflows() {
        return List.copyOf(predefinedWorkflows);
    }

    /**
     * Matches a predefined workflow based on the trigger keywords.
     */
    public Workflow matchWorkflow(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return null;
        }
        String lowerPrompt = prompt.toLowerCase();
        for (Workflow wf : predefinedWorkflows) {
            for (String kw : wf.triggerKeywords()) {
                if (lowerPrompt.contains(kw.toLowerCase())) {
                    LOGGER.log(Level.INFO, "Matched workflow " + wf.id() + " via keyword: " + kw);
                    return wf;
                }
            }
        }
        return null;
    }

    /**
     * Asynchronously generates a dynamic workflow using Ollama LLM.
     */
    public void generateDynamicWorkflow(String userPrompt, String baseUrl, String modelName, OllamaApiService apiService, java.util.function.Consumer<Workflow> callback) {
        LOGGER.log(Level.INFO, "Generating dynamic workflow for prompt: " + userPrompt);
        String systemInstructions = """
            You are a system that generates a task-specific workflow for any complex user request.
            Your output must be a single, valid JSON object that represents a custom workflow. Do not include markdown code block markers or any preamble, only the JSON.
            The workflow MUST follow the "Output-then-Verify" principle: every output step must be followed by a verify step that checks the correctness, quality, or safety of the output.

            JSON schema:
            {
              "id": "dynamic-workflow",
              "name": "Custom title of the workflow",
              "description": "Short description of the workflow goals",
              "trigger_keywords": [],
              "steps": [
                {
                  "phase": 1,
                  "name": "Name of output step",
                  "type": "output",
                  "description": "Instructions for what to output",
                  "verify_step_id": "verify-step-1"
                },
                {
                  "phase": 2,
                  "name": "Name of verify step",
                  "type": "verify",
                  "description": "Instructions for how to verify step 1 output",
                  "verify_step_id": null
                }
              ]
            }

            User request: "%s"
            """.formatted(userPrompt);

        Thread.startVirtualThread(() -> {
            try {
                String response = apiService.chat(baseUrl, modelName, systemInstructions);
                LOGGER.log(Level.INFO, "Received Ollama response for dynamic workflow: " + response);

                String cleanedJson = response;
                if (cleanedJson.contains("```json")) {
                    cleanedJson = cleanedJson.substring(cleanedJson.indexOf("```json") + 7);
                    if (cleanedJson.contains("```")) {
                        cleanedJson = cleanedJson.substring(0, cleanedJson.indexOf("```"));
                    }
                } else if (cleanedJson.contains("```")) {
                    cleanedJson = cleanedJson.substring(cleanedJson.indexOf("```") + 3);
                    if (cleanedJson.contains("```")) {
                        cleanedJson = cleanedJson.substring(0, cleanedJson.indexOf("```"));
                    }
                }
                cleanedJson = cleanedJson.trim();

                Workflow wf = parseWorkflowJson(cleanedJson);
                callback.accept(wf);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to generate dynamic workflow", e);
                callback.accept(null);
            }
        });
    }

    /**
     * Asynchronously determines the workflow for a user request, potentially gathering info.
     */
    public void determineWorkflow(String latestUserPrompt, String baseUrl, String modelName, OllamaApiService apiService, java.util.function.Consumer<DeterminationResult> callback) {
        LOGGER.log(Level.INFO, "Determining workflow for prompt: " + latestUserPrompt);

        // Build the system instructions listing all predefined workflows
        StringBuilder wfListBuilder = new StringBuilder();
        for (Workflow wf : predefinedWorkflows) {
            wfListBuilder.append("- ID: \"").append(wf.id()).append("\"\n");
            wfListBuilder.append("  Name: \"").append(wf.name()).append("\"\n");
            wfListBuilder.append("  Description: \"").append(wf.description()).append("\"\n");
            wfListBuilder.append("  Trigger Keywords: ").append(String.join(", ", wf.triggerKeywords())).append("\n\n");
        }

        String targetLanguage = "ja".equals(java.util.Locale.getDefault().getLanguage()) ? "Japanese" : "English";

        String systemInstructions = """
            You are a workflow determination agent. Your task is to analyze the user's request and determine the most appropriate workflow to handle it.
            You must output a single, valid JSON object with NO markdown block or surrounding text.

            Available predefined workflows:
            %s
            Other decision options:
            - "dynamic": Use this if the user request is a complex task, creation, refactoring, or multi-step execution that does NOT fit any predefined workflow, but still requires a structured sequence of output and verification steps.
            - "standard": Use this if the request is a simple question, greeting, general conversation, explanation request, or a query that does NOT require a multi-step task/workflow.

            JSON Schema:
            {
              "status": "DETERMINED" or "GATHERING",
              "decision": "<predefined-workflow-id>" or "dynamic" or "standard" or null,
              "message": "<polite message in %s>",
              "fileAccessNeeded": true or false,
              "fileAccessPath": "<relative-path-to-file-if-needed-else-null>"
            }

            Rules:
            1. If you can make a clear and confident decision immediately from the user's message(s), set "status" to "DETERMINED", "decision" to the chosen option, and "message" to a brief %s confirmation message.
            2. If the request is vague, ambiguous, or lacks crucial details to choose a workflow, set "status" to "GATHERING", "decision" to null, and write a polite clarifying question in %s in "message" to ask the user for clarification.
            3. Do not assume or jump to a predefined workflow or "dynamic" if there is too much ambiguity; ask first.
            4. If the user clarifies and a decision can be made, transition to "DETERMINED".
            5. Always respond in %s for the "message" field.
            6. If the user request refers to reading, analyzing, or modifying a specific file or directory in the workspace (e.g. "pom.xml", "Main.java", "src/..."), set "fileAccessNeeded" to true and specify the relative file/directory path in "fileAccessPath". Otherwise, set "fileAccessNeeded" to false and "fileAccessPath" to null.
            """.formatted(wfListBuilder.toString(), targetLanguage, targetLanguage, targetLanguage, targetLanguage, targetLanguage);

        StringBuilder conversationBuilder = new StringBuilder();
        conversationBuilder.append("=== CONVERSATION HISTORY ===\n");
        for (Message msg : determinationHistory) {
            conversationBuilder.append(msg.role().toUpperCase()).append(": ").append(msg.content()).append("\n");
        }
        conversationBuilder.append("USER: ").append(latestUserPrompt).append("\n\n");
        conversationBuilder.append("Please analyze the history and the latest user message, and output the JSON object.");

        String prompt = conversationBuilder.toString();

        Thread.startVirtualThread(() -> {
            try {
                String response = apiService.chat(baseUrl, modelName, systemInstructions + "\n\n" + prompt);
                LOGGER.log(Level.INFO, "Received Ollama response for workflow determination: " + response);

                String cleanedJson = response;
                if (cleanedJson.contains("```json")) {
                    cleanedJson = cleanedJson.substring(cleanedJson.indexOf("```json") + 7);
                    if (cleanedJson.contains("```")) {
                        cleanedJson = cleanedJson.substring(0, cleanedJson.indexOf("```"));
                    }
                } else if (cleanedJson.contains("```")) {
                    cleanedJson = cleanedJson.substring(cleanedJson.indexOf("```") + 3);
                    if (cleanedJson.contains("```")) {
                        cleanedJson = cleanedJson.substring(0, cleanedJson.indexOf("```"));
                    }
                }
                cleanedJson = cleanedJson.trim();

                DeterminationResult result = parseDeterminationResultJson(cleanedJson);
                callback.accept(result);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to determine workflow", e);
                callback.accept(null);
            }
        });
    }

    public static DeterminationResult parseDeterminationResultJson(String json) {
        String status = extractStringField(json, "status");
        String decision = extractStringField(json, "decision");
        String message = extractStringField(json, "message");
        if (decision.isEmpty() || "null".equals(decision)) {
            decision = null;
        }

        boolean fileAccessNeeded = false;
        Pattern boolPattern = Pattern.compile("\"fileAccessNeeded\"\\s*:\\s*(true|false)");
        Matcher boolMatcher = boolPattern.matcher(json);
        if (boolMatcher.find()) {
            fileAccessNeeded = Boolean.parseBoolean(boolMatcher.group(1));
        }

        String fileAccessPath = extractStringField(json, "fileAccessPath");
        if (fileAccessPath.isEmpty() || "null".equals(fileAccessPath)) {
            fileAccessPath = null;
        }

        return new DeterminationResult(status, decision, message, fileAccessNeeded, fileAccessPath);
    }

    public Workflow getActiveWorkflow() {
        return activeWorkflow;
    }

    public int getCurrentStepIndex() {
        return currentStepIndex;
    }

    public List<WorkflowStepStatus> getStepStatuses() {
        return List.copyOf(stepStatuses);
    }

    public List<String> getStepOutputs() {
        return List.copyOf(stepOutputs);
    }

    public Workflow getProposedWorkflow() {
        return proposedWorkflow;
    }

    public void setProposedWorkflow(Workflow workflow) {
        this.proposedWorkflow = workflow;
    }

    public void clearProposedWorkflow() {
        this.proposedWorkflow = null;
    }

    public List<Message> getDeterminationHistory() {
        return List.copyOf(determinationHistory);
    }

    public List<Message> getDeterminationHistoryWritable() {
        return determinationHistory;
    }

    public void clearDeterminationHistory() {
        this.determinationHistory.clear();
    }

    public String getPendingUserRequest() {
        return pendingUserRequest;
    }

    public void setPendingUserRequest(String pendingUserRequest) {
        this.pendingUserRequest = pendingUserRequest;
    }

    public void clearPendingUserRequest() {
        this.pendingUserRequest = "";
    }

    public String getUserRequest() {
        return userRequest;
    }

    public void setUserRequest(String userRequest) {
        this.userRequest = userRequest;
    }

    public boolean isStandardChatMode() {
        return standardChatMode;
    }

    public void setStandardChatMode(boolean standardChatMode) {
        this.standardChatMode = standardChatMode;
    }

    /**
     * Starts execution of a workflow with a user request.
     */
    public void startWorkflow(Workflow workflow, String userRequest) {
        this.activeWorkflow = workflow;
        this.standardChatMode = false;
        this.userRequest = userRequest != null ? userRequest : "";
        this.currentStepIndex = -1;
        this.stepStatuses.clear();
        this.stepOutputs.clear();
        for (int i = 0; i < workflow.steps().size(); i++) {
            this.stepStatuses.add(WorkflowStepStatus.PENDING);
            this.stepOutputs.add("");
        }
        this.proposedWorkflow = null;
        LOGGER.log(Level.INFO, "Workflow started: " + workflow.name() + " with user request: " + this.userRequest);
    }

    /**
     * Starts execution of a workflow.
     */
    public void startWorkflow(Workflow workflow) {
        startWorkflow(workflow, "");
    }

    /**
     * Cancels the current workflow.
     */
    public void cancelWorkflow() {
        this.activeWorkflow = null;
        this.currentStepIndex = -1;
        this.stepStatuses.clear();
        this.stepOutputs.clear();
        this.proposedWorkflow = null;
        this.pendingUserRequest = "";
        this.userRequest = "";
        this.determinationHistory.clear();
        this.standardChatMode = false;
        LOGGER.log(Level.INFO, "Workflow canceled.");
    }

    /**
     * Advances to the next step index. Returns true if there is a next step, false if completed.
     */
    public boolean advanceStep() {
        if (activeWorkflow == null) {
            return false;
        }
        currentStepIndex++;
        if (currentStepIndex >= activeWorkflow.steps().size()) {
            LOGGER.log(Level.INFO, "Workflow " + activeWorkflow.name() + " completed successfully!");
            activeWorkflow = null;
            currentStepIndex = -1;
            return false;
        }
        return true;
    }

    /**
     * Executes the current step logic.
     */
    public void executeStep(String baseUrl, String modelName, OllamaApiService apiService, ChatStreamListener listener, Runnable onStepFinished) {
        if (activeWorkflow == null || currentStepIndex < 0 || currentStepIndex >= activeWorkflow.steps().size()) {
            return;
        }

        WorkflowStep step = activeWorkflow.steps().get(currentStepIndex);
        stepStatuses.set(currentStepIndex, WorkflowStepStatus.RUNNING);

        if ("output".equalsIgnoreCase(step.type())) {
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("=== WORKFLOW STEP: ").append(step.name()).append(" ===\n");
            promptBuilder.append("Workflow: ").append(activeWorkflow.name()).append("\n");
            promptBuilder.append("Step Description: ").append(step.description()).append("\n\n");

            promptBuilder.append("=== CONTEXT (Previous Steps Outputs) ===\n");
            for (int i = 0; i < currentStepIndex; i++) {
                WorkflowStep prevStep = activeWorkflow.steps().get(i);
                promptBuilder.append("Step ").append(i + 1).append(" (").append(prevStep.name()).append("):\n");
                promptBuilder.append(stepOutputs.get(i)).append("\n\n");
            }

            promptBuilder.append("=== USER REQUEST ===\n");
            if (stepOutputs.get(0).isEmpty()) {
                promptBuilder.append((this.userRequest != null && !this.userRequest.isEmpty()) ? this.userRequest : "Implement user request");
            } else {
                promptBuilder.append("Please refine based on previous step design.");
            }

            String prompt = promptBuilder.toString();
            apiService.chatStream(baseUrl, modelName, prompt, new ChatStreamListener() {
                private final StringBuilder response = new StringBuilder();
                @Override
                public void onNext(String token) {
                    response.append(token);
                    listener.onNext(token);
                }
                @Override
                public void onComplete(String fullResponse) {
                    stepOutputs.set(currentStepIndex, fullResponse);
                    stepStatuses.set(currentStepIndex, WorkflowStepStatus.SUCCESS);
                    listener.onComplete(fullResponse);
                    onStepFinished.run();
                }
                @Override
                public void onError(Throwable error) {
                    stepStatuses.set(currentStepIndex, WorkflowStepStatus.FAILED);
                    listener.onError(error);
                }
            });
        } else {
            // Verify step
            File workspaceDir = SecurityService.getInstance().getActiveWorkspace();
            boolean isMavenVerify = (step.name().contains("ビルド") || step.name().contains("テスト") || step.description().contains("compile") || step.description().contains("test"))
                    && workspaceDir != null && new File(workspaceDir, "pom.xml").exists();

            if (isMavenVerify) {
                listener.onNext("[Automated Verification] Executing 'mvn clean test-compile'...\n");
                StringBuilder mavenOutput = new StringBuilder();
                Thread.startVirtualThread(() -> {
                    boolean success = runMavenCommand("test-compile", workspaceDir, mavenOutput);
                    if (success) {
                        listener.onNext("\n[Automated Verification] Build and test-compilation succeeded!\n");
                        stepOutputs.set(currentStepIndex, mavenOutput.toString());
                        stepStatuses.set(currentStepIndex, WorkflowStepStatus.SUCCESS);
                        listener.onComplete(mavenOutput.toString());
                        onStepFinished.run();
                    } else {
                        listener.onNext("\n[Automated Verification] Build FAILED! Error log:\n" + mavenOutput.toString() + "\n");
                        stepOutputs.set(currentStepIndex, "Maven build failed:\n" + mavenOutput.toString());
                        stepStatuses.set(currentStepIndex, WorkflowStepStatus.FAILED);

                        triggerLoopback(listener, onStepFinished, "Maven build failed. Please fix the compiler errors.");
                    }
                });
            } else {
                StringBuilder promptBuilder = new StringBuilder();
                promptBuilder.append("=== WORKFLOW STEP: ").append(step.name()).append(" ===\n");
                promptBuilder.append("Workflow: ").append(activeWorkflow.name()).append("\n");
                promptBuilder.append("Step Description: ").append(step.description()).append("\n\n");

                int prevOutputIdx = currentStepIndex - 1;
                if (prevOutputIdx >= 0) {
                    promptBuilder.append("=== ARTIFACT TO VERIFY (From previous step) ===\n");
                    promptBuilder.append(stepOutputs.get(prevOutputIdx)).append("\n\n");
                }

                promptBuilder.append("Verify the previous step output according to the description. Be rigorous.\n");
                promptBuilder.append("At the end of your response, you MUST write exactly 'VERIFICATION: SUCCESS' if everything is correct and no changes are needed, ");
                promptBuilder.append("or 'VERIFICATION: FAILED' followed by specific points/errors if any corrections are required.\n");

                String prompt = promptBuilder.toString();
                apiService.chatStream(baseUrl, modelName, prompt, new ChatStreamListener() {
                    private final StringBuilder response = new StringBuilder();
                    @Override
                    public void onNext(String token) {
                        response.append(token);
                        listener.onNext(token);
                    }
                    @Override
                    public void onComplete(String fullResponse) {
                        stepOutputs.set(currentStepIndex, fullResponse);
                        if (fullResponse.contains("VERIFICATION: FAILED")) {
                            stepStatuses.set(currentStepIndex, WorkflowStepStatus.FAILED);
                            // Do not complete before loopback is processed
                            triggerLoopback(listener, onStepFinished, "AI self-verification failed. Please address the feedback:\n" + fullResponse);
                        } else {
                            stepStatuses.set(currentStepIndex, WorkflowStepStatus.SUCCESS);
                            listener.onComplete(fullResponse);
                            onStepFinished.run();
                        }
                    }
                    @Override
                    public void onError(Throwable error) {
                        stepStatuses.set(currentStepIndex, WorkflowStepStatus.FAILED);
                        listener.onError(error);
                    }
                });
            }
        }
    }

    private void triggerLoopback(ChatStreamListener listener, Runnable onStepFinished, String feedback) {
        LOGGER.log(Level.INFO, "Triggering workflow loopback from step index " + currentStepIndex);
        listener.onNext("\n⚠️ Verification failed! Rolling back to the previous output step to fix issues...\n");
        int targetIdx = currentStepIndex - 1;
        if (targetIdx >= 0) {
            stepStatuses.set(targetIdx, WorkflowStepStatus.PENDING);
            String oldOutput = stepOutputs.get(targetIdx);
            stepOutputs.set(targetIdx, "[Feedback from verification failure]:\n" + feedback + "\n\n[Previous output]:\n" + oldOutput);
            currentStepIndex = targetIdx - 1;
        }
        listener.onComplete("VERIFICATION FAILED: " + feedback);
        onStepFinished.run();
    }

    private boolean runMavenCommand(String command, File workspaceDir, StringBuilder outputBuilder) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            List<String> cmd = new ArrayList<>();
            if (os.contains("win")) {
                cmd.addAll(List.of("cmd.exe", "/c", "mvn", command));
            } else {
                cmd.addAll(List.of("mvn", command));
            }
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(workspaceDir);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputBuilder.append(line).append("\n");
                }
            }
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to run maven command: mvn " + command, e);
            outputBuilder.append("Failed to run maven: ").append(e.getMessage());
            return false;
        }
    }

    /**
     * Loads built-in workflows from application resources.
     */
    public void loadBuiltInWorkflows() {
        LOGGER.log(Level.INFO, "Loading built-in workflows...");
        predefinedWorkflows.clear();
        try (InputStream is = getClass().getResourceAsStream("/workflows/source-code-creation.json")) {
            if (is != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String json = reader.lines().collect(Collectors.joining("\n"));
                    Workflow wf = parseWorkflowJson(json);
                    predefinedWorkflows.add(wf);
                    LOGGER.log(Level.INFO, "Successfully loaded built-in workflow: " + wf.id());
                }
            } else {
                LOGGER.log(Level.WARNING, "Built-in source-code-creation.json not found in classpath.");
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load built-in workflows", e);
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Loads custom workflows from the user's workspace directory (.age/workflows).
     */
    public void loadWorkspaceCustomWorkflows(File workspaceDir) {
        if (workspaceDir == null || !workspaceDir.exists()) {
            return;
        }
        File workflowsDir = new File(workspaceDir, ".age/workflows");
        if (!workflowsDir.exists() || !workflowsDir.isDirectory()) {
            LOGGER.log(Level.INFO, "No custom workspace workflows directory found.");
            return;
        }

        LOGGER.log(Level.INFO, "Loading custom workflows from: " + workflowsDir.getAbsolutePath());
        File[] jsonFiles = workflowsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
        if (jsonFiles != null) {
            for (File file : jsonFiles) {
                try {
                    String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                    Workflow wf = parseWorkflowJson(json);
                    // Avoid duplicate predefined workflow IDs by removing any existing first
                    predefinedWorkflows.removeIf(w -> w.id().equals(wf.id()));
                    predefinedWorkflows.add(wf);
                    LOGGER.log(Level.INFO, "Loaded custom workflow: " + wf.id());
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Failed to load custom workflow from file: " + file.getName(), e);
                    throw new UncheckedIOException(e);
                }
            }
        }
    }

    /**
     * Simple JSON parser helper to parse Workflow configuration.
     */
    public static Workflow parseWorkflowJson(String json) {
        String id = extractStringField(json, "id");
        String name = extractStringField(json, "name");
        String description = extractStringField(json, "description");
        List<String> triggerKeywords = extractArrayField(json, "trigger_keywords");
        List<WorkflowStep> steps = extractStepsField(json);

        return new Workflow(id, name, description, triggerKeywords, steps);
    }

    private static String extractStringField(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static List<String> extractArrayField(String json, String field) {
        List<String> list = new ArrayList<>();
        int idx = json.indexOf("\"" + field + "\"");
        if (idx == -1) {
            return list;
        }
        int startBracket = json.indexOf('[', idx);
        if (startBracket == -1) {
            return list;
        }
        int endBracket = -1;
        int depth = 0;
        for (int i = startBracket; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    endBracket = i;
                    break;
                }
            }
        }
        if (endBracket == -1) {
            return list;
        }
        String arrayContent = json.substring(startBracket + 1, endBracket);
        Pattern elementPattern = Pattern.compile("\"([^\"]*)\"");
        Matcher elementMatcher = elementPattern.matcher(arrayContent);
        while (elementMatcher.find()) {
            list.add(elementMatcher.group(1));
        }
        return list;
    }

    private static List<WorkflowStep> extractStepsField(String json) {
        List<WorkflowStep> steps = new ArrayList<>();
        int stepsIdx = json.indexOf("\"steps\"");
        if (stepsIdx == -1) {
            return steps;
        }

        int startBracket = json.indexOf('[', stepsIdx);
        if (startBracket == -1) {
            return steps;
        }

        int endBracket = -1;
        int depth = 0;
        for (int i = startBracket; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    endBracket = i;
                    break;
                }
            }
        }

        if (endBracket == -1) {
            return steps;
        }

        String stepsContent = json.substring(startBracket + 1, endBracket);
        Pattern stepPattern = Pattern.compile("\\{[\\s\\S]*?\\}");
        Matcher stepMatcher = stepPattern.matcher(stepsContent);
        while (stepMatcher.find()) {
            String stepJson = stepMatcher.group();
            int phase = 0;
            Pattern phasePattern = Pattern.compile("\"phase\"\\s*:\\s*(\\d+)");
            Matcher phaseMatcher = phasePattern.matcher(stepJson);
            if (phaseMatcher.find()) {
                phase = Integer.parseInt(phaseMatcher.group(1));
            }
            String name = extractStringField(stepJson, "name");
            String type = extractStringField(stepJson, "type");
            String description = extractStringField(stepJson, "description");
            String verifyStepId = extractStringField(stepJson, "verify_step_id");
            if (verifyStepId.isEmpty() || "null".equals(verifyStepId)) {
                verifyStepId = null;
            }
            steps.add(new WorkflowStep(phase, name, type, description, verifyStepId));
        }
        return steps;
    }
}
