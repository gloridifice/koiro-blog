```toml
title = "快速物理模拟水：浅水方程与泡沫渲染 | 实时水体渲染 vol.1"
summary = "本文是浅水模拟模块的第一期，也是水体渲染的第一期。主要介绍用一阶和二阶浅水方程求解器来做快速物理水，以及一个双浓度泡沫流送、纹理位移、方案着色。"
ai-translation = false
```

本博文有 rust + wgpu 实现，源码看这里哦：
https://github.com/gloridifice/wgpu-water-simulation

![foam](images/with-flow-wraped.jpg)

# 前言

流体有两种描述方式，一种是欧拉描述，关注固定空间位置上的流体状态。模拟时，我们可以用空间网格来记录这些物理量。另一种流体描述方式为拉格朗日描述，其将流体视为无数个小微团，每个微团是一个粒子，粒子之间相互作用产生流体演变。由于通过后者模拟真实水体的计算量很大，实时渲染的物理水方案通常使用前者。

本文不会具体介绍欧拉方程。我们需要知道的是：欧拉方程指忽略粘性的流体运动方程。这里把水视为不可压缩的流体，也就是三维流速的散度为 0：
$$\nabla\cdot\mathbf v=0$$
从欧拉方程推导到浅水方程的过程较为枯燥，因此本文不会涉及。本文会涉及实践上的公式，这些公式最终会被写到代码中。

# 一、水体 - 浅水方程（SWE）

### 1. 浅水方程一阶求解器

浅水方程是欧拉方程的一种简化近似。原本要记录三维空间的流体场，在浅水方程中，我们只记录水深和沿水深平均的水平流速。可以从直觉上来想象：把水体划分成二维网格，每个格子里都有一柱水。每格的水量和动量怎么变，就看四周网格面上的通量。

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

该方法的珍贵之处在于，我们只用对上一个子步的数据进行少量采样，就能算出这个子步的状态。每个网格可以并行计算，十分 GPU 友好。

来看看具体怎么实现吧！我们的更新思想是：新水深 = 旧水深 + 流入的水 - 流出的水。我们在每个格子保存两个物理量水深 $h$ 和水深与流速乘积 $hu$，其中 $u$ 是此处的液体水平流速，所以 $hu$ 代表单位宽度上的水流量。对于第 $i$ 个格子，更新深度的公式为：
$$ h_i^{\,t_0+\Delta t} = h_i^{\,t_0} + \frac{\Delta t}{\Delta x} \left(q_{\text{left}}-q_{\text{right}}\right) $$
其中 $q$ 为对应网格面上单位宽度的流量，$t_0$ 和 $t_0+\Delta t$ 分别是这次更新的起止时刻。

动量的更新和水深类似：
$$ (hu)_i^{\,t_0+\Delta t} = (hu)_i^{\,t_0} + \frac{\Delta t}{\Delta x} \left(P_{\text{left}}-P_{\text{right}}\right) + \Delta t S_i$$
这里 $P$ 是网格面上的动量通量，$S_i$ 为地形源项。怎么求 $q$ 和 $P$ 呢？一维状态 $U=(h,hu)^\mathsf{T}$ 的物理通量是：
$$ F(U)=\begin{pmatrix}hu\\ hu^2+\tfrac12gh^2\end{pmatrix} $$
其中 $hu^2$ 来自水流携带的动量，$\tfrac12gh^2$ 是静水压力的贡献。网格面有左右两个状态 $U_L,U_R$，我们用 Rusanov 数值通量把两侧都算进去：
$$ \widehat F=\tfrac12\big[F(U_L)+F(U_R)\big]-\tfrac12 a(U_R-U_L) $$
$a$ 取两侧 $|u|+\sqrt{gh}$ 的最大值。同一个面上的 $q$ 和 $P$ 就是 $\widehat F$ 的第一、第二个分量。项目中还做了静水重构和压力修正，以处理起伏的底床。

这样，我们就可以更新缓冲区存储的 $h$ 和 $hu$ 了！

刚刚是一维坐标系的情况。如果迁移到二维网格，我们用 $u,v$ 分别表示 X、Z 方向的流速。那每格需要存储的是 $h$, $hu$, $hv$ 三个变量。我们将它们统写为一个向量 $U$。
$$
U= \begin{bmatrix} h \\ hu\\ hv \end{bmatrix}
$$
我们把 $U$ 叫做守恒状态向量（Conserved State Vector），因为它存储了需要守恒的状态：水深 $h$（质量守恒），和动量 $hu$, $hv$（动量守恒）。

