```toml
title="hecs、3D 渲染系统搭建和探索更好的项目结构"
work="Terre"
index=3
published=true
date="2023-12-24T16:39:00"
```

# ECS 库

翻阅过一些 ECS 的文章，也试图自己写一个 ECS 系统。但是我发现其涉及的内容太多（例如反射、trait object 的类型转换、多线程等等），我的 rust 水平暂时不足以支撑我完成这项工作。在我寻找参考资料的过程中，我发现了这个库：[hecs](https://github.com/Ralith/hecs) - 这是一个轻量化的 ECS 库，提供了最基本的高效的 ECS 架构。

于是我想先使用这个库，边使用边学习。另外，看该项目的 readme，比较喜欢这个库的作者，ta 贴心地在 readme 中写清了 ECS 不适合做什么，还推荐了其它 ECS 库。对于我这样的入门者很有帮助。

# terre-core

最近一直在苦恼怎样更新和渲染模型，以及我需要让我编写渲染管线更加灵活一些。同时，我还苦恼于自己的项目结构越理越乱。于是我开始参考别人的项目，翻了 bevy_render，虽然给了我一些想法，但项目比较大，并不是很好阅读。想起之前的一个博客中，引用了一个 rust 游戏引擎 baryon：

[GitHub - kvark/baryon: Fast prototyping 3D engine](https://github.com/kvark/baryon/tree/main)

Baryon

看了看 baryon 是怎样实现游戏引擎的，很有帮助。参照 baryon，我决定将游戏核心部分分出`terre-core` [子项目](https://doc.rust-lang.org/book/ch14-03-cargo-workspaces.html)。

# 介绍 Bevy Rendering 的资料

Bevy 的渲染部分也是重要的参考资料。整理在这里。

- **Bevy Rendering:** [https://taintedcoders.com/bevy/rendering/](https://taintedcoders.com/bevy/rendering/)
- **Bevy Render (GPU) Framework:** [https://bevy-cheatbook.github.io/gpu.html](https://bevy-cheatbook.github.io/gpu.html)