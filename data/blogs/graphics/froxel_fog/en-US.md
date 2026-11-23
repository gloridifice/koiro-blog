```toml
title = "Fast Volumetric Rendering with Froxels"
summary = "How froxels, temporal reprojection, and jitter make volumetric fog practical in real time, with a walkthrough of Godot's implementation."
ai-translation = true
```

Fog and water are two of the most common uses for volumetric rendering in game engines. This post looks at a tried-and-tested way to render volumetric fog: a froxel grid, temporal reprojection, and temporal jitter.

As light travels through a medium, some of it is absorbed and some is scattered. Light scattered toward the camera contributes to the image. Light scattered out of the viewing direction is lost from that ray, and absorbed light is lost altogether.

Light can pass through fog, hit an object behind it, and reflect back according to the object's BRDF. It then travels through the fog again, where more light is absorbed or scattered. The loss of light along a ray is called *extinction*. In real-time rendering, we often account for this loss by multiplying the incoming radiance by a transmittance factor. We write the absorption coefficient as $\sigma_a$, the scattering coefficient as $\sigma_s$, and the total extinction coefficient as $\sigma_t=\sigma_a + \sigma_s$.

Let $s$ be the distance along the ray. The differential equation for light traveling through the medium is:

$$
\frac{dL}{ds}=-\sigma_tL+S
$$

Here $L$ is radiance, and $S$ is the contribution from light scattered into the ray. That source term is hard to evaluate exactly. For real-time rendering, we can treat it as constant over the segment. Integrating gives:

$$ L(d)=L_0e^{-\sigma_t d} +\frac{S}{\sigma_t}(1-e^{-\sigma_t d}) $$

For fixed coefficients and source term, the result depends only on the distance through the medium. If we know how far a ray from a light source travels through fog before reaching an opaque fragment, we can work out its radiance at that fragment.

# Froxels

Rendering fog in real time runs into two problems: sampling gets expensive, and even then it's easy to miss detail.

For every pixel, we have to work out how far the light from each source travels through the fog. Take our height field with bilinear interpolation and ray marching: each interpolation needs four samples, so 32 steps cost 128 samples! Even then, the march can skip thin patches and leave gaps in the fog. Bilinear interpolation only gives a piecewise bilinear reconstruction; better reconstruction costs more.

A graphics classic: if you want it cheap and still looking decent, reach for something that's more than a decade old.

Froxel-based volumetric fog first shipped in 2013:

| Year | Work | Contribution |
| --- | --- | --- |
| 2013 | Ubisoft, *Assassin's Creed IV: Black Flag* | Shipped real-time volumetric fog based on a 3D volume texture. |
| 2014 | Bart Wronski, SIGGRAPH 2014 | Described a frustum-aligned 3D cache, light injection, and volumetric integration. |
| 2015 | Sébastien Hillaire, SIGGRAPH 2015, Frostbite | Used the term *froxel* (frustum voxel) and developed a more physically consistent, unified volumetric rendering framework. |

The usual fast approach is to divide the camera frustum into a 3D grid and store it in a 3D texture. The depth slices are often spaced logarithmically, so the grid has finer resolution near the camera.

