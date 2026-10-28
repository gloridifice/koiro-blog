```toml
title = "Bevy 引擎渲染架构设计概览"
english_title = "Design of Bevy engine’s rendering system."
slug = "本文简单介绍了 Bevy 的渲染架构设计，包括有管理渲染顺序的向无环图、渲染流程中 System 顺序、不同物体的渲染阶段、主世界和渲染世界的互动等等。以 Bevy Core 3D 的渲染实现为参考。此外还简单介绍了在一般开发中，如何使用 Bevy 渲染相关的 API。"
published = true
last_edited_date = "2025-06-05T13:52:00"
authors = "宏楼 Koiro"
class = "Dev"
date = "2025-02-19T22:27:00"
date = "2025-02-20"
tags = ["Bevy", "Game Dev", "Rust"]
```

# 前言

本文会用 `#` 符号代表函数名，如 `#foo`；用 `A#foo`代表 `A` 类型的函数`foo` 。Bevy 为图形 API 做了封装，我们可以找到和 wgpu 一一对应的 API，在本文不多介绍这一部分。

# 渲染 App 与世界

Bevy 中的渲染发生在渲染 `App`，其是我们创建的 `App` 的 `SubApp`（子 App），两者可以在不同的线程运行。

```rust
fn build(&self, app: &mut App) {
	...
	let render_app = match app.get_sub_app_mut(RenderApp) {
    Ok(render_app) => render_app,
    Err(_) => return,
	};
}
```

# 渲染集合

在 Bevy 中，渲染相关的 System 在 `Render` Schedule 执行。同时，渲染的准备工作和渲染执行工作是按顺序进行的，Bevy 提供了 `RenderSet` 作为标记。被标记了 `RenderSet` 的 System 每帧按 `RenderSet` 枚举中的顺序进行执行：

```rust
pub enum RenderSet {
    ExtractCommands,
    PrepareAssets,
    ManageViews,
    Queue,
    QueueMeshes,
    PhaseSort,
    Prepare,
    PrepareResources,
    PrepareResourcesFlush,
    PrepareBindGroups,
    Render, //!!
    Cleanup,
    PostCleanup,
}
```

在实现上，我们可以用 `in_set` 为一个 System 指定一个 `RenderSet`

```rust
render_app.add_systems(Render, custom_system.in_set(RenderSet::Queue));
```

# 管理渲染流程的数据结构

在我们渲染一帧的过程中，会用到很多 Pass。不同的 Pass 执行的先后顺序不同。同时，其它程序可能要为已经写好的库的 Pass 之间插入新的 Pass。此外，我们还希望我们在写渲染行为的时候能专注分离。例如，在 Shadow 的 PrePass 的时候不需要考虑主 Pass 怎么写。综合以上需求，我们可以想象，我们需要一个能够存储各个渲染行为，同时能够管理和调整渲染行为先后顺序的数据结构。在 Bevy 中，这个数据结构是一个有向图，被称为 `RenderGraph (struct)`，下文称渲染图。渲染图的每个节点被直接称为 `Node (trait)`，它负责执行渲染行为。声明节点之间顺序的结构是 `Edge`。此外，渲染图还为我们提供了存储某些渲染相关数据的能力，其为 `Slot`。它可以存储 Buffer, TextureView, Sampler, Entity 等。

```rust
pub trait Node:
    Downcast
    + Send
    + Sync
    + 'static {
    // Required method
    fn run<'w>(
        &self,
        graph: &mut RenderGraphContext<'_>,
        render_context: &mut RenderContext<'w>,
        world: &'w World,
    ) -> Result<(), NodeRunError>;

    // Provided methods
    fn input(&self) -> Vec<SlotInfo> { ... }
    fn output(&self) -> Vec<SlotInfo> { ... }
    fn update(&mut self, _world: &mut World) { ... }
}
```

