```toml
title = "Unreal 5 多人网络同步源码梳理（基础）"
english_title = "Unreal 5 multiplayer network source code reading."
slug = "本文结合 UE 源码与注释梳理了 UE 5 的多人网络同步（服务器-客户端模式）下的网络同步过程。"
published = true
last_edited_date = "2025-06-08T23:48:00"
authors = "宏楼 Koiro"
class = "Dev"
date = "2025-06-05T13:33:00"
date = "2025-05-27"
tags = ["Cpp", "Unreal"]
```

> 笔者撰写本文使用的 UE 版本为 5.5.4
> 

在 `NEtDriver.h` 中，有一段详细的关于 UE 的网络同步机制的介绍。本文首先会在第一章翻译 UE 的注释文档，这对理解网络同步过程与其代码会很有帮助。接着，网络同步主要发生在 Tick 阶段，本文会在第二章中具体看一下 `UNetDriver` 类和它的 `Tick` 阶段的行为，同时也会涉及一些 `UIpNetDriver` 的内容。接下来，UE 对握手流程有一个较为清除的介绍，本文会再深入一些握手的具体代码，看看握手在 UE 源码中的触发时机。

# UE 网络同步系统

`NetDriver.h` 中的注释文档非常详细地介绍了网络同步机制和相关的类与代码。笔者在此先对其进行翻译。理解这部分内容对理解 UE 5 的网络同步机制十分有帮助。

## `NetDriver`, `NetConnections` 和 `Channels`

`UNetDriver` 负责管理一系列 `UNetConnetions`。一些典型的 `UNetDrivers` 有：

- `Game NetDriver`：负责正常的游戏网络通信
- `Demo NetDriver`： 负责记录或回放记录的游戏数据，这也是提供回放功能的地方
- `Beacon NetDriver`：负责正常游戏外网络通信

开发者也可以实现自己的 `NetDrivers`。

`NetConnections` 表示一个连接到游戏（更宽泛的说是 `NetDriver`）的独立的用户。

末端数据并不会直接被 `NetConnections` 处理，`NetConnections` 会将数据发送到 `Channels`，一般的 `Channel` 类型有：

- `Control Channel`：用于发送监控当前连接的数据，比如当前连接是否需要关闭等
- `Voice Channel`：用于在客户端和服务端之间发送声音数据
- `Unique Actor Channel` 在每个从服务端复制到客户端的 `Actor` 都存在

即使不是很常见，也可能会有使用自定义的 `Channel` 的情况。

## `Game Net Drivers`, `Net Connections`, 和 `Channels`

在一般情况下，在服务器和客户端只会存在一个 `NetDriver`，用于常规的游戏通信和连接。

服务器的 `NetDriver` 会维护一个 `NetConnection` 列表，每一个都代表了一个在游戏中的玩家，负责复制 `Actor` 数据。

客户端的 `NetDriver` 只有一个 `NetConnection`，表示与服务器的连接。

不管是在服务器还是在客户端，`NetDriver` 都负责接受来自网络的包，并将它们传递到正确的连接（需要时，建立新的 `NetConnection`）。

## 初始化连接 / 握手流程（Initiating Connections / Handshaking Flow）

`UIpNetDriver` 和 `UIpConnection`（或者它们的派生类）是引擎在大部分平台的默认选项。下面的文本描述了如何建立和管理连接。这些实现在不同的 `NetDriver` 中可能有所不同。

服务器和客户端拥有它们自己的 `NetDriver`，所有 UE 游戏复制通信都会在 `IpNetDriver` 来发送和接受。这些通信中也包含建立和重新建立连接（如果哪里出错了）的逻辑。

握手被分在多个地方进行：`NetDriver`、`PendingNetGame`、`World`、`PacketHandlers`等。将逻辑分在各处是因为需求是分散的。例如：一个连接是否正在发送 `UE-Protocol` 数据、一个地址是否有恶意、一个客户端是否有正确的游戏版本等。

### 开始设置与握手（Startup and Handshaking）

不管一个服务器在什么时候加载地图（通过 `UEngine::LoadMap`），我们都会调用 `UWorld::Listen`。它的作用是创建主要的 `Game Net Driver`，解析设置，并调用 `UNetDriver::InitListen`。最终，代码负责说明如何具体地监听客户端的连接。例如：在 `IpNetDriver` 中，将要被绑定到 IP/Port 会通过 `Socket Subsystem` 被决定（见 `ISocketSubsystem::GetLocalBindAddresses` 和 `ISocketSubsystem::BindNextPort`）。

既然服务器开始监听了，是时候开始接受客户端的连接了。

不管客户端在什么时候想要加入服务器，它们首先都会在 `UEngine::Browse` 中，建立一个有服务器 IP 的新 `UPendingNetGame`。
`UPendingNetGame::Initialize` 和 `UPendingNetGame::InitNetDriver` 负责进行初始化，并会各自设置 `NetDriver`。
在这个初始化过程中，客户端将会立即为服务器设置 `UNetConnection`，并开始通过这个连接发送数据，来初始化握手过程。

不管是在客户端还是在服务端，`UNetDriver::TickDispatch` 都负责接受网络数据。通常来说，当我们接受到一个包时，我们会检查它的地址，看看我们知不知道这个地址。我们需要确定我们有没有对这个源地址建立了连接，这件事通过简单地维护一个键为 `FInternetAddr`，值为 `UNetConnection` 的表完成。

如果一个包来自一个已经建立的连接，我们将包通过 `UNetConnection::ReceivedRawPacket` 传入连接。如果一个包来自一个没有被建立的连接，我们认为它是 `connectionless`（下文称“无连接的”），并尝试开始握手。要了解如何握手，见 `StatelessConnectionHandlerComponent.cpp`。

### `UWorld / UPendingNetGame / AGameModeBase Startup and Handshaking`

在 `UNetDriver` 和 `UNetConnection` 在服务器和客户端完成了它们的握手工作后。
客户端将会触发 `UPendingNetGame::SendInitialJoin` 游戏级别（game level）的握手。

游戏基本的握手大部分在 `FNetControlMessages` 完成。完整的控制信息集合可以见 `DataChannel.h`。

大部分处理这些控制信息的工作是同时在 `UWorld::NotifyControlMessage` 和 `UPendingNetGame::NotifyControlMessage` 完成的。大致流程如下：

> 使用挑战-响应（challenge-response）机制
> 
- 客户端的 `UPendingNetGame::SendInitialJoin` 发送 `NMT_Hello`
- 服务器的 `UWorld::NotifyControlMessage` 接收 `NMT_Hello`，并发送 `NMT_Challenge`
- 客户端的 `UPendingNetGame::NotifyControlMessage` 接收 `NMT_Challenge` 并在 `NMT_Login` 中发回数据
- 服务器的 `UWorld::NotifyControlMessage` 接收 `NMT_Login`，验证质询数据（challenge data），并调用 `AGameModeBase::PreLogin`
- 如果 `PreLogin` 并没有报任何错误，服务器会调用 `UWorld::WelcomePlayer`，这个函数会调用 `AGameModeBase::GameWelcomePlayer` 并发送含有地图信息的 `NMT_Welcome`
- 客户端的 `UPendingNetGame::NotifyControlMessage` 接收 `NMT_Welcome`，并读取地图信息（以便于后续加载），然后发送 `NMT_NetSpeed` 信息，这里包含了客户端配置了的网络速度（Net Speed）
- 服务器的 `UWorld::NotifyControlMessage` 接收 `NMT_NetSpeed`，然后调整到正确的网络速度

