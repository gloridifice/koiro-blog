```toml
title = "Eight-Way Autotiling: Theory and Practice in Bevy"
summary = "Connecting tiles is a headache. Connected tiles that look good can easily double the workload, making this a problem where developers need to balance technology, art, and production workflows. This article introduces autotiling theory, including all cases of eight-way tile connections, how to simplify those cases, how to draw perfect eight-way connecting tiles, and an autotiling algorithm. It also includes a small code implementation of the above."
ai-translation = true
```

Some terminology used in this article:

- We use a grid system to organize a set of objects, each of which can only move in grid-sized increments. We call this grid system a Tilemap, and the object in each grid cell a Tile.
- Eight-way connections: the visual connections of the center tile change depending on whether matching tiles exist in the eight immediately surrounding positions.

# How Many Tile Connection Cases Are There?

If we consider eight-way connections, with each different case corresponding to a different tile appearance, there are 2^8 = 256 cases in total. For example: C^8_8+C^7_8+C_8^6+...+C_8^0=2^8=256

$$
C^8_8+C^7_8+C_8^6+...+C_8^0=2^8=256
$$

If you don't want two diagonally adjacent blocks to look connected, you can remove cases like these:

```rust
1. +oo | 2. oo+ | 3. +o+ |  4. +o+
   o o |    o o |    o o |     o o
   ooo |    oo+ |    oo+ |     +o+
//用 o 来表示空位，+ 表示有可以连接的瓦片
```

The number of cases removed is: 4*(1+2^3+2*3)+4*(3+1)+4*(1)+1=81

$$
4*(1+2^3+2*3)+4*(3+1)+4*(1)+1=81
$$

If we consider the red block's appearance to be the same in the cases on the right, we can reduce the number of cases further.

![Untitled](images/Untitled.jpg)

But even after removing these cases, we still need quite a lot of them.

## Reducing 256 Cases to 13

We can draw the tilemap another way: split each existing tile into four tiles.

In other words, four small tiles make up one regular tile. Let's work out the appearances this requires:
(Each grid cell in the diagrams below represents one small tile.)

- Every time we place a regular tile, we necessarily place four small tiles. This means that three small grid cells in one direction from a small tile must contain tiles, so we only need to consider the five remaining surrounding cells.

![The red square is the center small tile](images/Untitled%201.jpg)

The red square is the center small tile

- When we place a regular tile to the right of or above the center tile shown in the diagram, it necessarily occupies two small-tile positions next to the center tile at once.

![Untitled](images/Untitled%202.jpg)

Since we also ignore variations caused by "diagonals only," the total number of cases is just `4<four corners> * 3<side-placement cases for each corner> + 1<all 8 surrounding cells filled> = 13`.

![Diagram of the 13 cases](images/sample.png)

Diagram of the 13 cases

## Bevy Implementation

In the engine, I added a SubTilemap to a Tilemap, and update the SubTilemap when the Tilemap updates.
