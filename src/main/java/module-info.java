module net.hero.genai {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.logging;
    requires java.sql;
    requires java.net.http;
    requires org.slf4j;
    requires langchain4j;
    requires langchain4j.ollama;
    requires langchain4j.core;
    requires org.eclipse.jgit;

    exports net.hero.genai;
    exports net.hero.genai.model;
    exports net.hero.genai.service;
    exports net.hero.genai.controller;

    opens net.hero.genai to javafx.graphics, javafx.fxml;
    opens net.hero.genai.controller to javafx.fxml;
}