```html component
<title>The froxel grid</title>

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
  .froxel label > span {
    display: flex;
    justify-content: space-between;
    gap: 8px;
  }
  .froxel output { color: #ffad83; font-variant-numeric: tabular-nums; }
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

<section class="froxel" aria-label="Interactive froxel grid">
  <canvas
    tabindex="0"
    role="img"
    aria-label="Frustum grid. Drag or use the arrow keys to rotate. Use the sliders below to select a cell."
  >This diagram requires Canvas support.</canvas>

  <p class="hint">Drag or use the arrow keys to rotate · Gray: grid · Orange: selected cell · Dashed line: view ray</p>

  <div class="controls">
    <label>
      Grid resolution
      <select data-grid>
        <option value="4,3,8">4 × 3 × 8</option>
        <option value="8,6,16" selected>8 × 6 × 16</option>
        <option value="12,9,24">12 × 9 × 24</option>
      </select>
    </label>

    <label>
      Depth distribution
      <select data-mode>
        <option value="log">Logarithmic slices</option>
        <option value="linear">Linear slices</option>
      </select>
    </label>

    <label>
      <span>Depth slice k <output data-value="k"></output></span>
      <input class="kc-range" data-axis="k" aria-label="Depth slice k" type="range"
             min="0" max="15" value="7" step="1">
    </label>

    <label>
      <span>Horizontal index i <output data-value="i"></output></span>
      <input class="kc-range" data-axis="i" aria-label="Horizontal index i" type="range"
             min="0" max="7" value="4" step="1">
    </label>

    <label>
      <span>Vertical index j <output data-value="j"></output></span>
      <input class="kc-range" data-axis="j" aria-label="Vertical index j" type="range"
             min="0" max="5" value="3" step="1">
    </label>
  </div>

  <div class="footer">
    <p class="readout"></p>
    <button type="button" data-reset>Reset view</button>
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
    readout.textContent = "This browser could not create a 2D canvas context.";
    return;
  }

  // Camera space: +Z points forward. Depth is measured along the view axis, not along the ray.
  const near = 1, far = 16;
  const tanHalfFov = Math.tan(Math.PI / 6); // vertical FOV = 60°
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

  // Map grid boundaries (u, v, z) to camera space.
  function point(u, v, z) {
    return [
      (2 * u - 1) * z * tanHalfFov * aspect,
      (1 - 2 * v) * z * tanHalfFov,
      z
    ];
  }

  // Draw the perspective frustum from an orthographic viewpoint.
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

    // Use a fixed bounding sphere so rotating the view does not change the scale.
    const radius = Math.hypot(
      far * tanHalfFov * aspect,
      far * tanHalfFov,
      far / 2
    );
    scale = Math.min(width - 48, height - 48) / (2 * radius);

    // Outline each depth slice.
    for (let s = 0; s <= nz; s++) {
      const corners = plane(depth(s));
      line([...corners, corners[0]], "#515151", 0.8);
    }

    // Draw grid lines along rays at fixed screen positions.
    for (let x = 0; x <= nx; x++) {
      for (let y = 0; y <= ny; y++) {
        line([
          point(x / nx, y / ny, near),
          point(x / nx, y / ny, far)
        ], "#414141", 0.7);
      }
    }

    // Draw the front and back of the selected depth slice.
    quad(plane(z0), "rgba(206,70,35,.07)", "#a75b43");
    quad(plane(z1), "rgba(206,70,35,.07)", "#a75b43");

    // The selected cell is a frustum bounded by two quad cross-sections, not a cube.
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

    // Draw translucent faces back to front in view space.
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

    // Draw the view ray through the center of the selected cell’s screen-space tile.
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
    ctx.fillText("Camera", cx + 9, cy - 8);

    for (const axis of ["i", "j", "k"]) {
      root.querySelector(`[data-value="${axis}"]`).textContent =
        inputs[axis].value;
    }

    readout.textContent =
      `Cell (${i}, ${j}, ${k}) · z = ${z0.toFixed(2)}–${z1.toFixed(2)} m` +
      ` · ${(nx * ny * nz).toLocaleString()} cells total`;
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
      input.dispatchEvent(new Event("input", { bubbles: true }));
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

Froxel fog has three stages, each of which caches different data:

| Stage | Cached data | Meaning |
| --- | --- | --- |
| Media injection | $\sigma_a,\sigma_s$ | Local absorption and scattering coefficients |
| Light injection | $S_{\mathrm{rgb}},\sigma_t$ | Local in-scattering source term and extinction coefficient |
| View-ray integration | $L_{\mathrm{acc,rgb}},T_{\mathrm{acc}}$ | Accumulated scattered radiance and transmittance from the camera to this depth |

In practice, we can combine media and light injection into one pass that writes $S_{\mathrm{rgb}}$ and $\sigma_t$ to froxel texture A. A second pass integrates along the view rays and writes the results to froxel texture B. Both are 3D textures.

During media injection, the obvious place to sample the medium is the center of each froxel.

But one sample per cell is too coarse. Even fog, which is mostly low-frequency, doesn't look good at that resolution.

<!-- TODO: Add an example from Godot with temporal reprojection disabled. -->

# Temporal reprojection and jitter

At [SIGGRAPH 2014, Bart Wronski](https://www.advances.realtimerendering.com/s2014/wronski/bwronski_volumetric_fog_siggraph2014.pdf) described a way to reuse results from earlier frames: temporal reprojection.

The idea is to map each current froxel sample into the previous frame's grid. Where the grids overlap, we fetch the previous value with trilinear sampling and blend it with the current value.

```html component
<title>Temporal reprojection: two camera frusta</title>

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
  .reprojection label > span {
    display: flex;
    justify-content: space-between;
    gap: 8px;
  }
  .reprojection output { font-variant-numeric: tabular-nums; }
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

