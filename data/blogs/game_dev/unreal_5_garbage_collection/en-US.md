```toml
title = "Reading the Unreal 5 Garbage Collection Source"
summary = "UE 5 uses a mark-sweep garbage collection strategy, with support for incremental collection and cluster-based counting optimizations. This article walks through the UE 5 garbage collection source and summarizes some of its programming patterns."
ai-translation = true
```

> To make the code more concise and readable, logging is removed from the UE source excerpts in this article and represented with //...log. Elsewhere, ... represents omitted code.
> 

The UE source version used in this article is 5.5.4. In UE 5.0, incremental collection became enabled by default, alongside garbage collection updates including parallel collection. If the APIs differ, use 5.5.4 as the reference.

# Introduction

## Garbage collection concepts

UE 5 uses a mark-sweep garbage collection strategy, with support for incremental collection and cluster-based counting optimizations. This section introduces these terms and the ideas behind them at a conceptual level. If you're already familiar with them, feel free to skip this section.

- Garbage collection: Garbage collection, or the garbage collector, is a program that runs periodically. It checks whether objects are alive and frees the memory of dead objects. A “dead” object is one that can never be accessed again in the program: the “garbage” in “garbage collection.”
- Object graph (Objects Graph): An object graph is a directed graph formed by objects and the references between them. It describes how objects reference one another.
- Mark-sweep (Mark-Sweep): This is the most commonly used garbage collection strategy today. Its basic idea is to start from some special “root objects” and traverse the entire object graph by following references between objects. All visited objects receive a “reachable” mark. Then, all objects are traversed again. If an object has no reachable mark, we consider it dead. In mark-sweep, we treat an object's “liveness” and “reachability” as equivalent. Root objects in UE include `UWorld`, and they can also be specified manually.
- Incremental collection (Incremental): The idea behind “incremental collection” is to split one garbage collection into smaller pieces executed at different times. This spreads out the time spent on a collection and reduces the possibility of program hitches. Incremental collection also faces challenges, such as the object graph changing at runtime. It needs mechanisms for asynchronous safety, which will be introduced below.
- Full collection (Full): Full collection is a term used to distinguish it from incremental collection. Once incremental collection exists, the previous behavior of “doing all collection at once” becomes full collection.
- Object cluster (Cluster): A data structure that groups closely related objects together to reduce the number of object graph traversals.

## Article outline

The first chapter introduces the programming patterns used for garbage collection in UE 5 at a conceptual level. Understanding these ideas in advance will help with reading the source later. The article then analyzes the garbage collection source in three chapters, progressing from the basics to deeper details.

- UE 5 garbage collection programming patterns
- How garbage collection is triggered
- The garbage collection process
- References and the object graph

# UE 5 garbage collection programming patterns

## Incremental collection

UE 5 manages the state of incremental collection through global atomic variables. The engine usually uses `IsIncrementalPurgePending()` to check that state.

```cpp
// GarbageCollection.cpp
std::atomic<bool> GObjIncrementalPurgeIsInProgress = false;

// GarbageCollection.cpp
bool IsIncrementalPurgePending()
{
	return GObjIncrementalPurgeIsInProgress || GObjPurgeIsRequired;
}
```

In incremental collection, when work needs to span frames, the engine often caches unfinished work in global or member variables and continues using it in the next frame. Because so much work has to be deferred, the word “Pending,” meaning “awaiting processing,” appears frequently in the garbage collection source.

UE 5 also lets developers set a time limit (Time Limit) for garbage collection within a frame. This limit is stored in `GIncrementalGatherTimeLimit`, with a default value of `0.0`, meaning no limit. There is no single function for checking whether the limit has been exceeded; it is calculated in each specific context, as in the following code:

```cpp
// GarbageCollection.cpp/IncrementalPurgeGarbage()
bTimeLimitReached = UnhashUnreachableObjects(bUseTimeLimit, TimeLimit);
```

Below, I'll use “timed out” to mean that garbage collection has exceeded the frame's time budget.

Incremental and full collection code are also often mixed together, generally distinguished by names like `bFullPurage`.

## Multithreading and asynchronous optimizations

Garbage collection runs on the game thread. However, individual parts of the work also use multithreaded optimizations. Garbage collection in UE 5 uses some features of the Task Graph system, such as:

