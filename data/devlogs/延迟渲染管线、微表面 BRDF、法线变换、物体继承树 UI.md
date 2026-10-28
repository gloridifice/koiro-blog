```toml
title="延迟渲染管线、微表面 BRDF、法线变换、物体继承树 UI"
work="Wgpu PBR"
index=2
published=true
preview="Jan-29-2025_01-13-05.gif"
date="2025-01-25"
```

![Jan-29-2025 01-13-05.gif](%E5%BB%B6%E8%BF%9F%E6%B8%B2%E6%9F%93%E7%AE%A1%E7%BA%BF%E3%80%81%E5%BE%AE%E8%A1%A8%E9%9D%A2%20BRDF%E3%80%81%E6%B3%95%E7%BA%BF%E5%8F%98%E6%8D%A2%E3%80%81%E7%89%A9%E4%BD%93%E7%BB%A7%E6%89%BF%E6%A0%91%20UI/Jan-29-2025_01-13-05.gif)

# 法线变换

法线的变换受模型旋转和缩放的影响。但缩放会使法线变换发生错误，不能直接用模型缩放和旋转的合变换来做。Jason L. McKesson 的 [这篇文章](https://www.notion.so/Normal-Mapping-1737e342f5b580499966c8b906b26558?pvs=21) 介绍了法线变换矩阵的原理和计算方法。这里直接卸出结论：

$$
N_{ormal} = (M_{scale\_rotation}^{-1})^T
$$

这个公式会对矩阵进行逆运算，如何安全快速地求逆：

> 下文会用 `./` 符号，如 `A ./ B` ，表示一种运算：对 B 的所有非零元素取倒数后，与 A 的元素逐个相乘。
* 因为旋转和缩放变换都是 3x3 矩阵即可表达，所以我们的法线变换矩阵只需要一个 3x3 矩阵，本节下文中矩阵均为 3x3
> 
- 旋转矩阵（下文称 R）是可逆的，其特性是其逆矩阵等于其转置矩阵，所以对旋转矩阵求逆是安全的：`invert(R) = transpose(R)`
- 缩放矩阵（下文称 S）是一个对角矩阵，其在对角线元素不为零的情况下是可逆的，并且其结果是各元素取倒：`invert(S) = 1 ./ S`
- 根据矩阵积的求逆和转置性质，我们有：

$$
\begin{cases}
S^{-1} = 1\ ./\ S \\
R^{-1} = R ^ T \\
M^T=(RS)^{-1}=S^{-1}R^{-1} \\
N_{ormal} = (M^{-1})^T
\end{cases}
\\ \ 
\\
\implies N = (S^{-1}R^{-1})^T=(R^{-1})^T (S^{-1})^T=(R^T)^T(1./S)^T
$$

最终得到的结果是

$$
N_{ormal} = R\  ./\  S
$$

代码实现

```rust
const MIN_SCALE: f32 = 0.001;
pub fn model_normal_matrix(&self) -> (Mat4, Mat3) {
    let translation = Matrix4::from_translation(self.position);
    let scale = Matrix4::from_nonuniform_scale(self.scale.x, self.scale.y, self.scale.z);
    let rotation = Matrix4::from(self.rotation);
    #[rustfmt::skip]
    let scale_t = Matrix3::new(
        1. / self.scale.x.max(Self::MIN_SACLE), 0.0, 0.0,
        0.0, 1. / self.scale.y.max(Self::MIN_SACLE), 0.0,
        0.0, 0.0, 1. / self.scale.z.max(Self::MIN_SACLE)
    );
    let model_matrix = translation * rotation * scale;
    let normal_matrix = Matrix3::from_cols(
        rotation.x.truncate(),
        rotation.y.truncate(),
        rotation.z.truncate(),
    ) * scale_t;
    (model_matrix, normal_matrix)
}
```

# 延迟渲染管线

目前项目延迟渲染管线实现方式是将一系列计算光照用的几何数据渲染成一系列纹理（一般被称为 Geometry Buffer <G-Buffer>），然后在计算 Pass 中采样并计算像素颜色。

## Write G-Buffer Pass

所以我们需要一个写入 G-Buffer 的 Pass 我们需要的几何信息写入多个纹理，wgpu 支持多目标渲染的，可以将片源着色器结果设为多个目标。具体实现是：在创建渲染管线时需要指定 targets（是一个 `Option<ColorTargetState>` 的数组，`ColorTargetState` 声明了 Target 的纹理格式、混合模式以及写入蒙版）。

```rust
fragment: Some(wgpu::FragmentState {
	module: &shader,
	entry_point: "fs_main",
	targets: &targets, // &[Option<ColorTargetState>]
	compilation_options: wgpu::PipelineCompilationOptions::default(),
}),
```

Write G-Buffer 的渲染目标目前是四个 Rgba8Unorm 的参数，框架和 wgsl 如下表：

|  | Targets | Texture Format |
| --- | --- | --- |
| 1 | World Pos Tex | Rgba8Unorm |
| 2 | Normal Tex | Rgba8Unorm |
| 3 | Base Color Tex | Rgba8Unorm |
| 5 | PBR Parameters Tex | Rgba8Unorm |

```rust
// write_g_buffer.wgsl
struct FragmentOutput{
    @location(0) world_pos: vec4<f32>,
    @location(1) normal: vec4<f32>,
    // For PBR
    @location(2) base_color: vec4<f32>,
    @location(3) pbr_parameters: vec4<f32>, // 0: Metallic, 1: Roughness, 2: Reflectance, 3: Ambient occlusion
}

...
@fragment
fn fs_main(in: VertexOutput) -> FragmentOutput { 
...
```

## Main Pass

得到了有关几何信息的贴图后，在后续的任何 Pass 中，我们都可以使用他们。延迟渲染管线中的主 Pass 又时被称为 Compute Pass，因为很多时候是用 Compute Shader 实现的。这样做在光源剔除上有好处。我还没有这么做，是一个需要后续优化的地方。我直接使用了一个全屏幕 Vertex Shader 来实现。

### Full screen vertex shader

我们唯一的输入是 vertex_index: u32，这是着色器内置的输入信息，通过 `@builtin(vertex_index)` 声明它。

```rust
@vertex
fn vs_main(
    @builtin(vertex_index) vertex_index: u32,
) -> VertexOutput {
    let uv = vec2<f32>(f32(vertex_index >> 1u), f32(vertex_index & 1u)) * 2.0;
    let clip_position = vec4<f32>(uv * vec2<f32>(2.0, -2.0) + vec2<f32>(-1.0, 1.0), 0.0, 1.0);
    return VertexOutput(clip_position, uv);
}
```

在渲染调用时，我们不需要指定顶点的结构，也不需要任何 Buffer，只要绘制序号为 0, 1, 2 的顶点就可以了。我们只用到了三个顶点，三个顶点是如下图的方式运作的：

```rust
fn render(){
...
	render_pass.draw(0..3, 0..1);
...
}
```

![面的前方：左手系逆时针 (CCW) ](%E5%BB%B6%E8%BF%9F%E6%B8%B2%E6%9F%93%E7%AE%A1%E7%BA%BF%E3%80%81%E5%BE%AE%E8%A1%A8%E9%9D%A2%20BRDF%E3%80%81%E6%B3%95%E7%BA%BF%E5%8F%98%E6%8D%A2%E3%80%81%E7%89%A9%E4%BD%93%E7%BB%A7%E6%89%BF%E6%A0%91%20UI/25-1-22.png)

面的前方：左手系逆时针 (CCW) 

### Bindings

关于 G-Buffer 的部分我传入主 Pass 的参数如右图。因为所有贴图的采样方式都是 No Filter (Nearest)，ClampToEdge. 所以只需要一个采样器就可以了。

```rust
wgpu::SamplerDescriptor {
	address_mode_u: wgpu::AddressMode::ClampToEdge,
	address_mode_v: wgpu::AddressMode::ClampToEdge,
	address_mode_w: wgpu::AddressMode::ClampToEdge,
	mag_filter: wgpu::FilterMode::Nearest,
	min_filter: wgpu::FilterMode::Nearest,
	mipmap_filter: wgpu::FilterMode::Nearest,
	compare: None,
	lod_min_clamp: 0.0,
	lod_max_clamp: 100.0,
	..Default::default()
}
```

|  | 0: G-Buffer |
| --- | --- |
| 0 | Sampler |
| 1 | World Pos Tex |
| 2 | Normal Tex |
| 3 | Base Color Tex |
| 5 | PBR Parameters Tex |

# 点光源

在 Shader 中计算点光源需要两个数据，一个是存储所有点光源信息的 Storage Buffer，另一个是点光源的总数，用于遍历。

## Radiance of a Point Light

> 下文将 Radiance 翻译为辐射率。（[https://zh.wikipedia.org/wiki/辐射率](https://zh.wikipedia.org/wiki/%E8%BE%90%E5%B0%84%E7%8E%87)）
> 

点光源的辐射率可以通过如下的方式计算：

```rust
let radiance = intensity / ((decay * pow2(distance)) + 0.001);
```

`intensity` 和 `decay` 是光源的属性，`distance` 是世界空间下光源到片源的距离，世界空间下的坐标方才记录在 G-Buffer 中了，`+ 0.001` 是为了除法安全。

## 遍历光源

点光源的数量存在了一个全局的光照相关的 Binding 中的一个向量里。`light.lights_nums.x` 其它分量设想用于存其它光照的数量。

```rust
    let point_lights_num = light.lights_nums.x;

    for (var i = 0u; i < point_lights_num; i += 1u) {
        let li = point_lights[i];
        let world2light_unnorm = li.position.xyz - world_pos;
        let world2camera_unnorm = camera.position - world_pos;
        let dist = length(world2light_unnorm);
        if dist > li.distance { continue; }
        let dir = normalize(world2light_unnorm);

        let radiance = li.intensity / ((li.decay * pow2(dist)) + 0.001); // + 0.001 for division safety
        surface_color += calculate_light(
            li.color.xyz,
            radiance,
            surface,
            dir,
            normalize(world2camera_unnorm),
        );
```

# 微表面 BRDF

鄙人理论知识不够扎实，加之有许多很好的理论资料，就不在这里说明微表面模型理论。资料会列在文尾的参考资料中。

微表面模型实现的难点是找到详细的一套参数标准和其跟实际计算的映射关系的。例如在微表面模型的计算不直接存在 Metallic （下文称金属度）参数。金属度实际影响菲涅尔项。翻的许多文章或视频并没有讲清楚这些。好在最终找到了好的参照。我参照 filament 引擎的实现，如此设置面向用户的参数（目前实现的部分）：

| Parameter | Type & Range |
| --- | --- |
| BaseColor | Linear RGB [0, 1] |
| Roughness | Scalar [0, 1] |
| Reflectance | Scalar [0, 1] |
| Metallic | Scalar [0, 1] |

其中 BaseColor, Reflectance, Metallic 共同决定了 f0 项，Reflectance, Metallic 目前也只影响 f0 项，diffuse 会也会受 metallic 影响。此外，为了让 roughness 的视觉变换更符合直觉，roughness 可以平方处理一下。

```rust
let f0 = 0.16 * reflectance * reflectance * (1.0 - metallic) + baseColor * metallic;
let diffuse_color = (1.0 - metallic) * base_color;
...
fn perceptual_roughness_to_roughness(perceptual_roughness: f32) -> f32 {
    let clamped = clamp(perceptual_roughness, 0.089, 1.0);
    return clamped * clamped;
}
```

表格和算法出自 [Physically Based Rendering in Filament](https://google.github.io/filament/Filament.html)，原理可见原文。

# 更好的 Editor UI

![image.png](%E5%BB%B6%E8%BF%9F%E6%B8%B2%E6%9F%93%E7%AE%A1%E7%BA%BF%E3%80%81%E5%BE%AE%E8%A1%A8%E9%9D%A2%20BRDF%E3%80%81%E6%B3%95%E7%BA%BF%E5%8F%98%E6%8D%A2%E3%80%81%E7%89%A9%E4%BD%93%E7%BB%A7%E6%89%BF%E6%A0%91%20UI/image.jpg)

增加了世界物体继承和组件的 UI，可以相对方便地控制各个物体的组件了。

## Material Override

增加了一个材质继承的组件，用于覆盖某个 Mesh 的材质。附加在 MeshRenderer 组件的即可。也因此可以方便地在 Inspector 中调整材质的属性了。

# 参考资料

- Romain Guy, Mathias Agopian - Physically Based Rendering in Filament [https://google.github.io/filament/Filament.html](https://google.github.io/filament/Filament.html)
一篇非常好和全面的 PBR 文章，细致地解释了 filament 实时渲染引擎中的 PBR 是如何实现的
- Blender - 原理化 BSDF [https://docs.blender.org/manual/zh-hans/3.5/render/shader_nodes/shader/principled.html](https://docs.blender.org/manual/zh-hans/3.5/render/shader_nodes/shader/principled.html)
- Boson (2020) - GraphicEngine-Tile-based Deferred Shading using Compute Shader [https://myboson.wordpress.com/2020/05/29/graphicengine-tiled-based-deferred-shading-using-compute-shader/](https://myboson.wordpress.com/2020/05/29/graphicengine-tiled-based-deferred-shading-using-compute-shader/)
- Jason L. McKesson (2012) - Normal Transformation [https://paroj.github.io/gltut/Illumination/Tut09 Normal Transformation.html](https://paroj.github.io/gltut/Illumination/Tut09%20Normal%20Transformation.html#idp8010)