# Anonymous stack URLs contain bearer credentials; never send them as referrers.
Rails.application.config.action_dispatch.default_headers['Referrer-Policy'] = 'no-referrer'
