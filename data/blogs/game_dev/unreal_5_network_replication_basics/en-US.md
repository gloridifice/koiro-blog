```toml
title = "Unreal 5 Multiplayer Network Replication: A Source Code Walkthrough (Basics)"
summary = "This article uses UE source code and comments to walk through the network replication process in UE 5's server-client multiplayer model."
ai-translation = true
```

> I used UE version 5.5.4 when writing this article.
> 

`NEtDriver.h` contains a detailed introduction to UE's network replication mechanism. In the first chapter, I'll translate UE's comment documentation, which is very helpful for understanding the replication process and its code. Network replication mainly takes place during Tick, so in the second chapter I'll look at the `UNetDriver` class and its behavior during `Tick`, along with some of `UIpNetDriver`. UE also provides a fairly clear introduction to the handshake process. I'll dig a little deeper into the handshake code to see when it is triggered in UE's source.

# UE's network replication system

The comments in `NetDriver.h` explain the network replication mechanism and its related classes and code in considerable detail. I'll translate them here first. Understanding this material is very helpful for understanding UE 5's network replication mechanism.

## `NetDriver`, `NetConnections`, and `Channels`

`UNetDriver` manages a set of `UNetConnetions`. Some typical `UNetDrivers` are:

- `Game NetDriver`: handles normal game network communication
- `Demo NetDriver`: records or plays back recorded game data; this is also where replay functionality is provided
- `Beacon NetDriver`: handles network communication outside normal gameplay

Developers can also implement their own `NetDrivers`.

`NetConnections` represent individual users connected to the game (or, more broadly, to a `NetDriver`).

`NetConnections` don't process the end data directly. Instead, `NetConnections` send the data to `Channels`. Common `Channel` types include:

- `Control Channel`: sends data for monitoring the current connection, such as whether it needs to be closed
- `Voice Channel`: sends voice data between the client and server
- A `Unique Actor Channel` exists for every `Actor` replicated from the server to the client

Although it isn't very common, a custom `Channel` may also be used.

## `Game Net Drivers`, `Net Connections`, and `Channels`

Normally, the server and client each have only one `NetDriver`, used for regular game communication and connections.

The server's `NetDriver` maintains a list of `NetConnection` objects. Each represents a player in the game and is responsible for replicating `Actor` data.

The client's `NetDriver` has only one `NetConnection`, representing its connection to the server.

On both the server and client, the `NetDriver` receives packets from the network and passes them to the correct connection (creating a new `NetConnection` when needed).

## Initiating Connections / Handshaking Flow

`UIpNetDriver` and `UIpConnection` (or their derived classes) are the engine's defaults on most platforms. The following text describes how connections are established and managed. These implementations may differ between `NetDriver` implementations.

The server and client each have their own `NetDriver`. All UE game replication traffic is sent and received through `IpNetDriver`. This traffic also includes the logic for establishing and reestablishing connections (if something goes wrong).

The handshake is spread across several places: `NetDriver`, `PendingNetGame`, `World`, `PacketHandlers`, and so on. The logic is distributed because the requirements are distributed. For example: whether a connection is sending `UE-Protocol` data, whether an address is malicious, or whether a client has the correct game version.

### Startup and Handshaking

Whenever a server loads a map (through `UEngine::LoadMap`), we call `UWorld::Listen`. Its job is to create the main `Game Net Driver`, parse settings, and call `UNetDriver::InitListen`. Ultimately, the code determines exactly how to listen for client connections. For example, in `IpNetDriver`, the IP/port to bind to is determined through the `Socket Subsystem` (see `ISocketSubsystem::GetLocalBindAddresses` and `ISocketSubsystem::BindNextPort`).

Now that the server is listening, it's time to start accepting client connections.

Whenever a client wants to join a server, it first creates a new `UPendingNetGame` with the server's IP in `UEngine::Browse`.
`UPendingNetGame::Initialize` and `UPendingNetGame::InitNetDriver` handle initialization and set up their respective `NetDriver`.
During this initialization, the client immediately sets up a `UNetConnection` for the server and starts sending data through that connection to initiate the handshake.

On both the client and server, `UNetDriver::TickDispatch` receives network data. Generally, when we receive a packet, we check its address to see whether we know it. We need to determine whether we have an established connection for that source address. This is done by simply maintaining a map with `FInternetAddr` as the key and `UNetConnection` as the value.

