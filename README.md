# 🏆 FTC Scouting Pro

**FTC Scouting Pro** is an enterprise-grade, desktop-based local area network (LAN) collaborative scouting application designed specifically for the rigorous data demands of the FIRST Tech Challenge (FTC). Built from the ground up to handle the strategic depth of modern game manuals, including the complexities of the DECODE season, this platform empowers teams to make data-driven decisions when it matters most.

**FTC Scouting Pro** 是一款专为 FIRST Tech Challenge (FTC) 严苛数据需求设计的企业级桌面端局域网协作侦查应用。它从底层重构，旨在应对现代比赛手册的战略深度（包括 DECODE 赛季的复杂战术），赋能车队在关键时刻做出数据驱动的决策。

Say goodbye to unreliable stadium internet connections, fragmented data silos, and messy Google Forms. Engineered with a high-performance lightweight backend (Javalin & HikariCP), real-time LAN synchronization, advanced heatmap analytics, and a sleek **Black & Gold Cyber-Luxury UI**, your team can scout smarter, react faster, and draft the absolute perfect alliance.

告别赛场不稳定的网络连接、碎片化的数据孤岛和混乱的 Google Forms。依托高性能轻量级后端（Javalin & HikariCP）、实时的局域网同步、高阶热力图分析以及全新打造的**黑金赛博奢华 UI**，让你的团队能够更智能地侦查、更快速地反应，并组建出绝对完美的联盟。

---

## ✨ Core Advantages / 核心优势

### 💎 1. Premium Cyber-Luxury UI (黑金赛博 UI)

* **English:** A completely revamped stereoscopic 3D interface powered by AtlantaFX. Featuring glowing outlines, hardware-accelerated sleek animations, and a high-contrast Black & Gold theme explicitly optimized to reduce eye strain in dark environments like stadium bleachers. Custom CSS structures allow for crystal-clear data visualization.
* **中文:** 基于 AtlantaFX 驱动的全新重构立体 3D 界面。拥有流光溢彩的边框、硬件加速的丝滑动画，以及专为赛场看台等暗光环境优化的高对比度黑金主题，有效降低视觉疲劳。定制化的 CSS 结构确保了极高的数据可视化清晰度。

### 📡 2. Zero-Internet Synchronization (零依赖局域网同步)

* **English:** Designed for congested RF environments. The Lead Scout's machine acts as a robust local server. Scouter clients connect via a local router (absolutely no WAN/internet required) utilizing **UDP Auto-Discovery**. No IP typing needed! Data integrity is ensured via TCP handshakes, and it also supports fail-safe USB `.ftcsync` encrypted file exports for extreme offline scenarios.
* **中文:** 专为射频拥堵环境设计。侦查组长的主机作为强大的本地服务器。从机客户端通过局域网路由器连接（绝对不需要外网），并利用 **UDP 自动发现** 技术，彻底告别手动输入 IP 地址！通过 TCP 握手确保数据完整性，同时支持防故障的 USB `.ftcsync` 加密文件导出，以应对极端的断网场景。

### 🤖 3. Advanced Analytics & Heatmaps (高阶数据分析与热力图)

* **English:** Real-time interactive field mapping with granular **Ctrl+Click** recording for precise coordinate tracking. Version 2.2.0 enhances the **Undo (Ctrl+Z)** stack for rapid error correction. The embedded H2 database instantly calculates comprehensive metrics including PPS (Points Per Second), cycle times, and intelligently classifies team playstyles (e.g., *Far Zone Specialist*, *Defense Heavy*).
* **中文:** 实时交互式场地热力图，支持高颗粒度的 **Ctrl+点击** 录入，精准追踪坐标。2.2.0 版本增强了 **撤销 (Ctrl+Z)** 栈，实现快速纠错。内嵌的 H2 数据库可瞬间计算包括 PPS（每秒得分）、循环周期在内的综合指标，并智能分类队伍打法（如“远端专精”、“重装防守”）。

### ☁️ 4. FTCScout.org Integration (FTCScout 官方同步)

