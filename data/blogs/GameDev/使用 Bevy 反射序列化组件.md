```toml
title = "使用 Bevy 反射序列化组件"
english_title = "Serialize components in Bevy with bevy_reflect."
slug = "我们的游戏的场景文件是自定义的，因此涉及到大量场景序列化的工作。本文简单介绍如何使用 bevy_reflect 序列化和反序列化 Component（到 toml 文件）。并且包括如何将反序列化后的反射插入 Entity. 过程中会以猫方块的自定义场景序列化为例解释。"
published = true
last_edited_date = "2025-06-05T13:48:00"
authors = "宏楼 Koiro"
class = "Dev"
date = "2025-03-24T04:00:00"
date = "2025-03-26"
tags = ["Bevy", "Game Dev", "Rust", "Serialization"]
```

# (反/)序列化 Entity 的所有 Component

只是为了方便，本文在一个 test 来学习和测试 reflect 功能。方便测试和打印。

```rust
#[cfg(test)]
mod test {
	fn test_reflect() {
		todo!();
	}
}
```

## 获得 Entity 的 Component 信息

反射的类型信息由 `TypeRegistry` 管理。我们的 App 类型注册信息存储在 `AppTypeRegistry` 中。这是一个[读写锁](https://doc.rust-lang.org/beta/std/sync/struct.RwLock.html)。这里我们以声明一个类型 `Foo`，然后注册它为例。

```rust
#[cfg(test)]
mod test {
	#[derive(Reflect, Component)]
	struct Foo {
		a: f32
		b: i32
	}
	fn test_reflect() {
	    let mut app = App::new();
      app.register_type::<Foo>();
      
      let world = app.world_mut();
      
      let registry_arc: TypeRegistryArc = world.resource::<AppTypeRegistry>().0.clone();
		  let registry = registry_arc.read();
		  
		  todo!();
	}
}
```

- `AppTypeRegistry # 0` 是一个原子引用计数，所以我们可以克隆它来避免占用世界的所有权

> 我们声明一个 App 是为了后续 `Component` 的测试。如果只想测试反射，不需要用到 `World`，也可以使用构造函数实例化一个 `TypeRegistry` 。如：
- `let registry = TypeRegistry::new();`
> 

接着，要想获得一个 Entity 的所有 Component 的信息，可以使用 `World # inspect_entity`，它会返回一个 `&ComponentInfo` 的迭代器。注意，如果 Entity 不存在，`inspect_entity` 会直接 panic. 在实际使用前最好检查一下 Entity 是否还存在。

```rust
// test_reflect()
let id = world.spawn(Foo { a: 0, b: 0 });
if world.entities().contains(id) { // 检查实体存在
	for component_info in world.inspect_entity(id) {
		todo!();
	}
}
```

## 序列化反射

接着我们通过 `World # get_reflect`获得某个实体的某个组件的反射。

```rust
// 在 for loop 中
let component_reflect: &dyn Reflect = world.get_reflect(id, component_info.type_id().unwrap()).unwrap();

let serialzier =
    ReflectSerializer::new(component_reflect.as_partial_reflect(), &registry);
let str = toml::to_string_pretty(&serialzier).unwrap();
println!("{}", &str); // Debug
```

- `ReflectSerializer` 反射内容的序列化器，可以被各类基于 serde 的序列化方法序列化
- 在 toml 中，反射的 `Foo` 的序列化的结果是一个 `toml::Table` 里包含了一个键值，其键是 `Foo` 的类型路径，值还是一个 `Table`，包含了 `Foo` 的字段。

## 反序列化反射

```rust
// 这是 toml 格式的 Table
let table = toml::from_str::<toml::Table>(&str).unwrap();
let key = table.iter().last().unzip().0.unwrap().clone();

// 重要的地方！
let reflect_deserializer = ReflectDeserializer::new(&registry);
let partial_reflect: Box<dyn PartialReflect> =
    reflect_deserializer.deserialize(table).unwrap();
    
assert!(partial_reflect.represents::<Foo>()); // 检查部分反射代表的是否是类型 Foo
```

- `key`：这里获得的 key 是 `Foo` 的完整类型路径。后面插入 Component 时会用到。
- `ReflectDeserializer`：反序列化器，其 `# deserialize` 方法来自对 serde 的 `DeserializeSeed` trait 实现，如果 rust analyzer 无法找到 `# deserialize` 方法，需要手动 `use DeserializeSeed` .

# 插入反射组件到实体中

```rust
let type_registration: &TypeRegistration = registry.get_with_type_path(&key).unwrap();
let type_id: TypeId = TypeRegistration::type_id(type_registration);
```

- `type_registration`：我们使用上面得到的类型路径（key）获取类型的注册信息，为了获取类型的 `TypeId`
- `type_id`：通过 `TypeRegistration # type_id` 来获取注册类型的 TypeId，这里使用显式的方法调用是为了与标准库原有的 `Any # type_id` 函数区分。

后续会用到地，为了让可以组件的反射可以执行 `Component` 的 trait 的行为，需要为我们的 `Foo`  类型标记 `reflect` 宏。

```rust
#[derive(Reflect, Component)]
#[reflect(Component, from_reflect = true)] // new!
struct Foo {
    a: f32,
    b: i32,
}
```

接着可以通过 `get_type_data::<ReflectComponent>` 将反射作为组件插入实体。[ReflectComponent](https://docs.rs/bevy/0.15.3/bevy/ecs/prelude/struct.ReflectComponent.html) 可以操作实现了 Component trait 的类型的反射。需要我们对类型使用 `#[reflect(Component)]` 宏才能被记录到 `TypeRegistry` 中。

```rust
if let Some(reflect_component) =
    registry.get_type_data::<ReflectComponent>(type_id) // -> Option<&ReflectComponent>
{
    info!("Successfully insert reflect component!");
    reflect_component.insert(
        &mut world.entity_mut(id),
        partial_reflect.as_ref(),
        &registry,
    );
}
```

也可以直接使用 `TypeRegistration` 来获取 `Foo` 类型的 `ReflectComponent` 数据。

```rust
type_registration.data::<ReflectComponent>(); // -> Option<&ReflectComponent>
```

# 运行时检查类型是否实现了某个 trait

上文中我们看到了，一个类型的 trait 数据以 data 的形式存储在 `TypeRegistry` 中。如果我们想记录类型实现的我们自己的 trait，也可以通过 `reflect` 宏来实现。`reflect` 宏会自动注册 trait 的反射数据。

```rust
#[derive(Reflect, Component)]
#[reflect(FooTrait, Component, from_reflect = true)] // new!
struct Foo {
    a: f32,
    b: i32,
}

#[reflect_trait] // new!
trait FooTrait {}

impl FooTrait for Foo {}
```

我们需要为想要被记录反射信息的 `FooTrait` 打上 `reflect_trait` 宏，它会生成一个结构体叫做 `ReflectFooTrait`.于此同时，我们还需要在 `Foo` 类型的 `reflect` 宏中加上 `FooTrait` .

- [reflect_trait](https://docs.rs/bevy/latest/bevy/prelude/attr.reflect_trait.html) 宏会自动生成 trait 的数据，既 `Reflect{trait_ident}` 结构体。

经过以上行为后，我们就可以像获取 `ReflectComponent` 一样获取 `ReflectFooTrait`，以此来判断一个类型是否实现了某个 trait.

```rust
let is_impl_foo_trait =
	type_registration.data::<ReflectFooTrait>().is_some();
	// or
	registry.get_type_data::<ReflectComponent>(type_id).is_some();
```
