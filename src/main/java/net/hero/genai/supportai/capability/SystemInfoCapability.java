package net.hero.genai.supportai.capability;

/**
 * Support AI capability to retrieve system and runtime environment metadata.
 */
public final class SystemInfoCapability implements SupportAICapability {

    @Override
    public String getId() {
        return "system-info";
    }

    @Override
    public String execute(final String argument) {
        final Runtime runtime = Runtime.getRuntime();
        final long maxMemoryMb = runtime.maxMemory() / (1024 * 1024);
        final long totalMemoryMb = runtime.totalMemory() / (1024 * 1024);
        final long freeMemoryMb = runtime.freeMemory() / (1024 * 1024);
        final long usedMemoryMb = totalMemoryMb - freeMemoryMb;

        final StringBuilder sb = new StringBuilder();
        sb.append("OS: ").append(System.getProperty("os.name")).append(" ")
          .append(System.getProperty("os.version")).append(" (").append(System.getProperty("os.arch")).append(")\n");
        sb.append("Java Version: ").append(System.getProperty("java.version")).append(" (").append(System.getProperty("java.vendor")).append(")\n");
        sb.append("Available Processors: ").append(runtime.availableProcessors()).append("\n");
        sb.append("Memory (Used / Total / Max): ").append(usedMemoryMb).append("MB / ")
          .append(totalMemoryMb).append("MB / ").append(maxMemoryMb).append("MB");

        return sb.toString();
    }
}
