```toml
title="渲染管线、Depth、Camera、Light、Transform、bevy_ecs"
work="Wgpu PBR"
index=0
published=true
preview="Nov-13-2024_19-17-11.gif"
date="2024-11-14T02:14:00"
```

![Nov-13-2024 19-17-11.gif](%E6%B8%B2%E6%9F%93%E7%AE%A1%E7%BA%BF%E3%80%81Depth%E3%80%81Camera%E3%80%81Light%E3%80%81Transform%E3%80%81bevy_ecs/Nov-13-2024_19-17-11.gif)

Wgpu PBR 是一个旨在用 wgpu 和 rust 建立一个 PBR 渲染管线渲染器的练习项目。未来可能会实现各种其它的图形学效果。

其目前使用 Egui 作为 UI，bevy_ecs 库管理逻辑。

> 现阶段展示出的代码为早期开发版本，未来大概率都会被重构
> 

# Mesh

加载好的模型是如下的包含关系：

Model → Mesh → Primitive.

Primitive 虽然是模型的最小单位，但是不能被单独渲染，只能在一个 Mesh 被渲染时被渲染。Primitive 是也承载材质的单位。

## 模型的数据结构

- Model
    - UploadedMesh (Vec<_>)
        - Vertex buffer
        - Index buffer
        - UploadedPrimitive (Vec<_>)
            - indices_start
            - indices_num
            - material_instance

目前支持加载 glb 格式的模型。

# Material

Material 的编程范式本质是组织和管理 Pipeline 和 BindGroup（同时还存储相关的 Layout）。目前项目的材质的 BindGroupLayout 如下：

1. Transform
2. Camera
3. Light
4. [Material‘s]
    1. Texture

Material 范式在这个项目目前是用 Material 和 Material Instance 两个结构体来实现的。

一个 Material 提供 `Pipeline` 和通用的 `BindGroup` （如 Camera, Light）和定义及存储相关的 `Layout`，而 Instance 则提供 Instance 之间会有差异的 `BindGroup` （如 Texture）。

- Transform 的 `BindGroup` 由 `MeshRenderer` 提供。

# Texture

