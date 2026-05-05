# TACZ AI 自动瞄准辅助模组 - 开发计划

## 概述
开发一个 Minecraft 1.20.1 Forge 模组，当玩家持有 TACZ（永恒枪械工坊：零）模组的枪械时，自动瞄准敌人并开火，支持快捷键开关。

---

## 1. 项目环境与依赖配置

### 1.1 添加 CurseMaven 仓库
在 `build.gradle` 的 `repositories` 块中添加 CurseMaven，用于引入 TACZ 依赖。

### 1.2 添加 TACZ 依赖
使用 CurseMaven 引入 TACZ Forge 1.20.1 版本：
- Project ID: `1028108` (timeless-and-classics-zero)
- File ID: 最新 1.20.1 版本的 ID
- 坐标格式: `curse.maven:timeless-and-classics-zero-1028108:<file-id>`

### 1.3 更新 mods.toml
在 `mods.toml` 中添加对 TACZ 的软依赖声明。

---

## 2. 代码架构

### 包结构
```
alku.taczai/
├── Taczai.java                  # 主类（已有，需清理示例代码）
├── Config.java                  # 配置文件（已有，需添加 AI 配置项）
├── keybind/
│   └── KeyMappings.java         # 按键绑定注册
├── aimbot/
│   ├── AimbotHandler.java       # 核心 AI 逻辑（客户端 Tick 事件处理）
│   ├── TargetSelector.java      # 目标选择逻辑
│   └── RotationHelper.java      # 角度计算工具
└── overlay/
    └── AimbotOverlay.java       # HUD 叠加层（显示开关状态/锁定目标）
```

---

## 3. 详细功能模块

### 3.1 按键绑定 (`KeyMappings.java`)
- **功能键**: 注册一个开关按键（推荐 `G` 键或鼠标侧键），用于切换自动瞄准开/关
- 使用 Forge 的 `RegisterKeyMappingsEvent` 注册
- 使用 `KeyConflictContext.UNIVERSAL` 确保在所有场景下生效
- 在 `ClientTickEvent` 中检测按键按下并切换状态

### 3.2 配置文件 (`Config.java`)
添加 Forge 配置项：
- `aimbotRange` (int, 默认 50): 自动瞄准的最大检测距离
- `aimbotFov` (double, 默认 180): 自动瞄准的视野角度（度），180 表示全周
- `autoFire` (boolean, 默认 true): 是否启用自动开火
- `aimSpeed` (double, 默认 0.5): 瞄准平滑速度系数（0~1）
- `targetHostileOnly` (boolean, 默认 true): 是否只瞄准敌对生物
- `aimAtHead` (boolean, 默认 true): 是否瞄准头部（否则瞄准躯干中心）

### 3.3 目标选择 (`TargetSelector.java`)
- 获取玩家周围指定范围内的所有活体实体
- 过滤条件：
  - 实体存活且可攻击
  - 在配置的半径范围内
  - 有视线（使用 `hasLineOfSight()` 或射线检测）
  - 可选：只选择敌对生物（检查 `LivingEntity.isAttackable()` + 对玩家的态度）
- 目标优先级：距离最近 > 血量最低 > 角度最近
- 缓存当前目标，只有当目标死亡/超出范围/有更优目标时才切换

### 3.4 角度计算 (`RotationHelper.java`)
- 计算从玩家到目标的方向向量
- 将方向向量转换为 Minecraft 的 Yaw（水平旋转）和 Pitch（垂直旋转）
- 如果 `aimAtHead` 为 true，瞄准目标的头部位置（`getEyeY()`）
- 提供平滑插值函数，使用 `Mth.rotLerp()` 实现平滑旋转

### 3.5 核心 AI 处理 (`AimbotHandler.java`)
在 `ClientTickEvent` 中执行：
1. **状态检查**：自动瞄准是否开启
2. **持枪检测**：玩家主手/副手物品是否为 TACZ 枪械
   - 方式一：检查 `item instanceof com.tacz.guns.api.item.IGun`（需要 API）
   - 方式二：检查 `ResourceLocation.getNamespace().equals("tacz")`（兼容性更好）
3. **目标选择**：调用 `TargetSelector` 获取最佳目标
4. **自动瞄准**：
   - 有目标时，计算目标角度并平滑旋转玩家视角
   - 使用 `player.setYRot()` 和 `player.setXRot()` 修改旋转
   - 同步更新摄像头旋转
5. **自动开火**（如启用）：
   - 当锁定目标且视角对准时，模拟鼠标左键按住（TACZ 左键开火）
   - 使用 `KeyMapping.attack.setDown(true)` 实现
   - 松开时恢复 `setDown(false)`

### 3.6 HUD 叠加层 (`AimbotOverlay.java`)
使用 `RenderGameOverlayEvent` 或 `RenderGuiOverlayEvent`：
- 显示自动瞄准状态（开启/关闭）
- 显示锁定目标名称和距离
- 显示锁定指示器（准星变色或特殊标记）

