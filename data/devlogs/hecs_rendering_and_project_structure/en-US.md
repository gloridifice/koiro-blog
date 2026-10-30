```toml
title = "hecs, Setting Up 3D Rendering, and Exploring a Better Project Structure"
ai-translation = true
```

# ECS library

I've read some articles about ECS and tried writing an ECS system myself. But I found that it involves too much (reflection, trait object type conversions, multithreading, and so on), and my Rust skills aren't enough to finish it yet. While looking for reference material, I found this library: [hecs](https://github.com/Ralith/hecs), a lightweight ECS library that provides a basic, efficient ECS architecture.

So I decided to use this library first and learn as I go. Also, reading the project's readme gave me a good impression of its author. They thoughtfully explain what ECS isn't suited for and recommend other ECS libraries. That's very helpful for a beginner like me.

# terre-core

Lately, I've been struggling with how to update and render models, and I need more flexibility in how I write rendering pipelines. Meanwhile, my project structure keeps getting messier the more I try to organize it. So I started looking at other people's projects. I read through bevy_render, which gave me some ideas, but it's a large project and not very easy to read. Then I remembered a Rust game engine, baryon, that a previous blog post had referenced:

[GitHub - kvark/baryon: Fast prototyping 3D engine](https://github.com/kvark/baryon/tree/main)

Baryon

Looking at how baryon implements a game engine was very helpful. Following baryon's example, I decided to split the game core into a `terre-core` [subproject](https://doc.rust-lang.org/book/ch14-03-cargo-workspaces.html).

# Resources introducing Bevy Rendering

Bevy's rendering code is another important reference. I've collected the resources here.

- **Bevy Rendering:** [https://taintedcoders.com/bevy/rendering/](https://taintedcoders.com/bevy/rendering/)
- **Bevy Render (GPU) Framework:** [https://bevy-cheatbook.github.io/gpu.html](https://bevy-cheatbook.github.io/gpu.html)
