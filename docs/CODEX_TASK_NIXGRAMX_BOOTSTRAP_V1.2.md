# NixgramX 第一阶段总任务书
## CODEX_TASK_NIXGRAMX_BOOTSTRAP_V1.2.md

> 相对 V1.1 的额外锁定：显示名 **NixgramX**；完整版包名 `app.nixgramx.android`；第一阶段必须提供 `_base`，实现方式优先照搬 NagramX 现有 `_base`，不要自造第二套功能裁剪。
>
> 相对 V1 的核心修订：更正 NagramX 基线与 Telegram 上游判定方式；第一天切开包名/签名/FCM/更新源；把「自动跟进」拆成 Watch → Assist → 人工适配 → Gate；功能清单改为源码导出验收；CI 与真机验收分开勾选。

> 项目目标：以 NagramX 最终完整版为基础，建立一个长期维护的 NixgramX。
>
> **第一优先级：持续、快速、可靠地跟进 Telegram Android 官方最新上游。**
>
> **第二优先级：修复 NagramX 最终版本中已经存在的 Bug、崩溃、兼容性问题和体验问题，把稳定性做到尽可能高。**
>
> 第一阶段不以增加大量全新功能为目标，不为了“代码更漂亮”进行没有必要的大规模重构。

---

# 0. 相对 V1 的变更摘要

必须按本节执行，不要再使用 V1 中已过时的硬条件。

1. NagramX 功能基线从 `12.8.1.1254` 更正为 **`12.9.2.1260` (`4335a2e`)**，并强制审计归档前 `12.10.0-a6c7d0a`（1261）。
2. Telegram 上游真相源改为 `DrKLO/Telegram` **master 上的 `update to x.y.z (build)` commit**，禁止把该仓库 GitHub Releases 页（可能长期停在旧 tag）当成最新版本。
3. 第一天必须切开身份：`applicationId`、签名、FCM、Maps、更新源、remote-config。不允许只改显示名。
4. Upstream 自动化降级为「发现 + 开分支 + 报告」；禁止自动把未解决冲突或未过门禁的同步合进 `main` / Stable。
5. 「功能未丢失」改为以 `NaConfig` / 设置项源码导出清单验收，不以散文枚举为准。
6. 验收拆成 **CI 可证明** 与 **真机才能证明**。凭证缺失标 `blocked`，不得写成已完成。
7. 明确完整版与 `_base` 必须同时交付；ToS / 账号风险、用户从 NagramX 迁移方式必须写进文档。
8. 后续 fork 只允许摘 bugfix，默认不移植新功能。
9. 所有者已确认：第一阶段必须同时交付完整版与 `_base`。
10. 所有者已确认：包名使用 `app.nixgramx.android`（完整版）与 `app.nixgramx.android.base`（`_base`）。

---

# 1. 项目名称与身份

正式项目名称：

**NixgramX**

## 1.1 显示与仓库

当前第一阶段：

- App 显示名称改为 `NixgramX`
- GitHub 项目统一使用 `NixgramX`
- 第一阶段图标暂时沿用 NagramX 当前图标
- 暂时不要重新设计图标
- 后续单独进行品牌图标设计

如果当前仓库仍使用旧项目名 `Rigram`，在权限允许的情况下应统一迁移/改名到 `NixgramX`；如果仓库级改名当前无法执行，则先完成源码、文档和 App 内名称迁移，并在报告中注明。

## 1.2 身份隔离（第一天必须完成，不得推迟）

NixgramX 是独立应用，不是 NagramX 的覆盖更新包。

必须使用 NixgramX 自己的：

- `applicationId`（禁止继续使用 `nu.gpu.nagram` / `nu.gpu.nagramx`）
- release / debug signing keystore
- `TELEGRAM_APP_ID` / `TELEGRAM_APP_HASH`
- `TMessagesProj/google-services.json`（FCM）
- Google Maps API key
- in-app 更新源（GitHub Release / 自有频道）
- remote-config / helper bot / `BaseRemoteHelper.CHANNEL_METADATA_ID` 一类远程入口

禁止：

- 复用 NagramX 官方签名证书
- 复用 NagramX 更新通道去下载别人的 APK
- 复用原作者 remote-config / 频道 metadata，导致配置仍被外部仓库控制
- 把原作者真实凭证提交进仓库

输出：

`docs/IDENTITY.md`

至少记录：

- 拟定 `applicationId`
- 显示名、包名、signing 指纹占位（真实证书指纹在首次正式签名后回填）
- FCM / Maps / 更新源 / remote-config 的替换点文件路径
- 与 NagramX 无法覆盖安装的原因
- 用户迁移路径（见第 1.3 节）

已锁定的 `applicationId`：

| 产物 | applicationId | 显示名 | 说明 |
| --- | --- | --- | --- |
| 完整版（默认） | `app.nixgramx.android` | NixgramX | 保留 NagramX 最终完整版能力 |
| `_base` | `app.nixgramx.android.base` | NixgramX | 照搬 NagramX `_base`：去掉 Save Deleted Messages 等条款敏感增强 |

两个包必须：

- 可同时安装，互不覆盖
- 使用同一套 NixgramX 签名（与 NagramX 证书不同）
- 更新器只拉取对应 flavor 的 APK
- 默认推荐完整版；`_base` 在 Release 说明里单独标注

## 1.3 用户从 NagramX 迁移

换包名 + 换签名后，不能覆盖安装 NagramX。

必须在 README / `docs/IDENTITY.md` 写明：

