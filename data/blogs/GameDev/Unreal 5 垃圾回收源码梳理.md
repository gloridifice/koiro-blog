```toml
title = "Unreal 5 垃圾回收源码梳理"
english_title = "Unreal 5 Grabage Collection Source Reading"
slug = "UE 5 采用标记清扫的垃圾回收策略，同时支持增量回收和集群优化计数。本文梳理了 UE 5 垃圾回收相关的源码，并总结了其部分编程范式。"
published = true
last_edited_date = "2025-06-05T13:50:00"
authors = "宏楼 Koiro"
class = "Dev"
date = "2025-06-05T13:28:00"
date = "2025-05-22"
tags = ["Cpp", "Unreal"]
```

> 为了使代码更加简洁易读，本文出现的 UE 源代码会去除掉输出日志的部分，用 //...log 来表示，除此以外，本文会用 ... 来表示省略的代码。
> 

本文使用的 UE 源码版本为 5.5.4。在 UE 5.0，增量回收成为了默认开启的选项，也迎来了包括并行回收在内的垃圾回收更新。如 API 有所出入，请以 5.5.4 为准。

# 前言

## 垃圾回收概念

UE 5 采用标记清扫的垃圾回收策略，同时支持增量回收和集群优化计数。这一小节会概念性地介绍一下这些名词及其思想。如果读者已经对此熟悉，请直接跳过此小节。

- 垃圾回收：垃圾回收或者说垃圾回收器是一段定期运行的程序。它会检查对象的存活状态，并释放死亡对象的内存。所谓“死亡”的对象，就是在程序中永远无法再被访问的对象，也是“垃圾回收”中的“垃圾”。
- 对象图（Objects Graph）：对象图是指对象和对象的引用关系构成的有向图，它描述了对象之间的引用关系。
- 标记清扫（Mark-Sweep）：是现今最常用的垃圾回收策略。它的基本思想是从一些特殊的“根对象”开始，根据对象之间的引用管线，完整地遍历对象图。所有被遍历对象会被打上“可达”标记。接着，再遍历一遍所有对象，如果一个对象没有被打上可达标记，我们认为它是死亡的。再标记清扫中，我们认为对象的“存活性”和“可达性”是等价的。根对象在 UE 中有 `UWorld` 等，也可以人为指定。
- 增量回收（Incremental）：“增量回收”的思想是将一次垃圾回收分成多个小段在不同的时间执行，这样分散了一次垃圾回收的时长，减少了程序卡顿的可能性。增量回收也面临着挑战，例如对象图在运行时是变化的。增量回收需要一些异步安全机制，这些在下文中都会介绍。
- 全量回收（Full）：全量回收是为了和增量回收区分而存在的名词，有了增量回收之后，之前的“一次进行全部回收”的行为就是全量回收。
- 对象簇（Cluster）：是一种将强相关的对象放在一起的数据结构，用于减少对象图的遍历次数

## 章节安排

本文将在第一章中概念性地介绍 UE 5 中垃圾回收的编程范式，提前了解这些思想有助于后续源码的阅读。接着，本文将会分为三个章节，由浅入深的对垃圾回收机制的源码进行解析。

- UE 5 垃圾回收编程范式
- 垃圾回收的触发机制
- 垃圾回收过程
- 引用与对象图

# UE 5 垃圾回收编程范式

## 增量回收

UE 5 的增量回收状态是通过全局的原子变量来管理的。引擎检查增量回收的状态通常会使用 `IsIncrementalPurgePending()` 函数。

```cpp
// GarbageCollection.cpp
std::atomic<bool> GObjIncrementalPurgeIsInProgress = false;

// GarbageCollection.cpp
bool IsIncrementalPurgePending()
{
	return GObjIncrementalPurgeIsInProgress || GObjPurgeIsRequired;
}
```

在增量回收中，引擎往往会在需要跨帧工作的场景中，使用全局变量或者成员变量缓存来存储没有完成的工作，并在下一帧时继续使用。因为有许多工作需要在延迟执行，因此垃圾回收的源代码会大量出现 “Pending” 一词，其表示“待处理的”。

此外，UE 5 允许开发者设置垃圾回收在一帧中的时间限制（Time Limit），这个时间限制使用 `GIncrementalGatherTimeLimit` 存储，默认值为 `0.0`，代表没有限制。是否超时并没有统一的函数，它会在具体的情景下被计算，例如下面的代码

```cpp
// GarbageCollection.cpp/IncrementalPurgeGarbage()
bTimeLimitReached = UnhashUnreachableObjects(bUseTimeLimit, TimeLimit);
```

在下文中，本文会用“超时”来表述垃圾回收执行的时间已经超过了帧的预算时间。

此外，增量回收的代码与全量回收的代码往往混合在一起，一般通过 `bFullPurage` 类似的字眼来标记。

## 多线程与异步优化

垃圾回收工作发生在游戏线程中。但是在局部的工作中，也会进行多线程优化。UE 5 中垃圾回收使用任务图（Task Graph）系统中的一些功能比如

```cpp
// GarbageCollection.cpp/GatherUnreachableObjects
ParallelFor( TEXT("GC.GatherUnreachable"), ...)
```

也使用了更加底层的专为 GC 设计的程序。比如可达性分析相关的 `ReferenceChainSearch.cpp` 。

此外，UE 使用了一个 `FGCCSyncObject` 的单例模式的锁来管理异步资源。在使用全局异步资源时需要上锁和解锁。锁里本身不存储任何资源的指针，锁只是起让其它线程等待的作用，资源是以全局变量的形式存在的，因此引擎一定要在使用全局资源时确保手工上锁和解锁。与之相关的两个函数是 `AcquireGCLock()` 和 `ReleaseGCLock()`，看到类似字眼说明是上锁或解锁垃圾回收强相关的资源。

# 垃圾回收的触发机制

## 强制垃圾回收

想要了解垃圾回收从何触发，执行了什么，一个简单的想法是从强制垃圾回收入手。我们先从 `ForceGarbageCollection()` 函数的源代码开始阅读。

```cpp
// UnrealEngine.cpp
void UEngine::ForceGarbageCollection(bool bForcePurge/*=false*/)
{
	TimeSinceLastPendingKillPurge = 1.0f + GetTimeBetweenGarbageCollectionPasses();
	bFullPurgeTriggered = bFullPurgeTriggered || bForcePurge;
	//...log
}
```

- `bForcePurge` 表示这次强制回收是否是一次全量回收，与之相对的是增量回收 `Incremental Purge`
- `TimeSinceLastPendingKillPurge` 私有成员变量的作用是记录距离上一次清除待销毁的对象的时间，用于判断垃圾回收条件
- `GetTimeBetweenGarbageCollectionPasses()` 用于获取两次垃圾回收之间的时间间隔
- `bFullPurgeTriggered` 私有成员变量的作用是标记下一次垃圾回收是否是一次全量回收

可以发现，代码在执行强制垃圾回收时，将 `TimeSinceLastPendingKillPurge` 赋值为 `GetTimeBetweenGarbageCollectionPasses() + 1.0` 。这使得垃圾回收条件将被触发。接下来本文具体介绍一下条件垃圾回收的代码。

## 条件垃圾回收

上文提到了“垃圾回收条件”，这些条件会在每帧都被调用的`ConditionalCollectGarbage()`函数中被判断，其相关的代码如下：

