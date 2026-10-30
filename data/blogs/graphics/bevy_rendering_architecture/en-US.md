```toml
title = "An Overview of the Bevy Engine's Rendering Architecture"
summary = "This article briefly introduces Bevy's rendering architecture, including the directed acyclic graph that manages rendering order, System ordering in the rendering workflow, render phases for different objects, and interactions between the main world and the render world. It uses Bevy Core 3D's rendering implementation as a reference. It also briefly covers how to use Bevy's rendering APIs in everyday development."
ai-translation = true
```

# Preface

This article uses `#` to indicate function names, such as `#foo`, and `A#foo` to indicate the function `foo` on type `A`. Bevy wraps the graphics API, providing APIs that correspond one-to-one with wgpu. This article won't go into that part in detail.

# The render App and world

Rendering in Bevy happens in the render `App`, which is a `SubApp` of the `App` we create. The two can run on different threads.

```rust
fn build(&self, app: &mut App) {
	...
	let render_app = match app.get_sub_app_mut(RenderApp) {
    Ok(render_app) => render_app,
    Err(_) => return,
	};
}
```

# Render sets

In Bevy, rendering-related Systems execute in the `Render` Schedule. Rendering preparation and execution happen in order, and Bevy provides `RenderSet` as a marker. Each frame, Systems marked with a `RenderSet` execute in the order of the `RenderSet` enum:

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

In practice, we can use `in_set` to assign a `RenderSet` to a System.

```rust
render_app.add_systems(Render, custom_system.in_set(RenderSet::Queue));
```

# The data structure that manages the rendering workflow

Rendering a frame involves many Passes. Different Passes execute in different orders, and other programs may need to insert new Passes between those in an existing library. We also want to keep concerns separate when writing rendering behavior. For example, when writing a Shadow PrePass, we shouldn't need to think about how to write the main Pass. With these requirements in mind, we can imagine needing a data structure that stores rendering behaviors while managing and adjusting their order. In Bevy, this data structure is a directed graph called `RenderGraph (struct)`, which we'll call the render graph below. Each node in the render graph is simply called a `Node (trait)` and is responsible for carrying out rendering behavior. The structure that declares ordering between nodes is `Edge`. The render graph also lets us store certain rendering-related data through `Slot`. A Slot can store a Buffer, TextureView, Sampler, Entity, and so on.

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

The `#run` function in the `Node` trait is where rendering commands execute, such as creating a `RenderPass` and issuing `draw` commands. `input` and `output` define information about the inputs and outputs needed during `#run`, which can be accessed through `RenderGraphContext` in `#run`. The default implementations of these two functions just return an empty `Vec`, and they aren't implemented most of the time. We won't worry about them for now.

To add a `Node` to an App, we can use `#add_render_graph_node`.

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

It takes a `RenderSubGraph` and a `RenderLabel`. These are both marker traits: the former marks a render subgraph, and the latter marks a Node. Defining your own `RenderLabel` is simple; Bevy provides a procedural macro of the same name.

```rust
// * 实现 RenderLable 需要实现 Debug, Clone, Eq
#[derive(Debug, Clone, Hash, PartialEq, Eq, RenderLabel)]
pub struct StartFooPass;
```

Also, notice that when adding a Node in the code above, we pass only its type, not a struct instance. That's because `#add_render_graph_node` requires the Node type to implement the `FromWorld` trait. `FromWorld` supports creating an instance of a type from the world, so we don't have to add an instance manually.

Before continuing, let's introduce a concept: View.

### View

In graphics APIs (at least in wgpu), the target of a rendering operation is a View of a texture, which represents a state of that texture. In Bevy, Views are managed using `Entity` and the `ViewTarget` component. We pass the `Entity` around. When we need the View, we query its `ViewTarget`, get the View, and call its `#get_color_attachment`.

### ViewNode

