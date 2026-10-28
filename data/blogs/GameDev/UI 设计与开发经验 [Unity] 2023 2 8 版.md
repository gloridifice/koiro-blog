```toml
title = "UI 设计与开发经验 [Unity] 2023.2.8 版"
slug = "自我总结"
published = true
last_edited_date = "2025-06-13T19:25:00"
authors = "宏楼 Koiro"
class = "Dev"
date = "2023-09-23T17:11:00"
tags = ["Unity"]
```

# 前言

> 自己是一名游戏开发进修中的学生，被朋友提醒，决定总结一下自己做游戏开发中的 UI 设计和开发经验，供自己快速回顾，也给在苦恼相同问题的伙伴一些参考。自己没有从业经验，本篇文章请当做无专业性的文章看待。如果有前辈能够给予指点，不甚感激。
> 

本篇聚焦于视觉交互设计，包含 GUI 及其动效的设计和开发。 

# 〇、自己的 UI 设计流程

## 1. 在平面设计软件制作 UI 保真图

- 在这个阶段与伙伴敲定一个可用的版本
- 一些草图杂乱，很多部分都是在游戏中优化完成

![草图](UI%20%E8%AE%BE%E8%AE%A1%E4%B8%8E%E5%BC%80%E5%8F%91%E7%BB%8F%E9%AA%8C%20%5BUnity%5D%202023%202%208%20%E7%89%88/Untitled.jpg)

草图

![成品](UI%20%E8%AE%BE%E8%AE%A1%E4%B8%8E%E5%BC%80%E5%8F%91%E7%BB%8F%E9%AA%8C%20%5BUnity%5D%202023%202%208%20%E7%89%88/ui-preview.jpg)

成品

![草图](UI%20%E8%AE%BE%E8%AE%A1%E4%B8%8E%E5%BC%80%E5%8F%91%E7%BB%8F%E9%AA%8C%20%5BUnity%5D%202023%202%208%20%E7%89%88/Untitled%201.jpg)

草图

![成品](UI%20%E8%AE%BE%E8%AE%A1%E4%B8%8E%E5%BC%80%E5%8F%91%E7%BB%8F%E9%AA%8C%20%5BUnity%5D%202023%202%208%20%E7%89%88/Untitled%202.jpg)

成品

![草图](UI%20%E8%AE%BE%E8%AE%A1%E4%B8%8E%E5%BC%80%E5%8F%91%E7%BB%8F%E9%AA%8C%20%5BUnity%5D%202023%202%208%20%E7%89%88/Untitled%203.jpg)

草图

![成品](UI%20%E8%AE%BE%E8%AE%A1%E4%B8%8E%E5%BC%80%E5%8F%91%E7%BB%8F%E9%AA%8C%20%5BUnity%5D%202023%202%208%20%E7%89%88/Untitled%204.jpg)

成品

## 2. 导出 UI 所需的图像素材

请将大小导出为 2^n（为了压缩），并最好使单位像素大小一致。

## 3. 在 Unity 中制作 UI

请见下文↓

# 一、在 Unity 中制作 UI（UGUI）

## Unity 中 GUI 对象管理

一般自己针对一个使用场景会写一个 `xxUIManager` ，以单例模式存在，这里包含 GUI 的所有（有需的）GUI 对象引用、会触发事件（退出、重新开始等）。

`xxUIManager` 最终会挂在一个 Canvas 下，再将该 Canvas 存为预制体，修改只需修改预制体即可。

以下为例：

- LevelUIManager (Canvas)
    - ButtonsPanel (Panel)
        - AButton
        - BButton
        - ……
    - KeymapPanel
    - WinPanel
    - ……

当某个面板的功能过多时，可能会将对一个面板的操作封装成一个脚本，挂在对应面板下。

`xxUIManager` 和面板的脚本下的物体引用通常会暴露给外部对象，但通常不会在外部对象中进行操作。外部对象对`xxUIManager`下子物体的操作尽可能封装成方法，供外部调用。

## 自定义 UI 组件 与 CustomEditor 与 预制体

### 编写自己的 UI 组件

很多时候 Unity 自带的 UI 组件完全不能满足自己的需求，此时会自己写 UI 脚本，继承 IPointerClickHandler, IPointerDownHandler, Up, Enter, Exit… 等常用的事件。

> 我还没有做过兼容手柄的菜单，所以此处肯定是忽略了兼容手柄选择的情况。
对兼容手柄有需求的读者或许可以阅读 Selectable 类。
> 