If a packet comes from an established connection, we pass it to that connection through `UNetConnection::ReceivedRawPacket`. If it comes from a connection that hasn't been established, we consider it `connectionless` and attempt to begin the handshake. For how the handshake works, see `StatelessConnectionHandlerComponent.cpp`.

### `UWorld / UPendingNetGame / AGameModeBase Startup and Handshaking`

After `UNetDriver` and `UNetConnection` finish their handshake work on the server and client,
the client triggers the game-level handshake through `UPendingNetGame::SendInitialJoin`.

Most of the game's basic handshake is handled through `FNetControlMessages`. The full set of control messages can be found in `DataChannel.h`.

Most of the work of processing these control messages is done in both `UWorld::NotifyControlMessage` and `UPendingNetGame::NotifyControlMessage`. The general flow is:

> Uses a challenge-response mechanism
> 
- The client's `UPendingNetGame::SendInitialJoin` sends `NMT_Hello`
- The server's `UWorld::NotifyControlMessage` receives `NMT_Hello` and sends `NMT_Challenge`
- The client's `UPendingNetGame::NotifyControlMessage` receives `NMT_Challenge` and sends data back in `NMT_Login`
- The server's `UWorld::NotifyControlMessage` receives `NMT_Login`, validates the challenge data, and calls `AGameModeBase::PreLogin`
- If `PreLogin` reports no errors, the server calls `UWorld::WelcomePlayer`, which calls `AGameModeBase::GameWelcomePlayer` and sends `NMT_Welcome` containing map information
- The client's `UPendingNetGame::NotifyControlMessage` receives `NMT_Welcome`, reads the map information (for loading later), and sends an `NMT_NetSpeed` message containing the client's configured Net Speed
- The server's `UWorld::NotifyControlMessage` receives `NMT_NetSpeed` and adjusts to the correct network speed

At this point, the handshake can essentially be considered complete. The player is fully connected to the game. Also, depending on how long the map takes to load, the client may receive some non-handshake control messages in `UPendingNetGame` before control is passed to `UWorld`. Additional steps may be taken to handle encryption if necessary.

### Reestablishing Lost Connections

Throughout a game, many things can cause a user's connection to be lost: an Internet outage, switching from LET to WIFI, leaving the game, and so on.

- initiated: started

If the server initiates a disconnect or becomes aware of one of these situations (through a timeout or error), the disconnect closes the `UNetConnection` and notifies the game so it can handle it. At this point, the game decides whether to support Join In Progress or Rejoins. If the game doesn't support them, we go through the handshake process described above again.

- albeit: although

If the client's connection is only briefly interrupted without the server becoming aware of it, the engine/game will usually recover automatically, albeit with some packet loss and lag spikes.

However, if the client's IP address or port changes without the server being aware of it, a recovery process begins. This repeats the low-level handshake without notifying the game code. The related code is in `StatlessConnectionHandlerComponent.cpp`.

## Data Transmission

A game's `NetConnecitons` and `NetDrivers` are usually independent of the underlying communication method/technology. Those details are left to their derived classes (such as `UOpConnectio` / `UIpNetDriver` or `UWebSocketConnection` / `UWebSocketNetDriver`).

Instead, `UNetDriver` and `UNetConnection` work with `Packets` and `Bunches`.

A `Packet` is a blob of data sent back and forth between a host's and a user's `NetConnection` objects. It contains metadata about the packet itself, such as header information and intellectual property, as well as `Bunches`.

A `Bunch` is a blob of data sent back and forth between a user's and a host's `Channel` objects. When a connection receives a packet, the packet is split into individual bunches. These bunches are then passed to individual `Channel` objects for further processing.

A packet may contain no bunches, one bunch, or multiple bunches. Because the size limit for multiple bunches in UE may be larger than a single packet, UE supports the notion of partial bunches:

When a bunch is too large, it is split into smaller bunches marked `PartialInitial`, `Partial`, or `PartialFinal`. Using this information, we can reassemble the bunches into a whole after receiving them.

Example — a client makes an RPC to the server:

