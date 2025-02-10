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
    "hash": {
        "algorithm": "PBKDF2WithHmacSHA256",  // Password encryption algorithm
        "iterations": 65536,                   // Hash iteration count
        "key_length": 256,                    // Key length (bits)
        "salt_length": 16                     // Salt length (bytes)
    }
}
```

#### 🛡️ Restrictions
```