```cpp
// UnrealEngine.cpp
void UEngine::ConditionalCollectGarbage()
{
	...
	const float TimeBetweenPurgingPendingKillObjects = GetTimeBetweenGarbageCollectionPasses(bHasPlayersConnected);
	...
	// Perform incremental purge update if it's pending or in progress.
	else if (!IsIncrementalPurgePending()
		// Purge reference to pending kill objects every now and so often.
		&& (TimeSinceLastPendingKillPurge > TimeBetweenPurgingPendingKillObjects) && TimeBetweenPurgingPendingKillObjects > 0.f)
	...
}
```

- `ConditionalCollectGarbage()` 函数会在 `UWord::Tick` 中被每帧调用
- `GetTimeBetweenGarbageCollectionPasses()` 函数有无参和有一个参的两个重载
    - 有参的版本需要传入一个是否有玩家连接的布尔值
        - 在服务器上，如果没有玩家连接，间隔会乘以 `GTimeBetweenPurgingPendingKillObjectsOnIdleServerMultiplier`，这是一个静态变量，默认值为 `10.0`
        - 这会降低垃圾回收的频率，有助于节约功耗
    - 无参的版本只是增加了一段自动判断是否有玩家在服务器的代码，具体实现是遍历所有 `WorldList` 里所有的 `UWorld`，检查每个世界是否有玩家

到这里，已经可以理解 UE 垃圾回收的大致触发机制。简单来说，垃圾回收系统会进行计时，每经过一定间隔触发一次垃圾回收。垃圾回收计时由 `UEngine` 的 `TimeSinceLastPendingKillPurge` 成员变量负责，而间隔则通过 `GetTimeBetweenGarbageCollectionPasses()` 函数获取：

垃圾回收的默认回收间隔为 `60.0`，由一个静态变量存储。当运行机器为服务器且没有玩家连入时，间隔会乘以默认为 `10.0` 的一个静态变量。此外，如果当前空余内存少于设定的 `GLowMemoryMemoryThresholdMB` 值，则会无视上面的规则，使用默认值为 `30.0` 的静态变量，会增加回收频率（如果默认回收间隔比低内存回收间隔还小，默认回收间隔）。下面是相关变量的表格。

| 情况 | 回收间隔默认值/s | 相关变量名 |
| --- | --- | --- |
| 默认情况 | `60.0` | `GTimeBetweenPurgingPendingKillObjects` |
| 服务器且没有玩家 | `60.0 * 10.0` | `GTimeBetweenPurgingPendingKillObjectsOnIdleServerMultiplier`（无服务器时的间隔系数） |
| 低内存 | `30.0` | `GLowMemoryMemoryThresholdMB`（低内存阈值）, `GLowMemoryTimeBetweenPurgingPendingKillObjects`（低内存时间隔） |

### `ConditionalCollectGarbage` 详解

刚刚本文介绍了 `ConditionalCollectGarbage` 局部的逻辑和获取间隔的逻辑，接着从整体上来看一下这个函数。

```cpp
// UnrealEngine.cpp

void UEngine::ConditionalCollectGarbage()
{
	if (GFrameCounter != LastGCFrame)
```

- `GFrameCounter` 是引擎的全局帧号
- `LastGCFrame` 是上一次触发垃圾回收的帧号

这个条件判断避免一帧内触发多次垃圾回收。这段 if 的结尾是一个增量回收的条件判断，我们把它插入在此：

---

```cpp
	else if (IsIncrementalReachabilityAnalysisPending())
	{
		PerformIncrementalReachabilityAnalysis(GetReachabilityAnalysisTimeLimit());
	}
```

- `IsIncrementalReachabilityAnalysisPending()` 获取增量垃圾回收是否待执行

意思是如果增量垃圾回收的可达性分析没有完成，继续进行可达性分析。

---

回到原来代码的位置：

```cpp
	{
		QUICK_SCOPE_CYCLE_COUNTER(STAT_ConditionalCollectGarbage);

#if !(UE_BUILD_SHIPPING || UE_BUILD_TEST)
		if (CVarStressTestGCWhileStreaming.GetValueOnGameThread() && IsAsyncLoading())
		{
			CollectGarbage(GARBAGE_COLLECTION_KEEPFLAGS, true);
		}
		else if (CVarForceCollectGarbageEveryFrame.GetValueOnGameThread())
		{
			CollectGarbage(GARBAGE_COLLECTION_KEEPFLAGS, true);
		}
		else
#endif
```

`!(UE_BUILD_SHIPPING || UE_BUILD_TEST)` 宏声明这段代码仅在非发布和非测试下启用，这部分代码与压力测试和调试相关。

```cpp
		{
			EGarbageCollectionType ForceTriggerPurge = ShouldForceGarbageCollection();
#if WITH_VERSE_VM || defined(__INTELLISENSE__)
			if (ForceTriggerPurge == EGarbageCollectionType::None && UE::GC::ShouldFrankenGCRun())
			{
				ForceTriggerPurge = EGarbageCollectionType::Incremental;
			}
#endif
			if (ForceTriggerPurge != EGarbageCollectionType::None)
			{
				ForceGarbageCollection(ForceTriggerPurge == EGarbageCollectionType::Full);
			}
```

这段代码与**强制**垃圾回收有关

- `EGarbageCollectionType` 枚举有三种值
    - `None` 不强制回收
    - `Incremental` 增量强制回收
    - `Full` 全量强制回收

在 `UEngine` 中 `ShouldForceGarbageCollection()` 永远返回 `None`。只有在使用 `VERSE` 的情况下，才可能会使用强制增量回收。接下来，就执行正式的垃圾回收代码了。

```cpp
			if (bFullPurgeTriggered)
			{
				if (TryCollectGarbage(GARBAGE_COLLECTION_KEEPFLAGS, true))
				{
					ForEachObjectOfClass(UWorld::StaticClass(),[](UObject* World)
					{
						CastChecked<UWorld>(World)->CleanupActors();
					});
					bFullPurgeTriggered = false;
					bShouldDelayGarbageCollect = false;
					TimeSinceLastPendingKillPurge = 0.0f;
				}
			}
```

- `bFullPurgeTriggered` 标记是否使用全量垃圾回收，在执行后被设为 `false`

如果使用全量垃圾回收，则会进行垃圾回收行为，即 `TryCollectGarbage` 和 `CleanupActors`，这两个函数会在后面的小节进行详细的介绍。如果不进行全量回收，来看下面的部分。

```cpp
			else
			{
				const bool bTestForPlayers = IsRunningDedicatedServer();
				bool bHasAWorldBegunPlay = false;
				bool bHasPlayersConnected = false;

				// Look for conditions in the worlds that would change the GC frequency
				for (const FWorldContext& Context : WorldList) {
					if (UWorld* World = Context.World()) {
						if (World->HasBegunPlay()) {
							bHasAWorldBegunPlay = true;
						}

						if (bTestForPlayers &&
							World->NetDriver &&
							World->NetDriver->ClientConnections.Num() > 0
						) {
							bHasPlayersConnected = true;
						}

						// If we found the conditions we wanted, no need to continue iterating
						if (bHasAWorldBegunPlay &&
							(!bTestForPlayers || bHasPlayersConnected)
						){
							break;
						}
					}
				}
```

这段 for 循环用于寻找可能改变 GC 频率的情况，具体来说，和上文描述过的一样，检查服务器的所有世界是否有玩家连接。其得到的结果 `bHasPlayersConnected`，会用于上文提到过的 `GetTimeBetweenGarbageCollectionPasses(bool)` 函数。这里没有使用无参版本的 `GetTimeBetweenGarbageCollectionPasses()` 的主要原因是，在这段逻辑中引擎还需要获得世界的开始状态，也就是 `bHasAWorldBegunPlay`。下面的代码立刻就会用到：

