# 更新与问题记录

> **文件位置**: `android/updates.md` (Android 端) 和 `docs/updates.md` (汇总)
> 最后更新: 2026-07-06

## 2026-07-06 更新

### 修复

---

#### 1. 注册后导航到旧页面（已修复）

**问题**: `RegisterActivity` 注册成功后的 `navigateToMain()` 跳转到 `HealthRecordActivity`（旧版单页界面），而不是 `MainActivity`（新版 5 标签底部导航界面）。用户注册登录后进入的是过时界面，部分功能（如底部导航标签）不可用。

**根因**: `RegisterActivity.kt` 中 `navigateToMain()` 的 Intent 目标写死为 `HealthRecordActivity::class.java`。

**修复**: 将 Intent 目标改为 `MainActivity::class.java`。

**涉及文件**:
- `android/.../RegisterActivity.kt` — `import` 和 `navigateToMain()` 方法中的引用均改为 `MainActivity`

---

#### 2. `PlainLogin` 主题使用 Android 原生父类导致闪退（已修复）

**问题**: 应用在启动 `LoginActivity` 时闪退（白屏后崩溃）。

**根因**: `themes.xml` 中 `Theme.WellnessApp.PlainLogin` 的父主题为 `@android:style/Theme.Material.Light.NoActionBar`，这是一个 **Android 平台原生主题**（`android:style/` 前缀）。但 `activity_login.xml` 布局中使用了 Material Components 控件（`MaterialButton`, `TextInputLayout`, `TextInputEditText`），这些控件**必须**使用继承自 `Theme.MaterialComponents.*` 的主题才能在运行时成功 inflate。使用原生主题会导致 `ClassCastException`。

**修复**: 将父主题改为 `Theme.MaterialComponents.Light.NoActionBar`，并同步调整了 color 属性声明。

**涉及文件**:
- `android/.../res/values/themes.xml` — `PlainLogin` 父主题从 `@android:style/Theme.Material.Light.NoActionBar` 改为 `Theme.MaterialComponents.Light.NoActionBar`

---

#### 3. LoginActivity 重复设置主题（已修复）

**问题**: `LoginActivity.onCreate()` 中调用了 `setTheme(Theme_WellnessApp)`，但 AndroidManifest 中已为该 Activity 声明了 `android:theme="@style/Theme.WellnessApp.PlainLogin"`。

**修复**: 删除了 `setTheme()` 调用，所有 Activity 的主题由 AndroidManifest 统一管理。

**涉及文件**:
- `android/.../LoginActivity.kt` — 移除 `setTheme()` 调用

---

#### 4. Settings 标签导致 BottomNavigationView 闪退（已修复）

**问题**: `MainActivity` 启动时闪退，报错 `java.lang.IllegalArgumentException: Maximum number of items supported by BottomNavigationView is 5`。

**根因**: `menu_bottom_nav.xml` 中包含了 6 个 `<item>`（Dashboard, Records, Coach, Chat, Knowledge, Settings），但 Android Material Components 的 `BottomNavigationView` 最多只支持 **5 个标签**。

**修复**: 将 Settings 从底部导航移除，改为顶部工具栏中的齿轮图标按钮。

**涉及文件**:
- `android/.../res/menu/menu_bottom_nav.xml` — 移除 Settings 标签，回退到 5 项
- `android/.../res/layout/activity_main.xml` — 顶部工具栏新增 `btnSettings` 图标按钮
- `android/.../res/values/ids.xml` — 新建文件，声明 `action_settings` ID
- `android/.../MainActivity.kt` — 绑定 `btnSettings` 点击事件；`selectTab()`/`showTab()` 正确处理 Settings 切换；新增 `uncheckBottomNavigation()` 方法

---

### 新增功能

#### 5. 用户可在 App 中直接配置并保存 AI 模型参数（新增）

**描述**: 用户可以在 Android 应用的 Settings 标签页中，自由配置自己的 AI 模型 `base_url`、`api_key`、`model_name` 和 `provider`。配置保存后，后端的 ChatService 会在 Tier-2 fallback 中优先使用用户的个性化配置；若用户未配置，则降级为全局配置。

##### 5.1 数据库

**新增表**: `user_model_configs` — 见 `database/schema.sql`

##### 5.2 后端新增文件

