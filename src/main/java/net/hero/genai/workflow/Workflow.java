package net.hero.genai.workflow;

import java.util.List;

public record Workflow(
    String id,
    String name,
    String description,
    List<String> triggerKeywords,
    List<WorkflowStep> steps,
    String orchestrationMode,
    ResourceManagement resourceManagement
) {
    public Workflow {
        if (id == null) {
            throw new IllegalArgumentException("id cannot be null");
        }
        if (name == null) {
            throw new IllegalArgumentException("name cannot be null");
        }
        if (description == null) {
            throw new IllegalArgumentException("description cannot be null");
        }
        if (triggerKeywords == null) {
            triggerKeywords = List.of();
        }
        if (steps == null) {
            steps = List.of();
        }
        if (resourceManagement == null) {
            resourceManagement = new ResourceManagement(true, true);
        }
    }

    // Overloaded constructor for backward compatibility with single-model workflows
    public Workflow(String id, String name, String description, List<String> triggerKeywords, List<WorkflowStep> steps) {
        this(id, name, description, triggerKeywords, steps, "SINGLE_MODEL", new ResourceManagement(true, true));
    }
}