```cpp
				if (bHasAWorldBegunPlay)
				{
					TimeSinceLastPendingKillPurge += FApp::GetDeltaTime();

					const float TimeBetweenPurgingPendingKillObjects = GetTimeBetweenGarbageCollectionPasses(bHasPlayersConnected);

					// See if we should delay garbage collect for this frame
					if (bShouldDelayGarbageCollect)
					{
						bShouldDelayGarbageCollect = false;
					}
					else if (IsIncrementalReachabilityAnalysisPending())
					{
						SCOPE_CYCLE_COUNTER(STAT_GCMarkTime);
						PerformIncrementalReachabilityAnalysis(GetReachabilityAnalysisTimeLimit());
					}
					// Perform incremental purge update if it's pending or in progress.
					else if (!IsIncrementalPurgePending()
						// Purge reference to pending kill objects every now and so often.
						&& (TimeSinceLastPendingKillPurge > TimeBetweenPurgingPendingKillObjects) && TimeBetweenPurgingPendingKillObjects > 0.f)
					{
						SCOPE_CYCLE_COUNTER(STAT_GCMarkTime);
						PerformGarbageCollectionAndCleanupActors();
					}
					else
					{
						SCOPE_CYCLE_COUNTER(STAT_GCSweepTime);
						float IncGCTime = GetIncrementalGCTimePerFrame();
						IncrementalPurgeGarbage(true, IncGCTime);
					}
				}
			}
		}
```

这段代码的大致逻辑是，只有在至少一个世界开始运行时，引擎才会计时和判断垃圾回收；在有上一帧留下的增量回收的任务时，继续进行增量回收的工作；如果没有增量回收任务，则判断计时是否达到间隔，来确定是否需要开启新一轮的垃圾回收。

- `PerformIncrementalReachabilityAnalysis()` 负责执行增量垃圾回收的分步可达性分析
- `PerformGarbageCollectionAndCleanupActors()` 负责执行全量垃圾回收，内容与上文中介绍的全量垃圾回收的逻辑类似，其也使用了 `UEngine::TryCollectGarbage()` 和 `UWorld::CleanupActors` 来回收对象。下文会具体介绍
- `IncrementalPurgeGarbage()` 负责销毁 `UObject` 和释放内存，这时可达性分析已经完成，后面的章节会具体介绍

```cpp
		if (const int32 Interval = CVarCollectGarbageEveryFrame.GetValueOnGameThread())
		{
			if (0 == (GFrameCounter % Interval))
			{
				ForceGarbageCollection(true);
			}
		}
```

这段代码负责执行控制台变量对垃圾回收强制干预，每隔 `Interval` 帧强制进行垃圾回收。

- `CVarCollectGarbageEveryFrame` 获取帧间隔

```cpp
		else if (CVarContinuousIncrementalGC.GetValueOnGameThread() > 0 &&
				!IsIncrementalReachabilityAnalysisPending() &&
				!IsIncrementalUnhashPending() &&
				!IsIncrementalPurgePending())
		{
			ForceGarbageCollection(false);
		}

		LastGCFrame = GFrameCounter;
	}
```

- `CVarContinuousIncrementalGC` 启用持续增量 GC todo

```cpp
	else if (IsIncrementalReachabilityAnalysisPending())
	{
		PerformIncrementalReachabilityAnalysis(GetReachabilityAnalysisTimeLimit());
	}
}
```

最后，如前文插入的 if 的结尾。如果增量回收的可达性分析还没有完成，进行可达性分析。

### `PerformGarbageCollectionAndCleanupActors()`

上文提到，在执行全量回收时，引擎会调用 `PerformGarbageCollectionAndCleanupActors()` 函数。其源代码如下：

```cpp
void UEngine::PerformGarbageCollectionAndCleanupActors()
{
	// We don't collect garbage while there are outstanding async load requests as we would need
	// to block on loading the remaining data.
	if (GPerformGCWhileAsyncLoading || !IsAsyncLoading())
	{
		bool bForcePurge = true;
		for (FWorldContext& Context : WorldList)
		{
			UWorld* World = Context.World();
			if (World != nullptr && World->IsGameWorld())
			{
				bForcePurge = false;
				break;
			}
		}
```

- `GPerformGCWhileAsyncLoading` 表示是否允许在异步加载时执行 GC，其配合 `IsAsyncLoading()` 进行异步加载时是否触发 GC 的判断
- `bForcePurge` 默认为 true，但是一旦发现存在活跃的游戏世界（`World != nullptr && World->IsGameWorld`）那么就取消强制清理。这样有助于减少游戏卡顿，优先保证流畅性。

```cpp
		// Perform housekeeping.
		if (TryCollectGarbage(GARBAGE_COLLECTION_KEEPFLAGS, bForcePurge))
		{
			ForEachObjectOfClass(UWorld::StaticClass(), [](UObject* World)
			{
				CastChecked<UWorld>(World)->CleanupActors();
			});

			// Reset counter.
			TimeSinceLastPendingKillPurge = 0.0f;
			bFullPurgeTriggered = false;
			LastGCFrame = GFrameCounter;
		}
	}
}
```

这里就是执行垃圾回收的代码了，与上文中全量强制垃圾回收有十分相似的地方。其中重要的两个函数是 `TryCollectGarbage` 和 `CleanupActors`，下面一个小节会对两者进行重点介绍。

# 垃圾回收的执行过程

上文提到了，在条件垃圾回收中（`UEngine::ConditionalCollectGarbage`），垃圾回收的过程发生在两处，一处是强制全量垃圾回收（`Engine::bFullPurgeTriggered`），另一处在 `PerformGarbageCollectionAndCleanupActors()` 中，两者的核心逻辑是十分相似的，我们先看看两者的代码：

```cpp
// if(bFullPurgeTriggered)
if (TryCollectGarbage(GARBAGE_COLLECTION_KEEPFLAGS, true))
{
	ForEachObjectOfClass(UWorld::StaticClass(),[](UObject* World)
	{
		CastChecked<UWorld>(World)->CleanupActors();
	});
	TimeSinceLastPendingKillPurge = 0.0f;
	bFullPurgeTriggered = false;
	bShouldDelayGarbageCollect = false;
}

// UEngine::PerformGarbageCollectionAndCleanupActors
if (TryCollectGarbage(GARBAGE_COLLECTION_KEEPFLAGS, bForcePurge))
{
	ForEachObjectOfClass(UWorld::StaticClass(), [](UObject* World)
	{
		CastChecked<UWorld>(World)->CleanupActors();
	});
	TimeSinceLastPendingKillPurge = 0.0f;
	bFullPurgeTriggered = false;
	LastGCFrame = GFrameCounter;
}
```

如源码中所示，首先会调用 `TryCollectGarbage()` 函数，接着会遍历所有世界（`UWorld`），调用其 `CleanupActors()` 函数，清除 `Actor`。下文会具体介绍 `TryCollectGarbage()` 和 `CleanupActors()` 的工作过程。 需要注意的一点是，强制全量垃圾回收并不会影响正常的垃圾回收帧计数（因为并没有更新`LastGCFrame`）。

## 回收过程：`TryCollectGarbage`

下文中会时常出现 `IsSuspended` 这代表是否处于增量回收的挂起（或称暂停）状态。以及，对象簇（Cluster）一词指一系列紧密关联的 `UObject`，例如一个 Actor 和它的 Sub Objects。显然垃圾回收中使用集群可以减少遍历次数。

