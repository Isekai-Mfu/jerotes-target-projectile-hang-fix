# JerotesWarehouse 追踪法术卡死修复（非官方）

修复 Minecraft 1.20.1 中，Jerotes 引力师与突变末影人等高速瞬移生物战斗时，追踪法术的父类射线检测导致内置服务器主线程卡死的问题。

本修复不是官方版本，与 Jerotes_ 官方无关。已通过多场“引力师 vs 突变末影人”实战测试；测试后可正常保存并退出世界。

## 直接使用修复版

1. 备份存档。
2. 下载 Release 中的 `jerotes-1.20.1-1.3.3-target-hang-fix.jar`。
3. 从 `mods` 中移走原版 JerotesWarehouse 1.3.3 JAR，再放入修复版。
4. **不要同时安装原版和修复版。** JerotesVillage、Mutant Monsters 等其他模组保持不变。

如需回退，关闭游戏后移除修复版，并恢复原版 JAR 即可。

## 适用范围与限制

已验证环境：

- Minecraft 1.20.1
- Forge 47.4.28
- JerotesWarehouse 1.3.3
- JerotesVillage 1.1.5.20
- Mutant Monsters 8.0.8

只支持官方 JerotesWarehouse 1.3.3，其他版本请勿直接替换。补丁器会校验原版 SHA-256：

`6ac96aaf9c1a895b2047a72bdbad0d13195de9a71ccadd5a755c02ef86af395c`

本修复只处理主线程卡死。战斗中的 `Invalid entity rotation: NaN` 警告仍可能出现，其底层来源不在本次修改范围内。

## 修复原理

`BaseTargetEntity` 的 Tick 会经过 `MagicAboutEntity` 调用 `MagicAbstractHurtingProjectile`，后者使用 `ProjectileUtil.getHitResultOnMoveVector` 做射线检测并查询区块。目标频繁瞬移时，这条路径可能使服务器主线程长时间无法完成 Tick。

这些追踪法术自身会在指定 Tick 通过 AABB 执行范围效果，并不依赖父类射线命中。补丁仅对 `BaseTargetEntity` 返回 `MISS`，跳过父类射线；普通飞行弹射物仍执行原版逻辑。

修改目标：`com/jerotes/jerotes/entity/Shoot/Magic/MagicAbstractHurtingProjectile.class`。

## 使用补丁器生成修复版

如果不想直接下载修改后的完整模组，可以对官方 1.3.3 JAR 应用补丁。

Windows：将官方 JAR 拖到 `apply-fix.bat` 上。

其他安装了 Java 17 或更高版本的系统：

```sh
java -jar JerotesTargetProjectileHangFixer.jar /path/to/official-jerotes-1.3.3.jar
```

补丁器会：

- 校验官方 JAR 的 SHA-256，拒绝不支持的版本；
- 保留原版备份，不覆盖已有输出或备份；
- 生成 `jerotes-1.20.1-1.3.3-target-hang-fix.jar`；
- 只修改目标 class、`META-INF/mods.toml` 和 `META-INF/MANIFEST.MF`；
- 校验生成 JAR 的结构及补丁字节码。

生成后仍需手动用修复版替换 `mods` 中的原版，不能同时安装两者。

## 对应源码与构建

原项目：[Jerotes/JerotesWarehouse](https://github.com/Jerotes/JerotesWarehouse)

1.3.3 对应官方源码提交：[`176fc53f0fd9720fc760076732b64c7bf7170292`](https://github.com/Jerotes/JerotesWarehouse/tree/176fc53f0fd9720fc760076732b64c7bf7170292)

本仓库的 `src/PatchJerotesTargetProjectileHang.java` 是完整补丁源码，配合上述官方原版即可重新生成发布 JAR。构建补丁器需要 JDK 17+ 和 ASM 9.x；编译主类后，将编译产物与 ASM 运行类打包，并设置 `Main-Class: PatchJerotesTargetProjectileHang`。已发布的补丁器 JAR 内含 ASM 运行库。

```sh
javac --release 17 -cp asm.jar -d build/classes src/PatchJerotesTargetProjectileHang.java
```

## 许可证

原 JerotesWarehouse 由 Jerotes_ 开发，采用 GNU GPLv3。本修改版及补丁源码继续采用 GPLv3，详见 `LICENSE` 和 `NOTICE.md`。

补丁器包含 ASM，ASM 使用 BSD 3-Clause 许可证，详见 `LICENSE-ASM.txt`。
