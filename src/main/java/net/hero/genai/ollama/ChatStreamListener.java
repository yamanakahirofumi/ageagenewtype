package net.hero.genai.ollama;

public interface ChatStreamListener {
    void onNext(String token);
    void onComplete(String fullResponse);
    void onError(Throwable error);
}
