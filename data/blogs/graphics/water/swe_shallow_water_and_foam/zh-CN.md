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

```html component
<title>一阶浅水方程求解器</title>
<section class="kc-root kc-panel">
  <figure class="kc-figure" style="margin:0;padding-block:12px">
    <svg viewBox="0 0 384 170" style="max-width:520px;margin-inline:auto"
         role="img" aria-label="一阶空间重构，格内为分片常数">
      <rect x="132" y="16" width="120" height="120" fill="var(--kc-surface)"/>
      <path d="M12 16H372V136H12Z M132 16V136"
            stroke="var(--kc-control-line)" fill="none"/>
      <path d="M252 16V136" stroke="var(--kc-muted)" stroke-dasharray="4 4"/>
      <path d="M12 102H132 M132 83H252 M252 40H372"
            stroke="var(--kc-accent)" stroke-width="2" fill="none"/>
      <g fill="var(--kc-ink)">
        <circle cx="72" cy="102" r="3"/>
        <circle cx="192" cy="83" r="3"/>
        <circle cx="312" cy="40" r="3"/>
      </g>
      <g fill="var(--kc-canvas)" stroke="var(--kc-ink)" stroke-width="1.4">
        <circle cx="252" cy="83" r="3.3"/>
        <circle cx="252" cy="40" r="3.3"/>
      </g>
      <g fill="var(--kc-ink)" font-size="17" text-anchor="middle">
        <text x="72" y="159">格 i−1</text>
        <text x="192" y="159">格 i</text>
        <text x="312" y="159">格 i+1</text>
      </g>
    </svg>
  </figure>
</section>
```

该方法的珍贵之处在于，我们只用对上一帧的数据进行少量采样，就能完整计算出这一帧的状态。每个网格可以并行计算，十分 GPU 友好。

来看看具体怎么实现吧！我们的更新思想是：新水深 = 旧水深 + 流入的水 - 流出的水。我们在每个格子保存两个物理量水深 $h$ 和水深与流速乘积 $hu$，其中 $u$ 是此处的液体水平流速，所以 $hu$ 代表单位宽度上的水流量。对于第 $i$ 个格子，更新深度的公式为：
$$ h_i^{\,t_0+\Delta t} = h_i^{\,t_0} + \frac{\Delta t}{\Delta x} \left(q_{\text{left}}-q_{\text{right}}\right) $$
其中 $q$ 为左右边界的流量。$t_0$ 为当前当前帧，$t_0+\Delta t$ 为下一帧。

我们使用纹理在两个通道分别存储 $h$ 和 $hu$。

动量的更新和水深类似：
$$ (hu)_i^{\,t_0+\Delta t} = (hu)_i^{\,t_0} + \frac{\Delta t}{\Delta x} \left(P_{\text{left}}-P_{\text{right}}\right) + \Delta t S_i$$
- $P$ 是动量通量，$P=hu^2 + \frac{gh^2}{2}$
	- $hu^2$：水流动量传递
	- $\frac12gh^2$：重力作用
- $S_i$ 为地形作用


这样，我们就可以更新缓冲区存储的 $h$ 和 $hu$ 了！

刚刚是一维坐标系的情况。如果迁移到二维网格，我们还要加一个水平维度，称其为 $v$ 吧。那我们的纹理需要存储的是 $h$, $hu$, $hv$ 三个变量。我们将它们统写为一个向量 $U$。
$$
U= \begin{bmatrix} h \\ hu\\ hv \end{bmatrix}
$$
我们把 $U$ 叫做守恒状态向量（Conserved State Vector），因为它存储了需要守恒的状态：水高 $h$（质量守恒），和动量 $hu$, $hv$（动量守恒）。

好吧，什么守恒状态根本不符合图形工程师的记忆习惯！在实际存储上，我们使用 `array<vec4<f32>>` 来存储守恒状态向量，`x, y, z` 分量分别存储 `h, hu, hv`。我并没有测试用数组更好还是用纹理更好。由于 SWE 没有双线性插值的需要，因此并没有必须选择纹理的倾向。

好了，回到 SWE 原理，我们称上述的一次更新为一个子步。为了保证模拟质量，我们不能只每帧更新一次浅水场。因为如果更新间隔过长，水面的扰动有可能跳过一个网格。我们要保证一个子步内扰动在一格的范围内。CFL（Courant–Friedrichs–Lewy） 条件可以帮助我们。这里不展开介绍该条件。直接来算下，我们一帧要更新几个子步吧！