```cpp
// GarbageCollection.cpp/GatherUnreachableObjects
ParallelFor( TEXT("GC.GatherUnreachable"), ...)
```

It also uses lower-level routines designed specifically for GC, such as `ReferenceChainSearch.cpp`, which relates to reachability analysis.

UE also uses a singleton lock, `FGCCSyncObject`, to manage asynchronous resources. Global asynchronous resources must be locked and unlocked when used. The lock itself stores no resource pointers; it only makes other threads wait. The resources exist as global variables, so the engine must ensure that it manually locks and unlocks them when using global resources. The two related functions are `AcquireGCLock()` and `ReleaseGCLock()`. Names like these indicate locking or unlocking resources closely tied to garbage collection.

# How garbage collection is triggered

## Forced garbage collection

A simple way to understand where garbage collection is triggered and what it does is to start with forced garbage collection. Let's begin with the source of `ForceGarbageCollection()`.

```cpp
// UnrealEngine.cpp
void UEngine::ForceGarbageCollection(bool bForcePurge/*=false*/)
{
	TimeSinceLastPendingKillPurge = 1.0f + GetTimeBetweenGarbageCollectionPasses();
	bFullPurgeTriggered = bFullPurgeTriggered || bForcePurge;
	//...log
}
```

- `bForcePurge` indicates whether this forced collection is a full collection, as opposed to an incremental collection, `Incremental Purge`.
- The private member variable `TimeSinceLastPendingKillPurge` records the time since objects pending destruction were last purged. It is used to evaluate the garbage collection conditions.
- `GetTimeBetweenGarbageCollectionPasses()` gets the interval between two garbage collections.
- The private member variable `bFullPurgeTriggered` marks whether the next garbage collection will be a full collection.

We can see that forced garbage collection assigns `GetTimeBetweenGarbageCollectionPasses() + 1.0` to `TimeSinceLastPendingKillPurge`. This causes the garbage collection condition to be met. Next, I'll look at the conditional garbage collection code in detail.

## Conditional garbage collection

The “garbage collection conditions” mentioned above are evaluated in `ConditionalCollectGarbage()`, which is called every frame. The relevant code is:

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

- `ConditionalCollectGarbage()` is called every frame in `UWord::Tick`.
- `GetTimeBetweenGarbageCollectionPasses()` has two overloads: one with no arguments and one with a single argument.
    - The version with an argument takes a Boolean indicating whether any players are connected.
        - On a server, if no players are connected, the interval is multiplied by `GTimeBetweenPurgingPendingKillObjectsOnIdleServerMultiplier`, a static variable whose default value is `10.0`.
        - This lowers the garbage collection frequency and helps save power.
    - The no-argument version simply adds code to determine automatically whether any players are on the server. It does this by traversing all `UWorld` instances in `WorldList` and checking each world for players.

At this point, we can understand roughly how UE garbage collection is triggered. In short, the garbage collection system tracks elapsed time and triggers a collection at a set interval. The `TimeSinceLastPendingKillPurge` member variable of `UEngine` tracks the time, while `GetTimeBetweenGarbageCollectionPasses()` provides the interval:

The default garbage collection interval is `60.0`, stored in a static variable. When the machine is running as a server with no players connected, the interval is multiplied by another static variable whose default is `10.0`. In addition, if the current free memory is below the configured `GLowMemoryMemoryThresholdMB`, the rules above are ignored and a static variable with a default value of `30.0` is used, increasing collection frequency (if the default collection interval is shorter than the low-memory interval, the default collection interval). The related variables are listed below.

| Situation | Default collection interval/s | Related variable names |
| --- | --- | --- |
| Default | `60.0` | `GTimeBetweenPurgingPendingKillObjects` |
| Server with no players | `60.0 * 10.0` | `GTimeBetweenPurgingPendingKillObjectsOnIdleServerMultiplier` (interval multiplier with no server) |
| Low memory | `30.0` | `GLowMemoryMemoryThresholdMB` (low-memory threshold), `GLowMemoryTimeBetweenPurgingPendingKillObjects` (low-memory interval) |

### `ConditionalCollectGarbage` in detail

I've just introduced part of the logic in `ConditionalCollectGarbage` and the logic for obtaining the interval. Now let's look at the function as a whole.

