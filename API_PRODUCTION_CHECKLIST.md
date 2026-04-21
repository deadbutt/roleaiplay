# IoT 应用后端API生产清单

## 概述

本文档列出了IoT应用需要实现的后端API接口，包括角色卡管理、对话管理、会话管理等功能。

## 现有API（已实现）

| 接口 | 功能 | 状态 |
|------|------|------|
| `/api/login` | 用户登录 | ✅ 已实现 |
| `/api/register/code` | 用户注册 | ✅ 已实现 |
| `/api/verify/send` | 发送验证码 | ✅ 已实现 |
| `/api/verify/check` | 验证验证码 | ✅ 已实现 |
| `/api/reset/password` | 重置密码 | ✅ 已实现 |
| `/api/voice` | AI对话 | ✅ 已实现 |
| `/api/device/data` | 获取设备数据 | ✅ 已实现 |
| `/api/device/cmd` | 发送设备指令 | ✅ 已实现 |

## 新增API清单

### 1. 用户管理

#### 1.1 获取用户信息
- **URL**: `/api/user/info`
- **方法**: GET
- **Header**: `Authorization: Bearer {token}`
- **响应示例**:
```json
{
  "code": 200,
  "msg": "获取成功",
  "data": {
    "email": "user@example.com",
    "username": "用户名",
    "created_at": "2026-04-20"
  }
}
```

#### 1.2 更新用户信息
- **URL**: `/api/user/update`
- **方法**: POST
- **Header**: `Authorization: Bearer {token}`
- **请求参数**:
| 字段 | 类型 | 描述 |
|------|------|------|
| username | String | 用户名 |

- **响应示例**:
```json
{
  "code": 200,
  "msg": "更新成功"
}
```

#### 1.3 登出账号
- **URL**: `/api/user/logout`
- **方法**: POST
- **Header**: `Authorization: Bearer {token}`
- **响应示例**:
```json
{
  "code": 200,
  "msg": "登出成功"
}
```

---

### 2. 角色卡管理

#### 2.1 创建角色卡
- **URL**: `/api/character/create`
- **方法**: POST
- **Header**: `Authorization: Bearer {token}`
- **说明**: 创建角色卡,用于在对话时作为Ollama的系统提示词(角色名+描述+环境设定)
- **请求参数**:
| 字段 | 类型 | 描述 |
|------|------|------|
| name | String | 角色名称 |
| description | String | 角色描述(性格、外貌、背景等) |
| scenario | String | 环境设定(场景、世界观等) |
| avatar_url | String | 头像URL |

- **响应示例**:
```json
{
  "code": 200,
  "msg": "创建成功",
  "data": {
    "id": "角色卡ID",
    "name": "角色名称",
    "description": "角色描述",
    "scenario": "环境设定",
    "avatar_url": "头像URL",
    "created_at": "2026-04-20"
  }
}
```

> **提示词构建方式**: 对话时后端会将角色信息组合为Ollama的系统提示词:
> ```
> 你是{name}。{description}\n\n当前场景: {scenario}
> ```

#### 2.2 获取角色卡列表
- **URL**: `/api/character/list`
- **方法**: GET
- **Header**: `Authorization: Bearer {token}`
- **响应示例**:
```json
{
  "code": 200,
  "msg": "获取成功",
  "data": [
    {
      "id": "角色卡ID",
      "name": "角色名称",
      "description": "角色描述",
      "scenario": "环境设定",
      "avatar_url": "头像URL",
      "created_at": "2026-04-20"
    }
  ]
}
```

#### 2.3 获取角色卡详情
- **URL**: `/api/character/detail/{id}`
- **方法**: GET
- **Header**: `Authorization: Bearer {token}`
- **响应示例**:
```json
{
  "code": 200,
  "msg": "获取成功",
  "data": {
    "id": "角色卡ID",
    "name": "角色名称",
    "description": "角色描述",
    "scenario": "环境设定",
    "avatar_url": "头像URL",
    "created_at": "2026-04-20"
  }
}
```