| 文件 | 说明 |
|------|------|
| `backend/.../entity/UserModelConfig.java` | JPA 实体 |
| `backend/.../repository/UserModelConfigRepository.java` | Spring Data 仓库 |
| `backend/.../dto/ModelConfigDTOs.java` | 请求/响应 DTO（API key 脱敏） |
| `backend/.../service/UserModelConfigService.java` | CRUD 业务逻辑 |
| `backend/.../controller/UserModelConfigController.java` | REST 控制器 |

##### 5.3 后端新增 API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET`    | `/api/model-config` | 列出所有配置 |
| `GET`    | `/api/model-config/active` | 获取当前激活的配置 |
| `POST`   | `/api/model-config` | 创建/更新配置 |
| `DELETE` | `/api/model-config/{id}` | 删除配置 |
| `PUT`    | `/api/model-config/{id}/activate` | 激活配置 |

##### 5.4 后端修改文件

| 文件 | 变更 |
|------|------|
| `service/AIClientService.java` | 新增 `chatWithUserConfig()` 方法 |
| `service/ChatService.java` | Tier-2 优先使用用户自定义配置 |

##### 5.5 Android 新增文件

| 文件 | 说明 |
|------|------|
| `res/drawable/ic_settings.xml` | Settings 图标 |
| `res/layout/fragment_settings.xml` | 设置页面布局 |
| `ui/main/fragments/SettingsFragment.kt` | 设置页 Fragment |

##### 5.6 Android 修改文件

| 文件 | 变更 |
|------|------|
| `data/model/Models.kt` | 新增模型配置数据类 |
| `data/api/ApiService.kt` | 新增 5 个端点 |
| `res/menu/menu_bottom_nav.xml` | 回退到 5 个标签，移除 Settings 移至顶部工具栏 |
| `res/layout/activity_main.xml` | 顶部工具栏新增 `btnSettings` 齿轮图标按钮 |
| `res/values/ids.xml` | 声明 `R.id.action_settings` ID |
| `res/values/strings.xml` | 新增设置相关字符串 |
| `ui/main/MainActivity.kt` | 注册 SettingsFragment；绑定 btnSettings 事件；新增 `uncheckBottomNavigation()` |
| `res/values/strings.xml` | 新增相关字符串 |
| `ui/main/MainActivity.kt` | 注册 SettingsFragment |

---

### 附录：Android 架构参考

#### 页面导航流

```
LoginActivity
    ├── Login 成功 → MainActivity (5-tab)
    │                    ├── DashboardFragment (健康看板)
    │                    ├── RecordsFragment (记录列表)
    │                    ├── CoachFragment (AI 建议)
    │                    ├── ChatFragment (聊天)
    │                    ├── KnowledgeFragment (知识库/RAG)
    │                    └── SettingsFragment (模型配置)
    │
    └── Register → RegisterActivity
                    └── 注册成功 → MainActivity (5-tab)

其他独立 Activity:
    ├── ChatActivity (聊天 — 独立版，含语音输入)
    ├── RecommendationActivity (推荐列表)
    ├── AnalyticsActivity (数据分析仪表盘)
    ├── WeeklySummaryActivity (周总结)
    └── RagActivity (知识库查询 — 独立版)
```

#### 主题结构

```
Theme.MaterialComponents.Light.NoActionBar
    └── Theme.WellnessApp                  ← 应用默认主题
            └── Theme.WellnessApp.PlainLogin   ← LoginActivity 专用
```

#### 常见闪退排查

| 原因 | 症状 | 排查方法 |
|------|------|----------|
| BottomNavigationView 超过 5 项 | 启动闪退，`IllegalArgumentException` | 检查 `menu_bottom_nav.xml` 中 `<item>` 数量 ≤ 5 |
| Material Components + 原生主题 | 启动白屏后崩溃 | 检查主题 parent 是否含 `MaterialComponents` |
| `setTheme()` 覆盖 Manifest | 主题不一致 | 全局搜索 `setTheme(` |
| Kotlin 重定义 | 编译报错 | `.\gradlew compileDebugKotlin` |
| Activity 未注册 | 运行时崩溃 | 检查 AndroidManifest |
| Layout ID 不匹配 | NullPointerException | 检查 Binding 类与 layout 文件 ID |
