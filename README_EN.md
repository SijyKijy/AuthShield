# AuthShield - Minecraft Server Authentication Plugin

![Version](https://img.shields.io/badge/NeoForge-1.20.x-blue)
![License](https://img.shields.io/badge/License-MIT-green)
![Language](https://img.shields.io/badge/Language-Java-orange)

[简体中文](README.md) | English

## 📝 Introduction

AuthShield is a login verification plugin designed for Minecraft NeoForge servers. It provides a comprehensive player authentication system that effectively protects player accounts and server security.

## ✨ Features

- 🔐 Complete Registration & Login System
  - Basic functions: register, login, password change
  - Secure password encryption storage
  - Auto-kick on login timeout
- 🛡️ Comprehensive Security Protection
  - Behavior restrictions before login
  - Unauthorized operation prevention
  - Password strength requirements
- 👑 Powerful Administration
  - Admin command support
  - Player data management
  - Initial spawn point setting
- 🎨 Beautiful Visual Effects
  - Particle effect feedback
  - Title prompt system
- 🌏 Multi-language Support
  - English
  - Chinese

## 📥 Installation

1. Download the latest version of `AuthShield.jar`
2. Place the file in your server's `mods` folder
3. Restart the server
4. Enjoy secure gaming!

## ⚙️ Configuration

The plugin will automatically create configuration files on first run:
```
📁 config/authshield/
   ├── 📄 config.json    - Main configuration
   └── 📄 translations.json - Language file
```

## 📌 Commands

### Player Commands
| Command | Shortcut | Description |
|---------|----------|-------------|
| `/register <password> <confirmPassword>` | `/reg` | Register account |
| `/login <password>` | `/l` | Login account |
| `/changepassword <oldPassword> <newPassword>` | `/cp` | Change password |

### Admin Commands
| Command | Description |
|---------|-------------|
| `/authshield help` | Display help information |
| `/authshield unregister <player>` | Unregister specified player |
| `/authshield setfirstspawn` | Set initial spawn point for new players |

## 🔧 Requirements

- ☕ Java 17+
- 🎮 Minecraft 1.20.x
- 🛠️ NeoForge Server

## 💬 Support

Need help? We're here for you!

[![Join our QQ Group](https://img.shields.io/badge/QQ_Group-528651839-blue)](https://jq.qq.com/?_wv=1027&k=528651839)

## 📜 License

This project is licensed under the [MIT](LICENSE) License.

