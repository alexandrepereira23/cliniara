import type { CepStubHandle } from './support/cepStubServer'

export default async function globalTeardown() {
  const handle = (globalThis as { __CEP_STUB__?: CepStubHandle | null }).__CEP_STUB__
  if (handle) {
    await handle.close()
  }
}