在此时，握手基本上可以认为完成了。玩家已经完全连接了游戏。此外，根据加载地图的时间，客户端可能还会在控制权转移到 `UWorld` 之前，在 `UPendingNetGame` 接收到一些非握手的控制信息（control messages）。必要时，还可以采取一些其它步骤来处理加密（Encryption）。

### 重新建立丢失的连接（Reestablishing Lost Connections）

在整个游戏过程中，有很多原因可以导致用户的连接丢失——互联网中断、从 LET 切换到 WIFI、离开游戏等。

- initiated：发起

如果服务器服务器发起了断开连接，或者意识到了其中一个情况（由于超时或错误），断开连接将会关闭 `UNetConnection` 并告知（Notify）游戏的方式以被处理。这是，由游戏决定是否支持 Join In Progress 或 Rejoins 机制。如果游戏不支持，那么我们会重新走一遍上面讲述过的握手流程。

- albeit：尽管

如果客户端的连接只是服务器意识不到的短暂中断（interrupts），引擎/游戏通常会自动修复，尽管会存在一些丢包（packet loss）和延迟峰值（lag spike）。

然而，如果客户端的 IP 地址或端口改变但服务器意识不到这点，将会开始一段修复流程。修复流程会重新进行底层握手，此时游戏代码并不会警告。与此相关的代码在 `StatlessConnectionHandlerComponent.cpp` 中。

## 数据传输（Data Transmission）

游戏的 `NetConnecitons` 和 `NetDrivers` 通常与底层所使用的通信方法/技术无关。这些留给了它们的派生类去处理（如 `UOpConnectio` / `UIpNetDriver` 或者 `UWebSocketConnection` / `UWebSocketNetDriver`）。

相反的，`UNetDriver` 和 `UNetConnection` 与 `Packets` 和 `Bunches` 一起工作。

`Packet`（包）是一堆主机与用户的 `NetConnection` 间，互相发送的数据块（blob）。它包含包本身的元数据，例如头文件信息和知识产权，还有 `Bunches`。

`Bunch`（束）是一对用户和主机的 `Channel` 间，互相发送的数据块（blob）。当一个连接接收了一个包，这个包将被拆成单独的束。这些束接着被传入独立的 `Channel`，等待之后被处理。

一个包可能不包含束、包含一个或多个束。因为 UE 中，多个束的大小限制可能比一个单独的包要大，所以 UE 支持“子束标记”（notion of partial bunches）：

当一个束太大时，它会被分成多个小束，这些束会被打上 `PartialInitial`、`Partial` 或 `PartialFinal` 的标记，使用这些信息，我们可以再接收后重新组合这些束回一个整体。

例子 - 客户端 RPC 服务器：

- 客户端调用 `Server_RPC`
- 这个请求将会通过 `NetDriver` 和 `NetConnection` 被转发（forwarded）到对应 Actor 的 Actor Channel
- 这个 Actor Channel 会序列化 RPC 标识符和参数到一个带有这个 Actor Channel 的 ID 的束中
- Actor Channel 向 `NetConnection` 请求发送束
- 接着，`NetConnection` 将会组合这些数据到一个包中，被发送到服务器
- 在服务器中，包会被 `NetDriver` 接收
- `NetDriver` 讲话检查包的源地址，并将包交给适当的 `NetConnection`
- 对应的 `NetConnection` 会把包拆称束
- `NetConnection` 会使用束包含的 Actor Channel ID 来把束发送到对应的 Actor Channel、
- Actor Channel 会拆开束，查看其包含的 RPC 数据，然后使用 RPC ID 和被序列化的参数去调用 Actor 的函数

### 可靠性和重新传输（Reliability and Retransmission）

UE 网络通常假设底层网络协议无法保证可靠性。所以它为包（Packet）和束（Bunch）实现了自己的可靠性和重新传输。

当一个 `NetConnection` 被建立，它会为它的包和束们建立一个序列号（Sequence Number），即包号（pack number）和束号（bunch number）。序列号既可能是固定的，也可能是随机的（随机的序列号由服务器发送）。

对于每个 `NetConnection`，每个包会包含一个包号，在每次发送包时都会增加。包号永远不会重复。

对于每个 `Channel`，每个可靠（reliable）束都会有一个束号，在每个可靠束发送时，束号会增长。与包不同，可靠束可能会被重新传输，所以我们可能会发送相同束号的束。

注意，包号和束号都只是序列号，它们没有本质区别。这里区分仅为了更清晰的理解。

### 接收侧检测丢包（Detecting Incoming Dropped Packets）

因为指定了包号，我们可以很轻松的检测什么时候发生了丢包。只要比对上一次成功接收的包和这一次成功接收的包的包号是否是连续的，就能判断是否丢包了。两次包号的差值 - 1 就是丢包的数量。

如果发现两次包号间的差值是 0 或负数，因为引擎不会重用序列号，说明我们只可能接收了更早发出的包还没接收到的包，或者外部服务正在尝试重新给我们发送数据。不管是哪种情况，引擎通常都会忽略这些丢失和不合法的包，也不会为它们发送 ACK（确认应答）。

如果在同一帧收到了顺序不对的包，我们有办法将它们修复（如果这项功能被启用）：如果我们收到了一个与上一次包的差值大于 1 的包，说明中间有包没有到达。我们把当前接收到的包缓存到一个队列中，当下一次我们接收到一个正确顺序的包，再检查和之前接收到的包的顺序关系是否正确，如果正确，我们执行它们；如果还不正确，我们继续接收包。

当我们读取完所有的目前有效的包后，我们会清理这个队列，执行里面的所有残留包。同时，所有丢失的都会被认为被丢弃。

接收侧每成功接收一个包都会发送它的包号作为 ACK（确认应答）。

### 发出侧检测丢包（Detecting Outgoing Dropped Packets）

上面提到了，接收侧每成功接收一个包都会发送它的包号作为 ACK。这些 ACK 会包含顺序的包号。
与接收者追踪包号类似，发送者也会追踪最大的 ACK 的包号。当 ACKs 被处理时，任何小于我们最后接收到的 ACK 的包号会被忽略，同时，任何包号间的间隙都被认为是 Not Acknowledged （NAKed，未应答的）。

发送者负责处理这些 ACKs 和 NAKs，同时用新的包号重新发送丢失的数据，新的数据将会被添加到新的发送包中。

### 重新发送丢失数据（Resending Missing Data）

上文提到了，包本身不包含有用的数据，而是束承载着它们。束可以被标记为可靠（Reliable）或者不可靠的（Unreliable）。

对于不可靠的束，引擎不会尝试重新发送丢失的数据。因此，如果一个束被标记为不可靠的，引擎没有它必须可以继续运行，或者使用外部的重试机制，亦或是冗余地发送该数据。

