package ink.icoding.wechat.article.article;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.util.Locale;
import java.util.Set;

/** Content safety is independent of layout: preserve CSS, SVG and non-executable markup. */
public final class ArticleContentPolicy {
    private static final Set<String> URL_ATTRIBUTES = Set.of("href", "src", "xlink:href", "action", "formaction", "poster", "background");
    private ArticleContentPolicy() {}

    public static String sanitize(String html) {
        if (html == null || html.isBlank()) return "<p></p>";
        Document document = Jsoup.parseBodyFragment(html);
        document.outputSettings().prettyPrint(false);
        document.select("script,iframe,object,embed,base,meta,frame,frameset,applet").remove();
        for (Element element : document.getAllElements()) {
            String animatedAttribute = element.attr("attributeName").toLowerCase(Locale.ROOT);
            if (Set.of("animate", "set", "animatemotion", "animatetransform").contains(element.normalName())
                    && (URL_ATTRIBUTES.contains(animatedAttribute) || animatedAttribute.startsWith("on") || animatedAttribute.equals("srcdoc"))) {
                element.remove();
                continue;
            }
            for (var attribute : element.attributes().asList()) {
                String name = attribute.getKey().toLowerCase(Locale.ROOT);
                if (name.startsWith("on") || name.equals("srcdoc") || name.equals("contenteditable")
                        || (URL_ATTRIBUTES.contains(name) && !safeUrl(attribute.getValue()))) {
                    element.removeAttr(attribute.getKey());
                }
            }
        }
        return document.body().html();
    }

    private static boolean safeUrl(String value) {
        String normalized = value.replaceAll("[\\p{Cntrl}\\s]+", "").toLowerCase(Locale.ROOT);
        if (normalized.startsWith("data:")) return normalized.startsWith("data:image/");
        if (!normalized.matches("^[a-z][a-z0-9+.-]*:.*")) return true;
        int colon = normalized.indexOf(':');
        return Set.of("http", "https", "mailto", "tel").contains(normalized.substring(0, colon));
    }

    public static String formatForWechat(String html) {
        // WeChat may apply its own rendering rules; preserve the author's spacing and style here.
        return sanitize(html);
    }
}
