# Commissions

## Companies and attribution

A partner proposes a **company**: an identity (3 to 48 characters, lower-case letters and digits, single `-` or
`_` between them), a commission type and an amount. The company starts `IN_REVIEW`; an admin sets the final terms
and the status (`ACTIVE`, `DISABLED`, back to `IN_REVIEW`). Only an `ACTIVE` company of a partner who is not blocked
attributes players and earns.

The company's link sends players to the brand site:

    <SITE_URL>/?affiliate=<identity>&click_id={subid}

The brand site (your platform, not this app) must store `affiliate` as the player's affiliate tag and `click_id`
in the player's attribution, and publish them in `register.events` and `invoice.events` ([Events](Events)).
`{subid}` is Keitaro's macro for its click id.

A player is attributed **once and for good**, by whichever of their registration and first deposit is handled
first. The tag is the company's identity itself, with no prefix. The tag of a company refused at that moment (not
ACTIVE, a blocked partner) is remembered as refused, so a company approved later never claims the player.

## Commission types

| Type | Amount | Earns |
|---|---|---|
| `CPA` | a fixed amount of `PARTNER_CURRENCY` | once per player, on the first deposit, if it is at least `CPA_MIN_DEPOSIT` |
| `REVSHARE_DEPOSIT` | a percent, at most 100 | that percent of every deposit |
| `REVSHARE_TURNOVER` | a percent, at most 100 | that percent of every real-money stake (casino `PLACE`, sportsbook `BET`), net of rollbacks |

- Percentages are `round_half_up(amount * percent / 100)` in nano units.
- Only the player's own money counts: `realAmount` of a stake, never lock or bonus money.
- A first deposit below `CPA_MIN_DEPOSIT` never pays CPA, not even after a bigger second deposit.
- Stakes of `CPA` and `REVSHARE_DEPOSIT` companies count as turnover in the statistics and earn nothing.
- Terms of `0` are valid (a partner paid up front): the company attributes, counts and sends postbacks like any
  other, and earns nothing. A commission that comes to 0 writes no ledger row and moves no balance.
- Changed terms apply to events handled after the change.

## The ledger and the balance

Every commission is a `commission` row, unique per source: `cpa:<playerId>`, `deposit:<invoiceId>`, or
`turnover:<companyId>:<playerId>:<day>` (one row per company, player and UTC day, which stakes add to and
rollbacks subtract from). The partner's one balance, shared by all their companies, moves in the same transaction.

A rollback takes back exactly what its stake added, with the amounts recorded then, even if the company has been
disabled or its terms changed since; it can leave the balance below zero, and later earnings cover it first.

## Statistics

The cabinet shows, per UTC day, for all companies or one: registrations, first deposits, deposits (count and sum),
turnover, earnings by type, conversion, and the top 10 countries. The range is the last 30 days by default and at
most 366 days.
