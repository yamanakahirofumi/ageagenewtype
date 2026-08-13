package net.hero.genai.service;

import net.hero.genai.model.SecurityRule;
import java.util.List;

/**
 * Abstract base class for security checkers that evaluate rule lists using wildcard pattern matching.
 */
public abstract class AbstractRuleSecurityChecker implements SecurityChecker {

    private final String supportedCategory;

    protected AbstractRuleSecurityChecker(final String supportedCategory) {
        this.supportedCategory = supportedCategory;
    }

    @Override
    public boolean supports(final String category) {
        return this.supportedCategory.equalsIgnoreCase(category);
    }

    @Override
    public boolean checkPermission(final String category, final String action, final String contextValue) {
        if (!supports(category)) {
            return false;
        }

        final List<SecurityRule> rules = SecurityService.getInstance().getRules();
        final String processedAction = preprocessAction(action, contextValue);

        for (final SecurityRule rule : rules) {
            if (!rule.enabled()) {
                continue;
            }
            if (!rule.category().equalsIgnoreCase(category)) {
                continue;
            }

            final String resolvedPattern = resolvePattern(rule.pattern(), contextValue);

            if (SecurityService.matchesWildcard(resolvedPattern, processedAction)) {
                return !rule.isDeny();
            }
        }

        // Default Deny
        return false;
    }

    /**
     * Preprocesses the action string (e.g., path normalization, parameter isolation).
     *
     * @param action       the action string to evaluate
     * @param contextValue the context string
     * @return the preprocessed action string
     */
    protected String preprocessAction(final String action, final String contextValue) {
        return action;
    }

    /**
     * Resolves variables or placeholders (e.g. ${WORKSPACE_DIR}) in the rule pattern.
     *
     * @param pattern      the rule pattern to resolve
     * @param contextValue the context string
     * @return the resolved rule pattern
     */
    protected String resolvePattern(final String pattern, final String contextValue) {
        return pattern;
    }
}
