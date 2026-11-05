# LibraryWeb2026 · 阅知图书馆

Java Web 作业二：Maven WAR + Servlet + JDBC + MySQL，前端 HTML/CSS/Vue/jQuery/Axios，同一个 Tomcat 应用提供页面和 API，无 JSP、Spring Boot、MyBatis。

## 开发环境

- JDK 21；Maven 3.9；MySQL 8.0。
- Apache Tomcat 10.1.61，使用 `jakarta.servlet`，不能直接部署到使用 `javax.servlet` 的 Tomcat 9。
- 前端依赖随 WAR 打包，版本和许可位于 `src/main/webapp/assets/vendor/`，局域网使用不依赖 CDN。

## 数据库与配置

本项目使用老师提供的 `../LibraryDB.nb3` 还原数据，不另行设计数据表或生成假数据。NB3 内含 TAR/GZIP、表定义和逐条数据；还原脚本从备份读取原始 DDL 和全部数据，目标库存在时拒绝覆盖。

在 PowerShell 中执行：

```powershell
$env:MYSQL_PWD = '你的MySQL密码'
python scripts/restore_backup.py ../LibraryDB.nb3 --database libraryweb2026
$env:MYSQL_PWD = $null
Copy-Item config/db.example.properties config/db.local.properties
```

编辑 `config/db.local.properties` 中的连接地址、账号、密码。实际配置被 Git 忽略，不进入 WAR。可用环境变量 `LIBRARY_DB_URL`、`LIBRARY_DB_USER`、`LIBRARY_DB_PASSWORD` 覆盖，也可用 JVM 参数 `-Dlibrary.config=配置绝对路径` 指定配置文件。

老师备份含 54 种图书、167 个副本、17 个用户、15 条借阅、11 条知识、10 条独立 student 表记录，以及空的推荐表和日志表。系统中的四种角色统一使用备份的 `user` 表；独立的 `student` 表保留原样。

**备份中的数据差异**：`Java核心技术 卷I` 库存为 0、实际副本 3；`Python编程：从入门到实践` 库存 3、副本 4；`Java梦一场` 库存 3、副本 5。系统不重写原始数据，管理页分别显示原始库存、有效副本数量和实际可借数量。借阅依据副本状态，新增或删除副本分别加减库存。备份里还有 9 种图书类型，统计按真实类型完整显示，不硬编码附件笔误中的“7种”。

## 构建和部署

```powershell
mvn package
powershell -ExecutionPolicy Bypass -File scripts/Start-Library.ps1 -SkipBuild
```

脚本将 WAR 部署到 `.runtime/apache-tomcat-10.1.61/webapps`，隐藏启动 Tomcat，设置用户级 `JAVA_HOME`、`CATALINA_HOME`，并生成 `bin/setenv.bat`。首次在其他电脑启动时会下载官方 Tomcat ZIP 并验证 SHA-512。配置路径使用项目的绝对路径，因此移动整个项目后请重新执行启动脚本。

- 本机：`http://localhost:8080/LibraryWeb2026/`
- 局域网：`http://本机IPv4地址:8080/LibraryWeb2026/`
- WAR：`target/LibraryWeb2026.war`
- 日志：`.runtime/apache-tomcat-10.1.61/logs/console.err.log`
- 停止：`powershell -ExecutionPolicy Bypass -File scripts/Stop-Library.ps1`

端口默认 8080，使用其他端口可传 `-Port 8081`。修改端口前先停止本项目的 Tomcat。环境变量修改后，已有终端需要重新打开；启动脚本本身已使用当前进程变量。

同一 Wi-Fi 的另一台设备访问局域网地址。若 Windows 防火墙阻止连接，允许 Java 在当前网络访问，或在管理员 PowerShell 中为当前网络建立只允许本地子网的 TCP 8080 入站规则。勿关闭整个防火墙。不同设备之间的实际连接需要在第二台设备上确认。

## 已实现功能

| 角色 | 功能 |
|---|---|
| 全部 | 注册、登录、图片验证码、重置、姓名/手机去重、角色首页、馆藏模糊查询、借书、还书 |
| 管理员 | 用户模糊查询与只读详情、推荐审核/驳回/入库、借阅关键字/超期/日期查询、按图书类型与专业统计 |
| 操作员 | 添加/编辑/废弃图书、新增/删除副本、个人借阅查询 |
| 学生 | 个人借阅全部/未还/日期范围查询 |
| 教师 | 推荐图书、相关馆藏提示、查看推荐结果、个人借阅查询 |

注册表单按四种角色动态切换附加字段，符合课程要求。用户查询初始不加载数据，提交查询后按创建时间倒序显示，不提供用户增删改功能。

## 代码结构与关键实现

```text
src/main/java/com/example/library/
  entity/    Base、Book、BookCopy、抽象 User、四个子类、Loan、BookRecommend
  orm/       复用作业一的 JDBCTool、Table、Column、Id
  dao/       查询、更新与用户类型映射
  service/   用户、图书、借还书、推荐审核业务
  servlet/   JSON 请求响应、图片验证码
  filter/    字符编码、会话请求令牌
  util/      数据库连接与事务、密码、JSON
src/main/webapp/
  index.html
  assets/    页面样式、Vue 应用、随包前端库
```

`JDBCTool` 保留作业一的 `save/update/delete/getOneById/resultSetToList`，补充父类字段遍历、MySQL 时间/外键引用转换、参数化查询与更新。所有业务 SQL 均通过它执行；更新时正确排除主键。用户的 `hasPermission()` 在四个子类实现，页面入口和 Servlet 都检查权限。

添加图书与创建副本、借还书的副本状态与借阅记录、审核入库都使用 JDBC 事务。借还书按图书和副本加行锁，避免一个副本被重复借出。应还日期按 `Book.days` 计算，超期状态按当前时间查询计算，日期范围包含结束日期全天。

删除副本采用逻辑删除：在备份已有的 `log` 表写入删除标记，图书库存减 1；有效副本和可借列表排除删除标记，保留副本行以保护历史 `Loan` 外键。废弃图书只改变状态，不删除历史借阅。新增密码使用 PBKDF2，兼容备份原有密码，返回浏览器的数据不包含密码。服务端不接受浏览器指定其他借阅人的 ID。

## 验证

```powershell
# 密码测试；数据库集成测试默认跳过。
mvn test

# 单独还原测试库后，启用真实 MySQL 集成测试，不修改演示库原始数据。
$env:LIBRARY_DB_PASSWORD = '你的MySQL密码'
powershell -ExecutionPolicy Bypass -File scripts/Test-Integration.ps1
$env:LIBRARY_DB_PASSWORD = $null
```

7 项集成测试覆盖继承映射/日期转换、库存与副本、借还书限制、并发借阅、推荐审核、注册去重、日期查询和统计。测试只清理自身创建的数据。浏览器演示步骤见 [docs/演示与提交.md](docs/演示与提交.md)。

## Git 提交

功能按阶段保留 Git 提交，远程推送待提供 Gitee 仓库地址后接入。每个功能后需实际推送，本地提交不能代替远端推送。

## 参考

- [Tomcat 10.1 官方下载及兼容要求](https://tomcat.apache.org/download-10.cgi)
- [MySQL Connector/J Maven 配置](https://dev.mysql.com/doc/connector-j/en/connector-j-installing-maven.html)
