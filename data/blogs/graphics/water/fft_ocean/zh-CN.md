```toml
title = "FFT 快速海洋 todo"
ai-translation = false
```

使用方向和幅度定义一个波，我们用一个波向量 k 来描述波。

Philips 频谱可以用于计算某个波 k 的幅度：
$$
P(k)=A\frac{e^{\frac{-1}{(kL)^2}}}{k^4}|\hat k\cdot \hat \omega|^2
$$
其中 $\omega$ 是风向向量 $\hat \omega$ 表示其单位向量。$L = \frac{U^2}{g}$ 是风速和重力觉得的长度尺度。

```rust
fn main() {
	let img_width = 128; let img_height = 128;
	let sea_width = 1000; let sea_height = 1000;
	for i in 0..width {
		for j in 0..height {
			n = i - img_width / 2;
			m = j - img_height / 2;
			k = Vec2::new(n / sea_width, m / sea_height) * 2 * PI;
		}
	}
}


fn p(k: Vec2, w: Vec2, ampli: f32) {
	let len = p.dot(w).normalize().pow2();
	let k2 = k.dot(k);
	return = -1/exp(k2 * L) / (k2 * k2) * len * len;	
}
```

一个完整的波高度公式为：
$$ 
h_{\mathbf k}(x,z,t) = a(\mathbf k) \cos\left[ k_xx+k_zz+\omega(\mathbf k)t+\phi(\mathbf k) \right] 
$$
使用欧拉公式 $\cos\theta=\frac{e^{i\theta}+e^{-i\theta}}{2}$ 将 cos 展开后为：

$$
 \begin{aligned} h_{\mathbf k}(x,z,t) ={}& \underbrace{\frac{a(\mathbf k)}{2}e^{i\phi(\mathbf k)}}_{\text{固定系数 }h_0(\mathbf k)} \underbrace{e^{i\omega(\mathbf k)t}}_{\text{时间项}} \underbrace{e^{i(k_xx+k_zz)}}_{\text{空间项}} \\ &+ \underbrace{\frac{a(\mathbf k)}{2}e^{-i\phi(\mathbf k)}}_{\text{共轭系数 }h_0(\mathbf k)^*} \underbrace{e^{-i\omega(\mathbf k)t}}_{\text{时间项}} \underbrace{e^{-i(k_xx+k_zz)}}_{\text{空间项}} \end{aligned} 
$$
水面的位置就是所有波相加，及逆傅里叶变换：
$$ h(x,z,t)=\sum_{\mathbf k}h_{\mathbf k}(x,z,t) $$
在这个复杂函数中，有部分是纯粹的关于 k 的函数，我们在开始时即可算出并存在数组里：
$$ h_0(\mathbf k) = \frac{a(\mathbf k)}{2}e^{i\phi(\mathbf k)} $$
这样，我们有了个一个 h_0 数组。
我们将这个系数结合时间项共同写成一个频谱系数 $H(\mathbf k, t)$
$$
H(\mathbf k,t) = h_0(\mathbf k)e^{i\omega(\mathbf k)t} + h_0(-\mathbf k)^*e^{-i\omega(\mathbf k)t}
$$
最终我们可以将原来的计算 h 的公式写成傅里叶形式：
$$ h(x,z,t) = \sum_{\mathbf k} H(\mathbf k,t)e^{i(k_xx+k_zz)} $$
可以发现这个式子简化了之前的共轭项，因为其隐含在了 k 的范围里，k 的范围是 -n 到 n，其中带入 k = -n 得到的就是 k = n 的共轭项。

这里每一帧我们可以根据时间计算 $H(k, t)$，然后根据对其做快速逆傅里叶变换 IFFT，就可以得到水面高度了。

## 实现

我们用一个 RGBA 四通道纹理存储 h0：

| 通道  | 存储内容                                                                       |
| --- | -------------------------------------------------------------------------- |
| R   | $\operatorname{Re}[h_0(\mathbf k)]$                                        |
| G   | $\operatorname{Im}[h_0(\mathbf k)]$                                        |
| B   | $\operatorname{Re}[h_0(-\mathbf k)^*]=\operatorname{Re}[h_0(-\mathbf k)]$  |
| A   | $\operatorname{Im}[h_0(-\mathbf k)^*]=-\operatorname{Im}[h_0(-\mathbf k)]$ |

![](images/h0_conjugate_spectrum.jpg)
图来自 https://barthpaleologue.github.io/Blog/posts/ocean-simulation-webgpu/

在实际实现中，我们根本不需要关心 h0 的公式，只要用随机数生成它就好了。这必然让人感到困惑，我会在最后进行解释。
$$h_0(\mathbf k) = \sqrt{\frac{P(\mathbf k)}2} \left(\xi_r(\mathbf k)+i\xi_i(\mathbf k)\right)$$
h0 本质上是一个复数，我们用两个正态分布的随机数来决定其实部和虚部，然后乘上 Philips 频谱。其中 $\xi_r$ 和  $\xi_i$ 是两个正太分布的随机数，分别决定实部和虚部。除以根号 2 是为了归一化这个复数。$\sqrt{P(k)}$ 的根号是因为 Philips 频谱描述的是幅度平方的变化。

> 归一化为了便于存储和计算，无特殊用途

### 1. 为什么可以直接用正态分布随机数生成实部和虚部来决定振幅和相位？

这里不给出严格的数学证明，只给一个直觉化的解释。首先，一个复数可以写成：
$$ h_0(\mathbf k)=X+iY=re^{i\phi} $$
如果 X 和 Y 是服从正态分布的，可以想象到，其会形成一个以圆点为中心，由内向外概率密度逐渐降低的分布形式。这种形式下，相位 $\phi$ 在 $0, 2\pi$ 是均匀分布的，密度只取决于距离圆点的距离 r，即模长，经计算，r 服从瑞利分布。

也就是说，我们得到一个相位 $0, 2\pi$ 均匀分布，模长（幅度）服从瑞利分布的 $h_0(k)$ 

![](images/complex_gaussian_distribution.png)
![](images/rayleigh_amplitude_distribution.png)


## Jacobian 泡沫

