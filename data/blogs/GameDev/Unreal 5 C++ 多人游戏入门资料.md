```toml
title = "Unreal 5 C++ 多人游戏入门资料"
english_title = "Unreal 5 C++ multiplayer starter note."
slug = "本文为个人笔记，是一篇轻量化的资料类的入门文章，适合非 UE 开发者和无多人游戏开发经验者阅读。包括使用 Rider 开发的注意事项、UE C++ 常用类、垃圾回收、多人网络同步概念与特性等。"
published = true
last_edited_date = "2025-08-26T22:27:00"
authors = "宏楼 Koiro"
class = "Dev"
date = "2025-05-13T11:37:00"
date = "2025-05-14"
tags = ["Cpp", "Unreal"]
```

# 注意事项

## 从 Rider 启动编辑器

由于 UE 的 C++ 反射内容必须编译后才能在编辑器中使用。推荐从 Rider 启动编辑器，这会预先编译后启动 UE。

## Ctrl+S 并不是全部保存

全部保存的默认按键是 Ctrl+Shift+S，即使按再多 Ctrl+S 只会保存当前资源！在场景中按 Ctrl+S 是保存当前关卡，也不会保存所有内容。此外，所有的保存按钮也都是保存当前资源，场景选项卡中的也是。想要保存记得使用 Ctrl+Shift+S。

## 使用版本管理工具

请务必使用版本管理工具存储工作，在入门 UE 时，强制关机、引擎崩溃、撤销内容错误或者不慎将不想要的结果保存都可能导致工程完全无法进行下去。

因为 UE 会将许多内容存在内存中，因此想要使用版本管理工具回退时：

1. 先关闭编辑器并不保存所有修改；
2. 回退版本；
3. 使用 Rider 打开编辑器；

这样可以安全地回退版本。

# UE 常见类

这个小节会描述 UE 当中重要且常用的一些类，总体的标题顺序是从基类到派生类。

## `UObject`

UObject 是 UE 参与反射与垃圾回收的最基本类。其包含两个重要子类：

- AActor
- UActorComponent

在类命名时通常使用 U 开头来标记此类为 UObject 的派生类，A 开头来标记此类为 AActor 的派生类。下文会对这两个类以及 UObject 的垃圾回收机制进行简单地介绍。

## `AActor`

AActor 是可以放在场景中的物体的父类。具体来说，它有变换属性（旋转、缩放、位置等），同时也有网络功能。

