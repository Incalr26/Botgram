# Botgram

[![Telegram Channel](https://img.shields.io/badge/Channel-@Botgram__Channel-blue?logo=telegram)](https://t.me/Botgram_Channel)
[![Telegram Group](https://img.shields.io/badge/Group-@Botgram__ChatGroup-blue?logo=telegram)](https://t.me/Botgram_ChatGroup)
[![GitHub Release](https://img.shields.io/github/v/release/Incalr26/Botgram?label=Release)](https://github.com/Incalr26/Botgram/releases)
[![License](https://img.shields.io/github/license/Incalr26/Botgram?label=License)](LICENSE)

使用 Telegram 机器人聊天的客户端。直接连接 Telegram 服务器，无中转服务器，所有数据本地存储。


## 优势 / Advantages
- 直接调用 Telegram Bot API，无中转服务器，安全快捷
- 没有telegram账号也可以通过他人申请的bot免费在telegram聊天
- 使用图形化交互管理bot，简单便捷
- 页面简洁

## 已实现功能 / Features
- 使用 Bot Token 登录，自动调用 `getMe` 验证
- 会话列表（私聊、群组、频道），支持未读计数
- 接收并显示文本消息（贴纸和媒体暂以文字提示）
- 发送纯文本消息
- 头像显示
- 手动添加聊天（输入 Chat ID 并验证）
- 聊天内显示每条消息的 ID 和时间
- 下拉刷新聊天列表
- 网络状态实时提示（虽然可能判断错误）
- 侧边抽屉菜单
- 完整的 API 原始数据日志，可支持复制、导出和发送给开发者
- 崩溃自动进入崩溃页面
- 粗体斜体等文本消息特殊格式显示
- 群成员身份/标签显示（目前不支持显示普通群成员的标签）
- 消息回复引用显示
- 消息自动刷新
- 消息转发
- 媒体消息接收与显示

## 注意 / Note
- 若要读取群中的消息，请检查是否关闭了隐私模式
- 若需读取其他bot的消息，需在botfather中打开bot to bot功能

## 下载 / Download
- [GitHub Releases](https://github.com/Incalr26/Botgram/releases)
- [Telegram 频道：@Botgram_Channel](https://t.me/Botgram_Channel) 

## 构建 / Build
- Kotlin / OkHttp / 原生 SQLite
- Android Gradle Plugin 8.2.0 + Gradle 8.7
- JDK 17

## 许可证 / License
[MIT License](LICENSE)
