# 发布到 Maven Central

公开源码仓库：<https://github.com/wujiiii/douyu-connect>。

消费者依赖的是 `douyu-connect-core`。`douyu-connect-example` 是带依赖的可执行示例，发布配置会排除它。

```xml
<dependency>
  <groupId>io.github.douyuconnect</groupId>
  <artifactId>douyu-connect-core</artifactId>
  <version>0.1.0</version>
</dependency>
```

上面的 `0.1.0` 是正式版坐标。仓库里的开发版本仍是 `0.1.0-SNAPSHOT`，只能通过 `mvn install` 安装到本机。

## 命名空间

Central Portal 只接受已经验证的命名空间。本仓库的 `groupId` 是 `io.github.douyuconnect`。

用 GitHub 账号验证时，命名空间对应 GitHub 用户名。当前远程仓库属于 `wujiiii`，GitHub 上没有名为 `douyuconnect` 的用户。因此用这个账号登录 Central 时，能直接验证的命名空间是 `io.github.wujiiii`。

发布前打开 [Namespaces](https://central.sonatype.com/publishing/namespaces)，确认已验证的命名空间覆盖 POM 里的 `groupId`：

- 门户里已经有 `io.github.douyuconnect`：保持现有坐标。
- 门户里只有 `io.github.wujiiii`：把父 POM、两个子模块父坐标，以及示例模块里的 `groupId` 改成 `io.github.wujiiii`，再发布。Java 包名 `io.github.douyuconnect` 可以保持不变。

## 本机凭据

在 [Central Portal 账户](https://central.sonatype.com/) 生成用户令牌。令牌写在本机 `~/.m2/settings.xml`，服务器 ID 使用 `central`。不要把用户名、密码或令牌写进本仓库、文档或 GitHub Actions 明文日志。

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>令牌用户名</username>
      <password>令牌密码</password>
    </server>
  </servers>
</settings>
```

另外需要一把已发布到公钥服务器的 GPG 密钥。`maven-gpg-plugin` 会在 `verify` 阶段对要上传的文件签名。

## 发布命令

把父 POM 和子模块的版本一起改成不带 `-SNAPSHOT` 的正式版本，例如 `0.1.0`。已发布到 Central 的版本不能覆盖。

在仓库根目录执行：

```shell
mvn -B -ntp verify
mvn -B -ntp -Pcentral deploy
```

`central` 配置档会：

- 用 flatten 插件生成 Central 需要的 POM 元数据，包括名称、描述、地址、Apache-2.0 许可、开发者和 SCM。
- 为 JAR 模块附加源码包和 Javadoc 包。
- 用 GPG 签名。
- 通过 `central-publishing-maven-plugin` 0.11.0 上传。`publishingServerId` 为 `central`。
- 排除 `douyu-connect-example`。

默认 `autoPublish` 为 `false`。上传并通过校验后，到 [Deployments](https://central.sonatype.com/publishing/deployments) 确认再发布。若要在校验通过后自动发布，追加 `-DautoPublish=true`。

同步完成前，Maven Central 搜索里还查不到新版本。
