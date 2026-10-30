```toml
title = "Serializing Components with Bevy Reflection"
summary = "Our game uses custom scene files, which means a lot of scene serialization work. This article briefly introduces how to use bevy_reflect to serialize and deserialize Components (to TOML files), including how to insert deserialized reflected values into an Entity. Along the way, I use the custom scene serialization in 猫方块 as an example."
ai-translation = true
```

# (De)serializing All Components of an Entity

For convenience, I'll use a test to learn about and try out reflection in this article. It makes testing and printing things easier.

```rust
#[cfg(test)]
mod test {
	fn test_reflect() {
		todo!();
	}
}
```

## Getting an Entity's Component Information

Reflected type information is managed by `TypeRegistry`. Our App's type registration information is stored in `AppTypeRegistry`. This is a [read-write lock](https://doc.rust-lang.org/beta/std/sync/struct.RwLock.html). Here, we'll declare a type called `Foo` and register it as an example.

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

- `AppTypeRegistry # 0` is an atomically reference-counted value, so we can clone it to avoid tying up ownership of the world.

> We declare an App for the `Component` tests later on. If you only want to test reflection and don't need a `World`, you can also instantiate a `TypeRegistry` using its constructor. For example:
- `let registry = TypeRegistry::new();`
> 

Next, to get information about all of an Entity's Components, we can use `World # inspect_entity`, which returns an iterator of `&ComponentInfo`. Note that if the Entity doesn't exist, `inspect_entity` will panic immediately. It's best to check whether the Entity still exists before using it in practice.

```rust
// test_reflect()
let id = world.spawn(Foo { a: 0, b: 0 });
if world.entities().contains(id) { // 检查实体存在
	for component_info in world.inspect_entity(id) {
		todo!();
	}
}
```

## Serializing Reflected Values

Next, we use `World # get_reflect` to get the reflected value of a particular component on an entity.

```rust
// 在 for loop 中
let component_reflect: &dyn Reflect = world.get_reflect(id, component_info.type_id().unwrap()).unwrap();

let serialzier =
    ReflectSerializer::new(component_reflect.as_partial_reflect(), &registry);
let str = toml::to_string_pretty(&serialzier).unwrap();
println!("{}", &str); // Debug
```

- `ReflectSerializer` is a serializer for reflected contents. It can be serialized by various serde-based serialization methods.
- In TOML, the serialized reflected `Foo` is a `toml::Table` containing one key-value pair. The key is the type path of `Foo`, and the value is another `Table` containing the fields of `Foo`.

## Deserializing Reflected Values

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

- `key`: The key obtained here is the full type path of `Foo`. We'll use it later when inserting the Component.
- `ReflectDeserializer`: The deserializer. Its `# deserialize` method comes from its implementation of serde's `DeserializeSeed` trait. If rust-analyzer can't find the `# deserialize` method, you'll need to add `use DeserializeSeed` manually.

# Inserting a Reflected Component into an Entity

```rust
let type_registration: &TypeRegistration = registry.get_with_type_path(&key).unwrap();
let type_id: TypeId = TypeRegistration::type_id(type_registration);
```

- `type_registration`: We use the type path (key) obtained above to get the type's registration information, so we can obtain its `TypeId`.
- `type_id`: We get the registered type's TypeId through `TypeRegistration # type_id`. The explicit method call here distinguishes it from the standard library's existing `Any # type_id` function.

For what comes next, we need to annotate our `Foo` type with the `reflect` macro so that its reflected component can perform the behaviors of the `Component` trait.

```rust
#[derive(Reflect, Component)]
#[reflect(Component, from_reflect = true)] // new!
struct Foo {
    a: f32,
    b: i32,
}
```

We can then use `get_type_data::<ReflectComponent>` to insert the reflected value into the entity as a component. [ReflectComponent](https://docs.rs/bevy/0.15.3/bevy/ecs/prelude/struct.ReflectComponent.html) can operate on reflected values of types that implement the Component trait. We need to use the `#[reflect(Component)]` macro on the type for this to be recorded in the `TypeRegistry`.

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

We can also use `TypeRegistration` directly to get the `ReflectComponent` data for the `Foo` type.

```rust
type_registration.data::<ReflectComponent>(); // -> Option<&ReflectComponent>
```

# Checking at Runtime Whether a Type Implements a Trait

Above, we saw that a type's trait data is stored as data in the `TypeRegistry`. If we want to record a type's implementation of our own trait, we can do that with the `reflect` macro too. The `reflect` macro automatically registers the trait's reflection data.

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

We need to annotate `FooTrait`, whose reflection information we want to record, with the `reflect_trait` macro. It generates a struct called `ReflectFooTrait`. At the same time, we also need to add `FooTrait` to the `reflect` macro on the `Foo` type.

- The [reflect_trait](https://docs.rs/bevy/latest/bevy/prelude/attr.reflect_trait.html) macro automatically generates the trait's data: the `Reflect{trait_ident}` struct.

After doing this, we can retrieve `ReflectFooTrait` just as we retrieve `ReflectComponent`, and use it to determine whether a type implements a particular trait.

```rust
let is_impl_foo_trait =
	type_registration.data::<ReflectFooTrait>().is_some();
	// or
	registry.get_type_data::<ReflectComponent>(type_id).is_some();
```
