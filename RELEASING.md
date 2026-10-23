# 发布说明

坐标：`io.github.kevinlove0712:fractional-indexing-java:0.1.0`。
使用 JDK 21 构建发布包，库仍编译为 Java 8 字节码。普通 `mvn clean verify` 继续支持 JDK 8。

## 首次准备

1. 使用 GitHub 账号 `kevinlove0712` 登录 https://central.sonatype.com/ ，确认命名空间
   `io.github.kevinlove0712` 已验证。
2. 在 Central Portal 创建发布用 User Token。仅在仓库外的 Maven `settings.xml` 中配置
   `server`，其 `id` 为 `central`；`username`/`password` 使用 Token 的对应两项。
3. 安装 GnuPG，准备自己的签名密钥，并按 Sonatype 要求发布公钥。私钥、口令及 Token
   不得提交到仓库，GPG 口令通过本机 pinentry 输入。

## 检查与发布

```sh
# 普通回归验证：分别使用 JDK 8 和 21
mvn clean verify

# JDK 21：仅检查主包、源码包、Javadoc 包，不签名、不上传
mvn -Prelease -Dgpg.skip=true clean verify

# 配好 Central 凭证与 GPG 后，签名并上传供 Central 校验
mvn -Prelease clean deploy
```

如使用独立的 Maven 配置文件，在命令中加入 `-s /path/to/settings.xml`。
`autoPublish=false`：上传校验通过后，在 Central Portal 检查本次部署并点击 Publish。
只有 Portal 确认发布成功，才能更新 README，说明依赖已可从 Central 获取。
正式版本发布后不能覆盖；下一次修改须使用新版本，同时更新 POM、SCM tag 和 README。

发布内容为主 JAR、sources JAR、javadoc JAR、POM 及其签名与校验和。
测试类、测试数据、临时探针和本地审查报告不打入运行时 JAR。
公开源码仓库保留回归测试、固定上游参考源码和测试样例生成脚本。

官方说明：
- https://central.sonatype.org/register/namespace/
- https://central.sonatype.org/publish/requirements/
- https://central.sonatype.org/publish/publish-portal-maven/
