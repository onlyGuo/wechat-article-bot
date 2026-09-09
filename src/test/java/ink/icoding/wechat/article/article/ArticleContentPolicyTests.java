package ink.icoding.wechat.article.article;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArticleContentPolicyTests {
    @Test
    void preservesLayoutAndSpacingAcrossSaveAndWechatFormatting() {
        String html = """
                <style>.card {display:grid;grid-template-columns:1fr 2fr;background:linear-gradient(red,blue)} @media(max-width:500px){.card{display:block}}</style><section id="story" class="card" data-layout="magazine" style="--accent:purple;padding:2rem"><div><p style="margin:0 0 37px;color:var(--accent)">正文</p><ul><li>列表</li></ul><dl><dt>词条</dt><dd>说明</dd></dl></div><table><tbody><tr><td colspan="2">表格</td></tr></tbody></table><svg viewBox="0 0 100 100"><circle cx="50" cy="50" r="40" fill="red"><animate attributeName="fill" values="red;blue" dur="2s"></animate></circle></svg></section>
                """.trim();
        String clean = ArticleContentPolicy.sanitize(html);
        var document = Jsoup.parseBodyFragment(clean);
        assertEquals(1, document.select("style").size());
        assertTrue(document.selectFirst("style").data().contains("@media"));
        assertEquals("magazine", document.selectFirst("section").attr("data-layout"));
        assertEquals("--accent:purple;padding:2rem", document.selectFirst("section").attr("style"));
        assertEquals("margin:0 0 37px;color:var(--accent)", document.selectFirst("p").attr("style"));
        assertEquals(1, document.select("ul,dl,table,svg").stream().filter(e -> e.tagName().equals("svg")).count());
        assertEquals(4, document.select("ul,dl,table,svg").size());
        assertEquals(1, document.select("svg animate").size());
        assertEquals(clean, ArticleContentPolicy.formatForWechat(clean));
        assertEquals(clean, ArticleContentPolicy.sanitize(clean));
    }

    @Test
    void removesExecutionWithoutStrippingPresentation() {
        String clean = ArticleContentPolicy.sanitize("""
                <style>body{color:purple}</style><script>alert(1)</script><iframe srcdoc="bad"></iframe>
                <a href="java&#x09;script:alert(1)" onclick="alert(1)">链接</a>
                <img src="/uploads/test.png" onerror="alert(1)" style="filter:grayscale(1)">
                <svg><a xlink:href="javascript:alert(1)">危险</a><set attributeName="href" to="javascript:alert(1)"></set></svg>
                """);
        var doc = Jsoup.parseBodyFragment(clean);
        assertTrue(doc.select("script,iframe,[onclick],[onerror],set").isEmpty());
        assertFalse(doc.selectFirst("a").hasAttr("href"));
        assertFalse(doc.selectFirst("svg a").hasAttr("xlink:href"));
        assertEquals("/uploads/test.png", doc.selectFirst("img").attr("src"));
        assertEquals("filter:grayscale(1)", doc.selectFirst("img").attr("style"));
        assertEquals(1, doc.select("style").size());
    }
}
