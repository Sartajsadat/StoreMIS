# Store MIS — Deployment and Operations Guide

This guide covers installing Store MIS on the store's server PC, connecting the shop PCs, backups, restoring,
updating and day-to-day care. Everything runs in Docker, so the steps are the same on Linux and Windows,
apart from installing Docker itself.

## 1. What you need

| Item | Minimum | Recommended |
|---|---|---|
| Server PC | 4 GB RAM, 2 CPU cores, 40 GB free disk | 8 GB RAM, SSD, 100 GB free disk |
| Operating system | Ubuntu Server 24.04 LTS, or Windows 10/11 Pro with Docker Desktop | Ubuntu Server 24.04 LTS |
| Network | All shop PCs on the same network (router / switch) | Wired connection for the server |
| Server address | A fixed IP address, e.g. `192.168.1.100` (reserve it in the router: "DHCP reservation") | |
| Power | | A UPS, so a power cut does not stop the server in the middle of a sale |
| Backup disk | | A USB disk that stays plugged into the server, plus a second one kept at home |
| Shop PCs | Any PC with Chrome or Edge | Thermal receipt printer (80 mm or 58 mm) on the POS PC |

The server runs four containers:

| Container | Job |
|---|---|
| `web` | nginx: serves the app on port 80 and forwards `/api` to the backend |
| `backend` | Spring Boot application (Java 21) |
| `db` | PostgreSQL 16. It cannot be reached from the network, only by the backend and the backup service |
| `backup` | Nightly backup of the database and the uploaded files, with automatic clean-up of old copies |

## 2. Install on Linux (Ubuntu Server 24.04) — recommended

1. Install Docker and Git:

   ```bash
   sudo apt update
   sudo apt install -y docker.io docker-compose-v2 git
   sudo systemctl enable --now docker
   sudo usermod -aG docker $USER    # then log out and back in
   ```

2. Get Store MIS:

   ```bash
   git clone https://github.com/Sartajsadat/StoreMIS.git
   cd StoreMIS
   ```

3. Install and start:

   ```bash
   bash deploy/scripts/install.sh
   ```

   The script creates `deploy/.env` with a random database password and token key, builds the images
   (the first build takes 5–15 minutes and needs internet), starts everything and prints:
   - the **first admin password** (write it down; you must change it at first sign-in)
   - the address to open on the shop PCs, e.g. `http://192.168.1.100/`

4. Allow the web port through the firewall (only if `ufw` is enabled):

   ```bash
   sudo ufw allow 80/tcp
   ```

Docker starts the containers again automatically after a reboot or power cut (`restart: unless-stopped`).

## 3. Install on Windows (Docker Desktop)

1. Install **Docker Desktop** (it installs WSL 2 if needed) and **Git for Windows**. Restart when asked.
2. In Docker Desktop → Settings → General, tick **Start Docker Desktop when you sign in to your computer**.
3. Open PowerShell and run:

   ```powershell
   git clone https://github.com/Sartajsadat/StoreMIS.git
   cd StoreMIS
   powershell -ExecutionPolicy Bypass -File deploy\scripts\install.ps1
   ```

4. Let the other PCs connect (PowerShell **as Administrator**):

   ```powershell
   New-NetFirewallRule -DisplayName "Store MIS" -Direction Inbound -Protocol TCP -LocalPort 80 -Action Allow
   ```

5. In Windows power settings set **Sleep: Never** for the server PC. Docker Desktop only runs after a user signs
   in, so set the PC to sign in automatically (or use Linux, which needs no sign-in).

If port 80 is already used on the server (for example by IIS or another program), set `HTTP_PORT=8080` in
`deploy\.env`, run `docker compose up -d` in the `deploy` folder, and open `http://<server-ip>:8080/` instead.

## 4. First sign-in and go-live checklist

Open `http://<server-ip>/` and sign in as `admin` with the password the install script printed. You will be
asked to choose a new password. Then, in this order:

1. **Settings → Business**: shop name, address, phone, logo.
2. **Settings → Currency**: today's USD rate.
3. **Settings → Invoice**: receipt format (80 mm / 58 mm / A4), header and footer notes, number prefixes.
4. **Finance → Cash Accounts**: the opening balance of each cash drawer (AFN and USD).
5. **Products → Categories and Units**, then **Products** with their opening stock (quantity and unit cost).
6. **Customers** and **Suppliers** with their opening balances (what they owe / what you owe).
7. **User Management → Users**: one account per staff member, with only the permissions they need.
8. Make a test sale, print the receipt, then cancel the sale (Admin) so the books stay clean.
9. Take a first backup (section 6) and check it appears.

## 5. Shop PCs and printers

- Open `http://<server-ip>/` in Chrome or Edge and bookmark it (or "Install app" / create a desktop shortcut).
- Each user chooses language (English, دری, پښتو) and light/dark theme from the top bar; the choice is remembered.
- **Thermal receipt printer**: install the printer driver, set the paper to 80 mm (or 58 mm) roll. In the
  browser's print window choose the printer, **Margins: None**, **Scale: 100%**, and untick "Headers and footers"
  once; the browser remembers it.
- **PDF reports**: click *PDF*, then choose **Save as PDF** as the printer.

## 6. Backups

