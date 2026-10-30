```toml
title = "An Introduction to Color for Graphic Design"
summary = "An article written for a club graphic design workshop, covering how to use color models, the characteristics of the HSB model, and things to keep in mind when working with color in graphic design."
ai-translation = true
```

<aside>
💡

An article written for a club graphic design workshop, covering how to use color models, the characteristics of the HSB model, and things to keep in mind when using it.

</aside>

# Recognizing Colors

Identifying colors is subjective. When we look at an image, we're often influenced by the combination of colors in its different elements, leading to perceptions that some people call “illusions.”

So, to learn about and use color, we first need to know what colors we're seeing. If we can describe a color quantitatively, and associate a color we see with certain numerical measures, that will help us identify it. Developments in modern computing have given us an easy-to-learn model for describing colors quantitatively. This model lets us describe, relatively accurately, exactly which color we're looking at.

> * Some theories hold that black, white, and gray shouldn't be called colors. In this article, though, all three are included in the concept of color.
I don't want either the author or the reader to get caught up in word games. If you encounter a word whose meaning, inferred from the context here, differs from the meaning you know, please take it to mean what you've inferred from the context.
> 

## Describing a Color Quantitatively: The HSV Model

Anyone who has used design software is probably familiar with these models for describing color: RGB, CMYK, HSL, and HSV.

> Color spaces and color models mean different things. The terms RGB, CMYK, HSL, and HSV also mean different things depending on whether they're being used as color spaces or color models. Below, we'll treat them as ideal mathematical models—that is, color models.
> 

Each model has different uses. RGB was designed for displays to reproduce color, while CMYK was designed for printing. HSL and HSV were designed for human use.

Let's get familiar with the HSV model here.

### The HSV (or HSB) Model

HSV stands for Hue, Saturation, and Value. It's also called HSB, where B stands for Brightness.

> The Chinese term “亮度” is used for Lightness in the HSL model, so it's best not to use it for Value (“明度”). Brightness in HSB can itself be translated as “亮度,” though, so in the context of HSV, people will understand that “亮度” refers to V. These terms can even have different meanings in some areas involving pigments. I hope we won't get caught up in word games.
> 

![image.png](images/image.jpg)

This image shows hue and saturation at a value of 100%. Saturation is 100% at the outermost ring and 0% at the innermost ring. Hue is usually measured in degrees, ranging from 0 to 360 degrees.

> Keep in mind that this is essentially a very direct mathematical mapping from RGB space to HSV that is comfortable to look at. It isn't a perfect color model for humans, and we'll see some of its problems later.
> 

![image.png](images/image%201.jpg)

### The HSL Model

In the HSL model, how vivid a color is depends on both its saturation and lightness parameters. Note that saturation in HSL already has a different mathematical meaning from saturation in HSV.

> HSL is similar to HSV. For some people, HSL better reflects the intuitive notion of “saturation” and “lightness” as two independent parameters. For others, however, its definition of saturation is wrong, because very soft, nearly white colors can be defined as fully saturated in HSL. Whether HSV or HSL is better suited to human user interfaces is debated.
* From Wikipedia
> 

![image.png](images/image%202.jpg)

### Mathematical Definitions — Reference

https://zh.wikipedia.org/zh-cn/HSL%E5%92%8CHSV%E8%89%B2%E5%BD%A9%E7%A9%BA%E9%97%B4#%E5%BE%9ERGB%E5%88%B0HSL%E6%88%96HSV%E7%9A%84%E8%BD%89%E6%8F%9B

# Hue

## Hue Itself Has “Brightness”

Here are two fully saturated colors with hues of 207 and 243 degrees. Our eyes might find the color at 207 degrees “brighter.” We won't try to determine here whether this is a flaw in the color model, a characteristic of human vision, or both.

In the HSV model, even at the same saturation and value, different hues have different perceived “brightness.” This is an objective fact, and something to keep in mind when using HSV—or even when using color at all.

![image.png](images/image%203.jpg)

![image.png](images/image%204.jpg)

Some practical applications:

1. When we want a darker color, we can try changing the hue.

Two classic examples:

- Yellow and orange
- Sky blue and indigo

Orange has a lower perceived brightness than yellow. So when we need a color that looks darker than yellow, shifting the hue toward orange often feels better than simply lowering V.

Orange is especially effective for depicting shadows on gold.

![golden sword.png](images/golden_sword.png)

These oranges were chosen using that approach.