`Node` trait 中的 `#run` 函数是发生执行渲染命令的函数，例如创建 `RenderPass`、执行 `draw` 命令等。而`input` 和 `output` 定义了 `#run` 时需要用到的输入和输出内容的信息，可以在 `#run` 时从 `RenderGraphContext` 中访问。这两个函数的默认实现只返回一个空的 `Vec` 并且大部分时候没有被实现。在此我们先不在意它们。

想要在 App 中加入一个 `Node`，我们可以用 `#add_render_graph_node` 函数。

```rust
render_app
		// 添加一个子图
    .add_render_sub_graph(Core3d)
    // 添加 Node
    .add_render_graph_node::<ViewNodeRunner<PrepassNode>>(Core3d, Node3d::Prepass)
    .add_render_graph_node::<ViewNodeRunner<DeferredGBufferPrepassNode>>(
        Core3d,
        Node3d::DeferredPrepass,
    )
    ...
    ...
    // 定义边（Node 的顺序）
		.add_render_graph_edges( 
        Core3d,
        (
            Node3d::Prepass,
            Node3d::DeferredPrepass,
            Node3d::CopyDeferredLightingId,
            Node3d::EndPrepasses,
            Node3d::StartMainPass,
            Node3d::MainOpaquePass,
            Node3d::MainTransmissivePass,
            Node3d::MainTransparentPass,
            Node3d::EndMainPass,
            Node3d::Tonemapping,
            Node3d::EndMainPassPostProcessing,
            Node3d::Upscaling,
        ),
    );
```

其接受一个 `RenderSubGraph` 和一个 `RenderLabel`。这两个 trait 都是标签 trait，只做标记作用。前者标记渲染子图，后者标记 Node。想要定义一个自己的 `RenderLabel` 很简单，Bevy 提供了同名的过程宏。

```rust
// * 实现 RenderLable 需要实现 Debug, Clone, Eq
#[derive(Debug, Clone, Hash, PartialEq, Eq, RenderLabel)]
pub struct StartFooPass;
```

另外，在上面的代码中，我们可以发现添加 Node 的时候，只传入了 Node 的类型，而不是一个结构题。因为 `#add_render_graph_node` 要求了传入 Node 类型是实现 `FromWorld` trait 的。`FromWorld` 这个 trait 支持从 world 创建一个对应类型的实例。所以不必我们手工添加一个实例。

在继续我们的话题之前，我们先介绍一个概念：View

### View

在图形 API 中（至少在 wgpu 中），一次渲染的目标是一个贴图的其中一个 View，其是一个贴图的一个状态。在 Bevy 中，View 被用 `Entity` 和 `ViewTarget` 组件来管理。我们在传递的过程中使用 `Entity` 来传递。需要用到 View 时，则 query 它的 `ViewTarget`，获取 View，调用其 `#get_color_attachment` 即可。

### ViewNode

`Node` 是一个 `trait`，我们在 Bevy 中最常用的 `Node` 的实现是 `ViewNodeRunner<N: ViewNode> (struct)` 。其中 `ViewNode` 是一个更易用的可以从 `world` 获取 View 上下文的 `trait`。也是我们添加渲染内容最常实现的 trait 之一。

看一下其内容就很好理解了：

```rust
pub trait ViewNode {
    type ViewQuery: ReadOnlyQueryData;

    // Required method
    fn run<'w>(
        &self,
        graph: &mut RenderGraphContext<'_>,
        render_context: &mut RenderContext<'w>,
        view_query: <Self::ViewQuery as WorldQuery>::Item<'w>,
        world: &'w World,
    ) -> Result<(), NodeRunError>;

    // Provided method
    fn update(&mut self, _world: &mut World) { ... }
}
```

> 注：`ViewNode` 并不是 Node 的实现，`ViewNodeRunner` 才是。
> 

`ViewQuery` 是我们要 query 的携带 `ViewTarget` （或其它能提供 View）的实体的 Query 类型。

在 `#run` 中，我们逐一介绍一下各个参数

- `render_context` 包含了 RenderDevice, CommandEncoder 等渲染上下文内容
- `graph` 包含了我们当前渲染图的各类上下文信息
- `view_query` 是我们要的 Query 的 View 的结果

