```toml
title = "时之笛 Re 的雾效渲染以及更多雾效流体模拟"
summary = "时之笛重制版的雾效模拟被玩家们津津乐道。我也研究了一下这个方向。在确定使用高度场和 froxel 时间重投影这两个成熟的方案的基础上。发现主要流体模拟部分有两种方案，我分别让 AI 做了两种方案的 Godot 实现。结果居然……"
ai-translation = false
```

# 前言

时之笛重制版的雾效模拟被玩家们津津乐道。正好最近在学习流体模拟，我也一并研究了一下这个实现。在确定使用高度场和 froxel 时间重投影渲染雾效，这两个成熟的方案的基础上。发现雾体流体模拟部分有两种方案：SWE 和 Stable Fluid，我分别让 AI 做了两种方案的 Godot 实现。经过数轮迭代和探索，我得到了不错的结果！本文会介绍两种风格不同的近地雾效模拟的方案。

根据实现效果推测，时之笛采用的方案可能是：
噪声高度场 + Stable Fluid 偏移高度场的采样位置 + 高度场增益纹理

常见的高度场流体模拟可以使用 SWE 浅水模拟方案。通过计算格子间的压力来计算浓度差，进而更新网格。要想让 SWE 模拟出时之笛的雾效会面临两个问题：

第一个问题是，SWE 难以形成稳定的噪波。来自两个原因：
1. 一阶浅水方程求解器因为使用对网格双线性采样，近似精度不足，丢失高频信息。会造成浓度扩散
	- 可以解决：使用二阶求解器可以有效缓解，但会增加开销
2. 重力会使得雾效自然铺平
	- 可以解决：只在一定高度以上的网格计算重力来解决，并因为风的流动，循环输送新的噪声雾场

第二个更重要的问题，也是确定时之笛没有使用 SWE 的关键证据——SWE 无法形成湍流。我们先来聊聊更贴近时之笛实现的方法。

# 方法 1：Stable Fluid + 高度场



# 方法 2：浅水方程（SWE）


# 后记：雾体着色

本文不过多介绍雾体着色，只简单带过一下，商用引擎一般都有成熟的体积雾渲染。

光线在穿过一个介质的时候会发生两种行为：被吸收和被散射。散射的一部分光会进入相机，视为贡献；一部分会射向其它方向，视为损失。吸收则是完全的损失。

光线经过散射和吸收后，会打到雾体后面的物体上，按照 BRDF 反射回来，再经过一轮吸收和反射。我们称光在这个过程中发生损失的现象为”消光“。实时渲染中常使用的简化是：消光是一个系数，入射光直接乘以这个系数得到出射光。”吸收系数“标记为 $\sigma_a$，”散射系数“标记为 $\sigma_s$ ，总”消光系数“标记为 $\sigma_t=\sigma_a + \sigma_s$.

我们设路径长度为 $s$，光线在介质中传播的微分方程表示为：
$$
\frac{dL}{ds}=-\sigma_tL+S
$$
这里 $S$ 代表其它其它光源光线散射贡献到该光线方向的光，这一项很难符合物理处理。实时渲染中，我们简化其为常数。

对上述微分方程进行积分：
$$ L(d)=L_0e^{-\sigma_t d} +\frac{S}{\sigma_t}(1-e^{-\sigma_t d}) $$
这是一个只与光线穿过介质的距离有关的函数～所以只要我们计算出光源射线到不透明物体片源间，射线穿过的雾的距离，就可以进行算出其到达片源的辐亮度。

## Froxel

好，实时渲染雾效会面临两个问题：

每个像素的计算中：

每个光源都要计算一遍在雾体的路程。以我们的高度场二次线性插值 + 射线步进为例，每次插值要采样四个点，步进 32 次就是 128 次采样！即使这样，射线步进还是会跳过一些较薄的区域，导致这块没雾。此外，二次线性插值是一阶连续，重建效果一般。更好地重建算法需要更大的开销。

好，图形学经典之——要想有还可以的质量又便宜，必须动用 10 年以前的技术了！