> 参考资料（官方文档）：[https://dev.epicgames.com/documentation/en-us/unreal-engine/unreal-engine-actor-lifecycle](https://dev.epicgames.com/documentation/en-us/unreal-engine/unreal-engine-actor-lifecycle)
> 
- 完整生命周期图表
    
    ![actorlifecycle1.png](./Unreal5Cpp多人游戏入门资料/actorlifecycle1.jpg)
    

一些重点（按顺序）：

- `BeginPlay`（Level 开始时被调用）
- `EndPlay`（在 Destory 中被调用）
- `Tick`（每帧更新）
- `BeginDestroy`（与垃圾回收相关，通常用于释放内存或处理多线程资源）
- `FinishDestory`（与垃圾回收相关，这是 `UObject`被释放前的最后一次调用）

## `APawn`

APawn 是用于表征可控制的实体，如玩家、NPC 等，它是 AActor 的子类。

[https://dev.epicgames.com/documentation/en-us/unreal-engine/API/Runtime/Engine/GameFramework/APawn](https://dev.epicgames.com/documentation/en-us/unreal-engine/API/Runtime/Engine/GameFramework/APawn)

## `UActorComponent`

`UActorComponent` 是一个重要的类，它是渲染网格、图像、碰撞、音频等玩家视觉、听觉、交互的唯一途径，也是游戏逻辑的重要载体。下文将 Component 称为“组件“。

组件被附加在 Actor 上，其没有变换信息。要想组件被正常使用，其需要被注册，注册会在组件被当作 sub-objects 时被自动完成，也可以手工使用 `RegisterComponent` 函数注册（但运行时注册会影响性能，这点需要注意）。

注册组件时，`UActorComponent::OnRegister` 会被触发。此外，`CreateRenderState` 和 `OnCreatePhysicsState` 也会被触发。如果想要取消注册一个组件，使用 `UnregisterComponent` 组件即可。

下面为组件的一些函数与功能：

- `PostLoad` 静态 Actor 加载时触发
- `InitializeComponent` 组件首次初始化时触发
- `BeginPaly` Actor 进入游戏时触发
- `TickComponent` 函数每帧都会执行
- `EndPlay` Actor 被移除时触发

```cpp
MyComponent = CreateDefaultSubobject<UMyCustomComponent>(TEXT("MyComponentName"));
MyComponent->SetupAttachment(RootComponent); // RootComponent 是 Actor 的默认根组件
```

如果需要一个组件有物理属性，可以使用其子类 `USceneComponent`，更进一步的，如果在此基础上还需要物理碰撞或者渲染能力，可以使用 `UPrimitiveComponent`。下面是常见的子类与其用途。

| **子类** | **功能说明** | **示例场景** |
| --- | --- | --- |
| `USceneComponent` | 提供空间变换能力，支持层级嵌套（如根组件、子组件） | 角色骨骼、摄像机弹簧臂 |
| `UPrimitiveComponent` | 继承自 `USceneComponent`，支持物理碰撞与渲染（如静态网格、粒子系统） | 武器碰撞体积、可破坏物体 |
| `UAudioComponent` | 直接继承自 `UActorComponent`，提供音频播放功能 | 背景音乐、角色脚步声 |
| `UMovementComponent` | 实现物理移动逻辑（如角色移动、飞行载具） | 角色跳跃、载具加速 |

# UE 垃圾回收机制

UE 采用标记清扫的垃圾回收策略，标记清扫的具体思想是：先在标记阶段从根集（Root Set）对象遍历对象图（一个包含了对象间引用信息的有向图），将所有被遍历的对象标记为“可达”。接着在清扫阶段遍历所有对象，如果一个对象没有可达标记，则说明其需要被释放。

在 UE 中，所有 `UObject` 类型及其子类型会参与到垃圾回收中。其中，因为 Actor 总是被场景和自己引用，所以需要被手动销毁。Actor 的销毁方式为调用 `Destroy` 函数。

下文会列举会在对象图产生边的情况。

### 所有强引用（Hard Reference）

- 持有一个 `UPROPETY` 宏标记的 C++ 裸指针

```cpp
UPROPERTY()
UMyObjectClass* Foo;
```

- 持有 `UPROPERTY` 宏标记的 C++ 裸指针的 UE 容器

```cpp
UPROPERTY()
TArray<UMyObjectClass*> Foo;
```

- 根集对象（可以通过 `MyObject->AddToRoot()` 将对象添加到根集）
- 被 `TStrongObjectPtr` 管理的对象（不需要 `UPROPERTY`）

### 某些弱引用（Soft Reference）

- 在 `UFUNCTION` 内被声明的新的 `UObject` 会存活到其生命周期结束（当前函数结束）。
- 在结构体或类中直接使用 `UObject` 的裸指针作为成员变量
- 使用 `TWeakObjectPtr<T>` 或 `TSoftObjectPointer<T>` 弱指针作为成员变量

## 智能指针与非 `UObject` 对象的内存管理

对于非 UObject 的对象，其不支持垃圾回收，但是 UE 也提供了与 C++11 标准库类似的内存管理方式，即智能指针。本小节不再赘述标准库中的智能指针的特性与工作原理，读者可以阅读 [LEARN C++ 相关章节](https://www.learncpp.com/cpp-tutorial/introduction-to-smart-pointers-move-semantics/)。下面表格介绍了非 UObject 与 UObject 智能指针，需要注意的是，非 UObject 的智能指针不能给 UObject 使用！

| 非 UObject 智能指针 | 机制 | 备注 | 可空性 | 标准库指针类比 |
| --- | --- | --- | --- | --- |
| `TUniquePtr` | 所有权 | - | 可空 | std::unique_ptr |
| `TSharedPtr` | 引用计数 | - | 可空 | std::shared_ptr |
| `TSharedRef` | 非空引用计数 | 可以转换为 TSharedPtr | 不可空 | - |
| `TWeakPtr` | 弱引用不计数 | 用于解决引用计数的循环引用问题 | 可空 | std::weak_ptr |
| UObject 智能指针 | 机制 | 备注 |  | - |
| `TStrongObjectPtr` | 引用计数 | 用于保护 UObject 不被垃圾回收 | 可空 | - |
| `TWeakObjectPtr` | 弱引用不计数 | 上面的弱引用版本 | 可空 | - |

> Tips：为什么 UE 有一套自己的智能指针？
UE 源码起源早于 C++ 11。另一方面，自主的智能指针有利于多平台支持的开发。此外，独立的智能指针也提供了统一的命名风格等好处。笔者仅了解至此。
> 

## 参考资料

- [https://mikelis.net/memory-management-garbage-collection-in-unreal-engine/](https://mikelis.net/memory-management-garbage-collection-in-unreal-engine/)

# 多人游戏与网络同步机制

在专用服务器模型下的多人游戏，服务端存储了真正的游戏状态，大部分游戏逻辑会在服务端进行。服务端经过运算后会将需要的数据同步给客户端。客户端通过发送数据或执行远程函数（RPC）的方式对服务端的数据进行修改。

复制（Replicate）是同步的重要过程，权威服务器（在大型射击游戏中通常为专用服务器）会将状态数据复制到客户端中，客户端会在本地执行渲染和音频行为。

`AActor` 是第一个用于支持网络的类，大部分复制行为可以在 Actor 中定义，其也是网络同步的数据的主要承载者。此外，`UObject`的派生类也可以通过附加到 Actor 的方式被正确地复制。同时，对于多人游戏，每个连接都会绑定一个 Pawn 与一个 PlayerController。

## Actor 的网络属性

### 所有者：Role 和 Remote Role

一个 Actor 所属于谁，即哪个机器包含了当前 Actor 的真正状态，这个属性被称为所有者。所有者会向连接的机器进行有条件地（例如相关性、休眠状态等）复制。想要查看一个 Actor 被谁所有，可以使用 `AActor::GetLocalRole` 和 `Remote Role`。

- Local Role 代表的是当前机器对该 Actor 的权限
- Remote Role 代表的是对端机器对该 Actor 的权限

| ROLE_None | 这个 Actor 不是可复制的 | Local Role | Remote Role |
| --- | --- | --- | --- |
| ROLE_Authority | 当前机器是 Actor 的所有者，持有 Actor 真正的状态 | 服务器 | 客户端 |
| ROLE_SimulatedProxy | 当前机器只是 Actor 的模拟，对于 Actor 的真正状态是只读的，同时不能调用远程函数 | 客户端 | 无 |
| ROLE_AutonomousProxy | 当前机器虽然是 Actor 的模拟，但可以更改其真正状态和调用远程函数 | 客户端 | 服务器 |

在服务器-客户端的模式下，一个 Actor 的所有者由服务器掌控，同时，客户端无法修改任何 Actor 的所有者。此外，服务器-客户端模式下，客户端的对端机器是服务端，服务端的对端机器是客户端。

### 优先级（Priority）

Actor 的 `NetPriority` 变量是一个浮点数，代表一个 Actor 的优先级。优先级更高的 Actor 拥有更多的带宽。重载 `GetNetPriority` 函数可以修改一个 Actor 的优先级。同时，优先级是会受各种参数影响的一个动态变量，例如观察者的位置、观察者的方向、上次复制 Actor 以来的时间等等。

### 复制（Replication）

Actor 要被从服务器发送复制到客户端，但是并不是所有内容都要被发送，下面的代码和注释描述了声明可复制字段和注册可复制字段的过程。

```cpp
AMyActor::AMyActor()
{
	bReplicates = true; // 启用 Actor 的复制
	...
}
```

```cpp
#pragma once

#include "CoreMinimal.h"
#include "GameFramework/Actor.h"
#include "MyActor.generated.h"

UCLASS()
class HELLOUNREAL_API AMyActor : public APawn
{
	GENERATED_BODY()
	
public:	
	AMyActor();

	UPROPERTY(EditAnywhere)
	AMyActor* Friend;

	// ========== Replication ==========
	
	/// 要想声明一个可以被复制的变量，首先需要使用 Replicated 或 ReplicatedUsing（见下）标签
	/// 接着还必须在 GetLifetimeReplicatedProps 注册要复制的变量（见下）
	UPROPERTY(Replicated)
	bool IsSleeping;

	/// @c ReplicatedUsing 为复制提供了一个声明回调的机会，
	/// 这个功能被称作 "RepNotify"。
	/// 在这个例子中，@c OnRep_OwnedCatsCount 会在客户端成功收到变量的复制值时运行。
	/// 需要注意的是，这个在回调中并不需要执行赋值，赋值在注册后是自动的。
	/// 回调函数会在被复制赋值之后被触发。
	UPROPERTY(ReplicatedUsing=OnRep_OwnedCatsCount)
	int OwnedCatsCount;

	/// 所有有复制变量的 Actor 都需要重载该函数，其作用是注册需要网络复制的变量。
	/// 
	/// 其会被在 Actor 被创建并加入网络时被触发，用于构建网络同步的元数据；
	/// 通常在服务端，是服务器生成 Actor 时调用此函数。
	/// 
	/// 使用该函数务必首先调用 @c Super::GetLifetimeReplicatedProps 以确保父类的内容被注册
	void GetLifetimeReplicatedProps(TArray<FLifetimeProperty>& OutLifetimeProps) const override;
	
protected:
	UFUNCTION()
	void OnRep_OwnedCatsCount();
};
```

### 相关性（**Relevancy**）

一个关卡可能很大，我们希望只有与玩家相关的 Actor 才被更新。UE 提供了相关性机制，只有与某个网络连接有关的 Actor 才会被复制。同时，如果某些 Actor 与当前客户端不相关，它们会在客户端被销毁。

网络驱动程序会自动调用 `AActor::IsNetRelevantFor` 来判断某个 Actor 是否与某个连接相关。如果想要强制设置相关性，可以使用 `AActor::ForceNetRelevant` 来管理。下面是默认的相关性判断条件：

1. 强制相关条件
满足以下任一条件时，Actor **必定相关**（向客户端同步）：
    1. Actor 被标记为 **`Always Relevant`**（始终相关）。
    2. Actor **属于当前连接的控制权对象**：
    3. 是当前连接的 `Pawn`。
    4. 被当前连接的 `Pawn` 或 `Player Controller` 拥有（`OwnedBy`）。
    5. Actor 是当前连接 `Pawn` 触发的**行为发起者**（如噪音、伤害的 Instigator）。
2. 依赖所有者的相关性
若满足以下条件，Actor 的相关性 **由其 Owner 决定**：
    1. Actor **设置了 Owner**（通过 `SetOwner`）。
    2. Actor 启用了 **`bNetUseOwnerRelevancy`**（使用 Owner 的相关性设置）。
3. 强制不相关条件
满足以下任一条件时，Actor **必定不相关**：
    1. Actor **仅对 Owner 相关**（`bOnlyRelevantToOwner=true`），且：
    2. 没有 Owner **或** Owner 自身不相关。
    3. Actor **被隐藏**（`bHidden=true`）。
    4. Actor **无根组件**（Root Component） **或** 根组件的碰撞未启用（`CollisionEnabled=NoCollision`）。
4. 基于骨骼附加的相关性
若 Actor **附加到其他 Actor 的骨骼**（如角色装备的武器）：
    1. 使用被附加 Actor 的 **基础相关性规则**（附加目标的网络相关性决定其相关性）。
5. 距离相关性（可选）
若启用了 **距离相关性**（通过 `AGameNetworkManager::bUseDistanceBasedRelevancy`）：
    1. Actor 在客户端的 **可视距离内**（基于 `NetCullDistanceSquared`）时相关，否则不相关。

**规则优先级说明**

1. **强制相关**条件的优先级最高（如 `Always Relevant`）。
2. **强制不相关**条件优先级次之（如隐藏或无根组件）。
3. 其余条件按顺序判断，最终由 **距离或附加目标相关性** 决定。

**调试建议**

- 使用控制台命令 `VisualizeNetworkRelevancy` 可视化相关性范围。
- 检查 Actor 属性：`bAlwaysRelevant`、`bOnlyRelevantToOwner`、`NetCullDistanceSquared`。

### 网络休眠（**Network Dormancy**）

休眠是一个手动管理的网络优化机制，休眠中的 Actor 不会发生复制，也不会在客户端因为相关性而被销毁。同时，不要修改休眠中的 Actor 的可复制数据，这可能发生数据丢失。

休眠属性存储在 AActor::NetDormacy 属性中，可以通过 AActor::SetNetDormancy 来更改。

`NetDormacy` 的类型是 `TEnumAsByte<enum ENetDormancy>` ，其中 ENetDormancy 枚举类型有下：

- `DORM_Never` 永不休眠
- `DORM_Awake` 苏醒
- `DORM_DormantPartial` 对于部分连接是休眠的，通过 `AActor::GetNetDormancy` 来获得具体在哪些连接中是休眠的
- `DORM_DormantAll` 对于所有连接都是休眠的
- `DORM_Intial` 初始对所有连接都是休眠的

通常使用：

```cpp
AActor::SetDormancy(ENetDormancy::DORM_Awake); // 唤醒
AActor::FlushNetDormancy(); // 唤醒

AActor::SetDormancy(ENetDormancy::DORM_DormantAll); // 休眠
```

## RPC 远程函数调用

除了复制和复制回调外，UE 还支持 RPC 来进行两端通信。简单来说，RPC 允许开发者在一端机器让另一端的机器调用某些函数。可以使用标签来标记这个函数在哪里执行，例如 Server 代表这个函数在服务器上执行，下面是一个简单的列表。

| 标签 | 运行位置 | 备注 |
| --- | --- | --- |
| Client | 客户端 | 所有在这个 Actor 上拥有连接的客户端 |
| Server | 服务器 | 只能在拥有这个 Actor 的客户端上调用 |
| Remote | 对端 |  |
| NetMulticast | 客户端与服务器 | 服务器与所有与这个 Actor 相关的客户端 |

```cpp
	/// RPC 远程函数调用，可以实现跨端调用函数的功能。
	/// @c Reliable 标签声明了这此调用一定要被抵达，不管带宽情况如何；
	/// 默认的可靠性是 Unreliable.
	/// @c WithCalidation 提供了简单的程序验证，通过实现对应的 XXX_Validate 函数实现
	UFUNCTION(Server, Reliable, WithValidation)
	void ServerRequestPetCat();

	UFUNCTION(Client)
	void ClientPlayPetCat();

	UFUNCTION(NetMulticast)
	void NetMulticastRPC();
```

在实现 RPC 函数时，函数名会有所不同：

```cpp
void AMyActor::ServerRequestPetCat_Implementation() { ... }
bool AMyActor::ServerRequestPetCat_Validate() { ... }
```

想要使用一个 RPC 函数，只需要调用其原本的定义（即原名）即可。下面是一段让你的朋友摸你的猫的示例：

❌错误示例

```cpp
UPROPERTY(Replicated)
AMyActor* Friend;

void LetMyFriendPetMyCat() {
	Friend->ServerRequestPetCat();
}

void ServerRequestPetCat_Implementatoin() {
	ClientPlayPetCat()
}
```

Server RPC 只能在持有该 Actor 的连接中调用，自己并没有调用朋友 Actor 的 RPC 的权限。所以代码不应该直接调用 Friend 的 RPC 函数。另一个问题是即使 Friend 标记了 Replicated，因为复制存在延迟，所以 Friend 也可能为空，在使用前需要做检查。

✔️正确示例

```cpp
void LetMyFriendPetMyCat() {
		ServerRequestLetMyFriendPetCat();
}

void ServerRequestLetMyFriendPetCat_Implementation() {
    if (IsValid(Friend) && TargetFriend->GetOwner() == GetOwner()) {
        Friend->ClientPlayPetCat();
    }
}
```

我们使用自身连接的 Actor 给服务器发送 RPC，而不是在自己的连接让朋友 Actor 发送摸猫操作的请求。

## 调试建议

编辑器中的多人游戏调试菜单在下图的位置：

![image.png](./Unreal5Cpp多人游戏入门资料/image.jpg)

## 场景切换

推荐阅读官方文档：[https://dev.epicgames.com/documentation/zh-cn/unreal-engine/travelling-in-multiplayer-in-unreal-engine](https://dev.epicgames.com/documentation/zh-cn/unreal-engine/travelling-in-multiplayer-in-unreal-engine)

# Profiling

性能分析菜单可以通过编辑器右下角的选项打开。

![image.png](./Unreal5Cpp多人游戏入门资料/image%201.jpg)

点击 Unreal Insights (Session Browser) 的选项可以打开多个调试选项卡。

![image.png](./Unreal5Cpp多人游戏入门资料/image%202.jpg)

点击录制按钮开始录制，再次点击停止录制。

![image.png](./Unreal5Cpp多人游戏入门资料/image%203.jpg)

录制完成后 Unreal Insights 会显示录制的数据，双击即可查看分析数据。

### 参考资料

- **How to Optimize Performance in Unreal Engine 5** [https://youtu.be/lfjG3z5VVIw](https://youtu.be/lfjG3z5VVIw)

# 其它常用 API

- 类型转换

```cpp
UBar* Parent;
...
UFoo* Child = Cast<UFoo>(Parent);
```

- 在子类中调用父类内容，使用 `Super`，如 `Super::Foo()`
- 检查指针的可用性（是否为空指针）：

```cpp
UPROPERTY()
UMyClass* ReflectedPtr;

UMyClass* NotRefletedPtr;
...
// For reflected pointer (UPROPERTY)
IsValid(ReflectedPtr); 
// For not reflected pointer
NotReflectedPtr->IsValidLowLevel()
```

- 指针的可空性：只有被 `UPROPERTY()` 修饰的 `UObject` 变量的指针在未初始化的情况下默认为空。但是未被修饰的 `UObject` 指针不应该为空。要检查没有被 `UPROPERTY()` 修饰的裸指针的空情况，可以使用 `IsValidLowLevel`。
- 强制要求使用子类的变量，可以使用 `TSubclassOf<T>`
- 实例创建，UObject 及其子类只支持无参数的构造函数来创建对象。`NewObject<T>(...)` 是最简单的工厂方法，用于创建一个 UObject。`NewNamedObject<T>(…)` 可以指定名称（FName）。