`TryCollectGarbage` 函数会调用 `GarbageColleciton.h` 中的 `FReachabilityAnalysisState::PerformReachabilityAnalysisAndConditionallyPurgeGarbage` 函数，它的大致过程为：

- 进行预回收 `UE::GC::PreCollectGarbageImpl<true>(ObjectKeepFlags);`
    - 其中模板中的布尔值作用是标记是否为全量回收
- 进行可达性分析 `PerformReachabilityAnalysis()`;
- 进行后回收 `UE::GC::PostCollectGarbageImpl<true>(ObjectKeepFlags);`
    - 这里会执行 `UObject::BeginDestroy` 和 `UObject::FinishDestroy` 并回收内存

本小节将**可达性分析**和**清扫分开**讲述。

### 可达性分析

在可达性分析时，存在中间函数，如 `FReachabilityAnalysisState` 的 `PerformReachabilityAnalysis()` 函数。但无论中间函数如何，其底层调用的都是 `FRealtimeGC::PerformReachabilityAnalysis` 函数，所以在此我们着重看这个函数的执行逻辑。下面是对这个函数的分步分析：

```cpp
// GarbageColleciton.cpp

void PerformReachabilityAnalysis(EObjectFlags KeepFlags, const EGCOptions Options) {
	LLM_SCOPE(ELLMTag::GC);

	const bool bIsGarbageTracking = !GReachabilityState.IsSuspended() && Stats.bFoundGarbageRef;

	if (!GReachabilityState.IsSuspended()) {
		StartReachabilityAnalysis(KeepFlags, Options);
		// We start verse GC here so that the objects are unmarked prior to verse marking them
		StartVerseGC();
	}

	{
		const double StartTime = FPlatformTime::Seconds();

		while (true) {
			PerformReachabilityAnalysisPass(Options);

			if (GReachabilityState.IsSuspended()) {
// We may have suspended either via incremental timeout, or because verse GC is still marking.
// If we are not incremental, keep going while verse GC adds to GReachableObjects.
				if (EnumHasAnyFlags(Options, EGCOptions::IncrementalReachability)) {
					break;
				}
			}
			else if (Private::GReachableObjects.IsEmpty() && Private::GReachableClusters.IsEmpty()) {
// We terminate verse GC here now that both sides have nothing left to mark.
// This check must happen only when !IsSuspended, so verse GC can no longer add to GReachableObjects.
				StopVerseGC();
				break;
			}
		}
		// ... time tracing
	}
```

这个 while 循环会执行 `PerformReachabilityAnalysisPass`，这是进行可达性标记的主要函数，它的作用是步进一次可达性分析，下文会对这个函数进行详细介绍。可达性分析是在循环中不断步进完成的，在这两种情况下才会退出循环：

- 增量模式挂起，一般是执行时间超过一帧的时间预算，这时会等到下一帧处理
- 可达对象或者可达对象簇为空，意味着数据处理完毕

```cpp
PRAGMA_DISABLE_DEPRECATION_WARNINGS
	// Allowing external systems to add object roots. This can't be done through AddReferencedObjects
	// because it may require tracing objects (via FGarbageCollectionTracer) multiple times
	if (!GReachabilityState.IsSuspended())
	{
		const double StartTime = FPlatformTime::Seconds();
		FCoreUObjectDelegates::TraceExternalRootsForReachabilityAnalysis.Broadcast(*this, KeepFlags, !(Options & EGCOptions::Parallel));
		GGCStats.TraceExternalRootsTime += FPlatformTime::Seconds() - StartTime;
	}
PRAGMA_ENABLE_DEPRECATION_WARNINGS
}
```

追踪外部物体 todo

---

### `PerformReachabilityAnalysisPass`

`PerformReachabilityAnalysisPass` 是这里的核心，它在一个 While 循环中被使用，这个循环过程会逐步标记所有的对象。下面是对这个函数的分步解析：

```cpp
// GarbageCollection.cpp

void PerformReachabilityAnalysisPass(const EGCOptions Options)
{
	FContextPoolScope Pool;
	FWorkerContext* Context = nullptr;

	if (!GReachabilityState.IsSuspended())
	{
		Context = Pool.AllocateFromPool();
	}
	else
	{
		Context = GReachabilityState.GetContextArray()[0];
		Context->bDidWork = false;
		InitialObjects.Reset();
	}
```

首先，创建上下文，即 `FWorkContext`，这个上下文包含了本地线程中的初始对象和引用信息。如果增量回收被暂停，那么直接使用增量回收的上一个上下文，如果增量回收没有被暂停，则会创建新的工作上下文。另一个会使用到的重要变量，是缓存的可达对象的列表 `GReachableObjects`（下文称“可达对象缓存”），这是一个静态对象，里面存储着 `UObject*`。它的作用是缓存增量垃圾回收挂起时未被处理完的对象，如果增量回收被挂起，这些对象会在下一次增量回收中被处理。

```cpp
	if (!Private::GReachableObjects.IsEmpty())
	{
		// Add objects marked with the GC barrier to the inital set of objects for the next iteration of incremental reachability
		Private::GReachableObjects.PopAllAndEmpty(InitialObjects);
		GGCStats.NumBarrierObjects += InitialObjects.Num();
		UE_LOG(LogGarbage, Verbose, TEXT("Adding %d object(s) marker by GC barrier to the list of objects to process"), InitialObjects.Num());
		ConditionallyAddBarrierReferencesToHistory(*Context);
	}
```

接着，函数将 `GReachableObjects` 的缓存的对象转移到 `InitialObjects` 中，在后续进行处理。

```cpp
	else if (GReachabilityState.GetNumIterations() == 0 || (Stats.bFoundGarbageRef && !GReachabilityState.IsSuspended()))
	{
		Context->InitialNativeReferences = GetInitialReferences(Options);
	}
```

如果可达对象缓存为空，则初始化工作上下文中的初始引用 `InitialNativeRefernces`。todo

```cpp
	if (!Private::GReachableClusters.IsEmpty())
	{
		// Process cluster roots that were marked as reachable by the GC barrier
		TArray<FUObjectItem*> KeepClusterRefs;
		Private::GReachableClusters.PopAllAndEmpty(KeepClusterRefs);
		for (FUObjectItem* ObjectItem : KeepClusterRefs)
		{
			// Mark referenced clusters and mutable objects as reachable
			MarkReferencedClustersAsReachable<EGCOptions::None>(ObjectItem->GetClusterIndex(), InitialObjects);
		}
	}
```

在这里，可达集群缓存会被处理和上面的可达对象缓存类似（todo）。在上面这段代码中会将所有的可达集群所引用的集群标记为可达。

```cpp
	Context->SetInitialObjectsUnpadded(InitialObjects);
```

todo!

```cpp
	PerformReachabilityAnalysisOnObjects(Context, Options);
```

这个函数是可达性分析的重点，它会根据当前的配置调用可达性分析函数：
`(this->*ReachabilityAnalysisFunctions[GetGCFunctionIndex(Options)])(*Context);`

```cpp
	if (!GReachabilityState.IsSuspended())
	{
		GReachabilityState.ResetWorkers();
		Stats.AddStats(Context->Stats);
		GReachabilityState.UpdateStats(Context->Stats);
		Pool.ReturnToPool(Context);
	}
}
```

最后，如果增量回收未被挂起，那么进行增量回收的收尾工作。todo!