- The client calls `Server_RPC`
- The request is forwarded through `NetDriver` and `NetConnection` to the corresponding Actor's Actor Channel
- The Actor Channel serializes the RPC identifier and parameters into a bunch carrying that Actor Channel's ID
- The Actor Channel asks `NetConnection` to send the bunch
- `NetConnection` then combines this data into a packet and sends it to the server
- On the server, the packet is received by `NetDriver`
- `NetDriver` checks the packet's source address and passes the packet to the appropriate `NetConnection`
- The corresponding `NetConnection` splits the packet into bunches
- `NetConnection` uses the Actor Channel ID contained in the bunch to send it to the corresponding Actor Channel
- The Actor Channel unpacks the bunch, examines the RPC data it contains, and calls the Actor's function using the RPC ID and serialized parameters

### Reliability and Retransmission

UE networking generally assumes that the underlying network protocol does not guarantee reliability. It therefore implements its own reliability and retransmission for packets and bunches.

When a `NetConnection` is established, it sets up sequence numbers for its packets and bunches: packet numbers and bunch numbers. The sequence numbers may be fixed or random (random sequence numbers are sent by the server).

For each `NetConnection`, every packet contains a packet number, which increases with each packet sent. Packet numbers are never reused.

For each `Channel`, every reliable bunch has a bunch number, which increases with each reliable bunch sent. Unlike packets, reliable bunches may be retransmitted, so we may send bunches with the same bunch number.

Note that packet numbers and bunch numbers are both just sequence numbers. There is no fundamental difference between them. The distinction here is only to make things easier to understand.

### Detecting Incoming Dropped Packets

Because packet numbers are assigned, we can easily detect when packet loss occurs. We just compare the packet numbers of the last successfully received packet and the current successfully received packet to see whether they are consecutive. The difference between the two packet numbers minus 1 is the number of packets lost.

If the difference between the two packet numbers is 0 or negative, then, because the engine does not reuse sequence numbers, we can only have received a previously sent packet that hadn't arrived yet, or an external service is attempting to resend data to us. In either case, the engine generally ignores these lost and invalid packets and does not send an ACK (acknowledgment) for them.

If packets arrive out of order within the same frame, we have a way to fix their order (if this feature is enabled): if we receive a packet whose number differs from the previous one by more than 1, some packets in between haven't arrived. We cache the current packet in a queue. The next time we receive a packet in the correct order, we check whether its ordering relative to the previously received packets is correct. If it is, we process them; if not, we keep receiving packets.

After reading all currently available packets, we clear this queue and process all remaining packets in it. Any missing packets are then considered dropped.

For every successfully received packet, the receiver sends its packet number as an ACK (acknowledgment).

### Detecting Outgoing Dropped Packets

As mentioned above, the receiver sends a packet's number as an ACK whenever it successfully receives one. These ACKs contain sequential packet numbers.
Much as the receiver tracks packet numbers, the sender tracks the highest ACK packet number. When ACKs are processed, any packet number lower than the last ACK we received is ignored, and any gaps between packet numbers are considered Not Acknowledged (NAKed).

The sender handles these ACKs and NAKs and resends lost data with new packet numbers. New data is added to newly sent packets.

### Resending Missing Data

As mentioned above, packets themselves don't contain useful data; bunches carry it. Bunches can be marked Reliable or Unreliable.

For unreliable bunches, the engine doesn't attempt to resend missing data. So if a bunch is marked unreliable, the engine must be able to keep running without it, use an external retry mechanism, or send the data redundantly.

Everything below describes reliable bunches.

Whenever a reliable bunch is sent, it is added to a list of unACKed reliable bunches. If we receive a packet containing a NAK for a reliable bunch, the engine retransmits an identical copy of that bunch. Note that because a bunch may be split into partial bunches, losing one partial bunch also causes the entire bunch to be retransmitted.

Only when all packets containing that reliable bunch have been ACKed do we remove the reliable bunch from the list above.

As with packets, we compare against the sequence number of the last successfully received bunch. If the difference is negative, we consider a bunch lost. Unlike packet handling, we don't discard this data. Instead, we add the bunch to a queue and pause processing of all bunches, whether reliable or unreliable.

Processing does not resume until we receive the missing bunch. After receiving it, we process that bunch and the bunches in the queue.

Any new bunches received while waiting for the missing bunch are not processed immediately, but are added to the queue.

# Important concepts

In addition to the important concepts in the first chapter, this chapter introduces a few concepts not mentioned above that appear frequently in the source code.

