package athena.coder.ai.tool.exception;

public enum ErrorCode {
    PARAM_MISSING(4001, "缺少必要参数: %s"),
    PARAM_INVALID(4002, "参数无效: %s"),
    PATH_INVALID(4003, "路径无效: %s"),
    FILE_NOT_FOUND(4004, "文件不存在: %s"),
    FILE_TOO_LARGE(4005, "文件过大 (%d KB)，超过限制 %d KB"),
    NOT_FILE(4006, "不是有效文件: %s"),
    NOT_DIRECTORY(4007, "不是目录: %s"),
    DIRECTORY_NOT_EMPTY(4008, "目录不为空，无法删除: %s"),

    COMMAND_FAILED(5001, "命令执行失败: %s"),
    TIMEOUT(5002, "执行超时 (%ds)"),
    PROCESS_ERROR(5003, "进程异常: %s"),
    PARSE_ERROR(5004, "解析错误: %s"),

    PATH_TRAVERSAL(6001, "检测到路径遍历攻击: %s"),
    DANGEROUS_COMMAND(6002, "禁止执行危险命令: %s"),
    COMMAND_BLOCKED(6003, "禁止执行的命令/参数: %s"),
    QUOTA_EXCEEDED(6004, "超出资源配额"),
    RATE_LIMIT_EXCEEDED(6005, "超出调用频率限制 (%d次/分钟)"),
    BINARY_FILE(6006, "不支持读取二进制文件: %s"),

    CONFIG_ERROR(7001, "配置错误: %s"),
    UNSUPPORTED_PROJECT_TYPE(7002, "不支持的项目类型: %s"),
    UNSUPPORTED_TYPE(7003, "不支持的类型: %s"),
    INVALID_FORMAT(7004, "格式无效: %s"),
    INTERNAL_ERROR(7999, "内部错误: %s"),

    FILE_READ_ERROR(7005, "文件读取错误: %s"),
    FILE_WRITE_ERROR(7006, "文件写入错误: %s"),
    FILE_ACCESS_ERROR(7007, "文件访问错误: %s"),
    FILE_DELETE_ERROR(7008, "文件删除错误: %s"),
    FILE_COPY_ERROR(7009, "文件复制错误: %s -> %s"),
    FILE_MOVE_ERROR(7010, "文件移动错误: %s -> %s"),
    FILE_LIST_ERROR(7011, "目录列表错误: %s");


    private final int code;
    private final String messageTemplate;

    ErrorCode(int code, String messageTemplate) {
        this.code = code;
        this.messageTemplate = messageTemplate;
    }

    public int getCode() {
        return code;
    }

    public String getMessageTemplate() {
        return messageTemplate;
    }
}