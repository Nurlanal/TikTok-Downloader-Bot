'use strict';
const fs = require('node:fs');
const http = require('node:http');
const https = require('node:https');
const config = require('./config');
const { createApp } = require('./app');
const worker = require('./imap/worker');
const db = require('./db');

// Başlanğıcda açarın düzgün olduğunu yoxlayırıq
config.credentialsKey();

const app = createApp();
let server;
if (config.tls.certFile && config.tls.keyFile) {
  server = https.createServer({ cert: fs.readFileSync(config.tls.certFile), key: fs.readFileSync(config.tls.keyFile) }, app);
} else {
  // HTTPS tərs proksi (nginx/Caddy) arxasında işləmək üçün
  server = http.createServer(app);
}

server.listen(config.port, config.host, () => {
  const proto = config.tls.certFile ? 'https' : 'http';
  console.log(`[api] PayReport AZ API ${proto}://${config.host}:${config.port}/api`);
  worker.startScheduler();
});

async function shutdown() {
  worker.stopScheduler();
  server.close();
  await db.close();
  process.exit(0);
}
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