**Network Metrics**, as the name suggests, are metrics developers can use to evaluate and debug network performance. The network replication source code in UE contains a lot of code for updating network metrics, often associated with the word `Metric`. Developers can also register their own network metrics and listen for metric update events. See `GetMetrics()` and `NetworkMetricsDatabase.h` for details.

# Source code walkthrough — the Tick phase

`UNetDriver` is the core of UE's network processing. `UNetDriver` has several important fields:

- `ServerConnection`: the connection to the server; if it is `nullptr`, this instance is running on the server
- `ClientConnections`: the list of connections to clients
- `MappedClientConnections`: a map from IPs to connections

Most of `UNetDriver`'s work takes place during the world's Tick, split across different phases. Below is the order of the world's Tick functions and events. Names starting with `On` represent events.

1. `World::Tick`
2. `OnTickDispatch`
3. `PrePhysics`
4. `ActorTick`
5. `OnPostTickDispatch`
6. `OnPreTickFlush`
7. `OnTickFlush`
8. `OnPostTickFlush`

Network-related work mainly takes place in these four events:

1. `OnTickDispatch`
2. `OnPostTickDispatch`
3. `OnTickFlush`
4. `OnPostTickFlush`

Here is the code in `UNetDriver` that registers for these four events:

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

| Call phase | Main function (`UNetDriver`) | Notes |
| --- | --- | --- |
| `OnTickDispatch` | `TickDispatch` | Called through `InternalTickDispatch`; the Internal intermediary prevents illegal recursive calls |
| `OnPostTickDispatch` | `PostTickDispatch` |  |
| `OnTickFlush` | `TickFlush` | Called through `InternalTickFlush`; Internal serves the same purpose as above<br> |
| `OnPostTickFlush` | `PostTickFlush` |  |
| The following sections describe these four functions in order. |  |  |

## `TickDispatch`

This function mainly handles receiving data.

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

The code above mainly updates real-time values and logs a message when a frame takes too long.

```cpp
	// Checks for standby cheats if enabled
	UpdateStandbyCheatStatus();
	ResetNetworkMetrics();
```

- `ResetNetworkMetrics` resets network metrics

Next comes the server-side logic. In the server logic,

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

First, it iterates over `ClientConnections` in reverse and checks connection validity. Invalid connections are safely cleaned up; valid connections have their `PreTickDispatch()` called. Connections that have been CleanUp'd are not destroyed immediately. Instead, they are stored in `RecentlyDisconnectedClients` and tracked for a while. Only after the preset tracking period has elapsed are they finally removed from `RecentlyDisconnectedClients`, meaning they are destroyed. Here is the code for that logic:

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

That covers the server side. The following code concerns the client side.

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

The client executes `PreTickDispatch`. This function performs the Tick work for DoS protection and error recovery; I won't go into detail here.

The packet-receiving logic is actually in `UIpNetDriver`, a derived class of `UNetDriver`:

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

If the receive thread is enabled, it processes the receive thread's data.

```cpp
#if !UE_BUILD_SHIPPING
	...
#endif
	...
	// Process all incoming packets
	for (FPacketIterator It(this); It; ++It)
	{
```

The iterator goes through all incoming packets. Processing of these packets begins below.

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

- `bOk` indicates whether the current packet can be processed

If an invalid, zero-size packet is received, possibly due to an error or attack, it is counted and skipped immediately. Otherwise, `NotifyLowLevelReceive` is called to handle the incoming packet.

Next is the case where `bOk` is false, which performs error handling:

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

If a blocking error occurs, it breaks out of the loop directly. An oversized packet may indicate a malicious attack; there may also be issues such as a client reset.

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

The latter half mainly handles connections, including new connection requests and distributing received data to connections.

## `PostTickDispatch`

`PostTickDispatch()` finishes task dispatch.

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

The function first executes each connection's `PostTickDispatch`. As shown above, on a client it calls the client connection's function; on a server it safely iterates over the client connection list and calls the function. In `UNetConnection::PostTickDispatch`, the connection flushes its cached packet queue, which buffers arriving packets while waiting for missing ones. It also calls `RPCDoS`'s `PostTickDispatch()`.

```cpp
#if UE_WITH_IRIS
	PostDispatchSendUpdate();
#endif

	if (ReplicationDriver)
	{
		ReplicationDriver->PostTickDispatch();
	}
```

- `ReplicationDriver->PostTickDispatch()` is an empty implementation with no behavior for now

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

