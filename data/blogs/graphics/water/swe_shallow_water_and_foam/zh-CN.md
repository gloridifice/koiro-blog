```toml
title = "SWE 浅水模拟与泡沫渲染 | 实时水体渲染 vol.1"
summary = "本文主要介绍了常见的物理交互水实现方式浅水模拟的简易推导，以及浅水中最重要的着色模块——泡沫着色的实现。本文使用双浓度场 & 纹理位移场 & 多噪波混合等值线的方式来渲染泡沫。"
ai-translation = false
```

本博文有 rust + wgpu 实现，源码看这里哦：
https://github.com/gloridifice/wgpu-water-simulation
# 前言

流体有两种描述方式，一种是欧拉描述使用空间网格记录流体的速度、压力、水深等物理量。另一种流体描述方式为拉格朗日描述，其将流体视为无数个小微团，每个微团是一个粒子，粒子之间相互作用产生流体演变。由于通过后者模拟真实水体的计算量很大，实时渲染的物理水方案通常使用前者。

本文不会具体介绍欧拉方程。我们需要知道的是：欧拉方程指忽略粘性的流体运动方程。我们的流体是不可压缩的。通常压缩能力较强的流体是气体。不可压缩的表示方式为流体速度的散度为 0：
$$\nabla\cdot\mathbf v=0$$
从欧拉方程推导到浅水方程的过程较为枯燥，因此本文不会涉及。本文会涉及实践上的公式，这些公式最终会被写到代码中。

# 一、水体 - 浅水方程（SWE）

### 1. 浅水方程一阶求解器

浅水方程是欧拉方程的一种简化近似。在欧拉描述中，我们记录三维空间的流体场，但是在浅水方程中，我们只记录一个二维的流速和水深场。可以从直觉上来想象：我们规定一块水体被分化成二维的网格，每个网格都有一定高度的水，根据该高度的水与相邻网格的水的高度差，可以计算网格间压力，进而可以计算一个时间步的流速和网格间的流量，最终更新本格水的高度。

该方法的珍贵之处在于，我们只用对上一帧的数据进行少量采样，就能完整计算出这一帧的状态。每个网格可以并行计算，十分 GPU 友好。

来看看具体怎么实现吧！我们的更新思想是：新水深 = 旧水深 + 流入的水 - 流出的水。我们在每个格子保存两个物理量水深 $h$ 和水深与流速乘积 $hu$，其中 $u$ 是此处的液体水平流速，所以 $hu$ 代表单位宽度上的水流量。对于第 $i$ 个格子，更新深度的公式为：
$$ h_i^{\,t_0+\Delta t} = h_i^{\,t_0} + \frac{\Delta t}{\Delta x} \left(q_{\text{left}}-q_{\text{right}}\right) $$
其中 $q$ 为左右边界的流量。$t_0$ 为当前当前帧，$t_0+\Delta t$ 为下一帧。


动量的更新和水深类似：
$$ (hu)_i^{\,t_0+\Delta t} = (hu)_i^{\,t_0} + \frac{\Delta t}{\Delta x} \left(P_{\text{left}}-P_{\text{right}}\right) + \Delta t S_i$$
- $P$ 是动量通量，$P=hu^2 + \frac{gh^2}{2}$
	- $hu^2$：水流把动量带到旁边；
	- $\frac12gh^2$：水压传递的作用，水越深，这一项越大。
- $S_i$ 为地形作用

$P$ 的具体计算公式为：
$$ P=hu^2+\frac12gh^2 $$
- $hu^2$：水流速度作用
- $\frac{1}{2} gh^2$：重力作用

###  2. SWE 与碰撞体求解

碰撞体使用模拟高地的方式来求解，我们设置碰撞体所在网格的高度为碰撞体的高度。
- `有效底床高度 = max(原地形高度, 覆盖该格的各碰撞体顶面高度)` 
依据此更新浅水网格的高度场。这种方式适合静态物体，不适合动态物体。因为频繁更新高度场会凭空吃掉水分。

