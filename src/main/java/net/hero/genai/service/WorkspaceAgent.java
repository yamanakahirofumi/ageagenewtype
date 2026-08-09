package net.hero.genai.service;

import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

public interface WorkspaceAgent {

    @UserMessage("{{it}}")
    TokenStream chatStream(final String message);

    @UserMessage("{{it}}")
    String chat(final String message);
}
