/* Throwaway adapter, only for the self-authored, script-free fixture. */
(() => {
  function initialize() {
    if (window.spike) return;
    const nodes = [];
    const walk = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
      acceptNode(node) {
        return node.parentElement.closest('script,style,svg') ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT;
      }
    });
    while (walk.nextNode()) nodes.push(walk.currentNode);
    const originalText = nodes.map(n => n.data).join('');
    const images = Array.from(document.querySelectorAll('img,svg,[role="img"]')).filter(image =>
      image.matches('img,svg') || getComputedStyle(image).backgroundImage.startsWith('url('));
    const send = (type, data) => { if (window.SpikeBridge) SpikeBridge.event(JSON.stringify({type, data})); };
    const position = (node, offset) => ({node: nodes.indexOf(node), offset});
    const controls = new Map();
    let wordCandidate = null;
    const style = document.createElement('style');
    style.textContent = `html { background: #faf7f0; } body { margin: 0 !important; padding: 10px 31px 24px !important; }
      [data-spike-generated] { user-select: none; -webkit-user-select: none; }
      button[data-spike-generated] { display: block; box-sizing: border-box; width: 100%; min-height: 48px; margin: 16px 0; border: 1px solid currentColor; color: inherit; background: transparent; font: inherit; touch-action: manipulation; }
      #spike-indicator { position: fixed; right: 3px; top: 40%; border-right: 2px solid currentColor; height: 35px; pointer-events: none; opacity: 0; }
      ::highlight(spike-arrival) { background-color: #f6cc65; color: #111; }`;
    style.textContent += '::highlight(spike-word) { background-color: #b5d7ff; color: #111; }';
    document.head.appendChild(style);
    // Readium's user-style selectors outrank the sample body rule above.
    document.body.style.setProperty('margin', '0', 'important');
    document.body.style.setProperty('padding', '10px 31px 24px', 'important');
    images.forEach((image, index) => {
      if (image.getAttribute('role') === 'presentation' || image.getAttribute('alt') === '') {
        image.style.display = 'none';
        return;
      }
      const button = document.createElement('button');
      button.dataset.spikeGenerated = 'image';
      button.textContent = 'View image';
      const alt = image.getAttribute('alt') || image.getAttribute('aria-label') || image.querySelector('title')?.textContent || '';
      button.setAttribute('aria-label', 'View image: ' + (alt || 'Image'));
      const link = image.closest('a');
      (link || image).before(button);
      image.style.display = 'none';
      controls.set(index, button);
      button.addEventListener('click', event => {
        event.preventDefault(); event.stopPropagation();
        const data = {
          index, src: image.getAttribute('src'), alt,
          readingAnchor: firstVisible(), viewport: {width: innerWidth, height: innerHeight}, href: location.pathname,
          buttonRect: button.getBoundingClientRect().toJSON(), fontPixels: parseFloat(getComputedStyle(button).fontSize),
          caption: image.closest('figure')?.querySelector('figcaption')?.textContent || '',
          link: link?.getAttribute('href') || ''
        };
        if (image.matches('img') && !image.closest('picture') && !data.src.endsWith('.svg')) {
          send('image', data);
        } else {
          // A fixture rasterization seam for native inspection of SVG/responsive/CSS images.
          const raster = new Image();
          raster.onload = () => {
            const canvas = document.createElement('canvas');
            canvas.width = raster.naturalWidth; canvas.height = raster.naturalHeight;
            canvas.getContext('2d').drawImage(raster, 0, 0);
            send('image', {...data, resolvedSrc: image.matches('img') ? image.currentSrc : image.localName, png: canvas.toDataURL('image/png').split(',')[1]});
          };
          raster.onerror = () => send('image-unavailable', {index});
          const svg = image.matches('svg') ? image.cloneNode(true) : null;
          if (svg) svg.style.removeProperty('display');
          raster.src = svg ? 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(new XMLSerializer().serializeToString(svg))
            : image.matches('img') ? image.currentSrc || image.src
            : getComputedStyle(image).backgroundImage.slice(4, -1).replace(/^['"]|['"]$/g, '');
        }
      });
    });
    const indicator = document.createElement('span');
    indicator.id = 'spike-indicator'; indicator.dataset.spikeGenerated = 'indicator';
    indicator.setAttribute('aria-hidden', 'true'); document.body.append(indicator);
    let userScrolling = false, indicatorTimer, progressTimer, programmatic = false;
    let lastUserScroll = 0, indicatorHidden = 0;
    function recordUserScroll() {
      if (programmatic || !userScrolling) return;
      indicator.style.opacity = '1'; clearTimeout(indicatorTimer);
      lastUserScroll = Date.now();
      indicatorTimer = setTimeout(() => {
        indicator.style.opacity = '0'; userScrolling = false; indicatorHidden = Date.now(); send('indicator-hidden', indicatorHidden);
      }, 3000);
      send('user-scroll', lastUserScroll);
    }
    document.addEventListener('touchmove', () => { userScrolling = true; }, {passive: true});
    window.addEventListener('scroll', recordUserScroll);
    window.addEventListener('scroll', () => {
      clearTimeout(progressTimer);
      progressTimer = setTimeout(() => send('reading-progress', {href: location.pathname, anchor: firstVisible()}), 300);
      const index = window.spike?.nativeControlIndex;
      if (index != null && controls.has(index)) send('image-control-position', {href: location.pathname, rect: controls.get(index).getBoundingClientRect().toJSON()});
    });
    function rangeFor(anchor) {
      if (anchor.kind !== 'text' || !nodes[anchor.start.node] || !nodes[anchor.end.node]) throw Error('Unavailable original node');
      const range = document.createRange();
      range.setStart(nodes[anchor.start.node], anchor.start.offset);
      range.setEnd(nodes[anchor.end.node], anchor.end.offset);
      if (range.toString() !== anchor.quote) throw Error('Original quote mismatch');
      return range;
    }
    function selection() {
      const selected = getSelection();
      if (!selected.rangeCount || selected.isCollapsed) return null;
      const range = selected.getRangeAt(0);
      const start = position(range.startContainer, range.startOffset), end = position(range.endContainer, range.endOffset);
      if (start.node < 0 || end.node < 0) return {error: 'Generated content is not a Precise bookmark target'};
      return {kind: 'text', start, end, quote: range.toString()};
    }
    // Segment only original text within the selected block, including split inline markup.
    // This English/Unicode fixture rule is not a multilingual segmentation policy.
    function wordSelection(step = 0, anchor = selection()) {
      if (!anchor || anchor.error) return anchor;
      const block = nodes[anchor.start.node].parentElement.closest('p,figcaption,h1,h2,li') || document.body;
      const textNodes = nodes.filter(node => block.contains(node));
      const text = textNodes.map(node => node.data).join('');
      const start = textNodes.slice(0, textNodes.indexOf(nodes[anchor.start.node])).reduce((sum, node) => sum + node.length, 0) + anchor.start.offset;
      const words = Array.from(text.matchAll(/[\p{L}\p{N}][\p{L}\p{N}\p{M}\u00ad]*(?:['’\-\u2010\u2011][\p{L}\p{N}][\p{L}\p{N}\p{M}\u00ad]*)*/gu));
      const current = words.findIndex(word => word.index <= start && start < word.index + word[0].length);
      if (current < 0) return anchor;
      const word = words[Math.max(0, Math.min(words.length - 1, current + step))];
      let begin, end, cursor = 0;
      for (const node of textNodes) {
        if (!begin && word.index < cursor + node.length) begin = position(node, word.index - cursor);
        if (word.index + word[0].length <= cursor + node.length) { end = position(node, word.index + word[0].length - cursor); break; }
        cursor += node.length;
      }
      const resolved = {kind: 'text', start: begin, end, quote: word[0]};
      rangeFor(resolved); // Verify the original quote without disturbing native handles/menu.
      return resolved;
    }
    function restore(anchor, highlight = false) {
      userScrolling = false;
      programmatic = true;
      try {
        if (anchor.kind === 'image') {
          const button = controls.get(anchor.image);
          if (!button) throw Error('Unavailable original image');
          button.scrollIntoView({block: 'start'});
        } else {
          const range = rangeFor(anchor);
          const rect = range.getBoundingClientRect();
          window.scrollBy(0, rect.top - 10);
          if (highlight && CSS.highlights) {
            CSS.highlights.set('spike-arrival', new Highlight(range));
            setTimeout(() => CSS.highlights.delete('spike-arrival'), 1500);
          }
        }
        return snapshot(anchor);
      } finally { setTimeout(() => { programmatic = false; }, 100); }
    }
    function snapshot(anchor) {
      const rect = anchor.kind === 'image' ? controls.get(anchor.image).getBoundingClientRect() : rangeFor(anchor).getBoundingClientRect();
      return {anchor, top: rect.top, bottom: rect.bottom, visible: rect.bottom > 0 && rect.top < innerHeight, scrollY, innerWidth, innerHeight};
    }
    function firstVisible() {
      const candidates = [];
      controls.forEach((button, image) => {
        const rect = button.getBoundingClientRect();
        if (rect.bottom > 0 && rect.top < innerHeight) candidates.push({top: Math.max(0, rect.top), anchor: {kind: 'image', image}});
      });
      for (let node = 0; node < nodes.length; node++) {
        const text = nodes[node];
        if (!text.data.trim() || !text.parentElement.getClientRects().length) continue;
        const block = document.createRange(); block.selectNodeContents(text);
        if (!Array.from(block.getClientRects()).some(rect => rect.bottom > 0 && rect.top < innerHeight)) continue;
        for (let offset = 0; offset < text.length; offset++) {
          // Avoid splitting surrogate pairs; the anchor's offsets remain raw UTF-16 units.
          const length = text.data.codePointAt(offset) > 0xffff ? 2 : 1;
          const range = document.createRange(); range.setStart(text, offset); range.setEnd(text, offset + length);
          const rect = range.getBoundingClientRect();
          if (rect.width && rect.bottom > 0 && rect.top < innerHeight && text.data.slice(offset, offset + length).trim()) {
            candidates.push({top: Math.max(0, rect.top), anchor: {kind: 'text', start: {node, offset}, end: {node, offset: offset + length}, quote: range.toString()}});
            break;
          }
          if (length === 2) offset++;
        }
      }
      candidates.sort((a, b) => a.top - b.top);
      return candidates[0]?.anchor || null;
    }
    window.spike = {
      originalText, nodes, images, controls, selection, wordSelection, restore, firstVisible, snapshot,
      showWordCandidate(anchor) {
        if (anchor && !anchor.error && CSS.highlights) {
          CSS.highlights.set('spike-word', new Highlight(rangeFor(anchor)));
          wordCandidate = anchor;
        }
        return anchor;
      },
      clearWordCandidate() { CSS.highlights?.delete('spike-word'); wordCandidate = null; return null; },
      nativeControlIndex: null,
      setTheme(dark) {
        for (const element of [document.documentElement, document.body]) {
          element.style.setProperty('background-color', dark ? '#181c21' : '#faf7f0', 'important');
          element.style.setProperty('color', dark ? '#fff' : '#111', 'important');
        }
      },
      selectionContains(x, y) {
        const selected = getSelection();
        return selected.rangeCount > 0 && Array.from(selected.getRangeAt(0).getClientRects()).some(r => x >= r.left && x <= r.right && y >= r.top && y <= r.bottom);
      },
      select(id, quote, occurrence = 0) {
        const element = document.getElementById(id);
        const textNodes = nodes.filter(n => element.contains(n));
        let all = textNodes.map(n => n.data).join(''), start = -1;
        for (let i = 0; i <= occurrence; i++) start = all.indexOf(quote, start + 1);
        if (start < 0) throw Error('Fixture word unavailable');
        let begin, end, cursor = 0;
        for (const text of textNodes) {
          if (!begin && start < cursor + text.length) begin = {node: nodes.indexOf(text), offset: start - cursor};
          if (start + quote.length <= cursor + text.length) { end = {node: nodes.indexOf(text), offset: start + quote.length - cursor}; break; }
          cursor += text.length;
        }
        const anchor = {kind: 'text', start: begin, end, quote};
        const selected = getSelection(); selected.removeAllRanges(); selected.addRange(rangeFor(anchor));
        return selection();
      },
      state() {
        return {href: location.pathname, scrollY, height: document.documentElement.scrollHeight, viewport: innerHeight,
          originalLength: originalText.length, canonicalUnchanged: originalText === nodes.map(n => n.data).join(''),
          generatedLabelsInOriginal: originalText.includes('View image'), images: images.length, buttons: controls.size,
          bodyPadding: getComputedStyle(document.body).padding, indicator: indicator.style.opacity, lastUserScroll, indicatorHidden,
          fontPixels: parseFloat(getComputedStyle(document.querySelector('p') || document.body).fontSize),
          fontFamily: getComputedStyle(document.querySelector('p') || document.body).fontFamily,
          background: getComputedStyle(document.body).backgroundColor, ink: getComputedStyle(document.body).color,
          selected: selection(), wordCandidate, candidateHighlight: CSS.highlights?.has('spike-word') || false, highlight: CSS.highlights?.has('spike-arrival') || false};
      }
    };
    document.addEventListener('selectionchange', () => send('selection', selection()));
    send('ready', spike.state());
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initialize);
  else initialize();
})();
