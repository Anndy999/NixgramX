# 本轮更新审查：只提交 PR，不构建

基线：main `2eb919449cb6c1b2e3bade44d4f8d3dfcf89af86`（1269）。工作分支：`codex/merge-review-fix`。

本轮只修两个有明确证据的问题及相关发布文案，不合并旧 PR、不回灌 beta、不发 APK、不更新 GitHub Release、不改线上频道或仓库 Variables。

## KI-009：发布器与已安装客户端的更新协议断开

### 复现与证据

已有 [1269 发布任务](https://github.com/Anndy999/NixgramX/actions/runs/34008086919) 的日志记录跳过 `#update*` JSON；无需重放线上发布即可确认分支选择。

1. App 选择正式或 Beta 更新通道。
2. 原发布器只发 APK，没有独立私有目标时跳过元数据。
3. App 手动检查仍在 @NixgramX 搜索原 JSON 标签。
4. 本次 APK-only 发布没有产生可供这条检查路径识别的新版本元数据。

**真实执行位置**：`.github/workflows/release.yml` / `staging.yml` 的 `upload` job → `Tools/scripts/upload.py:main()` → `read_update_metadata()` → `send_to_channel()` → `send_update_json()`。

**客户端调用链**：`UpdateHelper.checkNewVersionAvailable()` → `BaseRemoteHelper.load()` → `TL_messages_search`（同一公开频道、对应标签）→ `onGetMessageSuccess()` 剥离标签并解析 JSON → `UpdateHelper.onLoadSuccess()` 比较版本并获取消息/文档。不是根据 APK 标题猜测更新。

**一句话根因**：发布端停止提供仍被已安装客户端消费的同频道元数据。

**最小改动范围**：发布器恢复协议输出，改为编辑既有消息并回读验证；两个 workflow 传入对应元数据帖 ID 并串行发布；Java 仅更新过时注释，运行逻辑和常量不变。没有增加频道、修改安装身份或重写客户端。

**可能误伤/限制**：错误、已删除、标签不符、版本码异常的元数据会阻止发布；先核对配置。机器人编辑权限、旧客户端搜索索引、动画/下载仍须线上验证。APK 上传和消息编辑不是原子事务，上传后失败需人工核验，不能盲目重跑。

**置信度**：高（源码调用链 + 既有发布日志 + 离线回归）；这不是线上或真机验证通过声明。

### 提交前四问

- 用户检查更新是否经过此路径？是；客户端入口直接调用 `load()`，读的正是发布器现在维护的 JSON。
- 改的是原因还是表象？原因；恢复缺失的生产者输出，不更换弹窗来掩盖查不到版本。
- 回滚会不会复现？已把旧基线发布器放入同一离线测试环境：元数据编辑测试失败，当前代码通过。Beta 标题去哈希测试也同样先失败、后通过。
- 是否只动必要文件？只动发布器、其 workflow 参数、协议注释、对应文档和测试；没有改 UI 业务、签名、API 凭据、包名或 submodule。

## KI-010：upstream-watch 在载入 YAML 时失败

### 复现与证据

已有 [失败记录](https://github.com/Anndy999/NixgramX/actions/runs/34008086744) 没有正常启动 job。基线 YAML 的第 32 行 `commits=json.load(sys.stdin)` 跑出了 `run: |` 块，解析器报缺少冒号。

**真实执行位置**：GitHub 在启动任何 job 前加载 `.github/workflows/upstream-watch.yml`。问题发生在 YAML 装载，不是 Gradle，也不是 Telegram 上游 API 返回失败。

**一句话根因**：内嵌 Python 多行内容没有缩进到 YAML block scalar 中。

**最小改动范围**：仅恢复这 7 行缩进；保留主分支限制、定时/手动入口、软失败和报告逻辑。

**可能误伤/限制**：需要同时保持 Python 的内部相对缩进；已用实际提取的 Python 代码测试“找到首个 update-to 提交”和“找不到返回 2”两条路径。未触发远端 workflow，未创建/评论 issue。

**置信度**：高。

### 提交前四问

- 会经过修改位置吗？会；每次调度首先解析该 YAML。
- 是根因修复吗？是；修复无法加载的结构，不是隐藏失败通知。
- 回滚会复现吗？基线文件本地解析失败；修复后所有 workflow YAML 均能解析。
- 是否只动必要文件？该因果点只改 YAML 缩进，并补对应测试和问题记录。

## 文案

- 正式：`NixgramX · 12.10.1 (版本号)`。
- Beta：`NixgramX Beta · 12.10.1 (版本号)`，不再把构建哈希拼进标题。
- 可选说明仅来自用户的 `RELEASE_NOTES`；不退回到 git commit 标题。
- 不新发 JSON、Track、canary 哈希或贴纸通知。历史元数据帖仍保留原地更新，不能既删掉旧协议数据又承诺旧版兼容。

## 自检（不构建）

```bash
# 本机已有 PyYAML 6.0.3；未安装 Android/发布依赖，也未连接 Telegram。
# 其他环境运行全部测试前可安装 Tools/scripts/tests/requirements.txt。
python -B -m unittest discover -s Tools/scripts/tests -b -v
git diff --check
```

26 项测试通过：发布配置、正式/Beta 标签、旧版本阻断、同频道文档 ID、无新通知、编辑响应丢失重试、并发修改保护、回读验证、标题、客户端 JSON 契约、YAML 装载和内嵌检测逻辑。Pyrogram 的网络边界使用 mock，没有执行客户端登录/发帖。

未运行 `gradlew`、未编译 Java/Kotlin、未打 APK。该基线已有成功构建不等于此 PR 已构建。

PR 保持 Draft；提交正文使用 GitHub 支持的 `[skip ci]`，避免 push / pull_request 触发编译。此时 CI 可能为空或 Pending，不能当作构建通过，也不能绕过审批合并。经主人批准构建时，再提交不带 skip 标记的后续提交或明确执行允许的验证流程。

## 交付后的明确待办

1. 管理员确认现有元数据帖可读、可编辑，设置 `UPDATE_RELEASE_MESSAGE_ID` / `UPDATE_BETA_MESSAGE_ID`；本轮没有设置。若历史帖已删，另行批准一次初始化，不能随便填 ID。
2. 审批 PR 后安排构建；本轮没有提高版本号。正式/Beta 下一次实际发布必须高于所有已发 APK 的版本码。
3. beta 回灌 #12/#13/#14、GitHub 正式 Release 同步是另外的发布动作，本轮没有执行。
4. UB-1/UB-4 仍需执行路径及真机复核；UB-2/UB-3/UB-5 没有修复或改成 Fixed。旧 PR #2 仍不得合 main。

批准发布后，每项真机验收不超过四步：

- 更新：安装已有旧版 → 选择正式/Beta → 检查更新 → 确认版本、动画及下载。
- 频道：记录旧元数据消息 ID → 完成获批发布 → 确认只新增 APK → 确认旧 ID 的 JSON 已更新。
- 失败保护：离线测试错误 ID → 确认未调用上传 → 校验正确 ID 用例 → 确认读取、编辑、回读按顺序执行。

参考：[GitHub 跳过 CI](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/skip-workflow-runs)、[Pyrogram get_messages](https://docs.pyrogram.org/api/methods/get_messages)。本次没有移植其他 fork 的 bugfix。
