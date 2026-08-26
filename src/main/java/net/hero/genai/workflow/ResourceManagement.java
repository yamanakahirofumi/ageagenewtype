package net.hero.genai.workflow;

public record ResourceManagement(
    boolean serializedExecution,
    boolean unloadPreviousModel
) {
    public ResourceManagement() {
        this(true, true);
    }
}
