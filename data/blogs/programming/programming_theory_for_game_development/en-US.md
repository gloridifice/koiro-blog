```toml
title = "Programming Theory Fundamentals for Game Development"
summary = "This article was prepared for a club presentation by 宏楼 (me) and 拉斯普 (Aspirin). It covers the basic concepts of some important theories in game development, including programming paradigms, object-oriented programming, composition and inheritance, data-oriented programming, the concepts and benefits of ECS, and the architectures used by some game engines currently on the market."
ai-translation = true
```

<aside>
💡 Preface: This article was prepared for a club presentation by 宏楼 (me) (Koiro) and [拉斯普 (Aspirin)](https://github.com/raspirin). It covers the basic concepts of some important theories in game development, including programming paradigms, object-oriented programming, composition and inheritance, data-oriented programming, the concepts and benefits of ECS, and the architectures used by some game engines currently on the market.

</aside>

# Fundamentals

## Programming paradigms

Let's start with something simple. Programming languages don't just give us powerful tools; they also help us organize our thoughts about “how to do” something. To do this, a programming language needs to give us three abilities: the ability to express the most basic expressions, which make up the language's most fundamental concepts, such as primitive data types like integers and floating-point numbers (as well as locks, channels, and so on); the ability to compose, so we can combine the basics into something complex; and the ability to abstract, meaning that we can treat a complex structure we've put together as a whole and give it a name.

Everyone is familiar with primitive data types, so let's look more closely at composition and data. Suppose we want to represent a point in three-dimensional space. We can use three numbers, such as x, y, and z. But that means we need three variables every time we declare a point, and we have to use names to distinguish the x, y, and z values of different points. Here's an example declaring two points.

```c
int main(){
	//第一个点
	float x1 = 2.0f;
	float y1 = 3.0f;
	float z1 = 0.1f;
	//第二个点
	float x2 = 1.0f;
	float y2 = 2.0f;
	float z2 = 0.6f;
}
```

On the one hand, we have to use naming to indicate how the variables relate to one another. On the other, declaring so many variables makes our code hard to manage: declaring three points, for example, means declaring nine variables. As you can see, this is a cumbersome way to express things.

So we might ask: could we combine related objects and give the result a new name? C gives us this ability through `struct`.

```c
struct Vector3 {
  float x;
  float y;
	float z;
};
```

Here, we've combined three data types into a new data type and named it `Vector3` (a three-dimensional vector). This is one example of how programming languages give us composition and abstraction. We've combined x, y, and z and abstracted them into `Vector3`.

We use these abilities provided by programming languages to build the programs we want, and the languages in turn shape our style of solving problems. These styles are generally called programming paradigms. There are many named paradigms, such as imperative programming, procedural programming, object-oriented programming, and functional programming. It's worth noting that different programming paradigms shouldn't be seen as independent of one another, let alone opposed to one another. Some paradigms do conflict, though; we'll come back to that when we talk about data-oriented programming.

## Before approaching object-oriented programming

> In this article, “operations on data,” “operations,” and “functions” mean roughly the same thing. Please treat them as one concept in this section.
> 

Think back to what we learned in C class. C lets us use variables of primitive types to store the data we want. That's the first ability it gives us. It also lets us manipulate those variables through statements, and we can put multiple statements into a function so we can treat a complex operation as a single operation. This is another example of C's support for composition and abstraction.

The C-style (or, more precisely, procedural) programming paradigm puts functions at the center of the program—in other words, the operations our program needs to perform. From this perspective, a program is essentially a series of function declarations. We write down every operation we'll need as a function, and variables are how information passes between functions. This approach runs into some particular problems when maintaining certain large projects. First, a long execution flow makes the program hard to analyze and troubleshoot. Even though we can treat the program's execution flow as an indivisible whole while writing code, we still have to unfold it when debugging. Second, variables and the operations on them aren't associated in the code's logic, which can cause a lot of confusion. For example, both leftist heaps and binary search trees are stored as arrays (you don't need to know what these are; just know they're arrays). The difference is only in the operations on the arrays. Their insertion functions, for example, are different. In this situation, unless we indicate it through names, comments, or similar means, we don't know whether an array is a leftist heap or a binary search tree. So we say these two data structures and the operations that maintain them aren't associated in the code's logic, even though they are associated in the logic of the data structures. We therefore need a way to express the relationship between data and operations on that data in code: the ability to compose and abstract data together with its operations.

# What is object-oriented programming (OOP)?

@Rasp 

Let's see how the object-oriented programming paradigm attempts to solve these problems. Object-oriented programming turns programming from a series of function declarations into a series of object (or, more precisely, class) declarations. It treats objects as the basic units that carry out a program's functionality. Put another way, objects are the workers in a factory, and functionality is the work they do. How does object-oriented programming pull off this black magic? Most object-oriented languages construct variables and expressions in much the same way we're used to in C, so we need to think about what's different in the composition and abstraction abilities they provide.

Some object-oriented languages provide a basic concept called a “class,” a way of combining basic units. Unlike what we did with C structs, however, classes can combine not just data but operations too. Here's an example of a class. Ignore the `public` keyword for now; we'll explain it below.

```csharp
public class Vector3 {
	//成员字段
	public float x;
	public float y;
	public float z;

	//成员方法
	public void Clear() {
		x = 0f;
		y = 0f;
		z = 0f;
	} 
}
```

In this context, a class has two parts. The first is its member fields—the variables it holds, such as x, y, and z above. The second is its member methods—the functions it holds, such as `Clear()` above. This function doesn't do anything special; it just sets the vector's x, y, and z values to 0. Member fields are the data the class should store, and member methods are the operations that can be performed on that data.

In this example, we've declared a class called `Vector3` and included an operation: the `Clear` function, or `Clear` method. A method differs from a function in that it naturally has access to the other variables in the class. As we can see, the `Clear` method above can access x, y, and z directly without having them passed in as parameters. Our `Clear` method is actually similar to the function below, which sits outside the class. In the function below, we pass in a `Vector3` named `self`. In the class function above, we don't need to pass in `self`. We've effectively hidden it: we implicitly assume that functions in a class can use the class's other variables, so we don't have to write out `self`. We call this kind of function a method.

```csharp
void Clear(Vector3 self) {
	self.x = 0f;
	self.y = 0f;
	self.z = 0f;
}
```

Good object-oriented programs also require each class to be designed small enough to perform only one logical function.

This is a new way of understanding composition and abstraction. In terms of classes, we no longer think about what the entire program should do under which circumstances. Instead, we think about what capabilities each class should have, and what data and methods we should put into a class to give it a particular capability.

The object-oriented paradigm also considers it unhelpful to spend too much effort thinking about the correctness of external code, and encourages programmers to stop doing so. When defining a class, then, we can expose only the details we want other classes to know. For example, we can tell the outside world only that we have methods A and B, while keeping some other methods internal. This technique is called encapsulation.

Looking back at the code above, we see a `public` keyword before every variable and class declaration. This keyword declares that the class and variables are visible to other classes. If we don't specify it, in many programming languages the field is invisible to other classes by default (that is, fields are `private` by default).

So how do classes solve our earlier problems? Let's first review what those problems were.

- Long execution flows make a program hard to analyze and troubleshoot.
    
    ![[https://git.kernel.org/pub/scm/linux/kernel/git/torvalds/linux.git/tree/kernel/fork.c?h=v6.7-rc4](https://git.kernel.org/pub/scm/linux/kernel/git/torvalds/linux.git/tree/kernel/fork.c?h=v6.7-rc4)  Long code in fork.c](images/Untitled.gif)
    
    [https://git.kernel.org/pub/scm/linux/kernel/git/torvalds/linux.git/tree/kernel/fork.c?h=v6.7-rc4](https://git.kernel.org/pub/scm/linux/kernel/git/torvalds/linux.git/tree/kernel/fork.c?h=v6.7-rc4)  Long code in fork.c
    
- Variables and the operations on them aren't associated in the code's logic.

First, once we've defined a class's functionality, encapsulation lets us consider only the correctness of that functionality itself. If a class works correctly on its own, it will also work correctly when cooperating with other classes. That's because we only need to expose the parts of a class that promise to provide its functionality; the other parts are confined within the class. Second, encapsulation means we can't, and won't, spend too much time considering program details, because most code in other libraries has been hidden by other developers. These two points reduce the scope we need to keep in mind to something relatively manageable. Supporters of the object-oriented paradigm believe that designing programs with object-oriented thinking makes them easier to maintain.

Let's use Minecraft's `Block` as a concrete example of a class. As we mentioned earlier, a class can combine variables and operations. A simplified version of Minecraft's block class looks like this. To make the code easier to read, we'll leave out all `public` keywords from now on. Just assume every field is `public`.

```java
class Block {
	String name;
	float explosionResistance; //爆炸抵挡能力
	float destroyTime; //破坏这个方块需要的时间
	Item item;
	...
}
```

In the `Block` class, we've declared a lot of information that almost every block has, such as the time needed to break it. Now, to create a block—dirt, for example—we just need to instantiate the `Block` class.

```java
Block dirt = new Block("dirt",...);
```

Wait, what is instantiation? We introduced `struct` earlier. When we create a `struct`, we create all the variables declared inside it at the same time, “packaged” into one variable. Almost the same thing happens when we create a class: we create a collection of variables and functions declared in the class and “package” them into one variable. We call this variable an “object,” and the process “instantiation.” The `dirt` variable we've created is what we call an “object.” You may be wondering: since we haven't set initial values for these variables, what are their initial values in the dirt block we created? Programming languages offer many ways to declare and set an object's initial values, which you can learn about as you study a language. We've omitted that code here to make the concepts easier to understand.

Now we've created a dirt block. We can create stone blocks, leaf blocks, and so on in the same way.

```java
Block dirt = new Block("dirt",...);
Block stone = new Block("stone",...);
Block leaf = new Block("leaf",...);
...
```

Notice the parentheses in the code above. This is one way of setting an object's initial values in Java. Our different blocks have different textures, names, and breaking times, and we can set all of these inside the parentheses when we declare a block.

We've just used an example to show how classes combine variables. Now let's look at “operations (functions/methods).” For example, every block has a behavior that drops items when it's broken. We write a `spawnDrops` method in the Block class to spawn our drops when a block breaks.

```java
//Block class	
void spawnDrops(){

}
```

But in Minecraft, different blocks drop items differently. Dirt drops its own block item, for example, while leaves have a chance to drop saplings. We only have one `Block` class, though, so it can implement only one piece of logic. In fact, it's not just item drops: all sorts of behaviors differ from block to block. Is there an architecture that lets them keep the same behavior and variable declarations while implementing the special behaviors of each block?

![Untitled](images/Untitled.jpg)

This is where inheritance comes in. It lets us extend our classes with new functionality while retaining the contents of the original class.

```java
class LeafBlock extends Block {
	void spawnDrops() {
		//有概率掉落树苗的逻辑
	}
}
```

```java
class DirtBlock extends Block {
	void spawnDrops() {
		//掉落方块物品的逻辑
	}
}
```

Above, we've derived two new classes from `Block`. Both inherit from `Block`, so they have the contents of the `Block` class, while the class mechanism lets us change the logic of a particular method. This solves the problem we encountered earlier.

# Implementing a game with an inheritance model

@宏楼 Koiro 

We've just learned about inheritance, and using it to design a game is intuitive. Take Minecraft, a game implemented using an inheritance model. In Minecraft, the base class Entity is used to implement all kinds of things that aren't blocks, such as dropped items and mobs. Let's look at how Minecraft implements chickens and cows. Since all living entities share some characteristics, such as having a health system and being able to take damage, the developers derived a class called LivingEntity to serve as the base class for all living entities. Each mob behaves differently, so the developers derive a class for each specific mob.

For example, only chickens descend slowly through the air, so the developers just need to put the slow-falling logic in the Chicken class. Similarly, they put the logic for collecting milk with a bucket in the Cow class.

![Untitled](images/Untitled%201.gif)

![Untitled](images/Untitled%202.gif)

```java
public void aiStep() {
      super.aiStep();
      this.oFlap = this.flap;
      this.oFlapSpeed = this.flapSpeed;
      this.flapSpeed += (this.onGround() ? -1.0F : 4.0F) * 0.3F;
      this.flapSpeed = Mth.clamp(this.flapSpeed, 0.0F, 1.0F);
      if (!this.onGround() && this.flapping < 1.0F) {
         this.flapping = 1.0F;
      }

      this.flapping *= 0.9F;
      Vec3 vec3 = this.getDeltaMovement();
      if (!this.onGround() && vec3.y < 0.0D) {
         this.setDeltaMovement(vec3.multiply(1.0D, 0.6D, 1.0D));
      }

      this.flap += this.flapping * 2.0F;
      if (!this.level().isClientSide && this.isAlive() && !this.isBaby() && !this.isChickenJockey() && --this.eggTime <= 0) {
         this.playSound(SoundEvents.CHICKEN_EGG, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
         this.spawnAtLocation(Items.EGG);
         this.gameEvent(GameEvent.ENTITY_PLACE);
         this.eggTime = this.random.nextInt(6000) + 6000;
      }
   }
```

In the inheritance hierarchy, Chicken and Cow both inherit from Animal, which in turn inherits from LivingEntity through several layers.

![Inheritance hierarchy of the Cow class](images/Untitled%201.jpg)

Inheritance hierarchy of the Cow class

But consider this: what if we want a mob that can both be milked and fall slowly? What should we do?

In practice, this is easy to solve. Since we can't inherit from both the chicken class and the cow class, we can create a new class and put both the chicken's gliding logic and the cow's milking logic into it. But you should be noticing a problem: we've copied code. Code duplication is a tricky problem, because when we change a piece of code in the future, we also have to change all its copies, making the code harder to maintain. To reduce duplication in an inheritance model, our only option is to abstract the various behaviors into even more classes. Look at the Cow class's inheritance hierarchy, for example: there are many layers of classes between Animal and Mob. But if we think of a mob that doesn't fit any existing class, we have to rearrange the inheritance hierarchy or copy existing code, bringing back the duplication problem. If we could predict the future, we could construct a perfect inheritance hierarchy. But we can't. We either have to reorganize the hierarchy whenever a new requirement comes up, or copy code.

![Untitled](images/Untitled%202.jpg)

So in an inheritance model, it's hard to combine different capabilities.

# Composition over inheritance

This led people to develop the composition model in practice.

Imagine breaking an object's functionality down into individual abilities. For a chicken, we could break its behavior down like this (what abilities does it need?):

- Health
- AI
- Walking
- Slow falling
- Receiving potion effects
- …

Similarly, for a cow, we can write a “milk-producing ability.” This makes the earlier mob problem easy to solve: just combine “slow falling” and “milk production” and give both to one mob. Composition has many other benefits too. If we want to write attack logic, we don't have to care what the object is. As long as it has a health ability, we execute that ability's damage-taking logic. More simply, an object without a health ability can't be hurt. An object without the ability to receive potion effects is immune to them, because potion effects won't be applied to it.

## Composition and inheritance don't conflict

Inheritance is a programming language feature, and it doesn't conflict with composition. For example, different mobs have different AI behaviors, but they share some parts, so they can be derived from a common base class.

## Accessible examples of composition

Almost all modern game engines allow composition. In Unity, each object's behavior is implemented through various components. In Godot, we can use Nodes to implement composition.

![Unity](images/Untitled%203.jpg)

Unity

![Godot](images/Untitled%204.jpg)

Godot

# Problems with object-oriented programming in game development

@Rasp 

There's a classic saying in software engineering: no silver bullet. In other words, there's no universal solution.

Object-oriented programming has plenty of problems in actual engineering practice too. First, there's performance, mainly in two respects. One is that when we use a class to get the functionality we want, we inevitably also get functionality we don't need, and all that functionality has a cost. The other is that the concept of an “object” isn't friendly to modern machines. Imagine that we need the names of a large number of objects. To get their names, we have to pull all the objects into memory, usually including all the other data stored in them. Before long, memory fills up with data we don't need.

In fact, this data isn't as unrelated to our problem as it might appear. We've actually brought something with unmanageable internal state into our context. We can't tell whether certain operations are as harmless as they promise, because we can't see the other side of the encapsulation—until one day a mysterious internal error ruins the whole section of code we've written.

Another problem comes from object-oriented thinking itself: it keeps encouraging programmers to construct complex encapsulations. The powerful capabilities of classes often lead programmers to believe, mistakenly, that every problem can be solved by constructing a new class. A new problem? A new feature? No worries—we can solve it with a new class. So we combine more and more classes into new classes, making this uncontrollable internal state bigger and bigger. Eventually, we end up writing a lot of unnecessary boilerplate just to implement a simple feature.

It seems the object-oriented paradigm is far less clean and harmless than it appears. Is there a way to solve this problem?

# What is data-driven programming?

@Rasp 

> In the text below, “data-driven” and “data-oriented” mean the same thing. Whenever you see one, feel free to substitute whichever term you prefer.
> 

## Before introducing a new paradigm

We need to point this out again: there's no universal solution, and different programming paradigms aren't mutually exclusive. We should use each paradigm's strengths where it excels, rather than stubbornly believing one paradigm can solve every problem.

## Starting with data

Data-driven programming relies on patterns in data, performing different behaviors based on different patterns. This description is both simple and difficult. Unlike object-oriented programming, it doesn't describe a program in a way we can all understand intuitively. To understand patterns in data, we first need to understand what similar data is.

Often, we don't really care what our data actually is; we care only about what it can do. For example, when sorting a collection of data, we care only whether it can be ordered. We don't care whether the values can be added, copied, and so on. We can think of data that shares a common behavior like this as similar data. This gives us another hint: the behaviors we write, or operation functions, can often be used with many kinds of data. The similarities we extract from data also often show where our attention is focused when using that data.

```haskell
min :: (Ord t) => t -> t -> t
min a b = case compare a b of
	LT -> a
	GT -> b
	EQ -> a
```

By storing similar data together, we can retrieve only the data we want when we need it. And because this data shares common characteristics, we perform the same operations on it. This is a pattern in data.

![Untitled](images/Untitled%205.png)

The data-driven paradigm is therefore better suited to problems involving the same kind of operation on large amounts of similar data. It naturally offers better performance and makes it easier to use multithreading-related optimization techniques. But it isn't well suited to tasks that involve certain operations on some special piece of data, because that usually means artificially inventing a “similarity” for that special data and requiring other data not to satisfy it, which is an antipattern. Such tasks are naturally better suited to an object-oriented approach.

# What is ECS?

@宏楼 Koiro 

> The Component introduced here isn't the same as Unity's Component. Similarly, Entity isn't GameObject. They may have some similarities, but please don't carry over your past experience.
> 

ECS stands for the Entities | Components | Systems model. An entity is a special unit that packages many components together. Components store data, while systems process it. 

> ECS is a framework designed with data-oriented thinking. Below, we'll also introduce how an ECS system is implemented using this approach.
> 

Although that's a plain, concise introduction, it's still hard to understand. We can understand it through actual game content. Once again, we'll use Minecraft as an example.

> Note: Minecraft isn't implemented using an ECS model. We're using Minecraft's gameplay content to introduce ECS—or, in other words, how we would design Minecraft if we implemented it with ECS. The definition of Entity below has nothing to do with the Entity in Minecraft that we introduced earlier.
> 

## First, entities and components

Our game consists of a large number of objects. In Minecraft, for example, every block, item, and mob can be considered an entity. Each object has a unique identifier, and the entity is that unique identifier. In implementation, it's just an `unsigned int` (a 32-bit unsigned integer on most machines). Our data lives in components, and we can specify which entity each component belongs to.

This gives us a way to compose data. We combine different components through an entity's unique identifier.

For a concrete example, let's think about what components an item dropped on the ground in Minecraft would need:

```kotlin
- Transform //位置、旋转、缩放信息
- ItemTexture //物品的贴图
- RegistryName //这个物品种类的唯一标识
- Count //掉落的物品的数量
- DroppedItem //不存储数据，只是一个用来标识这时掉落物的标签
..
```

![Dec-07-2023 22-49-46.gif](images/Dec-07-2023_22-49-46.gif)

You might wonder why we don't just create an Item component and put all the item's related parameters in it.

That's certainly a workable design. But in the ECS model, keeping components small has performance advantages in Systems, as we'll explain below.

## What is a System?

A system is an operation on data. In programming languages, functions are how we operate on data. Systems in ECS are similar: in implementation, they're just various functions. There are two questions to answer when implementing systems:

- How do we get the data we need (mainly components)? (Understanding how components are stored.)
- How do we call our systems? (Understanding how systems are stored.)

### How components are stored

We just said that ECS is a data-oriented architecture. In data-oriented thinking, we put data of the same kind together. In ECS, components are that data. In implementation, we store components of the same type together.

![Untitled](images/Untitled%205.png)

### Putting systems together

Primitive data types can be stored, and functions can be stored too. Our game updates through a loop, and systems—or functions—execute during this process. Most languages today have many functional programming features, so storing functions, or operations, isn't difficult. Functional programming is another deep rabbit hole; we'll talk about it another time. All you need to know is that we can store functions together just like data, then call them every frame in the game loop.

## 🌰 Example

We've just learned how to address the two questions that come up when processing data. Let's look at a concrete example!

In Minecraft, dropped items rotate over time. This requires changing the rotation data in the `Transform` components of all dropped items every frame. To implement this, we write a function that processes `Transform` components and execute it every frame. Although we have a great many dropped items, the data-oriented design lets us retrieve all their `Transform` components at low cost, change their values, and make all dropped items rotate at a constant speed. In the process, we don't retrieve any other unnecessary data from each dropped item, avoiding unnecessary overhead.

![Dec-07-2023 22-49-46.gif](images/Dec-07-2023_22-49-46.gif)

```rust
fn rotate_drops(query: Query<&Transform, With<DroppedItem>>, time: Res<Time>){
    for transform in query{
        rotate_with_time(&mut transform, time.delta());
    }
}
```

With that explained, the reason for splitting different data into smaller components above should be clearer. Suppose we make a component large, responsible for lots of data, like the `ItemStackDorp` we mentioned.

To get `Transform`, we'd have to retrieve all of `ItemStackDrop`, bringing along data we don't use (texture, registryName…), which creates unnecessary overhead. So we tend to design each component to be small and responsible for one clearly defined task.

## Applications of ECS

ECS has many performance benefits, but its disadvantages are also obvious. It's nowhere near as intuitive as object-oriented programming for organizing data. In an object-oriented model, we keep an object's data and behavior together, while ECS separates them. ECS also has requirements for programming language features, so most game engines on the market aren't pure ECS systems. Some use other paradigms, and some mix ECS with other paradigms.

- Unreal uses a hybrid ECS paradigm.

The relatively pure and popular game engines using the ECS paradigm that I know of are:

- Bevy, a Rust-based, pure ECS, data-oriented game engine.
- Unity's DOTS framework, which was just updated this year (2023). Unity particularly emphasizes the framework's ECS and data-oriented features.

Still, the more important part of learning about ECS is understanding its underlying ideas: data-oriented thinking. ECS is only one implementation. The reason we keep thinking about architecture is to create better software.

That's all. Have fun, everyone. Happy game dev and happy hacking!

# Appendix

> Even the best learning resources can't solve every problem. As you learn, I hope you'll make plenty of use of search engines, ChatGPT, New Bing, and so on to solve your problems.
In development, searching in English on international search engines (Bing's international version, Google, etc.) can produce more helpful answers.
> 

## Programming fundamentals

CS50: Introduction to Computer Science, Harward University, [https://pll.harvard.edu/course/cs50-introduction-computer-science](https://pll.harvard.edu/course/cs50-introduction-computer-science) 
(Chinese subtitles — CS50: Introduction to Computer Science, [https://www.bilibili.com/video/BV1DA411Y7jk/](https://www.bilibili.com/video/BV1DA411Y7jk/))

> Who it's for: People who want to learn the fundamentals of programming.
What you can get from it: What we, as programmers, are actually doing and should be thinking about when we program. The basic ideas of programming.
> 

*Structure and Interpretation of Computer Programs,* MIT, [https://mitp-content-server.mit.edu/books/content/sectbyfn/books_pres_0/6515/sicp.zip/full-text/book/book.html](https://mitp-content-server.mit.edu/books/content/sectbyfn/books_pres_0/6515/sicp.zip/full-text/book/book.html) 
(Chinese version — Structure and Interpretation of Computer Programs, [https://www.bilibili.com/video/BV1Xx41117tr/](https://www.bilibili.com/video/BV1Xx41117tr/))

> Who it's for: People who want to understand how programming languages give us composition and abstraction, and how to design good programs.
What you can get from it: Basic knowledge of Lisp, the ability to design good programs, and the ability to analyze the structure of programming languages.
> 

## The object-oriented programming paradigm

### C# (C Sharp)

*Illustrated C#*, Daniel Solis and Cal Schrotenboer, Posts & Telecom Press

> Who it's for: Beginners with no programming language experience, and people whose only experience is with non-object-oriented languages.
What you can get from it: Basic and advanced knowledge of the C# programming language, practical guidance, and the application of basic object-oriented theory in C# programming.
> 

### Java & Kotlin

> Kotlin is a programming language that can be used to write Java programs, cross-platform applications, web applications, Android apps, and more.
Kotlin is more modern and fully compatible with Java, so more and more Java projects are adopting Kotlin as their primary development language (for example, Android).
> 

---

*Head First Java (Chinese Edition)*, Kathy Sierra , Bert Bates, China Electric Power Press

> Who it's for: Beginners with no programming language experience, people whose only experience is with non-object-oriented languages, and people who want to learn Java.
What you can get from it: An understanding of the object-oriented programming paradigm and how to put it into practice in Java.
> 

---

Kotlin language guide in Chinese: [https://hltj.gitbooks.io/kotlin-reference-chinese/content/txt/index0.html](https://hltj.gitbooks.io/kotlin-reference-chinese/content/txt/index0.html)

> Who it's for: People with experience programming in an object-oriented language who want to learn Kotlin.
> 

## Game engines

GAMES 104 — Introduction to Modern Game Engines: [https://www.bilibili.com/video/BV1oU4y1R7Km/](https://www.bilibili.com/video/BV1oU4y1R7Km/?vd_source=8e3bac5216c2065ea5e49aa968fbe85a)

> Who it's for: People curious about how games and game engines work. Even if you don't understand most of it, there's still a lot to gain.
What you can get from it: Theoretical knowledge of modern game engines.
> 

## The Bevy engine

> Who it's for: People curious about how Bevy designs a clean ECS structure.
What you can get from it: The ability to use the Bevy engine; knowledge of how an ECS game engine is put into practice; a deeper understanding of data-driven design.
> 

Bevy website: [https://bevyengine.org/](https://bevyengine.org/)

Rust Game Development with Bevy: [https://taintedcoders.com/](https://taintedcoders.com/)

Rust Bevy Entity Component System: [https://blog.logrocket.com/rust-bevy-entity-component-system/](https://blog.logrocket.com/rust-bevy-entity-component-system/)

## Further study

Beau­tiful Racket: an intro­duc­tion to language-oriented program­ming using Racket, [https://beautifulracket.com/](https://beautifulracket.com/) (Design your own programming language using Racket)
