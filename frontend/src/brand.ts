/**
 * Every word of the brand, in one place: the page titles, the logo's accessible name, and - written by the
 * `brand` plugin in vite.config.ts at build time - the HTML shell's title and description and the web app
 * manifest. The pictures are the files in src/assets/brand/ (logo-mark.svg at 6:5, which the HTML shell also
 * inlines as its loading mark, and logo-lockup.svg at 6:1) and the icons in public/ (favicon.svg, favicon.ico,
 * apple-touch-icon.png, icons/icon-192.png, icons/icon-512.png).
 *
 * No imports: vite.config.ts reads this file too.
 */
export const BRAND = {
  /** The product's full name: every page title and the logo's accessible name. */
  name: 'Affiliate Program',

  /** The name under the icon on a phone's home screen. */
  shortName: 'Partners',

  /** The HTML shell's meta description. */
  description:
    'Affiliate Program: run your companies, follow every registration and deposit, send postbacks to your tracker and request payouts.',

  /** The installed app's description, in the web app manifest. */
  appDescription: 'The partner cabinet of the affiliate program: companies, statistics, postbacks and payouts.',
} as const