Froxel 体积雾技术最早在 2013 年被使用：

| 时间     | 代表工作                                       | 主要贡献                                           |
| ------ | ------------------------------------------ | ---------------------------------------------- |
| 2013 年 | Ubisoft《刺客信条 IV：黑旗》                        | 在游戏中实际应用基于三维体积纹理的实时体积雾技术                       |
| 2014 年 | Bart Wronski，SIGGRAPH 2014                 | 系统提出视锥对齐的 3D Volume 缓存、光照注入与体积积分方案             |
| 2015 年 | Sébastien Hillaire，SIGGRAPH 2015，Frostbite | 明确使用 Froxel（Frustum Voxel） 术语，完善物理一致性与统一体积渲染框架 |

最常见和快速的方案是 Froxel，将相机视锥分割成三维网格（用一个三维纹理存储）。通常使用对数切片，这样离相机更近的区域得到更好的精度。

```html component
<title>Froxel 视锥网格示意图</title>

<style>
  .froxel {
    color-scheme: dark;
    background: #0d0d0d;
    color: #eae7e7;
    border: 1px solid #292929;
    border-radius: 6px;
    font: 13px/1.6 system-ui, sans-serif;
    overflow: hidden;
  }
  .froxel, .froxel * { box-sizing: border-box; }
  .froxel canvas {
    display: block;
    width: 100%;
    height: clamp(260px, 48vw, 390px);
    cursor: grab;
    touch-action: none;
  }
  .froxel canvas:active { cursor: grabbing; }
  .froxel :focus-visible {
    outline: 2px solid #93c5fd;
    outline-offset: -3px;
  }
  .froxel .controls {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 16px;
    padding: 16px;
    border-top: 1px solid #292929;
  }
  .froxel label { display: grid; gap: 6px; min-width: 0; }
  .froxel label span {
    display: flex;
    justify-content: space-between;
    gap: 8px;
  }
  .froxel output { color: #ffad83; font-variant-numeric: tabular-nums; }
  .froxel input { width: 100%; accent-color: #ce4623; }
  .froxel input[type="range"] { height: 32px; margin: 0; }
  .froxel select, .froxel button {
    min-height: 40px;
    padding: 6px 10px;
    border: 1px solid #444;
    border-radius: 4px;
    background: #171717;
    color: inherit;
    font: inherit;
    cursor: pointer;
  }
  .froxel .footer {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 12px 16px;
    background: #171717;
  }
  .froxel .readout { margin: 0; color: #b7b3b0; }
  .froxel .hint { margin: 0; padding: 0 16px 12px; color: #b7b3b0; }
  @media (max-width: 520px) {
    .froxel .controls { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  }
</style>

<section class="froxel" aria-label="Froxel 交互示意图">
  <canvas
    tabindex="0"
    role="img"
    aria-label="视锥网格。拖拽或使用方向键旋转；下方滑杆选择单元。"
  >浏览器需要支持 Canvas 才能显示示意图。</canvas>

  <p class="hint">拖拽／方向键旋转 · 灰色：网格 · 橙色：选中单元 · 虚线：视线</p>

  <div class="controls">
    <label>
      网格分辨率
      <select data-grid>
        <option value="4,3,8">4 × 3 × 8</option>
        <option value="8,6,16" selected>8 × 6 × 16</option>
        <option value="12,9,24">12 × 9 × 24</option>
      </select>
    </label>

    <label>
      深度分布
      <select data-mode>
        <option value="log">对数切片</option>
        <option value="linear">线性切片</option>
      </select>
    </label>

    <label>
      <span>深度层 k <output data-value="k"></output></span>
      <input data-axis="k" aria-label="深度层 k" type="range"
             min="0" max="15" value="7" step="1">
    </label>

    <label>
      <span>横向索引 i <output data-value="i"></output></span>
      <input data-axis="i" aria-label="横向索引 i" type="range"
             min="0" max="7" value="4" step="1">
    </label>

    <label>
      <span>纵向索引 j <output data-value="j"></output></span>
      <input data-axis="j" aria-label="纵向索引 j" type="range"
             min="0" max="5" value="3" step="1">
    </label>
  </div>

  <div class="footer">
    <p class="readout"></p>
    <button type="button" data-reset>重置视角</button>
  </div>
</section>

<script>
(() => {
  const root = document.currentScript.previousElementSibling;
  const canvas = root.querySelector("canvas");
  const ctx = canvas.getContext("2d");
  const grid = root.querySelector("[data-grid]");
  const mode = root.querySelector("[data-mode]");
  const readout = root.querySelector(".readout");
  const inputs = Object.fromEntries(
    [...root.querySelectorAll("[data-axis]")].map(el => [el.dataset.axis, el])
  );

  if (!ctx) {
    readout.textContent = "当前浏览器无法创建 Canvas 2D 上下文。";
    return;
  }

  // 相机空间：+Z 向前；深度为视轴距离，不是射线长度。
  const near = 1, far = 16;
  const tanHalfFov = Math.tan(Math.PI / 6); // 垂直 FOV = 60°
  const aspect = 4 / 3;

  let nx = 8, ny = 6, nz = 16;
  let yaw = -0.65, pitch = 0.28;
  let width = 0, height = 0, scale = 1;
  let drag = null, frame = 0;

  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));

  function depth(k) {
    const t = k / nz;
    return mode.value === "log"
      ? near * Math.pow(far / near, t)
      : near + (far - near) * t;
  }

  // 网格边界 (u,v,z) → 相机空间。
  function point(u, v, z) {
    return [
      (2 * u - 1) * z * tanHalfFov * aspect,
      (1 - 2 * v) * z * tanHalfFov,
      z
    ];
  }

  // 外部观察者使用正交投影；被观察的网格仍是透视视锥。
  function rotate([x, y, z]) {
    z -= far / 2;
    const rx = Math.cos(yaw) * x + Math.sin(yaw) * z;
    const rz = -Math.sin(yaw) * x + Math.cos(yaw) * z;
    return [
      rx,
      Math.cos(pitch) * y - Math.sin(pitch) * rz,
      Math.sin(pitch) * y + Math.cos(pitch) * rz
    ];
  }

  function project(p) {
    const [x, y] = rotate(p);
    return [width / 2 + x * scale, height / 2 - y * scale];
  }

  function path(points, closed = false) {
    ctx.beginPath();
    points.forEach((p, index) => {
      const [x, y] = project(p);
      if (index === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    if (closed) ctx.closePath();
  }

  function line(points, color, thickness = 1) {
    path(points);
    ctx.strokeStyle = color;
    ctx.lineWidth = thickness;
    ctx.stroke();
  }

  function quad(points, fill, stroke) {
    path(points, true);
    ctx.fillStyle = fill;
    ctx.fill();
    if (stroke) {
      ctx.strokeStyle = stroke;
      ctx.lineWidth = 1.5;
      ctx.stroke();
    }
  }

  function plane(z) {
    return [
      point(0, 0, z), point(1, 0, z),
      point(1, 1, z), point(0, 1, z)
    ];
  }

  function draw() {
    frame = 0;
    if (!width || !height) return;

    ctx.clearRect(0, 0, width, height);

    const i = Number(inputs.i.value);
    const j = Number(inputs.j.value);
    const k = Number(inputs.k.value);
    const z0 = depth(k), z1 = depth(k + 1);

    // 固定包围球，旋转时不产生自动缩放。
    const radius = Math.hypot(
      far * tanHalfFov * aspect,
      far * tanHalfFov,
      far / 2
    );
    scale = Math.min(width - 48, height - 48) / (2 * radius);

    // 每一层的边界。
    for (let s = 0; s <= nz; s++) {
      const corners = plane(depth(s));
      line([...corners, corners[0]], "#515151", 0.8);
    }

    // 固定屏幕位置对应的纵深线。
    for (let x = 0; x <= nx; x++) {
      for (let y = 0; y <= ny; y++) {
        line([
          point(x / nx, y / ny, near),
          point(x / nx, y / ny, far)
        ], "#414141", 0.7);
      }
    }

    // 选中深度层的前后边界。
    quad(plane(z0), "rgba(206,70,35,.07)", "#a75b43");
    quad(plane(z1), "rgba(206,70,35,.07)", "#a75b43");

    // 选中单元：两个四边形截面组成截锥，而不是等边立方体。
    const section = z => [
      point(i / nx, j / ny, z),
      point((i + 1) / nx, j / ny, z),
      point((i + 1) / nx, (j + 1) / ny, z),
      point(i / nx, (j + 1) / ny, z)
    ];
    const vertices = [...section(z0), ...section(z1)];
    const faces = [
      [0, 1, 2, 3], [4, 5, 6, 7],
      [0, 1, 5, 4], [1, 2, 6, 5],
      [2, 3, 7, 6], [3, 0, 4, 7]
    ];

    // 透明面按观察空间深度从远到近绘制。
    faces.sort((a, b) => {
      const average = f =>
        f.reduce((sum, index) => sum + rotate(vertices[index])[2], 0) / 4;
      return average(a) - average(b);
    });

    for (const face of faces) {
      quad(
        face.map(index => vertices[index]),
        "rgba(238,114,65,.22)",
        "#ffad83"
      );
    }

    // 相机到选中单元所在屏幕分块中心的视线。
    ctx.setLineDash([5, 5]);
    line([
      [0, 0, 0],
      point((i + 0.5) / nx, (j + 0.5) / ny, far)
    ], "#f4d3ab", 1.5);
    ctx.setLineDash([]);

    const [cx, cy] = project([0, 0, 0]);
    ctx.beginPath();
    ctx.arc(cx, cy, 4, 0, Math.PI * 2);
    ctx.fillStyle = "#eae7e7";
    ctx.fill();
    ctx.font = "12px system-ui";
    ctx.fillText("相机", cx + 9, cy - 8);

    for (const axis of ["i", "j", "k"]) {
      root.querySelector(`[data-value="${axis}"]`).textContent =
        inputs[axis].value;
    }

    readout.textContent =
      `单元 (${i}, ${j}, ${k}) · z = ${z0.toFixed(2)}–${z1.toFixed(2)} m` +
      ` · 共 ${(nx * ny * nz).toLocaleString()} 个`;
  }

  function schedule() {
    if (!frame) frame = requestAnimationFrame(draw);
  }

  function resize() {
    const rect = canvas.getBoundingClientRect();
    width = rect.width;
    height = rect.height;
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = Math.round(width * dpr);
    canvas.height = Math.round(height * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    schedule();
  }

  grid.addEventListener("change", () => {
    const previous = [nx, ny, nz];
    [nx, ny, nz] = grid.value.split(",").map(Number);

    ["i", "j", "k"].forEach((axis, index) => {
      const input = inputs[axis];
      const size = [nx, ny, nz][index];
      const fraction = (Number(input.value) + 0.5) / previous[index];
      input.max = size - 1;
      input.value = clamp(Math.floor(fraction * size), 0, size - 1);
    });
    schedule();
  });

  mode.addEventListener("change", schedule);
  Object.values(inputs).forEach(input =>
    input.addEventListener("input", schedule)
  );

  canvas.addEventListener("pointerdown", event => {
    if (!event.isPrimary || event.button !== 0) return;
    drag = { id: event.pointerId, x: event.clientX, y: event.clientY };
    canvas.setPointerCapture(event.pointerId);
  });

  canvas.addEventListener("pointermove", event => {
    if (!drag || drag.id !== event.pointerId) return;
    yaw += (event.clientX - drag.x) * 0.008;
    pitch = clamp(pitch + (event.clientY - drag.y) * 0.008, -1.2, 1.2);
    drag.x = event.clientX;
    drag.y = event.clientY;
    schedule();
  });

  function endDrag(event) {
    if (drag && drag.id === event.pointerId) drag = null;
  }
  canvas.addEventListener("pointerup", endDrag);
  canvas.addEventListener("pointercancel", endDrag);
  canvas.addEventListener("lostpointercapture", endDrag);

  canvas.addEventListener("keydown", event => {
    const moves = {
      ArrowLeft: [-0.08, 0], ArrowRight: [0.08, 0],
      ArrowUp: [0, -0.08], ArrowDown: [0, 0.08]
    };
    const move = moves[event.key];
    if (!move) return;
    event.preventDefault();
    yaw += move[0];
    pitch = clamp(pitch + move[1], -1.2, 1.2);
    schedule();
  });

  root.querySelector("[data-reset]").addEventListener("click", () => {
    yaw = -0.65;
    pitch = 0.28;
    schedule();
  });

  new ResizeObserver(resize).observe(canvas);
  resize();
})();
</script>
```