### 3. 用法线模拟短波

远海和浅水需要有平滑的过度，同时，还需要将 FFT 海洋的数据传递给 SWE，让 SWE 根据 FFT 海洋的参数进行模拟。

由于实时渲染的浅水模拟的精度不足，FFT 的短波往往会被稀释到难以在浅水场中看见。使用法线来模拟短波可以在视觉上近似模拟短波细节。计算法线的具体方法如下：

x 方向的坡度计算如下：
$$
g_x^{(s)} = A\frac{H(x+s\Delta,z)-H(x-s\Delta,z)} {2s\Delta} 
$$
- $\Delta$ 网格间距
- $H$ 逆 FFT 后的空间高度
- $s$ 是采样间隔

同理计算 z 方向，和 x 方向组成一个坡度向量 $g$
- s = 1 表示局部的坡度，s = 4 表示较大跨度的坡度，两者相减得到坡度差异。
$$\mathbf g_{\text{detail}} = \mathbf g^{(1)}-\mathbf g^{(4)}$$
坡度增量：

$$ \delta\mathbf g = \mathbf g_{\text{detail}} \cdot \underbrace{S(1-W_{\text{FFT}})W_{\text{depth}}W_{\text{pixel}}}_{\text{总权重}} $$

| 权重                 | 作用                                  |
| ------------------ | ----------------------------------- |
| $S$                | 面板中的 `Normal detail strength`，0 为关闭 |
| $1-W_{\text{FFT}}$ | 远海不补，越进入 SWE 区域补得越多，避免在远海重复叠加       |
| $W_{\text{depth}}$ | 实际水深 ≤2 厘米时关闭，≥25 厘米时完全开启，中间平滑过渡    |
| $W_{\text{pixel}}$ | 当一个屏幕像素覆盖过多 FFT 网格时淡出细节，减少远处闪烁      |
对于原法线 $n_0$，新法线的计算结果是：
$$\mathbf n= \operatorname{normalize} \begin{pmatrix} n_{0x}-n_{0y}\delta g_x\\ n_{0y}\\ n_{0z}-n_{0y}\delta g_z \end{pmatrix} $$
# 二、浅水泡沫

根据现实海洋中的观察，泡沫主要产生自高处的水与低处的水挤压，回落产生的高度差。

具体来说，因为阻力和重力，前方的海水会逐渐减速。当前方海水速度为零后，因为重力，前方的海水会逐渐回退。由于前方海水回退的过程中与后方的前进的海水尝试挤压，产生海浪，浪卷入空气，产生大量泡沫。由于后方海水的动量更大，其会带着泡沫继续向前进。此外，海水内部的气泡会不断上升，延长泡沫的寿命。并且不同大小和运动状态的泡沫的寿命衰减也不一样。

这么看来，我们无法从物理上实时模拟气泡。使用务实的思路，我们可以将气泡简化为一个依附于 SWE 水流场的浓度标量场。浓度越高的地方，我们用看起来更密制的纹理来着色；更稀疏的地方，则使用由明显空隙的网格纹理来着色。这样可以模拟出现实中的两种气泡视觉效果。

![](images/ocean_foam_patterns.jpg)

图为现实中的泡沫，一个大浪会形成许多小浪，两者都会卷入空气形成泡沫。大浪会形成厚实的泡沫，数个小浪则形成数个湾状泡沫。紫色部分同时有大浪和小浪贡献，而网纹状的泡沫（绿色）主要来自于小浪流动和汇聚。

![](images/shoreline_foam_front.jpg)

海浪泡沫的最前端往往存在一个存活最久的泡沫前沿。其原因大致是：最前方泡沫的动能最先消失，后面的泡沫继续前进不断与前方汇聚和补充动能，维持前方泡沫的浓度和滞留时间。

经过观察，我们用下面四个特征来更新“泡沫浓度”：