#### 2.4 更新角色卡
- **URL**: `/api/character/update/{id}`
- **方法**: PUT
- **Header**: `Authorization: Bearer {token}`
- **请求参数**:
| 字段 | 类型 | 描述 |
|------|------|------|
| name | String | 角色名称 |
| description | String | 角色描述 |
| scenario | String | 环境设定 |
| avatar_url | String | 头像URL |

- **响应示例**:
```json
{
  "code": 200,
  "msg": "更新成功"
}
```

#### 2.5 删除角色卡
- **URL**: `/api/character/delete/{id}`
- **方法**: DELETE
- **Header**: `Authorization: Bearer {token}`
- **响应示例**:
```json
{
  "code": 200,
  "msg": "删除成功"
}
```

---

### 3. 对话管理

#### 3.1 发送消息（带角色卡ID）
- **URL**: `/api/chat/send`
- **方法**: POST
- **Header**: `Authorization: Bearer {token}`
- **请求参数**:
| 字段 | 类型 | 描述 |
|------|------|------|
| text | String | 用户输入的文本 |
| user_id | String | 用户ID |
| character_id | String | 角色卡ID（可选） |
| session_id | String | 会话ID（用于关联对话历史） |

- **响应示例**:
```json
{
  "code": 200,
  "msg": "发送成功",
  "data": {
    "reply": "AI回复内容",
    "session_id": "会话ID"
  }
}
```

#### 3.2 创建新会话
- **URL**: `/api/chat/session/create`
- **方法**: POST
- **Header**: `Authorization: Bearer {token}`
- **请求参数**:
| 字段 | 类型 | 描述 |
|------|------|------|
| title | String | 会话标题 |
| character_id | String | 角色卡ID |

- **响应示例**:
```json
{
  "code": 200,
  "msg": "创建成功",
  "data": {
    "session_id": "会话ID",
    "title": "会话标题",
    "character_id": "角色卡ID",
    "created_at": "2026-04-20"
  }
}
```

#### 3.3 获取会话列表
- **URL**: `/api/chat/session/list`
- **方法**: GET
- **Header**: `Authorization: Bearer {token}`
- **响应示例**:
```json
{
  "code": 200,
  "msg": "获取成功",
  "data": {
    "pinned": [
      {
        "session_id": "会话ID",
        "title": "置顶会话标题",
        "character_name": "角色名称",
        "preview": "预览内容",
        "last_message_time": "2026-04-20 12:00:00",
        "is_pinned": true
      }
    ],
    "today": [
      {
        "session_id": "会话ID",
        "title": "今天会话标题",
        "character_name": "角色名称",
        "preview": "预览内容",
        "last_message_time": "2026-04-20 10:00:00",
        "is_pinned": false
      }
    ],
    "yesterday": [
      {
        "session_id": "会话ID",
        "title": "昨天会话标题",
        "character_name": "角色名称",
        "preview": "预览内容",
        "last_message_time": "2026-04-19 15:00:00",
        "is_pinned": false
      }
    ]
  }
}
```

#### 3.4 获取会话详情
- **URL**: `/api/chat/session/detail/{session_id}`
- **方法**: GET
- **Header**: `Authorization: Bearer {token}`
- **响应示例**:
```json
{
  "code": 200,
  "msg": "获取成功",
  "data": {
    "session_id": "会话ID",
    "title": "会话标题",
    "character_id": "角色卡ID",
    "messages": [
      {
        "id": "消息ID",
        "content": "消息内容",
        "is_user": true,
        "created_at": "2026-04-20 12:00:00"
      }
    ]
  }
}
```

#### 3.5 更新会话
- **URL**: `/api/chat/session/update/{session_id}`
- **方法**: PUT
- **Header**: `Authorization: Bearer {token}`
- **请求参数**:
| 字段 | 类型 | 描述 |
|------|------|------|
| title | String | 会话标题 |
| is_pinned | Boolean | 是否置顶 |

- **响应示例**:
```json
{
  "code": 200,
  "msg": "更新成功"
}
```

