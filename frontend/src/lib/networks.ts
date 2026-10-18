/** USDT networks a payout can go out on. A network configured on the server but unknown here still works. */
interface Network {
  /** The chain behind the token standard, as people call it. */
  chain: string
  pattern: RegExp
  hint: string
  placeholder: string
}

const NETWORKS: Record<string, Network> = {
  TRC20: {
    chain: 'Tron',
    pattern: /^T[1-9A-HJ-NP-Za-km-z]{33}$/,
    hint: 'A Tron address: starts with T, 34 characters.',
    placeholder: 'T…',
  },
  ERC20: {
    chain: 'Ethereum',
    pattern: /^0x[0-9a-fA-F]{40}$/,
    hint: 'An Ethereum address: 0x and 40 hex characters.',
    placeholder: '0x…',
  },
  BEP20: {
    chain: 'BNB Smart Chain',
    pattern: /^0x[0-9a-fA-F]{40}$/,
    hint: 'A BNB Smart Chain address: 0x and 40 hex characters.',
    placeholder: '0x…',
  },
  TON: {
    chain: 'The Open Network',
    pattern: /^(?:EQ|UQ)[A-Za-z0-9_-]{46}$/,
    hint: 'A TON address: starts with EQ or UQ, 48 characters.',
    placeholder: 'UQ…',
  },
}

const ANY: Omit<Network, 'chain'> = {
  pattern: /^[\x21-\x7e]{10,128}$/,
  hint: 'The wallet address, 10 to 128 characters.',
  placeholder: '',
}

export function network(code: string): Network {
  return NETWORKS[code] ?? { chain: '', ...ANY }
}

/** "TRC20 · Tron"; a network unknown here is shown by its code alone. */
export function networkName(code: string): string {
  const { chain } = network(code)
  return chain ? `${code} · ${chain}` : code
}

export function addressError(code: string, value: string): string | null {
  if (!value) return 'Enter the wallet address.'
  return network(code).pattern.test(value) ? null : network(code).hint
}
