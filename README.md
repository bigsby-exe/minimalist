# minimalist

A private task app, forked from [mshwery/minimalist](https://github.com/mshwery/minimalist).

Sign in with Google to keep tasks in one collection, grouped by your profile's tag order. Tags have an emoji and pastel colour. Tasks require a tag and can have an optional date, selected with a calendar. Within each tag, tasks sort by due date, with undated tasks last.

All tasks and Starred show the same underlying tasks. Stars clear at midnight in your profile timezone, including when the app is closed. Today’s tasks show ❗ and overdue tasks show ‼️. Tasks have no completion state; binning with confirmation removes them.

In Profile, edit tags, drag to reorder them or use the arrow buttons, and choose your timezone. An occupied tag must have its tasks reassigned before deletion. New accounts start with Household, Medical, and Work.

## Development checks

Run `bundle exec rails db:prepare` and `bundle exec rails test` with a test MySQL database configured. The task workspace tests cover account isolation, dates, tag management, and star expiry over daylight-saving changes.

The personal-task migration replaces the old task fields and assumes no existing user task data. Anonymous stack and shared-list routes are retired.

## Deployment

See [Docker deployment on Ubuntu](doc/DEPLOYMENT.md) for the production image,
database setup, environment variables, and Google OAuth configuration.
