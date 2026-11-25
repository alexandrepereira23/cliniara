# Design System — Voll.med Frontend

Estilo: **Neutral Warm (Warm Beige + Sage Green)** — paleta acolhedora de baixo cansaço visual para uso clínico prolongado, fugindo do branco puro e do azul hospitalar tradicional.

Implementado com **TailwindCSS v4** (`@theme inline` em `src/index.css`) e primitivos **shadcn/ui** (`src/components/ui/`), com suporte a tema claro (`:root`, padrão) e escuro (`.dark`).

---

## Tokens de Cor (`src/index.css`)

### Tema Claro (`:root` — Padrão)

| Variável CSS | Classe Tailwind v4 | Hex | Uso principal |
|---|---|---|---|
| `--background` | `bg-background` | `#F5F0EB` | Fundo geral das páginas |
| `--foreground` | `text-foreground` | `#1C1917` | Títulos e texto principal |
| `--card` / `--popover` | `bg-card` / `bg-popover` | `#EDE8E3` | Cards, cabeçalho superior e modais/popovers |
| `--card-foreground` | `text-card-foreground` | `#1C1917` | Texto sobre cards |
| `--primary` | `bg-primary` / `text-primary` | `#6B7F6A` | Verde sálvia — botões primários, foco, destaques |
| `--primary-foreground` | `text-primary-foreground` | `#FDFCFB` | Texto sobre botões/elementos primários |
| `--secondary` | `bg-secondary` | `#E5DED7` | Botões secundários e superfícies de apoio |
| `--secondary-foreground` | `text-secondary-foreground` | `#1C1917` | Texto sobre superfícies secundárias |
| `--muted` | `bg-muted` | `#EDE8E3` | Áreas neutras e hover sutil (`hover:bg-muted/50`) |
| `--muted-foreground` | `text-muted-foreground` | `#78716C` | Subtítulos, descrições, metadados e placeholders |
| `--accent` | `bg-accent` | `#6B7F6A` | Realce em botões `outline`/`ghost` |
| `--destructive` | `bg-destructive` / `text-destructive` | `#C4714F` | Terracota — exclusões, cancelamentos e erros |
| `--border` / `--input` | `border-border` / `border-input` | `#D4CCC4` | Divisórias, tabelas e bordas de campos |
| `--ring` | `ring-ring` | `#6B7F6A` | Anel de foco acessível |

### Tokens da Sidebar e Gráficos

| Variável CSS | Classe Tailwind v4 | Hex (`:root`) | Uso |
|---|---|---|---|
| `--sidebar` | `bg-sidebar` | `#EDE8E3` | Fundo da navegação lateral |
| `--sidebar-foreground` | `text-sidebar-foreground` | `#1C1917` | Itens inativos e identificação do usuário |
| `--sidebar-primary` | `bg-sidebar-primary` | `#6B7F6A` | Item de menu ativo e selo da marca |
| `--sidebar-primary-foreground` | `text-sidebar-primary-foreground` | `#FDFCFB` | Texto/ícone do item ativo |
| `--sidebar-accent` | `hover:bg-sidebar-accent` | `#E5DED7` | Hover dos itens de navegação |
| `--sidebar-border` | `border-sidebar-border` | `#D4CCC4` | Divisórias do topo e rodapé da sidebar |
| `--chart-1` a `--chart-5` | — | `#6B7F6A`, `#4A7C59`, `#C4964F`, `#C4714F`, `#A8A29E` | Séries de gráficos (Recharts) |

### Uso Recomendado em Componentes

```tsx
// Correto — utilizar sempre os tokens semânticos mapeados em src/index.css
<Card className="border-border">
  <h2 className="text-foreground font-semibold">Título</h2>
  <p className="text-sm text-muted-foreground">Subtítulo descritivo</p>
  <Button>Salvar</Button>
  <Button variant="destructive">Cancelar consulta</Button>
</Card>
```

> **Nota sobre badges de status:** `src/components/Badge.tsx` e `src/pages/Audit.tsx` utilizam pontualmente tons semânticos suaves (`green`, `yellow`, `red`, `blue`) apenas para diferenciação rápida de criticidade/ação (`ROTINA`, `PRIORITARIO`, `URGENCIA`, `VISUALIZOU`, `CRIOU`, `EDITOU`). Para layout, superfícies, botões e textos estruturais, nunca usar `bg-blue-*` ou `bg-gray-*`.

---

## Tipografia

**Fonte:** `Plus Jakarta Sans` (carregada via Google Fonts em `index.html` e vinculada a `--font-sans` em `src/index.css`).

