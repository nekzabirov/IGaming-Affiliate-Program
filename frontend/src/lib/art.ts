/**
 * The illustrations, each with the two widths it ships in. They are imported rather than kept in /public so their
 * names carry a content hash and the server may cache them for a year.
 *
 * They are the deployment's own artwork, found by name in src/assets/art/. A build without them - an empty folder -
 * draws a quiet glow of the same proportions in each place instead, so the layout never depends on the files.
 */
export interface Art {
  src: string
  srcset: string
}

const FILES = import.meta.glob<string>('../assets/art/*.webp', { eager: true, import: 'default' })

const file = (name: string): string | undefined => FILES[`../assets/art/${name}.webp`]

/** A soft white glow on nothing, at the picture's aspect ratio: on the dark page it reads as light, not as a box. */
function placeholder(width: number, height: number): Art {
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}">` +
    '<defs><radialGradient id="glow" cx="62%" cy="45%" r="55%">' +
    '<stop offset="0" stop-color="#fff" stop-opacity="0.10"/><stop offset="1" stop-color="#fff" stop-opacity="0"/>' +
    '</radialGradient></defs><rect width="100%" height="100%" fill="url(#glow)"/></svg>'
  const src = `data:image/svg+xml,${encodeURIComponent(svg)}`
  return { src, srcset: src }
}

/** [name] at [smallWidth] (`<name>-w<smallWidth>.webp`) and at twice that ([name].webp), [ratio] = height / width. */
function pair(name: string, smallWidth: number, ratio: number): Art {
  const small = file(`${name}-w${smallWidth}`)
  const large = file(name)
  if (!small || !large) return placeholder(smallWidth, Math.round(smallWidth * ratio))
  return { src: small, srcset: `${small} ${smallWidth}w, ${large} ${smallWidth * 2}w` }
}

/** The square pictures: empty states, the review banner and the support card. */
export const SQUARE_ART = {
  companies: pair('art-empty-companies', 480, 1),
  postbacks: pair('art-empty-postbacks', 480, 1),
  payouts: pair('art-empty-payouts', 480, 1),
  review: pair('art-review', 480, 1),
  support: pair('art-support', 480, 1),
} as const

export type SquareArtName = keyof typeof SQUARE_ART

/** The portrait beside the sign-in form (3:4). */
export const AUTH_ART = pair('art-auth', 720, 4 / 3)

/** The welcome card's backdrop (16:9); its subject sits right of centre so text can live on the left. */
export const DASHBOARD_ART = pair('art-dashboard', 960, 9 / 16)