下面的所有内容会用来介绍可靠束。

无论何时，一个可靠束被发送，它将被加入到一个非 ACKed 可靠束列表。如果我们接收了一个包含了可靠束的 NAK 的包，引擎将会重新传输一个一样的束的拷贝。注意，因为束可能可以被分成子束，丢掉一个子束也会导致让整个束重新传输。

当所有包含束这个可靠束的包 ACK 了，我们才把这个可靠束从上述的列表移除。

与包相似，我们比对最后成功接收的束的序列号，如果发现差值为负数，我们认为我们丢失了一个束。与包的是处理不同的是，我们不会丢掉这个数据。相反地，我们会将这个束加入到队列，并暂停处理任何束，不管是可靠还是不可靠的。

直到我们接收到丢失的束之前，处理都不会再继续进行。接收到丢失的束之后，我们后处理这个束和刚刚队列里的束们。

任何新的，在等待丢失束期间被接收到的束，不会被立刻处理，而是会被加入队列。

# 重要概念

除了第一章中的重要概念外，本章会介绍一些上文未出现的重要概念，这些概念在源码中经常见到。

**网络指标（Network Metric）** 如其名称，是供开发者评估和调试网络性能的指标。在 UE 网络同步部分的源码中，会有许多更新网络指标的代码，其往往与 `Metric` 字眼相关。开发者也可以自行注册网络指标，以及监听网络指标更新事件。具体可见 `GetMetrics()` 与 `NetworkMetricsDatabase.h`。

# 源代码梳理 - Tick 阶段

`UNetDriver` 是 UE 网络处理的核心，在 `UNetDriver` 中有一些重要的字段：

- `ServerConnection` 与服务端的连接，为 `nullptr` 说明当前运行在服务器上
- `ClientConnections` 与客户端的连接列表
- `MappedClientConnections` IP 到连接的表

`UNetDriver` 的工作主要发生在世界的 Tick 中，且分在不同的阶段完成。下面的是世界的 Tick 函数与事件的触发顺序，`On` 开头的代表事件。

1. `World::Tick`
2. `OnTickDispatch`
3. `PrePhysics`
4. `ActorTick`
5. `OnPostTickDispatch`
6. `OnPreTickFlush`
7. `OnTickFlush`
8. `OnPostTickFlush`

网络相关的内容主要发生在这四个事件中：

1. `OnTickDispatch`
2. `OnPostTickDispatch`
3. `OnTickFlush`
4. `OnPostTickFlush`

下面是 `UNetDriver` 注册这四个事件的代码：

```cpp
// NetDriver.cpp
void UNetDriver::RegisterTickEvents(class UWorld* InWorld)
{
	if (InWorld)
	{
		TickDispatchDelegateHandle = InWorld->OnTickDispatch().AddUObject(this, &UNetDriver::InternalTickDispatch);
		PostTickDispatchDelegateHandle	= InWorld->OnPostTickDispatch().AddUObject(this, &UNetDriver::PostTickDispatch);
		TickFlushDelegateHandle = InWorld->OnTickFlush().AddUObject(this, &UNetDriver::InternalTickFlush);
		PostTickFlushDelegateHandle = InWorld->OnPostTickFlush().AddUObject(this, &UNetDriver::PostTickFlush);
	}
}

```

| 调用阶段 | 主要函数（`UNetDriver`） | 备注 |
| --- | --- | --- |
| `OnTickDispatch` | `TickDispatch` | 通过 `InternalTickDispatch` 调用，Internal 中间层目的是防止非法递归调用 |
| `OnPostTickDispatch` | `PostTickDispatch` |  |
| `OnTickFlush` | `TickFlush` | 通过 `InternalTickFlush` 调用，Internal 作用如上<br> |
| `OnPostTickFlush` | `PostTickFlush` |  |
| 下文会按顺序详细介绍上述四个函数。 |  |  |

## `TickDispatch`

这个函数主要完成接收数据的工作。

```cpp
// NetDriver.cpp/UNetDriver::TickDispatch

void UNetDriver::TickDispatch( float DeltaTime )
{
	SendCycles=0;

	// Manage realtime values
	{
		const double CurrentRealtime = FPlatformTime::Seconds();
		LastTickDispatchRealtime = CurrentRealtime;

		// Check to see if too much time is passing between ticks
		// Setting this to somewhat large value for now, but small enough to catch blocking calls that are causing timeouts
		constexpr float TickLogThreshold = 5.0f;

		const float DeltaRealtime = CurrentRealtime - LastTickDispatchRealtime;
		bDidHitchLastFrame = (DeltaTime > TickLogThreshold || DeltaRealtime > TickLogThreshold);

		if (bDidHitchLastFrame)
		{
			UE_LOG( LogNet, Log, TEXT( "UNetDriver::TickDispatch: Very long time between ticks. DeltaTime: %2.2f, Realtime: %2.2f. %s" ), DeltaTime, DeltaRealtime, *GetName() );
		}
	}

	// Get new time.
	ElapsedTime += DeltaTime;

	IncomingBunchProcessingElapsedFrameTimeMS = 0.0f;
```

上面这段代码主要更新现实时间相关的内容，并在帧时间过长时打印日志。

```cpp
	// Checks for standby cheats if enabled
	UpdateStandbyCheatStatus();
	ResetNetworkMetrics();
```

- `ResetNetworkMetrics` 负责重置网络指标

接下来是服务器侧的逻辑，在服务器逻辑中，

```cpp
	if (ServerConnection == nullptr)
	{
		// Delete any straggler connections
		{
			QUICK_SCOPE_CYCLE_COUNTER(UNetDriver_TickDispatch_CheckClientConnectionCleanup)

			for (int32 ConnIdx=ClientConnections.Num()-1; ConnIdx>=0; ConnIdx--)
			{
				UNetConnection* CurConn = ClientConnections[ConnIdx];

				if (IsValid(CurConn))
				{
					if (CurConn->GetConnectionState() == USOCK_Closed)
					{
						CurConn->CleanUp();
					}
					else
					{
						CurConn->PreTickDispatch();
					}
				}
			}
		}
```

首先会逆序遍历 `ClientConnections`，检查连接的合法性，如果不合法，连接被安全清除；如果合法，则调用对应连接的 `PreTickDispatch()`。被 CleanUp 的连接并不会立刻被销毁，而是会被存储在 `RecentlyDisconnectedClients` 中，被追踪一段时间，当超过预设的追踪时间后，才会最终从 `RecentlyDisconnectedClients` 删除，意味着被销毁。上述逻辑的代码如下：

```cpp
		// Clean up recently disconnected client tracking
		if (RecentlyDisconnectedClients.Num() > 0)
		{
			int32 NumToRemove = 0;

			for (const FDisconnectedClient& CurElement : RecentlyDisconnectedClients)
			{
				if ((LastTickDispatchRealtime - CurElement.DisconnectTime) >= RecentlyDisconnectedTrackingTime)
				{
					verify(MappedClientConnections.Remove(CurElement.Address) == 1);

					NumToRemove++;
				}
				else
				{
					break;
				}
			}

			if (NumToRemove > 0)
			{
				RecentlyDisconnectedClients.RemoveAt(0, NumToRemove);
			}
		}
	}
```