Most of `NetDriver`'s work takes place in `TickFlush`.

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

As shown above, the engine first initializes the timer. It then begins Actor replication:

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

The code above corresponds to the server's replication mechanism. Because UE 5 introduced the new Iris replication mechanism, a number of macros appear here to distinguish traditional replication from Iris replication. I'll explain Iris in detail in a later chapter; it is omitted here for readability. Traditional replication takes place in `ServerReplicateActors()`.

---

### `ServerReplicateActors()`

Replicates all Actors relevant to connections. First, it builds a list of relevant Actors, then attempts to replicate each Actor to its relevant connections until a connection is saturated.

```cpp
int32 UNetDriver::ServerReplicateActors(float DeltaSeconds)
{
	SCOPE_CYCLE_COUNTER(STAT_NetServerRepActorsTime);

#if WITH_SERVER_CODE
	if ( ClientConnections.Num() == 0 ) { return 0; }
```

`WITH_SERVER_CODE` extends directly to the end of the function, meaning this function only runs on the server. If executed on a client, it returns 0 immediately. The code above also checks for the absence of server connections, in which case the engine skips replication directly.

```cpp
	GetMetrics()->SetInt(UE::Net::Metric::NumReplicatedActors,0 );
	GetMetrics()->SetInt(UE::Net::Metric::NumReplicatedActorBytes, 0);
```

Initializes the statistical metrics.

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

The engine then checks whether it supports Iris's `ReplicationDriver`. If so, it uses Iris. Otherwise, it executes the traditional replication logic that follows.

```cpp
	check( World );

	// Bump the ReplicationFrame value to invalidate any properties marked as "unchanged" for this frame.
	ReplicationFrame++;

	int32 Updated = 0;

	const int32 NumClientsToTick = ServerReplicateActors_PrepConnections( DeltaSeconds );

	if ( NumClientsToTick == 0 ) { return 0; } // No connections are ready this frame
```

- `ServerReplicateActors_PrepConnections` has two main jobs: determining how many connections to process this frame, namely `NumClientsToTick`, and rotating the connection processing order to prevent connections from being left unprocessed

If there are no connections to process, it returns immediately.

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

The code above mainly builds the list of Actors to replicate, filtering out `Actor` objects that don't need replication.

- `ServerTickTime` represents the server's Tick interval. If a maximum Tick rate (`ServerTickRate`) is set, the interval is the reciprocal of that maximum rate, used to build the replication consideration list
- `ServerReplicateActors_BuildConsiderList` builds the replication consideration list, filtering out dormant Actors

After determining which Actors to replicate, the actual replication logic begins. First, it iterates over each client connection,

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

First, it validates dormancy, mainly attempting updates when dormant objects return from dormancy to an active state.

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

Next, if a client doesn't need replication this frame and its `Channel`'s last update time is earlier than the connection's update time, the connection's `bPendingNetUpdate` is set to true so it can be added to the replication list next time.

The code that follows handles replication itself.

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

This code obtains the other viewer connections associated with each connection.

- `ViewTarget` is an `AActor` pointer, usually the current Actor's controller, such as a `PlayerController`, used to obtain the current `Actor`'s physical location
- `Viewer`: essentially an `FNetViewer` struct containing a network connection pointer, viewpoint, position coordinates, and so on. It is used to quickly determine whether an object is within another object's field of view. These data are usually associated with an Actor
- The syntax `new (ConnectionViewers) FNetViewer(Connection, DeltaSeconds);` allocates memory for a new `FNetViewer` in `ConnectionViewers`

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

Iterates over the `PlayerController` of every connection and child connection and attempts to update it.

```cpp
			FMemMark RelevantActorMark(FMemStack::Get());

			const bool bProcessConsiderListIsBound = OnProcessConsiderListOverride.IsBound();

			if (bProcessConsiderListIsBound)
			{
				OnProcessConsiderListOverride.Execute( { DeltaSeconds, Connection, bCPUSaturated }, Updated, ConsiderList );
			}
```

- `FMemMark` is a memory-stack management marker. It allocates memory by moving the stack-top pointer and can release it quickly. Specifically, it records the current stack-top pointer. Calling `Pop` restores the stack top to the recorded position, effectively "freeing" the memory quickly.
- `OnProcessConsiderListOverride` is an event related to custom replication logic, allowing developers to customize `ConsiderList`

