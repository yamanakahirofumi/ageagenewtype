package net.hero.genai.supportai;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service orchestrating registered capabilities available to the Support AI.
 */
public final class SupportAiService {

    private static final Logger LOGGER = Logger.getLogger(SupportAiService.class.getName());
    private static final SupportAiService INSTANCE = new SupportAiService();

    public static SupportAiService getInstance() {
        return INSTANCE;
    }

    private final Map<String, SupportAICapability> capabilities = new ConcurrentHashMap<>();

    private SupportAiService() {
        // Register default capabilities
        registerCapability(new SecurityCheckCapability());
        registerCapability(new WorkflowListCapability());
        registerCapability(new FileLookupCapability());

        // Register 10 new standard capabilities
        registerCapability(new GitStatusCapability());
        registerCapability(new GitLogCapability());
        registerCapability(new FileReadCapability());
        registerCapability(new DirectoryListCapability());
        registerCapability(new WorkspaceInfoCapability());
        registerCapability(new OllamaStatusCapability());
        registerCapability(new SecurityRulesListCapability());
        registerCapability(new FileSearchCapability());
        registerCapability(new SystemInfoCapability());
        registerCapability(new DateTimeNowCapability());
    }

    /**
     * Registers a capability.
     *
     * @param capability the capability to register
     */
    public void registerCapability(final SupportAICapability capability) {
        if (capability != null) {
            capabilities.put(capability.getId(), capability);
            LOGGER.log(Level.INFO, "Registered Support AI capability: " + capability.getId());
        }
    }

    /**
     * Unregisters a capability by ID.
     *
     * @param id the unique capability ID
     */
    public void unregisterCapability(final String id) {
        if (id != null) {
            capabilities.remove(id);
            LOGGER.log(Level.INFO, "Unregistered Support AI capability: " + id);
        }
    }

    /**
     * Gets a registered capability by ID.
     *
     * @param id the capability ID
     * @return the capability, or null if not found
     */
    public SupportAICapability getCapability(final String id) {
        return capabilities.get(id);
    }

    /**
     * Gets all registered capabilities.
     *
     * @return a list of all registered capabilities
     */
    public List<SupportAICapability> getRegisteredCapabilities() {
        return List.copyOf(capabilities.values());
    }

    /**
     * High-level entry point (口) for the Support AI to invoke a registered capability by ID.
     *
     * @param capabilityId the ID of the capability to invoke (e.g. "security-check", "list-workflows", "file-lookup")
     * @param argument     the argument to pass to the capability
     * @return the result of execution, or an error message if not found
     */
    public String invoke(final String capabilityId, final String argument) {
        final SupportAICapability capability = capabilities.get(capabilityId);
        if (capability == null) {
            LOGGER.log(Level.WARNING, "Capability not registered: " + capabilityId);
            return "Error: Capability '" + capabilityId + "' is not registered.";
        }
        try {
            LOGGER.log(Level.INFO, "Invoking Support AI capability: " + capabilityId + " with argument: " + argument);
            return capability.execute(argument);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing capability '" + capabilityId + "': " + e.getMessage(), e);
            return "Error executing capability '" + capabilityId + "': " + e.getMessage();
        }
    }
}
