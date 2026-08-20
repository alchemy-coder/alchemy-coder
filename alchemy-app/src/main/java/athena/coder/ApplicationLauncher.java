package athena.coder;

import atlantafx.base.theme.PrimerLight;
import athena.coder.ai.rag.RagManager;
import athena.coder.ai.spi.AiInfra;
import athena.coder.ai.spi.DefaultModelProvider;
import athena.coder.ai.spi.ModelConfigPort;
import athena.coder.ai.tool.ToolRegistry;
import athena.coder.app.ProjectManager;
import athena.coder.infra.DbManager;
import athena.coder.infra.repository.DbErrorLogSink;
import athena.coder.infra.repository.JdbiChatMemoryStore;
import athena.coder.infra.repository.AgentExecutionRepository;
import athena.coder.infra.repository.SqliteEmbeddingRepository;
import athena.coder.infra.repository.SqliteModelConfig;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import static athena.coder.entity.BasicConstants.MainUi.HEIGHT;
import static athena.coder.entity.BasicConstants.MainUi.WIDTH;
import static athena.coder.ui.MainUI.newMainUI;

public class ApplicationLauncher extends Application {

    public static Stage primaryStage;

    static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        ApplicationLauncher.primaryStage = primaryStage;
        // 组合根装配：向 ai 层注入 infra 端口实现（依赖反转，ai 不依赖 infra）
        ModelConfigPort modelConfig = new SqliteModelConfig();
        AiInfra.bind(new DbErrorLogSink(), new SqliteEmbeddingRepository(),
                JdbiChatMemoryStore::getInstance, ProjectManager::getProjectPath, new DefaultModelProvider(modelConfig),
                new AgentExecutionRepository());
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
//        primaryStage.setTitle(TITLE + " " + VERSION);
        primaryStage.setWidth(WIDTH);
        primaryStage.setHeight(HEIGHT);
        Scene scene = new Scene(newMainUI());
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() {
        // 先停索引线程，再关连接池，避免索引中写库失败
        RagManager.getInstance().shutdown();
        DbManager.shutdown();
        ToolRegistry.shutdownAll();
    }
}
