```toml
title = "Fast Water Simulation: Shallow Water Equations and Foam Rendering | Real-Time Water Rendering, Vol. 1"
summary = "This first post in the series uses first- and second-order shallow water solvers to simulate water in real time. It tracks two foam concentrations, then uses the flow to distort the textures that give fresh and residual foam their different looks."
ai-translation = true
```

I implemented the simulation in Rust with wgpu. The source is here:
https://github.com/gloridifice/wgpu-water-simulation

![Foam on the simulated water surface](images/with-flow-wraped.jpg)

# Introduction

There are two ways to describe a fluid. In the Eulerian description, we observe the fluid at fixed points in space, so we can store quantities like velocity on a grid. In the Lagrangian description, we follow small parcels of fluid. Each parcel is a particle, and their interactions determine how the fluid evolves. Modeling a whole body of water with particles can get expensive, so real-time water simulations often use the grid approach.

I won't go into the Euler equations in detail. What matters here is that they describe fluid motion without viscosity. We'll also treat water as incompressible, which means its three-dimensional velocity field has zero divergence:
$$\nabla\cdot\mathbf v=0$$
I'll skip the derivation of the shallow water equations from the Euler equations and focus on the formulas we'll actually use in code.

# I. Simulating water with the shallow water equations (SWE)

### 1. A first-order shallow water solver

The shallow water equations approximate the Euler equations. Instead of storing a fluid field throughout a 3D volume, we keep just the water depth and the depth-averaged horizontal velocity. Picture a 2D grid with a column of water in each cell. The amount of water and momentum in a cell changes according to what crosses its faces.

```html component
<title>First-order shallow water solver</title>
<section class="kc-root kc-panel">
  <figure class="kc-figure" style="margin:0;padding-block:12px">
    <svg viewBox="0 0 384 170" style="max-width:520px;margin-inline:auto"
         role="img" aria-label="First-order spatial reconstruction: the value is constant within each cell">
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
        <text x="72" y="159">Cell i−1</text>
        <text x="192" y="159">Cell i</text>
        <text x="312" y="159">Cell i+1</text>
      </g>
    </svg>
  </figure>
</section>
```

A cell needs only a few samples from the previous substep to compute its new state. That makes the solver a good fit for a GPU: all the cells can update in parallel.

Start in one dimension. The idea is simple: new depth = old depth + water in − water out. Each cell stores the depth $h$ and its product with horizontal velocity $u$, written $hu$. That product is the discharge per unit width. For cell $i$, the depth update is:
$$ h_i^{\,t_0+\Delta t} = h_i^{\,t_0} + \frac{\Delta t}{\Delta x} \left(q_{\text{left}}-q_{\text{right}}\right) $$
Here $q$ is the discharge per unit width through the indicated face, and $t_0$ and $t_0+\Delta t$ are the start and end times of the update.

Momentum updates much the same way:
$$ (hu)_i^{\,t_0+\Delta t} = (hu)_i^{\,t_0} + \frac{\Delta t}{\Delta x} \left(P_{\text{left}}-P_{\text{right}}\right) + \Delta t S_i$$
Here $P$ is the momentum flux through a face, and $S_i$ is the bed-slope source term. Where do $q$ and $P$ come from? The physical flux for the one-dimensional state $U=(h,hu)^\mathsf{T}$ is:
$$ F(U)=\begin{pmatrix}hu\\ hu^2+\tfrac12gh^2\end{pmatrix} $$
The $hu^2$ term comes from momentum carried by the flow; $\tfrac12gh^2$ comes from hydrostatic pressure. At a cell face we have two states, $U_L$ on the left and $U_R$ on the right. The Rusanov numerical flux uses both:
$$ \widehat F=\tfrac12\big[F(U_L)+F(U_R)\big]-\tfrac12 a(U_R-U_L) $$
For $a$, take the larger of the two values of $|u|+\sqrt{gh}$. The first component of $\widehat F$ gives $q$ and the second gives $P$ at that same face. My implementation also uses hydrostatic reconstruction and a pressure correction to handle uneven beds.