- 重置工作线程
- 合并统计信息
- 归还上下文到对象池

### 清除垃圾

`TryCollectGarbage/PostCollectGarbageImpl()`

清除垃圾的过程主要发生在 `PostCollectGarbageImpl()` 函数中。其主要负责清理标记的不可达对象。

```cpp
template<bool bPerformFullPurge>
void PostCollectGarbageImpl(EObjectFlags KeepFlags)
{
	const double PostCollectStartTime = FPlatformTime::Seconds();

	using namespace UE::GC;
	using namespace UE::GC::Private;

	if (!GIsIncrementalReachabilityPending)
	{
		FContextPoolScope ContextPool;
		TConstArrayView<TUniquePtr<FWorkerContext>> AllContexts = ContextPool.PeekFree();
		// This needs to happen before clusters get dissolved otherwisise cluster information will be missing from history
		UpdateGCHistory(AllContexts);

		// Reconstruct clusters if needed
		if (GUObjectClusters.ClustersNeedDissolving())
		{
			const double StartTime = FPlatformTime::Seconds();
			GUObjectClusters.DissolveClusters();
			UE_LOG(LogGarbage, Log, TEXT("%f ms for dissolving GC clusters"), (FPlatformTime::Seconds() - StartTime) * 1000);
		}

		DumpGarbageReferencers(AllContexts);

		const EGatherOptions GatherOptions = GetObjectGatherOptions();
		DissolveUnreachableClusters(GatherOptions);
```

上面这段逻辑主要负责解开不可达的对象簇，这里不深入介绍。

```cpp
		// This needs to happen after DissolveUnreachableClusters since it can mark more objects as unreachable
		if (GReachabilityState.GetNumIterations() > 1){
			ClearWeakReferences<true>(AllContexts);
		} else {
			ClearWeakReferences<false>(AllContexts);
		}
		if (bPerformFullPurge) {
			ContextPool.Cleanup();
		}
		GGatherUnreachableObjectsState.Init();
		if (bPerformFullPurge || !GAllowIncrementalGather || !FGCFlags::IsIncrementalGatherUnreachableSupported()) {
			GatherUnreachableObjects(GatherOptions, /*TimeLimit =*/ 0.0);
		}
	}
```

标记对象为不可达并添加到 `GUnreachableObjects` 中。

```cpp
	GIsGarbageCollectingAndLockingUObjectHashTables = false;
	UnlockUObjectHashTables();

	GIsGarbageCollecting = false;

	// The hash tables lock was released when reachability analysis was done.
	// BeginDestroy, FinishDestroy, destructors and callbacks are allowed to call functions like StaticAllocateObject and StaticFindObject.
	// Now release the GC lock to allow async loading and other threads to perform UObject operations under the FGCScopeGuard.
	ReleaseGCLock();
```

至此，可达性分析才全部完成。上面的过程都在主 GC 线程中完成，此处将 GC 的锁释放，供后面的工作并行执行。

```cpp
	if (!GIsIncrementalReachabilityPending)
	{
		...
		// Perform a full purge by not using a time limit for the incremental purge.
		if (bPerformFullPurge)
		{
			IncrementalPurgeGarbage(false);
		}
		...
	}

	//... log and trace
}
```

这里省略了大部分代码，留下了核心的部分。

- `IncrementalPurgeGarbage()` 函数是回收内存的核心函数，在 `PostCollectGarbageImpl` 中只会进行全量回收。但是这个函数也会在`UEngine::ConditionalCollectGarbage()` 中被调用

### `GatherUnreachableObjects`

`GatherUnreachableObjects` 会并行地遍历所有对象，判断其可达性，核心代码如下：

```cpp
while (Iterator.Index <= Iterator.LastIndex)
{
	FUObjectItem* ObjectItem = &GUObjectArray.GetObjectItemArrayUnsafe()[Iterator.Index++];
	if (FGCFlags::IsMaybeUnreachable_ForGC(ObjectItem))
	{
		checkf(!ObjectItem->HasAnyFlags(EInternalObjectFlags::ClusterRoot), TEXT("Unreachable cluster root found. Unreachable clusters should have been dissolved in DissolveUnreachableClusters!"));
		FGCFlags::SetUnreachable(ObjectItem);
		Iterator.Payload.Add({ ObjectItem });
	}

	if (Timer.IsTimeLimitExceeded())
	{
		return;
	}
}
```

- `FGCFlags::IsMaybeUnreachable_ForGC` 检查对象是否有 `MaybeUnreachableObjectFlag`
- `Iterator.Payload.Add({ ObjectItem })` 将不可达对象添加到一个临时集合，在后续使用

每个线程的迭代器预先被分配好，然后被迭代。`FUObjectItem` 除了包含了一个 `UObjectBase*` 裸指针外，还存储了一个 `FGCFlags` 位标记。一个不可达的对象将会被打上 `EInternalObjectFlags::Unreachabl` 的标记。

### `IncrementalDestroyGarbage`

在 `IncrementalPurgeGarbage` 的函数中这个函数的核心部分如下。

```cpp
		if (IsIncrementalUnhashPending())
		{
			bTimeLimitReached = UnhashUnreachableObjects(bUseTimeLimit, TimeLimit);

			if (GUnrechableObjectIndex >= GUnreachableObjects.Num())
			{
				FScopedCBDProfile::DumpProfile();
			}
		}

		if (!bTimeLimitReached)
		{
			bCompleted = IncrementalDestroyGarbage(bUseTimeLimit, TimeLimit);
		}
```

在这段待代码中，引擎会调用 `UnhashUnreachableObjects()` 函数，这个函数的主要功能是

- 将不可达对象从全局哈希表删除。全局哈希表用于快速定位对象如 `StaticFindObject`；也用于按路径索引资源
- 执行所有不可达对象的`BeginDestroy()` 函数
- 返回一个是否超时的布尔值

如果调用完 `BeginDestory()` 已经超时了，那么剩下的部分会到下帧执行。接着，该函数最终会调用 `IncrementalDestroyGarbage()`

```cpp
bool IncrementalDestroyGarbage(bool bUseTimeLimit, double TimeLimit)
{
	...
			while (GObjCurrentPurgeObjectIndex < GUnreachableObjects.Num())
			{
				FUObjectItem* ObjectItem = GUnreachableObjects[GObjCurrentPurgeObjectIndex].ObjectItem;
				checkSlow(ObjectItem);

				check(!FGCFlags::IsReachable_ForGC(ObjectItem) && FGCFlags::IsMaybeUnreachable_ForGC(ObjectItem));
				if (ObjectItem->IsUnreachable())
				{
					UObject* Object = static_cast<UObject*>(ObjectItem->Object);
					// Object should always have had BeginDestroy called on it and never already be destroyed
					check( Object->HasAnyFlags( RF_BeginDestroyed ) && !Object->HasAnyFlags( RF_FinishDestroyed ) );

					// Only proceed with destroying the object if the asynchronous cleanup started by BeginDestroy has finished.
					if(Object->IsReadyForFinishDestroy())
					{
						UE::GC::GDetailedStats.IncPurgeCount(Object);
						// Send FinishDestroy message.
						Object->ConditionalFinishDestroy();
					}
					else
					{
						GGCObjectsPendingDestruction.Add(Object);
						GGCObjectsPendingDestructionCount++;
					}
				}
```

