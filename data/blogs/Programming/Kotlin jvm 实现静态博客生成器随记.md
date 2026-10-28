```toml
title = "Kotlin jvm 实现静态博客生成器随记"
slug = "用 kotlin-jvm、notion 和 typescript 实现了自己的静态博客生成器，此博客就是用其生成的。写得过程很愉快。"
published = true
last_edited_date = "2026-05-07T22:30:00"
authors = "宏楼 Koiro"
class = "Dev"
date = "2023-10-02T19:01:00"
tags = ["Kotlin", "Notion"]
```

<aside>
✒️ 前言：在 2023 年的国庆假期前，自己在翻修自己的博客，偶然找到了 [super.so](http://super.so) 这个根据 notion 生成网页的平台。当想自己尝试搭建一个的时候，发现实用的部分都要付费，价格又不低，于是干脆自己写一个。既然能借助顺着 notion 作为数据库的思路，我找到了可以用的 kotlin 库，另外找到了用文件系统直接将 html 写入本地文件的样例，于是自己写博客生成器的历程就开始了。Kotlin 是一门很舒服的语言，这一点给我写博客生成器带来了很多舒适和便利，让我写博客的过程很愉快。

</aside>

# 零、用kotlin写博客生成器

什么是 Kotlin？

[Kotlin Programming Language](https://kotlinlang.org/)

Kotlin l官网

Kotlin 是一门可以用来开发 jvm 程序、跨平台应用、后段等的编程语言，它兼容 java。

我的项目各部分的分工如下：

- Kotlin-jvm：前端生成器
- Notion：数据库
- Typescript：编译成 JavaScript

# 一、开发环境配置

用到的 kotlin 库：

- [**kotlinx.html**](https://github.com/Kotlin/kotlinx.html#kotlinxhtml): A kotlinx.html library provides DSL to build HTML to [Writer](https://docs.oracle.com/javase/8/docs/api/java/io/Writer.html)/[Appendable](https://docs.oracle.com/javase/8/docs/api/java/lang/Appendable.html) or DOM.
- [**notion-sdk-jvm**](https://github.com/seratch/notion-sdk-jvmhttps://github.com/seratch/notion-sdk-jvm): A [Notion API](https://developers.notion.com/) SDK for any JVM language users.
- [**mordant**](https://github.com/ajalt/mordant): Colorful styling for command-line applications.

```kotlin
dependencies {
    testImplementation(kotlin("test"))

    val kotlinxHtmlVersion = "0.9.1"
    val notionSdkVersion = "1.9.0"
    val mordantVersion = "2.1.0"

    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:${kotlinxHtmlVersion}")
    implementation("com.github.seratch:notion-sdk-jvm-core:${notionSdkVersion}")
    implementation("com.github.ajalt.mordant:mordant:${mordantVersion}")
}
```

# 二、从 Notion 数据库抓取数据

使用 notion-sdk-jvm 库从 Notion 获取数据首先需要获得自己 Notion 账号的 Token，可以在下面的页面生成 token.

[Notion – The all-in-one workspace for your notes, tasks, wikis, and databases.](https://www.notion.so/my-integrations)

notion 的 integration 页面

在从 Integration 中获取 token 后，就可以实例化 `NotionClient` 类了，其构造函数接受 token 作为参数。为了 toke 不要暴露给他人，为了保证数据安全，我们将 token 以`NOTION_TOKEN` 为变量名写在环境变量中，用下面的方式获取 token.

```kotlin
val notionToken = System.getenv("NOTION_TOKEN");
NotionClient(token = notionToken).use { client ->
    //todo
}
```

`NotionClient` 中有很多获取数据的方法，其中每种数据获取方式的具体信息可以查阅 [Notion API](https://developers.notion.com/)

# 三、数据的保存与动态更新

每次生成博客都重新获取 notion 数据很慢，因此我们将获取 notion 数据的部分和生成博客的部分分开。我们从 notion 请求数据获得的是序列化好的 json 数据，我们先将其存储在本地。在生成博客的部分反序列化为 kotlin 对象，供我们使用。

## 数据的序列化和反序列化

notion-sdk-jvm 并没有提供直接获得 json 数据的 api，所有的方法返回的都是反序列化好的 Kotlin 对象。因此可以从其 api 中将需要的内容摘出来，复制一份该方法并直接返回 json（body : String ）。

![Untitled](Kotlin%20jvm%20%E5%AE%9E%E7%8E%B0%E9%9D%99%E6%80%81%E5%8D%9A%E5%AE%A2%E7%94%9F%E6%88%90%E5%99%A8%E9%9A%8F%E8%AE%B0/Untitled.jpg)

然后写入本地就可以了。

notion-sdk-jvm 的反序列化功能写在`NotionJsonSerializer` 及其子类中，可以直接调用  `NotionClient.defaultJsonSerializer` 来反序列化 json string 成 kotlin 对象。

## 数据的保存结构

在我们获取数据后，可以保存到一个本地文件夹中，其结构大概如下

```kotlin
notionData
	- database.json
	- queryDatabaseResult.json
	- a.json //某个文章的id
	-> a //某个文章的id的文件夹，递归地存储block内容
		- b.json
		- img_b.png //如果某个block是图片，则存储图片资源以img_id的格式
```

![Untitled](Kotlin%20jvm%20%E5%AE%9E%E7%8E%B0%E9%9D%99%E6%80%81%E5%8D%9A%E5%AE%A2%E7%94%9F%E6%88%90%E5%99%A8%E9%9A%8F%E8%AE%B0/Untitled%201.jpg)

## 动态更新数据

在获取数据时，可以通过比对最近更新时间 lastEditedTime，来判断是否需要更新该 page 或 block

```kotlin
private fun isBlockNeedToUpdate(block: Block, parentPath: Path): Boolean {
      val pageFile = parentPath.childPath(block.id!! + ".json").toFile()
        if (pageFile.exists() && pageFile.isFile) {
            val existPage = client.jsonSerializer.toBlock(pageFile.readText())
            if (existPage.lastEditedTime == block.lastEditedTime)
                return false
        }
        return true
    }
```

# 四、用 Kotlin 生成 Html

既然我们将 notion 数据存在了本地，我们读取写在本地的数据并用上面的方法反序列化，

## 写入 html

使用 `FileWriter#appendHTML().html{}` 的方式就可以写入 kotlinx.html 库的 `HTML` 对象进入文件了。

kotlinx 可以让开发者用 kotlin 的风格写 html，使用方法可以看其主页：[https://github.com/Kotlin/kotlinx.html](https://github.com/Kotlin/kotlinx.html)

```kotlin
filewriter.appendHTML().html {
		head{
			//...
		}
		body{
			//...
		}
}
filewriter.close()
```

# 五、CSS （SCSS）和 js（Typescript 编译）

## SCSS → CSS

写 css 的部分我使用了 SCSS，这是一个兼容 css 的样式表语言，你可以编译 SCSS 成 css 文件。

[Sass: Syntactically Awesome Style Sheets](https://sass-lang.com/)

Sass 官网

> SCSS 是 Sass 兼容 css 的版本，其后缀名为 .scss，SCSS 较 CSS 拥有更丰富的语言特性，同时向下兼容 CSS. 我的项目最开始用原生 CSS 书写，为了方便迁移和使用 IDE 的自动格式化，我选择了 SCSS
> 

### 安装 Sass

用 npm 安装 sass

> 如果你不了解 npm，请见 [https://www.npmjs.com/](https://www.npmjs.com/)
> 

```bash
npm install -g sass
```

<aside>
💡 SCSS 是 Sass 包的一部分，因此我们需要安装 Sass；

</aside>

### 编译 SCSS

```bash
sass --watch <input> <output>
```

使用上面的命令可以实时编译 SCSS 文件， -w (--watch) 参数的意思是实时监视文件变动并编译。

`<input>` `<output>` 可以是某个名为 .scss/.sass/.css 文件，也可以是文件夹；

## TypeScript → JavaScript

[JavaScript With Syntax For Types.](https://www.typescriptlang.org/)

TypeScript 官网

### 安装 TypeScript

```bash
npm install -g typescript --save-dev
```

### 编译 TypeScript

参照网络上的方法，我写了一个脚本来编译一个文件夹下的 TypeScript（mac 环境下）

`find` 后是需要编译的 ts 存放的目录，`--outDir` 后是输出目录

```bash
find ./src/main/typescript/ -name "*.ts" -type f >ts-files.txt
tsc @ts-files.txt --outDIr ./static/assets/js --removeComments
rm ts-files.txt
```

在 IDEA 的运行选项 → Edit Confihurations 窗口下可以添加运行选项

![Untitled](Kotlin%20jvm%20%E5%AE%9E%E7%8E%B0%E9%9D%99%E6%80%81%E5%8D%9A%E5%AE%A2%E7%94%9F%E6%88%90%E5%99%A8%E9%9A%8F%E8%AE%B0/Untitled%202.jpg)

# 六、本地服务器调试

本地服务器工具我用的是 http-server

[GitHub - http-party/http-server: a simple zero-configuration command-line http server](https://github.com/http-party/http-server)

其安装和使用在 Github 主页写的都十分清楚，不在此赘述。

# 结语

至此，博客框架就构建完成了，还有许多待优化的内容，例如现在编译需要运行几个命令和脚本、没有打包系统和资源管理系统等等。不过现在的状态足够自己使用，或许未来会有研究。

感觉这种做法并不流行，参考资料不多w。若能帮别人少踩点坑就最好了。