That's enough to update $h$ and $hu$ in the buffer. On a 2D grid, let $u$ and $v$ be the velocities along X and Z. Each cell now stores three values, $h$, $hu$, and $hv$, which we collect in a vector:
$$
U= \begin{bmatrix} h \\ hu\\ hv \end{bmatrix}
$$
We call $U$ the conserved state vector because its entries are the quantities we track for mass conservation (depth $h$) and momentum conservation ($hu$ and $hv$).

If you're a graphics programmer like me, the buffer layout is probably easier to remember than “conserved state vector.” I use an `array<vec4<f32>>`, with `h, hu, hv` in `x, y, z`. I haven't benchmarked an array against a texture. The SWE solver doesn't need bilinear filtering, so there's no particular reason to prefer a texture here.

One such update is a *substep*. Updating just once per frame can let a disturbance skip a cell if the frame interval is too long. We need to limit the substep duration based on grid spacing and propagation speed. That's what the CFL (Courant–Friedrichs–Lewy) condition is for. Rather than derive it, let's work out how many substeps to run per frame.

In one dimension, the Courant number tells us how many cell widths a disturbance travels in a substep: 0.1 means a tenth of a cell. Call our chosen limit `C`. If the maximum propagation speed is `a` and a cell is `dx` wide, the maximum substep duration is:

```
max_dt = C * dx / a;
```

A smaller `C` means more substeps per frame. When tuning, you can try values from 0.1 to 2.0 to see what the quality costs. That's a range to experiment with, not a guaranteed stability range: stability depends on the numerical scheme and the wave-speed estimate. For `a`, I add the maximum flow speed (12 m/s in my project) to the gravity wave speed $\sqrt{gh}$, using a depth bound estimated for the scene:
$$
 a=u_{max}+\sqrt{gh_{\text{bound}}}
$$
In two dimensions, both X and Z contribute. Here's how I choose the substep count `n` from the time to advance this frame, using `C = 0.2`:

```rust
let a = 12.0 + (9.81 * depth_bound).sqrt();
let max_dt = 0.2 / (a / dx + a / dz);

// Cap the simulated time per frame at 100 ms and at 128 stable substeps.
let elapsed = frame_interval.min(0.1).min(max_dt * 128.0);
let n = (elapsed / max_dt).ceil().max(1.0) as u32;
let dt = elapsed / n as f32;
```

![](<images/Pasted image 20261118123557.jpg>)
C = 0.2 at 60 FPS: 19 substeps per frame, about 2.36 ms.

![](<images/Pasted image 20261118123651.jpg>)
C = 1.1 at 60 FPS: 4 substeps per frame, about 0.43 ms.

In this scene, C = 1.1 looks much like C = 0.2 but costs a lot less. The detailed patch beside the smoother water is the FFT ocean; the smoother patch is the SWE region.

The first-order solver treats depth as constant within each cell. Numerical diffusion smooths away high-frequency detail, and the grid isn't fine enough to resolve the short waves coming in from the FFT ocean. Almost none of them survive, so the surface looks much calmer.

![](<images/Pasted image 20261118124929.jpg>)
The surface without short-wave normals.

Fortunately, we can put those short waves back into the shallow water region using surface normals.

### 2. Adding short waves with normals

We want a smooth transition between the open ocean and the shallow water. The long waves already feed into the SWE solver; we can add the short-wave detail at shading time.

At each point, we estimate the slope of the FFT height field twice, over a short span and a longer one. The long span approximates the contribution from long waves. The short span picks those up too, so we subtract the broad slope from the local one to isolate the short waves. We weight that difference, add it to the SWE surface slope, and reconstruct the normal.