这一部分旨在遍历 `GUnreachableObjects` 中的所有不可达对象，调用它们的 `FinishDestory()` 方法，如果 `IsReadyForFinishDestroy()` 不满足，就会被延迟处理（`GGCObjectsPendingDestruction`）。需要注意的是，此时不将对象从 `GUnreachableObjects` 中删除，也并不释放内存。内存将在所有对象都执行完 `FinishDestroy` 后被释放。

- Destruction（下文写作“毁灭”）：根据源码可以观察到，Destruction 在这里的语义指调用 `FinishDestroy()`，不包含释放内存
- `IsReadyForFinishDestroy()` 是一个 `UObject` 的公共虚函数，`UObject` 的默认实现是返回 `true`，通常被覆写，例如 `AActor` 中会在渲染结束时才为 `true`
- `ConditionalFinishDestroy()` 会执行 `FinishDestroy()` 函数
- `GGCObjectsPendingDestruction` 缓存了待销毁的对象的指针，这个变量是全局的，如果当帧没有处理完里面的内容（如超过了时限），会在下一帧处理。下文将这个对象称为“待销毁对象数组”

```cpp
				++GObjCurrentPurgeObjectIndex;

				// Only check time limit every so often to avoid calling FPlatformTime::Seconds too often.
				const bool bPollTimeLimit = ((TimeLimitTimePollCounter++) % TimeLimitEnforcementGranularityForDestroy == 0);
				if( bUseTimeLimit && bPollTimeLimit && ((FPlatformTime::Seconds() - GCStartTime) > TimeLimit) )
				{
					bTimeLimitReached = true;
					break;
				}
			}
		}
```

接着，引擎会检查垃圾回收有没有超时，如果超时，则跳出 while 循环。此时所有垃圾的 `FinishDestroy()` 并没有被调用完成。下面会有很长一段代码用来处理 `GGCObjectsPendingDestruction` 里延迟处理的对象。

```cpp
		// Have we finished the first round of attempting to call FinishDestroy on unreachable objects?
		if (GObjCurrentPurgeObjectIndex >= GUnreachableObjects.Num())
		{
			...
			while( GGCObjectsPendingDestructionCount > 0 )
			{
				int32 CurPendingObjIndex = 0;
				while( CurPendingObjIndex < GGCObjectsPendingDestructionCount )
				{
					...
				}
```

外面的 while 会进行对 `GGCObjectsPendingDestruction` 进行不断地轮询，直到超出时间限制或待销毁对象为空，为空代表着所有工作已经完成，会进入内存释放阶段。

在一次轮询中，内部的 while 循环首先会遍历一遍 `GGCObjectsPendingDestruction`，尝试对其进行毁灭并从 `GGCObjectsPendingDestruction` 中移除。如果还是不能毁灭，则把它保留在 `GGCObjectsPendingDestruction`。等待下一次轮询。

```cpp
				if( bUseTimeLimit )
				{
					break;
				}
```

如果设置了 CG 一帧的时间限制，并且我们已经对所有剩余对象完成了一次完整的迭代处理，那么即使还有剩余时间或未处理的对象，也直接退出循环。此时很可能是在等待渲染线程（如资源释放）。没有完成的工作会在下一帧继续。

```cpp
				else if( GGCObjectsPendingDestructionCount > 0 ) {
					if (FPlatformProperties::RequiresCookedData()) {...}
// Sleep before the next pass to give the render thread some time to release fences.
					FPlatformProcess::Sleep( 0 );
				}
				LastLoopObjectsPendingDestructionCount = GGCObjectsPendingDestructionCount;
			}
```

如果没有设置时间限制，引擎会在一次循环的结束检查一下用时，并在认为时间过长的时候打印日志，提醒开发者。

```cpp
			// Have all objects been destroyed now?
			if( GGCObjectsPendingDestructionCount == 0 )
			{
				...
// Release memory we used for objects pending destruction, leaving some slack space
				GGCObjectsPendingDestruction.Empty( 256 );
// Destroy has been routed to all objects so it's safe to delete objects now.
				GObjFinishDestroyHasBeenRoutedToAllObjects = true;
				GObjCurrentPurgeObjectIndexNeedsReset = true;
				GWarningTimeOutHasBeenDisplayedGC = false;
			}
		}
	}
```

在离开循环后，会再检查一次执行 `FinishDestroy` 的完成状态。如果待销毁对象数组不为空，它会在下一帧的这个函数被处理；如果为空，说明所有销毁工作完成了，可以释放内存了！那么函数会设置 `GObjFinishDestroyHasBeenRoutedToAllObjects` 为 `true`，这个布尔值很快就会被用到。请看接下来的代码，这部分代码是真正释放内存的地方。

```cpp
	if (GObjFinishDestroyHasBeenRoutedToAllObjects && !bTimeLimitReached)
	{
		...
		GUObjectPurge.DestroyObjects(bUseTimeLimit, TimeLimit, GCStartTime);
		...
		if (GUObjectPurge.IsFinished())
		{
			bCompleted = true;
			...
		}
	}
	//...log
```

本文只保留了核心调用，这里的核心是 `DestroyObjects()` 函数，这个函数会遍历 `GUnreachableObjects`，并释放其内存。内存释放的部分在这里不深入介绍。在所有对象都释放完后，`bCompleted` 会设置为 `true`，并在最后被返回。

```cpp
	return bCompleted;
}
```

至此，对 `IncrementalDestroyGarbage()` 函数的解析已经结束了。这个函数的作用是 `FinishDestroy()` 函数和释放内存。它通过一个**待销毁对象数组**来实现对工作的跨帧缓存，依此来实现增量回收；通过 while 循环来等待每一个待销毁对象执行完 `FinishDestroy()` 函数。

## `UWorld::CleanupActors()`

在执行 `TryCollectGarbage` 后 `CleanupActors` 会被执行。因为 Actor 往往是采用手动地 `Destroy` 来删除的，`Destroy` 并不会将 Actor 的内存释放，而是但是标记为待销毁，等待垃圾回收系统来处理。下面是触发 `Destroy()` 会执行的事情：

- 触发 `OnDestroy` 事件和蓝图逻辑
- 标记 `Actor` 为待销毁（包括了将其从 `ULevel` 的 Actor 数组中设置为 `nullptr` 等，`MarkAsGarbage()` 等）
- 从游戏逻辑中移除，不参与渲染、物理计算

使用 `MarkAsGarbage()` 将一个 `UObjectBaseUtility`（`UObject` 的基类） 标记为垃圾。 这个函数会为这个对象添加一个名为 `RF_MirroredGarbage` 的 `Flag`。
直接检查一个 `UObjet` 是否垃圾的办法是使用 `IsValidChecked()` 函数，这个函数实际上会检查 `Flag`，返回 `false` 意味着这个对象是垃圾。在垃圾回收时，这样直接被标记为垃圾的对象会被标记为不可达。进而在垃圾回收时被释放。

`CleanupActors` 清除的是关卡的 Actor 列表中的空指针，过程并不复杂，本文直接以注释的方式解释：

```cpp
void UWorld::CleanupActors()
{
	// 遍历所有世界
	for (ULevel* Level : Levels)
	{
		if(ensure(Level != nullptr) && (CurrentLevelPendingVisibility != Level))
		{

			const int32 FirstDynamicIndex = 2;
			int32 NumActorsToRemove = 0;
			// 反向遍历 Actors，因为会发生删除 Actor
			for(int32 ActorIndex=Level->Actors.Num()-1;
				ActorIndex>=FirstDynamicIndex;
				ActorIndex-- )
			{
				// 为了减少内存操作次数，在遇到空 Actor 时，
				// 引擎会增加一个连续空 Actor 计数，即上面的 NumActorsToRemove
				// 在遇到非空对象的时候，才会把之前遇到的一系列连续的空 Actor 删除
				if (Level->Actors[ActorIndex] == nullptr)
				{
					++NumActorsToRemove;
				}
				else if (NumActorsToRemove > 0)
				{
					Level->Actors.RemoveAt(ActorIndex+1, NumActorsToRemove, EAllowShrinking::No);
					NumActorsToRemove = 0;
				}
			}
			if (NumActorsToRemove > 0)
			{
				Level->Actors.RemoveAt(FirstDynamicIndex, NumActorsToRemove, EAllowShrinking::No);
			}
		}
	}
}
```

