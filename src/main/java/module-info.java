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
    exports net.hero.genai.chat;
    exports net.hero.genai.git;
    exports net.hero.genai.ollama;
    exports net.hero.genai.security;
    exports net.hero.genai.supportai;
    exports net.hero.genai.workflow;
    exports net.hero.genai.workspace;

    opens net.hero.genai to javafx.graphics, javafx.fxml;
    opens net.hero.genai.chat to javafx.fxml;
    opens net.hero.genai.git to javafx.fxml;
    opens net.hero.genai.ollama to javafx.fxml;
    opens net.hero.genai.security to javafx.fxml;
    opens net.hero.genai.supportai to javafx.fxml;
    opens net.hero.genai.workflow to javafx.fxml;
    opens net.hero.genai.workspace to javafx.fxml;
}