- 聊天记录在 Telegram 服务端，重新登录同一账号即可恢复云端对话
- 本地独有数据不会自动迁移，包括但不限于：已删除消息本地库、编辑历史、部分设置、翻译缓存、自定义下载路径状态
- 优先复用 NagramX 已有「设置导入导出」
- 不实现破解式读取 NagramX 私有数据目录
- 不引导用户卸载前关闭必要备份
- NagramX 完整版用户安装 `app.nixgramx.android`
- NagramX `_base` 用户安装 `app.nixgramx.android.base`

---

# 2. 核心上游

## 2.1 Telegram Android 官方上游

官方仓库：

https://github.com/DrKLO/Telegram

NixgramX 必须长期跟随 **Telegram Android 官方最新可用源码**。

不要把本任务书写死在某个 Telegram 版本号上。

### 上游判定规则

每次同步必须按以下顺序取「当前应跟进版本」：

1. 读取 `DrKLO/Telegram` `master` 最近的 `update to x.y.z (build)` commit。
2. 记录该 commit SHA、版本名、内部 build number。
3. 检查是否存在更新的 submodule / `third_party` 变更（2026-08 起官方将部分 third_party 改为 submodule，漏同步会直接导致 native 编译失败）。
4. **不要**把 `DrKLO/Telegram` 的 GitHub Releases 页当作最新版本依据。该页可能长期停留在更老的 tag，与 master 脱节。
5. 可用 Play / APKMirror / 官方渠道版本做交叉核对，但源码同步仍以 GitHub master commit 为准。

V1.1 执行时的参考快照（实施时必须重新核对，不得当永久目标）：

- 官方 master 已出现：`update to 12.10.1 (7038)`
- 因此第一阶段同步目标至少不应低于当时 master 最新 `update to` commit

Codex 每次执行同步任务时必须：

1. 获取 Telegram 官方最新状态（按上面规则）。
2. 判断当前应跟进的官方版本。
3. 记录 Telegram upstream commit / 版本 / build number / submodule 状态。
4. 将 NixgramX 同步到该版本。
5. 不允许为了保留旧 NagramX 实现而回退 Telegram 新代码。

原则：

> Telegram 官方实现优先保持最新，NagramX 功能必须适配 Telegram 新实现。

## 2.2 NagramX 功能基线

原项目：

https://github.com/risin42/NagramX

状态（V1.1 核对）：

- 仓库已于 **2026-08-23 archived**，只读，不再维护
- 完整功能版与 `_base`（去掉 Save Deleted Messages 等能力的 ToS-compliant 包）同时存在
- 包名：`nu.gpu.nagram` / `nu.gpu.nagramx`（base）

第一阶段功能基准使用：

**NagramX 最终最新完整版（非 `_base` 精简版）。**

### 已确认基线（取代 V1 的 12.8.1.1254）

| 用途 | 版本 | Git 标识 | 说明 |
| --- | --- | --- | --- |
| 正式 Release 基线 | `12.9.2.1260` | tag `1260` / commit `4335a2e` | archived 前最后正式 Release |
| 必须审计的后续源码 | `12.10.0` 测试提交 | `a6c7d0a`（频道包 1261） | 归档前已 rebase 到 12.10.0，但未再发正式完整维护周期 |
| 禁止再当基线 | `12.8.1.1254` | 旧 Release | V1 过时信息 |

对应仓库 Release：

https://github.com/risin42/NagramX/releases

### 对 `a6c7d0a` 的强制审计

NagramXTurbo 等后续 fork 将 `a6c7d0a` 视为「最后一版源码」。NixgramX 不得盲目整包采用，也不得无视。

必须对比 `4335a2e` vs `a6c7d0a`，在 `docs/UPSTREAM_AUDIT.md` 中逐项说明：

- 哪些是有效的 12.10.0 适配
- 哪些是未完成 rebase
- 哪些是回归风险
- NixgramX 采用哪一侧，为什么

原则：

> 功能行为以 12.9.2.1260 完整版为准；对 12.10.x 的代码适配优先吸收 `a6c7d0a` 中可解释、可编译、不引入无关新功能的部分。

---

# 3. NixgramX 的定位

NixgramX 不是从零开发 Telegram 客户端。

NixgramX 的定位是：

> **NagramX 的长期维护与稳定增强版本。**

长期目标：

1. Telegram 官方更新后尽快跟进。
2. NagramX 已有功能不丢失。
3. 修复 NagramX 最终版本中的已知 Bug。
4. 修复 Telegram 上游更新导致的兼容性 Bug。
5. 降低 Crash。
6. 降低 ANR。
7. 修复通知、Push、媒体、翻译、网络等真实使用问题。
8. 防止每次上游更新把已经修好的问题重新引入。
9. 新增功能优先级低于稳定性。

NixgramX 不定位为「功能最全的 NagramX 超级缝合版」。NagramXF / NagramXTurbo / NiagramX 的新 UI、新输入栏、新转发玩法默认不进入第一阶段。

---

# 4. 功能迁移原则

## 总原则

**先完整继承 NagramX 最终完整版。**

不要重新设计 NagramX 的现有功能。

不要因为认为某项功能“没必要”而删除。

不要为了减少工作量擅自精简功能。

不要先进行大规模架构重写。

第一版优先做到：

> NagramX 最终版现有行为 + Telegram 最新上游 + NixgramX 名称/身份/更新体系。

## 功能清单必须以源码为准

散文列表不能作为验收依据。

第一阶段必须从源码导出：