使用 Froxel 渲染雾体分三个阶段，这三个阶段分别缓存：

| 阶段   | 缓存的数据                                   | 含义                  |
| ---- | --------------------------------------- | ------------------- |
| 介质注入 | $\sigma_a,\sigma_s$                     | 局部吸收和散射系数           |
| 光照注入 | $S_{\mathrm{rgb}},\sigma_t$             | 局部入散射源项和消光系数        |
| 视线积分 | $L_{\mathrm{acc,rgb}},T_{\mathrm{acc}}$ | 相机到当前深度的累计散射辐亮度和透射率 |
在实践中，介质注入和光照注入可以由一个 pass 负责，直接输出 $S_{rgb}, \sigma_t$ 到 Froxel 纹理 A，然后使用一个 pass 计算视线积分，输出到 Froxel 纹理 B. 两个纹理都是 3D 纹理。

通常在介质注入阶段我们直接采样一个 froxel 的中心位置的介质参数。

可是，这个精度也太低了！即使雾只是低频信息，这个精度也很难得到让人接受的效果。

<todo godot 中不开启时间重投影的效果>

[Bart Wronski，SIGGRAPH 2014](https://www.advances.realtimerendering.com/s2014/wronski/bwronski_volumetric_fog_siggraph2014.pdf) 提出了复用过去帧结果的方案。也就是时间重投影技术（Temporal Reprojection）。

简单来说，就是找上一帧的 Froxel 与这一帧 Froxel 重合的部分，对这部分采样，进行加权混合。

```html component
<title>时间重投影：两个相机视锥</title>

<style>
  .reprojection {
    color-scheme: dark;
    color: #eae7e7;
    background: #0d0d0d;
    border: 1px solid #303030;
    border-radius: 6px;
    overflow: hidden;
    font: 13px/1.6 system-ui, sans-serif;
  }
  .reprojection, .reprojection * { box-sizing: border-box; }
  .reprojection canvas {
    display: block;
    width: 100%;
    height: clamp(280px, 50vw, 420px);
    touch-action: none;
    cursor: grab;
  }
  .reprojection canvas:active { cursor: grabbing; }
  .reprojection :focus-visible {
    outline: 2px solid #93c5fd;
    outline-offset: -3px;
  }
  .reprojection .legend,
  .reprojection .controls,
  .reprojection .result { padding: 14px 16px; }
  .reprojection .legend {
    display: flex;
    flex-wrap: wrap;
    gap: 8px 20px;
  }
  .reprojection .current { color: #ff996c; }
  .reprojection .history { color: #76d8cf; }
  .reprojection .controls {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
    border-top: 1px solid #303030;
  }
  .reprojection label { display: grid; gap: 6px; min-width: 0; }
  .reprojection label span {
    display: flex;
    justify-content: space-between;
    gap: 8px;
  }
  .reprojection output { font-variant-numeric: tabular-nums; }
  .reprojection input { width: 100%; height: 36px; margin: 0; accent-color: #ce4623; }
  .reprojection .result {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 16px;
    background: #171717;
  }
  .reprojection .uv {
    position: relative;
    width: 144px;
    height: 108px;
    flex: 0 0 auto;
    border: 1px solid #76d8cf;
    background:
      linear-gradient(#76d8cf22 1px, transparent 1px) 0 0 / 12.5% 16.6667%,
      linear-gradient(90deg, #76d8cf22 1px, transparent 1px) 0 0 / 12.5% 16.6667%,
      #101a19;
  }
  .reprojection .marker {
    position: absolute;
    width: 9px;
    height: 9px;
    border-radius: 50%;
    background: #fff;
    box-shadow: 0 0 0 3px #76d8cf55;
    transform: translate(-50%, -50%);
  }
  .reprojection .info { flex: 1 1 180px; min-width: 0; }
  .reprojection p { margin: 4px 0; overflow-wrap: anywhere; }
  .reprojection .note { color: #b7b3b0; font-size: 12px; }
  .reprojection button {
    min-height: 40px;
    padding: 6px 12px;
    color: inherit;
    background: #212121;
    border: 1px solid #444;
    border-radius: 4px;
    font: inherit;
    cursor: pointer;
  }
  .reprojection [hidden] { display: none !important; }
</style>

<section class="reprojection" aria-label="时间重投影交互示意图">
  <div class="legend">
    <span class="current">橙色实线：当前视锥</span>
    <span class="history">青色虚线：历史视锥</span>
    <span>白点：世界空间采样点 P</span>
  </div>

  <canvas tabindex="0" role="img"
    aria-label="两个相机视锥及重投影路径，可拖拽或用方向键旋转观察">
    浏览器需要支持 Canvas。
  </canvas>

  <div class="controls">
    <label>
      <span>历史相机横移 <output data-out="offset"></output></span>
      <input data-key="offset" aria-label="历史相机横移"
        type="range" min="-8" max="8" step=".1" value="-3">
    </label>
    <label>
      <span>历史相机偏航 <output data-out="angle"></output></span>
      <input data-key="angle" aria-label="历史相机偏航"
        type="range" min="-40" max="40" step="1" value="12">
    </label>
    <label>
      <span>当前采样深度 <output data-out="depth"></output></span>
      <input data-key="depth" aria-label="当前采样深度"
        type="range" min="2" max="17" step=".1" value="11">
    </label>
    <label>
      <span>时间抖动帧 <output data-out="frame"></output></span>
      <input data-key="frame" aria-label="时间抖动帧"
        type="range" min="0" max="63" step="1" value="0">
    </label>
  </div>

  <div class="result">
    <div class="uv" role="img" aria-label="历史帧 UV，左上角为原点">
      <span class="marker"></span>
    </div>
    <div class="info">
      <p data-uv></p>
      <p data-state></p>
      <p class="note">左图为历史帧 UV：左上 (0,0)，右下 (1,1)。</p>
    </div>
    <button type="button" data-next>下一抖动帧</button>
    <button type="button" data-reset>重置观察视角</button>
    <p class="note">
      拖拽／方向键旋转。抖动按 8×6 粗网格示意。
      本图只演示位置重投影与视锥边界判定，不模拟时间融合、遮挡和深度一致性检测。
    </p>
  </div>
</section>

<script>
(() => {
  const root = document.currentScript.previousElementSibling;
  const canvas = root.querySelector("canvas");
  const ctx = canvas.getContext("2d");
  const fields = Object.fromEntries(
    [...root.querySelectorAll("[data-key]")].map(el => [el.dataset.key, el])
  );
  const marker = root.querySelector(".marker");
  const uvText = root.querySelector("[data-uv]");
  const stateText = root.querySelector("[data-state]");

  if (!ctx) {
    stateText.textContent = "当前浏览器无法创建 Canvas 2D 上下文。";
    return;
  }

  const near = 1, far = 18;
  const tanY = Math.tan(25 * Math.PI / 180);
  const tanX = tanY * 4 / 3;
  const orange = "#ff996c", teal = "#76d8cf";
  let width = 0, height = 0, scale = 1;
  let orbit = -0.65, tilt = 0.32;
  let drag = null, pending = 0;

  const clamp = (v, min, max) => Math.max(min, Math.min(max, v));

  function halton(index, base) {
    let result = 0, weight = 1;
    while (index > 0) {
      weight /= base;
      result += weight * (index % base);
      index = Math.floor(index / base);
    }
    return result;
  }

  // 相机局部坐标 → 世界坐标。所有相机均以 +Z 为前方。
  function toWorld([x, y, z], camera) {
    const c = Math.cos(camera.yaw), s = Math.sin(camera.yaw);
    return [camera.x + c * x + s * z, y, -s * x + c * z];
  }

  // 世界坐标 → 历史相机局部坐标。
  function toLocal([x, y, z], camera) {
    x -= camera.x;
    const c = Math.cos(camera.yaw), s = Math.sin(camera.yaw);
    return [c * x - s * z, y, s * x + c * z];
  }

  function sample(u, v, z) {
    return [(2 * u - 1) * z * tanX, (1 - 2 * v) * z * tanY, z];
  }

  // 示意图观察相机，与当前／历史渲染相机无关。
  function project([x, y, z]) {
    z -= far / 2;
    const rx = Math.cos(orbit) * x + Math.sin(orbit) * z;
    const rz = -Math.sin(orbit) * x + Math.cos(orbit) * z;
    const ry = Math.cos(tilt) * y - Math.sin(tilt) * rz;
    return [width / 2 + rx * scale, height / 2 - ry * scale];
  }

  function path(points, closed = false) {
    ctx.beginPath();
    points.forEach((p, i) => {
      const [x, y] = project(p);
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    if (closed) ctx.closePath();
  }

  function line(points, color, dashed = false, thickness = 1) {
    path(points);
    ctx.strokeStyle = color;
    ctx.lineWidth = thickness;
    ctx.setLineDash(dashed ? [5, 5] : []);
    ctx.stroke();
    ctx.setLineDash([]);
  }

  function dot(p, text, color, radius = 4) {
    const [x, y] = project(p);
    ctx.beginPath();
    ctx.arc(x, y, radius, 0, 2 * Math.PI);
    ctx.fillStyle = color;
    ctx.fill();
    ctx.font = "12px system-ui";
    ctx.fillText(text, x + 8, y - 8);
  }

  function frustum(camera, color, dashed) {
    const plane = z => [
      sample(0, 0, z), sample(1, 0, z),
      sample(1, 1, z), sample(0, 1, z)
    ].map(p => toWorld(p, camera));
    const a = plane(near), b = plane(far);

    path(b, true);
    ctx.fillStyle = dashed ? "#76d8cf08" : "#ff996c08";
    ctx.fill();

    line([...a, a[0]], color, dashed);
    line([...b, b[0]], color, dashed);
    for (let i = 0; i < 4; i++) line([a[i], b[i]], color, dashed);

    // 远平面粗网格，使投影方向更容易辨认。
    for (let i = 1; i < 8; i++) {
      line([
        toWorld(sample(i / 8, 0, far), camera),
        toWorld(sample(i / 8, 1, far), camera)
      ], color + "40", dashed);
    }
    for (let j = 1; j < 6; j++) {
      line([
        toWorld(sample(0, j / 6, far), camera),
        toWorld(sample(1, j / 6, far), camera)
      ], color + "40", dashed);
    }
  }

  function draw() {
    pending = 0;
    if (!width || !height) return;
    ctx.clearRect(0, 0, width, height);

    const values = Object.fromEntries(
      Object.entries(fields).map(([key, input]) => [key, Number(input.value)])
    );
    const { offset, angle, depth, frame } = values;
    const current = { x: 0, yaw: 0 };
    const history = { x: offset, yaw: angle * Math.PI / 180 };

    // 在同一个粗网格单元内改变采样位置。
    const u = (4 + halton(frame + 1, 2)) / 8;
    const v = (2 + halton(frame + 1, 3)) / 6;
    const P = sample(u, v, depth);

    // 重投影核心：世界位置 → 历史相机 → 透视除法 → UV。
    const [hx, hy, hz] = toLocal(P, history);
    const inFront = hz > 0.000001;
    const hu = inFront ? (hx / (hz * tanX) + 1) / 2 : null;
    const hv = inFront ? (1 - hy / (hz * tanY)) / 2 : null;
    const valid = inFront && hz >= near && hz <= far &&
      hu >= 0 && hu <= 1 && hv >= 0 && hv <= 1;

    // 保留统一缩放范围，参数变化不会触发自动缩放。
    scale = Math.min(width - 40, height - 40) / 64;

    frustum(history, teal, true);
    frustum(current, orange, false);

    line([[0, 0, 0], P], orange, false, 2);
    line([[history.x, 0, 0], P], teal, true, 2);

    // Q 是 P 在历史相机近裁剪平面上的投影。
    if (inFront) {
      const Q = toWorld([hx * near / hz, hy * near / hz, near], history);
      dot(Q, "Q", teal, 3);
    }

    dot([0, 0, 0], "当前相机", orange);
    dot([history.x, 0, 0], "历史相机", teal);
    dot(P, "P", "#ffffff", 5);

    marker.hidden = !valid;
    if (valid) {
      marker.style.left = `${hu * 100}%`;
      marker.style.top = `${hv * 100}%`;
    }

    uvText.textContent = inFront
      ? `历史 UV = (${hu.toFixed(3)}, ${hv.toFixed(3)})`
      : "历史 UV：不可用，P 位于历史相机后方";

    stateText.textContent = valid
      ? "✓ 位于历史视锥内，可进一步验证历史数据。"
      : "× 位于历史视锥外，应拒绝历史数据。";

    const labels = {
      offset: `${offset.toFixed(1)} m`,
      angle: `${angle}°`,
      depth: `${depth.toFixed(1)} m`,
      frame: String(frame)
    };
    for (const [key, text] of Object.entries(labels)) {
      root.querySelector(`[data-out="${key}"]`).textContent = text;
    }
  }

  function schedule() {
    if (!pending) pending = requestAnimationFrame(draw);
  }

  function resize() {
    const rect = canvas.getBoundingClientRect();
    width = rect.width;
    height = rect.height;
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = Math.round(width * dpr);
    canvas.height = Math.round(height * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    schedule();
  }

  Object.values(fields).forEach(input =>
    input.addEventListener("input", schedule)
  );

  root.querySelector("[data-next]").addEventListener("click", () => {
    fields.frame.value = (Number(fields.frame.value) + 1) % 64;
    schedule();
  });

  root.querySelector("[data-reset]").addEventListener("click", () => {
    orbit = -0.65;
    tilt = 0.32;
    schedule();
  });

  canvas.addEventListener("pointerdown", event => {
    if (!event.isPrimary || event.button !== 0) return;
    drag = { id: event.pointerId, x: event.clientX, y: event.clientY };
    canvas.setPointerCapture(event.pointerId);
  });

  canvas.addEventListener("pointermove", event => {
    if (!drag || event.pointerId !== drag.id) return;
    orbit += (event.clientX - drag.x) * 0.008;
    tilt = clamp(tilt + (event.clientY - drag.y) * 0.008, -1.2, 1.2);
    drag.x = event.clientX;
    drag.y = event.clientY;
    schedule();
  });

  function stop(event) {
    if (drag && drag.id === event.pointerId) drag = null;
  }
  ["pointerup", "pointercancel", "lostpointercapture"].forEach(type =>
    canvas.addEventListener(type, stop)
  );

  canvas.addEventListener("keydown", event => {
    const move = {
      ArrowLeft: [-0.08, 0], ArrowRight: [0.08, 0],
      ArrowUp: [0, -0.08], ArrowDown: [0, 0.08]
    }[event.key];
    if (!move) return;
    event.preventDefault();
    orbit += move[0];
    tilt = clamp(tilt + move[1], -1.2, 1.2);
    schedule();
  });

  new ResizeObserver(resize).observe(canvas);
  resize();
})();
</script>
```

我们可以在介质注入阶段，稍稍改变介质采样的位置，原来使用 Froxel 中心采样，现在可以每一帧有一些不同，均匀又随机地覆盖整个 Froxel 格子。这样即使相机不动，也可以复用不同位置的采样结果。这个行为叫做时间抖动（Temporal Jitter）。

时间重投影的 pass 发生在光照注入 pass 之后。