我们用 $C$ 库朗数来表示扰动在一个子步最多能走的距离，0.1 代表能走 0.1 格宽。我们使用常数 `C`（$C_{max}$）标记库朗数最大值，也就是我们最多允许一次扰动在一个子步走的距离。设我们的水体传播速度是 `a`，一格网格宽度是 `dx`，那么一个子步的最大时间间隔  `max_dt` 就是：

```
max_dt = C * dx / a;
```

可以发现，`C` 越小，每帧模拟的子步数就更多。我们可以使用 C=0.1 ~ 2.0 扫描，来找到性能和质量的平衡点。最大传播速度 a 是水流的最大速度 + 重力因素波速度 $\sqrt{gh}$ ，我的项目中水流最大速度为 12m/s，h 估计上界根据游戏场景决定。
$$
 a=u_{max}+\sqrt{gh_{\text{估计上界}}}
$$
下面是每帧所需子步数 `n` 的计算过程。每张 SWE 网格只需要初始化时计算一次。

```rust
let a = 12.0 + (9.81 * depth_bound).sqrt();
let max_dt = 0.2 / (a / dx + a / dz);

// 把单帧推进时间限制在 100ms 和 128 个稳定子步内
let elapsed = frame_interval.min(0.1).min(max_dt * 128.0);
let n = (elapsed / max_dt).ceil().max(1.0) as u32;
```

![](<images/Pasted image 20261118123557.jpg>)
C=0.2 60FPS 每帧 19 子步 ~2.36ms

![](<images/Pasted image 20261118123651.jpg>)
C=1.1 60FPS 每帧 6 子步 ~0.43ms

可以看到，对于该场景，C = 1.1 的效果和 0.2 的效果是相近的，但开销低很多。（旁边充满细节的是 FFT 海洋，较为平缓的区域是 SWE 模拟区域）

在一阶求解器中，我们假设每个网格中的水是水平的，这个假设会更让水高容易发生扩散，抹去高频信息。导致水面视觉上变得更加平缓。同时，由于一阶求解器和网格密度不足的共同作用，这套方案无法很好的描述短波。使得来自 FFT 水域的短波几乎消失了（主要是网格密度不够）。

![](<images/Pasted image 20261118124929.jpg>)
没有加入短波法线的样子。

好在，这并不是大问题，短波可以使用法线模拟的方式在浅水模拟区域进行模拟。

### 2. 用法线模拟短波

远海和浅水需要有平滑的过度，同时，还需要将 FFT 海洋的数据传递给 SWE，让 SWE 根据 FFT 海洋的参数进行模拟。

我们使用的方法是，对于一个点，我们先从 FFT 高度场采样，获取一个点的小范围坡度，和大范围坡度，大范围坡度是长波影响的近似，小范围坡度包含这段影响。小范围坡度减去大范围坡度就是为了减去长波的影响，只保留小波的影响。（因为我们的长波已经传递给 SWE 了，SWE 可以正常表达）。这样相减之后，我们得到了这个点的坡度增量，将这个增量叠加到 SWE 的法线上，得到结果法线。