`docs/FEATURE_INVENTORY.md`

来源至少包括：

- `NaConfig` / Neko / Nagram 设置项定义
- 设置页实际入口
- 翻译 provider 列表
- 删除消息 / 编辑历史相关开关
- 网络 / Push / 下载 / 实验性功能开关

清单每项至少包含：

- 设置项 key / 字段名
- 默认值
- UI 入口
- 主要实现文件
- 完整版状态：`keep` / `removed-by-policy` / `blocked` / `adapted`
- `_base` 状态：`keep` / `disabled-in-base` / `removed-by-policy` / `blocked`

每次上游同步后必须复跑该清单，防止「编译成功但某个翻译 provider 或删除消息入口消失」。

## 明确保留

NagramX 最终完整版已有功能原则上全部保留，包括但不限于：

- 完整翻译功能体系
- 收到消息翻译
- 发送前翻译
- NagramX 已有全部翻译引擎、设置、Provider 和相关 UI
- 删除消息记录
- 防删除消息
- 防删除媒体
- 保存受保护内容相关功能
- 阅后即焚/一次性媒体相关现有增强
- 媒体相关功能
- 下载相关功能
- 自定义下载/存储相关功能
- 聊天增强
- 消息详情
- 消息处理增强
- 转发/复制相关增强
- Sticker / Emoji 相关增强
- Story 相关增强
- N-Settings
- UI / 个性化
- 聊天界面相关定制
- 网络相关功能
- Proxy
- VPN / Proxy 联动
- PushService
- UnifiedPush
- FCM
- 多账号相关增强
- 设置导入导出
- Developer / Debug 相关现有功能
- 内置浏览器相关增强
- 分享相关增强
- 文件与存储工具
- NagramX 实验性功能
- 本地化与语言相关功能
- NagramX 其他现有功能

如功能名称与当前源码实际命名不同，以 NagramX 源码真实实现和 `FEATURE_INVENTORY.md` 为准。

---

# 5. 明确排除的功能

以下三项是项目所有者明确不需要的功能：

1. **Ghost Mode**
2. **隐藏正在输入状态**
3. **在线状态增强/隐藏在线相关增强**

处理原则：

- 不保留其 UI 开关。
- 不保留仅用于上述功能的独立逻辑。
- 如果相关公共代码同时被其他正常功能使用，不得粗暴删除公共代码。
- 应采用最小修改方式禁用/移除上述功能。
- 不得因为后续“完整同步 NagramX”或移植其他 fork 而重新引入。
- 在 `FEATURE_INVENTORY.md` 中标记为 `removed-by-policy`。

除此之外，不要擅自继续删减 NagramX 功能。

---

# 5A. 产品策略：完整版与 `_base`（第一阶段必须做）

NagramX 同时提供：

- 完整版：`nu.gpu.nagram`，含 Save Deleted Messages 等增强
- `_base`：`nu.gpu.nagramx`，Release 说明为 *ToS-compliant version, without advanced features such as Save Deleted Messages*；并注明 *Release only, no CI updates*

NixgramX 第一阶段必须同时交付这两类产物。实现原则：

> **照搬 NagramX 现有 `_base` 的裁剪范围和构建方式，不要自己发明一份「更干净」或「更彻底」的精简清单。**

## 必须先审计再复制

在 `docs/UPSTREAM_AUDIT.md` 与 `docs/FEATURE_INVENTORY.md` 中记录 NagramX `_base` 实际关掉了什么。至少核对这些位置：

- `APP_PACKAGE` / `applicationId` 如何从 `nu.gpu.nagram` 换成 `nu.gpu.nagramx`
- Release / 本地构建如何打 `_base` 包（Gradle flavor、构建参数、还是单独脚本）
- Save Deleted Messages / 防删除媒体 / 保存受保护内容 / 编辑历史等开关在 `_base` 中是编译期剔除、运行时强制关闭，还是隐藏 UI
- `_base` 是否仍保留翻译、代理、N-Settings 等与条款无直接关系的功能（按 NagramX 现状，这些通常保留）
- NagramX 对 `_base` 采取「Release only, no CI updates」：NixgramX 第一阶段允许 `_base` 只走 Release 任务，不强制每次 PR 都打 `_base`；但 Release 路径必须能产出 `_base` APK

找不到独立 `productFlavors` 时，允许用与 NagramX 相同的方式：同一套源码 + 构建参数切换包名和功能开关。禁止复制成两个长期分叉分支。

## NixgramX 对应关系

| NagramX | NixgramX |
| --- | --- |
| `nu.gpu.nagram` 完整版 | `app.nixgramx.android` |
| `nu.gpu.nagramx` `_base` | `app.nixgramx.android.base` |
| 完整版含防删除等 | 同样保留 |
| `_base` 去掉 Save Deleted Messages 等 | 同样去掉，范围与 NagramX `_base` 对齐 |
| 两包可同时安装 | 同样必须可同时安装 |
| `_base` 不走日常 CI 更新 | 允许；Stable Release 必须同时挂完整版和 `_base` |

## 文档与风险说明

必须在 README 与 `docs/BAN_RISK.md` 写清：

- 完整版含本地保存删除消息等能力，存在 Telegram ToS / 账号风险
- `_base` 是对照 NagramX `_base` 的条款友好构建，不是官方客户端，也不保证不被限制
- NixgramX 不提供封号豁免
- 默认分发入口以完整版为主，`_base` 在 Release 资产和说明中单独列出
- 用户从 NagramX 完整版应装 NixgramX 完整版；从 NagramX `_base` 应装 NixgramX `_base`

