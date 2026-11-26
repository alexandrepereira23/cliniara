import { startCepStubServer, type CepStubHandle } from './support/cepStubServer'

let stubHandle: CepStubHandle | null = null

export default async function globalSetup() {
  stubHandle = await startCepStubServer()
  ;(globalThis as { __CEP_STUB__?: CepStubHandle | null }).__CEP_STUB__ = stubHandle
}
