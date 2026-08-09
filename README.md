# DuraDB — 轻量级页式存储数据库引擎

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![GitHub](https://img.shields.io/badge/GitHub-AngelDai--lab/DuraDB-black)](https://github.com/AngelDai-lab/DuraDB)

**DuraDB** 是一个从零实现的轻量级 Java 数据库存储引擎。项目涵盖了数据模型设计、自定义多格式序列化、页式存储管理、预写日志（WAL）与崩溃恢复、B+树（跳表）索引加速以及教务命令行交互系统（CLI）。

项目旨在探索数据库底层的持久化原理，并在保障 ACID 中 **Durability（持久性）** 的同时，对不同序列化机制与索引结构进行性能分析。

👉 **项目地址**：[https://github.com/AngelDai-lab/DuraDB](https://github.com/AngelDai-lab/DuraDB)

---

## 📖 目录

- [项目特性](#-项目特性)
- [核心模块详解](#-核心模块详解)
- [性能基准测试](#-性能基准测试)
- [快速开始](#-快速开始)
- [交互式命令](#-交互式命令)
- [项目结构](#-项目结构)
- [技术栈](#-技术栈)
- [测试报告](#-测试报告)
- [许可证](#-许可证)
- [联系与致谢](#-联系与致谢)

---

## 🚀 项目特性

| 特性 | 说明 |
|  :---:|  :---:|
| **多元序列化支持** | 实现并对比了自定义二进制（Binary）、JSON（Jackson）以及 Google Protocol Buffers（Protobuf）三种序列化协议 |
| **页式存储管理** | 采用 4KB 固定大小物理页组织数据，支持变长槽目录（Slot Directory）和 O(1) 标记删除 |
| **内存 LRU 缓冲池** | 利用 `LinkedHashMap` 实现最近最少使用（LRU）缓存页，默认容量 16 页（64KB），大幅减少磁盘 I/O |
| **强持久性（WAL）** | 实现预写日志（Write-Ahead Logging）与崩溃恢复机制，通过 `fsync` 强制落盘，结合 CRC32 校验和保障系统异常断电时的数据安全与幂等重放 |
| **高效索引查询** | 采用内存 B+树（底层基于线程安全的 `ConcurrentSkipListMap` 跳表）实现 **Key → RecordId** 的映射，查询复杂度由 O(N) 降至 O(log N) |
| **教务管理命令行** | 输入命令即可管理学生和课程，支持 CSV 导入导出、选课退课、数据统计 |
| **数据完整性校验** | 基于 CRC32 的页级校验和，每次读取时自动验证，防止数据损坏 |

---

## 📦 核心模块详解

### 1. 数据模型与序列化对比（model & codec）

设计了三种递进式的复杂数据模型以验证序列化器的健壮性：

| 模型 | 特点 | 序列化难点 | 验证目的 |
|  :---:|  :---:|  :---:|  :---:|
| **Student** | 基础对象，含变长 `name` 和浮点数 `gpa` | 变长字符串处理 | 基础序列化能力 |
| **Course** | 嵌套对象 `List<Student>` | 嵌套对象 + 集合类型 | 递归序列化能力 |
| **Node** | 循环引用 `List<Node>`（A→B, B→A） | 图结构循环引用 | 图结构序列化能力 |

**循环引用解决方案：**

| 序列化格式 | 解决方案 |
| :---:| :---:|
| **Binary** | 对象引用表（先分配唯一 ID，邻居列表只存 ID） |
| **JSON** | `@JsonIdentityInfo` 注解（第一次遇到完整输出，后续只输出 ID） |
| **Protobuf** | 自定义 GraphProto 消息（节点列表 + 邻居 ID 引用） |

### 2. 页式存储设计（storage）

### 页结构

每个 Page 大小为 **4KB**，分为三个区域：

| 区域 | 大小 | 增长方向 | 说明 |
|   :---:|   :---:|   :---:|   :---:|
| **Page Header** | 16 字节 | 固定 | 存储页元信息（pageId、recordCount、checksum 等） |
| **Record Data** | 变长 | 正向（从前往后） | 实际存储的序列化记录数据 |
| **Slot Directory** | 变长 | 反向（从后往前） | 每个槽 4 字节，记录每条数据的偏移量和长度 |


**Page Header（16 字节）：**

| 字段 | 大小 | 说明 |
|   :---:|   :---:|   :---:|
| `pageId` | 4 字节 | 页编号（从 0 开始） |
| `recordCount` | 2 字节 | 本页记录条数 |
| `freeSpaceOffset` | 2 字节 | 空闲空间起始位置 |
| `checksum` | 4 字节 | CRC32 校验和，检测数据损坏 |
| `reserved` | 4 字节 | 预留扩展 |

**核心设计要点：**

| 设计 | 说明 |
|   :---:|   :---:|
| **数据区正向写入** | 从第 16 字节开始向后追加 |
| **槽目录反向增长** | 从页尾向前增长，每槽 4 字节（2B 偏移 + 2B 长度） |
| **记录定位** | `RecordId(pageId, slotIndex)` 精确定位 |
| **删除策略** | 标记删除（槽长度置为 `-1`），O(1) 复杂度 |
| **校验和** | CRC32 只计算数据区（不含页头），避免循环依赖 |

### 3. 预写日志与崩溃恢复（WAL）

写入流程：
序列化 → 构造 WALEntry（含 CRC32） → 追加写入 WAL 文件 → fsync 强制落盘 → 修改内存页 → 标记脏页异步刷盘
text

| 字段 | 大小 | 说明 |
|   :---:|   :---:|   :---:|
| CRC32 | 4B | 校验和，检测日志是否损坏 |
| 操作类型 | 1B | 0x01=INSERT, 0x02=DELETE |
| recordId | 4B | 高 16 位=页号，低 16 位=槽索引 |
| 数据长度 | 2B | 数据字节数 |
| 数据 | 变长 | 序列化后的数据 |

**崩溃恢复流程**

```text
启动 → 检查 WAL 文件
  ├── 不存在 → 正常启动
  └── 存在 → 逐条读取 → CRC32 校验
        ├── 失败 → 丢弃该条
        └── 通过 → 幂等重放
              ├── INSERT → 查重（存在跳过）
              └── DELETE → 直接执行
        → Checkpoint（清空 WAL）→ 正常启动
```

### 4. B+树索引（index）

| 特性 | 说明 |
|   :---:|   :---:|
| **机制** | 内存索引 + 启动时扫描数据页重建 |
| **底层实现** | `ConcurrentSkipListMap`（Java 跳表） |
| **复杂度** | 插入/删除/查询均为 O(log N) |
| **重建耗时** | 10,000 条约 10ms，对万级数据可接受 |
| **一致性保证** | 插入/删除时同步更新索引，启动时完全重建 |

---

## 📊 性能基准测试（Benchmark）

### 测试环境

| 项目 | 配置 |
|   :---:|   :---:|
| 操作系统 | Windows 11 |
| 内存 | 16 GB |
| 磁盘 | SSD |
| Java 版本 | 17 |
| 测试数据量 | 5,000 条 |

### 存储空间对比（5,000 条）

| 格式 | 文件大小 | 结论 |
|   :---:|   :---:|   :---:|
| **Binary** | **144 KB** | ✅ 最紧凑 |
| JSON | 240 KB | ❌ 约 1.66 倍 |
| Protobuf | 148 KB | ✅ 接近 Binary |
| Binary + WAL | 144 KB | ✅ 同 Binary（WAL 单独存储） |

### 写入速度对比（5,000 条）

| 格式 | 耗时 | 结论 |
|   :---:|   :---:|   :---:|
| **Binary** | **4.86 s** | ✅ 最快 |
| JSON | 5.08 s | 略慢 |
| **Protobuf** | **4.83 s** | ✅ 最快 |
| Binary + WAL | 9.37 s | ❌ 约 2 倍开销 |

### 综合对比

| 格式 | 写入耗时 | 文件大小 | 可读性 | 循环引用 | 崩溃恢复 |
|   :---:|   :---:|   :---:|   :---:|   :---:|   :---:|
| **Binary** | **4.86 s** | **144 KB** | ❌ | ✅ 引用ID | ❌ |
| **JSON** | 5.08 s | 240 KB | ✅ | ✅ JsonIdentity | ❌ |
| **Protobuf** | **4.83 s** | 148 KB | ⚠️ | ✅ 需转换 | ❌ |
| **Binary + WAL** | 9.37 s | 144 KB | ❌ | ✅ 引用ID | ✅ |

### 索引加速比测试

| 查询方式 | 50,000 条数据耗时 | 复杂度 |
|   :---:|   :---:|   :---:|
| 全表扫描 | ~220 ms | O(N) |
| B+树索引 | ~0.15 ms | O(log N) |
| **加速比** | **约 1,400 倍** | — |

### 测试结论

| 序号 | 结论 |
|   :---:|   :---:|
| 1 | **Binary 存储效率最高**，文件最小，速度最快 |
| 2 | **JSON 可读性最强**，但文件最大（约 1.66 倍） |
| 3 | **Protobuf 接近 Binary** 的性能和空间，带 Schema，跨语言兼容 |
| 4 | **WAL 提供崩溃恢复能力**，但写入速度下降约 **2 倍** |
| 5 | 这是 ACID 中 **Durability（持久性）** 的性能代价 |

---

## 💻 快速开始

### 环境要求

| 依赖 | 版本 |
|   :---:|   :---:|
| JDK | 17+ |
| Maven | 3.6+ |
| 操作系统 | Windows / macOS / Linux |

### 运行步骤

```bash
#### 运行步骤

```bash
# 1. 克隆项目
git clone https://github.com/AngelDai-lab/DuraDB.git
cd DuraDB

# 2. 构建项目
mvn clean package

# 3. 准备数据（二选一）
# 方式一：使用自己的 CSV 文件，放入 data/ 目录
# 方式二：生成测试数据
mvn exec:java -Dexec.mainClass="com.duradb.util.CSVGenerator"

# 4. 启动教务管理系统
mvn exec:java -Dexec.mainClass="com.duradb.api.AcademicSystemCLI"

# 5. 运行统一测试
mvn exec:java -Dexec.mainClass="com.duradb.AllTests"
```

## ⌨️ 交互式命令
### 学生管理
```bash
命令	 格式	                   说明
load	load <csvFilePath>	      从 CSV 批量导入学生数据
list	list	                  列出所有学生
find	find <id>	              通过 B+树索引快速查找
add	    add <id> <name> <gpa>	  新增学生
delete	delete <id>	              删除学生
update	update <id> <name> <gpa>  修改学生信息
```

### 课程管理
```bash
命令	格式	                         说明
course  add	course add <code> <name>	添加课程
course  list	course list	            查看所有课程及选课人数
course  find	course find <code>	    查看课程详情及选课名单
enroll	enroll <code> <studentId>	    学生选课
drop	drop <code> <studentId>	        学生退课
系统命令
命令	格式	                         说明
stats	stats	                        查看系统统计信息
export	export <csvFilePath>	        导出学生数据为 CSV
help	help	                        获取帮助
exit	exit / quit	                    安全退出
```
### 使用示例:
```bash
> load data/students.csv
  加载完成: 180 条学生记录

> course add CS101 数据结构
  课程添加成功: CS101 - 数据结构

> enroll CS101 10
  选课成功: 学生 10 → CS101

> course find CS101
  课程详情:
    课程编号: CS101
    课程名称: 数据结构
    选课人数: 1 人
    选课学生名单:
      - Student{id=10, name='Student_10', gpa=4.59}

> stats
  教务系统统计:
    学生数: 180
    课程数: 1
    空闲页: 0
```
---

## 📁 项目结构
```bash
DuraDB/
├── src/
│   ├── main/
│   │   └── java/com/duradb/
│   │       ├── api/                    # 接入层
│   │       │   └── AcademicSystem.java  # 教务系统命令行
│   │       ├── codec/                  # 序列化层
│   │       │   ├── BinaryCodec.java  
│   │       │   ├── Codec.java          # 统一接口
│   │       │   ├── JsonCodec.java      
│   │       │   └── ProtobufCodec.java  
│   │       ├── model/                  # 数据模型层
│   │       │   ├── Student.java        
│   │       │   ├── Course.java         
│   │       │   └── Node.java          
│   │       ├── storage/                # 存储层
│   │       │   ├── Page.java           
│   │       │   ├── PageManager.java   
│   │       │   ├── BufferPool.java     
│   │       │   ├── RecordId.java       
│   │       │   ├── WALEntry.java       
│   │       │   └── WALManager.java     
│   │       ├── index/                  # 索引层
│   │       │   └── BPlusTree.java      
│   │       └── util/                   # 工具层
│   │           ├── ChecksumTest.java   # 测试能否检测到数据损坏
│   │           ├── ChecksumUtil.java   
│   │           ├── CSVGenerator.java   # 自动生成数据文件     
│   │           └── CSVLoader.java
│   └── test/
│       └── java/com/duradb/
│           ├── AllTests.java           # 测试所有模块
│           └── BenchmarkTest.java      # 测试性能基准
├── data/                               # 存放生成的数据文件
│   └── students.csv                    
├── results/                            # Benchmark 结果
│   └── benchmark_results.csv     
├── test_report.html                    # 自动生成的测试报告
├── pom.xml
└── README.md
```
---

## 🛠️ 技术栈

| 组件 | 技术 | 用途 |
|:---|:---|:---|
| 语言 | Java 17 | 核心开发语言 |
| 构建工具 | Maven | 项目构建与依赖管理 |
| JSON 序列化 | Jackson 2.15.2 | JSON 格式序列化 |
| Protobuf | protobuf-java 3.24.0 | Protocol Buffers 序列化 |
| 校验和 | CRC32 (java.util.zip) | 数据完整性校验 |
| 测试框架 | JUnit 5 | 单元测试 |
| 版本控制 | Git + GitHub | 代码托管 |
| IDE | VS Code / IntelliJ IDEA | 开发环境 |

### pom.xml 主要依赖
```xml
<dependencies>
    <!-- Jackson (JSON) -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.15.2</version>
    </dependency>
    <!-- Protobuf -->
    <dependency>
        <groupId>com.google.protobuf</groupId>
        <artifactId>protobuf-java</artifactId>
        <version>3.24.0</version>
    </dependency>
    <!-- JUnit 5 -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter-api</artifactId>
        <version>5.11.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```
---

## 🧪 测试报告

| 测试项 | 状态 | 说明 |
|:---|:---:|:---|
| 数据模型 | ✅ | Student/Course/Node 创建成功 |
| 序列化 | ✅ | Binary/JSON/Protobuf 全部通过 |
| Page | ✅ | 页结构读写正常 |
| PageManager | ✅ | 存储层 CRUD 正常 |
| WAL | ✅ | 预写日志与恢复正常 |
| B+树索引 | ✅ | 索引查询与维护正常 |
| Checksum | ✅ | CRC32 校验正常 |
| CSV 完整流程 | ✅ | 导入/导出/增删改查全部通过 |

**8/8 全部通过！** ✅

---

## 📄 许可证

本项目采用 MIT 许可证，详情见 [LICENSE](LICENSE) 文件。

MIT License © 2026 AngelDai

---

## 🙋 联系与致谢

### 联系

- **GitHub**：[AngelDai-lab](https://github.com/AngelDai-lab)
- **项目地址**：[https://github.com/AngelDai-lab/DuraDB](https://github.com/AngelDai-lab/DuraDB)

### 致谢

感谢所有为这个项目贡献代码、提出问题、提供建议的人。每一个成功的开源项目背后，都有一个支持和贡献的社区。

---

**DuraDB — Data that lasts.** 🚀