package com.stevenris.reader;

import static org.junit.Assert.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

public class BookParserTest {
    private Document doc(String html) {
        return Jsoup.parse(html, BookParser.url(8466, 1));
    }

    @Test public void acceptsBookAndCatalogLinks() {
        assertEquals(8466, BookParser.bookId("https://loveread.ec/read_book.php?id=8466&p=9"));
        assertEquals(8466, BookParser.bookId("http://www.loveread.ec/view_global.php?id=8466"));
    }
    @Test public void rejectsUntrustedHostsAndInvalidIds() {
        for (String url : new String[] {
                "https://loveread.ec.evil.org/read_book.php?id=8466",
                "https://evil.org/read_book.php?id=8466",
                "https://loveread.ec@evil.org/read_book.php?id=8466",
                "https://loveread.ec/read_book.php?id=0",
                "https://loveread.ec:123/read_book.php?id=1",
                "javascript:alert(1)" }) {
            try { BookParser.bookId(url); fail(url); }
            catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void followsOnlyPaginationForSameBook() {
        Document d = doc("<a href='read_book.php?id=8466&p=131'>131</a>"
                + "<a href='read_book.php?id=999&p=500'>Other book</a>"
                + "<a href='https://evil.org/read_book.php?id=8466&p=900'>Bad</a>");
        assertEquals(131, BookParser.pageCount(d, 8466));
    }
    @Test public void extractsParagraphsWithoutNavigationOrScripts() {
        String paragraph = "Это вымышленный абзац для проверки программы. Он не является текстом книги.";
        Document d = doc("<nav>Реклама и меню</nav><div class='text'><p>"
                + paragraph + "</p><p>Вторая строка<br>И её продолжение.</p>"
                + "<script>alert('junk')</script></div><footer>Подвал сайта</footer>");
        String text = BookParser.text(d);
        assertTrue(text.contains(paragraph));
        assertTrue(text.contains("Вторая строка"));
        assertFalse(text.contains("Реклама"));
        assertFalse(text.contains("alert"));
        assertFalse(text.contains("Подвал"));
    }
    @Test public void rejectsChallengePagesInsteadOfSavingThem() {
        try { BookParser.text(doc("<body><h1>Just a moment</h1>Enable JavaScript</body>")); fail(); }
        catch (IllegalArgumentException expected) { }
    }
    @Test public void preservesRepeatedParagraphs() {
        String p = "Это повторяющийся учебный абзац достаточной длины, созданный для тестирования.";
        String result = BookParser.text(doc("<div class='text'><p>" + p + "</p><p>" + p + "</p></div>"));
        assertEquals(p + "\n\n" + p, result);
    }
    @Test public void stripsPageNumberFromTitle() {
        assertEquals("Тестовая книга", BookParser.title(doc("<h1>Книга Тестовая книга, страница 1 – Автор</h1>")));
    }
}