```cpp
// UnrealEngine.cpp

void UEngine::ConditionalCollectGarbage()
{
	if (GFrameCounter != LastGCFrame)
```

- `GFrameCounter` is the engine's global frame number.
- `LastGCFrame` is the frame number when garbage collection was last triggered.

This condition prevents garbage collection from being triggered multiple times in one frame. At the end of this if statement is a condition for incremental collection, which I'll insert here:

---

```cpp
	else if (IsIncrementalReachabilityAnalysisPending())
	{
		PerformIncrementalReachabilityAnalysis(GetReachabilityAnalysisTimeLimit());
	}
```

- `IsIncrementalReachabilityAnalysisPending()` checks whether incremental garbage collection is pending.

This means that if the reachability analysis for incremental garbage collection has not finished, reachability analysis continues.

---

Returning to the original position in the code:

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

The `!(UE_BUILD_SHIPPING || UE_BUILD_TEST)` macro specifies that this code is enabled only in non-shipping, non-test builds. This part relates to stress testing and debugging.

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

This code relates to **forced** garbage collection.

- The `EGarbageCollectionType` enum has three values:
    - `None`: do not force collection.
    - `Incremental`: force incremental collection.
    - `Full`: force full collection.

In `UEngine`, `ShouldForceGarbageCollection()` always returns `None`. Forced incremental collection is only possible when using `VERSE`. Next comes the actual garbage collection code.

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

- `bFullPurgeTriggered` marks whether to use full garbage collection and is set to `false` afterward.

If full garbage collection is used, garbage collection runs through `TryCollectGarbage` and `CleanupActors`. These two functions will be introduced in detail in later sections. If full collection is not used, we move to the following part.

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

This for loop looks for situations that might change GC frequency. Specifically, as described above, it checks all worlds on the server for connected players. The resulting `bHasPlayersConnected` is used by the previously mentioned `GetTimeBetweenGarbageCollectionPasses(bool)`. The main reason the no-argument `GetTimeBetweenGarbageCollectionPasses()` is not used here is that the engine also needs to know whether a world has begun play, namely `bHasAWorldBegunPlay`. The code immediately below uses it:

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

The general logic is that the engine only tracks time and evaluates garbage collection once at least one world has begun running. If incremental collection tasks remain from the previous frame, it continues that work. If there are no incremental collection tasks, it checks whether the elapsed time has reached the interval to determine whether a new garbage collection cycle should begin.

- `PerformIncrementalReachabilityAnalysis()` performs step-by-step reachability analysis for incremental garbage collection.
- `PerformGarbageCollectionAndCleanupActors()` performs full garbage collection. Its contents resemble the full garbage collection logic introduced above, also using `UEngine::TryCollectGarbage()` and `UWorld::CleanupActors` to collect objects. This will be explained in detail below.
- `IncrementalPurgeGarbage()` destroys `UObject` instances and frees memory. Reachability analysis is already complete at this point. Later chapters will explain this in detail.

```cpp
		if (const int32 Interval = CVarCollectGarbageEveryFrame.GetValueOnGameThread())
		{
			if (0 == (GFrameCounter % Interval))
			{
				ForceGarbageCollection(true);
			}
		}
```

This code handles forced intervention in garbage collection through a console variable, forcing garbage collection every `Interval` frames.

- `CVarCollectGarbageEveryFrame` provides the frame interval.

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

- `CVarContinuousIncrementalGC` enables continuous incremental GC. todo

```cpp
	else if (IsIncrementalReachabilityAnalysisPending())
	{
		PerformIncrementalReachabilityAnalysis(GetReachabilityAnalysisTimeLimit());
	}
}
```

Finally, we reach the end of the if statement inserted earlier. If reachability analysis for incremental collection has not finished, perform reachability analysis.

### `PerformGarbageCollectionAndCleanupActors()`

As mentioned above, the engine calls `PerformGarbageCollectionAndCleanupActors()` when performing full collection. Its source is:

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

- `GPerformGCWhileAsyncLoading` indicates whether GC is allowed during asynchronous loading. Together with `IsAsyncLoading()`, it determines whether to trigger GC during asynchronous loading.
- `bForcePurge` defaults to true, but forced purging is canceled as soon as an active game world is found (`World != nullptr && World->IsGameWorld`). This helps reduce game hitches and prioritizes smooth gameplay.

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