#### 3.6 删除会话
- **URL**: `/api/chat/session/delete/{session_id}`
- **方法**: DELETE
- **Header**: `Authorization: Bearer {token}`
- **响应示例**:
```json
{
  "code": 200,
  "msg": "删除成功"
}
```

#### 3.7 删除所有会话
- **URL**: `/api/chat/session/delete-all`
- **方法**: DELETE
- **Header**: `Authorization: Bearer {token}`
- **响应示例**:
```json
{
  "code": 200,
  "msg": "删除所有会话成功"
}
```

#### 3.8 搜索会话
- **URL**: `/api/chat/session/search`
- **方法**: GET
- **Header**: `Authorization: Bearer {token}`
- **参数**:
| 字段 | 类型 | 描述 |
|------|------|------|
| keyword | String | 搜索关键词 |

- **响应示例**:
```json
{
  "code": 200,
  "msg": "搜索成功",
  "data": [
    {
      "session_id": "会话ID",
      "title": "会话标题",
      "character_name": "角色名称",
      "preview": "预览内容",
      "last_message_time": "2026-04-20 12:00:00"
    }
  ]
}
```

---

### 4. 记忆管理

#### 4.1 获取记忆列表
- **URL**: `/api/memory/list`
- **方法**: GET
- **Header**: `Authorization: Bearer {token}`
- **参数**:
| 字段 | 类型 | 描述 |
|------|------|------|
| character_id | String | 角色卡ID(必填) |
| category | String | 分类筛选(可选): event/state/relationship |

- **响应示例**:
```json
{
  "code": 200,
  "msg": "获取成功",
  "data": [
    {
      "id": "记忆ID",
      "content": "记忆内容",
      "category": "event",
      "enabled": true,
      "created_at": "2026-04-20 12:00:00"
    }
  ]
}
```

#### 4.2 添加记忆
- **URL**: `/api/memory/add`
- **方法**: POST
- **Header**: `Authorization: Bearer {token}`
- **请求参数**:
| 字段 | 类型 | 描述 |
|------|------|------|
| character_id | String | 角色卡ID |
| content | String | 记忆内容 |
| category | String | 分类: event/state/relationship |

- **响应示例**:
```json
{
  "code": 200,
  "msg": "添加成功",
  "data": {
    "id": "记忆ID"
  }
}
```

#### 4.3 更新记忆
- **URL**: `/api/memory/update/{id}`
- **方法**: PUT
- **Header**: `Authorization: Bearer {token}`
- **请求参数**:
| 字段 | 类型 | 描述 |
|------|------|------|
| content | String | 记忆内容 |
| enabled | Boolean | 是否启用 |

- **响应示例**:
```json
{
  "code": 200,
  "msg": "更新成功"
}
```

#### 4.4 删除记忆
- **URL**: `/api/memory/delete/{id}`
- **方法**: DELETE
- **Header**: `Authorization: Bearer {token}`
- **响应示例**:
```json
{
  "code": 200,
  "msg": "删除成功"
}
```

#### 4.5 清空角色记忆
- **URL**: `/api/memory/clear/{character_id}`
- **方法**: DELETE
- **Header**: `Authorization: Bearer {token}`
- **响应示例**:
```json
{
  "code": 200,
  "msg": "清空成功"
}
```

---

## 数据库表设计

### 1. 角色卡表 (characters)

```sql
CREATE TABLE characters (
    id VARCHAR(36) PRIMARY KEY COMMENT '角色卡ID',
    user_id VARCHAR(36) NOT NULL COMMENT '用户ID',
    name VARCHAR(100) NOT NULL COMMENT '角色名称',
    description TEXT COMMENT '角色描述(性格、外貌、背景等)',
    scenario TEXT COMMENT '环境设定(场景、世界观等)',
    avatar_url VARCHAR(255) COMMENT '头像URL',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
);
```

### 2. 会话表 (conversations)

