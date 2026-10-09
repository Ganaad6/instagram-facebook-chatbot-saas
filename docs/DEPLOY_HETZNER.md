# Deploying to Hetzner Cloud – runbook

Step by step from an empty Hetzner account to ChatShop running on `https://YOUR-DOMAIN`, with
nightly off-server backups. Takes about an hour, most of it waiting for DNS and the first build.
The general deployment notes are in the [README](../README.md#deploying-to-production); this is
the concrete version for one Hetzner server.

Throughout: `YOUR-DOMAIN` is the dashboard's domain (e.g. `app.example.mn`), `SERVER-IP` the
server's IPv4 address, and commands marked **(server)** run on the server as the `deploy` user.

**What it costs** (approximate, check current prices): server ~€5–8/month, Hetzner Backups +20% of
that, Storage Box for off-server dumps ~€4/month, plus the domain.

## Contents
1. [Before you start](#1-before-you-start)
2. [Create the server](#2-create-the-server)
3. [Firewall](#3-firewall)
4. [Secure the server](#4-secure-the-server)
5. [Install Docker](#5-install-docker)
6. [DNS](#6-dns)
7. [Get the code](#7-get-the-code)
8. [Configure `.env`](#8-configure-env)
9. [Start](#9-start)
10. [Point the Meta app at the server](#10-point-the-meta-app-at-the-server)
11. [Backups](#11-backups)
12. [Monitoring](#12-monitoring)
13. [Upgrading](#13-upgrading)
14. [Disaster recovery](#14-disaster-recovery)
15. [Troubleshooting](#15-troubleshooting)

## 1. Before you start

- [ ] A **domain** you control. A subdomain is fine (`app.example.mn`). If the DNS is on
      Cloudflare, the record must be **DNS only** (grey cloud), not proxied.
- [ ] An **SSH key** on your laptop (`ls ~/.ssh/id_ed25519.pub`; create one with
      `ssh-keygen -t ed25519` if missing).
- [ ] The **Meta app** id and secret (developers.facebook.com → your app → App settings → Basic).
- [ ] The branch you deploy is pushed to GitHub. `main` is currently behind the working branch.
      Merge first, or deploy the working branch by name in step 7.

## 2. Create the server

In [console.hetzner.cloud](https://console.hetzner.cloud): create a project (e.g. `chatshop`),
then **Add Server**:

| Setting | Choose |
|---|---|
| Location | Helsinki or Falkenstein/Nuremberg (EU, cheapest). Singapore works too but costs more and has less included traffic. |
| Image | **Ubuntu 24.04** |
| Type | **Shared vCPU, x86**: 2 vCPU / **4 GB RAM** / 40 GB disk (CX22 or its current successor). 2 GB is too little for the Java app, Postgres and the image build together. |
| Networking | Public IPv4 **on** (Meta and Let's Encrypt need it), IPv6 on |
| SSH keys | Add your `~/.ssh/id_ed25519.pub` |
| Backups | **On**. Daily disk snapshots, kept 7 days. A second layer next to the database dumps in step 11. |
| Name | `chatshop-1` |

Note the server's IPv4 address. That's `SERVER-IP`.

## 3. Firewall

Use a **Hetzner Cloud Firewall**, not `ufw`. Docker writes its own iptables rules and bypasses
`ufw` for published ports. A cloud firewall sits outside the server, so Docker can't open a hole
in it.

Console → **Firewalls → Create Firewall** → inbound rules:

| Protocol | Port | Source |
|---|---|---|
| TCP | 22 | Your own IP if it is fixed, otherwise any |
| TCP | 80 | Any IPv4, Any IPv6 (Let's Encrypt and the HTTP→HTTPS redirect) |
| TCP | 443 | Any IPv4, Any IPv6 |
| UDP | 443 | Any IPv4, Any IPv6 (HTTP/3, optional) |
| ICMP | – | Any (ping, optional) |

Leave outbound open. Apply it to `chatshop-1`. Postgres (5432) and the app (8080) are bound to
`127.0.0.1` by `docker-compose.yml` and are never reachable from outside anyway.

## 4. Secure the server

From your laptop:

```bash
ssh root@SERVER-IP
```

As root, create a normal user with your SSH key and sudo:

```bash
adduser --gecos "" deploy                      # asks for a password, used for sudo
usermod -aG sudo deploy
install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
install -m 600 -o deploy -g deploy /root/.ssh/authorized_keys /home/deploy/.ssh/authorized_keys
```

**In a second terminal**, check that `ssh deploy@SERVER-IP` works and `sudo -v` accepts the
password. Only then turn off root and password logins:

```bash
cat > /etc/ssh/sshd_config.d/99-chatshop.conf <<'EOF'
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
EOF
sshd -t && systemctl reload ssh
```

From now on everything is **(server)** as `deploy`:

```bash
ssh deploy@SERVER-IP
sudo apt-get update && sudo apt-get -y upgrade
# Security updates install automatically (Ubuntu default). Confirm it is enabled:
sudo dpkg-reconfigure -plow unattended-upgrades
# Hetzner images have no swap. 2 GB keeps the image build from being OOM-killed
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
sudo timedatectl set-timezone Asia/Ulaanbaatar    # cron times and logs in local time
sudo reboot                                        # if the upgrade installed a new kernel
```

## 5. Install Docker

**(server)** Use Docker's own apt repository. Not the snap package: it has a private `/tmp` and
other quirks that broke the smoke test locally.

```bash
sudo apt-get install -y ca-certificates curl git
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Cap container logs. Docker keeps them forever by default and fills the disk
sudo tee /etc/docker/daemon.json > /dev/null <<'EOF'
{ "log-driver": "json-file", "log-opts": { "max-size": "20m", "max-file": "5" } }
EOF
sudo systemctl restart docker

# Lets deploy run docker without sudo. Note: the docker group is root-equivalent
sudo usermod -aG docker deploy
exit        # log out and back in for the group to apply
```

Check: `ssh deploy@SERVER-IP 'docker compose version'`.

## 6. DNS

At your DNS provider add an **A record**: `YOUR-DOMAIN` → `SERVER-IP` (TTL 300 or "auto").
Skip the AAAA (IPv6) record for now. IPv4 is enough, and it avoids Docker IPv6 surprises.

Wait until it resolves before step 9. Caddy requests the HTTPS certificate on the first start
and Let's Encrypt rate-limits repeated failures:

```bash
dig +short YOUR-DOMAIN        # must print SERVER-IP
```

## 7. Get the code

The repository is private, so give the server a read-only **deploy key**. **(server)**:

```bash
ssh-keygen -t ed25519 -f ~/.ssh/github_deploy -N "" -C "chatshop-1 deploy key"
cat >> ~/.ssh/config <<'EOF'
Host github.com
  IdentityFile ~/.ssh/github_deploy
  IdentitiesOnly yes
EOF
cat ~/.ssh/github_deploy.pub
```

On GitHub: repository → **Settings → Deploy keys → Add deploy key**, paste the key and leave
"Allow write access" **off**. Then:

```bash
git clone git@github.com:Ganaad6/instagram-facebook-chatbot-saas.git ~/chatshop
cd ~/chatshop
git checkout main             # or the branch you deploy, see step 1
```

Optional but worthwhile: run the full-stack smoke test once on the server before configuring.
It needs ports 80/443 free, so do it now, before step 9:

```bash
scripts/smoke-test.sh         # builds the images (several minutes), tests, tears down
```

## 8. Configure `.env`

**(server)** in `~/chatshop`:

```bash
cp .env.example .env
chmod 600 .env
# Fresh secrets. Paste each into .env in place of the placeholder
for v in POSTGRES_SUPERUSER_PASSWORD APP_DB_PASSWORD OAUTH_STATE_SECRET ADMIN_PASSWORD WEBHOOK_VERIFY_TOKEN; do
  echo "$v=$(openssl rand -hex 24)"
done
echo "ENCRYPTION_SECRET_KEY=$(openssl rand -hex 16)"     # exactly 32 characters
nano .env
```

Set at least:

| Variable | Value |
|---|---|
| `DOMAIN` | `YOUR-DOMAIN` |
| `BASE_URL` | `https://YOUR-DOMAIN` |
| `CORS_ALLOWED_ORIGINS` | `https://YOUR-DOMAIN` |
| `SPRING_PROFILES_ACTIVE` | `prod` (refuses to start with weak or template secrets and lists what to fix) |
| the six secrets above | the generated values |
| `ADMIN_USERNAME` | something other than `admin` |
| `META_APP_ID`, `META_APP_SECRET` | from the Meta app |
| `META_OAUTH_SCOPES` | see [META_APP_REVIEW.md §1](META_APP_REVIEW.md#1-before-you-submit). Add `business_management` if Pages are owned by a Business portfolio. Drop the `instagram_*` scopes if Meta answers "Invalid Scopes" before the Instagram use case is set up. |
| `QPAY_API_URL` | `https://merchant.qpay.mn/v2` (production) |
| `LEGAL_OPERATOR_NAME`, `LEGAL_CONTACT_EMAIL` | `ChatShop` and the contact email (required by `prod`) |
| `SIGNUP_ENABLED` | `true` for self-serve sign-up, `false` to onboard shops yourself |

**Save a copy of the finished `.env` in your password manager now.** Without the same
`ENCRYPTION_SECRET_KEY`, a restored database can't decrypt the stored Meta Page tokens and QPay
passwords, and every shop has to reconnect. The database backups in step 11 don't include it.

## 9. Start

**(server)**:

```bash
cd ~/chatshop
docker compose up -d --build          # first build: 5–10 minutes
docker compose logs -f app            # wait for "Started ChatbotSaasApplication", then Ctrl+C
docker compose ps                     # postgres, app and caddy all "healthy"/"running"
docker compose logs caddy | grep -i certificate    # "certificate obtained successfully"
```

From your laptop:

```bash
curl -fsS https://YOUR-DOMAIN/actuator/health      # {"status":"UP"}
```

Open `https://YOUR-DOMAIN`. You should see the dashboard login. Open `/privacy` and check that
the operator name and email are shown.

## 10. Point the Meta app at the server

In developers.facebook.com, set every URL from
[META_APP_REVIEW.md §2–3](META_APP_REVIEW.md#2-app-settings--basic) to `https://YOUR-DOMAIN`:
OAuth redirect URI, deauthorize and data-deletion callbacks, privacy/terms URLs, app domain,
and the **webhook callback** `https://YOUR-DOMAIN/webhook` for both the Page and Instagram
objects. Use the `WEBHOOK_VERIFY_TOKEN` from the server's `.env`. Remove the old
`trycloudflare.com` URLs.

Then:

1. Sign up (or onboard) a shop on `https://YOUR-DOMAIN` and connect its Facebook Page under
   **Тохиргоо → Холболт**. The production database is new, so Pages connected to your local
   stack must be connected again here.
2. Send the Page a message from a personal account with a role on the app. The bot should answer
   and the chat should appear in the dashboard.

A Meta app has one webhook URL. From now on your local stack gets no messages through this app.
For local development later, create a separate test app in Meta.

## 11. Backups

Two layers: Hetzner Backups (whole-disk snapshots, enabled in step 2) and nightly database dumps
copied to a **Storage Box**, which is separate from the server's disk.

**Storage Box:** Hetzner console → **Storage Boxes → Create**, smallest size (BX11), same
location. In its settings enable **SSH support** and set a password. Note the user (`uXXXXXX`)
and host (`uXXXXXX.your-storagebox.de`).

**(server)**:

```bash
sudo apt-get install -y rclone
sudo install -d -o deploy -g deploy -m 700 /var/backups/chatshop
rclone config
#   n (new remote) → name: storagebox → type: sftp
#   host: uXXXXXX.your-storagebox.de   user: uXXXXXX   port: 23
#   password: y, enter the Storage Box password. Accept the defaults for the rest
rclone mkdir storagebox:chatshop

cd ~/chatshop
RCLONE_REMOTE=storagebox:chatshop scripts/backup.sh     # → "Backup OK: ..."
rclone ls storagebox:chatshop                          # the dump is there
```

Schedule it nightly at 03:00 with `crontab -e`:

```cron
0 3 * * * cd /home/deploy/chatshop && RCLONE_REMOTE=storagebox:chatshop scripts/backup.sh >> /home/deploy/backup.log 2>&1
```

`scripts/backup.sh` keeps 14 days of dumps on the server and 60 days on the Storage Box
(`KEEP_DAYS`, `REMOTE_KEEP_DAYS`). Glance at `~/backup.log` now and then. A backup nobody checks
eventually turns out to be empty.

**Test a restore once** before relying on it (see [§14](#14-disaster-recovery)). A throwaway
Hetzner server for an hour costs a few cents.

## 12. Monitoring

- **Uptime:** a free monitor at UptimeRobot or Better Stack on
  `https://YOUR-DOMAIN/actuator/health`, keyword `UP`, alerting to your phone or email.
- **Disk:** `df -h /` now and then. Product photos live in Postgres, and old images pile up
  after upgrades (step 13 prunes them).
- **Logs:** `docker compose logs --since 1h app`.

## 13. Upgrading

**(server)**:

```bash
cd ~/chatshop
scripts/backup.sh                     # fresh dump before every upgrade
git pull
docker compose up -d --build          # migrations run on start; sessions survive
docker compose logs -f app            # "Started ChatbotSaasApplication"
docker image prune -f                 # remove the previous images
docker builder prune -f --filter until=168h
```

If the new version misbehaves: `git checkout <previous commit>` and `docker compose up -d --build`.
If a database migration was the problem, restore the pre-upgrade dump (§14).

## 14. Disaster recovery

The server is gone or the database is broken. You need the latest dump (Storage Box) and the
`.env` from your password manager.

1. On a new server, follow steps 2–7, then put the saved `.env` in `~/chatshop` (`chmod 600`).
   Point DNS at the new IP if it changed.
2. Start **only Postgres**, so the app doesn't create an empty schema first, and restore:
   ```bash
   docker compose up -d postgres
   rclone copy storagebox:chatshop/chatbot_saas-YYYY-MM-DD-HHMM.dump .
   docker compose exec -T postgres pg_restore -U postgres -d chatbot_saas < chatbot_saas-*.dump
   docker compose up -d --build
   ```
3. Check §9 again. Shops stay connected, because the same `ENCRYPTION_SECRET_KEY` decrypts their
   tokens.

To restore over a running server's database instead, stop the app first and add `--clean`:
`docker compose stop app`, then the `pg_restore … --clean` above, then `docker compose up -d`.

## 15. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Browser shows a certificate error, Caddy logs "challenge failed" | DNS doesn't point at `SERVER-IP` yet, Cloudflare proxy is on, or port 80 is missing in the firewall. Fix it, then `docker compose restart caddy`. |
| App exits right after start, log lists settings | The `prod` validator: a secret is missing, a template value, or too short. Fix `.env`, `docker compose up -d`. |
| Build dies with "Killed" / exit 137 | Out of memory. Check the swap file (step 4) with `swapon --show`. |
| Meta: "Callback URL couldn't be validated" | `WEBHOOK_VERIFY_TOKEN` in Meta differs from `.env`, or the site isn't up on HTTPS yet. |
| Meta connect: "Invalid Scopes" | A scope in `META_OAUTH_SCOPES` has no use case in the app. See step 8. Restart the app after changing `.env`. |
| Meta connect: "No Facebook Page was shared" | The Page is in a Business portfolio. Add `business_management` to `META_OAUTH_SCOPES`. |
| Page connected but the bot is silent | App-level webhook subscription missing or still on the old URL (step 10). Check `docker compose logs app` for incoming `/webhook` requests. |
| `.env` changes have no effect | Compose reads `.env` on `up`, not on `restart`. Use `docker compose up -d`. |
| `APP_DB_PASSWORD` change breaks the DB login | The password is set only when the volume is first created. See README → "Database roles". |
