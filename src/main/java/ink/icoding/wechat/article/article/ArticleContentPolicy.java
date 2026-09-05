package ink.icoding.wechat.article.article;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public final class ArticleContentPolicy {
    private static final String FORBIDDEN_GENERATED_ELEMENTS = "ul, ol, dl, table";
    private static final String WECHAT_PARAGRAPH_SPACING = "margin-bottom: 16px;";

    private ArticleContentPolicy() {
    }

    public static void requireParagraphProse(String html) {
        if (html == null || html.isBlank()) return;
        Document document = Jsoup.parseBodyFragment(html);
        if (!document.select(FORBIDDEN_GENERATED_ELEMENTS).isEmpty()) {
            throw new IllegalArgumentException("文章正文不要使用列表或表格，请改用标题和自然段连续表达");
        }
    }

    public static String formatForWechat(String html) {
        if (html == null || html.isBlank()) return "<p style=\"margin-bottom: 16px;\"></p>";
        Document document = Jsoup.parseBodyFragment(html);
        document.outputSettings().prettyPrint(false);
        for (Element paragraph : document.select("p")) {
            String style = paragraph.attr("style").trim();
            if (!style.isEmpty() && !style.endsWith(";")) style += ";";
            paragraph.attr("style", (style.isEmpty() ? "" : style + " ") + WECHAT_PARAGRAPH_SPACING);
        }
        return document.body().html();
    }
}