说完了服务器侧，下面的代码与客户端侧有关

```cpp
	else if (IsValid(ServerConnection))
	{
		ServerConnection->PreTickDispatch();
	}

#if RPC_CSV_TRACKER
	GReceiveRPCTimingEnabled = (NetDriverName == NAME_GameNetDriver && ShouldEnableScopeSecondsTimers()) && (ServerConnection==nullptr);
#endif
}
```

客户端的行为则是执行 `PreTickDispatch`，这个函数的具体作用是 DoS 防护与错误恢复的 Tick 行为，这里不详细介绍。

在 `UNetDriver` 的派生类 `UIpNetDriver` 中，才包含接收包的逻辑：

```cpp
void UIpNetDriver::TickDispatch(float DeltaTime)
{
	LLM_SCOPE_BYTAG(NetDriver);

	Super::TickDispatch( DeltaTime );

	const bool bUsingReceiveThread = SocketReceiveThreadRunnable.IsValid();

	if (bUsingReceiveThread)
	{
		SocketReceiveThreadRunnable->PumpOwnerEventQueue();
	}
```

若启用了接收线程，则处理接收线程的数据。

```cpp
#if !UE_BUILD_SHIPPING
	...
#endif
	...
	// Process all incoming packets
	for (FPacketIterator It(this); It; ++It)
	{
```

迭代器遍历所有到达的包，下面开始处理这些包。

```cpp
		FReceivedPacketView ReceivedPacket;
		FInPacketTraits& ReceivedTraits = ReceivedPacket.Traits;
		bool bOk = It.GetCurrentPacket(ReceivedPacket);
		const TSharedRef<const FInternetAddr> FromAddr = ReceivedPacket.Address.ToSharedRef();
		UNetConnection* Connection = nullptr;
		UIpConnection* const MyServerConnection = GetServerConnection();

		if (bOk)
		{
			// Immediately stop processing (continuing to next receive), for empty packets (usually a DDoS)
			if (ReceivedPacket.DataView.NumBits() == 0)
			{
				DDoS.IncBadPacketCounter();
				continue;
			}

			FPacketAudit::NotifyLowLevelReceive((uint8*)ReceivedPacket.DataView.GetData(), ReceivedPacket.DataView.NumBytes());
		}
```

- `bOk` 代表当前数据包是否可被处理

如果接收到数据包大小为 0 的疑似错误包或攻击包的无效包，统计并直接跳过；如果不是，则调用 `NotifyLowLevelReceive` 处理来包。

接着是不 `bOk` 的情况，会进行错误处理：

```cpp
		else
		{
			if (IsRecvFailBlocking(ReceivedPacket.Error))
			{
				break;
			}
			else if (ReceivedPacket.Error != SE_ECONNRESET && ReceivedPacket.Error != SE_UDP_ERR_PORT_UNREACH)
			{
				//...log
				continue;
			}
		}
```

如果遇到阻塞性的错误，则直接跳出循环。如果包体过大，可能为恶意攻击，客户端可能被重置等问题。

```cpp
		if (MyServerConnection)
		{
			if (MyServerConnection->RemoteAddr->CompareEndpoints(*FromAddr))
			{
				Connection = MyServerConnection;
			}
			else //...log
		}

		if (Connection == nullptr) { ... }
		if( bOk == false ) { if( Connection ) { ... } }
		else {
			bool bIgnorePacket = false;
			// If we didn't find a client connection, maybe create a new one.
			if (Connection == nullptr) { ... }
		}
	}

	if (NewIPHashes.Num() > 0) TickNewIPTracking(DeltaTime);
	DDoS.PostFrameReceive();
}
```

后半部分则主要与处理连接有关。包括处理新的连接请求，将接收到的数据分发到连接等。

## `PostTickDispatch`

`PostTickDispatch()` 负责结束任务分发。

```cpp
void UNetDriver::PostTickDispatch()
{
	// Flush out of order packet caches for connections that did not receive the missing packets during TickDispatch
	if (ServerConnection != nullptr)
	{
		if (IsValid(ServerConnection))
		{
			ServerConnection->PostTickDispatch();
		}
	}

	TArray<UNetConnection*> ClientConnCopy = ClientConnections;
	for (UNetConnection* CurConn : ClientConnCopy)
	{
		if (IsValid(CurConn))
		{
			CurConn->PostTickDispatch();
		}
	}
```

函数首先会执行每个连接的 `PostTickDispatch`。如上面的代码所示：若为客户端，调用客户端连接的函数；若为服务器，安全地遍历客户端连接列表并调用函数。在 `UNetConnection::PostTickDispatch` 中，连接会刷新缓存包队列，这个队列用于等待丢失的包到来之前缓存到达的包。同时也会调用 `RPCDoS` 的 `PostTickDispatch()`。

```cpp
#if UE_WITH_IRIS
	PostDispatchSendUpdate();
#endif

	if (ReplicationDriver)
	{
		ReplicationDriver->PostTickDispatch();
	}
```

- `ReplicationDriver->PostTickDispatch()` 是一个空实现，暂时没有任何行为

```cpp
	if (GReceiveRPCTimingEnabled)
	{
		GRPCCSVTracker.EndTickDispatch();
		GReceiveRPCTimingEnabled = false;
	}

	if (bPendingDestruction)
	{
		if (World)
		{
			GEngine->DestroyNamedNetDriver(World, NetDriverName);
		}
		else
		{
			UE_LOG(LogNet, Error, TEXT("NetDriver %s pending destruction without valid world."), *NetDriverName.ToString());
		}
		bPendingDestruction = false;
	}
}
```

## `TickFlush`

`NetDriver` 的主要工作发生在 `TickFlush` 中。

```cpp
void UNetDriver::TickFlush(float DeltaSeconds)
{
	// ...trace

	bool bEnableTimer = (NetDriverName == NAME_GameNetDriver) && ShouldEnableScopeSecondsTimers();
	if (bEnableTimer)
	{
		GTickFlushGameDriverTimeSeconds = 0.0;
	}
	FSimpleScopeSecondsCounter ScopedTimer(GTickFlushGameDriverTimeSeconds, bEnableTimer);
```

如上面的代码所示，引擎首先会初始化计时器。接着开始执行 Actor 复制：

```cpp
	if (IsServer() && ClientConnections.Num() > 0 && !bSkipServerReplicateActors)
	{
		// Update all clients.
#if WITH_SERVER_CODE
		CSV_SCOPED_TIMING_STAT_EXCLUSIVE(ServerReplicateActors);

#if UE_WITH_IRIS
		...
#endif // UE_WITH_IRIS
		{
			ServerReplicateActors(DeltaSeconds);
		}
#endif // WITH_SERVER_CODE
	}
#if UE_WITH_IRIS
	...
#endif // UE_WITH_IRIS
```

上述代码对应着服务器的复制（Replication） 机制，由于在 UE 5 中新出了 Iris 复制机制，于此出现了许多宏来区分传统的复制与 Iris 复制。Iris 机制会在后面一章中具体解释，这里为了便于阅读暂时省略。传统的复制机制发生在 `ServerReplicateActors()` 函数中。