Every night at **21:30 Kabul time** the `backup` container saves:

- `storemis.dump` — the whole database (PostgreSQL custom format, checked after it is written)
- `files.tar.gz` — the logo, product images and expense receipts
- `SHA256SUMS` — checksums used to detect a damaged backup

They go to `deploy/backups/daily/<date_time>/`. Fridays are also kept in `weekly/`, the 1st of each month in
`monthly/`. By default 14 daily, 8 weekly and 12 monthly copies are kept; older ones are deleted
automatically. The time and the numbers are set in `deploy/.env` (`BACKUP_CRON`, `KEEP_DAILY`, …).

**Second copy on a USB disk.** Set `EXTERNAL_BACKUP_DIR` in `deploy/.env` and run `docker compose up -d` in `deploy`:

- Linux: mount the disk permanently (for example at `/media/storemis-usb`, with `nofail` in `/etc/fstab`) and set
  `EXTERNAL_BACKUP_DIR=/media/storemis-usb/storemis`.
- Windows: `EXTERNAL_BACKUP_DIR=E:/StoreMIS-backups` (use the disk's letter and forward slashes).

Every backup is then also copied there. **Once a week, copy the latest backup folder to a second USB disk and
take it home**: a fire or theft in the shop must not destroy the only copies.

Useful commands (run in the `deploy` folder):

| Task | Linux | Windows |
|---|---|---|
| Back up now | `bash scripts/backup-now.sh` | `powershell -ExecutionPolicy Bypass -File scripts\backup-now.ps1` |
| List backups and last result | `docker compose exec backup list.sh` | same |
| Result of the last backup | `cat backups/last-backup.txt` | `type backups\last-backup.txt` |

`last-backup.txt` starts with `OK` or `FAILED`; check it every morning, or after any problem.

## 7. Restoring a backup

Restoring **replaces all current data** with the backup. Everyone must stop working first.

```bash
cd deploy
bash scripts/restore.sh                        # lists the backups
bash scripts/restore.sh 2026-11-16_213000      # or: latest
```

Windows: `powershell -ExecutionPolicy Bypass -File scripts\restore.ps1 2026-11-16_213000`

The script asks you to type `YES`, takes a safety backup of the current data, stops the backend, checks the
backup's checksums, restores the database and uploaded files, and starts the backend again. Everyone then signs
in again.

**Restoring on a new server** (the old one is broken): install Store MIS as in section 2/3, copy the backup folder
from the USB disk into `deploy/backups/daily/`, then run the restore script as above. Use the same `deploy/.env`
as before if you still have it; otherwise the new one works too.

Practise a restore once a month on a spare PC, so you know the backups work.

## 8. Updating Store MIS

```bash
cd StoreMIS/deploy
bash scripts/update.sh
```

Windows: `powershell -ExecutionPolicy Bypass -File deploy\scripts\update.ps1`

The script takes a backup, downloads the new version, rebuilds and restarts. Database changes are applied
automatically when the backend starts. Update outside opening hours; it takes a few minutes.

## 9. Day-to-day care

| Task | Command (in `deploy`) |
|---|---|
| Is everything running? | `docker compose ps` (all should be `Up`, web/backend/db `healthy`) |
| Restart everything | `docker compose restart` |
| Stop / start | `docker compose stop` / `docker compose up -d` |
| Backend log (last 200 lines) | `docker compose logs --tail=200 backend` |
| Disk space used by Docker | `docker system df` |
| Free space from old images after updates | `docker image prune -f` |

Logs are rotated automatically (at most about 100 MB for the backend).

## 10. Troubleshooting

| Problem | What to do |
|---|---|
| Shop PCs cannot open the address | Is the server on? `docker compose ps`. Can the server open `http://localhost/`? Check the firewall (section 2 step 4 / section 3 step 4) and that the PCs use the server's current IP. |
| "Port is already allocated" when starting | Another program uses port 80. Set `HTTP_PORT=8080` in `.env`, `docker compose up -d`, use `http://<ip>:8080/`. |
| The page opens but sign-in fails with a server error | `docker compose logs --tail=200 backend`. The backend refuses to start if `.env` still has example secrets; run the install script again or fix `.env`. |
| Forgot the admin password | If another admin exists, they can reset it in **User Management**. Otherwise restore a backup taken when the password was known, or ask the developer for a reset. |
| `last-backup.txt` says FAILED | `docker compose logs --tail=50 backup`. Usually the disk is full: free space or lower `KEEP_DAILY`. |
| Receipt prints too small / with margins | In the print window: Margins **None**, Scale **100%**, correct paper size. |
| The time on documents is wrong | The app always uses Kabul time; check the server clock is correct. |
| After a power cut something is not `healthy` | Wait two minutes, then `docker compose restart`. |

## 11. Security notes

- Keep `deploy/.env` private. It contains the database password and the key that signs sign-in tokens.
- Only the web port is open to the network; the database is reachable only inside Docker.
- Accounts lock for 15 minutes after 5 wrong passwords. Each staff member must have their own account.
- Remove the accounts of staff who leave (User Management → Deactivate).
- The app is meant for the shop's local network. Do not forward the port to the internet from the router.
  If remote access is ever needed, use a VPN, and serve the app over HTTPS (then set `COOKIE_SECURE=true`).
