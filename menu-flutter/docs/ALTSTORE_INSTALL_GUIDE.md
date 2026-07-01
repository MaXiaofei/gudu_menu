# 咕嘟小食单 · iOS 免费安装保姆级教程（AltStore 方案）

> 目标：把 App 装到你和家人的 iPhone 上，**免费、不用 $99、自动续签**。
> 适合：第一次搞 iOS 侧载的人。每一步都写了「点哪里、看到什么」。
> 预计用时：第一次约 30~60 分钟（装 AltStore + 出 IPA + 装）。

---

## 整体原理（先理解，少踩坑）

```
你的 Mac 编译出 App(.ipa)
        ↓
AltStore 用【你的免费 Apple ID】给 ipa 重新签名
        ↓
装到 iPhone（有效期 7 天）
        ↓
Mac 开着 AltServer + 同 WiFi → 每 7 天自动续签，你无感
```

关键认知：
- **必须有 Mac**（AltServer 跑在 Mac 上做自动续签）
- **用你自己的免费 Apple ID**（不用花钱，不用开发者账号）
- **每台 iPhone 用各自的 Apple ID 签名**（家人用家人的）
- 签名 7 天过期，但 AltStore 自动续，**不用你插线、不用记日期**

---

## 你需要准备

| 东西 | 说明 |
|---|---|
| Mac 电脑 | 已有 ✅ |
| 一个免费 Apple ID | 没有就去 appleid.apple.com 注册 |
| iPhone（你的） | 要装的手机 |
| 数据线 | 第一次连 Mac 装东西用 |
| 这个项目工程 | `/Users/maxiaofei/mygithub/menu-new/menu-flutter` |

> 免费 Apple ID 的限制：每台手机最多 3 个这种 App、每 7 天续签、不能上架。够用。

---

## 第 1 步：在 Xcode 登录你的 Apple ID（一次性）

1. 打开 Xcode（启动台找，或 `open /Applications/Xcode.app`）
2. 顶部菜单 **Xcode → Settings...**（或 `⌘ + ,`）
3. 点顶部 **Accounts** 标签
4. 点左下角 **`+`** 按钮
5. 选 **Apple ID**
6. 输入你的 Apple ID 账号密码登录
7. 登录成功后，右侧会出现一个 **Personal Team**（写着你的名字 + "Personal Team"）

✅ **看到 Personal Team 就成功了**。这是你的免费签名身份。

---

## 第 2 步：打开工程，配置签名（一次性）

1. 终端运行（打开工程）：
   ```bash
   open /Users/maxiaofei/mygithub/menu-new/menu-flutter/ios/Runner.xcworkspace
   ```
   > ⚠️ 一定要打开 **Runner.xcworkspace**（白色图标），不是 Runner.xcodeproj（蓝色）。前者带依赖。

2. 左侧文件列表，点最顶上的 **Runner**（蓝色项目图标）

3. 中间区域，确保顶部 tab 在 **Signing & Capabilities**

4. 会看到 **"Automatically manage signing"** —— 勾上它（默认应该已勾）

5. **Team** 下拉框 → 选你刚登录的 **Personal Team**（你的名字）

6. **Bundle Identifier** 应该已经是 `com.maxiaofei.menuFlutter`
   - 如果报红说被占用，改成 `com.maxiaofei.gudu` 或加个后缀 `com.maxiaofei.menuFlutter2`

7. 看到 **"Signing Certificate"** 显示 `Apple Development: 你的邮箱` 就对了

8. 左上角 `⌘ + S` 保存

> 如果弹窗要"添加证书到钥匙串"，输 Mac 开机密码同意即可。

---

## 第 3 步：把构建目标切到「真机」

1. Xcode 顶部中间有个设备选择下拉（可能显示 "iPhone 17 Pro"）
2. 点开，**往上滚**，在列表顶部找 **`Any iOS Device (arm64)`**，选它
   - ⚠️ **不能选模拟器**（带"simulator"字的），Archive 不支持模拟器
3. 选完后，顶部显示 `Any iOS Device (arm64)`

> 这一步是为了能 Archive（打包）。模拟器只能 Run，不能 Archive。

---

## 第 4 步：Archive 打包出 IPA（核心步骤）

1. Xcode 顶部菜单 **Product → Archive**（或快捷键没有默认，走菜单）
2. 等它编译（几分钟，下面有进度条）
3. 编译完会自动弹出 **Organizer 窗口**，左边列表多了一条你的 App（带时间戳）
4. 右边大区域右侧有几个按钮，点 **Distribute App**

5. 弹窗选择：
   - 选 **Development** → Next
   - **Select a team** 选你的 Personal Team → Next
   - "App Thinning"：保持默认 `All compatible device variants` → Next
   - "Review" 页面看一眼，**没有红字报错**就 **Distribute**
   - 最后一步问你 **保存位置**：选桌面或某文件夹，保存

6. 保存完，文件夹里有个 **`.ipa`** 文件 —— **这就是要装到手机的包！** 记住它的位置。

> 如果 Distribute 报错"X couldn't be installed"，多半是 Bundle ID 冲突或签名问题，回到第 2 步检查 Team 选了没。