| Escala | Classes usuais | Uso |
|---|---|---|
| Título de página | `text-2xl font-bold text-foreground` | `PageHeader` |
| Título de seção / modal | `text-lg font-semibold` / `DialogTitle` | Cabeçalhos de card e diálogos |
| Subtítulo de agrupamento | `text-sm font-semibold text-foreground` | Seções internas de formulários |
| Corpo e campos | `text-sm` | Tabelas, inputs, selects e botões |
| Metadados e badges | `text-xs font-medium` | Badges, paginação e perfil na sidebar |

---

## Bordas e Raios

Base definida em `:root` como `--radius: 0.625rem` (`10px`):

- `rounded-sm`: `calc(var(--radius) - 4px)` (`6px`)
- `rounded-md`: `calc(var(--radius) - 2px)` (`8px`) — inputs, botões e selects
- `rounded-lg`: `var(--radius)` (`10px`) — itens da sidebar, cards e containers
- `rounded-xl`: `calc(var(--radius) + 4px)` (`14px`) — diálogos e painéis destacados
- `rounded-full`: avatares de iniciais e pílulas de `Badge`

---

## Ícones

**Biblioteca:** `lucide-react` (nunca usar emojis como ícones de interface).

- Navegação na sidebar (`DashboardLayout.tsx`): `className="h-5 w-5"`
- Ações em botões e paginação: `className="h-4 w-4"`
- Estados de carregamento assíncrono (ex.: busca de CEP): `Loader2` com `animate-spin`

---

## Componentes Base Principais

### `Button` (`src/components/ui/button.tsx`)

Baseado em `class-variance-authority`:

- **Variantes (`variant`):**
  - `default`: `bg-primary text-primary-foreground hover:bg-primary/90`
  - `destructive`: `bg-destructive text-white hover:bg-destructive/90`
  - `outline`: `border bg-transparent shadow-xs hover:bg-accent`
  - `secondary`: `bg-secondary text-secondary-foreground hover:bg-secondary/80`
  - `ghost`: `hover:bg-accent`
  - `link`: `text-primary underline-offset-4 hover:underline`
- **Tamanhos (`size`):** `default` (`h-9 px-4 py-2`), `sm` (`h-8 px-3`), `lg` (`h-10 px-6`), `icon` (`size-9`), `icon-sm` (`size-8`), `icon-lg` (`size-10`).

### `Badge` (`src/components/Badge.tsx`)

Pílula compacta (`rounded-full px-3 py-1 text-xs font-medium`) com variantes `default`, `success`, `warning`, `error` e `info`.

### Padrões Compartilhados de Página

- **`PageHeader` (`src/components/PageHeader.tsx`):** ícone, título, descrição e botão de ação primária opcional controlado por RBAC (`canWrite` / `isMedico`).
- **`SearchInput` (`src/components/SearchInput.tsx`):** campo de filtro textual dentro de um `Card` superior.
- **`EmptyState` (`src/components/EmptyState.tsx`):** estado vazio padronizado com ícone, mensagem contextual e call-to-action opcional.

---

## Estrutura de Layout (`DashboardLayout.tsx`)

```text
┌──────────────────┬────────────────────────────────────────────┐
│ Sidebar (w-64)   │ Header superior (h-16, bg-card, data pt-BR)│
│ bg-sidebar       ├────────────────────────────────────────────┤
│ - Marca Voll.med │ Main (flex-1 overflow-y-auto bg-background)│
│ - Nav por Role   │   └── .container py-8 (max-w 1280px)       │
│ - Avatar + Sair  │                                            │
└──────────────────┴────────────────────────────────────────────┘
```

- **Desktop (`lg:`):** sidebar fixa (`w-64` / `256px`) visível permanentemente à esquerda.
- **Mobile (`< lg`):** sidebar recolhida fora da tela (`-translate-x-full`) com abertura por botão `Menu` no header e overlay escuro (`bg-black/50`).
- **Container responsivo (`src/index.css`):** `padding` horizontal progressivo (`1rem` → `1.5rem` → `2rem`) e `max-width: 1280px` a partir de `1024px`.

---

## Diretrizes de Consistência

- Nunca introduzir uma segunda família tipográfica sem necessidade formal.
- Evitar animações chamativas (`animate-bounce`, `animate-ping`); restringir movimento a transições funcionais (`transition-colors`, `transition-transform` da sidebar e `animate-spin` em spinners).
- Sempre respeitar `border-border` em cards, tabelas e divisórias para preservar a unidade visual quente da paleta.