`Node` is a `trait`. The `Node` implementation we most commonly use in Bevy is `ViewNodeRunner<N: ViewNode> (struct)`. Here, `ViewNode` is an easier-to-use `trait` that can retrieve View context from the `world`. It's also one of the traits we most commonly implement when adding rendering content.

Its contents make it easy to understand:

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

> Note: `ViewNode` isn't an implementation of Node; `ViewNodeRunner` is.
> 

`ViewQuery` is the Query type for the entity carrying `ViewTarget` (or something else that can provide a View) that we want to query.

Let's go through the parameters in `#run`:

- `render_context` contains rendering context such as RenderDevice and CommandEncoder.
- `graph` contains various context information about our current render graph.
- `view_query` is the result of the View Query we requested.

Note that if a `ViewNode` implements `FromWorld`, its `ViewNodeRunner` automatically implements `FromWorld` too. So, to allow a `ViewNodeRunner` to be inserted into the App, remember to implement `FromWorld` for the ViewNode type.

<aside>
💡

Types that implement `Default` automatically implement `FromWorld`.

</aside>

### A ViewNode implementation example

The opaque Pass Node in Bevy's core render pipeline queries ExtractedCamera and other rendering-related components.

<aside>
👉

`ExtractedCamera` is a camera component in the render world that provides the information needed for rendering. In Bevy, *Extract* refers to the process of extracting data from the main world into the render world. Our main camera entity and its components are queried by a system in the `ExtractSchedule` stage, then instantiated as an `ExtractedCamera` and inserted into the camera entity in the render world.

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

# Render phases

Interacting directly with `RenderGraph` and `Node` is low-level and flexible, but not so good for ergonomics and extensibility. Bevy provides a better data structure for managing what gets rendered: `BinnedRenderPhase`.

<aside>
💡

A bin is a “bucket,” and “binned” means a data structure divided into buckets, or groups. We store elements in groups. An intuitive example of a binned data structure is `HashMap`: the hash value is the bucket's index, and each hash value corresponds to an array, or bucket. This gives elements with the same hash value somewhere to go.

</aside>

This data structure is somewhat complex, and explaining its implementation would take some time. Let's first get an intuitive understanding of what it is:

- It isn't a Node.
- It's a data structure that manages the context of objects being rendered.
- It can manage this information:
    - The list of entities to render.
    - The behavior used to render those entities (draw functions).
    - The order in which the entities are rendered.
- During a render phase, all rendering commands use the same `RenderPass`; it can't be switched partway through.
- An entity can appear multiple times in a `BinnedRenderPhase`, for example when the same entity is drawn in different batches.

Once all the preparation is done, using it is simple. We just query the `BinnedRenderPhase` we need in a Node and call its `#render` function to render in groups and batches.

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

As we just discussed, `BinnedRenderPhase` is a grouped data structure, so we need to specify Item and Key types to construct it. For Bevy's core opaque pipeline, the Key and Item look like this:

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

As you can see, `Opaque3dBinKey` specifies data such as the pipeline and draw_function IDs. This means we can use different render pipelines and draw functions within a single render phase.

To add an object to a render phase, just call its `#add` function. Here's the function signature:

```rust
pub fn add(
    &mut self,
    key: <BPI as BinnedPhaseItem>::BinKey,
    entity: (Entity, MainEntity),
    phase_type: BinnedRenderPhaseType,
)
```

Here, MainEntity is a component attached to an entity in the render world to track its entity in the main world.

```rust
pub struct MainEntity(Entity);
```

## The Draw trait and DrawFunctions

We mentioned that `DrawFunctionId` in `BinKey` can distinguish different rendering behaviors within a render phase. This is possible because Bevy provides a useful abstraction for draw functions. Bevy has a trait called `Draw` that declares rendering behavior. It also has a struct called `DrawFunctions<P: PhaseItem>` that stores `Draw` implementations. It's a `Resource` containing a `Vec<Box<dyn Draw<P: PhaseItem>>>`. We introduced `PhaseItem` above; it stores some of the context needed to render an object (such as its Entity).

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

