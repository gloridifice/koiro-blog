```toml
title = "Converting HDRI to Cubemaps and Prefiltering Environment Maps for IBL in wgpu"
ai-translation = true
```

# Mipmap

<aside>
💡

For a mipmap, level 0 represents the original texture, level 1 is a texture with half the original side length, and so on. mip_count is the number of mip levels, including level 0—the original texture.

</aside>

In wgpu, the texture for each mipmap level is stored in a texture view. When creating a view, we specify its base_mip_level, which corresponds to a level of the texture.

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

We generate views one by one over the range `0..mip_count`. Each view contains a texture with half the side length of the previous view. The downsampling method is determined by the shader and sampler we use while generating the views.

[https://www.w3.org/TR/WGSL/#texturesamplelevel](https://www.w3.org/TR/WGSL/#texturesamplelevel)

```glsl
textureSampleLevel(texture_2d, sampler, coords, level)
```

`textureSampleLevel` can sample different types of textures, including CubeMap, Texture Array, and so on. The function name is the same for each type, but the signatures differ slightly. See [this page](https://www.w3.org/TR/WGSL/#texturesamplelevel).

# Cube Map

In wgpu, a cube map is a texture 2d array. The image below shows the direction represented by the texture at each index. Each element's index in the array is called a Layer.

![[https://www.w3.org/TR/webgpu/#dom-gputextureviewdimension-cube](https://www.w3.org/TR/webgpu/#dom-gputextureviewdimension-cube)](images/image.jpg)

[https://www.w3.org/TR/webgpu/#dom-gputextureviewdimension-cube](https://www.w3.org/TR/webgpu/#dom-gputextureviewdimension-cube)

The usual way to render a cube map is to use six Passes, one for each of its six directions (separate Passes are required, rather than a single Pass, because the render target changes). We also need vertex data for a cube model to cover NDC and provide direction vectors for all directions.

Initialization:

1. Prepare the cube's vertex buffer.
2. Prepare buffers for the model matrices for the six directions.
3. Prepare the Pipeline.

## Sampling in WGSL

```glsl
// shader.wgsl
@group(0) @binding(1)
var r_texture: texture_cube<f32>;
```

The type of a Cube Map in wgsl is `texture_cube`.

# Equirectangular map to Cube Map

Equirectangular projection to CubeMap

HDR textures that store environment information usually use equirectangular projection, which allows more information to be used.

![Not the original HDR image; do not use. Source: [https://polyhaven.com/a/qwantani_afternoon](https://polyhaven.com/a/qwantani_afternoon)](images/image%201.jpg)

(Not the original HDR image; do not use.) Source: [https://polyhaven.com/a/qwantani_afternoon](https://polyhaven.com/a/qwantani_afternoon)

Here's how to sample it:

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
