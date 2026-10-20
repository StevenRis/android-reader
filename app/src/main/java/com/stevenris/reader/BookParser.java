package com.stevenris.reader;

import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public final class BookParser {
    private BookParser() {}
    public static final int MAX_PAGES = 2000;

    public static int bookId(String input) {
        try {
            URI uri = URI.create(input.trim());
            String host = uri.getHost();
            if (host == null || !(host.equalsIgnoreCase("loveread.ec")
                    || host.equalsIgnoreCase("www.loveread.ec"))
                    || !("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getUserInfo() != null || uri.getPort() != -1
                    || !("/read_book.php".equals(uri.getPath())
                    || "/view_global.php".equals(uri.getPath()))) {
                throw new IllegalArgumentException();
            }
            int id = Integer.parseInt(param(uri.getRawQuery(), "id"));
            if (id < 1) throw new IllegalArgumentException();
            return id;
        } catch (Exception e) {
            throw new IllegalArgumentException("Вставьте ссылку на книгу с loveread.ec");
        }
    }

    private static String param(String query, String name) {
        if (query == null) return "";
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && pair[0].equals(name))
                {
                try { return URLDecoder.decode(pair[1], "UTF-8"); }
                catch (java.io.UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
            }
        }
        return "";
    }

    public static String url(int id, int page) {
        if (id < 1 || page < 1 || page > MAX_PAGES) throw new IllegalArgumentException();
        return "https://loveread.ec/read_book.php?id=" + id + "&p=" + page;
    }

    public static int pageCount(Document doc, int id) {
        int max = 1;
        for (Element link : doc.select("a[href]")) {
            try {
                URI uri = URI.create(link.absUrl("href"));
                if (bookId(uri.toString()) != id || !"/read_book.php".equals(uri.getPath())) continue;
                int page = Integer.parseInt(param(uri.getRawQuery(), "p"));
                if (page > MAX_PAGES) throw new PageLimitException();
                if (page > max) max = page;
            } catch (PageLimitException e) {
                throw new IllegalArgumentException("Слишком много страниц в книге");
            } catch (Exception ignored) {
                // Non-book navigation and malformed links are not pagination.
            }
        }
        return max;
    }

    private static final class PageLimitException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static String title(Document doc) {
        Element heading = doc.selectFirst("h1");
        String title = heading != null ? heading.text() : doc.title();
        title = title.replaceFirst("(?iu)^Книга\\s+", "")
                .replaceFirst("(?iu),?\\s*страница\\s+\\d+.*$", "")
                .replaceFirst("(?iu)\\s*[|–—-]\\s*читать.*$", "").trim();
        return title.isEmpty() ? "Книга" : title;
    }

    public static final int FORMAT_VERSION = 2;

    public static final class Heading {
        public final String title;
        public final int offset;
        public final int level;
        Heading(String title, int offset, int level) {
            this.title = title; this.offset = offset; this.level = level;
        }
    }

    public static final class Page {
        public final String text;
        public final List<Heading> headings;
        Page(String text, List<Heading> headings) {
            this.text = text; this.headings = headings;
        }
    }

    public static String text(Document doc) { return parse(doc).text; }

    public static Page parse(Document doc) {
        Element root = doc.selectFirst("div.text");
        if (root == null) root = doc.selectFirst("#booktext, .book-text, #book-text");
        if (root == null) {
            Element firstParagraph = doc.selectFirst("p.MsoNormal");
            if (firstParagraph != null) root = firstParagraph.parent();
        }
        if (root == null || root == doc.body() || root == doc)
            throw new IllegalArgumentException("Не найден текст страницы. Возможно, сайт изменился или ограничил доступ.");
        root = root.clone();
        root.select("script, style, noscript, iframe, form, nav, .navigation, .pagination, .ads").remove();
        StringBuilder out = new StringBuilder();
        List<Heading> headings = new ArrayList<>();
        append(root, out, headings);
        String result = out.toString().trim();
        if (result.length() < 40)
            throw new IllegalArgumentException("Не найден текст страницы. Возможно, сайт изменился или ограничил доступ.");
        // append() never adds leading whitespace, so trimming only affects the end.
        return new Page(result, headings);
    }

    private static void paragraph(StringBuilder out) {
        while (out.length() > 0 && Character.isWhitespace(out.charAt(out.length() - 1)))
            out.setLength(out.length() - 1);
        if (out.length() > 0) out.append("\n\n");
    }

    private static void append(org.jsoup.nodes.Node node, StringBuilder out, List<Heading> headings) {
        if (node instanceof org.jsoup.nodes.TextNode) {
            String text = ((org.jsoup.nodes.TextNode)node).getWholeText()
                    .replace('\u00a0', ' ').replaceAll("\\s+", " ");
            if (out.length() == 0 || out.charAt(out.length() - 1) == '\n')
                text = text.replaceFirst("^ +", "");
            out.append(text);
            return;
        }
        if (!(node instanceof Element)) return;
        Element element = (Element)node;
        String tag = element.normalName();
        int headingLevel = tag.matches("h[1-6]") ? tag.charAt(1) - '0' : 0;
        for (String cssClass : element.classNames())
            if (cssClass.matches("(?i)h[1-6]")) headingLevel = cssClass.charAt(1) - '0';
        if (headingLevel > 0) {
            String title = element.text().replace('\u00a0', ' ').trim();
            if (!title.isEmpty()) {
                paragraph(out);
                headings.add(new Heading(title, out.length(), headingLevel));
                out.append(title);
                paragraph(out);
            }
            return;
        }
        if (tag.equals("br")) {
            out.append('\n');
            return;
        }
        boolean block = tag.matches("p|div|section|article|blockquote|li|ul|ol|table|tr|td|pre|hr");
        if (block) paragraph(out);
        for (org.jsoup.nodes.Node child : element.childNodes()) append(child, out, headings);
        if (block) paragraph(out);
    }
}
