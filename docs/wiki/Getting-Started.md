# Getting Started

## Run the whole stack

Requirements: Docker with Compose v2. The first build compiles the app (Node 22 and JDK 21 inside Docker) and
[Currency-Exchange-Service](Currency-Conversion) from its public repository (Rust), which takes several minutes.

    git clone https://github.com/nekzabirov/IGaming-Affiliate-Program.git
    cd IGaming-Affiliate-Program
    docker compose up --build -d

| What | Where |
|---|---|
| Partner cabinet and `/api` | http://localhost:8080 |
| Metrics, health | http://localhost:9464/metrics, http://localhost:9464/health |
| RabbitMQ management (guest / guest) | http://localhost:15680 |
| PostgreSQL (postgres / postgres) | localhost:55450, database `affiliate` |
| Currency-Exchange-Service gRPC | localhost:59090 |

`docker compose ps` shows `migrate` and the `currency-*` jobs as exited (0): they run once and stop.

## Your first partner, company and events

1. Open http://localhost:8080 and **create an account** (a login of 3 to 32 characters, a password of 8 to 72).
2. **Make it an admin** (registration only ever creates partners):

       docker compose exec postgres psql -U postgres -d affiliate \
         -c "UPDATE account SET role = 'ADMIN', updated_at = now() WHERE login = 'you'"

   Reload the page: the Admin section appears.
3. **Create a company** under Companies: identity `summer`, CPA, amount `25`. It starts `IN_REVIEW`.
4. **Approve it** under Admin, Review queue: set it ACTIVE. Its partner link is
   `http://localhost:3000/?affiliate=partner:summer&click_id={subid}` (`SITE_URL` is the brand site).
5. **Publish the sample events** (bash and curl):

       scripts/publish-sample-events.sh

   They register player 1001 with the tag `partner:summer`, a 50 USDT deposit, a 2 USDT casino stake and a
   10 USDT sportsbook stake. The dashboard now shows one registration and one first deposit, and the balance is
   25 USDT: the CPA, paid because the first deposit reached `CPA_MIN_DEPOSIT` (10).
6. **Request a payout** under Payouts, then mark it paid as described in [Administration](Administration).

What each event does, and how to write your own: [Events](Events).

## Run without Docker for the app

For development, run only the infrastructure in Docker and the app from Gradle; see [Development](Development).