注意，如果一个 `ViewNode` 实现了 `FromWorld`，那么其 `ViewNodeRunner` 会自动被实现 `FromWorld`，所以为了让 `ViewNodeRunner` 能够被插入 App，记得为 ViewNode 类型实现 `FromWorld`.

<aside>
💡

实现了 `Default` 的类型会自动被实现 `FromWorld`.

</aside>

### ViewNode 的实现案例

在 Bevy 核心渲染管线的不透明 Pass Node 中，其 Query 的是 ExtractedCamera 和其它渲染相关的组件。

<aside>
👉

`ExtractedCamera` 是渲染世界中提供了渲染所需信息的相机组件。在 Bevy 中，*Extract* 表示将从主世界提取到渲染世界中的过程。我们主相机实体和它的组件会在一个 `ExtractSchedule` 阶段的 system 中被 Query，然后被实例成一个 `ExtractedCamera`，插入到渲染世界的相机实体中。

</aside>

```rust
impl ViewNode for MainOpaquePass3dNode {
    type ViewQuery = (
        Entity,
        &'static ExtractedCamera,
        &'static ViewTarget,
        &'static ViewDepthTexture,
        Option<&'static SkyboxPipelineId>,
        Option<&'static SkyboxBindGroup>,
        &'static ViewUniformOffset,
    );
    ...
}
```

# 渲染阶段

直接与 `RenderGraph` 和 `Node` 交互虽然底层和灵活，但在人体工学和可拓展性上并不那么好。Bevy 提供了一个更好的管理渲染内容的数据结构：`BinnedRenderPhase`。

<aside>
💡

Bin 的中文是“桶”，Binned 代表分”桶“数据结构，或者说分组。我们会对元素进行分组存储。一个直观的 Binned 的数据结构是 `HashMap` ，哈希值是桶的索引，每个哈希值对应的一个数组，也就是桶。用于避免哈希值相同的元素无处所放。

</aside>

这是一个有些复杂的数据结构，要讲解它的实现需要花些时间。我们先从直觉上了解一下它是什么：

- 它不是一个 Node
- 它是一个管理被渲染的物体的上下文的数据结构
- 它可以管理这些信息
    - 要被渲染实体列表
    - 用什么行为渲染这些实体（渲染函数）
    - 实体的渲染顺序是怎样的
- 一个渲染阶段的渲染过程中，所有渲染命令使用同一个 `RenderPass` ，无法中途切换
- 一个实体可以在`BinnedRenderPhase` 中出现多次，例如同一个实体分别在不同批次被绘制

而一切一切的准备后，在使用上。我们只需要在一个 Node 中，Query 我们要用的 `BinnedRenderPhase` ，调用其中的 `#render` 函数，就可以进行分组的分批次的渲染。

```rust
...
fn run(...) { // impl ViewNode for MainOpaquePass3dNode
...
	let opaque_phase = ... // 从世界获得 Opaque3d 的 BinnedRenderPhase 实例（用 Resource 管理）

	// 渲染这个渲染阶段
	opaque_phase.render(&mut render_pass, world, view_entity); 
	// 返回值是一个 Result，实际使用中需要处理异常，这里简写
}
...
```

我们刚刚介绍过，`BinnedRenderPhase` 是分组数据结构。所以我们需要指定 Item 和 Key 的类型才能构建这个数据结构。对于 Bevy 的核心不透明管线来说，Key 和 Item 分别是这样的：

```rust
// Item: 需要实现 BinnedPhaseItem
pub struct Opaque3d {
		...
}
impl BinnedPhaseItem for Opaque3d {
    type BinKey = Opaque3dBinKey;
		...
}

// Key: 需要实现 Clone + Send + Sync + Eq + Ord + Hash
#[derive(Clone, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub struct Opaque3dBinKey { // Key
    pub pipeline: CachedRenderPipelineId,
    pub draw_function: DrawFunctionId,
		...
}
```

