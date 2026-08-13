package net.hero.genai.service;

/**
 * Security checker specializing in "http-url" verification.
 * Evaluates HTTP and HTTPS connection rules.
 * Extensible for advanced domain/SSRF mitigation in the future.
 */
public final class HttpUrlSecurityChecker extends AbstractRuleSecurityChecker {

    public HttpUrlSecurityChecker() {
        super("http-url");
    }

    @Override
    protected String preprocessAction(final String action, final String contextValue) {
        // Can add domain parsing, port restrictions, or protocol check here
        return super.preprocessAction(action, contextValue);
    }
}
