```toml
title = "What I Did in Fall 2023"
summary = "Before I knew it, it was December, and Splatoon had already moved on to Chill Season. My sense of time has been a little hazy; somehow, winter break is already just around the corner."
ai-translation = true
```

# Maybe I Didn't Actually Make Anything

The start of a new school year means club recruitment season. Over half a semester, I spent quite a bit of time on recruitment for two clubs, producing a few third-rate designs along the way. This fall, I tried putting some new technologies into practice, but didn't make anything even somewhat complete.

## Dipping My Toes into Godot

I've only taken part in one [GameJam](https://zh.wikipedia.org/wiki/Game_Jam) so far this semester. I tried [Godot](https://godotengine.org/) and finished the basic work I'd planned. It makes a lot of things easier than unity, but the overall experience wasn't that great. In the end, I only completed the most basic version of the idea, and the game wasn't playable.

Some advantages over unity:

- Far fewer pitfalls for beginners than unity
- More ready-made tools that are actually usable, so you don't have to build them yourself

Some frustrations:

- Godot might have a memory leak; it gets sluggish if you leave it open for a long time.
- There are some pitfalls with syncing Godot through git. Changes to scenes disappeared several times, and I still don't know why.
- The engine still crashed occasionally, for reasons I couldn't figure out.
- Godot's editor UI is less comfortable than Unity's. The most obvious annoyance is that you can't freely rearrange windows, which makes some operations quite a hassle (not that Unity's editor interactions are comfortable either).
- Once nodes are packaged into a scene, external nodes can't directly access its children, which sometimes makes the architecture of game scripts more troublesome to design.

Godot still isn't stable enough, so I don't feel like using it much for now.

Some of the discomfort was also down to me: I've never quite gotten the vim extension in VS Code configured properly (I normally use Idea Vim), and right now vsc is my only option for writing gd script with vim.

## Briefly Getting Sick of Popular Game Engines

For a while after that gj, I briefly disliked the three relatively popular general-purpose game engines on the market:

- UE is too big, compiles slowly, produces large builds, and uses C++;
- Unity: most of the time, writing unity scripts means writing boilerplate; unity and c# together make unity development somewhat terrible ergonomically.
- Godot has the problems I mentioned above.

During this spell of being sick of game engines—

## Wanting to Learn More About Bevy

[Bevy Engine](https://bevyengine.org/)

> Bevy Engine is a game engine library written in rust.
> 

I'd tried Bevy once quite a while ago. After I finished using godot, I went back to learning rust and bevy in my spare time for various reasons.

I had planned to write a project, but put it on hold because a library hadn't been updated to keep up.

Bevy is a game engine that's still in development, but its engine architecture already feels fairly mature, and the design itself is very modern. Although the current version of Bevy (0.12) doesn't help me make game prototypes more efficiently than other engines, I get a lot of enjoyment out of learning it.

- An [article](https://blog.logrocket.com/rust-bevy-entity-component-system/) explaining how bevy ecs is implemented
- Through bevy, I also came across its graphics api, wgpu, and started learning it.

# Closing Thoughts

That's about it for what I did this fall.

Happy Game Dev, Happy Hacking
