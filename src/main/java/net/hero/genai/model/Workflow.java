package net.hero.genai.model;

import java.util.List;

public record Workflow(
    String id,
    String name,
    String description,
    List<String> triggerKeywords,
    List<WorkflowStep> steps
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
    }
}
