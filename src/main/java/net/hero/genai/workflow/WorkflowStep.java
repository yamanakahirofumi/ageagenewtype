package net.hero.genai.workflow;

public record WorkflowStep(
    int phase,
    String name,
    String type, // "output" or "verify"
    String description,
    String verifyStepId
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
    }
}
