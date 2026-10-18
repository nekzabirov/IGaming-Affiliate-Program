/**
 * Money arrives as decimal strings with up to 9 fraction digits. Arithmetic and rounding run on BigInt nano
 * units, so a balance is never shown or compared through a binary float.
 */

const SCALE = 9

const ONE = 10n ** BigInt(SCALE)

const DECIMAL = /^([+-])?(\d+)(?:\.(\d+))?$/

const GROUPS = new Intl.NumberFormat('en-US')

/** Signed nano units of a decimal string; an unreadable value reads as zero. */
export function toNano(value: string | null | undefined): bigint {
  const match = DECIMAL.exec((value ?? '').trim())
  if (!match) return 0n
  const [, sign, whole = '0', fraction = ''] = match
  const nano = BigInt(whole) * ONE + BigInt(fraction.padEnd(SCALE, '0').slice(0, SCALE))
  return sign === '-' ? -nano : nano
}

/** The shortest decimal string of nano units: "30.5", not "30.500000000". */
function fromNano(nano: bigint): string {
  const negative = nano < 0n
  const magnitude = negative ? -nano : nano
  const fraction = (magnitude % ONE).toString().padStart(SCALE, '0').replace(/0+$/, '')
  return `${negative ? '-' : ''}${magnitude / ONE}${fraction ? `.${fraction}` : ''}`
}

/** The value as a person would type it. */
export const plain = (value: string | null | undefined) => fromNano(toNano(value))

/** Rounded toward zero to `decimals` places: the most of a balance a 2-decimal request can ask for. */
export function floorTo(value: string, decimals: number): string {
  const step = 10n ** BigInt(SCALE - decimals)
  return fromNano((toNano(value) / step) * step)
}

/** "1234.5" → "1,234.50", half away from zero; a negative value carries a true minus sign. */
export function formatDecimal(value: string | null | undefined, decimals = 2): string {
  const step = 10n ** BigInt(SCALE - decimals)
  const nano = toNano(value)
  const negative = nano < 0n
  const scaled = ((negative ? -nano : nano) + step / 2n) / step
  const factor = 10n ** BigInt(decimals)
  const fraction = decimals > 0 ? `.${(scaled % factor).toString().padStart(decimals, '0')}` : ''
  return `${negative && scaled > 0n ? '−' : ''}${GROUPS.format(scaled / factor)}${fraction}`
}

/** Exact sum of decimal strings. */
export const sumDecimals = (values: string[]) => fromNano(values.reduce((sum, value) => sum + toNano(value), 0n))

export function compareDecimal(a: string, b: string): number {
  const left = toNano(a)
  const right = toNano(b)
  return left === right ? 0 : left < right ? -1 : 1
}

export const isPositive = (value: string | null | undefined) => toNano(value) > 0n

export const isNegative = (value: string | null | undefined) => toNano(value) < 0n

/** For chart geometry only, where float precision cannot be seen. */
export const toNumber = (value: string | null | undefined) => Number(toNano(value)) / Number(ONE)
