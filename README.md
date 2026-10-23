# Fractional Indexing for Java

**在列表任意位置插入记录，无需修改其他记录的排序键。**

这是一个 Java 分数索引（Fractional Indexing）库：根据插入位置两侧的排序键生成新键，
让新记录排在指定位置，已有记录的排序键保持不变。支持头部、尾部、中间插入及批量插入，
适用于任务看板、拖拽排序、播放列表等需要自定义顺序的场景。

例如，已有两条记录的排序键为 `a0` 和 `a1`，在它们之间插入一条记录：

```java
String key = FractionalIndexing.generateKeyBetween("a0", "a1"); // a0V
```

按字符串顺序排列就是 `a0 < a0V < a1`，原有两条记录的排序键都不需要修改。

- 支持头部、尾部、中间插入，以及一次批量生成多个有序键。
- 支持 Java 8+，无运行时第三方依赖，可用于普通 Java 或 Spring 应用。
- 默认每个排序键最多 128 字符、每批最多 10,000 个键，均可按调用配置。

排序键在反复向同一间隙插入时会增长；达到长度限制时需由业务层整理，详见下文。
并发控制与数据持久化也由业务层负责。

**项目来源：** 基于 [rocicorp/fractional-indexing](https://github.com/rocicorp/fractional-indexing)
的默认编码实现移植，属于独立 Java 项目，并非 Rocicorp 官方 Java 版本。上游及本项目均采用
[CC0-1.0](LICENSE)。固定上游提交、原始源码、兼容范围及边界修复见 [UPSTREAM.md](UPSTREAM.md)。

## 构建

需要 JDK 8+ 和 Maven 3.9.x：

```sh
mvn clean verify
mvn install
```

生成 `target/fractional-indexing-java-0.1.0.jar`。
当前 Maven 坐标仅用于本地安装，**尚未发布到 Maven Central**：

```xml
<dependency>
  <groupId>io.github.kevinlove0712</groupId>
  <artifactId>fractional-indexing-java</artifactId>
  <version>0.1.0</version>
</dependency>
```

## 使用

```java
import dev.fractionalindexing.FractionalIndexing;
import java.util.List;

String first = FractionalIndexing.generateKeyBetween(null, null); // a0
String before = FractionalIndexing.generateKeyBetween(null, first); // Zz
String after = FractionalIndexing.generateKeyBetween(first, null); // a1
String middle = FractionalIndexing.generateKeyBetween(first, after); // a0V

List<String> front = FractionalIndexing.generateNKeysBetween(null, "a0", 3);
// [Zx, Zy, Zz]
List<String> inside = FractionalIndexing.generateNKeysBetween("a0", "a1", 3);
// [a0G, a0V, a0l]
List<String> end = FractionalIndexing.generateNKeysBetween("a0", null, 3);
// [a1, a2, a3]
```

`null` 表示这一侧没有相邻记录。`left`、`right` 都非空时必须满足 `left < right`；
批量结果按升序排列，依次绑定待插入的记录即可。`count == 0` 返回空列表。
无效键、相等或逆序边界、负数数量抛出 `IllegalArgumentException`。
`A1` 不是合法键；头字符有长度含义，不能任意构造键。

## 长度和批量限制

| 限制 | 默认值 | 可配置范围 |
| --- | ---: | --- |
| 输入和生成键的长度 | 128 字符 | 至少 2 |
| 一次生成的键数量 | 10,000 | 上限至少 1；请求数量可为 0 |

现有静态调用自动采用默认限制。需要调整时使用重载，无全局可变配置：

```java
// 单键：最多 256 字符
String key = FractionalIndexing.generateKeyBetween("a0", "a1", 256);

// 批量：3 个键；每个最多 128 字符；本次允许最多 1000 个键
List<String> keys = FractionalIndexing.generateNKeysBetween("a0", "a1", 3, 128, 1000);
```

- 输入长度在字符扫描和边界比较之前检查，`count == 0` 也检查边界。
- 生成过程在追加超限的小数字符之前检查长度，整数进位结果同样检查。
- 输入或结果过长抛出 `KeyLengthExceededException`（继承 `IllegalArgumentException`）。
- 数量超限、负数或无效配置抛出 `IllegalArgumentException`，在创建结果列表前检查。
- 上限是包含边界的：128 字符和 10,000 个键本身允许。不会截断，也不返回部分批量结果。
- 异常不会自动重排文档；调用方可整理排序键后重试。超长现有键需先整理或显式提高上限。

这些限制是每次调用的资源预算；提高上限时应结合数据库字段和可用内存，
避免直接使用未经校验的用户输入作为配置值。

## 普通 Java：直接运行 main

不需要 Spring，也不需要配置文件。下面可保存为 `Main.java`，兼容 Java 8：

```java
import dev.fractionalindexing.FractionalIndexing;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        // 默认：输入和结果最多128字符
        String defaultKey = FractionalIndexing.generateKeyBetween("a0", "a1");

        // 自定义：仅本次调用允许最多256字符
        int maxKeyLength = 256;
        String customKey = FractionalIndexing.generateKeyBetween("a0", "a1", maxKeyLength);

        // 默认批量：每个键最多128字符，一次最多10000个
        List<String> defaultBatch = FractionalIndexing.generateNKeysBetween("a0", "a1", 3);

        // 自定义批量：生成3个，每个最多256字符，本次数量上限1000
        int maxBatchSize = 1000;
        List<String> customBatch = FractionalIndexing.generateNKeysBetween(
                "a0", "a1", 3, maxKeyLength, maxBatchSize);

        System.out.println(defaultKey);   // a0V
        System.out.println(customKey);    // a0V
        System.out.println(defaultBatch); // [a0G, a0V, a0l]
        System.out.println(customBatch);  // [a0G, a0V, a0l]
    }
}
```

在项目根目录构建 JAR 后，编译运行示例（macOS/Linux）：

```sh
mvn package
mkdir -p target/examples
javac -cp target/fractional-indexing-java-0.1.0.jar -d target/examples Main.java
java -cp target/examples:target/fractional-indexing-java-0.1.0.jar Main
```

Windows 的 classpath 分隔符用 `;` 替换 `:`。
**256 是最大允许长度，不是要求生成256字符**，所以上述两个单键结果都是 `a0V`。
如果某次需要生成129字符，默认调用会抛 `KeyLengthExceededException`，传256的调用则允许。
自定义调用不会改变之后调用的默认值。

## Spring Boot：读取配置后传给库

在已有 Spring Boot 应用的 `application.yml` 中配置：

```yaml
fractional-indexing:
  max-key-length: 256
  max-batch-size: 1000
```

将下面的服务放到应用组件扫描的包下（包名按应用调整）：

```java
package com.example.demo;

import dev.fractionalindexing.FractionalIndexing;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ListOrderService {
    // Spring未配置这些属性时，分别回退到128和10000
    @Value("${fractional-indexing.max-key-length:128}")
    private int maxKeyLength;

    @Value("${fractional-indexing.max-batch-size:10000}")
    private int maxBatchSize;

    public String newKey(String left, String right) {
        return FractionalIndexing.generateKeyBetween(left, right, maxKeyLength);
    }

    public List<String> newKeys(String left, String right, int count) {
        return FractionalIndexing.generateNKeysBetween(
                left, right, count, maxKeyLength, maxBatchSize);
    }
}
```

其他 Spring 组件注入 `ListOrderService` 后，调用 `newKey`/`newKeys`，就会使用配置中的
256字符和1000个上限。不要自己 `new ListOrderService()`，否则不会获得 Spring 属性注入。

**只有把配置值传给重载方法，配置才会影响库的行为。** 即使 YAML 配了256，下面的
两参数调用仍使用库默认的128：

```java
FractionalIndexing.generateKeyBetween(left, right); // 仍然是128
```

库不会自动读取 YAML、Spring Environment、环境变量或 Nacos。Spring 示例中的
`@Value` 属于应用的配置读取方式，库本身仍无 Spring 依赖；非 Spring 应用也可以从
配置文件、环境变量或 JVM 参数读取整数，再像 `main` 示例那样传入。
本示例演示启动时注入配置，动态刷新由应用使用的配置集成负责。

## 数据库存储与并发

排序规则必须与 Java `String.compareTo` 一致。默认编码仅包含 ASCII 字母数字；
MySQL 字段可采用 `VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin`，
配合列表 ID 与排序键的联合索引。该容量与库的默认长度上限一致；如调整库配置，需同步检查字段容量。
避免忽略大小写的排序规则，前端也不要使用依赖语言区域的 `localeCompare`。

在业务层读取当前相邻记录后计算排序键。移动记录时先从逻辑顺序中排除该记录。
相同输入会生成相同输出：本库**不解决并发冲突**。调用方需要在列表级锁/数据库事务中
分配和保存，或使用修订号检查并重试。不要把排序键当作记录 ID 或显示序号。

## 长度增长和整理

默认编码，从 `a0` 开始连续前插或后插 100 万次，最后的键仅 5 字符。
但从 `a0/a1` 开始反复向同一个边界插入，可能很快增长：

| 操作 | 首次达到 32 字符 |
| --- | --- |
| 每次紧挨左边界新增一条记录 | 第 175 次 |
| 每次紧挨右边界新增一条记录 | 第 146 次 |
| 每批三条记录，持续紧挨左边界 | 第 88 批 |
| 每批三条记录，持续紧挨右边界 | 第 73 批 |

一次在 `a0/a1` 中间批量生成 1000 个键，最长 4 字符；显式将批量上限提高至 100000 时，
生成 100000 个键的最大长度为 6 字符。
这些数字是固定起始状态的测试结果，不是所有状态的保证。

本库执行上述硬限制，但不改写其他记录。调用方可在达到业务预警阈值时提前整理，
或在捕获 `KeyLengthExceededException` 后处理：

1. 在列表级并发保护下读取当前记录顺序。
2. `generateNKeysBetween(null, null, itemCount)` 生成一组新键；超过默认批量上限时，
   明确配置更合适的上限或设计分批处理。
3. 将记录按当前顺序绑定新键，在同一事务中保存，并重新计算本次操作的位置。

整理只改变排序键，不需要改记录 ID 或业务内容。如果数据库设了排序键唯一约束，需要
处理逐行更新时的新旧键冲突；不能假定事务本身会绕过唯一约束。
应用层仍需限制请求正文大小和请求频率。

## 测试

```sh
mvn clean verify
# 可选：重新生成原版对照样例，需要 Node.js 18+，不需要 npm install
node scripts/generate-fixtures.mjs
mvn test
```

覆盖 2,457 组固定原版对照样例、非法输入、整数进退位、最小/最大整数边界、
百万次首尾插入、十万键批量生成、随机插入/移动/删除、32 字符阈值以及超长键。
独立审查另外补充了 28,350 组对照向量及边界检查；长键回归测试验证单键和批量生成的
累计分配量随输入长度线性增长（需要 JVM 提供线程分配计数，否则明确跳过）。
额外覆盖默认/自定义上限、边界值、生成超限、批量中途超限及无效配置。
超长键和超大批量算法测试显式提高配置，原对照语料保持不变。
已在本地 JDK 8u462 和 JDK 21.0.1 下通过全部回归测试，均无跳过；产物字节码版本为 52（Java 8）。
GitHub Actions 配置了 Java 8/21 的测试矩阵。

## 开源与发布

代码及参考源码采用 [CC0-1.0](LICENSE)，来源记录在 [UPSTREAM.md](UPSTREAM.md)。
源码仓库：https://github.com/kevinlove0712/fractional-indexing-java

Maven Central 发布配置已放入 `release` profile；发布准备及操作见 [RELEASING.md](RELEASING.md)。
当前尚未发布到 Maven Central，项目不包含任何发布凭证。