Here is the code that performs garbage collection. It closely resembles the forced full garbage collection code above. The two important functions are `TryCollectGarbage` and `CleanupActors`, which the next section will focus on.

# The garbage collection process

As mentioned above, in conditional garbage collection (`UEngine::ConditionalCollectGarbage`), collection happens in two places: forced full garbage collection (`Engine::bFullPurgeTriggered`) and `PerformGarbageCollectionAndCleanupActors()`. Their core logic is very similar. Let's first look at the code for both:

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

As the source shows, `TryCollectGarbage()` is called first. Then all worlds (`UWorld`) are traversed, and their `CleanupActors()` functions are called to clean up `Actor` instances. Below, I'll explain how `TryCollectGarbage()` and `CleanupActors()` work in detail. One point to note is that forced full garbage collection does not affect the normal garbage collection frame count, because it does not update `LastGCFrame`.

## Collection process: `TryCollectGarbage`

`IsSuspended` will appear frequently below. It indicates whether incremental collection is suspended (or paused). Also, an object cluster (Cluster) means a series of closely related `UObject` instances, such as an Actor and its Sub Objects. Clearly, using clusters in garbage collection can reduce the number of traversals.

`TryCollectGarbage` calls `FReachabilityAnalysisState::PerformReachabilityAnalysisAndConditionallyPurgeGarbage` in `GarbageColleciton.h`. Its general process is:

- Perform pre-collection: `UE::GC::PreCollectGarbageImpl<true>(ObjectKeepFlags);`
    - The Boolean template argument indicates whether this is a full collection.
- Perform reachability analysis: `PerformReachabilityAnalysis()`;
- Perform post-collection: `UE::GC::PostCollectGarbageImpl<true>(ObjectKeepFlags);`
    - This executes `UObject::BeginDestroy` and `UObject::FinishDestroy` and reclaims memory.

This section discusses **reachability analysis** and **sweeping separately**.

### Reachability analysis

Reachability analysis involves intermediate functions, such as `PerformReachabilityAnalysis()` in `FReachabilityAnalysisState`. Regardless of these intermediate functions, the underlying call is always to `FRealtimeGC::PerformReachabilityAnalysis`, so I'll focus on its execution logic here. Below is a step-by-step analysis of this function:

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

This while loop executes `PerformReachabilityAnalysisPass`, the main function for reachability marking. Its role is to advance reachability analysis by one step. This function will be introduced in detail below. Reachability analysis is completed through repeated steps in the loop, which only exits in these two situations:

- Incremental mode is suspended, usually because execution has exceeded one frame's time budget. Processing then waits until the next frame.
- The reachable objects or reachable object clusters are empty, meaning the data has been processed.

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

Tracing external objects. todo

---

### `PerformReachabilityAnalysisPass`

`PerformReachabilityAnalysisPass` is the core here. It is used in a While loop that gradually marks all objects. Below is a step-by-step analysis of this function:

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

First, a context, `FWorkContext`, is created. This context contains the initial objects and reference information for the local thread. If incremental collection is paused, the previous incremental collection context is used directly. If it is not paused, a new work context is created. Another important variable used here is the cached list of reachable objects, `GReachableObjects` (called the “reachable object cache” below). This is a static object that stores `UObject*` pointers. It caches objects left unprocessed when incremental garbage collection is suspended. If incremental collection is suspended, these objects will be processed in the next incremental collection.

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

Next, the function moves the objects cached in `GReachableObjects` to `InitialObjects` for later processing.

```cpp
	else if (GReachabilityState.GetNumIterations() == 0 || (Stats.bFoundGarbageRef && !GReachabilityState.IsSuspended()))
	{
		Context->InitialNativeReferences = GetInitialReferences(Options);
	}
```

If the reachable object cache is empty, initialize the initial references, `InitialNativeRefernces`, in the work context. todo

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

Here, the reachable cluster cache is processed similarly to the reachable object cache above (todo). The code above marks all clusters referenced by reachable clusters as reachable.

```cpp
	Context->SetInitialObjectsUnpadded(InitialObjects);
```

todo!

```cpp
	PerformReachabilityAnalysisOnObjects(Context, Options);
```

This function is a key part of reachability analysis. It calls the reachability analysis function according to the current configuration:
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

Finally, if incremental collection is not suspended, perform the wrap-up work for incremental collection. todo!