好吧，什么守恒状态根本不符合图形工程师的记忆习惯！在实际存储上，我们使用 `array<vec4<f32>>` 来存储守恒状态向量，`x, y, z` 分量分别存储 `h, hu, hv`。我并没有测试用数组更好还是用纹理更好。由于 SWE 没有双线性插值的需要，因此并没有必须选择纹理的倾向。

好了，回到 SWE 原理，我们称上述的一次更新为一个子步。为了保证模拟质量，我们不能只每帧更新一次浅水场。因为如果更新间隔过长，水面的扰动有可能跳过一个网格。要根据网格间距和传播速度来限制子步时长。CFL（Courant–Friedrichs–Lewy）条件可以帮助我们。这里不展开介绍该条件。直接来算下，我们一帧要更新几个子步吧！

先看一维：库朗数表示扰动在一个子步走了多少格，0.1 就是 0.1 格宽。我们用常数 `C` 表示选定的库朗数上限。设最大传播速度是 `a`，一格网格宽度是 `dx`，那么一个子步的最大时间间隔  `max_dt` 就是：

```
max_dt = C * dx / a;
```

可以发现，`C` 越小，每帧模拟的子步数就更多。调参时可以在 C=0.1 ~ 2.0 之间做扫描，看看性能和质量怎么取舍。这个范围不代表稳定范围，能否稳定还取决于离散格式和波速估计。最大传播速度 a 是水流的最大速度 + 重力因素波速度 $\sqrt{gh}$ ，我的项目中水流最大速度为 12m/s，h 估计上界根据游戏场景决定。
$$
 a=u_{max}+\sqrt{gh_{\text{估计上界}}}
$$
到了二维，X、Z 两个方向都要算进去。下面以 C=0.2 为例，每帧根据实际推进时间计算子步数 `n`：

```rust
let a = 12.0 + (9.81 * depth_bound).sqrt();
let max_dt = 0.2 / (a / dx + a / dz);

// 把单帧推进时间限制在 100ms 和 128 个稳定子步内
let elapsed = frame_interval.min(0.1).min(max_dt * 128.0);
let n = (elapsed / max_dt).ceil().max(1.0) as u32;
let dt = elapsed / n as f32;
```

![](<images/Pasted image 20261118123557.jpg>)
C=0.2 60FPS 每帧 19 子步 ~2.36ms

![](<images/Pasted image 20261118123651.jpg>)
C=1.1 60FPS 每帧 4 子步 ~0.43ms

可以看到，对于该场景，C = 1.1 的效果和 0.2 的效果是相近的，但开销低很多。（旁边充满细节的是 FFT 海洋，较为平缓的区域是 SWE 模拟区域）

在一阶求解器中，我们把每格里的水深看作常数。一阶格式的数值扩散会抹去高频细节，网格又不够密，来自 FFT 水域的短波几乎消失了，水面看起来就平缓得多。

![](<images/Pasted image 20261118124929.jpg>)
没有加入短波法线的样子。

好在，这并不是大问题，短波可以使用法线模拟的方式在浅水模拟区域进行模拟。

### 2. 用法线模拟短波

来看看怎么计算短波法线吧！远海和浅水需要有平滑的过渡。长波已经传给了 SWE，短波细节则留到着色时再补。

我们使用的方法是，对于一个点，我们先从 FFT 高度场采样，获取一个点的小范围坡度，和大范围坡度，大范围坡度是长波影响的近似，小范围坡度包含这段影响。小范围坡度减去大范围坡度就是为了减去长波的影响，估算短波的影响。将这个坡度差加权后叠加到 SWE 的水面坡度上，再重建法线。

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
- $A$ 是 FFT 高度增益
- $s$ 是以格数计的采样步长，中心到采样点的距离是 $s\Delta$

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

碰撞体使用模拟高地的方式来求解，我们用碰撞体的顶面来抬高所在格子的有效底床：
- `有效底床高度 = max(原地形高度, 覆盖该格的各碰撞体顶面高度)` 
这里改的是底床，不是水深。这种方式适合静态物体；换成动态物体，底床突然抬高时，被挤占的水量还需要随更新重新分配。

### 4. 二阶求解器

二阶求解器分为空间和时间。

空间上，较好理解，二阶求解器将水面认为是有最大斜度的斜面，通过 MUSCL 方法计算。改成斜面后，边界的高度也就改变了，但还是用同一个数值通量公式。下面用 $U$ 的一个分量举例。

