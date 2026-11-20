package com.lhacenmed.sona.core.model

/** What the browser searches with, for what is typed that is no address - by [title], each at its [queryUrl] the words follow. */
enum class SearchEngine(val title: String, val queryUrl: String) {
    BRAVE("Brave", "https://search.brave.com/search?q="),
    GOOGLE("Google", "https://www.google.com/search?q="),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    QWANT("Qwant", "https://www.qwant.com/?q="),
    BING("Bing", "https://www.bing.com/search?q="),
    STARTPAGE("Startpage", "https://www.startpage.com/do/search?q="),
    ECOSIA("Ecosia", "https://www.ecosia.org/search?q="),
    ;

    companion object {
        val Default = DUCKDUCKGO
    }
}