```html component
<title>短波法线计算</title>
<section class="kc-root kc-panel">
  <figure class="kc-figure" style="margin:0;padding-block:12px">
    <svg viewBox="0 0 400 290" style="max-width:560px;margin-inline:auto"
         role="img" aria-label="从 FFT 高度采样计算短波法线">
      <desc>
        沿 X 轴在中心两侧各取相距一格和四格的高度样本，分别计算两条割线的坡度。
        Z 轴同样计算。用小跨度坡度减去大跨度坡度，乘以权重后修正原法线。
      </desc>

      <!-- FFT height and sample positions in an X-axis slice. -->
      <g fill="none" stroke="var(--kc-control-line)">
        <path d="M36 36V135H364"/>
        <path d="M72 108V135 M168 105V135 M200 43V135 M232 68V135 M328 74V135"
              stroke-dasharray="3 4"/>
      </g>
      <path d="M36 116 C48 114 60 109 72 108
               C112 101 139 121 168 105
               C190 93 208 75 232 68
               C265 58 294 77 328 74
               C343 73 353 69 364 66"
            fill="none" stroke="var(--kc-ink)" stroke-width="2"/>

      <!-- Two centered differences, drawn as secants. -->
      <path d="M72 108L328 74" fill="none" stroke="var(--kc-muted)"
            stroke-width="2" stroke-dasharray="6 4"/>
      <path d="M168 105L232 68" fill="none" stroke="var(--kc-accent)"
            stroke-width="3"/>
      <g fill="var(--kc-canvas)" stroke="var(--kc-muted)" stroke-width="2">
        <circle cx="72" cy="108" r="4"/>
        <circle cx="328" cy="74" r="4"/>
      </g>
      <g fill="var(--kc-accent)">
        <circle cx="168" cy="105" r="4"/>
        <circle cx="232" cy="68" r="4"/>
      </g>

      <g fill="var(--kc-ink)" font-family="var(--kc-font)" font-size="17">
        <text x="36" y="27">FFT 高度 H</text>
        <text x="245" y="50">gₓ⁽¹⁾</text>
        <text x="317" y="101">gₓ⁽⁴⁾</text>
      </g>
      <g fill="var(--kc-muted)" font-family="var(--kc-mono)"
         font-size="16" text-anchor="middle">
        <text x="72" y="157">x−4Δ</text>
        <text x="157" y="157">x−Δ</text>
        <text x="200" y="157">x</text>
        <text x="243" y="157">x+Δ</text>
        <text x="328" y="157">x+4Δ</text>
      </g>

      <path d="M20 176H380" stroke="var(--kc-control-line)"/>

      <!-- Apply the weighted slope to the original normal. -->
      <g fill="var(--kc-ink)" font-family="var(--kc-font)" font-size="17">
        <text x="24" y="217">
          <tspan>g</tspan><tspan baseline-shift="sub" font-size="12">detail</tspan>
          <tspan> = g⁽¹⁾ − g⁽⁴⁾</tspan>
        </text>
        <text x="24" y="246">δg = g<tspan baseline-shift="sub" font-size="12">detail</tspan>
          <tspan> × 总权重</tspan>
        </text>
        <text x="310" y="211">n₀</text>
        <text x="270" y="220">n</text>
      </g>
      <g fill="none" stroke-linecap="round" stroke-linejoin="round">
        <path d="M246 261H360" stroke="var(--kc-muted)" stroke-dasharray="4 4"/>
        <path d="M248 277L358 245" stroke="var(--kc-control-line)" stroke-width="2"/>
        <path d="M303 261V216 M298 222L303 216L308 222"
              stroke="var(--kc-muted)" stroke-width="2" stroke-dasharray="4 3"/>
        <path d="M303 261L290 216 M287 224L290 216L297 221"
              stroke="var(--kc-accent)" stroke-width="2.5"/>
      </g>
      <circle cx="303" cy="261" r="3" fill="var(--kc-ink)"/>
    </svg>
  </figure>
</section>
```

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

来看一下结果吧。

![](<images/Pasted image 20261118125017.jpg>)
加入短波法线后的样子


###  3. SWE 与碰撞体求解

碰撞体使用模拟高地的方式来求解，我们设置碰撞体所在网格的高度为碰撞体的高度。
- `有效底床高度 = max(原地形高度, 覆盖该格的各碰撞体顶面高度)` 
依据此更新浅水网格的高度场。这种方式适合静态物体，不适合动态物体。因为频繁更新高度场会凭空吃掉水分。

### 4. 二阶求解器

二阶求解器分为空间和时间。

空间上，较好理解，二阶求解器将水面认为是有最大斜度的斜面，通过 MUSCL 方法计算。改成斜面后，边界的高度也就改变了。除此之外，一阶和二阶没有差异。

一阶的边界估算：
$$ U_L=U_i,\qquad U_R=U_{i+1} $$
二阶的边界估算：
$$
\delta_i=\operatorname{minmod} (U_i-U_{i-1},U_{i+1}-U_i) \\
 \begin{aligned} U_L&=U_i+\frac12\delta_i\\ U_R&=U_{i+1}-\frac12\delta_{i+1} \end{aligned} $$
$\operatorname{minmod}$ 是斜率限制器，计算方式很简单：同号取最小值，反方向取零。用于防止斜率过大，产生震荡。


