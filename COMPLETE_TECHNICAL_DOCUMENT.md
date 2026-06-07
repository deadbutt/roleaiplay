# IoT 应用完整技术文档

## 1. 项目概述

本项目是一个集成了 IoT 设备控制和 AI 对话功能的 Android 应用。应用包含完整的用户认证系统、角色卡片管理系统和对话交互功能。用户可以通过该应用进行设备控制、创建管理角色卡片，并与 AI 角色进行智能对话。

### 核心特性
- **用户认证系统**：完整的登录、注册、密码找回流程
- **角色卡片管理**：创建、编辑、查看角色卡片
- **AI 对话功能**：与角色进行实时对话，管理对话历史
- **IoT 设备控制**：设备状态监控和控制功能

## 2. 系统架构

### 2.1 核心组件

| 组件名称 | 功能描述 | 布局文件 |
|---------|---------|---------|
| `MainActivity` | 应用入口选择界面 | `activity_main.xml` |
| `LoginActivity` | 用户登录界面 | `activity_login.xml` |
| `ChatActivity` | **AI对话主界面** | `activity_chat.xml` |
| `SettingsActivity` | 设置页面入口 | `activity_settings.xml` |
| `CharacterListActivity` | 角色卡片列表管理 | `activity_character_list.xml` |
| `CharacterEditActivity` | 角色编辑/创建界面 | `activity_character_edit.xml` |
| `CharacterDetailActivity` | 角色详情查看 | `activity_character_detail.xml` |
| `RegisterActivity` | 注册-填写邮箱 | `activity_register.xml` |
| `RegisterCodeActivity` | 注册-验证码 | `activity_register_code.xml` |
| `RegisterPasswordActivity` | 注册-设置密码 | `activity_register_password.xml` |
| `ForgetPasswordActivity` | 找回密码-邮箱 | `activity_forget_password.xml` |
| `PasswordVerificationActivity` | 找回密码-验证码 | `activity_password_verification.xml` |
| `PasswordNewSetActivity` | 找回密码-新密码 | `activity_password_new_set.xml` |

**重要说明：** `HomeActivity` 暂时不使用，登录后直接跳转到 `ChatActivity`，专注于AI聊天功能实现。

### 2.2 数据流向

```
MainActivity → LoginActivity → ChatActivity → SettingsActivity → CharacterListActivity → (CharacterEditActivity/CharacterDetailActivity) → ChatActivity
```

### 2.3 界面流程

```
┌─────────────────────┐
│ 入口选择界面        │
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐     ┌─────────────────────┐
│ 登录界面            │◄────┤ 注册-填写邮箱       │
└─────────┬───────────┘     └─────────┬───────────┘
          │                           │
          ▼                           ▼
┌─────────────────────┐     ┌─────────────────────┐
│ 对话界面（主界面）  │◄────┤ 注册-验证码         │
└─────────┬───────────┘     └─────────┬───────────┘
          │                           │
          ▼                           ▼
┌─────────────────────┐     ┌─────────────────────┐
│ 设置界面            │     │ 注册-设置密码       │
└─────────┬───────────┘     └─────────────────────┘
          │
          ▼
┌─────────────────────┐
│ 角色卡片列表        │
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│ 角色编辑/详情界面   │
└─────────────────────┘
```

## 3. 功能模块详解

### 3.1 用户认证模块

#### 3.1.1 登录页面 (LoginActivity)

**功能描述：**
- 用户登录认证界面
- 支持邮箱密码登录
- 登录成功后直接跳转到对话界面
- 提供注册和找回密码入口

**界面元素：**
- 邮箱输入框
- 密码输入框
- 登录按钮
- 注册账号按钮
- 忘记密码按钮

#### 3.1.2 注册流程

**注册-填写邮箱 (RegisterActivity)：**
- 输入邮箱 + 勾选协议

**注册-验证码 (RegisterCodeActivity)：**
- 6位验证码输入

**注册-设置密码 (RegisterPasswordActivity)：**
- 设置密码 + 确认密码

#### 3.1.3 密码找回流程

**找回密码-邮箱 (ForgetPasswordActivity)：**
- 输入邮箱

**找回密码-验证码 (PasswordVerificationActivity)：**
- 6位验证码输入

**找回密码-新密码 (PasswordNewSetActivity)：**
- 设置新密码

### 3.2 AI对话主界面 (ChatActivity)

**功能描述：**
- **主界面**：登录成功后直接进入
- 与AI进行智能对话，支持情感分析
- 管理对话历史记录
- 提供丰富的对话管理功能

