# 图片滑动对比

## 用法

博客和已更新的 Obsidian 插件默认提供已注册的样式与脚本。围栏内只需要：

````markdown
```html component
<figure class="kc-compare">
  <img data-side="a" src="./a.png" alt="A：处理前">
  <img data-side="b" src="./b.png" alt="B：处理后">
  <figcaption>处理前后的对比</figcaption>
</figure>
```
````

保留 figure、两个带 `data-side` 的直属 img 和直属 figcaption。脚本在文档就绪后补齐滑杆、分界线、可访问名称和错误状态，不需要手写内部 DOM、CSS、JS 或 caption ID。多个实例独立工作；初始化幂等。动态脚本应在 DOMContentLoaded 前生成这些标记，不承诺对稍后插入的节点自动发现。

## 外观与操作

只显示图片、分界滑杆和下方 caption。左 A、右 B、初始位置 50%；无边框、圆角、面板阴影、背景卡片、标题、工具栏、重置按钮或百分比读数。手柄及键盘焦点只用于操作。

- 鼠标、触屏可拖动或点击定位；拖出图片区仍能结束本次拖动。
- Tab 聚焦，方向键微调，Home 全 B，End 全 A。
- 触屏保留纵向滚动与双指缩放，取消拖动不会留下状态。
- 两图加载成功后才启用滑杆。失败时出现路径错误，恢复加载后可继续。
- 无默认动画。没有 JavaScript 时保留原始图片与 caption，不提供交互。

不要套用 `.kc-root` / `.kc-panel`；公共面板 CSS 虽然也会加载，但不会作用于图片对比。

## 对齐、比例与图片路径

两图始终占同一完整坐标空间，仅改变各自的 clip-path。不能通过改变图片宽度模拟裁切，否则会挤压或错位。两侧分别裁剪，透明像素不透出另一图。

最好使用相同尺寸、比例和构图的图片。默认容器按 A 的原始比例显示，B 使用 object-fit: cover 居中裁切、不拉伸。不同构图或不能接受裁切时，先确认对齐、裁剪或留白方案。

可以补上真实 width/height，并在 figure 设置 `style="--compare-ratio: 1200 / 750"` 预留高度。图片加载后仍按 A 的自然比例更新。

替换两张图的 src、alt 及 caption。src 相对于目标文章/笔记，不是 skill 目录；博客限允许的内容目录，Obsidian 限 Vault。CSS/JS 由宿主提供，图片仍由作者明确引用，不接受任意磁盘路径。

如需局部覆盖，使用组件作用域内的 style，例如 `.kc-compare { --compare-caption-color: #b7b3b0; }`。不要为单张图修改全局预设。

## 实现与示例

- [可直接打开的示例](../examples/image-compare.html)：本地示意图片，不是用户素材。
- [正式 CSS](../../../../assets/component/image-compare.css)、[正式 JS](../../../../assets/component/image-compare.js)：唯一实现，不在 skill 内复制。
- [注册说明](registration.md)：新增或修改公共资源的流程。

独立浏览器预览才需要示例中的 link/script；在支持预设的 html component 中省略这些引用。脱离博客/插件发布独立 HTML 时，显式加载或内联正式资源。

不读取像素、不使用 Canvas，不需要 fetch、存储或同源权限。图片比例和 caption 自然撑高，由宿主测量运行时处理。

## 验收

检查 0 / 50 / 100% 的左右关系、透明区域、对齐与尺寸不变；验证鼠标、触摸、键盘、加载失败及多实例。375px、320px 下无横向溢出，caption 可换行。博客与插件中用最小 HTML 验证，不依靠手工补充 CSS/JS 掩盖预设加载故障。
