# Claude Code Token 用量统计

本地统计本仓库下 Claude Code 会话的 token 消耗，按阶段 / 工具 / 会话 / 日期拆分，用来判断哪些过程值得优化。

## 数据来源

`~/.claude/projects/<本项目编码目录>/` 下的 `.jsonl` 会话记录（递归包含子代理）。
只读取 assistant 消息里的 `usage` 和工具名，不读取、不保存对话内容。

## 用法

```bash
# 扫描并刷新报表（默认最近 14 天）
node scripts/token-usage.mjs

# 最近 7 天 / 全部历史
node scripts/token-usage.mjs --days 7
node scripts/token-usage.mjs --days 0

# 只按现有 ledger 重新出报表
node scripts/token-usage.mjs --report-only

# 按当前归类规则重建 ledger（改了归类规则后用）
node scripts/token-usage.mjs --rebuild --days 0
```

产物：

- `docs/token-usage/report.md`：可提交的汇总报表
- `docs/token-usage/ledger.jsonl`：每次 assistant API 调用一行（本地账本，默认 gitignore）
- `docs/token-usage/hook-error.log`：Stop hook 异常时写入（默认 gitignore）

## 自动更新（Stop hook）

`.claude/settings.json` 已配置 Stop hook：

```json
{
  "hooks": {
    "Stop": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "node \"$(git -C \"${CLAUDE_PROJECT_DIR:-$PWD}\" rev-parse --show-toplevel)/scripts/token-usage.mjs\" --hook",
            "timeout": 30
          }
        ]
      }
    ]
  }
}
```

每次 Claude Code 回复结束，hook 会读取当前会话的 `transcript_path`，增量追加到 ledger 并刷新报表。
`.claude/` 在 `.gitignore` 里，所以这个 hook 配置是本地配置，不会提交；换机器需要重新配置。

如果从 `menu-mini/`、`menu-admin/` 子目录启动 Claude Code，会话会落在另一个 `~/.claude/projects/...` 目录；脚本默认会扫描以本项目根目录编码开头的所有目录，所以仍能统计到。

## 归类规则

| 阶段 | 规则（优先级从高到低） |
|---|---|
| 部署 | Bash 命令含 `git push` / `gh workflow` / `gh run` / `ssh` / `docker compose` / `deploy.sh` |
| 测试 | Bash 命令含 `vitest` / `mvnw test` / `playwright` / `vue-tsc` / `flutter test` 等 |
| 构建 | Bash 命令含 `npm run build` / `mvnw package` 或 `install` / `vite build` 等 |
| 版本管理 | `git status/diff/log/commit/...`（`git push` 归部署） |
| 开发 | `Edit` / `Write` / `NotebookEdit` |
| 调研 | `Read` / `Grep` / `Glob` / `WebSearch` / `ToolSearch` / `Agent`，以及 `ls/grep/cat/sed/awk` 等只读 Bash |
| 验证 | `curl/wget/node/python/open/screencapture/osascript` 等 |
| 规划 | `TodoWrite` |
| 沟通决策 | `AskUserQuestion` |
| 对话决策 | 没有工具调用的纯文本回复 |
| 其他工具 | 其余工具 / 普通 Bash 命令 |

同一条 API 消息在 transcript 里可能拆成 thinking / text / tool_use 多行、usage 相同；脚本按 `sessionId + messageId` 合并，避免重复计算。

## 成本口径

原始 token 会被 `cache_read` 放大，报表同时给：

- **原始 token**：input + output + cache_write + cache_read
- **加权 token**：默认 `input=1 / output=5 / cache_write=1.25 / cache_read=0.1`

权重是相对值，不是美元。可在 `docs/token-usage/pricing.json` 覆盖：

```json
{ "input": 1, "output": 5, "cacheWrite": 1.25, "cacheRead": 0.1 }
```

## 已知限制

- 阶段归类是启发式的；同一条消息同时包含测试和编辑时按优先级归到测试。
- 子代理费用单独记录并计入；报表里没有单独拆出“子代理”行，可按 `sidechain` 字段扩展。
- 统计的是本机 `~/.claude/projects` 下的会话；换机器、清理会话或换项目目录会漏。
- Stop hook 每次回复后运行，会扫描当前会话文件；会话很大时可能有几百毫秒开销。