**核心功能：**
- **AI对话**：发送消息到AI，接收情感分析后的回复
- **历史记录**：查看、搜索对话历史
- **新对话**：开启新的对话会话
- **删除对话**：删除特定对话记录
- **设置入口**：跳转到设置页面

**界面组件：**
- 消息列表区域
- 输入框和发送按钮
- 功能菜单（历史、删除、新对话、设置）

**技术实现要点：**
- 使用 `/voice` API接口进行AI对话
- 支持情感分析（detailed_emotion字段）
- 动态消息管理，支持滚动显示
- 网络请求异常处理

### 3.3 设置模块 (SettingsActivity)

**功能描述：**
- 应用设置主界面
- 提供角色卡片管理入口
- 包含退出账号功能

**界面元素：**
- 设置项列表
- 角色卡片管理入口按钮
- 退出账号按钮

### 3.4 角色卡片管理模块

#### 3.4.1 角色卡片列表 (CharacterListActivity)

**功能描述：**
- 显示所有已创建的角色卡片
- 支持新建角色卡片
- 点击头像进入角色详情或编辑界面

**核心逻辑：**
```kotlin
// 伪代码逻辑
fun onAvatarClick(character: Character) {
    if (character.isNew) {
        // 跳转到编辑界面
        startActivity(CharacterEditActivity::class.java)
    } else {
        // 跳转到详情界面
        startActivity(CharacterDetailActivity::class.java)
    }
}
```

**界面布局：**
- 顶部头像区域（新建/修改入口）
- 角色卡片网格/列表展示
- 每个卡片包含：头像、名称、简介

#### 3.4.2 角色编辑界面 (CharacterEditActivity)

**功能描述：**
- 创建新角色卡片
- 编辑现有角色信息
- 保存角色数据

**编辑字段：**
- 角色头像（支持上传/选择）
- 角色名称
- 角色描述/背景故事
- 角色属性设置
- 对话风格配置

**操作按钮：**
- 保存按钮
- 取消按钮
- 删除按钮（编辑模式下）

#### 3.4.3 角色详情界面 (CharacterDetailActivity)

**功能描述：**
- 查看角色完整信息
- 提供修改和对话功能入口

**界面元素：**
- 角色头像和基本信息展示
- 角色详细描述
- 修改按钮（跳转到编辑界面）
- 开始对话按钮（跳转到对话界面）

## 4. API 接口规范

### 4.1 基础配置

**基础 URL：**
```
http://bolank.asia:8091/api/
```

**请求格式：**
- 所有请求均使用 `application/json` 格式
- 通过 POST 方法提交

**响应格式：**
```json
{
    "code": 200,
    "msg": "操作成功",
    "data": {}
}
```

### 4.2 用户认证接口

#### 4.2.1 登录接口

- **URL**: `/login`
- **方法**: POST
- **请求参数**:
  | 字段 | 类型 | 描述 |
  |------|------|------|
  | email | String | 用户邮箱 |
  | password | String | 用户密码 |

- **响应示例**:
  ```json
  {
    "code": 200,
    "msg": "登录成功",
    "user": "user@example.com"
  }
  ```

#### 4.2.2 注册接口

- **URL**: `/register`
- **方法**: POST
- **请求参数**:
  | 字段 | 类型 | 描述 |
  |------|------|------|
  | email | String | 用户邮箱 |
  | password | String | 用户密码 |
  | code | String | 验证码 |

#### 4.2.3 密码重置接口

- **URL**: `/reset-password`
- **方法**: POST
- **请求参数**:
  | 字段 | 类型 | 描述 |
  |------|------|------|
  | email | String | 用户邮箱 |
  | newPassword | String | 新密码 |
  | code | String | 验证码 |

### 4.3 角色卡片管理接口

#### 4.3.1 获取角色列表

- **URL**: `/characters`
- **方法**: GET
- **响应示例**:
  ```json
  {
    "code": 200,
    "data": [
      {
        "id": "1",
        "name": "AI助手",
        "avatar": "avatar_url",
        "description": "智能助手角色"
      }
    ]
  }
  ```

#### 4.3.2 创建/更新角色

- **URL**: `/character`
- **方法**: POST/PUT
- **请求参数**:
  | 字段 | 类型 | 描述 |
  |------|------|------|
  | id | String | 角色ID（更新时必填） |
  | name | String | 角色名称 |
  | avatar | String | 头像URL |
  | description | String | 角色描述 |

#### 4.3.3 删除角色

- **URL**: `/character/{id}`
- **方法**: DELETE

### 4.4 对话管理接口

#### 4.4.1 发送AI对话消息

