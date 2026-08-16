// marked v18 — 全局配置：breaks + GFM + 代码块（语言标签栏 + hljs 高亮）
marked.use({
  breaks: true,
  gfm: true,
  renderer: {
    code({ text, lang }) {
      let highlighted;
      try {
        highlighted = lang && hljs.getLanguage(lang)
          ? hljs.highlight(text, { language: lang }).value
          : hljs.highlightAuto(text).value;
      } catch (e) {
        highlighted = hljs.highlightAuto(text).value;
      }
      var label = lang || 'code';
      return '<div class="code-wrap">'
        + '<div class="cp-bar"><span>' + label + '</span>'
        + '<button class="cp-btn" onclick="copyCode(this)">Copy</button></div>'
        + '<pre><code class="hljs">' + highlighted + '</code></pre></div>';
    }
  }
});

// 复制代码按钮（textarea fallback，兼容 JavaFX WebView 的 data: URI）
function copyCode(btn) {
  var code = btn.closest('.code-wrap').querySelector('code').textContent;
  var ta = document.createElement('textarea');
  ta.value = code;
  ta.style.position = 'fixed'; ta.style.left = '-9999px';
  document.body.appendChild(ta);
  ta.select();
  try { document.execCommand('copy'); } catch(e) {}
  document.body.removeChild(ta);
  btn.textContent = 'Copied!';
  setTimeout(function() { btn.textContent = 'Copy'; }, 1500);
}

// 全局选中文本后按 Ctrl+C / Cmd+C 复制（data: URI 下 clipboard API 不可用）
document.addEventListener('keydown', function(e) {
  if ((e.ctrlKey || e.metaKey) && e.key === 'c') {
    var sel = window.getSelection();
    if (sel && sel.toString()) {
      var ta = document.createElement('textarea');
      ta.value = sel.toString();
      ta.style.position = 'fixed'; ta.style.left = '-9999px';
      document.body.appendChild(ta);
      ta.select();
      try { document.execCommand('copy'); } catch(ex) {}
      document.body.removeChild(ta);
    }
  }
});
