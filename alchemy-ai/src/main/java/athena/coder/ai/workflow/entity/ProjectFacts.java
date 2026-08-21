package athena.coder.ai.workflow.entity;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 规划阶段产出的项目探索事实（结构化、单一写者 PLANNER，下游多读者只读）。
 * <p>
 * 解决多智能体重复探索：PLANNER 探索一次，把关键文件/符号/依赖沉淀为事实，
 * 下游 CODER/TESTER/ANALYST/REVIEWER/REPORTER 直接采信，缺失时才自行探测。
 * <p>
 * 防混乱机制：只共享结构化事实、不共享对话/推理；单一写者避免多方污染。
 */
public record ProjectFacts(
        String overview,
        List<ModuleFact> modules,
        List<FileFact> files,
        List<String> dependencies,
        List<String> gotchas) {

    /** 渲染时关键文件数量上限，防止 planner 堆砌无关路径撑爆下游上下文 */
    private static final int MAX_FILES = 30;
    /** 渲染时每个文件关键符号上限 */
    private static final int MAX_SYMBOLS = 3;

    public record ModuleFact(String name, String path, String role) {
    }

    public record FileFact(String path, String role, List<String> keySymbols) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 从 JSON 字符串解析；空/非法返回 null（调用方降级为自行探索）
     */
    public static ProjectFacts fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, ProjectFacts.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 渲染成注入下游 prompt 的紧凑 markdown；空/非法返回 ""
     */
    public static String toPromptBlock(String json) {
        ProjectFacts facts = fromJson(json);
        return facts == null ? "" : facts.render();
    }

    private String render() {
        StringBuilder sb = new StringBuilder();
        appendOverview(sb);
        appendModules(sb);
        appendFiles(sb);
        appendDependencies(sb);
        appendGotchas(sb);
        return sb.toString();
    }

    private void appendOverview(StringBuilder sb) {
        if (overview != null && !overview.isBlank()) {
            sb.append("概览：").append(overview.trim()).append('\n');
        }
    }

    private void appendModules(StringBuilder sb) {
        if (modules == null || modules.isEmpty()) {
            return;
        }
        sb.append("模块：");
        for (int i = 0; i < modules.size(); i++) {
            ModuleFact m = modules.get(i);
            if (i > 0) {
                sb.append(" | ");
            }
            sb.append(m.name());
            if (m.path() != null && !m.path().isBlank()) {
                sb.append("（").append(m.path()).append("）");
            }
            if (m.role() != null && !m.role().isBlank()) {
                sb.append(": ").append(m.role());
            }
        }
        sb.append('\n');
    }

    private void appendFiles(StringBuilder sb) {
        if (files == null || files.isEmpty()) {
            return;
        }
        sb.append("关键文件：\n");
        List<FileFact> shown = files.size() > MAX_FILES ? files.subList(0, MAX_FILES) : files;
        for (FileFact f : shown) {
            sb.append("- `").append(f.path()).append('`');
            if (f.role() != null && !f.role().isBlank()) {
                sb.append("（").append(f.role()).append("）");
            }
            if (f.keySymbols() != null && !f.keySymbols().isEmpty()) {
                List<String> symbols = f.keySymbols().size() > MAX_SYMBOLS
                        ? f.keySymbols().subList(0, MAX_SYMBOLS)
                        : f.keySymbols();
                sb.append(" — ").append(String.join(", ", symbols));
            }
            sb.append('\n');
        }
        if (files.size() > MAX_FILES) {
            sb.append("- …等共 ").append(files.size()).append(" 个文件\n");
        }
    }

    private void appendDependencies(StringBuilder sb) {
        if (dependencies == null || dependencies.isEmpty()) {
            return;
        }
        sb.append("依赖关系：\n");
        for (String d : dependencies) {
            sb.append("- ").append(d).append('\n');
        }
    }

    private void appendGotchas(StringBuilder sb) {
        if (gotchas == null || gotchas.isEmpty()) {
            return;
        }
        sb.append("注意点：\n");
        for (String g : gotchas) {
            sb.append("- ").append(g).append('\n');
        }
    }
}