```html component
<title>Computing short-wave normals</title>
<section class="kc-root kc-panel">
  <figure class="kc-figure" style="margin:0;padding-block:12px">
    <svg viewBox="0 0 400 290" style="max-width:560px;margin-inline:auto"
         role="img" aria-label="Computing a short-wave normal from FFT height samples">
      <desc>
        Sample heights one and four cells to either side along X and compute the two secant slopes.
        Repeat along Z. Subtract the broad slope from the local slope and use the weighted difference to adjust the original normal.
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
        <text x="36" y="27">FFT height H</text>
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
          <tspan> × total weight</tspan>
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

The slope along X is:
$$
g_x^{(s)} = A\frac{H(x+s\Delta,z)-H(x-s\Delta,z)} {2s\Delta}
$$
- $\Delta$ is the grid spacing.
- $H$ is the spatial height field after the inverse FFT.
- $A$ is the FFT height gain.
- $s$ is the sampling offset in cells, so each sample lies $s\Delta$ from the center.

Repeat along Z and combine the two components into a slope vector $\mathbf g$. Use $s=1$ for the local slope and $s=4$ for the broader slope. Their difference is:
$$\mathbf g_{\text{detail}} = \mathbf g^{(1)}-\mathbf g^{(4)}$$
The slope correction is:

$$ \delta\mathbf g = \mathbf g_{\text{detail}} \cdot \underbrace{S(1-W_{\text{FFT}})W_{\text{depth}}W_{\text{pixel}}}_{\text{total weight}} $$

| Weight | What it does |
| --- | --- |
| $S$ | The `Normal detail strength` control. Set it to 0 to turn the detail off. |
| $1-W_{\text{FFT}}$ | Adds nothing in the open ocean, where the FFT already supplies the detail. The correction grows as we move into the SWE region. |
| $W_{\text{depth}}$ | Off at depths of 2 cm or less, fully on at 25 cm or more, with a smooth transition in between. |
| $W_{\text{pixel}}$ | Fades detail when a screen pixel covers too many FFT cells, reducing shimmer in the distance. |

Given the original normal $\mathbf n_0$, the new normal is:
$$\mathbf n= \operatorname{normalize} \begin{pmatrix} n_{0x}-n_{0y}\delta g_x\\ n_{0y}\\ n_{0z}-n_{0y}\delta g_z \end{pmatrix} $$

Here's the result:

![](<images/Pasted image 20261118125017.jpg>)
The same surface with short-wave normals.

### 3. Handling colliders in the SWE solver

We treat colliders as raised terrain. For each cell, use the top surfaces of the colliders that cover it to raise the effective bed:

- `effective bed height = max(original terrain height, top heights of all colliders covering the cell)`

This changes the bed, not the water depth. It works well for static objects. With a moving object, a sudden rise in the bed displaces water, and the update also needs to redistribute it.

### 4. A second-order solver

There are two parts to the second-order solver: space and time.

In space, instead of a flat water surface in each cell, picture a plane with a limited slope. We compute the slope using MUSCL reconstruction. The heights at the faces change, but we can still use the same numerical flux formula. Here's what that looks like for one component of $U$.

First-order face states:
$$ U_L=U_i,\qquad U_R=U_{i+1} $$
Second-order face states:
$$
\begin{aligned}
\delta_i&=\operatorname{minmod}(U_i-U_{i-1},U_{i+1}-U_i) \\
U_L&=U_i+\tfrac12\delta_i,\qquad U_R=U_{i+1}-\tfrac12\delta_{i+1}
\end{aligned}
$$
The $\operatorname{minmod}$ slope limiter takes the argument with the smaller magnitude if both have the same sign. If their signs differ or either is zero, it returns zero. This keeps steep reconstructed slopes from introducing oscillations. In my project I reconstruct the surface elevation and horizontal velocity, and reconstruct the bed separately, using generalized minmod with a coefficient of 1.3.

```html component
<title>Second-order shallow water solver</title>
<section class="kc-root kc-panel">
  <figure class="kc-figure" style="margin:0;padding-block:12px">
    <svg viewBox="0 0 384 170" style="max-width:520px;margin-inline:auto"
         role="img" aria-label="Second-order spatial reconstruction: values vary linearly within each cell with a limited slope">
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
        <text x="72" y="159">Cell i−1</text>
        <text x="192" y="159">Cell i</text>
        <text x="312" y="159">Cell i+1</text>
      </g>
    </svg>
  </figure>
