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
    @Test public void keepsHeadingsAndParagraphsInDocumentOrder() {
        Document d = doc("<div class='text'><p>Первый учебный абзац достаточной длины для проверки.</p>"
                + "<h2><a name='9'>9. Тестовая глава</a></h2>"
                + "<p>Следующий учебный абзац с <em>выделением</em> и продолжением.</p>"
                + "<h3>10. Следующая глава</h3><blockquote>Цитата для теста.</blockquote></div>");
        BookParser.Page page = BookParser.parse(d);
        assertEquals(2, page.headings.size());
        assertEquals("9. Тестовая глава", page.headings.get(0).title);
        assertTrue(page.text.indexOf("Первый") < page.text.indexOf("9. Тестовая глава"));
        assertTrue(page.text.indexOf("9. Тестовая глава") < page.text.indexOf("Следующий"));
        assertTrue(page.text.contains("с выделением и продолжением."));
        for (BookParser.Heading heading : page.headings) {
            assertEquals(heading.title, page.text.substring(heading.offset,
                    heading.offset + heading.title.length()));
            assertEquals(page.text.indexOf(heading.title), heading.offset);
        }
        assertEquals(2, page.headings.get(0).level);
        assertEquals(3, page.headings.get(1).level);
    }

    @Test public void keepsLooseTextAndLineBreaksAlongsideParagraphs() {
        String result = BookParser.text(doc("<div class='text'>"
                + "Вводный текст вне абзаца, который нельзя потерять."
                + "<h2>Заголовок</h2><p>Первая строка<br>Вторая строка</p>"
                + "<div>Конец текста вне абзацев.</div></div>"));
        assertTrue(result.contains("Вводный текст"));
        assertTrue(result.contains("Заголовок"));
        assertTrue(result.contains("Первая строка\nВторая строка"));
        assertTrue(result.contains("Конец текста"));
    }

    @Test public void allHeadingLevelsAreRetainedAndInlineMarkupDoesNotDuplicateText() {
        String html = "<div class='text'>";
        for (int i = 1; i <= 6; i++)
            html += "<h" + i + ">Раздел " + i + "</h" + i + "><p>Учебный абзац " + i + ".</p>";
        html += "</div>";
        BookParser.Page page = BookParser.parse(doc(html));
        assertEquals(6, page.headings.size());
        assertTrue(page.text.contains("Раздел 6"));
        assertEquals(1, page.text.split("Раздел 1", -1).length - 1);
    }
    @Test public void recognizesLovereadCssHeadings() {
        BookParser.Page page = BookParser.parse(doc("<div class='text'>"
                + "<div class='h2'>9. Тестовая глава</div>"
                + "<p>Учебный абзац достаточной длины для проверки импорта.</p>"
                + "<div class='h3 extra'>10. Следующая глава</div><p>Продолжение текста.</p></div>"));
        assertEquals(2, page.headings.size());
        assertEquals("9. Тестовая глава", page.headings.get(0).title);
        assertEquals(2, page.headings.get(0).level);
        assertEquals(3, page.headings.get(1).level);
    }
}