可以看到，在 `Opaque3dBinKey` 中，我们指定了 pipeline 和 draw_function 的 Id 等数据，这意味着在一个渲染阶段中我们可以使用不同的渲染管线和绘制函数。

想要给一个渲染阶段添加渲染物体，调用其 `#add` 函数即可，这是它的函数签名：

```rust
pub fn add(
    &mut self,
    key: <BPI as BinnedPhaseItem>::BinKey,
    entity: (Entity, MainEntity),
    phase_type: BinnedRenderPhaseType,
)
```

其中 MainEntity 是附加在渲染世界实体的组件，用于跟踪主世界的实体。

```rust
pub struct MainEntity(Entity);
```

## Draw trait、DrawFunctions

我们介绍了在 `BinKey` 中可以指定 `DrawFunctionId` 来区分一个渲染阶段的不同的渲染行为，这得益于 Bevy 提供了实用的渲染函数封装。在 Bevy 中，有一个声明渲染行为的 trait 叫做 `Draw`。还有一个存储 `Draw` 的实现的结构体叫`DrawFunctions<P: PhaseItem>`，是一个 `Resource`，其里面是一个 `Vec<Box<dyn Draw<P: PhaseItem>>>`。`PhaseItem` 我们上文有介绍，它存储了渲染某个物体所需的部分上下文（如 Entity 等）。

```rust
pub trait Draw<P>:
    Send
    + Sync
    + 'static
where
    P: PhaseItem,
{
    // Required method
    fn draw<'w>(
        &mut self,
        world: &'w World,
        pass: &mut TrackedRenderPass<'w>,
        view: Entity,
        item: &P,
    ) -> Result<(), DrawError>;

    // Provided method
    fn prepare(&mut self, world: &World) { ... }
}
```

## RenderCommand trait

