import type { PostbackEvent, PostbackType } from '@/api/types'

/**
 * Everything the cabinet says about a tracker. Supporting a new one is one more entry in TRACKERS (and its
 * adapter on the server); the screens read only this shape.
 */

/** A run of guide text: plain words, a menu path to follow, or a literal to type. */
export type Inline = string | { path: string } | { code: string }

export interface GuideStep {
  title: string
  text: Inline[]
  /** A value the step asks to paste somewhere, shown with a copy button. */
  copy?: { label: string; value: string }
  /** A side remark under the step. */
  aside?: Inline[]
}

export interface GuideContext {
  /** The company's partner link with the tracker's click macro in place. */
  offerUrl: string
  clickParam: string
  sourceIp: string | null
}

export interface EventRule {
  event: PostbackEvent
  title: string
  when: string
  sends: Inline[]
}

export interface Parameter {
  name: string
  meaning: string
}

export interface Tracker {
  type: PostbackType
  name: string
  method: 'GET'
  /** The macro the tracker replaces with its click id in an offer URL. */
  clickMacro: string
  url: { label: string; placeholder: string; hint: string }
  steps: (context: GuideContext) => GuideStep[]
  events: EventRule[]
  parameters: Parameter[]
}

const KEITARO: Tracker = {
  type: 'KEITARO',
  name: 'Keitaro',
  method: 'GET',
  clickMacro: '{subid}',
  url: {
    label: 'Keitaro postback URL',
    placeholder: 'https://tracker.example.com/abc1234/postback?subid=REPLACE&status=REPLACE&payout=REPLACE',
    hint: 'Paste it as Keitaro shows it. We keep the address and the key and build the query ourselves.',
  },
  steps: ({ offerUrl, clickParam, sourceIp }) => [
    {
      title: 'Copy your postback URL',
      text: [
        'In Keitaro open ',
        { path: 'Settings → Postback URL' },
        ' (before v11.1: ',
        { path: 'Maintenance → Postback URL' },
        '). Copy the URL and paste it into the field below — we build the query ourselves.',
      ],
    },
    {
      title: 'Create the offer',
      text: ['Create an offer of type ', { path: 'Redirect' }, ' with this URL. Keitaro puts its click id in place of ', { code: '{subid}' }, '.'],
      copy: { label: 'Offer URL', value: offerUrl },
      aside: ['Working through an affiliate network instead? Give it the offer parameters ', { code: `${clickParam}={subid}` }, '.'],
    },
    {
      title: 'Take the payout from us',
      text: ['In the offer set ', { path: 'Payout → From param payout' }, '. A fixed payout in the offer overrides the amount we send.'],
    },
    {
      title: 'Add the spin and bet types',
      text: [
        'In ',
        { path: 'Settings → Conversion Types' },
        ' create two types with the values ',
        { code: 'spin' },
        ' and ',
        { code: 'bet' },
        '. Without them Keitaro ignores stakes; registrations and deposits work as they are.',
      ],
    },
    {
      title: 'Know what arrives',
      text: [
        'Registration → ',
        { code: 'status=registration' },
        '. Every deposit → ',
        { code: 'status=deposit' },
        ', every casino spin → ',
        { code: 'status=spin' },
        ', every sports bet → ',
        { code: 'status=bet' },
        ', each with its whole amount in EUR and a unique ',
        { code: 'tid' },
        '.',
      ],
    },
    {
      title: 'Check the setup',
      text: [
        'Open ',
        { path: 'Maintenance → Logs → Postbacks' },
        '. A test with a made-up subid shows “Click … not found” — that means the URL and the key are right.',
      ],
    },
    ...(sourceIp
      ? [
          {
            title: 'Behind Cloudflare?',
            text: ['If your tracker sits behind Cloudflare bot protection, allowlist our IP so our requests are not challenged.'],
            copy: { label: 'Our IP', value: sourceIp },
          },
        ]
      : []),
  ],
  events: [
    {
      event: 'REGISTRATION',
      title: 'Registration',
      when: 'A player signs up through your link.',
      sends: [{ code: 'status=registration' }],
    },
    {
      event: 'DEPOSIT',
      title: 'Deposit',
      when: 'Each successful deposit, the first one included, as a conversion of its own.',
      sends: [{ code: 'status=deposit' }, ', a unique ', { code: 'tid' }, ' and the deposit amount'],
    },
    {
      event: 'SPIN',
      title: 'Casino spin',
      when: 'Each stake in a casino game.',
      sends: [{ code: 'status=spin' }, ', a unique ', { code: 'tid' }, ' and the whole stake'],
    },
    {
      event: 'BET',
      title: 'Sports bet',
      when: 'Each sports bet.',
      sends: [{ code: 'status=bet' }, ', a unique ', { code: 'tid' }, ' and the whole stake'],
    },
  ],
  parameters: [
    { name: 'subid', meaning: 'The click id your tracker passed to us in click_id when the player arrived.' },
    { name: 'status', meaning: 'registration, deposit, spin or bet.' },
    { name: 'payout', meaning: 'The deposit or the whole stake in EUR with 2 decimals. Not sent on a registration.' },
    { name: 'currency', meaning: 'Always eur: Keitaro has no USDT.' },
    { name: 'tid', meaning: 'dep-<deposit id>, spin-<spin id> or bet-<bet id>, so each conversion is counted separately.' },
  ],
}

export const TRACKERS: Tracker[] = [KEITARO]

/** The entry for a type; a type the server supports before the cabinet describes it still gets a URL field. */
export function trackerFor(type: PostbackType): Tracker {
  return (
    TRACKERS.find((tracker) => tracker.type === type) ?? {
      type,
      name: type.charAt(0) + type.slice(1).toLowerCase(),
      method: 'GET',
      clickMacro: '',
      url: { label: 'Postback URL', placeholder: 'https://', hint: '' },
      steps: () => [],
      events: [],
      parameters: [],
    }
  )
}

export const EVENT_LABELS: Record<PostbackEvent, string> = {
  REGISTRATION: 'Registration',
  DEPOSIT: 'Deposit',
  SPIN: 'Casino spin',
  BET: 'Sports bet',
}
