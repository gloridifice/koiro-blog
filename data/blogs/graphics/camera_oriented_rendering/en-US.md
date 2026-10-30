```toml
title = "Designing a Camera-Oriented Rendering System"
ai-translation = true
```

> I first saw the term Camera driven rendering in a Bevy discussion:
[https://github.com/bevyengine/bevy/discussions/19702](https://github.com/bevyengine/bevy/discussions/19702)
I've been refactoring my Wgpu PBR project recently, which gives me a chance to try out this idea.
> 

In a rendering system, since each camera renders the scene once, many rendering resources are logically tied to a camera. These include the render target (its type, dimensions, and texture format), G-Buffer, depth buffer, and so on. This design supports multi-camera rendering and therefore also provides good support for multiple windows. Meanwhile, each camera goes through a frame rendering workflow, and the frame rendering logic is independent of the number of cameras. Given these two points, we want to design the system so graphics developers don't have to worry too much about how to retrieve resources from cameras. So the “camera-oriented” part happens when building the rendering system, not when using it.

Below, I'll use some components to show exactly what a camera stores.

Logical components are used to create the camera's view-projection matrix:

| Transform/WorldTransfrom | The camera's position component |
| --- | --- |
| Camera | Contains information such as the camera's FOV |

Rendering components are rendering settings or CPU-side containers for GPU resources:

| RenderTarget | Contains two color buffers (the Ping-Pong pattern) and an optional depth buffer |
| --- | --- |
| CameraBuffer | A buffer containing the view-projection matrix, position, and direction |
| CameraGlobalBindGroup | A bind group for global resources, tied to the camera |
| GBufferBindGroup | The G-Buffer bind group and textures for deferred rendering |

# The frame rendering workflow

To allow a texture from one step to be used in the next, the rendering system uses the Ping-Pong pattern. The idea is simple: alternate between two textures. The cost of swapping them is creating more Encoders. The current stage's work must be submitted before its contents are written to the render target; only then does swapping make sense. Otherwise, the swap could happen before the previous render target has been written to.

To make better use of Encoders and reduce DrawCalls, the concept of render phases is introduced (borrowed from Bevy). Specifically, a render phase shares one Encoder and has a common render target. Its rendering commands aren't submitted together until the phase ends.

| Opaque | Rendering opaque objects |
| --- | --- |
| Transparent | Rendering transparent objects |

A render phase can store a series of rendering functions that are called in order every frame to retrieve resources from the world and create rendering commands.

When rendering a frame, a render phase needs to provide its current ColorTarget and optional DepthTarget for the rendering functions to use.
