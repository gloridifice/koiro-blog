```toml
title="管线重构、Input、Egui Tiles、Shadow Mapping"
work="Wgpu PBR"
index=1
published=true
preview="shadow_mapping.jpg"
date="2024-12-25"
```

![模型 [https://sketchfab.com/3d-models/mkr-lykken-91b274a40ebe46cd931235ac32ae0492](https://sketchfab.com/3d-models/mkr-lykken-91b274a40ebe46cd931235ac32ae0492)](%E7%AE%A1%E7%BA%BF%E9%87%8D%E6%9E%84%E3%80%81Input%E3%80%81Egui%20Tiles%E3%80%81Shadow%20Mapping/image.jpg)

模型 [https://sketchfab.com/3d-models/mkr-lykken-91b274a40ebe46cd931235ac32ae0492](https://sketchfab.com/3d-models/mkr-lykken-91b274a40ebe46cd931235ac32ae0492)

箭头代表平行光的位置和方向（黑紫色的材质说明 Default Material 正常工作）。

# 渲染管线重构

原来的 Bind Group 管理有很大的问题。在我的电脑上 Bind Grop 最多有 4 个。 重构的第一件事是让 Bind Group 的安排变得更接近现代游戏引擎了。现在的 Bind Group 如下表。

| BindGroup | 说明 | 目前内容 |
| --- | --- | --- |
| 0: Global | 全局资源，例如相机、平行光、Shadow map | [0] Camera Buffer
[1] Global Light Uniform
[2] Shadow Map Texture2D
[3] Shadow Map Sampler |
| 1: Material | 材质的资源，例如贴图等 | [0] Texture 0
[1] Sampler 0 |
| 2: Object | 每个 MeshRenderer 的资源，例如 Transform | [0] Transform Buffer |

## Target

现在渲染的图像输出到了一张贴图上，用 Bevy 中的 Resource 管理。也因此能实现渲染到 Egui 的图像上。目前渲染的 Target 分两张 Color 和 Depth.

## 用于快速书写 BindGroup 的宏

为了快速书写 BindGroup 和 BindGroupLayout，写了两个声明宏。

### BindGroupLayout

```rust
#[macro_export]
macro_rules! bg_layout_descriptor {
    ([$name:literal] $($i:literal: $vis:expr => $c:expr;)*) => {
        wgpu::BindGroupLayoutDescriptor {
            label: Some($name),
            entries: &[
                $($c.into_bgl_entry($i, $vis),)*
            ]
        }
    };

    () => ()
}
```

```rust
let material_bind_group_layout =
	Arc::new(device.create_bind_group_layout(&bg_layout_descriptor!(
		["Material Bind Group Layout"]
		0: ShaderStages::FRAGMENT => BGLEntry::Tex2D(false, TextureSampleType::Float { filterable: true });
		1: ShaderStages::FRAGMENT => BGLEntry::Sampler(SamplerBindingType::Filtering);
)));
```

其中，为了便于书写，我将 `BindGroupEntry` 封装成了一个枚举类型 `BGLEntry` 。

### BindGroup

```rust
#[macro_export]
macro_rules! bg_descriptor {
    ([$name:literal] [$layout:expr] $($i:literal: $c:expr;)*) => {
        wgpu::BindGroupDescriptor {
            label: Some($name),
            layout: $layout,
            entries: &[
                $(wgpu::BindGroupEntry{
                    binding: $i,
                    resource: $c,
                },)*
            ]
        }
    };
}
```

```rust
let bind_group_layout =
	Arc::new(device.create_bind_group_layout(&bg_layout_descriptor! (
		["Global Bind Group Layout"]
		0: ShaderStages::VERTEX => BGLEntry::UniformBuffer(); // Camera Uniform
		1: ShaderStages::all() => BGLEntry::UniformBuffer(); // Global Light Uniform
		2: ShaderStages::FRAGMENT => BGLEntry::Tex2D(false, TextureSampleType::Depth); // Shadow Map
		3: ShaderStages::FRAGMENT => BGLEntry::Sampler(SamplerBindingType::Comparison); // Shadow Map
)));
```

# 简单的输入 Input

现在实现了一个简单的输入封装，由一个叫 Input 的 Resource 管理。可以用 Unity Input 的风格来检测键盘输入、鼠标位移、鼠标位置和鼠标按键。

```rust
pub struct Input {
    pub down_keys: HashSet<KeyCode>,
    pub hold_keys: HashSet<KeyCode>,
    pub up_keys: HashSet<KeyCode>,
    pub last_cursor_position: Vec2,
    pub cursor_position: Vec2,
    pub cursor_offset: Vec2,
    pub down_cursor_buttons: HashSet<CursorButton>
}
```

用起来是这样：

```rust
    let mut move_vec = Vector3::new(0., 0., 0.);
    if input.is_key_hold(KeyCode::KeyW) {
        move_vec += Vector3::new(0.0, 0.0, -1.0);
    }
		...
    if input.is_key_hold(KeyCode::Space) {
        if input.is_key_hold(KeyCode::ShiftLeft) {
            move_vec += Vector3::new(0.0, -1.0, 0.0);
        } else {
            move_vec += Vector3::new(0.0, 1.0, 1.0);
        }
    }
    let delta_time_sec = time.delta_time.as_secs_f32();
    if move_vec != Vector3::new(0., 0., 0.) {
        move_vec =
            cam_transform.rotation.rotate_vector(move_vec.normalize()) * speed * delta_time_sec;
        cam_transform.position += move_vec;
    }
```

我们的 render 和各类 update 函数调用发生在在 winit 中的 `RedrawRequested` 事件时，而各类输入事件与 `RedrawRequested` 事件同属于一个枚举，它们可能在两次 `RedrawRequested` 中间。因此如果我们想保证在 update 中使用 Input 是稳定的，我们的 Input 一部分需要在事件更新中更新（WindowEvent），一部分要在各种 update 中更新。如下表。

| 更新时刻 | 更新内容 |
| --- | --- |
| WindowEvent | 插入 down_keys, up_keys，插入和删除 hold_keys |
| pre_update | 更新 cursor_offset, last_cursor_position |
| post_update | 清除 down_keys, up_keys |

# Egui Tiles

![图来自官方 github 页 [https://github.com/rerun-io/egui_tiles](https://github.com/rerun-io/egui_tiles)](%E7%AE%A1%E7%BA%BF%E9%87%8D%E6%9E%84%E3%80%81Input%E3%80%81Egui%20Tiles%E3%80%81Shadow%20Mapping/image%201.jpg)

图来自官方 github 页 [https://github.com/rerun-io/egui_tiles](https://github.com/rerun-io/egui_tiles)

[egui_tiles](https://github.com/rerun-io/egui_tiles) 是一个为 egui GUI 库实现标签页功能的库。与之功能类似的库还有 [egui_dock](https://github.com/Adanos020/egui_dock)，我此前使用过，感觉不错。为了尝试新的库，还是在这个项目使用了 egui_tiles. 使用下来感觉还是 egui_dock 更好上手一些，提供的功能也更丰富。

在本开发日志的头图上你已经可以看到程序有侧边栏和标签页用于控制相机和灯光的参数了。

egui_tiles 将每一个有内容的部分称为 Pane，将标签页称为 Tab，每个标签页有不同的布局。而整个标签页的树的上下文称为 Behavior. Behavior 同时是一个 trait，我们每个 Pane 的 ui 行为要在其实现中书写。我们可以枚举的方式声明 Pane. 例如：

```rust
pub enum MyPane {
    MainView,
    ControlPanel,
}
```

我们在实现 Behavior 需要确定 Pane 泛型（下面的示例中为 `MyPane`）。

```rust
struct MyBehavior<'a> {
    world: &'a mut World, //这里的 World 是 bevy_ecs 中的 Wrold，我们实现 ui 的时候需要用它
}

impl<'a> egui_tiles::Behavior<MyPane> for MyBehavior<'a> {
    fn pane_ui(&mut self, ui: &mut egui::Ui, ..., pane: &mut MyPane) -> egui_tiles::UiResponse {
        match pane {
	        ...
        };
        egui_tiles::UiResponse::None
    }
    
    fn tab_title_for_pane(&mut self, pane: &Pane) -> egui::WidgetText {
        match pane {
            Pane::MainView => "Main View".into(),
            Pane::ControlPanel => "Control Panel".into(),
        }
    }
}
```

同时我们还需要定义我们的标签页们的树结构：

```rust
fn create_tree() -> egui_tiles::Tree<MyPane> {
    let mut tiles = egui_tiles::Tiles::default();

    let mut left_tabs_id_vec = vec![];
    let control_pane = tiles.insert_pane(MyPane::ControlPanel);
    let main_view_pane = tiles.insert_pane(MyPane::MainView);
    left_tabs_id_vec.push(tiles.insert_vertical_tile(vec![control_pane]));
    left_tabs_id_vec.push(tiles.insert_vertical_tile(vec![main_view_pane]));

    let left_tabs = tiles.insert_tab_tile(left_tabs_id_vec);

    let root = tiles.insert_horizontal_tile(vec![left_tabs]);

    egui_tiles::Tree::new("main_tree", root, tiles)
}
```

最后在需要的位置调用渲染就可以了：

```rust
...
let mut tree = create_tree();
let mut behavior = TreeBehavior { world };
tree.ui(&mut behavior, ui);
...
```

更多使用案例见 egui_tiles github 页的 [examples](https://github.com/rerun-io/egui_tiles/tree/main/examples).

# Shadow Mapping

Shadow Mapping 的基本原理是在前一 Pass 从灯光的视角渲染一张深度图，再在主 Pass 中通过用这张深度图采样，获得从灯光看向世界时的片源的最浅深度。将这个最浅深度与某个片源在灯光空间的实际深度对比。如果实际深度更深说明其在阴影中。理论和实践都可以参考该文章：[https://learnopengl.com/Advanced-Lighting/Shadows/Shadow-Mapping](https://learnopengl.com/Advanced-Lighting/Shadows/Shadow-Mapping)

简单说一下我的实践过程和踩的坑。

首先要为管线增加一个新的 Pass 渲染灯光空间的深度图，在这个 Pass 使用灯光的矩阵和物体的 Transform Buffer. 这个过程需要计算灯光的变换矩阵（View Projection 后文简称 VP），我踩了第一个坑。我用的数学库 cgmath 是为 OpenGL 设计的，在 OpenGL 中矩阵是 column major 的，但是 wgpu 的矩阵默认是 row major 的。这导致我传入着色器的矩阵始终是有问题的。好在最后发现了，转置一下即可。

> 关于计算机矩阵表达的 Column Major 和 Row Major 可以见这篇文章：[http://davidlively.com/programming/graphics/opengl-matrices/row-major-vs-column-major/](http://davidlively.com/programming/graphics/opengl-matrices/row-major-vs-column-major/)
> 

接着就是在主 Pass 中渲染 Shadow 了。除了要传 Shadow Map 的贴图和 Sampler 外，还有传灯光的表换矩阵。我在 `VertexOutput` 中新增一个字段 `light_space_clip_pos`.

```rust
...
out.light_space_clip_pos = light.view_proj * vec4<f32>(out.world_pos, 1.0);
...
```

然后在片源着色器中，可以通过 `light_space_clip_pos` 计算出采样 Shadow Map 的 uv 和当前片源在灯光空间的深度。

```rust
...
var light_space_pos = in.light_space_clip_pos;
var proj_coords = light_space_pos.xyz / light_space_pos.w;

let flip_correction = vec2<f32>(0.5, -0.5);

var uv = proj_coords.xy * flip_correction + vec2<f32>(0.5); // reverse y and map [-1, 1] to [0, 1]
var shadow = textureSampleCompare(tex_shadow_map, samp_shadow_map, uv, proj_coords.z);
...
```

这里需要 flip_correction 将 y 反转，这是我踩第二个坑的地方。因为 NDC 的原点在左下角 y 朝上，而材质 uv 的原点是从左上角，y 朝下，因此需要反转一下。乘 0.5 和加 0.5 将 NDC 的 xy 范围 [-1,1] 映射 uv 的范围 [0,1]. Wgpu 的 NDC 的 z 的范围是 [0,1]，因此不需要做额外处理。将其处以 w 后的值作为当前片源在相机空间下的深度。

有关管线、采样器、深度贴图的配置等，Wgpu 的 example 提供了 shadow 实现，可以阅读： [https://github.com/gfx-rs/wgpu/tree/trunk/examples/src/shadow](https://github.com/gfx-rs/wgpu/tree/trunk/examples/src/shadow)

# Still a lot of problem

我本以为 Shadow Mapping 是早发明且成熟的技术，结果发现想要实现效果好还是要做很多工作。目前我只实现了平行光的 Shadow Mapping. 后续还要优化，列一下我还需要做的内容：

- Cascaded Shadow Mapping (CSM)：
CSM 将玩家能看到的视野范围分为多个部分，这些部分分别用单独的 Shadow Map 覆盖。旨在解决平行光 Shadow Mapping 的视角问题、利用率问题和精度问题。
[https://learnopengl.com/Guest-Articles/2021/CSM](https://learnopengl.com/Guest-Articles/2021/CSM)
- 多光源 Shadow Mapping：
用 texture array 实现就好了，每个光源对应一张 Shadow Map. Wgpu 的 [examples 中的 shadow](https://github.com/gfx-rs/wgpu/tree/trunk/examples/src/shadow) 就是一个多光源实现，很好的参考。
- 多光源类型：
各种光源类型下的投影矩阵如何设置也是件需要花一些时间研究的事。
- Percentage-Closer Soft Shadows (PCSS):
随距离变化的软阴影。
- 反走样：
目前我还没有太多了解，之后会查一些资料。实践后切身认识到 Shadow Mapping 的反走样重要性，目前的程序时常走样地可怕。
    
    ![image.png](%E7%AE%A1%E7%BA%BF%E9%87%8D%E6%9E%84%E3%80%81Input%E3%80%81Egui%20Tiles%E3%80%81Shadow%20Mapping/image%202.jpg)
    

## Next Station

不过我在 Shadow Mapping 上耗费了太多精力（因为一些对库和矩阵不熟悉导致的问题）。打算先放一放，做一些其它工作换换口味比较好。最近的想法是先将 SSAO (Screen Space Ambient Occlusion) 实现了吧！如果可以，也修一下法线变换问题吧 [[Transforming Normal](https://terathon.com/blog/transforming-normals.html)]，修完法线后说不定可以把 HBAO (Horizon-Based Ambient Occlusion) 实现了。做 SSAO 要写一下后处理管线，那么之后可以把 MSAA 实现了。