- Reset the worker threads.
- Merge statistics.
- Return the context to the object pool.

### Cleaning up garbage

`TryCollectGarbage/PostCollectGarbageImpl()`

Garbage cleanup mainly takes place in `PostCollectGarbageImpl()`. Its main responsibility is to clean up objects marked as unreachable.

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

The logic above is mainly responsible for dissolving unreachable object clusters. I won't go into detail here.

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

Mark objects as unreachable and add them to `GUnreachableObjects`.

```cpp
	GIsGarbageCollectingAndLockingUObjectHashTables = false;
	UnlockUObjectHashTables();

	GIsGarbageCollecting = false;

	// The hash tables lock was released when reachability analysis was done.
	// BeginDestroy, FinishDestroy, destructors and callbacks are allowed to call functions like StaticAllocateObject and StaticFindObject.
	// Now release the GC lock to allow async loading and other threads to perform UObject operations under the FGCScopeGuard.
	ReleaseGCLock();
```

Only now is reachability analysis fully complete. The process above runs on the main GC thread. Here, the GC lock is released so that subsequent work can run in parallel.

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

Most of the code is omitted here, leaving the core portion.

- `IncrementalPurgeGarbage()` is the core function for reclaiming memory. In `PostCollectGarbageImpl`, it only performs full collection. However, this function is also called in `UEngine::ConditionalCollectGarbage()`.

### `GatherUnreachableObjects`

`GatherUnreachableObjects` traverses all objects in parallel and checks their reachability. The core code is:

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

- `FGCFlags::IsMaybeUnreachable_ForGC` checks whether the object has `MaybeUnreachableObjectFlag`.
- `Iterator.Payload.Add({ ObjectItem })` adds an unreachable object to a temporary collection for later use.

Each thread's iterator is allocated in advance and then iterated. Besides a raw `UObjectBase*` pointer, `FUObjectItem` also stores an `FGCFlags` bit flag. An unreachable object receives the `EInternalObjectFlags::Unreachabl` flag.

### `IncrementalDestroyGarbage`

Within `IncrementalPurgeGarbage`, the core part involving this function is as follows.

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

In this code, the engine calls `UnhashUnreachableObjects()`. Its main functions are to:

- Remove unreachable objects from the global hash tables. The global hash tables are used to locate objects quickly, as in `StaticFindObject`, and to index resources by path.
- Execute `BeginDestroy()` on all unreachable objects.
- Return a Boolean indicating whether the time limit has been exceeded.

If the time limit has already been exceeded after calling `BeginDestory()`, the remaining work is deferred to the next frame. Next, the function eventually calls `IncrementalDestroyGarbage()`.

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

This part traverses all unreachable objects in `GUnreachableObjects` and calls their `FinishDestory()` methods. If `IsReadyForFinishDestroy()` is not satisfied, processing is deferred (`GGCObjectsPendingDestruction`). Note that objects are not removed from `GUnreachableObjects` at this point, nor is their memory freed. Memory will be freed after all objects have executed `FinishDestroy`.

- Destruction (called “destruction” below): From the source, we can see that Destruction here means calling `FinishDestroy()`, not freeing memory.
- `IsReadyForFinishDestroy()` is a public virtual function of `UObject`. The default `UObject` implementation returns `true`. It is commonly overridden; for example, in `AActor`, it only becomes `true` once rendering has finished.
- `ConditionalFinishDestroy()` executes `FinishDestroy()`.
- `GGCObjectsPendingDestruction` caches pointers to objects pending destruction. This variable is global. If its contents are not fully processed in the current frame, for example because the time limit is exceeded, they will be processed in the next frame. I'll call this object the “pending destruction array” below.

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

Next, the engine checks whether garbage collection has timed out. If so, it exits the while loop. At this point, `FinishDestroy()` has not yet been called on all garbage objects. A long stretch of code below handles objects whose processing was deferred in `GGCObjectsPendingDestruction`.

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

The outer while loop repeatedly polls `GGCObjectsPendingDestruction` until the time limit is exceeded or no objects remain pending destruction. An empty array means all work is complete, and the process will enter the memory release phase.

During one polling pass, the inner while loop first traverses `GGCObjectsPendingDestruction`, attempting to destroy its objects and remove them from `GGCObjectsPendingDestruction`. If an object still cannot be destroyed, it remains in `GGCObjectsPendingDestruction` until the next polling pass.

