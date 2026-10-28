package com.gudu.xsd.modules.backoffice.domain.model;

/** 运营后台可追溯的操作。 */
public enum OperationAction {
    BAN("封禁账号", OperationTarget.ACCOUNT),
    UNBAN("解封账号", OperationTarget.ACCOUNT),
    RESTORE_MEDIA("恢复违规图片", OperationTarget.MEDIA_CHECK),
    CONFIRM_MEDIA("确认图片违规", OperationTarget.MEDIA_CHECK),
    CONFIRM_TEXT("确认文本违规", OperationTarget.TEXT_VIOLATION),
    RETRY_DEACTIVATION("重试注销匿名化", OperationTarget.DEACTIVATION),
    VIEW_AGENT_FULL("查看咕嘟对话全文", OperationTarget.ACCOUNT),
    ADOPT_INGREDIENT("收编为公共食材", OperationTarget.INGREDIENT),
    MERGE_INGREDIENT("合并到公共食材", OperationTarget.INGREDIENT),
    ADD_INGREDIENT_ALIAS("新增食材别名", OperationTarget.INGREDIENT),
    DEL_INGREDIENT_ALIAS("删除食材别名", OperationTarget.INGREDIENT);

    private final String label;
    private final OperationTarget target;

    OperationAction(String label, OperationTarget target) {
        this.label = label;
        this.target = target;
    }

    public String label() {
        return label;
    }

    public OperationTarget target() {
        return target;
    }
}
