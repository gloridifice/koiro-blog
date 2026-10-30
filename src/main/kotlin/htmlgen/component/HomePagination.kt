package htmlgen.component

import ServerPath
import kotlinx.html.*

class HomePagination(val pageNumber: Int, val totalElements: Int, val pageSize: Int) {
    init {
        require(pageSize > 0) { "Home page size must be positive" }
        require(totalElements >= 0) { "Home element count must not be negative" }
    }

    val pageCount = if (totalElements == 0) 1 else (totalElements - 1) / pageSize + 1

    init {
        require(pageNumber in 1..pageCount) { "Home page number is out of range: $pageNumber" }
    }

    val offset = (pageNumber - 1) * pageSize
    val firstItem = if (totalElements == 0) 0 else offset + 1
    val lastItem = offset + minOf(pageSize, totalElements - offset)
    val serverPath = path(pageNumber)

    // Bound the navigation size as the archive grows; links remain ordinary HTML links.
    val visiblePages: List<Int> = if (pageCount <= 7) (1..pageCount).toList() else
        sortedSetOf(1, pageCount, pageNumber - 1, pageNumber, pageNumber + 1)
            .filter { it in 1..pageCount }

    companion object {
        fun path(pageNumber: Int): ServerPath {
            require(pageNumber > 0) { "Home page number must be positive" }
            return ServerPath(if (pageNumber == 1) "home.html" else "home-$pageNumber.html")
        }
    }
}

fun FlowContent.homePagination(pagination: HomePagination) {
    if (pagination.pageCount == 1) return

    footer(classes = "home-pagination") {
        id = "home-pagination"
        nav(classes = "home-pagination__nav") {
            attributes["aria-label"] = "主页分页"
            paginationLink(pagination.pageNumber - 1, pagination.pageCount, previous = true)
            ol(classes = "home-pagination__pages") {
                var lastPage = 0
                for (number in pagination.visiblePages) {
                    if (lastPage != 0 && number - lastPage > 1) {
                        li {
                            span(classes = "home-pagination__ellipsis") {
                                attributes["aria-hidden"] = "true"
                                +"…"
                            }
                        }
                    }
                    li {
                        if (number == pagination.pageNumber) {
                            span(classes = "home-pagination__control") {
                                attributes["aria-label"] = "第 $number 页"
                                attributes["aria-current"] = "page"
                                +number.toString()
                            }
                        } else {
                            a(href = HomePagination.path(number).serverPath, classes = "home-pagination__control") {
                                attributes["aria-label"] = "第 $number 页"
                                +number.toString()
                            }
                        }
                    }
                    lastPage = number
                }
            }
            paginationLink(pagination.pageNumber + 1, pagination.pageCount, previous = false)
        }
        p(classes = "home-pagination__meta") {
            span { +"第 ${pagination.firstItem}–${pagination.lastItem} 条，共 ${pagination.totalElements} 条" }
            span { +"第 ${pagination.pageNumber} / ${pagination.pageCount} 页" }
        }
    }
}

private fun FlowContent.paginationLink(targetPage: Int, pageCount: Int, previous: Boolean) {
    val label = if (previous) "上一页" else "下一页"
    val content: FlowContent.() -> Unit = {
        if (previous) paginationArrow(previous)
        span(classes = "home-pagination__label") { +label }
        if (!previous) paginationArrow(previous)
    }
    if (targetPage in 1..pageCount) {
        a(href = HomePagination.path(targetPage).serverPath, classes = "home-pagination__control") {
            rel = if (previous) "prev" else "next"
            attributes["aria-label"] = label
            content()
        }
    } else {
        span(classes = "home-pagination__control") {
            attributes["aria-label"] = label
            attributes["aria-disabled"] = "true"
            content()
        }
    }
}

private fun FlowContent.paginationArrow(previous: Boolean) {
    svg {
        classes += "home-pagination__arrow"
        attributes["viewBox"] = "0 0 24 24"
        attributes["aria-hidden"] = "true"
        attributes["stroke-width"] = "1.75"
        attributes["stroke-linecap"] = "round"
        attributes["stroke-linejoin"] = "round"
        val points = if (previous) "14 6 8 12 14 18" else "10 6 16 12 10 18"
        unsafe { +"""<polyline points="$points"></polyline>""" }
    }
}
