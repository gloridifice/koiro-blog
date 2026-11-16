# 自绘参数滑杆

## 最小写法

```html
<div class="kc-root">
  <label for="density">密度</label>
  <input class="kc-range" id="density" type="range" min="0" max="1" step="0.01" value="0.42">
</div>
```

博客和 Obsidian 已注入 `component.css` / `range.js`，文章不重复引用资源或复制轨道实现。[独立示例](../examples/range.html) 的 link/script 仅用于直接浏览器预览。

## 外观与交互

- 无圆形滑块、描边或阴影。底色为 bg2 `#212121`，进度为 accent `#ce4623`；可由祖先的 `--kc-input-bg` / `--kc-accent` 覆盖。
- 常态 3px，悬浮 6px，拖拽 8px；轨道居中增厚，点击区域始终 40px 高。
- 厚度过渡 180ms，点击及键盘进度过渡 160ms，拖拽进度过渡 45ms；遵循 `prefers-reduced-motion`。
- 点击跳转、鼠标及触摸拖拽由公共脚本处理，移出轨道仍可拖动并限制在范围内。触摸纵向操作保留页面滚动。
- 点击和拖拽不显示焦点描边；键盘操作保留焦点提示。禁用时不可调整，且不增厚。

## 原生输入契约

仅增强 `input.kc-range[type="range"]`。脚本在文档就绪时为原输入添加 `.kc-slider` 外壳及自绘轨道；原输入透明覆盖，继续负责 label、表单值、min/max/step、disabled 和原生键盘行为。不影响未标记的 range，也不改变图片对比滑杆。

监听原输入的 `input` 事件更新计算和 output；拖拽结束且数值变化时发出 `change`。程序赋值后需发出 `input`，让预设和业务读数同时刷新：

```js
input.max = '24';
input.value = '12';
input.dispatchEvent(new Event('input', { bubbles: true }));
```

不要再手工设置旧的 `--value` 进度变量。动态 min/max/step/value 属性和表单 reset 也会刷新轨道。初始化幂等，不自动发现文档就绪后新插入的输入；禁用 JS 时保留可用的原生 range。

原输入会多一层外壳，作者样式不要依赖它是 label 的直接子元素，也不要用 `label span` 一并覆盖生成的轨道。标签文字可加专用类。组件的公式、输出格式及状态管理仍留在文章脚本中。