# 引用与对象图

对象图由所有 `UObject` 的引用关系逻辑表示，并没有一个直接的显式的对象图数据结构。运行时的引用收集逻辑大致如下：

- 自动收集 `UPROPERTY` 引用，这部分主要配合反射系统完成
- 对于非 `UPROPERTY` 引用，可以通过 `UGCObject` 和重写 `UObject::AddReferencedObjects` 方法来添加自定义引用添加行为（该函数默认在运行时没有行为）

特殊名词
`ARO`：`AddReferencedObjects` 的简写

## 自动引用生成与遍历

在上一章，我们已经知道，进行可达性分析的过程发生在 `ReachabilityAnalysisFunctions[]` 中。这个数组存储的是函数指针。

```cpp
	typedef void(FRealtimeGC::*ReachabilityAnalysisFn)(FWorkerContext&);

	/** Pointers to functions used for Reachability Analysis */
	ReachabilityAnalysisFn ReachabilityAnalysisFunctions[8];
```

这个函数指针的数组在 `FRealtimeGC` 的构造函数中被初始化，这里展示 `EGCOptions::None` 的初始化代码：

```cpp
FRealtimeGC()
{
	ReachabilityAnalysisFunctions[GetGCFunctionIndex(EGCOptions::None)] = &FRealtimeGC::PerformReachabilityAnalysisOnObjectsInternal<EGCOptions::None | EGCOptions::None>;
	...
}
```

实际上，所有 `EGCOptions` 的变体执行的都是 `FRealtimeGC::PerformReachabilityAnalysisOnObjectsInternal` 函数，只是它们被传入了不同的模板参数。可以说，`FRealtimeGC::PerformReachabilityAnalysisOnObjectsInternal` 是可达性分析的起点。下面是它的代码：

```cpp
	template <EGCOptions Options>
	void PerformReachabilityAnalysisOnObjectsInternal(FWorkerContext& Context)
	{
		TRACE_CPUPROFILER_EVENT_SCOPE(PerformReachabilityAnalysisOnObjectsInternal);
		//... Editor Only
		TReachabilityProcessor<Options> Processor;
		CollectReferencesForGC<TReachabilityCollector<Options>>(Processor, Context);
	}
```

- `TReachabilityProcessor` 是实现可达性分析的核心逻辑的类，下文中我们会具体介绍。

我们来具体看一看 `CollectReferencesForGC`：

```cpp
template<class CollectorType, class ProcessorType>
FORCEINLINE void CollectReferencesForGC(ProcessorType& Processor, UE::GC::FWorkerContext& Context)
{
    using FastReferenceCollector = TFastReferenceCollector<ProcessorType, CollectorType>;

    if constexpr (IsParallel(ProcessorType::Options))
    {       ProcessAsync([](void* P, FWorkerContext& C) { FastReferenceCollector(*reinterpret_cast<ProcessorType*>(P)).ProcessObjectArray(C); }, &Processor, Context);
    }    else
    {
       if (!GReachabilityState.IsSuspended())
       {          GReachabilityState.SetupWorkers(1);
          GReachabilityState.GetContextArray()[0] = &Context;
       }
       FastReferenceCollector(Processor).ProcessObjectArray(Context);

       Context.ResetInitialObjects();
       Context.InitialNativeReferences = TConstArrayView<UObject**>();

       GReachabilityState.CheckIfAnyContextIsSuspended();
    }}
```

其中，执行核心逻辑是这句：

```cpp
FastReferenceCollector(Processor).ProcessObjectArray(Context);
```

`ProcessObjectArray()` 函数的作用是递归地遍历对象引用链，标记所有可达对象。下面是对它的具体分析：

```cpp
	void ProcessObjectArray(FWorkerContext& Context)
	{
		Context.bDidWork = true;
		Context.bIsSuspended = false;
		static_assert(!EnumHasAllFlags(Options, EGCOptions::Parallel | EGCOptions::AutogenerateSchemas), "Can't assemble token streams in parallel");

		CollectorType Collector(Processor, Context);

		// Either TDirectDispatcher living on the stack or TBatchDispatcher reference owned by Collector
		decltype(GetDispatcher(Collector, Processor, Context)) Dispatcher = GetDispatcher(Collector, Processor, Context);
```

首先函数会初始化工作上下文，同时创建收集器和分发器：

- 收集器（`Collector`）负责收集引用
- 分发器（`Dispatcher`）根据（单/多线程）选择任务分发策略，对当前工作物体们进行一次引用遍历步进（Process）得到的引用首先会存储在分发器中，接着会通过块（Block）的方式分发给各个线程具体，下文会详细讲述这个过程

```cpp
StoleContext:
		// Process initial references first
		Context.ReferencingObject = FGCObject::GGCObjectReferencer;
		for (UObject** InitialReference : Context.InitialNativeReferences)
		{
			Dispatcher.HandleKillableReference(*InitialReference, EMemberlessId::InitialReference, EOrigin::Other);
		}
		TConstArrayView<UObject*> CurrentObjects = Context.InitialObjects;
```

这段代码的工作是从上下文获得根对象

- `FGCObject::GGCObjectReferencer` 是全局的根引对象引用管理器

```cpp
		while (true)
		{
			Context.Stats.AddObjects(CurrentObjects.Num());
			ProcessObjects(Dispatcher, CurrentObjects);
```

- `CurrentObjects` 是一个 UObject 指针的数组，存储着当前的待处理的对象
- `ProcessObjects` 就是遍历对象的引用的地方，它会将遍历到引用暂时存储在分发器 `Dispatcher` 中，本文会在之后会具体介绍这个函数，在此可以暂时认为我们已经得到了当前对象的引用关系在分发器中

```cpp
			// Free finished work block
			if (CurrentObjects.GetData() != Context.InitialObjects.GetData())
			{
				Context.ObjectsToSerialize.FreeOwningBlock(CurrentObjects.GetData());
			}
```

遍历完 `CurrentObjects` 后，它其实已经不被需要了，所以在这里就将它释放掉。（当然，初始对象列表是不能释放的，所以要进行一个条件判断）

```cpp
			if (Processor.IsTimeLimitExceeded())
			{
				FlushWork(Dispatcher);
				Dispatcher.Suspend();
				SuspendWork(Context);
				return;
			}
```

这段代码的作用是，检查是否超过增量回收的一帧的时间预算。如果已经超时了，就暂停回收，在下一帧继续回收。

- `Processor.IsTimeLimitExceeded()` 检查是否超过增量式回收的每帧预算
- `FlushWork` 将 `Dispatcher` 中的工作转移出来，暂存到 `TFastReferenceCollector` 中

到此，一次遍历步进的工作已经完成了，之后的代码是分配新的遍历工作。分配的方式是以工作块 `FWorkBlock` 为单位的。

