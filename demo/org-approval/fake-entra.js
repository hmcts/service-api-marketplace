// A stand-in for Entra's signing keys and token issuing, for the offline check only (local-check.sh). It signs
// tokens that look like an Entra External ID ID token, with a key it makes up when it starts, and publishes the
// public half at /keys so the service can verify them exactly as it would Microsoft's. Nothing here talks to
// Microsoft. Usage: node fake-entra.js <port>
const http = require('http')
const crypto = require('crypto')

const port = Number(process.argv[2] || 8095)
const { publicKey, privateKey } = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 })
const jwk = { ...publicKey.export({ format: 'jwk' }), kid: 'fake-key', use: 'sig', alg: 'RS256' }
const encode = (value) => Buffer.from(typeof value === 'string' ? value : JSON.stringify(value)).toString('base64url')

function sign (claims) {
  const input = encode({ alg: 'RS256', typ: 'JWT', kid: 'fake-key' }) + '.' + encode(claims)
  return input + '.' + crypto.sign('RSA-SHA256', Buffer.from(input), privateKey).toString('base64url')
}

http.createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost')
  res.setHeader('Content-Type', 'application/json')
  if (url.pathname === '/keys') return res.end(JSON.stringify({ keys: [jwk] }))
  if (url.pathname === '/token') {
    const q = (name, fallback) => url.searchParams.get(name) || fallback
    const now = Math.floor(Date.now() / 1000)
    const tenant = q('tid', 'd44f885c-4fac-47bf-afde-d7d861ec4d7b')
    const claims = {
      iss: q('iss', 'http://localhost:' + port + '/issuer'),
      aud: q('aud', 'demo-client'),
      tid: tenant,
      oid: q('oid', crypto.randomUUID()),
      sub: 'per-app-subject',
      name: q('name', ''),
      email: q('email', ''),
      iat: now,
      exp: now + Number(q('ttl', '600'))
    }
    return res.end(JSON.stringify({ token: sign(claims) }))
  }
  res.statusCode = 404
  res.end('{}')
}).listen(port, '127.0.0.1', () => console.log('fake entra on ' + port))
