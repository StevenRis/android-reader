package com.stevenris.reader;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

public class LiveSiteTest {
    @Test public void verifiesSinglePageWithoutSavingBookText() throws Exception {
        assumeTrue("1".equals(System.getenv("LIVE_SITE_TEST")));
        Document doc = Jsoup.connect(BookParser.url(8466, 1))
                .userAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                .timeout(25000).maxBodySize(2 * 1024 * 1024).get();
        BookParser.Page first = BookParser.parse(doc);
        String text = first.text;
        assertFalse("First-page headings were lost", first.headings.isEmpty());
        int pages = BookParser.pageCount(doc, 8466);
        assertTrue("Book page count was not discovered: " + pages, pages > 1);
        assertTrue("Content container was not detected", text.length() > 1000);
        assertTrue("Wrong book heading", BookParser.title(doc).contains("Танатонавты"));
        assertFalse("Navigation was included", text.contains("Вернуться к просмотру книги"));
        Document second = Jsoup.connect(BookParser.url(8466, 2))
                .userAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                .timeout(25000).maxBodySize(2 * 1024 * 1024).get();
        BookParser.Page parsed = BookParser.parse(second);
        assertTrue("Chapter heading from the reported page is missing",
                parsed.headings.stream().anyMatch(h -> h.title.equals("9. Полицейское досье")));
        for (BookParser.Heading h : parsed.headings)
            assertEquals(h.title, parsed.text.substring(h.offset, h.offset + h.title.length()));
        System.out.println("Live parser check OK: pages=" + pages + ", extractedChars=" + text.length());
    }
}