### 自定义 Inspector ( CustomEditor ) 方便地修改预制体参数

写好 UI 脚本后使用 CustomEditor 调整 UI 是十分方便的事。

[Unity - Manual:  Create a Custom Inspector](https://docs.unity3d.com/Manual/UIE-HowTo-CreateCustomInspector.html)

不过使用预制体时使用 Editor 会出现无法将改变的参数保存的问题，自己找了一些地方找到了解决方案。 Editor 类：

```csharp
private MyUIComponent componetSelf;
private void OnEnable()
{
		componetSelf = target as MyUIComponent;
}

public override void OnInspectorGUI()
{
		base.OnInspectorGUI();

		//objectSelf 指这个 editor 脚本所对应的组件(target)
		//objectSelf.TextComponent 是一个例子，指这个预制体内被修改的组件，这里是文字
		Undo.RecordObject(componetSelf.TextComponent, "descriptive name of this operation");

		//这里执行需要做的操作，例如修改文字的内容和字号
		componetSelf.TextComponent.text = EditorGUILayout.TextField("Name", componetSelf.TextComponent.text);
		componetSelf.TextComponent.fontSize = EditorGUILayout.FloatField("Font Size", componetSelf.TextComponent.fontSize);
    
		//标记文字组件被修改
		EditorUtility.SetDirty(componetSelf.TextComponent);
		PrefabUtility.RecordPrefabInstancePropertyModifications(componetSelf.TextComponent);
		//标记场景中我们的物体被修改
		EditorSceneManager.MarkSceneDirty(componetSelf.gameObject.scene);
}
```

## 资源文件管理

自己对 UI 的资源文件通常不会单开一个文件夹，而是放在对应种类的文件夹下方的 `~/UI/` 目录下。

自己通常会用 `<文件类型[小写]>(_<细分类型[小写]>)_<文件名称[大写驼峰]>`  的方式命名资源文件。对于 UI 的资源文件，例子是 `spr_ui_HelloWorld` ，其中 spr 代表 Sprite，ui 代表 UI，HelloWorld 是文件名。对于其它资源，命名方式同理，如预制体用 p_，状态机用 ant_，动画用 an_ 等，缩写的选择取决于自己和伙伴的认知。

## 精灵图的注意事项

对于用于 UI 的精灵图，建议将 Mipmap 关掉，（按需）将 Wrap Mode 改为 Clamp，这两者可能导致 UI 边缘出现错误的边线。

导入资源时设置资源属性可阅读：

[Unity - Scripting API: AssetPostprocessor.OnPostprocessTexture(Texture2D)](https://docs.unity3d.com/ScriptReference/AssetPostprocessor.OnPostprocessTexture.html)

# 二、动效

自己实现动效的方法可以不严谨地分为三类：

## 1. DoTween：功能完整、使用方便

> 有些学习成本、管理复杂
> 

DOTween 的官网：

[DOTween (HOTween v2)](http://dotween.demigiant.com/)

一个 DOTween 系列教程：

[How to Install DOTween in Unity - Yarsa DevBlog](https://blog.yarsalabs.com/dotween-unity-animation-part1/)

各种动画曲线一览：

[Easing Functions Cheat Sheet](https://easings.net/)

## 2. 自己写个 Tweener：灵活可控，支持打断动画

> 数学基础是插值，推荐视频
> 

[Lerp的使用方法_哔哩哔哩_bilibili](https://www.bilibili.com/video/BV1Ur4y1E7Fv)

在用 DOTween 时发现管理动画打断很不方便，会出现动画突然闪到终点的情况等等；另一方面，在场景切换或物体销毁时，物体的动画没有播放完，DOTween 也会给出安全模式警告。

于是就自己实现一个可以随时打断和返程的 Tweener（与 DOTween 中的 Tweener 无关）

我们实现一个类：

```csharp
		[Serializable] // 使可以在 Inspector 编辑
    public class SimpleTweener
    {
        [HideInInspector] public float time; // 动画的当前时间
        public float duration; //动画的持续时长
        public AnimationCurve curve; //动画曲线

        private bool isActive; //是否正在进行
        private bool isPositive; //是否进行正向计时

        public float ProgressRate => time / duration; //动画播放的进度

        public Action<float> doAnimation = (value) => { };

        public void Update()
        {
            if (isActive)
            {
                time = isPositive ? 
                    Mathf.Min(time + Time.deltaTime, duration) : 
                    Mathf.Max(time - Time.deltaTime, 0);
                
                if (time == 0 || time == duration) isActive = false;
                
                float result = curve.Evaluate(ProgressRate); //对曲线进行采样
                doAnimation.Invoke(result);
            }
        }

        public void Start()
        {
            isActive = true;
            isPositive = true;
        }

        public void Stop()
        {
            isActive = true;
            isPositive = false;
        }
    }
```

 之后我们在自己的 `MonoBehaviour` 中使用：

```csharp
public class AnimationTestBehaviour : MonoBehaviour, IPointerEnterHandler, IPointerExitHandler
{
				public SimpleTweener tweener;
        public Image image;

        private void OnEnable()
        {
            tweener.doAnimation += DoTween;
        }
				private void OnDisable()
        {
            tweener.doAnimation -= DoTween;
        }
				
        private void Update()
        {
            tweener.Update();
        }

        void DoTween(float value)
        {
            Color color = image.color;
            color.a = value;
            image.color = color;
            image.transform.localScale = (0.5f + value) * Vector3.one;
        }

        public void OnPointerEnter(PointerEventData eventData)
        {
            tweener.Start();
        }

        public void OnPointerExit(PointerEventData eventData)
        {
            tweener.Stop();
        }
    }
```

**Inspector**：

![Untitled](UI%20%E8%AE%BE%E8%AE%A1%E4%B8%8E%E5%BC%80%E5%8F%91%E7%BB%8F%E9%AA%8C%20%5BUnity%5D%202023%202%208%20%E7%89%88/Untitled%205.jpg)

*效果*：

![sample.gif](UI%20%E8%AE%BE%E8%AE%A1%E4%B8%8E%E5%BC%80%E5%8F%91%E7%BB%8F%E9%AA%8C%20%5BUnity%5D%202023%202%208%20%E7%89%88/sample.gif)

自己编写 Tweener 的可控性高却繁琐，单程动画自己一般会用 DoTween 实现。

*我想 DOTween 也是有实现可打断动画的方法的，自己应该学习一下。//不过自己造轮子快乐啊

## 3. 无限趋近式：编写快捷、适应性高

> 动画形式单一、可控性差、无法达到目标值
> 

在一次 Ludum Dare 的作品中我用了这个方法实现卡牌动画

[Time: Cardmancer](https://trioduck.itch.io/time-cardmancer)

![sample_1.gif](UI%20%E8%AE%BE%E8%AE%A1%E4%B8%8E%E5%BC%80%E5%8F%91%E7%BB%8F%E9%AA%8C%20%5BUnity%5D%202023%202%208%20%E7%89%88/sample_1.gif)

无限趋近式的意思是，让希望改变的参数的实际值无限趋近这个目标值。

以缩放为例，我们设定一个物体缩放的目标值，然后让实际值无限趋近这个目标值：

```csharp
float value = Mathf.Lerp(currentSize, targetSize, speed);
```

`speed` 是趋近的速度，在 0 ~ 1 之间。在 Update 中调用的话，记得将`speed` 乘上 `Time.deltaTime` .

```csharp
public void Update(){
	float currentSize = transform.localScale.x;
	currentSize = Mathf.Lerp(currentSize, targetSize, speed * (Time.deltaTime * 50f));
	image.transform.localScale = currentSize * Vector3.one;
}
```

![sample_2.gif](UI%20%E8%AE%BE%E8%AE%A1%E4%B8%8E%E5%BC%80%E5%8F%91%E7%BB%8F%E9%AA%8C%20%5BUnity%5D%202023%202%208%20%E7%89%88/sample_2.gif)

这种方法会导致物体永远无法达到目标值，动画的停用也需要用差值进行检测。但其管理和实现都很简单，2D 相机跟踪物体用这种方式实现十分方便。

# 后记：

回顾一下，发现自己做完的可玩的游戏都是 Game Jam 作品。自己在私下幻想和开发的游戏不便展示，完成度也拿不出手。所以本篇文章的 UI 美术都是 Game Jam 的快速生产内容，一定有许多不标准的地方。

做 UI 是自己喜欢的一件事，自己一有游戏想法，第一件事就是去做 UI，自己做了不少游戏的 UI，结果游戏却没怎么动工过w。若有机会，愿意结识更多的伙伴，去做些有趣好看的游戏。