| 效果   | 随水移动   | 汇聚变浓   | 泡沫生成        | 逐渐消失       |
| ---- | ------ | ------ | ----------- | ---------- |
| 实现方式 | 泡沫流体模拟 | 泡沫流体模拟 | 根据 SWE 状态实现 | 使用指数衰减降低浓度 |
我们将泡沫抽象成一个浓度标量场，在具体实现上，我使用了比 SWE 分辨率高一倍的网格来模拟这个标量场，让泡沫有更多细节。

在我的实现中，我们将泡沫抽象成有两种浓度的标量场，分别是“新鲜”和“残余”。它们着色方式和衰减参数。在标量场中，我们记录的参数是两种泡沫的泡沫密度。我们定义为 $N_i$ （New）新鲜泡沫密度和 $R_i$（Rest）残余泡沫密度。我们用 $F_i = N_i + R_i$ 表示总泡沫密度（Full）。

### 1. 随水移动 & 汇聚变浓

随水移动的部分十分简单，要想获得本帧泡沫的密度，我们可以根据当前水格子的速度乘以时间，得到泡沫本帧的位移，然后反向查询上一帧时对应格子的泡沫密度，即可得到本帧密度。这个过程叫 “半拉格朗日回溯”。我们称上一帧的来源的位置为源位置，由于源位置可能在网格边缘，我们会用源位置邻近的四个网格做一次四线性插值来得到结果。我们将得到的密度标记为 $N_{previous}$（新鲜泡沫）。

汇聚变浓部分，我们其本质是泡沫的挤压和扩散，如果水流在汇聚，那么其上面的泡沫会挤压，如果水流在扩散，那么其上面的泡沫会扩散。我们使用“速度散度”来描述这种挤压和扩散。
$$
d_{i,j} = \frac{u_{x+} - u_{x-}}{\Delta x} + \frac{u_{z+} - u_{z-}}{\Delta z}
$$
这里 u 指某个轴的水流速度（标量），x+ 和 x- 分别代表 x 方向上邻近一格的水的流速。

$$
N_{new} = N_{previous} \cdot e^{-d_{i,j} \Delta t}
$$
浓度流送的部分就这么简单。残余泡沫 $R$ 的计算方式与其相同，两者使用独立的标量场存储，各自使用相同算法演算。

### 2. 泡沫生成

在水流挤压的时候，会出现泡沫。例如有三个格子：

```
case1     case2
A B       A B
1 -2      5 2
- positive direction ->
```

B 格子的水流与 A 格子相反，会尝试发生挤压，这种情况下，我们认为其可能出现泡沫。第二种情况，A 的速度快于 B 的速度很多，也可能发生挤压。用公式表示挤压：
$$
\tau_{compress} = \frac{max(0, u_i - u_{i+1})}{d}
$$
$u_i$ 是当前沿流向的速度，$u_{i+1}$ 是前方沿流向的速度，$d$ 是采样距离。这是一种经验近似。
而压缩强度计算生成率，我们使用：
$$
t=clamp(\frac{\tau_{compress} - T}{W} ,0,1)
$$
T 和 W 是两个外部控制参数，分别对应阈值和过渡宽度。也就是只有压缩率为 T ~ T + W 的范围会生成泡沫。然后使用 smoothstep 平滑：
$$
s=t^2(3 - 2t)
$$
最终的生成率为：
$$
S = A_{param}\cdot s \cdot smoothstep(0.02, 0.1, |\vec u|)
$$
$A$ 是系数倍率，是给用户控制的外部参数。而最后一项是抑制低速水流，速度不超过 0.02 米/秒时不发射，达到 0.1 米/秒后不再减弱。最后，每个时间步生成的新密度就是 $S \cdot \Delta T$。

### 3. 逐渐消失

我们希望让用户控制泡沫的半衰期 $H_N$，半衰期与衰减率的公式为 $a = \frac{ln2}{H_N}$，那么泡沫的衰减公式为：
$$
N_{new} = N_{previous} e^{-a \Delta t}
$$
我们可以将生成率和退出率合成一个系数变化率。

