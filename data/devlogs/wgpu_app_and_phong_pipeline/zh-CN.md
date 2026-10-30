```toml
title = "rust wgpu app 运行环境和 Phong 渲染管线搭建"
ai-translation = false
```

![测试用的环境 ](images/Untitled.jpg)

测试用的环境 

# wgpu

[Introduction | Learn Wgpu](https://sotrh.github.io/learn-wgpu/)

[Render Pipelines in wgpu and Rust](https://whoisryosuke.com/blog/2022/render-pipelines-in-wgpu-and-rust/)

根据上面两教程学会了如何在 wgpu 中搭建渲染管线入门。

# noise 库

稍稍尝试了一下使用柏林噪声，用的是这个库。

[GitHub - Razaekel/noise-rs: Procedural noise generation library for Rust.](https://github.com/Razaekel/noise-rs)

# 现在的临时的生命周期系统

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

- 游戏的生命周期系统。
- 尝试搭建一个简单可用的 ECS 系统