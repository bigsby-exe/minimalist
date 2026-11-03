module Mobile
  # Device credentials are independent of browser cookies and the legacy API.
  class BaseController < ActionController::API
    before_action :prevent_caching
    before_action :authenticate_device!
    rescue_from ActiveRecord::RecordNotFound do
      render json: { error: "Record not found." }, status: :not_found
    end
    rescue_from ActiveRecord::RecordInvalid do |error|
      render json: { error: error.record.errors.full_messages.to_sentence }, status: :unprocessable_entity
    end
    rescue_from ActionController::ParameterMissing do
      render json: { error: "Required parameter missing." }, status: :bad_request
    end

    private

    def prevent_caching
      response.headers["Cache-Control"] = "no-store"
    end

    def bearer_token
      request.authorization.to_s.delete_prefix("Bearer ") if request.authorization.to_s.start_with?("Bearer ")
    end

    def authenticate_device!
      @device = AndroidDevice.for_token(bearer_token)
      render json: { error: "Connect this phone again." }, status: :unauthorized unless @device
    end

    def current_user
      @device&.user
    end
  end
end