- `FWorkBlock` 是一组固定数量的对象的集合。分块目的是将可达性分析的任务分割成多个快，有助于并行优化和增量式回收；同时 `FWorkBlock` 被设计成缓存友好的数据结构，有很好的性能表现。

```cpp
			int32 BlockSize = FWorkBlock::ObjectCapacity;
			FWorkBlockifier& RemainingObjects = Context.ObjectsToSerialize;
			FWorkBlock* Block = RemainingObjects.PopFullBlock<Options>();
```

这段代码就是将当前的待处理对象中获取一个完整大小的工作块（`FWorkBlock`），如果无法获取一个完整的工作块（待处理数量大于工作块容量），则进行更细致的工作分配：

```cpp
			if (!Block)
			{
				if constexpr (bIsParallel)
				{
					FSlowARO::ProcessUnbalancedCalls(Context, Collector);
				}

StoleARO:
				FlushWork(Dispatcher);
```

`FlushWork(Dispatcher)` 会将本地线程的任务结果合并到共享的 `RemainingObjects` 中。  这会增加 `RemainingObjects` 的数量，然后在下面引擎会再尝试获取工作块：

```cpp
				if (Block = RemainingObjects.PopFullBlock<Options>(); Block);
				else if (Block = RemainingObjects.PopPartialBlock(/* out if successful */ BlockSize); Block);
```

如果还是无法取出一个工作块，则只取出一个工作块。如果一个都取不出来，说明该线程的工作已经结束了。

- `BlockSize` 在上面的代码中出现过，是一个工作块的容量
- `RemainingObjects.PopPartialBlock` 必须是原子性的，因为有多个线程可能会请求进行此操作

```cpp
				else if (bIsParallel) // if constexpr yields MSVC unreferenced label warning
				{
					switch (StealWork(/* in-out */ Context, Collector, /* out */ Block, Options))
					{
						case ELoot::Nothing:	break;				// Done, stop working
						case ELoot::Block: break;				// Stole full block, process it
						case ELoot::ARO:	goto StoleARO;		// Stole and made ARO calls that feed into Dispatcher queues and RemainingObjects
						case ELoot::Context:	goto StoleContext;	// Stole initial references and initial objects worker that hasn't started working
					}
				}
```

既然这个线程没有了工作。`StealWork()` 函数会尝试获取其它线程没有做完的工作，它根据当前上下文返回一个 `ELoot` 枚举，这个枚举表示当前任务可以窃取的状态。`StealWork()`同时也会直接赋值传入的工作上下文。

- `Nothing`：什么都不干等到下面执行结束
- `Block`：上文中已经判断过了（实际上这个线程也不会执行到这里），直接获取一个完整的块，在下一次循环工作
- `ARO (AddReferencedObjects)`：窃取到其它线程未处理的手动引用工作，此时 `Block` 已经被替换成其它线程未处理的工作块，`goto` 到上面的 `StoleARO` 直接工作即可
- `Context`：窃取到其它线程未完成的上下文任务，此时上下问 `Context` 变量已经被替换成了其它上下文的工作，`goto` 到上面的 `StoleContext` 直接工作即可

可以发现，利用 `goto`，一个线程会在自己工作完成后，不断检查是否有其它没完成的工作，直到所有线程的所有工作都被完成，每个线程才会结束这段代码。

```cpp
				if (!Block)
				{
					break;
				}
			}
```

Block 为空意味着 `RemainingObjects` 和其它线程都没有工作要做了。所以退出 while 循环，意味遍历结束。

```cpp
			CurrentObjects = MakeArrayView(Block->Objects, BlockSize);
		} // while (true)
```

如果工作没有结束（取出了工作块），则将当前工作对象们设置为工作块中的对象。在下次循环进行工作。

```cpp
		Processor.LogDetailedStatsSummary();
	}
```

---

真正执行可达性分析的函数是 `ProcessObjects`，在上文中，它在 while 循环内被循环调用，我们来具体看一下它的内容：

```cpp
FORCEINLINE_DEBUGGABLE void ProcessObjects(DispatcherType& Dispatcher, TConstArrayView<UObject*> CurrentObjects)
{
	for (FPrefetchingObjectIterator It(CurrentObjects); It.HasMore(); It.Advance())
	{
		UObject* CurrentObject = It.GetCurrentObject();
		UClass* Class = CurrentObject->GetClass();
		UObject* Outer = CurrentObject->GetOuter();
```

首先它会对在 for 循环中对 `CurrentObjects` 进行迭代。并获取对象和对象的类的信息。

- `UClass` 是反射类，记录了一个类的反射信息，在下文中获取类的引用关系时会用到。`UClass` 的元数据在编译时生成，运行时只读
- `Outer` 变量是当前对象所在的父容器，在 UE 中，对象的父容器只存在一个，这也意味着一个对象有一条唯一的引用路径

```cpp
		if (!!(Options & EGCOptions::AutogenerateSchemas) && !Class->HasAnyClassFlags(CLASS_TokenStreamAssembled))
		{
			Class->AssembleReferenceTokenStream();
		}

		FSchemaView Schema = Class->ReferenceSchema.Get();
		Dispatcher.Context.ReferencingObject = CurrentObject;
```

- `FSchemaView` 类描述了一个类的所有强引用关系

```cpp
		// Emit base references
		Dispatcher.HandleImmutableReference(Class, EMemberlessId::Class, EOrigin::Other);
		Dispatcher.HandleImmutableReference(Outer, EMemberlessId::Outer, EOrigin::Other);
#if WITH_EDITOR
		UObject* Package = CurrentObject->GetExternalPackageInternal();
		Package = Package != CurrentObject ? Package : nullptr;
		Dispatcher.HandleImmutableReference(Package, EMemberlessId::ExternalPackage, EOrigin::Other);
#endif
```

- `HandleImmutableReference` 会在内部调用 `HandleReferenceDirectly`，它会将物体指针推到 `ImmutableBatcher` 中，确保不会被回收

因为 `UClass` 和 `Outer` 都继承自 `UObject`，它们都有被回收的可能。但是一个对象如果可达，那么其父容器和对象和反射信息一定不能被回收，所以引擎通过增加不可变引用的方式来实现这个效果。

引擎之所以让一个反射类 `UClass` 继承自 `UObject`，参与到垃圾回收中，是因为这样可以动态加载和卸载类的反射信息（仅蓝图类）。例如，在一个关卡中，一个蓝图类可能永远不会被使用，那么就可以不加载以优化性能。此外，对于已经加载的蓝图类反射信息，在使用结束后回也会被垃圾回收回收收掉。相对的，引擎也需要提供保护机制来确保一个 `UClass` 不会被错误回收。上文提到的就是其一。

```cpp
		if (!Schema.IsEmpty())
		{
			typename DispatcherType::SchemaStackScopeType SchemaStack(Dispatcher.Context, Schema);
			Processor.BeginTimingObject(CurrentObject);
			Private::VisitMembers(Dispatcher, Schema, CurrentObject);
			Processor.UpdateDetailedStats(CurrentObject);
		}}}
```

接下来这段代码中，判断如果这个类相关的对象的图不是空的，就执行遍历的逻辑。

- `BeginTimingObject` 和 `UpdateDetailedStats` 用于计时和性能分析
- `VisitMembers` 执行遍历类图的地方，它会将物体收集到 `Dispatcher` 的 `KillableBatcher` 和 `ImmutableBatcher` 中

上文提到过，`Dispacher` 中的物体最终会被合并到工作对象数组中。至此 `ProcessObjects` 完成了它进行一次可达性分析的步进的任务。
