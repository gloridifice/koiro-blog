```toml
title = "Implementing Bevy-Style ECS Systems"
ai-translation = true
```

Source code for this article: [https://github.com/gloridifice/terre/blob/terre/terre_core/src/ecs/system.rs](https://github.com/gloridifice/terre/blob/terre/terre_core/src/ecs/system.rs)

<aside>
💡 Prerequisites for this article:
- Functional programming in Rust: fn, Fn, FnMut, and so on. See [TRPL Chapter 13](https://doc.rust-lang.org/book/ch13-00-functional-features.html).
- Basic knowledge of generics and lifetimes. See [TRPL Chapter 10](https://doc.rust-lang.org/book/ch10-00-generics.html).

</aside>

# Starting at the summit

To make what I'm going to write easier to understand, I'll start by looking at the finished code to understand how this system is put together.

In Bevy, we add a system something like this:

```rust
//* system implement

fn foo_system(query: QueryMut<&mut i32>){ ... }

fn main() {
    App::new()
        .add_system(Stage::Start, foo_system)
        .run();
}
```

Here, we pass a function to `add_system`. This function is a system in terms of game logic.

> In the rest of this article, I'll use the term system function for a function that executes game logic, such as `foo_system` in the code block above. These system functions have types such as `fn() -> ()`, `fn(P1,) -> ()`, and so on.
> 

```rust
//schedule.rs
pub fn add_system<Params>(mut self, stage: Stage, function: impl IntoSystem<Params>) -> Self {
		...
    let to_add = Box::new(function.into_system());
		...
}
```

Looking at the signature of `add_system`, we can see that its system argument accepts a value implementing `IntoSystem`. In other words, the `fn` type of the system function `foo_system` we pass in actually implements `IntoSystem`.

Further down, we call the `into_system` method of `IntoSystem`. This method returns an `impl System`, which we then put in a Box and (in the omitted portion) add to the lifecycle.

So the `FnMut / fn` type of our logical system function doesn't implement `System` directly. Instead, it implements `IntoSystem` in some way, then produces a `System` through `into_system`.

Here's a diagram of the relationships between these types:

```rust
		trait System <= struct FuctionSystem
								 <-[into_system]- trait IntoSystem <= trait SystemParamFunction
```

The `FnMut` type implements `SystemParamFunction`, and `SystemParamFunction` implements `IntoSystem`. Its `into_system` method returns `FuctionSystem`, a struct that implements `System`. Please pause here for a moment to sort out these relationships.

Next, we'll start from scratch and implement this programming pattern step by step.

# trait System

First, we declare a `System` trait as follows.

```rust
//system.rs
pub trait System {
    fn run(&mut self, world: &mut World);
}
```

Here, `world` is used to retrieve the data we need; we don't need to worry about it for now. Next, we declare `IntoSystem`.

```rust
//system.rs
pub trait IntoSystem<Marker>{
		type Output: System + 'static;
		fn into_system(self) -> Self::Output;
}
```

- `Output` is a type we'll define that implements `System`. When we implement `IntoSystem`, we define what this `Output` is.
- The `into_system` function returns an implementation of `System` (namely, `Output`).

At this point, you might have two questions:

1. Why do we need `IntoSystem` as an intermediate trait instead of implementing `System` directly for a type we need?
    
    For example, using fn / FnMut directly.
    
2. What is the `Marker` in `IntoSystem<Marker>`, and why is it a generic parameter that isn't used anywhere in IntoSystem?

The first question: when storing a System, we need to use the Box<dyn System> type. Later, when implementing multiple implementations, flexible parameters, Query, and so on, we'll inevitably need to add generics to System. But Box<dyn System<_>> is not valid because it isn't a concrete type. So we want our System to have no generic parameters.

The second question: Marker allows us to implement IntoSystem multiple times for a single type. We'll encounter this situation later. The following code can help explain it.

```rust
trait Foo<T>{}
impl Foo<i32> for f32 {}
impl Foo<i16> for f32 {}
// 我们为 f32 实现了两遍 Foo, 编译器使用哪个实现会根据我们给泛型传入的具体类型决定
// 即使 T 没有被 Foo 用到，也是合法的
```
