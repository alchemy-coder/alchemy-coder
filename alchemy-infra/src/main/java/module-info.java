module alchemy_infra {
    requires alchemy_ai;
    requires langchain4j.core;
    requires org.jdbi.v3.core;
    requires com.zaxxer.hikari;
    requires java.sql;
    requires java.logging;

    opens athena.coder.entity.tree;
    opens athena.coder.infra.entity.chat;

    exports athena.coder.infra;
    exports athena.coder.infra.repository;
    exports athena.coder.entity.tree;
    exports athena.coder.infra.entity.chat;
}