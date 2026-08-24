package net.hero.genai.supportai.capability;

import net.hero.genai.security.SecurityRule;
import net.hero.genai.security.SecurityService;

import java.util.List;

/**
 * Support AI capability to list configured security rules and their status.
 */
public final class SecurityRulesListCapability implements SupportAICapability {

    @Override
    public String getId() {
        return "security-rules-list";
    }

    @Override
    public String execute(final String argument) {
        final SecurityService service = SecurityService.getInstance();
        final List<SecurityRule> rules = service.getRules();
        final StringBuilder sb = new StringBuilder();
        sb.append("Security Enforcement: ").append(service.isEnabled() ? "ENABLED" : "DISABLED").append("\n");
        sb.append("Rules Count: ").append(rules.size()).append("\n\n");
        for (final SecurityRule rule : rules) {
            sb.append("[").append(rule.enabled() ? "ACTIVE" : "DISABLED").append("] ")
              .append(rule.category()).append(" | ")
              .append(rule.pattern()).append(" -> ")
              .append(rule.isDeny() ? "DENY" : "ALLOW").append("\n");
        }
        return sb.toString().trim();
    }
}
