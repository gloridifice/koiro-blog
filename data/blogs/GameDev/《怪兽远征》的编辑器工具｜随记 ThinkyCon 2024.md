```toml
title = "《怪兽远征》的编辑器工具｜随记 ThinkyCon 2024"
slug = "本编文章是 ThinkyCon 2024 有关怪兽远征编辑器部分的分享的笔记。其对我们正在开发的解秘游戏的关卡编辑器开发提供了不少启发。"
published = true
last_edited_date = "2024-04-05T22:05:00"
authors = "宏楼 Koiro"
class = "Dev"
date = "2024-04-05T19:08:00"
tags = ["Editor", "Game Dev", "Unity"]
```

本编文章是 ThinkyCon 2024 有关怪兽远征编辑器部分的分享的笔记，这部分的主讲是 Alan Hazelden，下文用 Alan 简称。

[ThinkyCon 2024 - Day 1](https://www.youtube.com/live/7kgXBifWQXI?si=YV_v28aWoVnYm98q&t=5618)

ThinkyCon 2024 视频

![Untitled](%E3%80%8A%E6%80%AA%E5%85%BD%E8%BF%9C%E5%BE%81%E3%80%8B%E7%9A%84%E7%BC%96%E8%BE%91%E5%99%A8%E5%B7%A5%E5%85%B7%EF%BD%9C%E9%9A%8F%E8%AE%B0%20ThinkyCon%202024/Untitled.jpg)

# PuzzleScript

开发者最早使用 PuzzleScript 实现了一个游戏原型，并用其制作关卡原型。

![Untitled](%E3%80%8A%E6%80%AA%E5%85%BD%E8%BF%9C%E5%BE%81%E3%80%8B%E7%9A%84%E7%BC%96%E8%BE%91%E5%99%A8%E5%B7%A5%E5%85%B7%EF%BD%9C%E9%9A%8F%E8%AE%B0%20ThinkyCon%202024/Untitled%201.jpg)

其中关卡是用字符来表示的，点选即载入。

![Untitled](%E3%80%8A%E6%80%AA%E5%85%BD%E8%BF%9C%E5%BE%81%E3%80%8B%E7%9A%84%E7%BC%96%E8%BE%91%E5%99%A8%E5%B7%A5%E5%85%B7%EF%BD%9C%E9%9A%8F%E8%AE%B0%20ThinkyCon%202024/Untitled%202.jpg)

# Unity

---

## 编辑

开发者们实现了一套运行时编辑器关卡编辑工具，其可以检测玩家所在的关卡并进行操作。例如进入编辑模式，选择方块位置，拖拽，即可完成对关卡的修改。每一个关卡都有独立的编号，开发者可以通过编号索引关卡。鼠标悬浮在关卡上时会显示关卡名称和编号。

![Edit mode](%E3%80%8A%E6%80%AA%E5%85%BD%E8%BF%9C%E5%BE%81%E3%80%8B%E7%9A%84%E7%BC%96%E8%BE%91%E5%99%A8%E5%B7%A5%E5%85%B7%EF%BD%9C%E9%9A%8F%E8%AE%B0%20ThinkyCon%202024/Untitled%203.jpg)

Edit mode

Alan 说运行时编辑关卡的操作模式提供了一个很重要的特性：其避免了测试关卡时你需要“改关卡→重新运行→测试关卡”这样浪费时间的行为。

具体到他们的编辑工具，其有这些模式：

- Play：游玩模式
- Edit：编辑某个关卡
- Arrange：可以自由移动和缩放相机，双击关卡即可进入该关卡并切换至 Play 模式；也可以自由地拖拽关卡（岛屿）的位置
- Landmark：用于操作既不是关卡内容的地标物体

同时他们还提供了 Editor-only Level，即只有编辑器中才存在的关卡。这些关卡和普通关卡放在同一张大地图中，用白色的框标识。编辑器里可以设置显示和隐藏。

![Untitled](%E3%80%8A%E6%80%AA%E5%85%BD%E8%BF%9C%E5%BE%81%E3%80%8B%E7%9A%84%E7%BC%96%E8%BE%91%E5%99%A8%E5%B7%A5%E5%85%B7%EF%BD%9C%E9%9A%8F%E8%AE%B0%20ThinkyCon%202024/Untitled%204.jpg)

![还做了碰撞标识](%E3%80%8A%E6%80%AA%E5%85%BD%E8%BF%9C%E5%BE%81%E3%80%8B%E7%9A%84%E7%BC%96%E8%BE%91%E5%99%A8%E5%B7%A5%E5%85%B7%EF%BD%9C%E9%9A%8F%E8%AE%B0%20ThinkyCon%202024/Untitled%205.jpg)

还做了碰撞标识

开发者提供了 x 的标识，用于标注这里不希望有方块，否则可以岛屿被连通。

![Untitled](%E3%80%8A%E6%80%AA%E5%85%BD%E8%BF%9C%E5%BE%81%E3%80%8B%E7%9A%84%E7%BC%96%E8%BE%91%E5%99%A8%E5%B7%A5%E5%85%B7%EF%BD%9C%E9%9A%8F%E8%AE%B0%20ThinkyCon%202024/Untitled%206.jpg)

## 保存

做了任何改动，只有点击 Save/Save All，地图才会以 json 的格式被存储到磁盘中。使用 json 的其中一个好处是易于被 git 跟踪，可以进行版本管理。如果不小心搞毁了关卡，rollback 就好了。

## 设置主题

设置关卡的视觉主题是通过他们自己的 Tag 来实现的，开发者可以拖拽选择一系列关卡，然后切换关卡的 Tag 来快速切换主题。

![Untitled](%E3%80%8A%E6%80%AA%E5%85%BD%E8%BF%9C%E5%BE%81%E3%80%8B%E7%9A%84%E7%BC%96%E8%BE%91%E5%99%A8%E5%B7%A5%E5%85%B7%EF%BD%9C%E9%9A%8F%E8%AE%B0%20ThinkyCon%202024/Untitled%207.jpg)

## 回放测试

他们还开发了测试模式，测试模式可以录制玩家的操作，存储到本地，需要时进行回放。其对于记录解谜游戏的解法有帮助，同时也可以批量测试关卡、案例，确保关卡不会因为某些机制改变而出现问题。

![Untitled](%E3%80%8A%E6%80%AA%E5%85%BD%E8%BF%9C%E5%BE%81%E3%80%8B%E7%9A%84%E7%BC%96%E8%BE%91%E5%99%A8%E5%B7%A5%E5%85%B7%EF%BD%9C%E9%9A%8F%E8%AE%B0%20ThinkyCon%202024/Untitled%208.jpg)

![Untitled](%E3%80%8A%E6%80%AA%E5%85%BD%E8%BF%9C%E5%BE%81%E3%80%8B%E7%9A%84%E7%BC%96%E8%BE%91%E5%99%A8%E5%B7%A5%E5%85%B7%EF%BD%9C%E9%9A%8F%E8%AE%B0%20ThinkyCon%202024/Untitled%209.jpg)
