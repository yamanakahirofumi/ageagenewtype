package net.hero.genai.chat;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public final class ChatSession {
    private final ObservableList<Message> messages;
    private String selectedModel;

    public ChatSession() {
        this.messages = FXCollections.observableArrayList();
        this.selectedModel = "";
    }

    public ObservableList<Message> getMessages() {
        return this.messages;
    }

    public void addMessage(final Message message) {
        if (message != null) {
            this.messages.add(message);
        }
    }

    public void clear() {
        this.messages.clear();
    }

    public String getSelectedModel() {
        return this.selectedModel;
    }

    public void setSelectedModel(final String selectedModel) {
        this.selectedModel = selectedModel != null ? selectedModel : "";
    }
}