```cpp
			if (!bProcessConsiderListIsBound)
			{
				FActorPriority* PriorityList = NULL;
				FActorPriority** PriorityActors = NULL;

				// Get a sorted list of actors for this connection
				const int32 FinalSortedCount = ServerReplicateActors_PrioritizeActors(Connection, ConnectionViewers, ConsiderList, bCPUSaturated, PriorityList, PriorityActors);
```

This runs the default replication logic. First, the engine generates a sorted list of Actors for this connection, ordered by Actor priority.

```cpp
				// Process the sorted list of actors for this connection
				TInterval<int32> ActorsIndexRange(0, FinalSortedCount);
				const int32 LastProcessedActor = ServerReplicateActors_ProcessPrioritizedActorsRange(Connection, ConnectionViewers, PriorityActors, ActorsIndexRange, Updated);

				ServerReplicateActors_MarkRelevantActors(Connection, ConnectionViewers, LastProcessedActor, FinalSortedCount, PriorityActors);
			}
```

Next, it processes high-priority Actors.

- `ServerReplicateActors_ProcessPrioritizedActorsRange` processes high-priority Actors and returns the number of Actors left unprocessed
- `ServerReplicateActors_MarkRelevantActors` uses the remaining Actor count to mark relevant Actors as relevant and leave them for priority processing in the next frame

---

`ServerReplicateActors_ProcessPrioritizedActorsRange` iterates over `PriorityActors` within `ActorsIndexRange`, then each element's `Channel` and `Actor`, and determines the "recent relevancy" of other viewers to the current connection. There are two specific conditions:

- `Actor` objects visible within each `1.0 + 0.5R` seconds
- The return value of `IsActorRelevantToConnection`

After a series of complex relevancy checks, the function finally obtains or creates the Channel from the current connection to the relevant Actor, and performs Actor replication through `UNetChannel::ReplicateActor()`.

---

```cpp
			RelevantActorMark.Pop();
			ConnectionViewers.Reset();
			Connection->LastProcessedFrame = ReplicationFrame;
			const bool bWasSaturated = GNumSaturatedConnections > LocalNumSaturated;
			Connection->TrackReplicationForAnalytics(bWasSaturated);
		}
```

Next, the code above performs cleanup and finishing work, including releasing temporary high-priority memory, clearing `ConnectionViewers`, updating `LastProcessedFrame` to the current frame, and testing connection saturation (if the saturation count increases after processing, a bandwidth bottleneck occurred this frame).

```cpp
		if (Connection->GetPendingCloseDueToReplicationFailure())
		{
			ConnectionsToClose.Add(Connection);
		}
	}
```

Checks whether the connection needs to be closed because of replication behavior.

```cpp
	if (OnPostConsiderListUpdateOverride.IsBound())
	{
		OnPostConsiderListUpdateOverride.ExecuteIfBound( { DeltaSeconds, nullptr, bCPUSaturated }, Updated, ConsiderList );
	}
```

Broadcasts `OnPostConsiderListUpdateOverride`, the event for custom behavior after list processing.

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

This code rotates the `ClientConnections` list. Connections already processed at the front of `ClientConnections` are moved to the end, giving connections that haven't had a turn priority in the next frame.

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

Finally, the engine closes the connections collected above that need closing.

---

Returning to `UNetDriver::TickFlush()`, the next code concerns connection updates.

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

This mainly handles local voice packets. At the same time, on both the client and server, each `UNetConnection`'s `Tick` function is executed. I'll describe it in detail below.

```cpp
	if (ConnectionlessHandler.IsValid())
	{
		ConnectionlessHandler->Tick(DeltaSeconds);

		FlushHandler();
	}
```

This code forces all packets in `ConnectionlessHandler` to be sent.

- `ConnectionlessHandler` is a `TUniquePtr<PacketHandler>`, initialized in `NetDriver::InitConnectionlessHandler()`. Its `Tick` function safely sends the packets in its queue
- `FlushHandler` forces all buffered connectionless packets to be sent

```cpp
	//... debug
	//... Iris
	UpdateNetworkStats();
	// Send the current values of metrics to all of the metrics listeners.
	GetMetrics()->ProcessListeners();
```

