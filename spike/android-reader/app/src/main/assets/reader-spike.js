/* Throwaway adapter, only for the self-authored, script-free fixture. */
(() => {
  function initialize() {
    if (window.spike) return;
    const nodes = [];
    const walk = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
      acceptNode(node) {
        return node.parentElement.closest('script,style') ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT;
      }
    });
    while (walk.nextNode()) nodes.push(walk.currentNode);
    const originalText = nodes.map(n => n.data).join('');
    const images = Array.from(document.images);
    const send = (type, data) => { if (window.SpikeBridge) SpikeBridge.event(JSON.stringify({type, data})); };
    const position = (node, offset) => ({node: nodes.indexOf(node), offset});
    const controls = new Map();
    const style = document.createElement('style');
    style.textContent = `html { background: #faf7f0; } body { margin: 0 !important; padding: 10px 31px 24px !important; }
      [data-spike-generated] { user-select: none; -webkit-user-select: none; }
      button[data-spike-generated] { display: block; box-sizing: border-box; width: 100%; min-height: 48px; margin: 16px 0; border: 1px solid currentColor; color: inherit; background: transparent; font: inherit; }
      #spike-indicator { position: fixed; right: 3px; top: 40%; border-right: 2px solid currentColor; height: 35px; pointer-events: none; opacity: 0; }
      ::highlight(spike-arrival) { background-color: #f6cc65; color: #111; }`;
    document.head.appendChild(style);
    images.forEach((image, index) => {
      if (image.getAttribute('role') === 'presentation' || image.getAttribute('alt') === '') {
        image.style.display = 'none';
        return;
      }
      const button = document.createElement('button');
      button.dataset.spikeGenerated = 'image';
      button.textContent = 'View image';
      button.setAttribute('aria-label', 'View image: ' + (image.alt || 'Image'));
      image.before(button);
      image.style.display = 'none';
      controls.set(index, button);
      button.addEventListener('click', event => {
        event.preventDefault(); event.stopPropagation();
        send('image', {
          index, src: image.getAttribute('src'), alt: image.alt || '',
          caption: image.closest('figure')?.querySelector('figcaption')?.textContent || '',
          link: image.closest('a')?.getAttribute('href') || ''
        });
      });
    });
    const indicator = document.createElement('span');
    indicator.id = 'spike-indicator'; indicator.dataset.spikeGenerated = 'indicator';
    indicator.setAttribute('aria-hidden', 'true'); document.body.append(indicator);
    let userScrollUntil = 0, indicatorTimer, programmatic = false;
    document.addEventListener('touchmove', () => { userScrollUntil = Date.now() + 500; }, {passive: true});
    window.addEventListener('scroll', () => {
      if (programmatic || Date.now() > userScrollUntil) return;
      indicator.style.opacity = '1'; clearTimeout(indicatorTimer);
      indicatorTimer = setTimeout(() => { indicator.style.opacity = '0'; send('indicator-hidden', Date.now()); }, 3000);
      send('user-scroll', Date.now());
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
    function restore(anchor, highlight = false) {
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
      originalText, nodes, images, controls, selection, restore, firstVisible, snapshot,
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
          bodyPadding: getComputedStyle(document.body).padding, indicator: indicator.style.opacity,
          selected: selection(), highlight: CSS.highlights?.has('spike-arrival') || false};
      }
    };
    document.addEventListener('selectionchange', () => send('selection', selection()));
    send('ready', spike.state());
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initialize);
  else initialize();
})();
