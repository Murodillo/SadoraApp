import { QueryCache, QueryClient } from '@tanstack/react-query'
import { ApiFailure } from './client'
import { keys } from './hooks'

/**
 * The app's query client, built here so the tests get the very same behaviour.
 *
 * A 403 on any doctor call usually means her status changed under her — an admin
 * suspended her while the panel was open. Her account is then read again, and the gate
 * swaps the workspace for the status page instead of leaving her in front of refusals.
 * The account query itself is left out, or a 403 there would refetch itself for ever.
 */
export function createQueryClient(options: { retry?: boolean } = {}): QueryClient {
  const client: QueryClient = new QueryClient({
    queryCache: new QueryCache({
      onError: (error, failed) => {
        if (!(error instanceof ApiFailure) || error.status !== 403) return
        if (failed.queryKey.join('/') === keys.account.join('/')) return
        void client.invalidateQueries({ queryKey: keys.account })
      },
    }),
    defaultOptions: {
      queries: {
        staleTime: 15_000,
        // Retrying a 401 or a 403 only delays the answer; retry transport problems and
        // nothing else.
        retry:
          options.retry === false
            ? false
            : (failureCount, error) => error instanceof ApiFailure && error.code === 'network' && failureCount < 2,
      },
    },
  })
  return client
}
