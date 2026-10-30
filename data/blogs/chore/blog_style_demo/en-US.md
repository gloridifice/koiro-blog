```toml
title = "Blog Page and Style Test"
summary = "Page style test"
ai-translation = true
```

# This Is a Level-One Heading

> Write here; this is a blockquote
This is *italic. Underline ~~strikethrough  **bold**~~*
> 

- List
    - Sublist
    - The above
        - Sub-sublist
        - 

1. Numbered list
2. This is two
    1. A here
    2. B here

- A toggle list looks `like this`
    
    Is there anything here?
    
    123123
    

# Remember Not to Put Colons in Headings

Below is an image with a caption

![Just write something](images/Untitled.jpg)

Just write something

## Below Is a Bookmark (I'm a Level-Two Heading)

[bilibili (゜-゜)つロ Cheers~-bilibili](https://www.bilibili.com/)

This is a caption

- [ ]  You can write todos, too
- [x]  There's quite a lot to do

<aside>
💡 This is what a Callout looks like

</aside>

This is a code block

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
