import { defineConfig, devices } from '@playwright/test'
import net from 'node:net'
import path from 'node:path'

const CEP_STUB_PORT = process.env.CEP_STUB_PORT ?? '18081'
const CEP_API_KEY = process.env.CEP_EXPECTED_API_KEY ?? 'e2e-cep-secret-key'
const ADMIN_LOGIN = process.env.E2E_ADMIN_LOGIN ?? 'admin@vollmed.com'
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'admin123'
const DB_HOST = process.env.DB_HOST ?? '127.0.0.1'
const DB_PORT = Number(process.env.DB_PORT ?? 3307)

function canConnectTcp(host: string, port: number, timeoutMs = 800): Promise<boolean> {
  return new Promise((resolve) => {
    const socket = new net.Socket()
    const finish = (ok: boolean) => {
      socket.destroy()
      resolve(ok)
    }
    socket.setTimeout(timeoutMs)
    socket.once('connect', () => finish(true))
    socket.once('timeout', () => finish(false))
    socket.once('error', () => finish(false))
    socket.connect(port, host)
  })
}

const forcedDbMode = process.env.E2E_DB_MODE // 'mysql' | 'h2' | undefined
const mysqlReachable = forcedDbMode === 'mysql'
  ? true
  : forcedDbMode === 'h2'
    ? false
    : await canConnectTcp(DB_HOST, DB_PORT)

const isWindows = process.platform === 'win32'
const mvnCmd = isWindows ? '.\\mvnw.cmd' : './mvnw'
const backendDir = path.resolve(import.meta.dirname, '../backend')

const backendCommand = mysqlReachable
  ? `${mvnCmd} --batch-mode spring-boot:run`
  : `${mvnCmd} --batch-mode test-compile spring-boot:test-run`

const backendEnv: Record<string, string> = {
  ...(process.env as Record<string, string>),
  JWT_SECRET: process.env.JWT_SECRET ?? 'e2e-only-secret-key-with-at-least-32-characters',
  ADMIN_LOGIN,
  ADMIN_PASSWORD,
  CEP_API_BASE_URL: `http://127.0.0.1:${CEP_STUB_PORT}`,
  CEP_API_KEY,
  RATE_LIMIT_MAX_REQUESTS: process.env.RATE_LIMIT_MAX_REQUESTS ?? '200',
  // No modo H2 (test-run) o application.properties de teste sobrepõe o do main,
  // então a propriedade final também é informada diretamente.
  API_SECURITY_RATE_LIMIT_MAX_TENTATIVAS: process.env.RATE_LIMIT_MAX_REQUESTS ?? '200',
  SPRINGDOC_SWAGGER_UI_ENABLED: 'true',
  SPRINGDOC_API_DOCS_ENABLED: 'true',
  ...(mysqlReachable
    ? {
        DB_HOST,
        DB_PORT: String(DB_PORT),
        DB_NAME: process.env.DB_NAME ?? 'vollmed_api',
        DB_USER: process.env.DB_USER ?? 'root',
        DB_PASSWORD: process.env.DB_PASSWORD ?? 'root',
      }
    : {}),
}

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  timeout: 60_000,
  expect: {
    timeout: 10_000,
  },
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : [['list'], ['html', { open: 'never' }]],
  globalSetup: './e2e/global-setup.ts',
  globalTeardown: './e2e/global-teardown.ts',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: [
    {
      command: backendCommand,
      cwd: backendDir,
      url: 'http://localhost:8080/swagger-ui.html',
      timeout: 180_000,
      reuseExistingServer: !process.env.CI,
      env: backendEnv,
    },
    {
      command: 'npm run dev -- --port 5173 --strictPort',
      cwd: import.meta.dirname,
      url: 'http://localhost:5173',
      timeout: 60_000,
      reuseExistingServer: !process.env.CI,
    },
  ],
})