---

### `ServerReplicateActors()`

复制所有与连接相关的 Actors。首先会构建一个相关的 Actor 列表，接着，尝试去复制每一个 Actor 到与他们相关的连接。直到一个连接饱和。

```cpp
int32 UNetDriver::ServerReplicateActors(float DeltaSeconds)
{
	SCOPE_CYCLE_COUNTER(STAT_NetServerRepActorsTime);

#if WITH_SERVER_CODE
	if ( ClientConnections.Num() == 0 ) { return 0; }
```

`WITH_SERVER_CODE` 直接连着函数的结尾，意味着这个函数只在服务器上运行，如果在客户端执行，会直接返回 0。上面的代码还判断了，在没有服务端连接时，引擎直接跳过同步。

```cpp
	GetMetrics()->SetInt(UE::Net::Metric::NumReplicatedActors,0 );
	GetMetrics()->SetInt(UE::Net::Metric::NumReplicatedActorBytes, 0);
```

初始化统计指标。

```cpp
#if CSV_PROFILER_STATS
	FScopedNetDriverStats NetDriverStats(this);
	GNumClientConnections = ClientConnections.Num();
#endif

	if (ReplicationDriver)
	{
		return ReplicationDriver->ServerReplicateActors(DeltaSeconds);
	}
```

引擎会再检查引擎是否支持 Iris 的 `ReplicationDriver`，如果支持，则使用 Iris。如果不能，执行接下来的传统复制逻辑的代码。

```cpp
	check( World );

	// Bump the ReplicationFrame value to invalidate any properties marked as "unchanged" for this frame.
	ReplicationFrame++;

	int32 Updated = 0;

	const int32 NumClientsToTick = ServerReplicateActors_PrepConnections( DeltaSeconds );

	if ( NumClientsToTick == 0 ) { return 0; } // No connections are ready this frame
```

- `ServerReplicateActors_PrepConnections` 有两个主要功能，一是确定本帧要处理的连接的数量，也就是 `NumClientsToTick`；二是轮转连接处理顺序，防止有连接不被处理

如果没有待处理的连接，则直接结束。

```cpp
	AWorldSettings* WorldSettings = World->GetWorldSettings();

	bool bCPUSaturated		= false;
	float ServerTickTime	= GEngine->GetMaxTickRate( DeltaSeconds );
	if ( ServerTickTime == 0.f )
	{
		ServerTickTime = DeltaSeconds;
	}
	else
	{
		ServerTickTime	= 1.f/ServerTickTime;
		bCPUSaturated	= DeltaSeconds > 1.2f * ServerTickTime;
	}

	TArray<FNetworkObjectInfo*> ConsiderList;
	ConsiderList.Reserve( GetNetworkObjectList().GetActiveObjects().Num() );

	// Build the consider list (actors that are ready to replicate)
	ServerReplicateActors_BuildConsiderList( ConsiderList, ServerTickTime );

	TSet<UNetConnection*> ConnectionsToClose;

	FMemMark Mark( FMemStack::Get() );

	if (OnPreConsiderListUpdateOverride.IsBound())
	{
		OnPreConsiderListUpdateOverride.Execute({ DeltaSeconds, nullptr, bCPUSaturated }, Updated, ConsiderList);
	}
```

上面这段代码功能主要是构建待同步列表，筛选掉不需要被同步的 `Actor`

- `ServerTickTime` 代表服务器 Tick 的间隔，如果设置了最大 Tick 频率（`ServerTickRate`），间隔就是最大 Tick 频率的倒数，用于待同步列表的构建
- `ServerReplicateActors_BuildConsiderList` 构建待同步列表，这会剔除掉睡眠的 Actor

再确定好了要被同步的 Actor 后，开始正式的同步逻辑。首先会遍历每个客户端连接，

```cpp
	for ( int32 i=0; i < ClientConnections.Num(); i++ )
	{
		UNetConnection* Connection = ClientConnections[i];
		check(Connection);

		// net.DormancyValidate can be set to 2 to validate all dormant actors against last known state before going dormant
		if ( GNetDormancyValidate == 2 )
		{
			...
		}
```

首先会对休眠状态进行验证，主要是对休眠对象从睡眠状态恢复到活跃时尝试更新。

```cpp
		// if this client shouldn't be ticked this frame
		if (i >= NumClientsToTick)
		{
// UE_LOG(LogNet, Log, TEXT("skipping update to %s"),*Connection->GetName());
// then mark each considered actor as bPendingNetUpdate so that they will be considered again the next frame when the connection is actually ticked
			for (int32 ConsiderIdx = 0; ConsiderIdx < ConsiderList.Num(); ConsiderIdx++)
			{
				AActor *Actor = ConsiderList[ConsiderIdx]->Actor;
// if the actor hasn't already been flagged by another connection,
				if (Actor != NULL && !ConsiderList[ConsiderIdx]->bPendingNetUpdate)
				{
// find the channel
					UActorChannel *Channel = Connection->FindActorChannelRef(ConsiderList[ConsiderIdx]->WeakActor);
// and if the channel last update time doesn't match the last net update time for the actor
					if (Channel != NULL && Channel->LastUpdateTime < ConsiderList[ConsiderIdx]->LastNetUpdateTimestamp)
					{
//UE_LOG(LogNet, Log, TEXT("flagging %s for a future update"),*Actor->GetName());
// flag it for a pending update
						ConsiderList[ConsiderIdx]->bPendingNetUpdate = true;
					}
				}
			}
// clear the time sensitive flag to avoid sending an extra packet to this connection
			Connection->TimeSensitive = false;
		}
```

接着，如果一个客户端不需要在这帧被同步，并且它的 `Channel` 的上次更新时间比连接的更新时间还要早，那么会标记连接的 `bPendingNetUpdate` 为真，来让其下次被加入同步列表。

接下来的代码就是处理同步的部分了。

```cpp
		else if (Connection->ViewTarget)
		{
			const int32 LocalNumSaturated = GNumSaturatedConnections;

// Make a list of viewers this connection should consider (this connection and children of this connection)
			TArray<FNetViewer>& ConnectionViewers = WorldSettings->ReplicationViewers;

			ConnectionViewers.Reset();
			new( ConnectionViewers )FNetViewer( Connection, DeltaSeconds );
			for ( int32 ViewerIndex = 0; ViewerIndex < Connection->Children.Num(); ViewerIndex++ )
			{
				if ( Connection->Children[ViewerIndex]->ViewTarget != NULL )
				{
					new( ConnectionViewers )FNetViewer( Connection->Children[ViewerIndex], DeltaSeconds );
				}
			}
```

这段代码的逻辑是获取每个连接相关的其它观测者连接

- `ViewTarget` 是一个 `AActor` 指针，通常是当前的 Actor 的控制者，例如 `PlayerController`，用于获取当前 `Actor` 的物理位置
- `Viewer`：本质是一个`FNetViewer`结构体，它包含了网络连接指针、视角、位置坐标等，用于快速判断一个物体是否在另一个物体的视野范围内，这些数据通常是关联于某个 Actor 的
- `new (ConnectionViewers) FNetViewer(Connection, DeltaSeconds);` 语法在 `ConnectionViewers` 上分配内存给一个新的 `FNetViewer`