```cpp
				if( bUseTimeLimit )
				{
					break;
				}
```

If a per-frame time limit for CG is set and we have completed one full iteration over all remaining objects, the loop exits immediately, even if time or unprocessed objects remain. At this point, it is likely waiting for the render thread, for example to release resources. Unfinished work continues in the next frame.

```cpp
				else if( GGCObjectsPendingDestructionCount > 0 ) {
					if (FPlatformProperties::RequiresCookedData()) {...}
// Sleep before the next pass to give the render thread some time to release fences.
					FPlatformProcess::Sleep( 0 );
				}
				LastLoopObjectsPendingDestructionCount = GGCObjectsPendingDestructionCount;
			}
```

If no time limit is set, the engine checks elapsed time at the end of a loop and prints a log message to alert the developer if it considers the duration too long.

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

After leaving the loop, it checks once more whether `FinishDestroy` has completed. If the pending destruction array is not empty, it will be processed in this function in the next frame. If it is empty, all destruction work is complete, and memory can be freed! The function then sets `GObjFinishDestroyHasBeenRoutedToAllObjects` to `true`. This Boolean will be used shortly. The following code is where memory is actually freed.

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

I've kept only the core call here. The key is `DestroyObjects()`, which traverses `GUnreachableObjects` and frees their memory. I won't go deeper into memory release here. After all objects have been freed, `bCompleted` is set to `true` and returned at the end.

```cpp
	return bCompleted;
}
```

That concludes the analysis of `IncrementalDestroyGarbage()`. Its job is to call `FinishDestroy()` and free memory. It uses a **pending destruction array** to cache work across frames, enabling incremental collection, and a while loop to wait for each object pending destruction to finish executing `FinishDestroy()`.

## `UWorld::CleanupActors()`

`CleanupActors` runs after `TryCollectGarbage`. Actors are often deleted by manually calling `Destroy`. `Destroy` does not free an Actor's memory; it only marks it as pending destruction for the garbage collection system to handle. Here's what happens when `Destroy()` is triggered:

- Trigger the `OnDestroy` event and Blueprint logic.
- Mark the `Actor` as pending destruction (including setting its entry in the `ULevel` Actor array to `nullptr`, calling `MarkAsGarbage()`, and so on).
- Remove it from game logic so it no longer participates in rendering or physics calculations.

`MarkAsGarbage()` marks a `UObjectBaseUtility` (a base class of `UObject`) as garbage. This function adds a `Flag` named `RF_MirroredGarbage` to the object.
The way to check directly whether a `UObjet` is garbage is to use `IsValidChecked()`. This function actually checks the `Flag`; a return value of `false` means the object is garbage. During garbage collection, objects directly marked as garbage this way are marked as unreachable, and then freed during garbage collection.

`CleanupActors` removes null pointers from a level's Actor list. The process is not complicated, so I'll explain it directly through comments:

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

# References and the object graph

The object graph is represented logically by the reference relationships among all `UObject` instances. There is no direct, explicit object graph data structure. Runtime reference collection works roughly as follows:

- Automatically collect `UPROPERTY` references, primarily in cooperation with the reflection system.
- For non-`UPROPERTY` references, use `UGCObject` and override `UObject::AddReferencedObjects` to add custom reference-adding behavior (this function does nothing at runtime by default).

Special terminology
`ARO`: short for `AddReferencedObjects`.

## Automatic reference generation and traversal

In the previous chapter, we learned that reachability analysis takes place in `ReachabilityAnalysisFunctions[]`. This array stores function pointers.

```cpp
	typedef void(FRealtimeGC::*ReachabilityAnalysisFn)(FWorkerContext&);

	/** Pointers to functions used for Reachability Analysis */
	ReachabilityAnalysisFn ReachabilityAnalysisFunctions[8];
```

This array of function pointers is initialized in the constructor of `FRealtimeGC`. Here is the initialization code for `EGCOptions::None`:

```cpp
FRealtimeGC()
{
	ReachabilityAnalysisFunctions[GetGCFunctionIndex(EGCOptions::None)] = &FRealtimeGC::PerformReachabilityAnalysisOnObjectsInternal<EGCOptions::None | EGCOptions::None>;
	...
}
```

