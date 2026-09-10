# 开发工作流

## 环境前提

- **JDK 17**（`pom.xml` 的 `<java.version>17</java.version>`）。用 `java -version` 确认。
- Maven 用项目自带的 `mvnw` / `mvnw.cmd`，无需全局安装 Maven。首次运行会下载依赖，较慢。
- 项目路径含中文和空格（`C:/Users/86158/Desktop/毕业论文/202339170206 wjm`），**命令行里务必加引号**。

## 构建与运行

```bash
cd "C:/Users/86158/Desktop/毕业论文/202339170206 wjm"

# 编译
./mvnw -q clean compile

# 打包（跳过测试）
./mvnw -q clean package -DskipTests

# 运行：MySQL 模式
./mvnw spring-boot:run

# 运行：H2 演示模式（推荐，免数据库安装）
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2

# 运行已打包 jar
java -jar target/star-garden-0.0.1-SNAPSHOT.jar --spring.profiles.active=h2
```

Windows PowerShell 下用 `.\mvnw.cmd` 替代 `./mvnw`。

启动成功标志（约 5 秒）：

```
Tomcat started on port 8081 (http) with context path '/'
Started StarGardenApplication in X.xxx seconds
```

访问 `http://localhost:8081/`。管理员账号 `admin` 由 DataSeeder 首次启动创建。

## 数据库：两套 Profile

配置在 `src/main/resources/`：

| 文件 | 说明 |
|---|---|
| `application.properties` | 默认 **MySQL 8**：`jdbc:mysql://localhost:3306/star_garden`，用户名 `root`。密码写在本机文件里，**别提交到公开仓库** |
| `application-h2.properties` | **H2 文件库**：`jdbc:h2:file:./data/star_garden`，用户 `sa`、密码空，控制台 `/h2-console`，端口 8081 |
| `data.sql` | 初始化脚本，`spring.sql.init.mode=embedded` + `continue-on-error=true` |

两者都是 `ddl-auto=update`、`spring.jpa.defer-datasource-initialization=true`（先建表再跑 data.sql）。

**切换方式**：加 `--spring.profiles.active=h2`。

**答辩 / 演示建议用 H2**：不需要本地 MySQL，数据就在 `data/star_garden.mv.db`，整个目录拷走就能在别的机器复现。

## 数据操作

### H2 控制台

`http://localhost:8081/h2-console`，JDBC URL 填 `jdbc:h2:file:./data/star_garden`，用户 `sa`，密码留空。

### 常用排查 SQL

```sql
-- 推荐算法是否有足够数据（评估需要 >2 条记录的用户）
SELECT user_id, COUNT(*) c FROM task_record GROUP BY user_id HAVING c > 2 ORDER BY c DESC;

-- 各类别分布
SELECT task_category, COUNT(*), SUM(duration_minutes)
FROM task_record GROUP BY task_category;

-- 模拟用户是否播种成功
SELECT COUNT(*) FROM users WHERE username LIKE 'sim%';

-- 清空重来（H2 模式更简单：直接删 data/star_garden.mv.db 后重启）
DELETE FROM task_record WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'sim%');
```

### 重建数据库的两种做法

- **H2**：停服务 → 删 `data/star_garden.mv.db`（trace 文件一起删） → 重启，`DataSeeder` 会重新播种
- **MySQL**：`DROP DATABASE star_garden; CREATE DATABASE star_garden DEFAULT CHARSET utf8mb4;` → 重启

注意：`ddl-auto=update` **只加列不删列不改类型**。改了实体字段类型或重命名，H2 走上面的删除重建，MySQL 需手工 `ALTER TABLE`。

## 调试技巧

- **改 Thymeleaf 模板无需重启**（`spring.thymeleaf.cache=false`），刷新页面即可。
- **改 Java 代码需重启**。用 `spring-boot:run` 时 Ctrl+C 停掉再启动。
- `run.log` 是启动时重定向产生的日志，编码为 **UTF-16LE**，直接 `cat` 会看到乱码，用 `iconv -f UTF-16LE -t UTF-8 run.log | tail -50` 或交给编辑器识别。
- `spring.jpa.show-sql`：MySQL profile 为 `true`，H2 profile 为 `false`。想看 SQL 时临时改。
- 日志级别 `logging.level.com.stargarden=INFO`，调 `DEBUG` 可看更多细节。

## 前端改动

- 模板：`src/main/resources/templates/`（含 `admin/` 子目录 5 个后台页）
- 样式：`static/css/stargarden.css`（单一文件）
- 脚本：`static/js/stargarden.js`（单一文件）
- 图表库：`static/lib/echarts.min.js`（本地内置，无 CDN 依赖，离线可演示）

改了 CSS/JS 若页面没变化，是浏览器缓存，强制刷新（Ctrl+Shift+R）。

## 常见故障排查

| 现象 | 原因与解决 |
|---|---|
| 启动报 `Communications link failure` | 没起 MySQL，或库 `star_garden` 不存在 → 改用 `--spring.profiles.active=h2`，或建库 |
| 端口 8081 被占用 | 改 `server.port`，或 `netstat -ano \| findstr 8081` 找到进程结束掉 |
| `/admin/eval` 指标全是 0 | `task_record` 没数据或有效用户太少 → 确认 `app.data.seed=true` 且 DataSeeder 跑过 |
| 推荐结果为空 | 当天已完成类别被过滤；或 `focus_task` 无启用任务且内置池为空（内置池写死在代码里，不会为空） |
| Lombok 报找不到 getter/setter | IDE 需开启 Annotation Processing；命令行构建走 `maven-compiler-plugin` 的 `annotationProcessorPaths`，已配好 |
| WebSocket 连不上 | 检查 `WebMvcConfig` 是否放行了 `/ws/**`；端点用 `@ServerEndpoint`，取 Bean 要走 `SpringContextHolder` |
| 改了实体启动报列不存在 | `ddl-auto=update` 没生效（比如手工删过表） → H2 删库文件重启 |

## 工作区卫生

项目根目录堆积了不少调试产物，提交前建议清理或加进 `.gitignore`：

- `run.log`、`enc.html`、`enc2.html`、`hist.html`、`hist_now.html`、`tasks_hall2.html`、`tasks_page2.html`、`tasks_v2.html`、`v_admin.html`、`profile_v2.html` —— 抓取的页面快照与日志
- `c3.txt`、`cookie2.txt` —— **含登录 Cookie，切勿提交到公开仓库**
- `backup_admin_task_record_20260910.txt`、`backup_admin_user_garden_20260910.txt` —— 数据备份

推 GitHub 前务必确认 `.gitignore` 覆盖 `target/`、`*.log`、cookie 文件与备份文件。
