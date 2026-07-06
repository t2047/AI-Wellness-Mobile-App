# Wellness App — Android 更新与问题记录

## 2026-07-06 更新

- 
- 同步更新了项目所有文档

### 修复

---

#### 1. 注册后导航到旧页面（已修复）

**问题**: `RegisterActivity` 注册成功后的 `navigateToMain()` 跳转到 `HealthRecordActivity`（旧版单页界面），而不是 `MainActivity`（新版 5 标签底部导航界面）。用户注册登录后进入的是过时界面，部分功能（如底部导航标签）不可用。

**根因**: `RegisterActivity.kt` 中 `navigateToMain()` 的 Intent 目标写死为 `HealthRecordActivity::class.java`。

**修复**: 将 Intent 目标改为 `MainActivity::class.java`。

**涉及文件**:
- `RegisterActivity.kt` — `import` 和 `navigateToMain()` 方法中的引用均改为 `MainActivity`

---

#### 2. `PlainLogin` 主题使用 Android 原生父类导致闪退（已修复）

**问题**: 应用在启动 `LoginActivity` 时闪退（白屏后崩溃）。

**根因**: `themes.xml` 中 `Theme.WellnessApp.PlainLogin` 的父主题为 `@android:style/Theme.Material.Light.NoActionBar`，这是一个 **Android 平台原生主题**（`android:style/` 前缀）。但 `activity_login.xml` 布局中使用了 Material Components 控件（`MaterialButton`, `TextInputLayout`, `TextInputEditText`），这些控件**必须**使用继承自 `Theme.MaterialComponents.*` 的主题才能在运行时成功 inflate。使用原生主题会导致 `ClassCastException`。

**修复**: 将父主题改为 `Theme.MaterialComponents.Light.NoActionBar`，并同步调整了 color 属性声明。

**涉及文件**:
- `res/values/themes.xml` — `PlainLogin` 父主题从 `@android:style/Theme.Material.Light.NoActionBar` 改为 `Theme.MaterialComponents.Light.NoActionBar`

---

#### 3. LoginActivity 重复设置主题（已修复）

**问题**: `LoginActivity.onCreate()` 中调用了 `setTheme(com.wellnessapp.R.style.Theme_WellnessApp)`，但 AndroidManifest 中已为该 Activity 声明了 `android:theme="@style/Theme.WellnessApp.PlainLogin"`。代码中的 `setTheme` 覆盖了 Manifest 的声明，导致 Manifest 的主题声明失效。

**修复**: 删除了 `setTheme()` 调用，所有 Activity 的主题由 AndroidManifest 统一管理。

**涉及文件**:
- `LoginActivity.kt` — 移除第 29 行 `setTheme(com.wellnessapp.R.style.Theme_WellnessApp)`

---

#### 4. Settings 标签导致 BottomNavigationView 闪退（已修复）

**问题**: `MainActivity` 启动时闪退，报错 `java.lang.IllegalArgumentException: Maximum number of items supported by BottomNavigationView is 5`。

**根因**: `menu_bottom_nav.xml` 中包含了 6 个 `<item>`（Dashboard, Records, Coach, Chat, Knowledge, Settings），但 Android Material Components 的 `BottomNavigationView` 最多只支持 **5 个标签**。

**修复**: 将 Settings 从底部导航移除，改为顶部工具栏（Top Bar）中的齿轮图标按钮。

**涉及文件**:
- `res/menu/menu_bottom_nav.xml` — 移除 `<item android:id="@+id/action_settings"`
- `res/layout/activity_main.xml` — 在 `btnLogout` 左侧新增 `btnSettings` 图标按钮
- `res/values/ids.xml` — 新建文件，声明 `action_settings` 的 `id` 类型资源
- `MainActivity.kt` — `setupGlobalHeader()` 添加 `btnSettings` 点击监听（切换到 Settings Fragment）；`selectTab()` 处理 Settings 作为特例（不操作底部导航）；`showTab()` 新增 `uncheckBottomNavigation()` 清除底部导航高亮；`onCreate()` 恢复状态时正确处理 Settings

