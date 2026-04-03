# 🏆 FTC Scouting Pro: Enterprise Tactical Suite

### *High-Performance Collaborative Scouting for FIRST Tech Challenge*

**FTC Scouting Pro** is an enterprise-grade tactical platform designed for the extreme demands of the FIRST Tech Challenge. Moving beyond simple data collection, it provides a robust, low-latency, and high-concurrency environment for professional robotics teams to execute data-driven alliance strategies.

**FTC Scouting Pro** 是一款专为 FIRST Tech Challenge (FTC) 严苛需求设计的企业级战术平台。它超越了简单的数据收集，为专业战队提供了一个健壮、低延迟、高并发的环境，用于执行数据驱动的联盟策略。

---

## 🛰️ 1. Cluster-Sync Network Architecture / 集群同步网络架构

In high-interference stadium environments, connectivity is mission-critical. Our networking stack is engineered for zero-configuration and maximum resilience.

在干扰严重的赛场环境中，连接性至关重要。我们的网络栈专为“零配置”和极致弹性而设计。

- **UDP Multicast Beacon (Zero-Config Discovery):** Utilizes UDP multicast for automated host discovery. Scouters can join a competition cluster without typing IP addresses, eliminating human error during high-pressure events.

  **UDP 组播信标（零配置发现）：** 利用 UDP 组播实现自动化主机发现。侦查员无需输入 IP 地址即可加入比赛集群，消除了高压赛事中的人为错误。

- **Bi-Directional WebSocket Streaming:** Beyond traditional REST, our system maintains full-duplex WebSocket channels for real-time state synchronization, ensuring the Lead Scout sees every data point the millisecond it is recorded.

  **双向 WebSocket 流：** 超越传统的 REST 架构，系统维持全双工 WebSocket 通道进行实时状态同步，确保主侦查员在数据记录的毫秒级瞬间即可看到每一个数据点。

- **Asynchronous Non-Blocking I/O:** Powered by Java 11 `HttpClient` and `Javalin`, the network layer handles concurrent data submissions without freezing the UI thread, providing a seamless user experience.

  **异步非阻塞 I/O：** 基于 Java 11 `HttpClient` 和 `Javalin` 构建，网络层可处理并发数据提交而不会冻结 UI 线程，提供无缝的用户体验。


---

## 💽 2. Industrial-Grade Data Persistence / 工业级数据持久化

Our database layer utilizes modern backend patterns typically found in high-traffic web applications.

我们的数据库层采用了通常见于高流量 Web 应用程序的现代后端模式。

- **Connection Pooling (HikariCP):** Integrated with **HikariCP**, the world's fastest JDBC connection pool, to manage database resources efficiently and prevent connection leaks.

  **连接池 (HikariCP)：** 集成了全球最快的 JDBC 连接池 HikariCP，有效管理数据库资源并防止连接泄漏。

- **Fluent Data Access (JDBI 3):** Replaces brittle JDBC code with **JDBI 3 Object-Relational Mapping (ORM)**. Using the DAO (Data Access Object) pattern, the application ensures type-safe, readable, and highly maintainable SQL interactions.

  **流畅数据访问 (JDBI 3)：** 使用 JDBI 3 对象关系映射 (ORM) 替代了脆弱的传统 JDBC 代码。通过 DAO（数据访问对象）模式，确保了类型安全、易读且高度可维护的 SQL 交互。

- **Schema Evolution (Flyway):** Implements **Flyway** for database versioning. Any schema changes are automatically migrated upon startup, ensuring data consistency across all Scouter machines.

  **架构演进 (Flyway)：** 引入 Flyway 进行数据库版本控制。任何架构更改都会在启动时自动迁移，确保所有侦查员机器之间的数据一致性。

- **Military-Grade Security:** Sensitive data, including passwords and API keys, are stored using **AES-128 Encryption**, protecting tactical secrets from unauthorized access.

  **军用级安全：** 敏感数据（包括密码和 API 密钥）采用 AES-128 加密存储，保护战术机密免受未经授权的访问。