* **English:** Hosts can bind the application to an official FIRST Event Code. When internet is intermittently available, it automatically fetches official match schedules, syncs **Red/Blue Alliance Penalty Stats**, and securely broadcasts the **Official Event Name** and current match flow to all connected offline clients.
* **中文:** 主机可将应用与官方 FIRST 赛事代码绑定。在网络偶发可用的情况下，它将自动拉取官方赛程表，同步**红蓝联盟判罚数据**，并将**官方赛事全称**和当前比赛进度安全地广播给所有连接的离线从机。

### 📐 5. Custom Formulas Engine (自定义评分公式引擎)

* **English:** Powered by the robust `exp4j` mathematical expression evaluator. Write your own advanced logic formulas (e.g., `(auto * 2.5) + teleop + (seq ? 15 : 0) - penalties`) to algorithmically rank and sort teams based strictly on *your* specific alliance selection strategy.
* **中文:** 由强大的 `exp4j` 数学表达式解析器驱动。支持编写高阶逻辑公式（例如 `(auto * 2.5) + teleop + (seq ? 15 : 0) - penalties`），完全根据*您*特定的联盟选择战术对队伍进行算法级排名和排序。

---

## 🚀 Installation Guide / 安装指南

We provide fully bundled, standalone installers for both Windows and macOS platforms utilizing `jpackage`. **No separate Java Runtime Environment (JRE) installation is required!**

我们提供使用 `jpackage` 打包的 Windows 和 macOS 全包独立安装程序。**无需用户额外安装任何 Java 运行环境 (JRE)！**

### 🪟 For Windows Users (`.exe`)

*Optimized for Windows 10 & 11 (x64).*

