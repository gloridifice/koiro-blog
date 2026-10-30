package htmlgen.component

import content.ResolvedContent
import htmlgen.unsafeSVG
import kotlinx.html.*
import kotlinx.serialization.json.*
import routing.PageContext

const val LANGUAGE_ICON = """<svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="m5 8 6 6M4 14l6-6 2-3M2 5h12M7 2h1m14 20-5-10-5 10M14 18h6"/></svg>"""
private const val GITHUB_ICON = """<svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 19c-4.3 1.3-4.3-2.2-6-2.7M15 22v-3.9a3.4 3.4 0 0 0-.9-2.7c3-.3 6.2-1.5 6.2-6.9A5.4 5.4 0 0 0 18.8 4.8 5 5 0 0 0 18.7 1s-1.2-.4-3.9 1.5a13.3 13.3 0 0 0-7 0C5.1.6 3.9 1 3.9 1a5 5 0 0 0-.1 3.8A5.4 5.4 0 0 0 2.3 8.5c0 5.4 3.2 6.6 6.2 6.9a3.4 3.4 0 0 0-.9 2.7V22" transform="translate(1 0) scale(.94 1)"/></svg>"""
private const val ITCH_ICON = """<svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M3 7 5 3h14l2 4M3 7h18v1a2.25 2.25 0 0 1-4.5 0 2.25 2.25 0 0 1-4.5 0 2.25 2.25 0 0 1-4.5 0A2.25 2.25 0 0 1 3 8V7ZM4 11v9a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-9"/><path d="M9 13h6c1 0 1.5.7 1.8 1.7l.7 3.1c.2.9-.8 1.5-1.5.9L14.5 17h-5L8 18.7c-.7.6-1.7 0-1.5-.9l.7-3.1C7.5 13.7 8 13 9 13ZM9 14.5v2m-1-1h2m4.5 0h.01"/></svg>"""

fun FlowContent.message(context: PageContext, key: String, vararg args: Pair<String, Any>) {
    val message = context.message(key, *args)
    span { lang = message.actualLocale.tag; +message.text }
}

fun FlowContent.languageTools(context: PageContext, social: Boolean = false) {
    div("sidebar-tools") {
        attributes["role"] = "group"
        attributes["aria-label"] = context.text("site.socialLinks")
        if (social) for ((name, url, icon) in listOf(
            Triple("GitHub", "https://github.com/gloridifice", GITHUB_ICON),
            Triple("itch.io", "https://gloridifice.itch.io/", ITCH_ICON))) {
            a(url, target = "_blank", classes = "sidebar-tool") {
                rel = "noopener noreferrer"; title = name; attributes["aria-label"] = name; unsafeSVG(icon)
            }
        }
        button(type = ButtonType.button, classes = "sidebar-tool language-trigger") {
            attributes["aria-controls"] = "site-language-menu"
            attributes["aria-expanded"] = "false"
            attributes["aria-label"] = context.text("site.chooseLanguage")
            title = context.text("site.chooseLanguage")
            unsafeSVG(LANGUAGE_ICON)
        }
        div("language-menu") {
            id = "site-language-menu"
            attributes["aria-label"] = context.text("site.chooseLanguage")
            p("language-caption") { message(context, "site.chooseLanguage") }
            for (locale in context.config.locales) a(context.routes.page(locale, context.page).serverPath, classes = "language-choice") {
                attributes["data-locale"] = locale.tag
                if (locale == context.locale) attributes["aria-current"] = "true"
                span { lang = locale.tag; +locale.name }
                span("language-code") { +locale.tag }
                span("language-check") { attributes["aria-hidden"] = "true"; +if (locale == context.locale) "✓" else "" }
            }
        }
    }
}

fun FlowContent.languageConfiguration(context: PageContext, root: Boolean = false) {
    val json = buildJsonObject {
        put("root", root); put("default", context.config.defaultLocale.tag)
        put("locales", buildJsonArray {
            for (locale in context.config.locales) add(buildJsonObject {
                put("tag", locale.tag); put("language", locale.language)
                locale.script?.let { put("script", it) }; put("matchDefault", locale.matchDefault)
                put("url", context.routes.page(locale, context.page).serverPath)
            })
        })
    }.toString().replace("<", "\\u003c").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
    script(type = "application/json") { id = "site-language-config"; unsafe { +json } }
    script { src = "/assets/js/site-language.js"; defer = true }
}

fun translationMessages(content: ResolvedContent, context: PageContext, kind: String = "article"): List<Pair<Boolean, i18n.MessageText>> = buildList {
    val actual = context.languageName(content.variant.localeTag)
    if (content.fallback) add(true to context.message("translation.${kind}Fallback",
        "requestedLanguage" to context.languageName(content.requested.tag), "contentLanguage" to actual))
    if (content.variant.header.aiTranslation) add(false to context.message("translation.${kind}Ai", "contentLanguage" to actual))
}

fun FlowContent.translationLines(content: ResolvedContent, context: PageContext, kind: String = "article") {
    for ((fallback, text) in translationMessages(content, context, kind)) p("translation-line" + if (fallback) " translation-fallback" else "") {
        lang = text.actualLocale.tag
        unsafeSVG(LANGUAGE_ICON)
        span { +text.text }
    }
}

fun FlowContent.translationMarker(content: ResolvedContent, context: PageContext) {
    if (!content.fallback && !content.variant.header.aiTranslation) return
    val notices = translationMessages(content, context)
    val description = notices.joinToString(" ") { it.second.text }
    span("translation-marker" + if (content.fallback) " translation-fallback" else "") {
        lang = notices.first().second.actualLocale.tag
        title = description; attributes["role"] = "img"; attributes["aria-label"] = description
        unsafeSVG(LANGUAGE_ICON)
    }
}