---

### API 新增

详见 `docs/api-spec.md` 中 `/api/model-config` 端点。

---

### 新增功能

#### 5. 用户可在 App 中直接配置并保存 AI 模型参数（新增）

**描述**: 用户可以在 Android 应用的 Settings 标签页中，自由配置自己的 AI 模型 `base_url`、`api_key`、`model_name` 和 `provider`。配置保存后，后端的 ChatService 会在 Tier-2 fallback 中优先使用用户的个性化配置；若用户未配置，则降级为全局配置。

---

##### 5.1 数据库

**新增表**: `user_model_configs`

```sql
CREATE TABLE IF NOT EXISTS user_model_configs (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    provider_name VARCHAR(50) NOT NULL COMMENT 'openai, deepseek, doubao, custom',
    base_url    VARCHAR(500) NOT NULL COMMENT 'API endpoint base URL',
    api_key     VARCHAR(500) NOT NULL COMMENT 'Encrypted API key',
    model_name  VARCHAR(100) NOT NULL COMMENT 'Model identifier (e.g. gpt-4o-mini)',
    is_active   BOOLEAN DEFAULT TRUE,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_model_config_user_id (user_id),
    INDEX idx_model_config_provider (user_id, provider_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**涉及文件**: `database/schema.sql`

---

##### 5.2 后端 — 新增文件

| 文件 | 类 | 说明 |
|------|-----|------|
| `entity/UserModelConfig.java` | `UserModelConfig` | JPA 实体，映射 `user_model_configs` 表 |
| `repository/UserModelConfigRepository.java` | `UserModelConfigRepository` | Spring Data 仓库，支持按用户/供应商查询 |
| `dto/ModelConfigDTOs.java` | `ModelConfigRequest`, `ModelConfigResponse` | 请求/响应 DTO，API key 在响应中自动脱敏 |
| `service/UserModelConfigService.java` | `UserModelConfigService` | CRUD 业务逻辑：列表、新增/更新、删除、激活 |
| `controller/UserModelConfigController.java` | `UserModelConfigController` | REST 控制器，5 个端点 |

---

##### 5.3 后端 — 新增 API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET`    | `/api/model-config`           | 列出当前用户所有配置 |
| `GET`    | `/api/model-config/active`     | 获取当前激活的配置 |
| `POST`   | `/api/model-config`            | 创建或更新配置（自动设为 active） |
| `DELETE` | `/api/model-config/{id}`       | 删除指定配置 |
| `PUT`    | `/api/model-config/{id}/activate` | 激活指定配置（自动停用其他） |

所有端点均需要 JWT 认证。

---

##### 5.4 后端 — 修改文件

| 文件 | 变更 |
|------|------|
| `service/AIClientService.java` | 新增 `chatWithUserConfig(apiKey, baseUrl, model, messages, maxTokens)` 公共方法，允许使用用户自定义凭据调用任意 OpenAI 兼容 API |
| `service/ChatService.java` | Tier-2 fallback 逻辑增强：先检查用户是否有自定义激活配置 (`UserModelConfigService.getActiveConfigEntity`)，有则优先使用用户的 `apiKey`/`baseUrl`/`modelName`，失败后降级到全局配置 |

**数据流**:

```
用户 Chat 消息 →
  ChatService.processMessage()
    ├── Tier-1: Python Agent RAG (优先)
    ├── Tier-2: 用户自定义配置？
    │              ├── 有 → 用用户的 apiKey/baseUrl/model 调用 AI
    │              └── 无 → 用全局 .env 配置
    └── Tier-3: 静态 fallback
```

---

##### 5.5 Android — 新增文件

| 文件 | 说明 |
|------|------|
| `res/drawable/ic_settings.xml` | Settings 标签页的矢量图标 |
| `res/layout/fragment_settings.xml` | 设置页面布局：配置列表 + 新增/编辑表单 |
| `ui/main/fragments/SettingsFragment.kt` | 设置页 Fragment：加载配置列表、保存/激活/删除配置 |

