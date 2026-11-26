import http from 'node:http'

const ADDRESSES: Record<string, {
  cep: string
  logradouro: string
  complemento: string
  bairro: string
  cidade: string
  uf: string
}> = {
  '74000000': {
    cep: '74000-000',
    logradouro: 'Avenida Goiás',
    complemento: 'Setor Central',
    bairro: 'Centro',
    cidade: 'Goiânia',
    uf: 'GO',
  },
  '01001000': {
    cep: '01001-000',
    logradouro: 'Praça da Sé',
    complemento: 'lado ímpar',
    bairro: 'Sé',
    cidade: 'São Paulo',
    uf: 'SP',
  },
}

export interface CepStubHandle {
  port: number
  close: () => Promise<void>
}

export async function startCepStubServer(
  port = Number(process.env.CEP_STUB_PORT ?? 18081),
  expectedApiKey = process.env.CEP_EXPECTED_API_KEY ?? 'e2e-cep-secret-key',
): Promise<CepStubHandle | null> {
  const server = http.createServer((req, res) => {
    const url = new URL(req.url ?? '/', `http://127.0.0.1:${port}`)

    if (req.method === 'GET' && url.pathname === '/health') {
      res.writeHead(200, { 'Content-Type': 'application/json' })
      res.end(JSON.stringify({ status: 'ok' }))
      return
    }

    const match = url.pathname.match(/^\/api\/v1\/ceps\/(\d{8})\/detalhes$/)
    if (req.method === 'GET' && match) {
      if (expectedApiKey) {
        const apiKeyHeader = req.headers['x-api-key']
        if (apiKeyHeader !== expectedApiKey) {
          res.writeHead(401, { 'Content-Type': 'application/json' })
          res.end(JSON.stringify({ erro: 'X-API-Key ausente ou inválida no gateway CEP' }))
          return
        }
      }

      const cep = match[1]
      const payload = ADDRESSES[cep]
      if (!payload) {
        res.writeHead(404, { 'Content-Type': 'application/json' })
        res.end(JSON.stringify({ erro: 'CEP não encontrado' }))
        return
      }

      res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' })
      res.end(JSON.stringify(payload))
      return
    }

    res.writeHead(404, { 'Content-Type': 'application/json' })
    res.end(JSON.stringify({ erro: 'Rota não encontrada no stub CEP' }))
  })

  return new Promise((resolve, reject) => {
    server.once('error', (err: NodeJS.ErrnoException) => {
      if (err.code === 'EADDRINUSE') {
        resolve(null)
        return
      }
      reject(err)
    })

    server.listen(port, '127.0.0.1', () => {
      resolve({
        port,
        close: () =>
          new Promise<void>((resClose, rejClose) => {
            server.close((closeErr) => (closeErr ? rejClose(closeErr) : resClose()))
          }),
      })
    })
  })
}
