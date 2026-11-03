class ApplicationController < ActionController::Base
  # Prevent CSRF attacks by raising an exception.
  # For APIs, you may want to use :null_session instead.
  protect_from_forgery with: :exception
  before_action :require_approved_account

  # @see https://github.com/rails-api/active_model_serializers/issues/624
  serialization_scope :view_context

  rescue_from ActiveRecord::RecordNotFound, :with => :record_not_found

  def default_serializer_options
    {root: false}
  end

  # Devise without :database_authenticatable
  # @see https://github.com/plataformatec/devise/wiki/OmniAuth%3A-Overview#using-omniauth-without-other-authentications
  def new_session_path(scope)
    new_user_session_path
  end

  # Devise sign in path
  # @see https://github.com/plataformatec/devise/wiki/How-To%3A-Redirect-to-a-specific-page-on-successful-sign-in-and-sign-out
  def after_sign_in_path_for(resource)
    code = session.delete(:device_authorization_code)
    code.present? ? connect_device_path(code: code) : dashboard_path
  end
  
  def redirect_current_user
    if current_user
      redirect_to dashboard_path
    end
  end
  
  protected

    def require_approved_account
      return unless current_user && !current_user.access_allowed?

      sign_out current_user
      respond_to do |format|
        format.html { redirect_to new_user_session_path, alert: "This account is no longer approved for Steps." }
        format.any { head :unauthorized }
      end
    end

    def record_not_found
      respond_to do |format|
        format.json { render json: { error: "record not found" }, status: 404 }
        format.html { redirect_to :root }
      end
    end
end