禁止：

- 自建中间服务器接管 Telegram 登录
- 伪造官方协议时序以外的服务端身份
- 把完整版写成「官方支持」或「更安全」
- 把 `_base` 扩成大规模删功能
- 为 `_base` 另开无法合并的源码树

---

# 6. 登录体系

NixgramX 登录方式保持 Telegram / NagramX 原有体系。

包括当前 Telegram 官方上游支持的正常登录能力，例如：

- 手机号
- Telegram 验证码
- Two-Step Verification
- QR 登录
- 多账号
- 官方上游已经提供的其他正常登录方式

不要创建 NixgramX 自己的账号系统。

不要增加中间服务器接管 Telegram 登录。

Telegram API 认证信息使用项目所有者自己的：

- `TELEGRAM_APP_ID`
- `TELEGRAM_APP_HASH`

敏感信息不得提交到公开仓库。

凭证缺失时：

- 用 `local.properties` 占位和 GitHub Secrets 接口把构建体系搭完
- 不得把整个项目停在「没有 API ID 就无法继续写代码」
- 登录真机验收标记为 `blocked by owner secrets`

---

# 7. 图标与品牌资产

第一阶段：

**暂时沿用 NagramX 当前图标，但必须意识到这只是临时方案。**

不要在第一阶段重新设计 App 图标。

不要因为图标问题阻塞功能开发、上游同步和稳定性工作。

注意：

- Nagram / NagramX 的名称与图标可能存在独立品牌声明
- NixgramX 后续必须使用自有品牌资产
- 第一阶段可以暂用图标，但 README 不得把项目宣传成官方 NagramX 续作授权版（除非所有者另有书面授权）

后续项目稳定后再单独替换 NixgramX 品牌图标。

---

# 8. 第一阶段实施策略

不要从零重写 NagramX。

正确顺序：

## Phase 0 — 上游审计

首先审计：

1. `risin42/NagramX` 最终代码状态与 archived 事实。
2. 正式 Release 基线 `12.9.2.1260` / `4335a2e`。
3. `4335a2e` 与 `a6c7d0a`（12.10.0 测试提交）的有效差异。
4. NagramX 当前构建方式、submodule、CI、签名、更新器入口。
5. NagramX 当前 Telegram base。
6. Telegram 官方当前最新 master `update to` commit。
7. NagramX 与最新 Telegram 之间的主要代码差异。
8. 身份相关硬编码：包名、更新 URL、remote-config、FCM、Maps、频道 ID。

输出：

`docs/UPSTREAM_AUDIT.md`

必须记录：

- NagramX baseline tag/commit
- `a6c7d0a` 审计结论
- Telegram upstream commit/tag/build
- 两者版本关系
- 主要冲突范围
- 高风险文件
- 身份硬编码位置

完成审计后 **不要停止在“制定方案”**。

除非存在无法继续的外部阻塞，否则继续实施。

## Phase 1 — 建立可运行的 NixgramX 基线

首先从稳定的 NagramX 最终实现建立 NixgramX。

目标：

- 项目可打开
- Gradle 配置正常
- Native / submodule 配置正常
- Debug build 可编译
- Release build 路径可验证
- 包名、显示名、更新源已切到 NixgramX
- 完整版与 `_base` 两套 `applicationId` 均可构建（`_base` 可仅在 Release 路径验证）
- App 可安装（有真机时）
- App 可启动（有真机时）
- 登录页面正常（有真机与凭证时）
- 主界面正常（有真机与凭证时）

然后：

- App 名称改成 NixgramX
- 更新项目说明
- 保留 NagramX 图标（临时）
- 不在这一阶段进行非必要 UI 重构

## Phase 2 — 同步最新 Telegram

将 NixgramX 与 Telegram Android 官方当前最新上游同步。

遇到冲突时：

### 不允许

为了快速解决冲突：

- 直接采用旧 NagramX 整个文件覆盖 Telegram 新文件
- 删除 NagramX 功能
- 回退 Telegram 新特性
- 注释掉功能让项目“先编译过去”
- 使用空实现欺骗编译
- Catch Exception 后静默吞掉真实错误
- 自动 merge 后未经门禁推送 Stable

### 应该

逐个理解冲突：

1. Telegram 官方改了什么。
2. NagramX 在该区域做了什么。
3. 保留 Telegram 新逻辑。
4. 将 NagramX 功能重新适配到 Telegram 新逻辑。
5. 编译。
6. 对照 `FEATURE_INVENTORY.md` 做回归。
7. 能跑的测试就跑；不能跑的明确列为未测风险。

---

# 9. Git 上游策略

建议仓库长期存在：

- `main` — 当前 Stable
- `develop` — 下一版本开发
- `upstream-sync/<telegram-version>` — Telegram 上游同步分支

不要频繁重写公开历史。

每次 Telegram 官方重大同步应有清晰 commit / PR，例如：

`sync: Telegram 12.x.x upstream`

PR 中必须记录：

- Telegram upstream commit
- Telegram version / build number
- submodule 变化
- 冲突文件
- 手动适配内容
- `FEATURE_INVENTORY` 是否复跑
- Build 状态
- 已测试项目
- 未测试风险

---

# 10. Telegram 自动跟进机制

这是 NixgramX **最高优先级能力之一**，但第一阶段按能力分层交付，禁止做成「全自动发版」。

## 10.1 L1 Watch（第一阶段必须有）

GitHub Actions 定时 + 手动：

