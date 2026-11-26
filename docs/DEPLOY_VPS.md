# Deploy das APIs na VPS

Os dois frontends continuam na Vercel. A VPS executa as APIs, o MySQL do Cliniara e o Caddy, que termina o HTTPS para as duas APIs.

| Host | Destino |
|---|---|
| `vollmed.usenexora.online` (atual), `cliniara.usenexora.online` (planejado) | Frontend Cliniara na Vercel |
| `consulta-cep.usenexora.online` | Frontend Consultar-Cep na Vercel |
| `api-vollmed.usenexora.online` (atual), `api-cliniara.usenexora.online` (planejado) | Serviço `backend` no Compose |
| `api-cep.usenexora.online` | Serviço `consultacep` no Compose |

Crie registros A para os dois hosts `api-*`, apontando para o IP público da VPS. Configure os dois domínios dos painéis na Vercel e use os destinos CNAME/records mostrados em cada projeto. Libere as portas TCP 80 e 443 na VPS e no firewall do provedor.

## Preparar a VPS

Instale Docker Engine com o plugin Docker Compose para a distribuição Linux da VPS. Crie um usuário de deploy com acesso ao Docker e autenticação SSH por chave. Os comandos abaixo presumem que ambos os repositórios públicos serão clonados em `/opt/vollmed/app` e `/opt/vollmed/cep`:

```bash
sudo mkdir -p /opt/vollmed
sudo chown "$USER":"$USER" /opt/vollmed
git clone https://github.com/alexandrepereira23/voll.med.git /opt/vollmed/app
git clone https://github.com/alexandrepereira23/Consultar-Cep.git /opt/vollmed/cep
cp /opt/vollmed/app/.env.production.example /opt/vollmed/app/.env
chmod 600 /opt/vollmed/app/.env
```

Edite `/opt/vollmed/app/.env`, substitua os marcadores por valores fortes e configure:

- Gere valores independentes para `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD` e `JWT_SECRET` (por exemplo, com `openssl rand -hex 32`). Defina também `ADMIN_PASSWORD`.

- `CLINIARA_API_DOMAIN=api-cliniara.usenexora.online` para instalação nova após configurar o DNS. Em uma instalação ativa, mantenha `VOLL_MED_API_DOMAIN` com o valor atual durante a transição.
- `CEP_API_DOMAIN=api-cep.usenexora.online`
- `CEP_API_KEY`: uma chave forte compartilhada somente entre as duas APIs
- `CEP_CORS_ORIGENS_PERMITIDAS`: domínio público do painel CEP e seu domínio Vercel estável, separados por vírgula
- `CEP_BACKEND_CONTEXT=../cep/backend`

Use a mesma chave em `CEP_API_KEY` e na variável `API_KEY_VALOR` do serviço Consultar-Cep. O backend Cliniara chama o CEP internamente por `http://consultacep:8080`, dentro da rede Docker. Não publique a porta do MySQL nem das APIs diretamente no host.

Faça o primeiro deploy manual depois de configurar o DNS e o arquivo `.env`:

```bash
cd /opt/vollmed/app
docker compose --env-file .env -f docker-compose.prod.yml config --quiet
docker compose --env-file .env -f docker-compose.prod.yml up -d --build --remove-orphans
docker compose --env-file .env -f docker-compose.prod.yml ps
```

O Caddy solicita os certificados HTTPS automaticamente quando os dois registros DNS já apontam para a VPS e as portas 80/443 estão acessíveis.

## Deploy automático pelo GitHub Actions

Configure estes segredos no ambiente GitHub `production` de **cada repositório**:

- `DEPLOY_HOST`: IP ou hostname SSH da VPS
- `DEPLOY_USER`: usuário de deploy
- `DEPLOY_SSH_KEY`: chave privada SSH autorizada para esse usuário
- `DEPLOY_KNOWN_HOSTS`: chave de host SSH da VPS verificada por um canal confiável

No projeto Cliniara da Vercel, defina `VITE_API_BASE_URL` com o host ativo da API e o sufixo `/api`. Na instalação existente, mantenha `https://api-vollmed.usenexora.online/api`. O painel Consultar-Cep usa `https://api-cep.usenexora.online/api/v1` no build de produção.

