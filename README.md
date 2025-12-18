# FTC Scouting Pro - FTC 27570 赛季计分工具

![JavaFX](https://img.shields.io/badge/JavaFX-21-blue) ![JDK](https://img.shields.io/badge/JDK-21-orange)  ![GitHub release](https://img.shields.io/github/v/release/Lucalmz/27570-FTC-ScoutingPro) ![GitHub all releases](https://img.shields.io/github/downloads/Lucalmz/27570-FTC-ScoutingPro/total) ![Build Status](https://img.shields.io/github/actions/workflow/status/Lucalmz/27570-FTC-ScoutingPro/release-packager.yml?branch=main)    

下载最新版本

[![Download Latest](https://img.shields.io/badge/Download-Latest-brightgreen)](https://github.com/Lucalmz/27570-FTC-ScoutingPro/releases/latest)

**FTC Scouting Pro** 是一款专为 FIRST Tech Challenge (FTC) 赛季设计的现代化、跨平台的桌面计分应用程序。它旨在为比赛现场的计分员、教练和队员提供一个强大、易用且支持实时协作的工具。

本应用采用本地局域网（LAN）进行数据同步，无需依赖云服务器或互联网连接，完美适用于比赛现场的网络环境。

---

## ✨ 功能亮点

* **🏆 实时计分与排行榜:** 在比赛中实时录入得分数据，并即时查看队伍的各项平均数据排名和综合能力。
* **🤝 局域网协作:** 支持多用户在同一个局域网（WiFi）内协同计分。一人担任主机（Host），其他人作为客户端（Client）加入，所有数据实时同步。
* **🖥️ 不跨平台支持:** 提供 Windows (`.exe` 安装包) ，不给macOS用。
* **📊 详细数据统计:** 排行榜不仅显示总分，还提供各分项（如自主阶段得分、终局阶段得分等）的平均值统计，以及队伍特殊能力（如是否能完成特定任务）的达成情况。
* **📜 历史记录与查询:** 保存每一次的得分提交记录，并提供强大的搜索功能，可以按场次、队伍编号或提交人快速查找历史数据。
* **👥 权限管理:** 比赛创建者可以管理计分员成员，批准新成员的加入申请或移除现有成员。
* **🎨 现代化界面:** 采用蓝黑色系的现代UI设计，界面清晰、操作直观。

---

## 🚀 安装指南

您可以从本项目的 **[Releases 页面](https://github.com/Lucalmz/27570-FTC-ScoutingPro/releases)** 下载最新的稳定版本。

1. 访问 **Releases** 页面。
2. 根据您的操作系统，下载对应的安装文件：
   * **Windows 用户:** 下载 `FTCScoutingPro-Windows-v1.3.0.exe` 文件。
   * **macOS 用户:** 没得用。

### Windows 安装

1. 双击下载的 `.exe` 文件。
2. 按照安装向导的提示完成安装。
3. 安装完成后，您可以在桌面和开始菜单中找到 `FTCScoutingPro` 的快捷方式。

### macOS 安装

1. 我不会

## 📖 使用教程

### 1. 首次启动与用户创建

* 首次打开应用，您会看到登录界面。
* 输入您想用的用户名和密码，点击 **"Create User"** 来创建一个新账户。
* 创建成功后，使用相同的用户名和密码点击 **"Login"** 登录。

### 2. 作为主机 (Host) - 创建并主持一场比赛

* **创建比赛:** 登录后，在比赛中心页面底部的输入框中填写一个新的比赛名称，然后点击 **"Create Competition"**。
* **主持比赛:** 在比赛中心页面，选择 **"Host a Competition"** 模式。从您创建的比赛列表中，选中您想主持的比赛，然后点击 **"Start Hosting Selected Competition"**。
* **管理成员:** 如果您是比赛的创建者，在比赛中心的比赛列表中，您创建的比赛旁边会有一个 **"Edit Coordinators"** 按钮。点击它可以批准等待加入的成员，或移除已批准的成员。

### 3. 作为客户端 (Client) - 加入一场比赛

* **发现比赛:** 确保您的电脑已连接到与主机相同的 WiFi 或局域网。登录后，选择 **"Join a Competition"** 模式。应用会自动搜索网络中正在主持的比赛并显示在列表中。
* **加入比赛:** 从列表中选中您想加入的比赛，然后点击 **"Request to Join Selected Competition"**。
* **等待批准:** 您的加入请求需要得到比赛创建者（主机）的批准。一旦被批准，您就可以进入比赛并开始计分。

### 4. 计分与查看数据

* **计分录入 (Scoring):** 在此标签页，填写场次、队伍、联盟和各项得分信息，然后点击 "Submit" 提交。主机会收到所有人的提交并同步数据。
* **队伍排行榜 (Team Rankings):** 在此标签页，查看所有参赛队伍的平均表现和能力达成情况。可以点击列表头进行排序。
* **历史记录 (Score History):** 在此标签页，查看所有已提交的得分记录，并使用顶部的搜索框进行快速过滤。

---

## 🛠️ 参与贡献 (Contributing)

我们欢迎任何形式的贡献！如果您发现了 Bug 或有新的功能建议，请随时提交一个 **[Issue](https://github.com/Lucalmz/27570-FTC-ScoutingPro/issues)**。

如果您想贡献代码：

1. Fork 本仓库。
2. 创建一个新的分支 (`git checkout -b feature/AmazingFeature`)。
3. 提交您的修改 (`git commit -m 'Add some AmazingFeature'`)。
4. 将您的分支推送到远程 (`git push origin feature/AmazingFeature`)。
5. 提交一个 **Pull Request**。

### 本地开发环境

* **JDK:** 21
* **JavaFX:** 21
* **构建工具:** Maven
* **IDE:** IntelliJ IDEA (推荐)

运行 `mvn clean javafx:run` 来在本地启动应用。

---

## 📄 许可证 (License)

本项目采用 [MIT License](LICENSE.md) 授权。
