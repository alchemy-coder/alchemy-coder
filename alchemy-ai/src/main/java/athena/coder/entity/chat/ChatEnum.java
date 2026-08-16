package athena.coder.entity.chat;

public enum ChatEnum {
    USER,              // 用户消息 — 蓝色气泡右对齐
    ROBOT,             // [兼容旧代码] 普通机器人消息，默认等同于 ROBOT_RESULT
    ROBOT_PROGRESS,    // 进度步骤 — 灰底小字，同uuid折叠
    ROBOT_RESULT,      // 关键结果 — 彩色左边框 + 状态徽章
    ROBOT_REPORT,      // 最终报告 — 摘要条 + 结构化Markdown
    ROBOT_ERROR,       // 异常错误 — 红色突出
    ROBOT_CONFIRM      // 人工确认卡片 — 醒目样式，等待用户确认或提出修改意见
}
