import { expect, test } from '@playwright/test'

const ADMIN_LOGIN = process.env.E2E_ADMIN_LOGIN ?? 'admin@cliniara.com'
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'admin123'

test.describe('Autenticação e Guardas de Perfil (RBAC)', () => {
  test('deve rejeitar credenciais inválidas exibindo mensagem clara na tela de login', async ({ page }) => {
    await page.goto('/login')

    await page.locator('#login').fill(ADMIN_LOGIN)
    await page.locator('#senha').fill('senha-incorreta-999')
    await page.getByRole('button', { name: 'Entrar' }).click()

    await expect(page).toHaveURL(/\/login$/)
    await expect(page.getByText('Credenciais inválidas. Verifique seu login e senha.')).toBeVisible()
  })

  test('ROLE_ADMIN deve ser direcionado para /users, ver apenas navegação permitida e encerrar sessão', async ({ page }) => {
    await page.goto('/login')

    await page.locator('#login').fill(ADMIN_LOGIN)
    await page.locator('#senha').fill(ADMIN_PASSWORD)
    await page.getByRole('button', { name: 'Entrar' }).click()

    await expect(page).toHaveURL(/\/users$/)
    await expect(page.getByRole('heading', { name: 'Usuários' })).toBeVisible()

    const sidebarNav = page.locator('aside nav')
    await expect(sidebarNav.getByRole('link', { name: 'Usuários' })).toBeVisible()
    await expect(sidebarNav.getByRole('link', { name: 'Médicos' })).toHaveCount(0)
    await expect(sidebarNav.getByRole('link', { name: 'Prontuários' })).toHaveCount(0)
    await expect(sidebarNav.getByRole('link', { name: 'IA clínica' })).toHaveCount(0)

    // Tentativa de acesso direto a rota operacional deve redirecionar de volta para /users
    await page.goto('/doctors')
    await expect(page).toHaveURL(/\/users$/)

    await page.getByRole('button', { name: 'Sair' }).click()
    await expect(page).toHaveURL(/\/login$/)
  })
})
