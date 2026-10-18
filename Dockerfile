# syntax=docker/dockerfile:1
ARG RUBY_VERSION=3.4.9
FROM ruby:${RUBY_VERSION}-slim-bookworm AS base
WORKDIR /rails

ENV RAILS_ENV=production \
    BUNDLE_DEPLOYMENT=1 \
    BUNDLE_PATH=/usr/local/bundle \
    BUNDLE_WITHOUT=development:test \
    BUNDLE_FORCE_RUBY_PLATFORM=true \
    RAILS_LOG_TO_STDOUT=1 \
    RAILS_SERVE_STATIC_FILES=1

# Node is needed by the legacy CoffeeScript/Eco/ExecJS asset stack.
RUN apt-get update -qq && \
    apt-get install --no-install-recommends -y ca-certificates libmariadb3 libyaml-0-2 nodejs && \
    rm -rf /var/lib/apt/lists/*

FROM base AS build
RUN apt-get update -qq && \
    apt-get install --no-install-recommends -y build-essential default-libmysqlclient-dev git libyaml-dev pkg-config && \
    rm -rf /var/lib/apt/lists/*

COPY Gemfile Gemfile.lock ./
RUN gem install bundler -v 4.0.20 --no-document && \
    bundle install && \
    rm -rf /usr/local/bundle/cache

COPY . .
# No production secrets or database connection are needed to compile assets.
RUN SECRET_KEY_BASE_DUMMY=1 NEW_RELIC_AGENT_ENABLED=false \
    bundle exec rails assets:precompile && \
    rm -rf tmp/cache tmp/local_secret.txt

FROM base AS runtime
RUN groupadd --gid 1000 rails && \
    useradd --uid 1000 --gid 1000 --create-home --shell /bin/bash rails
COPY --from=build /usr/local/bundle /usr/local/bundle
COPY --from=build /rails /rails
RUN mkdir -p log tmp/pids tmp/cache storage && \
    chown -R rails:rails log tmp storage && \
    chmod +x bin/docker-entrypoint
USER rails:rails
EXPOSE 3000
ENTRYPOINT ["/rails/bin/docker-entrypoint"]
CMD ["bundle", "exec", "puma", "-C", "config/puma.rb"]
