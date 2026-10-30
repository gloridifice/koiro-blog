```toml
title = "Setting Up a Rust wgpu App and a Phong Rendering Pipeline"
ai-translation = true
```

![Test environment ](images/Untitled.jpg)

Test environment 

# wgpu

[Introduction | Learn Wgpu](https://sotrh.github.io/learn-wgpu/)

[Render Pipelines in wgpu and Rust](https://whoisryosuke.com/blog/2022/render-pipelines-in-wgpu-and-rust/)

I learned the basics of setting up a rendering pipeline in wgpu from the two tutorials above.

# noise library

I tried out Perlin noise a little using this library.

[GitHub - Razaekel/noise-rs: Procedural noise generation library for Rust.](https://github.com/Razaekel/noise-rs)

# The current temporary lifecycle system

```rust
pub struct App{
    updates: Vec<Box<dyn Fn(&mut Runtime)>>,
    starts: Vec<Box<dyn Fn(&mut Runtime)>>,
}

pub struct Runtime {
    pub context: GraphicsContext,
    pub size: winit::dpi::PhysicalSize<u32>,

    pub camera: Camera,
    pub camera_controller: CameraController,
    pub input: CursorInput,
    pub pass: PhongPass,
    pub nodes: Vec<Node>,
}

pub async fn run() {
    App::new()
        .add_system(Stage::Update, render)
        .add_system(Stage::Update, update)
        .run().await;
}
```

# //TODO

- The game's lifecycle system.
- Try building a simple, usable ECS system