## The RenderCommand trait

We don't interact with the `Draw` trait directly, though. Bevy provides an abstraction called `RenderCommand` that we can use to declare rendering behavior. We then convert it into a struct implementing the `Draw` trait and insert it into `DrawFunctions`. Below is an example from [custom_phase_item.rs](https://github.com/bevyengine/bevy/blob/release-0.15.2/examples/shader/custom_phase_item.rs). You don't need to read the code closely, but please read the comments carefully.

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

`#add_render_command` is a convenience function. Its implementation uses the `PhaseItem` and `RenderCommand` types we pass in to instantiate a struct implementing the `Draw` trait (`RenderCommandState`). It then retrieves the `DrawFunctions` Resource for the corresponding `PhaseItem` type from the world and adds `RenderCommandState` to that `DrawFunctions`.

- Implementation details
    
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

There are two ways to obtain a `DrawFunctionId`:

- Calling `DrawFunctions#write().add<D: Draw>(a: D)` returns a `DrawFunctionId`, which we maintain manually, for example in a Resource.
- Calling `DrawFunctions#write().add_with::<T: 'static, D: Draw>(a: D)` uses a type `T` (its `TypeId`) as an index for this `DrawFunctionId`. We can then retrieve the `DrawFunctionId` corresponding to that type at any time through `DrawFunctions#write().id::<T>()`.

The second approach is more common.

</aside>

## How to render a render phase

A render phase is still rendered within a Node. `BinnedRenderPhase` provides a `#render` function. Calling `BinnedRenderPhase#render` in a Node performs the rendering for that `BinnedRenderPhase`. Below is the signature of `#render`.

```rust
pub fn render<'w>(
    &self,
    render_pass: &mut TrackedRenderPass<'w>,
    world: &'w World,
    view: Entity,
) -> Result<(), DrawError>
```

Further reading 
[https://github.com/bevyengine/bevy/blob/release-0.15.2/crates/bevy_core_pipeline/src/core_3d/main_opaque_pass_3d_node.rs](https://github.com/bevyengine/bevy/blob/release-0.15.2/crates/bevy_core_pipeline/src/core_3d/main_opaque_pass_3d_node.rs)

## Other render phase structs

Besides BinnedRenderPhase, Bevy has other render phase structures.

| RenderPhase | Description |
| --- | --- |
| BinnedRenderPhase | Stores PhaseItems using a binned data structure |
| SortedRenderPhase | Stores PhaseItems in a Vec; supports sorting |

# Using bevy_render

Let's go over the cases we face when adding custom rendering effects:

- To insert custom rendering behavior into an existing Bevy render phase, we need to:
    - Create or implement our own `RenderCommand`, `Pipeline`, and other resources needed for rendering.
    - Add a system that runs in the queue stage. In this system, we add a `PhaseItem` to the render phase we want.
    - See [https://github.com/bevyengine/bevy/blob/release-0.15.2/examples/shader/custom_phase_item.rs](https://github.com/bevyengine/bevy/blob/release-0.15.2/examples/shader/custom_phase_item.rs).
- If our rendering behavior doesn't share any existing render phase, as with a post-processing effect, we need to:
    - Implement our own ViewNode.
    - Add Nodes and Edges to the App.

# Further reading

## Extract

- ExtractSchedule: [https://docs.rs/bevy/latest/bevy/prelude/struct.ExtractSchedule.html](https://docs.rs/bevy/latest/bevy/prelude/struct.ExtractSchedule.html)
- ExtractComponent: [https://docs.rs/bevy/latest/bevy/render/extract_component/index.html](https://docs.rs/bevy/latest/bevy/render/extract_component/index.html)
- Implementation of extracting a camera into `ExtractedCamera`: [https://github.com/bevyengine/bevy/blob/main/crates/bevy_render/src/camera/camera.rs](https://github.com/bevyengine/bevy/blob/main/crates/bevy_render/src/camera/camera.rs)