- `UpdateNetworkStats()` mainly updates basic statistics such as bandwidth utilization and packet loss rate
- `GetMetrics()` returns a smart pointer to `UNetworkMetricsDatabase`. This class manages all network metric listeners (the observer pattern). It calls listeners when metrics change, for tasks such as visualization and performance monitoring, and is also available for developers to use. Detailed documentation comments (including usage examples) can be found in `UNetworkMetricsDatabase.h`

```cpp
	// Update the lag state
	UpdateNetworkLagState();
}
```

Finally, the function updates the lag state machine. I won't go into that here.

---

### `UNetConnection::Tick`

The logic in `UNetConnection::Tick` is very long and complex. Rather than paste all of its code here, I'll give a systematic overview of its functions and show selected snippets.

First, right at the start, the engine may skip this connection's Tick for the frame to avoid updating the network too frequently and overloading bandwidth:

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

Specifically, if the current frame time is less than the configured minimum frame time, this Tick is skipped.

Next, it handles internal ACK mode. Internal ACK mode simulates remote packet sending and receiving locally, in scenarios such as replay mode and local games. In internal ACK mode, data does not need network serialization or deserialization, which can also reduce latency during replays.

```cpp
if (IsInternalAck())
{
	...
}
```

Next, the engine updates network statistics, including bandwidth calculations and incoming and outgoing packet loss rates.

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

It then handles connection errors, which are flagged in the code above.

```cpp
if (bConnectionPendingCloseDueToSocketSendFailure)
{
    Close(ENetCloseResult::SocketSendFailure);
    bConnectionPendingCloseDueToSocketSendFailure = false;
    // early out
    return;
}

```

Next comes timeout detection.

```cpp
const float Timeout = GetTimeoutValue();

const bool bReceiveTimedOut = (CurrentRealtimeSeconds - LastReceiveRealtime) > Timeout;
const bool bGracefulCloseTimedOut = (GetConnectionState() == USOCK_Closing) && (DriverElapsedTime > bGracefulCloseTimedOut);

if (bReceiveTimedOut || bGracefulCloseTimedOut)
{
	...
}
```

Timeouts are divided into receive timeouts and graceful-close timeouts.

- A receive timeout (`bReceiveTimedOut`) is triggered when the difference between the current time and the last time a valid packet was received exceeds the `Timeout` threshold. In addition to logging, it closes the connection and attempts to reconnect automatically
- A graceful-close timeout (`bGracefulCloseTimedOut`) is triggered when the current connection is in the closing process (`USOCK_Closing`) and has exceeded the graceful-close time limit (`bGracefulCloseTimedOut`). This is part of the normal closing process: the connection isn't closed immediately so that tasks such as unfinished reliable data transfers can complete. If graceful closing times out, it is forcibly closed

Either timeout triggers `UNetConnection::HandleConnectionTimeout()`, which broadcasts an error and closes the current connection it belongs to.

Next, it updates Channels:

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

- `CVarTickAllOpenChannels` configures whether all Channels are ticked

Because large multiplayer games often have many Channels (hundreds), selectively ticking only the necessary Channels can help performance considerably.

```cpp
for ( auto ProcessingActorMapIter = KeepProcessingActorChannelBunchesMap.CreateIterator(); ProcessingActorMapIter; ++ProcessingActorMapIter ) {
	...
}
```

Next, the engine processes the remaining data in Actor Channels that are to be closed.

- `KeepProcessingActorChannelBunchesMap` is a map of Channels that are to be fully closed. Although these Channels have been requested to close completely, they still have unprocessed data, so processing must continue here until it is complete, after which they can close

This remaining data includes sent reliable data that hasn't been ACKed, Partial Bunches spanning frames, and replicated properties whose processing was deferred.

The engine then flushes the network to send data:

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

`FlushNet` calls the low-level `LowLevelSend` to send data.

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

Near the end, the function attempts to resend data. It retrieves the resend queue and sends packets one by one in a while loop.

## `PostTickFlush`

`PostTickFlush` mainly handles cleanup, including clearing voice packets and destroying `NetDriver` itself (if `NetDriver` is pending destruction).

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

Clears voice packets (above).

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

Checks and destroys `NetDriver` (above).

## Recap

That completes the network system's general flow during Tick. I call it general logic because `UIpNetDriver` and other derived classes of `UNetDriver` share their parent's behavior while also implementing their own. Below, I'll briefly introduce `IpNetDriver` and how it handles handshakes.

# Handshake