In fact, all `EGCOptions` variants execute `FRealtimeGC::PerformReachabilityAnalysisOnObjectsInternal`, just with different template arguments. We can say that `FRealtimeGC::PerformReachabilityAnalysisOnObjectsInternal` is the starting point of reachability analysis. Here is its code:

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

- `TReachabilityProcessor` is the class implementing the core reachability analysis logic. We'll introduce it in detail below.

Let's take a closer look at `CollectReferencesForGC`:

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

The line that executes the core logic is:

```cpp
FastReferenceCollector(Processor).ProcessObjectArray(Context);
```

`ProcessObjectArray()` recursively traverses the object reference chain and marks all reachable objects. Below is a detailed analysis:

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

First, the function initializes the work context and creates a collector and a dispatcher:

- The collector (`Collector`) collects references.
- The dispatcher (`Dispatcher`) chooses a task dispatch strategy based on whether execution is single-threaded or multithreaded. References obtained by one reference traversal step (Process) over the current work objects are first stored in the dispatcher, then distributed to individual threads in blocks (Block). This process will be explained in detail below.

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

This code obtains the root objects from the context.

- `FGCObject::GGCObjectReferencer` is the global root object reference manager.

```cpp
		while (true)
		{
			Context.Stats.AddObjects(CurrentObjects.Num());
			ProcessObjects(Dispatcher, CurrentObjects);
```

- `CurrentObjects` is an array of UObject pointers storing the objects currently awaiting processing.
- `ProcessObjects` is where object references are traversed. It temporarily stores the references it visits in the dispatcher, `Dispatcher`. I'll introduce this function in detail later. For now, we can assume that the references of the current objects have been obtained and stored in the dispatcher.

```cpp
			// Free finished work block
			if (CurrentObjects.GetData() != Context.InitialObjects.GetData())
			{
				Context.ObjectsToSerialize.FreeOwningBlock(CurrentObjects.GetData());
			}
```

Once `CurrentObjects` has been traversed, it is no longer needed, so it is freed here. (Of course, the initial object list cannot be freed, which is why the condition is necessary.)

```cpp
			if (Processor.IsTimeLimitExceeded())
			{
				FlushWork(Dispatcher);
				Dispatcher.Suspend();
				SuspendWork(Context);
				return;
			}
```

This code checks whether incremental collection has exceeded its time budget for one frame. If it has timed out, collection is paused and resumes in the next frame.

- `Processor.IsTimeLimitExceeded()` checks whether incremental collection has exceeded its per-frame budget.
- `FlushWork` moves work out of `Dispatcher` and temporarily stores it in `TFastReferenceCollector`.

At this point, one traversal step is complete. The code that follows allocates new traversal work. Work is allocated in units of work blocks, `FWorkBlock`.

- `FWorkBlock` is a collection of a fixed number of objects. Splitting work into blocks divides reachability analysis tasks into multiple pieces, helping with parallel optimization and incremental collection. `FWorkBlock` is also designed as a cache-friendly data structure with good performance.

```cpp
			int32 BlockSize = FWorkBlock::ObjectCapacity;
			FWorkBlockifier& RemainingObjects = Context.ObjectsToSerialize;
			FWorkBlock* Block = RemainingObjects.PopFullBlock<Options>();
```

This code takes a full-sized work block (`FWorkBlock`) from the objects currently awaiting processing. If a full work block cannot be obtained (the pending count is greater than the block capacity), more fine-grained work allocation follows:

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

`FlushWork(Dispatcher)` merges the local thread's task results into the shared `RemainingObjects`. This increases the number of objects in `RemainingObjects`, and the engine then tries to obtain a work block again below:

```cpp
				if (Block = RemainingObjects.PopFullBlock<Options>(); Block);
				else if (Block = RemainingObjects.PopPartialBlock(/* out if successful */ BlockSize); Block);
```

If it still cannot take out a work block, it takes just one work block. If it cannot take even one, this thread's work is finished.

- `BlockSize`, seen in the code above, is the capacity of one work block.
- `RemainingObjects.PopPartialBlock` must be atomic, because multiple threads may request this operation.

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

Now that this thread has no work left, `StealWork()` attempts to obtain unfinished work from other threads. Based on the current context, it returns an `ELoot` enum indicating the kind of work the current task can steal. `StealWork()` also directly assigns to the work context passed in.

