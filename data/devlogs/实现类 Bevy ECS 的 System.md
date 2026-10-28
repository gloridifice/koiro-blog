```toml
title="实现类 Bevy ECS 的 System"
work="Terre"
index=4
published=false
preview=""
date="2024-01-02T19:15:00"
```

本文的源代码：[https://github.com/gloridifice/terre/blob/terre/terre_core/src/ecs/system.rs](https://github.com/gloridifice/terre/blob/terre/terre_core/src/ecs/system.rs)

<aside>
💡 阅读本文需要的前置知识：
- rust 中的函数式编程知识 fn, Fn, FnMut 等，可以阅读 [TRPL 第 13 章](https://doc.rust-lang.org/book/ch13-00-functional-features.html)；
- 基本的泛型知识与基本的生命周期知识，可以阅读 [TRPL 第 10 章](https://doc.rust-lang.org/book/ch10-00-generics.html)。

</aside>

# 从山顶出发

为了更容易理解我要写的东西，我先从已经实现好的代码中理解该系统是如何构成的。

在 Bevy 中，我们用类似下面的方式来添加一个系统：

```rust
//* system implement

fn foo_system(query: QueryMut<&mut i32>){ ... }

fn main() {
    App::new()
        .add_system(Stage::Start, foo_system)
        .run();
}
```

在这个过程中，我们在 `add_system` 中传入了一个函数，此函数是游戏逻辑上的系统.

> 在后文中，我会用 系统函数 一词来指执行游戏逻辑的函数，例如上个代码块中的 `foo_system` . 这些 系统函数 的类型为 `fn() -> ()` `fn(P1,) -> ()` 等等。
> 

```rust
//schedule.rs
pub fn add_system<Params>(mut self, stage: Stage, function: impl IntoSystem<Params>) -> Self {
		...
    let to_add = Box::new(function.into_system());
		...
}
```

如果看 `add_system` 的函数签名可以发现，系统的参数的位置，接受一个实现了 `IntoSystem` 的变量。也就是说我们传入的系统函数 `foo_system` 的 `fn` 类型，实际上被实现了 `IntoSystem` .

继续观察代码，我们调用了`IntoSystem` 的 `into_system` 方法，这个方法会返回一个 `impl System`，然后我们将该 `System` 装箱在 Box 中，（在省略部分）加入了生命周期。

也就是说，我们逻辑上的系统函数 `FnMut / fn` 类型并没有被直接实现 `System` ，而是通过某种方式实现了 `IntoSystem` ，而后通过 `into_system` 产生一个 `System`.

在这里，笔者直接展示一个各种类型之间的关系图：

```rust
		trait System <= struct FuctionSystem
								 <-[into_system]- trait IntoSystem <= trait SystemParamFunction
```

`FnMut` 类型被实现了 `SystemParamFunction`，而 `SystemParamFunction` 实现了 `IntoSystem`，其 `into_system` 方法的返回值为 `FuctionSystem` ，这是一个实现了 `System` 的结构体。在此处，麻烦读者略微停顿，理清这其中的逻辑关系。

我们接下来会再从零开始，一步一步实现这个编程范式。

# trait System

首先我们声明一个 `System` trait，如下面的一段代码。

```rust
//system.rs
pub trait System {
    fn run(&mut self, world: &mut World);
}
```

其中 `world` 用来获取我们需要的数据，暂时不需要关心。接着，我们声明 `IntoSystem`. 

```rust
//system.rs
pub trait IntoSystem<Marker>{
		type Output: System + 'static;
		fn into_system(self) -> Self::Output;
}
```

- `Output` 是我们会定义的实现了 `System` 的类型，我们实现 `IntoSystem` 的时候由我们来定义这个 `Output` 是什么。
- `into_system` 函数会返回一个 `System` 实现（即 `Output`）

到这里，读者可能产生两个问题：

1. 为什么我们需要一个 `IntoSystem` 作为中间 trait，而不直接为某个我们需要的类型实现 `System` ？
    
    例如直接用  fn / FnMut 
    
2. `IntoSystem<Marker>` 的 `Marker` 是什么，为什么是一个完全没有在 IntoSystem 中用到的泛型？

第一个问题：在存储 System 的时候，我们需要使用 Box<dyn System> 类型；在后面实现多实现、自由参数、Query 等内容时，不可避免的涉及需要在 System 中添加泛型；但 Box<dyn System<_>> 类型是不合法的，因为它不是一个具体类型。因此我们希望我们的 System 不带有泛型。

第二个问题：Marker 的作用是允许我们为一个类型实现多个 IntoSystem，在未来我们会遇到这样的情况。下面一段代码可以帮助理解这件事。

```rust
trait Foo<T>{}
impl Foo<i32> for f32 {}
impl Foo<i16> for f32 {}
// 我们为 f32 实现了两遍 Foo, 编译器使用哪个实现会根据我们给泛型传入的具体类型决定
// 即使 T 没有被 Foo 用到，也是合法的
```