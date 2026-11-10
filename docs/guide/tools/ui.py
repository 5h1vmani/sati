"""Reads a uiautomator dump. 'list' prints each labelled element; 'find LABEL' prints the centre of the last match."""
import html, re, sys
nodes = re.findall(r'<node [^>]*>', open(sys.argv[1]).read())
def attr(n, k):
    m = re.search(k + r'="([^"]*)"', n)
    return html.unescape(m.group(1)) if m else ''
def label(n):
    return attr(n, ' text') or attr(n, 'content-desc')
if sys.argv[2] == 'list':
    for n in nodes:
        if label(n): print(repr(label(n)), attr(n, 'bounds'))
else:
    for n in reversed(nodes):
        if label(n) == sys.argv[3]:
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', attr(n, 'bounds')))
            print((x1 + x2) // 2, (y1 + y2) // 2); break
