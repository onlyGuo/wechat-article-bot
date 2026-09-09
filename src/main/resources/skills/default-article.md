【公众号正文视觉模板】
新创作整篇文章或整篇重写时，正文必须采用下面的版式。局部修改已有文章时保持原有版式，不要为无关段落重排全文。

版式要求：
1. 文章标题放在标题字段中，正文不要机械重复主标题。正文依次由引言、导语、若干章节、配图/图注和收束语组成。
2. 开头用一段简短引言概括全文核心，视觉上使用浅灰文字和绿色左边线；随后用一个自然段承接正文。
3. 每章使用两位数字01、02、03……作为视觉章节号；章节号居中、绿色，下面有一条绿色短横线，再放居中的章节标题。这里的数字是章节装饰，不是编号列表。
4. 正文使用简洁自然段，字号16px、行高1.9、深灰色、段间距16px；不要使用ul、ol、dl、table，也不要写成条目清单。
5. 图片放在相关段落之后，宽度100%、高度自适应；需要说明时在图片下方使用居中的浅灰小字图注。图片必须来自素材工具返回的publicUrl，不得保留占位图片或外链图片。
6. 全部样式写在style内联属性中，不依赖class、style标签、脚本或外部CSS。绿色统一使用#07C160，正文颜色使用#333333，辅助文字使用#888888。
7. 章节通常为2至5个，数量由内容决定。最后用一句与主题相关的简短文字居中收束；不要照抄示例文案。

HTML结构示例（只参考结构与样式，必须根据实际主题替换所有文字、章节数量、图片和链接）：
<section style="margin:0 0 30px 0;">
  <blockquote style="margin:0;padding:0 0 0 14px;border-left:3px solid #07C160;color:#888888;font-size:15px;line-height:1.8;">“用一句话概括全文的核心内容。”</blockquote>
</section>
<p style="margin:0 0 16px 0;color:#333333;font-size:16px;line-height:1.9;text-align:justify;">正文内容从这里开始，用自然段完成导入。</p>
<section style="margin:42px 0 28px 0;text-align:center;">
  <div style="color:#07C160;font-size:20px;line-height:1.2;">01</div>
  <div style="width:18px;height:2px;margin:7px auto 16px auto;background:#07C160;"></div>
  <h2 style="margin:0;color:#222222;font-size:20px;font-weight:400;line-height:1.6;text-align:center;">章节标题</h2>
</section>
<p style="margin:0 0 16px 0;color:#333333;font-size:16px;line-height:1.9;text-align:justify;">本章正文使用连贯的自然段。</p>
<figure style="margin:24px 0 10px 0;">
  <img src="素材工具返回的publicUrl" alt="与正文有关的准确描述" style="display:block;width:100%;height:auto;margin:0;" />
  <figcaption style="margin-top:8px;color:#999999;font-size:13px;line-height:1.6;text-align:center;">必要时填写简短图注</figcaption>
</figure>
<p style="margin:0 0 16px 0;color:#555555;font-size:14px;line-height:1.8;">需要引用时，用自然段写“参考：来源名称”，并为来源名称添加链接。</p>
<section style="margin:48px 0 20px 0;text-align:center;">
  <div style="color:#999999;font-size:14px;line-height:1.8;">根据文章主题创作一句简短收束语</div>
</section>
