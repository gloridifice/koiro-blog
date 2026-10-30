package htmlgen.component

import kotlinx.html.*
import routing.PageContext
import routing.PageKey

class HomePagination(val pageNumber: Int, val totalElements: Int, val pageSize: Int) {
    init {
        require(pageSize > 0) { "Home page size must be positive" }
        require(totalElements >= 0) { "Home element count must not be negative" }
    }
    val pageCount = if (totalElements == 0) 1 else (totalElements - 1) / pageSize + 1
    init { require(pageNumber in 1..pageCount) { "Home page number is out of range: $pageNumber" } }
    val offset = (pageNumber - 1) * pageSize
    val firstItem = if (totalElements == 0) 0 else offset + 1
    val lastItem = offset + minOf(pageSize, totalElements - offset)
    val visiblePages: List<Int> = if (pageCount <= 7) (1..pageCount).toList() else
        sortedSetOf(1, pageCount, pageNumber - 1, pageNumber, pageNumber + 1).filter { it in 1..pageCount }
}

fun FlowContent.homePagination(pagination: HomePagination, context: PageContext) {
    if (pagination.pageCount == 1) return
    footer("home-pagination") {
        id = "home-pagination"
        nav("home-pagination__nav") {
            attributes["aria-label"] = context.text("pagination.label")
            paginationLink(pagination.pageNumber - 1, pagination.pageCount, true, context)
            ol("home-pagination__pages") {
                var lastPage = 0
                for (number in pagination.visiblePages) {
                    if (lastPage != 0 && number - lastPage > 1) li {
                        span("home-pagination__ellipsis") { attributes["aria-hidden"] = "true"; +"…" }
                    }
                    li {
                        if (number == pagination.pageNumber) span("home-pagination__control") {
                            attributes["aria-label"] = context.text("pagination.page", "page" to number)
                            attributes["aria-current"] = "page"; +number.toString()
                        } else a(context.routes.page(context.locale, PageKey.Home(number)).serverPath, classes = "home-pagination__control") {
                            attributes["aria-label"] = context.text("pagination.page", "page" to number); +number.toString()
                        }
                    }
                    lastPage = number
                }
            }
            paginationLink(pagination.pageNumber + 1, pagination.pageCount, false, context)
        }
        p("home-pagination__meta") {
            span { message(context, "pagination.summary", "first" to pagination.firstItem, "last" to pagination.lastItem, "total" to pagination.totalElements) }
            span { message(context, "pagination.position", "page" to pagination.pageNumber, "pages" to pagination.pageCount) }
        }
    }
}

private fun FlowContent.paginationLink(target: Int, count: Int, previous: Boolean, context: PageContext) {
    val label = context.text(if (previous) "pagination.previous" else "pagination.next")
    val content: FlowContent.() -> Unit = {
        if (previous) paginationArrow(true)
        span("home-pagination__label") { +label }
        if (!previous) paginationArrow(false)
    }
    if (target in 1..count) a(context.routes.page(context.locale, PageKey.Home(target)).serverPath, classes = "home-pagination__control") {
        rel = if (previous) "prev" else "next"; attributes["aria-label"] = label; content()
    } else span("home-pagination__control") { attributes["aria-label"] = label; attributes["aria-disabled"] = "true"; content() }
}

private fun FlowContent.paginationArrow(previous: Boolean) {
    svg {
        classes += "home-pagination__arrow"
        attributes["viewBox"] = "0 0 24 24"; attributes["aria-hidden"] = "true"
        attributes["stroke-width"] = "1.75"; attributes["stroke-linecap"] = "round"; attributes["stroke-linejoin"] = "round"
        val points = if (previous) "14 6 8 12 14 18" else "10 6 16 12 10 18"
        unsafe { +"""<polyline points="$points"></polyline>""" }
    }
}