可见 [https://sotrh.github.io/learn-wgpu/beginner/tutorial5-textures/](https://sotrh.github.io/learn-wgpu/beginner/tutorial5-textures/)

```rust
pub struct UploadedImage {
    pub size: wgpu::Extent3d,
    pub texture: Texture,
    pub view: TextureView,
    pub sampler: Sampler,
}
```

# 实现 Camera

数学库使用 cgmath，Camera 的 View 矩阵 Projection 矩阵可以用 cgmath 快速计算。

```rust
impl Camera{
		...
    pub fn build_view_projection_matrix(&self) -> Matrix4<f32> {
        let view = Matrix4::look_at_rh(self.eye, self.target, self.up);
        let proj = perspective(cgmath::Deg(self.fovy), self.aspect, self.znear, self.zfar);
        return OPENGL_TO_WGPU_MATRIX * proj * view;
    }
}

#[rustfmt::skip]
pub const OPENGL_TO_WGPU_MATRIX: cgmath::Matrix4<f32> = cgmath::Matrix4::new(
    1.0, 0.0, 0.0, 0.0,
    0.0, 1.0, 0.0, 0.0,
    0.0, 0.0, 0.5, 0.5,
    0.0, 0.0, 0.0, 1.0,
);
```

因为 OpenGL 中标准化设备坐标（NDC）的深度范围 (z) 为 [-1, 1]，而 wpgu/Vulkan/DX/Mental 中的标准化设备坐标的深度范围为 [0, 1]，cgmath 生成的投影矩阵符合 OpenGL 规范，所以需要一个转换矩阵将深度映射到 [0, 1]。

## RenderCamera

相机相关的 Buffer, BindGroupLayout, BindGroup 存放在 `RenderCamera` 的 bevy 中的 Resource 管理。在 RenderCamera 初始化时一起生成。

```rust
#[derive(Resource)]
pub struct RenderCamera {
    pub camera: Camera,
    pub buffer: Arc<wgpu::Buffer>,
    pub bind_group_layout: Arc<BindGroupLayout>,
    pub bind_group: Arc<BindGroup>,
}
```

```rust
impl RenderCamera {
    pub fn new(device: &wgpu::Device, aspect: f32) -> RenderCamera {
        let camera = Camera::new(aspect);
        let camera_buffer = device.create_buffer_init(&wgpu::util::BufferInitDescriptor {
					...
        });
        let camera_bind_group_layout =
            Arc::new(device.create_bind_group_layout(&CameraUniform::layout_desc()));
        let camera_bind_group = Arc::new(device.create_bind_group(&wgpu::BindGroupDescriptor {
					...
        }));
        RenderCamera {
            camera,
            buffer: Arc::new(camera_buffer),
            bind_group_layout: camera_bind_group_layout,
            bind_group: camera_bind_group,
        }
    }
}
```

# 实现 Transform

> 仅为实现方便才这样实现，不值得参考，寻找参考请见 Bevy 的 Transform 逻辑实现
> 

逻辑上的 Transform 如下：

```rust
#[derive(Component)]
pub struct Transform {
    pub parent: Option<Entity>,
    pub children: Vec<Entity>,
    pub position: Point3<f32>,
    pub rotation: Quaternion<f32>,
    pub scale: Vector3<f32>,
}
```

渲染上，目前 Transform 用 uniform 实现。被传递到 GPU 的 Uniform Buffer 包含一个模型矩阵（用于变换顶点位置）和一个模型的旋转矩阵（用于变换顶点法线）。

## 数据大小对齐

将数据传递到 gpu 需要对其大小 alignment， 遵循标准布局规则 (std140 或 std430)。在 wgsl 中，其大小被视为如下，用棕色标注了类型大小与 rust 中不同的情况：

| 数据类型 <f32> | f32 | vec2 | vec3 | vec4 | mat3x3 | mat4x4 |
| --- | --- | --- | --- | --- | --- | --- |
| 大小 / 字节 | 4 | 8 | 16 | 16 | 48 (3 * vec3) | 64 (4 * vec3) |

此外，传入 Buffer 的大小必须时 16 的倍数。因此声明 padding 变量对其数据。

```rust
pub struct TransformUniform {
    pub model: [[f32; 4]; 4],
    pub rotation: [[f32; 3]; 3],
    pub padding: [f32; 3],
}
```

## 封装

每个有 Transform 的要被渲染的模型，都有独立的 Transform Buffer 和 BindGroup，所以它们被一起封装到了一个叫 `MeshRenderer` 的组件中。

```rust
#[derive(Component, Default)]
pub struct MeshRenderer {
    pub mesh: Option<Arc<UploadedMesh>>,
    pub transform_bind_group: Option<Arc<BindGroup>>,
    pub transform_buffer: Option<Arc<Buffer>>,
}
```

# 实现平行光

与 Camera 的实现类似，采用一个 RenderLight 管理。目前只包含一个平行光。

传入 GPU 的 LightUniform 结构如下。是否要传 intensity 有待研究。

```rust
#[repr(C, align(16))]
#[derive(Debug, Clone, Copy)]
pub struct LightUniform {
    pub direction: [f32; 3],
    pub padding1: f32,
    pub color: [f32; 4],
    pub intensity: f32,
    pub padding2: [f32; 3],
}
```

# Engine Lifetime

游戏目前采用一套临时的生命周期

```rust
input()

handle_redraw()
	pre_update() // 更新 Time
	update() //游戏逻辑
	post_update() // 更新 Unifoms to GPU
	render() // 渲染
```

# 其它

- 相机的深度贴图被写在 depth_texture 中，未来会用到。
- 一个简单的 Input 用于记录输入
- 一个简单的 Time 用于记录 delta time

# 相关链接

【Wgpu 入门资料】[https://sotrh.github.io/learn-wgpu/](https://sotrh.github.io/learn-wgpu/)

【Vulkan 入门资料】[https://vulkan-tutorial.com/](https://vulkan-tutorial.com/)

【以开发游戏引擎的方式学习 Vulkan】[https://vkguide.dev/](https://vkguide.dev/)

【PBR 理论】[https://learnopengl.com/PBR/Theory](https://learnopengl.com/PBR/Theory)

【Bevy Engine】[https://bevyengine.org/](https://bevyengine.org/)

【Egui】[https://www.egui.rs/](https://www.egui.rs/)

【使用模型】[https://sketchfab.com/3d-models/mkr-lykken-91b274a40ebe46cd931235ac32ae0492](https://sketchfab.com/3d-models/mkr-lykken-91b274a40ebe46cd931235ac32ae0492)