</section>
```

For second-order accuracy in time, we use SSP-RK2.

> RK2 stands for second-order Runge–Kutta. The Chinese transliteration sounds wonderfully overdramatic to me. I love it.

In the first-order solver, explicit Euler uses the rate of change at the start of the substep for the entire update. SSP-RK2 evaluates the rate at the start and again at a predicted endpoint. The average of those two rates, multiplied by the substep duration, gives the change in state.

Take water depth as an example. The straight line in the plot represents the *rate* $dh/dt$. The change in depth over the substep is the area of the trapezoid beneath it:

```html component
<title>RK2 time integration</title>
<section class="kc-root kc-panel">
  <figure class="kc-figure" style="margin:0;padding:12px 8px">
    <svg viewBox="0 0 560 336" style="max-width:720px;margin-inline:auto"
         role="img" aria-label="Integrating a linearly varying rate over one substep">
      <desc>
        The rate dh/dt rises linearly from a to a+bΔt. The gray rectangle has area aΔt;
        the red triangle has area ½bΔt².
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

At the start of an SWE substep, though, all we know is the initial state $U^n$. Where do we get the rate at the other end? Take an explicit Euler step to predict an endpoint $U^*$, then evaluate the rate there.

Write the rate as $k=L(U)$. Given the current water state, $L$ tells us how fast depth and momentum are changing:
$$
L(U)= \begin{bmatrix} \text{rate of change of depth}\\ \text{rate of change of X momentum}\\ \text{rate of change of Z momentum} \end{bmatrix}
$$
The initial rate is $k_1=L(U^n)$. The predicted state is $U^*=U^n+\Delta t\,k_1$, so the rate at the predicted endpoint is $k_2=L(U^*)$. The final conserved state is:
$$
U^{n+1}=U^n + \frac{k_1+k_2}{2}\Delta t
$$

# II. Shallow water foam

When an ocean wave breaks, it traps air in the water and foam forms on the surface.

Watch a wave run up the shore. The water at the front slows and starts to retreat while the water behind it is still moving forward. Foam travels with the water and bunches up where the currents meet. Bubbles rising from below keep replenishing the surface foam. How quickly the foam dies away depends on bubble size and how the foam is moving.

Simulating individual bubbles isn't practical here. Instead, we can track a foam concentration that moves with the SWE flow. Where the foam is dense, we shade it with a texture that looks nearly solid; where it's sparse, we use a netlike pattern with visible gaps. That gives us two quite different looks we can see in real water:

![](images/ocean_foam_patterns.jpg)

In the photo, a large wave breaks into many smaller waves. Both trap air. The large wave leaves a thick patch of foam; the smaller waves make little scalloped patches. Both contribute to the area marked in purple. The network of fine strands marked in green comes mostly from the smaller waves flowing together.

![](images/shoreline_foam_front.jpg)

There's often a distinct line of foam at the leading edge of a wave. As the water ahead slows, foam keeps arriving from behind. That's how the front can stay so dense.

Those observations give us four behaviors to capture in the concentration update:

| Behavior | Move with the water | Thicken where flow converges | Generate foam | Fade over time |
| --- | --- | --- | --- | --- |
| Implementation | Foam transport | Foam transport | Estimate from the SWE state | Exponential decay |

We use two scalar fields: fresh foam concentration $N$ (“New”) and residual foam concentration $R$ (“Rest”). They travel with the same flow but have different shading and decay parameters. Some of the fresh foam becomes residual foam as it decays.

In my implementation, the foam grid has twice the resolution of the SWE grid to capture finer detail. The buffer actually stores total concentration $F=N+R$ (“Full”) and residual concentration $R$. We recover $N$ as $F-R$.

### 1. Moving with the water and thickening where it converges

Multiply the velocity in the current cell by the substep duration to get a displacement, then trace backward by that displacement to see where the foam came from. This is semi-Lagrangian backtracing. The source position needn't fall at a cell center, so we bilinearly interpolate the four neighboring cells. For fresh foam, call the sampled concentration $N_{previous}$.

Where the flow converges, foam packs together. Where it spreads out, the foam thins. We can express both effects with the horizontal velocity divergence:
$$
d_{i,j} = \frac{u_{x+} - u_{x-}}{\Delta x} + \frac{u_{z+} - u_{z-}}{\Delta z}
$$
Here $u_{x+}$ and $u_{x-}$ are the X velocities at the right and left faces of the same cell, both measured in the positive X direction. The faces are $\Delta x$ apart. The same convention applies along Z. The transported concentration is:
$$
N^* = N_{previous} \cdot e^{-d_{i,j} \Delta t}
$$
So negative divergence increases $N^*$, just as we'd expect when foam piles up. We compute the transported residual concentration $R^*$ the same way.

