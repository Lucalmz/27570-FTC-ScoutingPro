
# 🤖 FTC Scouting Pro

**FTC Scouting Pro** 是一款专为 FIRST Tech Challenge (FTC) 机器人竞赛设计的现代化、高性能侦查与数据分析应用。

它摒弃了传统的纸笔记录和简陋的电子表格，采用 **Host-Client (主机-客户端)** 架构，支持多台电脑实时局域网联机，实现数据的无缝同步。最新版本更是引入了**概率云射点分析**、**可视化录入**以及**自定义评分公式**，助您的队伍在选人与战术制定上抢占先机。

![License](https://img.shields.io/badge/license-MIT-blue.svg) ![Java](https://img.shields.io/badge/Java-21-orange) ![Platform](https://img.shields.io/badge/platform-Windows%20%7C%20macOS-lightgrey)

---

## ✨ 核心功能 (Key Features)

*   **☁️ 概率云分析 (Probability Cloud) [NEW]**:
    *   基于 **高斯核密度估计 (Gaussian KDE)** 算法，取代传统热力图。
    *   通过**紫(低) -> 蓝 -> 绿 -> 红(高)** 的丝滑渐变，直观展示机器人的潜在得分概率与习惯区域。
    *   自动处理红蓝方视角镜像，实现跨比赛的统一分析。
*   **🌐 局域网实时同步**:
    *   **Host 模式**: 创建比赛并作为服务器，汇总所有数据，拥有踢人/通过申请的管理权限。
    *   **Client 模式**: 自动发现局域网内的比赛，侦查员录入的数据实时回传给主机。
*   **📊 灵活的数据录入**:
    *   **可视化射点**: 在场地地图上直接点击记录进球位置，支持分队记录。
    *   **双模式切换**: 支持“联盟模式 (2 Teams)”和“单队模式 (Single Team)”。
*   **🧮 自定义评分引擎**:
    *   比赛拥有者可以编写数学公式（如 `auto * 1.5 + teleop`）来自定义 "Rating" 排名权重，无需修改代码。
*   **💾 本地持久化**: 使用嵌入式 H2 数据库，断网也不怕，数据永久保存。

---

## 📥 下载与安装 (Installation)

请前往本项目的 [Releases 页面](../../releases) 下载最新版本：

*   **Windows 用户**: 下载并运行 `.exe` 安装程序。
*   **macOS 用户**: 下载 `.dmg` 镜像文件并安装。

> **⚠️ 升级注意**: 此次更新涉及数据库结构变更，建议升级前备份并删除用户目录下的 `.ftcscoutingpro` 文件夹，以免发生数据冲突。

---

## 📖 用户教程 (User Guide)

### 1. 启动与登录
首次打开应用，您需要创建一个本地账户。
*   输入用户名和密码，点击 **Create User**。
*   创建成功后，使用该账号 **Login**。

### 2. 比赛大厅 (The Hub)
登录后进入大厅，这里是操作的起点：
*   **Host a Competition (主机)**:
    *   在下方输入框填写新比赛名称，点击 "Create"。
    *   在列表中选中比赛，点击 **"Start Hosting"** 开启服务器。
    *   **管理成员**: 点击 **"Manage Members"** 按钮，可批准新侦查员加入或踢出已有成员。
*   **Join a Competition (侦查员)**:
    *   点击 "Join a Competition" 切换面板。
    *   确保您与主机在同一局域网（Wi-Fi）下。
    *   列表会自动刷新发现的比赛，选中并点击 "Join"。等待主机批准后即可进入。

### 3. 数据录入 (Scoring)
进入比赛界面后，默认在 **"Scoring"** 标签页。

#### A. 模式选择
*   **Alliance Mode**: 记录一场正式比赛（红蓝对抗）。输入两个队伍号，系统会记录总分。
*   **Single Team**: 仅侦查单个机器人（如场边观察或单机练习）。界面会自动隐藏 Team 2 的输入框。

#### B. 射点图录入 (Map Input) 🔥
点击 TeleOp Artifacts 旁边的 **"📍 Record on Map"** 按钮打开地图录入窗口：
1.  **选择队伍**: 点击 "Team 1" 或 "Team 2" 切换当前记录对象（仅联盟模式）。
2.  **记录位置**:
    *   **Add (+)**: 在场地上点击进球位置，会出现对应颜色的点。
    *   **Remove (-)**: 点击已存在的点将其撤销。
3.  **确认**: 点击 **Confirm**。系统会自动计算总数填入输入框，并保存坐标数据。

### 4. 数据分析 (Rankings & Analysis)
切换到 **"Team Rankings"** 标签页查看排行榜。

*   **查看概率云 (Shot Chart)**: 点击表格中任意队伍行的 **"View Map"** 按钮。
    *   **紫色/深蓝区域**: 该队偶尔经过或得分的区域（低概率）。
    *   **绿色/黄色区域**: 常规得分区域（中概率）。
    *   **红色核心**: 该队的“绝对甜点位”，得分最为密集的区域（高概率）。
*   **编辑评分公式 (仅主机)**:
    *   点击右上角的 **"Edit Rating Formula"**。
    *   输入公式，例如：`(auto / 2) * 5 + (teleop / 2) * 3 + (climb ? 15 : 0)`。
    *   保存后，排行榜的 "Rating" 列会立即更新，并带有 `*` 标记。

---

## 💻 开发者指南 (Developer Guide)

如果您想自行构建或贡献代码，请参考以下步骤。

### 技术栈
*   **语言**: Java 21
*   **UI 框架**: JavaFX 21
*   **数据库**: H2 Database (Embedded)
*   **构建工具**: Maven
*   **数学引擎**: Exp4j

### 构建步骤

1.  **环境准备**: 确保安装了 JDK 21 和 Maven。
2.  **克隆项目**:
    ```bash
    git clone https://github.com/your-repo/ftc-scouting-pro.git
    cd ftc-scouting-pro
    ```
3.  **打包 JAR**:
    此命令会生成包含所有依赖的 Fat Jar。
    ```bash
    mvn clean package -DskipTests
    ```
4.  **生成安装包 (JPackage)**:
    根据当前操作系统生成 `.exe` 或 `.dmg`。
    ```bash
    mvn jpackage:jpackage
    ```
    生成的文件位于 `target/dist/` 目录下。

### 项目结构
*   `models/`: 数据实体 (Competition, ScoreEntry, TeamRanking)。
*   `services/`: 业务逻辑 (DatabaseService, NetworkService)。
*   `controllers/`: JavaFX 界面逻辑（包含最新的 `HeatmapController` 概率云算法）。
*   `fxml/`: 界面布局文件。

---

## 🤝 贡献与反馈

欢迎提交 Issue 反馈 Bug 或建议新功能！Pull Request 也非常欢迎。

**主要维护者**: @Bear27570 @Lucalmz

---

## 📄 开源协议

本项目基于 MIT License 开源。详情请参阅 LICENSE 文件。