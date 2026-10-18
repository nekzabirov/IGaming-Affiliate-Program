# IGaming Affiliate Program

Self-hosted affiliate program for casinos and sportsbooks: campaigns, CPA and revenue share, S2S postbacks,
payouts, driven purely by events.

Partners sign up in a web cabinet, create **companies** (campaigns) that an admin approves, and send players to
the brand site with their link. The app learns about registrations, deposits, casino spins and sportsbook bets
from [RabbitMQ events](Events) that any platform can publish, attributes players to companies, accrues CPA and
revenue-share commissions into each partner's balance, notifies the partners' trackers with server-to-server
[postbacks](Postbacks), and lets partners request crypto payouts that staff pay out by hand.

It is one Kotlin + Spring Boot service with a Vue single-page app, PostgreSQL and RabbitMQ. The only other service
it calls is [Currency-Exchange-Service](Currency-Conversion), and only for events in a currency other than the
partner currency.

## Pages

- [Getting Started](Getting-Started): the whole stack with `docker compose up`, your first partner and events
- [Configuration](Configuration): every environment variable
- [Architecture](Architecture): components, data model, flows
- [Events](Events): the RabbitMQ contract: exchanges, queues, payloads, idempotency, dead letters
- [Currency Conversion](Currency-Conversion): what is required from Currency-Exchange-Service
- [Commissions](Commissions): attribution, CPA, revenue share, turnover, rollbacks
- [Postbacks](Postbacks): S2S notifications to Keitaro and the SSRF guard
- [API](API): the REST API behind the cabinet
- [Administration](Administration): reviewing companies, blocking partners, paying payouts
- [Branding](Branding): your name, logo, artwork and promo library
- [Deployment](Deployment): the image, migrations, ports, proxies, scaling
- [Development](Development): building, testing, conventions