### 2. Generating foam

Compression in the flow gives us an estimate of where foam might form. Consider two cells, A and B:

```
case1     case2
A B       A B
1 -2      5 2
- positive direction ->
```

In the first case the flows point toward each other. In the second they point the same way, but the water in A is moving much faster than the water ahead of it in B. Both cases can produce compression and hence foam. We estimate the compression rate as:
$$
\tau_{compress} = \frac{max(0, u_i - u_{i+1})}{d}
$$
Here $u_i$ is the velocity component along the flow at the current point, $u_{i+1}$ is the same component sampled ahead, and $d$ is the distance between samples. This is an empirical estimate.

For this simplified model, consider only foam generated by compression along the flow. First map the compression rate to a weight:
$$
t=clamp(\frac{\tau_{compress} - T}{W} ,0,1)
$$
The controls $T$ and $W$ set the threshold and transition width. The weight is zero up to $T$, rises between $T$ and $T+W$, then stays at one: high compression can still generate foam. We smooth the transition with smoothstep:
$$
s=t^2(3 - 2t)
$$
The resulting generation rate is:
$$
S = A_{param}\cdot s \cdot smoothstep(0.02, 0.1, |\vec u|)
$$
$A_{param}$ is a user-controlled multiplier. The last factor suppresses emission in slow water: there's none at or below 0.02 m/s, and no attenuation at or above 0.1 m/s. Ignoring decay, a substep adds concentration $S\Delta t$.

### 3. Letting foam decay

I'd like the user to set a half-life $H_N$ for fresh foam. The corresponding decay rate is $a = \frac{\ln 2}{H_N}$. With decay alone, we get:
$$
N_{new} = N^* e^{-a \Delta t}
$$
With generation as well, the net rate of change of fresh foam is $S-aN$.

### 4. Turning fresh foam into residual foam

We let a fraction $f$ of the decaying fresh foam become residual foam. The rest disappears. Conversion alone contributes the following rate to the residual concentration:
$$
\left.\frac{dR}{dt}\right|_{\text{conversion}}=faN
$$
Residual foam has its own half-life $H_R$ and decay rate $b$. Including decay, the net rates for the two concentrations are:
$$ \frac{dN}{dt}=S-aN,\qquad \frac{dR}{dt}=faN-bR $$
When integrating the equation for $R$, remember that $N$ isn't constant: fresh foam is changing over the substep too.

### The full update

Transport comes first, then generation, conversion, and decay. Start with the transported concentrations $N^*,R^*$ and hold $S,a,b,f$ constant over the substep. Integrating from 0 to $\Delta t$ gives:
$$
N_{new}=N^*e^{-a\Delta t}+\frac{S}{a}(1-e^{-a\Delta t})
$$
Integrating the residual foam equation gives:
$$
 \begin{aligned}R_{\text{new}} &=R^*e^{-b\Delta t} +faN^*K +fS\left[\frac{1-e^{-b\Delta t}}{b}-K\right]\\K&=\frac{e^{-b\Delta t}-e^{-a\Delta t}}{a-b} \end{aligned}
$$
Here $b=\frac{\ln 2}{H_R}$. If the half-lives match, then $a=b$ and the expression for $K$ divides by zero. Use its limit, $K=\Delta t\,e^{-b\Delta t}$. My implementation handles equal or nearly equal decay rates separately.

# III. Shading the foam

The solver gives us concentrations, but they have no upper bound. To shade fresh foam, first map its concentration to a coverage parameter in $[0,1)$:
$$
c_N = 1 - e^{-N/k}
$$
Here $k$ is a positive user control. At a given concentration, a larger $k$ means less coverage and fainter foam.

Think of fresh foam as a binary mask for a moment. To get irregular edges, compare a noise value with $c_N$. If the noise is below $c_N$, draw white foam (opacity $\alpha=1$); otherwise draw none ($\alpha=0$). Raising $c_N$ covers more of the surface in white. In the actual shader, I use `smoothstep` to soften the edges a little.

For residual foam, keep only a narrow band of values in the noise field. The band traces contours that look like a network of foam strands. A higher residual concentration $R$ makes the band wider.

