# Branding

The software ships with a neutral brand ("Affiliate Program"). Everything that names or pictures a brand sits in a
few files, so a deployment replaces those files and touches no component.

## Name and texts

`frontend/src/brand.ts`:

| Key | Used for |
|---|---|
| `name` | every page title (`<page> · <name>`), the logo's accessible name, the HTML `<title>` |
| `shortName` | the name under the icon on a phone's home screen (web app manifest) |
| `description` | the HTML meta description |
| `appDescription` | the web app manifest's description |

The `brand` plugin in `frontend/vite.config.ts` writes them into `index.html` and generates
`manifest.webmanifest` at build time.

## Pictures

| File | Shape | Used for |
|---|---|---|
| `frontend/src/assets/brand/logo-mark.svg` | 6:5 | the mark: the phone's top bar, the account sheet, and inlined into `index.html` as the loading mark (the file must start with `<svg xmlns="http://www.w3.org/2000/svg"`) |
| `frontend/src/assets/brand/logo-lockup.svg` | 6:1 | the full logo: the sidebar and the sign-in screens |
| `frontend/public/favicon.svg`, `favicon.ico` | square | the browser tab |
| `frontend/public/apple-touch-icon.png` | 180 x 180 | iOS home screen |
| `frontend/public/icons/icon-192.png`, `icon-512.png` | square | the web app manifest |

Outline any text in the SVGs, so they need no font.

## Illustrations

`frontend/src/assets/art/` may hold illustrations, each in two widths: `art-auth` (3:4, 720 and 1440 px wide),
`art-dashboard` (16:9, 960 and 1920), and the squares `art-empty-companies`, `art-empty-postbacks`,
`art-empty-payouts`, `art-review`, `art-support` (480 and 960); the smaller file is named `<name>-w<width>.webp`, the
larger `<name>.webp`. They are found by name at build time; without them (the published repository has none) the
cabinet draws a soft glow of the same proportions in each place.

## Colours and fonts

`frontend/src/styles/theme.css` is the one place a colour, font, radius, shadow or curve is written as a value.
The bundled fonts (Exo 2, Manrope, JetBrains Mono, in `frontend/public/fonts/`) are under the SIL Open Font
License 1.1 (`LICENSES/OFL-1.1.txt`).

## Promo library

The Promo screen lists ready banners partners can download or link to. The banners live on a static site of
their own (any HTTPS host, a CDN, object storage) next to a catalogue, and `PROMO_MANIFEST_URL` names the
catalogue. Without it the Promo screen is hidden.

The browser fetches the catalogue directly, so its host must allow the cabinet's origin (CORS
`Access-Control-Allow-Origin`). File paths in it are relative to the catalogue's folder:

```json
{
  "topics": [{ "id": "welcome", "title": "Welcome bonus" }],
  "formats": [
    { "id": "square", "label": "Square", "width": 1080, "height": 1080 },
    { "id": "story", "label": "Story", "width": 1080, "height": 1920 }
  ],
  "items": [
    {
      "id": "welcome-en-square",
      "topic": "welcome",
      "title": "Welcome bonus",
      "language": "en",
      "format": "square",
      "width": 1080,
      "height": 1080,
      "file": "banners/welcome-en-square.png",
      "preview": "previews/welcome-en-square.webp",
      "bytes": 482113
    }
  ]
}
```

`format` is `square` or `story`. With `PROMO_MANIFEST_URL=https://promo.example.com/manifest.json` the file above
is served from `https://promo.example.com/banners/welcome-en-square.png`.
