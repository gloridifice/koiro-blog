```toml
title = "Blog 页面、样式测试"
slug = "页面样式测试"
published = true
last_edited_date = "2024-11-14T04:26:00"
class = "Life"
date = "2023-09-30T14:17:00"
```

# 这是一级标题

> 在这里写字，这里是引用框
这是*斜体。下划线 ~~删除  **加粗**~~*
> 

- 列表
    - 次列表
    - 以上
        - 次次列表
        - 

1. 序号列表
2. 这是二
    1. A here
    2. B here

- toggle list 的效果`是这样`
    
    有什么东西嘛
    
    123123
    

# 记得标题不要打冒号

下面是一张带有说明的图片

![随便写点什么](Blog%20%E9%A1%B5%E9%9D%A2%E3%80%81%E6%A0%B7%E5%BC%8F%E6%B5%8B%E8%AF%95/Untitled.jpg)

随便写点什么

## 下面是Bookmark（我是二级标题）

[哔哩哔哩 (゜-゜)つロ 干杯~-bilibili](https://www.bilibili.com/)

这是 caption

- [ ]  todo也是可以写的
- [x]  要做的事情有点多

<aside>
💡 Callout 的效果是这样的

</aside>

这是 code block

```kotlin
fun readChildrenBlocks(currentPath: Path): List<DataBlock>? {
    val files = currentPath.toFile().listFiles()
    val dataBlocks = if (currentPath.hasChildren() && files != null) {
        val handledFiles = files.filter { file ->
            file != null && file.isFile && file.name.endsWith(".json")
        }.sortedBy { it.name.split('_').first().toInt() }
        val list = ArrayList<DataBlock>()
        for (file in handledFiles) {
            val read = file.readText()
            val block = NotionClient.defaultJsonSerializer.toBlock(read)
            val dataBlock = if (block is ImageBlock){
                ImageDataBlock(block, currentPath)
            }else{
                DataBlock(block, currentPath)
            }
            list.add(dataBlock)
        }
        list
    } else null
    return dataBlocks
}
```
