package net.hero.genai.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public final class OllamaConfig {
    private final StringProperty apiBaseUrl;
    private final BooleanProperty connected;
    private final ObservableList<String> availableModels;

    public OllamaConfig() {
        this.apiBaseUrl = new SimpleStringProperty("http://localhost:11434");
        this.connected = new SimpleBooleanProperty(false);
        this.availableModels = FXCollections.observableArrayList();
    }

    public String getApiBaseUrl() {
        return apiBaseUrl.get();
    }

    public void setApiBaseUrl(final String url) {
        this.apiBaseUrl.set(url);
    }

    public StringProperty apiBaseUrlProperty() {
        return apiBaseUrl;
    }

    public boolean isConnected() {
        return connected.get();
    }

    public void setConnected(final boolean isConnected) {
        this.connected.set(isConnected);
    }

    public BooleanProperty connectedProperty() {
        return connected;
    }

    public ObservableList<String> getAvailableModels() {
        return availableModels;
    }

    public void setAvailableModels(final ObservableList<String> models) {
        this.availableModels.setAll(models);
    }
}
