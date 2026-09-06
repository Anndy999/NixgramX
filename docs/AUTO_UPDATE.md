# 单频道应用内更新

正式版与 Beta 共用现有 [@NixgramX](https://t.me/NixgramX)，不新增频道。

## 对用户显示什么

每次发布只新增 APK 帖，文案为 `NixgramX · 12.10.1 (版本号)` 或 `NixgramX Beta · 12.10.1 (版本号)`，可附 `RELEASE_NOTES` / `docs/RELEASE_NOTES.txt` 中的说明。不会自动追加提交标题、哈希、Track 提示、JSON 说明帖或贴纸通知。

## 为什么仍保留历史元数据帖

已安装 App 走 `UpdateHelper.checkNewVersionAvailable()` → `BaseRemoteHelper.load()`，在公开频道搜索 `#updateRelease` / `#updateBeta`，随后剥离标签并解析 JSON。APK 简短标题不能替代这个协议。

发布器**原地编辑每个通道已有的一条元数据帖**，不再新发元数据消息。历史 JSON 仍然可见，但不会因新发一条消息而刷屏。完全删除元数据帖与保留旧版更新兼容性不能同时实现；不要用旧的 `delete_public_update_json.py` 删除正在使用的帖子。

App 频道 ID 仍为 `3819693045`，用户名仍为 `NixgramX`。不改包名、签名、资源 ID，也不启用官方 Telegram 更新源。

## 发布前一次性配置

由频道管理员确认：

1. 找到同一频道中现有的 `#updateRelease {…}`、`#updateBeta {…}` **文本帖**，每个通道选择最新有效元数据帖，不要选择 APK 或说明帖。它们必须仍在旧版 App 的搜索结果中。
2. 确认发布机器人能读取并编辑这两条帖；优先选择原发布机器人发的帖，并检查管理员权限。
3. 在 GitHub 仓库 **Variables** 设置 `UPDATE_RELEASE_MESSAGE_ID`、`UPDATE_BETA_MESSAGE_ID`。值为帖链接末尾的正整数，不是频道 ID，也不是密钥。对应 workflow 将其传给 `UPDATE_METADATA_MESSAGE_ID`。
4. 确认 JSON 的 `version_code` 对应真实 APK 的 NixgramX 版本码，而非 Telegram 上游 `7038`。下一次版本码必须高于所有已发布的正式/Beta APK，不能因旧元数据滞后而复用版本号。

仍使用原有 `HELPER_BOT_TOKEN`、`HELPER_BOT_TARGET`、`APP_ID`、`APP_HASH`、构建/签名 Secrets。**不需要 `HELPER_BOT_CANARY_TARGET`**，不要创建私有频道或修改 App 的频道常量。不得提交、打印凭据。

如果历史帖已删除或身份/内容不符，发布器在发送 APK 前失败。需要管理员另行批准一次元数据初始化；本 PR 不自动新建、恢复、删除或修改任何线上帖子，也不设置仓库 Variables。不要填写其他消息 ID 绕过检查。

## 执行顺序与错误处理

1. `upload.main()` 校验消息 ID、固定发布目标及新版本号，读取同频道、同标签的历史 JSON；拒绝同通道版本码复用/倒退。
2. `send_to_channel()` 上传 APK，收集实际 APK 消息 ID。上传失败不会修改元数据。
3. `send_update_json()` 再读元数据，检查上传期间是否被其他发布改动，再用 `edit_message_text()` 原地更新。JSON 禁用格式解析，`document` 指向本频道实际 APK 消息 ID。
4. 回读必须与此次 JSON 完全一致才报告成功。已有贴纸 ID 被保留；没有时使用 App 现有本地动画回退，不新发贴纸。

Stable/Beta 发布 job 共用 `nixgramx-telegram-publish` 并发组，正在运行的发布不会被后一次构建取消。不同通道间的全局版本号仍须发布者检查：仅凭一条旧元数据无法知道其他通道或 APK-only 发布的最高版本。

APK 上传与元数据编辑不是原子事务。上传后编辑失败，job 会失败，旧元数据不会被当成新版本发布成功。APK 可能已经在频道中，**不要盲目重跑整个发布**：先核对日志的 APK 消息 ID、版本/签名及元数据状态，再由管理员决定修复元数据或重新发布。编辑响应丢失时会先检查目标文本，避免在脚本内重复上传 APK；这不保证整次 workflow 重跑不产生重复 APK。

## 客户端与验证范围

- 后台检查仍默认关闭；用户选择正式或 Beta 后使用相应标签。
- 手动检查走现有 `checkNewVersionAvailable(..., true)` 路径；本次不更改该逻辑。
- 解析、跨账号体验、搜索索引更新延迟及 Telegram 编辑权限需线上验收；离线 mock 测试不是 Telegram 或真机通过证明。
- 本次只提交 Draft PR，不运行 Gradle、不发 APK、不改线上消息、不合并分支。

无需凭据或 Pyrogram 安装即可运行发布器离线测试：

```bash
python -B -m unittest discover -s Tools/scripts/tests -p test_upload.py -v
```

合并并完成配置后，经主人批准再发布更高版本，用已有旧版 App 验证：

1. 选择正式更新通道，手动检查，确认发现新正式版并能下载。
2. 切换 Beta 通道，确认读取对应元数据并能获取相应 APK。
3. 检查频道：只新增 APK 帖，原元数据消息 ID 不变，无新 JSON/哈希/贴纸通知。
4. 核对发布 job 的回读成功记录及实际 APK 签名、版本号。