1. Navigate to the [Releases](https://www.google.com/search?q=../../releases) page. (前往 Releases 页面)
2. Download the latest `FTCScoutingPro-Windows-x64.zip` and extract the contents. (下载最新压缩包并解压)
3. Double-click the installer executable (`.exe`) and follow the intuitive setup wizard. (双击运行安装程序)
4. Launch the application via the Start Menu or the newly created Desktop shortcut. (通过开始菜单或桌面快捷方式启动)

### 🍎 For macOS Users (`.dmg`)

*Universal compatibility designed for Apple Silicon (M1/M2/M3/M4), legacy Intel Macs, and fully verified for modern environments including **macOS 26 (Tahoe)**.*

1. Navigate to the [Releases](https://www.google.com/search?q=../../releases) page. (前往 Releases 页面)
2. Download the appropriate disk image: `FTCScoutingPro-macOS-AppleSilicon.dmg` for modern Macs, or the Intel variant for older machines. (下载对应的 dmg 镜像文件)
3. Double-click to mount the `.dmg` and drag the `FTCScoutingPro` application icon directly into your `Applications` folder. (双击挂载后，将应用拖入“应用程序”文件夹)

> ⚠️ **IMPORTANT: macOS Gatekeeper & "Unidentified Developer" Bypass**
> **重要提示：解决 macOS Gatekeeper 拦截与“无法验证开发者”报错**
> Because this software is developed by the community and distributed open-source, it is not signed with a commercial Apple Developer certificate. macOS strict security protocols (Gatekeeper) will likely block the first execution, presenting a warning that the app is "damaged" or from an "unverified developer."
> 因为本软件由社区开发且开源发布，未采用商业苹果开发者证书进行签名。macOS 严格的安全协议 (Gatekeeper) 大概率会拦截首次运行，并警告应用“已损坏”或来自“未经验证的开发者”。
> **How to authorize the application (Only required once / 仅需操作一次):**
> 1. Open **Finder** -> Navigate to **Applications**. (打开 访达 -> 进入 应用程序 目录)
> 2. Locate `FTCScoutingPro`. (找到该应用)
> 3. **Hold the `Control` key on your keyboard and Click** (or Right-Click / Two-finger tap) the application icon. (**按住键盘上的 Control 键并点击** 或 右键/双指点击应用图标)
> 4. Select **Open** (`打开`) from the contextual menu. (在弹出的上下文菜单中选择“打开”)
> 5. The system will present a warning dialog. Click the **Open** (`打开`) button within this dialog. (系统会弹出一个警告对话框，在此对话框中再次点击“打开”按钮)
>
>
> *After completing this security override, macOS will remember your choice, and you can open the application normally via Launchpad or Spotlight in the future.*
> *完成此安全豁免后，macOS 会记住您的选择，以后您即可通过启动台或聚焦搜索像普通应用一样正常打开它。*

---

## 🛠️ Quick Start Workflow / 快速上手工作流

### 👑 As a Host (Lead Scout / 侦查组长)

1. **Initialize:** Launch the app -> Navigate to the **Hub** -> Select **Create New Competition**. (启动应用 -> 进入中枢 -> 创建新赛事)
2. **Deploy Server:** Select your newly created competition and click **"Start Hosting"**. The LAN server will initialize immediately. (选中赛事并点击“开始主机模式”，局域网服务器将立即启动)
3. **Bind Official Data (Optional but Recommended):** Transition to the `FTCScout` tab -> Enter the specific Season/Event Code -> Click **"Bind Event"**. (切换至 FTCScout 标签页 -> 输入赛季/赛事代码 -> 点击“绑定官方赛事”)
4. **Manage Fleet:** Monitor the "Members" panel. Review and **Approve** incoming join requests from your scouting team. (监控“成员”面板，审核并**批准**侦查员的加入申请)

### 💻 As a Client (Scouter / 侦查员)

1. **Connect:** Launch the app -> Navigate to the **Hub** -> Click **"Join Competition"**. (启动应用 -> 进入中枢 -> 点击“加入比赛”)
2. **Auto-Discover:** Wait a few seconds for the Host machine to appear automatically in the radar list via UDP broadcasting. (等待几秒钟，主机将通过 UDP 广播自动出现在雷达列表中)
3. **Request Access:** Select the Host and click **"Request to Join"**. (选中主机并点击“申请加入”)
4. **Execute Scouting:** Once the Lead Scout approves your connection, your interface will unlock. Head to the match screen and start tracking data! (一旦组长批准您的连接，界面将解锁。进入比赛界面开始记录数据！)

---

## 👨‍💻 Build from Source / 源码编译与构建

For developers looking to contribute or compile custom distributions.
为希望贡献代码或编译自定义发行版的开发者提供。

**Prerequisites (环境要求):**

* JDK 21 (Temurin or equivalent strictly recommended)
* Apache Maven 3.8+
* Git

```bash
# 1. Clone the repository locally
git clone https://github.com/YourName/FTC-Scouting-Pro.git
cd FTC-Scouting-Pro

# 2. Resolve dependencies and build the shaded Fat JAR (Includes all JavaFX modules)
mvn clean package -DskipTests

# 3. Execute the application directly from source for debugging
mvn javafx:run

# 4. Generate native standalone installers (.exe / .dmg) using jpackage
# Note: Ensure you review the <plugin> configurations in pom.xml for platform-specific UUIDs and signatures.
mvn jpackage:jpackage

```

---

## 📜 Acknowledgements & Credits / 致谢

This project stands on the shoulders of open-source giants:

* **UI Framework:** [AtlantaFX](https://github.com/mkpaz/atlantafx) for modern, responsive JavaFX styling.
* **Iconography:** [Ikonli](https://kordamp.org/ikonli/) (specifically the Feather pack for a clean, macOS-inspired aesthetic).
* **Data Persistence:** [H2 Database Engine](https://www.h2database.com/) & [HikariCP](https://www.google.com/search?q=https://github.com/brettwooldridge/HikariCP) for blazing-fast local storage and connection pooling.
* **Networking & API:** Standard Java Sockets (TCP/UDP), [Javalin](https://www.google.com/search?q=https://javalin.io/) for lightweight HTTP/WebSocket services, and Google [Gson](https://www.google.com/search?q=https://github.com/google/gson) for JSON serialization.
* **Math Engine:** [exp4j](https://www.google.com/search?q=https://www.objecthunter.net/exp4j/) for parsing dynamic scouting formulas.

Developed and maintained by Lucalmz (Committing as BEAR27570).
Architected and developed with ❤️ for the FIRST Tech Challenge global community.