一阶的边界估算：
$$ U_L=U_i,\qquad U_R=U_{i+1} $$
二阶的边界估算：
$$
\begin{aligned}
\delta_i&=\operatorname{minmod}(U_i-U_{i-1},U_{i+1}-U_i) \\
U_L&=U_i+\tfrac12\delta_i,\qquad U_R=U_{i+1}-\tfrac12\delta_{i+1}
\end{aligned}
$$
$\operatorname{minmod}$ 是斜率限制器：同号时保留符号，取绝对值较小的那个；异号或有零时返回零。这样可以防止斜率过大，产生振荡。项目里重构的是水面高程和水平流速，底床单独重构，用的是带 1.3 系数的广义 minmod。


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

二阶求解器还会使用 SSP-RK2 时间积分方法来提高模拟精度。

> RK2 的全称是 Second-Order Runge-Kutta，译名为二阶龙格-库塔法（好中二的中文译名，喜欢）

在一阶求解中，显式欧拉用起点的变化率来近似整个子步。SSP-RK2 则取起点和预测终点的变化率，两者的平均值乘以子步时长，就是状态的增量。

拿水深举例，图里连成直线的是水深变化率 $dh/dt$。线下的梯形面积才是这个子步的水深增量：

```html component
<title>RK2 时间积分示意</title>
<section class="kc-root kc-panel">
  <figure class="kc-figure" style="margin:0;padding:12px 8px">
    <svg viewBox="0 0 560 336" style="max-width:720px;margin-inline:auto"
         role="img" aria-label="线性变化率在一个子步内的积分">
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

但是，在 SWE 中，子步开始时我们只知道起点状态 $U^n$。终点的变化率从哪里来？先用显式欧拉预测终点状态 $U^*$，再用它算一次变化率就可以了。

斜率用 $k$ 来表达，$k=L(U)$，$L(U)$ 从直觉上理解，就是根据当前水体状态，计算水深和动量随时间的变化率。 

$$
L(U)= \begin{bmatrix} \text{水深变化率}\\ \text{X 动量变化率}\\ \text{Z 动量变化率} \end{bmatrix}
$$
起点变化率是 $k_1=L(U^n)$，预测状态是 $U^*=U^n+\Delta t\,k_1$，预测终点的变化率就是 $k_2=L(U^*)$。最终守恒状态为：
$$
U^{n+1}=U^n + \frac{k_1+k_2}{2}\Delta t
$$

# 二、浅水泡沫

现实海洋中，海浪破碎时会卷入空气，在水面形成泡沫。

看岸边的浪：前方的海水减速、回退时，会和后方前进的海水挤在一起。泡沫也会随水前进，在水流汇聚的地方变浓。此外，水里的气泡还会不断上浮，补充水面的泡沫。不同大小和运动状态的泡沫，消散的速度也不一样。

这么看来，我们无法从物理上实时模拟气泡。使用务实的思路，我们可以将气泡简化为随 SWE 水流移动的泡沫浓度。浓度越高的地方，我们用看起来更密实的纹理来着色；更稀疏的地方，则使用有明显空隙的网格纹理来着色。这样可以模拟出现实中的两种气泡视觉效果。

![](images/ocean_foam_patterns.jpg)

图为现实中的泡沫，一个大浪会形成许多小浪，两者都会卷入空气形成泡沫。大浪会形成厚实的泡沫，数个小浪则形成数个湾状泡沫。紫色部分同时有大浪和小浪贡献，而网纹状的泡沫（绿色）主要来自于小浪流动和汇聚。

![](images/shoreline_foam_front.jpg)

海浪最前端常能看到一条泡沫前沿。前方水流慢下来，后面的泡沫还在继续向前汇聚，这样前沿就能维持较高的泡沫浓度。

经过观察，我们用下面四个特征来更新“泡沫浓度”：

| 效果   | 随水移动   | 汇聚变浓   | 泡沫生成        | 逐渐消失       |
| ---- | ------ | ------ | ----------- | ---------- |
| 实现方式 | 泡沫流体模拟 | 泡沫流体模拟 | 根据 SWE 状态实现 | 使用指数衰减降低浓度 |

我们用两个标量场分别表示新鲜泡沫浓度 $N$（New）和残余泡沫浓度 $R$（Rest）。它们随同一股水流移动，但有各自的着色方式和衰减参数，衰减的新鲜泡沫还有一部分会转为残余泡沫。

在我的实现中，泡沫网格的分辨率比 SWE 高一倍，让泡沫有更多细节。缓冲区实际存的是总浓度 $F=N+R$（Full）和残余浓度 $R$，用 $F-R$ 就能还原 $N$。

### 1. 随水移动 & 汇聚变浓

随水移动的部分十分简单：用当前格子的流速乘以子步时长，得到位移，再沿位移反向查找泡沫的来源。这个过程叫“半拉格朗日回溯”。源位置不一定落在格子中心，所以我们用邻近的四个格子做双线性插值。以新鲜泡沫为例，将采样得到的浓度记为 $N_{previous}$。

汇聚变浓的部分也很好理解：水流汇聚时，泡沫挤在一起；水流散开时，泡沫就变稀。我们用“速度散度”来描述这个效果：
$$
d_{i,j} = \frac{u_{x+} - u_{x-}}{\Delta x} + \frac{u_{z+} - u_{z-}}{\Delta z}
$$
这里 $u_{x+}$ 和 $u_{x-}$ 是同一个格子右、左两个网格面上的 X 向流速，都以 X 正方向为正。两个面相距 $\Delta x$；Z 方向同理。
$$
N^* = N_{previous} \cdot e^{-d_{i,j} \Delta t}
$$
浓度输送的部分就这么简单。$N^*$ 是输送后的新鲜泡沫浓度，残余泡沫的 $R^*$ 也用同样的方法计算。

### 2. 泡沫生成

我们用水流挤压来估计哪里可能生成泡沫。例如有两个格子：

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
下面只看沿流向挤压产生泡沫的简化模型。先把压缩率映射成权重：
$$
t=clamp(\frac{\tau_{compress} - T}{W} ,0,1)
$$
T 和 W 是两个外部控制参数，分别对应阈值和过渡宽度。压缩率不超过 $T$ 时权重为 0，在 $T$ 到 $T+W$ 之间平滑增加，达到 $T+W$ 后保持为 1，仍可生成泡沫。过渡区间用 smoothstep 平滑：
$$
s=t^2(3 - 2t)
$$
这一项的生成率为：
$$
S = A_{param}\cdot s \cdot smoothstep(0.02, 0.1, |\vec u|)
$$
$A_{param}$ 是系数倍率，是给用户控制的外部参数。而最后一项是抑制低速水流，速度不超过 0.02 米/秒时不发射，达到 0.1 米/秒后不再减弱。只计生成时，一个子步的新增浓度就是 $S\Delta t$。

### 3. 逐渐消失

我们希望让用户控制泡沫的半衰期 $H_N$，半衰期与衰减率的公式为 $a = \frac{\ln 2}{H_N}$，那么只考虑衰减时，公式为：
$$
N_{new} = N^* e^{-a \Delta t}
$$
再算上生成，新鲜泡沫浓度的净变化率就是 $S-aN$。

### 4. 新鲜泡沫转为残余泡沫

我们认为，衰减的新鲜泡沫中有比例 $f$ 会转为残余泡沫，另一部分则直接消失。单看转化这一项，残余泡沫浓度的变化率为：
$$
\left.\frac{dR}{dt}\right|_{\text{转化}}=faN
$$
残余泡沫自身也有半衰期 $H_R$，对应的衰减率为 $b$。算上衰减，两种泡沫浓度的净变化率分别为：
$$ \frac{dN}{dt}=S-aN,\qquad \frac{dR}{dt}=faN-bR $$
需要注意的是，这里 R 公式中的 N 不是常数，而是关于 t 的函数 $N(t)$，在积分时需要考虑这一点。
### 汇总

更新顺序是，先输送，再处理生成、转化和衰减。用输送后的浓度 $N^*,R^*$ 作为初值，在这个子步内把 $S,a,b,f$ 当作常量，从 0 积分到 $\Delta t$，得到：
$$
N_{new}=N^*e^{-a\Delta t}+\frac{S}{a}(1-e^{-a\Delta t})
$$
残余泡沫类似，积分得到：
$$
 \begin{aligned}R_{\text{new}} &=R^*e^{-b\Delta t} +faN^*K +fS\left[\frac{1-e^{-b\Delta t}}{b}-K\right]\\K&=\frac{e^{-b\Delta t}-e^{-a\Delta t}}{a-b} \end{aligned}
$$
其中 $b=\frac{\ln 2}{H_R}$。如果两种泡沫的半衰期相同，$a=b$，这时不能直接用上面的分式，要取极限 $K=\Delta t\,e^{-b\Delta t}$。项目里也单独处理了两个衰减率相等或很接近的情况。

# 三、泡沫着色

刚刚了解了泡沫浓度如何更新，该着色了。浓度没有上限，我们先把新鲜泡沫的浓度映射到 $[0,1)$，得到覆盖率参数：
$$
c_N = 1 - e^{-N/k}
$$
其中 $k$ 是用户控制的正数。同样的浓度下，$k$ 越大，覆盖率越低，泡沫看起来越淡。

对于新鲜泡沫，先看二值的情况。为了让边缘不规则，我们把噪声值和 $c_N$ 比较：噪声值低于 $c_N$ 就显示白色泡沫（不透明度 $\alpha=1$），否则不显示（$\alpha=0$）。这样 $c_N$ 越大，白色覆盖的面积就越大。实际着色时用 `smoothstep` 把边缘稍微抹平。

残余泡沫则在噪声场中截取一条等值带，画出网格状的泡沫细丝。残余浓度 $R$ 越高，等值带就越宽。

```html component
<figure class="kc-compare">
  <img data-side="a" src="images/rest-only.jpg" alt="仅残余泡沫">
  <img data-side="b" src="images/with-flow-wraped.jpg" alt="残余泡沫+新鲜泡沫">
  <figcaption>左：仅残余泡沫；右：残余泡沫+新鲜泡沫</figcaption>