### 4. 新鲜泡沫转为残余泡沫

我们认为一部分退出的新鲜泡沫会转为残余泡沫，另一部分则直接消失。退出的新鲜泡沫我们刚刚已经计算了比率 $a$，我们认为退出的新鲜泡沫中，有 $f$ 比率的泡沫会变为残余泡沫，另外一部分直接消失。那么本次步进产生的残余泡沫为：
$$
\frac{dR}{dt} = fa N
$$由于残余泡沫密度 R 自身也有半衰期 $H_R$，对应退出率为 $b$，并且残余泡沫退出后直接消失。所以最终新鲜泡沫的变化率和残余泡沫的变化率分别为：
$$ \frac{dN}{dt}=S-aN,\qquad \frac{dR}{dt}=faN-bR $$
需要注意的是，这里 R 公式中的 N 不是常数，而是关于 t 的函数 $N(t)$，在积分时需要考虑这一点。
### 汇总

更新顺序是，先输送，再处理泡沫变化率（生成和消失）。我们直接对上面的微分方程积分从 0 到 $\Delta t$。得到新鲜泡沫的更新公式：
$$
N^* = N_{previous} \cdot e^{-d_{i,j} \Delta t}, \qquad  N_{new} =N^{*}e^{-a\,dt} +\frac{S}{a}(1-e^{-a\,\Delta t}) 
$$
其中 $N_{previous}$ 为回溯采样的旧密度，$a = \frac{ln2}{H_N}$，R 类似，积分得到：
$$
 \begin{aligned}R_{\text{new}} &=R^*e^{-b\Delta t} +faN^*K +fS\left[\frac{1-e^{-b\Delta t}}{b}-K\right]\\K&=\frac{e^{-b\Delta t}-e^{-a\Delta t}}{a-b} \end{aligned} 
$$
其中 $f$ 是退出的新鲜泡沫转换到残余泡沫的比率，$b = \frac{ln2}{H_R}$

# 三、泡沫着色

刚刚了解了泡沫浓度如何更新，该着色了。由于我们的浓度并不限制范围，即其范围可能为 0 到正无穷，我们需要先对该值进行一下归一化。
$$
c_N = 1 - e^{-N/k}
$$
其中 $k$ 是用户控制的参数，$k$ 越大，支持的浓度范围有大，浓度看起来越淡。

对于新鲜泡沫，我们希望其为二值，即纯透明或者完全不透明。为了做出泡沫边缘不规则的样子。我们用一个噪声作为可见性阈值，噪声值和 $c_N$ 比较，小于 $c_N$ 透明度为零，大于 $c_N$ 则为一。

![](images/fresh_foam_rendering.jpg)
图 a：新鲜泡沫的效果。

残余泡沫使用噪声，截取一条等值带来形成网格状。R 浓度来控制等值带的宽度。

![](images/residual_foam_rendering.jpg)
图 b：残余泡沫效果

![](images/combined_foam_rendering.jpg)
图 c：新鲜和残余叠加的效果
### 泡沫扭曲

目前泡沫没有跟随水流的拖曳和扭曲效果，需要实现一下。我们使用一个纹理位移场 $\mathbf D$ 来记录随水流的纹理位移。
$$
\mathbf D_{new}(\mathbf x) = \mathbf D_{old}(\mathbf x-\mathbf u\Delta t) +\mathbf u\Delta t
$$
初始 $\mathbf D = \mathbf 0$，$\mathbf x$ 为位置坐标。不过如果该场一直流动，那么其纹理会不断偏移下去，我们需要一些重置机制防止偏移值不断变大，出现过于扭曲的效果。

重置机制如下：