---

##### 5.6 Android — 修改文件

| 文件 | 变更 |
|------|------|
| `data/model/Models.kt` | 新增 `ModelConfigRequest`, `ModelConfigResponse` 数据类 |
| `data/api/ApiService.kt` | 新增 `getModelConfigs()`, `getActiveModelConfig()`, `saveModelConfig()`, `deleteModelConfig()`, `activateModelConfig()` 5 个端点 |
| `res/menu/menu_bottom_nav.xml` | 回退到 5 个标签，移除 Settings（改放顶部工具栏） |
| `res/layout/activity_main.xml` | 顶部工具栏新增 `btnSettings` 齿轮图标按钮 |
| `res/values/ids.xml` | **新文件** — 声明 `R.id.action_settings` ID 供 Fragment 切换使用 |
| `res/values/strings.xml` | 新增设置相关字符串资源（`nav_settings`, `model_config`, `save_config` 等） |
| `ui/main/MainActivity.kt` | import `SettingsFragment`；`setupGlobalHeader()` 绑定 `btnSettings` 点击事件；`selectTab()` 特判 Settings（不操作底部导航）；新增 `uncheckBottomNavigation()` 方法 |

---

##### 5.7 用户使用流程

```
用户打开 App → 进入 Settings 标签
  ├── 看到已保存的配置列表（如为空则显示 "No saved configurations"）
  ├── 填写 Provider / Base URL / API Key / Model Name
  ├── 点击 Save Configuration
  ├── 新配置自动激活（之前的 active 配置自动停用）
  └── 配置生效 → Chat 时使用用户的 API key 和 endpoint
```

## 附录：Android 架构参考

### 页面导航流

```
LoginActivity
    ├── Login 成功 → MainActivity (5-tab bottom nav + top bar)
    │                    ├── [Bottom Nav] DashboardFragment (健康看板)
    │                    ├── [Bottom Nav] RecordsFragment (记录列表)
    │                    ├── [Bottom Nav] CoachFragment (AI 建议)
    │                    ├── [Bottom Nav] ChatFragment (聊天)
    │                    ├── [Bottom Nav] KnowledgeFragment (知识库/RAG)
    │                    └── [Top Bar ⚙] SettingsFragment (模型配置)
    │
    └── Register → RegisterActivity
                    └── 注册成功 → MainActivity

其他独立 Activity:
    ├── ChatActivity (聊天 — 独立版，含语音输入)
    ├── RecommendationActivity (推荐列表)
    ├── AnalyticsActivity (数据分析仪表盘)
    ├── WeeklySummaryActivity (周总结)
    └── RagActivity (知识库查询 — 独立版)
```

### 主题结构

```
Theme.MaterialComponents.Light.NoActionBar
    └── Theme.WellnessApp                  ← 应用默认主题
            └── Theme.WellnessApp.PlainLogin   ← LoginActivity 专用
```

> **规则**: 所有 Activity 的主题统一在 `AndroidManifest.xml` 中声明，不在代码中调用 `setTheme()`。

### 常见闪退排查

| 原因 | 症状 | 排查方法 |
|------|------|----------|
| BottomNavigationView 超过 5 项 | 启动闪退，`IllegalArgumentException` | 检查 `menu_bottom_nav.xml` 中 `<item>` 数量 ≤ 5 |
| Material Components + 原生主题 | 启动白屏后崩溃 | 检查主题 parent 是否含 `MaterialComponents` |
| `setTheme()` 覆盖 Manifest | 主题不一致 | 全局搜索 `setTheme(` |
| Kotlin 重定义 | 编译报错 | `.\gradlew compileDebugKotlin` |
| Activity 未注册 | 运行时崩溃 | 检查 AndroidManifest |
| Layout ID 不匹配 | NullPointerException | 检查 Binding 类与 layout 文件 ID | 