Let's revisit the handshake process introduced in the first chapter.

- The client's `UPendingNetGame::SendInitialJoin` sends `NMT_Hello`
- The server's `UWorld::NotifyControlMessage` receives `NMT_Hello` and sends `NMT_Challenge`
- The client's `UPendingNetGame::NotifyControlMessage` receives `NMT_Challenge` and sends data back in `NMT_Login`
- The server's `UWorld::NotifyControlMessage` receives `NMT_Login`, validates the challenge data, and calls `AGameModeBase::PreLogin`
- If `PreLogin` reports no errors, the server calls `UWorld::WelcomePlayer`, which calls `AGameModeBase::GameWelcomePlayer` and sends `NMT_Welcome` containing map information
- The client's `UPendingNetGame::NotifyControlMessage` receives `NMT_Welcome`, reads the map information (for loading later), and sends an `NMT_NetSpeed` message containing the client's configured Net Speed
- The server's `UWorld::NotifyControlMessage` receives `NMT_NetSpeed` and adjusts to the correct network speed

In this chapter, I'll describe this process in more detail. Before describing the flow, here are a few related concepts.

- A URL is of type `FURL` and carries information such as the network protocol and map path
- When the client initiates a level change (`ClientTravel` or `OpenLevel`), the connection is reestablished
- When the server leads a level change, the connection does not need to be reestablished

`GameplayStatics::OpenLevel()` calls `UEngine::SetClientTravel`, setting the world context's `Context.TravelURL` variable to the URL of the level to join:

```cpp
void UEngine::SetClientTravel( UWorld *InWorld, const TCHAR* NextURL, ETravelType InTravelType )
{
    FWorldContext &Context = GetWorldContextFromWorldChecked(InWorld);
    Context.TravelURL    = NextURL;
    Context.TravelType   = InTravelType;
    ...
}

```

Next, `UGameEngine::TickWorldTravel()`, triggered in `UGameEngine::Tick()`, calls `UEngine::Browse()`. As mentioned above, it creates and manages a `UPendingNetGame` instance, an important object for the handshake between client and server.

`UEngine::Browse()` is called in these places:

- `UGameInstance::StartGameInstance()`: this function uses the game's default URL for `Browse`
- `UEngine::TickWorldTravel()`: this function gets the latest URL from the supplied world context (`FWorldContext`) for `Browse`. If it fails, it attempts to use the default URL through the function in the next item
- `UEngine::BrowseToDefaultMap()`: this function is called in `UEngine::TickWorldTravel()` to attempt to load the default map

`UPendingNetGame` needs a URL passed in for initialization. Below is the code in `Browse()` that initializes `UPendingGame`.

```cpp
WorldContext.PendingNetGame = NewObject<UPendingNetGame>();
WorldContext.PendingNetGame->Initialize(URL); //-V595
WorldContext.PendingNetGame->InitNetDriver(); //-V595

```

During PendingNetGame initialization, it attempts to initialize NetDriver. This is where the client's NetDriver is created. `InitNetDriver()` attempts to call `GEngine->CreateNamedNetDriver()` to create NetDriver; I won't go into detail here.

Next, in `InitNetDriver()`, after `NetDriver->InitConnect()`, it attempts to begin the handshake by calling `UPendingNetGame::BeginHandshake()`:

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

Then, just as the flow in UE's comment documentation describes, the client first sends `NMT_Hello` in `SendInitialJoin`. This happens in `BeginHandshake()`.

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

The rest of the process takes place in the client's `UPendingNetGame::NotifyControlMessage` and the server's `UWorld::NotifyControlMessage`. It is essentially the same as the flow above, so I won't repeat it here. One point worth mentioning is when these two functions are called:

- `UPendingNetGame::NotifyControlMessage` is called in `UControlChannel::ReceivedBunch`. Through layers of forwarding, its top-level trigger is `IpNetDriver`'s `TickDispatch`
- `UWorld::NotifyControlMessage` is also triggered at the top level in `UWorld::OnTickDispatch`, through layers of forwarding

## Recap

UE's handshake is set up in member variables by the client when loading a level. `UPendingNetGame` is then created in `TickWorldTravel`, and the handshake begins. The handshake mainly takes place in the client's `UPendingNetGame::NotifyControlMessage` and the server's `UWorld::NotifyControlMessage`. The two establish the connection by passing various control messages and map information.
