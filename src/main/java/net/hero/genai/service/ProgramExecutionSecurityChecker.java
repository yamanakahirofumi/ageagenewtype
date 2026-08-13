package net.hero.genai.service;

/**
 * Security checker specializing in "program-execution" verification.
 * Evaluates OS external commands and execution rules.
 * Extensible for advanced arguments/shell-injection parsing in the future.
 */
public final class ProgramExecutionSecurityChecker extends AbstractRuleSecurityChecker {

    public ProgramExecutionSecurityChecker() {
        super("program-execution");
    }

    @Override
    protected String preprocessAction(final String action, final String contextValue) {
        // Can add command-line argument scrubbing or path resolution for executables here
        return super.preprocessAction(action, contextValue);
    }
}
