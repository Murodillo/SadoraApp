/// <reference types="vite/client" />

interface ImportMetaEnv {
  /**
   * Where the API lives when it is not this page's own origin — `https://dev-api.sadora.app`
   * on staging, where the panel and the API are separate hostnames. Blank in development:
   * Vite's proxy answers `/v1` on the panel's origin.
   */
  readonly VITE_API_BASE?: string
}