```html component
<figure class="kc-compare">
  <img data-side="a" src="images/rest-only.jpg" alt="Residual foam only">
  <img data-side="b" src="images/with-flow-wraped.jpg" alt="Residual and fresh foam together">
  <figcaption>Left: residual foam only. Right: residual and fresh foam together.</figcaption>
</figure>
```

### Distorting the foam with the flow

The concentrations now move with the water, but the texture still doesn't stretch or twist with the current. We can track how far the flow carries it with a texture displacement field $\mathbf D$:
$$
\mathbf D_{new}(\mathbf x) = \mathbf D_{old}(\mathbf x-\mathbf u\Delta t) +\mathbf u\Delta t
$$
Start with $\mathbf D = \mathbf 0$, where $\mathbf x$ denotes position. We store *forward* displacement, so to draw foam at $\mathbf x$ we sample the texture back at $\mathbf x-\mathbf D(\mathbf x)$. Left to accumulate, that displacement would keep growing and the texture would stretch too far. We need a way to reset it.

Use two displacement layers, $\mathbf D_A$ and $\mathbf D_B$, each with reset period $T$. A layer resets to zero when its phase wraps. If A wraps during a substep, we still have to account for the time between the wrap and the end of the substep: $\mathbf D_A=\mathbf u\,(\phi_{new}T)$. Layer B is half a cycle out of phase with A, so when B wraps, its end-of-substep displacement is $\mathbf D_B=\mathbf u\left[\operatorname{fract}(\phi_{\text{new}}+0.5)T\right]$. The phase update is:
$$\phi_{new}=\operatorname{fract}(\phi+\frac{\Delta t}{T})$$
Here `fract` takes the fractional part. We feed the phase into a smoothed triangle wave to crossfade between the two layers:

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
  <title id="blend-title">Blend weights for the two foam displacement layers</title>
  <desc id="blend-desc">The weights for A and B sum to one. A resets at the start of the cycle and B halfway through. Each layer has zero weight at its reset.</desc>
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
  <!-- One cubic Bézier per half-cycle gives smoothstep exactly, without scripted sampling. -->
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

> Smoothed triangle wave:
> $$s=1-|2\phi_{\text{new}}-1|,\qquad w_A=s^2(3-2s),\qquad w_B=1-w_A$$
> The two weights always add up to one.

When A resets, its weight is zero; when B resets, its weight is zero. That hides the reset instead of letting the texture jump.

![](images/flow_distorted_foam.jpg)
Figure d: fresh and residual foam combined with flow distortion.

```html component
<figure class="kc-compare">
  <img data-side="a" src="images/without-flow-wrap.jpg" alt="Foam without flow distortion">
  <img data-side="b" src="images/with-flow-wraped.jpg" alt="Foam with flow distortion">
  <figcaption>Left: without flow distortion. Right: with flow distortion.</figcaption>
</figure>
```

### Foam textures

Fresh foam doesn't have to use procedural noise. A grayscale texture works too: use the texel value as a threshold and compare the fresh foam coverage against it to decide whether the pixel shows foam. Here's the difference between two textures:

```html component
<figure class="kc-compare">
  <img data-side="a" src="images/fresh-crest-2.jpg" alt="Crest texture">
  <img data-side="b" src="images/fresh-shorewave.jpg" alt="Shortwave texture">
  <figcaption>Left: crest texture. Right: shortwave texture.</figcaption>
</figure>
```

# Closing notes

We finally have a usable shallow water simulation, and it runs pretty fast. Buoyancy, waterfalls, and long stretches of coastline are still on my to-do list.

Here are the GPU timings for two scenes on an RTX 3070 at C = 1.1:

| Scene | SWE simulation (ms) | SWE grid | Substeps |
| --- | ---: | --- | ---: |
| Reef, second order | 0.44 | 256 × 256 | 4 |
| Reef, first order | 0.28 | 256 × 256 | 4 |
| Coast, second order | 0.19 | 256 × 256 | 2 |
| Coast, first order | 0.10 | 256 × 256 | 2 |

One more image:

![Coastal scene](images/ocean-1795156839104306900.jpg)
The coastal scene.