---

## 4. 实现步骤（按顺序）

### Step 1: 配置构建环境
- 修改 `build.gradle` 添加 CurseMaven 仓库和 TACZ 依赖
- 刷新 Gradle 项目

### Step 2: 清理主类
- 从 `Taczai.java` 移除示例方块/物品/创造模式标签页
- 保留基本模组框架和事件总线注册

### Step 3: 更新配置文件
- 在 `Config.java` 中添加 AI 相关的配置项
- 清理旧的示例配置项

### Step 4: 注册按键绑定
- 创建 `KeyMappings.java`
- 注册 `toggleAimbot` 按键
- 在 `Taczai` 中注册到事件总线

### Step 5: 实现目标选择
- 创建 `TargetSelector.java`
- 实现实体查找、过滤、优先级排序

### Step 6: 实现角度计算
- 创建 `RotationHelper.java`
- 实现角度计算和平滑旋转工具

### Step 7: 实现核心 AI 处理
- 创建 `AimbotHandler.java`
- 整合目标选择、角度计算、视角控制和自动开火

### Step 8: 添加 HUD 叠加层
- 创建 `AimbotOverlay.java`
- 显示状态信息和锁定目标

### Step 9: 更新 mods.toml
- 添加 TACZ 为依赖项

---

## 5. 核心实现细节

### 5.1 检测 TACZ 枪械
```java
// 方法1：检查命名空间（无需 TACZ API 编译依赖）
ResourceLocation rl = ForgeRegistries.ITEMS.getKey(item.getItem());
boolean isTaczGun = rl != null && rl.getNamespace().equals("tacz");

// 方法2：检查 IGun 接口（需要 TACZ API，更可靠）
boolean isTaczGun = item.getItem() instanceof com.tacz.guns.api.item.IGun;
```

### 5.2 自动瞄准旋转
```java
// 计算朝向目标的旋转角度
double dx = target.getX() - player.getX();
double dz = target.getZ() - player.getZ();
double dy = (target.getEyeY() - player.getEyeY());

// 计算 Yaw
float targetYaw = (float) (Math.atan2(dz, dx) * 180.0F / Math.PI) - 90.0F;
// 计算 Pitch
double horizontalDist = Math.sqrt(dx * dx + dz * dz);
float targetPitch = (float) -(Math.atan2(dy, horizontalDist) * 180.0F / Math.PI);

// 平滑插值
player.setYRot(Mth.rotLerp(aimSpeed, player.getYRot(), targetYaw));
player.setXRot(Mth.rotLerp(aimSpeed, player.getXRot(), targetPitch));
```

### 5.3 自动开火
```java
// 模拟按住左键（TACZ 左键开火）
KeyMapping.attack.setDown(true);
// 或者使用更优雅的方式：检查是否瞄准目标后自动点击
```

### 5.4 事件监听
```java
@SubscribeEvent
public void onClientTick(ClientTickEvent.Post event) {
    // 只在游戏世界加载时处理
    if (Minecraft.getInstance().level == null || Minecraft.getInstance().player == null) return;
    if (!aimbotEnabled) return;
    // ... 执行 AI 逻辑
}
```

---

## 6. 注意事项

1. **性能优化**：目标检测使用距离区间过滤，避免全量实体遍历；可配置检测频率
2. **平滑插值**：避免视角突变导致眩晕，使用 `Mth.rotLerp` 平滑过渡
3. **TACZ 兼容性**：使用命名空间检测方式可减少对 TACZ API 的编译依赖，运行时仍需 TACZ 模组
4. **多人模式**：旋转修改通过正常 Movement 包同步到服务端
5. **反作弊**：本模组不修改游戏核心数据包，仅模拟输入操作

---

## 7. 文件修改/创建清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `build.gradle` | 修改 | 添加 CurseMaven 仓库和 TACZ 依赖 |
| `src/main/resources/META-INF/mods.toml` | 修改 | 添加 TACZ 依赖声明 |
| `src/main/java/alku/taczai/Taczai.java` | 修改 | 清理示例代码，注册新的事件和类 |
| `src/main/java/alku/taczai/Config.java` | 修改 | 添加 AI 配置项 |
| `src/main/java/alku/taczai/keybind/KeyMappings.java` | 新建 | 按键绑定 |
| `src/main/java/alku/taczai/aimbot/AimbotHandler.java` | 新建 | 核心 AI 逻辑 |
| `src/main/java/alku/taczai/aimbot/TargetSelector.java` | 新建 | 目标选择 |
| `src/main/java/alku/taczai/aimbot/RotationHelper.java` | 新建 | 旋转计算 |
| `src/main/java/alku/taczai/overlay/AimbotOverlay.java` | 新建 | HUD 叠加层 |