<section class="reprojection" aria-label="Interactive temporal reprojection diagram">
  <div class="legend">
    <span class="current">Solid orange: current frustum</span>
    <span class="history">Dashed teal: previous frustum</span>
    <span>White dot: world-space sample P</span>
  </div>

  <canvas tabindex="0" role="img"
    aria-label="Two camera frusta and the reprojection path. Drag or use the arrow keys to rotate.">
    This diagram requires Canvas support.
  </canvas>

  <div class="controls">
    <label>
      <span>Previous camera offset <output data-out="offset"></output></span>
      <input class="kc-range" data-key="offset" aria-label="Previous camera offset"
        type="range" min="-8" max="8" step=".1" value="-3">
    </label>
    <label>
      <span>Previous camera yaw <output data-out="angle"></output></span>
      <input class="kc-range" data-key="angle" aria-label="Previous camera yaw"
        type="range" min="-40" max="40" step="1" value="12">
    </label>
    <label>
      <span>Current sample depth <output data-out="depth"></output></span>
      <input class="kc-range" data-key="depth" aria-label="Current sample depth"
        type="range" min="2" max="17" step=".1" value="11">
    </label>
    <label>
      <span>Jitter frame <output data-out="frame"></output></span>
      <input class="kc-range" data-key="frame" aria-label="Jitter frame"
        type="range" min="0" max="63" step="1" value="0">
    </label>
  </div>

  <div class="result">
    <div class="uv" role="img" aria-label="Previous-frame UV coordinates, with the origin at the top left">
      <span class="marker"></span>
    </div>
    <div class="info">
      <p data-uv></p>
      <p data-state></p>
      <p class="note">The panel on the left shows the previous frame’s UV coordinates: (0, 0) at the top left and (1, 1) at the bottom right.</p>
    </div>
    <button type="button" data-next>Next jitter frame</button>
    <button type="button" data-reset>Reset view</button>
    <p class="note">
      Drag or use the arrow keys to rotate. The demo uses a coarse 8×6 grid for jitter.
      It reprojects the sample and checks whether it falls inside the previous frustum. It does not blend frames or check occlusion or depth consistency.
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
    stateText.textContent = "This browser could not create a 2D canvas context.";
    return;
  }

  const near = 1, far = 18;
  const tanY = Math.tan(25 * Math.PI / 180);
  const tanX = tanY * 4 / 3;
  const orange = "#ff996c", teal = "#76d8cf";
  let width = 0, height = 0, scale = 1;
  let center = [0, 0];
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

  // Transform from camera space to world space. +Z points forward for both cameras.
  function toWorld([x, y, z], camera) {
    const c = Math.cos(camera.yaw), s = Math.sin(camera.yaw);
    return [camera.x + c * x + s * z, y, -s * x + c * z];
  }

  // Transform from world space to the previous camera’s space.
  function toLocal([x, y, z], camera) {
    x -= camera.x;
    const c = Math.cos(camera.yaw), s = Math.sin(camera.yaw);
    return [c * x - s * z, y, s * x + c * z];
  }

  function sample(u, v, z) {
    return [(2 * u - 1) * z * tanX, (1 - 2 * v) * z * tanY, z];
  }

  // The diagram viewpoint is independent of both rendering cameras.
  function view([x, y, z]) {
    z -= far / 2;
    const rx = Math.cos(orbit) * x + Math.sin(orbit) * z;
    const rz = -Math.sin(orbit) * x + Math.cos(orbit) * z;
    return [rx, Math.cos(tilt) * y - Math.sin(tilt) * rz];
  }

  function project(p) {
    const [x, y] = view(p);
    return [width / 2 + (x - center[0]) * scale,
            height / 2 - (y - center[1]) * scale];
  }

  function fitView(cameras) {
    const points = cameras.flatMap(camera => [
      toWorld([0, 0, 0], camera),
      ...[near, far].flatMap(z => [
        sample(0, 0, z), sample(1, 0, z),
        sample(1, 1, z), sample(0, 1, z)
      ].map(p => toWorld(p, camera)))
    ]).map(view);
    const xs = points.map(p => p[0]), ys = points.map(p => p[1]);
    const left = Math.min(...xs), right = Math.max(...xs);
    const bottom = Math.min(...ys), top = Math.max(...ys);
    center = [(left + right) / 2, (bottom + top) / 2];
    // Fit the projected bounds to the canvas, leaving room for markers and labels.
    scale = Math.min(Math.max(1, width - 128) / Math.max(1, right - left),
                     Math.max(1, height - 64) / Math.max(1, top - bottom));
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

    // Draw a coarse grid on the far plane to show the projection direction.
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

    // Move the sample within the same coarse cell.
    const u = (4 + halton(frame + 1, 2)) / 8;
    const v = (2 + halton(frame + 1, 3)) / 6;
    const P = sample(u, v, depth);

    // Reproject the world-space point into the previous camera and use the perspective divide to get UVs.
    const [hx, hy, hz] = toLocal(P, history);
    const inFront = hz > 0.000001;
    const hu = inFront ? (hx / (hz * tanX) + 1) / 2 : null;
    const hv = inFront ? (1 - hy / (hz * tanY)) / 2 : null;
    const valid = inFront && hz >= near && hz <= far &&
      hu >= 0 && hu <= 1 && hv >= 0 && hv <= 1;

    fitView([current, history]);

    frustum(history, teal, true);
    frustum(current, orange, false);

    line([[0, 0, 0], P], orange, false, 2);
    line([[history.x, 0, 0], P], teal, true, 2);

    // Q is P projected onto the previous camera’s near plane.
    if (inFront) {
      const Q = toWorld([hx * near / hz, hy * near / hz, near], history);
      dot(Q, "Q", teal, 3);
    }

    dot([0, 0, 0], "Current camera", orange);
    dot([history.x, 0, 0], "Previous camera", teal);
    dot(P, "P", "#ffffff", 5);

    marker.hidden = !valid;
    if (valid) {
      marker.style.left = `${hu * 100}%`;
      marker.style.top = `${hv * 100}%`;
    }

    uvText.textContent = inFront
      ? `Previous UV = (${hu.toFixed(3)}, ${hv.toFixed(3)})`
      : "Previous UV unavailable: P is behind the previous camera.";

    stateText.textContent = valid
      ? "✓ Inside the previous frustum. History can be checked further."
      : "× Outside the previous frustum. Reject history.";

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
    fields.frame.dispatchEvent(new Event("input", { bubbles: true }));
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

During media injection, we can nudge the sample position a little. Instead of always sampling the center of the froxel, we move the point from frame to frame so the samples spread through the whole cell. Even if the camera stays still, we'll get samples at different positions. That's temporal jitter.

> Temporal reprojection and jitter are common ways to resample over time. Fog isn't the only place you'll see them: TAA, SSR, and SSGI can also reuse samples from previous frames.

Temporal reprojection runs after light injection.

For temporal accumulation, a simple exponential moving average is usually enough. With a history weight of $\alpha$:

$$V_t = (1-\alpha) V_{new} + \alpha V_{t-1}$$

For volumetric fog, we accumulate $S$ and $\sigma_t$ in the latest froxel buffer. In practice, $\alpha$ can be set to 0.9.

How far behind is the accumulated result? We can measure the lag with a step response. Suppose the input jumps from 0 to 1 in one frame. After $n$ frames, the accumulated value is $V_n = 1 -\alpha^n$.

It still differs from 1 by $\alpha^n$. To find how many frames it takes to reach a fraction $q$ of the new value:

$$
1-\alpha^n=q,\qquad n= \frac{\ln(1-q)}{\ln \alpha}
$$

At 60 FPS:

| History weight $\alpha$ | Time constant (63.2%) | Time to 90% | Mean sample age |
| --- | ---: | ---: | ---: |
| 0.80 | 74.7 ms | 172 ms | 66.7 ms |
| 0.90 | 158 ms | 364 ms | 150 ms |
| 0.95 | 325 ms | 748 ms | 317 ms |

Even with a history weight of 0.95, it takes less than a second to reach 90%. Fog with more high-frequency detail generally needs a higher history weight to suppress noise. The trade-off shows up when the camera moves quickly: more history means more visible ghosting.

A fixed $\alpha$ depends on the frame rate. That isn't necessarily a problem for fog, but if you want similar behavior at different frame rates, set the weight from a time constant:

$$
\alpha=e^{-\Delta t/\tau}
$$

Substitute $n=t/\Delta t$ into the step response. The time to reach $q$ no longer depends on the length of a frame:

$$t=-\tau\ln(1-q)$$

$\tau$ is the time constant you tune. A larger $\tau$ gives more weight to history and slows convergence.

Godot 4 doesn't use this time-constant approach for its volumetric fog. Here's what it does instead.

# How Godot renders volumetric fog

Godot's Forward+ renderer supports volumetric fog natively. The pipeline has six stages:

1. Fog volume injection
2. Light injection
3. Copy to history
4. Filtering
5. Integration
6. Shading

Godot uses `density` directly as the extinction coefficient $\sigma_t$. The `albedo` controls how much light is scattered: `scattering = albedo * clamp(density, 0, 1)`. The fog volume injection stage samples each volume and injects $\sigma_t$, $\sigma_s$, and $S_{emit}$ into three `R32_UINT` 3D textures.

| C++ resource | GPU format | Froxel data | Symbol |
| --- | --- | --- | --- |
| `density_map` | `R32_UINT`, 4 B | Atomic accumulator for local density. It encodes density as `int(density × 1024)` and reads the result as signed, so negative local density can cancel out global fog. | $\Delta\sigma_t$ |
| `light_map` | `R32_UINT`, 4 B | Local **scattering coefficient**, not computed lighting. It packs the density-weighted albedo into R11/G11/B10 integer fields. | $\sigma_{s,\mathrm{rgb}}^{\mathrm{local}}$ |
| `emissive_map` | `R32_UINT`, 4 B | Density-weighted local emission. It also uses 11/11/10-bit packing, but with a different quantization range. | $S_{\mathrm{emit,rgb}}^{\mathrm{local}}$ |

Light injection loops over the light sources and writes an `RGBA16F` texture. RGB holds the scattering source term, including emission, and alpha holds the density used for extinction. If temporal resampling is enabled, this stage also blends in history.

| C++ resource | GPU format | Froxel data | Symbol |
| --- | --- | --- | --- |
| `light_density_map` | `RGBA16F` 3D, 8 B | Current-frame data before integration: RGB is the scattering source term plus emission, and alpha is the density used for extinction. | $\mathrm{RGB}=S_{\mathrm{rgb}},\quad \mathrm{A}=\sigma_t$ |

When temporal resampling is on, Godot copies the current `light_density_map` into `prev_light_density_map` for the next frame.

Next comes a Gaussian blur along X and Y only, in two separate passes. This smooths the grid data. The copy to history happens before the blur, so the filtered values don't feed back into temporal accumulation.

| Pass | Reads | Writes | Output |
| --- | --- | --- | --- |
| X blur | `light_density_map` | `fog_map` | $(S_{\mathrm{rgb}},\sigma_t)$ filtered along X |
| Y blur | `fog_map` | `light_density_map` | $(S_{\mathrm{rgb}},\sigma_t)$ filtered along both axes |

After filtering, Godot integrates along Z, starting at the camera and working through the slices from near to far. Each slice stores the running result in `fog_map`.

| C++ resource | GPU format | Froxel data | Symbol |
| --- | --- | --- | --- |
| `fog_map` | `RGBA16F` 3D, 8 B | Temporarily holds the X blur. After integration, RGB is the accumulated fog radiance from the camera to this depth, and **alpha is transmittance, not opacity**. | $\mathrm{RGB}=L_{\mathrm{acc,rgb}},\quad \mathrm{A}=T_{\mathrm{acc}}$ |

Here's the integration loop:

```c
ivec2 xy = ivec2(gl_GlobalInvocationID.xy);
vec3 L_acc = vec3(0.0);  // No accumulated fog light at the camera
float T_acc = 1.0;       // No attenuation at the camera
float prev_z = 0.0;

for (int k = 0; k < params.fog_volume_size.z; ++k) {
    ivec3 pos = ivec3(xy, k);
    vec4 V = imageLoad(density_map, pos);
    vec3 S = V.rgb;
    float sigma_t = V.a;

    float z = get_depth_at_pos(fog_cell_size.z, k);
    float d = abs(z - prev_z);
    prev_z = z;

    float T_seg = exp(-sigma_t * d);
    vec3 L_seg = S * (1.0 - T_seg) / max(sigma_t, 0.00001);

    L_acc += T_acc * L_seg;  // Light from this segment after passing through the nearer segments
    T_acc *= T_seg;          // Fold this segment into the accumulated transmittance

    imageStore(fog_map, pos, vec4(L_acc, T_acc));
}
```

In the shading stage, screen UVs and fragment depth give us a 3D texture coordinate. We sample `fog_map` with trilinear filtering to get $T$ and $L$, then composite the result:

```
final_color = T * color + L;
```

# A note on rendering water

I also used volumetric rendering in my own water project. Here's how the froxel approach looked and performed. Timings are in milliseconds on an RTX 3070 at 1280×800.

| Lighting | Froxel injection + temporal reprojection | Water surface shading | Whole scene: froxels on / off |
| --- | ---: | ---: | ---: |
| Skylight only | 0.047 | 3.492 | **4.107 / 4.342** |
| Skylight + 2 original lights | 0.047 | 3.570 | **4.187 / 4.400** |
| Skylight + 8 lights of the same size | 0.047 | 3.758 | **4.380 / 4.571** |

![Water rendering with froxels on and off](images/comparison-horizontal.jpg)
