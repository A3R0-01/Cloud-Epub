// Throwaway local preview. No dependencies or saved state.
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const file = path.join(__dirname, 'reader-layout.prototype.html');
http.createServer((req, res) => {
  const route = new URL(req.url, 'http://localhost').pathname;
  if (route !== '/' && route !== '/Docs/prototypes/reader-layout.prototype.html') {
    res.writeHead(404).end('Not found');
    return;
  }
  res.writeHead(200, {'Content-Type': 'text/html; charset=utf-8'});
  res.end(fs.readFileSync(file));
}).listen(8766, '127.0.0.1', () => console.log('Reader prototype: http://127.0.0.1:8766/?variant=A'));