```cpp
			// send ClientAdjustment if necessary
			// we do this here so that we send a maximum of one per packet to that client; there is no value in stacking additional corrections
			if ( Connection->PlayerController )
			{
				Connection->PlayerController->SendClientAdjustment();
			}

			for ( int32 ChildIdx = 0; ChildIdx < Connection->Children.Num(); ChildIdx++ )
			{
				if ( Connection->Children[ChildIdx]->PlayerController != NULL )
				{
					Connection->Children[ChildIdx]->PlayerController->SendClientAdjustment();
				}
			}
```

遍历所有连接和子连接的 `PlayerController` 并尝试更新。

```cpp
			FMemMark RelevantActorMark(FMemStack::Get());

			const bool bProcessConsiderListIsBound = OnProcessConsiderListOverride.IsBound();

			if (bProcessConsiderListIsBound)
			{
				OnProcessConsiderListOverride.Execute( { DeltaSeconds, Connection, bCPUSaturated }, Updated, ConsiderList );
			}
```

- `FMemMark` 是内存栈的管理标记工具，它通过移动栈顶指针来分配内存，并可以快速释放。具体来说，它会记录当前的栈顶指针，调用 `Pop` 时，会将栈顶设置回开始记录的栈顶，来实现快速“释放”内存的效果。
- `OnProcessConsiderListOverride` 是自定义同步逻辑相关的事件，可以允许开发者自定义 `ConsiderList`

```cpp
			if (!bProcessConsiderListIsBound)
			{
				FActorPriority* PriorityList = NULL;
				FActorPriority** PriorityActors = NULL;

				// Get a sorted list of actors for this connection
				const int32 FinalSortedCount = ServerReplicateActors_PrioritizeActors(Connection, ConnectionViewers, ConsiderList, bCPUSaturated, PriorityList, PriorityActors);
```

这里执行的是默认的同步逻辑。首先引擎会为这个连接生成一个排序的 Actors 列表，这个列表的顺序是按照 Actor 的优先级排序的。

```cpp
				// Process the sorted list of actors for this connection
				TInterval<int32> ActorsIndexRange(0, FinalSortedCount);
				const int32 LastProcessedActor = ServerReplicateActors_ProcessPrioritizedActorsRange(Connection, ConnectionViewers, PriorityActors, ActorsIndexRange, Updated);

				ServerReplicateActors_MarkRelevantActors(Connection, ConnectionViewers, LastProcessedActor, FinalSortedCount, PriorityActors);
			}
```

接着处理高优先级的 Actor

- `ServerReplicateActors_ProcessPrioritizedActorsRange` 会处理高优先级的 Actors 并返回没有处理完的 Actors 的数量
- `ServerReplicateActors_MarkRelevantActors` 会利用剩下的 Actor 数量，将相关的 Actor 标记为相关，并留到下帧优先处理

---

`ServerReplicateActors_ProcessPrioritizedActorsRange` 会遍历 `ActorsIndexRange` 内的 `PriorityActors`，然后每个元素的 `Channel` 和其 `Actor`，并判断其它观测者与当前连接的“最近相关性”，具体条件有两个：

- 在每 `1.0 + 0.5R` 秒内可见的 `Actor`
- 使用 `IsActorRelevantToConnection` 函数的返回结果

经过一系列复杂的相关性判断，最终函数会获取或创建当前连接到相关的 Actor 的 Channel，并通过 `UNetChannel::ReplicateActor()` 来执行对 Actor 的复制。

---

```cpp
			RelevantActorMark.Pop();
			ConnectionViewers.Reset();
			Connection->LastProcessedFrame = ReplicationFrame;
			const bool bWasSaturated = GNumSaturatedConnections > LocalNumSaturated;
			Connection->TrackReplicationForAnalytics(bWasSaturated);
		}
```

接着，上面的代码进行清除和收尾工作，包括释放临时高优先级内存、清空 `ConnectionViewers`、更新 `LastProcessedFrame` 到当前帧、进行连接饱和测试（如果处理后包和数增加，说明在这帧出现了带宽瓶颈）。

```cpp
		if (Connection->GetPendingCloseDueToReplicationFailure())
		{
			ConnectionsToClose.Add(Connection);
		}
	}
```

检查连接是否因为复制行为需要被关闭。

```cpp
	if (OnPostConsiderListUpdateOverride.IsBound())
	{
		OnPostConsiderListUpdateOverride.ExecuteIfBound( { DeltaSeconds, nullptr, bCPUSaturated }, Updated, ConsiderList );
	}
```

广播后处理列表的自定义行为事件 `OnPostConsiderListUpdateOverride`。

```cpp
	// shuffle the list of connections if not all connections were ticked
	if (NumClientsToTick < ClientConnections.Num())
	{
		int32 NumConnectionsToMove = NumClientsToTick;
		while (NumConnectionsToMove > 0)
		{
			// move all the ticked connections to the end of the list so that the other connections are considered first for the next frame
			UNetConnection *Connection = ClientConnections[0];
			ClientConnections.RemoveAt(0,1);
			ClientConnections.Add(Connection);
			NumConnectionsToMove--;
		}
	}
	Mark.Pop();
```

这段代码的作用是轮转 `ClientConnections` 列表，`ClientConnections` 前面被处理了的连接轮转到最后，下帧优先处理没有轮到的连接。

```cpp
#if NET_DEBUG_RELEVANT_ACTORS
	//...debug
#endif // NET_DEBUG_RELEVANT_ACTORS

	for (UNetConnection* ConnectionToClose : ConnectionsToClose)
	{
		ConnectionToClose->Close();
	}

	return Updated;
#else
	return 0;
#endif // WITH_SERVER_CODE
}
```

最后，引擎会关闭上面收集的需要关闭的连接。

---

回到 `UNetDriver::TickFlush()`，接下来的代码跟连接的更新有关。

```cpp
	// Reset queued bunch amortization timer
	ProcessQueuedBunchesCurrentFrameMilliseconds = 0.0f;

	// Poll all sockets.
	if( ServerConnection )
	{
		// Queue client voice packets in the server's voice channel
		ProcessLocalClientPackets();
		ServerConnection->Tick(DeltaSeconds);
	}
	else
	{
		// Queue up any voice packets the server has locally
		ProcessLocalServerPackets();
	}
	{
		QUICK_SCOPE_CYCLE_COUNTER(STAT_NetDriver_TickClientConnections)

		for (UNetConnection* Connection : ClientConnections)
		{
			Connection->Tick(DeltaSeconds);
		}
	}
```

这部分主要处理本地的语音包。同时不管是服务端还是服务器，都会执行每个 `UNetConnection` 的 `Tick` 函数，下文会具体介绍。

```cpp
	if (ConnectionlessHandler.IsValid())
	{
		ConnectionlessHandler->Tick(DeltaSeconds);

		FlushHandler();
	}
```

