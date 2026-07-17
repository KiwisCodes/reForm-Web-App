# Postgres Port Conflict: Native Windows Install vs. Docker

## Problem

Spring Boot failed to start with:

```
FATAL: password authentication failed for user "postgres"
```

This cascaded into a chain of bean-creation failures — `entityManagerFactory` →
`userRepository` → `customerUserDetailService` → `securityConfig` → `Unable to
start web server`. All of that noise was just dependency-injection fallout;
the one real error was the Postgres auth failure at the bottom of the stack.

## Why It Was Happening

This machine has a **native PostgreSQL 18 Windows service**
(`postgresql-x64-18`) permanently bound to port 5432 on the host. Confirmed
with:

```powershell
Get-NetTCPConnection -LocalPort 5432 -State Listen
Get-CimInstance Win32_Process -Filter "ProcessId = <pid>"
```

`docker-compose.yml` was mapping the containerized Postgres to that same host
port (`5432:5432`). On Windows, when the native service already owns a port,
Docker's port-forwarding never actually reaches the host side of that port —
but the container's own internal healthcheck (`pg_isready`, executed via
`docker exec` inside the container's network namespace) still reports
`healthy`, since it never touches the host port mapping at all. So
`docker-compose ps` looked completely fine and gave false confidence.

The result: the Java app, launched from IntelliJ as a native Windows process
connecting to `localhost:5432`, was always talking to the **native Postgres 18
install**, not the Docker container — and the native install's `postgres`
password isn't `password` (the value `application.yml` assumed).

### Misdiagnosis along the way

Two earlier fix attempts failed because verification was done incorrectly:

1. Assumed it was just a port-mapping mismatch between `application.yml`
   (`localhost:5432`) and `docker-compose.yml` (`5332:5432`) — a real
   inconsistency, but not the root cause.
2. Assumed the Docker volume was initialized with stale credentials (Postgres
   only applies `POSTGRES_PASSWORD` on first init of an empty data dir), so
   deleted and recreated the volume.

Both times, verification used:

```
docker exec reform-postgres psql -h 127.0.0.1 -U postgres -d reform_db ...
```

This connects through the **container's own loopback**, entirely bypassing
the host-published port. It kept "passing" while the actual host-side
connection kept failing — a false-positive check that hid the real problem.

## The Fix

Moved the Docker Postgres container off the contested port entirely:

- `docker-compose.yml`: `"5432:5432"` → `"5433:5432"`
- `application.yml`: datasource URL `localhost:5432` → `localhost:5433`

Verified correctly this time by connecting from the **actual Windows host**
(not from inside the container) using the native `psql.exe` client:

```powershell
$env:PGPASSWORD="password"
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -p 5433 -U postgres -d reform_db -c "SELECT version();"
```

This returned the container's Postgres 15 version string, confirming the
connection path the Java app actually uses now works end-to-end.

## Why This Approach

Three options were considered:

1. **Stop/disable the native Postgres service** — requires admin privileges
   and could break other local projects relying on that install. Riskier and
   out of scope for a dev-environment fix.
2. **Use the native Postgres instead of Docker** — would mean abandoning the
   docker-compose-managed dev setup (disposable volume, `ddl-auto:
   create-drop`) for a shared system install. More invasive, harder to reset
   cleanly.
3. **Move Docker to an unused port (5433)** — smallest, least destructive
   change. Touches only this project's two config files, leaves the native
   install and Docker volume untouched. Confirmed 5433 was free
   (`Get-NetTCPConnection -LocalPort 5433`) before switching.

Option 3 was chosen.

## Lesson

When verifying a fix for a host-port-mapping issue, always test from the
**actual client's network perspective** (here: the Windows host, via a
native client), not from inside the container. `docker exec` and container
healthchecks only prove the container is healthy internally — they say
nothing about whether the host-published port is actually reachable.