- `Nothing`: Do nothing and wait for execution to end below.
- `Block`: This was already checked above (in fact, this thread will not reach here either). Obtain a full block directly and work on it in the next iteration.
- `ARO (AddReferencedObjects)`: Steal manual reference work left unprocessed by another thread. At this point, `Block` has already been replaced with the other thread's unprocessed work block. Use `goto` to jump to `StoleARO` above and start working directly.
- `Context`: Steal an unfinished context task from another thread. At this point, the `Context` variable has been replaced with work from another context. Use `goto` to jump to `StoleContext` above and start working directly.

We can see that, by using `goto`, a thread continually checks for other unfinished work after completing its own. Each thread only exits this code once all work on all threads is complete.

```cpp
				if (!Block)
				{
					break;
				}
			}
```

An empty Block means neither `RemainingObjects` nor the other threads have any work left to do. The while loop therefore exits, ending the traversal.

```cpp
			CurrentObjects = MakeArrayView(Block->Objects, BlockSize);
		} // while (true)
```

If work is not finished (a work block was obtained), set the current work objects to the objects in that block. They will be processed in the next iteration.

```cpp
		Processor.LogDetailedStatsSummary();
	}
```

---

The function that actually performs reachability analysis is `ProcessObjects`. Above, it was called repeatedly inside a while loop. Let's look at its contents in detail:

```cpp
FORCEINLINE_DEBUGGABLE void ProcessObjects(DispatcherType& Dispatcher, TConstArrayView<UObject*> CurrentObjects)
{
	for (FPrefetchingObjectIterator It(CurrentObjects); It.HasMore(); It.Advance())
	{
		UObject* CurrentObject = It.GetCurrentObject();
		UClass* Class = CurrentObject->GetClass();
		UObject* Outer = CurrentObject->GetOuter();
```

First, it iterates over `CurrentObjects` in a for loop and obtains information about each object and its class.

- `UClass` is a reflection class that records a class's reflection information. It will be used below when obtaining the class's reference relationships. `UClass` metadata is generated at compile time and is read-only at runtime.
- The `Outer` variable is the current object's parent container. In UE, an object has only one parent container, which also means it has a unique reference path.

```cpp
		if (!!(Options & EGCOptions::AutogenerateSchemas) && !Class->HasAnyClassFlags(CLASS_TokenStreamAssembled))
		{
			Class->AssembleReferenceTokenStream();
		}

		FSchemaView Schema = Class->ReferenceSchema.Get();
		Dispatcher.Context.ReferencingObject = CurrentObject;
```

- `FSchemaView` describes all strong reference relationships of a class.

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

- `HandleImmutableReference` internally calls `HandleReferenceDirectly`, which pushes the object pointer into `ImmutableBatcher` to ensure it is not collected.

Because `UClass` and `Outer` both inherit from `UObject`, both can be collected. However, if an object is reachable, its parent container and reflection information must not be collected. The engine achieves this by adding immutable references.

The engine makes the reflection class `UClass` inherit from `UObject` and participate in garbage collection so that class reflection information can be loaded and unloaded dynamically (Blueprint classes only). For example, a Blueprint class might never be used in a level, so leaving it unloaded can improve performance. Reflection information for Blueprint classes that have already been loaded can also be garbage-collected once they are no longer used. In turn, the engine needs protective mechanisms to ensure that a `UClass` is not collected incorrectly. The mechanism mentioned above is one of them.

```cpp
		if (!Schema.IsEmpty())
		{
			typename DispatcherType::SchemaStackScopeType SchemaStack(Dispatcher.Context, Schema);
			Processor.BeginTimingObject(CurrentObject);
			Private::VisitMembers(Dispatcher, Schema, CurrentObject);
			Processor.UpdateDetailedStats(CurrentObject);
		}}}
```

The next part of the code checks whether the object graph associated with this class is nonempty and, if so, executes the traversal logic.

- `BeginTimingObject` and `UpdateDetailedStats` are used for timing and performance analysis.
- `VisitMembers` is where the class graph is traversed. It collects objects into `Dispatcher`'s `KillableBatcher` and `ImmutableBatcher`.

As mentioned above, objects in `Dispacher` are eventually merged into the work object array. At this point, `ProcessObjects` has completed its task of advancing reachability analysis by one step.