这段代码强制发送所有 `ConnectionlessHandler` 里的包。

- `ConnectionlessHandler` 是一个 `TUniquePtr<PacketHandler>`，在 `NetDriver::InitConnectionlessHandler()` 被初始化，其 `Tick` 函数会安全地发送其队列里的包
- `FlushHandler` 会强制发送所有缓存的无建立连接的包

```cpp
	//... debug
	//... Iris
	UpdateNetworkStats();
	// Send the current values of metrics to all of the metrics listeners.
	GetMetrics()->ProcessListeners();
```

- `UpdateNetworkStats()` 主要作用是更新基础统计，如带宽利用率、丢包率等
- `GetMetrics()` 会返回 `UNetworkMetricsDatabase` 的智能指针，这个类管理着所有网络指标监听器（监听者模式），指标变化时会调用监听器，用于执行可视化、性能检测等行为，同时允许开发者使用；在 `UNetworkMetricsDatabase.h` 可以看到详细的文档注释（包含使用案例）

```cpp
	// Update the lag state
	UpdateNetworkLagState();
}
```

最后，函数会更新延迟状态机。这里不深入介绍。

---

### `UNetConnection::Tick`

`UNetConnection::Tick` 的逻辑十分长和复杂，这里不贴其代码，而是系统性地介绍其功能，并列举部分代码。

首先，在最开始，为了避免过于频繁地更新网络，导致带宽过载，引擎可能会直接跳过这帧这个连接的 Tick：

```cpp
if (!IsInternalAck() && MaxNetTickRateFloat < EngineTickRate && DesiredTickRate > 0.0f)
{
    const float MinNetFrameTime = 1.0f/DesiredTickRate;
    if (FrameTime < MinNetFrameTime)
    {
	    return;
    }
}
```

具体逻辑就是当前帧时间小于设定的最小帧时间，则跳过这次 Tick。

接着，处理内部 ACK 模式。内部 ACK 模式是在本地模拟远程发包收包，其发生在回放模式、本地游戏等场景。内部 ACK 模式下，不需要对数据进行网络序列化和反序列化，也可以减少回放时的延迟。

```cpp
if (IsInternalAck())
{
	...
}
```

接着，引擎会更新网络统计，包括带宽计算、更新进出的丢包率等。

```cpp
if ( CurrentRealtimeSeconds - StatUpdateTime > StatPeriod )
{
	...
	// Add TotalPacketsLost to total since InTotalPackets only counts ACK packets
	InPacketsLossPercentage.UpdateLoss(InPacketsLost, InTotalPackets + InTotalPacketsLost, StatPeriodCount);
	// Using OutTotalNotifiedPackets so we do not count packets that are still in transit.
	OutPacketsLossPercentage.UpdateLoss(OutPacketsLost, OutTotalNotifiedPackets, StatPeriodCount);
	...
}
```

然后会进行连接异常处理，连接异常会在上面的代码中被标记。

```cpp
if (bConnectionPendingCloseDueToSocketSendFailure)
{
    Close(ENetCloseResult::SocketSendFailure);
    bConnectionPendingCloseDueToSocketSendFailure = false;
    // early out
    return;
}

```

接着会进行超时检测。

```cpp
const float Timeout = GetTimeoutValue();

const bool bReceiveTimedOut = (CurrentRealtimeSeconds - LastReceiveRealtime) > Timeout;
const bool bGracefulCloseTimedOut = (GetConnectionState() == USOCK_Closing) && (DriverElapsedTime > bGracefulCloseTimedOut);

if (bReceiveTimedOut || bGracefulCloseTimedOut)
{
	...
}
```

超时分为数据接收超时，和优雅关闭超时。

- 数据接收超时（`bReceiveTimedOut`）的触发条件是当前时间与最后一次收到有效数据包的时间差超过阈值 `Timeout`，除了会输出日志外，还会关闭连接和尝试自动重连
- 优雅关闭超时（`bGracefulCloseTimedOut`）的触发条件是当前连接处于关闭流程中（`USOCK_Closing`）并且超过了优雅超时时限（`bGracefulCloseTimedOut`）。这是由正常的关闭流程，不立刻关闭时是为了等待未完成的可靠数据传输等任务完成；如果优雅关闭超时了，则会强制关闭

不管是哪种超时，都会触发 `UNetConnection::HandleConnectionTimeout()` 函数，这个函数的功能是广播错误，并关闭当前所属连接。

接着会更新 Channels：

```cpp
if (CVarTickAllOpenChannels.GetValueOnAnyThread() == 0) {
	for( int32 i=ChannelsToTick.Num()-1; i>=0; i-- ) {
		ChannelsToTick[i]->Tick();
		if (ChannelsToTick[i]->CanStopTicking())
			ChannelsToTick.RemoveAt(i);
	}
}
else {
	for (int32 i = OpenChannels.Num() - 1; i >= 0; i--) {
		if (OpenChannels[i]) OpenChannels[i]->Tick();
		else UE_LOG(LogNet, Warning, TEXT("UNetConnection::Tick: null channel in OpenChannels array. %s"), *Describe());
	}
}
```

- `CVarTickAllOpenChannels` 配置是否 Tick 所有 Channels

因为在大型多人游戏中，Channels 的数量往往由很多（上百），因此选择性地 Tick 需要的 Channels 对于性能由很大帮助。

```cpp
for ( auto ProcessingActorMapIter = KeepProcessingActorChannelBunchesMap.CreateIterator(); ProcessingActorMapIter; ++ProcessingActorMapIter ) {
	...
}
```

接着，引擎会处理要被关闭的 Actor 的 Channels 的残留数据。

- `KeepProcessingActorChannelBunchesMap` 是想要被完全关闭的 Channels 的表，这些 Channel 虽然被请求了完全关闭，但是还有数据未处理完成，因此需要在这里继续处理，直到完成后才关闭

这些残留数据包括未 ACK 的已发送的可靠数据、跨帧的 Partial Bunches、延迟处理的同步属性等。

然后，引擎会进行网络冲刷，发送数据：

```cpp
	if ( TimeSensitive || (Driver->GetElapsedTime() - LastSendTime) > Driver->KeepAliveTime)
	{
		bool bHandlerHandshakeComplete = !Handler.IsValid() || Handler->IsFullyInitialized();

		// Delay any packet sends on the server, until we've verified that a packet has been received from the client.
		if (bHandlerHandshakeComplete && HasReceivedClientPacket())
		{
			FlushNet();
		}
	}
```

`FlushNet` 会调用底层的 `LowLevelSend` 来发送数据。

```cpp
// Resend any queued up raw packets (these come from the reliability handler)
BufferedPacket* ResendPacket = Handler->GetQueuedRawPacket();

if (ResendPacket && Driver->IsNetResourceValid())
{
	Handler->SetRawSend(true);

	while (ResendPacket != nullptr)
	{
		LowLevelSend(ResendPacket->Data, ResendPacket->CountBits, ResendPacket->Traits);
		ResendPacket = Handler->GetQueuedRawPacket();
	}

	Handler->SetRawSend(false);
}
...
```

接近结束的位置，函数会尝试重发数据。它会获取重发数据队列并在 while 循环中逐包发送。