> But the text against the orange background is hard to read because there isn't enough contrast. It's a flawed design. (The margins aren't right either.)
> 

![image.png](images/image%205.jpg)

1. Light in the real world has color, and objects often have color too, so our eyes are more familiar with the colors produced when colored light hits colored objects. When looking for darker and lighter colors, we can imagine the color of the light and shift the hue accordingly, or simply make a conscious effort to try changing the hue. The resulting colors often look more natural to the human eye. Exaggerating this characteristic might give us some nice stylized effects.
    
    ![[https://x.com/i/status/1848663623289172152](https://x.com/i/status/1848663623289172152)](images/image%206.jpg)
    
    [https://x.com/i/status/1848663623289172152](https://x.com/i/status/1848663623289172152)
    
    > In actual painting, hue shifts in shadows or light are more often achieved directly with blending modes.
    > 

## Some Observations on How “Contrast” Affects Color Perception

![image.png](images/image%207.jpg)

![image.png](images/image%208.jpg)

In high-contrast surroundings, our eyes have a harder time distinguishing between similar colors (left). Conversely, in low-contrast surroundings (right), our eyes can more easily pick out differences between similar colors.

So when designing with low-contrast background colors, such as light or dark modes on a product website, we need to be especially aware that the differences between color values don't have to be very large.

![Background color](images/image%209.jpg)

Background color

![AirPods Max background — a lighter stand-in background color](images/image%2010.jpg)

AirPods Max background — a lighter stand-in background color

You can see that the numerical difference between the two is very small, but in a low-contrast setting where the whole background is white, the difference between the colors is clearly visible to the naked eye (the shadow also increases the contrast between them). That's why I try to keep the numerical differences small when choosing colors like these.

![image.png](images/image%2011.jpg)

Also, because the background is white, choosing #000000 directly for the text creates strong contrast and draws a lot of attention. It may also make extended reading tiring. This relates to the concept of “typographic color,” which we'll introduce later.

![image.png](images/image%2012.jpg)

Apple's primary text color (the darkest one) is #1D1D1F, corresponding to a V of 12%.

![image.png](images/image%2013.jpg)

## Subtle Differences Between Hues Can't Be Ignored

🤓☝️I want a yellow. But thinking about it more carefully, every color in this little section of the hue wheel is yellow when taken on its own. Which yellow should I choose?

![image.png](images/image%2014.jpg)

Honestly, I've always thought greenish yellows have something of a sprouted potato about them, which makes me uncomfortable. So when choosing yellow, I usually pick one with a reddish hue.

![49 degrees](images/image%2015.jpg)

49 degrees

![59 degrees](images/image%2016.jpg)

59 degrees

A hue difference of 10 degrees already produces a fairly noticeable difference in visual impression. How we understand the feelings these hue differences create may be subjective, and takes experience in practice.

> The amount of perceptual difference produced by nearby numerical values varies across hues. I can't tell the difference between 236 and 246 degrees.
> 

![image.png](images/image%2017.jpg)

![image.png](images/image%2018.jpg)

# Color and Attention

Color has a lot to do with attention. Here are some rules of thumb:

- Elements whose colors contrast more strongly with the background are more likely to attract attention.
    - Contrast in hue
    - Contrast in value
    - Contrast in saturation
- Elements with high saturation and value are more likely to attract attention.

![image.png](images/image%2019.jpg)

![image.png](images/image%2020.jpg)

Even though Block is above it, Key is still more likely to catch the reader's eye first. Even though the image with the blue background is on the right, I think readers are still more likely to look at it first.

- For content that will be read for a long time, excessive saturation can tire readers' eyes. So for sections intended for sustained reading, such as article paragraphs, we usually choose a low-saturation background with a text color that doesn't contrast quite so strongly.

# The “Typographic Color” of Text

This section uses “typographic color” in the narrow sense of the visual weight or density of text. I hope it won't be confused with other meanings of “gray level,” the literal wording of the Chinese term, or taken too literally.

![MiSans at 150% line spacing: Normal on the left, Medium on the right](images/image%2021.jpg)

MiSans at 150% line spacing: Normal on the left, Medium on the right

These two passages might look similar in color, but they're actually quite different.

The text on the left is #00000, with a value of 0% and a *Normal* font weight. The text on the right is #464444, with a value of 27% and a *Medium* font weight. We can see that our perception of text color isn't determined by the text's color alone. It's also affected by the background color, font weight, typeface, letter spacing, line spacing, and so on. Anything that affects the form of the text will affect its “typographic color.”

![MiSans at 150% line spacing, Medium: #000000 on the left, #464444 on the right](images/image%2022.jpg)

MiSans at 150% line spacing, Medium: #000000 on the left, #464444 on the right

I think it's also easy to see intuitively that the more whitespace there is, the lighter the “typographic color” feels.

We can also see that the various factors behind “typographic color” affect one another. In the image above, the two passages have the same font weight, but the one on the left feels heavier. It's precisely because these factors interact in such complex ways that we use the abstract concept of “typographic color” to describe our intuitive impression of text's visual weight or density.

### When “Typographic Color” Matters

For long texts, we don't want the typographic color to be too dark, because that often means stronger contrast between text and background, causing visual fatigue during extended reading. On the other hand, excessively high contrast can actually make text harder to distinguish.

![The right side is easier to read, isn't it?](images/image%2023.jpg)

The right side is easier to read, isn't it?

Some rules of thumb:

- For long texts, I don't recommend going above Medium in font weight. Normal and Regular weights in a typeface are generally designed for reading and are good choices.
- If you use a very thin typeface, you can use a color that contrasts more strongly with the background. Conversely, if the typeface itself is fairly heavy, reduce the color contrast.

Looking at a design project as a whole, an element's typographic color also affects the visual center of gravity of the entire design. Elements whose typographic color contrasts more strongly with the background may more readily attract the reader's attention.

The idea of typographic color isn't limited to long texts, either. It also works for any densely repeated visual elements similar to long text, such as a background tiled with a pattern. We can also use it to describe the “visual weight or density” of elements that aren't densely repeated. But when considering those elements, we can think more precisely about weight, color, shape, and so on, so there isn't much need for a concept like typographic color.

### Reference

[The Type — 孔雀计划: An Approach to Chinese Typography](https://www.thetype.com/kongque/)

# The Colors on a Designer's Computer Aren't the Colors Readers See

When considering any design, we need to think about the conditions under which readers will see it. Color deserves particular attention. Here are a few examples:

- We're putting on a Nova meetup. The classroom equipment is a projector, and the lighting conditions are poor. The audience will see much lower saturation and considerably higher value.
- We're making a website. People will usually read it on a display, so the colors may be closer to what we see while creating it.
- We're making a trifold brochure that will be printed. Highly saturated colors in print may be far less intense to the eye than what we see on a display. This isn't just because it's printed in CMYK, but also because we see paper and pigments by reflected light.

Color is especially prone to discrepancies across different situations. Below are some tips for preventing unexpected discrepancies.

## Printing

- When designing something for print, it's best to use the CMYK color format from the moment you create the file. However, some graphics effects are only available in RGB space. If you need those, it's also fine to design in RGB and convert to CMYK before exporting. But be sure to convert to CMYK and check the colors before printing.
- For black in CMYK, I recommend using full K with all other values at 0. This means only black ink is used when printing black, which can prevent color bleeding caused by mixing different inks and also saves ink. Design software usually converts black to a mixture of CMY rather than full K when converting from RGB to CMYK, so keep an eye on this.

> Eye-catching design doesn't come from color alone. With limited time, focusing too much on how colors will look while neglecting improvements to other parts of the design can be more trouble than it's worth. Designers become reliable and avoid mistakes as they accumulate experience. When you don't yet have that much experience, do the best you can and find a balance.
> 

## References

[CMYK Color Model](https://zh.wikipedia.org/wiki/%E5%8D%B0%E5%88%B7%E5%9B%9B%E5%88%86%E8%89%B2%E6%A8%A1%E5%BC%8F)

[[Color Theory] A Look at the Differences Between RGB and CMYK Color Modes_bilibili](https://www.bilibili.com/video/BV1ui4y1277T?spm_id_from=333.788.recommend_more_video.3&vd_source=e722208f734b917399c8ea98ad032d88)

[[In-Depth Explainer] What Are a Monitor's Color Gamut and Color Accuracy? What Experiences Do They Affect?_bilibili](https://www.bilibili.com/video/BV1kk4y167rk/?spm_id_from=333.337.search-card.all.click&vd_source=e722208f734b917399c8ea98ad032d88)

# Building Experience with Color

> Practice is the most effective way: study and copy artists you like, copy photographs, sample colors from websites you like, think about the color values in photographs you like, and consider the relationships between colors you see in the real world. These are more helpful than most “color palette videos” and “improve your aesthetic sense” videos.
> 

[Public Work by Cosmos](https://public.work/)

[abdz - Have you given up on being inspired?](https://abduzeedo.com/index.php/)

[Brand Guidelines - Duolingo](https://design.duolingo.com/)

[Beautiful Free Images & Pictures | Unsplash](https://unsplash.com/)

## Random Color Palette Tools

> I've never used random color palette tools, but they might help some people.
> 

[RandomA11y - Endless collection of accessible color combos](https://randoma11y.com/apca?color=%23a76f4f)