Push na branch `main` do Consultar-Cep atualiza `/opt/vollmed/cep` e reconstrói somente `consultacep`. O workflow `.github/workflows/deploy.yml` da Cliniara exige os jobs `test` (Maven, Vitest, TypeScript e build) e `e2e` (Playwright com MySQL) antes de atualizar a stack via SSH. O deploy usa `/opt/cliniara/app` se lá houver um checkout Git e seu `.env`; do contrário usa `/opt/vollmed/app`. Nenhum segredo deve ser colocado no código ou enviado pelo chat.

## Migração para Cliniara

O rebranding pode ser publicado com o `.env` da VPS intacto. O Compose usa `CLINIARA_API_DOMAIN` quando informado e, na ausência, resolve `VOLL_MED_API_DOMAIN` para o Caddy. O Caddy atende **um** host da API Cliniara com essa configuração; o fallback não mantém os dois hosts simultaneamente.

- [ ] **Antes do primeiro deploy:** aguarde o fim dos workflows do grupo antigo (`vollmed-production`) e evite um deploy simultâneo do Consultar-Cep. Faça backup do MySQL e confirme a identidade do projeto Compose e os volumes em uso. Preserve `DB_NAME`, `DB_USER`, `ADMIN_LOGIN` e os volumes de produção; não substitua o `.env` pelo exemplo. O rollout com uma única instância pode causar uma interrupção breve.
- [ ] **Depois do deploy:** entre de novo com as credenciais existentes (os JWTs antigos serão invalidados). Teste login e uma leitura autenticada via HTTPS com dados esperados, além da auditoria LGPD ao acessar prontuários. Valide o título `Cliniara API` no Swagger local ou de homologação; ele segue desabilitado na VPS.
- [ ] **GitHub e Vercel:** se renomear o repositório, confira o remoto da VPS e a integração Git da Vercel. Mantenha `frontend` como diretório raiz, configure `cliniara.usenexora.online` no Vercel/DNS e teste login nos dois domínios do painel. Confirme `VITE_API_BASE_URL` nos ambientes Production e Preview, além das origens CORS efetivas.
- [ ] **Domínio da API (opcional):** mantenha `https://api-vollmed.usenexora.online/api` enquanto houver builds Vercel dependentes dele. Antes de mudar `CLINIARA_API_DOMAIN` para `api-cliniara.usenexora.online`, planeje DNS, TLS e uma configuração do Caddy que atenda ambos os hosts durante a transição. `VITE_API_BASE_URL` é incorporada no build: só altere e publique um novo build após validar ambos os hosts, inclusive o caminho de rollback. Mantenha a variável legada até encerrar a janela de rollback.
- [ ] **Pasta da VPS (opcional):** antes de habilitar `/opt/cliniara/app` com o `.env`, preserve o nome efetivo do projeto Compose (incluindo qualquer `COMPOSE_PROJECT_NAME` existente), confira os volumes e garanta que `CEP_BACKEND_CONTEXT` aponte para o checkout certo. Se apenas `app` mudar de lugar, `../cep/backend` deixa de achar `/opt/vollmed/cep/backend`; use um caminho absoluto ou mova os dois checkouts e ajuste o workflow do Consultar-Cep. Não suba uma segunda stack de produção.

**Rollback do rebranding:** volte à revisão anterior pelo fluxo de deploy, reutilizando o mesmo projeto Compose e volumes e mantendo `VOLL_MED_API_DOMAIN` definido com o host anterior. Se a pasta antiga ainda for exigida pelo workflow anterior, preserve-a para o rollback. Não restaure o banco só por causa do rebranding: as migrations V1–V25 não mudaram. Confirme novamente login e leitura autenticada.

**Desenvolvimento local:** se reutilizar um volume MySQL legado, preserve o nome do projeto Compose e defina `DB_NAME=vollmed_api` em `backend/.env`. `MYSQL_DATABASE` não cria outro schema num volume já inicializado. Mantenha o `ADMIN_LOGIN` explícito se quiser que ele também seja usado ao inicializar um banco vazio. O `AdminInitializer` não altera nem recria usuários quando já existe um `ROLE_ADMIN`. Os novos defaults `cliniara_api` e `admin@cliniara.com` são para ambientes novos.

## Dados do Railway

O Compose cria um volume MySQL novo. Antes de apontar o tráfego de produção para a VPS, exporte o banco atual do Railway e restaure-o no MySQL da VPS. As migrations Flyway atualizam o schema, mas não transferem os dados existentes.

Não use `docker compose down -v`: isso apaga os volumes persistentes, inclusive os dados do MySQL e os certificados do Caddy.
