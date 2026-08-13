package net.hero.genai.service;

/**
 * Interface representing a security checker that can evaluate permissions for specific actions within a category.
 */
public interface SecurityChecker {

    /**
     * Checks if this checker supports a given security category.
     *
     * @param category the category name (e.g., "file-access", "program-execution", "http-url")
     * @return true if this checker handles the given category, false otherwise
     */
    boolean supports(String category);

    /**
     * Evaluates permission for a specific category and action.
     *
     * @param category     the category (e.g., "file-access", "program-execution", "http-url")
     * @param action       the specific action path, command, or URL to evaluate
     * @param contextValue contextual parameters such as the active workspace directory absolute path
     * @return true if permitted, false if blocked
     */
    boolean checkPermission(String category, String action, String contextValue);
}
