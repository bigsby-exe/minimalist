# Docker deployment on Ubuntu

Run the Rails/Puma image with MySQL behind your existing HTTPS reverse proxy.
This targets a fresh installation. No Railway account or configuration is used.

## Image builds in GitHub Actions

`.github/workflows/docker.yml` builds the Dockerfile on pull requests, pushes to
`master`, version tags (`v*`), and manual workflow runs. PR builds do not publish.
Successful builds of `master` and version tags publish to:

```text
ghcr.io/bigsby-exe/minimalist:latest
ghcr.io/bigsby-exe/minimalist:sha-FULL_COMMIT_SHA
ghcr.io/bigsby-exe/minimalist:v1.0.0  # example, only when that tag is pushed
```

`latest` tracks master. Set `MINIMALIST_IMAGE` to a `sha-...` tag or image digest
for a fixed release. The workflow builds `linux/amd64` (standard Intel/AMD Ubuntu
servers). ARM servers need a corresponding platform build before deployment.
Actions are pinned to commit SHAs. Publication uses the repository's automatic
`GITHUB_TOKEN` with package-write permission; no registry secret needs adding.
Enable Actions if GitHub has disabled workflows for this fork, and approve the
PR workflow if GitHub requests it. No workflow connects to your Ubuntu server.

After the first publication, open the package settings in GitHub and choose its
visibility. A public repository does not automatically make a new GHCR package
public. For private images, authenticate on the server with `docker login ghcr.io`
using your GitHub username and a token with `read:packages`. Keep that token out
of this repository. Public packages can be pulled without login.

## First deployment

Install Docker Engine and the Docker Compose plugin on Ubuntu. Clone the repo
(or copy `compose.yaml`, `.env.example` and `bin/deploy` into a deployment folder).
Wait for the first successful master image build before deploying `latest`.

```sh
cp .env.example .env
chmod 600 .env
openssl rand -hex 64  # use for SECRET_KEY_BASE
openssl rand -hex 32  # use for MYSQL_PASSWORD
openssl rand -hex 32  # use for MYSQL_ROOT_PASSWORD
# Edit .env and paste those three separately generated values.
./bin/deploy
```

Also fill in `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` if using Google login.
Register this exact authorized redirect URI for your Google OAuth web client:
`https://YOUR-DOMAIN/users/auth/google_oauth2/callback`.
Configure the consent screen and permitted test users as needed.

Keep `SECRET_KEY_BASE` stable: it protects sessions and is also used to encode
list IDs in URLs. Do not set `SECRET_KEY_BASE_DUMMY` at runtime. Database passwords
initialize the MySQL volume on first boot; changing `.env` later does not change
passwords inside an existing database.

`bin/deploy` pulls the images, waits for MySQL health, stops an existing web
container, runs `db:prepare` once, and starts the new web container. This creates
the fresh schema or applies pending migrations. A failed migration stops the
script and leaves the old app stopped, avoiding running against a partial schema.
The deployment has a short maintenance window; it is not a zero-downtime rollout.

## Connect your reverse proxy

### Proxy running on the Ubuntu host

The default Compose configuration publishes the application only on
`127.0.0.1:3000`. Point your proxy upstream at `http://127.0.0.1:3000` (or change
`APP_PORT` in `.env`). Keep TLS termination on the proxy. Preserve the public
`Host` and overwrite untrusted forwarded headers, setting `X-Forwarded-Proto`
to the original client protocol.

For an existing Nginx HTTPS server block, the relevant location is:

```nginx
location / {
    proxy_pass http://127.0.0.1:3000;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}
```

The main app enforces HTTPS. `/up` alone allows plain HTTP for container health
checks and returns no application data. It checks Rails boot, not database health.

### Proxy running in Docker

A proxy container's `127.0.0.1` is not the Ubuntu host. With Compose 2.24.4 or newer,
copy `compose.proxy.yaml` as well and add these to `.env`:

```dotenv
COMPOSE_FILE=compose.yaml:compose.proxy.yaml
PROXY_NETWORK=your-existing-proxy-network
```

The override removes the host port and attaches only the web service to the
existing external network. Configure your proxy upstream as
`http://minimalist-web:3000`, with the same forwarded headers as above.
MySQL stays on the application's default network and has no published port.
Run `./bin/deploy` after selecting either proxy configuration.

## Updates, logs and backups

```sh
# Set MINIMALIST_IMAGE in .env to the desired image/tag, then:
./bin/deploy
docker compose ps
docker compose logs --tail=100 web
curl --fail http://127.0.0.1:3000/up  # host-proxy setup only
```

Back up before updates that migrate the database. The named `mysql_data` volume
survives container replacement and `docker compose down`. Do not use
`docker compose down -v` unless intentionally deleting the database.

Example logical backup (run from the deployment directory):

```sh
umask 077
mkdir -p backups
docker compose exec -T db sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump --single-transaction --no-tablespaces -u"$MYSQL_USER" "$MYSQL_DATABASE"' > "backups/minimalist-$(date +%F-%H%M%S).sql"
```

Schedule backups, retain copies off the server, and verify restores. Pinning an
older application image does not undo a database migration: rollback may require
a database restore. Containers restart automatically after reboots once Docker
is enabled at boot. Log files have size limits in Compose.

## Build details and verification

The Dockerfile uses Ruby 3.4.9, Bundler 4.0.20, frozen source-gem dependencies and
a non-root runtime. The portable `ruby` lockfile platform supports source builds
on Linux. Node supplies ExecJS for the legacy assets. Build-only dummy secrets
allow asset compilation; real secrets, `.env` files and local output are excluded
from the Docker context. Never put deployment secrets in Docker build arguments.

No application tests were added or run, as requested. Shell syntax and YAML
parsing were checked locally. Docker and Ruby are unavailable in the authoring
environment; GitHub Actions is the image-build gate. Verify the workflow is green
and check database initialization, login, list creation/sharing and drag-and-drop
on your server before relying on the deployment.

The app currently stores lists/tasks in MySQL and needs no Redis or worker.
If uploads are added later, configure durable storage rather than using the
web container's ephemeral filesystem.