- **URL**: `/voice`
- **方法**: POST
- **请求参数**:
  | 字段 | 类型 | 描述 |
  |------|------|------|
  | text | String | 用户输入的文本消息 |
  | user_id | String | 用户ID |
  | device_id | String | 设备ID |

- **响应示例**:
  ```json
  {
    "code": 200,
    "emotion": {
      "detailed_emotion": "开心"
    },
    "reply": "AI回复内容"
  }
  ```

#### 4.4.2 获取设备数据

- **URL**: `/device/data`
- **方法**: GET
- **响应示例**:
  ```json
  {
    "code": 200,
    "data": {
      "online": true,
      "update_time": "2026-04-20 10:30:00",
      "data": "{\"temperature\":25.5,\"humidity\":60.0,\"light\":\"on\"}"
    }
  }
  ```

#### 4.4.3 发送设备指令

- **URL**: `/device/command`
- **方法**: POST
- **请求参数**:
  | 字段 | 类型 | 描述 |
  |------|------|------|
  | device_id | String | 设备ID |
  | command | String | 设备指令 |
  | user_id | String | 用户ID |

## 5. 技术实现细节

### 5.1 Activity跳转逻辑

```kotlin
// 入口流程
MainActivity → LoginActivity (选择登录)
MainActivity → RegisterActivity (选择注册)

// 登录流程
LoginActivity → ChatActivity (登录成功)

// 主界面功能
ChatActivity → SettingsActivity (设置入口)

// 角色管理流程
SettingsActivity → CharacterListActivity
CharacterListActivity → CharacterEditActivity (新建角色)
CharacterListActivity → CharacterDetailActivity (已有角色)

// 角色操作流程
CharacterDetailActivity → CharacterEditActivity (修改角色)
CharacterDetailActivity → ChatActivity (开始角色对话)

// 退出流程
SettingsActivity → LoginActivity (退出账号)
```

### 5.2 数据存储方案

**角色数据结构：**
```kotlin
data class Character(
    val id: String,
    val name: String,
    val avatar: String,
    val description: String,
    val createdAt: Long,
    val isNew: Boolean = false
)
```

**对话数据结构：**
```kotlin
data class Conversation(
    val id: String,
    val characterId: String,
    val messages: List<Message>,
    val createdAt: Long
)

data class Message(
    val id: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long
)
```

### 5.4 ChatActivity AI聊天实现细节

**核心功能实现：**

1. **AI对话接口调用**：使用 `/voice` API进行情感分析对话
```kotlin
val json = JSONObject().apply {
    put("text", text)
    put("user_id", username)
    put("device_id", "default") // 使用默认设备ID
}
```

2. **消息发送处理**：
```kotlin
private fun sendMessage() {
    val text = inputEditText.text.toString().trim()
    if (text.isEmpty()) {
        Toast.makeText(this, "请输入内容", Toast.LENGTH_SHORT).show()
        return
    }
    
    // 清空输入框
    inputEditText.text?.clear()
    
    // 添加用户消息到聊天历史
    addChatMessage("👤 $text", isUser = true)
    
    // 发送到AI接口
    sendToAI(text)
}
```

3. **AI回复处理**：
```kotlin
private fun handleAIResponse(response: JSONObject) {
    if (response.getInt("code") == 200) {
        val emotion = response.getJSONObject("emotion")
        val reply = response.getString("reply")
        val detailedEmotion = emotion.getString("detailed_emotion")
        
        // 添加AI回复到聊天历史
        addChatMessage("🤖 [$detailedEmotion] $reply", isUser = false)
    } else {
        addChatMessage("❌ 分析失败，请重试", isUser = false)
    }
}
```

4. **聊天界面管理**：动态添加消息，支持滚动到底部，消息历史管理

### 5.3 界面交互设计

**登录流程优化：**
- 登录成功后直接进入对话界面，减少中间页面
- 自动保存登录状态，下次启动直接进入对话界面

**状态管理：**
- 新建角色状态：显示空白表单，保存后标记为已创建
- 编辑角色状态：预填充现有数据，支持修改保存
- 详情查看状态：只读模式，提供操作按钮

**用户引导：**
- 首次使用时的空状态提示
- 操作成功/失败的Toast反馈
- 加载状态的进度指示器

## 6. 技术栈与开发环境

### 6.1 技术栈

- **开发语言**：Kotlin
- **开发环境**：Android Studio
- **UI 框架**：XML 布局 + Material Design
- **网络请求**：OkHttp
- **架构模式**：Activity 模式
- **数据存储**：SharedPreferences + 网络API

### 6.2 开发环境

- **Android Studio**：最新版本
- **Android SDK**：API 级别 21+（Android 5.0+）
- **构建工具**：Gradle
- **Kotlin版本**：1.8+