我们设重置周期为 $T$，我们使用两层 $\mathbf D_A, \mathbf D_B$ 来存储流动。每隔 $T$ 一层就会重置值为 $\mathbf 0$，考虑到我们在子步结束时重置，此时已经流动了一小段时间，因此补上这段流动，重置值为 $\mathbf D_A=\mathbf u\,(\phi_{new}T)$，B 层相位会和 A 层错开半个周期，即 $\mathbf D_B=\mathbf u\left[\operatorname{fract}(\phi_{\text{new}}+0.5)T\right]$。
$$\phi_{new}=\operatorname{fract}(\phi+\frac{\Delta t}{T})$$
其中 fract 是取小数。接着我们将这个相位作为输入，使用平滑三角波函数作为权重，就可以做出交替的 A 和 B 层混合。如图所示：

```html component
<style>
  .foam-blend { display: block; width: 100%; height: auto; color-scheme: dark; }
  .foam-blend text { fill: #b7b3b0; font: 15px -apple-system, sans-serif; }
  .foam-blend .heading { fill: #eae7e7; font-size: 19px; }
  .foam-blend .curve-label { font-size: 19px; }
  .foam-blend .layer-a { color: #6eafff; }
  .foam-blend .layer-b { color: #ffb65d; }
  .foam-blend .layer-a text, .foam-blend .layer-b text { fill: currentColor; }
  @media (max-width: 480px) {
    .foam-blend text { font-size: 24px; }
    .foam-blend .heading, .foam-blend .curve-label { font-size: 28px; }
  }
</style>
<svg class="foam-blend" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 740 368" role="img" aria-labelledby="blend-title blend-desc">
  <title id="blend-title">泡沫流动的双层混合权重</title>
  <desc id="blend-desc">A、B 两层权重之和为一；A 在周期起点重置，B 在半周期重置，重置时对应层的权重为零。</desc>
  <path d="M60 50H708 M60 158H708 M222 50V266 M384 50V266 M546 50V266 M708 50V266" fill="none" stroke="#b7b3b0" stroke-opacity=".18"/>
  <path d="M60 50V266H708" fill="none" stroke="#b7b3b0" stroke-opacity=".6"/>
  <g text-anchor="end">
    <text x="44" y="55">1</text>
    <text x="44" y="163">0.5</text>
    <text x="44" y="271">0</text>
  </g>
  <g text-anchor="middle">
    <text x="60" y="291">0</text>
    <text x="222" y="291">0.25</text>
    <text x="384" y="291">0.5</text>
    <text x="546" y="291">0.75</text>
    <text x="708" y="291">1</text>
    <text x="384" y="354">Phase (time / period)</text>
  </g>
  <!-- 每半周期用一段三次 Bézier 精确表示 smoothstep，无需脚本采样。 -->
  <g class="layer-a">
    <path d="M60 266C168 266 276 50 384 50S600 266 708 266" fill="none" stroke="currentColor" stroke-width="2.5"/>
    <text class="curve-label" x="462" y="73">A</text>
    <circle cx="60" cy="266" r="3.5" fill="currentColor"/>
    <circle cx="708" cy="266" r="3.5" fill="currentColor"/>
    <text x="60" y="321">A reset</text>
    <text x="708" y="321" text-anchor="end">A reset</text>
  </g>
  <g class="layer-b">
    <path d="M60 50C168 50 276 266 384 266S600 50 708 50" fill="none" stroke="currentColor" stroke-width="2.5"/>
    <text class="curve-label" x="156" y="73">B</text>
    <circle cx="384" cy="266" r="3.5" fill="currentColor"/>
    <text x="384" y="321" text-anchor="middle">B reset</text>
  </g>
</svg>
```

> 平滑三角波公式：
> $$s=1-|2\phi_{\text{new}}-1|,\qquad w_A=s^2(3-2s),\qquad w_B=1-w_A$$
> 三角波可以保证两者加权为 1。

这样在 A 重置时 A 增益为 0，B 重置时 B 增益为 0，不会出现跳变。

![](images/flow_distorted_foam.jpg)
图 d：应用扭曲效果的两种泡沫叠加。

![](images/foam_distant_view.jpg)
图 e：远景。