## `PostTickFlush`

`PostTickFlush` 主要负责清理工作，包括清理语音包以及销毁 `NetDriver` 本身（如果 `NetDriver` 待销毁）。

```cpp
void UNetDriver::PostTickFlush()
{
#if UE_WITH_IRIS
	...
#endif // UE_WITH_IRIS

	if (World && !bSkipClearVoicePackets)
	{
		UOnlineEngineInterface::Get()->ClearVoicePackets(World);
	}
```

清理语音包（上）。

```cpp
	if (bPendingDestruction)
	{
		if (World)
		{
			GEngine->DestroyNamedNetDriver(World, NetDriverName);
		}
		else
		{
			UE_LOG(LogNet, Error, TEXT("NetDriver %s pending destruction without valid world."), *NetDriverName.ToString());
		}
		bPendingDestruction = false;
	}
}
```

检查和销毁 `NetDriver`（上）。

## 小结

至此，网络系统在 Tick 阶段的通用流程已经全部完成。之所以说是通用逻辑，是因为 `UIpNetDriver` 等其它 `UNetDriver` 派生类共用了父类的行为同时实现了自己的行为，下文会简单介绍 `IpNetDriver` 以及它是如何处理握手的。

# 握手

我们在回顾一下第一章中介绍过的握手的流程。

- 客户端的 `UPendingNetGame::SendInitialJoin` 发送 `NMT_Hello`
- 服务器的 `UWorld::NotifyControlMessage` 接收 `NMT_Hello`，并发送 `NMT_Challenge`
- 客户端的 `UPendingNetGame::NotifyControlMessage` 接收 `NMT_Challenge` 并在 `NMT_Login` 中发回数据
- 服务器的 `UWorld::NotifyControlMessage` 接收 `NMT_Login`，验证质询数据（challenge data），并调用 `AGameModeBase::PreLogin`
- 如果 `PreLogin` 并没有报任何错误，服务器会调用 `UWorld::WelcomePlayer`，这个函数会调用 `AGameModeBase::GameWelcomePlayer` 并发送含有地图信息的 `NMT_Welcome`
- 客户端的 `UPendingNetGame::NotifyControlMessage` 接收 `NMT_Welcome`，并读取地图信息（以便于后续加载），然后发送 `NMT_NetSpeed` 信息，这里包含了客户端配置了的网络速度（Net Speed）
- 服务器的 `UWorld::NotifyControlMessage` 接收 `NMT_NetSpeed`，然后调整到正确的网络速度

在本章中，下文会具体介绍一下这个流程。在描述流程之前，首先介绍一下这部分相关的一些概念。

- 一个 URL 是 `FURL` 类型，它携带了网络协议、地图路径等信息
- 在客户端主动切换关卡时（`ClientTravel` 或 `OpenLevel`），会重新建立连接
- 在服务器主导的关卡切换时，不需要重新建立连接

`GameplayStatics::OpenLevel()` 会调用 `UEngine::SetClientTravel`，设置世界上下文的 `Context.TravelURL` 变量为将要加入的关卡的 URL：

```cpp
void UEngine::SetClientTravel( UWorld *InWorld, const TCHAR* NextURL, ETravelType InTravelType )
{
    FWorldContext &Context = GetWorldContextFromWorldChecked(InWorld);
    Context.TravelURL    = NextURL;
    Context.TravelType   = InTravelType;
    ...
}

```

接着，在 `UGameEngine::Tick()` 中触发的 `UGameEngine::TickWorldTravel()` 中会调用 `UEngine::Browse()` 函数，如上文所说的，它会创建并管理一个 `UPendingNetGame` 实例，这是客户端与服务器握手的重要对象。

`UEngine::Browse()` 会在这些地方被调用

- `UGameInstance::StartGameInstance()`：这个函数会使用游戏默认 URL 进行 `Browse`
- `UEngine::TickWorldTravel()`：这个函数会获取传入的世界上下文（`FWorldContext`）的最新的一个 URL 进行 `Browse`，如果失败，会尝试使用默认的 URL，也就是下面这条的函数
- `UEngine::BrowseToDefaultMap()`：这个函数会在 `UEngine::TickWorldTravel()` 被调用，试图加载默认地图

`UPendingNetGame` 需要传入一个 URL 进行初始化。下面是 `Browse()` 函数初始化 `UPendingGame` 的代码。

```cpp
WorldContext.PendingNetGame = NewObject<UPendingNetGame>();
WorldContext.PendingNetGame->Initialize(URL); //-V595
WorldContext.PendingNetGame->InitNetDriver(); //-V595

```

在初始化 PendingNetGame 的过程中，会尝试初始化 NetDriver，客户端的 NetDriver 就是从此创建的。`InitNetDriver()` 会尝试调用 `GEngine->CreateNamedNetDriver()` 来创建 NetDriver，这里不深入介绍。

接着，在 `InitNetDriver()` 中，`NetDriver->InitConnect()` 后会尝试开始握手，调用 `UPendingNetGame::BeginHandshake()` 函数：

```cpp
// PendingNetGame.cpp
if( NetDriver->InitConnect( this, URL, ConnectionError ) )
{
	FNetDelegates::OnPendingNetGameConnectionCreated.Broadcast(this);

	ULocalPlayer* LocalPlayer = GEngine->GetFirstGamePlayer(this);
	if (LocalPlayer)
	{
		LocalPlayer->PreBeginHandshake(ULocalPlayer::FOnPreBeginHandshakeCompleteDelegate::CreateWeakLambda(this,
			[this]()
			{
				BeginHandshake();
			}));
	}
	else
	{
		BeginHandshake();
	}
}
...
```

接着，正如 UE 注释文档中的流程所说，客户端首先会在 `SendInitialJoin` 发送 `NMT_Hello`，这件事就发生在 `BeginHandshake()` 中。

```cpp
void UPendingNetGame::BeginHandshake()
{
	// Kick off the connection handshake
	UNetConnection* ServerConn = NetDriver->ServerConnection;
	if (ServerConn->Handler.IsValid()) { ... }
	else
	{
		SendInitialJoin();
	}
}
```

后面的流程发生在在客户端的 `UPendingNetGame::NotifyControlMessage` 和服务器的 `UWorld::NotifyControlMessage` 中。与上面的流程基本一致了，这里不多赘述。需要提及的一点是这两个函数的调用时机：

- `UPendingNetGame::NotifyControlMessage` 会在 `UControlChannel::ReceivedBunch` 中被调用，经过层层转发，最顶层的触发时机为 `IpNetDriver` 的 `TickDispatch`
- `UWorld::NotifyControlMessage` 经过层层转发，在顶层也是在 `UWorld::OnTickDispatch` 中被触发

## 小结

UE 的握手由客户端在载入关卡时被设定在成员变量中，并在 `TickWorldTravel` 中创建 `UPendingNetGame` 并开始握手流程。握手流程主要发生在客户端的 `UPendingNetGame::NotifyControlMessage` 和服务器的 `UWorld::NotifyControlMessage` 中，两者通过传递各项控制信息与地图信息，完成连接的建立。
