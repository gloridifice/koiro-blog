```toml
title = "Notion API 小记"
english_title = "Notion API quick note"
slug = "NotionAPI 的一些笔记"
published = true
last_edited_date = "2025-06-05T13:49:00"
authors = "宏楼 Koiro"
class = "Dev"
date = "2023-10-02T22:45:00"
date = "2023-10-03"
tags = ["Notion"]
```

# 前言

Notion API

[Start building with the Notion API](https://developers.notion.com/)

不同的语言有不同的 Notion API 的库，本文章只做概念上的记录。

# 基本概念

id 是 Notion 的所有对象都有一个重要的标识，在获取数据需要使用 id，保存数据时也可以使用 id 以避免文件名称重复。

- Database：数据库
- Page：文章
- Block：文章内容的基本单位
    
    ![拖拽选中一个 block 会被高亮](Notion%20API%20%E5%B0%8F%E8%AE%B0/Untitled.jpg)
    
    拖拽选中一个 block 会被高亮
    

> 下文中带有 # 的内容代表可以在 Notion API 中搜索关键字查到
> 

# 获取数据库(Database)内容

获取数据库的内容要通过 #Query database 的方式，返回值包括了 database 的所有 page

# 获取文章(Page)内容

一个文章的内容本身其实是一个 block，其内容是该 block 的子 block.

而文章的 id 和其根 block 的 id 是相同的，因此只需要用 #Retrieve block children 递归地获取子 block 就可以得到一篇文章的所有内容了。

记得 #Retrieve block children 只能获取一个 block 的子 block 的信息，我们还要用 #Retrieve block 来获取方块本身的信息。

# 容易遗漏的点

创建一个 token（Integration） 后，我们还需要在一个数据库或者文章的右上角省略号设置的 connections 中加上对应的 Integration，这样该 Integration 才有读取或操作对应数据库和文章和的权限。

![Untitled](Notion%20API%20%E5%B0%8F%E8%AE%B0/Untitled%201.jpg)