</figure>
```

### 泡沫扭曲

目前泡沫没有跟随水流的拖曳和扭曲效果，需要实现一下。我们使用一个纹理位移场 $\mathbf D$ 来记录随水流的纹理位移。
$$
\mathbf D_{new}(\mathbf x) = \mathbf D_{old}(\mathbf x-\mathbf u\Delta t) +\mathbf u\Delta t
$$
初始 $\mathbf D = \mathbf 0$，$\mathbf x$ 为位置坐标。这里记录的是正向位移，所以画 $\mathbf x$ 处的泡沫时，要回到 $\mathbf x-\mathbf D(\mathbf x)$ 处采样纹理。不过如果该场一直流动，那么其纹理会不断偏移下去，我们需要一些重置机制防止偏移值不断变大，出现过于扭曲的效果。

重置机制如下：

我们设重置周期为 $T$，我们使用两层 $\mathbf D_A, \mathbf D_B$ 来存储流动。每层都在自己的相位回绕时把位移归零。如果 A 层在这个子步内回绕，到子步结束时就要补上回绕后那一小段时间的位移：$\mathbf D_A=\mathbf u\,(\phi_{new}T)$。B 层和 A 层错开半个周期，所以 B 层回绕的子步同理补上 $\mathbf D_B=\mathbf u\left[\operatorname{fract}(\phi_{\text{new}}+0.5)T\right]$。
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

```html component
<figure class="kc-compare">
  <img data-side="a" src="images/without-flow-wrap.jpg" alt="无扭曲">
  <img data-side="b" src="images/with-flow-wraped.jpg" alt="有扭曲">
  <figcaption>左：无扭曲；右：有扭曲</figcaption>
