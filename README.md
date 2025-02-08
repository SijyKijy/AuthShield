# AuthShield - 我的世界服务器登录验证插件

![Version](https://img.shields.io/badge/NeoForge-1.21.1-blue)
![License](https://img.shields.io/badge/License-MIT-green)
![Language](https://img.shields.io/badge/Language-Java-orange)

简体中文 | [English](README_EN.md)

## 📝 介绍

AuthShield 是一个专为 Minecraft NeoForge 服务器设计的登录验证插件。它提供了一套完整的玩家身份验证系统，能有效保护玩家账号和服务器安全。

## ✨ 特性

- 🔐 完整的注册登录系统
  - 支持注册、登录、修改密码等基本功能
  - 安全的密码加密存储机制
  - 登录超时自动踢出保护
- 🛡️ 全方位的安全防护
  - 未登录状态行为限制
  - 防止未授权操作
  - 密码强度要求
- 👑 强大的管理功能
  - 管理员命令支持
  - 玩家数据管理
  - 初始出生点设置
- 🎨 精美的视觉效果
  - 粒子特效反馈
  - 标题提示系统
- 🌏 多语言支持
  - 中文
  - English

## 📥 安装步骤

1. 下载最新版本的 `AuthShield.jar`
2. 将文件放入服务器的 `mods` 文件夹
3. 重启服务器
4. 享受安全的游戏体验！

## ⚙️ 配置文件

插件首次运行时会自动创建配置文件：
```
📁 config/authshield/
   ├── 📄 config.json    - 主配置文件
   └── 📄 translations.json - 语言文件
```

## 📌 命令系统

### 玩家命令
| 命令 | 简写 | 描述 |
|------|------|------|
| `/register <密码> <确认密码>` | `/reg` | 注册账号 |
| `/login <密码>` | `/l` | 登录账号 |
| `/changepassword <旧密码> <新密码>` | `/cp` | 修改密码 |

### 管理员命令
| 命令 | 描述 |
|------|------|
| `/authshield help` | 显示帮助信息 |
| `/authshield unregister <玩家名>` | 注销指定玩家 |
| `/authshield setfirstspawn` | 设置新玩家初始出生点 |

## 🔧 依赖要求

- ☕ Java 17+
- 🎮 Minecraft 1.20.x
- 🛠️ NeoForge 服务端

## 💬 技术支持

遇到问题？我们随时为您提供帮助！

[![加入我们的QQ群](https://img.shields.io/badge/QQ群-528651839-blue)](https://jq.qq.com/?_wv=1027&k=528651839)

## 📜 许可证

本项目采用 [MIT](LICENSE) 许可证开源。