- 检查 `https://github.com/DrKLO/Telegram` master 最新 `update to` commit
- 与仓库记录的 last-synced commit 比较
- 若落后：开 issue 或更新 tracking issue，写入版本、SHA、粗略变更规模

## 10.2 L2 Assist（第一阶段应有，允许失败）

发现需要跟进时：

1. 创建 `upstream-sync/<telegram-version>` 分支
2. 尝试 merge / 同步官方树与 submodule
3. **不要自动解决高风险冲突**
4. 输出冲突文件列表、编译尝试日志、同步报告
5. 创建 Draft PR，默认不合并

## 10.3 L3 Adapt（默认人工 / Codex 执行，禁止全自动合入）

按第 8 节规则逐文件适配 NagramX 功能。

## 10.4 L4 Gate（合入 develop / main 前必须满足）

如果出现任一情况：

- merge conflict 未解决
- 编译失败
- 核心 CI 检查失败
- 发现明显 Crash
- `FEATURE_INVENTORY` 出现非策略性丢失
- 关键功能出现已知回归

则：

**禁止自动发布 Stable，禁止自动合并到 `main`。**

必须保留现有稳定版本。

第一阶段验收只要求 L1 存在且 L2 能跑出报告；不要求 L2 能自动解决真实冲突。

---

# 11. NixgramX 客户端自动更新

优先复用 NagramX 已有更新机制。

将更新源修改为 NixgramX 自己的正式 Release。

流程：

NixgramX App
→ 检查 NixgramX GitHub Release
→ 比较版本
→ 显示更新信息
→ 下载匹配 ABI 的 APK
→ 校验 APK（至少 SHA-256 + 签名证书）
→ 调起 Android 安装器

要求：

- 不执行静默恶意安装
- APK 必须校验
- 签名必须长期保持一致
- 更新失败不能破坏当前安装
- 下载失败可安全重试
- 用户可以稍后更新
- 不得再请求 NagramX 的 Release / 频道文件

如果 NagramX 已有成熟实现，应优先沿用并修复，而不是重新造一个更新器。

---

# 12. NixgramX Telegram 频道

项目所有者将为 NixgramX 单独建立 Telegram 频道。

频道信息尚未最终填入仓库时：

不要在源码硬编码个人 Token。

设计为 GitHub Secrets / CI Variables，例如：

- `HELPER_BOT_TOKEN`
- `HELPER_BOT_TARGET`
- `HELPER_BOT_CANARY_TARGET`

Release 成功后可自动：

1. 生成更新日志。
2. 通知 NixgramX Telegram 频道。
3. 提供 GitHub Release。
4. 在需要时上传 APK。
5. 提供版本号。
6. 提供 Telegram base version。
7. 提供 SHA-256 与签名指纹。

频道配置必须可替换。

缺失频道/Token 时，把通知步骤做成可跳过 job，不阻塞 APK 构建。

---

# 13. Bug 修复目标

NixgramX 最重要的长期工作不是堆新功能。

而是：

> **修复 NagramX 已有问题，并在每次 Telegram 上游更新后保持稳定。**

---

## Bug 优先级

### P0 — Critical

立即处理：

- App 无法启动
- 登录完全失败
- 消息丢失
- 数据损坏
- 大规模 Crash
- 严重数据库异常
- 严重安全问题

### P1 — High

高优先：

- Push / 通知收不到
- ANR
- 高频 Crash
- 通话异常
- 媒体严重异常
- 翻译崩溃/卡死
- Deleted Messages 严重异常
- 下载严重异常
- Proxy / 网络连接严重异常
- 明显内存泄漏
- 后台异常耗电

### P2 — Normal

- 个别功能失效
- 设置不生效
- UI 错位
- 边界情况异常

### P3 — Polish

- 小动画
- 文案
- 轻微 UI
- 非必要视觉微调

---

# 14. Bug 修复方法

对于每个真实 Bug：

1. 先复现。
2. 获取日志。
3. 确认触发条件。
4. 找到根因。
5. 做最小修复。
6. 编译。
7. 验证原 Bug。
8. 验证相关功能没有回归。
9. 记录修复原因。
10. 尽可能建立回归测试。

不要：

- 看到异常就大量重构
- 仅靠猜测修改
- 只隐藏错误提示
- 吞异常
- 用 delay/sleep 随意绕过竞态
- 删除功能规避 Bug

无真机无法复现时：

- 可以移植后续 fork 中「可解释、范围小、有明确堆栈/提交说明」的修复
- 必须在报告中标记验证级别：`source-reviewed` / `compiled` / `device-tested`
- 不得把 `source-reviewed` 写成「已测试通过」

---

# 15. 利用 NagramX 后续生态修复 Bug

功能基准仍然是：

**NagramX final（12.9.2.1260 完整版 + 对 12.10.x 的必要适配）**

但是 Bug 修复可以参考后续 fork。

重点审计：

- NagramXF
- NagramXTurbo
- NiagramX
- 其他真实持续维护且有明确 patch 的 NagramX fork

### 允许摘取

- crash / ANR 修复
- 翻译富文本崩溃
- 自动翻译刷消息崩溃
- 删除/编辑消息过滤与本地库修复
- 设置备份丢失自定义 API id/hash
- HDR / 媒体查看器明显错误
- 内存泄漏、明显布局崩溃
- 与当前 Telegram upstream 兼容的最小补丁

### 默认禁止摘取

- 新输入栏 / iOS 风格 UI
- 新主题体系
- 新转发玩法 / Force Forward 一类新功能
- AyuGram / exteraGram 的整包功能迁入
- 与「修 NagramX 已有 bug」无关的增强

