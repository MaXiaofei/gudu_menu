# 开发规范

> 配套 `docs/design/DESIGN.md`，明确"原型与代码同步"的具体操作。
> 核心原则见 [DESIGN.md §0](./design/DESIGN.md#0-设计资料层级原型为准)：**原型为准，代码为辅，双向同步**。

---

## 一、什么时候要同步原型

### 必须同步原型（改了代码 → 回写原型）

以下改动**必须**同步更新 44829 批次原型（`.superpowers/brainstorm/44829-1783002708/content/`）：

| 改动类型 | 示例 | 对应原型 |
|---|---|---|
| 页面布局/排版调整 | 菜品卡字段顺序变了 | 对应页面的 html |
| 新增/删除 UI 元素 | 加了个"收藏"按钮 | 对应页面的 html |
| 文案修改 | 按钮文字从"开始做"改成"做这顿饭" | 对应页面的 html |
| 交互流程变化 | 做菜流程从弹窗改成新页面 | 相关的多个 html |
| 新增页面 | 加了"营养报告"页 | 新建 html + 更新 README 文件清单 |

### 不需要同步原型

- 纯后端 API 改动（不改界面）
- Bug 修复（行为对齐原型，不改设计）
- 配置/依赖/构建改动
- 性能优化（不改界面）

### 原型驱动（改了原型 → 再改代码）

新功能/改版的标准流程：

```
1. 先改/新建 44829 原型 html
2. 确认设计 OK
3. 照原型实现 Flutter 代码
4. 提 PR（PR 模板勾"已同步原型"）
```

---

## 二、怎么同步原型

### 找到对应原型文件

参考 [44829 README 的文件清单](../.superpowers/brainstorm/44829-1783002708/README.md)，按页面找对应 html。

常见对应关系：

| Flutter 文件 | 原型文件 |
|---|---|
| `pages/dish/list_page.dart` | `cookbook-search.html` |
| `pages/menu/detail_page.dart` | `menu-detail-cai.html` / `-beicai` / `-caigou` / `-xietong` |
| `pages/pantry/list_page.dart` | `pantry-page.html` |
| `pages/dailylog/daily_log_page.dart` | `dailylog.html` |
| `pages/shopping/shopping_page.dart` | `menu-detail-caigou.html` + `shopping-restock.html` |

### 改原型时的规范

- **保持文案规范**：遵循 [DESIGN.md §2](./design/DESIGN.md)，不加 AI 味词、不加 emoji（功能符号 ✕✓ 除外）
- **图片位留占位**：遵循 [DESIGN.md §10](./design/DESIGN.md)，不用 emoji 顶替图片
- **改了文件清单要更新 README**：新增/删除 html 时，更新 44829 批次的 README 文件清单

---

## 三、同步检查

### 提 PR 前

PR 模板会提示勾选"是否已同步原型"。涉及界面改动必须勾。

### 手动跑检查脚本

```bash
# 对比 Flutter 代码和 44829 原型的修改时间，代码新于原型时提醒
bash scripts/check-prototype-sync.sh
```

输出示例：
```
⚠️ 以下代码文件比对应的原型更新，可能需要同步原型：

  menu-flutter/lib/pages/menu/detail_page.dart
    → 最后修改: 2026-08-04 14:30
    → 对应原型: menu-detail-cai.html (最后修改: 2026-08-04 10:00)
    → 建议: 检查界面改动是否已回写原型

共 1 处需确认。
```

---

## 四、冲突处理

原型和代码不一致时：

1. **以原型为准**（原型是设计意图的权威）
2. 除非原型本身有错 → 双方确认后同步修改
3. 暂时无法对齐的 → 在 PR 里说明原因，记录到 todo

---

## 五、调试与验证

### 默认调试方式：macOS 手机窗口

```bash
cd menu-flutter && flutter run -d macos
```

- macOS 窗口已固定为 **390×844 手机尺寸**（`macos/Runner/MainFlutterWindow.swift`），支持热重载，可直接截图看渲染效果（纯视觉样式如删除线/底色只有截图能确认）
- **不打包、不启动 Android 模拟器作为常规调试**——模拟器启动慢、易卡死，还要重新登录，验证成本远高于收益

### 何时才用 Android 模拟器

仅限 Android 特有行为：权限弹窗、applicationId/包名、通知/推送、剪贴板等。

```bash
cd menu-flutter && flutter build apk --debug
adb install -r build/app/outputs/flutter-apk/app-debug.apk
adb shell am start -n com.maxiaofei.menu_flutter/.MainActivity
```

### 纯接口/数据逻辑验证：curl 直查 staging，不启 UI

```bash
# 登录（注意：字段是 username 不是 phone；token 是裸值，不带 Bearer 前缀）
TOKEN=$(curl -s -X POST http://49.232.3.201:9090/gudu/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r .data.token)
curl -s "http://49.232.3.201:9090/gudu/menu/3/prep" -H "Authorization: $TOKEN"
```

### 提交前检查

- 后端：`mvn clean test-compile`（**必须 clean**，增量编译曾掩盖编译错误）+ 跑改动相关的单测
- 前端：`flutter analyze`（不新增 error；既有 info 不扩）
- 界面改动：按上面「三、同步检查」跑 `scripts/check-prototype-sync.sh`

---

## 附：快速判断流程图

```
改了代码
  │
  ├─ 涉及界面/交互/流程？
  │    ├─ 是 → 回写 44829 原型 → 提 PR（勾"已同步"）
  │    └─ 否 → 直接提 PR（勾"不涉及"）
  │
  └─ 跑 scripts/check-prototype-sync.sh 确认
```
