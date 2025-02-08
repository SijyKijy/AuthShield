# AuthShield - Minecraft Server Authentication Plugin

![Version](https://img.shields.io/badge/NeoForge-1.21.1-blue)
![License](https://img.shields.io/badge/License-MIT-green)

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

### Configuration Details

`config.json` contains the following main settings:

#### 🌏 Basic Settings
```json
"settings": {
    "language": "zh_cn",     // Language: zh_cn(Chinese) or en_us(English)
    "debug": false           // Enable debug mode
}
```

#### 🕒 Login Settings
```json
"login": {
    "timeout": {
        "enabled": true,      // Enable login timeout
        "seconds": 60,        // Timeout duration (seconds)
        "message": "..."      // Timeout message
    },
    "attempts": {
        "max": 3,            // Maximum login attempts
        "timeout_minutes": 10 // Cooldown after max attempts (minutes)
    }
}
```

#### 🔑 Password Requirements
```json
"password": {
    "min_length": 6,         // Minimum length
    "max_length": 32,        // Maximum length
    "require_special_char": false,  // Require special characters
    "require_number": false,        // Require numbers
    "require_uppercase": false,     // Require uppercase letters
    "hash_algorithm": "SHA-256"     // Password encryption algorithm
}
```

#### 🛡️ Restrictions
```json
"restrictions": {
    "gamemode": "spectator",    // Gamemode before login
    "effects": [                // Effects applied before login
        {
            "id": "minecraft:slow_falling",
            "amplifier": 255,
            "particles": false,
            "icon": false
        }
    ],
    "allowed_commands": [       // Allowed commands before login
        "login", "l", "register", "reg"
    ]
}
```

#### 💬 Message Settings
```json
"messages": {
    "title": {
        "enabled": true,    // Enable title
        "fade_in": 10,      // Fade in duration
        "stay": 70,         // Stay duration
        "fade_out": 20,     // Fade out duration
        "text": "..."       // Title text
    },
    "subtitle": {
        "enabled": true,    // Enable subtitle
        "text": "..."       // Subtitle text
    },
    "actionbar": {
        "enabled": true,    // Enable actionbar messages
        "interval": 40,     // Message display interval
        "text": "..."       // Message text
    }
}
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