原则：

如果某个 NagramX 已知 Bug：

- 后续 fork 已正确修复
- patch 可以解释
- 不引入无关功能
- 与当前 Telegram upstream 兼容

则可以复用/移植修复。

但：

**不要因为参考其他 fork 就把其全部新功能带进 NixgramX。**

需要记录：

- 来源仓库
- commit / PR
- 修复内容
- 是否修改
- 为什么采用
- 验证级别

输出：

`docs/BUG_FIX_SOURCES.md`

---

# 16. 稳定性专项

重点检查：

## Crash

重点分析：

- Java/Kotlin Exception
- Native crash
- JNI crash
- SQLite exception
- lifecycle crash
- race condition
- 翻译渲染 / 富文本
- 删除消息数据库
- 图标选择器等已知递归崩溃点

目标：

高频日常路径无已知 Crash。

第一阶段可先做本地日志导出，不强制接入第三方崩溃上报。若接入，必须可关闭且不上传聊天内容。

## ANR

检查：

- 主线程 I/O
- 主线程网络
- 大数据库操作
- 大文件操作
- 媒体处理
- 锁等待
- 同步等待

不得为了“防崩溃”把耗时工作全部塞到 UI 线程。

## 内存

检查：

- Activity/Fragment 泄漏
- Bitmap
- Video
- Media cache
- Listener
- Callback
- Coroutine/Thread
- Native buffer
- 删除消息本地库膨胀

## 电量 / 后台

特别关注：

- Push
- 长连接
- Proxy
- VPN 检测
- 定时任务
- download service
- location / sensors（如果存在）
- 不必要 wake lock

---

# 17. 核心回归测试范围

至少建立以下 Smoke / Regression Checklist，输出到 `docs/TEST_MATRIX.md`。

每条测试必须标注执行环境：

- `ci`
- `local-compile`
- `device`
- `not-run`

没有执行过的条目不得勾「通过」。

## 登录

- 首次启动
- 手机号
- 验证码
- 2FA
- QR
- 退出账号
- 添加第二账号
- 重启 App 后会话保持

## 消息

- 私聊
- 群组
- 频道
- 回复
- 编辑
- 删除
- 批量删除
- 转发
- 复制
- 搜索

## Deleted Messages

- 文字
- 图片
- 视频
- 文件
- 回复消息
- 编辑后删除
- 批量删除
- App 重启
- 数据库重新加载

## 翻译

必须覆盖 NagramX 当前全部翻译路径，包括：

- 收到消息翻译
- 发送前翻译
- 语言选择
- 长文本
- 富文本
- URL
- Emoji
- Quote
- Channel
- Group
- API failure
- timeout
- provider 切换
- NagramX 已有其他翻译能力

## 媒体

- 图片
- 视频
- GIF
- 文件
- Sticker
- Voice
- Music
- 一次性/TTL 媒体
- 下载
- 保存
- 分享
- 播放

## Push / Notification

- 前台
- 后台
- 锁屏
- App 被杀
- 多账号
- FCM
- PushService / UnifiedPush（对应构建）

## 网络

- Wi-Fi
- Mobile
- Proxy ON/OFF
- VPN
- 网络切换
- 离线恢复
- timeout
- Telegram reconnect

---

# 18. Android 兼容性

至少关注：

- Android 14
- Android 15
- Android 16
- 后续最新 Android

优先保证主流 ARM64 Android 设备稳定。

特别关注：

- Samsung / One UI
- Pixel / AOSP 行为差异
- 后台策略
- Notification
- Storage
- Photo picker
- Media
- PiP
- Edge-to-edge
- Android 新权限行为

无对应真机时，在 `TEST_MATRIX.md` 标 `not-run`，不要编造通过。

---

# 19. 构建体系

需要做到：

## Local Build

明确文档：

`BUILDING.md`

内容至少包括：

- JDK
- Android SDK
- NDK
- Gradle
- submodule / third_party
- `local.properties`
- Telegram API credentials
- FCM
- Signing
- 常用 assemble 任务名（debug / staging / release / abi）

## GitHub Actions

至少建立：

### PR Build

PR 时：

- 编译
- secrets 扫描
- 基础检查
- 不生成正式 Stable Release

### Develop Build

develop 分支：

- 生成测试 APK / Artifact
- 可用于 Alpha 测试

### Release Build

Tag / 手动正式发布：

- 使用 Release signing
- Build 完整版 APK 与 `_base` APK（至少各打 arm64-v8a；universal 可选）
- 资产文件名必须能区分 flavor，例如 `NixgramX-v<ver>-<abi>.apk` 与 `NixgramX-base-v<ver>-<abi>.apk`
- 计算 SHA-256
- 创建 GitHub Release，完整版和 `_base` 都挂上
- 发布 changelog，写明 `_base` 与完整版的差异（照搬 NagramX 的表述即可）
- 后续通知 Telegram channel（可跳过）

### Upstream Watch

见第 10 节 L1/L2。

Telegram 官方完整编译通常耗时长、费用高。Watch 不要对官方每一次中间 commit 都跑完整 Release；以 `update to x.y.z` 为触发粒度。

---

# 20. Secrets

以下内容不得提交到公开仓库：

- TELEGRAM_APP_ID
- TELEGRAM_APP_HASH
- release keystore
- KEYSTORE_PASS
- ALIAS_PASS
- Bot Token
- FCM 私有配置
- 其他私钥

