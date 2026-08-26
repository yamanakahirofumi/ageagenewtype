package net.hero.genai.workflow;

import java.util.List;

public record WorkflowStep(
    int phase,
    String name,
    String type, // "output" or "verify"
    String description,
    String verifyStepId,
    String assignedModel,
    String fallbackModel,
    List<String> ensembleModels,
    String aggregationStrategy, // "MAJORITY_VOTE" or "BEST_PARTS_SYNTHESIS"
    String verifyAction, // e.g. "MAVEN_TEST"
    String onFailureRouteModel
) {
    public WorkflowStep {
        if (name == null) {
            throw new IllegalArgumentException("name cannot be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("type cannot be null");
        }
        if (description == null) {
            throw new IllegalArgumentException("description cannot be null");
        }
        if (ensembleModels == null) {
            ensembleModels = List.of();
        }
    }

    // Overloaded constructor for backward compatibility with single-model steps
    public WorkflowStep(int phase, String name, String type, String description, String verifyStepId) {
        this(phase, name, type, description, verifyStepId, null, null, List.of(), null, null, null);
    }
}
