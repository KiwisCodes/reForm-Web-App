# Architectural Specification: Docker & Infrastructure Orchestration
**Document Version:** 1.0  
**Target Platform:** Reform (Modular Monolith)  
**Author:** Senior Technical Lead

---

## 1. What is Docker, and Why Do We Need It?

Before Docker, developers suffered from the **"It Works on My Machine"** syndrome.

If Teammate A installed PostgreSQL 15 directly on macOS using Homebrew, and Teammate B installed PostgreSQL 13 on Windows using an `.exe` installer, their database behaviors, SQL dialects, and connection ports would differ. Setting up local caching systems like **Redis** on Windows was notoriously difficult.

### The Solution: Containerization
Docker solves this by packaging software (like PostgreSQL or Redis) along with its entire operating system, libraries, and configurations into a lightweight, isolated box called a **Container**.

```text
       TRADITIONAL VIRTUAL MACHINES (Heavy)        LIGHTWEIGHT DOCKER CONTAINERS
       
   ┌─────────────────────────────────────────┐     ┌─────────────────────────────────────────┐
   │  App A  │  App B  │  App C              │     │  App A  │  App B  │  App C              │
   ├─────────────────────────────────────────┤     ├─────────────────────────────────────────┤
   │  Guest OS │  Guest OS │  Guest OS       │     │  Docker Daemon (Shared OS Kernel)       │
   ├─────────────────────────────────────────┤     ├─────────────────────────────────────────┤
   │  Hypervisor (Virtual Hardware)          │     │  Host Operating System (macOS/Windows)  │
   ├─────────────────────────────────────────┤     └─────────────────────────────────────────┘
   │  Host Operating System (macOS/Windows)  │     (Fast, boots in milliseconds, uses 1/10th
   └─────────────────────────────────────────┘      of the RAM of a Virtual Machine)
```

A Docker container runs **exactly the same** on your local machine, your teammate's computer, a staging server, and AWS production.

---

## 2. What is Docker Compose?

If a **Docker Container** is a single shipping container, **Docker Compose** is the cargo ship orchestrator.

Instead of writing long, complex terminal commands to start each individual container, configure their networks, and link their storage, you write a single blueprint file: **`docker-compose.yml`**.

With this file, you can spin up your entire database and cache infrastructure with **one single command**:

```bash
docker-compose up -d
```

---

## 3. Line-by-Line Breakdown of Your `docker-compose.yml`

Let us dissect every single block of your infrastructure file so you understand exactly what it is doing.

### The Database Service (`db`)

```yaml
  db:
    container_name: reform-postgres
    image: postgres:15-alpine
```
*   **`image: postgres:15-alpine`**: Tells Docker to download the official PostgreSQL version 15 image built on **Alpine Linux** (a security-focused, ultra-lightweight Linux distribution that makes the download size very small).

```yaml
    environment:
      POSTGRES_USER: ${POSTGRES_USER:-postgres}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-password}
      POSTGRES_DB: ${POSTGRES_DB:-reform_db}
      PGDATA: /data/postgres
```
*   **`${VAR:-default}`**: Tells Docker to look for an environment variable inside your local `.env` file. If it cannot find it, use the fallback value after the `:-` symbol.
*   **`PGDATA`**: Configures PostgreSQL's internal storage engine to write data files to `/data/postgres` inside the container.

```yaml
    volumes:
      - db:/data/postgres
```
*   **Volumes (The Persistence Guard):** Containers are **ephemeral** (meaning if you delete the container, all data inside it is wiped out). This line maps a persistent storage folder named `db` on your computer directly to `/data/postgres` inside the container, ensuring your user data is saved even if you restart your computer.

```yaml
    ports:
      - "5332:5432"
```
*   **Port Mapping (`Outside:Inside`):** PostgreSQL internally runs on port `5432`. This line maps port **`5332` on your actual computer (outside)** to **`5432` inside the isolated container**. Your Spring Boot application will connect to the database via port `5332`.

