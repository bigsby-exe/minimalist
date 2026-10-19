# Working on minimalist

This is a fork of `mshwery/minimalist` being developed into a private task app. Read [README.md](README.md) for the product overview. The owner confirmed that there is no existing user data to preserve during this redesign. Schema changes still need to support a fresh database setup; a legacy-data conversion plan is unnecessary. This is context for the redesign, not permission to erase a future populated database.

## Agreed behavior

- Each signed-in user owns one task collection. All tasks and Starred are views of the same records. Anonymous access and shared lists are retired.
- Profile tag order determines section order. Within each tag, tasks sort by earliest due date, with undated tasks last and oldest-created first for equal dates.
- Tasks require text and one owned tag. Dates are optional, default to unset, and use a calendar picker. The creation form remembers the last chosen tag. Text, tag, and date remain editable; empty text is rejected.
- Tags have a name, emoji, and pastel colour that colours their tasks. Users can edit and reorder tags. Deleting an occupied tag requires assigning its tasks to another owned tag first. New accounts start with Household, Medical, and Work, in that order.
- Task rows show a star, single-line name, and optional date, without repeating the section's tag. Long names truncate outside editing. Dates use Today, Tomorrow, Yesterday, or relative day counts within a week, then calendar dates. Date labels get `❗` when due today and `‼️` when overdue. Dates have no time component.
- Stars replace completion checkboxes. Stars expire at midnight in the profile timezone, initially detected from the browser. Tasks remain. This must work after the app has been closed and across daylight-saving changes.
- Binning is the only way to remove a task. Hover, keyboard focus, or mobile row activation reveals the bin. The first activation turns it red; the second deletes from both views. Confirmation resets after five seconds or interaction elsewhere. There is no completion state.
- Tapping a task name edits it inline, exposing tag and calendar controls. Enter or leaving the row saves; Escape cancels. Empty names are rejected. Date/tag changes move the row only after saving. Midnight expiry preserves unfinished edits.
- The add box has text, a tag emoji selector, and an optional calendar, with Enter to add and no submit button. Successful creation clears text/date, remembers the tag, and retains focus.
- The account avatar opens Profile and Sign out. Profile uses the same compact visual style and provides a return link to tasks.

## Active implementation

The dashboard uses Rails controllers, ERB, and plain JavaScript. Its assets are `app/assets/javascripts/workspace.js` and `app/assets/stylesheets/workspace.css`, loaded by the application layout. Task and profile templates live in `app/views/tasks/` and `app/views/profiles/`.

Start with `config/routes.rb` to identify reachable behavior. Old Backbone/CoffeeScript assets, list/stack models, controllers, and tables still exist, but their presence does not mean multiple lists, sharing, or anonymous stacks remain supported. Extend the active task/profile implementation.

Task and tag access is scoped through `current_user`. Keep ownership validation when assigning or reassigning tags; accepting a submitted record ID is insufficient.

Stars use `Task#starred?`, the `starred` scope, and `starred_until`, set from `User#next_midnight`. Expiration is computed when reading records rather than requiring a scheduled deletion job. The browser also updates open views at midnight without discarding unfinished input. Preserve both behaviors.

## Verification and gotchas

Use the Ruby version and dependencies declared in `.ruby-version`, `Gemfile`, and `Gemfile.lock`. MySQL is required. Configure an isolated test database through `config/database.yml` or a `mysql2://` `DATABASE_URL`, then run:

```sh
RAILS_ENV=test bundle exec rails db:prepare
bundle exec rails test
RAILS_ENV=test bundle exec rails zeitwerk:check
node --check app/assets/javascripts/workspace.js
git diff --check
```

The main behavior tests are `test/models/personal_tasks_test.rb` and `test/integration/task_workspace_test.rb`. For UI changes, check desktop and mobile widths, task editing, tag ordering, bin confirmation, and midnight expiry with an unfinished draft.

- Keep `utf8mb4` for task text and tags so emoji can be stored.
- The `json < 3` dependency constraint fixes an observed Rails session-decoding incompatibility. Before changing it, verify that authentication persists across multiple requests and JSON requests still work.
- Docker provides a fallback when local Bundler or MySQL is unavailable. `docker build -t minimalist-check .` checks production asset compilation. The runtime image defaults to production; explicitly set the test environment and test database when using it for tests.
- Browser checks can use mocked Google OAuth in a temporary test environment. Keep mocks and authentication bypasses out of shipped configuration.
- For deployment, database preparation, environment variables, or Google OAuth setup, read [doc/DEPLOYMENT.md](doc/DEPLOYMENT.md). The container entrypoint does not prepare the database automatically.

Update this file when agreed behavior or the active implementation changes. Keep transient test results and deployment status in handoffs rather than treating them as permanent repository facts.
