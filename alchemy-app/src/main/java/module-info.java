module alchemy_app {
    requires alchemy_ai;
    requires alchemy_infra;
    requires com.google.gson;
    requires javafx.graphics;
    requires javafx.controls;
    requires javafx.web;
    requires atlantafx.base;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.feather;
    requires org.jspecify;
    requires java.desktop;
    requires java.logging;
    requires langchain4j.core;

    // 导出包给 JavaFX 使用
    exports athena.coder to javafx.graphics;
    exports athena.coder.ui.modelselect to javafx.graphics;
}
