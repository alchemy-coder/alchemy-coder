package athena.coder.infra;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;

import java.io.File;
import java.util.List;

/**
 * SQLite 数据库管理：HikariCP 连接池 + 建表初始化。
 * <p>
 * SQLite 单写限制，连接池固定为 1。
 */
public class DbManager {

    private static final String DB_RELATIVE_PATH = "lumetix/lumetix.db";

    private static volatile Jdbi jdbi;
    private static volatile HikariDataSource dataSource;

    private DbManager() {
    }

    public static synchronized void init() {
        if (jdbi != null) {
            return;
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + ensureDbFileExists());
        config.setMaximumPoolSize(1);           // SQLite 单写限制
        config.setConnectionTestQuery("SELECT 1");

        HikariDataSource ds = new HikariDataSource(config);
        dataSource = ds;
        jdbi = Jdbi.create(ds);

        jdbi.useHandle(DbManager::createTables);
    }

    public static Jdbi getJdbi() {
        if (jdbi == null) {
            init();
        }
        return jdbi;
    }

    /**
     * 优雅关闭数据库连接池
     */
    public static void shutdown() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
            jdbi = null;
        }
    }

    // ==================== 建表 ====================

    private static void createTables(Handle handle) {
        createQuestTable(handle);
        createChatTables(handle);
        createModelTable(handle);
        createChatMemoryTable(handle);
        createErrorLogTable(handle);
        createRagTables(handle);
    }

    private static void createQuestTable(Handle handle) {
        handle.execute("""
                CREATE TABLE IF NOT EXISTS quest_list (
                    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
                    parent_id          INTEGER NOT NULL DEFAULT 0,
                    type               TEXT,
                    title              TEXT,
                    expand             TEXT,
                    create_at          TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
                    update_at          TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
                    deleted_at         TEXT
                );
                """);
    }

    private static void createChatTables(Handle handle) {
        handle.execute("""
                CREATE TABLE IF NOT EXISTS chat_detail (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    chat_id    INTEGER,
                    type       TEXT,
                    content    TEXT,
                    uuid    TEXT,
                    create_at  TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
                    update_at  TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
                    deleted_at TEXT
                );
                """);
        handle.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_chatid_uuid_type
                ON chat_detail(chat_id, type,uuid)
                WHERE  deleted_at IS NULL;
                """);
    }

    private static void createModelTable(Handle handle) {
        // 旧表无 type 列则补列（区分语言大模型/向量大模型）；model 表存 api_key，只能增量补列不可重建
        List<String> columns = handle.createQuery("PRAGMA table_info(model)")
                .map((rs, ctx) -> rs.getString("name"))
                .list();
        if (!columns.isEmpty() && !columns.contains("type")) {
            handle.execute("ALTER TABLE model ADD COLUMN type TEXT NOT NULL DEFAULT 'chat'");
            // 历史数据回填：唯一向量模型按 name+version 改为 embedding（对应 EmbeddingModelEnum.QIANWEN_EMBEDDING_V4），其余默认 chat
            handle.execute("UPDATE model SET type = 'embedding' WHERE name = 'qianwen' AND version = 'text-embedding-v4'");
        }
        handle.execute("""
                CREATE TABLE IF NOT EXISTS model (
                      id          INTEGER PRIMARY KEY AUTOINCREMENT,
                      type        TEXT    NOT NULL,
                      name        TEXT    NOT NULL,
                      version     TEXT    NOT NULL,
                      api_key     TEXT    NOT NULL,
                      create_at   TEXT,
                      update_at   TEXT,
                      deleted_at  TEXT
                  );
                """);
    }

    /** 聊天记忆表（每条消息一行记录 + 软删除） */
    private static void createChatMemoryTable(Handle handle) {
        handle.execute("""
                CREATE TABLE IF NOT EXISTS chat_memory (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    memory_id       TEXT NOT NULL,
                    agent_type      TEXT NOT NULL,
                    message_content TEXT NOT NULL,
                    is_deleted      INTEGER NOT NULL DEFAULT 0,
                    create_at       TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
                    update_at       TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
                );
                """);
        handle.execute("""
                CREATE INDEX IF NOT EXISTS idx_chat_memory_memory_id ON chat_memory(memory_id);
                """);
    }

    /** 错误日志表 */
    private static void createErrorLogTable(Handle handle) {
        handle.execute("""
                CREATE TABLE IF NOT EXISTS error_log (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    create_at     TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
                    source        TEXT NOT NULL,
                    error_type    TEXT NOT NULL,
                    error_message TEXT NOT NULL,
                    task_id       INTEGER,
                    agent_type    TEXT,
                    user_request  TEXT,
                    stack_trace   TEXT,
                    severity      TEXT NOT NULL DEFAULT 'ERROR'
                );
                """);
        handle.execute("""
                CREATE INDEX IF NOT EXISTS idx_error_log_create_at ON error_log(create_at);
                """);
    }

    /** RAG 向量存储：代码 chunk 向量 + 增量索引文件指纹 */
    private static void createRagTables(Handle handle) {
        handle.execute("""
                CREATE TABLE IF NOT EXISTS rag_embedding (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    project_key TEXT NOT NULL,
                    model       TEXT NOT NULL,
                    file_path   TEXT NOT NULL,
                    content     TEXT NOT NULL,
                    vector      BLOB NOT NULL,
                    create_at   TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
                    update_at   TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
                );
                """);
        handle.execute("""
                CREATE INDEX IF NOT EXISTS idx_rag_project_model ON rag_embedding(project_key, model);
                """);
        handle.execute("""
                CREATE INDEX IF NOT EXISTS idx_rag_file ON rag_embedding(project_key, file_path);
                """);
        // FTS5 关键词倒排索引（混合检索的关键词路）：主表删除时同步物理删
        handle.execute("""
                CREATE VIRTUAL TABLE IF NOT EXISTS rag_embedding_fts USING fts5(
                    project_key UNINDEXED,
                    model       UNINDEXED,
                    chunk_id    UNINDEXED,
                    file_path,
                    content
                );
                """);
        // 指纹表结构升级：已存在的旧表无 model 列则丢弃重建（仅丢缓存，下轮索引全量重建）
        List<String> columns = handle.createQuery("PRAGMA table_info(rag_file_snapshot)")
                .map((rs, ctx) -> rs.getString("name"))
                .list();
        if (!columns.isEmpty() && !columns.contains("model")) {
            handle.execute("DROP TABLE rag_file_snapshot");
        }
        handle.execute("""
                CREATE TABLE IF NOT EXISTS rag_file_snapshot (
                    project_key TEXT NOT NULL,
                    model       TEXT NOT NULL,
                    file_path   TEXT NOT NULL,
                    mtime       INTEGER NOT NULL,
                    size        INTEGER NOT NULL,
                    PRIMARY KEY (project_key, model, file_path)
                );
                """);
    }

    private static String ensureDbFileExists() {
        File dbFile = new File(System.getProperty("user.home"), DB_RELATIVE_PATH);
        File parentDir = dbFile.getParentFile();

        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            throw new RuntimeException("无法创建数据库父目录: " + parentDir.getAbsolutePath());
        }
        if (!dbFile.exists()) {
            try {
                dbFile.createNewFile();
            } catch (Exception e) {
                throw new RuntimeException("创建数据库文件错误: " + dbFile.getAbsolutePath(), e);
            }
        }
        return dbFile.getAbsolutePath();
    }
}