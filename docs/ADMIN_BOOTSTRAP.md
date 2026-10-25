# Trusted administrator bootstrap

RailReserve deliberately has no default administrator account or password. Public registration can create only CUSTOMER accounts. Bootstrap the first administrator only in a trusted development/operations environment, then sign in again so the new JWT contains the ADMIN role.

1. Start the local stack and register the intended administrator using `POST /api/v1/auth/register`.
2. Connect to the auth database as a trusted database administrator. Never expose the database port publicly.
3. Promote the intended account by its unique email:

```sql
UPDATE auth_users
SET role = 'ADMIN'
WHERE email = 'your-admin-email@example.com'
  AND active = TRUE;
```

4. Verify exactly one row was updated, then log in again and use the newly issued token.
5. Remove temporary database access and record the action in your operations audit process.

Do not hard-code an administrator password, promote arbitrary accounts through a public endpoint, or put database credentials in scripts or Git. In production, use a reviewed provisioning workflow with MFA/SSO, least privilege and audit logging.

## Initial administration APIs

All of these endpoints require an RS256 access token whose role claim is ADMIN:

- `POST /api/v1/admin/stations`
- `POST /api/v1/admin/trains`
- `POST /api/v1/admin/trains/{trainId}/stops`
- `POST /api/v1/admin/trains/{trainId}/service-dates`
- `POST /api/v1/admin/inventory`

A train search result is available only when station/train/stop data and a SCHEDULED service-date row exist. Booking requires seat inventory with a positive server-configured fare for the selected train/date. This administration API is a foundation; add approval workflows, audit trails, bulk import validation and independent authorization tests before production.
