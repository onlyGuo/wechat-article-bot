package ink.icoding.wechat.article.article;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArticleContentPolicyTests {
    @Test
    void addsSixteenPixelSpacingToEveryWechatParagraph() {
        String formatted = ArticleContentPolicy.formatForWechat(
                "<h2>标题</h2><p>第一段</p><p style=\"color: red\">第二段</p>");
        var paragraphs = Jsoup.parseBodyFragment(formatted).select("p");

        assertEquals(2, paragraphs.size());
        assertEquals("margin-bottom: 16px;", paragraphs.get(0).attr("style"));
        assertEquals("color: red; margin-bottom: 16px;", paragraphs.get(1).attr("style"));
    }

    @Test
    void acceptsNormalArticleParagraphsAndRejectsGeneratedListsOrTables() {
        assertDoesNotThrow(() -> ArticleContentPolicy.requireParagraphProse(
                "<h2>主题</h2><p>连续叙述的第一段。</p><p>连续叙述的第二段。</p>"));
        assertThrows(IllegalArgumentException.class,
                () -> ArticleContentPolicy.requireParagraphProse("<ul><li>列表内容</li></ul>"));
        assertThrows(IllegalArgumentException.class,
                () -> ArticleContentPolicy.requireParagraphProse("<table><tr><td>表格内容</td></tr></table>"));
    }
}