```yaml
    networks:
      - reform-internal-network
    restart: unless-stopped
```
*   **Networks:** Puts this container inside a secure, private virtual network so it can talk directly to Redis.
*   **`restart: unless-stopped`**: Tells Docker to automatically reboot this database if it crashes, unless you explicitly tell it to stop.

---

### Advanced Database Tuning

```yaml
    healthcheck:
      test: [ "CMD-SHELL", "pg_isready -U $$POSTGRES_USER -d $$POSTGRES_DB" ]
      interval: 5s
      timeout: 5s
      retries: 5
```
*   **Health Checking:** Every 5 seconds, Docker runs the native Postgres command `pg_isready` inside the container. If the database crashes or gets locked up, Docker flags it as "Unhealthy" so you can monitor it.

```yaml
    deploy:
      resources:
        limits:
          cpus: '0.50'
          memory: 512M
```
*   **Resource Limits:** Prevents the database from hogging your entire computer's memory. This limits Postgres to using a maximum of **50% of a single CPU core** and **512 Megabytes of RAM**.

```yaml
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"
```
*   **Log Rotation:** Database logs can grow to hundreds of gigabytes over time. This configuration limits logs to **3 files of 10 Megabytes each**, automatically deleting old logs to save your hard drive space.

---

### The Redis Cache Service (`redis`)

```yaml
  redis:
    container_name: reform-redis
    image: redis:7-alpine
    command: redis-server --save 60 1 --loglevel warning
```
*   **`image: redis:7-alpine`**: Downloads Redis version 7 running on Alpine Linux.
*   **`command`**: Configures Redis's persistence engine. `--save 60 1` tells Redis to write its memory state to the hard drive **every 60 seconds if at least 1 key changes**, ensuring we don't lose active user sessions if the cache restarts.

```yaml
    deploy:
      resources:
        limits:
          cpus: '0.25'
          memory: 256M
```
*   **Resource Limits:** Since Redis is single-threaded and highly efficient, we limit it to **25% of a CPU core** and **256 Megabytes of RAM**.

---

## 4. How to Run & Control Your Containers (The Quick Tutorial)

Follow this workflow to control your local infrastructure.

```text
                  THE INFRASTRUCTURE LIFECYCLE
                  
  [ Start Services (Background) ] ──>  docker-compose up -d
                │
                ▼ (To check status)
  [ View Running Containers ]     ──>  docker-compose ps
                │
                ▼ (To stop & clean up)
  [ Stop Services ]               ──>  docker-compose down
```

### 1. Launch Your Infrastructure (Local Development)
Open your terminal at the root of your project (where `docker-compose.yml` is located) and run:
```bash
docker-compose up -d
```
*   **`-d` (Detached Mode):** Runs the containers in the background, freeing up your terminal window.

### 2. Verify They Are Running
To see if your database and cache are alive and healthy, run:
```bash
docker-compose ps
```
You should see both `reform-postgres` and `reform-redis` listed with a status of `Up (healthy)`.

### 3. View Real-Time Logs
If you want to see what PostgreSQL or Redis are doing under the hood:
```bash
docker-compose logs -f
```
*   **`-f` (Follow):** Streams logs in real-time as database queries occur.

### 4. Stop Your Infrastructure
When you are done coding for the day, safely stop and park your databases by running:
```bash
docker-compose down
```
*Note: Because of our declared **Volumes**, your data is safely saved. When you run `docker-compose up -d` tomorrow, all your registered users and workspaces will still be there!*

---

## 🏁 5. Socratic Review

To verify your system infrastructure knowledge, analyze these two scenarios:

1.  **The Port Mapping Dilemma:** Your PostgreSQL port mapping is `"5332:5432"`.
    *   *Question:* If your local Spring Boot application wants to connect to PostgreSQL, which port must you write in your `application.yml` file? Port `5332` or Port `5432`? Why?
2.  **The Volume Safeguard:** If you run `docker-compose down -v` (which deletes the mapped volumes), what will happen to the data you saved in your database when you start the containers back up? Why is protecting your volumes critical in production?