### 6.3 项目结构

```
iot/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/iot/
│   │   │   │   ├── CharacterDetailActivity.kt
│   │   │   │   ├── CharacterEditActivity.kt
│   │   │   │   ├── CharacterListActivity.kt
│   │   │   │   ├── ChatActivity.kt
│   │   │   │   ├── ForgetPasswordActivity.kt
│   │   │   │   ├── LoginActivity.kt
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── PasswordNewSetActivity.kt
│   │   │   │   ├── PasswordVerificationActivity.kt
│   │   │   │   ├── RegisterActivity.kt
│   │   │   │   ├── RegisterCodeActivity.kt
│   │   │   │   ├── RegisterPasswordActivity.kt
│   │   │   │   ├── SettingsActivity.kt
│   │   │   │   └── SessionsActivity.kt
│   │   │   ├── res/
│   │   │   │   ├── drawable/
│   │   │   │   ├── layout/
│   │   │   │   │   ├── activity_character_detail.xml
│   │   │   │   │   ├── activity_character_edit.xml
│   │   │   │   │   ├── activity_character_list.xml
│   │   │   │   │   ├── activity_chat.xml
│   │   │   │   │   ├── activity_forget_password.xml
│   │   │   │   │   ├── activity_login.xml
│   │   │   │   │   ├── activity_main.xml
│   │   │   │   │   ├── activity_password_new_set.xml
│   │   │   │   │   ├── activity_password_verification.xml
│   │   │   │   │   ├── activity_register.xml
│   │   │   │   │   ├── activity_register_code.xml
│   │   │   │   │   ├── activity_register_password.xml
│   │   │   │   │   ├── activity_settings.xml
│   │   │   │   │   └── top_use_all.xml
│   │   │   │   ├── mipmap-*/
│   │   │   │   ├── values/
│   │   │   │   └── xml/
│   │   │   └── AndroidManifest.xml
│   │   ├── androidTest/
│   │   └── test/
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
└── settings.gradle.kts
```

## 7. 测试与质量保证

### 7.1 功能测试

- [ ] 登录流程正确性（直接跳转ChatActivity）
- [ ] 注册流程完整性
- [ ] 密码找回功能正确性
- [ ] 角色创建流程完整性
- [ ] 编辑保存功能正确性
- [ ] 状态跳转逻辑准确性
- [ ] AI对话功能稳定性
- [ ] 消息发送和接收功能
- [ ] 聊天历史管理功能
- [ ] API接口连通性（/voice接口）

### 7.2 用户体验测试

- [ ] 界面响应速度
- [ ] 操作流程顺畅度
- [ ] 错误处理友好性
- [ ] 界面布局适配性

### 7.3 性能测试

- [ ] 内存使用优化
- [ ] 网络请求效率
- [ ] 界面渲染性能

## 8. 部署与维护

### 8.1 注意事项

1. **UI 一致性**：确保所有界面使用统一的设计风格，包括颜色、字体、按钮样式等。

2. **用户体验**：
   - 提供清晰的错误提示
   - 实现加载状态显示
   - 优化输入体验，如自动填充、输入验证等

3. **安全性**：
   - 密码加密存储
   - 网络请求使用 HTTPS
   - 验证码防刷机制

4. **性能优化**：
   - 减少布局层级
   - 优化图片资源
   - 避免主线程阻塞

5. **兼容性**：
   - 适配不同屏幕尺寸
   - 支持 Android 5.0+ 系统

6. **代码质量**：
   - 遵循 Kotlin 编码规范
   - 模块化设计
   - 充分注释

### 8.2 后续工作

1. 完善代码实现，修复现有问题
2. 实现网络请求和数据存储
3. 添加设备控制和 AI 对话功能
4. 进行全面的测试和优化
5. 准备发布到应用商店

## 9. 更新日志

### v1.2 (2026-04-20)
- 调整架构：登录后直接跳转ChatActivity，暂时忽略HomeActivity
- 专注于AI聊天功能实现
- 更新ChatActivity作为主界面的详细说明
- 提供AI聊天的具体实现代码示例

### v1.1 (2026-04-20)
- 修正AI聊天API接口：使用 `/voice` 而非 `/chat/send`
- 更新HomeActivity实现说明：确认其为IoT设备控制主界面
- 添加设备数据监控和指令控制API
- 修正Activity跳转逻辑：登录后跳转HomeActivity

### v1.0 (2026-04-20)
- 整合原有三个技术文档
- 完善系统架构和功能模块
- 添加完整的API接口规范
- 优化项目结构和部署说明

---

**文档版本：** v1.0  
**最后更新：** 2026-04-20  
**维护人员：** 开发团队