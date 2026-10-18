# Administration

Admins use the same cabinet; the Admin section appears for an account with the role `ADMIN`.

## In the cabinet

- **Review queue**: every partner's companies, filtered by status and searched by identity or partner login. An
  admin sets a company's final terms (amount or percent, by the same rules a partner proposes them) and its status:
  `ACTIVE` to start attributing and earning, `DISABLED` to stop, `IN_REVIEW` to put it back.
- **Partners**: block a partner (their sessions stay, but every request answers `ACCOUNT_BLOCKED`, their companies
  stop attributing and earning) or unblock them. An admin cannot block their own account.

## In the database

Some actions are deliberately not in the API. Run them with `psql` against the app's database (with the compose
stack: `docker compose exec postgres psql -U postgres -d affiliate`).

Make an admin (registration only ever creates partners):

```sql
UPDATE account SET role = 'ADMIN', updated_at = now() WHERE login = 'alice';
```

Mark a payout `PAID` once the transfer is done (expect `UPDATE 1`):

```sql
UPDATE payout SET status = 'PAID', tx_hash = '<transaction hash>', updated_at = now()
WHERE id = 42 AND status = 'PENDING';
```

Reject a payout and give the reserved amount back, in one transaction (expect `UPDATE 1`; `UPDATE 0` means the
payout was not `PENDING` and nothing changed). The comment is shown to the partner:

```sql
BEGIN;
WITH rejected AS (
    UPDATE payout SET status = 'REJECTED', comment = 'The address is not a TRC20 address', updated_at = now()
    WHERE id = 42 AND status = 'PENDING'
    RETURNING account_id, amount
)
UPDATE account a SET balance = a.balance + r.amount, updated_at = now()
FROM rejected r WHERE a.id = r.account_id;
COMMIT;
```

## Dead letters

Events that failed wait in `affiliate.dead`. Find the cause in the `broker.handler_failed` log line, fix it, and
move them back as described in [Events](Events#replaying-dead-letters).
