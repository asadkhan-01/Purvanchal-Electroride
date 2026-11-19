#!/usr/bin/env node
// Development-only same-origin proxy: serves the Expo web preview and the Spring Boot API
// from one origin, so a forwarded/preview URL works without CORS or a public API port.
//   /backend/*  -> http://127.0.0.1:8080/*   (Spring Boot)
//   everything else -> http://127.0.0.1:8081 (Expo web)
// Use with apps/mobile/.env.local: EXPO_PUBLIC_API_URL=/backend
import http from 'node:http';

const port = Number(process.env.PROXY_PORT || 3000);
const api = { host: '127.0.0.1', port: Number(process.env.API_PORT || 8080) };
const web = { host: '127.0.0.1', port: Number(process.env.WEB_PORT || 8081) };
const prefix = '/backend';

http.createServer((req, res) => {
  const toApi = req.url === prefix || req.url.startsWith(prefix + '/');
  const target = toApi ? api : web;
  const path = toApi ? req.url.slice(prefix.length) || '/' : req.url;
  const headers = { ...req.headers, host: `${target.host}:${target.port}` };
  if (toApi) delete headers.origin; // same-origin from the browser's view
  const upstream = http.request({ ...target, path, method: req.method, headers }, (up) => {
    res.writeHead(up.statusCode ?? 502, up.headers);
    up.pipe(res);
  });
  upstream.on('error', (e) => { res.writeHead(502); res.end(`Upstream ${toApi ? 'API' : 'Expo'} unavailable: ${e.message}`); });
  req.pipe(upstream);
}).listen(port, () => console.log(`Dev proxy on http://localhost:${port} (API under ${prefix})`));
