package net.hero.genai.supportai;

/**
 * Interface representing an extensible capability (tool or API) that the Support AI can access.
 */
public interface SupportAICapability {

    /**
     * Unique identifier for this capability.
     *
     * @return the unique capability ID
     */
    String getId();

    /**
     * Executes the capability's action with the given argument.
     *
     * @param argument input parameter for the capability (e.g. file path, check parameters, etc.)
     * @return the result of execution
     */
    String execute(String argument);
}