使用：

- `local.properties`
- `.gitignore`
- GitHub Actions Secrets
- 安全的 CI environment

如果项目中发现原作者遗留真实凭证：

**立即停止提交并报告。**

CI 应包含基础 secrets 扫描（至少检查疑似 token / 私钥 / keystore 二进制）。

---

# 21. Signing

NixgramX 从第一次正式发布开始必须保持自己的稳定签名。

不能：

- 每次重新生成 release keystore
- 将 keystore 上传到公开仓库
- 混用 NagramX 官方签名

需要文档：

`docs/SIGNING.md`

不要写入真实密码或私钥。

首次正式签名后，在 README / `docs/IDENTITY.md` 公布证书 SHA-256 指纹，方便用户验包。

---

# 22. 版本方案

NixgramX 版本应能直接看出 Telegram base。

建议形式：

`<TelegramVersion>-<NixgramXRevision>`

例如：

`12.10.1-1`

修复 NixgramX 自己的 Bug：

`12.10.1-2`

Telegram 更新后：

`12.10.2-1`

必须同时满足：

- `versionName` 与 GitHub Release 名称、App 内显示一致
- `versionCode` 单调递增，不破坏覆盖安装
- 不得出现「名字已经是 12.10.1-1，versionCode 仍停在 NagramX 1254」这类分裂

推荐实现（可微调，但必须写入 `docs/UPSTREAM_SYNC.md`）：

- `versionName` = `<tgVersion>-<nixRevision>`
- `versionCode` 基于 Telegram 内部 build number 映射，例如 `tgBuild * 100 + nixRevision`
- 若继续沿用 NagramX 的 4 位内部号，则从 **大于 1261** 起单调增加，并建立对照表

---

# 23. Release Channel

建议：

## Alpha

- 开发测试
- 允许存在已知 Bug
- 不作为普通用户默认推荐

## Beta

- 核心功能基本正常
- 用于扩大测试

## Stable

要求：

- Build 通过
- 核心 Smoke Test 中已执行项通过
- 无已知 P0
- 无阻断级 P1
- 升级路径正常
- 未执行的真机项必须在 Release notes / `KNOWN_ISSUES.md` 列出

禁止把仅 CI 编译成功的包默认标为 Stable。

---

# 24. 不允许的行为

Codex 在执行本任务时不得：

1. 擅自精简 NagramX 功能。
2. 擅自添加大量新功能。
3. 为了编译删除冲突功能。
4. 用旧 Telegram 文件整体覆盖新 upstream 文件。
5. 大规模无目的重构。
6. 改完不编译就宣称完成。
7. 只生成计划而不实施。
8. 隐藏编译错误。
9. 提交 secrets。
10. 修改 NagramX / Telegram 原始远程仓库。
11. 强推覆盖 main 历史。
12. 未测试就自动发布 Stable。
13. 把未执行的真机测试勾成通过。
14. 复用 NagramX 包名、签名或更新通道。
15. 把后续 fork 的新功能整包带入。

---

# 25. 许可证与 Attribution

NixgramX 基于：

- Telegram Android（上游仓库声明为 GPL-2.0）
- NagramX（GPL-3.0）

组合成品的默认公开发布方式：

- 采用 **GPL-3.0**（或经审计后明确写出的兼容结论）
- 不得删除原始版权声明、License 文件、源码许可证头、必要 attribution

Nagram / NagramX 品牌名称与图标可能有独立声明。源码许可不等于品牌授权。

Codex 必须审计 Telegram 与 NagramX 当前许可证和源码继承要求。

输出：

`docs/LICENSE_AUDIT.md`

必须列出：

- 根 LICENSE
- 关键第三方 / native / submodule
- 翻译、删除消息等来自 Ayu / Neko 系的文件来源
- 最终对外 LICENSE 选择与理由

如果 Telegram 与 NagramX 的 license 条款/版本组合需要特殊处理：

不要自行猜测一个「看起来能过」的方案后直接改许可证。

记录具体文件、来源和许可证，采用兼容的公开发布方式；不确定则在报告中列为待所有者确认项。

---

# 26. 第一阶段最终交付物

完成后仓库至少应包含：

- 可编译的 NixgramX 源码
- 最新 Telegram upstream 已同步，或完整冲突/blocker 分析仍保留代码
- NagramX final 功能已保留（以 inventory 为准）
- 明确排除的 3 个功能已处理
- NixgramX App 名称
- 完整版 `app.nixgramx.android` 与 `_base` `app.nixgramx.android.base`
- 新更新源 / remote-config 已切开
- `_base` 裁剪范围已对照 NagramX `_base` 记录在 inventory 中
- NagramX 图标临时保留
- Debug build 成功
- Release build 流程建立
- GitHub Actions（PR / develop / release 路径）
- Upstream Watch（L1）与 Assist（L2，允许失败）
- Client updater 指向 NixgramX
- 基础 bug fixes
- 基础 regression checklist（区分已跑 / 未跑）

以及：

- `README.md`
- `BUILDING.md`
- `docs/UPSTREAM_AUDIT.md`
- `docs/UPSTREAM_SYNC.md`
- `docs/IDENTITY.md`
- `docs/FEATURE_INVENTORY.md`
- `docs/BUG_FIX_SOURCES.md`
- `docs/STABILITY.md`
- `docs/TEST_MATRIX.md`
- `docs/SIGNING.md`
- `docs/LICENSE_AUDIT.md`
- `docs/KNOWN_ISSUES.md`
- `docs/BAN_RISK.md`

