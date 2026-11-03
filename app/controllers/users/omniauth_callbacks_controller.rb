class Users::OmniauthCallbacksController < Devise::OmniauthCallbacksController
  def sign_in_with(provider_name)
    @user = User.from_omniauth(request.env['omniauth.auth'])
    unless @user
      redirect_to new_user_session_path, alert: "This Google account is not approved for Steps."
      return
    end
    sign_in_and_redirect @user, :event => :authentication
    set_flash_message(:notice, :success, :kind => provider_name) if is_navigational_format?
  end

  def google_oauth2
    sign_in_with 'Google'
  end
end