---

## 🏗️ 3. Decoupled Software Architecture / 解耦的程序架构

The codebase follows a strictly decoupled, service-oriented design to ensure scalability and testability.

代码库遵循严格解耦的面向服务设计，以确保可扩展性和可测试性。

- **Service-Oriented Design (SOA):** Business logic is isolated in a dedicated **Service Layer** (e.g., `RankingService`, `MatchDataService`), completely independent of the UI and Network layers.

  **面向服务的架构 (SOA)：** 业务逻辑被隔离在专门的服务层（如 `RankingService`, `MatchDataService`），完全独立于 UI 和网络层。

- **Reactive UI Binding:** Utilizes **JavaFX Properties** and **ViewModels** to create a reactive data flow. When the network receives an update, the UI updates automatically through property observers.

  **响应式 UI 绑定：** 利用 JavaFX 属性和 ViewModel 构建响应式数据流。当网络收到更新时，UI 会通过属性观察者自动同步更新。

- **Thread-Safety Mastery:** Implements a custom `FxThread` utility to bridge the gap between heavy background network processing and the JavaFX Application Thread, eliminating race conditions.

  **线程安全控制：** 实现自定义 `FxThread` 工具类，架起重量级后台网络处理与 JavaFX 应用程序线程之间的桥梁，消除竞态条件。


---

## 🎨 4. System-Native Modern Aesthetic / 现代原生视觉美学

The UI has evolved from high-contrast luxury to a **Modern Industrial Aesthetic**, emphasizing clarity and system-native familiarity.

UI 已从高对比度的奢华风格演变为**现代工业美学**，强调清晰度和系统原生的熟悉感。

- **Cupertino-Inspired Design:** Built on the **AtlantaFX Cupertino Dark** framework, the interface offers a clean, professional, and sophisticated look reminiscent of high-end macOS workstations.

  **库比蒂诺风格设计：** 基于 AtlantaFX Cupertino Dark 框架构建，界面提供简洁、专业且精致的观感，令人联想起高端 macOS 工作站。

- **Advanced Spatial Feedback:** Features gliding row highlights, physics-based spring animations, and "Glassmorphism" panel effects to guide the user's eye without cognitive overload.

  **高级空间反馈：** 拥有平滑的行高亮追踪、基于物理引擎的弹簧动画以及“毛玻璃”面板效果，在不增加认知负担的情况下引导用户视线。


---

## 🤖 5. Integrated Intelligence / 集成化智能

- **Gemini 3.1 Pro Integration:** A built-in AI assistant capable of analyzing the entire local database to provide real-time strategic advice, alliance synergy scores, and opponent weakness analysis.

  **Gemini 3.1 Pro 集成：** 内置 AI 助手，能够分析整个本地数据库，提供实时战术建议、联盟协同评分及对手弱点分析。

- **Custom Formula Evaluator:** Powered by `exp4j`, allowing users to define complex mathematical ranking algorithms on-the-fly.

  **自定义公式解析器：** 由 `exp4j` 驱动，允许用户即时定义复杂的数学排名算法。


---

## 🛠️ Tech Stack Summary / 技术栈概览

| **Component / 组件** | **Technology / 技术方案** |
| --- | --- |
| **Language / 语言** | Java 21 (LTS) |
| **Framework / UI框架** | JavaFX + AtlantaFX + Ikonli |
| **Database / 数据库** | H2 (Embedded) + HikariCP |
| **ORM / 持久层** | JDBI 3 (Fluent SQL) |
| **Migration / 版本控制** | Flyway |
| **Network / 网络** | Javalin (Host) + WebSocket (Sync) + UDP (Discovery) |
| **Build / 构建** | Maven + jpackage |

Developed by **Lucalmz BEAR 27570**.

由 **Lucalmz BEAR 27570** 开发。