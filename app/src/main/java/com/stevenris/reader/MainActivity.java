package com.stevenris.reader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public final class MainActivity extends Activity {
    private SharedPreferences prefs;
    private BookStore books;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile boolean downloading;
    private volatile boolean destroyed;
    private boolean night;
    private int font;
    private int bookId;
    private int page = 1;
    private int total = 1;
    private String bookTitle = "";
    private LinearLayout root;
    private ScrollView readingScroll;
    private TextView body;
    private TextView counter;
    private TextView downloadStatus;
    private String status = "";
    private Button previous;
    private Button next;
    private float restoreFraction;
    private int bg;
    private int ink;
    private int muted;
    private int card;
    private static final int ACCENT = Color.rgb(146, 105, 61);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("reader", MODE_PRIVATE);
        books = new BookStore(this);
        night = prefs.getBoolean("night", false);
        font = prefs.getInt("font", 21);
        int last = prefs.getInt("lastBook", 0);
        if (last > 0) {
            try {
                books.meta(last);
                openBook(last);
                return;
            } catch (Exception ignored) { }
        }
        library();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @SuppressWarnings("deprecation")
    private void base() {
        bg = Color.parseColor(night ? "#171B1A" : "#F6F0E5");
        ink = Color.parseColor(night ? "#E8E0D2" : "#302D27");
        muted = Color.parseColor(night ? "#B6B0A5" : "#756F64");
        card = Color.parseColor(night ? "#252A27" : "#FFFAF1");
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(night ? 0 :
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setPadding(dp(16), dp(8), dp(16), dp(8));
        setContentView(root);
        readingScroll = null;
        body = null;
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(0, dp(6), 0, dp(6));
        return view;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private Button button(String text, View.OnClickListener click) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTextColor(ink);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(46));
        button.setMinimumHeight(dp(46));
        button.setPadding(dp(10), dp(4), dp(10), dp(4));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(card);
        shape.setCornerRadius(dp(12));
        button.setBackground(shape);
        button.setOnClickListener(click);
        return button;
    }

    private void flex(LinearLayout row, View view) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
        lp.setMargins(dp(2), dp(3), dp(2), dp(3));
        row.addView(view, lp);
    }

    private void library() {
        savePosition();
        bookId = 0;
        base();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout heading = row();
        TextView title = label("Тихий ридер", 28, ink);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        heading.addView(button(night ? "Свет" : "Ночь", v -> toggleTheme()));
        root.addView(heading);
        root.addView(label("Твоя библиотека · без лишнего", 14, muted));

        EditText link = new EditText(this);
        link.setTextColor(ink);
        link.setHintTextColor(muted);
        link.setTextSize(15);
        link.setSingleLine(true);
        link.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        link.setHint("Ссылка на книгу с loveread.ec");
        link.setText(prefs.getString("link", ""));
        root.addView(link, new LinearLayout.LayoutParams(-1, dp(58)));
        LinearLayout controls = row();
        Button download = button(downloading ? "Загружается…" : "Скачать книгу", v -> {
            String url = link.getText().toString();
            try {
                int id = BookParser.bookId(url);
                prefs.edit().putString("link", url).apply();
                ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE))
                        .hideSoftInputFromWindow(link.getWindowToken(), 0);
                download(id);
            } catch (Exception e) { message(e.getMessage()); }
        });
        download.setEnabled(!downloading);
        flex(controls, download);
        Button cancel = button("Остановить", v -> {
            cancelled.set(true);
            updateStatus("Останавливаю… Скачанные страницы сохранятся.");
        });
        cancel.setEnabled(downloading);
        flex(controls, cancel);
        root.addView(controls);
        downloadStatus = label(status.isEmpty()
                ? "Загрузка идёт, пока приложение открыто. Читать скачанное можно без сети."
                : status, 14, muted);
        downloadStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        root.addView(downloadStatus);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        List<JSONObject> saved = books.books();
        if (saved.isEmpty()) {
            TextView empty = label("Пока здесь тихо.\nДобавь первую книгу по ссылке выше.", 22, muted);
            empty.setTypeface(Typeface.SERIF);
            empty.setPadding(0, dp(44), 0, dp(24));
            list.addView(empty);
        }
        for (JSONObject meta : saved) {
            int id = meta.optInt("id");
            int count = meta.optInt("total", 1);
            int available = books.downloaded(id, count);
            LinearLayout entry = new LinearLayout(this);
            entry.setOrientation(LinearLayout.VERTICAL);
            entry.setPadding(dp(14), dp(12), dp(14), dp(12));
            GradientDrawable shape = new GradientDrawable();
            shape.setColor(card);
            shape.setCornerRadius(dp(16));
            entry.setBackground(shape);
            TextView name = label(meta.optString("title"), 21, ink);
            name.setTypeface(Typeface.SERIF, Typeface.BOLD);
            entry.addView(name);
            entry.addView(label(available == count ? "Скачано полностью · " + count + " стр."
                    : "Скачано " + available + " из " + count + " стр.", 13, muted));
            LinearLayout buttons = row();
            Button read = button("Читать", v -> openBook(id));
            read.setEnabled(available > 0);
            flex(buttons, read);
            if (available < count) {
                Button resume = button("Докачать", v -> download(id));
                resume.setEnabled(!downloading);
                flex(buttons, resume);
            }
            Button remove = button("Удалить", v -> new AlertDialog.Builder(this)
                    .setTitle("Удалить книгу с телефона?")
                    .setMessage(meta.optString("title"))
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Удалить", (dialog, which) -> {
                        books.delete(id);
                        prefs.edit().remove("page_" + id).remove("scroll_" + id).apply();
                        if (prefs.getInt("lastBook", 0) == id) prefs.edit().remove("lastBook").apply();
                        library();
                    }).show());
            remove.setEnabled(!downloading);
            flex(buttons, remove);
            entry.addView(buttons);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, dp(12), 0, dp(4));
            list.addView(entry, lp);
        }
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void openBook(int id) {
        try {
            JSONObject meta = books.meta(id);
            bookId = id;
            total = meta.getInt("total");
            bookTitle = meta.getString("title");
            page = Math.min(total, Math.max(1, prefs.getInt("page_" + id, 1)));
            restoreFraction = prefs.getFloat("scroll_" + id, 0);
            if (!books.hasPage(id, page)) {
                for (int p = 1; p <= total; p++) if (books.hasPage(id, p)) {
                    page = p; restoreFraction = 0; break;
                }
            }
            String text = books.page(id, page);
            base();
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            LinearLayout tools = row();
            flex(tools, button("Книги", v -> library()));
            flex(tools, button("A−", v -> changeFont(-1)));
            flex(tools, button("A+", v -> changeFont(1)));
            flex(tools, button(night ? "Свет" : "Ночь", v -> toggleTheme()));
            root.addView(tools);
            TextView title = label(bookTitle, 14, muted);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            root.addView(title);
            readingScroll = new ScrollView(this);
            readingScroll.setFillViewport(true);
            body = label(text, font, ink);
            body.setTypeface(Typeface.SERIF);
            body.setLineSpacing(dp(5), 1.12f);
            body.setPadding(dp(4), dp(18), dp(4), dp(40));
            body.setTextIsSelectable(true);
            readingScroll.addView(body, new ScrollView.LayoutParams(-1, -2));
            root.addView(readingScroll, new LinearLayout.LayoutParams(-1, 0, 1));
            LinearLayout navigation = row();
            previous = button("←", v -> move(-1));
            previous.setContentDescription("Предыдущая страница сайта");
            next = button("→", v -> move(1));
            next.setContentDescription("Следующая страница сайта");
            counter = label("", 15, muted);
            counter.setGravity(Gravity.CENTER);
            counter.setOnClickListener(v -> choosePage());
            flex(navigation, previous);
            navigation.addView(counter, new LinearLayout.LayoutParams(0, dp(48), 2));
            flex(navigation, next);
            root.addView(navigation);
            root.addView(label("Номер — страница сайта. Нажми для перехода.", 11, muted));
            updateNavigation();
            restoreScroll();
            prefs.edit().putInt("lastBook", id).apply();
        } catch (Exception e) {
            bookId = 0;
            library();
            message("Не удалось открыть книгу. Попробуйте докачать её.");
        }
    }

    private void updateNavigation() {
        counter.setText(page + " / " + total);
        previous.setEnabled(page > 1);
        next.setEnabled(page < total);
    }

    private void move(int delta) { goTo(page + delta); }

    private void goTo(int destination) {
        if (destination < 1 || destination > total) return;
        if (!books.hasPage(bookId, destination)) {
            message("Эта страница ещё не скачана. Вернитесь в библиотеку и нажмите «Докачать».");
            return;
        }
        try {
            String text = books.page(bookId, destination);
            savePosition();
            page = destination;
            body.setText(text);
            readingScroll.scrollTo(0, 0);
            updateNavigation();
            prefs.edit().putInt("page_" + bookId, page).putFloat("scroll_" + bookId, 0).apply();
        } catch (Exception e) { message("Не удалось прочитать страницу."); }
    }

    private void choosePage() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(Integer.toString(page));
        input.selectAll();
        new AlertDialog.Builder(this).setTitle("Страница сайта: 1–" + total).setView(input)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Перейти", (dialog, which) -> {
                    try {
                        int requested = Integer.parseInt(input.getText().toString());
                        if (requested < 1 || requested > total) throw new NumberFormatException();
                        goTo(requested);
                    } catch (NumberFormatException e) { message("Введите число от 1 до " + total); }
                }).show();
    }

    private void savePosition() {
        if (bookId == 0 || readingScroll == null || body == null) return;
        int range = Math.max(1, body.getHeight() - readingScroll.getHeight());
        float fraction = Math.max(0, Math.min(1, (float)readingScroll.getScrollY() / range));
        prefs.edit().putInt("page_" + bookId, page).putFloat("scroll_" + bookId, fraction).apply();
    }

    private void restoreScroll() {
        ScrollView target = readingScroll;
        TextView text = body;
        float fraction = restoreFraction;
        target.post(() -> {
            if (readingScroll != target) return;
            int range = Math.max(0, text.getHeight() - target.getHeight());
            target.scrollTo(0, Math.round(range * fraction));
        });
    }

    private void changeFont(int delta) {
        savePosition();
        restoreFraction = prefs.getFloat("scroll_" + bookId, 0);
        font = Math.max(14, Math.min(36, font + delta));
        prefs.edit().putInt("font", font).apply();
        body.setTextSize(font);
        restoreScroll();
    }

    private void toggleTheme() {
        savePosition();
        night = !night;
        prefs.edit().putBoolean("night", night).apply();
        if (bookId > 0) openBook(bookId); else library();
    }

    private void updateStatus(String text) {
        status = text;
        if (!destroyed && bookId == 0 && downloadStatus != null) downloadStatus.setText(text);
    }

    private Document fetchPage(int id, int pageNumber) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < 3 && !cancelled.get(); attempt++) {
            try {
                return Jsoup.connect(BookParser.url(id, pageNumber))
                        .userAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                        .timeout(25000).maxBodySize(2 * 1024 * 1024).get();
            } catch (org.jsoup.HttpStatusException e) {
                if (e.getStatusCode() == 403 || e.getStatusCode() == 429)
                    throw new Exception("Сайт ограничил доступ (" + e.getStatusCode()
                            + "). Попробуйте позже или откройте сайт в браузере.");
                last = e;
            } catch (Exception e) { last = e; }
            if (attempt < 2) Thread.sleep(1500L * (attempt + 1));
        }
        if (cancelled.get()) throw new InterruptedException();
        throw last != null ? last : new Exception("Не удалось загрузить страницу");
    }

    private void download(int id) {
        if (downloading) { message("Дождитесь завершения текущей загрузки."); return; }
        downloading = true;
        cancelled.set(false);
        updateStatus("Проверяю книгу…");
        library();
        worker.execute(() -> {
            String completion;
            try {
                Document first = fetchPage(id, 1);
                String firstText = BookParser.text(first);
                String title = BookParser.title(first);
                int pages = BookParser.pageCount(first, id);
                // A pagination regression must not silently mark a partial book complete.
                try {
                    JSONObject old = books.meta(id);
                    if (old.optInt("total") > pages)
                        throw new IllegalStateException("Изменилось число страниц. Загрузка остановлена, сохранённая книга не изменена.");
                } catch (java.io.FileNotFoundException ignored) { }
                books.saveMeta(id, title, pages);
                books.savePage(id, 1, firstText);
                int saved = books.downloaded(id, pages);
                final int count = pages;
                final int initialSaved = saved;
                runOnUiThread(() -> updateStatus("Скачано " + initialSaved + " из " + count + " стр."));
                for (int p = 2; p <= pages && !cancelled.get(); p++) {
                    if (books.hasPage(id, p)) continue;
                    Thread.sleep(800);
                    if (cancelled.get()) break;
                    Document doc = fetchPage(id, p);
                    String content = BookParser.text(doc);
                    // A redirect to the first page must not masquerade as a later page.
                    if (content.equals(firstText))
                        throw new Exception("Сайт вернул первую страницу вместо страницы " + p + ". Попробуйте докачать позже.");
                    books.savePage(id, p, content);
                    saved++;
                    final int done = saved;
                    runOnUiThread(() -> updateStatus("Скачано " + done + " из " + count
                            + " стр. · можно остановить и продолжить позже"));
                }
                int available = books.downloaded(id, pages);
                completion = available == pages ? "Готово: «" + title + "». Можно читать без интернета."
                        : "Остановлено. Сохранено " + available + " из " + pages + " стр. Нажмите «Докачать».";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                completion = "Загрузка остановлена. Скачанное сохранено.";
            } catch (Exception e) {
                String reason = e.getMessage();
                completion = "Загрузка прервана: " + (reason == null ? e.getClass().getSimpleName() : reason)
                        + "\nСкачанное сохранено. Для продолжения нажмите «Докачать».";
            }
            final String done = completion;
            runOnUiThread(() -> {
                if (destroyed) return;
                downloading = false;
                updateStatus(done);
                if (bookId == 0) library(); else message(done);
            });
        });
    }

    private void message(String text) {
        if (!destroyed) new AlertDialog.Builder(this).setMessage(text == null ? "Ошибка" : text)
                .setPositiveButton("Понятно", null).show();
    }

    @Override protected void onPause() { savePosition(); super.onPause(); }

    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() {
        if (bookId > 0) library(); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        savePosition();
        destroyed = true;
        cancelled.set(true);
        worker.shutdownNow();
        super.onDestroy();
    }
}