---

# 27. README 应明确说明

NixgramX 是：

> A Telegram Android client based on NagramX, focused on staying current with Telegram upstream and delivering high stability.

README 中至少说明：

- 项目来源
- Telegram upstream（commit / version）
- NagramX base（`12.9.2.1260` / `4335a2e`，以及是否吸收 `a6c7d0a`）
- 当前 Telegram base
- 当前 NixgramX version
- 新包名，无法覆盖安装 NagramX
- Build 方法
- Release 下载与验包指纹
- 已知问题
- ToS / 账号风险摘要
- License / Attribution

不要宣传尚未实现的功能。

不要把项目写成 Telegram 官方客户端。

---

# 28. 最终验收标准

Codex 不得只报告“代码已经修改”。

必须逐条给出结果，并标注证据类型：`done` / `blocked` / `not-run`。

## CI / 仓库可证明

- [ ] NagramX 最终完整版基线已确认为 `12.9.2.1260` / `4335a2e`
- [ ] `a6c7d0a` 差异已审计
- [ ] Telegram 最新 upstream 已按 master `update to` commit 确认
- [ ] NixgramX 基线建立
- [ ] App 显示名称为 NixgramX
- [ ] 完整版 `applicationId` 为 `app.nixgramx.android`
- [ ] `_base` `applicationId` 为 `app.nixgramx.android.base`
- [ ] `_base` 已按 NagramX `_base` 去掉 Save Deleted Messages 等对应能力
- [ ] Release 路径可产出完整版与 `_base` 两套 APK
- [ ] 更新源 / remote-config 已离开 NagramX
- [ ] NagramX 图标暂时保留
- [ ] 明确排除功能未被重新加入
- [ ] NagramX 其他功能未被无故删除（对照 inventory）
- [ ] Debug APK 编译成功
- [ ] Release 构建路径可验证
- [ ] GitHub Actions Build 成功或给出失败日志
- [ ] Upstream Watch 自动化存在
- [ ] Update checker 指向 NixgramX
- [ ] Secrets 未提交
- [ ] License audit 完成
- [ ] Known issues 已记录
- [ ] Identity / Ban risk / Feature inventory 文档已存在

## 真机才能证明（无设备或无凭证则标 blocked / not-run）

- [ ] App 可启动
- [ ] 登录流程可进入
- [ ] 主界面可进入
- [ ] 核心消息流程 Smoke Test
- [ ] 翻译功能 Smoke Test
- [ ] Deleted Message 功能 Smoke Test
- [ ] 媒体功能 Smoke Test
- [ ] Push / Notification 基础检查
- [ ] Proxy / 网络基础检查

---

# 29. Codex 最终报告格式

最终必须输出：

## 1. 基线

- NagramX tag/commit（必须包含 `4335a2e` 与 `a6c7d0a` 处理结论）
- Telegram tag/commit / build number
- NixgramX commit
- `applicationId`

## 2. 已完成内容

按模块列出，区分文档、构建、同步、功能适配、身份切开。

## 3. Bug 修复

每个 Bug：

- 现象
- 根因
- 修改
- 来源（如有 fork 移植）
- 验证结果与验证级别

## 4. Upstream 冲突

- 文件
- Telegram 修改
- NagramX 修改
- 最终如何处理

## 5. Build

- Debug
- Release
- ABI
- CI
- submodule

## 6. Test

列出真实执行过的测试与环境。

不要把“代码看起来应该正常”写成“已测试”。

## 7. 未完成 / 风险

必须真实列出，包括 secrets、真机、未适配冲突、ToS 风险。

## 8. 下一步

只列当前尚未完成且明确需要继续处理的事项。

---

# 30. 最重要的执行原则

整个项目始终遵守：

> **Telegram upstream freshness > NagramX feature preservation > stability > polish > new features**

但这里的 `>` 不代表允许为了 Telegram 更新而删除 NagramX 功能。

正确理解是：

> Telegram 新代码必须保留，NagramX 功能必须重新适配；最终两者同时成立。

NixgramX 的长期目标不是成为功能最杂的 Telegram fork。

目标是：

> **持续跟进最新 Telegram，同时保留 NagramX 成熟功能，并成为一个稳定性非常强、Bug 修复非常积极的长期维护版本。**

---

# Codex 执行指令

请严格阅读本文件全文。本文件取代 V1 / V1.1 中已更正的基线与流程；与旧版冲突时以 V1.2 为准。

执行顺序：

1. 审计（含 `4335a2e` / `a6c7d0a` / 官方 master / 身份硬编码）。
2. 建立可编译基线，并切开包名、更新源、remote-config。
3. 同步 Telegram 最新 upstream。
4. 解决冲突。
5. 保留 NagramX 功能，导出并核对 feature inventory。
6. 处理明确排除功能。
7. 修复已知 Bug（只摘可解释补丁）。
8. Build。
9. Test（区分 CI 与真机）。
10. 建立 Actions：PR/Release + Upstream Watch L1/L2 + Updater。
11. 更新文档。
12. 输出最终报告。

**除非遇到必须由项目所有者提供的外部凭证、签名、API 或不可判断的重大冲突，否则不要停在“方案阶段”，继续推进实际实现。**

如果遇到凭证缺失：

使用安全占位和 Secrets 接口完成其余实现，不要把整个项目停住。真机登录/Push 标 `blocked`。

如果遇到某个功能暂时无法适配：

不要删除。

记录为 blocker，并保留当前代码和完整分析。

---

End of task.
