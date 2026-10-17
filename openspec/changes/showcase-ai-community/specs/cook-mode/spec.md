# Delta: Cook Mode（跟做模式）

**Change ID:** `showcase-ai-community`

## ADDED

### Requirement: 步骤计时器自动生成且可并行
#### Scenario: 步骤含时长
- GIVEN 步骤文本为「大火蒸 8 分钟」
- WHEN 进入该步骤
- THEN 显示「8:00」计时器按钮；已开始的其他计时器继续运行；到点有声音与系统通知

### Requirement: 语音关键词免手操作
#### Scenario: 下一步
- GIVEN 跟做模式开启语音
- WHEN 用户说「下一步」
- THEN 切换到下一步骤并朗读步骤文字；其他话语转给 Agent，附带当前菜谱与步骤上下文

### Requirement: 跟做期间屏幕常亮，完成后进入做完确认
#### Scenario: 最后一步
- GIVEN 用户在最后一步点「做好了」
- THEN 打开做完确认（用料去向与余量回写）
