# Deploy with Docker and Railway

The app runs as one Rails/Puma web service plus a separate MySQL database.
This setup targets a new deployment with no existing users. Merge the security
hardening changes first (already included on the base branch of this change).

## Railway

1. Create a Railway project and add its MySQL service. Keep the database on the
   private network; the web app does not need a public database endpoint.
2. Add this GitHub repository as a web service in the same project/environment.
   Railway reads `railway.json`, builds `Dockerfile`, runs `db:prepare` as a
   pre-deploy command, then starts Puma using the image's default command.
3. Before the first successful deployment, set these web-service variables.
   Replace `MySQL` in the reference if your database service has a different name.

| Variable | Value |
| --- | --- |
| `DATABASE_URL` | `${{MySQL.MYSQL_URL}}` (private connection URL) |
| `SECRET_KEY_BASE` | Generate once with `openssl rand -hex 64`; store as a secret |
| `GOOGLE_CLIENT_ID` | Your Google OAuth web client ID |
| `GOOGLE_CLIENT_SECRET` | Your Google OAuth web client secret |
| `NEW_RELIC_AGENT_ENABLED` | `false` unless you configure New Relic |

The image sets production mode, stdout logging, and static asset serving.
Railway supplies `PORT`; Puma reads it. Do not set `SECRET_KEY_BASE_DUMMY` at
runtime. Keep `SECRET_KEY_BASE` stable: besides sessions, this app uses it to
encode list IDs in URLs. Changing it invalidates existing list URLs.

4. Generate a Railway domain or attach your custom domain. In the Google Cloud
   OAuth client, register this exact authorized redirect URI:
   `https://YOUR-DOMAIN/users/auth/google_oauth2/callback`.
   Configure the consent screen and any permitted test users in Google Cloud.
5. Deploy. The `/up` health endpoint must return 200 within 120 seconds. It checks
   Rails boot, not database readiness. Database preparation must succeed before
   the web service starts; a failure stops the deployment.
6. Enable scheduled backups for the MySQL volume in Railway. Check the retention
   policy and perform a restore exercise before relying on it for important data.

The database is provisioned separately; `railway.json` configures only the web
service and does not create a database, buy a plan, or configure backups.

## Image build and local smoke check

```sh
docker build -t minimalist:local .
cp .env.example .env.production
# Fill in real secrets and a MySQL URL reachable from the container.
# localhost in DATABASE_URL refers to the container, not your computer.
docker run --rm --env-file .env.production minimalist:local bundle exec rails db:prepare
docker run --rm --name minimalist -p 127.0.0.1:3000:3000 --env-file .env.production minimalist:local
```

From another terminal, `curl --fail http://127.0.0.1:3000/up` should succeed.
The main application deliberately redirects HTTP to HTTPS. For full browser
use, place it behind a TLS-terminating reverse proxy rather than disabling SSL.
The proxy must overwrite untrusted forwarded headers and supply
`X-Forwarded-Proto: https`. `/up` alone is exempt from the HTTPS redirect so
internal health probes work; it returns no application data.

For a self-hosted deployment, run `db:prepare` once before starting/replacing web
containers. Do not put migrations in every replica's startup command. Give the
MySQL service a persistent volume and backups. Currently the application stores
its lists/tasks in MySQL; it does not need a Redis service or a background worker.
If file uploads are introduced later, configure durable object storage instead
of relying on the web container's ephemeral `storage` directory.

## Build details

- Ruby 3.4.9 and Bundler 4.0.20 match the committed lockfile.
- The lockfile's portable `ruby` platform and `BUNDLE_FORCE_RUBY_PLATFORM=true`
  allow Linux builds from the already locked source gems, including native
  extensions. Gem versions are not upgraded or resolved during the frozen build.
- The build stage includes compiler/MySQL headers; the final image runs as a
  non-root user without the compiler toolchain.
- Node provides the ExecJS runtime for the existing CoffeeScript/Eco/Sass assets.
- Assets are precompiled with Rails' build-only dummy secret. Real secrets and
  local environment files are excluded from the image's build context.
- `/up` reports whether Rails booted. Inspect the deploy logs for database errors,
  then check list creation, sign-in, sharing, and drag-and-drop on the real domain.

## Validation status

The authoring environment had neither Docker nor Ruby, so the container build,
Rails boot, gem installation, and database preparation have not been executed.
Shell syntax, Railway configuration structure, and whitespace were checked.
No tests were added or run, as requested. The first image build and staging boot
are required before treating this deployment configuration as production-ready.

References:
- https://docs.railway.com/guides/rails
- https://docs.railway.com/config-as-code/reference
- https://docs.railway.com/databases/mysql
- https://docs.railway.com/volumes/backups