```html component
<title>二阶浅水方程求解器</title>
<section class="kc-root kc-panel">
  <figure class="kc-figure" style="margin:0;padding-block:12px">
    <svg viewBox="0 0 384 170" style="max-width:520px;margin-inline:auto"
         role="img" aria-label="二阶空间重构，格内沿受限斜率线性变化">
      <rect x="132" y="16" width="120" height="120" fill="var(--kc-surface)"/>
      <path d="M12 16H372V136H12Z M132 16V136"
            stroke="var(--kc-control-line)" fill="none"/>
      <path d="M252 16V136" stroke="var(--kc-muted)" stroke-dasharray="4 4"/>
      <path d="M12 112L132 92 M132 95.35L252 70.65 M252 57.5L372 22.5"
            stroke="var(--kc-accent)" stroke-width="2" fill="none"/>
      <g fill="var(--kc-ink)">
        <circle cx="72" cy="102" r="3"/>
        <circle cx="192" cy="83" r="3"/>
        <circle cx="312" cy="40" r="3"/>
      </g>
      <g fill="var(--kc-canvas)" stroke="var(--kc-ink)" stroke-width="1.4">
        <circle cx="252" cy="70.65" r="3.3"/>
        <circle cx="252" cy="57.5" r="3.3"/>
      </g>
      <g fill="var(--kc-ink)" font-size="17" text-anchor="middle">
        <text x="72" y="159">格 i−1</text>
        <text x="192" y="159">格 i</text>
        <text x="312" y="159">格 i+1</text>
      </g>
    </svg>
  </figure>
</section>
```

二阶求解器还会使用 SSP-RK2 时间积分方法。来提高模拟精度。

> RK2 的全称是 Second-Order Runge-Kutta，译名为二阶龙格-库塔法（好中二的中文译名，喜欢）

在一阶求解中，我们在一个时间步 `dt` 的积分过程中假定水的高度保持不变，其是一个常数。但是更加符合现实的情况是，这段时间水高在变化。RK2 的思想是将水高变化近似为线性变化。

我们使用梯形积分来计算积分结果：

```html component
<title>RK2 时间积分示意</title>
<section class="kc-root kc-panel">
  <figure class="kc-figure" style="margin:0;padding:12px 8px">
    <svg viewBox="0 0 560 336" style="max-width:720px;margin-inline:auto"
         role="img" aria-label="线性变化率在一个时间步内的积分">
      <desc>
        变化率 dh/dt 从 a 线性升至 a+bΔt。灰色矩形面积为 aΔt，
        朱红色三角形面积为 ½bΔt²。
      </desc>

      <path d="M108 190H500V290H108Z" fill="var(--kc-surface)"/>
      <path d="M108 190L500 62V190Z" fill="var(--kc-accent)" fill-opacity=".24"/>

      <path d="M500 62V290" fill="none" stroke="var(--kc-muted)"
            stroke-opacity=".55" stroke-width="1.5" stroke-dasharray="6 6"/>
      <path d="M108 190H500" fill="none" stroke="var(--kc-muted)"
            stroke-width="2" stroke-dasharray="8 6"/>
      <path d="M108 34V290H543" fill="none" stroke="var(--kc-ink)"
            stroke-width="2.5"/>
      <path d="M108 190L500 62" fill="none" stroke="var(--kc-accent)"
            stroke-width="4"/>

      <g fill="var(--kc-ink)" font-family="var(--kc-font)" font-size="21">
        <g text-anchor="end">
          <text x="96" y="34">dh/dt</text>
          <text x="96" y="69">a+bΔt</text>
          <text x="96" y="197">a</text>
          <text x="96" y="319">0</text>
          <text x="543" y="319">t</text>
        </g>
        <text x="500" y="319" text-anchor="middle">Δt</text>
      </g>

      <g font-family="var(--kc-font)" font-size="24" text-anchor="middle">
        <text x="366" y="165" fill="var(--kc-ink)">½bΔt²</text>
        <text x="304" y="248" fill="var(--kc-muted)">aΔt</text>
      </g>
    </svg>
  </figure>
</section>
```

但是，在 SWE 中，一帧更新开始时（此时一格的液体状态为 $U^n$），我们并不知道终点位置的水高是多少。因此需要先执行一次完整的一阶更新，获取一阶结束时的液体状态 $U^*$。然后将起始状态变化率和预测变化率取平均，获得目标水高。然后使用梯形积分就可以了。

斜率用 $k$ 来表达，$k=L(U)$，$L(U)$ 从直觉上理解，就是根据当前水体状态，计算水深和动量随时间的变化率。 

$$
L(U)= \begin{bmatrix} \text{水深变化率}\\ \text{X 动量变化率}\\ \text{Y 动量变化率} \end{bmatrix}
$$
我们将初始状态变化率表达为 $k_1$，一阶状态变化率表示为 $k_2$，最终守恒状态为：
$$
U^{n+1}=U^n + \frac{k_1+k_2}{2}\Delta t
$$

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