</figure>
```


### 泡沫纹理

除了使用生成式噪波来做新鲜泡沫，也可以使用纹理模式渲染新鲜泡沫。将纹理的灰度作为显示阈值，使用新鲜泡沫浓度比较阈值来控制该像素是否显示泡沫。下面是两种纹理的显示效果对比。

```html component
<figure class="kc-compare">
  <img data-side="a" src="images/fresh-crest-2.jpg" alt="crest 纹理">
  <img data-side="b" src="images/fresh-shorewave.jpg" alt="shortwave 纹理">
  <figcaption>左：crest 纹理；右：shortwave 纹理</figcaption>
</figure>
```

# 结语

至此，我们终于有一个可用的浅水方程速度还不错的物理水模拟。不过现在还没有浮力、瀑布、长海岸等等。这是未来需要处理的工作。

下面是 RTX 3070，C=1.1 的两个场景的 GPU 耗时表现：

| 场景     | SWE 模拟/ms | SWE 尺寸    | 子步数 |
| ------ | --------- | --------- | --- |
| 礁石 2 阶 | 0.44      | 256 * 256 | 4   |
| 礁石 1 阶 | 0.28      | 256 * 256 | 4   |
| 海岸 2 阶 | 0.19      | 256 * 256 | 2   |
| 海岸 1 阶 | 0.10      | 256 * 256 | 2   |


一些其它图片：

![海岸场景](images/ocean-1795156839104306900.jpg)
海岸场景