不过我们并不直接接触 `Draw` 这个 trait。Bevy 提供了一个名为 `RenderCommand` 的封装，我们可以通过其来声明渲染行为。再将其转换为实现了 `Draw` trait 的结构体，插入 `DrawFunctions` 中。以下为 example [custom_phase_item.rs](https://github.com/bevyengine/bevy/blob/release-0.15.2/examples/shader/custom_phase_item.rs) 中的一个例子。不需要仔细阅读代码，但请仔细阅读注释。

```rust
// 声明一个新的 Command
struct DrawCustomPhaseItem;

// 为其实现 RenderCommand trait
impl<P> RenderCommand<P> for DrawCustomPhaseItem
where
    P: PhaseItem,
{
	  type Param = SRes<CustomPhaseItemBuffers>;
    type ViewQuery = ();
    type ItemQuery = ();
    fn render<'w>(...) -> RenderCommandResult {
		    // 渲染行为
				...
    }
}

// 一系列 RenderCommand 的元组被实现了 RenderCommand，
// 我们借由这种方式来将多个 Command 结合在一起
type DrawCustomPhaseItemCommands = (SetItemPipeline, DrawCustomPhaseItem);

...

fn main() {
		...
    app.get_sub_app_mut(RenderApp)
      .unwrap()
			...
			// 为 App 的 Opaque3d 渲染阶段添加 DrawCustomPhaseItemCommands
      .add_render_command::<Opaque3d, DrawCustomPhaseItemCommands>()
}
```

`#add_render_command` 是一个函数糖：它的实现是用我们传入的 `PhaseItem` 和`RenderCommand` 类型实例一个实现了 `Draw` trait 的结构体（`RenderCommandState`）。再获取世界中对应 `PhaseItem` 类型的 `DrawFunctions` 的 `Resource`，将 `RenderCommandState` 加入这个 `DrawFunctions` 中。

- 具体实现
    
    ```rust
    impl AddRenderCommand for SubApp {
        fn add_render_command<P: PhaseItem, C: RenderCommand<P> + Send + Sync + 'static>(
            &mut self,
        ) -> &mut Self
        where
            C::Param: ReadOnlySystemParam,
        {
            let draw_function = RenderCommandState::<P, C>::new(self.world_mut());
            let draw_functions = self
                .world()
                .get_resource::<DrawFunctions<P>>()
                .unwrap_or_else(|| {
                    panic!(
                        "DrawFunctions<{}> must be added to the world as a resource \
                         before adding render commands to it",
                        core::any::type_name::<P>(),
                    );
                });
            draw_functions.write().add_with::<C, _>(draw_function);
            self
        }
    }
    ```
    

<aside>
👉

想要获得一个`DrawFunctionId` 我们有两种方式：

- 调用 `DrawFunctions#write().add<D: Draw>(a: D)` 后会返回一个 `DrawFunctionId`，我们手工维护这个 Id 如用 Resource 等
- 调用 `DrawFunctions#write().add_with::<T: 'static, D: Draw>(a: D)` 后，其将一个类型 `T` （的 `TypeId`） 作为这个`DrawFunctionId`的索引。我们之后就可以通过`DrawFunctions#write().id::<T>()` 随时获得这个类型对应的 `DrawFunctionId`

第二种方式更加常用

</aside>

## 如何渲染一个渲染阶段

一个渲染阶段实际上还是在一个 Node 中被渲染的。`BinnedRenderPhase` 提供了 `#render` 函数。在一个 Node 中调用 `BinnedRenderPhase#render` 即为执行 `BinnedRenderPhase` 的渲染。下为 `#render` 函数的函数签名。

```rust
pub fn render<'w>(
    &self,
    render_pass: &mut TrackedRenderPass<'w>,
    world: &'w World,
    view: Entity,
) -> Result<(), DrawError>
```

参考阅读 
[https://github.com/bevyengine/bevy/blob/release-0.15.2/crates/bevy_core_pipeline/src/core_3d/main_opaque_pass_3d_node.rs](https://github.com/bevyengine/bevy/blob/release-0.15.2/crates/bevy_core_pipeline/src/core_3d/main_opaque_pass_3d_node.rs)

## 其它的渲染阶段结构体

除了 BinnedRenderPhase，Bevy 还有其它渲染阶段的结构。

| RenderPhase | 介绍 |
| --- | --- |
| BinnedRenderPhase | 采用 Bin 的数据结构存储 PhaseItem |
| SortedRenderPhase | 采用 Vec 存储 PhaseItem，可排序 |

# 使用 bevy_render

我们梳理一下，要想加入自定义的渲染效果，有这样的情况：

- 如果要在某个 Bevy 中已有的渲染阶段中插入一个自定义渲染行为，我们需要
    - 创建或实现自己的 `RenderCommand`、`Pipeline` 等渲染所需资源
    - 加入一个 queue 阶段执行的 system，在这个 system 中，我们给我们想要的渲染阶段加入 `PhaseItem`
    - 参考 [https://github.com/bevyengine/bevy/blob/release-0.15.2/examples/shader/custom_phase_item.rs](https://github.com/bevyengine/bevy/blob/release-0.15.2/examples/shader/custom_phase_item.rs)
- 如果我们的渲染行为不和任何现有的渲染阶段共享，比如后处理效果，我们需要
    - 实现自己的 ViewNode
    - 在 App 中添加 Nodes 和 Edges

# 参考阅读

## Extract

- ExtractSchedule：[https://docs.rs/bevy/latest/bevy/prelude/struct.ExtractSchedule.html](https://docs.rs/bevy/latest/bevy/prelude/struct.ExtractSchedule.html)
- ExtractComponent： [https://docs.rs/bevy/latest/bevy/render/extract_component/index.html](https://docs.rs/bevy/latest/bevy/render/extract_component/index.html)
- extract 相机到 `ExtractedCamera` 的实现：[https://github.com/bevyengine/bevy/blob/main/crates/bevy_render/src/camera/camera.rs](https://github.com/bevyengine/bevy/blob/main/crates/bevy_render/src/camera/camera.rs)
