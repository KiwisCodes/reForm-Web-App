# Docker Port Mapping: What `HOST:CONTAINER` Actually Means

Follow-up notes to [02-postgres-port-conflict-native-vs-docker.md](./02-postgres-port-conflict-native-vs-docker.md),
digging into the underlying networking concepts that made that bug possible.

## The core misconception

`"5432:5432"` in `docker-compose.yml` does **not** mean "connect the host to
itself." The two numbers name two *different* places that just happen to
share a number:

- **Left side (`5432` before the colon)** — a port on the **host's** real
  network interface/stack.
- **Right side (`5432` after the colon)** — a port inside the **container's**
  own private, isolated network namespace.

You could just as easily write `5433:5432` (which is what the fix in
02-postgres-port-conflict... did) — the numbers are independent; they only
match by choice, not by requirement.

## Bind, listen, claim — same mechanism, different words

A process must **bind** a port before it can **listen** on it:

1. `bind()` — the process asks the OS's networking stack to register it as
   the exclusive owner of a given port on a given network interface. The OS
   keeps a table of these claims (what `Get-NetTCPConnection -LocalPort 5432
   -State Listen` reads from).
2. `listen()` — once bound, the process can accept incoming connections on
   that port.

Only one process can hold a bind on a given host port + interface at a time.
A second process's bind attempt on the same port is rejected outright
(`address already in use`) — the OS has no way to route an incoming
connection to two different owners.

"Claiming" a port = successfully completing this bind. It's an entry in an
OS-level table, not a physical action.

## Docker's proxy is the "process" doing the claiming

Docker doesn't magically bridge host and container ports — a real mechanism
does it on Docker's behalf:

- On Linux: either a userspace `docker-proxy` process (one per published
  port) or kernel-level `iptables`/netfilter DNAT rules.
- On Windows (Docker Desktop): an added layer, since the daemon and
  containers actually run inside a WSL2 Linux VM — so there's a relay from
  the real Windows host, into that VM, then into the container.

Whichever mechanism, it plays the same three-step role as any other process
that wants to accept host-side connections:

1. `bind()` the host-side port (`5432`)
2. `listen()` for incoming connections there
3. Forward/relay each accepted connection into the container's isolated
   network namespace, where the container's own process (Postgres) is
   separately bound and listening on its own `5432`.

So a working mapping is really **two chained listeners**, not one:

```
incoming connection → host:5432  (Docker's proxy is the listener here)
                            │
                            ▼
                  Docker forwards/relays it
                            │
                            ▼
                container:5432  (actual Postgres, listening inside its own namespace)
```

## What actually broke, visualized

```
                         WINDOWS HOST — one shared network stack
   ┌──────────────────────────────────────────────────────────────────────┐
   │                                                                      │
   │   Native Postgres 18 service          Docker Desktop's proxy         │
   │   binds host port 5432  ◄───WINS───┐  tries to bind host port 5432  │
   │   (was already running first)      └──LOSES / never gets to exist   │
   │           ▲                            as a listener                │
   │           │ localhost:5432                                          │
   │           │                                                          │
   │   Java app (IntelliJ, native Windows process)                       │
   │   connects to localhost:5432 ────────┘                              │
   │   → always lands on the NATIVE Postgres, never the container         │
   │                                                                      │
   │   ┌────────────────────────────────────────────────────────────┐    │
   │   │  Docker container — separate, isolated network namespace   │    │
   │   │                                                              │    │
   │   │  Container Postgres 15 listens on container port 5432       │    │
   │   │  (this part works fine — nothing conflicts in here)         │    │
   │   │                                                              │    │
   │   │  healthcheck (pg_isready) runs via `docker exec`,           │    │
   │   │  i.e. FROM INSIDE this namespace → always reaches           │    │
   │   │  container:5432 directly → reports "healthy"                │    │
   │   │  → never touches the host-side conflict at all              │    │
   │   └────────────────────────────────────────────────────────────┘    │
   │           ▲                                                          │
   │           │  the mapping that never actually completed              │
   │           │  because Docker's proxy lost the host-side bind         │
   │           x                                                          │
   │   (nothing on the host side successfully forwards into here)        │
   └──────────────────────────────────────────────────────────────────────┘
```

Two verification paths, two different results:

- `docker exec reform-postgres psql ...` — arrow stays entirely inside the
  container's namespace → always "works" → false confidence.
- Java app / native `psql.exe` at `localhost:5432` — arrow stays entirely on
  the host side, hits the native service → the real symptom (wrong password)
  surfaces here.

## Takeaway

When a Docker port mapping "isn't working," the question to ask first is:
**did Docker's proxy ever actually win the bind on the host-side port?** If
something else already claimed that host port, the container-side listener,
the container's own healthcheck, and `docker exec`-based verification will
all report fine — because none of them ever cross the host boundary where
the actual conflict lives. Always verify from the real client's network
perspective (here: the Windows host, via a native client), not from inside
the container.