```sql
CREATE TABLE conversations (
    id VARCHAR(36) PRIMARY KEY COMMENT '会话ID',
    user_id VARCHAR(36) NOT NULL COMMENT '用户ID',
    character_id VARCHAR(36) COMMENT '角色卡ID',
    title VARCHAR(200) COMMENT '会话标题',
    is_pinned BOOLEAN DEFAULT FALSE COMMENT '是否置顶',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    last_message_at TIMESTAMP COMMENT '最后消息时间'
);
```

### 3. 消息表 (messages)

```sql
CREATE TABLE messages (
    id VARCHAR(36) PRIMARY KEY COMMENT '消息ID',
    conversation_id VARCHAR(36) NOT NULL COMMENT '会话ID',
    user_id VARCHAR(36) NOT NULL COMMENT '用户ID',
    content TEXT NOT NULL COMMENT '消息内容',
    is_user BOOLEAN DEFAULT TRUE COMMENT 'true=用户, false=AI',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
);
```

### 4. 用户表 (users) - 可能已存在

```sql
CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY COMMENT '用户ID',
    email VARCHAR(100) UNIQUE NOT NULL COMMENT '邮箱',
    username VARCHAR(100) COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码',
    avatar_url VARCHAR(255) COMMENT '头像URL',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
);
```

### 5. 记忆表 (memories)

```sql
CREATE TABLE memories (
    id VARCHAR(36) PRIMARY KEY COMMENT '记忆ID',
    user_id VARCHAR(36) NOT NULL COMMENT '用户ID',
    character_id VARCHAR(36) NOT NULL COMMENT '角色卡ID',
    content TEXT NOT NULL COMMENT '记忆内容',
    category VARCHAR(20) COMMENT '分类: event/state/relationship',
    enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_character (user_id, character_id)
);
```

---

## 权限验证

所有新增API都需要进行Token验证，建议使用JWT：

```
Authorization: Bearer {token}
```

### Token获取方式

1. 用户登录成功后，后端生成JWT返回
2. 前端保存Token到SharedPreferences
3. 后续请求在Header中携带Token

### Token验证失败响应

```json
{
  "code": 401,
  "msg": "Token无效或已过期"
}
```

---

## 实现优先级

| 优先级 | API | 说明 |
|--------|-----|------|
| P0 | `/api/user/logout` | 登出功能 |
| P0 | `/api/character/list` | 角色卡列表 |
| P0 | `/api/character/create` | 创建角色卡 |
| P1 | `/api/character/detail/{id}` | 角色卡详情 |
| P1 | `/api/character/update/{id}` | 更新角色卡 |
| P1 | `/api/character/delete/{id}` | 删除角色卡 |
| P1 | `/api/chat/send` | 发送消息（带角色卡ID,注入记忆） |
| P1 | `/api/chat/session/create` | 创建新会话 |
| P1 | `/api/chat/session/list` | 获取会话列表 |
| P2 | `/api/chat/session/detail/{session_id}` | 获取会话详情 |
| P2 | `/api/chat/session/update/{session_id}` | 更新会话 |
| P2 | `/api/chat/session/delete/{session_id}` | 删除会话 |
| P2 | `/api/chat/session/delete-all` | 删除所有会话 |
| P2 | `/api/chat/session/search` | 搜索会话 |
| P2 | `/api/memory/list` | 获取记忆列表 |
| P2 | `/api/memory/add` | 添加记忆 |
| P3 | `/api/memory/update/{id}` | 更新记忆 |
| P3 | `/api/memory/delete/{id}` | 删除记忆 |
| P3 | `/api/memory/clear/{character_id}` | 清空角色记忆 |
| P3 | `/api/user/info` | 获取用户信息 |
| P3 | `/api/user/update` | 更新用户信息 |

---

## 注意事项

1. **Token验证**: 所有新增API都需要验证Token
2. **用户隔离**: 所有数据都需要关联user_id，确保数据隔离
3. **分页支持**: 会话列表和角色卡列表需要考虑分页
4. **搜索优化**: 搜索接口需要优化性能
5. **软删除**: 建议使用软删除而不是物理删除
6. **时间排序**: 会话列表需要按last_message_time排序