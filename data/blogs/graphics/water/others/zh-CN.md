```toml
title = "水体网格笔记"
ai-translation = false
```

正方形网格分割成两个三角形的顶点网络，会出现如下走样：

![方形网格产生的水面走样](images/square_grid_aliasing.png)

这个有网格精度不足和顶点法线线性插值导致，在交错的三角形排布下就会产生此纹路。直接的解决办法，使用等边三角形网络：

![使用等边三角形网格的水面效果](images/equilateral_triangle_grid.png)

虽然仍能看出网格状，但是不会出现尖刺了，并且没有引入额外成本。