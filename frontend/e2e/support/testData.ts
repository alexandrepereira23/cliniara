export type BusinessDayOfWeek =
  | 'MONDAY'
  | 'TUESDAY'
  | 'WEDNESDAY'
  | 'THURSDAY'
  | 'FRIDAY'
  | 'SATURDAY'

const DAY_INDEX_MAP: Record<number, { diaSemana: BusinessDayOfWeek; diaLabel: string }> = {
  1: { diaSemana: 'MONDAY', diaLabel: 'Segunda-feira' },
  2: { diaSemana: 'TUESDAY', diaLabel: 'Terça-feira' },
  3: { diaSemana: 'WEDNESDAY', diaLabel: 'Quarta-feira' },
  4: { diaSemana: 'THURSDAY', diaLabel: 'Quinta-feira' },
  5: { diaSemana: 'FRIDAY', diaLabel: 'Sexta-feira' },
  6: { diaSemana: 'SATURDAY', diaLabel: 'Sábado' },
}

export function generateValidCpf(seed: number): string {
  const baseStr = String((seed % 899999999) + 100000000).padStart(9, '0')
  const digits = baseStr.split('').map(Number)

  if (digits.every((d) => d === digits[0])) {
    digits[8] = (digits[8] + 1) % 10
  }

  const calcDigit = (slice: number[], factor: number) => {
    const sum = slice.reduce((acc, num, idx) => acc + num * (factor - idx), 0)
    const rem = (sum * 10) % 11
    return rem === 10 ? 0 : rem
  }

  const d1 = calcDigit(digits, 10)
  const d2 = calcDigit([...digits, d1], 11)
  return [...digits, d1, d2].join('')
}

export function nextBusinessAppointmentSlot() {
  const target = new Date()
  target.setDate(target.getDate() + 2)

  // Clínica funciona de Segunda a Sábado (pula Domingo)
  if (target.getDay() === 0) {
    target.setDate(target.getDate() + 1)
  }

  const yyyy = target.getFullYear()
  const mm = String(target.getMonth() + 1).padStart(2, '0')
  const dd = String(target.getDate()).padStart(2, '0')
  const dayMeta = DAY_INDEX_MAP[target.getDay()]

  return {
    dateInput: `${yyyy}-${mm}-${dd}`,
    diaSemana: dayMeta.diaSemana,
    diaLabel: dayMeta.diaLabel,
  }
}

export function createClinicRunData() {
  const seed = Date.now() % 1000000
  const crm = String(100000 + (seed % 899999))
  const cpf = generateValidCpf(seed)
  const slot = nextBusinessAppointmentSlot()

  return {
    seed,
    especialidadeNome: `Cardiologia E2E ${seed}`,
    funcionarioLogin: `recepcao.${seed}@vollmed.com`,
    funcionarioSenha: 'Recepcao@123',
    medicoNome: `Dr. Renato E2E ${seed}`,
    medicoEmail: `dr.renato.${seed}@vollmed.com`,
    medicoCrm: crm,
    medicoLogin: `medico.${seed}@vollmed.com`,
    medicoSenha: 'Medico@123',
    pacienteNome: `Marina Paciente E2E ${seed}`,
    pacienteEmail: `marina.${seed}@email.com`,
    pacienteCpf: cpf,
    slot,
  }
}
