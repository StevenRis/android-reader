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

    public static String text(Document doc) {
        // Prefer the site's text container. Never save the whole page/body,
        // which would silently include navigation, advertising or a challenge.
        Element root = doc.selectFirst("div.text");
        if (root == null) root = doc.selectFirst("#booktext, .book-text, #book-text");
        List<Element> paragraphs = new ArrayList<>();
        if (root != null) {
            root = root.clone();
            root.select("script, style, noscript, iframe, form, .navigation, .pagination, .ads").remove();
            paragraphs.addAll(root.select("p"));
        } else {
            paragraphs.addAll(doc.select("p.MsoNormal"));
        }
        StringBuilder out = new StringBuilder();
        // Do not deduplicate actual paragraphs: deliberate repetitions are text.
        for (Element p : paragraphs) {
            if (!p.select("p").isEmpty()) {
                // select("p") includes p itself; ignore only actual nested p nodes.
                if (p.select("p").size() > 1) continue;
            }
            Element copy = p.clone();
            String text = copy.wholeText().replace('\u00a0', ' ').trim();
            if (!text.isEmpty()) out.append(text).append("\n\n");
        }
        if (out.length() == 0 && root != null) {
            out.append(root.wholeText().trim());
        }
        String result = out.toString().trim();
        if (result.length() < 40)
            throw new IllegalArgumentException("Не найден текст страницы. Возможно, сайт изменился или ограничил доступ.");
        return result;
    }
}
