# 修改说明

原项目：JerotesWarehouse  
原作者：Jerotes_  
原项目地址：https://github.com/Jerotes/JerotesWarehouse  
原许可证：GNU General Public License v3.0

本发布是由 Isekai-Mfu 提供的非官方修改版，与原作者无关。

修改日期：2026-10-29

修改内容：

- 在 `MagicAbstractHurtingProjectile` 中增加追踪法术命中结果辅助方法。
- 对 `BaseTargetEntity` 跳过父类移动射线并返回 `MISS`，避免目标高速瞬移时主线程卡死在射线区块查询路径。
- 普通非追踪弹射物继续使用原版射线逻辑。
- 模组显示名称和版本号增加了非官方修复标识，Mod ID 与注册内容保持不变。

完整修改以本仓库补丁源码为准。修改版及补丁源码按照 GNU GPLv3 发布，不附加额外限制。
