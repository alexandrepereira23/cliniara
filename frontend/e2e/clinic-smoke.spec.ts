import { expect, test, type Page } from '@playwright/test'
import { createClinicRunData } from './support/testData'

const ADMIN_LOGIN = process.env.E2E_ADMIN_LOGIN ?? 'admin@vollmed.com'
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'admin123'

async function loginAs(page: Page, login: string, senha: string) {
  await page.goto('/login')
  await page.evaluate(() => window.localStorage.clear())
  await page.goto('/login')
  await page.locator('#login').fill(login)
  await page.locator('#senha').fill(senha)
  await page.getByRole('button', { name: 'Entrar' }).click()
}

test.describe.serial('Jornada Clínica Smoke E2E (Usuários, CEP, Disponibilidade e Consulta)', () => {
  const run = createClinicRunData()

  test('1. ROLE_ADMIN cadastra novo usuário operacional ROLE_FUNCIONARIO', async ({ page }) => {
    await loginAs(page, ADMIN_LOGIN, ADMIN_PASSWORD)
    await expect(page).toHaveURL(/\/users$/)

    await page.getByRole('button', { name: 'Novo usuário' }).first().click()
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible()

    await dialog.locator('#login').fill(run.funcionarioLogin)
    await dialog.locator('#senha').fill(run.funcionarioSenha)
    await dialog.locator('#confirmarSenha').fill(run.funcionarioSenha)

    await dialog.locator('#role').click()
    await page.getByRole('option', { name: 'Funcionário', exact: true }).click()

    await dialog.getByRole('button', { name: 'Cadastrar' }).click()
    await expect(dialog).toBeHidden()
    await expect(page.getByText('Usuário cadastrado com sucesso')).toBeVisible()
  })

  test('2. ROLE_FUNCIONARIO navega pela clínica, cria especialidade e cadastra médico com busca de CEP', async ({ page }) => {
    await loginAs(page, run.funcionarioLogin, run.funcionarioSenha)
    await expect(page).toHaveURL(/\/$/)

    const sidebarNav = page.locator('aside nav')
    await expect(sidebarNav.getByRole('link', { name: 'Médicos' })).toBeVisible()
    await expect(sidebarNav.getByRole('link', { name: 'Pacientes' })).toBeVisible()
    await expect(sidebarNav.getByRole('link', { name: 'Consultas' })).toBeVisible()
    await expect(sidebarNav.getByRole('link', { name: 'Prescrições' })).toBeVisible()
    await expect(sidebarNav.getByRole('link', { name: 'Atestados' })).toBeVisible()
    await expect(sidebarNav.getByRole('link', { name: 'Prontuários' })).toHaveCount(0)
    await expect(sidebarNav.getByRole('link', { name: 'Usuários' })).toHaveCount(0)
    await expect(sidebarNav.getByRole('link', { name: 'Auditoria LGPD' })).toHaveCount(0)
    await expect(sidebarNav.getByRole('link', { name: 'IA clínica' })).toHaveCount(0)

    // Garante especialidade exclusiva para esta execução
    await sidebarNav.getByRole('link', { name: 'Especialidades' }).click()
    await expect(page).toHaveURL(/\/specialties$/)
    await page.getByRole('button', { name: 'Nova especialidade' }).first().click()

    const specDialog = page.getByRole('dialog')
    await specDialog.locator('#nome').fill(run.especialidadeNome)
    await specDialog.getByRole('button', { name: 'Cadastrar' }).click()
    await expect(specDialog).toBeHidden()
    await expect(page.getByText('Especialidade cadastrada')).toBeVisible()

    // Cadastra médico com autopreenchimento via gateway backend de CEP
    await sidebarNav.getByRole('link', { name: 'Médicos' }).click()
    await expect(page).toHaveURL(/\/doctors$/)
    await page.getByRole('button', { name: 'Novo médico' }).first().click()

    const docDialog = page.getByRole('dialog')
    await docDialog.locator('#nome').fill(run.medicoNome)
    await docDialog.locator('#email').fill(run.medicoEmail)
    await docDialog.locator('#telefone').fill('62999991122')
    await docDialog.locator('#crm').fill(run.medicoCrm)

    await docDialog.locator('#especialidade').click()
    await page.getByRole('option', { name: run.especialidadeNome, exact: true }).click()

    await docDialog.locator('#cep').fill('74000000')
    await docDialog.getByRole('button', { name: 'Buscar CEP' }).click()

    await expect(docDialog.locator('#logradouro')).toHaveValue('Avenida Goiás')
    await expect(docDialog.locator('#bairro')).toHaveValue('Centro')
    await expect(docDialog.locator('#cidade')).toHaveValue('Goiânia')
    await expect(docDialog.locator('#uf')).toHaveValue('GO')
    await expect(docDialog.locator('#complemento')).toHaveValue('Setor Central')
    await docDialog.locator('#numero').fill('1000')

    await docDialog.getByRole('button', { name: 'Cadastrar' }).click()
    await expect(docDialog).toBeHidden()
    await expect(page.getByText('Médico cadastrado com sucesso')).toBeVisible()
  })

  test('3. ROLE_FUNCIONARIO registra disponibilidade do médico e cadastra paciente com busca de CEP', async ({ page }) => {
    await loginAs(page, run.funcionarioLogin, run.funcionarioSenha)
    await expect(page).toHaveURL(/\/$/)

    const sidebarNav = page.locator('aside nav')

    // Cadastra disponibilidade para o dia útil calculado
    await sidebarNav.getByRole('link', { name: 'Disponibilidade' }).click()
    await expect(page).toHaveURL(/\/availability$/)

    await page.getByRole('combobox').first().click()
    await page.getByRole('option', { name: new RegExp(run.medicoNome) }).click()

    await page.getByRole('button', { name: 'Novo horário' }).first().click()
    const dispDialog = page.getByRole('dialog')
    await dispDialog.getByRole('combobox').click()
    await page.getByRole('option', { name: run.slot.diaLabel, exact: true }).click()
    await dispDialog.locator('#horaInicio').fill('08:00')
    await dispDialog.locator('#horaFim').fill('18:00')
    await dispDialog.getByRole('button', { name: 'Cadastrar' }).click()

    await expect(dispDialog).toBeHidden()
    await expect(page.getByRole('cell', { name: run.slot.diaLabel })).toBeVisible()

    // Cadastra paciente com busca de CEP
    await sidebarNav.getByRole('link', { name: 'Pacientes' }).click()
    await expect(page).toHaveURL(/\/patients$/)
    await page.getByRole('button', { name: 'Novo paciente' }).first().click()

    const patDialog = page.getByRole('dialog')
    await patDialog.locator('#nome').fill(run.pacienteNome)
    await patDialog.locator('#email').fill(run.pacienteEmail)
    await patDialog.locator('#telefone').fill('62988883344')
    await patDialog.locator('#cpf').fill(run.pacienteCpf)

    await patDialog.locator('#cep').fill('74000000')
    await patDialog.getByRole('button', { name: 'Buscar CEP' }).click()

    await expect(patDialog.locator('#logradouro')).toHaveValue('Avenida Goiás')
    await expect(patDialog.locator('#bairro')).toHaveValue('Centro')
    await expect(patDialog.locator('#cidade')).toHaveValue('Goiânia')
    await expect(patDialog.locator('#uf')).toHaveValue('GO')
    await expect(patDialog.locator('#complemento')).toHaveValue('Setor Central')
    await patDialog.locator('#numero').fill('250')

    await patDialog.getByRole('button', { name: 'Cadastrar' }).click()
    await expect(patDialog).toBeHidden()
    await expect(page.getByText('Paciente cadastrado com sucesso')).toBeVisible()
  })

  test('4. ROLE_FUNCIONARIO valida bloqueio fora da disponibilidade e agenda consulta em horário válido', async ({ page }) => {
    await loginAs(page, run.funcionarioLogin, run.funcionarioSenha)
    await expect(page).toHaveURL(/\/$/)

    await page.locator('aside nav').getByRole('link', { name: 'Consultas' }).click()
    await expect(page).toHaveURL(/\/appointments$/)

    await page.getByRole('button', { name: 'Nova consulta' }).first().click()
    const apptDialog = page.getByRole('dialog')

    await apptDialog.locator('#idPaciente').click()
    await page.getByRole('option', { name: new RegExp(run.pacienteNome) }).click()

    await apptDialog.locator('#idMedico').click()
    await page.getByRole('option', { name: new RegExp(run.medicoNome) }).click()

    await apptDialog.locator('#dateInput').fill(run.slot.dateInput)

    // 07:00 está dentro do funcionamento da clínica (07h-19h), mas fora da disponibilidade do médico (08h-18h)
    await apptDialog.locator('#timeInput').fill('07:00')
    await apptDialog.getByRole('button', { name: 'Agendar Consulta' }).click()
    await expect(page.getByText(/Médico não possui disponibilidade cadastrada/i)).toBeVisible()
    await expect(apptDialog).toBeVisible()

    // Ajusta para 10:00 (dentro da disponibilidade) e confirma o agendamento
    await apptDialog.locator('#timeInput').fill('10:00')
    await apptDialog.getByRole('button', { name: 'Agendar Consulta' }).click()

    await expect(apptDialog).toBeHidden()
    await expect(page.getByText('Consulta agendada com sucesso')).toBeVisible()
  })

  test('5. ROLE_ADMIN vincula usuário médico e ROLE_MEDICO visualiza sua agenda e menu de IA clínica', async ({ page }) => {
    await loginAs(page, ADMIN_LOGIN, ADMIN_PASSWORD)
    await expect(page).toHaveURL(/\/users$/)

    await page.getByRole('button', { name: 'Novo usuário' }).first().click()
    const userDialog = page.getByRole('dialog')

    await userDialog.locator('#login').fill(run.medicoLogin)
    await userDialog.locator('#senha').fill(run.medicoSenha)
    await userDialog.locator('#confirmarSenha').fill(run.medicoSenha)

    await userDialog.locator('#role').click()
    await page.getByRole('option', { name: 'Médico', exact: true }).click()

    const medicoTrigger = userDialog.locator('#medicoId')
    await expect(medicoTrigger).toBeEnabled()
    await medicoTrigger.click()
    await page.getByRole('option', { name: new RegExp(run.medicoNome) }).click()

    await userDialog.getByRole('button', { name: 'Cadastrar' }).click()
    await expect(userDialog).toBeHidden()
    await expect(page.getByText('Usuário cadastrado com sucesso')).toBeVisible()

    // Autentica como o novo ROLE_MEDICO e valida escopo assistencial
    await loginAs(page, run.medicoLogin, run.medicoSenha)
    await expect(page).toHaveURL(/\/$/)

    const sidebarNav = page.locator('aside nav')
    await expect(sidebarNav.getByRole('link', { name: 'IA clínica' })).toBeVisible()
    await expect(sidebarNav.getByRole('link', { name: 'Usuários' })).toHaveCount(0)
    await expect(sidebarNav.getByRole('link', { name: 'Especialidades' })).toHaveCount(0)
    await expect(sidebarNav.getByRole('link', { name: 'Convênios' })).toHaveCount(0)
    await expect(sidebarNav.getByRole('link', { name: 'Auditoria LGPD' })).toHaveCount(0)

    await sidebarNav.getByRole('link', { name: 'Consultas' }).click()
    await expect(page).toHaveURL(/\/appointments$/)
    await expect(
      page.getByRole('row').filter({ hasText: run.pacienteNome }).filter({ hasText: run.medicoNome }),
    ).toBeVisible()
  })

  test('6. ROLE_MEDICO cria, detalha e edita prontuário em 24h e emite prescrição', async ({ page }) => {
    await loginAs(page, run.medicoLogin, run.medicoSenha)
    await expect(page).toHaveURL(/\/$/)

    const sidebarNav = page.locator('aside nav')
    await sidebarNav.getByRole('link', { name: 'Prontuários' }).click()
    await expect(page).toHaveURL(/\/medical-records$/)

    // Cria prontuário para a consulta agendada
    await page.getByRole('button', { name: 'Novo Prontuário' }).first().click()
    const createDialog = page.getByRole('dialog')
    await createDialog.locator('#consultaId').click()
    await page.getByRole('option', { name: new RegExp(run.pacienteNome) }).click()
    await createDialog.locator('#diagnostico').fill('Cefaleia tensional inicial')
    await createDialog.locator('#cid10').fill('G44.2')
    await createDialog.locator('#anamnese').fill('Dor frontal bilateral há 2 dias')
    await createDialog.locator('#observacoes').fill('Hidratação e acompanhamento')
    await createDialog.getByRole('button', { name: 'Criar Prontuário' }).click()

    await expect(createDialog).toBeHidden()
    await expect(page.getByText('Prontuário criado com sucesso')).toBeVisible()

    const row = page.getByRole('row').filter({ hasText: run.pacienteNome })
    await expect(row).toBeVisible()

    // Visualiza detalhes do prontuário
    await row.getByRole('button', { name: 'Detalhar prontuário' }).click()
    const detailDialog = page.getByRole('dialog')
    await expect(detailDialog.getByText('Dor frontal bilateral há 2 dias')).toBeVisible()
    await expect(detailDialog.getByText('Editável (até 24h)')).toBeVisible()
    await detailDialog.getByRole('button', { name: 'Fechar' }).click()
    await expect(detailDialog).toBeHidden()

    // Edita o prontuário dentro da janela de 24h
    await row.getByRole('button', { name: 'Editar prontuário' }).click()
    const editDialog = page.getByRole('dialog')
    await editDialog.locator('#edit-diagnostico').fill('Cefaleia tensional revisada')
    await editDialog.getByRole('button', { name: 'Salvar Alterações' }).click()

    await expect(editDialog).toBeHidden()
    await expect(page.getByText('Prontuário atualizado com sucesso')).toBeVisible()
    await expect(page.getByRole('row').filter({ hasText: 'Cefaleia tensional revisada' })).toBeVisible()

    // Emite prescrição vinculada ao prontuário para gerar trilha de PRESCRICAO na auditoria
    await sidebarNav.getByRole('link', { name: 'Prescrições' }).click()
    await expect(page).toHaveURL(/\/prescriptions$/)

    const comboboxes = page.getByRole('combobox')
    await comboboxes.nth(0).click()
    await page.getByRole('option', { name: new RegExp(run.pacienteNome) }).click()
    await comboboxes.nth(1).click()
    await page.getByRole('option', { name: /Cefaleia tensional revisada/i }).click()

    await page.getByRole('button', { name: 'Nova Prescrição' }).click()
    const prescDialog = page.getByRole('dialog')
    await prescDialog.getByPlaceholder('Ex: Amoxicilina').fill('Dipirona')
    await prescDialog.getByPlaceholder('Ex: 500mg').fill('500mg')
    await prescDialog.getByPlaceholder('Ex: 1 cápsula a cada 8h').fill('1 comprimido a cada 8h')
    await prescDialog.getByPlaceholder('Ex: 7 dias').fill('3 dias')
    await prescDialog.getByRole('button', { name: 'Criar Prescrição' }).click()

    await expect(prescDialog).toBeHidden()
    await expect(page.getByText('Prescrição criada com sucesso')).toBeVisible()
  })

  test('7. ROLE_ADMIN cria ROLE_AUDITOR que audita prontuário/prescrição e inativa prontuário', async ({ page }) => {
    await loginAs(page, ADMIN_LOGIN, ADMIN_PASSWORD)
    await expect(page).toHaveURL(/\/users$/)

    await page.getByRole('button', { name: 'Novo usuário' }).first().click()
    const userDialog = page.getByRole('dialog')
    await userDialog.locator('#login').fill(run.auditorLogin)
    await userDialog.locator('#senha').fill(run.auditorSenha)
    await userDialog.locator('#confirmarSenha').fill(run.auditorSenha)
    await userDialog.locator('#role').click()
    await page.getByRole('option', { name: 'Auditor', exact: true }).click()
    await userDialog.getByRole('button', { name: 'Cadastrar' }).click()

    await expect(userDialog).toBeHidden()
    await expect(page.getByText('Usuário cadastrado com sucesso')).toBeVisible()

    // Autentica como ROLE_AUDITOR (home em /audit + acesso de leitura às telas clínicas/operacionais)
    await loginAs(page, run.auditorLogin, run.auditorSenha)
    await expect(page).toHaveURL(/\/audit$/)

    const sidebarNav = page.locator('aside nav')
    await expect(sidebarNav.getByRole('link', { name: 'Auditoria LGPD' })).toBeVisible()
    await expect(sidebarNav.getByRole('link', { name: 'Prontuários' })).toBeVisible()
    await expect(sidebarNav.getByRole('link', { name: 'Consultas' })).toBeVisible()
    await expect(sidebarNav.getByRole('link', { name: 'Usuários' })).toHaveCount(0)
    await expect(sidebarNav.getByRole('link', { name: 'IA clínica' })).toHaveCount(0)

    // Abre trilha do prontuário e inspeciona o recurso PRESCRICAO vinculado
    await page.getByRole('row').filter({ hasText: run.pacienteNome }).click()
    await expect(page.getByText('Histórico de acessos', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: /^Prescrição #\d+$/ }).first()).toBeVisible()

    await page.getByRole('button', { name: /^Prescrição #\d+$/ }).first().click()
    await expect(page.getByText(/Trilha de auditoria — Prescrição #\d+/)).toBeVisible()

    // Navega para Prontuários em modo governança e realiza inativação lógica
    await sidebarNav.getByRole('link', { name: 'Prontuários' }).click()
    await expect(page).toHaveURL(/\/medical-records$/)

    const pronRow = page.getByRole('row').filter({ hasText: run.pacienteNome })
    await expect(pronRow).toBeVisible()
    await expect(page.getByRole('button', { name: 'Novo Prontuário' })).toHaveCount(0)

    await pronRow.getByRole('button', { name: 'Inativar prontuário' }).click()
    const confirmDialog = page.getByRole('dialog')
    await confirmDialog.getByRole('button', { name: 'Confirmar Inativação' }).click()

    await expect(confirmDialog).toBeHidden()
    await expect(page.getByText('Prontuário inativado com sucesso')).toBeVisible()
  })
})
