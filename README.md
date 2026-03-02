
# 🏆 FTC Scouting Pro

![Java](https://img.shields.io/badge/Java-21-orange.svg)
![JavaFX](https://img.shields.io/badge/JavaFX-21-blue.svg)
![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20macOS-lightgrey.svg)
![Release](https://img.shields.io/badge/Release-v2.1.5-success.svg)

**FTC Scouting Pro** is an enterprise-grade, desktop-based local area network (LAN) collaborative scouting application designed specifically for the FIRST Tech Challenge (FTC).
**FTC Scouting Pro** 是一款专为 FIRST Tech Challenge (FTC) 设计的企业级桌面端局域网协作侦查应用。

Say goodbye to unreliable internet connections and messy Google Forms. With real-time LAN synchronization, advanced heatmap analytics, and a sleek **Black & Gold Cyber-Luxury UI**, your team can scout smarter and draft the perfect alliance.
告别不稳定的赛场网络和混乱的 Google Forms。凭借实时的局域网同步、先进的热力图分析以及全新的**黑金赛博奢华 UI**，让你的团队能够更智能地侦查并组建完美的联盟。

---

## ✨ Core Advantages / 核心优势

### 💎 1. Premium Cyber-Luxury UI (黑金赛博 UI)
*   **English:** A completely revamped stereoscopic 3D UI featuring glowing outlines, sleek animations, and a high-contrast Black & Gold theme optimized for dark environments (like stadium bleachers).
*   **中文:** 全新重构的立体 3D UI，拥有流光溢彩的边框、丝滑的动画效果，以及专为暗光环境（如赛场看台）优化的高对比度黑金主题。

### 📡 2. Zero-Internet Synchronization (零依赖局域网同步)
*   **English:** The Host acts as a local server. Clients connect via a local router (no internet required) with **UDP Auto-Discovery**. No IP typing needed! Also supports USB `.ftcsync` fallback.
*   **中文:** 主机作为本地服务器，从机通过局域网路由器连接（无需外网）。支持 **UDP 自动发现**，无需手动输入 IP 地址！同时支持 USB `.ftcsync` 离线文件导入导出作为备用方案。

### 🤖 3. Advanced Analytics & Heatmaps (高阶数据分析与热力图)
*   **English:** Interactive field mapping with **Ctrl+Click** recording. Now supports **Undo (Ctrl+Z)** for precise input! The system calculates PPS (Points Per Second) and classifies playstyles (e.g., *Far Zone Specialist*).
*   **中文:** 交互式场地热力图，支持 **Ctrl+点击** 录入。**新增撤销功能 (Ctrl+Z)**，误触也不怕！系统自动计算 PPS（每秒得分）并智能分类队伍打法（如“远端专精”）。

### ☁️ 4. FTCScout.org Integration (FTCScout 官方同步)
*   **English:** Hosts can bind the app to an official Event Code. It automatically fetches official match data, syncs **Penalty Stats**, and broadcasts the **Official Event Name** to all clients.
*   **中文:** 主机可绑定官方赛事代码。应用将自动拉取官方赛程数据，同步**红蓝联盟判罚数据**，并将**官方赛事全称**广播给所有连接的从机，确保信息统一。

### 📐 5. Custom Formulas (自定义评分公式)
*   **English:** Write your own math formulas (e.g., `(auto * 2) + teleop + (seq ? 10 : 0)`) to rank teams based on *your* strategy.
*   **中文:** 支持编写自定义数学公式（例如 `(auto * 2) + teleop + (seq ? 10 : 0)`），根据*你*的战术需求对队伍进行排名。

---

## 🚀 Installation Guide / 安装指南

We provide standalone installers for Windows and macOS. **No Java installation required!**
我们提供 Windows 和 macOS 的独立安装包，**无需预先安装 Java 环境！**

### 🪟 For Windows Users (`.exe`)
1.  Go to the [Releases](../../releases) page. (前往 Releases 页面)
2.  Download `FTCScoutingPro-Windows-x64.zip` and extract it. (下载并解压)
3.  Double-click the installer (`.exe`) and follow the setup wizard. (双击运行安装程序)
4.  Launch via Start Menu or Desktop shortcut. (通过开始菜单或桌面快捷方式启动)

### 🍎 For macOS Users (`.dmg`)
*Designed for Apple Silicon (M1/M2/M3) & Intel Macs.*

1.  Go to the [Releases](../../releases) page. (前往 Releases 页面)
2.  Download `FTCScoutingPro-macOS-AppleSilicon.zip` (or Intel version) and open the `.dmg`. (下载并打开 dmg 文件)
3.  Drag `FTCScoutingPro` into your `Applications` folder. (将应用拖入“应用程序”文件夹)

> ⚠️ **IMPORTANT: macOS "Unidentified Developer" Fix**
> **重要提示：解决 macOS“无法验证开发者”问题**
>
> Because this is an open-source project, it is not signed with a paid Apple certificate. macOS may block the first launch.
> 因为这是开源项目，未购买苹果付费证书签名，macOS 可能会拦截首次启动。
>
> **How to open it (Only needed once / 仅需操作一次):**
> 1.  Open **Finder** -> **Applications**. (打开 访达 -> 应用程序)
> 2.  Find `FTCScoutingPro`. (找到应用)
> 3.  **Hold `Control` key + Click** (or Right-Click) the app icon. (**按住 Control 键 + 点击** 或 右键点击应用图标)
> 4.  Select **Open** (`打开`) from the menu. (在菜单中选择“打开”)
> 5.  Click **Open** (`打开`) again in the popup dialog. (在弹出的警告框中再次点击“打开”)
>
> *After this, you can open it normally like any other app.*
> *操作一次后，以后即可像普通应用一样直接双击打开。*

---

## 🛠️ Quick Start / 快速开始

### 👑 As a Host (Lead Scout / 侦查组长)
1.  **Create:** Login -> Hub -> Create New Competition. (创建比赛)
2.  **Host:** Select competition -> "Start Hosting". (开始主机模式)
3.  **Bind (Optional):** Go to `FTCScout` tab -> Enter Season/Event Code -> "Bind Event". (绑定官方赛事)
4.  **Manage:** Click "Members" -> Approve join requests. (批准成员加入)

### 💻 As a Client (Scouter / 侦查员)
1.  **Join:** Login -> Hub -> "Join Competition". (加入比赛)
2.  **Discover:** Wait for the Host to appear in the list (UDP Auto-Discovery). (等待自动发现主机)
3.  **Request:** Click "Request to Join". (申请加入)
4.  **Scout:** Once approved, start scoring! (获批后开始记录数据)

---

## 👨‍💻 Build from Source / 源码构建

**Prerequisites (环境要求):**
*   JDK 21
*   Maven 3.8+

```bash
# Clone repository
git clone https://github.com/YourName/FTC-Scouting-Pro.git

# Build Fat JAR (Includes JavaFX)
mvn clean package -DskipTests

# Run directly
mvn javafx:run

# Create Installer (Windows/Mac)
# Note: Check pom.xml for platform-specific config
mvn jpackage:jpackage
```

---

## 📜 Credits

*   **UI Framework:** [AtlantaFX](https://github.com/mkpaz/atlantafx)
*   **Icons:** [Ikonli](https://kordamp.org/ikonli/)
*   **Database:** [H2 Database](https://www.h2database.com/)
*   **Network:** Java Standard Socket (TCP/UDP)

Developed with ❤️ for the FIRST Tech Challenge community.