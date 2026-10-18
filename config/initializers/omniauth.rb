# Use the Rails session key and a global form token for OmniAuth's CSRF verifier.
# See https://github.com/cookpad/omniauth-rails_csrf_protection#readme
OmniAuth.config.allowed_request_methods = [:post]
OmniAuth.config.request_validation_phase =
  OmniAuth::AuthenticityTokenProtection.new(key: :_csrf_token)