---

## 第 5 步：在 Mac 装 AltServer

1. 终端运行：
   ```bash
   brew install --cask alt-server
   ```
   等装完。

2. 启动 AltServer（可能要去 `系统设置 → 隐私与安全性` 同意运行，因为是第三方）：
   ```bash
   open -a AltServer
   ```
   > 启动后，Mac 顶部菜单栏（右上角）会出现一个**菱形/AltServer 图标**。

3. 点 AltServer 图标 → **Allow Access to iTunes / iCloud**（让 AltServer 能用你的 Apple ID 续签）
   - 会弹"钥匙串"要 Mac 密码，同意

✅ AltServer 在 Mac 上跑起来了（菜单栏有图标）。**以后这个要常开**（开机自启最好）。

---

## 第 6 步：iPhone 装 AltStore（第一次要插线）

1. 用**数据线**把 iPhone 连到 Mac
2. iPhone 上弹「**信任此电脑**」→ 输锁屏密码同意
3. 点 Mac 菜单栏的 **AltServer 图标** → **Install AltStore** → 选你的 iPhone
4. 输入你的 **Apple ID** 账号密码（AltStore 用它签名，和第 1 步同一个）
5. iPhone 上安装完成后，**设置 → 通用 → VPN与设备管理** → 找到「开发者 App」/ 你的 Apple ID → **信任**

✅ iPhone 桌面多了一个 **AltStore** 图标（蓝色菱形）。

> 第一次装完就可以拔线了。以后靠 WiFi 续签。

---

## 第 7 步：把你的 IPA 装进 AltStore

1. 把第 4 步导出的 **.ipa** 传到 iPhone：
   - 最简单：**AirDrop**（Mac 右键 ipa → 共享 → AirDrop → 选你的 iPhone）
   - 或传到 iCloud Drive / 微信文件助手

2. iPhone 打开 **AltStore** App

3. AltStore 左上角点 **`+`** 或 **My Apps** → 找到传过来的 ipa → 点它

4. AltStore 会用你的 Apple ID 自动签名 + 安装（等一会）

5. ✅ iPhone 桌面出现 **咕嘟小食单** 图标，点开能用！

> 第一次打开如果闪退提示「不受信任的开发者」，去 `设置 → 通用 → VPN与设备管理` 再信任一次。

---

## 第 8 步：配置自动续签（一劳永逸）

这是 AltStore 的核心价值——**7 天到期自动续，不用你管**：

1. **Mac 的 AltServer 保持常开**（开机自启：系统设置 → 通用 → 登录项，把 AltServer 加进去）
2. **iPhone 连同一个 WiFi**（和 Mac 同网段）
3. iPhone 的 **设置 → AltStore** 里，确保 **Background Refresh / Wireless syncing** 开着
4. 以后 App 快到期时，Mac 的 AltServer 在后台自动续签

> 续签条件：Mac 开着、AltServer 开着、同 WiFi。只要满足，App 永不过期。

---

## 家人的 iPhone 怎么装

每台家人手机：

1. 让家人**注册自己的免费 Apple ID**（如果没有）
2. 家人手机**插你的 Mac 一次**（像第 6 步装 AltStore），但签名时用**家人的 Apple ID**
3. 你把 ipa **AirDrop 给家人**
4. 家人手机 AltStore 打开 ipa → 用家人自己的 Apple ID 装
5. 之后家人手机的续签要靠**家人的 Apple ID** + Mac AltServer

> 限制：一台 Mac 的 AltServer 默认管几个 Apple ID 没问题，但每个 Apple ID 每台手机独立续签。家人要常来你这儿（同 WiFi）才会续签——或者让他们自己装 [SideStore](https://sidestore.io)（手机自己续签，不靠 Mac）。

---

## 常见问题

**Q: Archive 报错 "No profiles for 'com.maxiaofei.menuFlutter' were found"**
A: 第 2 步 Team 没选，或没勾 Automatically manage signing。回去选 Team。

**Q: AltStore 安装时提示 "An error occurred. The operation couldn't be completed"**
A: 多半是 Apple ID 每周签名次数到上限（免费账号限制），等明天再试。

**Q: App 打不开，闪退**
A: 证书过期或没信任。去 `设置 → 通用 → VPN与设备管理` 信任；如果过期，打开 AltStore 刷新会自动续。

**Q: 数据线拔了就不行？**
A: 只有第一次装 AltStore 要线。装完之后，自动续签靠 WiFi，不用线。

**Q: 能不能不插线装？**
A: 装好 AltStore 之后，以后传 ipa（AirDrop）+ 续签都靠 WiFi，不用线。

---

## 一句话流程速查

```
Xcode 登录 Apple ID
  → 打开工程选 Personal Team
  → 切到 Any iOS Device
  → Product → Archive → 导出 ipa
  → brew 装 AltServer 并启动
  → 插线给 iPhone 装 AltStore
  → AirDrop ipa 到手机 → AltStore 安装
  → AltServer 常开，自动续签
```

有问题随时贴报错给我。
