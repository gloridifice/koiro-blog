```toml
title = "在 wgpu 进行 HDRI 到 Cubemap 的转换与 IBL 预滤波环境贴图"
published = false
last_edited_date = "2025-06-13T19:42:00"
authors = "宏楼 Koiro"
class = "Dev"
date = "2025-02-12T18:40:00"
tags = ["Graphics", "Rust", "Wgpu"]
```

# Mipmap

<aside>
💡

对于一个 mipmap 来说，第 0 级代表着原贴图，第 1 级代表原贴图 1/2 边长大小的贴图，以此类推。mip_count 代表 mip 的数量，其包含第 0 级，也就是原贴图。

</aside>

在 wgpu 中，每一个 mipmap 的某一级的贴图存储在一个 texture 的 view 中，我们在创建 view 时会指定一个 view 的 base_mip_level ，其对应着一个 texture 的级。

```rust
let view_level_n = texture.create_view(&wgpu::TextureViewDescriptor {
	label: Some("mip"),
	format: None,
	dimension: None,
	aspect: wgpu::TextureAspect::All,
	base_mip_level: mip_level_n, // u32, LOOK AT HERE
	mip_level_count: NonZeroU32::new(1),
	base_array_layer: 0,
	array_layer_count: None,
});
```

我们要按 `0..mip_count` 的范围逐个生成 view，每个 view 的内容是边长为上一个 view 的 1/2 的贴图. 至于降采样方式，由我们生成 view 过程中用的 shader 和使用的采样器决定。

[https://www.w3.org/TR/WGSL/#texturesamplelevel](https://www.w3.org/TR/WGSL/#texturesamplelevel)

```glsl
textureSampleLevel(texture_2d, sampler, coords, level)
```

`textureSampleLevel` 可以采样不同类型的贴图，包括 CubeMap、Texture Array 等，采样每种类型的函数名相同，签名略不同。可以查询 [此页](https://www.w3.org/TR/WGSL/#texturesamplelevel)。

# 立方体贴图（Cube Map）

在 wgpu 中，一个立方体贴图是一个 texture 2d 数组。下图展示了每个序号下的贴图代表的方向。数组的每个元素的序号称为层（Layer）。

![[https://www.w3.org/TR/webgpu/#dom-gputextureviewdimension-cube](https://www.w3.org/TR/webgpu/#dom-gputextureviewdimension-cube)](%E5%9C%A8%20wgpu%20%E8%BF%9B%E8%A1%8C%20HDRI%20%E5%88%B0%20Cubemap%20%E7%9A%84%E8%BD%AC%E6%8D%A2%E4%B8%8E%20IBL%20%E9%A2%84%E6%BB%A4%E6%B3%A2%E7%8E%AF%E5%A2%83%E8%B4%B4%E5%9B%BE/image.jpg)

[https://www.w3.org/TR/webgpu/#dom-gputextureviewdimension-cube](https://www.w3.org/TR/webgpu/#dom-gputextureviewdimension-cube)

要想渲染一张立方体贴图，通常的做法是使用六个 Pass，分别对应着立方体贴图的六个方向（必须要使用不同的 Pass 而不是同一个 Pass 的原因是渲染目标会发生改变）。同时，还需要一个立方体模型的顶点数据来覆盖 NDC 并提供所有方向的方向向量。

初始化阶段：

1. 准备好立方体的顶点缓冲区
2. 准备好六个方向的模型矩阵的缓冲区
3. 准备好 Pipeline

## 在 WGSL 采样

```glsl
// shader.wgsl
@group(0) @binding(1)
var r_texture: texture_cube<f32>;
```

Cube Map 在 wgsl 中的类型是`texture_cube`

# Equirectangular map to Cube Map

等距长方投影到 CubeMap

通常保存环境信息的 HDR 贴图使用等距长方投影，这样可以利用更多信息。

![（非 HDR 原图，勿使用）来源：[https://polyhaven.com/a/qwantani_afternoon](https://polyhaven.com/a/qwantani_afternoon)](%E5%9C%A8%20wgpu%20%E8%BF%9B%E8%A1%8C%20HDRI%20%E5%88%B0%20Cubemap%20%E7%9A%84%E8%BD%AC%E6%8D%A2%E4%B8%8E%20IBL%20%E9%A2%84%E6%BB%A4%E6%B3%A2%E7%8E%AF%E5%A2%83%E8%B4%B4%E5%9B%BE/image%201.jpg)

（非 HDR 原图，勿使用）来源：[https://polyhaven.com/a/qwantani_afternoon](https://polyhaven.com/a/qwantani_afternoon)

采样其的方式如下

```rust
const vec2 invAtan = vec2(0.1591, 0.3183);
vec2 SampleSphericalMap(vec3 v)
{
    vec2 uv = vec2(atan(v.z, v.x), asin(v.y));
    uv *= invAtan;
    uv += 0.5;
    return uv;
}
```
